import kotlinx.browser.document
import kotlinx.browser.window
import react.dom.*
import ai.hypergraph.kaliningraph.graphs.*
import ai.hypergraph.kaliningraph.image.matToBase64Img

// ./gradlew jsBrowserRun
fun main() {
  window.onload = {
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
