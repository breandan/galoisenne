package ai.hypergraph.kaliningraph.tensor

import ai.hypergraph.kaliningraph.graphs.LabeledGraph
import ai.hypergraph.kaliningraph.theory.prefAttach
import ai.hypergraph.kaliningraph.types.A
import kotlin.test.Test
import kotlin.test.assertEquals

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.tensor.BooleanMatrixTest"
*/
class BooleanMatrixTest {
  @Test
  fun testMatMul() {
    val a = BooleanMatrix(0, 0, 0, 1, 0, 1, 0, 0, 1)
    val b = BooleanMatrix(1, 1, 0, 0, 0, 0, 0, 1, 1)
    val c = BooleanMatrix(1, 0, 1, 0, 0, 1, 0, 1, 1)

    assertEquals(a * (b + c), a * b + a * c)
  }

  @Test
  fun testRandMul() {
    for (i in 0..100) {
      val a = BooleanMatrix.random(4)
      val b = BooleanMatrix.random(4)
      val c = BooleanMatrix.random(4)

      assertEquals(a * (b + c), a * b + a * c, "$a\n$b\n$c")
    }
  }

  @Test
  fun testBooleanMatrixDistributivity() {
    val a = BooleanMatrix.random(3)
    val b = BooleanMatrix.random(3)
    val c = BooleanMatrix.random(3)

    assertEquals(a * (b + c), a * b + a * c)
  }

  @Test
  fun testBooleanMatrixFixpoint() {
    val randomGraph = LabeledGraph { a }.prefAttach(numVertices = 9)
    val adjMat = randomGraph.A.let { it + BooleanMatrix.one(it.numRows) }
    println(adjMat)
    val adjMatFP = adjMat.seekFixpoint { it * it }
    println(adjMatFP)
    for (i in 0..100) {
      val randVec = BooleanMatrix.random(adjMat.numRows, 1)
      assertEquals(adjMatFP * randVec, adjMatFP * adjMat * randVec)
    }
  }
}