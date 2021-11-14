package ai.hypergraph.kaliningraph

import ai.hypergraph.kaliningraph.tensor.DoubleMatrix
import kotlin.random.Random

operator fun IntRange.times(s: IntRange) =
  flatMap { l -> s.map { r -> l to r }.toSet() }.toSet()

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