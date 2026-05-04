package ai.hypergraph.markovian.concurrency

import ai.hypergraph.kaliningraph.parsing.*
import ai.hypergraph.kaliningraph.parsing.approximations.*
import java.io.File
import java.nio.*
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.IntStream
import kotlin.math.*
import kotlin.time.TimeSource

fun NFA.removeEpsilonsParallel(): NFA {
  val states = allStates.toIntArray().also { it.sort() }
  val Q = states.size
  if (Q == 0) return NFA(emptySet(), emptySet(), emptyMap())

  require(states[0] >= 0) { "removeEpsilonsParallel assumes non-negative state ids" }

  // ---- Dense state indexing: original Int state id -> 0..Q-1 ----
  val isContig0 =
    states[0] == 0 &&
        states[Q - 1] == Q - 1 &&
        run {
          var ok = true
          for (i in 0 until Q) {
            if (states[i] != i) {
              ok = false
              break
            }
          }
          ok
        }

  val idxOf: (Int) -> Int = if (isContig0) { { s -> s } } else {
    val maxId = states[Q - 1]

    if (maxId <= 2_000_000 && maxId <= 8 * Q) {
      val dense = IntArray(maxId + 1) { -1 }
      for (i in 0 until Q) dense[states[i]] = i

      { s ->
        val idx = if (s in dense.indices) dense[s] else -1
        require(idx >= 0) { "unknown NFA state id: $s" }
        idx
      }
    } else {
      val dense = HashMap<Int, Int>(Q * 2)
      for (i in 0 until Q) dense[states[i]] = i

      { s -> dense[s] ?: error("unknown NFA state id: $s") }
    }
  }

  // ---- Tiny primitive vector for adjacency construction ----
  class IntVec(cap0: Int = 4) {
    var a = IntArray(cap0)
    var size = 0

    fun add(x: Int) {
      if (size == a.size) a = a.copyOf(maxOf(4, a.size * 2))
      a[size++] = x
    }

    fun toArray(): IntArray = a.copyOf(size)
  }

  // epsOut[q] = dense targets reachable by one ε-edge.
  val epsTmp = Array(Q) { IntVec() }

  // symOut[q] = original symbolic edges leaving q.
  // Targets stay in original ids, matching your current removeEpsilons().
  val symTmp = Array(Q) { ArrayList<NFA.Edge>() }

  var hasEpsilon = false

  for ((src, edges) in transitions) {
    val si = idxOf(src)

    for (e in edges) {
      val lab = e.label
      if (lab == null) {
        hasEpsilon = true
        epsTmp[si].add(idxOf(e.target))
      } else symTmp[si].add(NFA.Edge(lab, e.target))
    }
  }

  // Fast path: already ε-free. Still dedupe per source, as your old method did.
  if (!hasEpsilon) {
    val out = HashMap<Int, List<NFA.Edge>>(transitions.size * 2)

    for (qi in 0 until Q) {
      val edges = symTmp[qi]
      if (edges.isEmpty()) continue

      val deduped =
        if (edges.size == 1) edges
        else {
          val seen = HashSet<NFA.Edge>(edges.size * 2)
          val xs = ArrayList<NFA.Edge>(edges.size)
          for (e in edges) if (seen.add(e)) xs.add(e)
          xs
        }

      if (deduped.isNotEmpty()) out[states[qi]] = deduped
    }

    return NFA(startStates, finalStates, out)
  }

  val epsOut = Array(Q) { i -> epsTmp[i].toArray() }
  val symOut = Array(Q) { i -> symTmp[i].toTypedArray() }

  val isOldFinal = BooleanArray(Q)
  for (f in finalStates) isOldFinal[idxOf(f)] = true

  // Written independently by state index, so no synchronization needed.
  val newOutByIdx = arrayOfNulls<List<NFA.Edge>>(Q)
  val newFinalMask = BooleanArray(Q)

  // Per-worker scratch storage to avoid allocating BooleanArray/queue per state.
  class Work {
    val seen = IntArray(Q)
    val queue = IntArray(Q)
    var mark = 1

    fun freshMark(): Int {
      if (mark == Int.MAX_VALUE) {
        Arrays.fill(seen, 0)
        mark = 1
      }
      return mark++
    }
  }

  val localWork = ThreadLocal.withInitial { Work() }

  IntStream.range(0, Q).parallel().forEach { q ->
    val work = localWork.get()
    val seen = work.seen
    val queue = work.queue
    val mark = work.freshMark()

    var head = 0
    var tail = 0

    seen[q] = mark
    queue[tail++] = q

    // Compute ε-closure(q) by BFS over dense ε adjacency.
    while (head < tail) {
      val u = queue[head++]
      val eps = epsOut[u]

      for (k in eps.indices) {
        val v = eps[k]
        if (seen[v] != mark) {
          seen[v] = mark
          queue[tail++] = v
        }
      }
    }

    // Promote q to final if closure(q) intersects old finals.
    var finalReachable = false
    var edgeHint = 0

    for (i in 0 until tail) {
      val p = queue[i]
      if (isOldFinal[p]) finalReachable = true
      edgeHint += symOut[p].size
    }

    if (finalReachable) newFinalMask[q] = true
    if (edgeHint == 0) return@forEach

    // Add all symbolic edges leaving states in closure(q), deduped.
    val out = ArrayList<NFA.Edge>(edgeHint)
    val seenEdges = HashSet<NFA.Edge>(edgeHint * 2)

    for (i in 0 until tail) {
      val p = queue[i]
      val edges = symOut[p]

      for (e in edges) if (seenEdges.add(e)) out.add(e)
    }

    if (out.isNotEmpty()) newOutByIdx[q] = out
  }

  val newTransitions = HashMap<Int, List<NFA.Edge>>(Q * 2)
  for (qi in 0 until Q) {
    val edges = newOutByIdx[qi]
    if (edges != null && edges.isNotEmpty()) newTransitions[states[qi]] = edges
  }

  val newFinals = HashSet<Int>(finalStates.size * 2)
  for (qi in 0 until Q) if (newFinalMask[qi]) newFinals.add(states[qi])

  return NFA(
    startStates = startStates,
    finalStates = newFinals,
    transitions = newTransitions
  )
}

