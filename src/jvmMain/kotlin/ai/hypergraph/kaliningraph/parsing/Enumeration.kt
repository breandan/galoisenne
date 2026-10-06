package edu.mcgill.cstk.experiments.repair

import ai.hypergraph.kaliningraph.KBitSet
import ai.hypergraph.kaliningraph.automata.latestLangEditDistance
import ai.hypergraph.kaliningraph.parsing.*
import ai.hypergraph.kaliningraph.repair.LED_BUFFER
import ai.hypergraph.kaliningraph.repair.MAX_RADIUS
import java.math.BigInteger
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.ForkJoinTask
import java.util.concurrent.FutureTask
import java.util.concurrent.RecursiveAction
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.stream.IntStream
import kotlin.math.abs

/** Exact size statistics for a fixed-length DFA slice. */
data class DFASize(val states: Long, val transitions: Long, val languageSize: BigInteger = BigInteger.ZERO)

/**
 * Constructs an exact deterministic partial DFA for L(this) ∩ Σ^[length].
 *
 * The historical name is retained for source compatibility; the returned automaton is not
 * required to be minimal. Determinism, completeness of the represented slice, and exact
 * distinct-word cardinalities are preserved. Structural hash-consing is an optional space/time
 * optimization and can be disabled with `-Dcstk.dfa.structuralSharing=false`. Exact completed
 * product states are memoized across length waves for the arena's lifetime. An optional completed
 * result budget can be configured with `-Dcstk.dfa.productMemoBytes=<bytes>` (zero retains only the
 * active wave); by default the persistent memo is unbounded.
 */
fun CFG.minimalSliceDFA(length: Int, onSlice: (Int, DFASize) -> Unit = { _, _ -> }): PackedDFA {
  require(length >= 1)
  val builder = IncrementalSliceBuilder(this)
  var finalSlice: SliceRoot? = null
  var finalSize: DFASize? = null
  repeat(length) {
    val slice = builder.nextSlice()
    val size = builder.sizeOf(slice)
    onSlice(slice.length, size)
    finalSlice = slice
    finalSize = size
  }
  return builder.pack(finalSlice!!, finalSize!!)
}

/**
 * A compact deterministic acyclic automaton for a finite set of token strings.
 *
 * The transition rows are stored in CSR form. States are memoized derivative residuals, which is
 * sufficient for determinism; equivalent transition rows are deliberately not minimized. This is
 * therefore a possibly nonminimal DAFSA rather than a parse-forest encoding whose paths may contain
 * duplicate strings. [finalState] is the canonical final sink, but other states may also be
 * accepting when one repair is a prefix of another.
 */
class PackedDAFSA internal constructor(
  val terminals: List<String>,
  val startState: Int,
  val finalState: Int,
  private val offsets: IntArray,
  private val labels: IntArray,
  private val targets: IntArray,
  private val accepting: BooleanArray,
  private val suffixLanguageSizes: Array<BigInteger>,
  val forestNodeCount: Int,
  val derivativeComputations: Int
) {
  val stateCount: Int get() = offsets.size - 1
  val transitionCount: Int get() = labels.size
  val languageSize: BigInteger get() = suffixLanguageSizes[startState]
  private val terminalIds by lazy(LazyThreadSafetyMode.PUBLICATION) {
    terminals.withIndex().associate { (index, terminal) -> terminal to index }
  }

  fun summarize(): String =
    "(states=$stateCount, transitions=$transitionCount, words=$languageSize, " +
      "forestNodes=$forestNodeCount, derivatives=$derivativeComputations)"

  fun recognizes(tokens: Iterable<String>): Boolean {
    var state = startState
    for (token in tokens) {
      val label = terminalIds[token] ?: return false
      state = transition(state, label)
      if (state < 0) return false
    }
    return isFinal(state)
  }

  fun isFinal(state: Int): Boolean = state in accepting.indices && accepting[state]
  fun outBegin(state: Int): Int = offsets[state]
  fun outEnd(state: Int): Int = offsets[state + 1]
  fun labelAt(edge: Int): Int = labels[edge]
  fun targetAt(edge: Int): Int = targets[edge]

  /** Enumerates every accepted token string exactly once in terminal-name order. */
  fun tokenWords(shouldContinue: () -> Boolean = { true }): Sequence<List<String>> = sequence {
    if (finalState < 0) return@sequence
    val path = ArrayList<String>()

    suspend fun SequenceScope<List<String>>.visit(state: Int) {
      if (!shouldContinue()) return
      if (isFinal(state)) yield(path.toList())

      for (edge in outBegin(state) until outEnd(state)) {
        val terminal = terminals[labelAt(edge)]
        if (terminal != "ε") path.add(terminal)
        visit(targetAt(edge))
        if (terminal != "ε") path.removeAt(path.lastIndex)
        if (!shouldContinue()) return
      }
    }

    visit(startState)
  }

  fun words(shouldContinue: () -> Boolean = { true }): Sequence<String> =
    tokenWords(shouldContinue).map { it.joinToString(" ") }

  /** Maps a zero-based rank to its unique repair in the DAFSA's edge order. */
  fun unrank(rank: BigInteger): List<String> {
    require(rank >= BigInteger.ZERO && rank < languageSize) {
      "Rank $rank is outside [0, $languageSize)"
    }
    var remaining = rank
    var state = startState
    val word = ArrayList<String>()

    while (true) {
      if (isFinal(state)) {
        if (remaining == BigInteger.ZERO) return word
        remaining -= BigInteger.ONE
      }

      var selected = false
      for (edge in outBegin(state) until outEnd(state)) {
        val branchSize = suffixLanguageSizes[targetAt(edge)]
        if (remaining < branchSize) {
          word.add(terminals[labelAt(edge)])
          state = targetAt(edge)
          selected = true
          break
        }
        remaining -= branchSize
      }
      check(selected) { "No branch contains rank $rank" }
    }
  }

  /** Inverse of [unrank] for accepted repairs. */
  fun rank(tokens: Iterable<String>): BigInteger {
    var rank = BigInteger.ZERO
    var state = startState

    for (token in tokens) {
      if (isFinal(state)) rank += BigInteger.ONE
      val wanted = terminalIds[token] ?: throw IllegalArgumentException("Unknown terminal: $token")
      var selected = false
      for (edge in outBegin(state) until outEnd(state)) {
        if (labelAt(edge) == wanted) {
          state = targetAt(edge)
          selected = true
          break
        }
        rank += suffixLanguageSizes[targetAt(edge)]
      }
      require(selected) { "Word is not accepted: no transition for $token" }
    }

    require(isFinal(state)) { "Word is not accepted: input ended in a non-final state" }
    return rank
  }

  /** Returns the target state, or -1 when the partial DAFSA has no such transition. */
  fun transition(state: Int, wanted: Int): Int {
    if (state !in 0 until stateCount) return -1
    for (edge in outBegin(state) until outEnd(state))
      if (labelAt(edge) == wanted) return targetAt(edge)
    return -1
  }
}

/**
 * Builds the same bounded CFG/Levenshtein intersection used by sparse-GRE repair. Chart cells hold
 * shared finite-language expressions; only residuals reachable from the final root are
 * determinized. This is intentionally a small sequential CPU implementation.
 */
