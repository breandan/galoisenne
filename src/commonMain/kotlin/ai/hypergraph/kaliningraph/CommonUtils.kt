package ai.hypergraph.kaliningraph

import ai.hypergraph.kaliningraph.tensor.*
import ai.hypergraph.kaliningraph.types.*
import kotlin.math.*
import kotlin.random.Random
import kotlin.reflect.KClass

fun randomMatrix(rows: Int, cols: Int = rows, rand: () -> Double = { Random.Default.nextDouble() }) =
  Array(rows) { Array(cols) { rand() }.toDoubleArray() }.toDoubleMatrix()

operator fun IntRange.times(s: IntRange): Set<V2<Int>> =
  flatMap { s.map(it::cc).toSet() }.toSet()

infix operator fun <T, U> Sequence<T>.times(other: Sequence<U>) =
  flatMap { other.map(it::to) }

fun <T, R : Ring<T>, M : Matrix<T, R, M>> Matrix<T, R, M>.elwise(op: (T) -> T): M =
  new(numRows, numCols, data.map { op(it) }, algebra)

operator fun <T, R : Ring<T>, M : Matrix<T, R, M>> T.times(m: Matrix<T, R, M>): M =
  with(m.algebra) { m.elwise { this@times * it  } }

operator fun <T, R : Ring<T>, M : Matrix<T, R, M>> Matrix<T, R, M>.times(t: T): M =
  with(algebra) { elwise { it * t } }

infix fun <T, R : Ring<T>, M : Matrix<T, R, M>> List<T>.dot(m: Matrix<T, R, M>): List<T> =
  m.cols.map { col -> with(m.algebra) { zip(col).fold(nil) { c, (a, b) -> c + a * b } } }

val ACT_TANH: (DoubleMatrix) -> DoubleMatrix = { it.elwise { tanh(it) } }

val NORM_AVG: (DoubleMatrix) -> DoubleMatrix = { it.meanNorm() }

fun DoubleMatrix.minMaxNorm() =
  data.fold(0.0 cc 0.0) { (a, b), e ->
    min(a, e) cc max(b, e)
  }.let { (min, max) -> elwise { e -> (e - min) / (max - min) } }

fun DoubleMatrix.meanNorm() =
  data.fold(VT(0.0, 0.0, 0.0)) { (a, b, c), e ->
    VT(a + e / data.size.toDouble(), min(b, e), max(c, e))
  }.let { (μ, min, max) -> elwise { e -> (e - μ) / (max - min) } }

fun allPairs(numRows: Int, numCols: Int): Set<V2<Int>> =
  (0 until numRows) * (0 until numCols)

fun randomVector(size: Int, rand: () -> Double = { Random.Default.nextDouble() }) =
  Array(size) { rand() }.toDoubleArray()

fun Array<DoubleArray>.toDoubleMatrix() = DoubleMatrix(size, this[0].size) { i, j -> this[i][j] }

fun kroneckerDelta(i: Int, j: Int) = if(i == j) 1.0 else 0.0

const val DEFAULT_FEATURE_LEN = 20
fun String.vectorize(len: Int = DEFAULT_FEATURE_LEN) =
  Random(hashCode()).let { randomVector(len) { it.nextDouble() } }

tailrec fun <T> closure(
  toVisit: Set<T> = emptySet(),
  visited: Set<T> = emptySet(),
  successors: Set<T>.() -> Set<T>
): Set<T> =
  if (toVisit.isEmpty()) visited
  else closure(
    toVisit = toVisit.successors() - visited,
    visited = visited + toVisit,
    successors = successors
  )

fun randomString(
  length: Int = 5,
  alphabet: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
) = List(length) { alphabet.random() }.joinToString("")


inline fun <reified T> exhaustiveSearch(
  base: Set<T>,
  dimension: Int = 1,
  asList: Array<T> = base.toTypedArray()
) = mls(base.size, dimension).map { it.map { asList[it] } }

/*TODO: Iterate over the Cartesian product space without repetition generating
 * a lazy stochastic sequence of tuples. Can be viewed as a random space-filling
 * curve in n-dimensional space. This method can sample without replacement from
 * an arbitrarily large product space in linear time and space.
 * https://www.nayuki.io/page/galois-linear-feedback-shift-register
 * https://gist.github.com/rgov/891712/40fc067e1df176667ec4618aa197d0453307cac0
 * https://en.wikipedia.org/wiki/Maximum_length_sequence
 * https://en.wikipedia.org/wiki/Gray_code#n-ary_Gray_code
 */