/**
 * Determinizes the NFA using a parallel frontier-based Subset Construction.
 *
 * Returns an equivalent DFA, represented as an ε-free NFA.
 */
fun NFA.determinizeParallel(parallelism: Int = Runtime.getRuntime().availableProcessors(), reportEvery: Int = 100_000): NFA {
  val timer = TimeSource.Monotonic.markNow()
  require(parallelism >= 1) { "parallelism must be >= 1" }

  // This removes epsilons implicitly before subset construction.
  val nfaNoEps = this.removeEpsilons()

  require(nfaNoEps.transitions.values.none { edges -> edges.any { it.label == null } }) {
    "removeEpsilons() produced an NFA with ε-edges"
  }

  /**
   * Sorted immutable IntArray wrapper for DFA subset states.
   *
   * Important: never mutate `states` after constructing this key.
   */
  class StateKey(val states: IntArray) {
    override fun equals(other: Any?): Boolean = other is StateKey && states.contentEquals(other.states)
    override fun hashCode(): Int = states.contentHashCode()
    override fun toString(): String = states.joinToString(prefix = "{", postfix = "}")
  }

  data class PendingEdge(val src: Int, val label: Σᐩ, val dst: Int)

  fun sortedKeyFromSet(xs: HashSet<Int>): StateKey = StateKey(xs.toIntArray().apply { sort() })

  fun isFinalSubset(k: StateKey): Boolean =
    k.states.any { it in nfaNoEps.finalStates }

  val pool = ForkJoinPool(parallelism)

  fun <T> runParallel(block: () -> T): T {
    return try {
      pool.submit(Callable { block() }).get()
    } catch (e: ExecutionException) {
      val c = e.cause
      when (c) {
        is RuntimeException -> throw c
        is Error -> throw c
        else -> throw RuntimeException(c)
      }
    }
  }

  try {
    // Pre-filter outgoing transitions. After removeEpsilons(), all labels should
    // be non-null, but this also avoids repeatedly checking nulls in the hot loop.
    val outgoing: Map<Int, List<NFA.Edge>> =
      nfaNoEps.transitions.mapValues { (q, edges) ->
        edges.map { e ->
          val label = e.label ?: error("Unexpected epsilon edge from state $q after removeEpsilons()")
          NFA.Edge(label, e.target)
        }
      }

    val subsetToId = ConcurrentHashMap<StateKey, Int>(1 shl 16)
    val nextId = AtomicInteger(0)

    val newFinalStates = ConcurrentHashMap.newKeySet<Int>()

    fun internSeed(k: StateKey): Int {
      val id = nextId.getAndIncrement()
      val old = subsetToId.putIfAbsent(k, id)
      require(old == null) { "Duplicate seed subset: $k" }
      if (isFinalSubset(k)) newFinalStates.add(id)
      return id
    }

    val startArr = nfaNoEps.startStates.toIntArray().apply { sort() }
    val startKey = StateKey(startArr)
    internSeed(startKey)

    var frontier: List<StateKey> = listOf(startKey)
    val newTransitions = HashMap<Int, MutableList<NFA.Edge>>()

    var processed = 0

    while (frontier.isNotEmpty()) {
      val nextFrontier = ConcurrentLinkedQueue<StateKey>()

      fun intern(k: StateKey): Int =
        subsetToId.computeIfAbsent(k) { kk ->
          val id = nextId.getAndIncrement()

          if (isFinalSubset(kk)) newFinalStates.add(id)

          nextFrontier.add(kk)
          id
        }

      val wave: List<List<PendingEdge>> = runParallel {
        frontier.parallelStream()
          .map { subsetKey ->
            val srcDfaId = subsetToId[subsetKey] ?: error("Missing DFA id for subset $subsetKey")

            // Accumulate:
            //   byLabel[a] = union of NFA targets reachable by `a`
            //
            // This is usually much faster than looping over the whole alphabet
            // for every subset, especially for sparse automata.
            val byLabel = HashMap<Σᐩ, HashSet<Int>>()

            for (q in subsetKey.states) {
              val outs = outgoing[q] ?: continue

              for (e in outs) {
                val label = e.label ?: error("Unexpected epsilon edge from state $q after removeEpsilons()")

                byLabel.getOrPut(label) { HashSet() }.add(e.target)
              }
            }

            if (byLabel.isEmpty()) { emptyList() } else {
              val edges = ArrayList<PendingEdge>(byLabel.size)

              // Stable per-state edge order. State IDs are still nondeterministic.
              val labels = byLabel.keys.toList().sorted()

              for (label in labels) {
                val targetSet = byLabel[label] ?: continue
                if (targetSet.isEmpty()) continue

                val dstKey = sortedKeyFromSet(targetSet)
                val dstDfaId = intern(dstKey)

                edges.add(PendingEdge(srcDfaId, label, dstDfaId))
              }

              edges
            }
          }.toList()
      }

      // Merge transitions sequentially to avoid contended writes.
      for (edges in wave) {
        for (e in edges) {
          newTransitions.getOrPut(e.src) { ArrayList() }
            .add(NFA.Edge(e.label, e.dst))
        }
      }

      processed += frontier.size
      if (reportEvery > 0 && processed >= reportEvery && processed % reportEvery < frontier.size) {
        println(
          "determinizeParallel: processed=$processed, " +
              "states=${subsetToId.size}, frontier=${frontier.size}, nextFrontier=${nextFrontier.size}"
        )
      }

      val nf = ArrayList<StateKey>(nextFrontier.size)
      while (true) {
        val k = nextFrontier.poll() ?: break
        nf.add(k)
      }

      frontier = nf
    }

    return NFA(
      startStates = setOf(0),
      finalStates = HashSet(newFinalStates),
      transitions = newTransitions
    ).also {
      println(
        "Determinized NFA (${it.allStates.size} states, " +
            "${newTransitions.values.sumOf { es -> es.size }} transitions) " +
            "in ${timer.elapsedNow()}"
      )
    }
  } finally { pool.shutdown() }
}

