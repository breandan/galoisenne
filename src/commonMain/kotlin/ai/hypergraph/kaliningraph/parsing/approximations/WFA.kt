package ai.hypergraph.kaliningraph.parsing.approximations

import ai.hypergraph.kaliningraph.automata.DFSM
import ai.hypergraph.kaliningraph.parsing.*
import ai.hypergraph.kaliningraph.parsing.NFA.Companion.toNFA
import kotlin.collections.get
import kotlin.math.*
import kotlin.time.TimeSource

data class WFA(
  val startWeights: Map<Int, Double>,
  val finalWeights: Map<Int, Double>,
  val transitions: Map<Int, List<WeightedEdge>>
) {
  data class WeightedEdge(val label: String?, val target: Int, val weight: Double)

  val allStates by lazy {
    transitions.keys +
        transitions.values.flatMap { it.map { e -> e.target } } +
        startWeights.keys + finalWeights.keys
  }

  fun summary(): String =
    "(states=${allStates.size}, transition=${transitions.values.sumOf { it.size }})"

  // ----------------------------
  // Fast logAddExp (LUT-based)
  // ----------------------------
  private object LogAddLUT {
    // x = (max - min) in [0, MAX_X]; we approximate ln(1 + exp(-x))
    private const val MAX_X = 50.0
    private const val RES = 1024              // 1/1024 resolution
    private const val INV_RES = 1.0 / RES
    private val TABLE: DoubleArray = run {
      val n = (MAX_X * RES).toInt()
      DoubleArray(n + 2) { i ->
        val x = i.toDouble() * INV_RES
        // ln(1 + exp(-x))
        ln1p(exp(-x))
      }
    }

    inline fun add(a: Double, b: Double): Double {
      if (a == Double.NEGATIVE_INFINITY) return b
      if (b == Double.NEGATIVE_INFINITY) return a

      val hi = if (a >= b) a else b
      val lo = if (a >= b) b else a
      val x = hi - lo // >= 0

      if (x >= MAX_X) return hi // ln(1+exp(-x)) ~ 0

      val t = x * RES
      val i = t.toInt()
      val frac = t - i
      val v0 = TABLE[i]
      val v1 = TABLE[i + 1]
      val corr = v0 + (v1 - v0) * frac

      return hi + corr
    }
  }

  // ----------------------------
  // Compiled structures for fast scoring
  // ----------------------------
  private class OutIndex(
    val syms: IntArray,
    val offsets: IntArray,
    val targets: IntArray,
    val weights: DoubleArray,
    val hashKeys: IntArray, // empty => linear path
    val hashPos: IntArray
  ) {
    inline fun findSym(symId: Int): Int {
      if (hashKeys.isEmpty()) {
        for (i in syms.indices) if (syms[i] == symId) return i
        return -1
      }

      val mask = hashKeys.size - 1
      var idx = mix32(symId) and mask
      while (true) {
        val k = hashKeys[idx]
        if (k == -1) return -1
        if (k == symId) return hashPos[idx]
        idx = (idx + 1) and mask
      }
    }
  }

  private data class Compiled(
    val detAscii: DeterministicAscii?,
    val charToId: IntArray,          // size 128, -1 => unknown
    val out: Array<OutIndex?>,
    val startStates: IntArray,
    val startW: DoubleArray,
    val finalW: DoubleArray
  )

  private val compiled: Compiled by lazy { compileForScore() }

  private data class DeterministicAscii(
    val startState: Int,
    val startWeight: Double,
    val next: IntArray,      // n * 128, -1 => missing
    val edgeW: DoubleArray,  // n * 128
    val finalW: DoubleArray
  )

  private fun compileForScore(): Compiled {
    val stateIds = allStates.toIntArray().also { it.sort() }
    val n = stateIds.size
    val toDense = HashMap<Int, Int>(n * 2)
    for (i in stateIds.indices) toDense[stateIds[i]] = i

    // Reserve 0..127 for single-char ASCII labels directly.
    var nextExtraSid = 128
    val extraSymIds = HashMap<String, Int>(256)

    fun symId(s: String): Int {
      if (s.length == 1) {
        val c = s[0].code
        if (c in 0..127) return c
      }
      return extraSymIds.getOrPut(s) { nextExtraSid++ }
    }

    val charToId = IntArray(128) { -1 }
    val out = arrayOfNulls<OutIndex>(n)

    // Fast path candidate: exactly one start, epsilon-free, one edge per ASCII char per state
    var detOk = startWeights.size == 1
    val detNext = IntArray(n * 128) { -1 }
    val detEdgeW = DoubleArray(n * 128)

    for ((srcId, edges) in transitions) {
      val src = toDense[srcId] ?: continue

      val bySymT = HashMap<Int, MutableList<Int>>()
      val bySymW = HashMap<Int, MutableList<Double>>()

      for (e in edges) {
        val lab = e.label
        if (lab == null) {
          detOk = false
          continue
        }

        val sid = symId(lab)
        if (lab.length == 1) {
          val c = lab[0].code
          if (c in 0..127) charToId[c] = sid
        }

        val tgt = toDense[e.target] ?: continue
        bySymT.getOrPut(sid) { ArrayList() }.add(tgt)
        bySymW.getOrPut(sid) { ArrayList() }.add(e.weight)
      }

      if (bySymT.isEmpty()) continue

      val k = bySymT.size
      val syms = IntArray(k)
      val offsets = IntArray(k + 1)

      var pos = 0
      var total = 0
      for ((sid, ts) in bySymT) {
        syms[pos] = sid
        offsets[pos] = total
        total += ts.size
        pos++
      }
      offsets[k] = total

      val targets = IntArray(total)
      val weights = DoubleArray(total)

      for (i in 0 until k) {
        val sid = syms[i]
        val ts = bySymT[sid]!!
        val ws = bySymW[sid]!!
        var p = offsets[i]
        for (j in ts.indices) {
          targets[p] = ts[j]
          weights[p] = ws[j]
          p++
        }

        if (detOk) {
          if (sid !in 0..127 || ts.size != 1) {
            detOk = false
          } else {
            val idx = src * 128 + sid
            if (detNext[idx] != -1) {
              detOk = false
            } else {
              detNext[idx] = ts[0]
              detEdgeW[idx] = ws[0]
            }
          }
        }
      }

      val (hashKeys, hashPos) =
        if (k <= 8) IntArray(0) to IntArray(0)
        else buildSymHash(syms)

      out[src] = OutIndex(syms, offsets, targets, weights, hashKeys, hashPos)
    }

    val startPairs = ArrayList<Pair<Int, Double>>(startWeights.size)
    for ((sId, w) in startWeights) {
      val s = toDense[sId] ?: continue
      startPairs.add(s to w)
    }

    val startStates = IntArray(startPairs.size)
    val startW = DoubleArray(startPairs.size)
    for (i in startPairs.indices) {
      startStates[i] = startPairs[i].first
      startW[i] = startPairs[i].second
    }

    val finalW = DoubleArray(n) { Double.NEGATIVE_INFINITY }
    for ((sId, w) in finalWeights) {
      val s = toDense[sId] ?: continue
      finalW[s] = w
    }

    val detAscii =
      if (detOk && startStates.size == 1) {
        DeterministicAscii(
          startState = startStates[0],
          startWeight = startW[0],
          next = detNext,
          edgeW = detEdgeW,
          finalW = finalW
        )
      } else null

    return Compiled(
      detAscii = detAscii,
      charToId = charToId,
      out = out,
      startStates = startStates,
      startW = startW,
      finalW = finalW
    )
  }

  fun scoreString(s: CharSequence, penalty: Double = -20.0): Double {
    val C = compiled
    val det = C.detAscii
    return if (det != null) scoreDetAscii(det, s, penalty) else scoreGeneral(C, s, penalty)
  }

  private fun scoreDetAscii(C: DeterministicAscii, s: CharSequence, penalty: Double): Double {
    var st = C.startState
    var score = C.startWeight

    val next = C.next
    val edgeW = C.edgeW

    var i = 0
    val len = s.length
    while (i < len) {
      val code = s[i].code
      if (code !in 0..127) {
        score += penalty
        i++
        continue
      }

      val idx = st * 128 + code
      val dst = next[idx]
      if (dst < 0) {
        score += penalty
      } else {
        score += edgeW[idx]
        st = dst
      }
      i++
    }

    val fw = C.finalW[st]
    return if (fw == Double.NEGATIVE_INFINITY) Double.NEGATIVE_INFINITY else score + fw
  }

  private fun scoreGeneral(C: Compiled, s: CharSequence, penalty: Double): Double {
    val n = C.finalW.size
    if (C.startStates.isEmpty()) return Double.NEGATIVE_INFINITY

    var curScores = DoubleArray(n) { Double.NEGATIVE_INFINITY }
    var nextScores = DoubleArray(n) { Double.NEGATIVE_INFINITY }
    var curActive = IntArray(n)
    var nextActive = IntArray(n)
    var curN = 0

    for (i in C.startStates.indices) {
      val st = C.startStates[i]
      val w = C.startW[i]
      val prev = curScores[st]
      if (prev == Double.NEGATIVE_INFINITY) {
        curScores[st] = w
        curActive[curN++] = st
      } else {
        curScores[st] = LogAddLUT.add(prev, w)
      }
    }
    if (curN == 0) return Double.NEGATIVE_INFINITY

    var j = 0
    val len = s.length
    while (j < len) {
      val code = s[j].code
      val sid = if (code in 0..127) C.charToId[code] else -1

      if (sid < 0) {
        for (i in 0 until curN) {
          val st = curActive[i]
          curScores[st] += penalty
        }
        j++
        continue
      }

      var nextN = 0
      var anyTransition = false

      for (i in 0 until curN) {
        val src = curActive[i]
        val srcScore = curScores[src]
        val out = C.out[src] ?: continue
        val pos = out.findSym(sid)
        if (pos < 0) continue

        anyTransition = true
        val from = out.offsets[pos]
        val to = out.offsets[pos + 1]
        for (p in from until to) {
          val tgt = out.targets[p]
          val newScore = srcScore + out.weights[p]
          val prev = nextScores[tgt]
          if (prev == Double.NEGATIVE_INFINITY) {
            nextScores[tgt] = newScore
            nextActive[nextN++] = tgt
          } else {
            nextScores[tgt] = LogAddLUT.add(prev, newScore)
          }
        }
      }

      if (!anyTransition) {
        for (i in 0 until curN) {
          val st = curActive[i]
          curScores[st] += penalty
        }
      } else {
        for (i in 0 until curN) {
          curScores[curActive[i]] = Double.NEGATIVE_INFINITY
        }

        val ts = curScores
        curScores = nextScores
        nextScores = ts

        val ta = curActive
        curActive = nextActive
        nextActive = ta

        curN = nextN
        if (curN == 0) return Double.NEGATIVE_INFINITY
      }

      j++
    }

    var total = Double.NEGATIVE_INFINITY
    for (i in 0 until curN) {
      val st = curActive[i]
      val fw = C.finalW[st]
      if (fw != Double.NEGATIVE_INFINITY) {
        total = LogAddLUT.add(total, curScores[st] + fw)
      }
    }
    return total
  }

  companion object {
    private fun mix32(x0: Int): Int {
      var x = x0
      x = x xor (x ushr 16)
      x *= 0x7feb352d
      x = x xor (x ushr 15)
      x *= 0x846ca68b.toInt()
      x = x xor (x ushr 16)
      return x
    }

    private fun buildSymHash(syms: IntArray): Pair<IntArray, IntArray> {
      var cap = 1
      while (cap < syms.size * 2) cap = cap shl 1

      val keys = IntArray(cap) { -1 }
      val pos = IntArray(cap)
      val mask = cap - 1

      for (i in syms.indices) {
        val sym = syms[i]
        var slot = mix32(sym) and mask
        while (keys[slot] != -1) {
          slot = (slot + 1) and mask
        }
        keys[slot] = sym
        pos[slot] = i
      }

      return keys to pos
    }
  }

  fun toGraphviz(
    name: String = "WFA",
    pruneThreshold: Double = 0.00, // Don't show edges with p < 1%
    topK: Int = 9                  // Show top K edges per state (both in and out)
  ): String = buildString {
    val prec = 4
    appendLine("digraph $name {")
    appendLine("  rankdir=LR;")
    appendLine("  node [shape = circle, fontname = \"Helvetica\"];")
    appendLine("  edge [fontname = \"Helvetica\"];")

    // --- 1. Indexing & Pre-calculation ---
    // Map: Target -> List<Pair<Source, Edge>>
    val incomingMap = HashMap<Int, MutableList<Pair<Int, WeightedEdge>>>()
    transitions.forEach { (src, edges) ->
      edges.forEach { edge ->
        incomingMap.getOrPut(edge.target) { ArrayList() }.add(src to edge)
      }
    }

    // --- 2. Pass 1: Forward Top-K (Standard) ---
    // Helper data class to track unique edges to draw
    data class DrawEdge(val src: Int, val edge: WeightedEdge, val isBackfill: Boolean = false)
    val edgesToRender = HashSet<DrawEdge>()

    transitions.forEach { (src, edges) ->
      edges.sortedByDescending { it.weight }
        .filter { it.weight >= pruneThreshold }
        .take(topK)
        .forEach { edgesToRender.add(DrawEdge(src, it)) }
    }

    // --- 3. Pass 2: Orphan Rescue (Incoming Backfill) ---
    // An "orphan" is a node that is currently rendered (active) but has no visible incoming edges.
    // We explicitly fetch incoming edges for these to prevent them from looking like start states.

    // Calculate currently visible targets from Pass 1
    val pass1Targets = edgesToRender.map { it.edge.target }.toSet()

    // The set of nodes currently "in the picture"
    val activeNodes = (edgesToRender.map { it.src } + pass1Targets + startWeights.keys + finalWeights.keys).toSet()

    // Identify orphans: Active nodes that are NOT start states and NOT visible targets
    val orphans = activeNodes.filter { node ->
      !startWeights.containsKey(node) && !pass1Targets.contains(node)
    }

    orphans.forEach { orphan ->
      val allIncoming = incomingMap[orphan] ?: emptyList()

      // Select top K incoming edges for this orphan
      // These are added REGARDLESS of whether the source has used its Top-K quota.
      allIncoming.sortedByDescending { it.second.weight }
        .filter { it.second.weight >= pruneThreshold }
        .take(topK)
        .forEach { (src, edge) -> edgesToRender.add(DrawEdge(src, edge, isBackfill = true)) }
    }

    // --- 4. Render Nodes ---
    // Recalculate rendered states (Backfill might have added new sources)
    val finalRenderedStates = (edgesToRender.map { it.src } + edgesToRender.map { it.edge.target } + startWeights.keys + finalWeights.keys).toSet()

    // Render Final States
    finalWeights.forEach { (s, w) ->
      if (s in finalRenderedStates && w > pruneThreshold) {
        val wStr = w.toString().take(3)
        appendLine("  $s [shape = doublecircle, xlabel=\"$wStr\"];")
      }
    }

    // Render Start States
    startWeights.forEach { (s, w) ->
      if (s in finalRenderedStates && w > pruneThreshold) {
        appendLine("  __start_$s [shape=point, style=invisible];")
        appendLine("  __start_$s -> $s [label=\"${w.toString().take(prec)}\"];")
      }
    }

    // --- 5. Render Edges ---
    // Group by (Source, Target) to merge parallel edges into one label
    val groupedEdges = edgesToRender.groupBy { it.src to it.edge.target }

    groupedEdges.forEach { (key, drawEdges) ->
      val (src, target) = key

      // Consolidated label
      val labelText = drawEdges.joinToString("\\n") {
        val sym = it.edge.label ?: "ε"
        "$sym ${it.edge.weight.toString().take(prec)}"
      }

      // Thicker lines for higher total probability
      val totalWeight = drawEdges.sumOf { it.edge.weight }
      val penwidth = 1.0 + (totalWeight * 3.0)

      // Use a dashed style if these are purely backfilled edges (optional, removed for consistency)
      // appendLine("  $src -> $target [label=\"$labelText\", penwidth=$penwidth];")
      appendLine("  $src -> $target [label=\"$labelText\", penwidth=$penwidth];")
    }

    // --- 6. Render Residuals (Summaries) ---
    // We draw dashed lines for any weight NOT accounted for by the explicit edges above.

    // A. Outgoing Residuals
    finalRenderedStates.forEach { src ->
      val allOutgoing = transitions[src] ?: emptyList()
      val drawnOutgoing = edgesToRender.filter { it.src == src }.map { it.edge }.toSet()

      val hiddenEdges = allOutgoing.filter { it !in drawnOutgoing }

      if (hiddenEdges.isNotEmpty()) {
        val hiddenWeight = hiddenEdges.sumOf { it.weight }
        if (hiddenWeight > pruneThreshold) {
          appendLine("  $src -> __pruned_out_$src [label=\"<${hiddenEdges.size} others>\\nSUM: ${hiddenWeight.toString().take(prec)}\", style=dotted, fontcolor=gray, color=gray];")
          appendLine("  __pruned_out_$src [shape=point, style=invisible];")
        }
      }
    }

    // B. Incoming Residuals
    finalRenderedStates.forEach { target ->
      if (!startWeights.containsKey(target)) {
        val allIncoming = incomingMap[target] ?: emptyList()
        val drawnIncoming = edgesToRender.filter { it.edge.target == target }.map { it.edge }.toSet()

        val hiddenIncoming = allIncoming.filter { it.second !in drawnIncoming }

        if (hiddenIncoming.isNotEmpty()) {
          val hiddenWeight = hiddenIncoming.sumOf { it.second.weight }
          if (hiddenWeight > pruneThreshold) {
            val label = "<${hiddenIncoming.size} others>\\nSUM: ${hiddenWeight.toString().take(prec)}"
            appendLine("  __hidden_in_$target [shape=point, style=invisible];")
            appendLine("  __hidden_in_$target -> $target [label=\"$label\", style=dotted, fontcolor=gray, color=gray];")
          }
        }
      }
    }

    appendLine("}")
  }
}

