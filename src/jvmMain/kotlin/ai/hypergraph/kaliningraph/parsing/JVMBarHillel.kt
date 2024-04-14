package ai.hypergraph.kaliningraph.parsing

import NUM_CORES
import ai.hypergraph.kaliningraph.*
import ai.hypergraph.kaliningraph.automata.*
import ai.hypergraph.kaliningraph.repair.minimizeFix
import ai.hypergraph.kaliningraph.types.*
import ai.hypergraph.kaliningraph.types.times
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
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

// When the CFG is acyclic, there is no need to compute the matrix fixpoint
// unless we want to further constrain it to contain specific tokens. In that
// case, we can simply construct the PTree directly from the grammar.
fun CFG.sampleDirectlyWR(
  cores: Int = NUM_CORES,
  stoppingCriterion: () -> Boolean = { true },
): Stream<String> =
  toPTree().let {
    (0..<cores).toList().parallelStream().flatMap { i ->
      it.sampleWRGD()
        .takeWhile { stoppingCriterion() }
        .distinct()
        .asStream()
    }
  }

fun PTree.sampleWithPCFG(
  pcfgTable: Map<Int, Int>,
  cores: Int = NUM_CORES,
  stoppingCriterion: () -> Boolean = { true }
): Stream<String> =
  (0..<cores).toList().parallelStream().flatMap { i ->
    sampleStrWithPCFG5(pcfgTable)
      .takeWhile { stoppingCriterion() }
      .distinct()
      .asStream()
  }

fun PTree.sampleDirectlyWOR(
  cores: Int = NUM_CORES,
  stoppingCriterion: () -> Boolean = { true }
): Stream<String> =
  (0..<cores).toList().parallelStream().flatMap { i ->
    sampleStrWithoutReplacement(cores, i)
      .takeWhile { stoppingCriterion() }
      .distinct()
      .asStream()
  }

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

fun CFG.jvmIntersectLevFSA(fsa: FSA, parikhMap: ParikhMap = this.parikhMap): CFG =
  jvmIntersectLevFSAP(fsa, parikhMap)
//  subgrammar(fsa.alphabet)
//    .also { it.forEach { println("${it.LHS} -> ${it.RHS.joinToString(" ")}") } }
//    .intersectLevFSAP(fsa)

val BH_TIMEOUT = 9.minutes
val MINFREEMEM = 1000000000L
val MAX_NTS = 4_000_000 // Gives each nonterminal about ~35kb of memory on Xmx=150GB
val MAX_PRODS = 200_000_000

val maxNTsSeen = AtomicInteger(0)

private fun CFG.jvmIntersectLevFSAP(fsa: FSA, parikhMap: ParikhMap): CFG {
//  if (fsa.Q.size < 650) throw Exception("FSA size was out of bounds")
  var clock = TimeSource.Monotonic.markNow()

  val nts = ConcurrentSkipListSet(setOf("START"))

  val initFinal =
    (fsa.init * fsa.final).map { (q, r) -> "START" to listOf("[$q~START~$r]") }

  val transits =
    fsa.Q.map { (q, a, r) -> "[$q~$a~$r]".also { nts.add(it) } to listOf(a) }

  // For every production A → σ in P, for every (p, σ, q) ∈ Q × Σ × Q
  // such that δ(p, σ) = q we have the production [p, A, q] → σ in P′.
  val unitProds = unitProdRules(fsa)
    .onEach { (a, _) -> nts.add(a) }

  // For each production A → BC in P, for every p, q, r ∈ Q,
  // we have the production [p,A,r] → [p,B,q] [q,C,r] in P′.
  val prods = nonterminalProductions
    .map { (a, b) -> ntMap[a]!! to b.map { ntMap[it]!! } }.toSet()
//  val lengthBoundsCache = lengthBounds.let { lb -> nonterminals.map { lb[it] ?: 0..0 } }
  val validTriples = fsa.validTriples.map { arrayOf(it.π1.π1, it.π2.π1, it.π3.π1) }

  val ct = (fsa.validPairs * nonterminals.indices.toSet()).toList()
  val ct2 = Array(fsa.states.size) { Array(nonterminals.size) { Array(fsa.states.size) { false } } }
  ct.parallelStream()
    .filter { fsa.obeys(it.π1, it.π2, it.π3, parikhMap) }
    .toList().also {
      val fraction = it.size.toDouble() / (fsa.states.size * nonterminals.size * fsa.states.size)
      println("Fraction of valid triples: $fraction")
    }.forEach { ct2[it.π1.π1][it.π3][it.π2.π1] = true }

  val elimCounter = AtomicInteger(0)
  val counter = AtomicInteger(0)
  val lpClock = TimeSource.Monotonic.markNow()
  val binaryProds =
    prods.parallelStream().flatMap {
      if (BH_TIMEOUT < clock.elapsedNow()) throw Exception("Timeout: ${nts.size} nts")
      val (A, B, C) = it.π1 to it.π2[0] to it.π2[1]
      val trip = arrayOf(A, B, C)
      validTriples.stream()
        // CFG ∩ FSA - in general we are not allowed to do this, but it works
        // because we assume a Levenshtein FSA, which is monotone and acyclic.
//        .filter { it.isCompatibleWith(A to B to C, fsa, lengthBoundsCache).also { if (!it) elimCounter.incrementAndGet() } }
//        .filter { it.checkCT(trip, ct1).also { if (!it) elimCounter.incrementAndGet() } }
//        .filter { it.obeysLevenshteinParikhBounds(A to B to C, fsa, parikhMap).also { if (!it) elimCounter.incrementAndGet() } }
        .filter { it.checkCompatibility(trip, ct2).also { if (!it) elimCounter.incrementAndGet() } }
        .map { (a, b, c) ->
          if (MAX_PRODS < counter.incrementAndGet()) throw Exception("∩-grammar has too many productions! (>$MAX_PRODS)")
          val (p, q, r)  = fsa.stateLst[a] to fsa.stateLst[b] to fsa.stateLst[c]
          "[$p~${ntLst[A]}~$r]".also { nts.add(it) } to listOf("[$p~${ntLst[B]}~$q]", "[$q~${ntLst[C]}~$r]")
        }
    }.toList()

  println("Levenshtein-Parikh constraints eliminated $elimCounter productions in ${lpClock.elapsedNow()}")

  fun Σᐩ.isSyntheticNT() =
    first() == '[' && length > 1 // && last() == ']' && count { it == '~' } == 2

  val totalProds = binaryProds.size + transits.size + unitProds.size + initFinal.size
  println("Constructed ∩-grammar with $totalProds productions in ${clock.elapsedNow()}")

  clock = TimeSource.Monotonic.markNow()
  return Stream.concat(binaryProds.stream(), (initFinal + transits + unitProds).stream()).parallel()
    .filter { (_, rhs) -> rhs.all { !it.isSyntheticNT() || it in nts } }
    .collect(Collectors.toSet())
    .jvmPostProcess(clock)
}

