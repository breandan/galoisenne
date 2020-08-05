package edu.mcgill.kaliningraph

import kweb.ImageElement
import kweb.Kweb
import kweb.html.events.KeyboardEvent
import kweb.img
import kweb.new
import org.ejml.data.DMatrixSparseCSC
import org.ejml.kotlin.times
import org.ejml.kotlin.transpose

@ExperimentalStdlibApi
fun main() {
  prefAttachDemo()
}

@ExperimentalStdlibApi
fun prefAttachDemo() {
  val graphs = mutableListOf(LabeledGraph(LGVertex("0")))

  Kweb(port = 16097) {
    doc.body.apply {
      val desc = new { element("p")
        .innerHTML("Use ↑/↓ to grow graph, and →/← keys to evolve the graph...") }
      val el = new { element("div")
        .setAttributeRaw("style", "max-width: 500px;")
        .innerHTML(graphs.last().html()) }
      val mat = new { img(null, mapOf("width" to "200", "height" to "200")) }
      val vec = new { img(null, mapOf("width" to "200", "height" to "200")) }
      val nex = new { img(null, mapOf("width" to "200", "height" to "200")) }
      val program = new { element("p").innerHTML("") }

      on.keydown { keystroke ->
        handle(keystroke, graphs)

        el.innerHTML(graphs.last().html())
        mat.render(graphs.last()) { it.A }
        vec.render(graphs.last()) { it.S() }
        nex.render(graphs.last()) { it.A.transpose() * it.S() }
      }

      program.innerHTML("<p style=\"font-size:40px\">${graphs.last().string}</p>")
    }
  }

  ProcessBuilder("x-www-browser", "http://0.0.0.0:16097").start()
}

@ExperimentalStdlibApi
private fun handle(it: KeyboardEvent, graphs: MutableList<LabeledGraph>) {
  when {
    "Left" in it.key -> { }
    "Right" in it.key -> {
      val current = graphs.last()
      if (current.none { it.occupied }) {
        current.sortedBy { -it.id.toInt() + Math.random() * 10 }
          .takeWhile { Math.random() < 0.5 }
          .forEach { it.occupied = true }
        current.done = current.filter { it.occupied }.map { it.id }.toMutableSet()
        current.string = "y = ${current.done.joinToString(" + ")}"
      } else current.propagate()
    }
    "Up" in it.key -> if (graphs.size > 1) graphs.removeLastOrNull()
    "Down" in it.key -> graphs.add(graphs.last().prefAttach())
  }
}

private fun ImageElement.render(graph: LabeledGraph, renderFun: (LabeledGraph) -> DMatrixSparseCSC) {
  setAttributeRaw("src", renderFun(graph).adjToMat())
}