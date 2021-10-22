package ai.hypergraph.kaliningraph.matrix

import ai.hypergraph.kaliningraph.*
import ai.hypergraph.kaliningraph.types.Ring
import org.ejml.data.*
import org.ejml.kotlin.*
import java.lang.reflect.Constructor
import kotlin.math.*
import kotlin.random.Random


/**
 * Generic matrix which supports overloadable addition and multiplication
 * using an abstract algebra (e.g. tropical semiring). Useful for many
 * problems in graph theory.
 *
 * @see [MatrixAlgebra]
 */

interface Matrix<T, R : Ring<T>, M : Matrix<T, R, M>> {
  val data: List<T>
  val numRows: Int
  val numCols: Int
  val algebra: MatrixAlgebra<T, R>

  val indices get() = (0 until numRows) * (0 until numCols)
  val rows get() = data.chunked(numCols)
  val cols get() = (0 until numCols).map { c -> rows.map { it[c] } }

  operator fun times(that: M): M = with(algebra) { this@Matrix times that }
  operator fun plus(that: M): M = with(algebra) { this@Matrix plus that }

  // Constructs a new instance with the same concrete matrix type
  fun new(numCols: Int, numRows: Int, algebra: MatrixAlgebra<T, R>, data: List<T>): M =
    (javaClass.getConstructor(
      MatrixAlgebra::class.java, Int::class.java, Int::class.java, List::class.java
    ) as Constructor<M>).newInstance(algebra, numCols, numRows, data)

  fun join(that: Matrix<T, R, M>, op: (Int, Int) -> T): M =
    assert(numCols == that.numRows) {
      "Dimension mismatch: $numRows,$numCols . ${that.numRows},${that.numCols}"
    }.run { new(numCols, that.numRows, algebra, indices.map { (i, j) -> op(i, j) }) }

  operator fun get(r: Int, c: Int): T = data[r * numCols + c]
  operator fun get(r: Int): List<T> =
    data.toList().subList(r * numCols, r * numCols + numCols)

  fun transpose(): M = new(numCols, numRows, algebra, indices.map { (i, j) -> this[j, i] })
}

/**
 * Ad hoc polymorphic algebra (can be specified at runtime).
 */

interface MatrixAlgebra<T, R : Ring<T>> {
  val ring: R

  infix fun <M : Matrix<T, R, M>> Matrix<T, R, M>.plus(that: Matrix<T, R, M>): M =
    join(that) { i, j -> with(ring) { this@plus[i][j] + that[i][j] } }

  infix fun List<T>.dot(es: List<T>): T =
    with(ring) { zip(es).map { (a, b) -> a * b }.reduce { a, b -> a + b } }

  infix fun <M : Matrix<T, R, M>> Matrix<T, R, M>.times(that: Matrix<T, R, M>): M =
    join(that) { i, j -> this[i] dot that[j] }

