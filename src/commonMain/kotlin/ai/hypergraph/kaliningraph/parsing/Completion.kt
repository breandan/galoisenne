@file:Suppress("NonAsciiCharacters")

package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.KBitSet
import ai.hypergraph.kaliningraph.cache.LRUCache
import ai.hypergraph.kaliningraph.types.cache

/** Maximum exact suffix length retained by the grammar-wide completion spectrum. */
const val DEFAULT_COMPLETION_SPECTRUM_BOUND = 32

/** Cached indexes for exact, unbounded suffix-length queries. */
val CFG.completionIndex: CFGCompletionIndex by cache { CFGCompletionIndex(this) }

fun CFG.minimumSuffixLength(prefix: List<Σᐩ>): Int? =
  completionIndex.minimumSuffixLength(prefix)

fun CFG.minimumNonemptySuffixLength(prefix: List<Σᐩ>): Int? =
  completionIndex.minimumSuffixLength(prefix, terminals)

/** Exact shortest positive suffix length for every viable first terminal. */
fun CFG.minimumSuffixLengthsByFirstTerminal(prefix: List<Σᐩ>): Map<Σᐩ, Int> =
  completionIndex.minimumSuffixLengthsByFirstTerminal(prefix)

/** One exact, bounded suffix-frontier analysis for a prefix. */
data class BoundedSuffixLengthAnalysis(
  /** Exact viable first terminals, including those whose shortest suffix exceeds [maxLength]. */
  val nextTerminals: Set<Σᐩ>,
  /** Exact positive suffix lengths within [maxLength], including the first terminal. */
  val lengthsByFirstTerminal: Map<Σᐩ, List<Int>>,
  val maxLength: Int
) {
  /** Viable terminals which cannot be represented by this bounded analysis. */
  val terminalsBeyondBound: Set<Σᐩ>
    get() = nextTerminals - lengthsByFirstTerminal.keys
}

/**
 * The first [countPerTerminal] exact positive suffix lengths no larger than [maxLength],
 * grouped by their first emitted terminal. The first terminal is included in each length.
 *
 * Unlike the unbounded scalar queries, this constructs one residual system for the whole
 * frontier. Terminals whose shortest completion exceeds [maxLength] are intentionally absent.
 */
fun CFG.boundedSuffixLengthsByFirstTerminal(
  prefix: List<Σᐩ>,
  countPerTerminal: Int,
  maxLength: Int = DEFAULT_COMPLETION_SPECTRUM_BOUND
): Map<Σᐩ, List<Int>> =
  completionIndex.boundedSuffixLengthsByFirstTerminal(prefix, countPerTerminal, maxLength)

/** As above, retaining exact unbounded first-terminal viability alongside the bounded spectra. */
fun CFG.boundedSuffixLengthAnalysis(
  prefix: List<Σᐩ>,
  countPerTerminal: Int,
  maxLength: Int = DEFAULT_COMPLETION_SPECTRUM_BOUND
): BoundedSuffixLengthAnalysis =
  completionIndex.boundedSuffixLengthAnalysis(prefix, countPerTerminal, maxLength)

/** Exact distinct positive suffix lengths, generated in ascending order without a horizon. */
fun CFG.nonemptySuffixLengths(prefix: List<Σᐩ>): Sequence<Int> =
  completionSuffixLengths(prefix, includeEmpty = false)

/** As above, optionally retaining zero when [prefix] is already a complete word. */
fun CFG.completionSuffixLengths(prefix: List<Σᐩ>, includeEmpty: Boolean): Sequence<Int> {
  val index = completionIndex
  val minimum = index.minimumSuffixLength(prefix, if (includeEmpty) null else terminals)
    ?: return emptySequence()
  return sequence {
    yield(minimum)
    yieldAll(index.after(prefix).nonemptySuffixLengths.dropWhile { it <= minimum })
  }.memoized()
}

/** The prefix-conditioned completion language, projected onto terminal lengths. */
class CompletionLengths internal constructor(
  val acceptsEmpty: Boolean,
  val nextTerminals: Set<Σᐩ>,
  lengths: Sequence<Int>
) {
  val nonemptySuffixLengths: Sequence<Int> = lengths.filter { it > 0 }.memoized()
  val minimumNonemptySuffixLength: Int? by lazy { nonemptySuffixLengths.firstOrNull() }
  val minimumSuffixLength: Int? by lazy { if (acceptsEmpty) 0 else minimumNonemptySuffixLength }

  fun suffixLengths(includeEmpty: Boolean): Sequence<Int> =
    if (includeEmpty && acceptsEmpty)
      sequence { yield(0); yieldAll(nonemptySuffixLengths) }.memoized()
    else nonemptySuffixLengths
}