fun mls(base: Int, digits: Int, l: List<Int> = emptyList()): Sequence<List<Int>> =
  if (Int.MAX_VALUE < base.toDouble().pow(digits)) {
    println("Large sample space detected, sampling with replacement")
    generateSequence { List(digits) { Random.nextInt(base) } }
  } else if (digits <= 0) sequenceOf(l)
  else (0 until base).asSequence()
    .flatMap { mls(base, digits - 1, l + it) }


// https://link.springer.com/content/pdf/bbm%3A978-1-4615-1509-8%2F1.pdf
// https://sci-hub.se/https://doi.org/10.1023/A:1027422805851
val primitivePolynomials = mapOf(
  4 to listOf(19, 25),
  5 to listOf(37, 41, 47, 55, 59, 61),
  6 to listOf(67, 91, 97, 103, 109, 115),
  7 to listOf(
    131, 137, 143, 145, 157, 167, 171, 185, 191, 193, 203, 211, 213, 229,
    239, 241, 247, 253
  ),
  8 to listOf(
    285, 299, 301, 333, 351, 355, 357, 361, 369, 391, 397, 425, 451, 463,
    487, 501
  ),
  9 to listOf(
    529, 539, 545, 557, 563, 601, 607, 617, 623, 631, 637, 647, 661, 675,
    677, 687, 695, 701, 719, 721, 731, 757, 761, 787, 789, 799, 803, 817,
    827, 847, 859, 865, 875, 877, 883, 895, 901, 911, 949, 953, 967, 971,
    973, 981, 985, 995, 1001, 1019
  ),
  10 to listOf(
    1033, 1051, 1063, 1069, 1125, 1135, 1153, 1163, 1221, 1239, 1255,
    1267, 1279, 1293, 1305, 1315, 1329, 1341, 1347, 1367, 1387, 1413,
    1423, 1431, 1441, 1479, 1509, 1527, 1531, 1555, 1557, 1573, 1591,
    1603, 1615, 1627, 1657, 1663, 1673, 1717, 1729, 1747, 1759, 1789,
    1815, 1821, 1825, 1849, 1863, 1869, 1877, 1881, 1891, 1917, 1933,
    1939, 1969, 2011, 2035, 2041
  ),
  11 to listOf(
    2053, 2071, 2091, 2093, 2119, 2147, 2149, 2161, 2171, 2189, 2197,
    2207, 2217, 2225, 2255, 2257, 2273, 2279, 2283, 2293, 2317, 2323,
    2341, 2345, 2363, 2365, 2373, 2377, 2385, 2395, 2419, 2421, 2431,
    2435, 2447, 2475, 2477, 2489, 2503, 2521, 2533, 2551, 2561, 2567,
    2579, 2581, 2601, 2633, 2657, 2669, 2681, 2687, 2693, 2705, 2717,
    2727, 2731, 2739, 2741, 2773, 2783, 2793, 2799, 2801, 2811, 2819,
    2825, 2833, 2867, 2879, 2881, 2891, 2905, 2911, 2917, 2927, 2941,
    2951, 2955, 2963, 2965, 2991, 2999, 3005, 3017, 3035, 3037, 3047,
    3053, 3083, 3085, 3097, 3103, 3159, 3169, 3179, 3187, 3205, 3209,
    3223, 3227, 3229, 3251, 3263, 3271, 3277, 3283, 3285, 3299, 3305,
    3319, 3331, 3343, 3357, 3367, 3373, 3393, 3399, 3413, 3417, 3427,
    3439, 3441, 3475, 3487, 3497, 3515, 3517, 3529, 3543, 3547, 3553,
    3559, 3573, 3589, 3613, 3617, 3623, 3627, 3635, 3641, 3655, 3659,
    3669, 3679, 3697, 3707, 3709, 3713, 3731, 3743, 3747, 3771, 3791,
    3805, 3827, 3833, 3851, 3865, 3889, 3895, 3933, 3947, 3949, 3957,
    3971, 3985, 3991, 3995, 4007, 4013, 4021, 4045, 4051, 4069, 4073
  ),
  12 to listOf(
    4179, 4201, 4219, 4221, 4249, 4305, 4331, 4359, 4383, 4387, 4411,
    4431, 4439, 4449, 4459, 4485, 4531, 4569, 4575, 4621, 4663, 4669,
    4711, 4723, 4735, 4793, 4801, 4811, 4879, 4893, 4897, 4921, 4927,
    4941, 4977, 5017, 5027, 5033, 5127, 5169, 5175, 5199, 5213, 5223,
    5237, 5287, 5293, 5331, 5391, 5405, 5453, 5523, 5573, 5591, 5597,
    5611, 5641, 5703, 5717, 5721, 5797, 5821, 5909, 5913, 5955, 5957,
    6005, 6025, 6061, 6067, 6079, 6081, 6231, 6237, 6289, 6295, 6329,
    6383, 6427, 6453, 6465, 6501, 6523, 6539, 6577, 6589, 6601, 6607,
    6631, 6683, 6699, 6707, 6761, 6795, 6865, 6881, 6901, 6923, 6931,
    6943, 6999, 7057, 7079, 7103, 7105, 7123, 7173, 7185, 7191, 7207,
    7245, 7303, 7327, 7333, 7355, 7365, 7369, 7375, 7411, 7431, 7459,
    7491, 7505, 7515, 7541, 7557, 7561, 7701, 7705, 7727, 7749, 7761,
    7783, 7795, 7823, 7907, 7953, 7963, 7975, 8049, 8089, 8123, 8125,
    8137
  ),
  13 to listOf(
    8219, 8231, 8245, 8275, 8293, 8303, 8331, 8333, 8351, 8357, 8367,
    8379, 8381, 8387, 8393, 8417, 8435, 8461, 8469, 8489, 8495, 8507,
    8515, 8551, 8555, 8569, 8585, 8599, 8605, 8639
  ),
  14 to listOf(
    16427, 16441, 16467, 16479, 16507, 16553, 16559, 16571, 16573,
    16591, 16619, 16627, 16653, 16659, 16699, 16707, 16795, 16797,
    16807, 16813, 16821, 16853, 16857, 16881
  ),
  15 to listOf(
    32771, 32785, 32791, 32813, 32821, 32863, 32887, 32897, 32903,
    32915, 32933, 32963, 32975, 32989, 32999, 33013, 33025, 33045,
    33061, 33111, 33117, 33121, 33133, 33157
  ),
  16 to listOf(
    65581, 65593, 65599, 65619, 65725, 65751, 65839, 65853, 65871,
    65885, 65943, 65953, 65965, 65983, 65991, 66069, 66073, 66085,
    66095, 66141, 66157, 66181, 66193, 66209
  )
)

