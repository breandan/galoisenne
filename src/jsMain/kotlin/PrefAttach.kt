import ai.hypergraph.kaliningraph.graphs.*
import ai.hypergraph.kaliningraph.types.*
import ai.hypergraph.kaliningraph.sample
import ai.hypergraph.kaliningraph.theory.prefAttach
import kotlinx.browser.document
import kotlinx.html.dom.append
import kotlinx.html.p
import org.w3c.dom.events.KeyboardEvent
import kotlin.random.Random

@ExperimentalStdlibApi
fun prefAttach() =
  animate(initial = LGVertex("0").graph) { it: KeyboardEvent, graphs: MutableList<LabeledGraph> ->
    when {
      "h" in it.key -> {}
      "l" in it.key -> {
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
      "k" in it.key -> if (graphs.size > 1) graphs.removeLastOrNull()
      "j" in it.key -> graphs.add(graphs.last().prefAttach { degree ->
        this + LGVertex(
          label = size.toString(),
          out = if (vertices.isEmpty()) emptySet()
          else degMap.sample().take(degree.coerceAtMost(size)).toSet()
        ).graph
      })
    }
  }.also {
    document.body?.append?.p { +"Use k/j to grow graph, and l/h keys to evolve the graph..." }
  }