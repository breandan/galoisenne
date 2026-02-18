package ai.hypergraph.kaliningraph.parsing.approximations

import ai.hypergraph.kaliningraph.parsing.*
import kotlin.time.TimeSource

// Immutable key: multiset of nonterminals (counts vector) + cached sum(counts)
private class VecKey(val counts: IntArray, val sum: Int) {
  override fun equals(other: Any?): Boolean =
    other is VecKey && sum == other.sum && counts.contentEquals(other.counts)
  override fun hashCode(): Int = 31 * sum + counts.contentHashCode()
}

private data class ParikhProd(
  val lhsIdx: Int,
  val rhsNtIdxs: IntArray,         // indices of nonterminals occurring in RHS (with repetition)
  val rhsTerms: Array<Σᐩ>,         // terminals occurring in RHS, in order
  val deltaNtSum: Int              // = rhsNtIdxs.size - 1
)
// ------------------------------------------------------------
// KMP-Compatible Parikh Image NFA
// ------------------------------------------------------------

/**
 * Immutable key for the State Map.
 * Represents a sparse vector: [ntIndex, count, ntIndex, count, ...]
 */
private class SparseKey(val data: IntArray, val totalSum: Int) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is SparseKey) return false
    return totalSum == other.totalSum && data.contentEquals(other.data)
  }
  override fun hashCode(): Int = totalSum * 31 + data.contentHashCode()
}

/**
 * Mutable probe to query the map without allocating a SparseKey.
 * This works because Kotlin's MutableMap uses the object's current hashCode/equals state.
 */
private class ProbeKey {
  var data: IntArray = IntArray(0)
  var totalSum: Int = 0

  override fun equals(other: Any?): Boolean {
    if (other !is SparseKey) return false
    return totalSum == other.totalSum && data.contentEquals(other.data)
  }
  override fun hashCode(): Int = totalSum * 31 + data.contentHashCode()

  fun setFrom(other: IntArray, sum: Int) {
    this.data = other
    this.totalSum = sum
  }
}

