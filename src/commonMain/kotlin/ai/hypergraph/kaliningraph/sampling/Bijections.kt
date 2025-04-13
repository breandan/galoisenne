package ai.hypergraph.kaliningraph.sampling

import ai.hypergraph.kaliningraph.choose
import ai.hypergraph.kaliningraph.types.to
import kotlin.math.*


// Bijection between k-combinations and integers
// https://en.wikipedia.org/wiki/Combinatorial_number_system
fun Set<Int>.encode(): Int {
  var (k, i, total) = size to 0 to 0
  val asArray = toIntArray()

  while (i < size) {
    val result = asArray[i] choose k
    total += result
    k -= 1
    i += 1
  }

  return total
}

fun Int.decodeCombo(k: Int): Set<Int> {
  var choice: Int = k - 1
  while (choice choose k < this) choice++

  var N = this
  var kk = k
  val result = mutableSetOf<Int>()
  (choice downTo 0).forEach { ch ->
    if (ch choose kk <= N) {
      N -= ch choose kk--
      result.add(ch)
    }
  }
  return result
}

fun ndBoxUnpair(lengths: List<Int>, index: Int): List<Int> {
  val n = lengths.size
  val indexes = MutableList(n) { 0 }
  var dimensionProduct = 1
  for (dimension in (n - 1) downTo 0) {
    indexes[dimension] = index / dimensionProduct % lengths[dimension]
    dimensionProduct *= lengths[dimension]
  }
  return indexes
}

fun ndBoxPair(lengths: List<Int>, indexes: List<Int>): Int {
  val n = lengths.size
  var index = 0
  var dimensionProduct = 1
  for (dimension in (n - 1) downTo 0) {
    index += indexes[dimension] * dimensionProduct
    dimensionProduct *= lengths[dimension]
  }
  return index
}

fun Int.pow(n: Int): Int = when (n) {
  0 -> 1
  1 -> this
  else -> {
    var result = this
    for (i in 1..<n) {
      result *= this
    }
    result
  }
}

/**
 * Constructs a bijection between ℕ <-> ℕᵏ using Szudzik's pairing function
 * generalized to n-tuples, n.b. optimally compact for hypercubic shells.
 */

fun List<Int>.tupled(): Int {
  val n = size

  if (n == 0) return 0

  val shell = max()

  fun recursiveIndex(dim: Int): Int {
    val sliceDims = n - dim - 1
    val subshellCount = (shell + 1).pow(sliceDims) - shell.pow(sliceDims)
    val indexI = this[dim]
    return if (indexI == shell) {
      subshellCount * shell + ndBoxPair(List(sliceDims) { shell + 1 }, slice(dim + 1..<n))
    } else {
      subshellCount * indexI + recursiveIndex(dim + 1)
    }
  }
  return shell.pow(n) + recursiveIndex(0)
}

fun Int.untupled(n: Int): List<Int> {
  val shell = toDouble().pow(1.0 / n).toInt()

  fun recursiveIndexes(dim: Int, remaining: Int): List<Int> =
    if (dim == n - 1) {
      listOf(shell)
    } else {
      val sliceDims = n - dim - 1
      val subshellCount = (shell + 1).pow(sliceDims) - shell.pow(sliceDims)
      val indexI = min(remaining / subshellCount, shell)
      if (indexI == shell) {
        listOf(shell) + ndBoxUnpair(List(sliceDims) { shell + 1 }, remaining - subshellCount * shell)
      } else {
        listOf(indexI) + recursiveIndexes(dim + 1, remaining - subshellCount * indexI)
      }
    }

  return recursiveIndexes(0, this - shell.pow(n))
}