class KneserNeyLM(
  val maxOrder: Int,
  val counts: Map<List<String>, Double>,
  val discount: Double = 0.75
) {
  // Determine maxOrder from data if not provided
  constructor(counts: Map<List<String>, Double>) : this(
    maxOrder = counts.keys.maxOfOrNull { it.size } ?: 1,
    counts = counts
  )

  // c(h) = Sum of counts of all n-grams starting with h
  // Maps history -> Total count of occurrences
  private val contextCount: Map<List<String>, Double> = buildMap {
    for ((ng, c) in counts) {
      if (ng.isNotEmpty()) {
        val h = ng.dropLast(1)
        put(h, (get(h) ?: 0.0) + c)
      }
    }
  }

  // N1+(h*) = Number of unique word types that follow history h
  private val numTypesAfter: Map<List<String>, Int> = run {
    val types = HashMap<List<String>, HashSet<String>>()
    for (ng in counts.keys) {
      if (ng.isNotEmpty()) {
        types.getOrPut(ng.dropLast(1)) { HashSet() }.add(ng.last())
      }
    }
    types.mapValues { it.value.size }
  }

  // N1+(*w) = Number of unique histories (specifically bigram predecessors) for w
  // Used for the unigram continuation probability
  private val numTypesBeforeWord: Map<String, Int> = run {
    val types = HashMap<String, HashSet<String>>()
    for (ng in counts.keys) {
      if (ng.size == 2) { // Standard KN uses bigram counts for unigram continuation
        types.getOrPut(ng.last()) { HashSet() }.add(ng.first())
      }
    }
    types.mapValues { it.value.size }
  }

  private val totalBigramTypes: Double =
    counts.keys.count { it.size == 2 }.toDouble().coerceAtLeast(1.0)

  // Base recursion case: Continuation Unigram P_cont(w)
  private fun pContUnigram(w: String): Double {
    val count = numTypesBeforeWord[w] ?: 0
    return if (count > 0) count / totalBigramTypes else 1e-9 // Minimal floor
  }

  /**
   * Calculates P(next | history) using Interpolated Kneser-Ney smoothing.
   */
  fun prob(next: String, history: List<String>): Double {
    // 1. Truncate history to max effective length (Order - 1)
    val h = if (history.size >= maxOrder) history.takeLast(maxOrder - 1) else history

    // 2. Base Case: Empty history -> Unigram continuation
    if (h.isEmpty()) return pContUnigram(next)

    // 3. Recursive Step
    val hw = h + next
    val c_hw = counts[hw] ?: 0.0      // Count of exact n-gram
    val c_h = contextCount[h] ?: 0.0  // Count of history

    // If history is unseen, backoff immediately
    if (c_h <= 0.0) return prob(next, h.drop(1))

    // Absolute Discounting
    val first = max(c_hw - discount, 0.0) / c_h

    // Interpolation weight (Lambda)
    val distinctContinuations = numTypesAfter[h] ?: 0
    val lambda = (discount * distinctContinuations) / c_h

    return first + lambda * prob(next, h.drop(1))
  }
}

