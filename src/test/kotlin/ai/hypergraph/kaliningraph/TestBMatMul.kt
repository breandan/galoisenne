package ai.hypergraph.kaliningraph

import ai.hypergraph.kaliningraph.matrix.BSqMat
import org.junit.Assert.assertEquals
import org.junit.jupiter.api.Test

class TestBMatMul {
  @Test
  fun testMatMul() {
    val a = BSqMat(0, 0, 0, 1, 0, 1, 0, 0, 1)
    val b = BSqMat(1, 1, 0, 0, 0, 0, 0, 1, 1)
    val c = BSqMat(1, 0, 1, 0, 0, 1, 0, 1, 1)

    assertEquals(a * (b + c), a * b + a * c)
  }

  @Test
  fun testRandMul() {
    for (i in 0..100) {
      val a = BSqMat.random(4)
      val b = BSqMat.random(4)
      val c = BSqMat.random(4)

      assertEquals("$a\n$b\n$c", a * (b + c), a * b + a * c)
    }
  }
}