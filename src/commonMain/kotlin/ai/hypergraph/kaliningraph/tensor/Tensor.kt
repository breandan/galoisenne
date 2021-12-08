package ai.hypergraph.kaliningraph.tensor

import ai.hypergraph.kaliningraph.*
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

interface Matrix<T, A : Ring<T>, M : Matrix<T, A, M>> : SparseTensor<Triple<Int, Int, T>> {
  val algebra: A
  val data: List<T>
  override val map: MutableMap<Triple<Int, Int, T>, Int> get() = TODO()

  val numRows: Int
  val numCols: Int

  // Only include nonzero indices for sparse matrices?
  val indices get() = allPairs(numRows, numCols)
  val rows get() = data.chunked(numCols)
  val cols get() = (0 until numCols).map { c -> rows.map { it[c] } }

  operator fun plus(t: M): M = join(t) { i, j -> with(algebra) { this@Matrix[i, j] + t[i, j] } }
  operator fun times(t: M): M = join(t) { i, j -> this[i] dot t.transpose()[j] }

  infix fun List<T>.dot(es: List<T>): T =
    with(algebra) { zip(es).map { (a, b) -> a * b }.reduce{ a, b -> a + b } }

  // Constructs a new instance with the same concrete matrix type
  fun new(numRows: Int, numCols: Int, data: List<T>, algebra: A): M
// TODO = this::class.primaryConstructor!!.call(algebra, numRows, numCols, data) as M


  fun join(that: M, idxs: Set<Pair<Int, Int>> = allPairs(numRows, that.numCols), op: (Int, Int) -> T): M =
    require(numCols == that.numRows) {
      "Dimension mismatch: $numRows,$numCols . ${that.numRows},${that.numCols}"
    }.let { new(numRows, that.numCols, idxs.map { (i, j) -> op(i, j) }, algebra) }

  operator fun get(r: Any, c: Any): T = TODO("Implement support for named indexing")
  operator fun get(r: Int, c: Int): T = data[r * numCols + c]
  operator fun get(r: Int): List<T> =
    data.toList().subList(r * numCols, r * numCols + numCols)

  fun transpose(): M = new(numCols, numRows, indices.map { (i, j) -> this[j, i] }, algebra)
}

// https://www.ijcai.org/Proceedings/2020/0685.pdf
val BOOLEAN_ALGEBRA = Ring.of(
  nil = false,
  one = true,
  plus = { a, b -> a || b },
  times = { a, b -> a && b }
)

val INTEGER_FIELD = Field.of(
  nil = 0,
  one = 1,
  plus = { a, b -> a + b },
  minus = { a, b -> a - b },
  times = { a, b -> a * b },
  div = { _, _ -> throw NotImplementedError("Division not defined on integer field.") }
)

val DOUBLE_FIELD = Field.of(
  nil = 0.0,
  one = 1.0,
  plus = { a, b -> a + b },
  minus = { a, b -> a - b },
  times = { a, b -> a * b },
  div = { a, b -> a / b }
)

val MINPLUS_ALGEBRA = Ring.of(
  nil = Int.MAX_VALUE,
  one = 0,
  plus = { a, b -> min(a, b) },
  times = { a, b -> a + b }
)

val MAXPLUS_ALGEBRA = Ring.of(
  nil = Int.MIN_VALUE,
  one = 0,
  plus = { a, b -> max(a, b) },
  times = { a, b -> a + b }
)

private fun <T> TODO_ALGEBRA(t: T) = Ring.of(
  nil = t,
  one = t,
  plus = { a, b -> TODO() },
  times = { a, b -> TODO() }
)

