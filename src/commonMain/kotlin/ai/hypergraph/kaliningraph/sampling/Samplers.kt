package ai.hypergraph.kaliningraph.sampling

import ai.hypergraph.kaliningraph.*
import ai.hypergraph.kaliningraph.tensor.*
import ai.hypergraph.kaliningraph.types.*
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
  else (0 until i[0]).asSequence().flatMap { all(i.drop(1), l + it) }

// Does not pass empirical tests?
val lecuyerGenerator =
  listOf(1, 3, 7, 11, 13, 19, 25, 37, 59,
    47, 61, 55, 41, 67, 97, 91, 109, 103,
    115, 131, 193, 137, 145, 143, 241, 157, 185,
    167, 229, 171, 213, 191, 253, 203, 211, 239,
    247, 285, 369, 299, 425, 301, 361, 333, 357,
    351, 501, 355, 397, 391, 451, 463, 487, 529,
    545, 539, 865, 557, 721, 563, 817, 601, 617,
    607, 1001, 623, 985, 631, 953, 637, 761, 647,
    901, 661, 677, 675, 789, 687, 981, 695, 949,
    701, 757, 719, 973, 731, 877, 787, 803, 799,
    995, 827, 883, 847, 971, 859, 875, 895, 1019,
    911, 967, 1033, 1153, 1051, 1729, 1063, 1825, 1069,
    1441, 1125, 1329, 1135, 1969, 1163, 1673, 1221, 1305,
    1239, 1881, 1255, 1849, 1267, 1657, 1279, 2041, 1293,
    1413, 1315, 1573, 1341, 1509, 1347, 1557, 1367, 1877,
    1387, 1717, 1423, 1933, 1431, 1869, 1479, 1821, 1527,
    1917, 1531, 1789, 1555, 1603, 1591, 1891, 1615, 1939,
    1627, 1747, 1663, 2035, 1759, 2011, 1815, 1863, 2053,
    2561, 2071, 3713, 2091, 3393, 2093, 2881, 2119, 3617,
    2147, 3169, 2149, 2657, 2161, 2273, 2171, 3553, 2189,
    2833, 2197, 2705, 2207, 3985, 2217, 2385, 2225, 2257,
    2255, 3889, 2279, 3697, 2283, 3441, 2293, 2801, 2317,
    2825, 2323, 3209, 2341, 2633, 2345, 2377, 2363, 3529,
    2365, 3017, 2373, 2601, 2395, 3497, 2419, 3305, 2421,
    2793, 2431, 4073, 2435, 3097, 2447, 3865, 2475, 3417,
    2477, 2905, 2489, 2521, 2503, 3641, 2533, 2681, 2551,
    3833, 2567, 3589, 2579, 3205, 2581, 2693, 2669, 2917,
    2687, 4069, 2717, 2965, 2727, 3669, 2731, 3413, 2739,
    3285, 2741, 2773, 2783, 4021, 2799, 3957, 2811, 3573,
    2819, 3085, 2867, 3277, 2879, 4045, 2891, 3373, 2911,
    4013, 2927, 3949, 2941, 3053, 2951, 3613, 2955, 3357,
    2963, 3229, 2991, 3933, 2999, 3805, 3005, 3037, 3035,
    3517, 3047, 3709, 3083, 3331, 3103, 3971, 3159, 3747,
    3179, 3427, 3187, 3299, 3223, 3731, 3227, 3475, 3251,
    3283, 3263, 4051, 3271, 3635, 3319, 3827, 3343, 3851,
    3367, 3659, 3399, 3627, 3439, 3947, 3487, 3995, 3515,
    3547, 3543, 3771, 3559, 3707, 3623, 3655, 3679, 4007,
    3743, 3991, 3791, 3895, 4179, 6465, 4201, 4801, 4219,
    7105, 4221, 6081, 4249, 4897, 4305, 4449, 4331, 6881,
    4359, 7185, 4383, 7953, 4387, 6289, 4411, 7057, 4431)
    .mapIndexed { i, it -> i to listOf(it) }.toMap()

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
  0 to listOf(0L),
  1 to listOf(1L),
  2 to listOf(7L),
  3 to listOf(11L),
  4 to listOf(19L, 25L),
  5 to listOf(37L, 41L, 47L, 55L, 59L, 61L),
  6 to listOf(67L, 91L, 97L, 103L, 109L, 115L),
  7 to listOf(
    131L, 137L, 143L, 145L, 157L, 167L, 171L, 185L, 191L, 193L, 203L, 211L, 213L, 229L,
    239L, 241L, 247L, 253
  ),
  8 to listOf(
    285L, 299L, 301L, 333L, 351L, 355L, 357L, 361L, 369L, 391L, 397L, 425L, 451L, 463L,
    487L, 501
  ),
  9 to listOf(
    529L, 539L, 545L, 557L, 563L, 601L, 607L, 617L, 623L, 631L, 637L, 647L, 661L, 675L,
    677L, 687L, 695L, 701L, 719L, 721L, 731L, 757L, 761L, 787L, 789L, 799L, 803L, 817L,
    827L, 847L, 859L, 865L, 875L, 877L, 883L, 895L, 901L, 911L, 949L, 953L, 967L, 971L,
    973L, 981L, 985L, 995L, 1001L, 1019
  ),
  10 to listOf(
    1033L, 1051L, 1063L, 1069L, 1125L, 1135L, 1153L, 1163L, 1221L, 1239L, 1255L,
    1267L, 1279L, 1293L, 1305L, 1315L, 1329L, 1341L, 1347L, 1367L, 1387L, 1413L,
    1423L, 1431L, 1441L, 1479L, 1509L, 1527L, 1531L, 1555L, 1557L, 1573L, 1591L,
    1603L, 1615L, 1627L, 1657L, 1663L, 1673L, 1717L, 1729L, 1747L, 1759L, 1789L,
    1815L, 1821L, 1825L, 1849L, 1863L, 1869L, 1877L, 1881L, 1891L, 1917L, 1933L,
    1939L, 1969L, 2011L, 2035L, 2041L
  ),
  11 to listOf(
    2053L, 2071L, 2091L, 2093L, 2119L, 2147L, 2149L, 2161L, 2171L, 2189L, 2197L,
    2207L, 2217L, 2225L, 2255L, 2257L, 2273L, 2279L, 2283L, 2293L, 2317L, 2323L,
    2341L, 2345L, 2363L, 2365L, 2373L, 2377L, 2385L, 2395L, 2419L, 2421L, 2431L,
    2435L, 2447L, 2475L, 2477L, 2489L, 2503L, 2521L, 2533L, 2551L, 2561L, 2567L,
    2579L, 2581L, 2601L, 2633L, 2657L, 2669L, 2681L, 2687L, 2693L, 2705L, 2717L,
    2727L, 2731L, 2739L, 2741L, 2773L, 2783L, 2793L, 2799L, 2801L, 2811L, 2819L,
    2825L, 2833L, 2867L, 2879L, 2881L, 2891L, 2905L, 2911L, 2917L, 2927L, 2941L,
    2951L, 2955L, 2963L, 2965L, 2991L, 2999L, 3005L, 3017L, 3035L, 3037L, 3047L,
    3053L, 3083L, 3085L, 3097L, 3103L, 3159L, 3169L, 3179L, 3187L, 3205L, 3209L,
    3223L, 3227L, 3229L, 3251L, 3263L, 3271L, 3277L, 3283L, 3285L, 3299L, 3305L,
    3319L, 3331L, 3343L, 3357L, 3367L, 3373L, 3393L, 3399L, 3413L, 3417L, 3427L,
    3439L, 3441L, 3475L, 3487L, 3497L, 3515L, 3517L, 3529L, 3543L, 3547L, 3553L,
    3559L, 3573L, 3589L, 3613L, 3617L, 3623L, 3627L, 3635L, 3641L, 3655L, 3659L,
    3669L, 3679L, 3697L, 3707L, 3709L, 3713L, 3731L, 3743L, 3747L, 3771L, 3791L,
    3805L, 3827L, 3833L, 3851L, 3865L, 3889L, 3895L, 3933L, 3947L, 3949L, 3957L,
    3971L, 3985L, 3991L, 3995L, 4007L, 4013L, 4021L, 4045L, 4051L, 4069L, 4073
  ),
  12 to listOf(
    4179L, 4201L, 4219L, 4221L, 4249L, 4305L, 4331L, 4359L, 4383L, 4387L, 4411L,
    4431L, 4439L, 4449L, 4459L, 4485L, 4531L, 4569L, 4575L, 4621L, 4663L, 4669L,
    4711L, 4723L, 4735L, 4793L, 4801L, 4811L, 4879L, 4893L, 4897L, 4921L, 4927L,
    4941L, 4977L, 5017L, 5027L, 5033L, 5127L, 5169L, 5175L, 5199L, 5213L, 5223L,
    5237L, 5287L, 5293L, 5331L, 5391L, 5405L, 5453L, 5523L, 5573L, 5591L, 5597L,
    5611L, 5641L, 5703L, 5717L, 5721L, 5797L, 5821L, 5909L, 5913L, 5955L, 5957L,
    6005L, 6025L, 6061L, 6067L, 6079L, 6081L, 6231L, 6237L, 6289L, 6295L, 6329L,
    6383L, 6427L, 6453L, 6465L, 6501L, 6523L, 6539L, 6577L, 6589L, 6601L, 6607L,
    6631L, 6683L, 6699L, 6707L, 6761L, 6795L, 6865L, 6881L, 6901L, 6923L, 6931L,
    6943L, 6999L, 7057L, 7079L, 7103L, 7105L, 7123L, 7173L, 7185L, 7191L, 7207L,
    7245L, 7303L, 7327L, 7333L, 7355L, 7365L, 7369L, 7375L, 7411L, 7431L, 7459L,
    7491L, 7505L, 7515L, 7541L, 7557L, 7561L, 7701L, 7705L, 7727L, 7749L, 7761L,
    7783L, 7795L, 7823L, 7907L, 7953L, 7963L, 7975L, 8049L, 8089L, 8123L, 8125L,
    8137
  ),
  13 to listOf(
    8219L, 8231L, 8245L, 8275L, 8293L, 8303L, 8331L, 8333L, 8351L, 8357L, 8367L,
    8379L, 8381L, 8387L, 8393L, 8417L, 8435L, 8461L, 8469L, 8489L, 8495L, 8507L,
    8515L, 8551L, 8555L, 8569L, 8585L, 8599L, 8605L, 8639L
  ),
  14 to listOf(
    16427L, 16441L, 16467L, 16479L, 16507L, 16553L, 16559L, 16571L, 16573L,
    16591L, 16619L, 16627L, 16653L, 16659L, 16699L, 16707L, 16795L, 16797L,
    16807L, 16813L, 16821L, 16853L, 16857L, 16881L
  ),
  15 to listOf(
    32771L, 32785L, 32791L, 32813L, 32821L, 32863L, 32887L, 32897L, 32903L,
    32915L, 32933L, 32963L, 32975L, 32989L, 32999L, 33013L, 33025L, 33045L,
    33061L, 33111L, 33117L, 33121L, 33133L, 33157L
  ),
  16 to listOf(
    65581L, 65593L, 65599L, 65619L, 65725L, 65751L, 65839L, 65853L, 65871L,
    65885L, 65943L, 65953L, 65965L, 65983L, 65991L, 66069L, 66073L, 66085L,
    66095L, 66141L, 66157L, 66181L, 66193L, 66209L
  ),
  17 to listOf(
    131081L, 131087L, 131105L, 131117L, 131123L, 131135L, 131137L,
    131157L, 131177L, 131195L, 131213L, 131225L, 131235L, 131247L,
    131259L, 131269L, 131317L
  ),
  18 to listOf(262183L, 262207L, 262221L, 262267L, 262273L, 262363L, 262375L, 262381L, 262407L),
  19 to listOf(524327L, 524351L, 524359L, 524371L, 524377L, 524387L, 524399L, 524413L, 524435L, 524463L),
  20 to listOf(1048585L, 1048659L, 1048677L, 1048681L, 1048699L),
  21 to listOf(2097157L, 2097191L, 2097215L, 2097253L),
  22 to listOf(4194307L, 4194361L),
  23 to listOf(8388641L, 8388651L, 8388653L, 8388659L, 8388671L, 8388685L, 8388709L),
  24 to listOf(16777243L),
  25 to listOf(33554441L),
  26 to listOf(67108935L),
  // https://oeis.org/A132453
  27 to listOf(134217767L),
  28 to listOf(268435465L),
  29 to listOf(536870917L),
  30 to listOf(1073741907L),
  31 to listOf(2147483657L),
  32 to listOf(4294967493L),
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
  else if (degree !in generator) throw Exception("Space is too large! ($degree) dim=${dimensions.map { it.size }.joinToString("x", "[", "]")}")
  else LFSR(degree)
    .let { if (skip == 1) it else it.filterIndexed { i, _ -> i % skip == shift } }
    .map { it.toBitList2(degree) }
    .hastyPuddingTrick(cardinalities)
    .map { shuffledDims.zip(it).map { (dims, idx) -> dims[idx] } } +
    sequenceOf(shuffledDims.map { it[0] }) // LFSR will never generate all 0s

// Samples without replacement from the joint distribution of ordered k-combinations of n elements crossed with Σ^k
fun <T> MDSamplerWithoutReplacementNK(Σ: Set<T>, n: Int, k: Int, skip: Int = 1, shift: Int = 0)=
  MDSamplerWithoutReplacementNKF(Σ, n=n, k=k, skip=skip, shift=shift)
    .map { (a, b) -> a.zip(b).toMap() }

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
  else if (degree !in generator) throw Exception("Space is too large! ($degree)")
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
  for (j in 0 until len) {
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
  else (0 until numEl).asSequence().map { it.decodeCombo(k).map { asArray[it] }.toSet() }