fun repairWithPackedDAFSA(brokenTokens: List<String>, cfg: CFG): PackedDAFSA? {
  val start = cfg.bindex[START_SYMBOL]
  val leftAdjacency = cfg.leftAdj
  val nonterminalCount = cfg.nonterminals.size

  fun languageEditDistance(radius: Int): Int? {
    val levFSA = makeLevFSA(brokenTokens, radius)
    val stateCount = levFSA.numStates
    val active = Array(stateCount) { Array(stateCount) { KBitSet(nonterminalCount) } }
    val activeCounts = Array(stateCount) { IntArray(stateCount) }

    levFSA.allIndexedTxs2(cfg.grpUPs, cfg.bindex).forEach { (p, nonterminal, q) ->
      if (!active[p][q][nonterminal]) {
        active[p][q].set(nonterminal)
        activeCounts[p][q]++
      }
    }

    var minimum = Int.MAX_VALUE
    for (distance in 1 until stateCount) {
      for (p in 0 until stateCount - distance) {
        val q = p + distance
        val midpoints = levFSA.allPairs[p][q] ?: continue
        val target = active[p][q]

        for (midpoint in midpoints) {
          if (activeCounts[p][midpoint] == 0 || activeCounts[midpoint][q] == 0) continue
          val right = active[midpoint][q]
          active[p][midpoint].forEachSetBit { leftNonterminal ->
            leftAdjacency[leftNonterminal]?.forEachIfIn(right) { _, parent ->
              if (!target[parent]) {
                target.set(parent)
                activeCounts[p][q]++
                if (p == 0 && parent == start && levFSA.isFinal[q]) {
                  val (x, y) = checkNotNull(levFSA.idsToCoords[q])
                  minimum = minOf(minimum, abs(brokenTokens.size - x + y))
                }
              }
            }
          }
          if (minimum == 1) return 1
        }
      }
    }

    return minimum.takeUnless { it == Int.MAX_VALUE }
  }

  val upperBound = MAX_RADIUS * 3
  val editDistance = (3 until upperBound).firstNotNullOfOrNull(::languageEditDistance) ?: upperBound
  val radius = (editDistance + LED_BUFFER).coerceAtMost(MAX_RADIUS + LED_BUFFER)
  latestLangEditDistance = editDistance

  val levFSA = makeLevFSA(brokenTokens, radius)
  val stateCount = levFSA.numStates
  val forest = RepairForest()
  val active = Array(stateCount) { Array(stateCount) { KBitSet(nonterminalCount) } }
  val chart = Array(stateCount) { Array(stateCount) { mutableMapOf<Int, Int>() } }
  val terminalSeeds = Array(stateCount) {
    Array(stateCount) { mutableMapOf<Int, KBitSet>() }
  }

  levFSA.allIndexedTxs1(cfg.grpUPs).forEach { (p, terminal, q) ->
    val terminalIndex = cfg.tmMap[terminal] ?: return@forEach
    for (parent in cfg.tmToVidx[terminalIndex]) {
      terminalSeeds[p][q].getOrPut(parent) { KBitSet(cfg.tmLst.size) }.set(terminalIndex)
    }
  }

  for (p in 0 until stateCount) {
    for (q in p + 1 until stateCount) {
      for ((parent, terminalSet) in terminalSeeds[p][q]) {
        active[p][q].set(parent)
        chart[p][q][parent] = forest.terminals(terminalSet.toList(), cfg.tmLst)
      }
    }
  }

  val alternatives = mutableMapOf<Int, MutableList<Int>>()
  for (distance in 1 until stateCount) {
    for (p in 0 until stateCount - distance) {
      val q = p + distance
      val midpoints = levFSA.allPairs[p][q] ?: continue
      alternatives.clear()

      for (midpoint in midpoints) {
        val leftRoots = chart[p][midpoint]
        val rightRoots = chart[midpoint][q]
        if (leftRoots.isEmpty() || rightRoots.isEmpty()) continue
        val rightActive = active[midpoint][q]

        active[p][midpoint].forEachSetBit { leftNonterminal ->
          val leftRoot = leftRoots[leftNonterminal] ?: return@forEachSetBit
          leftAdjacency[leftNonterminal]?.forEachIfIn(rightActive) { rightNonterminal, parent ->
            val rightRoot = rightRoots[rightNonterminal] ?: return@forEachIfIn
            alternatives.getOrPut(parent) { mutableListOf() }
              .add(forest.concat(leftRoot, rightRoot))
          }
        }
      }

      val cell = chart[p][q]
      for ((parent, children) in alternatives) {
        cell[parent]?.let(children::add)
        cell[parent] = forest.union(children)
        active[p][q].set(parent)
      }
    }
  }

  val roots = levFSA.levFinalIdxs.mapNotNull { chart[0][it][start] }
  if (roots.isEmpty()) return null
  return forest.determinizeAndPack(forest.union(roots), cfg.tmLst)
}

/** Shared finite-language expression DAG. Only root-reachable derivatives become DFA states. */
private class RepairForest {
  private sealed interface Node
  private data object Epsilon : Node
  private class Terminals(val labels: IntArray) : Node
  private class Union(val children: IntArray) : Node
  private data class Concat(val left: Int, val right: Int) : Node

  private class IntArrayKey(private val values: IntArray) {
    override fun hashCode(): Int = values.contentHashCode()
    override fun equals(other: Any?): Boolean =
      other is IntArrayKey && values.contentEquals(other.values)
  }

  private data class DFARow(val accepting: Boolean, val labels: IntArray, val targets: IntArray)

  private val nodes = ArrayList<Node>().apply { add(Epsilon) }
  private val terminalNodes = HashMap<IntArrayKey, Int>()
  private val unionNodes = HashMap<IntArrayKey, Int>()
  private val concatNodes = HashMap<Long, Int>()
  private val nullableMemo = HashMap<Int, Boolean>()
  private val derivativeMemo = HashMap<Int, Map<Int, Int>>()

  fun terminals(rawLabels: List<Int>, terminalNames: List<String>): Int {
    if (rawLabels.isEmpty()) return EMPTY
    val includesEpsilon = rawLabels.any { terminalNames[it] == "ε" }
    val labels = rawLabels.filterNot { terminalNames[it] == "ε" }
      .distinct().sorted().toIntArray()
    if (labels.isEmpty()) return if (includesEpsilon) EPSILON else EMPTY
    val key = IntArrayKey(labels)
    val terminalNode = terminalNodes[key]
      ?: add(Terminals(labels)).also { terminalNodes[key] = it }
    return if (includesEpsilon) union(listOf(EPSILON, terminalNode)) else terminalNode
  }

  fun union(rawChildren: List<Int>): Int {
    if (rawChildren.isEmpty()) return EMPTY
    val flattened = ArrayList<Int>()
    for (child in rawChildren) when {
      child == EMPTY -> Unit
      nodes[child] is Union -> flattened.addAll((nodes[child] as Union).children.toList())
      else -> flattened.add(child)
    }
    if (flattened.isEmpty()) return EMPTY
    val children = flattened.distinct().sorted().toIntArray()
    if (children.size == 1) return children[0]
    val key = IntArrayKey(children)
    return unionNodes[key] ?: add(Union(children)).also { unionNodes[key] = it }
  }

  fun concat(left: Int, right: Int): Int = when {
    left == EMPTY || right == EMPTY -> EMPTY
    left == EPSILON -> right
    right == EPSILON -> left
    else -> {
      val key = pack(left, right)
      concatNodes[key] ?: add(Concat(left, right)).also { concatNodes[key] = it }
    }
  }

