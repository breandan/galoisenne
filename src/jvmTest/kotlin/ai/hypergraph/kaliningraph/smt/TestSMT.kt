package ai.hypergraph.kaliningraph.smt

import ai.hypergraph.kaliningraph.tensor.*
import ai.hypergraph.kaliningraph.times
import ai.hypergraph.kaliningraph.types.*
import org.junit.jupiter.api.Test
import org.sosy_lab.java_smt.SolverContextFactory
import org.sosy_lab.java_smt.SolverContextFactory.Solvers.PRINCESS
import org.sosy_lab.java_smt.api.*
import org.sosy_lab.java_smt.api.NumeralFormula.IntegerFormula
import org.sosy_lab.java_smt.api.SolverContext.ProverOptions.GENERATE_MODELS
import java.math.BigInteger
import kotlin.math.*
import kotlin.reflect.KProperty
import kotlin.test.*

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.smt.TestSMT"
 */
class TestSMT {
  @Test
  // https://en.wikipedia.org/wiki/Taxicab_number
  fun testTaxiCab() = SMTInstance().solve {
    val a by IntVar()
    println(a)
    val b by IntVar()
    val c by IntVar()
    val d by IntVar()

    val exp = 3
    val f = ((a pwr exp) pls (b pwr exp)) eq ((c pwr exp) pls (d pwr exp))

    val bigNum = 2

    val containsNegative = (a lt 0) or (b lt 0) or (c lt 0)

    val areLarge =
      (a pwr 2 gt bigNum) and
        (b pwr 2 gt bigNum) and
        (c pwr 2 gt bigNum) and
        (d pwr 2 gt bigNum)

    val isNontrivial = f and containsNegative and areLarge and
      distinct(listOf(a, b, c, d).map { it.formula })

    val solution = solveInteger(isNontrivial)
    println(solution)

    assertEquals(
      solution[a]!!.pow(exp) + solution[b]!!.pow(exp),
      solution[c]!!.pow(exp) + solution[d]!!.pow(exp)
    )
  }

  @Test
  // https://en.wikipedia.org/wiki/Sums_of_three_cubes
  fun testSumOfCubes() = SMTInstance().solve {
    val a by IntVar()
    val b by IntVar()
    val c by IntVar()
    val d by IntVar()

    val exp = 3
    val f = (a pwr exp) pls (b pwr exp) pls (c pwr exp) eq d

    val bigNum = 3

    val containsNegative = (a lt 0) or (b lt 0) or (c lt 0)

    val areLarge =
      (a pwr 2 gt bigNum) and
        (b pwr 2 gt bigNum) and
        (c pwr 2 gt bigNum) and
        (d pwr 2 gt bigNum)

    val isNontrivial = f and containsNegative and areLarge

    val solution = solveInteger(isNontrivial)
    println(solution)

    assertEquals(
      solution[a]!!.pow(exp) + solution[b]!!.pow(exp) + solution[c]!!.pow(exp),
      solution[d]
    )
  }

