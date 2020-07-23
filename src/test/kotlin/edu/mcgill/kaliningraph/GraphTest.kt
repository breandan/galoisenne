package edu.mcgill.kaliningraph

import org.junit.Assert.*
import org.junit.jupiter.api.Test

class GraphTest {
  @Test
  fun testIsomorphic() {
    assertEquals(
      LabeledGraph { a - b - c - d - e; a - c - e },
      LabeledGraph { b - c - d - e - f; b - d - f }
    )
  }

  @Test
  fun testEquivalence() {
    val graph1 = LabeledGraph { a - b - c - d - e; a - c - e }

    val abcde = LabeledGraph { a - b - c - d - e }
    val ace = LabeledGraph { a - c - e }
    val graph2 = abcde + ace

    assertEquals(graph1, graph2)

    val abcdead = LabeledGraph { a - b - c - d - e - a - d }
    val edcbace = LabeledGraph { e - d - c - b - a - c - e }
    assertNotEquals(abcdead, edcbace)
  }
}