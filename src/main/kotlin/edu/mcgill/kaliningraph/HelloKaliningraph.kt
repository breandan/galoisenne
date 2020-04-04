package edu.mcgill.kaliningraph

fun main() {
  println("Hello Kaliningraph!")

  val dg = buildGraph {
    val t = d - a - c - b - e
    val g = d - c - e

    val m = a - b - c - d
    val n = c - d - e

    Graph(t, g, d - e) + Graph(m, n)
  }
  val t = dg.V.first()
  dg.show()

  println("${t}:" + t.neighbors())

  val abcd = buildGraph { Graph(a - b - c - a) }
  val efgh = buildGraph { Graph(e - f - g - e) }
  val abcd_wl3 = abcd.computeWL(3).values.sorted()
  println("WL3(abcd) = $abcd_wl3")
  val efgh_wl3 = efgh.computeWL(3).values.sorted()
  println("WL3(efgh) = $efgh_wl3")
  println("Isomorphic: ${abcd.isomorphicTo(efgh)}")
}