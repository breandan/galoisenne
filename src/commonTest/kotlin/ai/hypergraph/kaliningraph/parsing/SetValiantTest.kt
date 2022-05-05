package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.types.π2
import kotlinx.datetime.Clock
import kotlin.collections.*
import kotlin.test.*

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
    """.let { cfl ->
      assertTrue("she eats a fish with a fork".matches(cfl))
      assertFalse("she eats fish with".matches(cfl))
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
    """.let { cfl ->
      assertTrue("aaaa".matches(cfl))
      assertTrue("bbbb".matches(cfl))
      assertFalse("abab".matches(cfl))
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
    """.let { cfl ->
      assertTrue("aaabbb".also { println(cfl.parse(it)) }.matches(cfl))
      assertTrue("aabb".matches(cfl))
      assertFalse("abab".matches(cfl))
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
    """.let { cfl ->
      assertTrue("( 1 + 2 * 3 ) / 4".matches(cfl))
      assertFalse("( 1 + 2 * 3 ) - ) / 4".matches(cfl))
      assertFalse("( 1 + 2 * 3 ) - ( ) / 4".matches(cfl))
      println(cfl.parse("( 1 + ( 2 * 3 ) ) / 4").prettyPrint())
      println(cfl.parseCFG().prettyPrint())
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest.testCFLValidationFails"
*/
//  @Test
//  fun testCFLValidationFails() {
//    assertFails { """( S ) -> S""".validate() }
//  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest.testDyckLanguage"
*/
  @Test
  fun testDyckLanguage() {
    """
      S -> A B
      S -> A C
      S -> S S
      C -> S B
      A -> (
      B -> )
    """.let { cfl ->
      assertTrue("()(()())()".matches(cfl))
      assertFalse("()(()()()".matches(cfl))
      assertTrue("()(())".matches(cfl))
      assertTrue("()()".matches(cfl))
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest.testDyckSolver"
*/
  @Test
  fun testDyckSolver() {
    """S -> ( ) | ( S ) | S S""".parseCFG().let { cfl ->
      val sols = "(____()____)".solve(cfl, fillers = cfl.alphabet + "")
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
    """S -> ( ) | [ ] | ( S ) | [ S ] | S S""".parseCFG().let { CFG: CFG ->
      println("CFL parsed: ${CFG.prettyPrint()}")
      val sols = "__________".solve(CFG)
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
    """S -> ( ) | [ ] | ( S ) | [ S ] | S S""".parseCFG().let { cfl ->
      println("Total Holes, Instances Checked, Solutions Found")
      for (len in 2..6 step 2) {
        val template = List(len) { "_" }.joinToString("")
        fun now() = Clock.System.now().toEpochMilliseconds()
        val startTime = now()
        var totalChecked = 0
        val sols = template
          .genCandidates(cfl, cfl.alphabet)
          .map { totalChecked++; it }
          .filter { it.matches(cfl) }.distinct()
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
    """S -> ( ) | [ ] | ( S ) | [ S ] | S S""".let { cfl ->
      println("Grammar: $this")
      assertTrue("()[()()]()".matches(cfl))
      assertFalse("([()()]()".matches(cfl))
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest.testDyck3Language"
*/
  @Test
  fun testDyck3Language() {
  """S -> ( ) | [ ] | { } | ( S ) | [ S ] | { S } | S S""".let { cfl ->
      // TODO: Fix under approximation?
      assertTrue("{()[(){}()]()}".matches(cfl))
      assertFalse("{()[(){()]()}".matches(cfl))
    }
  }


/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest.testDyck3Solver"
*/
  @Test
  fun testDyck3Solver() {
    """S -> ( ) | [ ] | ( S ) | [ S ] | { S } | S S""".parseCFG().let { cfl ->
      val sols = "(____()____)".solve(cfl)
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
    """.parseCFG().let { cfl ->
      println(cfl)
      cfl.forEach { (_, b) -> assertContains(1..2, b.size) }
      cfl.nonterminals.flatMap { it.π2 }.forEach { assertContains(cfl.variables, it) }
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
    """.parseCFG().let { cfl -> assertEquals(normalForm, cfl) }

    """
      S -> C | D
      C -> c
      D -> d
    """.parseCFG().let { cfl -> assertEquals(normalForm, cfl) }
  }
}