/**
 * One CYK prefix chart followed by a lazy least fixed point over exact lengths.
 * The grammar must be unit-free CNF (terminal units and binary variable rules).
 */
class CFGCompletionIndex(private val grammar: CFG) {
  private data class BoundedSpectra(
    val lengths: IntArray,
    val firstLengths: IntArray,
    val firstTerminals: IntArray
  )
  private data class BinaryRule(val parent: Int, val left: Int, val right: Int)
  private data class WeightedParent(val parent: Int, val appendedLength: Long)
  private data class UnaryRule(val parent: Int, val child: Int)
  private data class SumRule(val parent: Int, val left: Int, val right: Int)
  private data class SumUse(val parent: Int, val sibling: Int)

  init {
    require(START_SYMBOL in grammar.nonterminals) { "A completion grammar must declare $START_SYMBOL" }
    grammar.forEach { (lhs, rhs) ->
      require(
        rhs.size == 1 && rhs.single() !in grammar.nonterminals ||
          rhs.size == 2 && rhs.all(grammar.nonterminals::contains)
      ) { "Completion requires unit-free CNF; found $lhs -> ${rhs.joinToString(" ")}" }
    }
  }

  private val variableCount = grammar.nonterminals.size
  private val terminalCount = grammar.terminals.size
  private val terminalWordCount = (terminalCount + Int.SIZE_BITS - 1) / Int.SIZE_BITS
  private val start = grammar.bindex[START_SYMBOL]
  private val binaryRules = grammar.mapNotNull { (lhs, rhs) ->
    if (rhs.size == 2)
      BinaryRule(grammar.bindex[lhs], grammar.bindex[rhs[0]], grammar.bindex[rhs[1]])
    else null
  }
  private val minimumWordLength = minimumWordLengths()
  private val boundedWordSpectra = boundedWordSpectra()
  private val binaryRulesByLeft = Array(variableCount) { mutableListOf<BinaryRule>() }.also { uses ->
    binaryRules.forEach { uses[it.left] += it }
  }
  private val weightedParents = Array(variableCount) { mutableListOf<WeightedParent>() }.also { parents ->
    binaryRules.forEach { rule ->
      val appendedLength = minimumWordLength[rule.right]
      if (appendedLength < COMPLETION_INFINITY)
        parents[rule.left] += WeightedParent(rule.parent, appendedLength)
    }
  }

  private inner class PrefixQuery(val prefix: List<Σᐩ>, val chart: Array<Array<KBitSet>>?) {
    private val minima = mutableMapOf<Set<Σᐩ>?, Int?>()

    val completion: CompletionLengths by lazy { buildCompletion(prefix, chart) }

    val minimumByFirstTerminal: Map<Σᐩ, Int> by lazy {
      linkedMapOf<Σᐩ, Int>().apply {
        completion.nextTerminals.forEach { terminal ->
          minimum(setOf(terminal))?.let { put(terminal, it) }
        }
      }
    }

    fun minimum(allowedFirstTerminals: Set<Σᐩ>?): Int? {
      val allowed = allowedFirstTerminals?.toSet()
      if (allowed?.isEmpty() == true) return null
      if (minima.containsKey(allowed)) return minima[allowed]
      return computeMinimumSuffixLength(
        prefix = prefix,
        full = chart,
        constrainedMinimum = allowed?.let(::minimumWordLengthsStartingWithCached)
      ).also { minima[allowed] = it }
    }
  }

  private val constrainedMinimums = LRUCache<Set<Σᐩ>, LongArray>(64)

  private fun query(prefix: List<Σᐩ>): PrefixQuery {
    val key = prefix.toList()
    return PrefixQuery(key, prefixChart(key))
  }

  /** Exact min-plus query; an optional terminal set constrains the first emitted token. */
  fun minimumSuffixLength(prefix: List<Σᐩ>, allowedFirstTerminals: Set<Σᐩ>? = null): Int? =
    query(prefix).minimum(allowedFirstTerminals)

  /** Exact positive suffix minima, grouped by the first emitted terminal. */
  fun minimumSuffixLengthsByFirstTerminal(prefix: List<Σᐩ>): Map<Σᐩ, Int> = query(prefix).minimumByFirstTerminal

  /**
   * Exact positive suffix lengths within the precomputed grammar-wide spectrum bound.
   * This performs one prefix chart construction and one bounded residual fixed point,
   * independent of the number of viable first terminals.
   */
  fun boundedSuffixLengthsByFirstTerminal(
    prefix: List<Σᐩ>,
    countPerTerminal: Int,
    maxLength: Int = DEFAULT_COMPLETION_SPECTRUM_BOUND
  ): Map<Σᐩ, List<Int>> =
    boundedSuffixLengthAnalysis(prefix, countPerTerminal, maxLength).lengthsByFirstTerminal

