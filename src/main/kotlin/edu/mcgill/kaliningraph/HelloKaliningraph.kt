package edu.mcgill.kaliningraph

import kweb.Kweb
import kweb.new

@ExperimentalStdlibApi
fun main() {
  println("Hello Kaliningraph!")

  val de = Graph { d - e }
  val dacbe = Graph { d - a - c - b - e }
  val dce = Graph { d - c - e }

  val abcd = Graph { a - b - c - d }
  val cfde = Graph { c - "a" - f - d - e }

  val dg = Graph(dacbe, dce, de) + Graph(abcd, cfde)
  dg.show()

  val l = dg.V.first()
  println("$l:" + l.neighbors())
  println("Ego Graph of ${dg.toList()[2]}: " + dg.toList()[2].egoGraph().V)

  val abca = Graph { a - b - c - a }
  val efgh = Graph { e - f - g - e }
  val abcd_wl3 = abca.wl(3).values.sorted()
  println("WL3(abcd) = $abcd_wl3")
  val efgh_wl3 = efgh.wl(3).values.sorted()
  println("WL3(efgh) = $efgh_wl3")
  println("Isomorphic: ${abca.isomorphicTo(efgh)}")

  prefAttachDemo()
}

@ExperimentalStdlibApi
fun prefAttachDemo() {
  val graph = mutableListOf(Graph { a - b })

  Kweb(port = 16097) {
    doc.body.apply {
      val desc = new { element("p").innerHTML("Use →/← keys...") }
      val el = new { element("div").innerHTML(graph.last().html()) }
      on.keydown {
        when {
          "Left" in it.key -> {
            graph.removeLastOrNull()
            graph.lastOrNull()?.html()?.let(el::innerHTML)
          }
          "Right" in it.key -> {
            graph.add(graph.last().prefAttach())
            el.innerHTML(graph.last().html())
          }
        }
      }
    }
  }

  ProcessBuilder("x-www-browser", "http://0.0.0.0:16097").start()
}