package ai.hypergraph.kaliningraph.smt

import ai.hypergraph.kaliningraph.parsing.*
import ai.hypergraph.kaliningraph.tensor.*
import ai.hypergraph.kaliningraph.types.*
import org.junit.jupiter.api.Test
import kotlin.collections.filter
import kotlin.random.Random
import kotlin.test.*

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
    val dim = 10
    val len = 6
    val universe = (0 until dim).toList()

    fun draw() = universe.shuffled(rand).take(len).map { universe.indexOf(it) }

    val setA = draw().toSet()
    val setB = draw().toSet()
    fun Set<Int>.encodeAsMatrix(universe: Set<Int>, dim: Int = universe.size) =
      FreeMatrix(SAT_ALGEBRA, size, dim) { i, j -> Literal(elementAt(i) == universe.elementAt(j)) }

    val A = setA.encodeAsMatrix(universe.toSet())
    val X = FreeMatrix(SAT_ALGEBRA, dim) { i, j ->
      if (i == j) BoolVar("B$i") else BoolVar("OD$i.$j")
    }
    val B = setB.encodeAsMatrix(universe.toSet())
    val dontCare = BoolVar("dc")
    val Y = FreeMatrix(SAT_ALGEBRA, len) { _, _ -> dontCare }

    println("A:$A")
    println("X:$X")
    println("B:$B")
    println("Y:$Y")

    val intersection = (A * X * B.transpose) eq Y
    val solution = solveBoolean(intersection)

    val expected = setA intersect setB
    val actual = solution.keys.mapNotNull { "$it".drop(1).toIntOrNull() }.toSet()
    println("Expected: $expected")
    println("Actual  : $actual")

    assertEquals(expected, actual)
  }

  fun <T> Collection<T>.powerset(): Set<Set<T>> = when {
    isEmpty() -> setOf(setOf())
    else -> drop(1).powerset().let { it + it.map { it + first() } }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.smt.TestSAT.testTwoWayJoin"
*/
  @Test
  fun testTwoWayJoin() = SMTInstance().solve {
    val cfl = """
      A -> A C
      B -> C D
      B -> E H
      C -> C A
    """.parseCFL(false)
    println(cfl.prettyPrint())
    val vars = setOf("A", "B", "C", "D", "E", "F", "G", "H", "I")
    val grammar = cfl

    fun <T> Set<T>.encodeAsMatrix(
      universe: Set<T>,
      rows: Int,
      cols: Int = universe.size,
    ) = FreeMatrix(SAT_ALGEBRA, rows, cols) { i, j ->
        Literal(if (size <= i) false else elementAt(i) == universe.elementAt(j))
      }

    // We only use off-diagonal entries
    val odMat = FreeMatrix(SAT_ALGEBRA, vars.size) { i, j ->
      if(i == j) Literal(true) else BoolVar("OD$i.$j")
    }

    fun <T> Set<T>.encodeAsDMatrix(universe: Set<T>) =
      FreeMatrix(SAT_ALGEBRA, universe.size) { i, j ->
        if (i == j) Literal(universe.elementAt(i) in this)
        else odMat[i, j]
      }

    fun join(s1: Set<String>, s2: Set<String>) =
      (s1 * s2).flatMap { (a, b) ->
        grammar.filter { listOf(a, b) == it.second }.map { it.first } }.toSet()

    val pwrset = vars.powerset() - setOf(emptySet())
    val allPairs = (pwrset * pwrset)
    val allTriples = allPairs.mapNotNull { (s1, s2) ->
      val s3 = join(s1, s2)
      if(s3.isEmpty()) null else (s1 to s2 to s3)
    }

//    val designMatrix = FreeMatrix(SAT_ALGEBRA, vars.size) { r, c ->
//      BoolVar("G$r.$c")
//    }

    val constraint = allTriples.take(180).mapIndexed { i, (s1, s2, s3) ->
      val rows = maxOf(s1.size, s2.size)

      val (X, Y, designMatrix) =
      s1.encodeAsMatrix(vars, rows) to
      s2.encodeAsMatrix(vars, rows) to
      s3.encodeAsDMatrix(vars)

//      println("S1: $s1")
//      println(X.toString())
//      println("S2: $s2")
//      println(Y.toString())
//      println("S3: $s3")
//      println(designMatrix)

      // https://dl.acm.org/doi/pdf/10.1145/3318464.3380607
      // http://www.cs.cmu.edu/afs/cs/user/dwoodruf/www/gwwz.pdf
      val tx = X * designMatrix * designMatrix * Y.transpose
//      println()
//      println()

      val dontCare = BoolVar("dc$i")
      val DC = FreeMatrix(SAT_ALGEBRA, rows) { _, _ -> dontCare }
      tx eq DC
    }.reduce { acc, formula -> acc and formula }

    println("Solving:${allTriples.size}")

    val solution = solveBoolean(constraint)

    val G = FreeMatrix(odMat.data.map { solution[it]?.let { if(it) "1" else "0" } ?: "UNK" })

    println("Design matrix: $G")

    allTriples.take(180).shuffled().forEachIndexed { i, (s1, s2, s3) ->
      val rows = maxOf(s1.size, s2.size)

      val (X, Y) = s1.encodeAsMatrix(vars, rows) to s2.encodeAsMatrix(vars, rows)
      // Synthesized * operator
      val D = FreeMatrix(SAT_ALGEBRA, G.numRows) { i, j ->
        if(i == j) BoolVar("K$i") else Literal(G[i, j] == "1")
      }

      val tx = (X * D * D * Y.transpose) // * D * is UNSAT but * D * D * is SAT?
      val dontCare = BoolVar("DDC$i")
      val DC = FreeMatrix(SAT_ALGEBRA, rows) { _, _ -> dontCare }

      val diag = solveBoolean(tx eq DC)

      val actual = diag.keys
        .mapNotNull { "$it".drop(1).toIntOrNull() }
        .toSet().map { vars.elementAt(it) }

//      println("Expected: $s3")
//      println("Actual  : $actual")
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.smt.TestSAT.testRepeatSetInt"
*/
//  @Test
  fun testRepeatSetInt() = repeat(100) { testSetIntersectionOneHot() }
}