  /** Bounded exact spectra plus the exact unbounded viable first-terminal set. */
  fun boundedSuffixLengthAnalysis(
    prefix: List<Σᐩ>,
    countPerTerminal: Int,
    maxLength: Int = DEFAULT_COMPLETION_SPECTRUM_BOUND
  ): BoundedSuffixLengthAnalysis {
    require(countPerTerminal >= 0) { "countPerTerminal must be nonnegative" }
    require(maxLength in 0..DEFAULT_COMPLETION_SPECTRUM_BOUND) {
      "maxLength must be between 0 and $DEFAULT_COMPLETION_SPECTRUM_BOUND"
    }
    val key = prefix.toList()
    val full = prefixChart(key) ?: return BoundedSuffixLengthAnalysis(emptySet(), emptyMap(), maxLength)
    return solveBoundedSuffixLengths(
      prefixSize = key.size,
      full = full,
      countPerTerminal = countPerTerminal,
      maxLength = maxLength
    )
  }

  private fun computeMinimumSuffixLength(
    prefix: List<Σᐩ>,
    full: Array<Array<KBitSet>>?,
    constrainedMinimum: LongArray?
  ): Int? {
    if (prefix.isEmpty())
      return (constrainedMinimum ?: minimumWordLength)[start]
        .takeIf { it < COMPLETION_INFINITY }?.toInt()
    full ?: return null
    val size = prefix.size
    val completion = Array(size + 1) { LongArray(variableCount) { COMPLETION_INFINITY } }
    (constrainedMinimum ?: minimumWordLength).copyInto(completion[size])
    val heap = PackedMinHeap()
    for (begin in size - 1 downTo 0) {
      val distance = completion[begin]
      if (constrainedMinimum == null) {
        for (variable in full[begin][size].iterator()) distance[variable] = 0L
      } else {
        for (leftVariable in full[begin][size].iterator()) {
          val adjacency = grammar.leftAdj[leftVariable] ?: continue
          for (edge in adjacency.other.indices) {
            val rightCost = constrainedMinimum[adjacency.other[edge]]
            if (rightCost < distance[adjacency.aIdx[edge]])
              distance[adjacency.aIdx[edge]] = rightCost
          }
        }
      }
      for (split in begin + 1 until size)
        for (leftVariable in full[begin][split].iterator()) {
          val adjacency = grammar.leftAdj[leftVariable] ?: continue
          for (edge in adjacency.other.indices) {
            val rightCost = completion[split][adjacency.other[edge]]
            if (rightCost < distance[adjacency.aIdx[edge]])
              distance[adjacency.aIdx[edge]] = rightCost
          }
        }
      closeWeightedParents(distance, heap)
    }
    return completion[0][start].takeIf { it < COMPLETION_INFINITY }?.toInt()
  }

  fun after(prefix: List<Σᐩ>): CompletionLengths = query(prefix).completion

  private fun buildCompletion(prefix: List<Σᐩ>, full: Array<Array<KBitSet>>?): CompletionLengths {
    full ?: return EMPTY_COMPLETION
    val n = prefix.size
    val nodeCount = variableCount * (n + 2)
    fun word(variable: Int) = variable
    fun residual(begin: Int, variable: Int) = variableCount * (begin + 1) + variable

    val epsilonSeeds = BooleanArray(nodeCount)
    val terminalSeeds = Array(nodeCount) { KBitSet(terminalCount) }
    val unaryRules = linkedSetOf<UnaryRule>()
    val sumRules = linkedSetOf<SumRule>()

    grammar.forEach { (lhs, rhs) ->
      if (rhs.size == 1)
        terminalSeeds[word(grammar.bindex[lhs])].set(grammar.tmMap.getValue(rhs.single()))
    }
    binaryRules.forEach { (parent, left, right) ->
      sumRules += SumRule(word(parent), word(left), word(right))
      for (begin in 0 until n)
        sumRules += SumRule(residual(begin, parent), residual(begin, left), word(right))
    }
    for (variable in 0 until variableCount)
      unaryRules += UnaryRule(residual(n, variable), word(variable))

    for (begin in 0 until n) {
      for (variable in full[begin][n].iterator())
        epsilonSeeds[residual(begin, variable)] = true
      for (split in begin + 1 until n)
        for (left in full[begin][split].iterator())
          (grammar.leftAdj[left] ?: continue).forEachIfIn(ALL_VARIABLES) { right, parent ->
            unaryRules += UnaryRule(residual(begin, parent), residual(split, right))
          }
    }

    val root = residual(0, start)
    val system = LengthSystem(
      nodeCount = nodeCount,
      terminalCount = terminalCount,
      root = root,
      epsilonSeeds = epsilonSeeds,
      terminalSeeds = terminalSeeds,
      unaryRules = unaryRules.toList(),
      sumRules = sumRules.toList()
    )
    return CompletionLengths(
      acceptsEmpty = system.acceptsEmpty,
      nextTerminals = system.firstTerminals.mapTo(linkedSetOf()) { grammar.tmLst[it] },
      lengths = system.lengths()
    )
  }

