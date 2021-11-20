package ai.hypergraph.kaliningraph

import ai.hypergraph.kaliningraph.tensor.*
import ai.hypergraph.kaliningraph.types.Ring
import kotlin.math.*
import kotlin.random.Random

fun randomMatrix(rows: Int, cols: Int = rows, rand: () -> Double = { Random.Default.nextDouble() }) =
  Array(rows) { Array(cols) { rand() }.toDoubleArray() }.toDoubleMatrix()

operator fun IntRange.times(s: IntRange) =
  flatMap { l -> s.map { r -> l to r }.toSet() }.toSet()

fun <T, R : Ring<T, R>, M : Matrix<T, R, M>> Matrix<T, R, M>.elwise(op: (T) -> T): M =
  new(numRows, numCols, algebra, data.map { op(it) })

val ACT_TANH: (DoubleMatrix) -> DoubleMatrix = { it.elwise { tanh(it) } }

val NORM_AVG: (DoubleMatrix) -> DoubleMatrix = { it.meanNorm() }

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

fun <T> T.power(exp: Int, matmul: (T, T) -> T) =
  generateSequence(this) { matmul(it, this) }.take(exp)

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