package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.hasBalancedBrackets
import ai.hypergraph.kaliningraph.sampling.MDSamplerWithoutReplacement
import ai.hypergraph.kaliningraph.tensor.seekFixpoint
import ai.hypergraph.kaliningraph.types.π2
import kotlinx.datetime.Clock
import prettyPrint
import kotlin.test.*
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest"
*/
class SetValiantTest {
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
    """
      S -> S + S | S * S | S - S | S / S | ( S )
      S -> 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9
      S -> X | Y | Z
    """.let { cfg ->
      assertTrue("( 1 + 2 * 3 ) / 4".matches(cfg))
      assertFalse("( 1 + 2 * 3 ) - ) / 4".matches(cfg))
      assertFalse("( 1 + 2 * 3 ) - ( ) / 4".matches(cfg))
      println(cfg.parse("( 1 + ( 2 * 3 ) ) / 4")?.prettyPrint())
      println(cfg.parseCFG().prettyPrint())
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
      val sols = "( _ _ _ _ ( ) _ _ _ _ ) ".split(" ")
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
      val sols = "_ _ _ _ _ _ _ _ ".split(" ").solve(CFG).take(5).toList()
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
      for (len in 2..6 step 2) {
        val template = List(len) { "_" }
        fun now() = Clock.System.now().toEpochMilliseconds()
        val startTime = now()
        var totalChecked = 0
        val sols = template
          .genCandidates(cfg, cfg.terminals)
          .map { totalChecked++; it }
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
      val sols = "( _ _ _ _ ( ) _ _ _ _ )".split(" ").solve(cfg).take(5).toList()
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
  @Test
  fun testDropUnitProds() {
    "S -> c | d".parseCFG()
    """
      S -> A
      A -> B
      B -> C
      B -> D
      C -> c
      D -> d
    """.parseCFG().let { cfg ->
      println(cfg.prettyPrint())
      assertTrue("B" !in cfg.nonterminals)
      assertTrue("A" !in cfg.nonterminals)
    }

    """
      S -> C | D
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
    val cfg = """
      S -> X
      X -> A | V | ( X , X ) | X X | ( X )
      A -> FUN | F | LI | M | L
      FUN -> fun V `->` X
      F -> if X then X else X
      M -> match V with Branch
      Branch -> `|` X `->` X | Branch Branch
      L -> let V = X
      L -> let rec V = X
      LI -> L in X

      V -> Vexp | ( Vexp ) | List | Vexp Vexp
      Vexp -> Vname | FunName | Vexp VO Vexp | B
      Vexp -> ( Vname , Vname ) | Vexp Vexp | I
      List -> [] | V :: V
      Vname -> a | b | c | d | e | f | g | h | i
      Vname -> j | k | l | m | n | o | p | q | r
      Vname -> s | t | u | v | w | x | y | z
      FunName -> foldright | map | filter
      FunName -> curry | uncurry | ( VO )
      VO ->  + | - | * | / | >
      VO -> = | < | `||` | `&&`
      I -> 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9
      B ->  true | false
    """.trimIndent().parseCFG()
    cfg.parse("1 + <I> + 2").also { println(it!!.prettyPrint()) }
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
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest.testUTMRepresentationEquivalence"
*/
  @ExperimentalTime
  @Test
  fun testUTMRepresentationEquivalence() {
    with("""P -> ( P ) | P P | ε""".parseCFG()) {
      val str = "( ( ) ( ) ) ( ) ( ( ( ) ) ( ) ) ( ( ( ) ) ) ( ) ( ) ( )".tokenizeByWhitespace()
      val slowTransitionFP =  measureTimedValue {
        initialMatrix(str).seekFixpoint(succ={it + it * it})
      }.also { println("Slow transition: ${it.duration.inWholeMilliseconds}") }.value
      val fastTransitionFP = measureTimedValue {
        initialUTMatrix(str).seekFixpoint().toFullMatrix()
      }.also { println("Fast transition: ${it.duration.inWholeMilliseconds}ms") }.value

      assertEquals(slowTransitionFP, fastTransitionFP)
    }
  }
}