  /** Longest token prefix shared by every admitted suffix in the selected branch. */
  fun forcedContinuation(prefix: List<Σᐩ>, includeEmpty: Boolean): List<Σᐩ> {
    val result = mutableListOf<Σᐩ>()
    while (true) {
      val completion = after(prefix + result)
      if ((includeEmpty || result.isNotEmpty()) && completion.acceptsEmpty) break
      val next = completion.nextTerminals.singleOrNull() ?: break
      result += next
    }
    return result
  }

  private fun prefixChart(prefix: List<Σᐩ>): Array<Array<KBitSet>>? {
    val size = prefix.size
    val full = Array(size + 1) { Array(size + 1) { KBitSet(variableCount) } }
    prefix.forEachIndexed { index, terminal ->
      val terminalIndex = grammar.tmMap[terminal] ?: return null
      grammar.tmToVidx[terminalIndex].forEach { full[index][index + 1].set(it) }
    }
    for (span in 2..size) for (begin in 0..size - span) {
      val end = begin + span
      val target = full[begin][end]
      for (split in begin + 1 until end) {
        val left = full[begin][split]
        val right = full[split][end]
        if (left.isEmpty() || right.isEmpty()) continue
        for (leftVariable in left.iterator())
          (grammar.leftAdj[leftVariable] ?: continue).forEachIfIn(right) { _, parent ->
            target.set(parent)
          }
      }
    }
    return full
  }

