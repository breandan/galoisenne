package ai.hypergraph.kaliningraph.sampling

import ai.hypergraph.kaliningraph.choose
import ai.hypergraph.kaliningraph.tensor.*
import ai.hypergraph.kaliningraph.toDoubleMatrix
import ai.hypergraph.kaliningraph.types.*
import kotlin.math.*
import kotlin.random.*


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
  else (0 until i[0]).asSequence().flatMap { all(i.drop(1), l + it) }

// TODO: Compute minimal elements of GF(p^e) dynamically
// http://www.seanerikoconnor.freeservers.com/Mathematics/AbstractAlgebra/PrimitivePolynomials/theory.html
// https://math.stackexchange.com/questions/2232179/how-to-find-minimal-polynomial-for-an-element-in-mboxgf2m
// http://crc.stanford.edu/crc_papers/CRC-TR-04-03.pdf#page=24
// https://link.springer.com/content/pdf/bbm%3A978-3-642-54649-5%2F1.pdf#page=5

val generator = mapOf(
  // Degree to binary polynomial coefficients in decimal form
  // https://link.springer.com/content/pdf/bbm%3A978-1-4615-1509-8%2F1.pdf
  // https://sci-hub.se/https://doi.org/10.1023/A:1027422805851
  // https://github.com/umontreal-simul/ssj/blob/f384e22adf08bd5202ea65bb7cd53fee192cb3ce/src/main/java/umontreal/ssj/hups/SobolSequence.java#L488
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
  ),
  17 to listOf(
    131081, 131087, 131105, 131117, 131123, 131135, 131137,
    131157, 131177, 131195, 131213, 131225, 131235, 131247,
    131259, 131269, 131317
  ),
  18 to listOf(262183, 262207, 262221, 262267, 262273, 262363, 262375, 262381, 262407),
  19 to listOf(524327, 524351, 524359, 524371, 524377, 524387, 524399, 524413, 524435, 524463),
  20 to listOf(1048585, 1048659, 1048677, 1048681, 1048699),
  21 to listOf(2097157, 2097191, 2097215, 2097253),
  22 to listOf(4194307, 4194361),
  23 to listOf(8388641, 8388651, 8388653, 8388659, 8388671, 8388685, 8388709),
  24 to listOf(16777243),
  25 to listOf(33554441),
  26 to listOf(67108935),
)

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
  primitivePolynomial: List<Int> = generator[degree]!!.random().toString(2)
    .mapIndexedNotNull { i, c -> if (c == '1') i else null }
): Sequence<UInt> = // LFSRM(degree)
  sequence {
    val vec0 = Random.nextInt(1..(2.0.pow(degree).toInt())).toUInt()
    var vec = vec0
    var i = 0
    do {
      val bit = primitivePolynomial.map { vec shr it }
        .fold(0u) { a, c -> a xor c } and 1u
      vec = (vec shr 1) or (bit shl (degree - 1))
      yield(vec)
    } while (++i < 2.0.pow(degree).toInt() - 1)
  }

fun randomSequenceWithoutRepetition(range: IntRange) =
  LFSR(ceil(log2((range.last - range.first + 1).toDouble())).toInt())
    .filter { it.toInt() <= range.last - range.first }
    .map { range.first + it.toInt() - 1 }

private fun RandomVector(
  degree: Int,
  initialValue: UInt = Random.nextInt(1..(2.0.pow(degree).toInt())).toUInt(),
  initialState: List<Boolean> = initialValue.toBitList(degree),
) = FreeMatrix(XOR_ALGEBRA, degree, 1) { r, _ -> initialState[r] }

// https://en.wikipedia.org/wiki/Linear-feedback_shift_register#Matrix_forms
private fun TransitionMatrix(degree: Int, polynomial: List<Boolean>) =
  FreeMatrix(XOR_ALGEBRA, degree) { r, c -> if (r == 0) polynomial[c] else c == r - 1 }

private fun PrimitivePolynomial(length: Int) =
  generator[length]!!.random().toString(2).map { it == '1' }

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

fun <T> MDSamplerWithoutReplacement(set: Set<T>, dimension: Int = 1) =
  MDSamplerWithoutReplacement(List(dimension) { set })

fun <T> MDSamplerWithoutReplacement(
  dimensions: List<Set<T>>,
  cardinalities: List<Int> = dimensions.map { it.size },
  // Shuffle coordinates to increase entropy of sampling
  shuffledDims: List<List<T>> = dimensions.map { it.shuffled() },
  bitLens: List<Int> = dimensions.map(Set<T>::size).toBitLens(),
  degree: Int = bitLens.sum().also { println("Sampling with LFSR(GF(2^$it))") }
): Sequence<List<T>> =
  if (degree < 4) findAll(dimensions).shuffled()
  else if (degree !in generator) throw Exception("Space is too large! ($degree)")
  else LFSR(degree).map { it.toBitList(degree) }
    .hastyPuddingTrick(cardinalities)
    .map { shuffledDims.zip(it).map { (dims, idx) -> dims[idx] } } +
    sequenceOf(shuffledDims.map { it[0] }) // LFSR will never generate all 0s

private fun List<Int>.toBitLens(): List<Int> = map { ceil(log2(it.toDouble())).toInt() }
private fun List<Boolean>.toInt() = joinToString("") { if(it) "1" else "0" }.toInt(2)
private fun List<Boolean>.toUInt() = joinToString("") { if(it) "1" else "0" }.toUInt(2)
private fun UInt.toBitList(len: Int): List<Boolean> =
  toString(2).padStart(len, '0').map { it == '1' }

// Takes a list of bits and chunk lengths and returns a list of Ints, e.g.,
// (1010101100, [3, 2, 3, 2]) -> [101, 01, 011, 00] -> [4, 1, 3, 0]
private fun List<Boolean>.toIndexes(bitLens: List<Int>): List<Int> =
  bitLens.fold(listOf<List<Boolean>>() to this) { (a, b), i ->
    (a + listOf(b.take(i))) to b.drop(i)
  }.first.map { it.toInt() }

// Discards samples representing an integer exceeding set cardinality in any dimension
private fun Sequence<List<Boolean>>.hastyPuddingTrick(cardinalities: List<Int>): Sequence<List<Int>> =
  map { it.toIndexes(cardinalities.toBitLens()) }
    .filter { it.zip(cardinalities).all { (a, b) -> a < b } }

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

fun <T> Set<T>.choose(i: IntRange) =
  i.asSequence().flatMap { findAll(this, it).map { it.toSet() } }.distinct()

inline fun <reified T> Set<T>.choose(
  k: Int,
  numEl: Int = size choose k,
  order: Sequence<Int> = randomSequenceWithoutRepetition(0 .. numEl),
  asArray: Array<T> = toTypedArray()
) = order.map { it.decodeCombo(k).map { asArray[it] }.toSet() }

// https://www.farside.org.uk/201311/encoding_n_choose_k
//fun encode(choices: Set<Int>): Int {
//  var k = choices.size
//  return choices.sorted().sumOf { it choose k-- }
//}

fun Int.decodeCombo(k: Int): Set<Int> {
  var choice: Int = k - 1
  while (choice choose k < this) choice++

  var N = this
  var k = k
  val result = mutableSetOf<Int>()
  (choice downTo 0).forEach { choice ->
    if (choice choose k <= N) {
      N -= choice choose k--
      result.add(choice)
    }
  }
  return result
}