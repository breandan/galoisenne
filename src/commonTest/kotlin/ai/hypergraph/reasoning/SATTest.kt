package ai.hypergraph.reasoning

import ai.hypergraph.kaliningraph.tensor.*
import ai.hypergraph.kaliningraph.times
import ai.hypergraph.reasoning.*
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/*
./gradlew jvmTest --tests "ai.hypergraph.reasoning.SATTest"
*/
class SATTest {
  val rand = Random(Random.nextInt().also { println("Using seed: $it") })

  /*
  ./gradlew jvmTest --tests "ai.hypergraph.reasoning.SATTest.testRMatInv"
  */
  @Test
  fun testRMatInv() = repeat(100) {
    val dim = 10
    // https://www.koreascience.or.kr/article/JAKO200507523302678.pdf#page=3
    // "It is well known that the permutation matrices are the only invertible Boolean matrices..."
    val p = (0 until dim).shuffled(rand)
//    println("Permutation:\n" + p.joinToString(" "))
    val A = FreeMatrix(RSAT_ALGEBRA, dim) { i, j -> BLit(j == p[i]) }
    val P = BooleanMatrix(A.data.map { it == T })
//    println("Permutation matrix:$P")
    val B = RMatVar("B", RSAT_ALGEBRA, dim)

    val isInverse = (A * B * A) eq A
    val solution = isInverse.solution

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
  ./gradlew jvmTest --tests "ai.hypergraph.reasoning.SATTest.testGF2Fixpoint"
  */
  @Test
  fun testGF2Fixpoint() {
    val dim = 5
    val A: FreeMatrix<CNF> = RMatVar("a", RXOR_SAT_ALGEBRA, dim)

    val solution = ((A * A) eq A).solution

    val B = BooleanMatrix(XOR_ALGEBRA, A.data.map { solution[it]!! })
    assertEquals(B, B * B)
  }

  /*
  ./gradlew jvmTest --tests "ai.hypergraph.reasoning.SATTest.testGF2Eigenvector"
  */
//  @Test
  fun testGF2Eigenvector() {
    val dim = 10
    val A: FreeMatrix<CNF> = FreeMatrix(RXOR_SAT_ALGEBRA, List(dim * dim) { BLit(Random.nextBoolean()) })
    val x: FreeMatrix<CNF> = RMatVar("a", RXOR_SAT_ALGEBRA, dim, 1)

    // Solves x != 0 in Ax = x
    val model = ((A * x) eq (x) ʌ (x.data).reduce { acc, f -> f v acc })
    try {
      val solution = model.solution
      val s = BooleanMatrix(dim, 1, x.data.map { solution[it]!! }, XOR_ALGEBRA).also { println(it) }
      val a = BooleanMatrix(XOR_ALGEBRA, A.data.map { it == T })

      assertEquals(a * s, s)
    } catch(_: Exception) {}
  }

  /*
  ./gradlew jvmTest --tests "ai.hypergraph.reasoning.SATTest.testBooleanEigenvector"
  */
//  @Test
  fun testBooleanEigenvector() {
    val dim = 10
    val A = RMatVar("a", RSAT_ALGEBRA, dim, dim)
    val x: FreeMatrix<CNF> = RMatVar("x", RSAT_ALGEBRA, dim, 1)

    // Solves x != 0 in Ax = x
    val solution = (
        A * x eq x ʌ
        // Eliminates trivial symmetries
        (A neq A * A) ʌ
        (A neq A * A * A) ʌ
        // Eliminates trivial eigenvectors
        x.data.reduce { acc, f -> acc v f } ʌ
        x.data.reduce { a, f -> a ʌ f }.negate()
      ).solution

    val a = BooleanMatrix(BOOLEAN_ALGEBRA, A.data.map { solution[it]!! }).also { println(it * it * it) }
    val s = BooleanMatrix(dim, 1, x.data.map { solution[it]!! }, BOOLEAN_ALGEBRA).also { println(it) }

    assertEquals(a * s, s)
  }

  /*
./gradlew jvmTest --tests "ai.hypergraph.reasoning.SATTest.testUTXORMatFixpoint"
*/
//  @Test
  fun testUTXORMatFixpoint() {
    val dim = 20
    val setVars = setOf(0 to dim - 1, 0 to 1, 2 to 3, 4 to 5)
    val A: FreeMatrix<CNF> = FreeMatrix(RXOR_SAT_ALGEBRA, dim) { i, j ->
      if (i to j in setVars) T
      else if (j >= i + 1) BVar("V${i}_$j")
      else F
    }

    val fpOp = A + A * A

    println("A:\n$A")
    println("Solving for UT form:\n" + fpOp.map { if (it != F) 1 else "" })

    val isFixpoint = fpOp eqUT A

    val solution = isFixpoint.solution
    val D = BooleanMatrix(XOR_ALGEBRA, A.data.map { solution[it]?: (it == T) })

    println("Decoding:\n$D")

    assertEquals(D, D + D * D)
  }

  /*
  ./gradlew jvmTest --tests "ai.hypergraph.reasoning.SATTest.testUTRMatFixpoint"
  */
//  @Test
  fun testUTRMatFixpoint()  {
    val dim = 20
    val setVars = setOf(0 to dim - 1)
    val A = FreeMatrix(RSAT_ALGEBRA, dim) { i, j ->
      if (i to j in setVars) T
      else if (j >= i + 1 && j * i % 3 < 1 ) BVar("V${i}_$j") else F
    }

    val fpOp = A + A * A

    println("A:\n$A")
    println("Solving for UT form:\n" + fpOp.map { if (it != F) 1 else "" } )

    val isFixpoint = fpOp eqUT A

    val solution = isFixpoint.solution

    val D = BooleanMatrix(BOOLEAN_ALGEBRA, A.data.map { solution[it] ?: (it == T) })

    println("Decoding:\n${D.toString().replace("0", " ")}")

    assertEquals(D, D + D * D)
  }

  /*
  ./gradlew jvmTest --tests "ai.hypergraph.reasoning.SATTest.testSetIntersectionOneHot"
  */
//  @Test
  fun testSetIntersectionOneHot() = repeat(100) {
    val dim = 10
    val len = 6
    val universe = (0 until dim).toList()

    fun draw() = universe.shuffled(rand).take(len).map { universe.indexOf(it) }

    val setA = draw().toSet()
    val setB = draw().toSet()
    fun Set<Int>.encodeAsMatrix(universe: Set<Int>, dim: Int = universe.size) =
      FreeMatrix(RSAT_ALGEBRA, size, dim) { i, j -> BLit(elementAt(i) == universe.elementAt(j)) }

    val A = setA.encodeAsMatrix(universe.toSet())
    val X = FreeMatrix(RSAT_ALGEBRA, dim) { i, j ->
      if (i == j) BVar("B$i") else BVar("OD${i}_$j")
    }
    val B = setB.encodeAsMatrix(universe.toSet())
    val dontCare = BVar("dc")
    val Y = FreeMatrix(RSAT_ALGEBRA, len) { _, _ -> dontCare }

//    println("A:$A")
//    println("X:$X")
//    println("B:$B")
//    println("Y:$Y")

    val intersection = (A * X * B.transpose) eq Y
    val solution = intersection.solution

    val expected = setA intersect setB
    val actual = solution.keys.mapNotNull { "$it".drop(1).toIntOrNull() }.toSet()
//    println("Expected: $expected")
//    println("Actual  : $actual")

    assertEquals(expected, actual)
  }

  /*
  ./gradlew jvmTest --tests "ai.hypergraph.reasoning.SATTest.testValiantMatEq"
  */
//  @Test // TODO: fix
//  fun testValiantMatEq()  {
//    val mvars = FreeMatrix(3) { r, c -> Array(3) { BVar("R${r}_${c}_$it") } }
//    val lits = FreeMatrix(3) { _, _ -> Array(3) { BLit(Random.nextBoolean()) } }
//    val testveq = mvars.toUTMatrix() valiantMatEq lits.toUTMatrix()
//
//    val ts = testveq.solve()
//    val solution = FreeMatrix(mvars.data.map { it.map { BLit(ts[it]?: false ) }.toTypedArray() })
//
//    assertEquals(lits, solution)
//  }

  /*
  ./gradlew jvmTest --tests "ai.hypergraph.reasoning.SATTest.testMultipleFactories"
  */
//  @Test
//  fun testMultipleFactories()  {
//    val a = BVar("HV[cfgHash=123]")
//    val b = BVar("hello")
//
//    println((a xor b).solve())
//  }
}