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
    val dim = 5
    // https://www.koreascience.or.kr/article/JAKO200507523302678.pdf#page=3
    // "It is well known that the permutation matrices are the only invertible Boolean matrices..."
    val p = (0 until dim).shuffled()
    println("Permutation:\n" + p.joinToString(","))
    val A = FreeMatrix(SAT_ALGEBRA, dim) { i, j -> Literal(j == p[i]) }
    println("Permutation matrix:")
    A.rows.forEach {
      println(it.joinToString(" ") { it.toString().first() + "" })
    }
    val B = FreeMatrix(SAT_ALGEBRA, dim) { i, j -> BoolVar("b$i$j") }

    val isInverse = (A * B * A) eq A

    val solution = solveBoolean(isInverse)

//    println(solution.entries.joinToString("\n") { it.key.toString() + "," + it.value })

    val sol = B.rows.map { i -> i.map { solution[it]!! } }
    val maxLen = sol.flatten().maxOf { it.toString().length }
    println("Inverse permutation matrix:")
    sol.forEach {
      println(it.joinToString(" ") { it.toString().first() + "" })
    }

    val a = BooleanMatrix(dim) { i, j -> j == p[i] }
    val b = BooleanMatrix(dim) { i, j -> sol[i][j] }
    assertEquals(a * b * a, a)
  }
}