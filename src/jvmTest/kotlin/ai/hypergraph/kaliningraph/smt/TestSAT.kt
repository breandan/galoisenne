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
    val dim = 10
    // https://www.koreascience.or.kr/article/JAKO200507523302678.pdf#page=3
    // "It is well known that the permutation matrices are the only invertible Boolean matrices..."
    val p = (0 until dim).shuffled()
    println("Permutation:\n" + p.joinToString(" "))
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
    val dim = 20
    val len = 14
    val universe = (1 until dim).toList()

    fun draw() = universe.shuffled().take(len).map { universe.indexOf(it) }

    val setA = draw().toSet()
    val setB = draw().toSet()
    fun Set<Int>.encodeAsMatrix() =
      FreeMatrix(SAT_ALGEBRA, len, dim) { i, j ->
        Literal(elementAt(i) == j)
      }

    val A = setA.encodeAsMatrix()
    val X = FreeMatrix(SAT_ALGEBRA, dim) { i, j ->
      if (i == j) BoolVar("$i") else Literal(false)
    }
    val B = setB.encodeAsMatrix()
    val dontCare = BoolVar("dc")
    val Y = FreeMatrix(SAT_ALGEBRA, dim) { _, _ -> dontCare }

    val intersection = (A * X * B.transpose()) eq Y
    val solution = solveBoolean(intersection)

    val expected = setA intersect setB
    val actual = (solution.keys - dontCare.formula).map { "$it".toInt() }.toSet()

    assertEquals(expected, actual)
  }

  /*
  ./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.smt.TestSAT.testRepeatSetInt"
  */
  @Test
  fun testRepeatSetInt() = repeat(100) { testSetIntersectionOneHot() }
}