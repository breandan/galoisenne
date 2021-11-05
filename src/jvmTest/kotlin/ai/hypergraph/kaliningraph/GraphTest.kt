package ai.hypergraph.kaliningraph

import ai.hypergraph.kaliningraph.theory.diameter
import org.junit.jupiter.api.*

class GraphTest {
  val graph = LabeledGraph { a - b - c - d - e; a - c - e }
  @Test
  fun testIsomorphic() {
    Assertions.assertEquals(graph, LabeledGraph { b - c - d - e - f; b - d - f })
  }

  @Test
  fun testEquivalence() {
    val abcde = LabeledGraph { a - b - c - d - e }
    val ace = LabeledGraph { a - c - e }
    val graph2 = abcde + ace

    Assertions.assertEquals(graph, graph2)

    val abcdead = LabeledGraph { a - b - c - d - e - a - d }
    val edcbace = LabeledGraph { e - d - c - b - a - c - e }
    Assertions.assertNotEquals(abcdead, edcbace)
  }

  @Test
  fun testClosure() {
    Assertions.assertEquals(graph.toSet(), graph.vertices.flatMap { it.neighbors + it }.toSet())
  }

  @Test
  fun testDiameter() {
    Assertions.assertEquals(2, graph.diameter())
  }

  // TODO: Test whether graph is closed under other circumstances
}