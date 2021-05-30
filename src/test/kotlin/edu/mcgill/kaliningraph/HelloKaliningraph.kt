package edu.mcgill.kaliningraph

import edu.mcgill.kaliningraph.circuits.CircuitBuilder
import org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY

fun main() {
  println("Hello Kaliningraph!")

  val de = LabeledGraph { d - e }
  println("de diamater: " + de.diameter())
  val dce = LabeledGraph { d - c - e }
  println("dce diamater: " + dce.diameter())
  val dacbe = LabeledGraph { d - a - c - b - e - f - g - h - i - j - k }
  dacbe.show()
  println("dacbe diamater: " + dacbe.diameter())

  val abcd = LabeledGraph { a - b - c - d }
  val cfde = LabeledGraph { c - "a" - f - d - e }

  val dg = LabeledGraph.G(dacbe, dce, de) +
    LabeledGraph.G(abcd, cfde)

  dg.show()

  val l = dg.vertices.first()
  println("$l:" + l.neighbors())
  println("Ego GraphBuilder of ${dg.toList()[2]}: " + dg.toList()[2].neighborhood().vertices)

  val abca = LabeledGraph { a - b - c - a }
  val efgh = LabeledGraph { e - f - g - e }
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