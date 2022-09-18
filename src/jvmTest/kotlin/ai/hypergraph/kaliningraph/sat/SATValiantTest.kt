package ai.hypergraph.kaliningraph.sat

import ai.hypergraph.kaliningraph.parsing.*
import ai.hypergraph.kaliningraph.types.*
import org.junit.jupiter.api.Test
import org.logicng.formulas.Formula
import kotlin.math.pow
import kotlin.system.measureTimeMillis
import kotlin.test.*

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.SATValiantTest"
*/
class SATValiantTest {
/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.SATValiantTest.testVecJoin"
*/
  @Test
  fun testVecJoin() {
    val cfg = "S -> ( S ) | ( ) | S S".parseCFG()

    val pwrsetSquared =
      cfg.nonterminals.take(5).powerset().let { it * it }
    println("Cardinality:" + pwrsetSquared.size)

    /*
     * Checks that bitvector join faithfully encodes set join, i.e.:
     *
     *      S   ⋈   S' = Z for all subsets S', S' in P(Variables)
     *      ⇵       ⇵    ⇵
     *      V   ☒   V' = Z'
     */
    pwrsetSquared.forEach { (a, b) ->
      with(cfg) {
        assertEquals(toBitVec(setJoin(a, b)), join(toBitVec(a), toBitVec(b)))
        assertEquals(setJoin(a, b), toNTSet(join(toBitVec(a), toBitVec(b))))
      }
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.SATValiantTest.testTLArithmetic"
*/
  @Test
  fun testTLArithmetic() {
    val cfg = """
      S1C -> 1
      S2C -> 2
      S3C -> 3
      S4C -> 4
      S -> S1 | S2 | S3 | S4 
      S -> S1 = S1C
      S -> S2 = S2C
      S -> S3 = S3C
      S -> S4 = S4C
      S1 -> S1C
      S2 -> S2C | S1 + S1
      S3 -> S3C | S2 + S1 | S1 + S2
      S4 -> S4C | S3 + S1 | S1 + S3 | S2 + S2
    """.parseCFG()

    println(cfg.prettyPrint())
    //println(cfg.parse("3 + 1 = 4"))
    //cfg.parseHTML("3 + 1 = 4").show()
    assertEquals("3 + 1 = 4", "3 + _ = 4".synthesizeIncrementally(cfg, allowNTs = false).first())
    assertEquals("3 + 1 = 4", "_ + 1 = 4".synthesizeIncrementally(cfg, allowNTs = false).first())
    assertEquals("3 + 1 = 4", "3 + 1 = _".synthesizeIncrementally(cfg, allowNTs = false).first())

    assertTrue("3 + 1 = 4".matches(cfg))
    assertTrue("2 + 2 = 4".matches(cfg))
    assertTrue("2 + 1 = 3".matches(cfg))
    assertTrue("1 + 1 + 1 = 3".matches(cfg))
    assertTrue("1 + 1 + 1 + 1 = 4".matches(cfg))
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
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.SATValiantTest.testArithmetic"
*/
  @Test
  fun testArithmetic() {
    """
      S -> S + S | S * S | S - S | S / S | ( S )
      S -> 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9
      S -> X | Y | Z
    """.let { cfg ->
      val cfg = cfg.parseCFG()
      assertTrue("( 1 + 2 * 3 ) / 4".matches(cfg))
      assertFalse("( 1 + 2 * 3 ) - ) / 4".matches(cfg))
      assertFalse("( 1 + 2 * 3 ) - ( ) / 4".matches(cfg))
      println(cfg.parse("( 1 + 2 ) - 1")?.prettyPrint())
//      cfg.parseHTML("( ( 1 + 2 ) * 3 ) / 4").show()
      println(cfg.prettyPrint())
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.SATValiantTest.testXujieExample"
*/
  @Test
  fun testXujieExample() {
    val cfg = xujieGrammar.trimIndent().parseCFG(normalize=false)

    (setOf("T2", "T1", "S") to setOf("T2", "S", "R1", "R2")).let { (a, b) ->
      with(cfg) {
        println(cfg.prettyPrint())
        println("Set A:$a")
        println("Set B:$b")
        println("Join A*B:" + setJoin(a, b))
        println("A:" + toBitVec(a))
        println("B:" + toBitVec(b))

        println("BV join:" + join(toBitVec(a), toBitVec(b)))
        println("Set join:" + toNTSet(join(toBitVec(a), toBitVec(b))))

        assertEquals(toBitVec(setJoin(a, b)), join(toBitVec(a), toBitVec(b)))
        assertEquals(setJoin(a, b), toNTSet(join(toBitVec(a), toBitVec(b))))
      }
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.SATValiantTest.testSingleStepMultiplication"
*/
  @Test
  fun testSingleStepMultiplication()  {
    xujieGrammar.trimIndent().parseCFG(normalize = false).let { cfg ->
      (setOf("T1", "S", "L2") to setOf("T2", "S", "R1", "R2")).let { (a, b) ->
        val trueJoin = cfg.setJoin(a, b)
        println("True join: $trueJoin")

        val litA: List<Formula> = cfg.toBitVec(a).toLitVec()
        val satB: List<Formula> = List(cfg.toBitVec(b).size) { i -> BVar("BV$i") }
        val litC: List<Formula> = cfg.toBitVec(cfg.setJoin(a, b)).toLitVec()

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
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.SATValiantTest.testXujieMethod"
*/
  @Test
  fun testXujieMethod()  {
    val cfg = "S -> [ S ] | [ ] | S S".parseCFG()

    println(cfg.prettyPrint())

    val decodedString = "_ _ _ _ "
      .also { println("$it is being synthesized...") }
      .synthesizeIncrementally(cfg, allowNTs = false).first()

    println("$decodedString generated by SATValiant!")

    val isValid = cfg.isValid(decodedString)
    println("$decodedString is ${if (isValid) "" else "not "}valid according to SetValiant!")

    assertTrue(isValid)
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.SATValiantTest.testXujieParsingWithFP"
*/
  @Test
  fun testXujieParsingWithFP()  {
    val cfg = """
      S -> B | [ S ] | ( S ) | S + S | S * S
      B -> X | Y | Z
    """.parseCFG()

    "[ X _ + _ _ _ ( _ ) _ [ _ _ _ ] _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ + _ _ _ "
      .also { println("$it is being synthesized...") }
      .synthesizeIncrementally(cfg, allowNTs = false).take(10).forEach { decodedString ->
        println("$decodedString generated by fixed point solving!")

        val isValid = cfg.isValid(decodedString)
        println("$decodedString is ${if (isValid) "" else "not "}valid according to SetValiant!")

        assertTrue(isValid)
      }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.SATValiantTest.testXujieExWithNT"
*/
  @Test
  fun testXujieExWithNT()  {
    val cfg = """
      S -> A + B | A * B | S + S | S * S
      A -> X | [ A ]
      A -> a a a
      Y -> b b
      B -> Y | ( B )
    """.parseCFG()

    println(cfg.prettyPrint())

    "[ a a a ] _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ "
      .also { println("$it is being synthesized...") }
      .synthesizeIncrementally(cfg).take(10).forEach { decodedString ->
        println("$decodedString generated by fixed point solving!")

        val isValid = cfg.isValid(decodedString)
        println("$decodedString is ${if (isValid) "" else "not "}valid according to SetValiant!")

        //assertTrue(isValid)
      }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.SATValiantTest.testOCamlHW"
*/
  @Test
  fun testOCamlHW()  {
    val cfg = """
      S -> Let Var = Exp In Exp
      Exp -> Exp Exp | ( Exp ) | ( Exp , Exp ) | S | Var
    """.parseCFG().also { println(it.prettyPrint()) }

    "Let Var = Var In Var ( _ , _ _ _ Var Var Var Var Var _ _ _ _ _ _ _ ) Var Var Var"
      .also { println("$it is being synthesized...") }
      .synthesizeIncrementally(cfg).take(10).forEach {
        println(it)
        //val isValid = cfg.isValid(it)
        //println("$it is${if (isValid) " " else " not "}valid according to SetValiant!")
      }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.SATValiantTest.testXujieParsingWithFP1"
*/
  @Test
  fun testXujieParsingWithFP1()  {
    val cfg = """
      S -> A | B | A + B | A * B | S + S | S * S | [ S ] | ( S )
      A -> X | Y
      B -> Y | Z
    """.parseCFG()

    val decodedString = "[ X _ + ( _ ) _ ( _ _ _ _"
      .also { println("$it is being synthesized...") }
      .synthesizeIncrementally(cfg, allowNTs = false).first()

    println("$decodedString generated by fixed point solving!")

    val isValid = cfg.isValid(decodedString)
    println("$decodedString is ${if (isValid) "" else "not "}valid according to SetValiant!")

    assertTrue(isValid)
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.SATValiantTest.testMultiSAT"
*/
  @Test
  fun testMultiSAT() {
    val cfg = """
      S -> A | B | A + B | A * B | S + S | S * S | [ S ] | ( S )
      A -> X | Y
      B -> X | Z
    """.parseCFG()

    val distinct = mutableSetOf<String>()
    "[ X _ + ( _ ) _ ( _ _ _ _ _ _ _ _ _ _ ".also { println("$it is being synthesized...") }
      .synthesizeIncrementally(cfg, allowNTs = false).take(100)
      .forEach { decodedString ->
//        assertTrue(decodedString !in distinct)
        distinct += decodedString
        println("$decodedString generated by fixed point solving!")

        val isValid = cfg.isValid(decodedString)
        println("$decodedString is ${if (isValid) "" else "not "}valid according to SetValiant!")

        assertTrue(isValid)
      }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.SATValiantTest.testMultiSATWithSubstitution"
*/
  @Test
  fun testMultiSATWithSubstitution() {
    val cfg = """
      S -> A | B | A + B | A * B | S + S | S * S | [ S ] | ( S )
      A -> X | Y
      B -> X | Z
    """.parseCFG()

    val distinct = mutableSetOf<String>()
    "_ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ ".also { println("$it is being synthesized...") }
      .synthesizeIncrementally(cfg, allowNTs = false).take(100)
      .forEach { decodedString ->
        val another = decodedString
          .tokenizeByWhitespace()
          .joinToString(" ") { if (Math.random() < 0.7) "_" else it }
          .synthesizeIncrementally(cfg, allowNTs = false)
          .firstOrNull() ?: return@forEach

        println("Decoded: $decodedString")
        println("Another: $another")
        assertTrue(cfg.isValid(another))
      }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.SATValiantTest.testMultiSATWithSubstitution"
*/
  @Test
  fun testMultiSATWithRandomSubstitution() {
    val cfg = """
      S -> S + S | S * S | S - S | S / S | ( S ) | S = S
      S -> 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9
      S -> X | Y | Z
    """.parseCFG()

    val distinct = mutableSetOf<String>()
    "_ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ ".also { println("$it is being synthesized...") }
      .synthesizeIncrementally(cfg, allowNTs = false).take(100)
      .forEach { decodedString ->
        val another = decodedString
          .multiTokenSubstitutionsAndInsertions()
          .take(10)
          .forEach { println(it) }

        println("Decoded: $decodedString")
        println("Another: $another")
      }
  }

  /*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.SATValiantTest.testAlgebraicLanguage"
*/
  @Test
  fun testAlgebraicLanguage() {
    val cfg = """
      S -> S + S | S * S | S - S | S / S | ( S )
      S -> 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9
      S -> X | Y | Z
    """.parseCFG()

    val decodedString = "X + _ _ Z _ _ * _ _ _ "
      .also { println("$it is being synthesized...") }
      .synthesizeIncrementally(cfg, allowNTs = false).first()

    println("$decodedString generated by SATValiant!")

    val isValid = cfg.isValid(decodedString)
    println("$decodedString is ${if (isValid) "" else "not "}valid according to SetValiant!")

    assertTrue(isValid)
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.SATValiantTest.testAssociativity"
*/
  @Test
  fun testAssociativity() {
    val cfg = """
      S -> S + S | S * S | S - S | S / S | ( S ) | S = S
      S -> 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9
      S -> X | Y | Z
    """.parseCFG().map { (l, r) ->
      l.replace(START_SYMBOL, "S", ) to
      r.map { it.replace(START_SYMBOL, "S") }
    }.toSet()

    val t = cfg.initialMatrix(tokenize("3 + 1 = 4"))
    assertNotEquals(t * (t * t), (t * t) * t)
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.SATValiantTest.testVariableLengthDecoding"
*/
  @Test
  fun testVariableLengthDecoding() {
    val cfg = """
      START -> A B C
      A -> a A | ε
      B -> b B | ε
      C -> ε
    """.parseCFG()

    println("Grammar:\n" + cfg.prettyPrint())
    "_ _ _ _ _ _ _ _ _ ".synthesizeIncrementally(cfg, allowNTs = false)
      .distinct().take(100).forEach { decodedString ->
        val isValid = cfg.isValid(decodedString)
        println("$decodedString is ${if (isValid) "" else "not "}valid according to SetValiant!")

        assertTrue(isValid)
      }

    val dyckPadded = "S -> [ S ] | [ ] | S S".parseCFG()

    println("Grammar:\n" + dyckPadded.prettyPrint())
    "_________".synthesizeIncrementally(dyckPadded, allowNTs = false)
      .distinct().take(100).forEach { decodedString ->
        val isValid = dyckPadded.isValid(decodedString)
        println("$decodedString is ${if (isValid) "" else "not "}valid according to SetValiant!")

        assertTrue(isValid)
      }

    val arithPadded= """
      S -> S + S | S * S | S - S | S / S | ( S )
      S -> 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9
      S -> X | Y | Z
    """.parseCFG()

    println("Grammar:\n" + arithPadded.prettyPrint())
    "_ _ _ _ _ _ _ _ _ ".synthesizeIncrementally(arithPadded, allowNTs = false)
      .distinct().take(100).forEach { decodedString ->
        val isValid = arithPadded.isValid(decodedString)
        println("$decodedString is ${if (isValid) "" else "not "}valid according to SetValiant!")

        assertTrue(isValid)
      }
  }

  val arith = """
    O -> + | * | - | /
    S -> S O S | ( S )
    S -> 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9
  """.parseCFG()

  private fun Tree.middle(): String? = children.drop(1).firstOrNull()?.terminal
  fun Tree.eval(): Int = when {
    middle() == "*" -> children.first().eval() * children.last().eval()
    middle() == "+" -> children.first().eval() + children.last().eval()
    terminal?.toIntOrNull() != null -> terminal!!.toInt()
    terminal in listOf("(", ")") -> -1
    else -> children.asSequence().map { it.eval() }.first { 0 <= it }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.SATValiantTest.testArithmeticEval"
*/
  @Test
  fun testArithmeticEval() {
    assertEquals(arith.parse("( 1 + 2 ) * ( 3 * 4 )")!!.eval(), 36)
  }

  val checkedArithCFG = """
    S -> S1 | S2 | S3 | S4 | S5 | S6 | S7
    S -> S1 = S1
    S -> S2 = S2
    S -> S3 = S3
    S -> S4 = S4
    S -> S5 = S5
    S -> S6 = S6
    S -> S7 = S7
    S -> S8 = S8
    S -> S9 = S9
    
    S1 -> P1
    S2 -> P2 | ( S2 ) | P1 + P1
    S3 -> P3 | ( S3 ) | P2 + P1 | P1 + P2
    S4 -> P4 | ( S4 ) | P3 + P1 | P1 + P3 | P2 + P2
    S5 -> P5 | ( S5 ) | P1 + P4 | P4 + P1 | P2 + P3 | P2 + P3
    S6 -> P6 | ( S6 ) | P1 + P5 | P5 + P1 | P3 + P3 | P2 + P4 | P4 + P2
    S7 -> P7 | ( S7 ) | P1 + P6 | P6 + P1 | P5 + P2 | P2 + P5 | P4 + P3 | P3 + P4
    S8 -> P8 | ( S8 ) | P1 + P7 | P7 + P1 | P6 + P2 | P2 + P6 | P3 + P5 | P5 + P3 | P4 + P4
    S9 -> P9 | ( S9 ) | P1 + P8 | P8 + P1 | P7 + P2 | P2 + P7 | P3 + P6 | P6 + P3 | P4 + P5 | P5 + P4
    
    P1 -> 1 | ( S1 ) | P1 * P1
    P2 -> 2 | ( S2 ) | P2 * P1 | P1 * P2
    P3 -> 3 | ( S3 ) | P3 * P1 | P1 * P3
    P4 -> 4 | ( S4 ) | P2 * P2 | P4 * P1 | P1 * P4
    P5 -> 5 | ( S5 ) | P5 * P1 | P1 * P5
    P6 -> 6 | ( S6 ) | P6 * P1 | P1 * P6 | P3 * P2 | P2 * P3
    P7 -> 7 | ( S7 ) | P7 * P1 | P1 * P7
    P8 -> 8 | ( S8 ) | P4 * P2 | P2 * P4 | P8 * P1 | P1 * P8
    P9 -> 9 | ( S9 ) | P9 * P1 | P1 * P9 | P3 * P3
  """.parseCFG()

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.SATValiantTest.testCheckedArithmetic"
*/
  @Test
  fun testCheckedArithmetic() {
    "( _ + _ ) * ( _ + _ ) = ( _ * _ ) + ( _ * _ )"
      .synthesizeIncrementally(checkedArithCFG, allowNTs = false)
      .map {
        println(it)
        val (left, right) = it.split("=")
        val (ltree, rtree) = arith.parse(left)!! to arith.parse(right)!!
        val (leval, reval) = ltree.eval() to rtree.eval()
        println("$leval = $reval")
        assertEquals(leval, reval)
        leval
      }.distinct().take(4).toList()
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.SATValiantTest.testDyckBenchmark"
*/
  @Test
  fun testDyckBenchmark() {
    val dyck1 = """S -> ( ) | ( S ) | S S""".parseCFG()
    val dyck2 = """S -> ( ) | [ ] | ( S ) | [ S ] | S S""".parseCFG()
    val dyck3 = """S -> ( ) | [ ] | { } | ( S ) | [ S ] | { S } | S S""".parseCFG()
    val dyck4 = """S -> ( ) | [ ] | { } | < > | < S > | ( S ) | [ S ] | { S } | S S""".parseCFG()

    fun List<Double>.stddev() = average().let { μ -> map { (it - μ).pow(2) } }.average().pow(0.5)

    fun String.dropHoles(i: Int = 4, idxs: Set<Int> = indices.shuffled().take(i).toSet()) =
      split(" ").mapIndexed { i, it -> if (i in idxs) "_" else it }.joinToString(" ")

    val numSamples = 20
    var data = "holes, d1, d1err, d2, d2err, d3, d3err, d4, d4err"
    for(holes in setOf(6, 8, 10, 12, 14, 16)) {
      data += "\n$holes"
      setOf(dyck1, dyck2, dyck3, dyck4).forEach { cfg ->
        val str =
          List(50) { "_" }.joinToString(" ").synthesizeIncrementally(cfg, allowNTs = false).take(30).toList()
        (0..numSamples).map {
          measureTimeMillis {
            str.random().dropHoles(holes).synthesizeIncrementally(cfg, allowNTs = false).take(1).toList()
          }.toDouble()
        }.let { data += ", " + it.average() + ", " + it.stddev() }
      }
    }

    println(data)
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.SATValiantTest.testDyckRepairBenchmark"
*/
  @Test
  fun testDyckRepairBenchmark() {
    val dyck1 = """S -> ( ) | ( S ) | S S""".parseCFG()
    val dyck2 = """S -> ( ) | [ ] | ( S ) | [ S ] | S S""".parseCFG()
    val dyck3 = """S -> ( ) | [ ] | { } | ( S ) | [ S ] | { S } | S S""".parseCFG()
    val dyck4 = """S -> ( ) | [ ] | { } | < > | < S > | ( S ) | [ S ] | { S } | S S""".parseCFG()

    fun List<Double>.stddev(): Double =
      average().let { μ -> map { (it - μ).pow(2) } }.average().pow(0.5)

    fun String.makeError(i: Int = 4, idxs: Set<Int> = indices.shuffled().take(i).toSet()): String =
      split(" ").mapIndexed { i, it -> if (i in idxs) "" else it }.joinToString(" ")

    val numSamples = 10
    var data = "errors, d1, d1err, d2, d2err, d3, d3err, d4, d4err"
    for (errors in setOf(1, 2, 3)) {
      data += "\n$errors"
      setOf(dyck1, dyck2, dyck3, dyck4).forEach { cfg ->
        val str =
          List(50) { "_" }.joinToString(" ").synthesizeIncrementally(cfg, allowNTs = false).take(30).toList()
        (0..numSamples).map {
          measureTimeMillis {
            val bad = str.random().makeError(errors)
            val (_, stubs) = cfg.parseWithStubs(bad)
            val exclude = stubs.allIndicesInsideParseableRegions()

            bad.synthesizeIncrementally(
              cfg = cfg,
              variations = listOf {
                bad.multiTokenSubstitutionsAndInsertions(
                  numberOfEdits = 3,
                  exclusions = exclude,
                )
              }
            ).also { println(it.take(1).toList()) }
          }.toDouble()
        }.let { data += ", " + it.average() + ", " + it.stddev(); println(data) }
      }
    }

    println(data)
  }

///*
//./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.SATValiantTest.testParallelStream"
//*/
//  @Test
//  fun testParallelStream() {
//    (0..1).toList().parallelStream().map { testCheckedArithmetic(); "Done" }
//      .forEach { println(it) }
//  }
}