  fun determinizeAndPack(root: Int, terminals: List<String>): PackedDAFSA {
    require(root != EMPTY)
    val residualIds = mutableMapOf(root to 0)
    val residuals = arrayListOf(root)
    val rows = ArrayList<DFARow>()
    var nextState = 0

    while (nextState < residuals.size) {
      val residual = residuals[nextState]
      val transitions = derivatives(residual).entries.sortedBy { terminals[it.key] }
      val labels = IntArray(transitions.size)
      val targets = IntArray(transitions.size)
      transitions.forEachIndexed { edge, (label, targetResidual) ->
        labels[edge] = label
        targets[edge] = residualIds.getOrPut(targetResidual) {
          residuals.add(targetResidual)
          residuals.lastIndex
        }
      }
      rows.add(DFARow(nullable(residual), labels, targets))
      nextState++
    }

    val offsets = IntArray(rows.size + 1)
    for (state in rows.indices) offsets[state + 1] = offsets[state] + rows[state].labels.size
    val labels = IntArray(offsets.last())
    val targets = IntArray(offsets.last())
    val accepting = BooleanArray(rows.size)
    for (state in rows.indices) {
      accepting[state] = rows[state].accepting
      rows[state].labels.copyInto(labels, offsets[state])
      rows[state].targets.copyInto(targets, offsets[state])
    }

    val counts = arrayOfNulls<BigInteger>(rows.size)
    val visiting = BooleanArray(rows.size)
    fun count(state: Int): BigInteger {
      counts[state]?.let { return it }
      check(!visiting[state]) { "Repair residual graph must be acyclic" }
      visiting[state] = true
      var result = if (accepting[state]) BigInteger.ONE else BigInteger.ZERO
      for (edge in offsets[state] until offsets[state + 1]) result += count(targets[edge])
      visiting[state] = false
      counts[state] = result
      return result
    }
    val suffixLanguageSizes = Array(rows.size) { count(it) }

    return PackedDAFSA(
      terminals = terminals.toList(),
      startState = 0,
      finalState = residualIds[EPSILON] ?: -1,
      offsets = offsets,
      labels = labels,
      targets = targets,
      accepting = accepting,
      suffixLanguageSizes = suffixLanguageSizes,
      forestNodeCount = nodes.size,
      derivativeComputations = derivativeMemo.size
    )
  }

  private fun derivatives(expression: Int): Map<Int, Int> {
    if (expression == EMPTY || expression == EPSILON) return emptyMap()
    derivativeMemo[expression]?.let { return it }

    val alternatives = mutableMapOf<Int, MutableList<Int>>()
    fun merge(transitions: Map<Int, Int>, suffix: Int = EPSILON) {
      for ((label, residual) in transitions) {
        alternatives.getOrPut(label) { mutableListOf() }.add(concat(residual, suffix))
      }
    }

    when (val node = nodes[expression]) {
      Epsilon -> Unit
      is Terminals -> for (label in node.labels)
        alternatives.getOrPut(label) { mutableListOf() }.add(EPSILON)
      is Union -> for (child in node.children) merge(derivatives(child))
      is Concat -> {
        merge(derivatives(node.left), node.right)
        if (nullable(node.left)) merge(derivatives(node.right))
      }
    }

    val result = alternatives.mapValues { (_, residuals) -> union(residuals) }
    derivativeMemo[expression] = result
    return result
  }

  private fun nullable(expression: Int): Boolean {
    if (expression == EMPTY) return false
    nullableMemo[expression]?.let { return it }
    val result = when (val node = nodes[expression]) {
      Epsilon -> true
      is Terminals -> false
      is Union -> node.children.any(::nullable)
      is Concat -> nullable(node.left) && nullable(node.right)
    }
    nullableMemo[expression] = result
    return result
  }

  private fun add(node: Node): Int = nodes.size.also { nodes.add(node) }

  companion object {
    const val EMPTY = -1
    const val EPSILON = 0
    private fun pack(left: Int, right: Int): Long =
      (left.toLong() shl Int.SIZE_BITS) or (right.toLong() and 0xffffffffL)
  }
}

/** Lazily enumerates the exact DFA languages L(this) ∩ Σ^[1, maxLength] in token shortlex order. */
fun CFG.wordsInShortlexOrder(maxLength: Int): Sequence<List<String>> {
  require(maxLength >= 1)
  return sequence {
    val builder = IncrementalSliceBuilder(this@wordsInShortlexOrder)
    repeat(maxLength) { yieldAll(builder.words(builder.nextSlice())) }
  }
}

/** Primitive CSR representation of a deterministic finite automaton. */
class PackedDFA internal constructor(
  val terminals: List<String>,
  val startState: Int,
  val finalState: Int,
  val languageSize: BigInteger,
  private val suffixLanguageSizes: Array<BigInteger>,
  private val offsets: IntArray,
  private val edges: LongArray
) {
  val stateCount: Int get() = offsets.size - 1
  val transitionCount: Int get() = edges.size
  val width: Int get() = terminals.size
  val size: DFASize get() = DFASize(stateCount.toLong(), transitionCount.toLong(), languageSize)
  fun summarize() = "(states=$stateCount, transitions=$transitionCount)"
  private val labelsInLexicographicOrder = terminals.indices.sortedBy(terminals::get).toIntArray()
  private val lexicographicRankByLabel = IntArray(width).also { ranks ->
    labelsInLexicographicOrder.forEachIndexed { rank, label -> ranks[label] = rank }
  }
  private val terminalIds by lazy(LazyThreadSafetyMode.PUBLICATION) {
    terminals.withIndex().associate { (i, terminal) -> terminal to i }
  }

  fun isFinal(state: Int): Boolean = finalState >= 0 && state == finalState
  fun outBegin(state: Int): Int = offsets[state]
  fun outEnd(state: Int): Int = offsets[state + 1]
  private fun encodedLabelAt(edge: Int): Int = (edges[edge] ushr Int.SIZE_BITS).toInt()
  fun labelAt(edge: Int): Int = labelsInLexicographicOrder[encodedLabelAt(edge)]
  fun targetAt(edge: Int): Int = edges[edge].toInt()

  /** Returns the target state, or -1 when the partial DFA has no such transition. */
  fun transition(state: Int, label: Int): Int {
    if (label !in lexicographicRankByLabel.indices) return -1
    val wanted = lexicographicRankByLabel[label]
    var low = outBegin(state)
    var high = outEnd(state) - 1
    while (low <= high) {
      val middle = (low + high) ushr 1
      val found = encodedLabelAt(middle)
      when {
        found < wanted -> low = middle + 1
        wanted < found -> high = middle - 1
        else -> return targetAt(middle)
      }
    }
    return -1
  }

  fun recognizes(labels: IntArray): Boolean {
    var state = startState
    for (label in labels) {
      state = transition(state, label)
      if (state < 0) return false
    }
    return isFinal(state)
  }

  fun recognizes(tokens: Iterable<String>): Boolean {
    var state = startState
    for (token in tokens) {
      val label = terminalIds[token] ?: return false
      state = transition(state, label)
      if (state < 0) return false
    }
    return isFinal(state)
  }

  /** Maps a zero-based rank in [0, |L|) to its unique word in lexicographic order. */
  fun unrank(rank: BigInteger): List<String> {
    require(rank.signum() >= 0 && rank < languageSize) { "Rank $rank is outside [0, $languageSize)" }
    var remaining = rank
    var state = startState
    val word = ArrayList<String>()

    while (!isFinal(state)) {
      var selected = false
      for (edge in outBegin(state) until outEnd(state)) {
        val target = targetAt(edge)
        val branchSize = suffixLanguageSizes[target]
        if (remaining < branchSize) {
          word += terminals[labelAt(edge)]
          state = target
          selected = true
          break
        }
        remaining -= branchSize
      }
      check(selected) { "No branch contains rank $rank" }
    }
    check(remaining == BigInteger.ZERO)
    return word
  }

  /** Inverse of [unrank] for words accepted by this fixed-length DFA slice. */
  fun rank(tokens: Iterable<String>): BigInteger {
    var rank = BigInteger.ZERO
    var state = startState

    for (token in tokens) {
      val wanted = terminalIds[token] ?: throw IllegalArgumentException("Unknown terminal: $token")
      var selected = false
      for (edge in outBegin(state) until outEnd(state)) {
        val target = targetAt(edge)
        if (labelAt(edge) == wanted) {
          state = target
          selected = true
          break
        }
        rank += suffixLanguageSizes[target]
      }
      require(selected) { "Word is not accepted: no transition for $token" }
    }
    require(isFinal(state)) { "Word is not accepted: input ended in a non-final state" }
    return rank
  }
}

