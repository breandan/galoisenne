package ai.hypergraph.kaliningraph

import ai.hypergraph.kaliningraph.tensor.FreeMatrix
import ai.hypergraph.kaliningraph.tensor.transpose
import kotlin.math.ceil

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