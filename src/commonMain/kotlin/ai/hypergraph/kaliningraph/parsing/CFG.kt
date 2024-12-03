@file:Suppress("NonAsciiCharacters")
package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.*
import ai.hypergraph.kaliningraph.graphs.LabeledGraph
import ai.hypergraph.kaliningraph.sampling.choose
import ai.hypergraph.kaliningraph.types.*
import kotlin.jvm.JvmName
import kotlin.random.Random
import kotlin.time.*
import kotlin.time.Duration.Companion.seconds

typealias Σᐩ = String
typealias Production = Π2<Σᐩ, List<Σᐩ>>
typealias IProduction = Π2<Int, List<Int>>
// TODO: make this immutable
typealias CFG = Set<Production>

val Production.LHS: Σᐩ get() = first
val Production.RHS: List<Σᐩ> get() = second
// Not sure why this was added, but we don't have time for it in production
//  second.let { if (it.size == 1 && 2 < it.first().length && it.first().first() == '`') it.map(Σᐩ::stripEscapeChars) else it }

/**
 * "Freezes" the enclosed CFG, making it immutable and caching its hashCode for
 * much faster equality checks unlike the default LinkedHashSet implementation,
 * which must recompute hashCode(), incurring O(n) cost in the size of the CFG.
 * This is only necessary because we are using the cache { ... } pattern, which
 * will be slow to compute the first time, but much faster on subsequent calls.
 * Storing the hashCode() in a field avoids recomputing it on every read.
 */
fun CFG.freeze(): CFG = if (this is FrozenCFG) this else FrozenCFG(this)
private class FrozenCFG(val cfg: CFG): CFG by cfg {
  val cfgId = cfg.hashCode()
  override fun equals(other: Any?) =
    ((other as? FrozenCFG)?.cfgId == cfgId) || (other as? CFG) == cfg
  override fun hashCode(): Int = cfgId
}

val CFG.language: CFL by cache { CFL(this) }
val CFG.delimiters: Array<Σᐩ> by cache { (terminals.sortedBy { -it.length } + arrayOf(HOLE_MARKER, " ")).toTypedArray() }
val CFG.nonterminals: Set<Σᐩ> by cache { map { it.LHS }.toSet() }
val CFG.symbols: Set<Σᐩ> by cache { nonterminals + flatMap { it.RHS } }
val CFG.terminals: Set<Σᐩ> by cache { symbols - nonterminals }
val CFG.terminalUnitProductions: Set<Production>
    by cache { filter { it.RHS.size == 1 && it.RHS[0] !in nonterminals } }
val CFG.unitProductions: Set<Pair<Σᐩ, Σᐩ>> by cache { filter { it.RHS.size == 1 }.map { it.LHS to it.RHS[0] }.toSet() }
val CFG.nonterminalProductions: Set<Production> by cache { filter { it !in terminalUnitProductions } }
val CFG.unitNonterminals: Set<Σᐩ> by cache { terminalUnitProductions.map { it.LHS }.toSet() }
val CFG.bimap: BiMap by cache { BiMap(this) }
// Maps nonterminal sets to their terminals, n.b., each terminal can be generated
// by multiple nonterminals, and each nonterminal can generate multiple terminals
val CFG.tmap: Map<Set<Σᐩ>, Set<Σᐩ>> by cache {
  terminals.map { bimap[listOf(it)] to it }.groupBy { it.first }
    .mapValues { it.value.map { it.second }.toSet() }
}

val CFG.unicodeMap by cache { terminals.associateBy { Random(it.hashCode()).nextInt().toChar().toUnicodeEscaped() } }

val CFG.ntLst by cache { (symbols + "ε").toList() }
val CFG.ntMap by cache { ntLst.mapIndexed { i, s -> s to i }.toMap() }

// Maps each nonterminal to the set of nonterminal pairs that can generate it,
// which is then flattened to a list of adjacent pairs of nonterminal indices
val CFG.vindex: Array<IntArray> by cache {
  Array(bindex.indexedNTs.size) { i ->
    bimap[bindex[i]].filter { it.size > 1 }
      .flatMap { listOf(bindex[it[0]], bindex[it[1]]) }.toIntArray()
  }
}

val CFG.vindex2: Array<List<List<Int>>> by cache {
  Array(bindex.indexedNTs.size) { i ->
    bimap[bindex[i]].filter { it.size > 1 }
      .map { listOf(bindex[it[0]], bindex[it[1]]) }
  }
}