/**
 * Wrapper for List<String> to be used as a HashMap key.
 * Can be optimized with integer packing later if needed.
 */
data class Hist(val toks: List<String>)

fun NFA.toWFAWithLM(
  lm: KneserNeyLM,
  bos: String = "BOS",
  eos: String = "EOS"
): WFA {
  // 1. Context Inference
  // We need to determine "What word history does state S represent?"
  // Since we cannot change topology, we infer history from incoming edges.
  // Map<StateID, List<String>>
  val stateContexts = HashMap<Int, List<String>>()

  // Initialize start states with BOS (Beginning of Sentence) context
  // The context size should ideally be maxOrder - 1
  val startCtx = listOf(bos)
  for (s in startStates) {
    stateContexts[s] = startCtx
  }

  // Propagate context via BFS/Topological traversal
  // If a state has multiple incoming edges with conflicting histories,
  // strictly choosing one is heuristic. We choose the *longest common suffix*
  // or simply the first valid one found, to avoid exploding the state space.
  val queue = ArrayDeque<Int>()
  queue.addAll(startStates)
  val visited = HashSet<Int>()
  startStates.forEach { visited.add(it) }

  // We might need to iterate multiple times if there are cycles, but for labeling
  // we just need *a* valid history. A simple traversal suffices for consistent labeling.
  // Note: For a true n-gram NFA, the structure guarantees unique suffixes.
  while (queue.isNotEmpty()) {
    val u = queue.removeFirst()
    val h = stateContexts[u] ?: emptyList()

    transitions[u]?.forEach { edge ->
      val v = edge.target
      if (edge.label != null && !visited.contains(v)) {
        // Append label to history, keeping only N-1 length
        val nextHistory = (h + edge.label).takeLast(lm.maxOrder - 1)
        stateContexts[v] = nextHistory
        visited.add(v)
        queue.add(v)
      } else if (edge.label == null && !visited.contains(v)) {
        // Epsilon transition: inherit history
        stateContexts[v] = h
        visited.add(v)
        queue.add(v)
      }
    }
  }

  // 2. Build Weighted Transitions
  val newTransitions = HashMap<Int, MutableList<WFA.WeightedEdge>>()

  for ((u, edges) in transitions) {
    // If we couldn't reach this state or infer context, default to empty or BOS
    val history = stateContexts[u] ?: listOf(bos)

    val weightedEdges = edges.map { edge ->
      val label = edge.label
      val weight = if (label == null) {
        // Epsilon transitions: Usually probability 1.0 (log prob 0.0) in standard FSTs
        // unless specific epsilon-weights are modeled.
        1.0
      } else {
        // Kneser-Ney Probability P(label | history)
        lm.prob(label, history)
      }
      WFA.WeightedEdge(label, edge.target, weight)
    }

    // 3. Local Normalization (Optional but recommended for stochastic WFAs)
    // Ensures sum of outgoing probabilities = 1.0
    val totalWeight = weightedEdges.sumOf { it.weight }

    val normalizedEdges = if (totalWeight > 0.0) {
      weightedEdges.map { it.copy(weight = it.weight / totalWeight) }
    } else {
      // If total is 0 (shouldn't happen with smoothing), uniform distribution
      val uni = 1.0 / weightedEdges.size
      weightedEdges.map { it.copy(weight = uni) }
    }

    newTransitions[u] = normalizedEdges.toMutableList()
  }

  // 4. Handle Final Weights (Probability of EOS)
  val newFinalWeights = HashMap<Int, Double>()
  for (f in finalStates) {
    val h = stateContexts[f] ?: listOf(bos)
    // P(EOS | history)
    newFinalWeights[f] = lm.prob(eos, h)
  }

  return WFA(
    startWeights = startStates.associateWith { 1.0 }, // Assuming uniform start or handled by BOS context
    finalWeights = newFinalWeights,
    transitions = newTransitions
  )
}

