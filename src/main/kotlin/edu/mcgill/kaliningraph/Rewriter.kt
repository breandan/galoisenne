package edu.mcgill.kaliningraph

import edu.mcgill.kaliningraph.display.animate
import kweb.html.Document
import kweb.html.events.KeyboardEvent
import kotlin.random.Random

@ExperimentalStdlibApi
fun main() {
  animate(
    LabeledGraphBuilder("abcdecfghia")
  ) { doc: Document, it: KeyboardEvent, graphs: MutableList<LabeledGraph> ->
    when {
      "Left" in it.key -> {
      }
      "Right" in it.key -> {
        val current = graphs.last()
        if (current.none { it.occupied }) {
          current.takeWhile { DEFAULT_RANDOM.nextDouble() < 0.5 }
            .forEach { it.occupied = true }
        } else current.propagate()
      }
      "Up" in it.key -> if (graphs.size > 1) graphs.removeLastOrNull()
      "Down" in it.key -> {
        val current = graphs.last()
        val rand = Random(1)
        val sub = "cdec" to "ijkl"
        graphs.add(current.rewrite(sub, rand).also {
          it.description = "${current.randomWalk(rand).take(20).joinToString("")}...$sub"
        })
      }
    }
  }
}