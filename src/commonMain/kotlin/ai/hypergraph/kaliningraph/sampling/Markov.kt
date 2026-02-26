package ai.hypergraph.kaliningraph.sampling

import kotlin.math.ln

class FastMC<T> private constructor(
  val memory: Int,
  val vocabularySize: Int, // observed vocab + OOV
  private val tokenToId: Map<T, Int>,
  private val padId: Int,
  private val oovId: Int,
  private val base: Int,
  private val contextLen: Int,
  private val dropFactor: Long,
  private val startContextKey: Long,
  private val prefixIds: IntArray,
  private val suffixIds: IntArray,
  private val rowByContext: LongIntTable,
  private val rows: Array<Row>,
  private val unseenDefaultCost: Double,
) {
  /**
   * Opaque cached context state.
   *
   * value = ln(count(context) + V)
   * rowIdx = -1 iff the context was unseen during training
   */
  class ContextBase internal constructor(
    internal val ctxKey: Long,
    internal val rowIdx: Int,
    val value: Double,
  )

  fun encode(token: T?): Int = tokenToId[token] ?: oovId

  fun encode(seq: Iterable<T>): IntArray {
    val tmp = ArrayList<Int>()
    for (t in seq) tmp.add(encode(t))
    return tmp.toIntArray()
  }

  /**
   * Fast average NLL over [seq], normalized by seq length.
   */
  fun score(seq: Iterable<T>): Double = scoreEncoded(encode(seq))
  fun score(seq: List<T>): Double = scoreEncoded(encode(seq))

  fun scoreEncoded(seq: IntArray): Double {
    val norm = if (seq.isEmpty()) 1 else seq.size
    var total = 0.0
    var base = contextBaseFromKey(startContextKey)

    for (i in prefixIds.indices) {
      val tok = prefixIds[i]
      total += scoreTransitionFromBaseEncoded(base, tok)
      base = advanceEncoded(base, tok)
    }

    for (i in seq.indices) {
      val tok = seq[i]
      total += scoreTransitionFromBaseEncoded(base, tok)
      base = advanceEncoded(base, tok)
    }

    for (i in suffixIds.indices) {
      val tok = suffixIds[i]
      total += scoreTransitionFromBaseEncoded(base, tok)
      base = advanceEncoded(base, tok)
    }

    return total / norm.toDouble()
  }

  /**
   * Build a cached context handle from a raw token context.
   *
   * If [context] is shorter than memory-1, it is left-padded with PAD.
   * If longer, only the trailing memory-1 tokens are used.
   */
  fun contextBase(context: List<T?>): ContextBase {
    var ctxKey = startContextKey
    if (contextLen == 0) return contextBaseFromKey(0L)

    val start = (context.size - contextLen).coerceAtLeast(0)
    for (i in start until context.size) {
      ctxKey = advanceKey(ctxKey, encode(context[i]))
    }
    return contextBaseFromKey(ctxKey)
  }

  /**
   * Same as [contextBase], but avoids token encoding if you already have IDs.
   */
  fun contextBaseEncoded(context: IntArray): ContextBase {
    var ctxKey = startContextKey
    if (contextLen == 0) return contextBaseFromKey(0L)

    val start = (context.size - contextLen).coerceAtLeast(0)
    for (i in start until context.size) {
      ctxKey = advanceKey(ctxKey, context[i])
    }
    return contextBaseFromKey(ctxKey)
  }

  /**
   * Score one token from a cached context handle.
   *
   * This is the hot-path API you want inside DFA decoding.
   */
  fun scoreTransitionFromBase(base: ContextBase, next: T?): Double =
    scoreTransitionFromBaseEncoded(base, encode(next))

  fun scoreTransitionFromBaseEncoded(base: ContextBase, nextTokId: Int): Double {
    val rowIdx = base.rowIdx
    if (rowIdx < 0) return base.value // unseen context => all tokens cost ln(V)
    return rows[rowIdx].costs.getOrDefault(nextTokId, base.value)
  }

  /**
   * Advance to the next cached context after emitting one token.
   * Useful in incremental decoding.
   */
  fun advance(base: ContextBase, emitted: T): ContextBase =
    advanceEncoded(base, encode(emitted))

  fun advanceEncoded(base: ContextBase, emittedTokId: Int): ContextBase =
    contextBaseFromKey(advanceKey(base.ctxKey, emittedTokId))

  /**
   * Convenience wrapper matching the old API shape.
   */
  fun scoreChunk(context: List<T>, next: T): Double =
    scoreTransitionFromBase(contextBase(context), next)

  fun scoreChunkEncoded(context: IntArray, nextTokId: Int): Double =
    scoreTransitionFromBaseEncoded(contextBaseEncoded(context), nextTokId)

  private inline fun contextBaseFromKey(ctxKey: Long): ContextBase {
    val rowIdx = rowByContext.getOrDefault(ctxKey, -1)
    val v = if (rowIdx < 0) unseenDefaultCost else rows[rowIdx].defaultCost
    return ContextBase(ctxKey = ctxKey, rowIdx = rowIdx, value = v)
  }

  private inline fun advanceKey(ctxKey: Long, tokId: Int): Long {
    if (contextLen == 0) return 0L
    return ((ctxKey % dropFactor) * base.toLong()) + tokId.toLong()
  }

  private class Row(
    val defaultCost: Double,   // ln(count(context) + V)
    val costs: IntDoubleTable, // token -> ln(count(context)+V) - ln(count(context,token)+1)
  )

  private class MutableRow(
    var total: Int = 0,
    val counts: IntIntTable = IntIntTable(),
  )

  companion object {
    private const val PAD_ID = 0
    private const val OOV_ID = 1

    internal fun <T> compile(
      corpus: List<T>,
      memory: Int,
      scorePrefix: List<T>,
      scoreSuffix: List<T>,
    ): FastMC<T> {
      require(memory >= 1)

      val tokenToId = LinkedHashMap<T, Int>()
      var nextId = 2
      for (t in corpus) if (t !in tokenToId) tokenToId[t] = nextId++

      val vocabObserved = tokenToId.size
      val emittedDomain = vocabObserved + 1 // + OOV
      val base = vocabObserved + 2          // PAD + OOV + observed
      val contextLen = memory - 1

      require(canPack(base, contextLen)) {
        "Cannot pack context of length $contextLen with base $base into Long."
      }

      val dropFactor = powLong(base.toLong(), (contextLen - 1).coerceAtLeast(0))
      val startContextKey = 0L // [PAD, PAD, ...] packs to all zeros

      val encodeRuntime: (T) -> Int = { t -> tokenToId[t] ?: OOV_ID }

      val prefixIds = IntArray(scorePrefix.size) { encodeRuntime(scorePrefix[it]) }
      val suffixIds = IntArray(scoreSuffix.size) { encodeRuntime(scoreSuffix[it]) }

      val rowByContext = LongIntTable()
      val mutableRows = ArrayList<MutableRow>()

      fun rowFor(ctxKey: Long): MutableRow {
        val idx = rowByContext.getOrDefault(ctxKey, -1)
        if (idx >= 0) return mutableRows[idx]
        val newIdx = mutableRows.size
        mutableRows.add(MutableRow())
        rowByContext.put(ctxKey, newIdx)
        return mutableRows[newIdx]
      }

      if (memory == 1) {
        val row = rowFor(0L)
        for (t in corpus) {
          val tok = encodeRuntime(t)
          row.total++
          row.counts.increment(tok)
        }
      } else {
        var ctxKey = startContextKey
        for (i in corpus.indices) {
          val tok = encodeRuntime(corpus[i])
          val row = rowFor(ctxKey)
          row.total++
          row.counts.increment(tok)
          ctxKey = ((ctxKey % dropFactor) * base.toLong()) + tok.toLong()
        }
      }

      val rows = Array(mutableRows.size) { i ->
        val mr = mutableRows[i]
        val defaultCost = ln(mr.total.toDouble() + emittedDomain.toDouble())
        val costs = IntDoubleTable(capacityForSize(mr.counts.size))
        mr.counts.forEach { tokId, count ->
          costs.put(tokId, defaultCost - ln(count.toDouble() + 1.0))
        }
        Row(defaultCost, costs)
      }

      val unseenDefaultCost = ln(emittedDomain.toDouble())

      return FastMC(
        memory = memory,
        vocabularySize = emittedDomain,
        tokenToId = tokenToId,
        padId = PAD_ID,
        oovId = OOV_ID,
        base = base,
        contextLen = contextLen,
        dropFactor = dropFactor,
        startContextKey = startContextKey,
        prefixIds = prefixIds,
        suffixIds = suffixIds,
        rowByContext = rowByContext,
        rows = rows,
        unseenDefaultCost = unseenDefaultCost,
      )
    }

    private fun canPack(base: Int, digits: Int): Boolean {
      var acc = 1L
      repeat(digits) {
        if (acc > Long.MAX_VALUE / base.toLong()) return false
        acc *= base.toLong()
      }
      return true
    }

    private fun powLong(base: Long, exp: Int): Long {
      var b = base
      var e = exp
      var out = 1L
      while (e > 0) {
        if ((e and 1) != 0) out *= b
        e = e ushr 1
        if (e != 0) b *= b
      }
      return out
    }

    private fun capacityForSize(size: Int): Int {
      var cap = 8
      val need = (size * 4) / 3 + 1
      while (cap < need) cap = cap shl 1
      return cap
    }
  }
}