val CFG.bindex: Bindex<Σᐩ> by cache { Bindex(nonterminals) }
val CFG.normalForm: CFG by cache { normalize() }
val CFG.depGraph: LabeledGraph by cache { dependencyGraph() }
val CFG.revDepGraph: LabeledGraph by cache { revDependencyGraph() }

// Terminals which are blocked from being synthesized by a solver
val CFG.blocked: MutableSet<Σᐩ> by cache { mutableSetOf() }

val CFG.originalForm: CFG by cache { rewriteHistory[this]?.get(0) ?: this }
val CFG.nonparametricForm: CFG by cache { rewriteHistory[this]!![1] }
//val CFG.originalForm by cache { rewriteHistory[this]!![0] }
//val CFG.nonparametricForm by cache { rewriteHistory[this]!![1] }

/** Backing fields for [reachableSymbols], [reachableSymbolsViaUnitProds]
 *  TODO: back the fields with functions instead of vis versa using mutable maps?
 *        - Pros: early accesses are faster with a gradually-filled map
 *        - Cons: immutable fields follow convention, easier to reason about
 */
val CFG.reachability by cache { mutableMapOf<Σᐩ, Set<Σᐩ>>() }

// Equivalence class of an NT B are all NTs, A ->* B ->* C
// reachable via unit productions (in forward or reverse)
val CFG.unitReachability by cache {
  symbols.associateWith { from ->
    LabeledGraph {
      unitProductions.map { it.first to it.second }
//      .filter { (a, b) -> nonterminals.containsAll(listOf(a, b)) }
        .forEach { (a, b) -> a - b }
    }.let {
      setOf(from) + (it.transitiveClosure(setOf(from)) +
        it.reversed().transitiveClosure(setOf(from)))
    }.filter { it in nonterminals }
  }
}

val CFG.noNonterminalStubs: CFG by cache {
//  try { throw Exception() } catch (e: Exception) { e.printStackTrace() }
  println("Disabling nonterminal stubs!")
  filter { it.RHS.none { it.isNonterminalStubIn(this) } }.toSet().freeze()
    .also { rewriteHistory.put(it, freeze().let { rewriteHistory[it]!! + listOf(it)}) }
    .also { it.blocked.addAll(blocked) }
}

val CFG.noEpsilonOrNonterminalStubs: CFG by cache {
//  try { throw Exception() } catch (e: Exception) { e.printStackTrace() }
  println("Disabling nonterminal stubs!")
  filter { it.RHS.none { it.isNonterminalStubIn(this) } }
    .filter { "ε" !in it.toString() }.toSet().freeze()
    .also { rewriteHistory.put(it, freeze().let { rewriteHistory[it]!! + listOf(it)}) }
    .also { it.blocked.addAll(blocked) }
}

val CFG.parikhFPCache: Map<Σᐩ, BitvecPosetInterval> by cache { TODO() }

// Maps each symbol to the set of nonterminals that can generate it
val CFG.generators: Map<Σᐩ, Set<Σᐩ>> by cache {
  map { prod -> prod.RHS.map { it to prod.LHS } }.flatten()
    .groupBy { it.first }.mapValues { it.value.map { it.second }.toSet() }
}

val CFG.nonterminalFormulas: Map<Σᐩ, Σᐩ> by cache {
  nonterminals.associateWith { nt -> toFormula(nt) }
}

/**
 * Maps each nonterminal to terminals that can be reached from it. At least one of
 * each of these terminals must be present in the input string for the nonterminal
 * to be matched. If a string does not contain any of these terminals, we know the
 * nonterminal is not contained in the parse tree, and can prune it from the CFG.
 *
 *       Γ |- A -> a
 *       -----------------------
 *       Γ |- φ[A] = a
 *
 *       Γ |- A -> B C
 *       -----------------------
 *       Γ |- φ[A] = φ[B] ʌ φ[C]
 *
 *       Γ |- A -> B | C
 *       -----------------------
 *       Γ |- φ[A] = φ[B] v φ[C]
 */

fun CFG.toFormula(nt: Σᐩ): Σᐩ =
  when (nt) {
    in terminals -> nt
    !in nonterminals -> "false"
    else -> bimap[nt].joinToString(" or ", "( ", " )") {
      it.joinToString(" and ", "( ", " )") { toFormula(it) }
    }
  } // TODO: fix stack blowup when there is a cycle in the CFG


