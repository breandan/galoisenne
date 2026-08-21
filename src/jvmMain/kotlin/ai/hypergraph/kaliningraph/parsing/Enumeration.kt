package edu.mcgill.cstk.experiments.repair

import ai.hypergraph.kaliningraph.parsing.*
import java.math.BigInteger
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.ForkJoinTask
import java.util.concurrent.RecursiveAction
import java.util.concurrent.RecursiveTask
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.stream.IntStream

/** Exact size statistics for a fixed-length DFA slice. */
data class DFASize(val states: Long, val transitions: Long, val languageSize: BigInteger = BigInteger.ZERO)

/**
 * Constructs an exact deterministic partial DFA for L(this) ∩ Σ^[length].
 *
 * The historical name is retained for source compatibility; the returned automaton is not
 * required to be minimal. Determinism, completeness of the represented slice, and exact
 * distinct-word cardinalities are preserved. Structural hash-consing is an optional space/time
 * optimization and can be disabled with `-Dcstk.dfa.structuralSharing=false`.
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
  val parallelism: Int
)

private data class SliceRoot(val length: Int, val state: Int, val languageSize: BigInteger)

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
private class IncrementalSliceBuilder(private val cfg: CFG) {
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
  private var context: AcyclicDFAArena.DeterminizationContext? = null
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
    parallelism = DFAConstructionExecutor.parallelism
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
    context = arena.newDeterminizationContext()
    latestLength++
    val layer = IntArray(width) { AcyclicDFAArena.UNBUILT }
    layers.add(layer)
    computeCells(latestLength, intArrayOf(start), context!!)
    latestLayerComplete = width == 1
  }

  private fun completeLatestLayer() {
    if (!latestLayerComplete) {
      val remaining = IntArray(width - 1)
      var index = 0
      for (a in 0 until width) if (a != start) remaining[index++] = a
      computeCells(latestLength, remaining, checkNotNull(context))
      latestLayerComplete = true
    }
    // No task can still reference the completed layer's memo after runCells returns.
    context = null
  }

  private fun computeCells(length: Int, cells: IntArray, determinization: AcyclicDFAArena.DeterminizationContext) = runCells(cells) { a ->
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
    layer[a] = arena.unionProducts(products, size, determinization)
    completedCFGCells.incrementAndGet()
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

private class AcyclicDFAArena(private val lexicographicRankByLabel: IntArray) {
  // EMPTY=-1; UNBUILT=-2; FINAL=0; every other id names one exact deterministic row.
  private class LongBuffer {
    private var values = LongArray(4)
    var size = 0
      private set
    fun add(value: Long) {
      if (size == values.size) values = values.copyOf(size * 2)
      values[size++] = value
    }
    fun toLongArray() = values.copyOf(size)
  }

  private data class Row(val values: IntArray, val languageSize: BigInteger)

  class IntArrayKey(val values: IntArray) {
    override fun hashCode() = values.contentHashCode()
    override fun equals(other: Any?) = other is IntArrayKey && values.contentEquals(other.values)
  }

  data class LongArrayKey(val values: LongArray) {
    override fun hashCode() = values.contentHashCode()
    override fun equals(other: Any?) = other is LongArrayKey && values.contentEquals(other.values)
  }

  class DeterminizationContext {
    // Completed results only: recursive ForkJoin workers must never block on an owner that may be
    // suspended below them in the same help/join stack. Concurrent misses may compute equivalent
    // nonminimal rows; the first completed exact result becomes the shared representative.
    val memo = ConcurrentHashMap<LongArrayKey, Int>()
  }

  private val nextId = AtomicInteger(1)
  private val rows = ConcurrentHashMap<Int, Row>().apply {
    put(FINAL, Row(IntArray(0), BigInteger.ONE))
  }
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

  fun newDeterminizationContext() = DeterminizationContext()

  /** Allocates a deterministic row without merging it with equivalent row signatures. */
  fun allocateRow(row: IntArray): Int {
    require(row.isNotEmpty() && row.size and 1 == 0)
    var languageSize = BigInteger.ZERO
    var previousLexicographicRank = -1
    for (i in row.indices step 2) {
      val label = row[i]
      require(label in lexicographicRankByLabel.indices)
      val lexicographicRank = lexicographicRankByLabel[label]
      require(previousLexicographicRank < lexicographicRank) {
        "DFA row labels must be unique and lexicographically ordered"
      }
      previousLexicographicRank = lexicographicRank
      languageSize += rows.getValue(row[i + 1]).languageSize
    }
    val id = nextId.getAndIncrement()
    check(id > FINAL) { "DFA arena exhausted its positive Int state identifiers" }
    rows[id] = Row(row, languageSize)
    return id
  }

  fun shareStructuralRow(row: IntArray): Int =
    sharedRowIds?.computeIfAbsent(IntArrayKey(row)) { allocateRow(row) } ?: allocateRow(row)

  /** Exact subset-style determinization of a union of concatenated DFA languages. */
  fun unionProducts(raw: LongArray, inputSize: Int, context: DeterminizationContext): Int {
    require(inputSize in 0..raw.size)
    var size = 0
    for (rawIndex in 0 until inputSize) {
      val packed = raw[rawIndex]
      var prefix = left(packed)
      var suffix = right(packed)
      if (prefix == EMPTY || suffix == EMPTY) continue
      if (prefix == FINAL) prefix = suffix.also { suffix = FINAL }
      raw[size++] = product(prefix, suffix)
    }
    if (size == 0) return EMPTY
    Arrays.sort(raw, 0, size)
    var unique = 1
    for (i in 1 until size) if (raw[i] != raw[unique - 1]) raw[unique++] = raw[i]
    if (unique == 1 && right(raw[0]) == FINAL) return left(raw[0])

    val frozen = raw.copyOf(unique)
    val key = LongArrayKey(frozen)
    context.memo[key]?.let { return it }
    val result = determinize(frozen, context)
    return context.memo.putIfAbsent(key, result) ?: result
  }

  private fun determinize(frozen: LongArray, context: DeterminizationContext): Int {
    val byLabel = HashMap<Int, LongBuffer>()
    frozen.forEach { packed ->
      val prefix = left(packed)
      val suffix = right(packed)
      require(prefix != FINAL)
      val row = rows.getValue(prefix).values
      for (i in row.indices step 2)
        byLabel.getOrPut(row[i]) { LongBuffer() }.add(product(row[i + 1], suffix))
    }

    val labels = byLabel.keys.sortedBy(lexicographicRankByLabel::get)
    val children = Array(labels.size) { byLabel.getValue(labels[it]).toLongArray() }
    val targets = computeChildren(children, context)
    return shareStructuralRow(IntArray(labels.size * 2) { index ->
      if (index and 1 == 0) labels[index / 2] else targets[index / 2]
    })
  }

  private fun computeChildren(children: Array<LongArray>, context: DeterminizationContext): IntArray {
    val targets = IntArray(children.size)
    if (children.size < 2 ||
      children.sumOf { it.size.toLong() } < RECURSIVE_FORK_THRESHOLD ||
      ForkJoinTask.getPool() !== DFAConstructionExecutor.pool ||
      ForkJoinTask.getSurplusQueuedTaskCount() > MAX_SURPLUS_TASKS
    ) {
      children.indices.forEach { i ->
        targets[i] = unionProducts(children[i], children[i].size, context)
      }
      return targets
    }

    val inline = children.indices.maxBy { children[it].size }
    val tasks = arrayOfNulls<RecursiveTask<Int>>(children.size)
    var schedulingFailure: Throwable? = null
    for (child in children.indices) {
      if (child != inline && DFAConstructionExecutor.tryAcquireFork()) {
        var taskOwnsPermit = false
        try {
          val task = object : RecursiveTask<Int>() {
            override fun compute(): Int = try {
              unionProducts(children[child], children[child].size, context)
            } finally {
              DFAConstructionExecutor.releaseFork()
            }
          }
          tasks[child] = task
          task.fork()
          taskOwnsPermit = true
          recursiveForks.incrementAndGet()
        } catch (t: Throwable) {
          tasks[child] = null
          if (!taskOwnsPermit) DFAConstructionExecutor.releaseFork()
          schedulingFailure = t
          break
        }
      }
    }

    var firstFailure = schedulingFailure
    if (firstFailure == null) {
      try {
        targets[inline] = unionProducts(children[inline], children[inline].size, context)
        children.indices.forEach { child ->
          if (child != inline && tasks[child] == null)
            targets[child] = unionProducts(children[child], children[child].size, context)
        }
      } catch (t: Throwable) {
        firstFailure = t
      }
    }

    tasks.indices.forEach { child ->
      val task = tasks[child] ?: return@forEach
      try {
        targets[child] = task.join()
      } catch (t: Throwable) {
        val priorFailure = firstFailure
        if (priorFailure == null) firstFailure = t
        else if (t !== priorFailure) priorFailure.addSuppressed(t)
      }
    }
    firstFailure?.let { throw it }
    return targets
  }

  fun recursiveForkCount(): Long = recursiveForks.get()

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
    fun product(left: Int, right: Int) = (left.toLong() shl 32) or (right.toLong() and 0xffffffffL)
    private fun left(product: Long) = (product shr 32).toInt()
    private fun right(product: Long) = product.toInt()
  }
}
