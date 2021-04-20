package edu.mcgill.kaliningraph.circuits

import org.junit.jupiter.api.Test

class CGraphTest {
  @Test
  fun testCircuitBuilder() {
    CircuitBuilder {
      val funA by def(a, b, c) { a + b + c }
      j = funA(3, 2, 1)
      j = b * c
      d = e * f
      g = 1 - h
      i = a + d + g
    }
      //.reversed() //TODO: What is this even supposed to mean?
      // Maybe this whole tower of abstractions wasn't such a good idea after all
//      .show()
  }
}