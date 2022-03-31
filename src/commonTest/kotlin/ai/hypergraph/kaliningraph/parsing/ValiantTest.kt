package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.*
import ai.hypergraph.kaliningraph.types.*
import kotlinx.datetime.*
import kotlin.math.pow
import kotlin.test.*

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.ValiantTest"
*/
class ValiantTest {
/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.ValiantTest.testSimpleGrammar"
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
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.ValiantTest.testVerySimpleGrammar"
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
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.ValiantTest.testAABB"
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
      assertTrue("aaabbb".matches(cfl))
      assertTrue("aabb".matches(cfl))
      assertFalse("abab".matches(cfl))
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.ValiantTest.testArithmetic"
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
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.ValiantTest.testCFLValidationFails"
*/
//  @Test
//  fun testCFLValidationFails() {
//    assertFails { """( S ) -> S""".validate() }
//  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.ValiantTest.testDyckLanguage"
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

  fun String.dyckCheck() =
    fold(mutableMapOf("<>" to 0, "()" to 0, "[]" to 0, "{}" to 0)) { a, c ->
      a.apply {
        keys.firstOrNull { c in it }
          ?.let { a[it] = a[it]!! + 2 * it.indexOf(c) - 1 }
      }
    }.values.sum() == 0

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.ValiantTest.testDyckSolver"
*/
  @Test
  fun testDyckSolver() {
    """S -> ( ) | ( S ) | S S""".parseCFL().let { cfl ->
      val sols = "(____()____)".solve(cfl, fillers = cfl.alphabet + "")
        .map { println(it); it }.toList()
      println("${sols.distinct().size}/${sols.size}")
      println("Solutions found: ${sols.joinToString(", ")}")

      sols.forEach { assertTrue(it.dyckCheck()) }
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.ValiantTest.testDyck2Solver"
*/
  @Test
  fun testDyck2Solver() {
    """S -> ( ) | [ ] | ( S ) | [ S ] | S S""".parseCFL().let { cfl ->
      val sols = "(___()___)".solve(cfl).map { println(it); it }.toList()
      println("${sols.distinct().size}/${sols.size}")

      println("Solutions found: ${sols.joinToString(", ")}")

      sols.forEach { assertTrue(it.dyckCheck()) }
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.ValiantTest.testExhaustiveSearch"
*/
  @Test
  fun testExhaustiveSearch() {
    // Checks whether the exhaustive search is truly exhaustive
    ((2..5).toSet() * (2..5).toSet()).forEach { (s, dim) ->
      val base = (0 until s).map { it.digitToChar().toString() }.toSet()
      val sfc = exhaustiveSearch(base, dim).toSet()
      assertEquals(sfc, sfc.distinct().toSet())
      assertEquals(s.toDouble().pow(dim).toInt(), sfc.size)
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.ValiantTest.testLFSR"
*/
  @Test
  fun testLFSR() {
    // Tests whether LFSR is maximal
    for(i in 4..10) {
      val size = LFSR(i).toList().distinct().size
      println("$i: ${2.0.pow(i).toInt()} / ${size + 1}")
      assertEquals(2.0.pow(i).toInt(), size + 1)
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.ValiantTest.benchmarkNaiveSearch"
*/
  @Test
  fun benchmarkNaiveSearch() {
    """S -> ( ) | [ ] | ( S ) | [ S ] | S S""".parseCFL().let { cfl ->
      println("Total Holes, Instances Checked, Solutions Found")
      for (len in 2..10 step 2) {
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
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.ValiantTest.testDyck2Language"
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
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.ValiantTest.testDyck3Language"
*/
  @Test
  fun testDyck3Language() {
  """S -> ( ) | [ ] | { } | ( S ) | [ S ] | { S } | S S""".let { cfl ->
      assertTrue("{()[(){}()]()}".matches(cfl))
      assertFalse("{()[(){()]()}".matches(cfl))
    }
  }


/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.ValiantTest.testDyck3Solver"
*/
  @Test
  fun testDyck3Solver() {
    """S -> ( ) | [ ] | ( S ) | [ S ] | { S } | S S""".parseCFL().let { cfl ->
      val sols = "(_____()_____)".solve(cfl)
        .map { println(it); it }.take(5).toList()
      println("Solution found: ${sols.joinToString(", ")}")

      sols.forEach { assertTrue(it.dyckCheck()) }
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.ValiantTest.testNormalization"
*/
  @Test
  fun testNormalization() {
    """
      S -> a X b X
      X -> a Y | b Y
      Y -> X | c
    """.parseCFL().let { cfl ->
      println(this)
      cfl.forEach { (_, b) -> assertContains(1..2, b.size) }
      cfl.nonterminals.flatMap { it.π2 }.forEach { assertContains(cfl.variables, it) }
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.ValiantTest.testDropUnitProds"
*/
  @Test
  fun testDropUnitProds() {
    val normalForm = "S -> c | d".parseCFL()
    """
      S -> A
      A -> B
      B -> C
      B -> D
      C -> c
      D -> d
    """.parseCFL().let { cfl -> assertEquals(normalForm, cfl) }

    """
      S -> C | D
      C -> c
      D -> d
    """.parseCFL().let { cfl -> assertEquals(normalForm, cfl) }
  }
}