  @Test
  fun testNonLinear() {
    // Search space of integer quadruplets w, x, y, z s.t. w^z + x^z = y^z + z
    data class Fermat(val w: Int, val x: Int, val y: Int, val z: Int)

    fun Int.pow(exp: Int) = toDouble().pow(exp).toInt()
    fun Fermat.isValidSolution() = w.pow(z) + x.pow(z) == y.pow(z) + z

    val range = (-10..10).toSet()
    val erange = (2..8).toSet()
    range.let { it * it * it * erange }
      .map { Fermat(it.first.first.first, it.first.first.second, it.first.second, it.second) }
      .filter { it.isValidSolution() }
      .forEach { println(it) }

    (1..2).forEach { z ->
      SMTInstance().solve {
        val w by IntVar()
        val x by IntVar()
        val y by IntVar()

        val f = ((w pwr z) pls (x pwr z)) eq ((y pwr z) pls z)

        val bigNum = 3

        val containsNegative = (w lt 0) or (x lt 0) or (y lt 0)

        val areLarge =
          (w pwr 2 gt bigNum) and
            (x pwr 2 gt bigNum) and
            (y pwr 2 gt bigNum)

        val isNontrivial = f and containsNegative and areLarge

        val solution = solveInteger(isNontrivial)
        println("$solution, z=$z")

        assertEquals(
          solution[w]!!.pow(z) + solution[x]!!.pow(z),
          solution[y]!!.pow(z) + z
        )
      }
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.smt.TestSMT.testBistochastic"
*/
  @Test
  fun testBistochastic() = SMTInstance().solve {
    val dim = 7

    val m = FreeMatrix(dim) { i, j -> IntVar("v$i$j") }

    val sum by IntVar()

    fun FreeMatrix<SMTF>.isStochastic() =
      rows.map { it.reduce { a, b: SMTF -> a.run { this + b } } eq sum }
        .reduce { a, b -> a and b }

    val isBistochastic = m.isStochastic() and m.transpose().isStochastic()

    val assymetric = m.indices
      .mapNotNull { (i, j) -> if (i != j) m[j][i] neq m[i][j] else null }
      .reduce { a, b -> a and b }

    val positive = m.data.map { it gt 0 }.reduce { a, b -> a and b }

    val solution = solveInteger(isBistochastic and assymetric and positive)

    val sol = m.rows.map { i -> i.map { solution[it]!! } }
    sol.forEach { println(it.joinToString(" ")) }

    (m.rows + m.cols).windowed(2).map { twoSlices ->
      val (a, b) = twoSlices[0] to twoSlices[1]
      assertEquals(a.sumOf { solution[it]!! }, b.sumOf { solution[it]!! })
    }
  }


  @Test
  fun testIsAssociative() = SMTInstance().solve {
    val SMT_ALGEBRA = DefaultSMTAlgebra()

    val dim = 2
    val a = FreeMatrix(dim, dim, SMT_ALGEBRA) { i, j -> IntVar("a$i$j") }
    val b = FreeMatrix(dim, dim, SMT_ALGEBRA) { i, j -> IntVar("b$i$j") }
    val c = FreeMatrix(dim, dim, SMT_ALGEBRA) { i, j -> IntVar("c$i$j") }

    val plusAssoc = ((a + b) + c) eq (a + (b + c))
//    val multAssoc = ((a * b) * c) eq (a * (b * c)) // TODO: why is this so slow?

    val goal = forall((a.data + b.data + c.data).map { it.formula }, plusAssoc)
    val shouldBeTrue = prove(goal)

    println(shouldBeTrue)

    assertTrue(shouldBeTrue)
  }

  @Test
  fun testIsDistributive() = SMTInstance().solve {
    val SMT_ALGEBRA = DefaultSMTAlgebra()

    val dim = 2
    val a = FreeMatrix(dim, dim, SMT_ALGEBRA) { i, j -> IntVar("a$i$j") }
    val b = FreeMatrix(dim, dim, SMT_ALGEBRA) { i, j -> IntVar("b$i$j") }
    val c = FreeMatrix(dim, dim, SMT_ALGEBRA) { i, j -> IntVar("c$i$j") }

    val plusDistrib = (a * (b + c)) neq (a * b + a * c)

    val goal = exists((a.data + b.data + c.data).map { it.formula }, plusDistrib)
    val shouldBeFalse = prove(goal)

    println(shouldBeFalse)

    assertFalse(shouldBeFalse)
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.smt.TestSMT.testMatInv"
*/

  @Test
  fun testMatInv() = SMTInstance().solve {
    val SMT_ALGEBRA = DefaultSMTAlgebra()
    val dim = 10
    val A = FreeMatrix(dim, dim, SMT_ALGEBRA) { i, j -> SMTF(i + j) }
    val B = FreeMatrix(dim, dim, SMT_ALGEBRA) { i, j -> IntVar("a$i$j") }

    val isInverse = (A * B * A) eq A

    val solution = solveInteger(isInverse)

    val sol = B.rows.map { i -> i.map { solution[it]!! } }
    val maxLen = sol.flatten().maxOf { it.toString().length }
    sol.forEach { println(it.joinToString(" ") { it.toString().padStart(maxLen) }) }

    val aDoub = DoubleMatrix(dim, dim) { i, j -> (i + j).toDouble() }
    val sDoub = DoubleMatrix(dim, dim) { i, j -> sol[i][j].toDouble() }
    assertEquals(aDoub * sDoub * aDoub, aDoub)
  }
}