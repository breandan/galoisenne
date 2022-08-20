package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.formatAsGrid
import ai.hypergraph.kaliningraph.graphs.LabeledGraph
import ai.hypergraph.kaliningraph.sampling.choose
import ai.hypergraph.kaliningraph.types.*
import kotlin.jvm.JvmName

typealias Production = Π2<String, List<String>>
typealias CFG = Set<Production>

val Production.LHS: String get() = first
val Production.RHS: List<String> get() =
  second.let { if(it.size == 1) it.map(String::stripEscapeChars) else it }
fun Production.pretty() = LHS + " -> " + RHS.joinToString(" ")

val CFG.delimiters: Array<String> by cache { (terminals.sortedBy { -it.length } + arrayOf("_", " ")).toTypedArray() }
val CFG.nonterminals: Set<String> by cache { map { it.LHS }.toSet() }
val CFG.symbols: Set<String> by cache { nonterminals + flatMap { it.RHS } }
val CFG.terminals: Set<String> by cache { symbols - nonterminals }
val CFG.terminalUnitProductions: Set<Production>
    by cache { filter { it.RHS.size == 1 && it.RHS[0] !in nonterminals }.toSet() }
val CFG.nonterminalProductions: Set<Production> by cache { filter { it !in terminalUnitProductions } }
val CFG.bimap: BiMap by cache { BiMap(this) }
val CFG.bindex: Bindex by cache { Bindex(this) }
val CFG.joinMap: JoinMap by cache { JoinMap(this) }
val CFG.normalForm: CFG by cache { normalize() }
val CFG.pretty by cache { map { it.pretty() }.formatAsGrid(4) }
val CFG.graph by cache { toGraph() }

class JoinMap(val CFG: CFG) {
  // TODO: Doesn't appear to confer any significant speedup? :/
  val precomputedJoins: MutableMap<Pair<Set<String>, Set<String>>, Set<Triple<String, String, String>>> =
    CFG.nonterminals.choose(1..3).let { it * it }
      .associateWith { subsets -> subsets.let { (l, r) -> join(l, r) } }
      .also { println("Precomputed join map has ${it.size} entries.") }
      .toMutableMap()

  fun join(l: Set<String>, r: Set<String>, tryCache: Boolean = false): Set<Triple<String, String, String>> =
    if (tryCache) precomputedJoins[l to r] ?: join(l, r, false).also { precomputedJoins[l to r] = it }
    else (l * r).flatMap { (l, r) -> CFG.bimap[listOf(l, r)].map { Triple(it, l, r) } }.toSet()

  @JvmName("setJoin")
  operator fun get(l: Set<String>, r: Set<String>): Set<String> =
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
  val indexedNTs: Array<String> by cache { CFG.nonterminals.toTypedArray() }
  val ntIndices: Map<String, Int> by cache { indexedNTs.zip(indexedNTs.indices).toMap() }
  operator fun get(i: Int) = indexedNTs[i]
  operator fun get(s: String) = ntIndices[s]!!
}
// Maps variables to expansions and expansions to variables in a Grammar
class BiMap(CFG: CFG) {
  val L2RHS = CFG.groupBy({ it.LHS }, { it.RHS }).mapValues { it.value.toSet() }
  val R2LHS = CFG.groupBy({ it.RHS }, { it.LHS }).mapValues { it.value.toSet() }
  operator fun get(p: List<String>): Set<String> = R2LHS[p] ?: emptySet()
  operator fun get(p: String): Set<List<String>> = L2RHS[p] ?: emptySet()
}

fun CFG.prettyPrint(cols: Int = 4): String = pretty.toString()

fun CFG.toGraph() =
  LabeledGraph { forEach { prod -> prod.second.forEach { rhs -> prod.LHS - rhs } } }

//=====================================================================================
// CFG Normalization
// http://firsov.ee/cert-norm/cfg-norm.pdf
// https://www.cs.rit.edu/~jmg/courses/cs380/20051/slides/7-1-chomsky.pdf
// https://user.phil-fak.uni-duesseldorf.de/~kallmeyer/Parsing/cyk.pdf#page=21

private fun CFG.normalize(): CFG =
  addGlobalStartSymbol()
    .expandOr()
    .addEpsilonProduction()
    .refactorEpsilonProds()
    .elimVarUnitProds()
    .refactorRHS()
    .terminalsToUnitProds()
    .removeUselessSymbols()

