import ai.hypergraph.kaliningraph.graphs.LabeledGraph
import ai.hypergraph.kaliningraph.image.matToBase64Img
import kotlinx.browser.document
import kotlinx.html.*
import kotlinx.html.dom.append
import org.w3c.dom.*
import org.w3c.dom.events.KeyboardEvent

var viz = Viz()
fun animate(initial: LabeledGraph, transition: (KeyboardEvent, MutableList<LabeledGraph>) -> Unit) {
  val graphs = mutableListOf(initial)

  document.body!!.apply {
    append.p { +"Use k/j to grow graph, and l/h keys to evolve the graph..." }

    viz.renderSVGElement(graphs.last().toDot()).then {
      querySelector("svg")?.remove()
      append(it)
    }

    onkeypress = { keyEvent: KeyboardEvent ->
      removeAllTags("svg")
      removeAllTags("img")
      removeAllTags("p")

      transition(keyEvent, graphs)
      viz.renderSVGElement(graphs.last().toDot()).then { append(it) }

      graphs.last().run {
        append { img { src = A.matToBase64Img() } }
        append { img { src = S().matToBase64Img() } }
        append { img { src = (A.transpose() * S()).matToBase64Img() } }
        append { p { +description; setAttribute("style", "font-size:20px") } }
      }
    }
  }
}

fun HTMLElement.removeAllTags(named: String): Unit =
  querySelector(named)?.run { remove(); removeAllTags(named) } ?: Unit