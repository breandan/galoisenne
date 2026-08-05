package ai.hypergraph.kaliningraph.parsing

import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlin.random.Random

/** Platform-native, immutable exact integer used only by [BoundedAcyclicCFG]'s hot paths. */
internal expect class ExactCount

internal expect val EXACT_COUNT_ZERO: ExactCount
internal expect val EXACT_COUNT_ONE: ExactCount

internal expect fun exactCountAdd(left: ExactCount, right: ExactCount): ExactCount
internal expect fun exactCountSubtract(left: ExactCount, right: ExactCount): ExactCount
internal expect fun exactCountMultiply(left: ExactCount, right: ExactCount): ExactCount
internal expect fun exactCountDivide(left: ExactCount, right: ExactCount): ExactCount
internal expect fun exactCountRemainder(left: ExactCount, right: ExactCount): ExactCount
internal expect fun exactCountCompare(left: ExactCount, right: ExactCount): Int
internal expect fun exactCountEquals(left: ExactCount, right: ExactCount): Boolean
internal expect fun exactCountHash(value: ExactCount): Int
internal expect fun exactCountFromInt(value: Int): ExactCount
internal expect fun exactCountShiftLeft(value: ExactCount, bitCount: Int): ExactCount
internal expect fun exactCountBitLength(value: ExactCount): Int
internal expect fun BigInteger.toExactCount(): ExactCount
internal expect fun ExactCount.toPublicBigInteger(): BigInteger

internal operator fun ExactCount.plus(other: ExactCount): ExactCount = exactCountAdd(this, other)
internal operator fun ExactCount.minus(other: ExactCount): ExactCount = exactCountSubtract(this, other)
internal operator fun ExactCount.times(other: ExactCount): ExactCount = exactCountMultiply(this, other)
internal operator fun ExactCount.div(other: ExactCount): ExactCount = exactCountDivide(this, other)
internal operator fun ExactCount.rem(other: ExactCount): ExactCount = exactCountRemainder(this, other)
internal operator fun ExactCount.compareTo(other: ExactCount): Int = exactCountCompare(this, other)
internal fun ExactCount.divrem(other: ExactCount): Pair<ExactCount, ExactCount> =
  this / other to this % other
internal fun ExactCount.isZero(): Boolean = exactCountEquals(this, EXACT_COUNT_ZERO)
internal fun ExactCount.isOne(): Boolean = exactCountEquals(this, EXACT_COUNT_ONE)

private const val EXACT_RANDOM_WORD_BITS = 63
private const val EXACT_RANDOM_CHUNK_BITS = 21
private const val EXACT_RANDOM_CHUNK_MASK = (1L shl EXACT_RANDOM_CHUNK_BITS) - 1L

private fun exactCountFromRandomWord(word: Long): ExactCount {
  var result = EXACT_COUNT_ZERO
  for (shift in 42 downTo 0 step EXACT_RANDOM_CHUNK_BITS) {
    val chunk = ((word ushr shift) and EXACT_RANDOM_CHUNK_MASK).toInt()
    result = exactCountShiftLeft(result, EXACT_RANDOM_CHUNK_BITS) + exactCountFromInt(chunk)
  }
  return result
}

/**
 * Uniform exact integer in `[0, 2^bitCount)`. The 63-bit word layout and RNG consumption match
 * `Random.nextBigInteger(bitCount)`, but no ionspin number or decimal conversion is constructed.
 */
internal fun Random.nextExactCount(bitCount: Int): ExactCount {
  require(bitCount >= 0)
  if (bitCount == 0) return EXACT_COUNT_ZERO

  val words = (bitCount + EXACT_RANDOM_WORD_BITS - 1) / EXACT_RANDOM_WORD_BITS
  var result = EXACT_COUNT_ZERO
  for (index in 0 until words) {
    var word = nextLong() and Long.MAX_VALUE
    if (index == words - 1) {
      val topBits = bitCount - (words - 1) * EXACT_RANDOM_WORD_BITS
      val topMask = if (topBits == EXACT_RANDOM_WORD_BITS) Long.MAX_VALUE
      else (1L shl topBits) - 1L
      word = word and topMask
    }
    result += exactCountShiftLeft(exactCountFromRandomWord(word), index * EXACT_RANDOM_WORD_BITS)
  }
  return result
}

/** Uniform exact integer in `[0, bound)` using rejection sampling. */
internal fun Random.nextExactCount(bound: ExactCount, bitLength: Int = exactCountBitLength(bound)): ExactCount {
  require(bound > EXACT_COUNT_ZERO) { "bound must be > 0" }
  while (true) {
    val rank = nextExactCount(bitLength)
    if (rank < bound) return rank
  }
}