  companion object {
    operator fun <T, R : Ring<T>> invoke(ring: R) =
      object : MatrixAlgebra<T, R> {
        override val ring: R = ring
      }
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

val INTEGER_ALGEBRA = MatrixAlgebra(
  Ring(
    nil = 0,
    one = 1,
    plus = { a, b -> a + b },
    times = { a, b -> a * b }
  )
)

val DOUBLE_ALGEBRA = MatrixAlgebra(
  Ring(
    nil = 0.0,
    one = 1.0,
    plus = { a, b -> a + b },
    times = { a, b -> a * b }
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

abstract class AbstractMatrix<T, M: AbstractMatrix<T, M>> constructor(
  override val algebra: MatrixAlgebra<T, Ring<T>>,
  override val numRows: Int,
  override val numCols: Int = numRows,
  override val data: List<T>,
) : Matrix<T, Ring<T>, M> {
  val values by lazy { data.toSet() }
  override fun toString() =
    data.maxOf { it.toString().length + 2 }.let { pad ->
      data.foldIndexed("") { i, a, b ->
        a + "$b".padEnd(pad, ' ') + " " + if (i > 0 && (i + 1) % numCols == 0) "\n" else ""
      }
    }

  override fun equals(other: Any?) =
    other is FreeMatrix<*> && data.zip(other.data).all { (a, b) -> a == b } ||
      other is Matrix<*, *, *> && other.data == data

  override fun hashCode() = data.hashCode()
}

// A free matrix has no associated algebra by default. If you try to do math
// with the default implementation it will fail at runtime.
open class FreeMatrix<T> constructor(
  override val algebra: MatrixAlgebra<T, Ring<T>> =
    object : MatrixAlgebra<T, Ring<T>> { override val ring: Ring<T> by lazy { TODO() } },
  override val numRows: Int,
  override val numCols: Int = numRows,
  override val data: List<T>,
) : AbstractMatrix<T, FreeMatrix<T>>(algebra, numRows, numCols, data) {
  constructor(elements: List<T>) : this(
    numRows = sqrt(elements.size.toDouble()).toInt(),
    data = elements
  )

  constructor(numRows: Int, numCols: Int = numRows, f: (Int, Int) -> T) : this(
    numRows = numRows,
    numCols = numCols,
    data = List(numRows * numCols) { f(it / numRows, it % numCols) }
  )

  constructor(vararg rows: T) : this(rows.toList())
}

// Concrete subclasses
open class BooleanMatrix constructor(
  override val algebra: MatrixAlgebra<Boolean, Ring<Boolean>> = BOOLEAN_ALGEBRA,
  override val numRows: Int,
  override val numCols: Int = numRows,
  override val data: List<Boolean>,
) : AbstractMatrix<Boolean, BooleanMatrix>(algebra, numRows, numCols, data) {
  constructor(elements: List<Boolean>) : this(
    algebra = BOOLEAN_ALGEBRA,
    numRows = sqrt(elements.size.toDouble()).toInt(),
    data = elements
  )

  constructor(numRows: Int, numCols: Int = numRows, f: (Int, Int) -> Boolean) : this(
    numRows = numRows,
    numCols = numCols,
    data = List(numRows * numCols) { f(it / numRows, it % numCols) }
  )

  constructor(vararg rows: Short) : this(rows.fold("") { a, b -> a + b })
  constructor(vararg rows: String) :
    this(rows.fold("") { a, b -> a + b }
      .toCharArray().let { chars ->
        val values = chars.distinct()
        assert(values.size == 2) { "Expected two values" }
        chars.map { it == values[0] }
      }
    )

  // TODO: Implement Four Russians for speedy boolean matmuls https://arxiv.org/pdf/0811.1714.pdf#page=5
  override fun times(that: BooleanMatrix): BooleanMatrix = super.times(that)

  val isFull by lazy { data.all { it } }

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
}

open class IntegerMatrix constructor(
  override val algebra: MatrixAlgebra<Int, Ring<Int>> = INTEGER_ALGEBRA,
  override val numRows: Int,
  override val numCols: Int = numRows,
  override val data: List<Int>,
) : AbstractMatrix<Int, IntegerMatrix>(algebra, numRows, numCols, data) {
  constructor(elements: List<Int>) : this(
    algebra = INTEGER_ALGEBRA,
    numRows = sqrt(elements.size.toDouble()).toInt(),
    data = elements
  )

  constructor(numRows: Int, numCols: Int = numRows, f: (Int, Int) -> Int) : this(
    numRows = numRows,
    numCols = numCols,
    data = List(numRows * numCols) { f(it / numRows, it % numCols) }
  )

  constructor(vararg rows: Int) : this(rows.toList())

}

open class DoubleMatrix constructor(
  override val algebra: MatrixAlgebra<Double, Ring<Double>> = DOUBLE_ALGEBRA,
  override val numRows: Int,
  override val numCols: Int = numRows,
  override val data: List<Double>,
) : AbstractMatrix<Double, DoubleMatrix>(algebra, numRows, numCols, data) {
  constructor(elements: List<Double>) : this(
    algebra = DOUBLE_ALGEBRA,
    numRows = sqrt(elements.size.toDouble()).toInt(),
    data = elements
  )

  constructor(numRows: Int, numCols: Int = numRows, f: (Int, Int) -> Double) : this(
    numRows = numRows,
    numCols = numCols,
    data = List(numRows * numCols) { f(it / numRows, it % numCols) }
  )

  constructor(vararg rows: Double) : this(rows.toList())
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