fun NFA.trainDFAParallel(
  data: List<List<Σᐩ>>,
  alpha: Double = DEFAULT_LIDSTONE_ALPHA,
  strict: Boolean = false,
  parallelism: Int = Runtime.getRuntime().availableProcessors(),
  reportEvery: Int = 1_000_000
): WFA {
  require(alpha > 0.0) { "alpha must be positive" }
  require(parallelism >= 1) { "parallelism must be >= 1" }
  require(startStates.size == 1) {
    "trainDFA expects a deterministic automaton with exactly one start state; got ${startStates.size}"
  }

  require(transitions.values.none { edges -> edges.any { it.label == null } }) {
    "trainDFA expects an epsilon-free DFA. Call removeEpsilons().determinize() first."
  }

  val start = startStates.single()
  val pool = ForkJoinPool(parallelism)

  fun <T> runParallel(block: () -> T): T =
    try { pool.submit(Callable { block() }).get() }
    catch (e: ExecutionException) {
      val c = e.cause
      when (c) {
        is RuntimeException -> throw c
        is Error -> throw c
        else -> throw RuntimeException(c)
      }
    }

  class Counts {
    val transCounts = HashMap<Int, HashMap<Σᐩ, Long>>()
    val stopCounts = HashMap<Int, Long>()

    fun incTrans(q: Int, a: Σᐩ) {
      val m = transCounts.getOrPut(q) { HashMap() }
      m[a] = (m[a] ?: 0L) + 1L
    }

    fun incStop(q: Int) { stopCounts[q] = (stopCounts[q] ?: 0L) + 1L }

    fun mergeFrom(other: Counts) {
      for ((q, otherMap) in other.transCounts) {
        val m = transCounts.getOrPut(q) { HashMap(otherMap.size * 2 + 1) }
        for ((a, c) in otherMap) { m[a] = (m[a] ?: 0L) + c }
      }

      for ((q, c) in other.stopCounts) { stopCounts[q] = (stopCounts[q] ?: 0L) + c }
    }
  }

  try {
    // Check determinism and build fast transition lookup:
    //   next[q][a] = target
    val next: Map<Int, Map<Σᐩ, Int>> = runParallel {
      val table = ConcurrentHashMap<Int, Map<Σᐩ, Int>>(transitions.size)

      transitions.entries.parallelStream().forEach { entry ->
        val q = entry.key
        val edges = entry.value

        val local = HashMap<Σᐩ, Int>(edges.size * 2 + 1)

        for (e in edges) {
          val label = e.label ?: error("trainDFA expects an ε-free deterministic NFA; found ε-edge from state $q")
          val old = local.putIfAbsent(label, e.target)
          require(old == null) { "Not deterministic at state $q on label $label: multiple outgoing edges" }
        }

        table[q] = local
      }

      table
    }

    val seen = AtomicInteger(0)

    // Parallel traversal of all training words.
    //
    // IntStream.collect gives each worker/task a private Counts object, so the
    // hot path uses ordinary HashMaps instead of contended ConcurrentHashMaps.
    val counts: Counts = runParallel {
      IntStream.range(0, data.size).parallel().collect({ Counts() }, { acc, i ->
          val done = seen.incrementAndGet()
          if (reportEvery > 0 && done % reportEvery == 0) println("Training DFA: $done / ${data.size}")

          val word = data[i]
          var q = start
          var ok = true

          for (tok in word) {
            val t = next[q]?.get(tok)

            if (t == null) {
              if (strict) error("Training word #$i leaves the DFA at state $q on token $tok: " + word.joinToString(" "))
              else { ok = false; break }
            } else {
              acc.incTrans(q, tok)
              q = t
            }
          }

          if (ok) {
            if (q !in finalStates && strict) error("Training word #$i ends in non-final state $q: " + word.joinToString(" "))

            if (q in finalStates) { acc.incStop(q) }
          }
        },

        { a, b -> a.mergeFrom(b) }
      )
    }

    val weightedTransitions = ConcurrentHashMap<Int, List<WFA.WeightedEdge>>()
    val finalWeights = ConcurrentHashMap<Int, Double>()

    val states = allStates.toList()

    runParallel {
      states.parallelStream().forEach { q ->
        val outs = transitions[q].orEmpty()
        val labels = outs.map { e -> e.label ?: error("trainDFA expects an ε-free DFA; found ε-edge from state $q") }

        // Enabled stochastic choices from q: all outgoing labels, plus EOS if final.
        val k = labels.size + if (q in finalStates) 1 else 0
        if (k == 0) return@forEach

        val cTrans: Map<Σᐩ, Long> = counts.transCounts[q] ?: emptyMap()
        val cStop = counts.stopCounts[q] ?: 0L

        val totalObserved =
          labels.sumOf { a -> (cTrans[a] ?: 0L).toDouble() } +
              if (q in finalStates) cStop.toDouble() else 0.0

        val denom = totalObserved + alpha * k

        if (outs.isNotEmpty()) {
          weightedTransitions[q] = outs.map { e ->
            val a = e.label!!
            val p = ((cTrans[a] ?: 0L).toDouble() + alpha) / denom

            WFA.WeightedEdge(label = a, target = e.target, weight = ln(p))
          }
        }

        if (q in finalStates) finalWeights[q] = ln((cStop.toDouble() + alpha) / denom)
      }
    }

    return WFA(
      startWeights = mapOf(start to 0.0),
      finalWeights = finalWeights,
      transitions = weightedTransitions
    )
  } finally { pool.shutdown() }
}

