package ai.hypergraph.kaliningraph.matrix

import ai.hypergraph.kaliningraph.times
import ai.hypergraph.kaliningraph.types.*
import kotlin.math.sqrt

// Generic matrix

open class TMat<T: Group<T>>(val numRows: Int, val numCols: Int, open val data: List<T>) {
  constructor(size: Int, data: List<T>) : this(size, size, data)
  constructor(rows: Int, cols: Int, f: (Int, Int) -> T) :
      this(rows, cols, List(rows * cols) { f(it / cols, it % cols) })

  val indices = (0 until numRows) * (0 until numCols)
  val rows by lazy { data.chunked(numCols) }
  val cols by lazy { (0 until numCols).map { c -> rows.map { it[c] } } }

  open fun join(that: TMat<T>, op: (Int, Int) -> T) =
    assert(numCols == that.numRows) {
      "Dimension mismatch: $numRows,$numCols . ${that.numRows},${that.numCols}"
    }.run { TMat<T>(numCols, that.numRows, op) }

  fun <U: Group<U>> map(f: (T) -> U) = invoke(numRows, numCols, data.map(f))
  
  open operator fun <U: Group<U>> invoke(rows: Int, cols: Int, map: List<U>) = TMat(rows, cols, map)

  fun toSqMat(): TSqMat<T> =
    assert(numRows == numCols) { "Dimension mismatch: $numRows != $numCols" }
      .run { TSqMat(data) }

  open operator fun get(r: Int, c: Int) = data[r * numCols + c]
  open operator fun get(r: Int) =
    data.toList().subList(r * numCols, r * numCols + numCols)

  open fun transpose(): TMat<T> = TMat(numCols, numRows) { r, c -> this[c, r] }
}

class TSqMat<T : Group<T>>(override val data: List<T>):
  TMat<T>(sqrt(data.size.toDouble()).toInt(), data) /**TODO: Make this a [Group]/[Ring]?*/ {
  val size: Int = sqrt(data.size.toDouble()).toInt()

  constructor(size: Int, f: (Int) -> T) : this(List(size * size) { f(it) })
  constructor(size: Int, f: (Int, Int) -> T) : this(List(size * size) { f(it / size, it % size) })

  init { assert(size * size == data.size) { "Expected square matrix!" } }

  operator fun plus(that: TMat<T>) = join(that) { i, j -> this[i][j].run { this + that[i][j] } }

  operator fun times(that: TSqMat<T>) = join(that) { i, j -> this[i] * that[j] }

  operator fun times(that: TMat<T>) = join(that) { i, j -> this[i] * that[j] }

  override fun join(that: TMat<T>, op: (Int, Int) -> T): TSqMat<T> = super.join(that, op).toSqMat()

  override fun transpose() = TSqMat(cols.flatten())

  override fun toString() = data.foldIndexed("") { i, a, b ->
    a + b + " " + if (i > 0 && (i + 1) % size == 0) "\n" else ""
  }

  override fun <U : Group<U>> invoke(rows: Int, cols: Int, map: List<U>): TSqMat<U> = TSqMat(map)

  override fun equals(other: Any?) =
    other is TSqMat<*> && data.zip(other.data).all { (a, b) -> a == b }

  override fun hashCode() = data.hashCode()
}

private operator fun <E: Group<E>> List<E>.times(es: List<E>): E =
  zip(es).map { (a, b) -> a.run { a * b } }.reduce { a, b -> a.run { a + b } }