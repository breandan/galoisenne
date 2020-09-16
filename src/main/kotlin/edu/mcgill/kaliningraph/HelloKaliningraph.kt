package edu.mcgill.kaliningraph

import edu.mcgill.kaliningraph.circuits.CircuitBuilder

fun main() {
  println("Hello Kaliningraph!")

  val de = LabeledGraphBuilder { d - e }
  println(de.diameter())
  val dce = LabeledGraphBuilder { d - c - e }
  println(dce.diameter())
  val dacbe = LabeledGraphBuilder { d - a - c - b - e - f - g - h - i - j - k }
  dacbe.show()
  println(dacbe.diameter())

  val abcd = LabeledGraphBuilder { a - b - c - d }
  val cfde = LabeledGraphBuilder { c - "a" - f - d - e }

  val dg = de.new(dacbe, dce, de) + de.new(abcd, cfde)

  dg.show()

  val l = dg.vertices.first()
  println("$l:" + l.neighbors())
  println("Ego GraphBuilder of ${dg.toList()[2]}: " + dg.toList()[2].neighborhood().vertices)

  val abca = LabeledGraphBuilder { a - b - c - a }
  val efgh = LabeledGraphBuilder { e - f - g - e }
  val abcd_wl3 = abca.wl(3).values.sorted()
  println("WL3(abcd) = $abcd_wl3")
  val efgh_wl3 = efgh.wl(3).values.sorted()
  println("WL3(efgh) = $efgh_wl3")
  println("Isomorphic: ${abca.isomorphicTo(efgh)}")

  CircuitBuilder {
    a = b + c
    e = a + d
    f = b - h
    b = g + 1
  }//.show()
}