// Prunes all nonterminals that represent a finite set of terminals down to the root
// Usually this is a tree-like structure, but it can also be a DAG of nonterminals
val CFG.pruneTreelikeNonterminals: CFG by cache {
  println("Pruning treelike nonterminals!")
  filter { it.RHS.any { !it.isTreelikeNonterminalIn(this) } || "ε" in it.LHS }.toSet()
    .let { cfg ->
      val brokenReferences = cfg.terminals
      cfg +
        // Restore preexisting nonterminal stubs for all remaining treelike nonterminals
        brokenReferences.filter { "<$it>" in terminals }.map { it to listOf("<$it>") } +
        cfg.nonterminals.filter { it.isOrganicNonterminal() }.map { it to listOf("<$it>") } +
        // Restore old nonterminal stubs for unreferenced unit productions
        brokenReferences.filter { it.isSyntheticNonterminal() && it in nonterminals }
          .map { l -> filter { it.LHS == l }.map { l to it.RHS } }
          .flatten()
//          .first()
          .toSet().also { println("Restored productions: ${it.prettyPrint()}") }
    }
    .let { it.transformIntoCNF() }
    .also { rewriteHistory.put(it, freeze().let { listOf(rewriteHistory[it]!![0]) + listOf(it)}) }
    .also { it.blocked.addAll(blocked) }
}

// Returns true iff the receiver is a nonterminal whose descendants
// are themselves either (1) treelike nonterminals or (2) terminals
private fun Σᐩ.isTreelikeNonterminalIn(
  cfg: CFG,
  reachables: Set<Σᐩ> = cfg.reachableSymbols(this) - this,
  nonTreeLike: Set<Σᐩ> = setOf(this)
): Bln = when {
  "ε" in this -> true
  (reachables intersect nonTreeLike).isNotEmpty() -> false
  else -> reachables.all { it in cfg.terminals ||
      it.isTreelikeNonterminalIn(cfg, nonTreeLike = nonTreeLike + reachables) }
}

val CFG.joinMap: JoinMap by cache { JoinMap(this) }
class JoinMap(val CFG: CFG) {
  // TODO: Doesn't appear to confer any significant speedup? :/
  val precomputedJoins: MutableMap<Π2A<Set<Σᐩ>>, Set<Π3A<Σᐩ>>> =
    CFG.nonterminals.choose(1..3).let { it * it }
      .associateWith { subsets -> subsets.let { (l, r) -> join(l, r) } }
      .also { println("Precomputed join map has ${it.size} entries.") }.toMutableMap()

  fun join(l: Set<Σᐩ>, r: Set<Σᐩ>, tryCache: Bln = false): Set<Π3A<Σᐩ>> =
    if (tryCache) precomputedJoins[l to r] ?: join(l, r, false).also { precomputedJoins[l to r] = it }
    else (l * r).flatMap { (l, r) -> CFG.bimap[listOf(l, r)].map { Triple(it, l, r) } }.toSet()

  @JvmName("setJoin")
  operator fun get(l: Set<Σᐩ>, r: Set<Σᐩ>): Set<Σᐩ> =
    join(l, r, false).map { it.first }.toSet()

  @JvmName("treeJoin")
  operator fun get(left: Forest, right: Forest): Forest =
    join(left.map { it.root }.toSet(), right.map { it.root }.toSet(), false)
      .map { (rt, l, r) ->
        Tree(rt, null, left.first { it.root == l }, right.first { it.root == r })
      }.toSet()
}

// Maps indices to nonterminals and nonterminals to indices
class Bindex<T>(
  val set: Set<T>,
  val indexedNTs: List<T> = set.toList(),
  val ntIndices: Map<T, Int> = indexedNTs.zip(indexedNTs.indices).toMap()
): List<T> by indexedNTs {
  constructor(map: Map<Int, T>) : this(map.values.toSet(), map.values.toList(), map.entries.associate { it.value to it.key })
  operator fun get(s: T): Int = ntIndices[s] ?: 1.also { println("Unknown nonterminal: $s"); null!! }
  fun getUnsafe(s: T): Int? = ntIndices[s]
  override fun toString(): String = indexedNTs.mapIndexed { i, it -> "$i: $it" }.joinToString("\n", "Bindex:\n", "\n")
}
// Maps variables to expansions and expansions to variables in a grammar
class BiMap(cfg: CFG) {
  val L2RHS by lazy { cfg.groupBy({ it.LHS }, { it.RHS }).mapValues { it.value.toSet() } }
  val R2LHS by lazy { cfg.groupBy({ it.RHS }, { it.LHS }).mapValues { it.value.toSet() } }