/** Reusable zero-based BigInteger bijection for a CFG's shortlex language through [maxLength]. */
fun CFG.shortlexDFAIndex(maxLength: Int = Int.MAX_VALUE): ShortlexDFAIndex = ShortlexDFAIndex(this, maxLength)

class ShortlexDFAIndex internal constructor(cfg: CFG, private val maxLength: Int) {
  private data class Slice(val length: Int, val start: BigInteger, val end: BigInteger, val root: SliceRoot)

  private val builder = IncrementalSliceBuilder(cfg)
  private val slices = ArrayList<Slice>()
  private var indexedSize = BigInteger.ZERO

  init { require(maxLength >= 1) }

  /** Ensures that [rank], interpreted as a zero-based global shortlex rank, can be decoded. */
  @Synchronized
  fun ensureRank(rank: BigInteger) {
    require(rank.signum() >= 0) { "Rank must be nonnegative: $rank" }
    while (rank >= indexedSize) {
      require(slices.size < maxLength) {
        "Rank $rank is outside the $indexedSize words of lengths 1..$maxLength"
      }
      appendSlice()
    }
  }

  /** Maps a zero-based global shortlex rank to its unique token sequence. */
  @Synchronized
  fun unrank(rank: BigInteger): List<String> {
    ensureRank(rank)
    val slice = findSlice(rank)
    return builder.unrank(slice.root, rank - slice.start)
  }

  /** Inverse of [unrank] for nonempty token sequences in the indexed language. */
  @Synchronized
  fun rank(tokens: List<String>): BigInteger {
    require(tokens.isNotEmpty()) { "Length-zero slices are not indexed" }
    require(tokens.size <= maxLength) { "Token length ${tokens.size} exceeds $maxLength" }
    while (slices.size < tokens.size) appendSlice()
    val slice = slices[tokens.size - 1]
    return slice.start + builder.rank(slice.root, tokens)
  }

  /**
   * Retains compatibility with the former packed-slice index.
   *
   * Incremental construction shares earlier arena states with future layers, so those exact DFA
   * states cannot be released without forcing reconstruction. The index no longer creates packed
   * snapshots for rank/unrank, making this operation intentionally a validation-only no-op.
   */
  @Synchronized
  fun releaseBefore(rank: BigInteger) = require(rank.signum() >= 0) { "Rank must be nonnegative: $rank" }

  private fun appendSlice() {
    check(slices.size < Int.MAX_VALUE) { "Cannot index slices beyond Int.MAX_VALUE" }
    val root = builder.nextSlice()
    check(root.length == slices.size + 1)
    val start = indexedSize
    indexedSize += root.languageSize
    slices += Slice(root.length, start, indexedSize, root)
  }

  private fun findSlice(rank: BigInteger): Slice {
    var low = 0
    var high = slices.lastIndex
    while (low < high) {
      val middle = (low + high) ushr 1
      if (rank < slices[middle].end) high = middle else low = middle + 1
    }
    return slices[low].also { check(it.start <= rank && rank < it.end) }
  }
}

internal data class DFAConstructionStats(
  val latestLength: Int,
  val completedCFGCells: Long,
  val recursiveForks: Long,
  val parallelism: Int,
  val productMemoEntries: Int,
  val productMemoHits: Long,
  val productMemoMisses: Long,
  val productMemoInFlightHits: Long,
  val coalescedSiblingProducts: Long,
  val pendingProductComputations: Long,
  val productMemoEstimatedBytes: Long,
  val productMemoEvictions: Long
)

internal data class SliceRoot(val length: Int, val state: Int, val languageSize: BigInteger)

/** One bounded scheduler is shared by cell construction and recursive determinization. */
private object DFAConstructionExecutor {
  val parallelism: Int = maxOf(
    1,
    Integer.getInteger(
      "cstk.dfa.parallelism",
      Runtime.getRuntime().availableProcessors()
    )
  )
  val pool = ForkJoinPool(parallelism)
  private val availableForks = AtomicInteger(maxOf(1, parallelism * 4))

  fun tryAcquireFork(): Boolean {
    if (parallelism <= 1) return false
    while (true) {
      val available = availableForks.get()
      if (available == 0) return false
      if (availableForks.compareAndSet(available, available - 1)) return true
    }
  }

  fun releaseFork() = availableForks.incrementAndGet()
}

/**
 * Persistent exact slice construction. After returning slice n, all earlier layers are complete
 * and layer n contains START (plus every cell when n=1). Advancing fills the remainder of n once,
 * then computes only START at n+1.
 */
internal class IncrementalSliceBuilder(private val cfg: CFG) {
  private val width = cfg.nonterminals.size
  private val terminalNames = cfg.tmLst.toList()
  private val lexicographicRankByLabel = IntArray(terminalNames.size).also { ranks ->
    terminalNames.indices.sortedBy(terminalNames::get)
      .forEachIndexed { rank, label -> ranks[label] = rank }
  }
  private val terminalIds = terminalNames.withIndex().associate { (i, terminal) -> terminal to i }
  private val binary = Array(width) { cfg.vindex[it].copyOf() }
  private val terminalLabels = Array(width) { a ->
    cfg.terminalLists[a].map(cfg.tmMap::getValue).distinct()
      .sortedBy(lexicographicRankByLabel::get).toIntArray()
  }
  private val start = cfg.bindex[START_SYMBOL]
  private val arena = AcyclicDFAArena(lexicographicRankByLabel)
  private val layers = ArrayList<IntArray>().apply {
    add(IntArray(width) { AcyclicDFAArena.UNBUILT })
  }
  private val completedCFGCells = AtomicLong()
  private var latestLength = 0
  private var latestLayerComplete = false
  private var failure: Throwable? = null

  init {
    require(width > 0 && START_SYMBOL in cfg.nonterminals) {
      "Expected a grammar containing $START_SYMBOL"
    }
    cfg.forEach { (lhs, rhs) ->
      require(
        rhs.size == 2 && rhs.all { it in cfg.nonterminals } ||
          rhs.size == 1 && rhs[0] !in cfg.nonterminals
      ) { "Expected a CFG in binary normal form, found $lhs -> ${rhs.joinToString(" ")}" }
    }
  }

  @Synchronized
  fun nextSlice(): SliceRoot {
    failure?.let { throw IllegalStateException("Incremental DFA builder previously failed", it) }
    try {
      if (latestLength == 0) initializeLengthOne() else advanceOneLength()
      val root = layers[latestLength][start]
      check(root != AcyclicDFAArena.UNBUILT)
      return SliceRoot(latestLength, root, arena.languageSize(root))
    } catch (t: Throwable) {
      failure = t
      throw t
    }
  }

  fun sizeOf(slice: SliceRoot): DFASize = arena.sizeOf(slice.state)

  fun pack(slice: SliceRoot, expected: DFASize = sizeOf(slice)): PackedDFA =
    arena.pack(slice.state, terminalNames, expected)

  fun words(slice: SliceRoot): Sequence<List<String>> =
    arena.wordsInLexicographicOrder(slice.state, terminalNames)

  fun unrank(slice: SliceRoot, rank: BigInteger): List<String> =
    arena.unrank(slice.state, rank, terminalNames)

  fun rank(slice: SliceRoot, tokens: Iterable<String>): BigInteger =
    arena.rank(slice.state, tokens, terminalIds)

  fun constructionStats() = DFAConstructionStats(
    latestLength = latestLength,
    completedCFGCells = completedCFGCells.get(),
    recursiveForks = arena.recursiveForkCount(),
    parallelism = DFAConstructionExecutor.parallelism,
    productMemoEntries = arena.productMemoSize(),
    productMemoHits = arena.productMemoHitCount(),
    productMemoMisses = arena.productMemoMissCount(),
    productMemoInFlightHits = arena.productMemoInFlightHitCount(),
    coalescedSiblingProducts = arena.coalescedSiblingProductCount(),
    pendingProductComputations = arena.pendingProductCount(),
    productMemoEstimatedBytes = arena.productMemoEstimatedBytes(),
    productMemoEvictions = arena.productMemoEvictionCount()
  )

