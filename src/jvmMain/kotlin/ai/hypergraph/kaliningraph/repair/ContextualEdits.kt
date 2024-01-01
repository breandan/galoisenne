package ai.hypergraph.kaliningraph.repair

import java.io.File
import kotlin.math.pow

// Divesity: lower is more diverse, higher is less diverse, 1.0 is natural frequencies
fun File.readTrigramStats(diversity: Double = 1.0): CEADist =
  readLines().drop(1).map { it.split(", ") }.associate {
    ContextEdit(
      type = EditType.valueOf(it[0].trim()),
      context = Context(it[1], it[2], it[3]),
      newMid = it[4]
    ) to it[5].trim().toDouble().pow(diversity).toInt().coerceAtLeast(1)
  }.let { CEADist(it) }