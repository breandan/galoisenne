package ai.hypergraph.kaliningraph.tensor

import ai.hypergraph.kaliningraph.graphs.LabeledGraph
import ai.hypergraph.kaliningraph.theory.prefAttach
import ai.hypergraph.kaliningraph.types.*
import kotlin.test.*

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.tensor.TensorTest"
*/

class TensorTest {
  @Test
  fun tensorTest() {
    data class T3(val x: Int, val n: String, val c: Double)
    class SomeTensor<T>(override val map: MutableMap<T, Int> = mutableMapOf()) : SparseTensor<T> {
      override fun toString() = map.entries.joinToString("\n") { (k, v) -> "$k to $v" }
    }

    // Probability DSL for Markovian
    fun <T> SparseTensor<T>.P(that: (T) -> Boolean, given: (T) -> Boolean = { true }) =
      map.entries.fold(0 cc 0) { (n, d), (k, v) ->
        val (a, b) = given(k) cc that(k)
        when {
          a && b -> n + v cc d + v
          a -> n cc d + v
          else -> n cc d
        }
      }.let { (n, d) -> n.toDouble() / d.toDouble().coerceAtLeast(1.0) }

    val spt = SomeTensor<T3>()

    spt[T3(x = 1, n = "b", c = 3.0)]++
    spt[T3(x = 1, n = "b", c = 3.0)]++
    spt[T3(x = 3, n = "a", c = 2.1)]++
    spt[T3(x = 2, n = "a", c = 2.1)]++
    spt[T3(x = 2, n = "b", c = 3.0)]++

    val condProb = spt.P(that = { it.x == 1 }, given = { it.n == "b" })
    println("Query: $condProb")

    println(spt.toString())
  }

  @Test
  fun testBooleanMatrixDistributivity() {
    val a = BooleanMatrix.random(3)
    val b = BooleanMatrix.random(3)
    val c = BooleanMatrix.random(3)

    assertEquals(a * (b + c), a * b + a * c)
  }

  @Test
  fun testBooleanMatrixEigenvalues() {
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

  @Test
  fun testFreeMatrixMultiplicationAssociativity() {
    val a = FreeMatrix(algebra = INTEGER_FIELD, numRows = 2, data = listOf(0, 1, 2, 3))
    val b = FreeMatrix(algebra = INTEGER_FIELD, numRows = 2, data = listOf(0, 1, 2, 3))
    val c = FreeMatrix(algebra = INTEGER_FIELD, numRows = 2, data = listOf(0, 1, 2, 3))

    assertEquals(a * (b * c), (a * b) * c)
  }
}