  private fun initializeLengthOne() {
    val layer = IntArray(width) { AcyclicDFAArena.UNBUILT }
    layers.add(layer)
    runCells(IntArray(width) { it }) { a ->
      val labels = terminalLabels[a]
      layer[a] = if (labels.isEmpty()) AcyclicDFAArena.EMPTY else
        arena.shareStructuralRow(IntArray(labels.size * 2) { i ->
          if (i and 1 == 0) labels[i / 2] else AcyclicDFAArena.FINAL
        })
      completedCFGCells.incrementAndGet()
    }
    latestLength = 1
    latestLayerComplete = true
  }

  private fun advanceOneLength() {
    completeLatestLayer()
    latestLength++
    val layer = IntArray(width) { AcyclicDFAArena.UNBUILT }
    layers.add(layer)
    computeCells(latestLength, intArrayOf(start))
    latestLayerComplete = width == 1
  }

  private fun completeLatestLayer() {
    if (!latestLayerComplete) {
      val remaining = IntArray(width - 1)
      var index = 0
      for (a in 0 until width) if (a != start) remaining[index++] = a
      computeCells(latestLength, remaining)
      latestLayerComplete = true
    }
  }

  private fun computeCells(length: Int, cells: IntArray) {
    runCells(cells) { a ->
      val layer = layers[length]
      check(layer[a] == AcyclicDFAArena.UNBUILT)
      val rules = binary[a]
      val capacity = Math.multiplyExact(rules.size / 2, length - 1)
      val products = LongArray(capacity)
      var size = 0
      for (r in rules.indices step 2) {
        val b = rules[r]
        val c = rules[r + 1]
        for (split in 1 until length) {
          val left = layers[split][b]
          val right = layers[length - split][c]
          check(left != AcyclicDFAArena.UNBUILT && right != AcyclicDFAArena.UNBUILT)
          if (left != AcyclicDFAArena.EMPTY && right != AcyclicDFAArena.EMPTY)
            products[size++] = AcyclicDFAArena.product(left, right)
        }
      }
      layer[a] = arena.unionProducts(products, size, length)
      completedCFGCells.incrementAndGet()
    }
    arena.finishProductGeneration()
  }

  private fun runCells(cells: IntArray, action: (Int) -> Unit) {
    if (cells.isEmpty()) return
    DFAConstructionExecutor.pool.invoke(object : RecursiveAction() {
      override fun compute() {
        if (cells.size == 1) {
          action(cells[0])
          return
        }
        invokeAll(cells.map { cell ->
          object : RecursiveAction() {
            override fun compute() = action(cell)
          }
        })
      }
    })
  }
}

internal class AcyclicDFAArena(private val lexicographicRankByLabel: IntArray) {
  // EMPTY=-1; UNBUILT=-2; FINAL=0; every other id names one exact deterministic row.
  private class LabelScratch(width: Int) {
    val counts = IntArray(width)
    val slots = IntArray(width) { UNBUILT }
    val cursors = IntArray(width)
    val touched = IntArray(width)
  }

  private data class Row(
    val values: IntArray,
    val languageSize: BigInteger,
    val remainingDepth: Int
  )

  class IntArrayKey(val values: IntArray) {
    private val contentHash = values.contentHashCode()
    override fun hashCode() = contentHash
    override fun equals(other: Any?) =
      other is IntArrayKey && contentHash == other.contentHash && values.contentEquals(other.values)
  }

  class LongArrayKey(
    val values: LongArray,
    val size: Int,
    val remainingDepth: Int,
    private val contentHash: Int = prefixHash(values, size)
  ) {
    init { require(size in 0..values.size) }

    fun owned(): LongArrayKey =
      if (size == values.size) this
      else LongArrayKey(values.copyOf(size), size, remainingDepth, contentHash)

    override fun hashCode() = contentHash

    override fun equals(other: Any?): Boolean {
      if (other !is LongArrayKey || contentHash != other.contentHash ||
        size != other.size || remainingDepth != other.remainingDepth
      ) return false
      for (i in 0 until size) if (values[i] != other.values[i]) return false
      return true
    }

    companion object {
      private fun prefixHash(values: LongArray, size: Int): Int {
        var result = 1
        for (i in 0 until size) result = 31 * result + java.lang.Long.hashCode(values[i])
        return result
      }
    }
  }

  private sealed interface PreparedProductSet {
    data class Direct(val state: Int) : PreparedProductSet
    data class Key(val value: LongArrayKey) : PreparedProductSet
  }

  private sealed interface MemoizedProduct
  private class CompletedProduct(val state: Int) : MemoizedProduct
  private class BoundedCompletedProduct(
    val state: Int,
    val generation: Int
  ) : MemoizedProduct
  private data class FailedProduct(val failure: Throwable) : MemoizedProduct
  private class PendingProduct(
    val key: LongArrayKey,
    val generation: Int
  ) : MemoizedProduct {
    lateinit var task: FutureTask<Int>
    val started = AtomicBoolean()
    @Volatile var runnerThread: Thread? = null
  }

  private data class GroupedChildren(
    val labels: IntArray,
    val products: Array<PreparedProductSet?>
  )

  private data class ProductMemoGeneration(
    val generation: Int,
    val estimatedBytes: Long
  )

  private val nextId = AtomicInteger(1)
  private val rows = ConcurrentHashMap<Int, Row>().apply {
    put(FINAL, Row(IntArray(0), BigInteger.ONE, 0))
  }
  private val labelsInLexicographicOrder = IntArray(lexicographicRankByLabel.size).also { labels ->
    val seen = BooleanArray(labels.size)
    lexicographicRankByLabel.forEachIndexed { label, rank ->
      require(rank in labels.indices && !seen[rank]) {
        "Lexicographic label ranks must be a permutation of 0 until ${labels.size}"
      }
      seen[rank] = true
      labels[rank] = label
    }
  }
  private val labelScratch = ThreadLocal.withInitial {
    LabelScratch(lexicographicRankByLabel.size)
  }
  private val activeProducts = ThreadLocal.withInitial { ArrayDeque<PendingProduct>() }
  // Product keys contain arena-local state IDs. Successful entries therefore live for exactly the
  // arena's lifetime unless the completed-result budget evicts an old generation. Pending entries
  // are never evicted, and eviction only permits exact recomputation; it never removes DFA rows.
  private val productMemo = ConcurrentHashMap<LongArrayKey, MemoizedProduct>()
  private val maximumProductMemoBytes = System.getProperty(PRODUCT_MEMO_BYTES_PROPERTY)
    ?.toLongOrNull()?.also { require(it >= 0L) {
      "$PRODUCT_MEMO_BYTES_PROPERTY must be a nonnegative byte count"
    } }
  private val completedGenerations = ArrayDeque<ProductMemoGeneration>()
  private val currentGenerationBytes = AtomicLong()
  private val productMemoBytes = AtomicLong()
  private val productMemoEvictions = AtomicLong()
  @Volatile private var currentProductGeneration = 0
  // Incremental layers revisit many identical residuals, so structural hash-consing is enabled by
  // default to keep the persistent representation tractable. It is only a sharing optimization:
  // disabling it produces a nonminimal but equally complete DFA with identical rank/unrank results.
  private val sharedRowIds = when (
    val configured = System.getProperty(STRUCTURAL_SHARING_PROPERTY, "true")
  ) {
    "true" -> ConcurrentHashMap<IntArrayKey, Int>()
    "false" -> null
    else -> throw IllegalArgumentException("$STRUCTURAL_SHARING_PROPERTY must be true or false, found: $configured")
  }
  private val recursiveForks = AtomicLong()
  private val productMemoHits = AtomicLong()
  private val productMemoMisses = AtomicLong()
  private val productMemoInFlightHits = AtomicLong()
  private val coalescedSiblingProducts = AtomicLong()
  private val pendingProducts = AtomicLong()