// https://dl.acm.org/doi/pdf/10.1145/321765.321777
// http://www.math.sci.hiroshima-u.ac.jp/m-mat/MT/ARTICLES/tgfsr3.pdf
// https://en.wikipedia.org/wiki/Linear-feedback_shift_register#Non-binary_Galois_LFSR
// TODO: https://www-users.cse.umn.edu/~garrett/students/reu/MB_algorithm.pdf#page=3
// http://www.bolet.org/~pornin/2007-fse-granboulan+pornin.pdf
// If sample space is not power of two, we can iterate the smallest LFSR greater
// than our set cardinality until it emits a value in range: "Hasty Pudding trick"
// All values will be unique.
// TODO: Encode/decode Cartesian product space to binary sequence

fun LFSR(degree: Int = 16): Sequence<UInt> = sequence {
  val vec0 = 1u
  var vec = vec0
  val taps = primitivePolynomials[degree]!!.random().toString(2)
    .mapIndexedNotNull { i, c -> if (c == '1') i else null }
  do {
    val bit = taps.map { vec shr it }.fold(0u) { a, c -> a xor c } and 1u
    vec = (vec shr 1) or (bit shl (degree - 1))
    yield(vec)
  } while (vec != vec0)
}

// Samples from unnormalized counts with normalized frequency
fun <T> Map<T, Number>.sample(random: Random = Random.Default) =
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
               target: Double = random.nextDouble()) =
  cdf.binarySearch { it.compareTo(target) }
    .let { if (it < 0) abs(it) - 1 else it }


// Maybe we can hack reification using super type tokens?
infix fun Any.isA(that: Any) = when {
  this !is KClass<out Any> && that !is KClass<out Any> -> this::class.isInstance(that)
  this !is KClass<out Any> && that is KClass<out Any> -> this::class.isInstance(that)
  this is KClass<out Any> && that is KClass<out Any> -> this.isInstance(that)
  this is KClass<out Any> && that !is KClass<out Any> -> this.isInstance(that)
  else -> TODO()
}

infix fun Collection<Any>.allAre(that: Any) = all { it isA that }
infix fun Collection<Any>.anyAre(that: Any) = any { it isA that }