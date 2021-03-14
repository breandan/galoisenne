package edu.mcgill.kaliningraph

import edu.mcgill.kaliningraph.display.animate
import kweb.html.Document
import kweb.html.events.KeyboardEvent
import kotlin.random.Random

@ExperimentalStdlibApi
fun main() =
  animate(
    initial = LabeledGraph(LGVertex("0"))
  ) { _: Document, it: KeyboardEvent, graphs: MutableList<LabeledGraph> ->
    when {
      "Left" in it.key -> { }
      "Right" in it.key -> {
        val current = graphs.last()
        if (current.none { it.occupied }) {
          current.sortedBy { -it.id.toInt() + Random.Default.nextDouble() * 10 }
            .takeWhile { Random.Default.nextDouble() < 0.5 }
            .forEach { it.occupied = true }
          current.accumuator = current.filter { it.occupied }.map { it.id }.toMutableSet()
          current.description = "y = ${current.accumuator.joinToString(" + ")}"
        } else {
          current.run {
            propagate()
            description = "y = " + accumuator.joinToString(" + ")
          }
        }
      }
      "Up" in it.key -> if (graphs.size > 1) graphs.removeLastOrNull()
      "Down" in it.key -> graphs.add(graphs.last().prefAttach())
    }
  }