fun makeWFA(cfg: CFG, freqNgrams: Map<List<String>, Double>, allNgrams: Set<List<String>>): WFA {
  val timer = TimeSource.Monotonic.markNow()
  val approx = cfg.toNederhofNFA(historyDepth = 2).removeEpsilons()
  println("Original NFA summary ${approx.summary()}")
  val freqNgrams = freqNgrams.toMutableMap()
  allNgrams.forEach { ngram -> if (ngram !in freqNgrams) freqNgrams[ngram] = 1.0 }
  val km = KneserNeyLM(freqNgrams)
  val fullNgrams = freqNgrams.entries.associate { it.key to it.value }.keys.toNFA()
  println(fullNgrams.summary())
  val intNFA = approx.intersect(fullNgrams)
  println("∩-NFA summary ${approx.summary()}")
  val minWFA = intNFA.minimize().determinize().normalize().toWFAWithLM(km)
  println("min-WFA summary ${minWFA.summary()}")
  println("Constructed WFA in ${timer.elapsedNow()}")
  return minWFA
}

/**
 * Exports the WFA to the SafeTensors format as a Byte array.
 */
fun WFA.toSafeTensors(): ByteArray {
  // --- 1. Flatten the Graph ---
  val vocabSet = mutableSetOf<String>()
  transitions.values.forEach { edges ->
    edges.forEach { edge -> edge.label?.let { vocabSet.add(it) } }
  }
  val vocabList = vocabSet.sorted()
  // 0 reserved for Epsilon
  val vocabMap = vocabList.withIndex().associate { it.value to (it.index + 1) }

  // Use Kotlin MutableLists
  val sources = mutableListOf<Int>()
  val targets = mutableListOf<Int>()
  val labels = mutableListOf<Int>()
  val scores = mutableListOf<Float>()

  val sortedStates = transitions.keys.sorted()
  for (src in sortedStates) {
    val edges = transitions[src] ?: continue
    // Sort: Target -> Label -> Weight
    val sortedEdges = edges.sortedWith(compareBy({ it.target }, { it.label ?: "" }, { it.weight }))
    for (edge in sortedEdges) {
      sources.add(src)
      targets.add(edge.target)
      labels.add(if (edge.label == null) 0 else vocabMap[edge.label]!!)
      scores.add(edge.weight.toFloat())
    }
  }

  val numEdges = sources.size
  val intSize = 4
  val floatSize = 4

  // Offsets
  val sourcesLen = numEdges * intSize
  val targetsLen = numEdges * intSize
  val labelsLen = numEdges * intSize
  val scoresLen = numEdges * floatSize

  val sourcesStart = 0
  val targetsStart = sourcesStart + sourcesLen
  val labelsStart = targetsStart + targetsLen
  val scoresStart = labelsStart + labelsLen
  val totalDataSize = scoresStart + scoresLen

  // --- 2. Construct JSON Header ---
  fun toJsonList(list: List<Any>): String = list.joinToString(",", "[", "]") {
    when (it) {
      is String -> {
        // CRITICAL: Escape backslashes first, then quotes, then controls
        val escaped = it.replace("\\", "\\\\")
          .replace("\"", "\\\"")
          .replace("\n", "\\n")
          .replace("\r", "\\r")
          .replace("\t", "\\t")
        "\"$escaped\""
      }
      else -> it.toString()
    }
  }

  val sKeys = startWeights.keys.sorted()
  val fKeys = finalWeights.keys.sorted()

  // Note: Start/Final weights are doubles, but we serialize as floats for compactness in metadata if desired,
  // or keep as doubles in JSON text. JSON text handles standard numbers fine.
  val metadata = """
        "vocab":${toJsonList(vocabList)},
        "start_states":${toJsonList(sKeys)},
        "start_weights":${toJsonList(sKeys.map { startWeights[it]!! })},
        "final_states":${toJsonList(fKeys)},
        "final_weights":${toJsonList(fKeys.map { finalWeights[it]!! })}
    """.trimIndent().replace("\n", "")

  fun tensorJson(name: String, dtype: String, start: Int, end: Int): String {
    return "\"$name\":{\"dtype\":\"$dtype\",\"shape\":[$numEdges],\"data_offsets\":[$start,$end]}"
  }

  val headerJson = """{
        "__metadata__":{$metadata},
        ${tensorJson("sources", "I32", sourcesStart, targetsStart)},
        ${tensorJson("targets", "I32", targetsStart, labelsStart)},
        ${tensorJson("labels", "I32", labelsStart, scoresStart)},
        ${tensorJson("scores", "F32", scoresStart, totalDataSize)}
    }""".trimIndent().replace("\n", "").replace(" ", "")

  val headerBytes = headerJson.encodeToByteArray()
  val headerLen = headerBytes.size.toLong()

  // --- 3. Write to ByteArray ---
  val totalSize = 8 + headerBytes.size + totalDataSize
  val result = ByteArray(totalSize)
  var pos = 0

  // KMP Helper: Write Little Endian
  fun writeLongLE(v: Long) {
    result[pos++] = (v).toByte()
    result[pos++] = (v ushr 8).toByte()
    result[pos++] = (v ushr 16).toByte()
    result[pos++] = (v ushr 24).toByte()
    result[pos++] = (v ushr 32).toByte()
    result[pos++] = (v ushr 40).toByte()
    result[pos++] = (v ushr 48).toByte()
    result[pos++] = (v ushr 56).toByte()
  }

  fun writeIntLE(v: Int) {
    result[pos++] = (v).toByte()
    result[pos++] = (v ushr 8).toByte()
    result[pos++] = (v ushr 16).toByte()
    result[pos++] = (v ushr 24).toByte()
  }

  fun writeFloatLE(v: Float) {
    writeIntLE(v.toBits())
  }

  // A. Header Length (u64)
  writeLongLE(headerLen)

  // B. Header JSON
  headerBytes.copyInto(result, pos)
  pos += headerBytes.size

  // C. Tensors
  sources.forEach { writeIntLE(it) }
  targets.forEach { writeIntLE(it) }
  labels.forEach { writeIntLE(it) }
  scores.forEach { writeFloatLE(it) }

  return result
}

