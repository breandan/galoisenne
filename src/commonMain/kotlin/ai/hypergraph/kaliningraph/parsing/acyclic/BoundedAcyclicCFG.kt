package ai.hypergraph.kaliningraph.parsing

import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlin.random.Random

internal data class BoundedCountCacheStats(
  val entries: Int,
  val weight: Int,
  val maxWeight: Int,
  val hits: Int,
  val misses: Int,
  val evictions: Int
)

internal data class BoundedCountWorkspaceStats(
  val entries: Int,
  val hits: Int,
  val misses: Int,
  val minimumEntries: Int,
  val minimumHits: Int,
  val minimumMisses: Int,
  val decodingEntries: Int,
  val decodingHits: Int,
  val decodingMisses: Int
)

internal class BoundedDecodingChoice(
  val rhs: List<String>,
  val split: Int,
  val cumulativeWeight: ExactCount,
  val rightCount: ExactCount = EXACT_COUNT_ZERO
)

/** Exact count of a symbol's shortest derivations, shared by overlapping residual grammars. */
internal class BoundedMinimumRow(
  val yield: Int,
  val count: ExactCount
)

/** One sampled derivation together with the exact terminal-yield length it was drawn from. */
data class BoundedLengthSample(
  val length: Int,
  val terminals: List<String>
)

/** A shortest-first draw together with the exact length prefix which was counted to produce it. */
data class BoundedLengthSampleBatch(
  val samples: List<BoundedLengthSample>,
  val inspectedDerivationCount: BigInteger,
  val inspectedLengths: IntRange,
  val coversFullBound: Boolean
)

/**
 * An immutable acyclic CFG which already owns the indexes needed by [BoundedAcyclicCFG].
 *
 * Large generated grammars commonly assemble their productions from immutable, already-grouped
 * DAG fragments. Re-grouping and re-validating every production when only the start root changes
 * is needlessly expensive. Implementations of this interface promise that:
 *
 *  * [acyclicCountingOrder] contains every nonterminal exactly once, children before parents;
 *  * [acyclicNonterminalIndex] is the corresponding dense `0 until size` index;
 *  * [productionsFor] returns every production for an indexed nonterminal in stable order; and
 *  * the set iterator, indexes and production lists are immutable for the lifetime of the CFG.
 *
 * Ordinary callers should continue using [boundedAcyclic], which validates its input. This
 * contract exists for grammar generators that establish the same invariants by construction.
 */
interface PreindexedAcyclicCFG : Set<Production> {
  val acyclicCountingOrder: List<String>
  val acyclicNonterminalIndex: Map<String, Int>
  val acyclicStructuralStats: String
  fun productionsFor(nonterminal: String): List<Production>
}

/**
 * Retains immutable derivation-count rows for a family of overlapping acyclic grammars.
 *
 * Entries are keyed by nonterminal spelling. Within one workspace, a symbol's complete transitive
 * set of productions must remain stable: the symbol may be absent from some grammar views, but it
 * must never be redefined or acquire a differently defined descendant. Its production iteration
 * order must also remain stable because that order defines the exact indexed-sampling order. This
 * is useful for a sweep of residual grammars which all reference the same prepared source grammar.
 *
 * A miss still uses the process-global structural cache, then retains that immutable row and its
 * monotonic IDs locally. A later overlapping grammar can therefore bypass both structural hashing
 * and arithmetic. Call [clear] when the prepared grammar is no longer reusable.
 */
class BoundedCountWorkspace {
  internal class Row(
    val maxLength: Int,
    val values: Array<ExactCount>,
    val firstNonzero: Int,
    val lastNonzero: Int,
    val countVectorId: Int,
    val structuralId: Int
  )

  private val rows = mutableMapOf<String, Row>()
  private val minimumRows = mutableMapOf<String, BoundedMinimumRow>()
  private val decodingChoices = mutableMapOf<String, MutableMap<Int, List<BoundedDecodingChoice>>>()
  private var hits = 0
  private var misses = 0
  private var minimumHits = 0
  private var minimumMisses = 0
  private var decodingHits = 0
  private var decodingMisses = 0

  internal val isEmpty: Boolean get() = rows.isEmpty()

  internal fun get(symbol: String, requiredMaxLength: Int): Row? {
    val row = rows[symbol]
    return if (row != null && row.maxLength >= requiredMaxLength) {
      hits++
      row
    } else {
      misses++
      null
    }
  }

  internal fun put(
    symbol: String,
    maxLength: Int,
    values: Array<ExactCount>,
    firstNonzero: Int,
    lastNonzero: Int,
    countVectorId: Int,
    structuralId: Int
  ): Row {
    rows[symbol]?.takeIf { it.maxLength >= maxLength }?.let { return it }
    return Row(
      maxLength, values, firstNonzero, lastNonzero, countVectorId, structuralId
    )
      .also { rows[symbol] = it }
  }

  internal fun getMinimumRow(symbol: String): BoundedMinimumRow? = minimumRows[symbol].also {
    if (it == null) minimumMisses++ else minimumHits++
  }

  internal fun putMinimumRow(symbol: String, row: BoundedMinimumRow): BoundedMinimumRow =
    minimumRows.getOrPut(symbol) { row }

  internal fun getDecodingChoices(
    symbol: String,
    length: Int
  ): List<BoundedDecodingChoice>? {
    val choices = decodingChoices[symbol]?.get(length)
    return if (choices == null) {
      decodingMisses++
      null
    } else {
      decodingHits++
      choices
    }
  }

  internal fun putDecodingChoices(
    symbol: String,
    length: Int,
    choices: List<BoundedDecodingChoice>
  ): List<BoundedDecodingChoice> =
    decodingChoices.getOrPut(symbol) { mutableMapOf() }
      .getOrPut(length) { choices }

  /** Drops every retained row. Existing bounded grammars which already forced their counts are unaffected. */
  fun clear() {
    rows.clear()
    minimumRows.clear()
    decodingChoices.clear()
    hits = 0
    misses = 0
    minimumHits = 0
    minimumMisses = 0
    decodingHits = 0
    decodingMisses = 0
  }

  internal fun stats() = BoundedCountWorkspaceStats(
    entries = rows.size,
    hits = hits,
    misses = misses,
    minimumEntries = minimumRows.size,
    minimumHits = minimumHits,
    minimumMisses = minimumMisses,
    decodingEntries = decodingChoices.values.fold(0) { total, lengths -> total + lengths.size },
    decodingHits = decodingHits,
    decodingMisses = decodingMisses
  )
}

/**
 * A length-bounded view of an acyclic CFG in binary normal form.
 *
 * Variable-unit productions are retained rather than expanded. [derivationCount] and [sample]
 * range uniformly over derivations whose terminal yield has length `0..maxLength`; ambiguous
 * derivations are deliberately counted separately. The underlying [grammar] remains a regular
 * [CFG], and [forest] exposes its finite shared parse forest without expanding its DAG.
 */
