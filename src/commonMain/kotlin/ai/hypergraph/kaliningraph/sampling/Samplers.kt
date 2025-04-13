package ai.hypergraph.kaliningraph.sampling

import ai.hypergraph.kaliningraph.*
import ai.hypergraph.kaliningraph.tensor.*
import ai.hypergraph.kaliningraph.types.*
import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlin.math.*
import kotlin.random.*
import kotlin.time.*


fun randomMatrix(rows: Int, cols: Int = rows, rand: () -> Double = { Random.Default.nextDouble() }) =
  Array(rows) { Array(cols) { rand() }.toDoubleArray() }.toDoubleMatrix()

fun randomVector(size: Int, rand: () -> Double = { Random.Default.nextDouble() }) =
  Array(size) { rand() }.toDoubleArray()

fun randomString(
  length: Int = 5,
  alphabet: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
) = List(length) { alphabet.random() }.joinToString("")

/* Iterates over the Cartesian product space without repetition, generating
 * a lazy stochastic sequence of tuples. Can be viewed as a random space-filling
 * curve in n-dimensional space. This method can sample without replacement from
 * an arbitrarily large product space in linear time and space.
 * https://www.nayuki.io/page/galois-linear-feedback-shift-register
 * https://gist.github.com/rgov/891712/40fc067e1df176667ec4618aa197d0453307cac0
 * https://en.wikipedia.org/wiki/Maximum_length_sequence
 * https://en.wikipedia.org/wiki/Gray_code#n-ary_Gray_code
 */

fun <T> findAll(base: Set<T>, dimension: Int = 1): Sequence<List<T>> =
  findAll(List(dimension) { base })

fun <T> findAll(
  dimensions: List<Set<T>>,
  cardinalities: List<Int> = dimensions.map { it.size },
  asList: List<List<T>> = dimensions.map { it.shuffled() }
): Sequence<List<T>> =
   all(cardinalities).map { (asList zip it).map { (l, i) -> l[i] } }

fun all(i: List<Int>, l: List<Int> = emptyList()): Sequence<List<Int>> =
  if (i.isEmpty()) sequenceOf(l)
  else (0..<i[0]).asSequence().flatMap { all(i.drop(1), l + it) }

// https://dl.acm.org/doi/pdf/10.1145/321765.321777
// http://www.math.sci.hiroshima-u.ac.jp/m-mat/MT/ARTICLES/tgfsr3.pdf
// https://en.wikipedia.org/wiki/Linear-feedback_shift_register#Non-binary_Galois_LFSR
// TODO: https://www-users.cse.umn.edu/~garrett/students/reu/MB_algorithm.pdf#page=3
// http://www.bolet.org/~pornin/2007-fse-granboulan+pornin.pdf
// If sample space is not power of two, we can iterate the smallest LFSR greater
// than our set cardinality until it emits a value in range: "Hasty Pudding trick"
// All values will be unique.

fun LFSR(
  degree: Int = 16,
  primitivePolynomial: List<Int> = Polynomials.academic[degree]!!.random().toString(2)
    .mapIndexedNotNull { i, c -> if (c == '1') i else null }
): Sequence<ULong> = // LFSRM(degree)
  if (degree == 0) sequenceOf() else sequence {
    val max = 1L shl degree
    val vec0 = Random.nextULong(1UL ..max.toULong())
    var vec = vec0
    var i = 0
    do {
      val bit = primitivePolynomial.fold(0UL) { a, c -> a xor (vec shr c) } and 1UL
      vec = (vec shr 1) or (bit shl (degree - 1))
      yield(vec)
    } while (++i < max - 1)
  }

// Generates a random sequence of unique values in range
fun randomSequenceWithoutRepetition(range: IntRange): Sequence<Int> =
  LFSR(ceil(log2((range.last - range.first + 1).toDouble())).toInt())
    .filter { it.toInt() <= range.last - range.first }
    .map { range.first + it.toInt() - 1 }

private fun RandomVector(
  degree: Int,
  initialValue: ULong = Random.nextULong(1UL..(2.0.pow(degree).toULong())),
  initialState: List<Boolean> = initialValue.toBitList2(degree),
) = FreeMatrix(XOR_ALGEBRA, degree, 1) { r, _ -> initialState[r] }

// https://en.wikipedia.org/wiki/Linear-feedback_shift_register#Matrix_forms
private fun TransitionMatrix(degree: Int, polynomial: List<Boolean>) =
  FreeMatrix(XOR_ALGEBRA, degree) { r, c -> if (r == 0) polynomial[c] else c == r - 1 }

private fun PrimitivePolynomial(length: Int): List<Boolean> =
  Polynomials.academic[length]!!.random().toString(2).map { it == '1' }

