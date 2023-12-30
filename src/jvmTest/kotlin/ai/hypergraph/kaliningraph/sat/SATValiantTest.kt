package ai.hypergraph.kaliningraph.sat

import Grammars.arith
import Grammars.evalArith
import ai.hypergraph.kaliningraph.*
import ai.hypergraph.kaliningraph.image.toHtmlPage
import ai.hypergraph.kaliningraph.parsing.*
import ai.hypergraph.kaliningraph.repair.*
import ai.hypergraph.kaliningraph.types.*
import ai.hypergraph.kaliningraph.visualization.show
import org.junit.jupiter.api.Test
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
      cfg.nonterminals.take(5).powerset().let { it * it }.toList()
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
        assertContentEquals(toBitVec(setJoin(a, b)), join(toBitVec(a), toBitVec(b)))
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
    """.parseCFG().noNonterminalStubs

    println(cfg.prettyPrint())
    println(cfg.parse("3 + 1 = 4")?.prettyPrint())
    println("_ _ _ = _".synthesizeIncrementally(cfg).first())
    println("_ _ _ _ _ _ _ _ + 1 _ _ _ _ _ _ _ _".synthesizeIncrementally(cfg).first())
    //cfg.parseHTML("3 + 1 = 4").show()
    assertEquals("3 + 1 = 4", "3 + _ = 4".synthesizeIncrementally(cfg, allowNTs = false).first().also { println("Got $it")})
    assertEquals("3 + 1 = 4", "_ + 1 = 4".synthesizeIncrementally(cfg, allowNTs = false).first().also { println("Got $it")})
    assertEquals("3 + 1 = 4", "3 + 1 = _".synthesizeIncrementally(cfg, allowNTs = false).first().also { println("Got $it")})

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
        println("A:" + toBitVec(a).toList())
        println("B:" + toBitVec(b).toList())

        println("BV join:" + join(toBitVec(a), toBitVec(b)).toList())
        println("Set join:" + toNTSet(join(toBitVec(a), toBitVec(b))))

        assertContentEquals(toBitVec(setJoin(a, b)), join(toBitVec(a), toBitVec(b)))
        assertEquals(setJoin(a, b), toNTSet(join(toBitVec(a), toBitVec(b))))
      }
    }
  }

  /*
  ./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.SATValiantTest.testSingleStepMultiplication"
  */
  @Test
  fun testSingleStepMultiplication() {
    xujieGrammar.trimIndent().parseCFG(normalize = false).let { cfg ->
      (setOf("T1", "S", "L2") to setOf("T2", "S", "R1", "R2")).let { (a, b) ->
        val trueJoin = cfg.setJoin(a, b)
        println("True join: $trueJoin")

        val litA: SATVector = cfg.toBitVec(a).toLitVec()
        val satB: SATVector = BVecVar(cfg.toBitVec(b).size) { i -> "BV$i" }
        val litC: SATVector = cfg.toBitVec(cfg.setJoin(a, b)).toLitVec()

        println("\nSolving for B:")
        val solution = (cfg.join(litA, satB) vecEq litC).solve()
        println(solution)
        println("B=" + satB.map { solution[it] ?: false }.toBooleanArray().decodeWith(cfg))

        val satA: SATVector = BVecVar(cfg.toBitVec(b).size) { i -> "AV$i" }
        val litB: SATVector = cfg.toBitVec(b).toLitVec()

        println("\nSolving for A:")
        val solution1 = (cfg.join(satA, litB) vecEq litC).solve()
        println(solution1)
        println("A=" + satA.map { solution1[it] ?: false }.toBooleanArray().decodeWith(cfg))

        val satC: SATVector = BVecVar(cfg.toBitVec(b).size) { i -> "CV$i" }

        println("\nSolving for C:")
        val solution2 = (cfg.join(litA, litB) vecEq satC).solve()
        println(solution2)
        val bitVecJoin = satC.map { solution2[it]!! }.toBooleanArray().decodeWith(cfg)
        println("C=$bitVecJoin")

        assertEquals(trueJoin, bitVecJoin)
      }
    }
  }

  /*
  ./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.SATValiantTest.testBalancedBrackets"
  */
  @Test
  fun testBalancedBrackets()  {
    val cfg = "S -> [ S ] | [ ] | S S".parseCFG().noNonterminalStubs

    println(cfg.prettyPrint())

    "_ _ _ _ _ _".also { println("$it is being synthesized...") }
      .split(' ').genCandidates(cfg)
      .filter { (it.matches(cfg) to it.hasBalancedBrackets())
        .also { (valiant, stack) ->
          // Should never see either of these statements if we did our job correctly
          if (!valiant && stack) println("Valiant under-approximated Stack: $it")
          else if (valiant && !stack) println("Valiant over-approximated Stack: $it")
          assertFalse(!valiant && stack || valiant && !stack)
        }.first
      }.take(10).toList()
      .also { assert(it.isNotEmpty()) }
      .forEach { decodedString ->
        println("$decodedString generated by SATValiant!")

        val isValid = decodedString.matches(cfg)
        println("$decodedString is ${if (isValid) "" else "not "}valid according to SetValiant!")

        assertTrue(isValid)
      }
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

    "[ X _ + _ _ _ ( _ ) _ [ _ _ _ ] _ _ _ _ _ _ _ _ + _ _ _ "
      .also { println("$it is being synthesized...") }
      .synthesizeIncrementally(cfg, allowNTs = false).take(10)
      .toList().also { assert(it.isNotEmpty()) }
      .forEach { decodedString ->
        println("$decodedString generated by fixed point solving!")

        val isValid = decodedString.matches(cfg)
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
      .synthesizeIncrementally(cfg).take(10)
      .toList().also { assert(it.isNotEmpty()) }
      .forEach { decodedString ->
        println("$decodedString generated by fixed point solving!")

        val isValid = decodedString.matches(cfg)
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
      .synthesizeIncrementally(cfg).take(10)
      .toList().also { assert(it.isNotEmpty()) }
      .forEach {
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

    val isValid = decodedString.matches(cfg)
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
      .synthesizeIncrementally(cfg, allowNTs = false)
      .take(100).toList().also { assert(it.isNotEmpty()) }
      .forEach { decodedString ->
//        assertTrue(decodedString !in distinct)
        distinct += decodedString
        println("$decodedString generated by fixed point solving!")

        val isValid = decodedString.matches(cfg)
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
      .synthesizeIncrementally(cfg, allowNTs = false)
      .take(100).toList().also { assert(it.isNotEmpty()) }
      .forEach { decodedString ->
        val another = decodedString.tokenizeByWhitespace()
          .joinToString(" ") { if (Math.random() < 0.7) "_" else it }
          .synthesizeIncrementally(cfg, allowNTs = false)
          .firstOrNull() ?: return@forEach

        println("Decoded: $decodedString")
        println("Another: $another")
        assertTrue(another.matches(cfg))
      }
  }

  /*
  ./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.SATValiantTest.testMultiSATWithRandomSubstitution"
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
      .synthesizeIncrementally(cfg, allowNTs = false)
      .take(100).toList().also { assert(it.isNotEmpty()) }
      .forEach { decodedString ->
        println("Decoded: $decodedString")
        decodedString.multiTokenSubstitutionsAndInsertions()
          .take(10).forEach { println("Another: $it") }
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

    val isValid = decodedString.matches(cfg)
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

    val t = cfg.initialMatrix("3 + 1 = 4".tokenizeByWhitespace())
    assertNotEquals(t * (t * t), (t * t) * t)
  }

  /*
  ./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.SATValiantTest.whyIsItEmpty"
  */
  @Test
  fun whyIsItEmpty() {
    val cfg = """
        START -> P
        P -> A | ( P ) | ( ) | P P | W
        W -> w | public static
      """.parseCFG().also { println(it.prettyPrint()) }

//    println(cfg.parse("Z <= X + Y")?.prettyPrint())

//    assertNotNull(cfg.parse("Z <= Y"))
    "public static w ( ) _ _ _ _ _ _"
      .synthesizeIncrementally(cfg).take(10).forEach { println(it) }

    "_ _ _ _ _ _ _ _ static public _ _ _ _ _ _ _ _"
      .synthesizeIncrementally(cfg).take(10).forEach { println(it) }

    println("Synthesizing a counterexample:")
    "_ _ _ _ _ _ _ _ ( ) _ _ _ _ _ _ _ _"
      .synthesizeIncrementally(cfg, synthesizer = { it.solve(cfg) }).take(10)
      .forEach { println(it)

        cfg.parseWithStubs(it).second.onEach { println(it.prettyPrint()) }
        assertNotNull(cfg.parse(it), "Got: $it") }
  }

  /*
  ./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.SATValiantTest.testIfThenLang"
  */
  @Test
  fun testIfThenLang() {
    val cfg = """
      START -> X
      X -> I | F | P
      P -> I O I
      F -> IF | BF
      IF -> if B then I else I
      BF -> if B then B else B
      O -> + | - | * | /
      I -> 1 | 2 | 3 | 4 | IF
      B -> true | false | B BO B | ( B ) | BF | N B
      BO -> and | or
      N -> !
    """.parseCFG()

    "if _ _ _ _ _ _ <BO> _ _ _ _ _ _".synthesizeIncrementally(cfg)
      .take(100).toList().also { assert(it.isNotEmpty()) }
      .forEach { assertNotNull(cfg.parse(it.also { println(it) }), "Unparseable: $it") }
  }

  /*
  ./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.SATValiantTest.testVariableLengthDecoding"
  */
  @Test
  fun testVariableLengthDecoding() {
    val cfg = """
      START -> A B
      A -> a A | a
      B -> b B | b
    """.parseCFG()

    println("Grammar:\n" + cfg.prettyPrint())
    "_ _ _ _ _ _ _ _ _".synthesizeIncrementally(cfg, allowNTs = false)
      .distinct().take(100).toList().also { assert(it.isNotEmpty()) }
      .forEach { decodedString ->
        val isValid = decodedString.matches(cfg)
        println("$decodedString is ${if (isValid) "" else "not "}valid according to SetValiant!")

        assertTrue(isValid)
      }

    val dyckPadded = "S -> [ S ] | [ ] | S S".parseCFG()

    println("Grammar:\n" + dyckPadded.prettyPrint())
    "_ _ _ _ _ _ _ _ _ _".synthesizeIncrementally(dyckPadded, allowNTs = false)
      .distinct().take(100).toList().also { assert(it.isNotEmpty()) }
      .forEach { decodedString ->
        val isValid = decodedString.matches(dyckPadded)
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
      .distinct().take(100).toList().also { assert(it.isNotEmpty()) }
      .forEach { decodedString ->
        val isValid = decodedString.matches(arithPadded)
        println("$decodedString is ${if (isValid) "" else "not "}valid according to SetValiant!")

        assertTrue(isValid)
      }
  }

  /*
  ./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.SATValiantTest.testCheckedArithmetic"
  */
  @Test
  fun testCheckedArithmetic() {
    "( _ + _ ) * ( _ + _ ) = ( _ * _ ) + ( _ * _ )"
      .synthesizeIncrementally(Grammars.checkedArithCFG, allowNTs = false)
      .take(10).toList().also { assert(it.isNotEmpty()) }
      .map {
        println(it)
        val (left, right) = it.split('=')
        val (ltree, rtree) = arith.parse(left)!! to arith.parse(right)!!
        val (leval, reval) = ltree.evalArith() to rtree.evalArith()
        println("$leval = $reval")
        assertEquals(leval, reval)
        leval
      }.distinct().take(4).toList()
  }

  /*
  ./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.SATValiantTest.testImpossibleSubstring"
  */
  @Test
  fun testImpossibleSubstring() {
    // How quickly can we decide whether a string s is a substring of no string in CFL ∩ Σⁿ?
    val cfg = """E -> E + E | E * E | ( E ) | x""".parseCFG()

    assertNull("_ _ _ _ x ) +".synthesizeIncrementally(cfg).firstOrNull())
    val holes = List(10) { "_" }.joinToString(" ")
    assertNull("$holes ( ) $holes".synthesizeIncrementally(cfg).firstOrNull())

    val cfg1 = "S -> w | ( ) | [ ] | { } | ( S ) | [ S ] | { S } | S S".parseCFG().noNonterminalStubs
    println(cfg1.prettyPrint())
    assertNotNull(cfg1.synthesize("_ _ _ _ _ _ _ _ w ( _ _ _ _ _ _ _ _".tokenizeByWhitespace()).firstOrNull().also { println(it) })
  }

  /*
  ./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.SATValiantTest.testPrunedQuery"
  */
  @Test
  fun testPrunedQuery() {
    val cfg = """S -> w | ( ) | [ ] | < > | { } | ( S ) | [ S ] | < S > | { S } | S S""".parseCFG()
    val longQuery = "w ( w ) w ( w ( w ( w ) w ( w ) w ( w ) ) ) w ( w ( w )" +
    " w ( w ) w ( w ) w ( w ) w ( w ( w ) ) w ( ( w ( w ( w ) ) w ) w )" +
    " w ( w ( ) w ( w ) ) w ( w ( w ) _ w ( w ) w ( ) ) w ( ) w"
    val shortQuery = "w ( w ) w ( w ( w ( w ) w ( w ) w ( w ) ) ) w ( <START> ( w ( w ) _ w ( w ) w ( ) ) w ( ) w"
    println(longQuery.synthesizeIncrementally(cfg, enablePruning = true).first())
  }

  /*
  ./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.SATValiantTest.testCFLPumping"
  */
  @Test
  fun testCFLPumping() {
    val cfgA = """
        START -> L R
        L -> a b | a L b
        R -> c | c R
      """.parseCFG().noNonterminalStubs

    val cfgB = """
        START -> L R
        R -> b c | b R c
        L -> a | a L
      """.parseCFG().noNonterminalStubs

    // This language should recognize {aⁿbⁿcⁿ | n > 0}
    val csl = (cfgA intersect cfgB)
//    println("CSL:\n" + csl.prettyPrint())
    csl.synthesize(List(20) { "_" })
      .map { it.replace("ε", "").tokenizeByWhitespace().joinToString(" ") }.distinct()
     .map {
        println(it)
        val (a, b, c) = it.count { it == 'a' } to it.count { it == 'b' } to it.count { it == 'c' }
        assertEquals(a, b)
        assertEquals(b, c)
        it
      }.take(5).toList().also { assert(it.isNotEmpty()) }
  }

  val sumCFG = """
      START -> S
      O -> +
      S -> S O S | N | - N | ( S )
      N -> N1 | N2 | N3 | N4 | N5 | N6 | N7 | N8 | N9
      N1 -> 1 
      N2 -> 2 
      N3 -> 3
      N4 -> 4
      N5 -> 5
      N6 -> 6
      N7 -> 7
      N8 -> 8
      N9 -> 9
    """.trimIndent().parseCFG()

  /*
  ./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.SATValiantTest.testLevensheteinIntersection"
  */
  @Test
  fun testLevensheteinIntersection() {
    val cfg = sumCFG.noNonterminalStubs
    val strWithParseErr = "1 + 2 + + +".tokenizeByWhitespace()
    val dist = 2
    MAX_REPAIR = 3
    val levCFG = constructLevenshteinCFG(strWithParseErr, dist, cfg.terminals + "ε").parseCFG().noNonterminalStubs

    val template = "_ _ _ _ _ _ _"
    val allL5 = template.synthesizeIncrementally(levCFG).toSet()//.also { println("L5: $it") }
    val allA5 = template.synthesizeIncrementally(cfg).toSet()//.also { println("A5: $it") }

    val setIntersect = (allA5 intersect allL5).also { println("A5 ∩ L5: $it") }
    assertNotEquals(setIntersect, emptySet())

    val cflIntersect = cfg.levenshteinRepair(dist, strWithParseErr, solver = { synthesize(it) }).toSet()
    assertNotEquals(cflIntersect, emptySet())
    /**TODO: If this fails, [CJL.alignNonterminals] is probably the culprit (also maybe [MAX_REPAIR] or [TIMEOUT_MS]) */
    assertEquals(setIntersect, cflIntersect)
  }

  /*
// Original constraint system:
    Synthesizing (7): _ _ _ _ _ _ _
    Solver formed 282832 constraints in 854ms
    Synthesizing (7): _ _ _ _ _ _ _
    Solver formed 80766 constraints in 121ms
    A5 ∩ L5: [1 + 2 + 3, 1 + 2 + 4, 1 + 2 + 6, 1 + 2 + 5, 1 + 2 + 8, 1 + 2 + 9, 1 + 2 + 2, 1 + 2 + 1, 1 + 2 + 7, 1 + 2 + - 3, 1 + 2 + - 7, 1 + 2 + - 8, 1 + 2 + - 5, 1 + 2 + - 6, 1 + 2 + - 4, 1 + 2 + - 2, 1 + 2 + - 9, 1 + 2 + - 1, 1 + 2 + 7 + 8, 1 + 2 + 4 + 8, 1 + 2 + 8 + 8, 1 + 2 + 6 + 8, 1 + 2 + 5 + 8, 1 + 2 + 1 + 8, 1 + 2 + 3 + 8, 1 + 2 + 9 + 8, 1 + 2 + 2 + 8, 1 + 2 + 4 + 6, 1 + 2 + 4 + 4, 1 + 2 + 4 + 3, 1 + 2 + 4 + 5, 1 + 2 + 4 + 1, 1 + 2 + 4 + 2, 1 + 2 + 4 + 9, 1 + 2 + 4 + 7, 1 + 2 + 5 + 1, 1 + 2 + 5 + 6, 1 + 2 + 5 + 5, 1 + 2 + 5 + 7, 1 + 2 + 5 + 2, 1 + 2 + 5 + 9, 1 + 2 + 5 + 3, 1 + 2 + 5 + 4, 1 + 2 + 3 + 9, 1 + 2 + 7 + 9, 1 + 2 + 8 + 9, 1 + 2 + 6 + 9, 1 + 2 + 9 + 9, 1 + 2 + 1 + 9, 1 + 2 + 2 + 9, 1 + 2 + 2 + 3, 1 + 2 + 2 + 4, 1 + 2 + 2 + 1, 1 + 2 + 2 + 2, 1 + 2 + 2 + 6, 1 + 2 + 2 + 7, 1 + 2 + 2 + 5, 1 + 2 + 3 + 7, 1 + 2 + 7 + 7, 1 + 2 + 8 + 7, 1 + 2 + 6 + 7, 1 + 2 + 9 + 7, 1 + 2 + 1 + 7, 1 + 2 + 9 + 5, 1 + 2 + 9 + 6, 1 + 2 + 9 + 3, 1 + 2 + 9 + 4, 1 + 2 + 9 + 2, 1 + 2 + 9 + 1, 1 + 2 + 1 + 3, 1 + 2 + 1 + 4, 1 + 2 + 1 + 2, 1 + 2 + 1 + 1, 1 + 2 + 1 + 5, 1 + 2 + 1 + 6, 1 + 2 + 3 + 3, 1 + 2 + 3 + 4, 1 + 2 + 3 + 2, 1 + 2 + 3 + 1, 1 + 2 + 6 + 3, 1 + 2 + 6 + 4, 1 + 2 + 6 + 2, 1 + 2 + 6 + 1, 1 + 2 + 7 + 3, 1 + 2 + 8 + 3, 1 + 2 + 7 + 4, 1 + 2 + 8 + 4, 1 + 2 + 7 + 2, 1 + 2 + 7 + 1, 1 + 2 + 8 + 2, 1 + 2 + 8 + 1, 1 + 2 + 8 + 5, 1 + 2 + 8 + 6, 1 + 2 + 7 + 5, 1 + 2 + 7 + 6, 1 + 2 + 3 + 5, 1 + 2 + 6 + 5, 1 + 2 + 3 + 6, 1 + 2 + 6 + 6]
    Synthesizing (8): _ _ _ _ _ _ _ _
    Solver formed 541139 constraints in 1689ms

// With start symbols fixpoint constraint optimization:
    Synthesizing (7): _ _ _ _ _ _ _
    Solver formed 62667 constraints in 228ms
    Synthesizing (7): _ _ _ _ _ _ _
    Solver formed 18100 constraints in 22ms
    A5 ∩ L5: [1 + 2 + 7, 1 + 2 + 8, 1 + 2 + 6, 1 + 2 + 1, 1 + 2 + 5, 1 + 2 + 2, 1 + 2 + 9, 1 + 2 + 4, 1 + 2 + 3, 1 + 2 + - 3, 1 + 2 + - 7, 1 + 2 + - 5, 1 + 2 + - 4, 1 + 2 + - 8, 1 + 2 + - 2, 1 + 2 + - 1, 1 + 2 + - 9, 1 + 2 + - 6, 1 + 2 + 7 + 6, 1 + 2 + 8 + 6, 1 + 2 + 5 + 6, 1 + 2 + 6 + 6, 1 + 2 + 4 + 6, 1 + 2 + 4 + 2, 1 + 2 + 4 + 1, 1 + 2 + 4 + 9, 1 + 2 + 4 + 3, 1 + 2 + 4 + 7, 1 + 2 + 4 + 5, 1 + 2 + 4 + 8, 1 + 2 + 4 + 4, 1 + 2 + 6 + 9, 1 + 2 + 8 + 9, 1 + 2 + 8 + 2, 1 + 2 + 6 + 2, 1 + 2 + 6 + 1, 1 + 2 + 8 + 1, 1 + 2 + 5 + 1, 1 + 2 + 3 + 1, 1 + 2 + 1 + 1, 1 + 2 + 2 + 1, 1 + 2 + 9 + 1, 1 + 2 + 7 + 1, 1 + 2 + 3 + 6, 1 + 2 + 6 + 7, 1 + 2 + 1 + 7, 1 + 2 + 2 + 7, 1 + 2 + 9 + 7, 1 + 2 + 7 + 7, 1 + 2 + 8 + 7, 1 + 2 + 5 + 7, 1 + 2 + 3 + 7, 1 + 2 + 5 + 5, 1 + 2 + 5 + 8, 1 + 2 + 5 + 4, 1 + 2 + 5 + 3, 1 + 2 + 5 + 2, 1 + 2 + 5 + 9, 1 + 2 + 3 + 5, 1 + 2 + 3 + 8, 1 + 2 + 3 + 4, 1 + 2 + 3 + 3, 1 + 2 + 3 + 2, 1 + 2 + 3 + 9, 1 + 2 + 8 + 5, 1 + 2 + 8 + 8, 1 + 2 + 8 + 4, 1 + 2 + 6 + 5, 1 + 2 + 6 + 8, 1 + 2 + 6 + 4, 1 + 2 + 6 + 3, 1 + 2 + 8 + 3, 1 + 2 + 7 + 3, 1 + 2 + 7 + 2, 1 + 2 + 7 + 9, 1 + 2 + 7 + 4, 1 + 2 + 7 + 5, 1 + 2 + 7 + 8, 1 + 2 + 2 + 8, 1 + 2 + 1 + 8, 1 + 2 + 9 + 8, 1 + 2 + 2 + 5, 1 + 2 + 2 + 6, 1 + 2 + 2 + 4, 1 + 2 + 2 + 2, 1 + 2 + 2 + 9, 1 + 2 + 2 + 3, 1 + 2 + 1 + 5, 1 + 2 + 1 + 6, 1 + 2 + 1 + 4, 1 + 2 + 1 + 2, 1 + 2 + 1 + 9, 1 + 2 + 1 + 3, 1 + 2 + 9 + 5, 1 + 2 + 9 + 6, 1 + 2 + 9 + 4, 1 + 2 + 9 + 2, 1 + 2 + 9 + 9, 1 + 2 + 9 + 3]
    Synthesizing (8): _ _ _ _ _ _ _ _
    Solver formed 118663 constraints in 272ms

// With n-reachability fixpoint constraint optimization:

    Synthesizing (7): _ _ _ _ _ _ _
    Solver formed 52508 constraints in 245ms
    Synthesizing (7): _ _ _ _ _ _ _
    Solver formed 16829 constraints in 30ms
    A5 ∩ L5: [1 + 2 + 5, 1 + 2 + 4, 1 + 2 + 8, 1 + 2 + 9, 1 + 2 + 3, 1 + 2 + 1, 1 + 2 + 6, 1 + 2 + 7, 1 + 2 + 2, 1 + 2 + - 8, 1 + 2 + - 4, 1 + 2 + - 9, 1 + 2 + - 2, 1 + 2 + - 1, 1 + 2 + - 3, 1 + 2 + - 5, 1 + 2 + - 7, 1 + 2 + - 6, 1 + 2 + 9 + 5, 1 + 2 + 3 + 5, 1 + 2 + 1 + 5, 1 + 2 + 8 + 5, 1 + 2 + 7 + 5, 1 + 2 + 4 + 5, 1 + 2 + 5 + 5, 1 + 2 + 6 + 5, 1 + 2 + 2 + 5, 1 + 2 + 5 + 3, 1 + 2 + 1 + 3, 1 + 2 + 9 + 3, 1 + 2 + 2 + 3, 1 + 2 + 3 + 3, 1 + 2 + 4 + 3, 1 + 2 + 8 + 3, 1 + 2 + 6 + 3, 1 + 2 + 7 + 3, 1 + 2 + 1 + 2, 1 + 2 + 9 + 2, 1 + 2 + 2 + 2, 1 + 2 + 6 + 2, 1 + 2 + 5 + 2, 1 + 2 + 8 + 2, 1 + 2 + 7 + 2, 1 + 2 + 4 + 2, 1 + 2 + 3 + 2, 1 + 2 + 4 + 1, 1 + 2 + 3 + 1, 1 + 2 + 2 + 1, 1 + 2 + 9 + 1, 1 + 2 + 1 + 1, 1 + 2 + 5 + 1, 1 + 2 + 6 + 1, 1 + 2 + 8 + 1, 1 + 2 + 7 + 1, 1 + 2 + 9 + 6, 1 + 2 + 3 + 6, 1 + 2 + 8 + 6, 1 + 2 + 7 + 6, 1 + 2 + 4 + 6, 1 + 2 + 5 + 6, 1 + 2 + 6 + 6, 1 + 2 + 2 + 6, 1 + 2 + 1 + 6, 1 + 2 + 2 + 7, 1 + 2 + 9 + 7, 1 + 2 + 1 + 7, 1 + 2 + 8 + 7, 1 + 2 + 6 + 7, 1 + 2 + 5 + 7, 1 + 2 + 4 + 7, 1 + 2 + 3 + 7, 1 + 2 + 7 + 7, 1 + 2 + 4 + 9, 1 + 2 + 4 + 4, 1 + 2 + 4 + 8, 1 + 2 + 6 + 4, 1 + 2 + 7 + 4, 1 + 2 + 8 + 4, 1 + 2 + 5 + 4, 1 + 2 + 3 + 4, 1 + 2 + 2 + 4, 1 + 2 + 1 + 4, 1 + 2 + 9 + 4, 1 + 2 + 9 + 8, 1 + 2 + 2 + 8, 1 + 2 + 1 + 8, 1 + 2 + 3 + 8, 1 + 2 + 5 + 8, 1 + 2 + 6 + 8, 1 + 2 + 7 + 8, 1 + 2 + 8 + 8, 1 + 2 + 2 + 9, 1 + 2 + 1 + 9, 1 + 2 + 9 + 9, 1 + 2 + 6 + 9, 1 + 2 + 7 + 9, 1 + 2 + 8 + 9, 1 + 2 + 5 + 9, 1 + 2 + 3 + 9]
    Synthesizing (8): _ _ _ _ _ _ _ _
    Solver formed 105229 constraints in 316ms

// With downwards and upwards n-reachability fixpoint constraints:
    Synthesizing (7): _ _ _ _ _ _ _
    Solver formed 48082 constraints in 239ms
    Synthesizing (7): _ _ _ _ _ _ _
    Solver formed 16795 constraints in 29ms
    A5 ∩ L5: [1 + 2 + 7, 1 + 2 + 8, 1 + 2 + 6, 1 + 2 + 3, 1 + 2 + 9, 1 + 2 + 4, 1 + 2 + 5, 1 + 2 + 1, 1 + 2 + 2, 1 + 2 + - 9, 1 + 2 + - 5, 1 + 2 + - 7, 1 + 2 + - 6, 1 + 2 + - 8, 1 + 2 + - 4, 1 + 2 + - 3, 1 + 2 + - 1, 1 + 2 + - 2, 1 + 2 + 6 + 2, 1 + 2 + 9 + 2, 1 + 2 + 2 + 2, 1 + 2 + 1 + 2, 1 + 2 + 5 + 2, 1 + 2 + 4 + 2, 1 + 2 + 7 + 2, 1 + 2 + 8 + 2, 1 + 2 + 3 + 2, 1 + 2 + 9 + 1, 1 + 2 + 2 + 1, 1 + 2 + 8 + 1, 1 + 2 + 6 + 1, 1 + 2 + 7 + 1, 1 + 2 + 1 + 1, 1 + 2 + 3 + 1, 1 + 2 + 5 + 1, 1 + 2 + 4 + 1, 1 + 2 + 6 + 5, 1 + 2 + 7 + 5, 1 + 2 + 8 + 5, 1 + 2 + 5 + 5, 1 + 2 + 4 + 5, 1 + 2 + 3 + 5, 1 + 2 + 9 + 5, 1 + 2 + 2 + 5, 1 + 2 + 1 + 5, 1 + 2 + 7 + 3, 1 + 2 + 6 + 3, 1 + 2 + 8 + 3, 1 + 2 + 3 + 3, 1 + 2 + 9 + 3, 1 + 2 + 1 + 3, 1 + 2 + 2 + 3, 1 + 2 + 5 + 3, 1 + 2 + 4 + 3, 1 + 2 + 9 + 9, 1 + 2 + 9 + 8, 1 + 2 + 9 + 7, 1 + 2 + 9 + 4, 1 + 2 + 9 + 6, 1 + 2 + 4 + 9, 1 + 2 + 6 + 9, 1 + 2 + 7 + 9, 1 + 2 + 2 + 9, 1 + 2 + 5 + 9, 1 + 2 + 1 + 9, 1 + 2 + 3 + 9, 1 + 2 + 8 + 9, 1 + 2 + 4 + 7, 1 + 2 + 3 + 7, 1 + 2 + 5 + 7, 1 + 2 + 7 + 7, 1 + 2 + 8 + 7, 1 + 2 + 6 + 7, 1 + 2 + 2 + 7, 1 + 2 + 1 + 7, 1 + 2 + 4 + 4, 1 + 2 + 3 + 4, 1 + 2 + 2 + 4, 1 + 2 + 1 + 4, 1 + 2 + 7 + 4, 1 + 2 + 6 + 4, 1 + 2 + 8 + 4, 1 + 2 + 5 + 4, 1 + 2 + 8 + 8, 1 + 2 + 5 + 8, 1 + 2 + 3 + 8, 1 + 2 + 1 + 8, 1 + 2 + 4 + 8, 1 + 2 + 2 + 8, 1 + 2 + 7 + 8, 1 + 2 + 6 + 8, 1 + 2 + 7 + 6, 1 + 2 + 6 + 6, 1 + 2 + 8 + 6, 1 + 2 + 5 + 6, 1 + 2 + 3 + 6, 1 + 2 + 1 + 6, 1 + 2 + 4 + 6, 1 + 2 + 2 + 6]
    Synthesizing (8): _ _ _ _ _ _ _ _
    Solver formed 99842 constraints in 266ms
   */

  /*
  ./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.SATValiantTest.testLevensheteinCompleteness"
  */
  @Test
  fun testLevensheteinCompleteness() {
    val cfg = sumCFG.noNonterminalStubs
    val strWithParseErr = "1 + 2 + 3 + + 4 + 5 + 6 + 7"
//                          "1 + 2 + 3 + 2 + 5 + 5"
    val tokens = strWithParseErr.tokenizeByWhitespace()

    val sampleSize = 50
    var time = System.currentTimeMillis()
    val levenshteinRadius = 2
    val levRepairs = cfg.levenshteinRepair(levenshteinRadius, tokens, solver = { synthesize(it) })
      .mapIndexed { i, it -> println("$i, ${System.currentTimeMillis() - time}, $it"); it  }
      .take(sampleSize).toSet()

    println("Lev repairs (total time = ${System.currentTimeMillis() - time}ms): $levRepairs")

    time = System.currentTimeMillis()
    val scnRepairs= repairLazily(strWithParseErr, cfg, synthesizer = { synthesize(it) }, edits = levenshteinRadius)
      .filter { levenshtein(it.tokenizeByWhitespace(), strWithParseErr.tokenizeByWhitespace()) <= levenshteinRadius }
      .distinct().mapIndexed { i, it -> println("$i, ${System.currentTimeMillis() - time}, $it"); it  }
      .take(sampleSize).toSet()

    println("Scn repairs (total time = ${System.currentTimeMillis() - time}ms): $scnRepairs")
    println("Both: ${levRepairs intersect scnRepairs}")
    println("Lev - Scn: ${levRepairs - scnRepairs}")
    println("Scn - Lev: ${scnRepairs - levRepairs}")

//    assertTrue(scnRepairs in levRepairs, "scnRepairs ⊈ levRepairs: ${scnRepairs - levRepairs}")
  }

  /*
  Before constraints:
  - Lev repairs (total time = 23334ms)
  - Scn repairs (total time = 7892ms)

  With Parikh constraints:
  - Lev repairs (total time = 24120ms)
  - Scn repairs (total time = 2418ms)
   */

  val arithCFG: CFG = """
      START -> S
      S -> BS | IS
      
      IO -> + | - | * | /
      IS -> N | - N | IS IO IS | ( IS ) | BS
      N -> N1 | N2 | N3 | N4 | N5 | N6 | N7 | N8 | N9
      N1 -> 1 
      N2 -> 2 
      N3 -> 3
      N4 -> 4
      N5 -> 5
      N6 -> 6
      N7 -> 7
      N8 -> 8
      N9 -> 9
      
      B -> true | false
      BO -> and | or | =
      ISC -> IS < IS | IS > IS | IS == IS | IS != IS | if ( BS ) then { BS } else { BS } | if ( BS ) then { IS } else { IS }
      BS -> B | BS BO BS | ( ISC )
    """.trimIndent().parseCFG()

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.SATValiantTest.testTreelikePruning"
*/

  @Test
  fun testTreelikePruning() {
    val cfg = arithCFG
      .also { println("Before pruning (${it.size}):\n${it.prettyPrint()}") }
      .pruneTreelikeNonterminals
      .also { println("After pruning (${it.size}):\n${it.prettyPrint()}") }
    "_ _ _ _ _ _".synthesizeIncrementally(cfg)
      .onEach { println(it) }
      .take(10)
      .toList()
      .also { println("All: $it") }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.SATValiantTest.testCoarsening"
*/
  @Test
  fun testCoarsening() {
      println(sumCFG.prettyPrint())
      println(sumCFG.parseTable("1 + ( 3 + - 2 ) + 4").toHtmlPage().show())
      println(sumCFG.parseTable("1 + <S> + 4").toHtmlPage().show())
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.SATValiantTest.testLevenshteinRepair"
*/
  @Test
  fun testLevenshteinRepair() {
    val cfg = sumCFG.noNonterminalStubs
    val strings = "3 _ _ + _ _ _ _ 4".synthesizeIncrementally(cfg)
      .onEach { println(it) }

   // Deletes two random characters from each string
    val corruptedStrings =
      strings.map { val tokens = it.split(' ')
        val toDelete = tokens.indices.shuffled().take(2)
        tokens.filterIndexed { i, _ -> i !in toDelete }.joinToString(" ")
      }
        .filter { it !in cfg.language }
        .take(10).toList()

    val repairs = corruptedStrings.map { os ->
      println("Corrupted: $os")
      println("Repairs:")
      cfg.levenshteinRepair(2, os.tokenizeByWhitespace(), solver = { synthesize(it) })
        .distinct().take(20).toList().sortedBy { levenshtein(os, it) }
        .also { println(it.joinToString("\n")) }
    }

    repairs.flatten().also { assert(it.isNotEmpty()) }.forEach { assertTrue { it in arithCFG.language } }
  }

 /*
 ./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.SATValiantTest.testBoolFormula"
 */
  @Test
  fun testBoolFormula() {
    println(booleanFormulaCFG.parse("( ( true ) $LOR ( true $LOR ( false ) ) ) $AND ( true )")!!.evalToBool()!!)
//    println(sumCFG.nonterminalFormulas["S"])
//    measureTimeMillis {
//      val ops = listOf("&", "|")
//      val px = (0..10).joinToString(" ", "(") { "x$it ${ops.random()}" }.dropLast(2) + ")"
//      println(px)
//      val py = (0..10).joinToString(" ", "(") { "y$it ${ops.random()}" }.dropLast(2) + ")"
//      println(py)
//      val (x, y) = ff.parse(px) to ff.parse(py)
//      println((x eq y).transform(TseitinTransformation())
//      )
//    }.also { println("Time: ${it}ms") }

    println(arithCFG.originalForm.prettyPrint())
    arithCFG.originalForm.depGraph.let {
      val start = it.vertices.first { it.label == "START" }
      println(it.reachability(setOf(start), 2))
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.SATValiantTest.testLongTerminals"
*/
  @Test
  fun testLongTerminals() {
    println("START -> A B C D E F G H I".parseCFG().prettyPrint())
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.SATValiantTest.testConstantFormulaPropagation"
*/
  @Test
  fun testConstantFormulaPropagation() {
    // How quickly can we decide whether a string s is a substring of no string in CFL ∩ Σⁿ?
    val cfg = """S -> S + S | S * S | ( S ) | x""".parseCFG()

    "x + x + _ _ _ _ x ) + x".synthesizeIncrementally(cfg).toList()
      .forEach { println(it) }
  }
}