  /**
   * Solves all first-terminal spectra together. Bit d-1 denotes an exact positive length d.
   * Grammar-word spectra are precomputed above; only residual rows depend on the prefix.
   */
  private fun solveBoundedSuffixLengths(
    prefixSize: Int,
    full: Array<Array<KBitSet>>,
    countPerTerminal: Int,
    maxLength: Int
  ): BoundedSuffixLengthAnalysis {
    val rowLengths = Array(prefixSize + 1) { IntArray(variableCount) }
    val rowFirstLengths = Array(prefixSize + 1) { IntArray(variableCount * terminalCount) }
    val rowFirstTerminals = Array(prefixSize + 1) { IntArray(variableCount * terminalWordCount) }
    val rowNullable = Array(prefixSize + 1) { BooleanArray(variableCount) }
    boundedWordSpectra.lengths.copyInto(rowLengths[prefixSize])
    boundedWordSpectra.firstLengths.copyInto(rowFirstLengths[prefixSize])
    boundedWordSpectra.firstTerminals.copyInto(rowFirstTerminals[prefixSize])

    for (begin in prefixSize - 1 downTo 0) {
      val lengths = rowLengths[begin]
      val firstLengths = rowFirstLengths[begin]
      val firstTerminals = rowFirstTerminals[begin]
      val nullable = rowNullable[begin]

      for (variable in full[begin][prefixSize].iterator()) nullable[variable] = true
      for (split in begin + 1 until prefixSize)
        for (leftVariable in full[begin][split].iterator())
          (grammar.leftAdj[leftVariable] ?: continue).forEachIfIn(ALL_VARIABLES) { right, parent ->
            unionBoundedState(
              targetVariable = parent,
              targetLengths = lengths,
              targetFirstLengths = firstLengths,
              targetFirstTerminals = firstTerminals,
              targetNullable = nullable,
              sourceVariable = right,
              sourceLengths = rowLengths[split],
              sourceFirstLengths = rowFirstLengths[split],
              sourceFirstTerminals = rowFirstTerminals[split],
              sourceNullable = rowNullable[split]
            )
          }

      val queued = BooleanArray(variableCount)
      val queue = ArrayDeque<Int>()
      fun enqueue(variable: Int) {
        if (!queued[variable]) {
          queued[variable] = true
          queue += variable
        }
      }
      for (variable in 0 until variableCount)
        if (nullable[variable] || lengths[variable] != 0 ||
          hasFirstTerminal(firstTerminals, variable)) enqueue(variable)

      while (queue.isNotEmpty()) {
        val left = queue.removeFirst().also { queued[it] = false }
        val leftLengthMask = lengths[left]
        val leftOffset = left * terminalCount
        binaryRulesByLeft[left].forEach { rule ->
          val rightLengthMask = boundedWordSpectra.lengths[rule.right]
          if (minimumWordLength[rule.right] >= COMPLETION_INFINITY) return@forEach
          var changed = false
          val parent = rule.parent
          val candidateLengths = concatenateLengthMasks(leftLengthMask, rightLengthMask) or
            if (nullable[left]) rightLengthMask else 0
          val mergedLengths = lengths[parent] or candidateLengths
          if (mergedLengths != lengths[parent]) {
            lengths[parent] = mergedLengths
            changed = true
          }

          val parentOffset = parent * terminalCount
          val rightOffset = rule.right * terminalCount
          for (terminal in 0 until terminalCount) {
            val candidateFirstLengths =
              concatenateLengthMasks(firstLengths[leftOffset + terminal], rightLengthMask) or
                if (nullable[left]) boundedWordSpectra.firstLengths[rightOffset + terminal] else 0
            val index = parentOffset + terminal
            val merged = firstLengths[index] or candidateFirstLengths
            if (merged != firstLengths[index]) {
              firstLengths[index] = merged
              changed = true
            }
          }
          val parentTerminalOffset = parent * terminalWordCount
          val leftTerminalOffset = left * terminalWordCount
          val rightTerminalOffset = rule.right * terminalWordCount
          for (word in 0 until terminalWordCount) {
            val candidateFirstTerminals = firstTerminals[leftTerminalOffset + word] or
              if (nullable[left]) boundedWordSpectra.firstTerminals[rightTerminalOffset + word] else 0
            val index = parentTerminalOffset + word
            val merged = firstTerminals[index] or candidateFirstTerminals
            if (merged != firstTerminals[index]) {
              firstTerminals[index] = merged
              changed = true
            }
          }
          if (changed) enqueue(parent)
        }
      }
    }

    val rootFirstLengths = rowFirstLengths[0]
    val rootOffset = start * terminalCount
    val admittedBits = lowLengthBits(maxLength)
    val nextTerminals = linkedSetOf<Σᐩ>()
    val rootFirstTerminalOffset = start * terminalWordCount
    for (terminal in 0 until terminalCount)
      if (rootFirstTerminals(rootFirstTerminalOffset, terminal, rowFirstTerminals[0]))
        nextTerminals += grammar.tmLst[terminal]
    val lengthsByFirstTerminal = linkedMapOf<Σᐩ, List<Int>>().apply {
      for (terminal in 0 until terminalCount) {
        var lengths = rootFirstLengths[rootOffset + terminal] and admittedBits
        if (lengths == 0) continue
        val admitted = ArrayList<Int>(countPerTerminal)
        while (lengths != 0 && admitted.size < countPerTerminal) {
          val bit = lengths.countTrailingZeroBits()
          admitted += bit + 1
          lengths = lengths and (lengths - 1)
        }
        if (admitted.isNotEmpty()) put(grammar.tmLst[terminal], admitted)
      }
    }
    return BoundedSuffixLengthAnalysis(nextTerminals, lengthsByFirstTerminal, maxLength)
  }

  /** Exact grammar-word spectra, shared by every prefix query. */
  private fun boundedWordSpectra(): BoundedSpectra {
    val lengths = IntArray(variableCount)
    val firstLengths = IntArray(variableCount * terminalCount)
    val firstTerminals = IntArray(variableCount * terminalWordCount)
    grammar.forEach { (lhs, rhs) ->
      if (rhs.size == 1) {
        val parent = grammar.bindex[lhs]
        val terminal = grammar.tmMap.getValue(rhs.single())
        lengths[parent] = lengths[parent] or 1
        val index = parent * terminalCount + terminal
        firstLengths[index] = firstLengths[index] or 1
        val terminalIndex = parent * terminalWordCount + terminal / Int.SIZE_BITS
        firstTerminals[terminalIndex] =
          firstTerminals[terminalIndex] or (1 shl (terminal % Int.SIZE_BITS))
      }
    }

    var changed: Boolean
    do {
      changed = false
      binaryRules.forEach { rule ->
        val candidateLengths = concatenateLengthMasks(lengths[rule.left], lengths[rule.right])
        val mergedLengths = lengths[rule.parent] or candidateLengths
        if (mergedLengths != lengths[rule.parent]) {
          lengths[rule.parent] = mergedLengths
          changed = true
        }
        val parentOffset = rule.parent * terminalCount
        val leftOffset = rule.left * terminalCount
        for (terminal in 0 until terminalCount) {
          val candidateFirstLengths =
            concatenateLengthMasks(firstLengths[leftOffset + terminal], lengths[rule.right])
          val index = parentOffset + terminal
          val merged = firstLengths[index] or candidateFirstLengths
          if (merged != firstLengths[index]) {
            firstLengths[index] = merged
            changed = true
          }
        }
        if (minimumWordLength[rule.left] < COMPLETION_INFINITY &&
          minimumWordLength[rule.right] < COMPLETION_INFINITY) {
          val parentOffset = rule.parent * terminalWordCount
          val leftOffset = rule.left * terminalWordCount
          for (word in 0 until terminalWordCount) {
            val merged = firstTerminals[parentOffset + word] or firstTerminals[leftOffset + word]
            if (merged != firstTerminals[parentOffset + word]) {
              firstTerminals[parentOffset + word] = merged
              changed = true
            }
          }
        }
      }
    } while (changed)
    return BoundedSpectra(lengths, firstLengths, firstTerminals)
  }

