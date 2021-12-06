package ai.hypergraph.kaliningraph

import ai.hypergraph.kaliningraph.tensor.*
import ai.hypergraph.kaliningraph.types.Ring
import kotlin.math.*
import kotlin.random.Random

fun randomMatrix(rows: Int, cols: Int = rows, rand: () -> Double = { Random.Default.nextDouble() }) =
  Array(rows) { Array(cols) { rand() }.toDoubleArray() }.toDoubleMatrix()

operator fun IntRange.times(s: IntRange) =
  flatMap { l -> s.map { r -> l to r }.toSet() }.toSet()

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
  data.fold(0.0 to 0.0) { (a, b), e ->
    min(a, e) to max(b, e)
  }.let { (min, max) -> elwise { e -> (e - min) / (max - min) } }

fun DoubleMatrix.meanNorm() =
  data.fold(Triple(0.0, 0.0, 0.0)) { (a, b, c), e ->
    Triple(a + e / data.size.toDouble(), min(b, e), max(c, e))
  }.let { (μ, min, max) -> elwise { e -> (e - μ) / (max - min) } }

// Returns the Cartesian product of two sets
operator fun <T, Y> Set<T>.times(s: Set<Y>): Set<Pair<T, Y>> =
  flatMap { l -> s.map { r -> l to r }.toSet() }.toSet()

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
  
// Samples from unnormalized counts with normalized frequency
fun <T> Map<T, Number>.sample(random: Random = Random.Default) =
  entries.map { (k, v) -> k to v }.unzip().let { (keys, values) ->
    val cdf = values.cdf()
    generateSequence { keys[cdf.sample(random)] }
  }

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

fun main() {
  println("asdf")
}