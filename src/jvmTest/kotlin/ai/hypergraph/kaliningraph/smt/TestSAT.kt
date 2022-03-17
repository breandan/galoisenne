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

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.smt.TestSAT.testRepeatInv"
*/
  @Test
  fun testRepeatInv() = repeat(100) { testBMatInv() }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.smt.TestSAT.testSetIntersectionOneHot"
*/
  @Test
  fun testSetIntersectionOneHot() = SMTInstance().solve {
    val dim = 9
    val card = 5
    val universe = (1 until dim).toList().toIntArray()
    fun draw() = universe.toSet().shuffled()
      .take(card).map { universe.indexOf(it) }

    val setA = draw()
    val setB = draw()

    val A = FreeMatrix(SAT_ALGEBRA, card, dim) { i, j -> Literal(setA[i] == j) }
    val X = FreeMatrix(SAT_ALGEBRA, dim) { i, j ->
      if (i == j) BoolVar("$i") else Literal(false)
    }
    val B = FreeMatrix(SAT_ALGEBRA, card, dim) { i, j -> Literal(setB[i] == j) }
    val dontCare = BoolVar("dc")
    val Y = FreeMatrix(SAT_ALGEBRA, dim) { i, j -> dontCare }

    val intersection = (A * X * B.transpose()) eq Y
    val solution = solveBoolean(intersection)

    val expected = setA.toSet().intersect(setB.toSet())
    val actual = solution.filter { "dc" !in it.key.toString() }
      .keys.map { it.toString().toInt() }.toSet()
    assertEquals(expected, actual)
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.smt.TestSAT.testRepeatSetInt"
*/
  @Test
  fun testRepeatSetInt() = repeat(100) { testSetIntersectionOneHot() }
}