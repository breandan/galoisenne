package ai.hypergraph.kaliningraph.parsing

import Grammars
import Grammars.toyArith
import ai.hypergraph.kaliningraph.*
import ai.hypergraph.kaliningraph.types.*
import ai.hypergraph.kaliningraph.repair.vanillaS2PCFG
import ai.hypergraph.kaliningraph.tensor.rows
import ai.hypergraph.kaliningraph.tensor.seekFixpoint
import ai.hypergraph.kaliningraph.types.π2
import kotlinx.datetime.Clock
import kotlin.math.ceil
import kotlin.math.log
import kotlin.math.log2
import kotlin.random.Random
import kotlin.test.*
import kotlin.time.*

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest"
*/
class SetValiantTest {
/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest.testCompleteness"
*/
  @Test
  fun testCompleteness() {
    val g = Grammars.toyArith
    val sols = g.enumSeq("_ + _".tokenizeByWhitespace()).toSet()

    val all = g.terminals.filter { it.all { it.isLetterOrDigit() && it.code < 128 } }
      .toSet().let { it * it }.map { (a, b) -> "$a + $b" }.toSet()

    assertEquals(all, sols)
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest.testStressRecognizer"
*/
  @Test
  fun testStressRecognizer() {
    val g = Grammars.seq2parsePythonVanillaCFG
    g.sliceSample(20).take(10000).forEach {
      assertTrue(it.matches(g))
      assertFalse(it.tokenizeByWhitespace().dropLastWhile { it == "DEDENT" || it == "NEWLINE" }.matches(g))
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest.testSimpleGrammar"
*/
  @Test
  fun testSimpleGrammar() {
    """
        S -> NP VP    
       VP -> eats    
       VP -> VP PP
       VP -> VP NP
       PP -> P NP
        P -> with
       NP -> she
       NP -> Det N
       NP -> NP PP
        N -> fish
        N -> fork
      Det -> a
    """.let { cfg ->
      assertTrue("she eats a fish with a fork".matches(cfg))
      assertFalse("she eats fish with".matches(cfg))
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest.testVerySimpleGrammar"
*/
  @Test
  fun testVerySimpleGrammar() {
    """
      S -> A | B
      A -> a | A A
      B -> b | B B
    """.let { cfg ->
      assertTrue("a a a a ".matches(cfg))
      assertTrue("b b b b ".matches(cfg))
      assertFalse("a b a b ".matches(cfg))
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest.testAABB"
*/
  @Test
  fun testAABB() {
//  https://www.ps.uni-saarland.de/courses/seminar-ws06/papers/07_franziska_ebert.pdf#page=3
    """
      S -> X Y
      X -> X A
      X -> A A
      Y -> Y B
      Y -> B B
      A -> a
      B -> b
    """.let { cfg ->
      assertTrue("a a a b b b ".also { println(cfg.parse(it)) }.matches(cfg))
      assertTrue("a a b b ".matches(cfg))
      assertFalse("a b a b ".matches(cfg))
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest.testArithmetic"
*/
  @Test
  fun testArithmetic() {
    toyArith.let { cfg ->
      assertTrue("( 1 + 2 * 3 ) / 4".matches(cfg))
      assertFalse("( 1 + 2 * 3 ) - ) / 4".matches(cfg))
      assertFalse("( 1 + 2 * 3 ) - ( ) / 4".matches(cfg))
      println(cfg.parse("( 1 + ( 2 * 3 ) ) / 4")?.prettyPrint())
      println(cfg.prettyPrint())
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest.testCFLValidationFails"
*/
  @Test
  fun testCFLValidationFails() {
    assertFails { """( S ) -> S""".validate() }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest.testDyckLanguage"
*/
  @Test
  fun testDyckLanguage() {
    """
        START -> T
        T -> A B
        T -> A C
        T -> T T
        C -> T B
        A -> (
        B -> )
      """.parseCFG().let { cfg ->
      println(cfg.prettyPrint())
      assertTrue("( ) ( ( ) ( ) ) ( ) ".matches(cfg))
      assertFalse("( ) ( ( ) ( ) ( ) ".matches(cfg))
      assertTrue("( ) ( ( ) ) ".matches(cfg))
      assertTrue("( ) ( ) ".matches(cfg))
      assertFalse("( ) ) ".matches(cfg))
      assertFalse(")".matches(cfg))
    }

    """S -> ( ) | ( S ) | S S""".parseCFG().let { cfg ->
      assertTrue("( ) ( ( ) ( ) ) ( ) ".matches(cfg))
      assertFalse("( ) ( ( ) ( ) ( ) ".matches(cfg))
      assertTrue("( ) ( ( ) ) ".matches(cfg))
      assertTrue("( ) ( ) ".matches(cfg))
      assertFalse("( ) ) ".matches(cfg))
      assertFalse(")".matches(cfg))
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest.testDyckSolver"
*/
  @Test
  fun testDyckSolver() {
    """S -> ( ) | ( S ) | S S""".parseCFG().let { cfg ->
      val sols = "( _ _ _ _ ( ) _ _ _ _ ) ".tokenizeByWhitespace()
        .solve(cfg, fillers = cfg.terminals + "").take(5).toList()
      println("${sols.distinct().size}/${sols.size}")
      println("Solutions found: ${sols.joinToString(", ")}")

      sols.forEach { assertTrue(it.hasBalancedBrackets()) }
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest.testDyck2Solver"
*/
  @Test
  fun testDyck2Solver() {
    """S -> ( ) | [ ] | ( S ) | [ S ] | S S""".parseCFG(validate = true).let { CFG: CFG ->
      println("CFL parsed: ${CFG.prettyPrint()}")
      val sols = "_ _ _ _ _ _ _ _ ".tokenizeByWhitespace().solve(CFG).take(5).toList()
      println("${sols.distinct().size}/${sols.size}")

      println("Solutions found: ${sols.joinToString(", ")}")

      sols.forEach { assertTrue(it.hasBalancedBrackets()) }
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest.benchmarkNaiveSearch"
*/
  @Test
  fun benchmarkNaiveSearch() {
    """S -> ( ) | [ ] | ( S ) | [ S ] | S S""".parseCFG().let { cfg ->
      println("Total Holes, Instances Checked, Solutions Found")
      for (len in 2..8 step 2) {
        val template = List(len) { "_" }
        fun now() = Clock.System.now().toEpochMilliseconds()
        val startTime = now()
        var totalChecked = 0
        val sols = template
          .genCandidates(cfg, cfg.terminals)
          .onEach { totalChecked++ }
          .filter { it.matches(cfg) }.distinct()
          .takeWhile { now() - startTime < 20000 }.toList()

        println("$len".padEnd(11) + ", $totalChecked".padEnd(19) + ", ${sols.size}")
      }
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest.testDyck2Language"
*/
  @Test
  fun testDyck2Language() {
    """S -> ( ) | [ ] | ( S ) | [ S ] | S S""".let { cfg ->
      println("Grammar: $this")
      assertTrue("( ) [ ( ) ( ) ] ( ) ".matches(cfg))
      assertFalse("( [ ( ) ( ) ] ( ) ".matches(cfg))
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest.testDyck3Language"
*/
  @Test
  fun testDyck3Language() {
  """S -> ( ) | [ ] | { } | ( S ) | [ S ] | { S } | S S""".let { cfg ->
      // TODO: Fix under approximation?
      assertTrue("{ ( ) [ ( ) { } ( ) ] ( ) } ".matches(cfg))
      assertFalse("{ ( ) [ ( ) { ( ) ] ( ) } ".matches(cfg))
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest.testDyck3Solver"
*/
  @Test
  fun testDyck3Solver() {
    """S -> ( ) | [ ] | ( S ) | [ S ] | { S } | S S""".parseCFG().let { cfg ->
      val sols = "( _ _ _ _ ( ) _ _ _ _ )".tokenizeByWhitespace().solve(cfg).take(5).toList()
      println("Solution found: ${sols.joinToString(", ")}")

      sols.forEach { assertTrue(it.hasBalancedBrackets()) }
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest.testNormalization"
*/
  @Test
  fun testNormalization() {
    """
      S -> a X b X
      X -> a Y | b Y
      Y -> X | c
    """.parseCFG().let { cfg ->
      println(cfg)
      cfg.forEach { (_, b) -> assertContains(1..2, b.size) }
      cfg.nonterminalProductions.flatMap { it.π2 }.forEach { assertContains(cfg.nonterminals, it) }
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest.testEscapeChars"
*/
  @Test
  fun testEscapeChars() {
      """
        S -> a `->` b `|` c
      """.parseCFG().let { cfg ->
        println(cfg.prettyPrint())
        assertTrue("a -> b | c".matches(cfg))
      }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest.testDropUnitProds"
*/
//  @Test
  fun testDropUnitProds() {
    "START -> c | d".parseCFG()
    """
      START -> A
      A -> B
      B -> C
      B -> D
      C -> c
      D -> d
    """.parseCFG().noNonterminalStubs.let { cfg ->
      println(cfg.prettyPrint())
      assertTrue("B" !in cfg.nonterminals)
      assertTrue("A" !in cfg.nonterminals)
    }

    """
      START -> C | D
      C -> c
      D -> d
    """.parseCFG()
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest.testNotCopy"
*/
  @Test
  fun testNotCopy() {
// https://cs.stackexchange.com/a/19155/74308
    """
      START -> A | B | A B | B A
      A -> a | a A a | a A b | b A b | b A a
      B -> b | a B a | a B b | b B b | b B a
    """.parseCFG().let { cfg ->
      assertTrue("a a a a a a b b ".matches(cfg))
      assertFalse("a a a a a a ".matches(cfg))
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest.testEpsilonProd"
*/
  @Test
  fun testEpsilonProd() {
    """
      P -> W 1 1
      W -> ε | w
    """.parseCFG().let { cfg ->
      assertTrue("w 1 1".matches(cfg))
      assertTrue("1 1".matches(cfg))
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest.testLocalCanonicity"
*/
  @Test
  fun testLocalCanonicity() {
    val cfg1 = """P -> ( P ) | P P | ε""".parseCFG().also { println(it.prettyPrint()) }
    val cfg2 = """P -> ( P ) | ( P ) | P P | ε""".parseCFG().also { println(it.prettyPrint()) }
    assertEquals(cfg1, cfg2)

    val cfg3 = """P -> ( ) | P P | ( P )""".parseCFG().also { println(it.prettyPrint()) }
    val cfg4 = """P -> P P | ( P ) | ( )""".parseCFG().also { println(it.prettyPrint()) }
    assertEquals(cfg3, cfg4)
  }


/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest.testOCaml"
*/
  @Test
  fun testOCaml() {
    val expr = "1 + 2 + 3"
    val tree = Grammars.ocamlCFG.parse(expr)!!
    println(tree.prettyPrint())
    val leaves = tree.contents()
    assertEquals(expr, leaves)

    val holExpr = "_ _ _ _"

    measureTime {
      val solutions = Grammars.ocamlCFG.solve(holExpr, levMetric("( false curry )"))
      println("Found: ${solutions.size} unique solutions")
      solutions.forEach { println(it); assertTrue("$it was invalid!") { Grammars.ocamlCFG.isValid(it) } }
    }.also { println("Finished in ${it.inWholeMilliseconds}ms.") }
  }
/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest.testUnitParse"
*/
  @Test
  fun testUnitParse() {
    assertNotNull(vanillaS2PCFG.parse("NEWLINE"))
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest.testParametricLanguage"
*/
  @Test
  fun testParametricLanguage() {
    val cfg = """
      START -> E<X>
      op -> + | *
      E<X> -> E<X> op E<X>
      X -> Int | Bool | Float
      E<Int> -> 0 | 1 | 2 | 3
      E<Bool> -> T | F
      E<Float> -> E<Int> . E<Int>

      Upcasting (e.g., 1.0 + 2 ⊢ E<Float>):
      E<Float> -> E<Int> op E<Float> | E<Float> op E<Int>
    """.trimIndent().parseCFG()

    cfg.parse("<E<Float>> + <E<Int>> + <E<Float>>").also { println(it!!.prettyPrint()) }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest.testIfThenLanguage"
*/
  @Test
  fun testIfThenLanguage() {
    assertTrue("true and false" in Grammars.ifThen.language)
    assertTrue("( true and false" !in Grammars.ifThen.language)
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest.testUTMRepresentationEquivalence"
*/
  @ExperimentalTime
  @Test
  fun testUTMRepresentationEquivalence() {
    with(vanillaS2PCFG) {
      println("SIZE: ${nonterminals.size}")
      val str = "NAME = NAME ( NAME , NUMBER ) . NAME ( ) NEWLINE NAME = NAME . NAME NEWLINE NAME = STRING . NAME ( NAME for NAME in NAME if NAME . NAME ( ) ) NEWLINE NAME ( NAME ) NEWLINE".tokenizeByWhitespace()
//  with("""P -> ( P ) | P P | ε""".parseCFG()) {
//    val str = "( ( ) ( ) ) ( ) ( ( ( ) ) ( ) ) ( ( ( ) ) ) ( ) ( ) ( ) ( ( ) ( ) ) ( ) ( ( ) ( ) ) ( ) ( ( ) ( ) ) ( )".tokenizeByWhitespace()
      val iter = ceil(log2(str.size.toDouble())).toInt() + 9
      var i = 0
      val slowTransitionFP = measureTimedValue {
        initialMatrix(str).seekFixpoint(
          succ = {
            (it + it.squareUpperTriangular())
//            .also { println("ITER=${i++}"); it.prettyPrint(); println() }
           },
//          stop = { i, _, _ -> i == iter }
        )[0, str.size]
      }.also { println("Slow transition: ${it.duration.inWholeMilliseconds}ms") }.value

      i = 0
      val fastTransitionFP = measureTimedValue {
        initialUTMatrix(str).seekFixpoint(
//          debug = { println("ITER=${i++}"); it.toFullMatrix().prettyPrint(); println() }
        ).toFullMatrix()[0, str.size]
      }.also { println("Fast transition: ${it.duration.inWholeMilliseconds}ms") }.value

      measureTimedValue {
        println(isValidAlt(str))
      }.also { println("DP transition: ${it.duration.inWholeMilliseconds}ms") }.value

      println("Length: ${str.size}")

      assertEquals(slowTransitionFP, fastTransitionFP)
    }
  }

  fun TreeMatrix.prettyPrint() {
    rows.forEach { row ->
      println(row.joinToString(" ") { e -> if (e.isEmpty()) "  " else e.size.toString().padEnd(2) })
    }
  }
/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest.testHardestCFL"
*/
  @Test
  fun testHardestCFL() {
    Grammars.hardestCFL.enumSeqSmart(List(10){ "_" }).distinct().take(100)
      .forEach { println(it) }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest.testLevenshteinAutomata"
*/
  @Test
  fun testLevenshteinAutomata() {
    // Levenshtein automata for the word "flees" with d=1 and Σ={x,f,l,e,s}
    val cfg = """
       START -> d:4:0 | d:4:1 | d:5:0 | d:5:1
       * -> x | f | l | e | s
       
       d:1:0 -> f
       d:2:0 -> d:1:0 l
       d:3:0 -> d:2:0 e
       d:4:0 -> d:3:0 e
       d:5:0 -> d:4:0 s
       
       d:0:1 -> *
       d:1:1 -> d:0:1 f | d:1:0 * | *
       d:2:1 -> d:1:1 l | d:1:0 * | d:2:0 * | l
       d:3:1 -> d:2:1 e | d:2:0 * | d:3:0 * | d:1:0 e
       d:4:1 -> d:3:1 e | d:3:0 * | d:4:0 * | d:2:0 e
       d:5:1 -> d:4:1 s | d:4:0 * | d:5:0 * | d:3:0 s
    """.trimIndent().parseCFG()

    assertNotNull(cfg.parse("f l e e s"))
    assertNotNull(cfg.parse("x l e e s"))
    assertNotNull(cfg.parse("f x l e e s"))
    assertNotNull(cfg.parse("f l e e s x"))
    assertNotNull(cfg.parse("f l e e s x"))
    assertNull(cfg.parse("f e e l s"))
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest.testLevenshteinGrammar"
*/
  @Test
  fun testLevenshteinGrammar() {
    val cfg = constructLevenshteinCFG("flees".map { it.toString() }, 2, "flesx".map { it.toString() }.toSet())
      .also { println(it) }
      .parseCFG()
    assertNotNull(cfg.parse("f l e e s"))
    assertNotNull(cfg.parse("x l e e s"))
    assertNotNull(cfg.parse("f x l e e s"))
    assertNotNull(cfg.parse("f l e e s x"))
    assertNotNull(cfg.parse("f l e e s x"))
    assertNotNull(cfg.parse("f e e l s"))
    assertNull(cfg.parse("f e e l s s")?.prettyPrint())
  }

 /*
 ./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest.testLevenshteinWalker"
 */
  @Test
  fun testLevenshteinWalker() {
    val tks = listOf("a", "b", "c")
    val fsa = makeLevFSA(tks, 2)

    val from = fsa.Q.first().first
    val to = "q_3/0"
    val path = fsa.levWalk(from, to)
    assertEquals(fsa.levString, tks)
    assertEquals(path, tks)
    println(fsa.levString.subList(0, 3))
  }

  fun randomBitVector(size: Int) =
    (0 until size).map { Random.nextBoolean() }.toBooleanArray()

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest.benchmarkBitwiseJoin"
*/
  @Test
  fun benchmarkBitwiseJoin() {
    val size = Grammars.ocamlCFG.nonterminals.size
    val vidx = Grammars.ocamlCFG.vindex
    val pairs =
      (0..10_000_000).map { randomBitVector(size) to randomBitVector(size) }

    measureTime {
      pairs.map { (a, b) -> fastJoin(vidx, a, b) }
        .reduce { a, b -> union(a, b) }
    }.also { println("Merged a 10^6 bitvecs in ${it.inWholeMilliseconds}ms.") } // Should be ~5000ms
  }

  /*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest.testEnumerativeCombinatorics"
*/
  @Test
  fun testEnumerativeCombinatorics() {
    // Dyck numbers
    for (i in 4..16 step 2) {
      val rep1 = Grammars.dyckUnambig.enumSeq(List(i) { "_" }).toSet()
      print("" + rep1.size + ",")
    }
    println()
    for (i in 4..16 step 2) {
      val rep1 = Grammars.dyck.enumSeq(List(i) { "_" }).toSet()
      print("" + rep1.size + ",")
    }
    println()
    // Schröder numbers
    for (i in 4..16 step 2) {
      val rep1 = Grammars.dyck.startPTree(List(i) { "_" })
      print("" + rep1!!.totalTrees + ",")
    }
    println()

    // Hmm
    val r1 = Grammars.dyckUnambig.enumSeq(List(14) { "_" }).toSet()
    val r2 = Grammars.dyck.enumSeq(List(14) { "_" }).toSet()
    println(r1.size)
    println(r2.size)
    ((r2 - r1).forEach { println("" + (it in Grammars.dyckUnambig.language) + "/" + (it in Grammars.dyck.language)) })
  }
}