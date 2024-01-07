package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.automata.*
import ai.hypergraph.kaliningraph.types.*
import ai.hypergraph.kaliningraph.types.times
import kotlin.time.TimeSource

/**
 * Much faster version of [intersectLevFSA] that leverages parallelism to construct
 * the intersection grammar since we are on the JVM, resulting in a ~10x speedup.
 */

infix fun CFG.jvmIntersectLevFSA(fsa: FSA): CFG =
  subgrammar(fsa.alphabet)
//    .also { it.forEach { println("${it.LHS} -> ${it.RHS.joinToString(" ")}") } }
    .intersectLevFSAP(fsa)

private infix fun CFG.intersectLevFSAP(fsa: FSA): CFG {
  var clock = TimeSource.Monotonic.markNow()
  val lengthBoundsCache = lengthBounds
  fun Π3A<STC>.isCompatibleWith(nts: Triple<Σᐩ, Σᐩ, Σᐩ>): Boolean {
    fun Pair<Int, Int>.dominates(other: Pair<Int, Int>) =
      first <= other.first && second <= other.second

    fun manhattanDistance(first: Pair<Int, Int>, second: Pair<Int, Int>): Int =
      second.second - first.second + second.first - first.first

    // Range of the shortest path to the longest path, i.e., Manhattan distance
    fun SPLP(a: STC, b: STC) =
      (fsa.APSP[a.π1 to b.π1] ?: Int.MAX_VALUE)..
          manhattanDistance(a.coords(), b.coords())

    fun IntRange.overlaps(other: IntRange) =
      (other.first in first..last) || (other.last in first..last)

    fun lengthBounds(nt: Σᐩ): IntRange =
      (lengthBoundsCache[nt] ?: -1..-1)
        // Okay if we overapproximate the length bounds a bit
        .let { (it.first - 1)..(it.last + 1) }

    // "[$p,$A,$r] -> [$p,$B,$q] [$q,$C,$r]"
    fun isCompatible() =
      first.coords().dominates(second.coords())
          && second.coords().dominates(third.coords())
          && lengthBounds(nts.first).overlaps(SPLP(first, third))
          && lengthBounds(nts.second).overlaps(SPLP(first, second))
          && lengthBounds(nts.third).overlaps(SPLP(second, third))

    return isCompatible()
  }

  val nts = mutableSetOf("START")
  fun Σᐩ.isSyntheticNT() =
    first() == '[' && last() == ']' && count { it == ',' } == 2
  fun List<Π2<Σᐩ, List<Σᐩ>>>.filterRHSInNTS() =
    asSequence().filter { (_, rhs) -> rhs.all { !it.isSyntheticNT() || it in nts } }

  val initFinal =
    (fsa.init * fsa.final).map { (q, r) -> "START" to listOf("[$q,START,$r]") }
      .filterRHSInNTS()

  val transits =
    fsa.Q.map { (q, a, r) -> "[$q,$a,$r]".also { nts.add(it) } to listOf(a) }
      .filterRHSInNTS()

  // For every production A → σ in P, for every (p, σ, q) ∈ Q × Σ × Q
  // such that δ(p, σ) = q we have the production [p, A, q] → σ in P′.
  val unitProds = unitProdRules(fsa)
    .onEach { (a, _) -> nts.add(a) }.filterRHSInNTS()

  // For each production A → BC in P, for every p, q, r ∈ Q,
  // we have the production [p,A,r] → [p,B,q] [q,C,r] in P′.
  val prods: Set<Production> = nonterminalProductions
  val binaryProds = prods.parallelStream().map {
//      if (i % 100 == 0) println("Finished ${i}/${nonterminalProductions.size} productions")
      val triples = fsa.stateCoords * fsa.stateCoords * fsa.stateCoords
      val (A, B, C) = it.π1 to it.π2[0] to it.π2[1]
      triples
        // CFG ∩ FSA - in general we are not allowed to do this, but it works
        // because we assume a Levenshtein FSA, which is monotone and acyclic.
        .filter { it.isCompatibleWith(A to B to C) }
        .map { (a, b, c) ->
          val (p, q, r)  = a.π1 to b.π1 to c.π1
          "[$p,$A,$r]".also { nts.add(it) } to listOf("[$p,$B,$q]", "[$q,$C,$r]")
        }.toList()
    }.toList().flatten().filterRHSInNTS()

  println("Constructing ∩-grammar took: ${clock.elapsedNow()}")
  clock = TimeSource.Monotonic.markNow()
  return (initFinal + transits + binaryProds + unitProds).toSet().postProcess()
    .also { println("Postprocessing took ${clock.elapsedNow()}") }
}