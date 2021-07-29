package edu.mcgill.kaliningraph.display

import edu.mcgill.kaliningraph.*
import edu.mcgill.kaliningraph.matrix.BMat
import kweb.*
import kweb.html.Document
import kweb.html.events.KeyboardEvent
import java.net.URL

fun animate(initial: LabeledGraph, transition: (Document, KeyboardEvent, MutableList<LabeledGraph>) -> Unit) {
  val graphs = mutableListOf(initial)

  Kweb(port = 16097) {
    doc.body.apply {
      new { element("p")
        .innerHTML("Use ↑/↓ to grow graph, and →/← keys to evolve the graph...") }
      val el = new { element("div")
        .setAttributeRaw("style", "max-width: 500px;")
        .innerHTML(graphs.last().html()) }
      val mat = new { img(null, mapOf("width" to "200", "height" to "200")) }
      val vec = new { img(null, mapOf("width" to "200", "height" to "200")) }
      val nex = new { img(null, mapOf("width" to "200", "height" to "200")) }
      val desc = new { element("p").innerHTML("") }
//      val from = new { textArea().on.change { fromField = it.retrieved ?: "" } }
//      val repl = new { textArea().on.change { replField = it.retrieved ?: "" } }

      on.keydown { keystroke ->
        transition(doc, keystroke, graphs)

        el.innerHTML(graphs.last().html())
        mat.render(graphs.last()) { it.A }
        vec.render(graphs.last()) { it.S() }
        nex.render(graphs.last()) { it.A.transpose() * it.S() }
        desc.innerHTML("<p style=\"font-size:40px\">${graphs.last().description}</p>")
      }
    }
  }

  URL("http://0.0.0.0:16097").show()
}

//var fromField = ""
//var replField = ""

fun ImageElement.render(graph: LabeledGraph, renderFun: (LabeledGraph) -> BMat) {
  setAttributeRaw("src", renderFun(graph).matToImg())
}