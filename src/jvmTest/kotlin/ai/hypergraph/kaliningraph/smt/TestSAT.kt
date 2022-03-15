package ai.hypergraph.kaliningraph.smt

import ai.hypergraph.kaliningraph.tensor.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TestSAT {
/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.smt.TestSAT.testBMatInv"
*/
  @Test
  fun testBMatInv() = SMTInstance().solve {
    val SAT_ALGEBRA = DefaultSATAlgebra()
    val dim = 2
    val A = FreeMatrix(dim, dim, SAT_ALGEBRA) { i, j -> Literal((i + j) % 2 == 0) }
    val B = FreeMatrix(dim, dim, SAT_ALGEBRA) { i, j -> BoolVar("b$i$j") }

    val isInverse = (A * B * A) eq A

    val solution = solveBoolean(isInverse)

//    println(solution.entries.joinToString("\n") { it.key.toString() + "," + it.value })

    val sol = B.rows.map { i -> i.map { solution[it]!! } }
    val maxLen = sol.flatten().maxOf { it.toString().length }
    sol.forEach {
      println(it.joinToString(" ") { it.toString().padStart(maxLen) })
    }

    val a = BooleanMatrix(dim, dim) { i, j -> (i + j) % 2 == 0 }
    val b = BooleanMatrix(dim, dim) { i, j -> sol[i][j] }
    assertEquals(a * b * a, a)
  }
}