/* =========================
 * Primitive hash tables
 * ========================= */

private class LongIntTable(initialCapacity: Int = 8) {
  private var keys = LongArray(nextPow2(initialCapacity)) { EMPTY_KEY }
  private var values = IntArray(keys.size)
  private var used = 0

  fun getOrDefault(key: Long, defaultValue: Int): Int {
    var idx = mix(key) and (keys.size - 1)
    while (true) {
      val k = keys[idx]
      if (k == EMPTY_KEY) return defaultValue
      if (k == key) return values[idx]
      idx = (idx + 1) and (keys.size - 1)
    }
  }

  fun put(key: Long, value: Int) {
    if ((used + 1) * 10 >= keys.size * 7) rehash(keys.size shl 1)
    var idx = mix(key) and (keys.size - 1)
    while (true) {
      val k = keys[idx]
      if (k == EMPTY_KEY) {
        keys[idx] = key
        values[idx] = value
        used++
        return
      }
      if (k == key) {
        values[idx] = value
        return
      }
      idx = (idx + 1) and (keys.size - 1)
    }
  }

  private fun rehash(newCap: Int) {
    val oldKeys = keys
    val oldVals = values
    keys = LongArray(newCap) { EMPTY_KEY }
    values = IntArray(newCap)
    used = 0
    for (i in oldKeys.indices) {
      val k = oldKeys[i]
      if (k != EMPTY_KEY) put(k, oldVals[i])
    }
  }