  /** Allocates a deterministic row without merging it with equivalent row signatures. */
  fun allocateRow(row: IntArray): Int {
    require(row.isNotEmpty() && row.size and 1 == 0)
    var languageSize = BigInteger.ZERO
    var previousLexicographicRank = -1
    var childDepth = UNBUILT
    for (i in row.indices step 2) {
      val label = row[i]
      require(label in lexicographicRankByLabel.indices)
      val lexicographicRank = lexicographicRankByLabel[label]
      require(previousLexicographicRank < lexicographicRank) {
        "DFA row labels must be unique and lexicographically ordered"
      }
      previousLexicographicRank = lexicographicRank
      val child = rows.getValue(row[i + 1])
      if (childDepth == UNBUILT) childDepth = child.remainingDepth
      else require(childDepth == child.remainingDepth) {
        "A fixed-length DFA row cannot mix depths $childDepth and ${child.remainingDepth}"
      }
      languageSize += child.languageSize
    }
    val id = nextId.getAndIncrement()
    check(id > FINAL) { "DFA arena exhausted its positive Int state identifiers" }
    rows[id] = Row(row, languageSize, Math.incrementExact(childDepth))
    return id
  }

  fun shareStructuralRow(row: IntArray): Int =
    sharedRowIds?.computeIfAbsent(IntArrayKey(row)) { allocateRow(row) } ?: allocateRow(row)

  /** Exact, persistent, single-flight determinization of concatenated DFA-language unions. */
  fun unionProducts(raw: LongArray, inputSize: Int, remainingDepth: Int): Int =
    resultOf(resolve(prepareProducts(raw, inputSize, remainingDepth)))

  private fun prepareProducts(
    raw: LongArray,
    inputSize: Int,
    remainingDepth: Int
  ): PreparedProductSet {
    require(inputSize in 0..raw.size)
    require(remainingDepth >= 0)
    var size = 0
    for (rawIndex in 0 until inputSize) {
      val packed = raw[rawIndex]
      var prefix = left(packed)
      var suffix = right(packed)
      if (prefix == EMPTY || suffix == EMPTY) continue
      if (prefix == FINAL) prefix = suffix.also { suffix = FINAL }
      raw[size++] = product(prefix, suffix)
    }
    if (size == 0) return PreparedProductSet.Direct(EMPTY)
    Arrays.sort(raw, 0, size)
    var unique = 1
    for (i in 1 until size) if (raw[i] != raw[unique - 1]) raw[unique++] = raw[i]
    if (unique == 1 && right(raw[0]) == FINAL) {
      val state = left(raw[0])
      require(rows.getValue(state).remainingDepth == remainingDepth)
      return PreparedProductSet.Direct(state)
    }

    val first = raw[0]
    require(
      rows.getValue(left(first)).remainingDepth + rows.getValue(right(first)).remainingDepth ==
        remainingDepth
    ) { "Product alternatives do not have the expected remaining depth $remainingDepth" }
    return PreparedProductSet.Key(LongArrayKey(raw, unique, remainingDepth))
  }

  private fun resolve(prepared: PreparedProductSet): MemoizedProduct = when (prepared) {
    is PreparedProductSet.Direct -> CompletedProduct(prepared.state)
    is PreparedProductSet.Key -> resolve(prepared.value)
  }

  private fun resolve(probe: LongArrayKey): MemoizedProduct {
    productMemo[probe]?.let { existing ->
      productMemoHits.incrementAndGet()
      if (existing is PendingProduct) productMemoInFlightHits.incrementAndGet()
      return existing
    }

    val owned = probe.owned()
    val pending = PendingProduct(owned, currentProductGeneration)
    pending.task = FutureTask { computePending(pending) }
    pendingProducts.incrementAndGet()
    val existing = productMemo.putIfAbsent(owned, pending)
    if (existing != null) {
      pendingProducts.decrementAndGet()
      productMemoHits.incrementAndGet()
      if (existing is PendingProduct) productMemoInFlightHits.incrementAndGet()
      return existing
    }
    productMemoMisses.incrementAndGet()
    return pending
  }

  private fun computePending(pending: PendingProduct): Int {
    val stack = activeProducts.get()
    var pushed = false
    var result = EMPTY
    var computationFailure: Throwable? = null
    try {
      stack.peekLast()?.let { parent ->
        require(pending.key.remainingDepth == parent.key.remainingDepth - 1) {
          "Nested product depth ${pending.key.remainingDepth} must follow ${parent.key.remainingDepth}"
        }
      }
      check(stack.none { it === pending }) {
        "Recursive product dependency at depth ${pending.key.remainingDepth}"
      }
      stack.addLast(pending)
      pushed = true
      pending.runnerThread = Thread.currentThread()
      result = determinize(pending.key)
    } catch (failure: Throwable) {
      computationFailure = failure
    } finally {
      pending.runnerThread = null
      if (pushed) check(stack.removeLast() === pending)
    }

    try {
      computationFailure?.let { failure ->
        check(productMemo.replace(pending.key, pending, FailedProduct(failure))) {
          "Single-flight product memo lost its failed pending owner"
        }
        throw failure
      }
      val completed = maximumProductMemoBytes?.let {
        BoundedCompletedProduct(result, pending.generation)
      } ?: CompletedProduct(result)
      check(productMemo.replace(pending.key, pending, completed)) {
        "Single-flight product memo lost its pending owner"
      }
      if (maximumProductMemoBytes != null) {
        val estimatedBytes = estimatedProductMemoBytes(pending.key)
        currentGenerationBytes.addAndGet(estimatedBytes)
        productMemoBytes.addAndGet(estimatedBytes)
      }
      return result
    } finally {
      pendingProducts.decrementAndGet()
    }
  }

  private fun determinize(frozen: LongArrayKey): Int {
    require(frozen.remainingDepth > 0)
    val grouped = groupChildren(frozen)
    val targets = resolveChildren(grouped, frozen.remainingDepth)
    return shareStructuralRow(IntArray(grouped.labels.size * 2) { index ->
      if (index and 1 == 0) grouped.labels[index / 2] else targets[index / 2]
    })
  }

  /** Two primitive passes replace HashMap<Int, LongBuffer> and emit labels in lexical order. */
  private fun groupChildren(frozen: LongArrayKey): GroupedChildren {
    val scratch = labelScratch.get()
    var touchedCount = 0
    try {
      for (productIndex in 0 until frozen.size) {
        val packed = frozen.values[productIndex]
        val prefix = left(packed)
        require(prefix != FINAL)
        val row = rows.getValue(prefix).values
        for (i in row.indices step 2) {
          val label = row[i]
          if (scratch.counts[label] == 0) scratch.touched[touchedCount++] = label
          scratch.counts[label] = Math.incrementExact(scratch.counts[label])
        }
      }

      var activeLabels = 0
      for (label in labelsInLexicographicOrder)
        if (scratch.counts[label] != 0) activeLabels++
      check(activeLabels > 0)

      val labels = IntArray(activeLabels)
      val rawChildren = arrayOfNulls<LongArray>(activeLabels)
      var slot = 0
      for (label in labelsInLexicographicOrder) {
        val count = scratch.counts[label]
        if (count == 0) continue
        labels[slot] = label
        scratch.slots[label] = slot
        rawChildren[slot] = LongArray(count)
        slot++
      }

      for (productIndex in 0 until frozen.size) {
        val packed = frozen.values[productIndex]
        val prefix = left(packed)
        val suffix = right(packed)
        val row = rows.getValue(prefix).values
        for (i in row.indices step 2) {
          val label = row[i]
          val childSlot = scratch.slots[label]
          rawChildren[childSlot]!![scratch.cursors[label]++] = product(row[i + 1], suffix)
        }
      }

      val prepared = arrayOfNulls<PreparedProductSet>(activeLabels)
      for (child in 0 until activeLabels) {
        val raw = checkNotNull(rawChildren[child])
        prepared[child] = prepareProducts(raw, raw.size, frozen.remainingDepth - 1)
        rawChildren[child] = null
      }
      return GroupedChildren(labels, prepared)
    } finally {
      for (i in 0 until touchedCount) {
        val label = scratch.touched[i]
        scratch.counts[label] = 0
        scratch.slots[label] = UNBUILT
        scratch.cursors[label] = 0
      }
    }
  }