const val WDFA_MAGIC: Int = 0x41464457 // "WDFA" little-endian-ish
const val WDFA_VERSION: Int = 1
const val WDFA_INF: Int = 0x3fffffff

fun negLogCost(w: Double, scale: Int): Int {
  if (!w.isFinite()) return WDFA_INF
  val c = (-w * scale).roundToInt()
  require(c >= 0) {
    "Expected log-weights <= 0 for GPU cost encoding; got weight=$w"
  }
  return c.coerceAtMost(WDFA_INF)
}

/**
 * Serializes a deterministic, epsilon-free, token-level WFA/WDFA.
 *
 * Assumes edge labels are CFG terminals and cfg.tmMap[label] is the same
 * 1-based token id written into sampled packets. If cfg.tmMap is 0-based
 * in your build, change `tok = ...` to `tok = ... + 1`.
 */
fun WFA.toWDFAInts(cfg: CFG, scale: Int = 1000, missingPenalty: Double = -20.0): IntArray {
  require(startWeights.size == 1) { "GPU WDFA path expects exactly one start state" }
  require(transitions.values.none { es -> es.any { it.label == null } }) { "GPU WDFA path expects ε-free automata" }

  val states = allStates.toIntArray().also { it.sort() }
  val n = states.size
  val dense = HashMap<Int, Int>(n * 2)
  for (i in states.indices) dense[states[i]] = i

  val startOrig = startWeights.keys.single()
  val start = dense[startOrig] ?: error("Missing dense start state: $startOrig")
  val startCost = negLogCost(startWeights.getValue(startOrig), scale)
  val missingCost = negLogCost(missingPenalty, scale)

  val finalCost = IntArray(n) { WDFA_INF }
  for ((q0, w) in finalWeights) {
    val q = dense[q0] ?: continue
    finalCost[q] = negLogCost(w, scale)
  }

  val rowOffset = IntArray(n + 1)
  val edgeTok = ArrayList<Int>()
  val edgeDst = ArrayList<Int>()
  val edgeCost = ArrayList<Int>()

  for (q in 0 until n) {
    val q0 = states[q]
    val outs = transitions[q0].orEmpty()

    val row = ArrayList<Triple<Int, Int, Int>>(outs.size)
    val seen = HashSet<Int>()

    for (e in outs) {
      val lab = e.label ?: error("epsilon edge not allowed")
      val tok = (cfg.tmMap[lab] ?: error("WDFA label not in cfg.tmMap: $lab")) + 1
      require(tok > 0) { "Expected packet-compatible 1-based token id for '$lab', got $tok" }
      require(seen.add(tok)) { "Non-deterministic WDFA row: state=$q0 has duplicate token=$tok label=$lab" }

      val dst = dense[e.target] ?: error("Missing dense target state: ${e.target}")
      row += Triple(tok, dst, negLogCost(e.weight, scale))
    }

    row.sortBy { it.first }

    rowOffset[q] = edgeTok.size
    for ((tok, dst, cost) in row) {
      edgeTok += tok
      edgeDst += dst
      edgeCost += cost
    }
  }
  rowOffset[n] = edgeTok.size

  val payloads = listOf(
    finalCost,
    rowOffset,
    edgeTok.toIntArray(),
    edgeDst.toIntArray(),
    edgeCost.toIntArray()
  )

  val constants = listOf(
    WDFA_MAGIC,
    WDFA_VERSION,
    scale,
    n,
    edgeTok.size,
    start,
    startCost,
    missingCost
  )

  val lens = payloads.map { it.size }
  val offsets = lens.runningFold(0) { acc, len -> acc + len }.dropLast(1)

  val header = ArrayList<Int>(constants.size + payloads.size * 2)
  header += constants
  for (i in payloads.indices) { header += offsets[i]; header += lens[i] }

  val out = IntArray(header.size + lens.sum())
  var p = 0
  for (x in header) out[p++] = x
  for (buf in payloads) { for (x in buf) out[p++] = x }

  return out
}

fun WFA.writeWDFA(file: String, cfg: CFG, scale: Int = 1000, missingPenalty: Double = -20.0) {
  val ints = toWDFAInts(cfg, scale, missingPenalty)
  val bb = ByteBuffer .allocate(ints.size * 4).order(ByteOrder.LITTLE_ENDIAN)

  for (x in ints) bb.putInt(x)
  File(file).writeBytes(bb.array())
}