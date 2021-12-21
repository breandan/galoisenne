package ai.hypergraph.kaliningraph

import ai.hypergraph.kaliningraph.display.animate
import ai.hypergraph.kaliningraph.graphs.LGVertex
import ai.hypergraph.kaliningraph.graphs.LabeledGraph
import ai.hypergraph.kaliningraph.theory.prefAttach
import kweb.html.Document
import kweb.html.events.KeyboardEvent
import kotlin.random.Random

@ExperimentalStdlibApi
fun main() =
  animate(
    initial = LGVertex("0").graph
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
      "Down" in it.key -> graphs.add(graphs.last().prefAttach { degree ->
        this + LGVertex(
          label = size.toString(),
          out = if (vertices.isEmpty()) emptySet()
          else degMap.sample().take(degree.coerceAtMost(size)).toSet()
        ).graph
      })
    }
  }