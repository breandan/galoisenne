package edu.mcgill.kaliningraph

import kweb.ImageElement
import kweb.Kweb
import kweb.html.events.KeyboardEvent
import kweb.img
import kweb.new
import kotlin.random.Random

@ExperimentalStdlibApi
fun main() {
  rewriteDemo()
}

@ExperimentalStdlibApi
fun rewriteDemo() {
  var graphs = mutableListOf(LabeledGraphBuilder("abcdecfghia"))// must have cycle

  Kweb(port = 16097) {
    doc.body.apply {
      val desc = new { element("p")
        .innerHTML("Use ↑/↓ to grow graph, and →/← keys to evolve the graph...") }
      val el = new { element("div")
        .setAttributeRaw("style", "max-width: 500px;")
        .innerHTML(graphs.last().html()) }
      val mat = new { img(null, mapOf("width" to "200", "height" to "200")) }
      val program = new { element("p").innerHTML("") }

      on.keydown { keystroke ->
        handle(keystroke, graphs)

        el.innerHTML(graphs.last().html())
        mat.render(graphs.last()) { it.A }
        desc.innerHTML("<p style=\"font-size:40px\">${graphs.last().string}</p>")
      }
    }
  }

  ProcessBuilder(browserCmd, "http://0.0.0.0:16097").start()
}

@ExperimentalStdlibApi
private fun handle(it: KeyboardEvent, graphs: MutableList<LabeledGraph>) {
  when {
    "Left" in it.key -> { }
    "Right" in it.key -> {
      val current = graphs.last()
      if (current.none { it.occupied }) {
        current.sortedBy { -it.id.toInt() + DEFAULT_RANDOM.nextDouble() * 10 }
          .takeWhile { DEFAULT_RANDOM.nextDouble() < 0.5 }
          .forEach { it.occupied = true }
        current.accumuator = current.filter { it.occupied }.map { it.id }.toMutableSet()
        current.string = "y = ${current.accumuator.joinToString(" + ")}"
      } else current.propagate()
    }
    "Up" in it.key -> if (graphs.size > 1) graphs.removeLastOrNull()
    "Down" in it.key -> {
      val current = graphs.last()
      val rand = Random(1)
      val sub = "cdec" to "ijkl"
      graphs.add(current.rewrite(sub, rand).also {
        it.string = "${current.randomWalk(rand).take(20).joinToString("")}...$sub"
      })
    }
  }
}

private fun ImageElement.render(graph: LabeledGraph, renderFun: (LabeledGraph) -> SpsMat) {
  setAttributeRaw("src", renderFun(graph).matToImg())
}