// Parallel streaming doesn't seem to be that much faster (yet)?

fun CFG.jvmPostProcess(clock: TimeSource.Monotonic.ValueTimeMark) =
  jvmDropVestigialProductions(clock)
    .jvmElimVarUnitProds()
      .also { println("Normalization eliminated ${size - it.size} productions in ${clock.elapsedNow()}") }
      .freeze()

tailrec fun CFG.jvmElimVarUnitProds(
  toVisit: Set<Σᐩ> = nonterminals,
  vars: Set<Σᐩ> = nonterminals,
  toElim: Σᐩ? = toVisit.firstOrNull()
): CFG {
  fun Production.isVariableUnitProd() = RHS.size == 1 && RHS[0] in vars
  if (toElim == null) return filter { !it.isVariableUnitProd() }
  val varsThatMapToMe =
    asSequence().asStream().parallel()
      .filter { it.RHS.size == 1 && it.RHS[0] == toElim }
      .map { it.LHS }.collect(Collectors.toSet())
  val thingsIMapTo =
    asSequence().asStream().parallel()
      .filter { it.LHS == toElim }.map { it.RHS }
      .collect(Collectors.toSet())
  return (varsThatMapToMe * thingsIMapTo).fold(this) { g, p -> g + p }
    .jvmElimVarUnitProds(toVisit.drop(1).toSet(), vars)
}

fun CFG.jvmDropVestigialProductions(clock: TimeSource.Monotonic.ValueTimeMark): CFG {
  val start = clock.elapsedNow()
  val counter = AtomicInteger(0)
  val nts: Set<Σᐩ> = asSequence().asStream().parallel().map { it.first }.collect(Collectors.toSet())
  val rw = asSequence().asStream().parallel()
    .filter { prod ->
      if (counter.incrementAndGet() % 10 == 0 && BH_TIMEOUT < clock.elapsedNow()) throw Exception("Timeout!")
      // Only keep productions whose RHS symbols are not synthetic or are in the set of NTs
      prod.RHS.all { !(it.first() == '[' && 1 < it.length) || it in nts }
    }
    .collect(Collectors.toSet())
//    .also { println("Removed ${size - it.size} invalid productions in ${clock.elapsedNow() - start}") }
    .freeze().jvmRemoveUselessSymbols()

//  println("Removed ${size - rw.size} vestigial productions, resulting in ${rw.size} productions.")

  return if (rw.size == size) rw else rw.jvmDropVestigialProductions(clock)
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
    .collect(Collectors.toSet())

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
  nonterminals: Set<Σᐩ> = asSequence().asStream().parallel().map { it.LHS }.collect(Collectors.toSet()),
  from: Set<Σᐩ> = asSequence().asStream().parallel()
     .filter { it.RHS.size == 1 && it.RHS[0] !in nonterminals }
     .map { it.LHS }.collect(Collectors.toSet())
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