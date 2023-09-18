@file:Suppress("NonAsciiCharacters")
package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.graphs.LabeledGraph
import ai.hypergraph.kaliningraph.sampling.choose
import ai.hypergraph.kaliningraph.types.*
import kotlin.jvm.JvmName
import kotlin.time.*

typealias Σᐩ = String
typealias Production = Π2<Σᐩ, List<Σᐩ>>
// TODO: make this immutable
typealias CFG = Set<Production>

val Production.LHS: Σᐩ get() = first
val Production.RHS: List<Σᐩ> get() =
  second.let { if (it.size == 1) it.map(Σᐩ::stripEscapeChars) else it }

/**
 * "Freezes" the enclosed CFG, making it immutable and caching its hashCode for
 * much faster equality checks unlike the default LinkedHashSet implementation,
 * which must recompute hashCode(), incurring O(n) cost in the size of the CFG.
 * This is only necessary because we are using the cache { ... } pattern, which
 * will be slow to compute the first time, but much faster on subsequent calls.
 * Storing the hashCode() in a field avoids recomputing it on every read.
 */
fun CFG.freeze(): CFG = FrozenCFG(this)
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
val CFG.unitProductions: Set<Production> by cache { filter { it.RHS.size == 1 } }
val CFG.nonterminalProductions: Set<Production> by cache { filter { it !in terminalUnitProductions } }
val CFG.bimap: BiMap by cache { BiMap(this) }
// Maps nonterminal sets to their terminals, n.b., each terminal can be generated
// by multiple nonterminals, and each nonterminal can generate multiple terminals
val CFG.tmap: Map<Set<Σᐩ>, Set<Σᐩ>> by cache {
  terminals.map { bimap[listOf(it)] to it }.groupBy { it.first }
    .mapValues { it.value.map { it.second }.toSet() }
}

// Maps each nonterminal to the set of nonterminals that can generate it
val CFG.vindex: Array<IntArray> by cache {
  Array(bindex.indexedNTs.size) { i ->
    bimap[bindex[i]].filter { it.size > 1 }
      .flatMap { listOf(bindex[it[0]], bindex[it[1]]) }.toIntArray()
  }
}

val CFG.bindex: Bindex<Σᐩ> by cache { Bindex(nonterminals) }
val CFG.normalForm: CFG by cache { normalize() }
val CFG.graph: LabeledGraph by cache { dependencyGraph() }

val CFG.originalForm: CFG by cache { rewriteHistory[this]!![0] }
val CFG.nonparametricForm: CFG by cache { rewriteHistory[this]!![1] }
//val CFG.originalForm by cache { rewriteHistory[this]!![0] }
//val CFG.nonparametricForm by cache { rewriteHistory[this]!![1] }

/** Backing fields for [reachableSymbols], [reachableSymbolsViaUnitProds]
    TODO: back the fields with functions instead of vis versa using mutable maps?
          - Pros: early accesses are faster with a gradually-filled map
          - Cons: immutable fields follow convention, easier to reason about
 */
val CFG.reachability by cache { mutableMapOf<Σᐩ, Set<Σᐩ>>() }

// Equivalence class of an NT B are all NTs, A ->* B ->* C
// reachable via unit productions (in forward or reverse)
val CFG.unitReachability by cache {
  symbols.associateWith { from ->
    LabeledGraph {
      unitProductions.map { it.LHS to it.RHS.first() }
//      .filter { (a, b) -> nonterminals.containsAll(listOf(a, b)) }
        .forEach { (a, b) -> a - b }
    }.let {
      setOf(from) + (it.transitiveClosure(setOf(from)) +
        it.reversed().transitiveClosure(setOf(from)))
    }.filter { it in nonterminals }
  }
}

val CFG.noNonterminalStubs: CFG by cache {
  println("Disabling nonterminal stubs!")
  filter { it.RHS.none { it.isNonterminalStubIn(this) } }.toSet()
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
): 𝔹 = when {
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

  fun join(l: Set<Σᐩ>, r: Set<Σᐩ>, tryCache: 𝔹 = false): Set<Π3A<Σᐩ>> =
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
  val L2RHS = cfg.groupBy({ it.LHS }, { it.RHS }).mapValues { it.value.toSet() }
  val R2LHS = cfg.groupBy({ it.RHS }, { it.LHS }).mapValues { it.value.toSet() }
  operator fun get(p: List<Σᐩ>): Set<Σᐩ> = R2LHS[p] ?: emptySet()
  operator fun get(p: Σᐩ): Set<List<Σᐩ>> = L2RHS[p] ?: emptySet()
}

fun CFG.forestHash(s: Σᐩ) = parseForest(s).structureEncode()
fun CFG.nonterminalHash(s: Σᐩ) = s.tokenizeByWhitespace().map { preimage(it) }.hashCode()
fun CFG.preimage(vararg nts: Σᐩ): Set<Σᐩ> = bimap.R2LHS[nts.toList()] ?: emptySet()

fun CFG.dependencyGraph() = LabeledGraph { forEach { prod -> prod.second.forEach { rhs -> prod.LHS - rhs } } }