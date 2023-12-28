package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.types.*
import kotlin.time.TimeSource

infix fun FSA.intersectLevFSA(cfg: CFG) = cfg.intersectLevFSA(this)
// http://www.cs.umd.edu/~gasarch/BLOGPAPERS/cfg.pdf#page=2
// https://browse.arxiv.org/pdf/2209.06809.pdf#page=5

infix fun CFG.intersectLevFSA(fsa: FSA): CFG = freeze().intersectLevFSAP(fsa)

fun CFG.makeLevGrammar(source: List<Σᐩ>, distance: Int) =
  intersectLevFSA(makeLevFSA(source, distance, terminals))

fun CFG.barHillelRepair(prompt: List<Σᐩ>, distance: Int) =
  makeLevGrammar(prompt, distance).enumSeq(List(prompt.size + distance) { "_" })

private infix fun CFG.intersectLevFSAP(fsa: FSA): CFG {
  var clock = TimeSource.Monotonic.markNow()
  val initFinal =
    (fsa.init * fsa.final).map { (q, r) -> "START" to listOf("[$q,START,$r]") }

  val transits =
    fsa.Q.map { (q, a, r) -> "[$q,$a,$r]" to listOf(a) }

  fun Triple<Σᐩ, Σᐩ, Σᐩ>.isCompatibleWith(nts: Triple<Σᐩ, Σᐩ, Σᐩ>): Boolean {
    fun Σᐩ.coords(): Pair<Int, Int> =
      (length / 2 - 1).let { substring(2, it + 2).toInt() to substring(it + 3).toInt() }

    fun Pair<Int, Int>.dominates(other: Pair<Int, Int>) =
      first <= other.first && second <= other.second

    fun manhattanDistance(first: Pair<Int, Int>, second: Pair<Int, Int>): Int =
      second.second - first.second + second.first - first.first

    // Range of the shortest path to the longest path, i.e., Manhattan distance
    fun SPLP(a: Σᐩ, b: Σᐩ) =
      (fsa.APSP[a to b] ?: Int.MAX_VALUE)..
        manhattanDistance(a.coords(), b.coords())

    fun IntRange.overlaps(other: IntRange) =
      (other.first in first..last) || (other.last in first..last)

    fun lengthBounds(nt: Σᐩ): IntRange = lengthBounds[nt] ?: -1..-1

    // "[$p,$A,$r] -> [$p,$B,$q] [$q,$C,$r]"
    fun isCompatible() =
      first.coords().dominates(second.coords())
        && second.coords().dominates(third.coords())
        && lengthBounds(nts.first).overlaps(SPLP(first, third))
        && lengthBounds(nts.second).overlaps(SPLP(first, second))
        && lengthBounds(nts.third).overlaps(SPLP(second, third))

    return isCompatible()
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
        .filter { it.isCompatibleWith(A to B to C) }
        .map { (p, q, r) -> "[$p,$A,$r]" to listOf("[$p,$B,$q]", "[$q,$C,$r]") }
    }.flatten()

  println("Constructing ∩-grammar took: ${clock.elapsedNow()}")
  clock = TimeSource.Monotonic.markNow()
  return (initFinal + transits + binaryProds + unitProds).toSet().postProcess()
    .also { println("Postprocessing took ${clock.elapsedNow()}") }
}

private fun CFG.unitProdRules(fsa: FSA) =
  unitProductions.map { (A, rhs) ->
    val relevantTransits = fsa.Q.filter { it.π2 == rhs[0] }
    relevantTransits.map { (p, σ, q) -> "[$p,$A,$q]" to listOf(σ) }
  }.flatten()

fun CFG.postProcess() =
    this.also { println("∩-grammar has ${it.size} total productions") }
    .dropVestigialProductions().normalForm.noNonterminalStubs
    .also { println("∩-grammar has ${it.size} useful productions") }
    .freeze()
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
    .freeze().removeUselessSymbols()

  println("Removed ${size - rw.size} vestigial productions, resulting in ${rw.size} productions.")

  return if (rw.size == size) this else rw.dropVestigialProductions(criteria)
}

infix fun FSA.intersect(cfg: CFG) = cfg.intersect(this)

infix fun CFG.intersect(fsa: FSA): CFG {
  val clock = TimeSource.Monotonic.markNow()
  val initFinal =
    (fsa.init * fsa.final).map { (q, r) -> "START" to listOf("[$q,START,$r]") }

  val transits =
    fsa.Q.map { (q, a, r) -> "[$q,$a,$r]" to listOf(a) }

  // For every production A → σ in P, for every (p, σ, q) ∈ Q × Σ × Q
  // such that δ(p, σ) = q we have the production [p, A, q] → σ in P′.
  val unitProds = unitProdRules(fsa)

  // For each production A → BC in P, for every p, q, r ∈ Q,
  // we have the production [p,A,r] → [p,B,q] [q,C,r] in P′.
  val binaryProds =
    nonterminalProductions.map {
      val triples = fsa.states * fsa.states * fsa.states
      val (A, B, C) = it.π1 to it.π2[0] to it.π2[1]
      triples.map { (p, q, r) ->
        "[$p,$A,$r]" to listOf("[$p,$B,$q]", "[$q,$C,$r]")
      }
    }.flatten()

  return (initFinal + transits + binaryProds + unitProds).toSet().postProcess()
    .also { println("Postprocessing took ${clock.elapsedNow()}") }
}

// Tracks the number of tokens a given nonterminal can represent
// e.g., a NT with a bound of 1..3 can parse { s: Σ^[1, 3] }
val CFG.lengthBounds: Map<Σᐩ, IntRange> by cache {
  val clock = TimeSource.Monotonic.markNow()
  val epsFree = noEpsilonOrNonterminalStubs.freeze()
  val tpl = List(20) { "_" }
  val map =
    epsFree.nonterminals.associateWith { -1..-1 }.toMutableMap()
  epsFree.initPForestMat(tpl).seekFixpoint().diagonals.mapIndexed { idx, sets ->
    sets.flatMap { it.map { it.key } }.forEach { nt ->
      map[nt]?.let {
        (if (it.first < 0) (idx + 1) else it.first)..(idx + 1)
      }?.let { map[nt] = it }
    }
  }

  println("Computed NT length bounds in ${clock.elapsedNow()}")
  map
}