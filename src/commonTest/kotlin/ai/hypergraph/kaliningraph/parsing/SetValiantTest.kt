package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.automata.Stack
import ai.hypergraph.kaliningraph.automata.peek
import ai.hypergraph.kaliningraph.automata.pop
import ai.hypergraph.kaliningraph.automata.push
import ai.hypergraph.kaliningraph.sampling.MDSamplerWithoutReplacement
import ai.hypergraph.kaliningraph.tensor.seekFixpoint
import ai.hypergraph.kaliningraph.types.π2
import kotlinx.datetime.Clock
import kotlin.collections.*
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
      assertTrue("aaaa".matches(cfg))
      assertTrue("bbbb".matches(cfg))
      assertFalse("abab".matches(cfg))
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
      assertTrue("aaabbb".also { println(cfg.parse(it)) }.matches(cfg))
      assertTrue("aabb".matches(cfg))
      assertFalse("abab".matches(cfg))
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
      assertTrue("()(()())()".matches(cfg))
      assertFalse("()(()()()".matches(cfg))
      assertTrue("()(())".matches(cfg))
      assertTrue("()()".matches(cfg))
      assertFalse("())".matches(cfg))
      assertFalse(")".matches(cfg))
    }

    """S -> ( ) | ( S ) | S S""".parseCFG().let { cfg ->
      assertTrue("()(()())()".matches(cfg))
      assertFalse("()(()()()".matches(cfg))
      assertTrue("()(())".matches(cfg))
      assertTrue("()()".matches(cfg))
      assertFalse("())".matches(cfg))
      assertFalse(")".matches(cfg))
    }
  }

  /*
   * Takes a grammar and a partially complete string where '_' denotes holes, and
   * returns a set of completed strings consistent with that grammar. Naive search
   * over all holes takes O(|Σ|^n) where n is the number of holes.
   */

  fun String.solve(CFG: CFG, fillers: Set<String> = CFG.terminals): Sequence<String> =
    genCandidates(CFG, fillers).filter {
      (it.matches(CFG) to it.dyckCheck()).also { (valiant, stack) ->
        // Should never see either of these statements if we did our job correctly
        if (!valiant && stack) println("Valiant under-approximated Stack: $it")
        else if (valiant && !stack) println("Valiant over-approximated Stack: $it")
      }.first
    }

  val HOLE_MARKER = '_'

  fun String.genCandidates(CFG: CFG, fillers: Set<String> = CFG.terminals) =
    MDSamplerWithoutReplacement(fillers, count { it == HOLE_MARKER }).map {
      fold("" to it) { (a, b), c ->
        if (c == '_') (a + b.first()) to b.drop(1) else (a + c) to b
      }.first.replace("ε", "")
    }

  infix fun Char.matches(that: Char) =
    if (this == ')' && that == '(') true
    else if (this == ']' && that == '[') true
    else if (this == '}' && that == '{') true
    else this == '>' && that == '<'

  fun String.dyckCheck() =
    filter { it in "()[]{}<>" }.fold(Stack<Char>()) { stack, c ->
      stack.apply { if (isNotEmpty() && c.matches(peek())) pop() else push(c) }
    }.isEmpty()

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest.testDyckSolver"
*/
  @Test
  fun testDyckSolver() {
    """S -> ( ) | ( S ) | S S""".parseCFG().let { cfg ->
      val sols = "(____()____)".solve(cfg, fillers = cfg.terminals + "")
        .map { println(it); it }.take(5).toList()
      println("${sols.distinct().size}/${sols.size}")
      println("Solutions found: ${sols.joinToString(", ")}")

      sols.forEach { assertTrue(it.dyckCheck()) }
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest.testDyck2Solver"
*/
  @Test
  fun testDyck2Solver() {
    """S -> ( ) | [ ] | ( S ) | [ S ] | S S""".parseCFG(validate = true).let { CFG: CFG ->
      println("CFL parsed: ${CFG.prettyPrint()}")
      val sols = "________".solve(CFG)
        .map { println(it); it }.take(5).toList()
      println("${sols.distinct().size}/${sols.size}")

      println("Solutions found: ${sols.joinToString(", ")}")

      sols.forEach { assertTrue(it.dyckCheck()) }
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
        val template = List(len) { "_" }.joinToString("")
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
      assertTrue("()[()()]()".matches(cfg))
      assertFalse("([()()]()".matches(cfg))
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest.testDyck3Language"
*/
  @Test
  fun testDyck3Language() {
  """S -> ( ) | [ ] | { } | ( S ) | [ S ] | { S } | S S""".let { cfg ->
      // TODO: Fix under approximation?
      assertTrue("{()[(){}()]()}".matches(cfg))
      assertFalse("{()[(){()]()}".matches(cfg))
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest.testDyck3Solver"
*/
  @Test
  fun testDyck3Solver() {
    """S -> ( ) | [ ] | ( S ) | [ S ] | { S } | S S""".parseCFG().let { cfg ->
      val sols = "(____()____)".solve(cfg)
        .map { println(it); it }.take(5).toList()
      println("Solution found: ${sols.joinToString(", ")}")

      sols.forEach { assertTrue(it.dyckCheck()) }
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
    val normalForm = "S -> c | d".parseCFG()
    """
      S -> A
      A -> B
      B -> C
      B -> D
      C -> c
      D -> d
    """.parseCFG().let { cfg ->
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
      assertTrue("aaaaaabb".matches(cfg))
      assertFalse("aaaaaa".matches(cfg))
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
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest.testUTMRepresentationEquivalence"
*/
  @ExperimentalTime
  @Test
  fun testUTMRepresentationEquivalence() {
    with("""P -> ( P ) | P P | ε""".parseCFG()) {
      val str = tokenize("(()())()((())())((()))()()()")
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