// Xujie's algorithm - it works! :-D
fun Tree.denormalize(): Tree {
  fun Tree.removeSynthetic(
    refactoredChildren: List<Tree> = children.map { it.removeSynthetic() }.flatten(),
    isSynthetic: (Tree) -> Boolean = { 2 <= root.split('.').size }
  ): List<Tree> =
    if (children.isEmpty()) listOf(Tree(root, terminal, span = span))
    else if (isSynthetic(this)) refactoredChildren
    else listOf(Tree(root, children = refactoredChildren.toTypedArray(), span = span))

  return removeSynthetic().first()
}
val START_SYMBOL = "START"

fun CFG.generateStubs() =
  this + filter { it.LHS.split(".").size == 1 }
    .map { it.LHS to listOf("<${it.LHS}>") }.toSet()

// Add start symbol if none are present (e.g., in case the user forgets)
private fun CFG.addGlobalStartSymbol() = this +
  if (START_SYMBOL in nonterminals) emptySet()
  else nonterminals.map { START_SYMBOL to listOf(it) }

// Expands RHS `|` productions, e.g., (A -> B | C) -> (A -> B, A -> C)
private fun CFG.expandOr(): CFG =
  flatMap { prod ->
    prod.RHS.fold(listOf(listOf<String>())) { acc, s ->
      if (s == "|") (acc + listOf(listOf()))
      else (acc.dropLast(1) + listOf(acc.last() + s))
    }.map { prod.LHS to it }
  }.toSet()

// Adds V -> εV | Vε to every production [V -> v] in CFG so
// that holes can be [optionally] elided by the SAT solver.
private fun CFG.addEpsilonProduction(): CFG =
  map { it.LHS }.toSet().fold(this) { acc, it -> acc + (it to listOf(it, "ε")) }

// http://firsov.ee/cert-norm/cfg-norm.pdf#subsection.3.1
tailrec fun CFG.nullableNonterminals(
  nbls: Set<String> = setOf("ε"),
  nnts: Set<String> = filter { nbls.containsAll(it.RHS) }.map { it.LHS }.toSet()
): Set<String> = if (nnts == (nbls - "ε")) nnts else nullableNonterminals(nnts + nbls)

fun List<String>.drop(nullables: Set<String>, keep: Set<Int>): List<String> =
  mapIndexedNotNull { i, s ->
    if (s in nullables && i !in keep) null
    else if (s in nullables && i in keep) s
    else s
  }

// http://firsov.ee/cert-norm/cfg-norm.pdf#subsection.3.2
fun Production.allSubSeq(
  nullables: Set<String>,
  indices: Set<Set<Int>> = RHS.indices.filter { RHS[it] in nullables }.powerset().toSet()
): Set<Production> = indices.map { idxs -> LHS to RHS.drop(nullables, idxs) }.toSet()

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

fun CFG.refactorEpsilonProds(nlbls: Set<String> = nullableNonterminals()): CFG =
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
  generating: Set<String> = generatingSymbols(),
  reachable: Set<String> = reachableSymbols(),
): CFG = filter { (s, _) -> s in generating && s in reachable }

private fun CFG.reachableSymbols(): Set<String> =
  graph.transitiveClosure(setOf(graph.first { it.label == START_SYMBOL }))
    .map { it.label }.filter { it in nonterminals }.toSet()

private fun CFG.generatingSymbols(
  from: Set<String> = terminalUnitProductions.map { it.LHS }.toSet(),
  revGraph: LabeledGraph = graph.reversed()
): Set<String> =
  revGraph.transitiveClosure(revGraph.filter { it.label in from }.toSet())
    .map { it.label }.toSet()

/* Drops variable unit productions, for example:
 * Initial grammar: (A -> B, B -> c, B -> d) ->
 * After expansion: (A -> B, A -> c, A -> d, B -> c, B -> d) ->
 * After elimination: (A -> c, A -> d, B -> c, B -> d)
 */
private tailrec fun CFG.elimVarUnitProds(
  toVisit: Set<String> = nonterminals,
  vars: Set<String> = nonterminals,
  toElim: String? = toVisit.firstOrNull()
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