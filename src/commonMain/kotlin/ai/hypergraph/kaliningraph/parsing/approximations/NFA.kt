package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.KBitSet
import ai.hypergraph.kaliningraph.nextBigInteger
import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlin.random.Random
import kotlin.time.TimeSource

data class NFA(
  val startStates: Set<Int>,
  val finalStates: Set<Int>,
  val transitions: Map<Int, List<Edge>>
) {
  companion object {
    /**
     * Converts a set of n-grams into an NFA that strictly enforces the n-gram constraints.
     * * Requirement: The input set must be "complete" (if "abc" is present, "ab" and "bc" must also be present).
     * * Logic:
     * Let N be the maximum length of an n-gram in the set.
     * We map n-grams to edges based on their length to preserve context history:
     * 1. Growing (Length < N): Source=[w_1...w_{k-1}], Target=w_1...w_k
     * - This builds the history state up to length N-1.
     * 2. Sliding (Length == N): Source=[w_1...w_{n-1}], Target=w_2...w_n
     * - This maintains the history window of length N-1.
     */
    fun Set<List<String>>.toNFA(): NFA {
      val stateToId = mutableMapOf<List<String>, Int>()
      val transitions = mutableMapOf<Int, MutableList<NFA.Edge>>()

      fun id(context: List<String>): Int = stateToId.getOrPut(context) { stateToId.size }

      // 1. Determine the maximum context length required (N)
      val maxN = this.maxOfOrNull { it.size } ?: 0

      this.forEach { ngram ->
        if (ngram.isNotEmpty()) {
          val label = ngram.last()
          val srcCtx = ngram.dropLast(1)

          // CRITICAL FIX:
          // If the n-gram is shorter than maxN, the target state must strictly
          // represent the FULL n-gram (growing the context).
          // If the n-gram is maxN, we slide the window (standard De Bruijn).
          val tgtCtx = if (ngram.size < maxN) {
            ngram // Grow: [A] -> [A, B]
          } else {
            ngram.drop(1) // Slide: [A, B] -> [B, C] (where maxN=3)
          }

          val u = id(srcCtx)
          val v = id(tgtCtx)

          transitions.getOrPut(u) { mutableListOf() }
            .add(NFA.Edge(label, v))
        }
      }

      // Ensure the empty start state exists in the map so we can reference it
      val startId = id(emptyList())
      val allStates = stateToId.values.toSet()

      return NFA(
        // We must start from the empty context to validate the word from the beginning.
        startStates = setOf(startId),
        finalStates = allStates,
        transitions = transitions
      )
    }
  }

  data class Edge(val label: Σᐩ?, val target: Int)
  val allStates by lazy { transitions.keys +
      transitions.values.flatMap { it.map { e -> e.target } } +
      startStates + finalStates }

  fun summary(): String = "(states=${allStates.size}, transition=${transitions.values.sumOf { it.size }})"

  fun removeEpsilons(): NFA {
    val maxState = allStates.maxOrNull() ?: 0
    val stateCount = maxState + 1

    val closures = Array(stateCount) { i -> KBitSet(stateCount).apply { set(i) } }

    // 2. Fixed-Point Iteration
    var changed = true
    while (changed) {
      changed = false
      for ((src, edges) in transitions) {
        for (edge in edges) {
          if (edge.label == null) {
            // Important: Check how your BitSet 'or' behaves.
            // Standard BitSet.or modifies in-place.
            // We need to detect if 'closures[src]' actually changed.
            val prevCardinality = closures[src].cardinality() // or equivalent count method
            closures[src].or(closures[edge.target])
            if (closures[src].cardinality() > prevCardinality) { changed = true }
          }
        }
      }
    }

    // 3. Build New Transitions (Symbolic only)
    val newTransitions = mutableMapOf<Int, MutableList<Edge>>()

    // We only care about iterating states that actually exist/matter
    // But iterating 0..maxState is safe if sparse states are handled.
    for (q in 0..maxState) {
      // For every state p reachable from q via epsilon...
      closures[q].forEachSetBit { p ->
        transitions[p]?.forEach { edge ->
          if (edge.label != null) {
            val qEdges = newTransitions.getOrPut(q) { mutableListOf() }
            // Prevent duplicates
            if (qEdges.none { it.label == edge.label && it.target == edge.target }) {
              qEdges.add(Edge(edge.label, edge.target))
            }
          }
        }
      }
    }

    // 4. Update Final States
    // A state is now final if it can reach an old final state via epsilon
    val oldFinalsBitSet = KBitSet(stateCount)
    finalStates.forEach { oldFinalsBitSet.set(it) }

    val newFinals = mutableSetOf<Int>()
    for (q in 0..maxState) if (closures[q].intersects(oldFinalsBitSet)) newFinals.add(q)

    return NFA(startStates, newFinals, newTransitions)
  }

  fun trim(): NFA {
    val reachable = mutableSetOf<Int>()
    val queue = ArrayDeque<Int>()

    queue.addAll(startStates)
    reachable.addAll(startStates)

    while (queue.isNotEmpty()) {
      val current = queue.removeFirst()
      transitions[current]?.forEach { edge -> if (reachable.add(edge.target)) queue.add(edge.target) }
    }

    val trimmedTransitions = transitions
      .filterKeys { it in reachable } // Only keep edges FROM reachable
      .mapValues { (_, edges) -> edges.filter { it.target in reachable } }
      .filterValues { it.isNotEmpty() }

    val trimmedFinals = finalStates.intersect(reachable)

    return NFA(startStates, trimmedFinals, trimmedTransitions)
  }

  fun slice(n: Int): NFA {
    val timer = TimeSource.Monotonic.markNow()
    require(n >= 1) { "n must be >= 1" }

    // ---- Local tiny int vector to avoid boxing ----
    class IntVec(cap: Int = 16) {
      var a: IntArray = IntArray(cap)
      var size: Int = 0
      fun add(x: Int) {
        if (size == a.size) a = a.copyOf(maxOf(8, a.size * 2))
        a[size++] = x
      }
      fun clear() { size = 0 }
      fun toArray(): IntArray = a.copyOf(size)
    }

    val statesSorted = allStates.toIntArray().also { it.sort() }
    val Q = statesSorted.size
    if (Q == 0) return NFA(emptySet(), emptySet(), emptyMap())

    // We will create product-state ids in [0, Q*(n+1)).
    val totalStates = Q.toLong() * (n.toLong() + 1L)
    require(totalStates <= Int.MAX_VALUE.toLong()) {
      "slice too large: Q=$Q, n=$n => ${totalStates} states (won't fit in Int ids)"
    }

    // Fast path: already 0..Q-1
    val isContig0 = (statesSorted[0] == 0 && statesSorted[Q - 1] == Q - 1) &&
        run {
          var ok = true
          for (i in 0 until Q) if (statesSorted[i] != i) { ok = false; break }
          ok
        }

    // Map original state id -> dense index 0..Q-1
    val idxOf: (Int) -> Int = if (isContig0) {
      { s -> s }
    } else {
      val maxId = statesSorted[Q - 1]
      // Hybrid: array map if not too sparse; else HashMap.
      if (maxId <= 2_000_000 && maxId <= 8 * Q) {
        val map = IntArray(maxId + 1) { -1 }
        for (i in 0 until Q) map[statesSorted[i]] = i
        { s -> map[s] }
      } else {
        val map = HashMap<Int, Int>(Q * 2)
        for (i in 0 until Q) map[statesSorted[i]] = i
        { s -> map[s]!! }
      }
    }

    // ---- Convert base transitions to dense adjacency arrays (no epsilons assumed) ----
    val outLabels: Array<Array<Σᐩ>> = Array(Q) { emptyArray() }
    val outTargets: Array<IntArray> = Array(Q) { IntArray(0) }

    for ((src, edges) in transitions) {
      val srcI = idxOf(src)
      val m = edges.size
      if (m == 0) continue
      val labs = Array(m) { j -> edges[j].label!! }     // no epsilons => !!
      val tgts = IntArray(m) { j -> idxOf(edges[j].target) }
      outLabels[srcI] = labs
      outTargets[srcI] = tgts
    }

    // Dense final-mask for quick membership
    val isFinal = BooleanArray(Q)
    for (f in finalStates) isFinal[idxOf(f)] = true

    // Start indices (deduped)
    val startVec = IntVec(startStates.size)
    val seen0 = BooleanArray(Q)
    for (s in startStates) {
      val si = idxOf(s)
      if (!seen0[si]) { seen0[si] = true; startVec.add(si) }
    }
    val layerNodes: Array<IntArray> = Array(n + 1) { IntArray(0) }
    layerNodes[0] = startVec.toArray()

    // ---- Storage for layered transitions ----
    // Use array backing when Q*n is reasonable; else sparse map to avoid huge null arrays.
    val useArrayBacking = (Q.toLong() * n.toLong()) <= 5_000_000L
    val transArr: Array<ArrayList<Edge>?>? =
      if (useArrayBacking) arrayOfNulls(Q * n) else null
    val transMap: HashMap<Int, ArrayList<Edge>>? =
      if (!useArrayBacking) HashMap<Int, ArrayList<Edge>>(minOf(1_000_000, Q * 4)) else null

    fun getList(srcProd: Int, cap: Int): ArrayList<Edge> {
      if (useArrayBacking) {
        var l = transArr!![srcProd]
        if (l == null) {
          l = ArrayList(cap)
          transArr[srcProd] = l
        }
        return l
      } else {
        return transMap!!.getOrPut(srcProd) { ArrayList(cap) }
      }
    }

    // ---- Forward build: only states reachable at each layer ----
    // Reuse nextSeen without reallocating: clear via touched list.
    var curr = layerNodes[0]
    val nextSeen = BooleanArray(Q)
    val touched = IntVec(256)

    for (i in 0 until n) {
      val next = IntVec(maxOf(16, curr.size))
      val baseNext = (i + 1) * Q

      for (srcI in curr) {
        val tgts = outTargets[srcI]
        if (tgts.isEmpty()) continue
        val labs = outLabels[srcI]
        val srcProd = i * Q + srcI
        val out = getList(srcProd, tgts.size)

        // Copy all outgoing edges into next layer
        for (k in tgts.indices) {
          val tgtI = tgts[k]
          out.add(Edge(labs[k], baseNext + tgtI))
          if (!nextSeen[tgtI]) {
            nextSeen[tgtI] = true
            touched.add(tgtI)
            next.add(tgtI)
          }
        }
      }

      // clear marks for next iteration
      for (tIdx in 0 until touched.size) nextSeen[touched.a[tIdx]] = false
      touched.clear()

      curr = next.toArray()
      layerNodes[i + 1] = curr
      if (curr.isEmpty()) break // no reachable states at further layers
    }

    // If nothing reachable at layer n => empty language
    val layerN = layerNodes[n]
    if (layerN.isEmpty()) {
      val newStarts = buildSet(layerNodes[0].size) { for (si in layerNodes[0]) add(si) }
      return NFA(newStarts, emptySet(), emptyMap())
    }

    // ---- Backward prune: keep only nodes/edges that can reach a final at layer n ----
    // aliveNext[t] means (t at layer i+1) can reach acceptance (at layer n).
    var aliveNext = BooleanArray(Q)
    for (t in layerN) if (isFinal[t]) aliveNext[t] = true

    // If no accepting states reachable at layer n => empty language
    var anyFinalReachable = false
    for (t in layerN) if (aliveNext[t]) { anyFinalReachable = true; break }
    if (!anyFinalReachable) {
      val newStarts = buildSet(layerNodes[0].size) { for (si in layerNodes[0]) add(si) }
      return NFA(newStarts, emptySet(), emptyMap())
    }

    // Prune transitions layer by layer from n-1 down to 0
    for (i in n - 1 downTo 0) {
      val aliveCurr = BooleanArray(Q)
      val baseNext = (i + 1) * Q
      val nodes = layerNodes[i]
      for (srcI in nodes) {
        val srcProd = i * Q + srcI
        val list: ArrayList<Edge> = if (useArrayBacking) {
          transArr!![srcProd] ?: continue
        } else {
          transMap!![srcProd] ?: continue
        }

        var w = 0
        val sz = list.size
        for (r in 0 until sz) {
          val e = list[r]
          val tgtI = e.target - baseNext // (i+1)*Q + tgtI
          if (tgtI >= 0 && tgtI < Q && aliveNext[tgtI]) {
            list[w++] = e
          }
        }

        if (w == 0) {
          if (useArrayBacking) transArr!![srcProd] = null else transMap!!.remove(srcProd)
        } else {
          if (w < list.size) list.subList(w, list.size).clear()
          aliveCurr[srcI] = true
        }
      }
      aliveNext = aliveCurr
    }

    // Starts must be alive at layer 0
    val newStarts = buildSet(layerNodes[0].size) {
      for (si in layerNodes[0]) if (aliveNext[si]) add(si) // layer 0 => id = si
    }
    if (newStarts.isEmpty()) return NFA(emptySet(), emptySet(), emptyMap())

    // Finals are finals at layer n that were reachable & accepting
    val newFinals = buildSet(layerN.size) {
      val baseN = n * Q
      for (fi in layerN) if (isFinal[fi]) add(baseN + fi)
    }

    // Build transitions map output
    val newTransitions: Map<Int, List<Edge>> = if (useArrayBacking) {
      val m = HashMap<Int, List<Edge>>(minOf(Q * 4, Q * n))
      val arr = transArr!!
      for (idx in arr.indices) {
        val l = arr[idx]
        if (!l.isNullOrEmpty()) m[idx] = l
      }
      m
    } else {
      transMap!!
    }

    return NFA(
      startStates = newStarts,
      finalStates = newFinals,
      transitions = newTransitions
    )//.also { println("Sliced NFA in ${timer.elapsedNow()}") }
  }

  fun sampleAcyclic(): Sequence<String> {
    // Uniform over accepting *paths* (edge sequences), not distinct strings.

    val ZERO = BigInteger.ZERO
    val ONE  = BigInteger.ONE

    fun Random.nextBigIntBelow(bound: BigInteger): BigInteger {
      require(bound.signum() > 0) { "bound must be > 0" }
      val bits = bound.bitLength()
      while (true) {
        val r = nextBigInteger(bits)
        if (r < bound) return r
      }
    }

    fun pickIndex(cdf: Array<BigInteger>, r: BigInteger): Int {
      // smallest i with r < cdf[i]
      var lo = 0
      var hi = cdf.size - 1
      while (lo < hi) {
        val mid = (lo + hi) ushr 1
        if (r < cdf[mid]) hi = mid else lo = mid + 1
      }
      return lo
    }

    // ---- densify state ids for array speed ----
    val statesSorted = allStates.toIntArray().also { it.sort() }
    val Q = statesSorted.size
    if (Q == 0) return emptySequence()

    val isContig0 = (statesSorted.first() == 0 && statesSorted.last() == Q - 1) &&
        run {
          var ok = true
          for (i in 0 until Q) if (statesSorted[i] != i) { ok = false; break }
          ok
        }

    val idxOf: (Int) -> Int = if (isContig0) {
      { s -> s }
    } else {
      val maxId = statesSorted.last()
      if (maxId <= 2_000_000 && maxId <= 8 * Q) {
        val map = IntArray(maxId + 1) { -1 }
        for (i in 0 until Q) map[statesSorted[i]] = i
        { s -> map[s] }
      } else {
        val map = HashMap<Int, Int>(Q * 2)
        for (i in 0 until Q) map[statesSorted[i]] = i
        { s -> map[s]!! }
      }
    }

    // ---- adjacency (ε-free => label!! safe) ----
    val outT = Array(Q) { IntArray(0) }
    val outL = Array(Q) { emptyArray<Σᐩ>() }

    for ((src, edges) in transitions) {
      if (edges.isEmpty()) continue
      val si = idxOf(src)
      val m = edges.size
      val tgts = IntArray(m)
      val labs = Array<Σᐩ>(m) { "" }
      for (i in 0 until m) {
        val e = edges[i]
        labs[i] = e.label!!           // ε-free precondition
        tgts[i] = idxOf(e.target)
      }
      outT[si] = tgts
      outL[si] = labs
    }

    val isFinal = BooleanArray(Q)
    for (f in finalStates) isFinal[idxOf(f)] = true

    val startIdxs = run {
      val seen = BooleanArray(Q)
      val tmp = IntArray(startStates.size)
      var k = 0
      for (s in startStates) {
        val si = idxOf(s)
        if (!seen[si]) { seen[si] = true; tmp[k++] = si }
      }
      tmp.copyOf(k)
    }

    // ---- topo order (Kahn). If you truly trust acyclicity, you can delete the check. ----
    val indeg = IntArray(Q)
    for (s in 0 until Q) for (t in outT[s]) indeg[t]++

    val queue = IntArray(Q)
    var qh = 0
    var qt = 0
    for (i in 0 until Q) if (indeg[i] == 0) queue[qt++] = i

    val topo = IntArray(Q)
    var tsz = 0
    while (qh < qt) {
      val v = queue[qh++]
      topo[tsz++] = v
      for (t in outT[v]) {
        val d = --indeg[t]
        if (d == 0) queue[qt++] = t
      }
    }
    require(tsz == Q) { "NFA is not acyclic (cycle detected)" }

    // ---- DP: count[v] = #accepting paths starting at v (includes stop-at-v if final) ----
    val count = Array(Q) { ZERO }
    for (k in Q - 1 downTo 0) {
      val v = topo[k]
      var c = if (isFinal[v]) ONE else ZERO
      for (t in outT[v]) {
        val w = count[t]
        if (w.signum() != 0) c = c.add(w)
      }
      count[v] = c
    }

    // total accepting paths from starts
    var totalStarts = ZERO
    for (s in startIdxs) totalStarts = totalStarts.add(count[s])
    if (totalStarts.signum() == 0) return emptySequence()

    // start CDF
    val startCum = Array(startIdxs.size) { ZERO }
    run {
      var acc = ZERO
      for (i in startIdxs.indices) {
        acc = acc.add(count[startIdxs[i]])
        startCum[i] = acc
      }
    }

    // per-state: active edges (to suffix>0) + CDF over edge weights
    val actT = Array(Q) { IntArray(0) }
    val actL = Array(Q) { emptyArray<Σᐩ>() }
    val actCum = Array(Q) { emptyArray<BigInteger>() } // cumulative over edges only (no stop)

    for (s in 0 until Q) {
      val tgts = outT[s]
      if (tgts.isEmpty()) continue

      var k = 0
      for (t in tgts) if (count[t].signum() != 0) k++
      if (k == 0) continue

      val aT = IntArray(k)
      val aL = Array<Σᐩ>(k) { "" }
      val cdf = Array(k) { ZERO }

      var acc = ZERO
      var w = 0
      val labs = outL[s]
      for (i in tgts.indices) {
        val t = tgts[i]
        val wt = count[t]
        if (wt.signum() == 0) continue
        aT[w] = t
        aL[w] = labs[i]
        acc = acc.add(wt)
        cdf[w] = acc
        w++
      }
      actT[s] = aT
      actL[s] = aL
      actCum[s] = cdf
    }

    fun sampleOnce(rng: Random): String {
      // pick start state proportional to count[start]
      val rs = rng.nextBigIntBelow(totalStarts)
      val sIdx = startIdxs[pickIndex(startCum, rs)]

      var state = sIdx
      val sb = StringBuilder()
      var first = true

      while (true) {
        val total = count[state]                 // includes stop-at-final weight 1 if final
        var r = rng.nextBigIntBelow(total)

        if (isFinal[state]) {
          if (r == ZERO) break                   // stop here
          r = r.subtract(ONE)                    // otherwise choose an outgoing edge
        }

        val cdf = actCum[state]
        if (cdf.isEmpty()) break                 // should only happen when total==1 and final-stop was taken

        val ei = pickIndex(cdf, r)
        val label = actL[state][ei]
        if (!first) sb.append(' ')
        sb.append(label)
        first = false
        state = actT[state][ei]
      }

      return sb.toString()
    }

    return sequence {
      val rng = Random(1234)
      while (true) yield(sampleOnce(rng))
    }
  }

  fun toGraphviz(name: String = "Nederhof_NFA"): String = buildString {
    appendLine("digraph $name {")
    appendLine("  rankdir=LR;")
    appendLine("  node [shape = circle, fontname = \"Helvetica\"];")
    appendLine("  edge [fontname = \"Helvetica\"];")

    // 1. Define Final States
    if (finalStates.isNotEmpty()) {
      append("  node [shape = doublecircle];")
      finalStates.forEach { append(" $it") }
      appendLine(";")
    }

    // 2. Define Start States
    appendLine("  node [shape = circle];")
    appendLine("  __start [style = invisible, shape = point];")
    startStates.forEach { start ->
      appendLine("  __start -> $start;")
    }

    // 3. Edges (Consolidated)
    // Group transitions by Source -> Target
    val groupedTransitions = transitions.flatMap { (src, edges) ->
      edges.map { edge -> Triple(src, edge.target, edge.label) }
    }.groupBy { (src, target, _) -> src to target }

    // Render groups
    // Render groups
    for ((key, group) in groupedTransitions) {
      val (src, target) = key

      // Distinct labels for this src->target (avoid repeated "a, a, a")
      val labelsDistinct = group
        .map { it.third }            // Σᐩ? labels (null = ε)
        .distinct()
        .sortedWith(compareBy<Σᐩ?>({ it != null }, { it ?: "" })) // ε first, then lexicographic

      if (labelsDistinct.size > 100) {
        // Too many labels -> collapse
        appendLine("  $src -> $target [label=\"${labelsDistinct.size} transitions\", style=bold];")
      } else {
        // Concatenate into a single comma-separated label
        val labelText = labelsDistinct.joinToString(", ") { (it ?: "ε").escapeDotLabel() }

        // Style: dashed gray only if it's purely epsilon-transitions
        val style = if (labelsDistinct.all { it == null }) "style=dashed, color=gray" else "style=solid"

        appendLine("  $src -> $target [label=\"$labelText\", $style];")
      }
    }

    appendLine("}")
  }

  fun complement(alphabet: Set<Σᐩ>): NFA {
    val timer = TimeSource.Monotonic.markNow()
    val nfa = removeEpsilons().trim()
    val sigma: List<Σᐩ> = alphabet.toList().sortedBy { it } // stable iteration

    // ----- Determinize (subset construction), total over `sigma` -----
    class SubsetKey(states: IntArray) {
      val a: IntArray = states // must be sorted, never mutated
      override fun equals(other: Any?): Boolean = other is SubsetKey && a.contentEquals(other.a)
      override fun hashCode(): Int = a.contentHashCode()
    }

    fun keyOf(states: Set<Int>): SubsetKey {
      val arr = states.toIntArray()
      arr.sort()
      return SubsetKey(arr)
    }

    val subsetToId = HashMap<SubsetKey, Int>(128)
    val work = ArrayDeque<SubsetKey>()
    val dfaTransitions = mutableMapOf<Int, MutableList<Edge>>()

    fun intern(k: SubsetKey): Int {
      val existing = subsetToId[k]
      if (existing != null) return existing
      val id = subsetToId.size
      subsetToId[k] = id
      work.addLast(k)
      return id
    }

    val startKey = keyOf(nfa.startStates)
    val startId = intern(startKey)

    while (work.isNotEmpty()) {
      val subset = work.removeFirst()
      val srcId = subsetToId[subset]!! // already interned

      val out = mutableListOf<Edge>()
      for (sym in sigma) {
        val tgt = HashSet<Int>()
        for (s in subset.a) {
          nfa.transitions[s]?.forEach { e ->
            if (e.label == sym) tgt.add(e.target)
          }
        }
        val tgtId = intern(keyOf(tgt)) // note: tgt may be empty => sink subset
        out.add(Edge(sym, tgtId))
      }
      if (out.isNotEmpty()) dfaTransitions[srcId] = out
    }

    // ----- Flip accepting states -----
    val dfaFinals = mutableSetOf<Int>()
    for ((subset, id) in subsetToId) {
      // Accepting iff subset intersects NFA finals
      if (subset.a.any { it in nfa.finalStates }) dfaFinals.add(id)
    }

    val allDfaStates = subsetToId.values.toSet()
    val complementFinals = allDfaStates - dfaFinals

    return NFA(
      startStates = setOf(startId),
      finalStates = complementFinals,
      transitions = dfaTransitions
    ).trim().also { println("Complemented NFA in ${timer.elapsedNow()}") }
  }

  private fun String.escapeDotLabel(): String = replace("\\", "\\\\").replace("\"", "\\\"")

  /**
   * Computes the intersection of this NFA with another NFA.
   * Result accepts L(this) ∩ L(other).
   */
  fun intersect(other: NFA): NFA {
    val timer = TimeSource.Monotonic.markNow()

    // We map pairs (u, v) -> newId
    // u from this, v from other
    val stateMap = HashMap<Long, Int>()
    val transitions = mutableMapOf<Int, MutableList<Edge>>()
    val queue = ArrayDeque<Long>()

    // Helper to pack two Ints into a Long
    fun pack(u: Int, v: Int): Long = (u.toLong() shl 32) or (v.toLong() and 0xFFFFFFFFL)
    fun unpack(p: Long): Pair<Int, Int> = (p ushr 32).toInt() to p.toInt()

    fun getId(u: Int, v: Int): Int {
      val key = pack(u, v)
      return stateMap.getOrPut(key) {
        val id = stateMap.size
        queue.add(key)
        id
      }
    }

    // Initialize with Cartesian product of start states
    val newStarts = mutableSetOf<Int>()
    for (s1 in this.startStates) {
      for (s2 in other.startStates) {
        newStarts.add(getId(s1, s2))
      }
    }

    // BFS to build reachable product graph
    while (queue.isNotEmpty()) {
      val currentPacked = queue.removeFirst()
      val currentId = stateMap[currentPacked]!!
      val (u, v) = unpack(currentPacked)

      // Get transitions for u and v
      val edges1 = this.transitions[u] ?: emptyList()
      val edges2 = other.transitions[v] ?: emptyList()

      // Match edges with same label
      // Optimization: Group by label if density is high,
      // but simple nested loop is often faster for sparse NFAs.
      for (e1 in edges1) {
        for (e2 in edges2) {
          if (e1.label == e2.label) { // Matches symbols (including null if both are null)
            val targetId = getId(e1.target, e2.target)
            transitions.getOrPut(currentId) { mutableListOf() }
              .add(Edge(e1.label, targetId))
          }
        }
      }
    }

    // Final states are pairs (f1, f2) where f1 ∈ F1 and f2 ∈ F2
    val newFinals = mutableSetOf<Int>()
    stateMap.forEach { (packed, id) ->
      val (u, v) = unpack(packed)
      if (u in this.finalStates && v in other.finalStates) {
        newFinals.add(id)
      }
    }

    return NFA(newStarts, newFinals, transitions)
      .also { println("Intersected NFA (${it.allStates.size} states) in ${timer.elapsedNow()}") }
  }


  /**
   * Minimizes the NFA using Bisimulation Minimization (Partition Refinement).
   * Note: This computes the quotient modulo largest bisimulation, not full NFA minimization (PSPACE-complete).
   */
  fun minimize(): NFA {
    val timer = TimeSource.Monotonic.markNow()

    // 0. Remove unreachable states first to avoid processing garbage
    val trimmed = this.trim()
    if (trimmed.allStates.isEmpty()) return trimmed

    val states = trimmed.allStates.sorted().toIntArray()
    val stateToIndex = states.withIndex().associate { it.value to it.index }
    val n = states.size

    // 1. Initial Partition: Final vs Non-Final
    // blockIds[i] = block ID of state at index i
    var blockIds = IntArray(n)
    val finalsMask = KBitSet(n)

    trimmed.finalStates.forEach { fs -> stateToIndex[fs]?.let { finalsMask.set(it) } }

    // Assign initial blocks: 0 for non-final, 1 for final
    // (If all are final or all non-final, we handle that correctly)
    for (i in 0 until n) {
      blockIds[i] = if (finalsMask[i]) 1 else 0
    }
    var numBlocks = 2

    // Precompute reverse transitions for O(1) lookup: Label -> TargetBlock -> Sources
    // inverseTransitions[label][targetStateIdx] = list of sourceStateIdxs
    val invTrans = mutableMapOf<Σᐩ?, MutableMap<Int, MutableList<Int>>>()

    trimmed.transitions.forEach { (src, edges) ->
      val srcIdx = stateToIndex[src] ?: return@forEach
      edges.forEach { edge ->
        val tgtIdx = stateToIndex[edge.target]!!
        invTrans.getOrPut(edge.label) { mutableMapOf() }
          .getOrPut(tgtIdx) { mutableListOf() }
          .add(srcIdx)
      }
    }

    // 2. Refinement Loop (Paige-Tarjan / Hopcroft style)
    // We maintain a set of "splitters": pairs (Label, BlockID) that might refine the partition.
    // Initially, both blocks (0 and 1) are splitters for all alphabet symbols.

    // Optimization: Just check stability directly until fixed point (simpler to implement correctly for NFAs)
    var changed = true
    while (changed) {
      changed = false

      // Calculate signature for each state
      // Signature = {(Label, TargetBlockID)}
      // Two states are equivalent if they have the same set of (Label, TargetBlockID) pairs

      val signatures = Array<MutableSet<Pair<Σᐩ?, Int>>>(n) { HashSet() }

      for (i in 0 until n) { // for each state index
        val originalState = states[i]
        trimmed.transitions[originalState]?.forEach { edge ->
          val targetIdx = stateToIndex[edge.target]!!
          val targetBlock = blockIds[targetIdx]
          signatures[i].add(edge.label to targetBlock)
        }
      }

      // Group states by (CurrentBlock, Signature)
      val newGroups = (0 until n).groupBy { i ->
        // Key is Pair(CurrentBlock, Signature)
        // We rely on Set.equals for signature comparison
        blockIds[i] to signatures[i]
      }

      // Assign new block IDs
      var newBlockCount = 0
      val newBlockIds = IntArray(n)

      for ((_, groupIndices) in newGroups) {
        val newBlockId = newBlockCount++
        for (idx in groupIndices) {
          newBlockIds[idx] = newBlockId
        }
      }

      if (newBlockCount > numBlocks) {
        blockIds = newBlockIds
        numBlocks = newBlockCount
        changed = true
      }
    }

    // 3. Reconstruct NFA from Blocks
    val newStartStates = mutableSetOf<Int>()
    val newFinalStates = mutableSetOf<Int>()
    val newTransitions = mutableMapOf<Int, MutableList<Edge>>()

    // Map old states to their block representative (the block ID)
    for (i in 0 until n) {
      val originalState = states[i]
      val blockId = blockIds[i]

      if (originalState in trimmed.startStates) newStartStates.add(blockId)
      if (originalState in trimmed.finalStates) newFinalStates.add(blockId)

      // Add transitions: Block(u) -> Label -> Block(v)
      // We iterate all edges, but use a Set to avoid duplicate edges between blocks
      trimmed.transitions[originalState]?.forEach { edge ->
        val targetIdx = stateToIndex[edge.target]!!
        val targetBlock = blockIds[targetIdx]

        val edges = newTransitions.getOrPut(blockId) { mutableListOf() }
        // Only add if not already present (Multi-edges with same label/target are redundant)
        if (edges.none { it.label == edge.label && it.target == targetBlock }) {
          edges.add(Edge(edge.label, targetBlock))
        }
      }
    }

    return NFA(newStartStates, newFinalStates, newTransitions)
      .also { println("Minimized NFA: ${this.allStates.size} -> ${it.allStates.size} states in ${timer.elapsedNow()}") }
  }

  /**
   * Generates a sequence of random strings from the NFA via random walk.
   * * @param maxSteps The maximum number of transitions to take per string.
   * @param stopAtFinal If true, the walk may stop early (with 15% probability)
   * upon reaching a final state.
   */
  fun sample(maxSteps: Int = 20, stopAtFinal: Boolean = true): Sequence<String> = sequence {
    val rng = Random.Default
    if (startStates.isEmpty()) return@sequence

    // Convert to list once for O(1) random access
    val starts = startStates.toList()

    while (true) {
      var curr = starts.random(rng)
      val sb = StringBuilder()

      for (i in 0 until maxSteps) {
        val edges = transitions[curr]
        if (edges.isNullOrEmpty()) break // Dead end

        val edge = edges.random(rng)

        if (edge.label != null) {
          if (sb.isNotEmpty()) sb.append(" ")
          sb.append(edge.label)
        }

        curr = edge.target

        // If we reached a valid acceptance state, maybe stop early?
        if (stopAtFinal && curr in finalStates && rng.nextDouble() < 0.15) break
      }

      if (sb.isNotEmpty()) yield(sb.toString())
    }
  }

  /**
   * Returns true if the NFA accepts the given sequence of tokens.
   */
  fun recognizes(str: List<Σᐩ>): Boolean {
    // 1. Current states = Epsilon-closure of start states
    var currentStates = expandEpsilons(startStates)

    for (symbol in str) {
      if (currentStates.isEmpty()) return false

      // 2. Move: For each state, find transitions matching 'symbol'
      val nextStates = mutableSetOf<Int>()
      for (s in currentStates) {
        transitions[s]?.forEach { edge ->
          if (edge.label == symbol) {
            nextStates.add(edge.target)
          }
        }
      }

      // 3. Epsilon-closure of the new set
      currentStates = expandEpsilons(nextStates)
    }

    // 4. Accept if any current state is a final state
    return currentStates.any { it in finalStates }
  }

  // Helper to compute epsilon closure of a set of states (BFS)
  private fun expandEpsilons(states: Set<Int>): Set<Int> {
    val closure = states.toMutableSet()
    val queue = ArrayDeque(states)

    while (queue.isNotEmpty()) {
      val s = queue.removeFirst()
      transitions[s]?.forEach { edge -> if (edge.label == null && closure.add(edge.target)) queue.add(edge.target) }
    }
    return closure
  }

  /**
   * Extremely fast n-gram extraction using Batched Dynamic Programming.
   *
   * approaches the problem as a matrix multiplication of path sets,
   * avoiding the exponential cost of re-traversing shared suffixes.
   */
  fun extractNgrams(n: Int = 3): Map<List<Σᐩ>, Double> {
    require(n >= 1) { "n must be >= 1" }

    // 1. Slice: Get the trellis of depth n.
    //    This is already fast (approx 40-50ms)
    val trellis = this.slice(n)
    if (trellis.startStates.isEmpty()) return emptyMap()

    // 2. State Mapping: Dense Integers for array performance
    //    Map sparse state IDs to 0..numStates-1
    val states = trellis.allStates.toList()
    val stateToIdx = states.withIndex().associate { it.value to it.index }
    val numStates = states.size

    // 3. Adjacency: Pre-group transitions by source index
    //    adj[u] = List of (Label, TargetIndex)
    val adj = Array(numStates) { ArrayList<Pair<Σᐩ, Int>>() }
    trellis.transitions.forEach { (src, edges) ->
      val u = stateToIdx[src] ?: return@forEach
      edges.forEach { edge ->
        // slice() ensures non-null labels
        val v = stateToIdx[edge.target]
        if (v != null) adj[u].add(edge.label!! to v)
      }
    }

    // 4. Batched Forward Propagation
    //    paths[u] = List of all path-strings (List<String>) that end at state u
    //    We iterate exactly n times (depth of trellis).
    var currentPaths = Array(numStates) { ArrayList<List<Σᐩ>>() }

    // Initialize: Start states have one empty path "[]" (conceptually)
    // Actually, to get n-grams of length n, we need n edges.
    // We start with "empty" at layer 0.
    trellis.startStates.forEach { s ->
      stateToIdx[s]?.let { idx -> currentPaths[idx].add(emptyList()) }
    }

    // Step layer 1 to n
    for (layer in 0 until n) {
      val nextPaths = Array(numStates) { ArrayList<List<Σᐩ>>() }
      var activeCount = 0

      for (u in 0 until numStates) {
        val pathsAtU = currentPaths[u]
        if (pathsAtU.isEmpty()) continue

        val edges = adj[u]
        if (edges.isEmpty()) continue

        // For each outgoing edge (Label, v)
        for ((label, v) in edges) {
          val pathsAtV = nextPaths[v]

          // Extend all paths arriving at u with 'label'
          // This "Batch" operation is the key optimization.
          // We avoid visiting v recursively for each individual path.
          val extended = ArrayList<List<Σᐩ>>(pathsAtU.size)
          for (p in pathsAtU) {
            // Optimization: Create new list only if needed.
            // Small lists (n=3,4) are cheap, but we can optimize capacity.
            val newPath = ArrayList<Σᐩ>(p.size + 1)
            newPath.addAll(p)
            newPath.add(label)
            extended.add(newPath)
          }

          pathsAtV.addAll(extended)
        }
      }

      // Update for next iteration
      currentPaths = nextPaths
      // Quick check to break early if graph dies out
      for (list in currentPaths) if (list.isNotEmpty()) activeCount++
      if (activeCount == 0) return emptyMap()
    }

    // 5. Aggregate Results
    //    Sum up counts of identical n-grams (if multiple paths produce same string)
    val results = HashMap<List<Σᐩ>, Double>()
    for (paths in currentPaths) {
      for (p in paths) {
        results[p] = (results[p] ?: 0.0) + 1.0
      }
    }

    return results
  }

  /**
   * Determinizes the NFA using the Subset Construction algorithm.
   * Returns an equivalent DFA (represented as an NFA).
   */
  fun determinize(): NFA {
    val timer = TimeSource.Monotonic.markNow()

    // 0. Precompute Epsilon Closures
    // This removes epsilons implicitly during determinization.
    val nfaNoEps = this.removeEpsilons()

    // 1. Identify Alphabet (Sigma)
    // We only need to check transitions that actually exist.
    val alphabet = HashSet<Σᐩ>()
    nfaNoEps.transitions.values.forEach { edges ->
      edges.forEach { if (it.label != null) alphabet.add(it.label) }
    }
    val sortedSigma = alphabet.toList().sorted() // Stable iteration order

    // 2. Subset Construction State Mapping
    //    Key: Sorted IntArray of NFA states (representing a DFA state)
    //    Value: New DFA State ID (Int)
    //    Using a custom wrapper for IntArray to use it as a HashMap key.
    class StateKey(val states: IntArray) {
      override fun equals(other: Any?): Boolean =
        other is StateKey && states.contentEquals(other.states)
      override fun hashCode(): Int = states.contentHashCode()
    }

    val subsetToId = HashMap<StateKey, Int>()
    val workQueue = ArrayDeque<StateKey>()

    // 3. Start State
    //    DFA start = { NFA start states }
    val startArr = nfaNoEps.startStates.toIntArray().apply { sort() }
    val startKey = StateKey(startArr)

    subsetToId[startKey] = 0
    workQueue.add(startKey)

    // 4. Build DFA
    val newTransitions = HashMap<Int, MutableList<Edge>>()
    val newFinalStates = HashSet<Int>()

    // Check if start state is final
    if (startArr.any { it in nfaNoEps.finalStates }) newFinalStates.add(0)

    var nextId = 1

    while (workQueue.isNotEmpty()) {
      val currentSubsetKey = workQueue.removeFirst()
      val currentDfaId = subsetToId[currentSubsetKey]!!
      val currentNfaStates = currentSubsetKey.states

      // For each symbol in the alphabet...
      for (symbol in sortedSigma) {
        // Find reachable NFA states from current subset via 'symbol'
        // Using a temporary set to collect targets, then converting to array
        // (For very dense graphs, a BitSet might be faster here)
        val nextNfaStatesSet = HashSet<Int>()

        for (nfaState in currentNfaStates) {
          nfaNoEps.transitions[nfaState]?.forEach { edge ->
            if (edge.label == symbol) {
              nextNfaStatesSet.add(edge.target)
            }
          }
        }

        if (nextNfaStatesSet.isNotEmpty()) {
          val nextArr = nextNfaStatesSet.toIntArray().apply { sort() }
          val nextKey = StateKey(nextArr)

          // Intern state ID
          var nextDfaId = subsetToId[nextKey]
          if (nextDfaId == null) {
            nextDfaId = nextId++
            subsetToId[nextKey] = nextDfaId
            workQueue.add(nextKey)

            // Check finality
            if (nextArr.any { it in nfaNoEps.finalStates }) {
              newFinalStates.add(nextDfaId)
            }
          }

          // Add transition
          newTransitions.getOrPut(currentDfaId) { ArrayList() }
            .add(Edge(symbol, nextDfaId))
        }
      }
    }

    return NFA(
      startStates = setOf(0),
      finalStates = newFinalStates,
      transitions = newTransitions
    ).also { println("Determinized NFA (${it.allStates.size} states) in ${timer.elapsedNow()}") }
  }

  /**
   * Returns a new NFA that accepts L(this) - {word}.
   *
   * @param word The sequence of tokens to remove from the language.
   */
  fun removeWord(word: List<Σᐩ>): NFA {
    val timer = TimeSource.Monotonic.markNow()

    // The match index 'k' represents how much of 'word' we have matched so far.
    // k in [0, word.size]: We have matched word[0..k-1].
    // k = word.size + 1 (TRAP): We have diverged from 'word' (mismatch), so we accept everything.
    val TRAP = word.size + 1

    val stateMap = HashMap<Long, Int>()
    val transitions = mutableMapOf<Int, MutableList<Edge>>()
    val queue = ArrayDeque<Long>()

    // Helper to pack (OriginalStateID, MatchIndex) into a Long key
    fun pack(u: Int, k: Int): Long = (u.toLong() shl 32) or (k.toLong() and 0xFFFFFFFFL)
    fun unpack(p: Long): Pair<Int, Int> = (p ushr 32).toInt() to p.toInt()

    fun getId(u: Int, k: Int): Int {
      val key = pack(u, k)
      return stateMap.getOrPut(key) {
        val id = stateMap.size
        queue.add(key)
        id
      }
    }

    // 1. Initialize Start States
    // We start at the original start states with match index 0.
    val newStarts = startStates.map { getId(it, 0) }.toSet()

    // 2. Build Product Graph
    while (queue.isNotEmpty()) {
      val currentPacked = queue.removeFirst()
      val currentId = stateMap[currentPacked]!!
      val (u, k) = unpack(currentPacked)

      this.transitions[u]?.forEach { edge ->
        val sym = edge.label
        val v = edge.target

        // Determine the next state of the constraint filter
        val nextK = when {
          // Case A: Epsilon transition.
          // Does not advance the word position.
          sym == null -> k

          // Case B: Already diverged (Trap).
          // Once we mismatch, we stay mismatched (accepting everything).
          k == TRAP -> TRAP

          // Case C: Match found!
          // We are at index k, and the symbol matches word[k]. Advance.
          k < word.size && sym == word[k] -> k + 1

          // Case D: Mismatch/Divergence.
          // We expected word[k] but got something else (or overshot length).
          else -> TRAP
        }

        val targetId = getId(v, nextK)
        transitions.getOrPut(currentId) { mutableListOf() }.add(Edge(sym, targetId))
      }
    }

    // 3. Define Final States
    // A state (u, k) is final if:
    //   1. 'u' was final in the original NFA.
    //   2. AND we are NOT in the state that corresponds to exactly matching 'word'.
    val newFinals = mutableSetOf<Int>()
    stateMap.forEach { (packed, id) ->
      val (u, k) = unpack(packed)
      if (u in this.finalStates) {
        // If k == word.size, it means we reached a final state by matching 'word' exactly.
        // We exclude this case to remove the word.
        // (TRAP states and partial matches are still accepted).
        if (k != word.size) {
          newFinals.add(id)
        }
      }
    }

    return NFA(newStarts, newFinals, transitions)
      .also { println("Removed word of length ${word.size} in ${timer.elapsedNow()}") }
  }

  /**
   * Returns a new NFA that accepts L(this) ∩ Complement(Σ* factor Σ*).
   * Effectively removes any string that *contains* the given [factor] as a substring.
   */
  fun removeFactor(factor: List<Σᐩ>): NFA {
    if (factor.isEmpty()) return NFA(emptySet(), emptySet(), emptyMap()) // Removing empty factor empties the language
    val timer = TimeSource.Monotonic.markNow()

    // 1. Build KMP Failure Function (Pi table) for the factor
    // This allows us to track "how much of 'factor' have we matched?" efficiently.
    val pi = IntArray(factor.size)
    var j = 0
    for (i in 1 until factor.size) {
      while (j > 0 && factor[i] != factor[j]) j = pi[j - 1]
      if (factor[i] == factor[j]) j++
      pi[i] = j
    }

    // 2. Product Construction with the "Factor DFA"
    // State is an Int representing the length of the matching prefix of 'factor'.
    // If we reach state 'factor.size', we have found the factor (TRAP).
    val LIMIT = factor.size

    val stateMap = HashMap<Long, Int>()
    val transitions = mutableMapOf<Int, MutableList<Edge>>()
    val queue = ArrayDeque<Long>()

    fun pack(u: Int, matchLen: Int): Long = (u.toLong() shl 32) or (matchLen.toLong() and 0xFFFFFFFFL)
    fun unpack(p: Long): Pair<Int, Int> = (p ushr 32).toInt() to p.toInt()

    fun getId(u: Int, matchLen: Int): Int {
      val key = pack(u, matchLen)
      return stateMap.getOrPut(key) {
        val id = stateMap.size
        queue.add(key)
        id
      }
    }

    // Start states: matchLen = 0
    val newStarts = startStates.map { getId(it, 0) }.toSet()

    while (queue.isNotEmpty()) {
      val currentPacked = queue.removeFirst()
      val currentId = stateMap[currentPacked]!!
      val (u, q) = unpack(currentPacked)

      // If q == LIMIT, we have already found the forbidden factor.
      // Since we want to REMOVE such strings, we do not extend from this state (it's a dead end).
      if (q == LIMIT) continue

      this.transitions[u]?.forEach { edge ->
        val sym = edge.label
        val v = edge.target

        var nextQ = q
        // Simulate the DFA transition for 'sym' on the factor
        if (sym != null) {
          while (nextQ > 0 && sym != factor[nextQ]) nextQ = pi[nextQ - 1]
          if (sym == factor[nextQ]) nextQ++
        }

        // If nextQ == LIMIT, we found the factor. We map it to a trap state or just don't add the edge?
        // To be safe, we map it to a distinct state but mark it non-final/dead.
        // Here, we simply generate the edge. We will filter finals later.

        val targetId = getId(v, nextQ)
        transitions.getOrPut(currentId) { mutableListOf() }.add(Edge(sym, targetId))
      }
    }

    // 3. Final States
    // A state (u, q) is final if u was final AND q < LIMIT (we never completed the forbidden factor)
    val newFinals = mutableSetOf<Int>()
    stateMap.forEach { (packed, id) ->
      val (u, q) = unpack(packed)
      if (u in this.finalStates && q < LIMIT) {
        newFinals.add(id)
      }
    }

    return NFA(newStarts, newFinals, transitions).trim()
//      .also { println("Removed factor of length ${factor.size} in ${timer.elapsedNow()}") }
  }

  /**
   * Normalizes the NFA by:
   * 1. Pruning "dead ends" (states that cannot reach a final state).
   * 2. Ensuring there is exactly one start state.
   * - If multiple (or zero) valid start states exist, a new start state is created
   * with epsilon transitions to the valid original starts.
   */
  fun normalize(): NFA {
    // 1. Identify "Live" states (Co-reachability)
    // A state is live if it can reach a final state.
    val live = HashSet<Int>()
    val queue = ArrayDeque<Int>()

    // All final states are inherently live
    live.addAll(finalStates)
    queue.addAll(finalStates)

    // Build reverse graph: Target -> List<Source> to propagate liveness backwards
    val revTrans = HashMap<Int, ArrayList<Int>>()
    var maxId = -1

    for ((src, edges) in transitions) {
      if (src > maxId) maxId = src
      for (edge in edges) {
        revTrans.getOrPut(edge.target) { ArrayList() }.add(src)
        if (edge.target > maxId) maxId = edge.target
      }
    }

    // Ensure maxId accounts for isolated start/final states
    startStates.maxOrNull()?.let { if (it > maxId) maxId = it }
    finalStates.maxOrNull()?.let { if (it > maxId) maxId = it }

    // Backward BFS to find all live states
    while (queue.isNotEmpty()) {
      val u = queue.removeFirst()
      revTrans[u]?.forEach { v ->
        if (live.add(v)) {
          queue.add(v)
        }
      }
    }

    // 2. Filter Transitions
    // Keep transitions only if the target is live.
    // (If target is live, source must be live (or dead start), so we filter sources too)
    val newTransitions = HashMap<Int, MutableList<Edge>>()
    for ((src, edges) in transitions) {
      if (src in live) {
        val validEdges = edges.filter { it.target in live }
        if (validEdges.isNotEmpty()) {
          newTransitions[src] = validEdges.toMutableList()
        }
      }
    }

    // 3. Unify Start State
    // Only consider start states that can actually reach a final state
    val liveStarts = startStates.filter { it in live }
    val newStartStates: Set<Int>

    // If we already have exactly one live start state, we can reuse it to keep IDs stable.
    // Otherwise (0 or >1), we create a new single start state.
    if (liveStarts.size == 1) {
      newStartStates = setOf(liveStarts.first())
    } else {
      val newStart = maxId + 1
      newStartStates = setOf(newStart)

      // Add epsilon transitions from new start to all live old starts
      if (liveStarts.isNotEmpty()) {
        val startEdges = newTransitions.getOrPut(newStart) { ArrayList() }
        for (s in liveStarts) {
          startEdges.add(Edge(null, s))
        }
      }
    }

    // Final states must be live (which they are by definition, unless we filter for disjoint components later)
    val newFinalStates = finalStates.intersect(live)

    return NFA(newStartStates, newFinalStates, newTransitions)
  }
}