fun CFG.toParikhImageNFA(
  startSymbol: Σᐩ = "START",
  k: Int = 5, // Default small bound
  removeEpsilons: Boolean = true,
  trim: Boolean = true
): NFA {
  val timer = TimeSource.Monotonic.markNow()

  // 1. Index Non-terminals canonically (Alphabetical sort)
  val ntsList = this.nonterminals.toList().sorted()
  val ntMap = ntsList.mapIndexed { i, s -> s to i }.toMap()
  val numNTs = ntsList.size

  // 2. Pre-process Productions
  data class FastProd(
    val lhsIdx: Int,
    val rhsNtIndices: IntArray, // Sorted indices of NTs in RHS
    val rhsTerminals: List<Σᐩ>,
    val netChange: Int
  )

  val prodsByLhs = Array(numNTs) { ArrayList<FastProd>() }
  for (prod in this) {
    val lhsIdx = ntMap[prod.LHS] ?: continue

    // Collect RHS NTs and sort them for easier merging later
    val rhsNt = mutableListOf<Int>()
    val rhsTerm = mutableListOf<Σᐩ>()
    for (sym in prod.RHS) {
      val idx = ntMap[sym]
      if (idx != null) rhsNt.add(idx)
      else if (sym != EPS_SYM) rhsTerm.add(sym)
    }
    rhsNt.sort() // Sort to ensure canonical order for vector merge

    prodsByLhs[lhsIdx].add(FastProd(
      lhsIdx,
      rhsNt.toIntArray(),
      rhsTerm,
      rhsNt.size - 1
    ))
  }

  // 3. State Management
  val stateIdMap = mutableMapOf<Any, Int>() // Key: SparseKey | ProbeKey
  val queue = ArrayDeque<SparseKey>()
  var nextStateId = 0
  val probe = ProbeKey()

  // Helper: Get or Create State ID
  fun getOrAllocState(newData: IntArray, newSum: Int): Int {
    // A. Try looking up with the reusable probe
    probe.setFrom(newData, newSum)
    val existing = stateIdMap[probe]
    if (existing != null) return existing

    // B. Not found: Allocate immutable key
    val permanentKey = SparseKey(newData, newSum)
    val id = nextStateId++
    stateIdMap[permanentKey] = id
    queue.add(permanentKey)
    return id
  }

  // Initialize Start State
  val startIdx = ntMap[startSymbol] ?: error("Start symbol $startSymbol not in NonTerminals")
  val startId = getOrAllocState(intArrayOf(startIdx, 1), 1)

  // Transitions
  val transitions = mutableMapOf<Int, MutableList<NFA.Edge>>()
  fun addEdge(u: Int, label: Σᐩ?, v: Int) {
    transitions.getOrPut(u) { mutableListOf() }.add(NFA.Edge(label, v))
  }

  // 4. Main BFS Loop
  while (queue.isNotEmpty()) {
    val currKey = queue.removeFirst()
    val u = stateIdMap[currKey]!! // Safe because we just pulled it from queue
    val vec = currKey.data // [idx, count, idx, count...]

    // Optimization: Only iterate Active Non-Terminals
    var i = 0
    while (i < vec.size) {
      val ntIdx = vec[i]
      // val ntCount = vec[i+1] // Not strictly needed for logic, just existence
      i += 2

      val productions = prodsByLhs[ntIdx]
      for (p in productions) {
        // Check Bound
        val newSum = currKey.totalSum + p.netChange
        if (newSum > k) continue

        // --- Vector Update (KMP Friendly) ---
        // We perform: NewVec = OldVec - 1*LHS + 1*RHS
        // Since OldVec and RHS are both sorted by index, we can do this efficiently.
        val newVec = updateVector(vec, ntIdx, p.rhsNtIndices)
        // ------------------------------------

        val v = getOrAllocState(newVec, newSum)

        // Emit Edges
        val terms = p.rhsTerminals
        if (terms.isEmpty()) {
          addEdge(u, null, v)
        } else if (terms.size == 1) {
          addEdge(u, terms[0], v)
        } else {
          // Chain intermediate states
          var curr = u
          for (tIdx in 0 until terms.size - 1) {
            val midState = nextStateId++ // Alloc new raw ID
            addEdge(curr, terms[tIdx], midState)
            curr = midState
          }
          addEdge(curr, terms.last(), v)
        }
      }
    }
  }

  // Identify Final State (Empty Vector)
  probe.setFrom(IntArray(0), 0)
  val finalState = stateIdMap[probe]
  val finals = if (finalState != null) setOf(finalState) else emptySet()

  val raw = NFA(setOf(startId), finals, transitions)
  val n1 = if (trim) raw.trim() else raw
  return if (removeEpsilons) n1.removeEpsilons() else n1
}

/**
 * Pure KMP function to update a sparse vector.
 * @param current: [idx, count, ...] sorted by idx
 * @param decrementIdx: The NT index to decrement
 * @param addIndices: Sorted array of NT indices to increment
 */
private fun updateVector(current: IntArray, decrementIdx: Int, addIndices: IntArray): IntArray {
  // 1. Unpack current to a small mutable buffer
  // Since k is small, we can use a simple list or array.
  // Using two parallel arrays for simplicity in pure Kotlin common.
  val indices = mutableListOf<Int>()
  val counts = mutableListOf<Int>()

  var i = 0
  while (i < current.size) {
    indices.add(current[i])
    counts.add(current[i+1])
    i += 2
  }

  // 2. Decrement LHS
  // We know decrementIdx exists because we are iterating active NTs
  val loc = indices.binarySearch(decrementIdx)
  if (loc >= 0) {
    if (counts[loc] == 1) {
      indices.removeAt(loc)
      counts.removeAt(loc)
    } else {
      counts[loc] = counts[loc] - 1
    }
  }

  // 3. Increment RHS (Sorted Insert)
  // Since addIndices is sorted, we can insert efficiently.
  for (addIdx in addIndices) {
    // Find position
    // Optimization: search relative to last insert to be faster?
    // For small k, standard binarySearch is fine.
    var pos = indices.binarySearch(addIdx)
    if (pos >= 0) {
      counts[pos] = counts[pos] + 1
    } else {
      // Not found, insert at -insertionPoint - 1
      val insertAt = -(pos + 1)
      indices.add(insertAt, addIdx)
      counts.add(insertAt, 1)
    }
  }

  // 4. Repack
  val result = IntArray(indices.size * 2)
  for (j in indices.indices) {
    result[j * 2] = indices[j]
    result[j * 2 + 1] = counts[j]
  }
  return result
}