  val TDEPS: Map<Σᐩ, MutableSet<Σᐩ>> by lazy { // Maps all symbols to NTs that can generate them
    mutableMapOf<Σᐩ, MutableSet<Σᐩ>>().apply {
      for ((l, r) in cfg) for (symbol in r)
          getOrPut(symbol) { mutableSetOf() }.add(l)
    }
  }
  val NDEPS: Map<Σᐩ, MutableSet<Σᐩ>> by lazy { // Maps all NTs to the symbols they can generate
    mutableMapOf<Σᐩ, MutableSet<Σᐩ>>().apply {
      for ((l, r) in cfg) for (symbol in r)
          getOrPut(l) { mutableSetOf() }.add(symbol)
    }
  }
  val TRIPL by lazy {
    R2LHS.filter { it.key.size == 2 }
      .map { it.value.map { v -> v to it.key[0] to it.key[1] } }.flatten()
  }
  val X2WZ: Map<Σᐩ, List<Triple<Σᐩ, Σᐩ, Σᐩ>>> by lazy {
    TRIPL.groupBy { it.second }.mapValues { it.value }
  }
  val UNITS by lazy {
    cfg.filter { it.RHS.size == 1 && it.RHS[0] !in cfg.nonterminals }
      .groupBy({ it.LHS }, { it.RHS[0] }).mapValues { it.value.toSet() }
  }
  operator fun get(p: List<Σᐩ>): Set<Σᐩ> = R2LHS[p] ?: emptySet()
  operator fun get(p: Σᐩ): Set<List<Σᐩ>> = L2RHS[p] ?: emptySet()
  operator fun get(p: Set<Σᐩ>): Set<Σᐩ> = TDEPS.entries.filter { it.value == p }.map { it.key }.toSet()
}

// n.b., this only works if the CFG is acyclic, i.e., finite otherwise it will loop forever
fun CFG.toPTree(from: Σᐩ = START_SYMBOL, origCFG: CFG = this): PTree =
  PTree(from, bimap[from].map { toPTree(it[0], origCFG) to if (it.size == 1) PTree() else toPTree(it[1], origCFG) })
    .also { it.ntIdx = (origCFG.ntMap[(if('~' in from) from.split('~')[1] else from)] ?: Int.MAX_VALUE) }

/*
Γ ⊢ ∀ v.[α→*]∈G ⇒ α→[β]       "If all productions rooted at α
----------------------- □β     yield β, then α necessarily yields β"
Γ ⊢ □ α→[β]

Γ ⊢ □ ω→[α] □ α→[β]
----------------------- trans
Γ ⊢ □ ω → [α]∪[β]

Γ ⊢ s∈Σ\Σ'  v'∈V.□v'→[s]      "Any production containing a nonterminal that
----------------------- elim   necessarily generates a terminal that is not
Γ ⊢ ∀ρ,v∈ρ  G' ← G'\ρ          in the subgrammar can be safely removed."
*/

val CFG.mustGenerate by cache { inevitableSymbols() }

fun CFG.inevitableSymbols(map: Map<Σᐩ, Set<Σᐩ>> = emptyMap()): Map<Σᐩ, Set<Σᐩ>> {
  val newMap = map.toMutableMap()
  symbols.forEach { smb ->
//    println("Testing $smb")
    bimap.TDEPS[smb]?.forEach { nt ->
//      println("Testing $smb -> $nt")
      if (bimap[nt].all { smb in it || nt in it }) {
//        println("Worked! $nt => $smb")
        newMap[nt] = newMap.getOrPut(nt) { setOf(nt) } +
            newMap.getOrPut(smb) { setOf(smb) }
      }
//      else {
//        if (smb == "NEWLINE")
//        println("Failed! $nt !=> $smb, first ${bimap[nt].first { smb !in it }}")
//      }
    }
    newMap[smb] = newMap.getOrPut(smb) { setOf(smb) }
  }
  return if (newMap == map) map else inevitableSymbols(newMap)
}

fun Bln.explain(cfg: CFG, prod: Production, reason: String = "") = this.also{
  if(it) {
    println("Removed [${prod.LHS} -> ${prod.RHS.joinToString(" ")}] because $reason")
    if (cfg.count { it.first == prod.LHS } == 1) println("And no other productions were left for `${prod.LHS}`!")
  }
}

