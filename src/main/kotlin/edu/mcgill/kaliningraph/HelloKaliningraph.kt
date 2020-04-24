package edu.mcgill.kaliningraph

import kweb.Kweb
import kweb.new

@ExperimentalStdlibApi
fun main() {
  println("Hello Kaliningraph!")

  val dg = buildGraph {
    val t = d - a - c - b - e
    val g = d - c - e

    val m = a - b - c - d
    val n = c - "a" - f - d - e

    Graph(t, g, d - e) + Graph(m, n)
  }
  val t = dg.V.first()
  dg.show()

  println("Ego graph of ${dg.toList()[2]}: " + dg.toList()[2].egoGraph().V)

  println("${t}:" + t.neighbors())

  val abcd = buildGraph { Graph(a - b - c - a) }
  val efgh = buildGraph { Graph(e - f - g - e) }
  val abcd_wl3 = abcd.wl(3).values.sorted()
  println("WL3(abcd) = $abcd_wl3")
  val efgh_wl3 = efgh.wl(3).values.sorted()
  println("WL3(efgh) = $efgh_wl3")
  println("Isomorphic: ${abcd.isomorphicTo(efgh)}")

  browserDemo()
}

@ExperimentalStdlibApi
fun browserDemo() {
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