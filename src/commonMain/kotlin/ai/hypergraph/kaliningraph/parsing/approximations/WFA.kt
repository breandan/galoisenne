package ai.hypergraph.kaliningraph.parsing.approximations

import ai.hypergraph.kaliningraph.parsing.CFG
import ai.hypergraph.kaliningraph.parsing.NFA
import ai.hypergraph.kaliningraph.parsing.NFA.Companion.toNFA
import ai.hypergraph.kaliningraph.repair.toyPython
import kotlin.math.max
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

  fun summary(): String = "(states=${allStates.size}, transition=${transitions.values.sumOf { it.size }})"

  // Deterministic lookup: state -> (symbol -> edge)
  // Precondition: DFA (at most one outgoing edge per (state, symbol)),
  // and (typically) epsilon-free or already epsilon-closed.
  private val dfaIndex: Map<Int, Map<String, WeightedEdge>> by lazy {
    transitions.mapValues { (_, edges) ->
      buildMap(edges.size) {
        for (e in edges) {
          val sym = e.label ?: continue // ignore epsilons under DFA precondition
          // If you want to enforce determinism in debug builds, uncomment:
          // require(!containsKey(sym)) { "Non-deterministic: multiple edges for ($sym)" }
          put(sym, e)
        }
      }
    }
  }

  /**
   * Scores `str` assuming this WFA is a DFA (deterministic, usually epsilon-free).
   *
   * Semantics: sum over start states of
   *   startWeight * Π edgeWeight * finalWeight
   *
   * Returns 0.0 if no accepting path exists.
   */
  fun dfaScore(str: List<String>): Double {
    var total = 0.0

    for ((start, w0) in startWeights) {
      var state = start
      var w = w0
      var alive = true

      for (sym in str) {
        val e = dfaIndex[state]?.get(sym)
        if (e == null) { alive = false; break }
        w *= e.weight
        state = e.target
      }

      if (alive) {
        val fw = finalWeights[state]
        if (fw != null) total += w * fw
      }
    }

    return total
  }

  /**
   * Viterbi Algorithm: Finds the highest weight path for a given sequence.
   * Assumes weights are probabilities (multiplicative).
   * Returns null if not accepted.
   */
  fun probabilityOf(str: List<String>): Double {
    // Current states: Map<StateID, Probability>
    var current = HashMap<Int, Double>()

    // Initialize with Epsilon Closure of Start States
    // (Simplified: assuming no epsilons at start for brevity, or pre-closed)
    for ((s, w) in startWeights) current[s] = w
    current = expandEpsilonWeights(current)

    for (symbol in str) {
      val next = HashMap<Int, Double>()
      for ((u, prob) in current) {
        transitions[u]?.forEach { edge ->
          if (edge.label == symbol) {
            val newProb = prob * edge.weight
            // Viterbi maximization
            if (newProb > (next[edge.target] ?: -1.0)) {
              next[edge.target] = newProb
            }
          }
        }
      }
      if (next.isEmpty()) return 0.0
      current = expandEpsilonWeights(next)
    }

    // Finalize
    var maxProb = 0.0
    for ((u, prob) in current) {
      val finalW = finalWeights[u]
      if (finalW != null) {
        val total = prob * finalW
        if (total > maxProb) maxProb = total
      }
    }
    return maxProb
  }

  // Helper to push weights through epsilon transitions (Max-Product)
  private fun expandEpsilonWeights(initial: Map<Int, Double>): HashMap<Int, Double> {
    val result = HashMap(initial)
    val queue = ArrayDeque(initial.keys)

    while (queue.isNotEmpty()) {
      val u = queue.removeFirst()
      val wU = result[u]!!

      transitions[u]?.forEach { edge ->
        if (edge.label == null) {
          val wNew = wU * edge.weight
          val wOld = result[edge.target] ?: -1.0
          if (wNew > wOld) {
            result[edge.target] = wNew
            queue.add(edge.target)
          }
        }
      }
    }
    return result
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
        .forEach { (src, edge) ->
          edgesToRender.add(DrawEdge(src, edge, isBackfill = true))
        }
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
  val approx = cfg.toNederhofNFA(historyDepth = 6).removeEpsilons()
  println("Original NFA summary ${approx.summary()}")
  val freqNgrams = freqNgrams.toMutableMap()
  allNgrams.forEach { ngram -> if (ngram !in freqNgrams) freqNgrams[ngram] = 1.0 }
  val km = KneserNeyLM(freqNgrams)
  val fullNgrams = freqNgrams.entries.associate { it.key to it.value }.keys.toNFA()
  println(fullNgrams.summary())
  val intNFA = approx.intersect(fullNgrams)
  println("∩-NFA summary ${approx.summary()}")
  val minWFA = intNFA.minimize().determinize().normalize().toWFAWithLM(km)
  println("min-WFA summary ${approx.summary()}")
  println("Constructed WFA in ${timer.elapsedNow()}")
  return minWFA
}