  /** Coalesces exact sibling keys before scheduling and scatters one result to every label edge. */
  private fun resolveChildren(grouped: GroupedChildren, parentDepth: Int): IntArray {
    val childCount = grouped.labels.size
    val targets = IntArray(childCount) { UNBUILT }
    val representatives = IntArray(childCount) { UNBUILT }
    val resolutions = arrayOfNulls<MemoizedProduct>(childCount)
    var buckets = 1
    val desiredBuckets = minOf(1L shl 30, childCount.toLong() * 2L)
    while (buckets.toLong() < desiredBuckets) buckets = buckets shl 1
    val heads = IntArray(buckets) { UNBUILT }
    val next = IntArray(childCount) { UNBUILT }

    for (child in 0 until childCount) {
      when (val prepared = checkNotNull(grouped.products[child])) {
        is PreparedProductSet.Direct -> {
          require(prepared.state != EMPTY)
          require(rows.getValue(prepared.state).remainingDepth == parentDepth - 1)
          targets[child] = prepared.state
          grouped.products[child] = null
        }
        is PreparedProductSet.Key -> {
          require(prepared.value.remainingDepth == parentDepth - 1)
          val bucket = (prepared.value.hashCode() xor
            (prepared.value.hashCode() ushr 16)) and (buckets - 1)
          var representative = heads[bucket]
          while (representative != UNBUILT) {
            val candidate = grouped.products[representative] as PreparedProductSet.Key
            if (prepared.value == candidate.value) break
            representative = next[representative]
          }
          if (representative == UNBUILT) {
            representatives[child] = child
            next[child] = heads[bucket]
            heads[bucket] = child
          } else {
            representatives[child] = representative
            grouped.products[child] = null
            coalescedSiblingProducts.incrementAndGet()
          }
        }
      }
    }

    var uniqueWork = 0L
    for (child in 0 until childCount) {
      if (representatives[child] != child) continue
      val key = (checkNotNull(grouped.products[child]) as PreparedProductSet.Key).value
      resolutions[child] = resolve(key)
      uniqueWork += key.size
      grouped.products[child] = null
    }

    val canFork = childCount >= 2 && uniqueWork >= RECURSIVE_FORK_THRESHOLD &&
      ForkJoinTask.getPool() === DFAConstructionExecutor.pool &&
      ForkJoinTask.getSurplusQueuedTaskCount() <= MAX_SURPLUS_TASKS
    var inline = UNBUILT
    var firstFailure: Throwable? = null
    fun collect(child: Int) {
      try {
        targets[child] = resultOf(checkNotNull(resolutions[child]))
      } catch (failure: Throwable) {
        val first = firstFailure
        if (first == null) firstFailure = failure
        else if (first !== failure) first.addSuppressed(failure)
      }
    }

    if (canFork) {
      for (child in 0 until childCount) {
        val pending = resolutions[child] as? PendingProduct ?: continue
        if (inline == UNBUILT || pending.key.size >
          (resolutions[inline] as PendingProduct).key.size
        ) inline = child
      }
      for (child in 0 until childCount) {
        if (child == inline) continue
        val pending = resolutions[child] as? PendingProduct ?: continue
        trySchedule(pending)
      }
      if (inline != UNBUILT) collect(inline)
    }

    for (child in 0 until childCount) {
      if (representatives[child] == child && targets[child] == UNBUILT)
        collect(child)
    }
    firstFailure?.let { throw it }
    for (child in 0 until childCount) {
      val representative = representatives[child]
      if (representative != UNBUILT && representative != child)
        targets[child] = targets[representative]
      check(targets[child] != UNBUILT)
    }
    return targets
  }

  private fun trySchedule(pending: PendingProduct): Boolean {
    if (!DFAConstructionExecutor.tryAcquireFork()) return false
    if (!pending.started.compareAndSet(false, true)) {
      DFAConstructionExecutor.releaseFork()
      return true
    }
    return try {
      DFAConstructionExecutor.pool.execute(object : RecursiveAction() {
        override fun compute() = try {
          pending.task.run()
        } finally {
          DFAConstructionExecutor.releaseFork()
        }
      })
      recursiveForks.incrementAndGet()
      true
    } catch (_: Throwable) {
      DFAConstructionExecutor.releaseFork()
      pending.task.run()
      false
    }
  }

  private fun resultOf(product: MemoizedProduct): Int = when (product) {
    is CompletedProduct -> product.state
    is BoundedCompletedProduct -> product.state
    is FailedProduct -> throw product.failure
    is PendingProduct -> await(pending = product)
  }

  private fun await(pending: PendingProduct): Int {
    activeProducts.get().peekLast()?.let { parent ->
      check(parent !== pending) { "A product computation cannot await itself" }
      require(pending.key.remainingDepth == parent.key.remainingDepth - 1) {
        "Product wait depth ${pending.key.remainingDepth} must follow ${parent.key.remainingDepth}"
      }
    }
    pending.started.compareAndSet(false, true)
    // FutureTask.run is exact cooperative help: it claims an unstarted/merely queued task, or is
    // a no-op when another worker already owns it. No ForkJoin join/help operation occurs here.
    pending.task.run()
    check(pending.runnerThread !== Thread.currentThread() || pending.task.isDone) {
      "Product owner attempted to wait on its own incomplete computation"
    }
    return getUninterruptibly(pending.task)
  }

  private fun getUninterruptibly(task: FutureTask<Int>): Int {
    var interrupted = Thread.interrupted()
    try {
      while (true) {
        try {
          if (!task.isDone && ForkJoinTask.inForkJoinPool())
            ForkJoinPool.managedBlock(object : ForkJoinPool.ManagedBlocker {
              override fun isReleasable() = task.isDone
              override fun block(): Boolean {
                if (!task.isDone) try {
                  task.get()
                } catch (_: ExecutionException) {
                  // The nonblocking get below unwraps the original cause.
                }
                return true
              }
            })
          return task.get()
        } catch (_: InterruptedException) {
          interrupted = true
        } catch (failure: ExecutionException) {
          throw failure.cause ?: failure
        }
      }
    } finally {
      if (interrupted) Thread.currentThread().interrupt()
    }
  }

  fun recursiveForkCount(): Long = recursiveForks.get()
  fun productMemoSize(): Int = productMemo.size
  fun productMemoHitCount(): Long = productMemoHits.get()
  fun productMemoMissCount(): Long = productMemoMisses.get()
  fun productMemoInFlightHitCount(): Long = productMemoInFlightHits.get()
  fun coalescedSiblingProductCount(): Long = coalescedSiblingProducts.get()
  fun pendingProductCount(): Long = pendingProducts.get()
  fun productMemoEstimatedBytes(): Long =
    if (maximumProductMemoBytes == null) -1L else productMemoBytes.get()
  fun productMemoEvictionCount(): Long = productMemoEvictions.get()

