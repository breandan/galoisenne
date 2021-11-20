package ai.hypergraph.kaliningraph.tensor

import ai.hypergraph.kaliningraph.times
import ai.hypergraph.kaliningraph.types.*
import kotlin.math.*
import kotlin.random.Random

/**
 * Generic matrix which supports overloadable addition and multiplication
 * using an abstract algebra (e.g. tropical semiring). Useful for many
 * problems in graph theory.
 *
 * @see [MatrixRing]
 */

interface Matrix<T, R : Ring<T, R>, M : Matrix<T, R, M>> : SparseTensor<Triple<Int, Int, T>> {
  val data: List<T>
  override val map: MutableMap<Triple<Int, Int, T>, Int> get() = TODO()

  val numRows: Int
  val numCols: Int
  val algebra: MatrixRing<T, R>

  val indices get() = (0 until numRows) * (0 until numCols)
  val rows get() = data.chunked(numCols)
  val cols get() = (0 until numCols).map { c -> rows.map { it[c] } }

  operator fun times(that: M): M = with(algebra) { this@Matrix times that }
  operator fun plus(that: M): M = with(algebra) { this@Matrix plus that }

  // Constructs a new instance with the same concrete matrix type
  fun new(numRows: Int, numCols: Int, algebra: MatrixRing<T, R>, data: List<T>): M
// TODO = this::class.primaryConstructor!!.call(algebra, numRows, numCols, data) as M

  fun join(that: Matrix<T, R, M>, op: (Int, Int) -> T): M =
    if (numCols != that.numRows) {
      throw Exception("Dimension mismatch: $numRows,$numCols . ${that.numRows},${that.numCols}")
    } else { new(numCols, that.numRows, algebra, indices.map { (i, j) -> op(i, j) }) }

  operator fun get(r: Any, c: Any): T = TODO("Implement support for named indexing")
  operator fun get(r: Int, c: Int): T = data[r * numCols + c]
  operator fun get(r: Int): List<T> =
    data.toList().subList(r * numCols, r * numCols + numCols)

  fun transpose(): M = new(numCols, numRows, algebra, indices.map { (i, j) -> this[j, i] })
}

/**
 * Ad hoc polymorphic algebra (can be specified at runtime).
 */

interface MatrixRing<T, R : Ring<T, R>> {
  val algebra: R

  infix fun <M : Matrix<T, R, M>> Matrix<T, R, M>.plus(that: Matrix<T, R, M>): M =
    join(that) { i, j -> with(this@MatrixRing.algebra) { this@plus[i][j] + that[i][j] } }

  infix fun List<T>.dot(es: List<T>): T =
    with(algebra) { zip(es).map { (a, b) -> a * b }.reduce { a, b -> a + b } }

  infix fun <M : Matrix<T, R, M>> Matrix<T, R, M>.times(that: Matrix<T, R, M>): M =
    join(that) { i, j -> this[i] dot that[j] }

  companion object {
    operator fun <T, R : Ring<T, R>> invoke(ring: R) =
      object : MatrixRing<T, R> { override val algebra: R = ring }
  }
}

// Ring with additive inverse
interface MatrixField<T, R : Field<T, R>>: MatrixRing<T, R> {
  override val algebra: R

  infix fun <M : Matrix<T, R, M>> Matrix<T, R, M>.minus(that: Matrix<T, R, M>): M =
    join(that) { i, j -> with(this@MatrixField.algebra) { this@minus[i][j] - that[i][j] } }

  companion object {
    operator fun <T, R : Field<T, R>> invoke(field: R) =
      object : MatrixField<T, R> { override val algebra: R = field }
  }
}

val BOOLEAN_ALGEBRA = MatrixRing(
  // https://www.ijcai.org/Proceedings/2020/0685.pdf
  Ring.of(
    nil = false,
    one = true,
    plus = { a, b -> a || b },
    times = { a, b -> a && b }
  )
)

val INTEGER_FIELD = MatrixField(
  Field.of(
    nil = 0,
    one = 1,
    plus = { a, b -> a + b },
    minus = { a, b -> a - b },
    times = { a, b -> a * b },
    div = { _, _ -> throw NotImplementedError("Division not defined on integer field.") }
  )
)

val DOUBLE_FIELD = MatrixField(
  Field.of(
    nil = 0.0,
    one = 1.0,
    plus = { a, b -> a + b },
    minus = { a, b -> a - b },
    times = { a, b -> a * b },
    div = { a, b -> a / b }
  )
)

val MINPLUS_ALGEBRA = MatrixRing(
  Ring.of(
    nil = Int.MAX_VALUE,
    one = 0,
    plus = { a, b -> min(a, b) },
    times = { a, b -> a + b }
  )
)

val MAXPLUS_ALGEBRA = MatrixRing(
  Ring.of(
    nil = Int.MIN_VALUE,
    one = 0,
    plus = { a, b -> max(a, b) },
    times = { a, b -> a + b }
  )
)