  private fun unionBoundedState(
    targetVariable: Int,
    targetLengths: IntArray,
    targetFirstLengths: IntArray,
    targetFirstTerminals: IntArray,
    targetNullable: BooleanArray,
    sourceVariable: Int,
    sourceLengths: IntArray,
    sourceFirstLengths: IntArray,
    sourceFirstTerminals: IntArray,
    sourceNullable: BooleanArray
  ): Boolean {
    var changed = false
    if (sourceNullable[sourceVariable] && !targetNullable[targetVariable]) {
      targetNullable[targetVariable] = true
      changed = true
    }
    val mergedLengths = targetLengths[targetVariable] or sourceLengths[sourceVariable]
    if (mergedLengths != targetLengths[targetVariable]) {
      targetLengths[targetVariable] = mergedLengths
      changed = true
    }
    val targetOffset = targetVariable * terminalCount
    val sourceOffset = sourceVariable * terminalCount
    for (terminal in 0 until terminalCount) {
      val targetIndex = targetOffset + terminal
      val merged = targetFirstLengths[targetIndex] or sourceFirstLengths[sourceOffset + terminal]
      if (merged != targetFirstLengths[targetIndex]) {
        targetFirstLengths[targetIndex] = merged
        changed = true
      }
    }
    val targetTerminalOffset = targetVariable * terminalWordCount
    val sourceTerminalOffset = sourceVariable * terminalWordCount
    for (word in 0 until terminalWordCount) {
      val index = targetTerminalOffset + word
      val merged = targetFirstTerminals[index] or sourceFirstTerminals[sourceTerminalOffset + word]
      if (merged != targetFirstTerminals[index]) {
        targetFirstTerminals[index] = merged
        changed = true
      }
    }
    return changed
  }

  private fun hasFirstTerminal(firstTerminals: IntArray, variable: Int): Boolean {
    val offset = variable * terminalWordCount
    for (word in 0 until terminalWordCount)
      if (firstTerminals[offset + word] != 0) return true
    return false
  }

  private fun rootFirstTerminals(offset: Int, terminal: Int, firstTerminals: IntArray): Boolean =
    firstTerminals[offset + terminal / Int.SIZE_BITS] and
      (1 shl (terminal % Int.SIZE_BITS)) != 0

  private fun minimumWordLengths(): LongArray {
    val result = LongArray(variableCount) { COMPLETION_INFINITY }
    grammar.forEach { (lhs, rhs) ->
      if (rhs.size == 1) result[grammar.bindex[lhs]] = 1L
    }
    var changed: Boolean
    do {
      changed = false
      binaryRules.forEach { rule ->
        val left = result[rule.left]
        val right = result[rule.right]
        if (left >= COMPLETION_INFINITY || right >= COMPLETION_INFINITY) return@forEach
        val candidate = left + right
        if (candidate < result[rule.parent]) {
          result[rule.parent] = candidate
          changed = true
        }
      }
    } while (changed)
    return result
  }

  private fun minimumWordLengthsStartingWith(allowedTerminals: Set<Σᐩ>): LongArray {
    val result = LongArray(variableCount) { COMPLETION_INFINITY }
    val heap = PackedMinHeap()
    allowedTerminals.forEach { terminal ->
      val terminalIndex = grammar.tmMap[terminal] ?: return@forEach
      grammar.tmToVidx[terminalIndex].forEach { parent -> result[parent] = 1L }
    }
    closeWeightedParents(result, heap)
    return result
  }

  private fun minimumWordLengthsStartingWithCached(allowedTerminals: Set<Σᐩ>): LongArray =
    if (allowedTerminals == grammar.terminals) minimumWordLength
    else constrainedMinimums.getOrPut(allowedTerminals) { minimumWordLengthsStartingWith(allowedTerminals) }

