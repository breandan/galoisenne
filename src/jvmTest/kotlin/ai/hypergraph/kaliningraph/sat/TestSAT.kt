package ai.hypergraph.kaliningraph.sat

import ai.hypergraph.kaliningraph.parsing.*
import ai.hypergraph.kaliningraph.tensor.*
import ai.hypergraph.kaliningraph.types.*
import org.junit.jupiter.api.Test
import org.logicng.formulas.*
import org.sosy_lab.java_smt.api.FormulaManager
import kotlin.random.Random
import kotlin.test.*

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.TestSAT"
*/
class TestSAT {
  val rand = Random(Random.nextInt().also { println("Using seed: $it") })
/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.TestSAT.testBMatInv"
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
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.TestSAT.testUTXORMatFixpoint"
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
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.TestSAT.testUTBMatFixpoint"
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
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.TestSAT.testSetIntersectionOneHot"
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
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.TestSAT.testMatEq"
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

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.TestSAT.testVecJoin"
*/
  @Test
  fun testVecJoin() {
    val cfg = "S -> ( S ) | ( ) | S S".parseCFG()

    val pwrsetSquared = cfg.variables.take(5).depletedPS().let { it * it }
    println("Cardinality:" + pwrsetSquared.size)

    /*
     * Checks that bitvector joins faithfully encode set join, i.e.:
     *
     *      S   ⋈   S' = Z for all subsets S', S' in P(Variables)
     *      ⇵       ⇵    ⇵
     *      V   ☒   V' = Z'
     */
    pwrsetSquared.forEach { (a, b) ->
      with(cfg) {
        assertEquals(toBitVec(join(a, b)), join(toBitVec(a), toBitVec(b)))
        assertEquals(join(a, b), toNTSet(join(toBitVec(a), toBitVec(b))))
      }
    }
  }

