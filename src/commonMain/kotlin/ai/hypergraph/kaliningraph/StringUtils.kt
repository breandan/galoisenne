package ai.hypergraph.kaliningraph

import ai.hypergraph.kaliningraph.tensor.FreeMatrix
import ai.hypergraph.kaliningraph.tensor.transpose
import kotlin.math.ceil
import kotlin.math.min

fun List<String>.formatAsGrid(cols: Int = -1): FreeMatrix<String> {
  fun String.tok() = split(" -> ")
  fun String.LHS() = tok()[0]
  fun String.RHS() = tok()[1]
  val groups = groupBy { it.LHS() }

  fun List<String>.rec() = if (cols == -1) // Minimize whitespace over all grids with a predefined number of columns
    (3..5).map { formatAsGrid(it) }.minBy { it.toString().length }
  else sortedWith(compareBy(
    { groups[it.LHS()]!!.maxOf { it.length } }, // Shortest longest pretty-printed production comes first
    { -groups[it.LHS()]!!.size }, // Take small groups first
    { it.LHS() }, // Must never split up two LHS productions
    { it.length }
  )).let { productions ->
    val (cols, rows) = cols to ceil(productions.size.toDouble() / cols).toInt()
    val padded = productions + List(cols * rows - productions.size) { "" }
    FreeMatrix(cols, rows, padded).transpose
  }.let { up ->
    FreeMatrix(up.numRows, up.numCols) { r, c ->
      if (up[r, c].isEmpty()) return@FreeMatrix ""
      val (lhs, rhs) = up[r, c].split(" -> ").let { it[0] to it[1] }
      val lp = lhs.padStart(up.transpose[c].maxOf { it.substringBefore(" -> ").length })
      val rp = rhs.padEnd(up.transpose[c].maxOf { it.substringAfter(" -> ").length })
      "$lp → $rp"
    }
  }

  return rec()
}

private fun <T> List<List<T>>.col(i: Int) = map { it[i] }

// https://en.wikipedia.org/wiki/Seam_carving
fun String.carveSeams(toRemove: Regex = Regex("\\s{2,}")): String =
  replace("  |  ", "    ")
    .lines().filter { it.isNotBlank() }.map { it.split("→") }.let { toMerge ->
    val minCols = toMerge.minOf { it.size }
    val takeAway = (0 until minCols).map { toMerge.col(it).minOf { toRemove.find(it)!!.value.length } }
    val subs = takeAway.map { List(it) { " " }.joinToString("") }
    toMerge.joinToString("\n", "\n") {
      it.mapIndexed { i, it -> if (i < minCols) it.replaceFirst(subs[i], "   ") else it }.joinToString("→")
        .drop(4).dropLast(3)
    }
  }

fun <T> levenshtein(o1: List<T>, o2: List<T>): Int {
  var prev = IntArray(o2.size + 1)
  for (j in 0 until o2.size + 1) prev[j] = j
  for (i in 1 until o1.size + 1) {
    val curr = IntArray(o2.size + 1)
    curr[0] = i
    for (j in 1 until o2.size + 1) {
      val d1 = prev[j] + 1
      val d2 = curr[j - 1] + 1
      val d3 = prev[j - 1] + if (o1[i - 1] == o2[j - 1]) 0 else 1
      curr[j] = min(min(d1, d2), d3)
    }

    prev = curr
  }
  return prev[o2.size]
}