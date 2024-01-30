package ai.hypergraph.kaliningraph.parsing

import NUM_CORES
import ai.hypergraph.kaliningraph.*
import ai.hypergraph.kaliningraph.automata.*
import ai.hypergraph.kaliningraph.repair.minimizeFix
import ai.hypergraph.kaliningraph.types.*
import ai.hypergraph.kaliningraph.types.times
import java.util.concurrent.*
import java.util.stream.*
import kotlin.streams.*
import kotlin.time.Duration.Companion.minutes
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

fun CFG.parallelEnumSeqWR(
  prompt: List<String>,
  cores: Int = NUM_CORES,
  stoppingCriterion: () -> Boolean = { true }
): Sequence<String> =
  startPTree(prompt)?.let {
    (0..<cores).toList().parallelStream().map { i ->
      it.sampleWRGD()
        .map { it.removeEpsilon() }
        .takeWhile { stoppingCriterion() }
        .distinct()
    }.asSequence().flatten()
  } ?: sequenceOf()

fun CFG.jvmSample(
  fastMap: Map<Σᐩ, Collection<List<Σᐩ>>>,
  from: Σᐩ = START_SYMBOL,
): String {
  val children = fastMap[from]
  if (children.isNullOrEmpty()) { return if ("ε" in from) "" else from }
  else {
    val child = children.random()
    return if (child.size == 1) jvmSample(fastMap, child.first())
    else child.map { jvmSample(fastMap, it) }.filter { it.isNotEmpty() }.joinToString(" ")
  }
}

fun CFG.sampleDirectlyWR(
  cores: Int = NUM_CORES,
  stoppingCriterion: () -> Boolean = { true },
  fastMap: ConcurrentHashMap<Σᐩ, ConcurrentLinkedQueue<List<Σᐩ>>> =
    asSequence().asStream().parallel().collect(
      { ConcurrentHashMap<Σᐩ, ConcurrentLinkedQueue<List<Σᐩ>>>() },
      { map, (l, r) -> map.getOrPut(l) { ConcurrentLinkedQueue() }.add(r) },
      { map1, map2 -> map1.putAll(map2) }
    ),
): Sequence<String> =
  (0..<cores).toList().parallelStream().map { i ->
    generateSequence { jvmSample(fastMap) }
      .takeWhile { stoppingCriterion() }
      .distinct()
  }.asSequence().flatten()

fun CFG.parallelEnumSeqWOR(
  prompt: List<String>,
  cores: Int = NUM_CORES,
  stoppingCriterion: () -> Boolean = { true }
): Sequence<String> =
  startPTree(prompt)?.let {
    (0..<cores).toList().parallelStream().map { i ->
      it.sampleStrWithoutReplacement()
        .map { it.removeEpsilon() }
        .takeWhile { stoppingCriterion() }
        .distinct()
    }.asSequence().flatten()
  } ?: sequenceOf()

fun CFG.parallelEnumListWR(
  prompt: List<String>,
  cores: Int = NUM_CORES,
  stoppingCriterion: () -> Boolean = { true }
): List<String> =
  startPTree(prompt)?.let {
    (0..<cores).toList().parallelStream().map { i ->
      it.sampleWRGD()
        .map { it.removeEpsilon() }
        .takeWhile { stoppingCriterion() }
        .distinct()
        .toList()
    }.toList().flatten()
  } ?: listOf()

fun CFG.parallelEnumListWOR(
  prompt: List<String>,
  cores: Int,
  stoppingCriterion: () -> Boolean = { true }
): List<String> =
  startPTree(prompt)?.let {
    (0..<cores).toList().parallelStream().map { i ->
      it.sampleStrWithoutReplacement(cores, i)
        .map { it.removeEpsilon() }
        .takeWhile { stoppingCriterion() }
        .distinct()
        .toList()
    }.toList().flatten()
  } ?: listOf()

/**
 * Much faster version of [intersectLevFSA] that leverages parallelism to construct
 * the intersection grammar since we are on the JVM, resulting in a ~10x speedup.
 */

infix fun CFG.jvmIntersectLevFSA(fsa: FSA): CFG = jvmIntersectLevFSAP(fsa)
//  subgrammar(fsa.alphabet)
//    .also { it.forEach { println("${it.LHS} -> ${it.RHS.joinToString(" ")}") } }
//    .intersectLevFSAP(fsa)

