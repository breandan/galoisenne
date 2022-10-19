package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.cache.LRUCache
import ai.hypergraph.kaliningraph.formatAsGrid
import ai.hypergraph.kaliningraph.graphs.LabeledGraph
import ai.hypergraph.kaliningraph.sampling.choose
import ai.hypergraph.kaliningraph.tensor.FreeMatrix
import ai.hypergraph.kaliningraph.types.*
import kotlin.jvm.JvmName

@Suppress("NonAsciiCharacters")
typealias Σᐩ = String
typealias Production = Π2<Σᐩ, List<Σᐩ>>
typealias CFG = Set<Production>

val Production.LHS: Σᐩ get() = first
val Production.RHS: List<Σᐩ> get() =
  second.let { if (it.size == 1) it.map(Σᐩ::stripEscapeChars) else it }
fun Production.pretty(): Σᐩ = LHS + " -> " + RHS.joinToString(" ")

val CFG.language: CFL by cache { CFL(this) }
val CFG.delimiters: Array<Σᐩ> by cache { (terminals.sortedBy { -it.length } + arrayOf("_", " ")).toTypedArray() }
val CFG.nonterminals: Set<Σᐩ> by cache { map { it.LHS }.toSet() }
val CFG.symbols: Set<Σᐩ> by cache { nonterminals + flatMap { it.RHS } }
val CFG.terminals: Set<Σᐩ> by cache { symbols - nonterminals }
val CFG.terminalUnitProductions: Set<Production>
    by cache { filter { it.RHS.size == 1 && it.RHS[0] !in nonterminals }.toSet() }
val CFG.unitProductions: Set<Production> by cache { filter { it.RHS.size == 1 }.toSet() }
val CFG.nonterminalProductions: Set<Production> by cache { filter { it !in terminalUnitProductions } }
val CFG.bimap: BiMap by cache { BiMap(this) }
val CFG.tmap by cache { terminals.associateBy { bimap[listOf(it)] } }
val CFG.bindex: Bindex by cache { Bindex(this) }
val CFG.joinMap: JoinMap by cache { JoinMap(this) }
val CFG.normalForm: CFG by cache { normalize() }
val CFG.pretty: FreeMatrix<Σᐩ> by cache { map { it.pretty() }.formatAsGrid(3) }
val CFG.graph: LabeledGraph by cache { toGraph() }
val CFG.originalForm: CFG by cache { rewriteHistory[this]!![0] }
val CFG.nonparametricForm: CFG by cache { rewriteHistory[this]!![1] }
//val CFG.originalForm by cache { rewriteHistory[this]!![0] }
//val CFG.nonparametricForm by cache { rewriteHistory[this]!![1] }
val CFG.reachability by cache { mutableMapOf<Σᐩ, Set<Σᐩ>>() }
val CFG.noNonterminalStubs: CFG by cache {
  println("Disabling nonterminal stubs!")
  filter { it.RHS.none { it.isNonterminalStubIn(this) } }.toSet()
    .also { rewriteHistory.put(it, rewriteHistory[this]!! + listOf(this)) }
    .also { it.blocked.addAll(blocked) }
}

class JoinMap(val CFG: CFG) {
  // TODO: Doesn't appear to confer any significant speedup? :/
  val precomputedJoins: MutableMap<Π2A<Set<Σᐩ>>, Set<Π3A<Σᐩ>>> =
    CFG.nonterminals.choose(1..3).let { it * it }
      .associateWith { subsets -> subsets.let { (l, r) -> join(l, r) } }
      .also { println("Precomputed join map has ${it.size} entries.") }.toMutableMap()

