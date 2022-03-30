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

// Iterates through the Cartesian product space without repetition by generating
// a lazy stochastic sequence of tuples. Can be viewed as a random space-filling
// curve in n-dimensional space. This method can sample without replacement from
// an arbitrarily large product space in linear time and space.

fun <T> lazyRandomSpaceFillingCurve(
  base: Set<T>,
  dimension: Int = 1,
  tuple: List<T> = emptyList()
): Sequence<List<T>> =
  if (dimension == 0) sequenceOf(tuple.shuffled())
  else base.shuffled().asSequence().flatMap {
    lazyRandomSpaceFillingCurve(base, dimension - 1, tuple + it)
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