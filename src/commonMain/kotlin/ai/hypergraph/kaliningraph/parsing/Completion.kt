@file:Suppress("NonAsciiCharacters")

package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.KBitSet
import ai.hypergraph.kaliningraph.types.cache

/** Cached indexes for exact, unbounded suffix-length queries. */
val CFG.completionIndex: CFGCompletionIndex by cache { CFGCompletionIndex(this) }

fun CFG.minimumSuffixLength(prefix: List<Σᐩ>): Int? =
  completionIndex.minimumSuffixLength(prefix)

fun CFG.minimumNonemptySuffixLength(prefix: List<Σᐩ>): Int? =
  completionIndex.minimumSuffixLength(prefix, terminals)

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
  private val start = grammar.bindex[START_SYMBOL]
  private val binaryRules = grammar.mapNotNull { (lhs, rhs) ->
    if (rhs.size == 2)
      BinaryRule(grammar.bindex[lhs], grammar.bindex[rhs[0]], grammar.bindex[rhs[1]])
    else null
  }
  private val minimumWordLength = minimumWordLengths()
  private val weightedParents = Array(variableCount) { mutableListOf<WeightedParent>() }.also { parents ->
    binaryRules.forEach { rule ->
      val appendedLength = minimumWordLength[rule.right]
      if (appendedLength < COMPLETION_INFINITY)
        parents[rule.left] += WeightedParent(rule.parent, appendedLength)
    }
  }

  /** Exact min-plus query; an optional terminal set constrains the first emitted token. */
  fun minimumSuffixLength(prefix: List<Σᐩ>, allowedFirstTerminals: Set<Σᐩ>? = null): Int? {
    if (allowedFirstTerminals?.isEmpty() == true) return null
    val constrainedMinimum = allowedFirstTerminals?.let(::minimumWordLengthsStartingWith)
    if (prefix.isEmpty())
      return (constrainedMinimum ?: minimumWordLength)[start]
        .takeIf { it < COMPLETION_INFINITY }?.toInt()
    val full = prefixChart(prefix) ?: return null
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

  fun after(prefix: List<Σᐩ>): CompletionLengths {
    val full = prefixChart(prefix) ?: return EMPTY_COMPLETION
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