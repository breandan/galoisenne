package ai.hypergraph.kaliningraph.parsing

import NUM_CORES
import ai.hypergraph.kaliningraph.*
import ai.hypergraph.kaliningraph.automata.*
import ai.hypergraph.kaliningraph.repair.minimizeFix
import ai.hypergraph.kaliningraph.types.*
import ai.hypergraph.kaliningraph.types.times
import kotlin.streams.asSequence
import kotlin.time.TimeSource

fun CFG.parallelEnumSeqMinimalWOR(
  prompt: List<String>,
  tokens: List<String>,
  stoppingCriterion: () -> Boolean = { true }
): Sequence<String> =
  startPTree(prompt)?.let {
    (0..<NUM_CORES).toList().parallelStream().map { i ->
      it.sampleStrWithoutReplacement(i)
        .map { it.removeEpsilon() }
        .takeWhile { stoppingCriterion() }
        .distinct()
        .flatMap { minimizeFix(tokens, it.tokenizeByWhitespace()) { this in language } }
        .distinct()
    }.asSequence().flatten()
  } ?: sequenceOf()

fun CFG.parallelEnumSeqMinimalWR(
  prompt: List<String>,
  tokens: List<String>,
  stoppingCriterion: () -> Boolean = { true }
): Sequence<String> =
  startPTree(prompt)?.let {
    (0..<NUM_CORES).toList().parallelStream().map { i ->
      it.sampleWRGD()
        .map { it.removeEpsilon() }
        .takeWhile { stoppingCriterion() }
        .distinct()
        .flatMap { minimizeFix(tokens, it.tokenizeByWhitespace()) { this in language } }
        .distinct()
    }.asSequence().flatten()
  } ?: sequenceOf()

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

  val nts = mutableSetOf("START")
  fun Σᐩ.isSyntheticNT() =
    first() == '[' && last() == ']' && count { it == '~' } == 2
  fun List<Π2<Σᐩ, List<Σᐩ>>>.filterRHSInNTS() =
    parallelStream()
      .filter { (_, rhs) -> rhs.all { !it.isSyntheticNT() || it in nts } }
      .toList().toSet()

  val initFinal =
    (fsa.init  * fsa.final).map { (q, r) -> "START" to listOf("[$q~START~$r]") }

  val transits =
    fsa.Q.map { (q, a, r) -> "[$q,$a,$r]".also { nts.add(it) } to listOf(a) }

  // For every production A → σ in P, for every (p, σ, q) ∈ Q × Σ × Q
  // such that δ(p, σ) = q we have the production [p, A, q] → σ in P′.
  val unitProds = unitProdRules(fsa)
    .onEach { (a, _) -> nts.add(a) }

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
        .filter { it.isCompatibleWith(A to B to C, this@intersectLevFSAP, fsa) }
        .map { (a, b, c) ->
          val (p, q, r)  = a.π1 to b.π1 to c.π1
          "[$p~$A~$r]".also { nts.add(it) } to listOf("[$p~$B~$q]", "[$q~$C~$r]")
        }.toList()
    }.toList().flatten()

  println("Constructing ∩-grammar took: ${clock.elapsedNow()}")
  clock = TimeSource.Monotonic.markNow()
  return (initFinal + transits + binaryProds + unitProds).toList()
    .filterRHSInNTS().postProcess()
    .also { println("Postprocessing took ${clock.elapsedNow()}") }
}