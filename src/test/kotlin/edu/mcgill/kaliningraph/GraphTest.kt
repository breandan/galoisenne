package edu.mcgill.kaliningraph

import org.junit.Assert.*
import org.junit.jupiter.api.Test

class GraphTest {
  @Test
  fun testIsomorphic() {
    assertEquals(
      LabeledGraphBuilder { a - b - c - d - e; a - c - e },
      LabeledGraphBuilder { b - c - d - e - f; b - d - f }
    )
  }

  @Test
  fun testEquivalence() {
    val graph1 = LabeledGraphBuilder { a - b - c - d - e; a - c - e }

    val abcde = LabeledGraphBuilder { a - b - c - d - e }
    val ace = LabeledGraphBuilder { a - c - e }
    val graph2 = abcde + ace

    assertEquals(graph1, graph2)

    val abcdead = LabeledGraphBuilder { a - b - c - d - e - a - d }
    val edcbace = LabeledGraphBuilder { e - d - c - b - a - c - e }
    assertNotEquals(abcdead, edcbace)
  }
}