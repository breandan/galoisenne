package edu.mcgill.kaliningraph

import org.junit.Assert.assertEquals
import org.junit.jupiter.api.Test

class GraphTest {
  @Test
  fun testIsomorphic() {
    assertEquals(
      Graph { a - b - c - d - e; a - c - e },
      Graph { b - c - d - e - f; b - d - f }
    )
  }

  @Test
  fun testEquivalence() {
    val graph1 = Graph { a - b - c - d - e; a - c - e }

    val abcde = Graph { a - b - c - d - e }
    val ace = Graph { a - c - e }
    val graph2 = abcde + ace

    assertEquals(graph1, graph2)
  }
}