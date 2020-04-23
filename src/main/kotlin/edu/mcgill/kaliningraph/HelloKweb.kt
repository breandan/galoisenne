package edu.mcgill.kaliningraph

import kweb.*

@ExperimentalStdlibApi
fun main() {
  val graph = mutableListOf(buildGraph { Graph(a) })

  Kweb(port = 16097) {
    doc.body.apply {
      val el = new { element("div").innerHTML(graph.last().html()) }
      on.keydown {
        when {
          "Left" in it.key -> {
            graph.removeLastOrNull()
            graph.lastOrNull()?.html()?.let(el::innerHTML)
          }
          "Right" in it.key -> {
            graph.add(graph.last().grow())
            el.innerHTML(graph.last().html())
          }
        }
      }
    }
  }

  ProcessBuilder("x-www-browser", "http://0.0.0.0:16097").start()
}

fun Graph.grow() = buildGraph { prefAttach(this@grow, 1) }