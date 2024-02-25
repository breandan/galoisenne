package ai.hypergraph.kaliningraph

import ai.hypergraph.kaliningraph.sampling.randomVector
import ai.hypergraph.kaliningraph.tensor.*
import ai.hypergraph.kaliningraph.types.*
import kotlin.math.*
import kotlin.random.*
import kotlin.reflect.KClass

fun <T, R : Ring<T>, M : Matrix<T, R, M>> Matrix<T, R, M>.elwise(op: (T) -> T): M =
  new(data = data.map { op(it) })

operator fun <T, R : Ring<T>, M : Matrix<T, R, M>> T.times(m: Matrix<T, R, M>): M =
  with(m.algebra) { m.elwise { this@times * it  } }

operator fun <T, R : Ring<T>, M : Matrix<T, R, M>> Matrix<T, R, M>.times(t: T): M =
  with(algebra) { elwise { it * t } }

fun <T, R : Ring<T>, M : Matrix<T, R, M>> Matrix<T, R, M>.pow(i: Int): M =
  (0..i).fold(this) { a, _ -> a * this as M } as M

fun DoubleMatrix.norm() = data.sumOf { it * it }.pow(0.5)

fun DoubleMatrix.companionMatrix(): DoubleMatrix =
  if (numRows != 1) throw Exception("Companion matrix requires scalar coefficients")
  else DoubleMatrix(numCols) { r, c ->
    if (r + 1 == c) 1.0
    else if (r == numCols - 1) -this[0, c]
    else 0.0
  }

fun DoubleMatrix.eigen(tolerance: Double = 0.00001): Π2<DoubleMatrix, Double> {
  val init = this * DoubleMatrix(numCols, 1, List(numCols) { 1.0 })
  val eigVec = init.seekFixpoint(
    stop = { i, t, tt -> (t - tt).norm() < tolerance },
    succ =  { (this * it).let { it * (1.0 / it.norm()) } }
  )

  val eigVal = ((this * eigVec) * eigVec.transpose)[0, 0] /
      (eigVec * eigVec.transpose)[0, 0]

  return eigVec to eigVal
}

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

infix fun Int.choose(k: Int): Int {
  require(0 <= k && 0 <= this) { "Bad (k, n) = ($k, $this)!" }
  if (k > this || k < 0) return 0
  if (k > this / 2) return this choose this - k
  var result = 1
  for (i in 1..k) result = result * (this - i + 1) / i
  return result
}

tailrec fun fact(n: Int, t: Int = 1): Int = if (n == 1) t else fact(n - 1, t * n)

fun DoubleMatrix.exp(max: Int = 10): DoubleMatrix =
  (1..max).fold(DoubleMatrix.one(numRows) to this) { (acc, an), i ->
    (acc + an * (1.0 / fact(i).toDouble())) to (an * this)
  }.first

fun <T, Y> joinToScalar(
  m1: Matrix<T, *, *>,
  m2: Matrix<T, *, *>,
  filter: (Int, Int) -> Boolean = { _, _ -> true },
  join: (T, T) -> Y,
  reduce: (Y, Y) -> Y
): Y =
  if (m1.shape() != m2.shape())
    throw Exception("Shape mismatch: ${m1.shape()} != ${m2.shape()}")
  else m1.data.zip(m2.data)
    .filterIndexed { i, _ -> filter(i / m1.numCols, i % m1.numCols) }
    .map { (a, b) -> join(a, b) }
    .reduce { a, b -> reduce(a, b) }

fun Array<DoubleArray>.toDoubleMatrix() = DoubleMatrix(size, this[0].size) { i, j -> this[i][j] }

fun kroneckerDelta(i: Int, j: Int) = if (i == j) 1.0 else 0.0

fun hashPair(i1: Int, i2: Int): Int = i1 * 31 + i2

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