class BoundedAcyclicCFG private constructor(
  val grammar: CFG,
  val maxLength: Int,
  val startSymbol: String,
  private val workspace: BoundedCountWorkspace?,
  suppliedCountingOrder: List<String>?,
  reusedIndex: SuppliedGrammarIndex?
) {
  private data class SuppliedGrammarIndex(
    val countingOrder: List<String>,
    val nonterminals: Set<String>,
    val nonterminalIndex: Map<String, Int>,
    val rules: Map<String, List<Production>>?,
    val preindexed: PreindexedAcyclicCFG?,
    val structuralStats: String
  )

  constructor(
    grammar: CFG,
    maxLength: Int,
    startSymbol: String = START_SYMBOL,
    workspace: BoundedCountWorkspace? = null,
    suppliedCountingOrder: List<String>? = null
  ) : this(grammar, maxLength, startSymbol, workspace, suppliedCountingOrder, null)

  companion object {
    // Roughly 8K length-64 vectors, plus their structural equations. Values are immutable and may
    // be shared by every bounded grammar in the process; the weighted LRU prevents cursor sweeps
    // from retaining an unbounded number of exact integers.
    private const val SHARED_COUNT_CACHE_MAX_WEIGHT = 600_000
    private val sharedCountCache = SharedCountCache(SHARED_COUNT_CACHE_MAX_WEIGHT)

    internal fun clearSharedCountCache() = sharedCountCache.clear()
    internal fun sharedCountCacheStats(): BoundedCountCacheStats = sharedCountCache.stats()
  }

  // A caller which already owns a child-before-parent order has enough information to index and
  // validate the grammar in one production pass. Cursor residuals use this path thousands of
  // times; rebuilding the LHS set, grouping the same rules, and scanning once more for validation
  // otherwise dominates their recognition-free construction cost on Kotlin/JS.
  private val suppliedIndex = reusedIndex
    ?: (grammar as? PreindexedAcyclicCFG)?.let(::usePreindexedGrammar)
    ?: suppliedCountingOrder?.let(::indexSuppliedGrammar)
  private val nonterminalList = suppliedIndex?.countingOrder
    ?: grammar.mapTo(linkedSetOf()) { it.first }.toList()
  private val nonterminals = suppliedIndex?.nonterminals ?: nonterminalList.toSet()
  private val nonterminalIndex = suppliedIndex?.nonterminalIndex
    ?: buildMap(nonterminalList.size) {
      nonterminalList.forEachIndexed { index, symbol -> put(symbol, index) }
    }
  private val rules = when {
    suppliedIndex?.preindexed != null -> emptyMap()
    suppliedIndex?.rules != null -> suppliedIndex.rules
    else -> grammar.groupBy { it.first }
  }
  private val countingOrder: List<String>
  private val overflowCounts = mutableMapOf<Pair<String, Int>, ExactCount>()
  private val localMinimumRows = mutableMapOf<String, BoundedMinimumRow>()
  private val recognitionIndexDelegate = lazy(::buildRecognitionIndex)
  private val recognitionIndex by recognitionIndexDelegate

  internal val isRecognitionIndexInitialized: Boolean
    get() = recognitionIndexDelegate.isInitialized()

  // Bounded construction has already indexed the left-hand variables. Expose the small report
  // summary directly so callers do not populate CFG.kt's much broader grammar-property caches just
  // to print three cardinalities for hundreds of short-lived residual grammars.
  private val structuralStatsText: String by lazy {
    suppliedIndex?.structuralStats ?: run {
      val terminals = linkedSetOf<String>()
      var nonterminalProductions = 0
      grammar.forEach { (_, rhs) ->
        rhs.filterTo(terminals) { it !in nonterminals }
        if (rhs.size != 1 || rhs[0] in nonterminals) nonterminalProductions++
      }
      "CFG(|Σ|=${terminals.size}, |V|=${nonterminals.size}, |P|=$nonterminalProductions)"
    }
  }

  private fun rulesFor(nonterminal: String): List<Production> =
    suppliedIndex?.preindexed?.productionsFor(nonterminal) ?: rules[nonterminal].orEmpty()

  /** Compact structural statistics for this bounded grammar. */
  fun structuralStats(): String = structuralStatsText

  init {
    require(maxLength >= 0) { "Maximum yield length must be nonnegative" }
    require(grammar.isEmpty() || startSymbol in nonterminals) {
      "Start symbol '$startSymbol' has no productions"
    }
    countingOrder = suppliedIndex?.countingOrder ?: requireAcyclic()
  }

  /** Reuses this validated immutable grammar index when only the terminal-length bound changes. */
  private fun rebound(newMaxLength: Int): BoundedAcyclicCFG {
    val sharedIndex = suppliedIndex ?: SuppliedGrammarIndex(
      countingOrder = countingOrder,
      nonterminals = nonterminals,
      nonterminalIndex = nonterminalIndex,
      rules = rules,
      preindexed = null,
      structuralStats = structuralStats()
    )
    return BoundedAcyclicCFG(
      grammar,
      newMaxLength,
      startSymbol,
      workspace,
      suppliedCountingOrder = null,
      reusedIndex = sharedIndex
    )
  }

  /** Accepts indexes whose acyclicity and immutability were established by the generator. */
  private fun usePreindexedGrammar(indexed: PreindexedAcyclicCFG): SuppliedGrammarIndex {
    val order = indexed.acyclicCountingOrder
    val index = indexed.acyclicNonterminalIndex
    require(order.size == index.size && order.indices.all { position ->
      index[order[position]] == position
    }) { "Preindexed CFG must supply a dense index matching its counting order" }
    return SuppliedGrammarIndex(
      countingOrder = order,
      nonterminals = index.keys,
      nonterminalIndex = index,
      rules = null,
      preindexed = indexed,
      structuralStats = indexed.acyclicStructuralStats
    )
  }

  /**
   * Indexes a caller-prepared child-before-parent order without rebuilding Kahn's adjacency graph.
   * Every variable must occur exactly once, while one production scan groups rules, computes the
   * report cardinalities, verifies binary normal form, and validates every variable dependency.
   */
  private fun indexSuppliedGrammar(order: List<String>): SuppliedGrammarIndex {
    val stableOrder = order.toList()
    val position = buildMap(stableOrder.size) {
      stableOrder.forEachIndexed { index, symbol ->
        require(put(symbol, index) == null) { "Counting order repeats symbol '$symbol'" }
      }
    }
    val groupedRules = linkedMapOf<String, MutableList<Production>>()
    val seenNonterminals = linkedSetOf<String>()
    val terminals = linkedSetOf<String>()
    var nonterminalProductions = 0
    grammar.forEach { (lhs, rhs) ->
      val parent = position[lhs]
      require(parent != null) { "Counting order omits grammar nonterminal '$lhs'" }
      seenNonterminals += lhs
      require(rhs.size in 0..2) {
        "Expected binary normal form: $lhs -> ${rhs.joinToString(" ")}"
      }
      if (rhs.size == 2) require(rhs.all(position::containsKey)) {
        "Binary productions must contain only variables: $lhs -> ${rhs.joinToString(" ")}"
      }
      rhs.forEach { symbol -> position[symbol]?.let { child ->
        require(child < parent) {
          "Counting order must place child '$symbol' before parent '$lhs'"
        }
      } }
      rhs.filterTo(terminals) { it !in position }
      if (rhs.size != 1 || rhs[0] in position) nonterminalProductions++
      groupedRules.getOrPut(lhs) { mutableListOf() } += lhs to rhs
    }
    require(seenNonterminals.size == stableOrder.size) {
      "Counting order contains unknown symbol(s): " +
        (position.keys - seenNonterminals).joinToString()
    }
    return SuppliedGrammarIndex(
      countingOrder = stableOrder,
      nonterminals = position.keys,
      nonterminalIndex = position,
      rules = groupedRules,
      preindexed = null,
      structuralStats =
        "CFG(|Σ|=${terminals.size}, |V|=${stableOrder.size}, |P|=$nonterminalProductions)"
    )
  }

  private fun requireNormalForm(lhs: String, rhs: List<String>) {
    require(rhs.size in 0..2) { "Expected binary normal form: $lhs -> ${rhs.joinToString(" ")}" }
    if (rhs.size == 2) require(rhs.all { it in nonterminals }) {
      "Binary productions must contain only variables: $lhs -> ${rhs.joinToString(" ")}"
    }
  }

  /** Returns a child-before-parent order suitable for bottom-up derivation counting. */
  private fun requireAcyclic(): List<String> {
    // Residual cursor grammars contain thousands of short-lived variables. Dense integer indexes
    // avoid building and repeatedly hashing three String-keyed maps merely to validate their DAG.
    // Treat repeated dependencies as parallel DAG edges. Counting and consuming each occurrence
    // is cheaper than hashing them into sets and gives the same topological order constraints.
    val children = Array(nonterminalList.size) { mutableListOf<Int>() }
    val incoming = IntArray(nonterminalList.size)
    grammar.forEach { (lhs, rhs) ->
      requireNormalForm(lhs, rhs)
      val parent = nonterminalIndex.getValue(lhs)
      rhs.forEach { symbol -> nonterminalIndex[symbol]?.let { child ->
        children[parent] += child
        incoming[child]++
      } }
    }
    val queue = ArrayList<Int>(nonterminalList.size)
    incoming.forEachIndexed { index, count -> if (count == 0) queue += index }
    var next = 0
    while (next < queue.size) children[queue[next++]].forEach { child ->
      if (--incoming[child] == 0) queue += child
    }
    require(queue.size == nonterminals.size) {
      "BoundedAcyclicCFG requires an acyclic grammar; cycle includes " +
        nonterminalList.filterIndexed { index, _ -> incoming[index] > 0 }.joinToString()
    }
    return queue.asReversed().map(nonterminalList::get)
  }

  private class RecognitionIndex(
    val spanParents: Map<String, Set<String>>,
    val terminalParents: Map<String, Set<String>>,
    val binaryParents: Map<Pair<String, String>, Set<String>>,
    val nullable: Set<String>
  ) {
    val spanClosure = mutableMapOf<String, Set<String>>()
  }

  /** Builds nullable/CYK indexes only for callers which actually invoke [recognizes]. */
  private fun buildRecognitionIndex(): RecognitionIndex {
    val spanParents = mutableMapOf<String, MutableSet<String>>()
    val terminalParents = mutableMapOf<String, MutableSet<String>>()
    val binaryParents = mutableMapOf<Pair<String, String>, MutableSet<String>>()
    val nullable = linkedSetOf<String>()

    grammar.forEach { (lhs, rhs) -> when {
      rhs.size == 1 && rhs[0] in nonterminals ->
        spanParents.getOrPut(rhs[0]) { linkedSetOf() } += lhs
      rhs.size == 1 -> terminalParents.getOrPut(rhs[0]) { linkedSetOf() } += lhs
      rhs.size == 2 -> binaryParents.getOrPut(rhs[0] to rhs[1]) { linkedSetOf() } += lhs
    } }

    // [countingOrder] is child-before-parent, so an acyclic grammar needs exactly one pass.
    countingOrder.forEach { nonterminal ->
      if (rulesFor(nonterminal).any { (_, rhs) ->
          rhs.isEmpty() || rhs.all { it in nonterminals && it in nullable }
        }) nullable += nonterminal
    }

    grammar.forEach { (lhs, rhs) ->
      if (rhs.size != 2) return@forEach
      if (rhs[0] in nullable) spanParents.getOrPut(rhs[1]) { linkedSetOf() } += lhs
      if (rhs[1] in nullable) spanParents.getOrPut(rhs[0]) { linkedSetOf() } += lhs
    }
    return RecognitionIndex(spanParents, terminalParents, binaryParents, nullable)
  }

  /**
   * Unit closure augmented by binary productions whose other child is nullable. This lets the
   * ordinary nonempty CYK chart account for splits at either edge without storing zero-width cells.
   */
  private fun closure(index: RecognitionIndex, nonterminal: String): Set<String> =
    index.spanClosure.getOrPut(nonterminal) {
      val result = linkedSetOf(nonterminal)
      val queue = mutableListOf(nonterminal)
      var next = 0
      while (next < queue.size)
        index.spanParents[queue[next++]].orEmpty()
        .forEach { parent -> if (result.add(parent)) queue += parent }
      result
    }

  private fun close(index: RecognitionIndex, symbols: Collection<String>): Set<String> =
    buildSet { symbols.forEach { addAll(closure(index, it)) } }

  /** Recognizes one token sequence in the bounded language, including variable-unit closure. */
  fun recognizes(tokens: List<String>): Boolean {
    if (tokens.size > maxLength || startSymbol !in nonterminals) return false
    val index = recognitionIndex
    if (tokens.isEmpty()) return startSymbol in index.nullable
    val chart = Array(tokens.size) { Array<Set<String>>(tokens.size + 1) { emptySet() } }
    tokens.forEachIndexed { position, terminal ->
      chart[position][position + 1] = close(index, index.terminalParents[terminal].orEmpty())
    }
    for (span in 2..tokens.size) for (begin in 0..tokens.size - span) {
      val end = begin + span
      val generated = linkedSetOf<String>()
      for (split in begin + 1 until end) chart[begin][split].forEach { left ->
        chart[split][end].forEach { right ->
          index.binaryParents[left to right]?.let(generated::addAll)
        }
      }
      chart[begin][end] = close(index, generated)
    }
    return startSymbol in chart[0][tokens.size]
  }

  private class CountTable(val values: Array<Array<ExactCount>>)

  private class CountVector(
    val values: Array<ExactCount>,
    val firstNonzero: Int,
    val lastNonzero: Int
  )

  private data class StructuralPair(val left: Int, val right: Int)

  /** A name-independent equation for one bounded count vector. */
  private data class StructuralCountEquation(
    val epsilonRules: Int,
    val terminalRules: Int,
    val unitChildren: List<Int>,
    val binaryChildren: List<StructuralPair>
  ) {
    val weight: Int
      get() = 4 + unitChildren.size + binaryChildren.size * 2
  }

  private class SharedCountVector(
    val structuralId: Int,
    val countVectorId: Int,
    val maxLength: Int,
    val vector: CountVector,
    val weight: Int,
    val vectorKey: CountVectorKey?
  )

  /** Content key whose exact-integer hash is computed only when a vector enters the LRU. */
  private class CountVectorKey(private val values: Array<ExactCount>) {
    private val cachedHashCode = values.fold(1) { hash, value ->
      31 * hash + exactCountHash(value)
    }

    override fun hashCode(): Int = cachedHashCode
    override fun equals(other: Any?): Boolean =
      other is CountVectorKey && cachedHashCode == other.cachedHashCode &&
        values.size == other.values.size && values.indices.all { index ->
          exactCountEquals(values[index], other.values[index])
        }
  }

  private class CountVectorIdentity(val id: Int, var references: Int)

  /**
   * A small access-ordered cache implemented with common Kotlin collections. Structural IDs are
   * monotonic and never reused, so eviction can cause only a cache miss, never a false hit. Count
   * vector IDs are content-canonical while retained; consumers can compare them in O(1) without
   * rehashing every exact integer whenever a renamed grammar hits the cache.
   */
  private class SharedCountCache(private val maxWeight: Int) {
    private val entries = linkedMapOf<StructuralCountEquation, SharedCountVector>()
    private val countVectorIdentities = mutableMapOf<CountVectorKey, CountVectorIdentity>()
    private var weight = 0
    private var nextStructuralId = 0
    private var nextCountVectorId = 0
    private var hits = 0
    private var misses = 0
    private var evictions = 0

    fun get(equation: StructuralCountEquation, requiredMaxLength: Int): SharedCountVector? {
      val cached = entries.remove(equation)
      if (cached == null) {
        misses++
        return null
      }
      // Remove/reinsert gives LinkedHashMap access ordering on every Kotlin target.
      entries[equation] = cached
      return if (cached.maxLength >= requiredMaxLength) {
        hits++
        cached
      } else {
        misses++
        null
      }
    }

    fun put(
      equation: StructuralCountEquation,
      maxLength: Int,
      vector: CountVector
    ): SharedCountVector {
      val previous = entries.remove(equation)
      if (previous != null) {
        weight -= previous.weight
        if (previous.maxLength >= maxLength) {
          entries[equation] = previous
          weight += previous.weight
          return previous
        }
        releaseCountVector(previous)
      }
      val entryWeight = equation.weight + vector.values.size
      val structuralId = previous?.structuralId ?: run {
        check(nextStructuralId < Int.MAX_VALUE) { "Shared count cache exhausted structural IDs" }
        nextStructuralId++
      }
      if (entryWeight > maxWeight) return SharedCountVector(
        structuralId,
        nextCountVectorId(),
        maxLength,
        vector,
        entryWeight,
        null
      )
      val vectorKey = CountVectorKey(vector.values)
      val identity = countVectorIdentities[vectorKey]?.also { it.references++ } ?: run {
        CountVectorIdentity(nextCountVectorId(), 1).also {
          countVectorIdentities[vectorKey] = it
        }
      }
      val entry = SharedCountVector(
        structuralId,
        identity.id,
        maxLength,
        vector,
        entryWeight,
        vectorKey
      )
      entries[equation] = entry
      weight += entryWeight
      while (weight > maxWeight && entries.isNotEmpty()) {
        val oldest = entries.entries.iterator().next()
        val oldestKey = oldest.key
        val oldestValue = oldest.value
        entries.remove(oldestKey)
        weight -= oldestValue.weight
        releaseCountVector(oldestValue)
        evictions++
      }
      return entry
    }

    private fun nextCountVectorId(): Int {
      check(nextCountVectorId < Int.MAX_VALUE) { "Shared count cache exhausted count-vector IDs" }
      return nextCountVectorId++
    }

    private fun releaseCountVector(entry: SharedCountVector) {
      val key = entry.vectorKey ?: return
      val identity = countVectorIdentities[key] ?: return
      identity.references--
      if (identity.references == 0) countVectorIdentities.remove(key)
    }

    fun clear() {
      entries.clear()
      countVectorIdentities.clear()
      weight = 0
      // IDs can be retained by a BoundedCountWorkspace after the structural entries are cleared.
      // Keep both counters monotonic so a later cache population can never alias those rows.
      hits = 0
      misses = 0
      evictions = 0
    }

    fun stats() = BoundedCountCacheStats(
      entries.size,
      weight,
      maxWeight,
      hits,
      misses,
      evictions
    )
  }

  private data class CountSignature(
    val epsilonRules: Int,
    val terminalRules: Int,
    val unitVectors: List<Int>,
    val binaryVectors: List<Long>
  )

  /**
   * Computes every bounded count bottom-up in dense, integer-indexed vectors. The previous
   * recursive `Map<Pair<String, Int>, BigInteger>` implementation allocated and hashed a pair for
   * every child/split lookup. That work dominates Kotlin/JS on large residual grammars. Support
   * bounds also avoid multiplying the zero entries outside a child's attainable yield lengths.
   */
  private val boundedCounts: CountTable by lazy {
    // A shared-cache hit replaces an entire immutable row. Keep an empty sentinel initially so
    // only genuine misses allocate and initialize a maxLength-sized exact-integer array.
    val values = Array(nonterminalList.size) { emptyArray<ExactCount>() }
    val first = IntArray(nonterminalList.size) { maxLength + 1 }
    val last = IntArray(nonterminalList.size) { -1 }
    val vectorIds = IntArray(nonterminalList.size) { -1 }
    val structuralIds = IntArray(nonterminalList.size) { -1 }
    val convolutions = mutableMapOf<Long, CountVector>()
    val signatureVectors = mutableMapOf<CountSignature, Int>()

    fun accumulate(index: Int, length: Int, contribution: ExactCount) {
      if (contribution.isZero()) return
      val previous = values[index][length]
      values[index][length] =
        if (previous.isZero()) contribution else previous + contribution
      if (length < first[index]) first[index] = length
      if (length > last[index]) last[index] = length
    }

    // After the first residual has populated a workspace, walk only through cache misses reachable
    // from this cursor root. A hit is a complete transitive summary, so its descendants can stay
    // lazy until sampling actually enters that branch.
    val warmWorkspace = workspace?.takeUnless { it.isEmpty }
    val requiredMisses = linkedSetOf<String>()
    val resolvedCounts = linkedSetOf<String>()
    fun requireCount(nonterminal: String) {
      if (!resolvedCounts.add(nonterminal)) return
      val outputIndex = nonterminalIndex.getValue(nonterminal)
      warmWorkspace?.get(nonterminal, maxLength)?.let { cached ->
        values[outputIndex] = cached.values
        last[outputIndex] = minOf(cached.lastNonzero, maxLength)
        first[outputIndex] = if (cached.firstNonzero <= last[outputIndex])
          cached.firstNonzero
        else maxLength + 1
        vectorIds[outputIndex] = cached.countVectorId
        structuralIds[outputIndex] = cached.structuralId
        return
      }
      requiredMisses += nonterminal
      rulesFor(nonterminal).forEach { (_, rhs) ->
        rhs.filter { it in nonterminals }.forEach(::requireCount)
      }
    }
    if (warmWorkspace != null) requireCount(startSymbol)
    val activeCountingOrder = if (warmWorkspace == null) countingOrder
      else countingOrder.filter { it in requiredMisses }

    activeCountingOrder.forEach countNonterminal@ { nonterminal ->
      val outputIndex = nonterminalIndex.getValue(nonterminal)
      var epsilonRules = 0
      var terminalRules = 0
      val unitVectors = mutableListOf<Int>()
      val binaryVectors = mutableListOf<Long>()
      val unitStructures = mutableListOf<Int>()
      val binaryStructures = mutableListOf<StructuralPair>()
      rulesFor(nonterminal).forEach { (_, rhs) -> when {
        rhs.isEmpty() -> epsilonRules++
        rhs.size == 1 && rhs[0] !in nonterminals -> terminalRules++
        rhs.size == 1 -> {
          val childIndex = nonterminalIndex.getValue(rhs[0])
          unitVectors += vectorIds[childIndex]
          unitStructures += structuralIds[childIndex]
        }
        else -> {
          val leftIndex = nonterminalIndex.getValue(rhs[0])
          val rightIndex = nonterminalIndex.getValue(rhs[1])
          val leftId = vectorIds[leftIndex]
          val rightId = vectorIds[rightIndex]
          binaryVectors += (leftId.toLong() shl 32) or (rightId.toLong() and 0xffffffffL)
          binaryStructures += StructuralPair(structuralIds[leftIndex], structuralIds[rightIndex])
        }
      } }
      val signature = CountSignature(
        epsilonRules,
        terminalRules,
        unitVectors.sorted(),
        binaryVectors.sorted()
      )
      val structuralEquation = StructuralCountEquation(
        epsilonRules,
        terminalRules,
        unitStructures.sorted(),
        binaryStructures.sortedWith(compareBy<StructuralPair> { it.left }.thenBy { it.right })
      )
      sharedCountCache.get(structuralEquation, maxLength)?.let { cached ->
          values[outputIndex] = cached.vector.values
          last[outputIndex] = minOf(cached.vector.lastNonzero, maxLength)
          first[outputIndex] = if (cached.vector.firstNonzero <= last[outputIndex])
            cached.vector.firstNonzero
          else maxLength + 1
          vectorIds[outputIndex] = cached.countVectorId
          structuralIds[outputIndex] = cached.structuralId
          workspace?.put(
            nonterminal,
            maxLength,
            values[outputIndex],
            first[outputIndex],
            last[outputIndex],
            vectorIds[outputIndex],
            structuralIds[outputIndex]
          )
          if (signature !in signatureVectors) signatureVectors[signature] = outputIndex
          return@countNonterminal
      }
      signatureVectors[signature]?.let { previousIndex ->
        values[outputIndex] = values[previousIndex]
        first[outputIndex] = first[previousIndex]
        last[outputIndex] = last[previousIndex]
        val cached = sharedCountCache.put(
          structuralEquation,
          maxLength,
          CountVector(values[outputIndex], first[outputIndex], last[outputIndex])
        )
        vectorIds[outputIndex] = cached.countVectorId
        structuralIds[outputIndex] = cached.structuralId
        workspace?.put(
          nonterminal,
          maxLength,
          values[outputIndex],
          first[outputIndex],
          last[outputIndex],
          vectorIds[outputIndex],
          structuralIds[outputIndex]
        )
        return@countNonterminal
      }
      values[outputIndex] = Array(maxLength + 1) { EXACT_COUNT_ZERO }
      rulesFor(nonterminal).forEach { (_, rhs) -> when {
        rhs.isEmpty() -> accumulate(outputIndex, 0, EXACT_COUNT_ONE)
        rhs.size == 1 && rhs[0] !in nonterminals ->
          if (maxLength >= 1) accumulate(outputIndex, 1, EXACT_COUNT_ONE)
        rhs.size == 1 -> {
          val childIndex = nonterminalIndex.getValue(rhs[0])
          if (last[childIndex] >= first[childIndex])
            for (length in first[childIndex]..last[childIndex])
              accumulate(outputIndex, length, values[childIndex][length])
        }
        else -> {
          val leftIndex = nonterminalIndex.getValue(rhs[0])
          val rightIndex = nonterminalIndex.getValue(rhs[1])
          val key = (vectorIds[leftIndex].toLong() shl 32) or
            (vectorIds[rightIndex].toLong() and 0xffffffffL)
          val cached = convolutions[key]
          val convolution = if (cached != null) {
            cached
          } else {
            val product = Array(maxLength + 1) { EXACT_COUNT_ZERO }
            var productFirst = maxLength + 1
            var productLast = -1
            if (last[leftIndex] >= first[leftIndex] && last[rightIndex] >= first[rightIndex]) {
              val lastLeft = minOf(last[leftIndex], maxLength - first[rightIndex])
              if (lastLeft >= first[leftIndex]) for (leftLength in first[leftIndex]..lastLeft) {
                val leftCount = values[leftIndex][leftLength]
                if (leftCount.isZero()) continue
                val lastRight = minOf(last[rightIndex], maxLength - leftLength)
                if (lastRight < first[rightIndex]) continue
                for (rightLength in first[rightIndex]..lastRight) {
                  val rightCount = values[rightIndex][rightLength]
                  if (rightCount.isZero()) continue
                  val contribution = when {
                    leftCount.isOne() -> rightCount
                    rightCount.isOne() -> leftCount
                    else -> leftCount * rightCount
                  }
                  val length = leftLength + rightLength
                  val previous = product[length]
                  product[length] =
                    if (previous.isZero()) contribution else previous + contribution
                  if (length < productFirst) productFirst = length
                  if (length > productLast) productLast = length
                }
              }
            }
            CountVector(product, productFirst, productLast).also { convolutions[key] = it }
          }
          if (convolution.lastNonzero >= convolution.firstNonzero)
            for (length in convolution.firstNonzero..convolution.lastNonzero)
              accumulate(outputIndex, length, convolution.values[length])
        }
      } }
      val cached = sharedCountCache.put(
        structuralEquation,
        maxLength,
        CountVector(values[outputIndex], first[outputIndex], last[outputIndex])
      )
      vectorIds[outputIndex] = cached.countVectorId
      structuralIds[outputIndex] = cached.structuralId
      workspace?.put(
        nonterminal,
        maxLength,
        values[outputIndex],
        first[outputIndex],
        last[outputIndex],
        vectorIds[outputIndex],
        structuralIds[outputIndex]
      )
      signatureVectors[signature] = outputIndex
    }
    CountTable(values)
  }

  /** Number of derivations rooted at [nonterminal] with exactly [length] terminals. */
  fun derivationCount(nonterminal: String, length: Int): BigInteger =
    exactDerivationCount(nonterminal, length).toPublicBigInteger()

  private fun exactDerivationCount(nonterminal: String, length: Int): ExactCount {
    if (length < 0) return EXACT_COUNT_ZERO
    val index = nonterminalIndex[nonterminal] ?: return EXACT_COUNT_ZERO
    if (length <= maxLength) {
      val table = boundedCounts
      if (table.values[index].isEmpty()) {
        val cached = workspace?.get(nonterminal, maxLength)
          ?: error("Missing bounded count row for $nonterminal")
        table.values[index] = cached.values
      }
      return table.values[index][length]
    }

    // Preserve the public API for exact out-of-bound queries without making the common bounded
    // table as large as the greatest length a caller might ask for.
    return overflowCounts.getOrPut(nonterminal to length) {
      rulesFor(nonterminal).fold(EXACT_COUNT_ZERO) { total, (_, rhs) ->
        total + when {
          rhs.isEmpty() -> EXACT_COUNT_ZERO
          rhs.size == 1 && rhs[0] !in nonterminals -> EXACT_COUNT_ZERO
          rhs.size == 1 -> exactDerivationCount(rhs[0], length)
          else -> (0..length).fold(EXACT_COUNT_ZERO) { subtotal, split ->
            subtotal + exactDerivationCount(rhs[0], split) *
              exactDerivationCount(rhs[1], length - split)
          }
        }
      }
    }
  }

  private val lengthCounts: List<ExactCount> by lazy {
    (0..maxLength).map { exactDerivationCount(startSymbol, it) }
  }

  private val cumulativeLengthCounts: List<ExactCount> by lazy {
    var cumulative = EXACT_COUNT_ZERO
    lengthCounts.map { count ->
      cumulative += count
      cumulative
    }
  }

  private val exactTotalDerivationCount: ExactCount by lazy { cumulativeLengthCounts.last() }
  private val exactTotalBitLength: Int by lazy { exactCountBitLength(exactTotalDerivationCount) }

  /** Total derivations whose terminal yield has length `0..maxLength`. */
  val derivationCount: BigInteger by lazy { exactTotalDerivationCount.toPublicBigInteger() }

  val isEmpty: Boolean get() = exactTotalDerivationCount.isZero()

  /**
   * A grammar-local decoding interval. Workspace entries retain symbol spellings because one
   * workspace is shared by several residual grammar views whose integer indexes need not agree.
   * Resolve those spellings once on entry to this grammar: decoding then stays on dense integer
   * indexes instead of hashing one to three strings at every node of every sampled derivation.
   */
  private class CompiledDecodingChoice(
    val cumulativeWeight: ExactCount,
    val split: Int,
    val rightCount: ExactCount,
    val terminal: String? = null,
    val leftChild: Int = -1,
    val rightChild: Int = -1
  )

  private val decodingChoices = mutableMapOf<Long, List<CompiledDecodingChoice>>()

  private fun decodingKey(nonterminalIndex: Int, length: Int): Long =
    (nonterminalIndex.toLong() shl 32) or (length.toLong() and 0xffffffffL)

  /** Materializes stable rule/split intervals once, then reuses them across uniform samples. */
  private fun compileChoices(
    choices: List<BoundedDecodingChoice>
  ): List<CompiledDecodingChoice> = choices.map { choice ->
    val rhs = choice.rhs
    when {
      rhs.isEmpty() -> CompiledDecodingChoice(
        choice.cumulativeWeight, choice.split, choice.rightCount
      )
      rhs.size == 1 && rhs[0] !in nonterminals -> CompiledDecodingChoice(
        choice.cumulativeWeight,
        choice.split,
        choice.rightCount,
        terminal = rhs[0]
      )
      rhs.size == 1 -> CompiledDecodingChoice(
        choice.cumulativeWeight,
        choice.split,
        choice.rightCount,
        leftChild = nonterminalIndex.getValue(rhs[0])
      )
      else -> CompiledDecodingChoice(
        choice.cumulativeWeight,
        choice.split,
        choice.rightCount,
        leftChild = nonterminalIndex.getValue(rhs[0]),
        rightChild = nonterminalIndex.getValue(rhs[1])
      )
    }
  }

  private fun choices(nonterminalIndex: Int, length: Int): List<CompiledDecodingChoice> {
    val key = decodingKey(nonterminalIndex, length)
    decodingChoices[key]?.let { return it }
    val nonterminal = nonterminalList[nonterminalIndex]
    workspace?.getDecodingChoices(nonterminal, length)?.let { cached ->
      return compileChoices(cached).also { decodingChoices[key] = it }
    }
    val choices = mutableListOf<BoundedDecodingChoice>()
    var cumulative = EXACT_COUNT_ZERO
    rulesFor(nonterminal).forEach { (_, rhs) -> when {
      rhs.isEmpty() && length == 0 -> {
        cumulative += EXACT_COUNT_ONE
        choices += BoundedDecodingChoice(rhs, -1, cumulative)
      }
      rhs.size == 1 && rhs[0] !in nonterminals && length == 1 -> {
        cumulative += EXACT_COUNT_ONE
        choices += BoundedDecodingChoice(rhs, -1, cumulative)
      }
      rhs.size == 1 && rhs[0] in nonterminals -> {
        val weight = exactDerivationCount(rhs[0], length)
        if (!weight.isZero()) {
          cumulative += weight
          choices += BoundedDecodingChoice(rhs, -1, cumulative)
        }
      }
      rhs.size == 2 -> for (split in 0..length) {
        val leftCount = exactDerivationCount(rhs[0], split)
        if (leftCount.isZero()) continue
        val rightCount = exactDerivationCount(rhs[1], length - split)
        if (rightCount.isZero()) continue
        cumulative += leftCount * rightCount
        choices += BoundedDecodingChoice(rhs, split, cumulative, rightCount)
      }
    } }
    check(exactCountEquals(cumulative, exactDerivationCount(nonterminal, length))) {
      "Decoding intervals do not match the derivation count"
    }
    val materialized = choices.toList()
    val cached = workspace?.putDecodingChoices(nonterminal, length, materialized) ?: materialized
    return compileChoices(cached).also { decodingChoices[key] = it }
  }

  /** Decodes [index] in the stable length/rule/split ordering of the bounded derivation forest. */
  fun sample(index: BigInteger): List<String> {
    val exactIndex = index.toExactCount()
    require(index >= BigInteger.ZERO && exactIndex < exactTotalDerivationCount) {
      "Derivation index $index is outside [0, $derivationCount)"
    }
    return sampleExact(exactIndex)
  }

  private fun sampleExact(index: ExactCount): List<String> {
    var low = 0
    var high = cumulativeLengthCounts.size
    while (low < high) {
      val middle = (low + high) ushr 1
      if (index < cumulativeLengthCounts[middle]) high = middle else low = middle + 1
    }
    check(low < cumulativeLengthCounts.size) { "Unreachable derivation index" }
    val intervalStart = if (low == 0) EXACT_COUNT_ZERO else cumulativeLengthCounts[low - 1]
    return decode(startSymbol, low, index - intervalStart)
  }

  /**
   * Decodes [index] among only the derivations whose terminal yield has exactly [length] tokens.
   * Ambiguous derivations retain separate indexes even when their terminal yields are identical.
   */
  fun sampleAtLength(length: Int, index: BigInteger): List<String> {
    require(length in 0..maxLength) {
      "Yield length $length is outside [0, $maxLength]"
    }
    val count = exactDerivationCount(startSymbol, length)
    val exactIndex = index.toExactCount()
    require(index >= BigInteger.ZERO && exactIndex < count) {
      "Derivation index $index is outside [0, ${count.toPublicBigInteger()}) at length $length"
    }
    return decode(startSymbol, length, exactIndex)
  }

  /** Uniformly samples one derivation with exactly [length] terminal tokens. */
  fun sampleAtLength(length: Int, random: Random = Random.Default): List<String> {
    require(length in 0..maxLength) {
      "Yield length $length is outside [0, $maxLength]"
    }
    val count = exactDerivationCount(startSymbol, length)
    check(!count.isZero()) { "Cannot sample an empty length-$length language" }
    return decode(startSymbol, length, random.nextExactCount(count, exactCountBitLength(count)))
  }

  /** Uniformly samples one of the length-bounded derivations. */
  fun sample(random: Random = Random.Default): List<String> {
    check(!isEmpty) { "Cannot sample an empty bounded language" }
    return sampleExact(random.nextExactCount(exactTotalDerivationCount, exactTotalBitLength))
  }

  /** Infinite uniform-with-replacement stream over the length-bounded derivations. */
  fun samples(random: Random = Random.Default): Sequence<List<String>> =
    generateSequence { sample(random) }

  /**
   * Samples the shortest derivations first, without enumerating their terminal yields.
   *
   * Each nonempty exact-length slice contributes at most [samplesPerLength] independent,
   * uniform-with-replacement draws. Empty lengths are skipped, and sampling stops once
   * [sampleLimit] results have been emitted. Thus the default visits at most the first ten
   * nonempty lengths and draws at most 100 derivations in total.
   */
  fun samplesByIncreasingLength(
    random: Random = Random.Default,
    sampleLimit: Int = 100,
    samplesPerLength: Int = 10
  ): Sequence<BoundedLengthSample> {
    require(sampleLimit >= 0) { "Sample limit must be nonnegative" }
    require(samplesPerLength in 1..10) { "Samples per length must be in 1..10" }
    return sequence {
      var remaining = sampleLimit
      for (length in 0..maxLength) {
        if (remaining == 0) break
        val count = exactDerivationCount(startSymbol, length)
        if (count.isZero()) continue
        val bitLength = exactCountBitLength(count)
        val draws = minOf(samplesPerLength, remaining)
        repeat(draws) {
          val rank = random.nextExactCount(count, bitLength)
          yield(BoundedLengthSample(length, decode(startSymbol, length, rank)))
        }
        remaining -= draws
      }
    }
  }

  /**
   * Finds the first attainable yield length. Overlapping residual grammars share nearly all of
   * their descendants, so a workspace-backed traversal stops as soon as it reaches a previously
   * summarized symbol instead of rescanning every production in every cursor grammar.
   */
  private fun minimumRow(): BoundedMinimumRow {
    val cache = workspace
    if (cache != null) {
      val pending = mutableListOf(startSymbol to false)
      while (pending.isNotEmpty()) {
        val (nonterminal, expanded) = pending.removeAt(pending.lastIndex)
        if (cache.getMinimumRow(nonterminal) != null) continue
        if (!expanded) {
          pending += nonterminal to true
          rulesFor(nonterminal).asReversed().forEach { (_, rhs) ->
            rhs.asReversed().forEach { child ->
              if (child in nonterminals && cache.getMinimumRow(child) == null)
                pending += child to false
            }
          }
          continue
        }
        var minimum = Int.MAX_VALUE
        var shortestCount = EXACT_COUNT_ZERO
        rulesFor(nonterminal).forEach { (_, rhs) ->
          var candidate = Int.MAX_VALUE
          var count = EXACT_COUNT_ZERO
          when {
            rhs.isEmpty() -> {
              candidate = 0
              count = EXACT_COUNT_ONE
            }
            rhs.size == 1 && rhs[0] !in nonterminals -> {
              candidate = 1
              count = EXACT_COUNT_ONE
            }
            rhs.size == 1 -> {
              val child = cache.getMinimumRow(rhs[0])
                ?: error("Missing minimum-yield row for ${rhs[0]}")
              candidate = child.yield
              count = child.count
            }
            else -> {
              val left = cache.getMinimumRow(rhs[0])
                ?: error("Missing minimum-yield row for ${rhs[0]}")
              val right = cache.getMinimumRow(rhs[1])
                ?: error("Missing minimum-yield row for ${rhs[1]}")
              candidate = if (left.yield == Int.MAX_VALUE || right.yield == Int.MAX_VALUE)
                Int.MAX_VALUE
              else if (left.yield > Int.MAX_VALUE - right.yield) Int.MAX_VALUE
              else left.yield + right.yield
              count = if (candidate == Int.MAX_VALUE) EXACT_COUNT_ZERO else left.count * right.count
            }
          }
          when {
            candidate < minimum -> {
              minimum = candidate
              shortestCount = count
            }
            candidate == minimum -> shortestCount += count
          }
        }
        cache.putMinimumRow(nonterminal, BoundedMinimumRow(minimum, shortestCount))
      }
      return cache.getMinimumRow(startSymbol)
        ?: error("Missing minimum-yield row for $startSymbol")
    }

    val minimumYields = IntArray(nonterminalList.size) { Int.MAX_VALUE }
    val shortestCounts = Array(nonterminalList.size) { EXACT_COUNT_ZERO }
    countingOrder.forEach { nonterminal ->
      val index = nonterminalIndex.getValue(nonterminal)
      rulesFor(nonterminal).forEach { (_, rhs) ->
        var candidate = Int.MAX_VALUE
        var count = EXACT_COUNT_ZERO
        when {
          rhs.isEmpty() -> {
            candidate = 0
            count = EXACT_COUNT_ONE
          }
          rhs.size == 1 && rhs[0] !in nonterminals -> {
            candidate = 1
            count = EXACT_COUNT_ONE
          }
          rhs.size == 1 -> {
            val child = nonterminalIndex.getValue(rhs[0])
            candidate = minimumYields[child]
            count = shortestCounts[child]
          }
          else -> {
            val leftIndex = nonterminalIndex.getValue(rhs[0])
            val rightIndex = nonterminalIndex.getValue(rhs[1])
            val left = minimumYields[leftIndex]
            val right = minimumYields[rightIndex]
            candidate = if (left == Int.MAX_VALUE || right == Int.MAX_VALUE) Int.MAX_VALUE
            else if (left > Int.MAX_VALUE - right) Int.MAX_VALUE else left + right
            count = if (candidate == Int.MAX_VALUE) EXACT_COUNT_ZERO
              else shortestCounts[leftIndex] * shortestCounts[rightIndex]
          }
        }
        when {
          candidate < minimumYields[index] -> {
            minimumYields[index] = candidate
            shortestCounts[index] = count
          }
          candidate == minimumYields[index] -> shortestCounts[index] += count
        }
      }
      localMinimumRows[nonterminal] = BoundedMinimumRow(
        minimumYields[index], shortestCounts[index]
      )
    }
    return localMinimumRows.getValue(startSymbol)
  }

  private fun cachedMinimumRow(nonterminal: String): BoundedMinimumRow =
    workspace?.getMinimumRow(nonterminal) ?: localMinimumRows.getValue(nonterminal)

  /** Decodes a rank in the shortest derivation slice without constructing all bounded vectors. */
  private fun decodeMinimum(
    nonterminal: String,
    initialIndex: ExactCount,
    output: MutableList<String>
  ) {
    val target = cachedMinimumRow(nonterminal)
    var index = initialIndex
    rulesFor(nonterminal).forEach { (_, rhs) ->
      var leftRow: BoundedMinimumRow? = null
      var rightRow: BoundedMinimumRow? = null
      val (candidate, weight) = when {
        rhs.isEmpty() -> 0 to EXACT_COUNT_ONE
        rhs.size == 1 && rhs[0] !in nonterminals -> 1 to EXACT_COUNT_ONE
        rhs.size == 1 -> cachedMinimumRow(rhs[0]).let { it.yield to it.count }
        else -> {
          leftRow = cachedMinimumRow(rhs[0])
          rightRow = cachedMinimumRow(rhs[1])
          val left = requireNotNull(leftRow)
          val right = requireNotNull(rightRow)
          val yield = if (left.yield == Int.MAX_VALUE || right.yield == Int.MAX_VALUE)
            Int.MAX_VALUE
          else if (left.yield > Int.MAX_VALUE - right.yield) Int.MAX_VALUE
          else left.yield + right.yield
          yield to if (yield == Int.MAX_VALUE) EXACT_COUNT_ZERO else left.count * right.count
        }
      }
      if (candidate != target.yield) return@forEach
      if (index >= weight) {
        index -= weight
        return@forEach
      }
      when {
        rhs.isEmpty() -> Unit
        rhs.size == 1 && rhs[0] !in nonterminals -> output += rhs[0]
        rhs.size == 1 -> decodeMinimum(rhs[0], index, output)
        else -> {
          val rightCount = requireNotNull(rightRow).count
          val (leftIndex, rightIndex) = index.divrem(rightCount)
          decodeMinimum(rhs[0], leftIndex, output)
          decodeMinimum(rhs[1], rightIndex, output)
        }
      }
      return
    }
    error("Invalid shortest-derivation index for $nonterminal")
  }

  /**
   * Enumerates at most [limit] distinct terminal yields from a nonterminal's minimum-length row.
   *
   * This walks the shared derivation DAG and caps every memoized sublanguage at [limit], rather
   * than repeatedly drawing derivations and hoping that ambiguity does not decode to the same
   * terminal sequence. A random cyclic offset changes only the order of equally short choices;
   * the same seeded [random] therefore remains reproducible without allocating shuffled copies of
   * the (occasionally very large) production lists.
   */
  private fun distinctMinimumTerminals(
    root: String,
    limit: Int,
    random: Random
  ): List<List<String>> {
    if (limit == 0) return emptyList()
    val memo = mutableMapOf<String, List<List<String>>>()
    lateinit var collect: (String) -> List<List<String>>
    collect = { nonterminal ->
      memo[nonterminal] ?: run {
        val target = cachedMinimumRow(nonterminal)
        val terminals = linkedSetOf<List<String>>()
        val rules = rulesFor(nonterminal)
        val offset = if (rules.isEmpty()) 0 else random.nextInt(rules.size)
        var ruleOrdinal = 0
        while (ruleOrdinal < rules.size && terminals.size < limit) {
          val (_, rhs) = rules[(offset + ruleOrdinal) % rules.size]
          ruleOrdinal++
          val candidateLength = when {
            rhs.isEmpty() -> 0
            rhs.size == 1 && rhs[0] !in nonterminals -> 1
            rhs.size == 1 -> cachedMinimumRow(rhs[0]).yield
            else -> {
              check(rhs.size == 2) { "Bounded grammar is not in binary normal form" }
              val left = cachedMinimumRow(rhs[0]).yield
              val right = cachedMinimumRow(rhs[1]).yield
              if (left == Int.MAX_VALUE || right == Int.MAX_VALUE || left > Int.MAX_VALUE - right)
                Int.MAX_VALUE
              else left + right
            }
          }
          if (candidateLength != target.yield) continue

          when {
            rhs.isEmpty() -> terminals.add(emptyList())
            rhs.size == 1 && rhs[0] !in nonterminals -> terminals += listOf(rhs[0])
            rhs.size == 1 -> for (candidate in collect(rhs[0])) {
              terminals += candidate
              if (terminals.size == limit) break
            }
            else -> {
              val left = collect(rhs[0])
              val right = collect(rhs[1])
              if (left.isNotEmpty() && right.isNotEmpty()) {
                val leftOffset = random.nextInt(left.size)
                val rightOffset = random.nextInt(right.size)
                var leftOrdinal = 0
                while (leftOrdinal < left.size && terminals.size < limit) {
                  val leftTokens = left[(leftOffset + leftOrdinal) % left.size]
                  var rightOrdinal = 0
                  while (rightOrdinal < right.size && terminals.size < limit) {
                    val rightTokens = right[(rightOffset + rightOrdinal) % right.size]
                    terminals += leftTokens + rightTokens
                    rightOrdinal++
                  }
                  leftOrdinal++
                }
              }
            }
          }
        }
        terminals.toList().also { memo[nonterminal] = it }
      }
    }
    return collect(root)
  }

  /** Enumerates a capped set of distinct terminal yields from one exact-length slice. */
  private fun distinctTerminalsAtLength(
    root: String,
    length: Int,
    limit: Int,
    random: Random
  ): List<List<String>> {
    if (limit == 0 || exactDerivationCount(root, length).isZero()) return emptyList()
    val memo = mutableMapOf<Long, List<List<String>>>()
    lateinit var collect: (String, Int) -> List<List<String>>
    collect = { nonterminal, targetLength ->
      val nonterminalId = nonterminalIndex.getValue(nonterminal)
      val key = (nonterminalId.toLong() shl 32) or (targetLength.toLong() and 0xffffffffL)
      memo[key] ?: run {
        val terminals = linkedSetOf<List<String>>()
        val rules = rulesFor(nonterminal)
        val offset = if (rules.isEmpty()) 0 else random.nextInt(rules.size)
        var ruleOrdinal = 0
        while (ruleOrdinal < rules.size && terminals.size < limit) {
          val (_, rhs) = rules[(offset + ruleOrdinal) % rules.size]
          ruleOrdinal++
          when {
            rhs.isEmpty() -> if (targetLength == 0) terminals.add(emptyList())
            rhs.size == 1 && rhs[0] !in nonterminals ->
              if (targetLength == 1) terminals += listOf(rhs[0])
            rhs.size == 1 -> {
              if (exactDerivationCount(rhs[0], targetLength).isZero()) continue
              for (candidate in collect(rhs[0], targetLength)) {
                terminals += candidate
                if (terminals.size == limit) break
              }
            }
            else -> {
              check(rhs.size == 2) { "Bounded grammar is not in binary normal form" }
              val splitOffset = random.nextInt(targetLength + 1)
              var splitOrdinal = 0
              while (splitOrdinal <= targetLength && terminals.size < limit) {
                val split = (splitOffset + splitOrdinal) % (targetLength + 1)
                splitOrdinal++
                if (
                  exactDerivationCount(rhs[0], split).isZero() ||
                  exactDerivationCount(rhs[1], targetLength - split).isZero()
                ) continue
                val left = collect(rhs[0], split)
                val right = collect(rhs[1], targetLength - split)
                if (left.isEmpty() || right.isEmpty()) continue
                val leftOffset = random.nextInt(left.size)
                val rightOffset = random.nextInt(right.size)
                var leftOrdinal = 0
                while (leftOrdinal < left.size && terminals.size < limit) {
                  val leftTokens = left[(leftOffset + leftOrdinal) % left.size]
                  var rightOrdinal = 0
                  while (rightOrdinal < right.size && terminals.size < limit) {
                    val rightTokens = right[(rightOffset + rightOrdinal) % right.size]
                    terminals += leftTokens + rightTokens
                    rightOrdinal++
                  }
                  leftOrdinal++
                }
              }
            }
          }
        }
        terminals.toList().also { memo[key] = it }
      }
    }
    return collect(root, length)
  }

  /**
   * Returns distinct terminal sequences in increasing exact-yield length.
   *
   * Unlike [shortestSampleBatch], ambiguity cannot consume the result cap: equivalent derivations
   * are collapsed before they are returned. The minimum row retains its count-only fast path. If
   * it contains fewer than [sampleLimit] unique yields, progressively wider bounded views inspect
   * subsequent lengths until the cap or [maxLength] is reached. Per-node and final languages are
   * both capped, bounding enumeration work by the requested result count.
   */
  fun shortestDistinctSampleBatch(
    random: Random = Random.Default,
    sampleLimit: Int = 10
  ): BoundedLengthSampleBatch {
    require(sampleLimit >= 0) { "Sample limit must be nonnegative" }
    if (sampleLimit == 0 || grammar.isEmpty()) return BoundedLengthSampleBatch(
      samples = emptyList(),
      inspectedDerivationCount = BigInteger.ZERO,
      inspectedLengths = IntRange.EMPTY,
      coversFullBound = grammar.isEmpty()
    )

    val minimumRow = minimumRow()
    val minimum = minimumRow.yield
    if (minimum == Int.MAX_VALUE || minimum > maxLength) return BoundedLengthSampleBatch(
      samples = emptyList(),
      inspectedDerivationCount = BigInteger.ZERO,
      inspectedLengths = 0..maxLength,
      coversFullBound = true
    )

    val samples = distinctMinimumTerminals(startSymbol, sampleLimit, random)
      .mapTo(mutableListOf()) { BoundedLengthSample(minimum, it) }
    var inspectedCount = minimumRow.count.toPublicBigInteger()
    var inspectedMaximum = minimum

    // Two new exact lengths catch the common "one unique global minimum" case in one count view.
    var window = 2
    while (samples.size < sampleLimit && inspectedMaximum < maxLength) {
      val nextMaximum = minOf(maxLength, minimum + window)
      val view = if (nextMaximum == maxLength) this else rebound(nextMaximum)
      var lastInspected = inspectedMaximum
      for (length in inspectedMaximum + 1..nextMaximum) {
        val count = view.exactDerivationCount(startSymbol, length)
        inspectedCount += count.toPublicBigInteger()
        lastInspected = length
        if (count.isZero()) continue
        view.distinctTerminalsAtLength(
          startSymbol,
          length,
          sampleLimit - samples.size,
          random
        ).forEach { terminals -> samples += BoundedLengthSample(length, terminals) }
        if (samples.size == sampleLimit) break
      }
      inspectedMaximum = lastInspected
      if (samples.size == sampleLimit || inspectedMaximum == maxLength) break
      window = minOf(maxLength - minimum, window * 2)
    }

    return BoundedLengthSampleBatch(
      samples = samples,
      inspectedDerivationCount = inspectedCount,
      inspectedLengths = 0..inspectedMaximum,
      coversFullBound = inspectedMaximum == maxLength
    )
  }

  /**
   * Materializes a bounded shortest-first batch without forcing counts through [maxLength].
   *
   * The first view reaches only the minimum yield plus the number of requested length slices.
   * If that interval contains gaps, progressively wider views are used until enough nonempty
   * lengths are found. Existing full-count properties remain lazy and retain their original
   * semantics; [inspectedDerivationCount] explicitly covers only [inspectedLengths].
   */
  fun shortestSampleBatch(
    random: Random = Random.Default,
    sampleLimit: Int = 100,
    samplesPerLength: Int = 10
  ): BoundedLengthSampleBatch {
    require(sampleLimit >= 0) { "Sample limit must be nonnegative" }
    require(samplesPerLength in 1..10) { "Samples per length must be in 1..10" }
    if (sampleLimit == 0 || grammar.isEmpty()) return BoundedLengthSampleBatch(
      samples = emptyList(),
      inspectedDerivationCount = BigInteger.ZERO,
      inspectedLengths = IntRange.EMPTY,
      coversFullBound = grammar.isEmpty()
    )

    val minimumRow = minimumRow()
    val minimum = minimumRow.yield
    if (minimum == Int.MAX_VALUE || minimum > maxLength) return BoundedLengthSampleBatch(
      samples = emptyList(),
      inspectedDerivationCount = BigInteger.ZERO,
      inspectedLengths = 0..maxLength,
      coversFullBound = true
    )

    val requestedLengths = (sampleLimit - 1) / samplesPerLength + 1
    if (requestedLengths == 1) {
      val bitLength = exactCountBitLength(minimumRow.count)
      val samples = List(sampleLimit) {
        val terminals = ArrayList<String>(minimum)
        decodeMinimum(
          startSymbol,
          random.nextExactCount(minimumRow.count, bitLength),
          terminals
        )
        check(terminals.size == minimum) {
          "Decoded ${terminals.size} terminals for a shortest derivation of length $minimum"
        }
        BoundedLengthSample(minimum, terminals)
      }
      return BoundedLengthSampleBatch(
        samples = samples,
        inspectedDerivationCount = minimumRow.count.toPublicBigInteger(),
        inspectedLengths = 0..minimum,
        coversFullBound = minimum == maxLength
      )
    }
    var inspectedMaximum = minOf(
      maxLength.toLong(),
      minimum.toLong() + requestedLengths - 1L
    ).toInt()
    while (true) {
      val view = if (inspectedMaximum == maxLength) this else rebound(inspectedMaximum)
      val counts = (0..inspectedMaximum).map { length ->
        length to view.exactDerivationCount(startSymbol, length)
      }
      val nonempty = counts.filterNot { (_, count) -> count.isZero() }
      if (nonempty.size >= requestedLengths || inspectedMaximum == maxLength) {
        var remaining = sampleLimit
        val samples = buildList {
          nonempty.forEach { (length, count) ->
            if (remaining == 0) return@forEach
            val draws = minOf(samplesPerLength, remaining)
            val bitLength = exactCountBitLength(count)
            repeat(draws) {
              val rank = random.nextExactCount(count, bitLength)
              add(BoundedLengthSample(length, view.decode(startSymbol, length, rank)))
            }
            remaining -= draws
          }
        }
        val inspectedCount = counts.fold(EXACT_COUNT_ZERO) { total, (_, count) ->
          total + count
        }
        return BoundedLengthSampleBatch(
          samples = samples,
          inspectedDerivationCount = inspectedCount.toPublicBigInteger(),
          inspectedLengths = 0..inspectedMaximum,
          coversFullBound = inspectedMaximum == maxLength
        )
      }
      val inspectedWidth = inspectedMaximum.toLong() - minimum + 1L
      inspectedMaximum = minOf(
        maxLength.toLong(),
        minimum.toLong() + inspectedWidth * 2L - 1L
      ).toInt()
    }
  }

  private fun decode(nonterminal: String, length: Int, initialIndex: ExactCount): List<String> {
    val output = ArrayList<String>(length)
    decodeInto(nonterminalIndex.getValue(nonterminal), length, initialIndex, output)
    check(output.size == length) {
      "Decoded ${output.size} terminals for a derivation of length $length"
    }
    return output
  }

  private fun decodeInto(
    nonterminalIndex: Int,
    length: Int,
    index: ExactCount,
    output: MutableList<String>
  ) {
    val choices = choices(nonterminalIndex, length)
    var low = 0
    var high = choices.size
    while (low < high) {
      val middle = (low + high) ushr 1
      if (index < choices[middle].cumulativeWeight) high = middle else low = middle + 1
    }
    check(low < choices.size) {
      "Invalid derivation index for ${nonterminalList[nonterminalIndex]}/$length"
    }
    val choice = choices[low]
    val intervalStart = if (low == 0) EXACT_COUNT_ZERO else choices[low - 1].cumulativeWeight
    val localIndex = index - intervalStart
    when {
      choice.terminal != null -> output += choice.terminal
      choice.leftChild < 0 -> Unit
      choice.rightChild < 0 -> decodeInto(choice.leftChild, length, localIndex, output)
      else -> {
        // Keep quotient and remainder as locals. In Kotlin/JS a destructured Pair here allocates an
        // object at every binary node, which is a substantial fraction of a 100-sample cursor.
        val leftIndex = localIndex / choice.rightCount
        val rightIndex = localIndex % choice.rightCount
        decodeInto(choice.leftChild, choice.split, leftIndex, output)
        decodeInto(
          choice.rightChild,
          length - choice.split,
          rightIndex,
          output
        )
      }
    }
  }

  /** Shared parse forest for the complete finite grammar (without the length bound). */
  val forest: PTree? by lazy {
    if (startSymbol !in nonterminals) null
    else {
      val memo = mutableMapOf<String, PTree>()
      fun tree(nonterminal: String): PTree = memo[nonterminal] ?: PTree(
        nonterminal,
        rulesFor(nonterminal).map { (_, rhs) ->
          fun child(symbol: String): PTree =
            if (symbol in nonterminals) tree(symbol) else PTree(symbol)
          when (rhs.size) {
            0 -> PTree() to PTree()
            1 -> child(rhs[0]) to PTree()
            else -> child(rhs[0]) to child(rhs[1])
          }
        }
      ).also { memo[nonterminal] = it }
      tree(startSymbol)
    }
  }
}

fun CFG.boundedAcyclic(
  maxLength: Int,
  startSymbol: String = START_SYMBOL,
  workspace: BoundedCountWorkspace? = null,
  countingOrder: List<String>? = null
) = BoundedAcyclicCFG(this, maxLength, startSymbol, workspace, countingOrder)
