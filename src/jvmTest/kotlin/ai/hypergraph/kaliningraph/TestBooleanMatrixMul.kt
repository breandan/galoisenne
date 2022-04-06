package ai.hypergraph.kaliningraph

import ai.hypergraph.kaliningraph.tensor.BooleanMatrix
import org.junit.jupiter.api.*

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.TestBooleanMatrixMul"
*/
class TestBooleanMatrixMul {
  @Test
  fun testMatMul() {
    val a = BooleanMatrix(0, 0, 0, 1, 0, 1, 0, 0, 1)
    val b = BooleanMatrix(1, 1, 0, 0, 0, 0, 0, 1, 1)
    val c = BooleanMatrix(1, 0, 1, 0, 0, 1, 0, 1, 1)

    Assertions.assertTrue(a * (b + c) == a * b + a * c)
  }

  @Test
  fun testRandMul() {
    for (i in 0..100) {
      val a = BooleanMatrix.random(4)
      val b = BooleanMatrix.random(4)
      val c = BooleanMatrix.random(4)

      Assertions.assertTrue(a * (b + c) == a * b + a * c, "$a\n$b\n$c")
    }
  }
}