fun CFG.findShortestInvalidSubstrings(
  w: List<Σᐩ>,
  minLen: Int = 5,
  maxLen: Int = 10,
  padding: Int = 20,
  allNgrams: Set<List<Σᐩ>>? = null
): List<List<Σᐩ>> {
  val pad = List(padding) { "_" }
  return (minLen..maxLen).asSequence()
    .map { len -> w.windowed(len) }
    .map { substrings ->
      substrings.filter { sub -> if (allNgrams != null) sub !in allNgrams else !isValid(pad + sub + pad) }
    }.firstOrNull { it.isNotEmpty() } ?: emptyList()
}

/**
 * Extracts all contiguous subwords (token subsequences) that appear in *any* word
 * derivable from [startSymbol] with yield length ≤ n.
 *
 * Performance notes:
 * - Works over the cached CNF-ish views (vindex / leftAdj / terminalLists) from normalForm.
 * - Uses length-bounded DP to enumerate all distinct yields up to length n (you can’t beat output size).
 * - Uses leftAdj + bitsets to avoid scanning all productions for every split.
 *
 * Assumptions / behavior:
 * - Terminals are the “tokens” (Σᐩ strings) in your CFG.
 * - If the literal terminal "ε" is present, it is treated as epsilon (length 0).
 */

