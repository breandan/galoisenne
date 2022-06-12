package ai.hypergraph.kaliningraph.tensor

import ai.hypergraph.kaliningraph.*
import kotlin.math.ln
import kotlin.test.Test
import kotlin.test.assertTrue

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.tensor.TestDoubleMatrix"
*/
class TestDoubleMatrix {
  val M = DoubleMatrix.vector(1.0, -6.0, 11.0, -6.0, -2.0).companionMatrix()

  @Test
  fun testEigen() {
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

  @Test
  fun testMatExp() {
    val e1 = M.eigen().first
    val e2 = M.exp().eigen().first
    assertTrue((e1 - e2).norm() < 0.001)
  }
}