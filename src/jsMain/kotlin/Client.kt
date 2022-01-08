import kotlinx.browser.document
import kotlinx.browser.window
import react.dom.*
import ai.hypergraph.kaliningraph.graphs.*
import ai.hypergraph.kaliningraph.image.matToBase64Img
import org.w3c.dom.HTMLElement
import kotlin.js.Promise

// ./gradlew jsBrowserRun --continuous
fun main() {
  window.onload = {
    renderGraph("digraph { a -> b }").then { document.body!!.append(it) }
    render(document.getElementById("root")) {
      img {
        attrs {
          src = LabeledGraph { a - b - c }.A.matToBase64Img()
        }
      }
      child(Welcome::class) {
        attrs {
          name = "Kotlin/JS"
        }
      }
    }
  }
}

external fun renderGraph(p: String): Promise<HTMLElement>
