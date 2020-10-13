package edu.mcgill.kaliningraph

import edu.mcgill.kaliningraph.matrix.BSqMat
import org.junit.Assert.*
import org.junit.jupiter.api.Test

class TestBMatMul {
  @Test
  fun testMatMul() {
    val a = BSqMat(0, 0, 0, 1, 0, 1, 0, 0, 1)
    val b = BSqMat(1, 1, 0, 0, 0, 0, 0, 1, 1)

    assertEquals(a * (b + b), a * b + a * b)
  }

  @Test
  fun testRandMul() {
    for(i in 0..100) {
      val a = BSqMat.random(4)
      val b = BSqMat.random(4)
      val c = BSqMat.random(4)

      assertEquals(a * (b + c), a * b + a * c)
    }
  }
}