/**
 * Decodes a SafeTensors ByteArray to a WFA.
 */
fun ByteArray.toWFA(): WFA {
  // --- 1. Byte Reader Helpers (Little Endian) ---
  var pos = 0
  fun readLongLE(): Long {
    if (pos + 8 > size) throw IndexOutOfBoundsException("Buffer too small for header length")
    val v = (this[pos].toLong() and 0xFF) or
        ((this[pos + 1].toLong() and 0xFF) shl 8) or
        ((this[pos + 2].toLong() and 0xFF) shl 16) or
        ((this[pos + 3].toLong() and 0xFF) shl 24) or
        ((this[pos + 4].toLong() and 0xFF) shl 32) or
        ((this[pos + 5].toLong() and 0xFF) shl 40) or
        ((this[pos + 6].toLong() and 0xFF) shl 48) or
        ((this[pos + 7].toLong() and 0xFF) shl 56)
    pos += 8
    return v
  }

  val headerLen = readLongLE().toInt()
  if (pos + headerLen > size) throw IndexOutOfBoundsException("Buffer too small for header JSON")

  val headerJson = this.decodeToString(startIndex = pos, endIndex = pos + headerLen)
  val dataStart = pos + headerLen

  // --- 2. Robust JSON Parsing (State Machine) ---

  // Robustly parses ["str1", "str2", ...] handling escapes \" and brackets ] inside strings
  fun parseJsonStringList(key: String): List<String> {
    val keySearch = "\"$key\":"
    val startIdx = headerJson.indexOf(keySearch)
    if (startIdx == -1) return emptyList()

    // Find start of list '['
    val arrayStart = headerJson.indexOf('[', startIdx)
    if (arrayStart == -1) return emptyList()

    val result = ArrayList<String>()
    val sb = StringBuilder()
    var inString = false
    var isEscaped = false

    // Scan starting after '['
    for (i in arrayStart + 1 until headerJson.length) {
      val char = headerJson[i]

      if (inString) {
        if (isEscaped) {
          // Handle standard JSON escapes
          when (char) {
            'n' -> sb.append('\n')
            'r' -> sb.append('\r')
            't' -> sb.append('\t')
            'b' -> sb.append('\b')
            'f' -> sb.append('\u000c') // Form feed
            '\\' -> sb.append('\\')
            '/' -> sb.append('/')
            '"' -> sb.append('"')
            'u' -> { /* Unicode escapes tricky without full parser, usually not needed for simple WFA */ sb.append("\\u") }
            else -> sb.append(char)
          }
          isEscaped = false
        } else {
          if (char == '\\') {
            isEscaped = true
          } else if (char == '"') {
            // End of current string
            inString = false
            result.add(sb.toString())
            sb.setLength(0) // Clear buffer
          } else {
            sb.append(char)
          }
        }
      } else {
        // Outside strings
        if (char == '"') {
          inString = true
        } else if (char == ']') {
          // End of array found
          return result
        }
        // Ignore commas and whitespace between strings
      }
    }
    return result
  }

  // Simple parser for number lists (Integers/Doubles don't contain brackets/quotes, so Regex is safe enough here)
  fun parseNumberList(key: String): List<String> {
    val match = "\"$key\":\\[(.*?)\\]".toRegex().find(headerJson) ?: return emptyList()
    val content = match.groupValues[1]
    if (content.isBlank()) return emptyList()
    return content.split(",")
  }

  fun getTensorOffsets(name: String): Pair<Int, Int> {
    // Look for: "name":{ ... "data_offsets":[start,end] ... }
    // We use a specific regex that skips nested braces if necessary, but standard SafeTensors header is flat.
    val regex = "\"$name\":\\{.*?\"data_offsets\":\\[(\\d+),(\\d+)\\].*?\\}".toRegex()
    val match = regex.find(headerJson) ?: throw IllegalArgumentException("Tensor $name not found")
    val (start, end) = match.destructured
    return start.toInt() to end.toInt()
  }

  // --- 3. Parse Metadata ---
  val vocabList = parseJsonStringList("vocab") // <--- Uses robust parser

  val startStates = parseNumberList("start_states").map { it.trim().toInt() }
  val startWeightsList = parseNumberList("start_weights").map { it.trim().toDouble() }
  val finalStates = parseNumberList("final_states").map { it.trim().toInt() }
  val finalWeightsList = parseNumberList("final_weights").map { it.trim().toDouble() }

  val sMap = startStates.zip(startWeightsList).toMap()
  val fMap = finalStates.zip(finalWeightsList).toMap()

  // --- 4. Parse Tensors ---
  val (srcStart, srcEnd) = getTensorOffsets("sources")
  val (tgtStart, tgtEnd) = getTensorOffsets("targets")
  val (lblStart, lblEnd) = getTensorOffsets("labels")
  val (scrStart, scrEnd) = getTensorOffsets("scores")

  val numEdges = (srcEnd - srcStart) / 4

  // Helpers for random access reading
  fun readIntAt(offset: Int): Int {
    val p = dataStart + offset
    if (p + 4 > size) throw IndexOutOfBoundsException("Data truncated")
    return (this[p].toInt() and 0xFF) or
        ((this[p + 1].toInt() and 0xFF) shl 8) or
        ((this[p + 2].toInt() and 0xFF) shl 16) or
        ((this[p + 3].toInt() and 0xFF) shl 24)
  }

  fun readFloatAt(offset: Int): Float {
    return Float.fromBits(readIntAt(offset))
  }

  // --- 5. Reconstruct Graph ---
  val transitions = HashMap<Int, MutableList<WFA.WeightedEdge>>()

  var currentSrc = srcStart
  var currentTgt = tgtStart
  var currentLbl = lblStart
  var currentScr = scrStart

  for (i in 0 until numEdges) {
    val src = readIntAt(currentSrc)
    val tgt = readIntAt(currentTgt)
    val lblIdx = readIntAt(currentLbl)
    val weight = readFloatAt(currentScr)

    currentSrc += 4
    currentTgt += 4
    currentLbl += 4
    currentScr += 4

    // SAFETY CHECK: Ensure index is valid
    val label = if (lblIdx == 0) null else {
      val listIdx = lblIdx - 1
      if (listIdx >= vocabList.size) {
        // Fallback for debugging, or throw explicit error
        throw IndexOutOfBoundsException("Label Index $listIdx out of bounds (Vocab size: ${vocabList.size})")
      }
      vocabList[listIdx]
    }

    val edge = WFA.WeightedEdge(label, tgt, weight.toDouble())
    transitions.getOrPut(src) { ArrayList() }.add(edge)
  }

  return WFA(sMap, fMap, transitions)
}