fun LFSRM(
  degree: Int,
  initialVec: FreeMatrix<Boolean> = RandomVector(degree),
  primitivePolynomial: List<Boolean> = PrimitivePolynomial(degree),
  matrix: FreeMatrix<Boolean> = TransitionMatrix(degree, primitivePolynomial)
): Sequence<UInt> = sequence {
  var i = 0
  var s: FreeMatrix<Boolean> = initialVec
  do {
    s = matrix * s
    yield(s.data.toUInt())
  } while (++i < 2.0.pow(degree).toInt() - 1)
}

fun <T> MDSamplerWithoutReplacement(set: Set<T>, dimension: Int = 1, skip: Int = 1, shift: Int = 0): Sequence<List<T>> =
  MDSamplerWithoutReplacement(List(dimension) { set }, skip = skip, shift = shift)

fun <T> MDSamplerWithoutReplacement(
  dimensions: List<Set<T>>,
  cardinalities: List<Int> = dimensions.map { it.size },
  skip: Int = 1,
  shift: Int = 0,
  // Shuffle coordinates to increase entropy of sampling
  shuffledDims: List<List<T>> = dimensions.map { it.shuffled() },
  bitLens: List<Int> = dimensions.map(Set<T>::size).toBitLens2(),
  degree: Int = bitLens.sum()
): Sequence<List<T>> =
  if (degree < 4) findAll(dimensions).shuffled()
  else if (degree !in Polynomials.academic) throw Exception("Space is too large! ($degree) dim=${dimensions.map { it.size }.joinToString("x", "[", "]")}")
  else LFSR(degree)
    .let { if (skip == 1) it else it.filterIndexed { i, _ -> i % skip == shift } }
    .map { it.toBitList2(degree) }
    .hastyPuddingTrick(cardinalities)
    .map { shuffledDims.zip(it).map { (dims, idx) -> dims[idx] } } +
    sequenceOf(shuffledDims.map { it[0] }) // LFSR will never generate all 0s

// Samples without replacement from the joint distribution of ordered k-combinations of n elements crossed with Σ^k
fun <T> MDSamplerWithoutReplacementNK(Σ: Set<T>, n: Int, k: Int, skip: Int = 1, shift: Int = 0)=
  MDSamplerWithoutReplacementNKF(Σ, n=n, k=k, skip=skip, shift=shift)
    .map { (a, b) -> a.zip(b) }

fun <T> MDSamplerWithoutReplacementNKF(
  dimensions: Set<T>,
  n: Int,
  k: Int,
  // Controls the start index and stride of each core's sampler (for parallelization)
  skip: Int = 1,
  shift: Int = 0,
  // Shuffle coordinates to increase entropy of sampling
  shuffledDims: List<T> = dimensions.shuffled(),
  cardinality: Int = dimensions.size.pow(k),
  choices: Int = n choose k,
  degree: Int = log_2(choices) + log_2(cardinality)
): Sequence<Pair<Set<Int>, List<T>>> =
  if (degree < 4) throw Exception("Space is too small! ($degree)")
  else if (degree !in Polynomials.academic) throw Exception("Space is too large! ($degree)")
  else LFSR(degree)
    //.also { println("Params: n=$n, k=$k, skip=$skip, shift=$shift, cardinality=$cardinality, choices=$choices, degree=$degree") }
    .let { if (skip == 1) it else it.filterIndexed { i, _ -> i % skip == shift } }
    .map { it.toBitList2(degree) }.hastyPuddingTrick(listOf(choices, cardinality))
    .map { it.first().decodeCombo(k) to it.last().untupled(k).map { shuffledDims[it] } } +
      sequenceOf(0.decodeCombo(k) to 0.untupled(k).map { shuffledDims[it] }) // LFSR will never generate all 0s

// ceil(log_2(x)) but faster than converting to double and back
fun log_2(x: Int): Int {
  var i = 0
  var j = x
  while (j > 0) {
    j = j shr 1
    i++
  }
  return i
}

private fun List<Int>.toBitLens2(): List<Int> = map { log_2(it) }

private fun List<Boolean>.toInt() = joinToString("") { if (it) "1" else "0" }.toInt(2)
// Above function rewritten much faster:
private fun List<Boolean>.toIntFast(): Int {
  var i = 0
  for (b in this) {
    i = (i shl 1) or if (b) 1 else 0
  }
  return i
}
private fun List<Boolean>.toUInt() = joinToString("") { if (it) "1" else "0" }.toUInt(2)

private fun UInt.toBitList(len: Int): List<Boolean> =
  toString(2).padStart(len, '0').map { it == '1' }
// Much faster version of above function:
private fun ULong.toBitList2(len: Int): List<Boolean> {
  val bits = mutableListOf<Boolean>()
  var i = this
  for (j in 0..<len) {
    bits.add(i and 1UL == 1UL)
    i = i shr 1
  }
  return bits
}

// Takes a list of bits and chunk lengths and returns a list of Ints, e.g.,
// (1010101100, [3, 2, 3, 2]) -> [101, 01, 011, 00] -> [4, 1, 3, 0]
private fun List<Boolean>.toIndexes(bitLens: List<Int>): List<Int> =
  bitLens.fold(listOf<List<Boolean>>() to this) { (a, b), i ->
    (a + listOf(b.take(i))) to b.drop(i)
  }.first.map { it.toIntFast() }
