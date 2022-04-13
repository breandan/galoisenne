package ai.hypergraph.kaliningraph.smt

import ai.hypergraph.kaliningraph.parsing.*
import ai.hypergraph.kaliningraph.tensor.*
import ai.hypergraph.kaliningraph.types.*
import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.test.*

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.smt.TestSAT"
*/
class TestSAT {
  val rand = Random(Random.nextInt().also { println("Using seed: $it") })
/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.smt.TestSAT.testBMatInv"
*/
  @Test
  fun testBMatInv() = repeat(100) { SMTInstance().solve {
    val dim = 10
    // https://www.koreascience.or.kr/article/JAKO200507523302678.pdf#page=3
    // "It is well known that the permutation matrices are the only invertible Boolean matrices..."
    val p = (0 until dim).shuffled(rand)
//    println("Permutation:\n" + p.joinToString(" "))
    val A = FreeMatrix(SAT_ALGEBRA, dim) { i, j -> Literal(j == p[i]) }
    val P = BooleanMatrix(A.data.map { it.toBool()!! })
//    println("Permutation matrix:$P")
    val B = FreeMatrix(SAT_ALGEBRA, dim) { i, j -> BoolVar("B$i$j") }

    val isInverse = (A * B * A) eq A
    val solution = solveBoolean(isInverse)

//    println(solution.entries.joinToString("\n") { it.key.toString() + "," + it.value })

    val sol = BooleanMatrix(B.data.map { solution[it]!! })
//    println("Inverse permutation matrix:$sol")

    val a = BooleanMatrix(dim) { i, j -> j == p[i] }
    val b = BooleanMatrix(dim) { i, j -> sol[i][j] }
    assertEquals(a * b * a, a)
    // https://math.stackexchange.com/questions/98549/the-transpose-of-a-permutation-matrix-is-its-inverse
    assertEquals(P.transpose, b)
  }}

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
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.smt.TestSAT.testSetIntersectionOneHot"
*/
  @Test
  fun testSetIntersectionOneHot() = repeat(100) {
    SMTInstance().solve {
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

//    println("A:$A")
//    println("X:$X")
//    println("B:$B")
//    println("Y:$Y")

    val intersection = (A * X * B.transpose) eq Y
    val solution = solveBoolean(intersection)

    val expected = setA intersect setB
    val actual = solution.keys.mapNotNull { "$it".drop(1).toIntOrNull() }.toSet()
//    println("Expected: $expected")
//    println("Actual  : $actual")

    assertEquals(expected, actual)
  }}

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.smt.TestSAT.testMatEq"
*/
  @Test
  fun testMatEq() = SMTInstance().solve {
    val mvars = FreeMatrix(3) { r, c -> List(3) { BoolVar("R$r.$c.$it") } }
    val lits = FreeMatrix(3) { r, c -> List(3) { Literal(Random.nextBoolean()) } }
    val testveq = mvars matEq lits

    val ts = solveBoolean(testveq)
    val solution = FreeMatrix(mvars.data.map { it.map { Literal(ts[it]!!) } })

    assertEquals(lits, solution)
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.smt.TestSAT.testVecJoin"
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
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.smt.TestSAT.testXujieExample"
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
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.smt.TestSAT.testSingleStepMultiplication"
*/
  @Test
  fun testSingleStepMultiplication() = SMTInstance().solve {
    xujieGrammar.trimIndent().parseCFG(normalize = false).let { cfg ->
      (setOf("T1", "S", "L2") to setOf("T2", "S", "R1", "R2")).let { (a, b) ->
        val trueJoin = cfg.join(a, b)
        println("True join: $trueJoin")

        val litA: List<SATF> = cfg.toBitVec(a).toLitVec(this)
        val satB: List<SATF> = List(cfg.toBitVec(b).size) { i -> BoolVar("BV$i") }
        val litC: List<SATF> = cfg.toBitVec(cfg.join(a, b)).toLitVec(this)

        println("\nSolving for B:")
        val solution = solveBoolean(cfg.join(litA, satB) vecEq litC)
        println(solution)
        println("B=" + satB.map { solution[it] ?: false }.decodeWith(cfg))

        val satA: List<SATF> = List(cfg.toBitVec(b).size) { i -> BoolVar("AV$i") }
        val litB: List<SATF> = cfg.toBitVec(b).toLitVec(this)

        println("\nSolving for A:")
        val solution1 = solveBoolean(cfg.join(satA, litB) vecEq litC)
        println(solution1)
        println("A=" + satA.map { solution1[it] ?: false }.decodeWith(cfg))

        val satC: List<SATF> = List(cfg.toBitVec(b).size) { i -> BoolVar("CV$i") }

        println("\nSolving for C:")
        val solution2 = solveBoolean(cfg.join(litA, litB) vecEq satC)
        println(solution2)
        val bitVecJoin = satC.map { solution2[it]!! }.decodeWith(cfg)
        println("C=$bitVecJoin")

        assertEquals(trueJoin, bitVecJoin)
      }
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.smt.TestSAT.testXujieMethod"
*/
  @Test
  fun testXujieMethod() = SMTInstance().solve {
    val cfg = "S -> ( S ) | [ S ] | [ ] | | ( ) | S S".parseCFG()

    val decodedString = "[_()_[__]_()__"
      .also { println("$it is being synthesized...") }
      .synthesizeFrom(cfg, this)

    println("$decodedString generated by SATValiant!")

    val isValid = cfg.isValid(decodedString)
    println("$decodedString is${if (isValid) " " else " not "}valid according to SetValiant!")

    assertTrue(isValid)
}

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.smt.TestSAT.testXujieParsingWithFP"
*/
  @Test
  fun testXujieParsingWithFP() = SMTInstance().solve {
    val cfg = """
      S -> A + B | A * B | S + S | S * S
      A -> X | [ A ]
      B -> Y | ( B )
    """.parseCFG()

    val decodedString = "[X_+(_)______________________"
      .also { println("$it is being synthesized...") }
      .synthesizeFromFPSolving(cfg, this).take(1).first()

    println("$decodedString generated by fixed point solving!")

    val isValid = cfg.isValid(decodedString)
    println("$decodedString is${if (isValid) " " else " not "}valid according to SetValiant!")

    assertTrue(isValid)
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.smt.TestSAT.testXujieParsingWithFP1"
*/
  @Test
  fun testXujieParsingWithFP1() = SMTInstance().solve {
    val cfg = """
      S -> A | B | A + B | A * B | S + S | S * S | [ S ] | ( S )
      A -> X | Y
      B -> Y | Z
    """.parseCFG()

    val decodedString = "[X_+(_)_(____"
      .also { println("$it is being synthesized...") }
      .synthesizeFromFPSolving(cfg, this).take(1).first()

    println("$decodedString generated by fixed point solving!")

    val isValid = cfg.isValid(decodedString)
    println("$decodedString is${if (isValid) " " else " not "}valid according to SetValiant!")

    assertTrue(isValid)
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.smt.TestSAT.testMultiSAT"
*/
  @Test
  fun testMultiSAT() = SMTInstance().solve {
    val cfg = """
      S -> A | B | A + B | A * B | S + S | S * S | [ S ] | ( S )
      A -> X | Y
      B -> X | Z
    """.parseCFG()

    val distinct = mutableSetOf<String>()
    "[X_+(_)_(______".also { println("$it is being synthesized...") }
      .synthesizeFromFPSolving(cfg, this).take(10)
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
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.smt.TestSAT.testAlgebraicLanguage"
*/
  @Test
  fun testAlgebraicLanguage() = SMTInstance().solve {
    val cfg = """
      S -> S + S | S * S | S - S | S / S | ( S )
      S -> 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9
      S -> X | Y | Z
    """.parseCFG()

    val decodedString = "X+__Z__*___"
      .also { println("$it is being synthesized...") }
      .synthesizeFrom(cfg, this)

    println("$decodedString generated by SATValiant!")

    val isValid = cfg.isValid(decodedString)
    println("$decodedString is${if (isValid) " " else " not "}valid according to SetValiant!")

    assertTrue(isValid)
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.smt.TestSAT.testMatrixJoin"
*/
  @Test
  fun testMatrixJoin() = SMTInstance().solve {
    val cfg = xujieGrammar.parseCFG(false)
    println(cfg.prettyPrint())
    val vars = cfg.variables

    // We only use off-diagonal entries
    val odMat = FreeMatrix(SAT_ALGEBRA, vars.size) { i, j ->
      if(i == j) Literal(true) else BoolVar("OD$i.$j")
    }

    fun <T> Set<T>.encodeAsDMatrix(universe: Set<T>) =
      FreeMatrix(SAT_ALGEBRA, universe.size) { i, j ->
        if (i == j) Literal(universe.elementAt(i) in this)
        else odMat[i, j]
      }

    val allSubsetPairs = vars.depletedPS().let { it * it }
    val nonemptyTriples = allSubsetPairs.mapNotNull { (s1, s2) ->
      val s3 = cfg.join(s1, s2)
      if (s3.isEmpty()) null else (s1 to s2 to s3)
    }

//    val designMatrix = FreeMatrix(SAT_ALGEBRA, vars.size) { r, c ->
//      BoolVar("G$r.$c")
//    }

      val constraint = nonemptyTriples.take(180).mapIndexed { i, (s1, s2, s3) ->
      val rows = maxOf(s1.size, s2.size)

      val (X, Y, designMatrix) =
        s1.encodeAsMatrix(this, vars, rows) to
        s2.encodeAsMatrix(this, vars, rows) to
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

      val dontCare = BoolVar("dc$i")
      val DC = FreeMatrix(SAT_ALGEBRA, rows) { _, _ -> dontCare }
      tx eq DC
    }.reduce { acc, formula -> acc and formula }

//    println("Solving:${nonemptyTriples.size}")

    val solution = solveBoolean(constraint)

    val G = FreeMatrix(odMat.data.map { solution[it]?.let { if(it) "1" else "0" } ?: "UNK" })

//    println("Design matrix: $G")

    nonemptyTriples.take(180).shuffled().forEachIndexed { i, (s1, s2, s3) ->
      val rows = maxOf(s1.size, s2.size)

      val (X, Y) =
        s1.encodeAsMatrix(this, vars, rows) to
        s2.encodeAsMatrix(this, vars, rows)

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
}