  /** Seals one completed length wave and bounds only its reusable completed-result index. */
  @Synchronized
  fun finishProductGeneration() {
    check(pendingProducts.get() == 0L) {
      "Cannot finish a product generation with ${pendingProducts.get()} computations pending"
    }
    val maximumBytes = maximumProductMemoBytes ?: return
    val generation = currentProductGeneration
    currentProductGeneration = Math.incrementExact(currentProductGeneration)
    val generationBytes = currentGenerationBytes.getAndSet(0L)
    if (generationBytes != 0L)
      completedGenerations.addLast(ProductMemoGeneration(generation, generationBytes))

    if (productMemoBytes.get() <= maximumBytes) return
    val targetBytes = maximumBytes - maximumBytes / 4L
    var cutoffGeneration = -1
    while (productMemoBytes.get() > targetBytes && completedGenerations.isNotEmpty()) {
      val evicted = completedGenerations.removeFirst()
      cutoffGeneration = evicted.generation
      productMemoBytes.addAndGet(-evicted.estimatedBytes)
    }
    if (cutoffGeneration >= 0) {
      productMemo.forEach { (key, value) ->
        if (value is BoundedCompletedProduct && value.generation <= cutoffGeneration &&
          productMemo.remove(key, value)
        ) productMemoEvictions.incrementAndGet()
      }
    }
  }

  private fun estimatedProductMemoBytes(key: LongArrayKey): Long =
    PRODUCT_MEMO_ENTRY_OVERHEAD_BYTES + java.lang.Long.BYTES.toLong() * key.size

  fun languageSize(root: Int): BigInteger =
    if (root == EMPTY) BigInteger.ZERO else rows.getValue(root).languageSize

  fun wordsInLexicographicOrder(root: Int, terminals: List<String>): Sequence<List<String>> = sequence {
    if (root == EMPTY) return@sequence
    val path = ArrayList<String>()

    suspend fun SequenceScope<List<String>>.visit(state: Int) {
      if (state == FINAL) {
        yield(path.toList())
        return
      }
      val row = rows.getValue(state).values
      for (i in row.indices step 2) {
        path += terminals[row[i]]
        visit(row[i + 1])
        path.removeAt(path.lastIndex)
      }
    }

    visit(root)
  }

  fun unrank(root: Int, rank: BigInteger, terminals: List<String>): List<String> {
    val total = languageSize(root)
    require(rank.signum() >= 0 && rank < total) { "Rank $rank is outside [0, $total)" }
    var remaining = rank
    var state = root
    val word = ArrayList<String>()

    while (state != FINAL) {
      val row = rows.getValue(state).values
      var selected = false
      for (i in row.indices step 2) {
        val target = row[i + 1]
        val branchSize = rows.getValue(target).languageSize
        if (remaining < branchSize) {
          word += terminals[row[i]]
          state = target
          selected = true
          break
        }
        remaining -= branchSize
      }
      check(selected) { "No branch contains rank $rank" }
    }
    check(remaining == BigInteger.ZERO)
    return word
  }

  fun rank(root: Int, tokens: Iterable<String>, terminalIds: Map<String, Int>): BigInteger {
    var rank = BigInteger.ZERO
    var state = root
    for (token in tokens) {
      val wanted = terminalIds[token] ?: throw IllegalArgumentException("Unknown terminal: $token")
      require(state != EMPTY && state != FINAL) { "Word is not accepted: unexpected $token" }
      val row = rows.getValue(state).values
      var selected = false
      for (i in row.indices step 2) {
        val target = row[i + 1]
        if (row[i] == wanted) {
          state = target
          selected = true
          break
        }
        rank += rows.getValue(target).languageSize
      }
      require(selected) { "Word is not accepted: no transition for $token" }
    }
    require(state == FINAL) { "Word is not accepted: input ended in a non-final state" }
    return rank
  }

  fun sizeOf(root: Int): DFASize {
    if (root == EMPTY) return DFASize(1, 0)
    val seen = BitSet(nextId.get())
    var queue = IntArray(1024)
    var head = 0
    var tail = 1
    queue[0] = root
    seen[root] = true
    var states = 0L
    var transitions = 0L

    while (head < tail) {
      val rowKey = rows.getValue(queue[head++])
      val row = rowKey.values
      states++
      transitions += row.size / 2
      for (i in 1 until row.size step 2) {
        val target = row[i]
        if (!seen[target]) {
          seen[target] = true
          if (tail == queue.size) queue = queue.copyOf(queue.size * 2)
          queue[tail++] = target
        }
      }
    }
    return DFASize(states, transitions, rows.getValue(root).languageSize)
  }

  fun pack(root: Int, terminals: List<String>, expected: DFASize): PackedDFA {
    if (root == EMPTY)
      return PackedDFA(
        terminals.toList(), 0, EMPTY, BigInteger.ZERO,
        arrayOf(BigInteger.ZERO),
        intArrayOf(0, 0), LongArray(0)
      )

    require(expected.states < Int.MAX_VALUE) { "Packed DFA has too many states: ${expected.states}" }
    require(expected.transitions <= Int.MAX_VALUE) { "Packed DFA has too many transitions: ${expected.transitions}" }
    val stateCount = expected.states.toInt()
    val transitionCount = expected.transitions.toInt()
    val arenaToDense = IntArray(nextId.get()) { EMPTY }
    val denseToArena = IntArray(stateCount)
    val offsets = IntArray(stateCount + 1)
    arenaToDense[root] = 0
    denseToArena[0] = root
    var head = 0
    var tail = 1

    while (head < tail) {
      val row = rows.getValue(denseToArena[head]).values
      offsets[head + 1] = offsets[head] + row.size / 2
      for (i in 1 until row.size step 2) {
        val target = row[i]
        if (arenaToDense[target] == EMPTY) {
          arenaToDense[target] = tail
          denseToArena[tail++] = target
        }
      }
      head++
    }
    check(tail == stateCount && offsets.last() == transitionCount)

    val labelsInLexicographicOrder = terminals.indices.sortedBy(terminals::get)
    val lexicographicRankByLabel = IntArray(terminals.size).also { ranks ->
      labelsInLexicographicOrder.forEachIndexed { rank, label -> ranks[label] = rank }
    }
    val edges = LongArray(transitionCount)
    IntStream.range(0, stateCount).parallel().forEach { state ->
      val row = rows.getValue(denseToArena[state]).values
      var edge = offsets[state]
      for (i in row.indices step 2) {
        val label = lexicographicRankByLabel[row[i]]
        val target = arenaToDense[row[i + 1]]
        check(target >= 0)
        edges[edge++] = (label.toLong() shl Int.SIZE_BITS) or
          (target.toLong() and 0xffffffffL)
      }
      Arrays.sort(edges, offsets[state], offsets[state + 1])
    }

    val finalState = arenaToDense[FINAL]
    check(finalState >= 0)
    val suffixLanguageSizes = Array(stateCount) { state ->
      rows.getValue(denseToArena[state]).languageSize
    }
    check(suffixLanguageSizes[0] == expected.languageSize)
    check(suffixLanguageSizes[finalState] == BigInteger.ONE)
    return PackedDFA(
      terminals = terminals.toList(),
      startState = 0,
      finalState = finalState,
      languageSize = expected.languageSize,
      suffixLanguageSizes = suffixLanguageSizes,
      offsets = offsets,
      edges = edges
    )
  }

  companion object {
    const val UNBUILT = -2
    const val EMPTY = -1
    const val FINAL = 0
    private const val RECURSIVE_FORK_THRESHOLD = 256L
    private const val MAX_SURPLUS_TASKS = 2
    private const val STRUCTURAL_SHARING_PROPERTY = "cstk.dfa.structuralSharing"
    private const val PRODUCT_MEMO_BYTES_PROPERTY = "cstk.dfa.productMemoBytes"
    private const val PRODUCT_MEMO_ENTRY_OVERHEAD_BYTES = 128L
    fun product(left: Int, right: Int) = (left.toLong() shl 32) or (right.toLong() and 0xffffffffL)
    private fun left(product: Long) = (product shr 32).toInt()
    private fun right(product: Long) = product.toInt()
  }
}
