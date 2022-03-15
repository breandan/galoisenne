package ai.hypergraph.kaliningraph.smt

import ai.hypergraph.kaliningraph.tensor.*
import ai.hypergraph.kaliningraph.types.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TestSAT {
  /*
  TODO: Why doesn't this work?
  ./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.smt.TestSAT.testBMatInv"
  */
  @Test
  fun testBMatInv() = SMTInstance().solve {
    val SAT_ALGEBRA = DefaultSATAlgebra()
    val dim = 3
    val A = FreeMatrix(dim, dim, SAT_ALGEBRA) { i, j -> SATF(true) }
    val B = FreeMatrix(dim, dim, SAT_ALGEBRA) { i, j -> BoolVar("b$i$j") }

    val isInverse = (A * B) eq A

    val solution = solveBoolean(isInverse)

    println(solution.entries.joinToString("\n") { it.key.toString() + "," + it.value })

    val sol = B.rows.map { i -> i.map { solution[it]!! } }
    val maxLen = sol.flatten().maxOf { it.toString().length }
    sol.forEach {
      println(it.joinToString(" ") { it.toString().padStart(maxLen) })
    }

    val aDoub = BooleanMatrix(dim, dim) { i, j -> (i + j) % 2 == 0 }
    val sDoub = BooleanMatrix(dim, dim) { i, j -> sol[i][j] }
    assertEquals(aDoub * sDoub * aDoub, aDoub)
  }
}