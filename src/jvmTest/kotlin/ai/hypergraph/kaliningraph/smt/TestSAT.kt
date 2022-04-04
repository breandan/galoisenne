package ai.hypergraph.kaliningraph.smt

import ai.hypergraph.kaliningraph.tensor.*
import ai.hypergraph.kaliningraph.types.*
import org.junit.jupiter.api.Test
import kotlin.collections.filter
import kotlin.random.Random
import kotlin.test.assertEquals

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.smt.TestSAT"
*/
class TestSAT {
  val rand = Random(0.also { println("Using seed: $it") })
  /*
  ./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.smt.TestSAT.testBMatInv"
  */
  @Test
  fun testBMatInv() = SMTInstance().solve {
    val dim = 10
    // https://www.koreascience.or.kr/article/JAKO200507523302678.pdf#page=3
    // "It is well known that the permutation matrices are the only invertible Boolean matrices..."
    val p = (0 until dim).shuffled(rand)
    println("Permutation:\n" + p.joinToString(" "))
    val A = FreeMatrix(SAT_ALGEBRA, dim) { i, j -> Literal(j == p[i]) }
    val P = BooleanMatrix(A.data.map { it.toBool()!! })
    println("Permutation matrix:$P")
    val B = FreeMatrix(SAT_ALGEBRA, dim) { i, j -> BoolVar("B$i$j") }

    val isInverse = (A * B * A) eq A
    val solution = solveBoolean(isInverse)

//    println(solution.entries.joinToString("\n") { it.key.toString() + "," + it.value })

    val sol = BooleanMatrix(B.data.map { solution[it]!!})
    println("Inverse permutation matrix:$sol")

    val a = BooleanMatrix(dim) { i, j -> j == p[i] }
    val b = BooleanMatrix(dim) { i, j -> sol[i][j] }
    assertEquals(a * b * a, a)
    // https://math.stackexchange.com/questions/98549/the-transpose-of-a-permutation-matrix-is-its-inverse
    assertEquals(P.transpose, b)
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.smt.TestSAT.testUTXORMatFixpoint"
*/
   @Test
   fun testUTXORMatFixpoint() = SMTInstance().solve {
     val dim = 20
     val setVars = setOf(0 to dim - 1, 0 to 1, 2 to 3, 4 to 5)
     val A = FreeMatrix(XOR_SAT_ALGEBRA, dim) { i, j ->
       if (i to j in setVars) Literal(true)
       else if (j >= i + 1) BoolVar("V$i.$j")
       else Literal(false)
     }

     val fpOp = A + A * A

     println("A:\n$A")
     println("Solving for UT form:\n" + fpOp.map { if("$it" != "false") 1 else "" })

     val isFixpoint = fpOp eqUT A

     val solution = solveBoolean(isFixpoint)
     val D = BooleanMatrix(XOR_ALGEBRA, A.data.map { solution[it] ?: it.toBool()!! })

     println("Decoding:\n$D")

     assertEquals(D, D + D * D)
     println("Passed.")
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.smt.TestSAT.testUTGF2MatFixpoint"
*/

  fun testUTGF2MatFixpoint() = SMTInstance().solve {
    val dim = 20
    val setVars = setOf(0 to dim - 1, 0 to 1, 2 to 3, 4 to 5)
    val A = FreeMatrix(GF2_SMT_ALGEBRA, dim) { i, j ->
      if (i to j in setVars) Literal(1)
      else if (j >= i + 1) IntVar("V$i.$j")
      else Literal(0)
    }

    val fpOp = A + A * A

    println("A:\n$A")
    println("Solving for UT form:\n" + fpOp.map { if("$it" != "false") 1 else "" } )

    val isFixpoint = fpOp eqUT A

    val solution = solveInteger(isFixpoint)
    val D = FreeMatrix(INTEGER_FIELD, A.data.map { solution[it] ?: it.toInt()!! } )

    println("Decoding:\n$D")

    assertEquals(D, D + D * D)
    println("Passed.")
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.smt.TestSAT.testUTBMatFixpoint"
*/
  @Test
  fun testUTBMatFixpoint() = SMTInstance().solve {
    val dim = 20
    val setVars = setOf(0 to dim - 1)
    val A = FreeMatrix(SAT_ALGEBRA, dim) { i, j ->
      if (i to j in setVars) Literal(true)
      else if (j >= i + 1 && j * i % 3 < 1 ) BoolVar("V$i.$j")
      else Literal(false)
    }

    val fpOp = A + A * A

    println("A:\n$A")
    println("Solving for UT form:\n" + fpOp.map { if("$it" != "false") 1 else "" } )

    val isFixpoint = fpOp eqUT A

    val solution = solveBoolean(isFixpoint)
    val D = BooleanMatrix(BOOLEAN_ALGEBRA, A.data.map { solution[it] ?: it.toBool()!! } )

    println("Decoding:\n${D.toString().replace("0", " ")}")

    assertEquals(D, D + D * D)
    println("Passed.")
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.smt.TestSAT.testRepeatInv"
*/
//  @Test
  fun testRepeatInv() = repeat(100) { testBMatInv() }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.smt.TestSAT.testSetIntersectionOneHot"
*/
  @Test
  fun testSetIntersectionOneHot() = SMTInstance().solve {
    val dim = 30
    val len = 20
    val universe = (1 until dim).toList()

    fun draw() = universe.shuffled(rand).take(len).map { universe.indexOf(it) }

    val setA = draw().toSet()
    val setB = draw().toSet()
    fun Set<Int>.encodeAsMatrix() =
      FreeMatrix(SAT_ALGEBRA, len, dim) { i, j -> Literal(elementAt(i) == j) }

    val A = setA.encodeAsMatrix()
    val X = FreeMatrix(SAT_ALGEBRA, dim) { i, j ->
      if (i == j) BoolVar("B$i") else Literal(false)
    }
    val B = setB.encodeAsMatrix()
    val dontCare = BoolVar("dc")
    val Y = FreeMatrix(SAT_ALGEBRA, len) { _, _ -> dontCare }

    val intersection = (A * X * B.transpose) eq Y
    val solution = solveBoolean(intersection)

    val expected = setA intersect setB
    val actual = (solution.keys - dontCare.formula).map { "$it".drop(1).toInt() }.toSet()
    println("Expected: $expected")
    println("Actual  : $expected")

    assertEquals(expected, actual)
  }

  fun <T> Collection<T>.powerset(): Set<Set<T>> = when {
    isEmpty() -> setOf(setOf())
    else -> drop(1).powerset().let { it + it.map { it + first() } }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.smt.TestSAT.testTwoWayJoin"
*/
  fun testTwoWayJoin() = SMTInstance().solve {
    val vars = setOf("A", "B", "C", "D")
    val grammar = setOf(
      "A" to ("A" to "C"),
      "B" to ("C" to "D"),
      "C" to ("C" to "A")
    )

    val pwrset = vars.powerset() - setOf(emptySet())
    val allPairs = pwrset * pwrset
    val allTriples = allPairs.map { (s1, s2) ->
      val s3 = (s1 * s2).flatMap { (a, b) ->
        grammar.filter { (a to b) == it.second }.map { it.first } }.toSet()
      (s1 to s2 to s3)//.also { println("" + it.first + " join " + it.second + " := " + it.third) }
    }

    val designMatrix = FreeMatrix(XOR_SAT_ALGEBRA, vars.size) { r, c ->
      BoolVar("G$r.$c")
    }

    val constraint = allTriples.map { (s1, s2, s3) ->
      val (X, Y, Z) = encSetToMat(vars, s1) to encSetToMat(vars, s2) to encSetToMat(vars, s3)
//      println("X:\n$X")
//      println("Y:\n$Y")
//      println("Z:\n$Z")
      val tx = (X * designMatrix).transpose * Y
      if(tx.data.zip(Z.data).any { (a, b) ->
          a == Literal(true) && b == Literal(false) ||
            b == Literal(true) && a == Literal(false)
        }) {
        println(tx)
        println(Z)
        println()
        println()
      }

      tx eq Z
    }.reduce { acc, formula -> acc and formula }

    val solution = solveBoolean(constraint)

    val G = BooleanMatrix(designMatrix.data.map { solution[it]!! })

    println(G)
  }

  fun <T> SMTInstance.encSetToMat(universe: Set<T>, set: Set<T>, indU: List<T> = universe.toList()) =
    FreeMatrix(SAT_ALGEBRA, universe.size) { r:Int, c: Int ->
      Literal(r == c && indU[r] in set)
    }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.smt.TestSAT.testRepeatSetInt"
*/
//  @Test
  fun testRepeatSetInt() = repeat(100) { testSetIntersectionOneHot() }
}