package edu.mcgill.kaliningraph

fun main() {
  println("Hello Kaliningraph!")

  val de = GraphBuilder { d - e }
  val dacbe = GraphBuilder { d - a - c - b - e }
  val dce = GraphBuilder { d - c - e }

  val abcd = GraphBuilder { a - b - c - d }
  val cfde = GraphBuilder { c - "a" - f - d - e }

  val dg = Graph(dacbe, dce, de) + Graph(abcd, cfde)
  dg.show()

  val l = dg.V.first()
  println("$l:" + l.neighbors())
  println("Ego GraphBuilder of ${dg.toList()[2]}: " + dg.toList()[2].neighborhood().V)

  val abca = GraphBuilder { a - b - c - a }
  val efgh = GraphBuilder { e - f - g - e }
  val abcd_wl3 = abca.wl(3).values.sorted()
  println("WL3(abcd) = $abcd_wl3")
  val efgh_wl3 = efgh.wl(3).values.sorted()
  println("WL3(efgh) = $efgh_wl3")
  println("Isomorphic: ${abca.isomorphicTo(efgh)}")
}