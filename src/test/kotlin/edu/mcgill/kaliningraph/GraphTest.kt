package edu.mcgill.kaliningraph

import org.junit.Assert.*
import org.junit.jupiter.api.Test

class GraphTest {
  @Test
  fun testIsomorphic() {
    assertEquals(
      GraphBuilder { a - b - c - d - e; a - c - e },
      GraphBuilder { b - c - d - e - f; b - d - f }
    )
  }

  @Test
  fun testEquivalence() {
    val graph1 = GraphBuilder { a - b - c - d - e; a - c - e }

    val abcde = GraphBuilder { a - b - c - d - e }
    val ace = GraphBuilder { a - c - e }
    val graph2 = abcde + ace

    assertEquals(graph1, graph2)

    val abcdead = GraphBuilder { a - b - c - d - e - a - d }
    val edcbace = GraphBuilder { e - d - c - b - a - c - e }
    assertNotEquals(abcdead, edcbace)
  }
}