private infix fun CFG.jvmIntersectLevFSAP(fsa: FSA): CFG {
  var clock = TimeSource.Monotonic.markNow()

  val lengthBoundsCache = lengthBounds
  val nts = ConcurrentSkipListSet(setOf("START"))

  val initFinal =
    (fsa.init * fsa.final).map { (q, r) -> "START" to listOf("[$q~START~$r]") }

  val transits =
    fsa.Q.map { (q, a, r) -> "[$q,$a,$r]".also { nts.add(it) } to listOf(a) }

  // For every production A → σ in P, for every (p, σ, q) ∈ Q × Σ × Q
  // such that δ(p, σ) = q we have the production [p, A, q] → σ in P′.
  val unitProds = unitProdRules(fsa)
    .onEach { (a, _) -> nts.add(a) }

  // For each production A → BC in P, for every p, q, r ∈ Q,
  // we have the production [p,A,r] → [p,B,q] [q,C,r] in P′.
  val prods: Set<Production> = nonterminalProductions
  var i = 0
  val validTriples =
    fsa.stateCoords.let { it * it * it }.filter { it.isValidStateTriple() }.toList()

  val binaryProds =
    prods.parallelStream().flatMap {
//      if (i++ % 100 == 0) println("Finished $i/${nonterminalProductions.size} productions")
      if (4.minutes < clock.elapsedNow()) throw Exception("Timeout")
      val (A, B, C) = it.π1 to it.π2[0] to it.π2[1]
      validTriples
        // CFG ∩ FSA - in general we are not allowed to do this, but it works
        // because we assume a Levenshtein FSA, which is monotone and acyclic.
//        .filter { it.isCompatibleWith(A to B to C, fsa, lengthBoundsCache) }
        .map { (a, b, c) ->
          val (p, q, r)  = a.π1 to b.π1 to c.π1
          "[$p~$A~$r]".also { nts.add(it) } to listOf("[$p~$B~$q]", "[$q~$C~$r]")
        }.toList().stream()
    }.toList()

  fun Σᐩ.isSyntheticNT() =
    first() == '[' && length > 1 // && last() == ']' && count { it == '~' } == 2
  fun List<Production>.filterRHSInNTS(): CFG =
    parallelStream()
      .filter { (_, rhs) -> rhs.all { !it.isSyntheticNT() || it in nts } }
      .asSequence().toSet()

  println("Constructing ∩-grammar took: ${clock.elapsedNow()}")
  clock = TimeSource.Monotonic.markNow()
  return (initFinal + transits + binaryProds + unitProds)
    .filterRHSInNTS()
    .jvmPostProcess(clock)
}

// Parallel streaming doesn't seem to be that much faster (yet)?

fun CFG.jvmPostProcess(clock: TimeSource.Monotonic.ValueTimeMark) =
    let { if (it.size < 1_000) it.dropVestigialProductions() else it.jvmDropVestigialProductions(clock) }
//    .elimVarUnitProds()
    .normalForm
    .noEpsilonOrNonterminalStubs
    .also { println("Reduced ∩-grammar from $size to ${it.size} useful productions in ${clock.elapsedNow()}") }
    .freeze()

fun CFG.jvmDropVestigialProductions(
  clock: TimeSource.Monotonic.ValueTimeMark,
  criteria: (Σᐩ) -> Boolean = { it.first() == '[' && 1 < it.length }// && it.last() == ']' && it.count { it == '~' } == 2 }
): CFG {
  val nts: Set<Σᐩ> = ConcurrentSkipListSet<Σᐩ>().also { set -> asSequence().asStream().parallel().forEach { set.add(it.first) } }
  val rw = asSequence().asStream().parallel()
    .filter { prod ->
      if (4.minutes < clock.elapsedNow()) throw Exception("Timeout")
      prod.RHS.all { !criteria(it) || it in nts }
    }
    .asSequence().toSet()
//    .also { println("Removed ${size - it.size} invalid productions") }
    .freeze().jvmRemoveUselessSymbols()

//  println("Removed ${size - rw.size} vestigial productions, resulting in ${rw.size} productions.")

  return if (rw.size == size) rw else rw.jvmDropVestigialProductions(clock, criteria)
}

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

// TODO: https://zerobone.net/blog/cs/non-productive-cfg-rules/
fun CFG.jvmRemoveUselessSymbols(
  generating: Set<Σᐩ> = jvmGenSym(),
  reachable: Set<Σᐩ> = jvmReachSym()
): CFG =
  asSequence().asStream().parallel()
    .filter { (s, _) -> s in reachable && s in generating }
    .asSequence().toSet()

private fun CFG.jvmReachSym(from: Σᐩ = START_SYMBOL): Set<Σᐩ> {
  val allReachable: MutableSet<Σᐩ> = mutableSetOf(from)
  val nextReachable: MutableSet<Σᐩ> = mutableSetOf(from)
  val NDEPS =
    ConcurrentHashMap<Σᐩ, ConcurrentSkipListSet<Σᐩ>>().apply {
      this@jvmReachSym.asSequence().asStream().parallel()
        .forEach { (l, r) -> getOrPut(l) { ConcurrentSkipListSet() }.addAll(r) }
    }

  do {
    val t = nextReachable.first()
    nextReachable.remove(t)
    allReachable += t
    nextReachable += (NDEPS[t]?: emptyList())
      .filter { it !in allReachable && it !in nextReachable }
  } while (nextReachable.isNotEmpty())

//  println("TERM: ${allReachable.any { it in terminals }} ${allReachable.size}")

  return allReachable
}

private fun CFG.jvmGenSym(
  nonterminals: Set<Σᐩ> = asSequence().asStream().parallel().map { it.LHS }.asSequence().toSet(),
  from: Set<Σᐩ> = asSequence().asStream().parallel()
     .filter { it.RHS.size == 1 && it.RHS[0] !in nonterminals }
     .map { it.LHS }.asSequence().toSet()
): Set<Σᐩ> {
  val allGenerating: MutableSet<Σᐩ> = mutableSetOf()
  val nextGenerating: MutableSet<Σᐩ> = from.toMutableSet()
  val TDEPS =
    ConcurrentHashMap<Σᐩ, ConcurrentSkipListSet<Σᐩ>>().apply {
      this@jvmGenSym.asSequence().asStream().parallel()
        .forEach { (l, r) -> r.forEach { getOrPut(it) { ConcurrentSkipListSet() }.add(l) } }
    }

  do {
    val t = nextGenerating.first()
    nextGenerating.remove(t)
    allGenerating += t
    nextGenerating += (TDEPS[t] ?: emptyList())
      .filter { it !in allGenerating && it !in nextGenerating }
  } while (nextGenerating.isNotEmpty())

//  println("START: ${START_SYMBOL in allGenerating} ${allGenerating.size}")

  return allGenerating
}