abstract class AbstractMatrix<T, R: Ring<T, R>, M: AbstractMatrix<T, R, M>> constructor(
  override val algebra: MatrixRing<T, R>,
  override val numRows: Int,
  override val numCols: Int = numRows,
  override val data: List<T>,
) : Matrix<T, R, M> {
  val values by lazy { data.toSet() }
  override val map: MutableMap<Triple<Int, Int, T>, Int> by lazy {
    indices.fold(mutableMapOf<Triple<Int, Int, T>, Int>()) { map, (r, c) ->
      val element = get(r, c)
      if (element != algebra.algebra.nil) map[Triple(r, c, element)] = 1
      map
    } as MutableMap<Triple<Int, Int, T>, Int>
  }

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
  override val algebra: MatrixRing<T, Ring.of<T>> =
    object : MatrixRing<T, Ring.of<T>> { override val algebra: Ring.of<T> by lazy { TODO() } },
  override val numRows: Int,
  override val numCols: Int = numRows,
  override val data: List<T>,
) : AbstractMatrix<T, Ring.of<T>, FreeMatrix<T>>(algebra, numRows, numCols, data) {
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

  override fun new(numRows: Int, numCols: Int, algebra: MatrixRing<T, Ring.of<T>>, data: List<T>) =
    FreeMatrix(algebra, numRows, numCols, data)
}

// Concrete subclasses
open class BooleanMatrix constructor(
  override val algebra: MatrixRing<Boolean, Ring.of<Boolean>> = BOOLEAN_ALGEBRA,
  override val numRows: Int,
  override val numCols: Int = numRows,
  override val data: List<Boolean>,
) : AbstractMatrix<Boolean, Ring.of<Boolean>, BooleanMatrix>(algebra, numRows, numCols, data) {
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
        if(values.size != 2) { throw Exception("Expected two values") }
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

  override fun new(numRows: Int, numCols: Int, algebra: MatrixRing<Boolean, Ring.of<Boolean>>, data: List<Boolean>) =
     BooleanMatrix(algebra, numRows, numCols, data)
}

open class DoubleMatrix constructor(
  override val algebra: MatrixField<Double, Field.of<Double>> = DOUBLE_FIELD,
  override val numRows: Int,
  override val numCols: Int = numRows,
  override val data: List<Double>,
) : AbstractMatrix<Double, Field.of<Double>, DoubleMatrix>(algebra, numRows, numCols, data) {
  constructor(elements: List<Double>) : this(
    algebra = DOUBLE_FIELD,
    numRows = sqrt(elements.size.toDouble()).toInt(),
    data = elements
  )

  constructor(numRows: Int, numCols: Int = numRows, f: (Int, Int) -> Double) : this(
    numRows = numRows,
    numCols = numCols,
    data = List(numRows * numCols) { f(it / numRows, it % numCols) }
  )

  constructor(vararg rows: Double) : this(rows.toList())

  operator fun minus(that: DoubleMatrix): DoubleMatrix = with(algebra) { this@DoubleMatrix minus that }
  override fun new(numRows: Int, numCols: Int, algebra: MatrixRing<Double, Field.of<Double>>, data: List<Double>) =
    DoubleMatrix(algebra as MatrixField<Double, Field.of<Double>>, numRows, numCols, data)
}

fun DoubleMatrix.toBMat(
  threshold: Double = (data.maxOf { it } + data.minOf { it }) / 2,
  partitionFn: (Double) -> Boolean = { it > threshold }
) = BooleanMatrix(numRows, numCols) { i, j -> partitionFn(get(i, j)) }

operator fun BooleanMatrix.times(mat: DoubleMatrix): DoubleMatrix = toDoubleMatrix() * mat
operator fun BooleanMatrix.plus(mat: DoubleMatrix): DoubleMatrix = toDoubleMatrix() + mat
operator fun DoubleMatrix.minus(mat: BooleanMatrix): DoubleMatrix = this - mat.toDoubleMatrix()
fun BooleanMatrix.toDoubleMatrix() = DoubleMatrix(numRows, numCols) { i, j -> if (get(i, j)) 1.0 else 0.0 }

// TODO: Naperian functors
// https://www.cs.ox.ac.uk/people/jeremy.gibbons/publications/aplicative.pdf
// https://github.com/NickHu/naperian-functors/blob/master/src/Data/Naperian.hs
// https://github.com/NickHu/naperian-functors/blob/master/src/Data/Naperian/Vector.hs
// https://github.com/NickHu/naperian-functors/blob/master/src/Data/Naperian/Symbolic.hs
// "The main idea is that a rank-n array is essentially a data structure of type
// D₁(D₂(...(Dₙ a))), where each Dᵢ is a dimension : a container type, categorically
// a functor; one might think in the first instance of lists."

// Alternatively: a length-2ⁿ array which can be "parsed" into a certain shape?
// See: http://conal.net/talks/can-tensor-programming-be-liberated.pdf
interface SparseTensor<T/*Should be a named tuple or dataclass of some kind*/> {
  // TODO: Precompute specific [Borel] subsets of T's attributes that we expect to be queried at runtime
// e.g. (n-1)-D slices and 1D fibers
// https://mathoverflow.net/questions/393427/generalization-of-sinkhorn-s-theorem-to-stochastic-tensors
// private val marginals: MutableMap<List<T>, Int> = mutableMapOf()
  val map: MutableMap<T, Int>
  operator fun get(t: T) = map.getOrElse(t) { 0 }

//  TODO: Support mutability but also map-reduce-ability/merge-ability for parallelism
//  operator fun plus(other: SparseTensor<T>) = SparseTensor(map = this.map + other.map)
//  operator fun MutableMap<T, Int>.plus(map: MutableMap<T, Int>): MutableMap<T, Int> =
//    HashMap(this).apply { map.forEach { (k, v) -> merge(k, v, Int::plus) } }

  operator fun set(index: T, i: Int) { map[index] = i }

  fun count(selector: (T) -> Boolean) =
    map.entries.sumOf { if(selector(it.key)) it.value else 0 }
}