fun CFG.extractSubwords(n: Int, startSymbol: String = "START"): Set<List<String>> {
  require(n >= 1) { "n must be >= 1" }

  // 1. Get Normalized Grammar (CNF)
  val g = this.normalForm.freeze()
  val nt = g.nonterminals
  val bindex = g.bindex
  val start = bindex[startSymbol]

  // 2. Identification of True Terminals
  // CRITICAL FIX: Only treat a symbol as a terminal if it was a terminal in the
  // ORIGINAL grammar (this.terminals). This prevents "orphaned" nonterminals
  // (symbols that lost their productions during normalization) from masquerading as terminals.
  val originalTerminals = this.terminals
  val EPS = "ε"

  val trueTerminals: List<String> = g.terminals.asSequence()
    .filter { it != EPS && it !in nt && it in originalTerminals }
    .toList()

  val T = trueTerminals.size
  val termId: Map<String, Int> = trueTerminals.withIndex().associate { it.value to it.index }

  // --- Bit-Packing Logic (State of the art 'Long' packing for speed) ---
  // Pack: [len: LEN_BITS] | [tok1, tok2... : Remainder]
  // Token ID 0..T-1 is stored as id+1. 0 implies "no token" in that slot.
  fun bitsNeeded(x: Int): Int = (Int.SIZE_BITS - (maxOf(1, x)).countLeadingZeroBits())

  val LEN_BITS = 6 // Sufficient for n up to 63
  val bits = bitsNeeded(T + 1)
  val maskTok = (1L shl bits) - 1L
  val maxTokenBits = 63 - LEN_BITS

  require(bits * n <= maxTokenBits) {
    "Cannot pack: T=$T, n=$n => needs ${bits * n} bits (max $maxTokenBits). Reduce n or vocabulary."
  }

  // Inline primitives
  fun lenOf(gm: Long): Int = (gm and 0x3F).toInt()
  fun toksOf(gm: Long): Long = gm ushr LEN_BITS
  fun mk(len: Int, toks: Long): Long = (toks shl LEN_BITS) or len.toLong()

  fun tokAt(toks: Long, i: Int): Int = (((toks ushr (bits * i)) and maskTok).toInt() - 1)
  fun pack1(id: Int): Long = mk(1, (id + 1).toLong())

  fun maskBits(k: Int): Long = if (k <= 0) 0L else (1L shl (bits * k)) - 1L

  fun prefix(gm: Long, k: Int): Long {
    val l = lenOf(gm)
    if (k <= 0) return 0L
    if (k >= l) return gm
    return mk(k, toksOf(gm) and maskBits(k))
  }

  fun suffix(gm: Long, k: Int): Long {
    val l = lenOf(gm)
    if (k <= 0) return 0L
    if (k >= l) return gm
    val shift = bits * (l - k)
    return mk(k, (toksOf(gm) ushr shift) and maskBits(k))
  }

  fun concat(a: Long, b: Long): Long {
    val la = lenOf(a)
    val lb = lenOf(b)
    val l = la + lb
    if (l == 0) return 0L
    val toks = toksOf(a) or (toksOf(b) shl (bits * la))
    return mk(l, toks)
  }

  // --- Optimized Open-Addressing LongHashSet ---
  class LongHashSet(initCap: Int = 16) {
    private val EMPTY = Long.MIN_VALUE
    private var keys = LongArray(maxOf(16, initCap.takeHighestOneBit() * 2)) { EMPTY }
    private var size_ = 0
    val size get() = size_

    fun add(x: Long): Boolean {
      if (x == EMPTY) return false // Should not happen with valid packing
      if (size_ * 2 > keys.size) rehash() // Load factor 0.5 for speed
      var i = ((x * -7046029254386353131L) ushr 32).toInt() and (keys.size - 1)
      while (true) {
        val k = keys[i]
        if (k == EMPTY) { keys[i] = x; size_++; return true }
        if (k == x) return false
        i = (i + 1) and (keys.size - 1)
      }
    }

    fun forEach(f: (Long) -> Unit) {
      for (k in keys) if (k != EMPTY) f(k)
    }

    fun toLongArray(): LongArray {
      val out = LongArray(size_); var j = 0
      for (k in keys) if (k != EMPTY) out[j++] = k
      return out
    }

    private fun rehash() {
      val old = keys
      keys = LongArray(old.size * 2) { EMPTY }
      size_ = 0
      for (k in old) if (k != EMPTY) add(k)
    }
  }

  // --- DP Tables ---
  val W = g.nonterminals.size
  // Array of Arrays of Sets. Null represents empty set.
  val short = Array(W) { arrayOfNulls<LongHashSet>(n + 1) }
  val pref  = Array(W) { arrayOfNulls<LongHashSet>(n + 1) }
  val suff  = Array(W) { arrayOfNulls<LongHashSet>(n + 1) }
  val fac   = Array(W) { arrayOfNulls<LongHashSet>(n + 1) }

  fun ensure(t: Array<Array<LongHashSet?>>, a: Int, l: Int): LongHashSet =
    t[a][l] ?: LongHashSet().also { t[a][l] = it }

  // Adders
  fun addFac(a: Int, gm: Long): Boolean =
    if (lenOf(gm) in 1..n) ensure(fac, a, lenOf(gm)).add(gm) else false

  fun addPref(a: Int, gm: Long): Boolean {
    val l = lenOf(gm)
    if (l !in 1..n) return false
    val c1 = ensure(pref, a, l).add(gm)
    val c2 = ensure(fac, a, l).add(gm)
    return c1 || c2
  }

  fun addSuff(a: Int, gm: Long): Boolean {
    val l = lenOf(gm)
    if (l !in 1..n) return false
    val c1 = ensure(suff, a, l).add(gm)
    val c2 = ensure(fac, a, l).add(gm)
    return c1 || c2
  }

  fun addShort(a: Int, gm: Long): Boolean {
    val l = lenOf(gm)
    if (l > n) return false
    val ch = ensure(short, a, l).add(gm)
    if (!ch) return false

    // Base cases for short strings
    if (l == 1) {
      // Avoid recursion for length 1, direct add
      addPref(a, gm); addSuff(a, gm); addFac(a, gm)
    } else {
      addPref(a, prefix(gm, minOf(l, n)))
      addSuff(a, suffix(gm, minOf(l, n)))
      addFac(a, gm)
    }
    return true
  }

  // --- Initialization ---
  // We use parent-pointers to propagate updates upwards.
  class IntList {
    var data = IntArray(8); var size = 0
    fun add(v: Int) { if (size == data.size) data = data.copyOf(size*2); data[size++] = v }
  }
  class PairList {
    var data = IntArray(16); var size = 0 // Stored as [other, parent, other, parent...]
    fun add(other: Int, parent: Int) {
      if (size == data.size) data = data.copyOf(size * 2)
      data[size++] = other; data[size++] = parent
    }
  }

  val unitParents  = Array(W) { IntList() }
  val leftParents  = Array(W) { PairList() } // (C, A) for A -> self C
  val rightParents = Array(W) { PairList() } // (B, A) for A -> B self

  for ((lhs, rhs) in g) {
    if (rhs.size == 1) {
      val a = bindex[lhs]
      val s = rhs[0]
      // Only process terminals if they are in our validated trueTerminals map
      if (s in termId) {
        addShort(a, pack1(termId[s]!!))
      } else if (s == EPS) {
        addShort(a, 0L)
      } else if (s in nt) {
        // Unit production A -> B (NT) - we handle this via propagation,
        // but we need to record the parent link
        unitParents[bindex[s]].add(a)
      }
    } else if (rhs.size == 2) {
      val a = bindex[lhs]
      // Assume CNF: Both children must be NTs
      if (rhs[0] in nt && rhs[1] in nt) {
        val b = bindex[rhs[0]]
        val c = bindex[rhs[1]]
        leftParents[b].add(c, a)   // When B updates, tell A (with C as partner)
        rightParents[c].add(b, a)  // When C updates, tell A (with B as partner)
      }
    }
  }

  // --- Reachability Analysis (to seed Queue) ---
  val reachable = BooleanArray(W)
  val qArr = IntArray(W)
  var qH = 0; var qT = 0

  reachable[start] = true
  qArr[qT++] = start

  while (qH < qT) {
    val u = qArr[qH++]
    // Find children: A -> B ...
    // Note: We only have parent pointers above. We need to scan grammar or use bindex maps.
    // Since this is one-time, iterating grammar is acceptable or using g.bimap if available.
    // Iterating grammar is safest.
    for ((lhs, rhs) in g) {
      if (bindex[lhs] == u) {
        for (sym in rhs) {
          if (sym in nt) {
            val v = bindex[sym]
            if (!reachable[v]) { reachable[v] = true; qArr[qT++] = v }
          }
        }
      }
    }
  }

  // --- Worklist ---
  val inQ = BooleanArray(W)
  val workQ = IntArray(W + 1) // Ring buffer
  var wHead = 0; var wTail = 0; var wSize = 0

  fun enqueue(x: Int) {
    if (reachable[x] && !inQ[x]) {
      inQ[x] = true
      workQ[wTail] = x
      wTail = (wTail + 1) % workQ.size
      wSize++
    }
  }
  fun dequeue(): Int {
    val x = workQ[wHead]
    wHead = (wHead + 1) % workQ.size
    wSize--
    inQ[x] = false
    return x
  }

  // Seed Queue with non-empty terminals/epsilons
  for (i in 0 until W) {
    if (reachable[i]) {
      // If initialized with any data, enqueue
      var hasData = false
      for(l in 0..n) if(short[i][l]?.size ?: 0 > 0) hasData = true
      if(hasData) enqueue(i)
    }
  }

  // --- Helper to iterate sets without allocation ---
  fun forEach(set: LongHashSet?, f: (Long) -> Unit) { set?.forEach(f) }

  // --- Propagation Logic ---
  while (wSize > 0) {
    val x = dequeue()

    // 1. Propagate up Unit Productions: A -> x
    val ups = unitParents[x]
    for (i in 0 until ups.size) {
      val A = ups.data[i]
      if (!reachable[A]) continue

      var change = false
      for (l in 0..n) {
        // A inherits everything from x
        forEach(short[x][l]) { change = addShort(A, it) || change }
        forEach(pref[x][l])  { change = addPref(A, it)  || change }
        forEach(suff[x][l])  { change = addSuff(A, it)  || change }
        forEach(fac[x][l])   { change = addFac(A, it)   || change }
      }
      if (change) enqueue(A)
    }

    // 2. Propagate up Binary Productions (x is Left Child): A -> x C
    val lps = leftParents[x]
    var i = 0
    while (i < lps.size) {
      val C = lps.data[i++]; val A = lps.data[i++]
      if (!reachable[A]) continue

      var change = false
      val snapC = (A == C) // Aliasing check

      // Inherit factors/prefixes/suffixes
      for (l in 1..n) forEach(fac[x][l]) { change = addFac(A, it) || change }
      for (l in 1..n) forEach(pref[x][l]) { change = addPref(A, it) || change }

      // Combine x + C
      // Short(A) = Short(x) + Short(C)
      for (lx in 0..n) {
        val sx = short[x][lx] ?: continue
        for (lc in 0..(n - lx)) {
          val sc = short[C][lc] ?: continue
          // Use array snapshot if C is aliased to A (unlikely but safe)
          val cArr = if (snapC) sc.toLongArray() else null

          sx.forEach { gx ->
            if (cArr != null) {
              for(gc in cArr) change = addShort(A, concat(gx, gc)) || change
            } else {
              sc.forEach { gc -> change = addShort(A, concat(gx, gc)) || change }
            }
          }
        }
      }

      // Cross-boundary Prefixes: Short(x) + Pref(C)
      for (lx in 0..n) {
        val sx = short[x][lx] ?: continue
        for (lc in 1..(n - lx)) {
          val pc = pref[C][lc] ?: continue
          val cArr = if (snapC) pc.toLongArray() else null
          sx.forEach { gx ->
            if (cArr != null) for(gp in cArr) change = addPref(A, concat(gx, gp)) || change
            else pc.forEach { gp -> change = addPref(A, concat(gx, gp)) || change }
          }
        }
      }

      // Cross-boundary Suffixes: Suff(x) + Short(C)
      for (lx in 1..n) {
        val ux = suff[x][lx] ?: continue
        for (lc in 0..(n - lx)) {
          val sc = short[C][lc] ?: continue
          val cArr = if (snapC) sc.toLongArray() else null
          ux.forEach { gx ->
            if (cArr != null) for(gc in cArr) change = addSuff(A, concat(gx, gc)) || change
            else sc.forEach { gc -> change = addSuff(A, concat(gx, gc)) || change }
          }
        }
      }

      // Cross-boundary Factors: Suff(x) + Pref(C)
      for (lx in 1..n) {
        val ux = suff[x][lx] ?: continue
        for (lc in 1..(n - lx)) {
          val pc = pref[C][lc] ?: continue
          val cArr = if (snapC) pc.toLongArray() else null
          ux.forEach { gx ->
            if (cArr != null) for(gp in cArr) change = addFac(A, concat(gx, gp)) || change
            else pc.forEach { gp -> change = addFac(A, concat(gx, gp)) || change }
          }
        }
      }

      if (change) enqueue(A)
    }

    // 3. Propagate up Binary Productions (x is Right Child): A -> B x
    val rps = rightParents[x]
    i = 0
    while (i < rps.size) {
      val B = rps.data[i++]; val A = rps.data[i++]
      if (!reachable[A]) continue

      var change = false
      val snapB = (A == B)

      // Inherit factors/suffixes
      for (l in 1..n) forEach(fac[x][l]) { change = addFac(A, it) || change }
      for (l in 1..n) forEach(suff[x][l]) { change = addSuff(A, it) || change }

      // Logic mirrors the Left Child case, but x is now the 2nd operand
      // Short(A) = Short(B) + Short(x)
      for (lb in 0..n) {
        val sb = short[B][lb] ?: continue
        val bArr = if (snapB) sb.toLongArray() else null
        for (lx in 0..(n - lb)) {
          val sx = short[x][lx] ?: continue
          sx.forEach { gx ->
            if (bArr != null) for(gb in bArr) change = addShort(A, concat(gb, gx)) || change
            else sb.forEach { gb -> change = addShort(A, concat(gb, gx)) || change }
          }
        }
      }

      // Pref(A) crossing: Short(B) + Pref(x)
      for (lb in 0..n) {
        val sb = short[B][lb] ?: continue
        val bArr = if (snapB) sb.toLongArray() else null
        for (lx in 1..(n - lb)) {
          val px = pref[x][lx] ?: continue
          px.forEach { gx ->
            if (bArr != null) for(gb in bArr) change = addPref(A, concat(gb, gx)) || change
            else sb.forEach { gb -> change = addPref(A, concat(gb, gx)) || change }
          }
        }
      }

      // Suff(A) crossing: Suff(B) + Short(x)
      for (lb in 1..n) {
        val ub = suff[B][lb] ?: continue
        val bArr = if (snapB) ub.toLongArray() else null
        for (lx in 0..(n - lb)) {
          val sx = short[x][lx] ?: continue
          sx.forEach { gx ->
            if (bArr != null) for(gb in bArr) change = addSuff(A, concat(gb, gx)) || change
            else ub.forEach { gb -> change = addSuff(A, concat(gb, gx)) || change }
          }
        }
      }

      // Factors crossing: Suff(B) + Pref(x)
      for (lb in 1..n) {
        val ub = suff[B][lb] ?: continue
        val bArr = if (snapB) ub.toLongArray() else null
        for (lx in 1..(n - lb)) {
          val px = pref[x][lx] ?: continue
          px.forEach { gx ->
            if (bArr != null) for(gb in bArr) change = addFac(A, concat(gb, gx)) || change
            else ub.forEach { gb -> change = addFac(A, concat(gb, gx)) || change }
          }
        }
      }

      if (change) enqueue(A)
    }
  }

  // --- Decoding ---
  val result = HashSet<List<String>>()
  for (l in 1..n) {
    fac[start][l]?.forEach { gm ->
      val toks = toksOf(gm)
      val list = ArrayList<String>(l)
      for (k in 0 until l) {
        list.add(trueTerminals[tokAt(toks, k)])
      }
      result.add(list)
    }
  }
  return result
}

