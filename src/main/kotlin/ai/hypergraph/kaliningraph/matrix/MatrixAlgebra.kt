package ai.hypergraph.kaliningraph.matrix

import ai.hypergraph.kaliningraph.*
import ai.hypergraph.kaliningraph.types.Ring
import org.ejml.data.*
import org.ejml.kotlin.*
import kotlin.math.*
import kotlin.random.Random


interface Matrix<T> {
  companion object {
    operator fun <T> invoke(numRows: Int = 0, numCols: Int = numRows, data: List<T> = emptyList()): Matrix<T> =
      object : Matrix<T> {
        override val numRows: Int = numRows
        override val numCols: Int = numCols
        override val data: List<T> = data
      }

    operator fun <T> invoke(rows: Int, cols: Int = rows, f: (Int, Int) -> T): Matrix<T> =
      invoke(rows, cols, List(rows * cols) { f(it / cols, it % cols) })
  }

  val data: List<T>
  val numRows: Int
  val numCols: Int

  val indices get() = (0 until numRows) * (0 until numCols)
  val rows get() = data.chunked(numCols)
  val cols get() = (0 until numCols).map { c -> rows.map { it[c] } }


  fun join(that: Matrix<T>, op: (Int, Int) -> T): Matrix<T> =
    assert(numCols == that.numRows) {
      "Dimension mismatch: $numRows,$numCols . ${that.numRows},${that.numCols}"
    }.run { invoke(numCols, that.numRows, op) }

  fun <U> map(f: (T) -> U): Matrix<U> = invoke(numRows, numCols, data.map(f))

  operator fun get(r: Int, c: Int) = data[r * numCols + c]
  operator fun get(r: Int): List<T> =
    data.toList().subList(r * numCols, r * numCols + numCols)

  fun transpose(): Matrix<T> = invoke(numCols, numRows) { r, c -> this[c, r] }
}

interface MatrixAlgebra<T, R : Ring<T>> {
  val ring: R

  operator fun Matrix<T>.plus(that: Matrix<T>): Matrix<T> =
    join(that) { i, j -> with(ring) { this@plus[i][j] + that[i][j] } }

  infix fun List<T>.dot(es: List<T>): T =
    with(ring) { zip(es).map { (a, b) -> a * b }.reduce { a, b -> a + b } }

  operator fun Matrix<T>.times(that: Matrix<T>): Matrix<T> = join(that) { i, j -> this[i] dot that[j] }

  companion object {
    operator fun <T, R : Ring<T>, M: Matrix<T>> invoke(ring: R) =
      object : MatrixAlgebra<T, R> { override val ring: R = ring }
  }
}

// https://www.ijcai.org/Proceedings/2020/0685.pdf

val BOOLEAN_ALGEBRA = MatrixAlgebra(
  Ring(
    nil = false,
    one = true,
    plus = { a, b -> a || b },
    times = { a, b -> a && b }
  )
)

val MINPLUS_ALGEBRA = MatrixAlgebra(
  Ring(
    nil = Integer.MAX_VALUE,
    one = 0,
    plus = { a, b -> min(a, b) },
    times = { a, b -> a + b }
  )
)

val MAXPLUS_ALGEBRA = MatrixAlgebra(
  Ring(
    nil = Integer.MIN_VALUE,
    one = 0,
    plus = { a, b -> max(a, b) },
    times = { a, b -> a + b }
  )
)

open class BooleanMatrix constructor(
  val algebra: MatrixAlgebra<Boolean, Ring<Boolean>> = BOOLEAN_ALGEBRA,
  override val numRows: Int, override val numCols: Int,
  override val data: List<Boolean>,
) : Matrix<Boolean> {
  constructor(elements: List<Boolean>) : this(BOOLEAN_ALGEBRA, sqrt(elements.size.toDouble()).toInt(), sqrt(elements.size.toDouble()).toInt(), elements)
  constructor(numRows: Int, numCols: Int = numRows, f: (Int, Int) -> Boolean) : this(List(numRows*numCols) { f(it / numRows, it % numCols) })
  constructor(vararg rows: Short) : this(rows.fold("") { a, b -> a + b })
  constructor(vararg rows: String) :
    this(rows.fold("") { a, b -> a + b }
      .toCharArray().let { chars ->
        val values = chars.distinct()
        assert(values.size == 2) { "Expected two values" }
        chars.map { it == values[0] }
      }
    )

  fun Matrix<Boolean>.toBooleanMatrix() = BooleanMatrix(algebra, numRows, numCols, data)

  // TODO: https://arxiv.org/pdf/0811.1714.pdf#page=5
  operator fun times(that: Matrix<Boolean>) =
    with(algebra) { this@BooleanMatrix as Matrix<Boolean> * that }.toBooleanMatrix()
  operator fun plus(that: Matrix<Boolean>) =
    with(algebra) { this@BooleanMatrix as Matrix<Boolean> + that }.toBooleanMatrix()

  override fun transpose(): BooleanMatrix = super.transpose().toBooleanMatrix()
  val contents: Array<BooleanArray> = data.chunked(numCols).map { it.toBooleanArray() }.toTypedArray()

  val isFull = data.all { it }
  val values = data.distinct().sorted()

  companion object {
    fun grayCode(size: Int): BooleanMatrix = TODO()
    fun ones(size: Int) = BooleanMatrix(size) { _, _ -> true }
    fun zeroes(size: Int) = BooleanMatrix(size) { _, _ -> false }
    fun one(size: Int) = BooleanMatrix(size) { i, j -> i == j }
    fun random(size: Int) = BooleanMatrix(size) { _, _ -> Random.nextBoolean() }
  }

  override fun toString() = data.foldIndexed("") { i, a, b ->
    a + (if (b) 1 else 0) + " " + if (i > 0 && (i + 1) % numCols == 0) "\n" else ""
  }

  override fun equals(other: Any?) =
    other is BooleanMatrix && data.zip(other.data).all { (a, b) -> a == b }

  override fun hashCode() = contents.contentDeepHashCode()
}

fun DMatrix.toBMat() = BooleanMatrix(numRows, numCols) { i, j -> get(i, j) > 0.5 }
fun BMatrixRMaj.toBMat() = BooleanMatrix(numRows, numCols) { i, j -> get(i, j) }
operator fun BooleanMatrix.times(mat: SpsMat): SpsMat = toEJMLSparse() * mat
operator fun BooleanMatrix.plus(mat: SpsMat): SpsMat = toEJMLSparse() * mat
operator fun SpsMat.minus(mat: BooleanMatrix): SpsMat = this - mat.toEJMLSparse()

fun BooleanMatrix.toEJML() = BMatrixRMaj(numRows, numCols).also {
  for ((i, j) in (0 until numRows) * (0 until numCols)) it[i, j] = this[i, j]
}

fun BooleanMatrix.toEJMLSparse() = SpsMat(numRows, numCols, numRows).also {
  for ((i, j) in (0 until numRows) * (0 until numCols))
    it[i, j] = if (this[i, j]) 1.0 else 0.0
}