fun CFG.removeTerminalsVerbose(allowed: Set<Σᐩ>, otps: Set<Production> = this.terminalUnitProductions, origTerms: Set<Σᐩ> = this.terminals, mustGenerate: Map<Σᐩ, Set<Σᐩ>> = this.mustGenerate): CFG {
  val deadNTs = mutableSetOf<Σᐩ>()
  val next = toMutableSet().apply { removeAll { prod ->
    (
//        (prod in otps && (prod.RHS.first() !in allowed))
//          .explain(this, prod, "the terminal `${prod.RHS.first()}` is not allowed") ||
        (mustGenerate[prod.LHS]?.any { (it in origTerms && it !in allowed)
          .explain(this, prod, "LHS value `${prod.LHS}` must generate `$it` and `$it` was not allowed") } == true) ||
        prod.RHS.any { rhs -> mustGenerate[rhs]?.any { (it in origTerms && it !in allowed)
          .explain(this, prod, "RHS value `$rhs` must generate `$it` and `$it` was not allowed") } == true }
    ).also { if (it && this.count { it.first == prod.first } == 1) {
        println("Added `${prod.first}` to deadNTs!")
        deadNTs.add(prod.LHS) }
      }
  } }

  next.removeAll { prod ->
    prod.RHS.any { rhs ->
      (rhs in deadNTs).explain(next, prod, "the RHS value `$rhs` is a dead NT!") ||
        (rhs !in origTerms).explain(next, prod, "the RHS terminal `$rhs` was a chopped NT")
    }
  }

  return if (next.size == size) this else next.removeTerminalsVerbose(allowed, otps, origTerms, mustGenerate)
}

fun CFG.removeTerminals(
  allowed: Set<Σᐩ>,
  deadNTs: Set<Σᐩ> = emptySet(),
  origTerms: Set<Σᐩ> = this.terminals,
  mustGenerate: Map<Σᐩ, Set<Σᐩ>> = this.mustGenerate
): CFG {
  val deadNTs = deadNTs.toMutableSet()
  val next = toMutableSet().apply {
    removeAll { prod ->
      (prod.RHS + prod.LHS).toSet().any { mustGenerate[it]?.any { it in origTerms && it !in allowed || it in deadNTs } == true }
        .also { if (it && count { it.first == prod.first } == 1) deadNTs.add(prod.LHS) }
    }
  }

  next.removeAll { prod -> prod.RHS.any { rhs -> rhs in deadNTs || (rhs in next.terminals && rhs !in origTerms) } }

  val new = next.removeUselessSymbols()

  return if (new.size == size) this else new.removeTerminals(allowed, deadNTs, origTerms, mustGenerate)
}

/*
 Specializes the CFG to a set of terminals X, by recursively pruning
 every nonterminal v which necessarily generates a terminal t' ∉ X and
 every nonterminal that necessarily generates v. We call the set of all
 productions that remain after pruning, the preimage of G under T or the "subgrammar".
 */
fun CFG.subgrammar(image: Set<Σᐩ>): CFG =
  removeTerminals(image)
    .also { rewriteHistory.put(it, freeze().let { rewriteHistory[it]!! + listOf(it)}) }
    .freeze()

fun CFG.directSubgrammar(toRemove: Set<Σᐩ>): CFG =
  filter { (it.RHS + it.LHS).all { it !in toRemove } }
    .normalize().noEpsilonOrNonterminalStubs.freeze()
    .also { println("Reduced CFG from $size to ${it.size} rules") }

fun CFG.forestHash(s: Σᐩ) = parseForest(s).map { it.structureEncode() }.hashCode()
fun CFG.nonterminalHash(s: Σᐩ) = s.tokenizeByWhitespace().map { preimage(it) }.hashCode()
fun CFG.preimage(vararg nts: Σᐩ): Set<Σᐩ> = bimap.R2LHS[nts.toList()] ?: emptySet()

fun CFG.dependencyGraph() =
  LabeledGraph { forEach { prod -> prod.second.forEach { rhs -> prod.LHS - rhs } } }

fun CFG.revDependencyGraph() =
  LabeledGraph { forEach { prod -> prod.second.forEach { rhs -> rhs - prod.LHS } } }

fun CFG.jsonify() = "cfg = {\n" +
  bimap.L2RHS.entries.joinToString("\n") {
    ("\"${it.key}\": [${it.value.joinToString(", ") {
      it.joinToString(", ", "(", ")") { "\"$it\"" }
    }}],")
  } + "\n}"

class TermDict(
  val terms: Set<Σᐩ>,
  val dict: Map<Char, Σᐩ> = terms.associateBy { Random(it.hashCode()).nextInt().toChar() },
  val revDict: Map<Σᐩ, Char> = dict.entries.associate { (k, v) -> v to k }
) : Map<Char, Σᐩ> by dict {
  fun encode(str: String) = str.tokenizeByWhitespace().map { revDict[it]!! }.joinToString("")
  fun encode(str: List<String>) = str.map { revDict[it]!! }.joinToString("")
}