fun DFSM.toWFA(tmLst: List<String>): WFA {
  require(width <= tmLst.size) {
    "DFSM width ($width) exceeds tmLst size (${tmLst.size})"
  }

  // Dense integer ids for WFA states. Put q_alpha first for a stable/idempotent start id.
  val stateNames = buildList {
    add(q_alpha)
    for (q in Q) if (q != q_alpha) add(q)
    for ((src, outs) in deltaMap) {
      if (src !in this) add(src)
      for (dst in outs.values) if (dst !in this) add(dst)
    }
    for (q in F) if (q !in this) add(q)
  }

  val idOf = stateNames.withIndex().associate { (i, q) -> q to i }

  val startWeights = mapOf(idOf.getValue(q_alpha) to 0.0)

  val finalWeights = buildMap {
    for (q in F) {
      val id = idOf[q]
      if (id != null) put(id, 0.0)
    }
  }

  // Only explicit DFSM transitions become WFA edges; each gets weight log(1)=0.
  val transitions = buildMap {
    for (q in stateNames) {
      val srcId = idOf.getValue(q)
      val outs = deltaMap[q]

      val edges =
        if (outs == null) emptyList()
        else outs.entries
          .sortedBy { it.key } // deterministic order
          .map { (sym, dst) ->
            require(sym in 0 until width) {
              "Symbol id $sym out of range [0, $width)"
            }

            WFA.WeightedEdge(
              label = tmLst[sym],
              target = idOf.getValue(dst),
              weight = 0.0
            )
          }

      put(srcId, edges)
    }
  }

  return WFA(
    startWeights = startWeights,
    finalWeights = finalWeights,
    transitions = transitions
  )
}

