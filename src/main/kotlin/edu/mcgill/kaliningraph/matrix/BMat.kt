package edu.mcgill.kaliningraph.matrix

import org.ejml.data.BMatrixRMaj
import org.ejml.data.DMatrixRMaj
import org.ejml.kotlin.times
import kotlin.math.sqrt
import kotlin.random.Random

// https://www.ijcai.org/Proceedings/2020/0685.pdf

class BMat(vararg val data: Boolean) {
  val contents: Array<BooleanArray>
  val size: Int = sqrt(data.size.toDouble()).toInt()
  val values = data.distinct().sorted()

  companion object {
    fun grayCode(size: Int): BMat = TODO()
    fun ones(size: Int) = BMat(size, true)
    fun zeros(size: Int) = BMat(size, false)
    fun one(size: Int) = BMat(size) { i, j -> i == j }
    fun random(size: Int) = BMat(size) { _ -> Random.nextBoolean() }
  }

  constructor(size: Int, b: Boolean) : this(*BooleanArray(size * size) { b })
  constructor(size: Int, f: (Int) -> Boolean) : this(*BooleanArray(size * size) { f(it) })
  constructor(size: Int, f: (Int, Int) -> Boolean) : this(*BooleanArray(size * size) { f(it / size, it % size) })

  fun copy() = BMat(this.size) { i, j -> this[i, j] }

  private operator fun get(r: Int, c: Int) = contents[r][c]
  private operator fun get(r: Int) = contents[r]

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
    this.contents = data.toList().chunked(size)
      .map { it.toBooleanArray() }.toTypedArray()
  }

  constructor(vararg rows: Int) : this(rows.fold("") { a, b -> a + b })

  // TODO: https://arxiv.org/pdf/0811.1714.pdf#page=5
  operator fun times(that: BMat): BMat {
    assert(size == that.size) { "Dimension mismatch: ${size}x${that.size}" }
    val bMatT = that.transpose()
    return BMat(size) { i, j -> this[i] * bMatT[j] }
  }

  operator fun plus(that: BMat): BMat {
    assert(size == that.size) { "Dimension mismatch: ${size}x${that.size}" }
    return BMat(
      *contents.zip(that.contents) { a, b -> a.zip(b) { c, d -> c || d } }.flatten().toBooleanArray()
    )
  }

  private operator fun BooleanArray.times(other: BooleanArray) =
    zip(other) { a, b -> a && b }.reduce { a, b -> a || b }

  fun transpose() = BMat(size) { r, c -> this[c, r] }

  override fun toString() = data.foldIndexed("") { i, a, b ->
    a + (if (b) 1 else 0) + "  " +
      if (i > 0 && (i + 1) % size == 0) "\n" else ""
  }

  fun toEJML() = BMatrixRMaj(size, size).let {
    for (i in 0 until size) for (j in 0 until size) it[i, j] = this[i, j]
  }

  fun toDMat() = DMatrixRMaj(contents.map { it.map { if (it) 1.0 else 0.0 }.toDoubleArray() }.toTypedArray())

  override fun equals(other: Any?): Boolean {
    if (other !is BMat) return false

    for (i in 0 until size) for (j in 0 until size)
      if (this[i, j] != other[i, j]) return false

    return true
  }

  override fun hashCode() = contents.contentDeepHashCode()
}

fun BMatrixRMaj.toBMat() =
  BMat(numRows) { i, j -> get(i, j) }

fun DMatrixRMaj.toBMat() =
  BMat(numRows) { i, j -> get(i, j) < 0.5 }