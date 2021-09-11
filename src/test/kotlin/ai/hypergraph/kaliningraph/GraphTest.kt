package ai.hypergraph.kaliningraph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.jupiter.api.Test

class GraphTest {
  val graph = LabeledGraph { a - b - c - d - e; a - c - e }
  @Test
  fun testIsomorphic() {
    assertEquals(
      graph,
      LabeledGraph { b - c - d - e - f; b - d - f }
    )
  }

  @Test
  fun testEquivalence() {
    val abcde = LabeledGraph { a - b - c - d - e }
    val ace = LabeledGraph { a - c - e }
    val graph2 = abcde + ace

    assertEquals(graph, graph2)

    val abcdead = LabeledGraph { a - b - c - d - e - a - d }
    val edcbace = LabeledGraph { e - d - c - b - a - c - e }
    assertNotEquals(abcdead, edcbace)
  }

  @Test
  fun testClosure() {
    assertEquals(graph.toSet(), graph.vertices.flatMap { it.neighbors + it }.toSet())
  }

  @Test
  fun testDiameter() {
    assertEquals(2, graph.diameter())
  }

  // TODO: Test whether graph is closed under other circumstances
}