fun WFA.intersectOther(wfa2: WFA): WFA {
  require(transitions.values.none { edges -> edges.any { it.label == null } }) {
    "WFA.intersect currently requires the left automaton to be epsilon-free"
  }
  require(wfa2.transitions.values.none { edges -> edges.any { it.label == null } }) {
    "WFA.intersect currently requires the right automaton to be epsilon-free"
  }

  val productStart = mutableMapOf<Int, Double>()
  val productFinal = mutableMapOf<Int, Double>()
  val productTransitions = mutableMapOf<Int, MutableList<WFA.WeightedEdge>>()

  // Pair of source-state ids -> dense product-state id
  val pairToId = mutableMapOf<Pair<Int, Int>, Int>()
  val worklist = mutableListOf<Pair<Int, Int>>()

  fun internPair(q1: Int, q2: Int): Int {
    val key = q1 to q2
    val existing = pairToId[key]
    if (existing != null) return existing

    val id = pairToId.size
    pairToId[key] = id
    worklist.add(key)

    val f1 = this@intersectOther.finalWeights[q1]
    val f2 = wfa2.finalWeights[q2]
    if (f1 != null && f2 != null) productFinal[id] = f1 + f2

    return id
  }

  // Cross-product of start states
  for ((q1, w1) in startWeights) {
    for ((q2, w2) in wfa2.startWeights) {
      val pid = internPair(q1, q2)
      productStart[pid] = w1 + w2
    }
  }

  var head = 0
  while (head < worklist.size) {
    val (q1, q2) = worklist[head++]
    val srcId = pairToId.getValue(q1 to q2)

    val outs1 = transitions[q1].orEmpty()
    val outs2 = wfa2.transitions[q2].orEmpty()
    if (outs1.isEmpty() || outs2.isEmpty()) continue

    // Index right outgoing edges by label for faster matching
    val rightByLabel = mutableMapOf<String, MutableList<WFA.WeightedEdge>>()
    for (e2 in outs2) {
      val lab = e2.label ?: continue
      val bucket = rightByLabel[lab]
      if (bucket == null) rightByLabel[lab] = mutableListOf(e2)
      else bucket.add(e2)
    }

    var dstEdges: MutableList<WFA.WeightedEdge>? = null

    for (e1 in outs1) {
      val lab = e1.label ?: continue
      val matches = rightByLabel[lab] ?: continue

      if (dstEdges == null) dstEdges = mutableListOf()

      for (e2 in matches) {
        val tgtId = internPair(e1.target, e2.target)
        dstEdges.add(
          WFA.WeightedEdge(
            label = lab,
            target = tgtId,
            weight = e1.weight + e2.weight
          )
        )
      }
    }

    if (dstEdges != null) productTransitions[srcId] = dstEdges
  }

  return WFA(
    startWeights = productStart,
    finalWeights = productFinal,
    transitions = productTransitions
  )
}