  private fun closeWeightedParents(distance: LongArray, heap: PackedMinHeap) {
    heap.clear()
    distance.forEachIndexed { variable, cost ->
      if (cost < COMPLETION_INFINITY) heap.push(variable, cost)
    }
    while (heap.isNotEmpty()) {
      val packed = heap.pop()
      val variable = packed.toInt()
      val cost = packed ushr 32
      if (cost != distance[variable]) continue
      weightedParents[variable].forEach { edge ->
        val candidate = cost + edge.appendedLength
        if (candidate < distance[edge.parent] && candidate < COMPLETION_INFINITY) {
          distance[edge.parent] = candidate
          heap.push(edge.parent, candidate)
        }
      }
    }
  }

  private inner class LengthSystem(
    private val nodeCount: Int,
    terminalCount: Int,
    private val root: Int,
    private val epsilonSeeds: BooleanArray,
    private val terminalSeeds: Array<KBitSet>,
    private val unaryRules: List<UnaryRule>,
    private val sumRules: List<SumRule>
  ) {
    private val productive = productiveNodes()
    private val relevant = relevantNodes()
    val acceptsEmpty: Boolean
    val firstTerminals: List<Int>

    init {
      val nullable = epsilonSeeds.copyOf()
      val first = Array(nodeCount) { KBitSet(terminalCount) }
      terminalSeeds.forEachIndexed { node, terminals -> first[node] or terminals }
      val unaryUses = Array(nodeCount) { mutableListOf<UnaryRule>() }
      val sumUses = Array(nodeCount) { mutableListOf<SumRule>() }
      unaryRules.filter(::isActive).forEach { unaryUses[it.child] += it }
      sumRules.filter(::isActive).forEach {
        sumUses[it.left] += it
        if (it.right != it.left) sumUses[it.right] += it
      }
      val queued = BooleanArray(nodeCount)
      val queue = ArrayDeque<Int>()
      fun enqueue(node: Int) {
        if (relevant[node] && !queued[node]) { queued[node] = true; queue += node }
      }
      for (node in 0 until nodeCount)
        if (nullable[node] || !first[node].isEmpty()) enqueue(node)
      while (queue.isNotEmpty()) {
        val child = queue.removeFirst().also { queued[it] = false }
        unaryUses[child].forEach { rule ->
          var changed = unionInto(first[rule.parent], first[rule.child])
          if (nullable[rule.child] && !nullable[rule.parent]) {
            nullable[rule.parent] = true
            changed = true
          }
          if (changed) enqueue(rule.parent)
        }
        sumUses[child].forEach { rule ->
          var changed = unionInto(first[rule.parent], first[rule.left])
          if (nullable[rule.left]) changed = unionInto(first[rule.parent], first[rule.right]) || changed
          if (nullable[rule.left] && nullable[rule.right] && !nullable[rule.parent]) {
            nullable[rule.parent] = true
            changed = true
          }
          if (changed) enqueue(rule.parent)
        }
      }
      acceptsEmpty = relevant[root] && nullable[root]
      firstTerminals = if (relevant[root]) first[root].toList() else emptyList()
    }

    fun lengths(): Sequence<Int> = sequence {
      if (!relevant[root]) return@sequence
      val unaryParents = Array(nodeCount) { mutableSetOf<Int>() }
      val sumUses = Array(nodeCount) { mutableSetOf<SumUse>() }
      unaryRules.filter(::isActive).forEach { unaryParents[it.child] += it.parent }
      sumRules.filter(::isActive).forEach {
        sumUses[it.left] += SumUse(it.parent, it.right)
        sumUses[it.right] += SumUse(it.parent, it.left)
      }

      val known = Array(nodeCount) { mutableSetOf<Int>() }
      val settled = Array(nodeCount) { mutableListOf<Int>() }
      val heap = PackedMinHeap()
      fun offer(node: Int, length: Int) {
        if (length >= 0 && relevant[node] && known[node].add(length)) heap.push(node, length.toLong())
      }
      for (node in 0 until nodeCount) if (relevant[node]) {
        if (epsilonSeeds[node]) offer(node, 0)
        if (!terminalSeeds[node].isEmpty()) offer(node, 1)
      }

      while (heap.isNotEmpty()) {
        val packed = heap.pop()
        val node = packed.toInt()
        val length = (packed ushr 32).toInt()
        settled[node] += length
        unaryParents[node].forEach { offer(it, length) }
        sumUses[node].forEach { use ->
          settled[use.sibling].forEach { siblingLength ->
            val sum = length.toLong() + siblingLength
            if (sum <= Int.MAX_VALUE) offer(use.parent, sum.toInt())
          }
        }
        if (node == root) yield(length)
      }
    }

    private fun productiveNodes(): BooleanArray {
      val result = BooleanArray(nodeCount)
      val unaryParents = Array(nodeCount) { mutableListOf<Int>() }
      val sumUses = Array(nodeCount) { mutableListOf<SumRule>() }
      unaryRules.forEach { unaryParents[it.child] += it.parent }
      sumRules.forEach {
        sumUses[it.left] += it
        if (it.right != it.left) sumUses[it.right] += it
      }
      val queue = ArrayDeque<Int>()
      fun mark(node: Int) {
        if (!result[node]) { result[node] = true; queue += node }
      }
      for (node in 0 until nodeCount)
        if (epsilonSeeds[node] || !terminalSeeds[node].isEmpty()) mark(node)
      while (queue.isNotEmpty()) {
        val child = queue.removeFirst()
        unaryParents[child].forEach(::mark)
        sumUses[child].forEach { if (result[it.left] && result[it.right]) mark(it.parent) }
      }
      return result
    }

    private fun relevantNodes(): BooleanArray {
      val result = BooleanArray(nodeCount)
      if (!productive[root]) return result
      val unaryByParent = unaryRules.groupBy { it.parent }
      val sumsByParent = sumRules.groupBy { it.parent }
      val queue = ArrayDeque<Int>().apply { add(root) }
      result[root] = true
      fun mark(node: Int) {
        if (productive[node] && !result[node]) { result[node] = true; queue += node }
      }
      while (queue.isNotEmpty()) {
        val parent = queue.removeFirst()
        unaryByParent[parent].orEmpty().forEach { mark(it.child) }
        sumsByParent[parent].orEmpty()
          .filter { productive[it.left] && productive[it.right] }
          .forEach { mark(it.left); mark(it.right) }
      }
      return result
    }

    private fun isActive(rule: UnaryRule) = relevant[rule.parent] && relevant[rule.child]
    private fun isActive(rule: SumRule) =
      relevant[rule.parent] && relevant[rule.left] && relevant[rule.right]
  }