abstract class AbstractMatrix<T, A: Ring<T>, M: AbstractMatrix<T, A, M>> constructor(
  override val numRows: Int,
  override val numCols: Int = numRows,
  override val data: List<T>,
  override val algebra: A,
) : Matrix<T, A, M> {
  val values by lazy { data.toSet() }
  override val map: MutableMap<Triple<Int, Int, T>, Int> by lazy {
    indices.fold(mutableMapOf()) { map, (r, c) ->
      val element = get(r, c)
      if (element != algebra.nil) map[Triple(r, c, element)] = 1
      map
    }
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
  override val numRows: Int,
  override val numCols: Int = numRows,
  override val data: List<T>,
  override val algebra: Ring<T> = TODO_ALGEBRA(data.first()),
) : AbstractMatrix<T, Ring<T>, FreeMatrix<T>>(numRows, numCols, data, algebra) {
  constructor(elements: List<T>) : this(
    numRows = sqrt(elements.size.toDouble()).toInt(),
    data = elements
  )

  constructor(numRows: Int, numCols: Int = numRows, f: (Int, Int) -> T) : this(
    numRows = numRows,
    numCols = numCols,
    data = List(numRows * numCols) { f(it / numCols, it % numCols) }
  )

  constructor(vararg rows: T) : this(rows.toList())

  override fun new(numRows: Int, numCols: Int, data: List<T>, algebra: Ring<T>) =
    FreeMatrix(numRows, numCols, data, algebra)
}

// Concrete subclasses
open class BooleanMatrix constructor(
  override val numRows: Int,
  override val numCols: Int = numRows,
  override val data: List<Boolean>,
  override val algebra: Ring<Boolean> = BOOLEAN_ALGEBRA,
) : AbstractMatrix<Boolean, Ring<Boolean>, BooleanMatrix>(numRows, numCols, data, algebra) {
  constructor(elements: List<Boolean>) : this(
    algebra = BOOLEAN_ALGEBRA,
    numRows = sqrt(elements.size.toDouble()).toInt(),
    data = elements
  )

  constructor(numRows: Int, numCols: Int = numRows, f: (Int, Int) -> Boolean) : this(
    numRows = numRows,
    numCols = numCols,
    data = List(numRows * numCols) { f(it / numCols, it % numCols) }
  )

  constructor(vararg rows: Short) : this(rows.fold("") { a, b -> a + b })
  constructor(rows: String) : this(
    rows.filter { !it.isWhitespace() }.toCharArray().let { chars ->
      val values = chars.distinct()
      require(values.size <= 2) { "Expected two values or less" }
      values.maxOrNull()!!.let { hi -> chars.map { it == hi } }
    }
  )

  // TODO: Implement Four Russians for speedy boolean matmuls https://arxiv.org/pdf/0811.1714.pdf#page=5
  // override fun BooleanMatrix.times(t: BooleanMatrix): BooleanMatrix = TODO()

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

  override fun new(numRows: Int, numCols: Int, data: List<Boolean>, algebra: Ring<Boolean>) =
     BooleanMatrix(numRows, numCols, data, algebra)
}

open class DoubleMatrix constructor(
  override val numRows: Int,
  override val numCols: Int = numRows,
  override val data: List<Double>,
  override val algebra: Field<Double> = DOUBLE_FIELD,
) : AbstractMatrix<Double, Field<Double>, DoubleMatrix>(numRows, numCols, data, algebra) {
  constructor(elements: List<Double>) : this(
    algebra = DOUBLE_FIELD,
    numRows = sqrt(elements.size.toDouble()).toInt(),
    data = elements
  )

  constructor(numRows: Int, numCols: Int = numRows, f: (Int, Int) -> Double) : this(
    numRows = numRows,
    numCols = numCols,
    data = List(numRows * numCols) { f(it / numCols, it % numCols) }
  )

  constructor(vararg rows: Double) : this(rows.toList())

  operator fun minus(that: DoubleMatrix): DoubleMatrix =
    join(that) { i, j -> algebra.minus(this[i, j], that[i][j]) }

  companion object {
    fun random(size: Int) = DoubleMatrix(size) { _, _ -> Random.nextDouble() }
  }

  override fun new(numRows: Int, numCols: Int, data: List<Double>, algebra: Field<Double>) =
    DoubleMatrix(numRows, numCols, data, algebra)
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