/**
 * Exports the NFA to the SafeTensors format as a Byte array.
 * This is pure Kotlin Common code (no JDK/java.io dependencies).
 */
fun NFA.toSafeTensors(): ByteArray {
  // --- 1. Flatten the Graph ---
  // We need a stable vocabulary (0 is reserved for Epsilon in our tensor representation)
  val vocabSet = mutableSetOf<String>()
  transitions.values.forEach { edges ->
    edges.forEach { edge -> edge.label?.let { vocabSet.add(it) } }
  }
  val vocabList = vocabSet.sorted()
  val vocabMap = vocabList.withIndex().associate { it.value to (it.index + 1) }

  // Use generic Lists, not platform specific arrays
  val sources = ArrayList<Int>()
  val targets = ArrayList<Int>()
  val labels = ArrayList<Int>()

  // Sort by source state for deterministic output
  val sortedStates = transitions.keys.sorted()
  for (src in sortedStates) {
    val edges = transitions[src] ?: continue
    // Sort edges: Target then Label
    val sortedEdges = edges.sortedWith(compareBy({ it.target }, { it.label ?: "" }))
    for (edge in sortedEdges) {
      sources.add(src)
      targets.add(edge.target)
      labels.add(if (edge.label == null) 0 else vocabMap[edge.label]!!)
    }
  }

  val numEdges = sources.size
  val intSize = 4
  val floatSize = 4

  // Calculate offsets for the data blob
  // Data order: sources(I32) -> targets(I32) -> labels(I32) -> scores(F32)
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
  fun toJsonIntList(list: Collection<Int>) = list.joinToString(",", "[", "]")
  fun toJsonStrList(list: List<String>) = list.joinToString(",", "[", "]") { "\"${it.escapeJson()}\"" }

  // Manual JSON construction to avoid heavy dependencies
  val metadata = """
    "vocab":${toJsonStrList(vocabList)},
    "start_states":${toJsonIntList(startStates)},
    "final_states":${toJsonIntList(finalStates)}
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
  // SafeTensors spec: header length is an 8-byte unsigned integer (LE)
  val headerLen = headerBytes.size.toLong()

  // --- 3. Allocate and Fill Result Buffer ---
  // Total size = 8 bytes (N) + N bytes (Header) + Data Blob
  val totalSize = 8 + headerBytes.size + totalDataSize
  val result = ByteArray(totalSize)
  var pos = 0

  // Helper: Little Endian writers
  fun writeLongLE(v: Long) {
    result[pos++] = (v and 0xFF).toByte()
    result[pos++] = ((v ushr 8) and 0xFF).toByte()
    result[pos++] = ((v ushr 16) and 0xFF).toByte()
    result[pos++] = ((v ushr 24) and 0xFF).toByte()
    result[pos++] = ((v ushr 32) and 0xFF).toByte()
    result[pos++] = ((v ushr 40) and 0xFF).toByte()
    result[pos++] = ((v ushr 48) and 0xFF).toByte()
    result[pos++] = ((v ushr 56) and 0xFF).toByte()
  }

  fun writeIntLE(v: Int) {
    result[pos++] = (v and 0xFF).toByte()
    result[pos++] = ((v ushr 8) and 0xFF).toByte()
    result[pos++] = ((v ushr 16) and 0xFF).toByte()
    result[pos++] = ((v ushr 24) and 0xFF).toByte()
  }

  fun writeFloatLE(v: Float) {
    writeIntLE(v.toBits()) // Kotlin generic support for float bits
  }

  // A. Write Header Size (u64)
  writeLongLE(headerLen)

  // B. Write Header JSON
  headerBytes.copyInto(result, pos)
  pos += headerBytes.size

  // C. Write Data Tensors
  sources.forEach { writeIntLE(it) }
  targets.forEach { writeIntLE(it) }
  labels.forEach { writeIntLE(it) }
  // Initialize scores to 0.0 (Log-Space 1.0)
  repeat(numEdges) { writeFloatLE(0.0f) }

  return result
}

// Simple JSON string escaper
private fun String.escapeJson(): String =
  this.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")