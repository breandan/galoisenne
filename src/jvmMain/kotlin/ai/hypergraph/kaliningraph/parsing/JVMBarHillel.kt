package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.*
import ai.hypergraph.kaliningraph.automata.*
import ai.hypergraph.kaliningraph.repair.*
import ai.hypergraph.kaliningraph.types.*
import ai.hypergraph.kaliningraph.types.times
import java.util.stream.*
import kotlin.streams.*
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.LongAdder
import kotlin.collections.asSequence

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

fun PTree.sampleDirectlyWORAndScore(
  cores: Int = NUM_CORES,
  stoppingCriterion: () -> Boolean = { true },
  pcfgMap: Map<Π3A<Σᐩ>, Int>, pcfgNorm: Map<Σᐩ, Int>
): Stream<Π2<String, Double>> =
  (0..<cores).toList().parallelStream().flatMap { i ->
    sampleStrWithoutReplacementAndScore(cores, i, pcfgMap, pcfgNorm)
      .takeWhile { stoppingCriterion() }
      .distinctBy { it.first }
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

fun CFG.jvmIntersectLevFSA(fsa: FSA): CFG = jvmIntersectLevFSAP(fsa)
//  subgrammar(fsa.alphabet)
//    .also { it.forEach { println("${it.LHS} -> ${it.RHS.joinToString(" ")}") } }
//    .intersectLevFSAP(fsa)

fun CFG.makeLevPTree(toRepair: Σᐩ, levDist: Int = 3, parikhMap: ParikhMap = this.parikhMap): PTree =
  jvmIntersectLevFSAP(makeLevFSA(toRepair, levDist), parikhMap).toPTree()

val BH_TIMEOUT = 9.minutes
val MINFREEMEM = 1000000000L
val MAX_NTS = 4_000_000 // Gives each nonterminal about ~35kb of memory on Xmx=150GB

/**
 * Checks whether the NT can parse the string between indices a.π2 and b.π2,
 * representing a horizontal subtrajectory. Horizontal subtrajectories in a
 * LevFSA are necessarily distinct, i.e., there is only one path between two
 * states that both have the same height. If the two states a and b are both
 * the same height in an LevFSA, this subtrajectory represents an unmodified
 * substring. This will ignore state pairs that reside on different levels.
 *
 * @see computeNTCompat
 */

fun FSA.compat(a: STC, b: STC, nt: Int, compat: Array<Array<Array<Boolean>>>) =
  if (a.π3 != b.π3) true else compat[a.π2][b.π2][nt]

fun computeNTCompat(cfg: CFG, levStr: List<Σᐩ>): Array<Array<Array<Boolean>>> {
  val tbl = cfg.parseTableBln(levStr)
  val arr = Array(tbl.numRows) { Array(tbl.numCols) { Array(cfg.nonterminals.size) { false } } }
  for (r in 0 until tbl.numRows)
    for (c in r until tbl.numCols)
      for (k in cfg.nonterminals.indices)
        arr[r][c][k] = tbl[r, c][k]

  return arr
}

var filterMs = 0L
var normMs = 0L

// We pass pm and lbc because cache often flushed forcing them to be reloaded
// and we know they will usually be the same for all calls to this function.
fun CFG.jvmIntersectLevFSAP(fsa: FSA, parikhMap: ParikhMap = this.parikhMap): CFG {
//  if (fsa.Q.size < 650) throw Exception("FSA size was out of bounds")
  if (parikhMap.size < fsa.width + fsa.height) throw Exception("WARNING: Parikh map size exceeded")
  var clock = TimeSource.Monotonic.markNow()

  // Tracks all nonterminals constructed on the left hand side of a synthetic production
  val ntsb = Array(fsa.numStates) { Array(symbols.size) { Array(fsa.numStates) { false } } }

  val initFinal =
    (fsa.init * fsa.final).map { (q, r) -> listOf(ntMap["START"]!!) to listOf(listOf(fsa.stateMap[q]!!, ntMap["START"]!!, fsa.stateMap[r]!!)) }

  // For every production A → σ in P, for every (p, σ, q) ∈ Q × Σ × Q
  // such that δ(p, σ) = q we have the production [p, A, q] → σ in P′.
  val unitProds = unitProdRules3(fsa)
    .map { (a, b) -> a.also { ntsb[a[0]][a[1]][a[2]] = true } to b }
    .toSet()

  val ccClock = TimeSource.Monotonic.markNow()
  val compat: Array<Array<Array<Boolean>>> = computeNTCompat(this, fsa.levString)
  println("Computed NT compatibility in ${ccClock.elapsedNow()}")

  // For each production A → BC in P, for every p, q, r ∈ Q,
  // we have the production [p,A,r] → [p,B,q] [q,C,r] in P′.
  val prods = nonterminalProductions.map { (a, b) -> ntMap[a]!! to b.map { ntMap[it]!! } }.toSet()
  val validTriples = fsa.validTriples.map { arrayOf(it.π1.π1, it.π2.π1, it.π3.π1) }

  val ctClock = TimeSource.Monotonic.markNow()
  val ct = (fsa.validPairs * nonterminals.indices.toSet()).toList()
  val ct2 = Array(fsa.numStates) { Array(nonterminals.size) { Array(fsa.numStates) { false } } }
  ct.parallelStream()
    .filter { it: Π3<STC, STC, Int> ->
      // Checks whether the distinct subtrajectory between two horizontal states is parseable by a given NT
      fsa.compat(it.π1, it.π2, it.π3, compat)
        // Checks whether the length bounds for the nonterminal (i.e., the range of the number of terminals it can
        // parse) is compatible with the range of path lengths across all paths connecting two states in an FSA.
        // This is a coarse approximation, but is cheaper to compute, so it filters out most invalid triples.
        && parikhMap.ntLengthBounds[it.π3].overlaps(SPLPArith(it.π1, it.π2))
        // Checks the Parikh map for compatibility between the CFG nonterminals and state pairs in the FSA.
        // This is a finer grained filter, but more expensive to compute, so we use the coarse filter first
        && fsa.obeys(it.π1, it.π2, it.π3, parikhMap)
    }
//    .toList().also {
//      val candidates = (fsa.numStates * nonterminals.size * fsa.numStates)
//      val fraction = it.size.toDouble() / candidates
//      println("Fraction of valid LBH triples: ${it.size}/$candidates ≈ $fraction")
//    }
    .forEach { ct2[it.π1.π1][it.π3][it.π2.π1] = true }
  println("Precomputed LP constraints in ${ctClock.elapsedNow()}")

  var counter = 0
  val lpClock = TimeSource.Monotonic.markNow()
  val binaryProds =
    prods.parallelStream().flatMap {
      if (BH_TIMEOUT < clock.elapsedNow()) throw Exception("Timeout: ${ntsb.flatten().sumOf { it.count { it } }} nts")
      val (A, B, C) = it.π1 to it.π2[0] to it.π2[1]
      val trip = arrayOf(A, B, C)
      validTriples.stream()
        // CFG ∩ FSA - in general we are not allowed to do this, but it works
        // because we assume a Levenshtein FSA, which is monotone and acyclic.
//        .filter { it.isCompatibleWith(A to B to C, fsa, lengthBoundsCache) }
//        .filter { it.checkCT(trip, ct1).also { if (!it) elimCounter.incrementAndGet() } }
//        .filter { it.obeysLevenshteinParikhBounds(A to B to C, fsa, parikhMap) }
        .filter { it.checkCompatibility(trip, ct2) }
        .map { (a, b, c) ->
          if (MAX_IPRODS < counter++) throw Exception("∩-grammar has too many productions! (>$MAX_IPRODS)")
//          val (p, q, r) = states[a] to states[b] to states[c]
//          "[$p~${allsym[A]}~$r]".also { nts.add(listOf(p, allsym[A], r)) } to listOf("[$p~${allsym[B]}~$q]", "[$q~${allsym[C]}~$r]")
          listOf(a, A, c).also { ntsb[a][A][c] = true } to listOf(listOf(a, B, b), listOf(b, C, c))
        }
    }.toList()

  val elimCounter = (validTriples.size * prods.size) - binaryProds.size
  println("Levenshtein-Parikh constraints eliminated $elimCounter productions in ${lpClock.elapsedNow()}")

  // !isSyntheticNT() === is START or a terminal
  fun <T> List<T>.isSyntheticNT() = size > 1
  fun List<Int>.toNT() = if (size == 1) ntLst[first()]
    else "[" + fsa.stateLst[this[0]] + "~" + ntLst[this[1]] + "~" + fsa.stateLst[this[2]] + "]"

  val totalProds = binaryProds.size + unitProds.size + initFinal.size
  println("Constructed ∩-grammar with $totalProds productions in ${clock.elapsedNow()}")

  filterMs += clock.elapsedNow().inWholeMilliseconds

  clock = TimeSource.Monotonic.markNow()
  return Stream.concat(binaryProds.stream(), (initFinal + unitProds).stream()).parallel()
    // A production, e.g., * -> * [G], can be removed if the synthetic nonterminal [G] does not exist, i.e.,
    // every instance of [G] -> * * was incompatible with the FSA, so the nonterminal [G] is "unproductive".
    .filter { (_, rhs) -> rhs.all { !it.isSyntheticNT() || ntsb[it[0]][it[1]][it[2]] } }
    .map { (l, r) -> l.toNT() to r.map { it.toNT() } }
    .collect(Collectors.toSet())
    .also { println("Eliminated ${totalProds - it.size} extra productions before normalization") }
//    .also { it.jdvpNew() }
    .jvmPostProcess(clock)
    .also {
      normMs += clock.elapsedNow().inWholeMilliseconds
      println("Fraction of time spent normalizing: " + normMs / (normMs.toDouble() + filterMs))
    }
//    .expandNonterminalStubs(origCFG = this@jvmIntersectLevFSAP)
//    .jdvpNew()
}

fun CFG.jvmPostProcess(clock: TimeSource.Monotonic.ValueTimeMark) =
    jvmDropVestigialProductions(clock)
      .also { println("Normalization eliminated ${size - it.size} productions in ${clock.elapsedNow()}") }

fun CFG.jvmDropVestigialProductions(clock: TimeSource.Monotonic.ValueTimeMark): CFG {
  val start = clock.elapsedNow()
  var counter = 0
  val scfg = asSequence()
  val nts: Set<Σᐩ> = scfg.asStream().parallel().map { it.first }.collect(Collectors.toSet())
  val rw = scfg.asStream().parallel().filter { prod ->
     if (counter++ % 1000 == 0 && BH_TIMEOUT < clock.elapsedNow()) throw Exception("Timeout! ${clock.elapsedNow()}")
      // Only keep productions whose RHS symbols are not synthetic or are in the set of NTs
      prod.RHS.all { !(it.first() == '[' && 1 < it.length) || it in nts }
    }
    .collect(Collectors.toSet())
    .also { println("Removed ${size - it.size} invalid productions in ${clock.elapsedNow() - start}") }
    .freeze()
    .jvmRemoveUselessSymbols(nts)

  println("Removed ${size - rw.size} vestigial productions, resulting in ${rw.size} productions.")

  return if (rw.size == size) jvmElimVarUnitProds(rw).freeze()
  else rw.jvmDropVestigialProductions(clock)
}

// Eliminates unit productions whose RHS is not a terminal. For Bar-Hillel intersections, we know the only
// examples of this are the (S -> *) rules, so elimination is much simpler than the full CNF normalization.
fun jvmElimVarUnitProds(cfg: CFG): CFG {
  val scfg = cfg.asSequence()
  val vars = scfg.asStream().parallel().map { it.first }.collect(Collectors.toSet())
  val toElim = scfg.asStream().parallel()
    .filter { it.RHS.size == 1 && it.LHS == "START" && it.RHS[0] in vars }
    .map { it.RHS[0] }
    .collect(Collectors.toSet())
  val newCFG = scfg.asStream().parallel()
    .filter { it.RHS.size > 1 || it.RHS[0] !in toElim }
    .map { if (it.LHS in toElim) "START" to it.RHS else it }
    .collect(Collectors.toSet())
  return newCFG
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

fun CFG.jvmRemoveUselessSymbols(
  nonterminals: Set<Σᐩ>,
  generating: Set<Σᐩ> = jvmGenSym(nonterminals),
  reachable: Set<Σᐩ> = jvmReachSym()
): CFG =
  asSequence().asStream().parallel()
//    .filter { (s, _) -> s in reachable && s in generating }
    .filter { (s, r) -> s in reachable && s in generating && r.all { it in reachable && (r.size == 1 || it in generating) } }
    .collect(Collectors.toSet())

private fun CFG.jvmReachSym(from: Σᐩ = START_SYMBOL): Set<Σᐩ> {
  val allReachable: MutableSet<Σᐩ> = mutableSetOf(from)
  val nextReachable: MutableSet<Σᐩ> = mutableSetOf(from)
  val NDEPS =
    ConcurrentHashMap<Σᐩ, MutableSet<Σᐩ>>(size).apply {
      this@jvmReachSym.asSequence().asStream().parallel()
        .forEach { (l, r) -> getOrPut(l) { ConcurrentHashMap.newKeySet() }.addAll(r) }
    }
//    this@jvmReachSym.asSequence().asStream().parallel()
//      .flatMap { (l, r) -> r.stream().map { l to it } }
//      // List of second elements grouped by first element
//      .collect(Collectors.groupingByConcurrent({ it.first }, Collectors.mapping({ it.second }, Collectors.toSet())))

  while (nextReachable.isNotEmpty()) {
    val t = nextReachable.first()
    nextReachable.remove(t)
    allReachable += t
    nextReachable += (NDEPS[t]?: emptyList())
      .filter { it !in allReachable && it !in nextReachable }
  }

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
    ConcurrentHashMap<Σᐩ, MutableSet<Σᐩ>>(size).apply {
      this@jvmGenSym.asSequence().asStream().parallel()
        .forEach { (l, r) -> r.forEach { getOrPut(it) { ConcurrentHashMap.newKeySet() }.add(l) } }
    }
//    this@jvmGenSym.asSequence().asStream().parallel()
//      .flatMap { (l, r) -> r.asSequence().asStream().map { it to l } }
//      // List of second elements grouped by first element
//      .collect(Collectors.groupingByConcurrent({ it.first }, Collectors.mapping({ it.second }, Collectors.toList())))

  while (nextGenerating.isNotEmpty()) {
    val t = nextGenerating.first()
    nextGenerating.remove(t)
    allGenerating += t
    nextGenerating += (TDEPS[t] ?: emptyList())
      .filter { it !in allGenerating && it !in nextGenerating }
  }

//  println("START: ${START_SYMBOL in allGenerating} ${allGenerating.size}")

  return allGenerating
}

// TODO: Incomplete / untested
// Based on: https://zerobone.net/blog/cs/non-productive-cfg-rules/
// Precondition: The CFG must be binarized, i.e., almost CNF but may have useless productions
// Postcondition: The CFG is in Chomsky Normal Form (CNF)
//fun CFG.jdvpNew(): CFG {
//  println("Total productions: $size")
//  val timer = TimeSource.Monotonic.markNow()
//  val counter = ConcurrentHashMap<Set<Σᐩ>, LongAdder>()
//
//  // Maps each nonterminal to the set of RHS sets that contain it
//  val UDEPS = ConcurrentHashMap<Σᐩ, ConcurrentLinkedQueue<Set<Σᐩ>>>(size)
//  // Maps the set of symbols on the RHS of a production to the production
//  val NDEPS = ConcurrentHashMap<Set<Σᐩ>, ConcurrentLinkedQueue<Production>>(size).apply {
//    put(emptySet(), ConcurrentLinkedQueue())
//    this@jdvpNew.asSequence().asStream().parallel().forEach {
//      val v = it.second.toSet() // RHS set, i.e., the set of NTs on the RHS of a production
//      // If |v| is 1, then the production must be a unit production, i.e, A -> a, b/c A -> B is not binarized
//      getOrPut(if(it.second.size == 1) emptySet() else v) { ConcurrentLinkedQueue() }.add(it)
//      v.forEach { s -> UDEPS.getOrPut(s) { ConcurrentLinkedQueue() }.add(v) }
//      if (v.size == 2) counter.putIfAbsent(v, LongAdder().apply { add(2L) })
//    }
//  }
//
//  println("Built graph in ${timer.elapsedNow()}: ${counter.size} conjuncts, ${UDEPS.size + NDEPS.size} edges")
//
//  val nextReachable: LinkedHashSet<Set<Σᐩ>> = LinkedHashSet<Set<Σᐩ>>().apply { add(emptySet()) }
//
//  val productive = mutableSetOf<Production>()
//  do {
////    println("Next reachable: ${nextReachable.size}, Productive: ${productive.size}")
//    val q = nextReachable.removeFirst()
//    if (counter[q]?.sum() == 0L || NDEPS[q]?.all { it in productive } == true) continue
//    else if (q.size == 2) { // Conjunct
//      val dec = counter[q]!!.apply { decrement() }
//      if (dec.sum() == 0L) { // Seen both
//        NDEPS[q]?.forEach {
//          productive.add(it)
//          UDEPS[it.LHS]?.forEach { st -> if (st !in productive) nextReachable.addLast(st) }
//        }
//      } else nextReachable.addLast(q) // Always add back if sum not zero
//    } else {
//      NDEPS[q]?.forEach {
//        productive.add(it)
//        UDEPS[it.LHS]?.forEach { st -> if (st !in productive) nextReachable.addLast(st) }
//      }
//    }
//  } while (nextReachable.isNotEmpty())
//
//  println("Eliminated ${size - productive.size} unproductive productions in ${timer.elapsedNow()}")
//  println("Resulting in ${productive.size} productions.")
//
//  val QDEPS =
//    ConcurrentHashMap<Σᐩ, ConcurrentLinkedQueue<Production>>(size).apply {
//      productive.asSequence().asStream().parallel().forEach {
//        getOrPut(it.LHS) { ConcurrentLinkedQueue() }.add(it)
//      }
//    }
//
//  val done = mutableSetOf(START_SYMBOL)
//  val nextProd: MutableList<Σᐩ> = mutableListOf(START_SYMBOL)
//  val productiveAndReachable = mutableSetOf<Production>()
//
//  do {
//    val q = nextProd.removeFirst().also { done += it }
//    QDEPS[q]?.forEach { it ->
//      productiveAndReachable.add(it)
//      it.RHS.forEach { if (it !in done) nextProd += it }
//    }
//  } while (nextProd.isNotEmpty())
//
//  println("Eliminated ${productive.size - productiveAndReachable.size} unreachable productions in ${timer.elapsedNow()}")
//  println("Resulting in ${productiveAndReachable.size} productions.")
//
//  return productiveAndReachable.freeze()
//}