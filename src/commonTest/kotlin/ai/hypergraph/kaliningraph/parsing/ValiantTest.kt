package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.types.π2
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

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.ValiantTest.testDyckSolver"
*/
  @Test
  fun testDyckSolver() {
    """
     S -> ( ) | ( S ) | S S
    """.let { cfl ->
      val ss = "(__()__)".solve(cfl, allowEmptyStrings = false)
      val ps = setOf("(((())))", "(()()())", "(()())()", "()(()())", "()(())()")
      assertEquals(ps, ss)
//      val eps = setOf("(((())))", "(()()())", "(()())()", "(()()))", "(()())",
//        "((())))", "((()))", "()(()())", "()(())()", "()(()))", "()(())",
//        "()()())", "()()()", "()())", "(())()", "(()))", "(())")
//      val ess = "(__()__)".solve(cfl, allowEmptyStrings = true)
//      assertEquals(eps, ess)
    }
  }

  /*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.ValiantTest.testDyck2Solver"
*/
  @Test
  fun testDyck2Solver() {
    """
     S -> ( ) | ( S ) | [ S ] | S S
    """.let { cfl ->
      val solutions = "(__()__)".solve(cfl, allowEmptyStrings = false)
      println(solutions)
      assertTrue(solutions.all {
        it.count { it == '[' } == it.count { it == ']' } &&
          it.count { it == '(' } == it.count { it == ')' }
      })
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