  fun join(l: Set<Σᐩ>, r: Set<Σᐩ>, tryCache: Boolean = false): Set<Π3A<Σᐩ>> =
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
class Bindex(CFG: CFG) {
  val indexedNTs: Array<Σᐩ> by cache { CFG.nonterminals.toTypedArray() }
  val ntIndices: Map<Σᐩ, Int> by cache { indexedNTs.zip(indexedNTs.indices).toMap() }
  operator fun get(i: Int) = indexedNTs[i]
  operator fun get(s: Σᐩ) = ntIndices[s]!!
}
// Maps variables to expansions and expansions to variables in a grammar
class BiMap(CFG: CFG) {
  val L2RHS = CFG.groupBy({ it.LHS }, { it.RHS }).mapValues { it.value.toSet() }
  val R2LHS = CFG.groupBy({ it.RHS }, { it.LHS }).mapValues { it.value.toSet() }
  operator fun get(p: List<Σᐩ>): Set<Σᐩ> = R2LHS[p] ?: emptySet()
  operator fun get(p: Σᐩ): Set<List<Σᐩ>> = L2RHS[p] ?: emptySet()
}

fun CFG.prettyPrint(): Σᐩ = pretty.toString()

fun CFG.toGraph() = LabeledGraph { forEach { prod -> prod.second.forEach { rhs -> prod.LHS - rhs } } }

//=====================================================================================
// CFG Normalization
// http://firsov.ee/cert-norm/cfg-norm.pdf
// https://www.cs.rit.edu/~jmg/courses/cs380/20051/slides/7-1-chomsky.pdf
// https://user.phil-fak.uni-duesseldorf.de/~kallmeyer/Parsing/cyk.pdf#page=21

val rewriteHistory = LRUCache<CFG, List<CFG>>()

private fun CFG.normalize(): CFG =
  mutableListOf<CFG>().let { rewrites ->
    addGlobalStartSymbol()
      .expandOr()
      .also { rewrites.add(it) } /** [originalForm] */
      .eliminateParametricityFromLHS()
      .also { rewrites.add(it) } /** [nonparametricForm] */
      .transformIntoCNF()
      // This should occur after CNF transform otherwise it causes issues
      // during nonterminal-constrained synthesis, e.g., _ _ _ <NT> _ _ _
      // because we do not use equivalence class during bitvector encoding
      .generateNonterminalStubs()
      .also { cnf -> rewriteHistory.put(cnf, rewrites) }
  }

fun CFG.transformIntoCNF(): CFG =
  addEpsilonProduction()
    .refactorEpsilonProds()
    .elimVarUnitProds()
    .refactorRHS()
    .terminalsToUnitProds()
    .removeUselessSymbols()

val START_SYMBOL = "START"

fun Σᐩ.getParametersIn(cfg: CFG) =
  cfg.unitProductions.map { it.LHS }.filter { "<$it>" in this }

fun CFG.eliminateParametricityFromRHS(
  ntReplaced: Σᐩ,
  ntReplacements: Set<Σᐩ>
): CFG =
  if (ntReplacements.isEmpty()) this
  else flatMap { prod ->
    if (prod.RHS.none { ntReplaced in it }) listOf(prod)
    else ntReplacements.map { ntr ->
      (prod.LHS to prod.RHS.map { if (it == ntReplaced) ntr else it })
    }
  }.toSet()

fun CFG.eliminateParametricityFromLHS(
  parameters: Set<Σᐩ> =
    nonterminals.flatMap { it.getParametersIn(this) }.toSet()
): CFG =
  if (parameters.isEmpty()) this else {
    var i = false
    var (ntReplaced, ntReplacements) = "" to setOf<Σᐩ>()
    flatMap { prod ->
      val params = prod.LHS.getParametersIn(this)
      if (params.isEmpty() || i) return@flatMap listOf(prod)
      i = true
      ntReplaced = prod.LHS
      val map = params.associateWith { bimap[it].map { it[0] }.toSet() }
      val (s, r) = map.entries.maxByOrNull { it.value.size }!!
      r.map { rc ->
        prod.LHS.replace("<$s>", "<$rc>").also { ntReplacements += it } to
          prod.RHS.map { it.replace("<$s>", "<$rc>") }
      }
    }.toSet()
      .eliminateParametricityFromRHS(ntReplaced, ntReplacements)
      .eliminateParametricityFromLHS()
  }

fun CFG.generateNonterminalStubs(): CFG =
  this + (filter { it.LHS.split(".").size == 1 && "ε" !in it.LHS && it.LHS != "START" }
    .map { it.LHS to listOf("<${it.LHS}>") }.toSet()).addEpsilonProduction()

// Add start symbol if none are present (e.g., in case the user forgets)
private fun CFG.addGlobalStartSymbol(): CFG =
  this + if (START_SYMBOL in nonterminals) emptySet()
  else nonterminals.map { START_SYMBOL to listOf(it) }

// Expands RHS `|` productions, e.g., (A -> B | C) -> (A -> B, A -> C)
private fun CFG.expandOr(): CFG =
  flatMap { prod ->
    prod.RHS.fold(listOf(listOf<Σᐩ>())) { acc, s ->
      if (s == "|") (acc + listOf(listOf()))
      else (acc.dropLast(1) + listOf(acc.last() + s))
    }.map { prod.LHS to it }
  }.toSet()

// Adds V -> εV | Vε to every unit production [V -> v] in CFG
// so that holes can be [optionally] elided by the SAT solver.
private fun CFG.addEpsilonProduction(): CFG =
  terminalUnitProductions.filterNot { "ε" in it.pretty() }.map { it.LHS }.toSet()
    .fold(this) { acc, it -> acc + (it to listOf(it, "ε+")) + (it to listOf("ε+", it))} +
    "ε+".let { (it to listOf(it, it)) } + ("ε+" to listOf("ε"))

// http://firsov.ee/cert-norm/cfg-norm.pdf#subsection.3.1
tailrec fun CFG.nullableNonterminals(
  nbls: Set<Σᐩ> = setOf("ε"),
  nnts: Set<Σᐩ> = filter { nbls.containsAll(it.RHS) }.map { it.LHS }.toSet()
): Set<Σᐩ> = if (nnts == (nbls - "ε")) nnts else nullableNonterminals(nnts + nbls)

fun List<Σᐩ>.drop(nullables: Set<Σᐩ>, keep: Set<Int>): List<Σᐩ> =
  mapIndexedNotNull { i, s ->
    if (s in nullables && i !in keep) null
    else if (s in nullables && i in keep) s
    else s
  }

// http://firsov.ee/cert-norm/cfg-norm.pdf#subsection.3.2
fun Production.allSubSeq(nullables: Set<Σᐩ>): Set<Production> =
    RHS.indices.filter { RHS[it] in nullables }.powerset().toSet()
    .map { idxs -> LHS to RHS.drop(nullables, idxs) }.toSet()

/**
 * Makes ε-productions optional. n.b. We do not use CNF, but almost-CNF!
 * ε-productions are allowed, because want to be able to synthesize them
 * as special characters, then simply omit them during printing.
 *
 *  - Determine nullable variables, i.e., those which contain ε on the RHS
 *  - For each production omit every possible subset of nullable variables,
 *      e.g., (P -> AxB, A -> ε, B -> ε) -> (P -> xB, P -> Ax, P -> x)
 *  - Remove all productions with an empty RHS
 */

fun CFG.refactorEpsilonProds(nlbls: Set<Σᐩ> = nullableNonterminals()): CFG =
  (this + setOf(START_SYMBOL to listOf(START_SYMBOL, "ε")))
    .flatMap { p -> if (p.RHS.any { it in nlbls }) p.allSubSeq(nlbls) else listOf(p) }
    .filter { it.RHS.isNotEmpty() }.toSet()

/**
 * Eliminate all non-generating and unreachable symbols.
 *
 * All terminal-producing symbols are generating.
 * If A -> [..] and all symbols in [..] are generating, then A is generating
 * No other symbols are generating.
 *
 * START is reachable.
 * If S -> [..] is reachable, then all variables in [..] are reachable.
 * No other symbols are reachable.
 *
 * A useful symbol is both generating and reachable.
 */

private fun CFG.removeUselessSymbols(
  generating: Set<Σᐩ> = generatingSymbols(),
  reachable: Set<Σᐩ> = reachableSymbols(),
): CFG = filter { (s, _) -> s in generating && s in reachable }

// Equivalence class of an NT B are all NTs, A ->* B ->* C
// reachable via unit productions (in forward or reverse)
fun CFG.equivalenceClass(from: Set<Σᐩ>): Set<Σᐩ> =
  from.intersect(nonterminals).let { nts ->
    nts.flatMap {
      bimap[it].filter { it.size == 1 }.flatten() + bimap[listOf(it)]
    }.intersect(nonterminals).let {
      val ec = nts + it
      if (ec == nts) ec else equivalenceClass(ec)
    }
  }

fun CFG.reachableSymbols(from: Σᐩ = START_SYMBOL): Set<Σᐩ> =
  reachability.getOrPut(from) {
    graph.transitiveClosure(setOf(graph.first { it.label == from }))
      .map { it.label }.filter { it in nonterminals }.toSet()
  }

private fun CFG.generatingSymbols(
  from: Set<Σᐩ> = terminalUnitProductions.map { it.LHS }.toSet(),
  revGraph: LabeledGraph = graph.reversed()
): Set<Σᐩ> =
  revGraph.transitiveClosure(revGraph.filter { it.label in from }.toSet())
    .map { it.label }.toSet()

/* Drops variable unit productions, for example:
 * Initial grammar: (A -> B, B -> c, B -> d) ->
 * After expansion: (A -> B, A -> c, A -> d, B -> c, B -> d) ->
 * After elimination: (A -> c, A -> d, B -> c, B -> d)
 */
private tailrec fun CFG.elimVarUnitProds(
  toVisit: Set<Σᐩ> = nonterminals,
  vars: Set<Σᐩ> = nonterminals,
  toElim: Σᐩ? = toVisit.firstOrNull()
): CFG {
  fun Production.isVariableUnitProd() = RHS.size == 1 && RHS[0] in vars
  if (toElim == null) return filter { !it.isVariableUnitProd() }
  val varsThatMapToMe =
    filter { it.RHS.size == 1 && it.RHS[0] == toElim }.map { it.LHS }.toSet()
  val thingsIMapTo = filter { it.LHS == toElim }.map { it.RHS }.toSet()
  return (varsThatMapToMe * thingsIMapTo).fold(this) { g, p -> g + p }
    .elimVarUnitProds(toVisit.drop(1).toSet(), vars)
}

// Refactors long productions, e.g., (A -> BCD) -> (A -> BE, E -> CD)
private tailrec fun CFG.refactorRHS(): CFG {
  val longProd = firstOrNull { it.RHS.size > 2 } ?: return this
  val freshName = longProd.RHS.takeLast(2).joinToString(".")
  val newProd = freshName to longProd.RHS.takeLast(2)
  val shortProd = longProd.LHS to (longProd.RHS.dropLast(2) + freshName)
  val newGrammar = this - longProd + shortProd + newProd
  return if (this == newGrammar) this else newGrammar.refactorRHS()
}

// Replaces terminals in non-unit productions, e.g., (A -> bC) -> (A -> BC, B -> b)
private tailrec fun CFG.terminalsToUnitProds(): CFG {
  val mixProd = nonterminalProductions.firstOrNull { it.RHS.any { it !in nonterminals } } ?: return this
  val termIdx = mixProd.RHS.indexOfFirst { it !in nonterminals }
  val freshName = "F." + mixProd.RHS[termIdx]
  val freshRHS = mixProd.RHS.toMutableList().also { it[termIdx] = freshName }
  val newProd = freshName to listOf(mixProd.RHS[termIdx])
  val newGrammar = this - mixProd + (mixProd.LHS to freshRHS) + newProd
  return if (this == newGrammar) this else newGrammar.terminalsToUnitProds()
}