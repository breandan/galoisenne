package edu.mcgill.kaliningraph

import org.junit.Assert.*
import org.junit.jupiter.api.Test

class GraphTest {
  val graph = LabeledGraphBuilder { a - b - c - d - e; a - c - e }
  @Test
  fun testIsomorphic() {
    assertEquals(
      graph,
      LabeledGraphBuilder { b - c - d - e - f; b - d - f }
    )
  }

  @Test
  fun testEquivalence() {
    val abcde = LabeledGraphBuilder { a - b - c - d - e }
    val ace = LabeledGraphBuilder { a - c - e }
    val graph2 = abcde + ace

    assertEquals(graph, graph2)

    val abcdead = LabeledGraphBuilder { a - b - c - d - e - a - d }
    val edcbace = LabeledGraphBuilder { e - d - c - b - a - c - e }
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