package ai.hypergraph.kaliningraph.tensor

import ai.hypergraph.kaliningraph.companionMatrix
import ai.hypergraph.kaliningraph.eigen
import ai.hypergraph.kaliningraph.norm
import kotlin.test.Test
import kotlin.test.assertTrue

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.tensor.TestDoubleMatrix"
*/
class TestDoubleMatrix {
  @Test
  fun testEigen() {
    val M = DoubleMatrix.vector(1.0, -6.0, 11.0, -6.0, -2.0).companionMatrix()
    val (x, λ) = M.eigen()
    // We want: Mx ≈ λx
    assertTrue((M * x - λ * x).norm() < 0.001)
  }

  @Test
  fun testRandomEigen() {
    val M = DoubleMatrix.random(10)
    val (x, λ) = M.eigen()
    assertTrue((M * x - λ * x).norm() < 0.001)
  }
}