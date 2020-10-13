package edu.mcgill.kaliningraph.matrix

import org.ejml.data.BMatrixRMaj
import org.ejml.data.DMatrix
import org.ejml.data.DMatrixRMaj
import org.ejml.data.DMatrixSparseCSC
import org.ejml.data.Matrix
import kotlin.math.sqrt
import kotlin.random.Random

// https://www.ijcai.org/Proceedings/2020/0685.pdf

open class BMat(val rows: Int, val cols: Int, open vararg val data: Boolean) {
  constructor(size: Int, vararg data: Boolean) : this(size, size, *data)
  constructor(rows: Int, cols: Int, f: (Int, Int) -> Boolean) :
    this(rows, cols, *BooleanArray(rows * cols) { f(it / cols, it % cols) })

  open operator fun get(r: Int, c: Int) = data[r * cols + c]
  open operator fun get(r: Int) = data.toList().subList(r * cols, r * cols + cols).toBooleanArray()

  open fun transpose(): BMat = BMat(cols, rows) { r, c -> this[c, r] }
}

class BSqMat(override vararg val data: Boolean) : BMat(sqrt(data.size.toDouble()).toInt(), *data) {
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

  fun copy() = BSqMat(size) { i, j -> this[i, j] }

  constructor(vararg rows: String) :
    this(*rows.fold("") { a, b -> a + b }
      .toCharArray().let { chars ->
        val values = chars.distinct()
        assert(values.size == 2) { "Expected two values" }
        chars.map { it == values[0] }.toBooleanArray()
      }
    )

  init {
    assert(size * size == data.size) { "Expected square matrix!" }
    contents = data.toList().chunked(size)
      .map { it.toBooleanArray() }.toTypedArray()
  }

  constructor(vararg rows: Int) : this(rows.fold("") { a, b -> a + b })

  // TODO: https://arxiv.org/pdf/0811.1714.pdf#page=5
  operator fun times(that: BSqMat): BSqMat {
    assert(size == that.size) { "Dimension mismatch: ${size}x${that.size}" }
    val bMatT = that.transpose()
    return BSqMat(size) { i, j -> this[i] * bMatT[j] }
  }

  operator fun times(that: BMat): BMat {
    assert(size == that.rows) { "Dimension mismatch: ${size}x${that.rows}" }
    val bMatT = that.transpose()
    return BMat(size, that.cols) { i, j -> this[i] * bMatT[j] }
  }

  operator fun plus(that: BSqMat): BSqMat {
    assert(size == that.size) { "Dimension mismatch: ${size}x${that.size}" }
    return BSqMat(
      *contents.zip(that.contents) { a, b -> a.zip(b) { c, d -> c || d } }.flatten().toBooleanArray()
    )
  }

  private operator fun BooleanArray.times(other: BooleanArray) =
    zip(other) { a, b -> a && b }.reduce { a, b -> a || b }

  override fun transpose(): BSqMat = BSqMat(size) { r, c -> this[c, r] }

  override fun toString() = data.foldIndexed("") { i, a, b ->
    a + (if (b) 1 else 0) + "  " +
      if (i > 0 && (i + 1) % size == 0) "\n" else ""
  }

  fun toEJML() = BMatrixRMaj(size, size).let {
    for (i in 0 until size) for (j in 0 until size) it[i, j] = this[i, j]
  }

  fun toDMat() = DMatrixRMaj(contents.map { it.map { if (it) 1.0 else 0.0 }.toDoubleArray() }.toTypedArray())

  override fun equals(other: Any?): Boolean {
    if (other !is BSqMat) return false

    for (i in 0 until size) for (j in 0 until size)
      if (this[i, j] != other[i, j]) return false

    return true
  }

  override fun hashCode() = contents.contentDeepHashCode()
}

fun DMatrix.toBMat() =
  BSqMat(numRows) { i, j -> get(i, j) < 0.5 }

fun BMatrixRMaj.toBMat() =
  BSqMat(numRows) { i, j -> get(i, j) }