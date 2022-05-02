package ai.hypergraph.kaliningraph.sat

import ai.hypergraph.kaliningraph.tensor.*
import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.test.assertEquals

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.SATTest"
*/
class SATTest {
  val rand = Random(Random.nextInt().also { println("Using seed: $it") })

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.SATTest.testBMatInv"
*/
  @Test
  fun testBMatInv() = repeat(100) { 
    val dim = 10
    // https://www.koreascience.or.kr/article/JAKO200507523302678.pdf#page=3
    // "It is well known that the permutation matrices are the only invertible Boolean matrices..."
    val p = (0 until dim).shuffled(rand)
//    println("Permutation:\n" + p.joinToString(" "))
    val A = FreeMatrix(SAT_ALGEBRA, dim) { i, j -> BLit(j == p[i]) }
    val P = BooleanMatrix(A.data.map { it.toBool() })
//    println("Permutation matrix:$P")
    val B = FreeMatrix(SAT_ALGEBRA, dim) { i, j -> BVar("B${i}_$j") }

    val isInverse = (A * B * A) eq A
    val solution = isInverse.solve()

//    println(solution.entries.joinToString("\n") { it.key.toString() + "," + it.value })

    val sol = BooleanMatrix(B.data.map { solution[it]!! })
//    println("Inverse permutation matrix:$sol")

    val a = BooleanMatrix(dim) { i, j -> j == p[i] }
    val b = BooleanMatrix(dim) { i, j -> sol[i][j] }
    assertEquals(a * b * a, a)
    // https://math.stackexchange.com/questions/98549/the-transpose-of-a-permutation-matrix-is-its-inverse
    assertEquals(P.transpose, b)
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.SATTest.testUTXORMatFixpoint"
*/
   @Test
   fun testUTXORMatFixpoint()  {
     val dim = 20
     val setVars = setOf(0 to dim - 1, 0 to 1, 2 to 3, 4 to 5)
     val A = FreeMatrix(XOR_SAT_ALGEBRA, dim) { i, j ->
       if (i to j in setVars) BLit(true)
       else if (j >= i + 1) BVar("V${i}_$j")
       else BLit(false)
     }

     val fpOp = A + A * A

     println("A:\n$A")
     println("Solving for UT form:\n" + fpOp.map { if(it != F) 1 else "" })

     val isFixpoint = fpOp eqUT A

     val solution = isFixpoint.solve()
     val D = BooleanMatrix(XOR_ALGEBRA, A.data.map { solution[it] ?: it.toBool() })

     println("Decoding:\n$D")

     assertEquals(D, D + D * D)
     println("Passed.")
  }


/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.SATTest.testUTBMatFixpoint"
*/
  @Test
  fun testUTBMatFixpoint()  {
    val dim = 20
    val setVars = setOf(0 to dim - 1)
    val A = FreeMatrix(SAT_ALGEBRA, dim) { i, j ->
      if (i to j in setVars) BLit(true)
      else if (j >= i + 1 && j * i % 3 < 1 ) BVar("V${i}_$j")
      else BLit(false)
    }

    val fpOp = A + A * A

    println("A:\n$A")
    println("Solving for UT form:\n" + fpOp.map { if(it != F) 1 else "" } )

    val isFixpoint = fpOp eqUT A

    val solution = isFixpoint.solve()

    val D = BooleanMatrix(BOOLEAN_ALGEBRA, A.data.map { solution[it] ?: it.toBool() })

    println("Decoding:\n${D.toString().replace("0", " ")}")

    assertEquals(D, D + D * D)
    println("Passed.")
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.SATTest.testSetIntersectionOneHot"
*/
  @Test
  fun testSetIntersectionOneHot() = repeat(100) {
    val dim = 10
    val len = 6
    val universe = (0 until dim).toList()

    fun draw() = universe.shuffled(rand).take(len).map { universe.indexOf(it) }

    val setA = draw().toSet()
    val setB = draw().toSet()
    fun Set<Int>.encodeAsMatrix(universe: Set<Int>, dim: Int = universe.size) =
      FreeMatrix(SAT_ALGEBRA, size, dim) { i, j -> BLit(elementAt(i) == universe.elementAt(j)) }

    val A = setA.encodeAsMatrix(universe.toSet())
    val X = FreeMatrix(SAT_ALGEBRA, dim) { i, j ->
      if (i == j) BVar("B$i") else BVar("OD${i}_$j")
    }
    val B = setB.encodeAsMatrix(universe.toSet())
    val dontCare = BVar("dc")
    val Y = FreeMatrix(SAT_ALGEBRA, len) { _, _ -> dontCare }

//    println("A:$A")
//    println("X:$X")
//    println("B:$B")
//    println("Y:$Y")

    val intersection = (A * X * B.transpose) eq Y
    val solution = intersection.solve()

    val expected = setA intersect setB
    val actual = solution.keys.mapNotNull { "$it".drop(1).toIntOrNull() }.toSet()
//    println("Expected: $expected")
//    println("Actual  : $actual")

    assertEquals(expected, actual)
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.SATTest.testMatEq"
*/
  @Test
  fun testMatEq()  {
    val mvars = FreeMatrix(3) { r, c -> List(3) { BVar("R${r}_${c}_$it") } }
    val lits = FreeMatrix(3) { r, c -> List(3) { BLit(Random.nextBoolean()) } }
    val testveq = mvars matEq lits

    val ts = testveq.solve()
    val solution = FreeMatrix(mvars.data.map { it.map { BLit(ts[it]!!) } })

    assertEquals(lits, solution)
  }
}