// Above function rewritten much faster:
private fun List<Boolean>.toIndexes2(bitLens: List<Int>): List<Int> {
  val indexes = mutableListOf<Int>()
  var i = 0
  for (len in bitLens) {
    indexes.add(subList(i, i + len).toIntFast())
    i += len
  }
  return indexes
}

// Discards samples representing an integer exceeding set cardinality in any dimension
private fun Sequence<List<Boolean>>.hastyPuddingTrick(cardinalities: List<Int>): Sequence<List<Int>> =
  map { it.toIndexes2(cardinalities.toBitLens2()) }
    .filter { it.zip(cardinalities).all { (a, b) -> a < b } }

// Samples from unnormalized counts with normalized frequency
fun <T> Map<T, Number>.sample(random: Random = Random.Default): Sequence<T> =
  entries.map { (k, v) -> k to v }.unzip()
    .let { (keys, values) -> generateSequence { keys[values.cdf().sample(random)] } }

fun Collection<Number>.cdf() = CDF(
  sumOf { it.toDouble() }
    .let { sum -> map { i -> i.toDouble() / sum } }
    .runningReduce { acc, d -> d + acc }
)

class CDF(val cdf: List<Double>): List<Double> by cdf

// Draws a single sample using KS-transform w/binary search
fun CDF.sample(random: Random = Random.Default,
               target: Double = random.nextDouble()): Int =
  cdf.binarySearch { it.compareTo(target) }
    .let { if (it < 0) abs(it) - 1 else it }

fun <T> Set<T>.choose(i: IntRange): Sequence<Set<T>> =
  i.asSequence().flatMap { findAll(this, it).map { it.toSet() } }.distinct()

inline fun <reified T> Set<T>.choose(
  k: Int,
  numEl: Int = size choose k,
  order: Sequence<Int> = randomSequenceWithoutRepetition(0 .. numEl),
  asArray: Array<T> = toTypedArray()
): Sequence<Set<T>> =
  if (size <= k) sequenceOf(this)
  else order.map { it.decodeCombo(k).map { asArray[it] }.toSet() }

// TODO: implement choice with Cartesian product (n choose k) x {...}^k
fun <T, Y> Set<T>.chooseWith(k: IntRange, g: Set<Y>): Sequence<Π2<Set<T>, List<Y>>> = TODO()

// Enumerate k-combinations in order provided
inline fun <reified T> List<T>.choose(
  k: Int,
  numEl: Int = size choose k,
  asArray: Array<T> = toTypedArray()
): Sequence<Set<T>> =
  if (size <= k) sequenceOf(toSet())
  else (0..<numEl).asSequence().map { it.decodeCombo(k).map { asArray[it] }.toSet() }

fun <T> List<T>.sampleWithGeomDecay(): T {
  if (isEmpty()) throw NoSuchElementException("List is empty.")

  val r = 0.5 // Common ratio; adjust this for different decay rates

  // Compute the total sum of the geometric series up to size
  val total = (1 - r.pow(size)) / (1 - r)

  // Generate a random value between 0 and the total
  val rnd = Random.nextDouble() * total

  // Iterate to find which item this random value corresponds to
  var cumulativeSum = 0.0
  var index = 0
  while (index < size) {
    cumulativeSum +=r.pow(index.toDouble())
    if (rnd < cumulativeSum) break
    index++
  }

  return this[index]
}

fun bigLFSRSequence(int: Int): Sequence<BigInteger> = bigLFSRSequence(BigInteger(int))
fun bigLFSRSequence(maxVal: BigInteger): Sequence<BigInteger> =
  BigLFSR(makeBigIntFromTaps(Polynomials.xlinz[maxVal.bitLength()]!!), makeRandBigInt(maxVal.bitLength()))
    .sequence().filter { it < maxVal }

fun makeBigIntFromTaps(taps: List<Int>): BigInteger =
  taps.map {
    BigInteger.parseString(Array(it + 1) { if (it == 0) '1' else '0' }.joinToString(""), 2)
  }.reduce { a, c -> a.or(c) }.or(BigInteger.ONE)

fun makeRandBigInt(len: Int): BigInteger =
    BigInteger.parseString(Array(len) { if (it == 0) '1' else if (Random.nextBoolean()) '1' else '0' }.joinToString(""), 2) + 1

class BigLFSR(primitivePoly: BigInteger, val start: BigInteger = BigInteger.ONE) {
  private val taps: BigInteger = primitivePoly.shr(1)

  fun sequence(): Sequence<BigInteger> = sequence {
    var last = start
    yield(last)
    var next: BigInteger
    while (true) {
      val shiftedOutA1: Boolean = last.bitAt(0)
      next = last.shr(1)
      if (shiftedOutA1) { next = next.xor(taps) }
      if (next == start) { yield(BigInteger.ZERO); break } else yield(next)
      last = next
    }
  }
}