  val xujieGrammar = """
       S -> L1 T1
       S -> L2 T2
      T1 -> S R1
      T2 -> S R2
      L1 -> (
      L2 -> [
      R1 -> )
      R2 -> ]
    """

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.TestSAT.testXujieExample"
*/
  @Test
  fun testXujieExample() {
    val cfg = xujieGrammar.trimIndent().parseCFG(normalize=false)

    (setOf("T2", "T1", "S") to setOf("T2", "S", "R1", "R2")).let { (a, b) ->
      with(cfg) {
        println(cfg.prettyPrint())
        println("Set A:$a")
        println("Set B:$b")
        println("Join A*B:" + join(a, b))
        println("A:" + toBitVec(a))
        println("B:" + toBitVec(b))

        println("BV join:" + join(toBitVec(a), toBitVec(b)))
        println("Set join:" + toNTSet(join(toBitVec(a), toBitVec(b))))

        assertEquals(toBitVec(join(a, b)), join(toBitVec(a), toBitVec(b)))
        assertEquals(join(a, b), toNTSet(join(toBitVec(a), toBitVec(b))))
      }
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.TestSAT.testSingleStepMultiplication"
*/
  @Test
  fun testSingleStepMultiplication()  {
    xujieGrammar.trimIndent().parseCFG(normalize = false).let { cfg ->
      (setOf("T1", "S", "L2") to setOf("T2", "S", "R1", "R2")).let { (a, b) ->
        val trueJoin = cfg.join(a, b)
        println("True join: $trueJoin")

        val litA: List<Formula> = cfg.toBitVec(a).toLitVec()
        val satB: List<Formula> = List(cfg.toBitVec(b).size) { i -> BVar("BV$i") }
        val litC: List<Formula> = cfg.toBitVec(cfg.join(a, b)).toLitVec()

        println("\nSolving for B:")
        val solution = (cfg.join(litA, satB) vecEq litC).solve()
        println(solution)
        println("B=" + satB.map { solution[it] ?: false }.decodeWith(cfg))

        val satA: List<Formula> = List(cfg.toBitVec(b).size) { i -> BVar("AV$i") }
        val litB: List<Formula> = cfg.toBitVec(b).toLitVec()

        println("\nSolving for A:")
        val solution1 = (cfg.join(satA, litB) vecEq litC).solve()
        println(solution1)
        println("A=" + satA.map { solution1[it] ?: false }.decodeWith(cfg))

        val satC: List<Formula> = List(cfg.toBitVec(b).size) { i -> BVar("CV$i") }

        println("\nSolving for C:")
        val solution2 = (cfg.join(litA, litB) vecEq satC).solve()
        println(solution2)
        val bitVecJoin = satC.map { solution2[it]!! }.decodeWith(cfg)
        println("C=$bitVecJoin")

        assertEquals(trueJoin, bitVecJoin)
      }
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.TestSAT.testXujieMethod"
*/
  @Test
  fun testXujieMethod()  {
    val cfg = "S -> ( S ) | [ S ] | [ ] | | ( ) | S S".parseCFG()

    val decodedString = "[_()_[__]_()__"
      .also { println("$it is being synthesized...") }
      .synthesizeFromFPSolving(cfg).first()

    println("$decodedString generated by SATValiant!")

    val isValid = cfg.isValid(decodedString)
    println("$decodedString is${if (isValid) " " else " not "}valid according to SetValiant!")

    assertTrue(isValid)
}

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.TestSAT.testXujieParsingWithFP"
*/
  @Test
  fun testXujieParsingWithFP()  {
    val cfg = """
      S -> A + B | A * B | S + S | S * S
      A -> X | [ A ]
      B -> Y | ( B )
    """.parseCFG()

  val decodedString = "[X_+(_)__________________"
      .also { println("$it is being synthesized...") }
      .synthesizeFromFPSolving(cfg).take(10).forEach { decodedString ->
        println("$decodedString generated by fixed point solving!")

        val isValid = cfg.isValid(decodedString)
        println("$decodedString is${if (isValid) " " else " not "}valid according to SetValiant!")

        assertTrue(isValid)
      }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.TestSAT.testXujieParsingWithFP1"
*/
  @Test
  fun testXujieParsingWithFP1()  {
    val cfg = """
      S -> A | B | A + B | A * B | S + S | S * S | [ S ] | ( S )
      A -> X | Y
      B -> Y | Z
    """.parseCFG()

    val decodedString = "[X_+(_)_(____"
      .also { println("$it is being synthesized...") }
      .synthesizeFromFPSolving(cfg).first()

    println("$decodedString generated by fixed point solving!")

    val isValid = cfg.isValid(decodedString)
    println("$decodedString is${if (isValid) " " else " not "}valid according to SetValiant!")

    assertTrue(isValid)
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.TestSAT.testMultiSAT"
*/
  @Test
  fun testMultiSAT() {
  val cfg = """
      S -> A | B | A + B | A * B | S + S | S * S | [ S ] | ( S )
      A -> X | Y
      B -> X | Z
    """.parseCFG()

    val distinct = mutableSetOf<String>()
    "[X_+(_)_(______".also { println("$it is being synthesized...") }
      .synthesizeFromFPSolving(cfg).take(10)
      .forEach { decodedString ->
        assertTrue(decodedString !in distinct)
        distinct += decodedString
        println("$decodedString generated by fixed point solving!")

        val isValid = cfg.isValid(decodedString)
        println("$decodedString is${if (isValid) " " else " not "}valid according to SetValiant!")

        assertTrue(isValid)
      }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.TestSAT.testAlgebraicLanguage"
*/
  @Test
  fun testAlgebraicLanguage() {
    val cfg = """
      S -> S + S | S * S | S - S | S / S | ( S )
      S -> 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9
      S -> X | Y | Z
    """.parseCFG()

    val decodedString = "X+__Z__*___"
      .also { println("$it is being synthesized...") }
      .synthesizeFromFPSolving(cfg).first()

    println("$decodedString generated by SATValiant!")

    val isValid = cfg.isValid(decodedString)
    println("$decodedString is${if (isValid) " " else " not "}valid according to SetValiant!")

    assertTrue(isValid)
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.TestSAT.testMatrixJoin"
*/
  @Test
  fun testMatrixJoin() {
    val cfg = xujieGrammar.parseCFG(false)
    println(cfg.prettyPrint())
    val vars = cfg.variables

    // We only use off-diagonal entries
    val odMat = FreeMatrix(SAT_ALGEBRA, vars.size) { i, j ->
      if(i == j) BLit(true) else BVar("OD${i}_$j")
    }

    fun <T> Set<T>.encodeAsDMatrix(universe: Set<T>) =
      FreeMatrix(SAT_ALGEBRA, universe.size) { i, j ->
        if (i == j) BLit(universe.elementAt(i) in this)
        else odMat[i, j]
      }

    val allSubsetPairs = vars.depletedPS().let { it * it }
    val nonemptyTriples = allSubsetPairs.mapNotNull { (s1, s2) ->
      val s3 = cfg.join(s1, s2)
      if (s3.isEmpty()) null else (s1 to s2 to s3)
    }

//    val designMatrix = FreeMatrix(SAT_ALGEBRA, vars.size) { r, c ->
//      BVar("G${r}_$c")
//    }

      val constraint = nonemptyTriples.take(180).mapIndexed { i, (s1, s2, s3) ->
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

      val dontCare = BVar("dc$i")
      val DC = FreeMatrix(SAT_ALGEBRA, rows) { _, _ -> dontCare }
      tx eq DC
    }.reduce { acc, formula -> acc and formula }

//    println("Solving:${nonemptyTriples.size}")

    val solution = constraint.solve()

    val G = FreeMatrix(odMat.data.map { solution[it]?.let { if (it) "1" else "0" } ?: "UNK" })

//    println("Design matrix: $G")

    nonemptyTriples.take(180).shuffled().forEachIndexed { i, (s1, s2, s3) ->
      val rows = maxOf(s1.size, s2.size)

      val (X, Y) =
        s1.encodeAsMatrix(vars, rows) to
        s2.encodeAsMatrix(vars, rows)

      // Synthesized * operator
      val D = FreeMatrix(SAT_ALGEBRA, G.numRows) { i, j ->
        if(i == j) BVar("K$i") else BLit(G[i, j] == "1")
      }

      val tx = (X * D * D * Y.transpose) // * D * is UNSAT but * D * D * is SAT?
      val dontCare = BVar("DDC$i")
      val DC = FreeMatrix(SAT_ALGEBRA, rows) { _, _ -> dontCare }

      val diag = (tx eq DC).solve()

      val actual = diag.keys
        .mapNotNull { "$it".drop(1).toIntOrNull() }
        .toSet().map { vars.elementAt(it) }

//      println("Expected: $s3")
//      println("Actual  : $actual")
    }
  }
}