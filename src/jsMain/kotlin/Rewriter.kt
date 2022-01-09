import ai.hypergraph.kaliningraph.graphs.LabeledGraph
import ai.hypergraph.kaliningraph.types.cc
import org.w3c.dom.events.KeyboardEvent
import kotlin.random.Random

@ExperimentalStdlibApi
fun rewriter() {
  animate(
    LabeledGraph("abcdecfghia").also { println(it) }
  ) { event: KeyboardEvent, graphs: MutableList<LabeledGraph> ->
    when {
      "h" in event.key -> {}
      "l" in event.key -> {
        val current = graphs.last()
        if (current.none { it.occupied }) {
          current.takeWhile { Random.Default.nextDouble() < 0.5 }.forEach { it.occupied = true }
        } else current.propagate()
      }
      "k" in event.key -> if (graphs.size > 1) graphs.removeLastOrNull()
      "j" in event.key -> {
        val current = graphs.last()
        val sub = "cdec" cc "ijkl"
        graphs.add(current.rewrite(sub).also {
          it.description = "${current.randomWalk().take(20).joinToString("")}...$sub"
        })
      }
    }
  }
}