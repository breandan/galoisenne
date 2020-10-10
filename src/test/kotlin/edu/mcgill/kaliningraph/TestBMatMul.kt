package edu.mcgill.kaliningraph

import edu.mcgill.kaliningraph.matrix.BMat
import org.junit.Assert.*
import org.junit.jupiter.api.Test

class TestBMatMul {
  @Test
  fun testMatMul() {
    val a = BMat(0, 0, 0, 1, 0, 1, 0, 0, 1)
    val b = BMat(1, 1, 0, 0, 0, 0, 0, 1, 1)

    assertEquals(a * (b + b), a * b + a * b)
  }

  @Test
  fun testRandMul() {
    for(i in 0..100) {
      val a = BMat.random(4)
      val b = BMat.random(4)
      val c = BMat.random(4)

      assertEquals(a * (b + c), a * b + a * c)
    }
  }
}