package edu.mcgill.kaliningraph

import edu.mcgill.kaliningraph.circuits.Notebook

fun main() {
  println("Hello Kaliningraph!")

  val de = LabeledGraph { d - e }
  val dacbe = LabeledGraph { d - a - c - b - e }
  val dce = LabeledGraph { d - c - e }

  val abcd = LabeledGraph { a - b - c - d }
  val cfde = LabeledGraph { c - "a" - f - d - e }

  val dg = Graph(dacbe, dce, de) + Graph(abcd, cfde)

  val l = dg.V.first()
  println("$l:" + l.neighbors())
  println("Ego GraphBuilder of ${dg.toList()[2]}: " + dg.toList()[2].neighborhood().V)

  val abca = LabeledGraph { a - b - c - a }
  val efgh = LabeledGraph { e - f - g - e }
  val abcd_wl3 = abca.wl(3).values.sorted()
  println("WL3(abcd) = $abcd_wl3")
  val efgh_wl3 = efgh.wl(3).values.sorted()
  println("WL3(efgh) = $efgh_wl3")
  println("Isomorphic: ${abca.isomorphicTo(efgh)}")

  Notebook {
    a = b + c
    e = a + d
    f = b - h
    b = g + 1
  }.show()
}