  private val ALL_VARIABLES = KBitSet(variableCount).apply { setAll() }

  private companion object {
    val EMPTY_COMPLETION = CompletionLengths(false, emptySet(), emptySequence())
  }
}

private fun unionInto(target: KBitSet, source: KBitSet): Boolean {
  var changed = false
  for (index in target.data.indices) {
    val union = target.data[index] or source.data[index]
    if (union != target.data[index]) { target.data[index] = union; changed = true }
  }
  return changed
}

/** Convolution of positive lengths encoded at bit d-1, truncated exactly at length 32. */
private fun concatenateLengthMasks(leftLengths: Int, rightLengths: Int): Int {
  var left = leftLengths
  var result = 0
  while (left != 0) {
    val shift = left.countTrailingZeroBits() + 1
    if (shift < Int.SIZE_BITS) result = result or (rightLengths shl shift)
    left = left and (left - 1)
  }
  return result
}

private fun lowLengthBits(maxLength: Int): Int = when {
  maxLength <= 0 -> 0
  maxLength >= Int.SIZE_BITS -> -1
  else -> (1 shl maxLength) - 1
}

private fun <T> Sequence<T>.memoized(): Sequence<T> {
  val source by lazy { iterator() }
  val values = mutableListOf<T>()
  var exhausted = false
  return Sequence {
    object : Iterator<T> {
      var index = 0
      override fun hasNext(): Boolean {
        if (index < values.size) return true
        if (exhausted) return false
        if (source.hasNext()) values += source.next() else exhausted = true
        return index < values.size
      }

      override fun next(): T {
        if (!hasNext()) throw NoSuchElementException()
        return values[index++]
      }
    }
  }
}

private class PackedMinHeap {
  private var values = LongArray(64)
  private var size = 0

  fun isNotEmpty() = size > 0
  fun clear() { size = 0 }

  fun push(node: Int, length: Long) {
    val value = (length shl 32) or (node.toLong() and 0xffffffffL)
    if (size == values.size) values = values.copyOf(size * 2)
    var index = size++
    while (index > 0) {
      val parent = (index - 1) / 2
      if (values[parent] <= value) break
      values[index] = values[parent]
      index = parent
    }
    values[index] = value
  }

  fun pop(): Long {
    val result = values[0]
    val last = values[--size]
    var index = 0
    while (index * 2 + 1 < size) {
      val left = index * 2 + 1
      val right = left + 1
      val child = if (right < size && values[right] < values[left]) right else left
      if (values[child] >= last) break
      values[index] = values[child]
      index = child
    }
    if (size > 0) values[index] = last
    return result
  }
}

private const val COMPLETION_INFINITY = 2_147_483_648L
