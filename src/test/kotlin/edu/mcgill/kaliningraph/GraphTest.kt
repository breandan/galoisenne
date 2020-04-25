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
}