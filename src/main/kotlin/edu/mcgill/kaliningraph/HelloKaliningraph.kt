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

  dg.show()
}