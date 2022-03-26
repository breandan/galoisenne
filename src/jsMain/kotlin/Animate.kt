import ai.hypergraph.kaliningraph.graphs.LabeledGraph
import ai.hypergraph.kaliningraph.image.matToBase64Img
import ai.hypergraph.kaliningraph.types.*
import kotlinx.browser.document
import kotlinx.html.*
import kotlinx.html.dom.append
import org.w3c.dom.HTMLElement
import org.w3c.dom.asList
import org.w3c.dom.events.KeyboardEvent

var viz = Viz()

// Used to animate graph rewrite process with a keyboard
fun animate(initial: LabeledGraph, renderState: Boolean = true, transition: (KeyboardEvent, MutableList<LabeledGraph>) -> Unit) {
  val graphs = mutableListOf(initial)

  document.body!!.apply {
      querySelector("root").run { childNodes.asList().forEach { removeChild(it) } }
    fun doRender() {
      viz.renderSVGElement(graphs.last().toDot()).then { append(it) }
      append {
        div {
          graphs.last().run {
            p { +"Adjacency Matrix"; setAttribute("style", "font-size:20px") }
            img { src = A.matToBase64Img() }
            if (renderState) {
              p { +"S"; setAttribute("style", "font-size:20px") }
              img { src = S().matToBase64Img() }
              p { +"S'"; setAttribute("style", "font-size:20px") }
              img { src = (A.transpose() * S()).matToBase64Img() }
            }
            p { +description; setAttribute("style", "font-size:20px") }
          }
        }
      }
    }
    doRender()
    onkeypress = { keyEvent: KeyboardEvent ->
      removeAllTags("div")
      removeAllTags("svg")

      transition(keyEvent, graphs)
      doRender()
    }
  }
}

fun HTMLElement.removeAllTags(named: String): Unit =
  querySelector(named)?.run { remove(); removeAllTags(named) } ?: Unit