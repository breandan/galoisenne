package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.types.*
import kotlin.time.TimeSource

infix fun FSA.intersectLevFSA(cfg: CFG) = cfg.intersectLevFSA(this)
// http://www.cs.umd.edu/~gasarch/BLOGPAPERS/cfg.pdf#page=2
// https://browse.arxiv.org/pdf/2209.06809.pdf#page=5

infix fun CFG.intersectLevFSA(fsa: FSA): CFG {
  var clock = TimeSource.Monotonic.markNow()
  val initFinal =
    (fsa.init * fsa.final).map { (q, r) -> "START -> [$q,START,$r]" }

  val transits =
    fsa.Q.map { (q, a, r) -> "[$q,$a,$r] -> $a" }

  fun Triple<Σᐩ, Σᐩ, Σᐩ>.isValid(): Boolean {
    fun Σᐩ.coords() =
      substringAfter("_").split("/")
        .let { (i, j) -> i.toInt() to j.toInt() }

    fun Pair<Int, Int>.dominates(other: Pair<Int, Int>) =
      first <= other.first && second <= other.second

    return first.coords().dominates(second.coords()) &&
      second.coords().dominates(third.coords())
  }

  // For every production A → σ in P, for every (p, σ, q) ∈ Q × Σ × Q
  // such that δ(p, σ) = q we have the production [p, A, q] → σ in P′.
  val unitProds = unitProdRules(fsa)

  // For each production A → BC in P, for every p, q, r ∈ Q,
  // we have the production [p,A,r] → [p,B,q] [q,C,r] in P′.
  val binaryProds =
    nonterminalProductions.map {
      val triples = fsa.states * fsa.states * fsa.states
      val (A, B, C) = it.π1 to it.π2[0] to it.π2[1]
      triples
        // CFG ∩ FSA - in general we are not allowed to do this, but it works
        // because we assume a Levenshtein FSA, which is monotone and acyclic.
        .filter { it.isValid() }
        .map { (p, q, r) -> "[$p,$A,$r] -> [$p,$B,$q] [$q,$C,$r]" }
    }.flatten()

  println("Constructing ∩-grammar took: ${clock.elapsedNow().inWholeMilliseconds}ms")
  clock = TimeSource.Monotonic.markNow()
  return (initFinal + transits + binaryProds + unitProds).postProcess()
    .also { println("Postprocessing took ${clock.elapsedNow().inWholeMilliseconds}ms") }
}

private fun CFG.unitProdRules(fsa: FSA) =
  unitProductions.map { (A, rhs) ->
    val relevantTransits = fsa.Q.filter { it.π2 == rhs[0] }
    relevantTransits.map { (p, σ, q) -> "[$p,$A,$q] -> $σ" }
  }.flatten()

fun List<Σᐩ>.postProcess() =
  joinToString("\n").parseCFG(normalize = false)
    .also { println("∩-grammar has ${it.size} total productions") }
    .dropVestigialProductions().normalForm.noNonterminalStubs
    .also { println("∩-grammar has ${it.size} useful productions") }
    //    .also { println(it.pretty) }
    //    .also { println(it.size) }

// Recursively removes all productions from a synthetic CFG containing a
// dangling nonterminal, i.e., a nonterminal that does not produce any terminals
//
// This works but is the most inefficient part of the current implementation...
//
// TODO: Maybe instead of creating an enormous CFG and then removing productions
//       we can just create a CFG that only contains the productions we need, by
//       starting from the terminals and working our way up to START?
//  Consider:
//    ∩-grammar has 96634 total productions
//    Removed 81177 vestigial productions.
//    Removed 15035 vestigial productions.
//    Removed 331 vestigial productions.
//    Removed 57 vestigial productions.
//    Removed 7 vestigial productions.
//    Removed 0 vestigial productions.
//    Disabling nonterminal stubs!
//    ∩-grammar has 56 useful productions <- Why can't we just create this CFG?!
fun CFG.dropVestigialProductions(
  criteria: (Σᐩ) -> Boolean = { it.first() == '[' && it.last() == ']' && it.count { it == ',' } == 2 }
): CFG {
  val nts: Set<Σᐩ> = map { it.LHS }.toSet()
//  val reachable = reachableSymbols()
  val rw = toMutableSet()
    .apply { removeAll { prod -> prod.RHS.any { criteria(it) && it !in nts } } }
    .removeUselessSymbols()

  println("Removed ${size - rw.size} vestigial productions.")

  return if (rw.size == size) this else rw.dropVestigialProductions(criteria)
}

infix fun FSA.intersect(cfg: CFG) = cfg.intersect(this)

infix fun CFG.intersect(fsa: FSA): CFG {
  val clock = TimeSource.Monotonic.markNow()
  val initFinal =
    (fsa.init * fsa.final).map { (q, r) -> "START -> [$q,START,$r]" }

  val transits =
    fsa.Q.map { (q, a, r) -> "[$q,$a,$r] -> $a" }

  // For every production A → σ in P, for every (p, σ, q) ∈ Q × Σ × Q
  // such that δ(p, σ) = q we have the production [p, A, q] → σ in P′.
  val unitProds = unitProdRules(fsa)

  // For each production A → BC in P, for every p, q, r ∈ Q,
  // we have the production [p,A,r] → [p,B,q] [q,C,r] in P′.
  val binaryProds =
    nonterminalProductions.map {
      val triples = fsa.states * fsa.states * fsa.states
      val (A, B, C) = it.π1 to it.π2[0] to it.π2[1]
      triples.map { (p, q, r) -> "[$p,$A,$r] -> [$p,$B,$q] [$q,$C,$r]" }
    }.flatten()

  return (initFinal + transits + binaryProds + unitProds).postProcess()
    .also { println("Postprocessing took ${clock.elapsedNow().inWholeMilliseconds}ms") }
}