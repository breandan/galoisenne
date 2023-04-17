package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.cache.LRUCache
import ai.hypergraph.kaliningraph.graphs.LabeledGraph
import ai.hypergraph.kaliningraph.types.*

//=====================================================================================
// CFG Normalization
// http://firsov.ee/cert-norm/cfg-norm.pdf
// https://www.cs.rit.edu/~jmg/courses/cs380/20051/slides/7-1-chomsky.pdf
// https://user.phil-fak.uni-duesseldorf.de/~kallmeyer/Parsing/cyk.pdf#page=21

val rewriteHistory = LRUCache<CFG, List<CFG>>()

fun CFG.normalize(): CFG =
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
  this + (filter { it.LHS.isOrganicNonterminal()  }
    .map { it.LHS to listOf("<${it.LHS}>") }.toSet()).addEpsilonProduction()

fun Σᐩ.isSyntheticNonterminal() =
  split(".").size != 1 || "ε" in this || this == "START"
fun Σᐩ.isOrganicNonterminal() =
  split(".").size == 1 && "ε" !in this && this != "START"

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
    .fold(this) { acc, it -> acc + (it to listOf(it, "ε+")) + (it to listOf("ε+", it)) } +
    ("ε+" to listOf("ε+", "ε+")) + ("ε+" to listOf("ε"))

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
fun CFG.equivalenceClass(from: Σᐩ): Set<Σᐩ> = reachableSymbolsViaUnitProds(from)

fun LabeledGraph.transitiveClosure(from: Set<Σᐩ>) =
  transitiveClosure(filter { it.label in from }).map { it.label }.toSet()

fun CFG.reachableSymbolsViaUnitProds(from: Σᐩ = START_SYMBOL): Set<Σᐩ> =
  unitReachability.getOrPut(from) {
    LabeledGraph {
      unitProductions.map { it.LHS to it.RHS.first() }
//      .filter { (a, b) -> nonterminals.containsAll(listOf(a, b)) }
        .forEach { (a, b) -> a - b }
    }.let {
      setOf(from) + (it.transitiveClosure(setOf(from)) +
        it.reversed().transitiveClosure(setOf(from)))
    }.filter { it in nonterminals }
  }

fun CFG.reachableSymbols(from: Σᐩ = START_SYMBOL): Set<Σᐩ> =
  reachability.getOrPut(from) { graph.transitiveClosure(setOf(from)) }

fun CFG.generatingSymbols(
  from: Set<Σᐩ> = terminalUnitProductions.map { it.LHS }.toSet(),
  revGraph: LabeledGraph = graph.reversed()
): Set<Σᐩ> = revGraph.transitiveClosure(from)

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