  private fun mix(x: Long): Int {
    var z = x
    z = (z xor (z ushr 33)) * -0xae502812aa7333L
    z = (z xor (z ushr 33)) * -0x3b314601e57a13adL
    z = z xor (z ushr 33)
    return z.toInt()
  }

  private companion object {
    private const val EMPTY_KEY: Long = Long.MIN_VALUE
  }
}

private class IntIntTable(initialCapacity: Int = 8) {
  private var keys = IntArray(nextPow2(initialCapacity)) { EMPTY_KEY }
  private var values = IntArray(keys.size)
  private var used = 0

  val size: Int get() = used

  fun increment(key: Int, delta: Int = 1) {
    if ((used + 1) * 10 >= keys.size * 7) rehash(keys.size shl 1)
    var idx = mix(key) and (keys.size - 1)
    while (true) {
      val k = keys[idx]
      if (k == EMPTY_KEY) {
        keys[idx] = key
        values[idx] = delta
        used++
        return
      }
      if (k == key) {
        values[idx] += delta
        return
      }
      idx = (idx + 1) and (keys.size - 1)
    }
  }

  inline fun forEach(block: (key: Int, value: Int) -> Unit) {
    for (i in keys.indices) {
      val k = keys[i]
      if (k != EMPTY_KEY) block(k, values[i])
    }
  }

  private fun rehash(newCap: Int) {
    val oldKeys = keys
    val oldVals = values
    keys = IntArray(newCap) { EMPTY_KEY }
    values = IntArray(newCap)
    used = 0
    for (i in oldKeys.indices) {
      val k = oldKeys[i]
      if (k != EMPTY_KEY) increment(k, oldVals[i])
    }
  }

  private fun mix(x: Int): Int {
    var z = x
    z = z xor (z ushr 16)
    z *= -0x7a143595
    z = z xor (z ushr 15)
    z *= -0x3d4d51cb
    z = z xor (z ushr 16)
    return z
  }

  private companion object {
    private const val EMPTY_KEY: Int = Int.MIN_VALUE
  }
}

private class IntDoubleTable(initialCapacity: Int = 8) {
  private var keys = IntArray(nextPow2(initialCapacity)) { EMPTY_KEY }
  private var values = DoubleArray(keys.size)
  private var used = 0

  fun getOrDefault(key: Int, defaultValue: Double): Double {
    var idx = mix(key) and (keys.size - 1)
    while (true) {
      val k = keys[idx]
      if (k == EMPTY_KEY) return defaultValue
      if (k == key) return values[idx]
      idx = (idx + 1) and (keys.size - 1)
    }
  }

  fun put(key: Int, value: Double) {
    if ((used + 1) * 10 >= keys.size * 7) rehash(keys.size shl 1)
    var idx = mix(key) and (keys.size - 1)
    while (true) {
      val k = keys[idx]
      if (k == EMPTY_KEY) {
        keys[idx] = key
        values[idx] = value
        used++
        return
      }
      if (k == key) {
        values[idx] = value
        return
      }
      idx = (idx + 1) and (keys.size - 1)
    }
  }

  private fun rehash(newCap: Int) {
    val oldKeys = keys
    val oldVals = values
    keys = IntArray(newCap) { EMPTY_KEY }
    values = DoubleArray(newCap)
    used = 0
    for (i in oldKeys.indices) {
      val k = oldKeys[i]
      if (k != EMPTY_KEY) put(k, oldVals[i])
    }
  }

  private fun mix(x: Int): Int {
    var z = x
    z = z xor (z ushr 16)
    z *= -0x7a143595
    z = z xor (z ushr 15)
    z *= -0x3d4d51cb
    z = z xor (z ushr 16)
    return z
  }

  private companion object {
    private const val EMPTY_KEY: Int = Int.MIN_VALUE
  }
}

private fun nextPow2(n: Int): Int {
  var x = if (n < 2) 2 else n - 1
  x = x or (x ushr 1)
  x = x or (x ushr 2)
  x = x or (x ushr 4)
  x = x or (x ushr 8)
  x = x or (x ushr 16)
  return x + 1
}