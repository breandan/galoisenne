package ai.hypergraph.kaliningraph.matrix

import ai.hypergraph.kaliningraph.SpsMat
import org.ejml.data.*
import org.ejml.kotlin.*
import kotlin.math.sqrt
import kotlin.random.Random

// https://www.ijcai.org/Proceedings/2020/0685.pdf

open class BMat(val rows: Int, val cols: Int, open vararg val data: Boolean) {
  constructor(size: Int, vararg data: Boolean) : this(size, size, *data)
  constructor(rows: Int, cols: Int, f: (Int, Int) -> Boolean) :
    this(rows, cols, *BooleanArray(rows * cols) { f(it / cols, it % cols) })

  open fun join(that: BMat, op: (Int, Int) -> Boolean) =
    assert(cols == that.rows) {
      "Dimension mismatch: $rows,$cols . ${that.rows},${that.cols}"
    }.run { BMat(cols, that.rows, op) }

  fun toSqMat() =
    assert(rows == cols) { "Dimension mismatch: $cols != $rows" }
      .run { BSqMat(rows) { i -> data[i] } }

  open operator fun get(r: Int, c: Int) = data[r * cols + c]
  open operator fun get(r: Int) =
    data.toList().subList(r * cols, r * cols + cols).toBooleanArray()

  open fun transpose(): BMat = BMat(cols, rows) { r, c -> this[c, r] }

  fun toEJML() = BMatrixRMaj(rows, cols).also {
    for (i in 0 until rows) for (j in 0 until cols) it[i, j] = this[i, j]
  }

  fun toEJMLSparse() = SpsMat(rows, cols, rows).also {
    for (i in 0 until rows) for (j in 0 until cols)
      it[i, j] = if (this[i, j]) 1.0 else 0.0
  }
}

class BSqMat(override vararg val data: Boolean):
  BMat(sqrt(data.size.toDouble()).toInt(), *data) {
  val isFull = data.all { it }
  val size: Int = sqrt(data.size.toDouble()).toInt()
  val values = data.distinct().sorted()
  val contents: Array<BooleanArray>

  companion object {
    fun grayCode(size: Int): BSqMat = TODO()
    fun ones(size: Int) = BSqMat(size, true)
    fun zeros(size: Int) = BSqMat(size, false)
    fun one(size: Int) = BSqMat(size) { i, j -> i == j }
    fun random(size: Int) = BSqMat(size) { _ -> Random.nextBoolean() }
  }

  constructor(size: Int, b: Boolean) : this(*BooleanArray(size * size) { b })
  constructor(size: Int, f: (Int) -> Boolean) : this(*BooleanArray(size * size) { f(it) })
  constructor(size: Int, f: (Int, Int) -> Boolean) : this(*BooleanArray(size * size) { f(it / size, it % size) })
  constructor(vararg rows: Int) : this(rows.fold("") { a, b -> a + b })
  constructor(vararg rows: String) :
    this(*rows.fold("") { a, b -> a + b }
      .toCharArray().let { chars ->
        val values = chars.distinct()
        assert(values.size == 2) { "Expected two values" }
        chars.map { it == values[0] }.toBooleanArray()
      }
    )

  fun copy() = BSqMat(size) { i, j -> this[i, j] }

  init {
    assert(size * size == data.size) { "Expected square matrix!" }
    contents = data.toList().chunked(size)
      .map { it.toBooleanArray() }.toTypedArray()
  }

  // TODO: https://arxiv.org/pdf/0811.1714.pdf#page=5
  operator fun times(that: BSqMat) =
    join(that) { i, j -> this[i] * that[j] } as BSqMat

  operator fun times(that: BMat) =
    join(that) { i, j -> this[i] * that[j] }

  operator fun plus(that: BMat) =
    join(that.toSqMat()) { i, j -> this[i, j] || that[i, j] } as BSqMat

  override fun join(that: BMat, op: (Int, Int) -> Boolean) =
    if (that is BSqMat)
      assert(size == that.size) { "Dimension mismatch: $size,${that.size}" }
        .run { BSqMat(size, op) }
    else super.join(that, op)

  private operator fun BooleanArray.times(other: BooleanArray) =
    zip(other) { a, b -> a && b }.reduce { a, b -> a || b }

  override fun transpose(): BSqMat = BSqMat(size) { r, c -> this[c, r] }

  override fun toString() = data.foldIndexed("") { i, a, b ->
    a + (if (b) 1 else 0) + " " + if (i > 0 && (i + 1) % size == 0) "\n" else ""
  }

  override fun equals(other: Any?): Boolean {
    if (other !is BSqMat) return false

    for (i in 0 until size) for (j in 0 until size)
      if (this[i, j] != other[i, j]) return false

    return true
  }

  override fun hashCode() = contents.contentDeepHashCode()
}

fun DMatrix.toBMat() = BMat(numRows, numCols) { i, j -> get(i, j) > 0.5 }
fun BMatrixRMaj.toBMat() = BMat(numRows, numCols) { i, j -> get(i, j) }
operator fun BMat.times(mat: SpsMat): SpsMat = toEJMLSparse() * mat
operator fun BMat.plus(mat: SpsMat): SpsMat = toEJMLSparse() * mat
operator fun SpsMat.minus(mat: BMat): SpsMat = this - mat.toEJMLSparse()