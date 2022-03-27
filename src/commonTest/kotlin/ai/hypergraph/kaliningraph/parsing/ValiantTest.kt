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
    """.let { CFL(it) }.run {
      assertTrue(isValid("she eats a fish with a fork"))
      assertFalse(isValid("she eats fish with"))
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
    """.let { CFL(it) }.run {
      assertTrue(isValid(tokens = "aaaa".map { it.toString() }))
      assertTrue(isValid(tokens = "bbbb".map { it.toString() }))
      assertFalse(isValid(tokens = "abab".map { it.toString() }))
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
    """.let { CFL(it) }.run {
      assertTrue(isValid(tokens = "aaabbb".map { it.toString() }))
      assertTrue(isValid(tokens = "aabb".map { it.toString() }))
      assertFalse(isValid(tokens = "abab".map { it.toString() }))
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
    """.let { CFL(it) }.run {
      assertTrue(isValid(tokens = "()(()())()".map { it.toString() }))
      assertFalse(isValid(tokens = "()(()()()".map { it.toString() }))
      assertTrue(isValid(tokens = "()(())".map { it.toString() }))
      assertTrue(isValid(tokens = "()()".map { it.toString() }))
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.ValiantTest.testDyck2Language"
*/
  @Test
  fun testDyck2Language() {
    CFL("""S -> ( ) | [ ] | ( S ) | [ S ] | S S""").run {
      println("Grammar: $this")
      assertTrue(isValid(tokens = "()[()()]()".map { it.toString() }))
      assertFalse(isValid(tokens = "([()()]()".map { it.toString() }))
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.ValiantTest.testDyck3Language"
*/
  @Test
  fun testDyck3Language() {
    CFL("""S -> ( ) | [ ] | { } | ( S ) | [ S ] | { S } | S S""").run {
      assertTrue(isValid(tokens = "{()[(){}()]()}".map { it.toString() }))
      assertFalse(isValid(tokens = "{()[(){()]()}".map { it.toString() }))
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
    """.let { CFL(it) }.run {
      println(this)
      normalForm.forEach { (_, b) -> assertContains(1..2, b.size) }
      nonterminals.flatMap { it.π2 }.forEach { assertContains(variables, it) }
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.ValiantTest.testDropUnitProds"
*/
  @Test
  fun testDropUnitProds() {
    """
      S -> A
      A -> B
      B -> C
      B -> D
      C -> c
      D -> d
    """.let { CFL(it) }
    .run { assertEquals(CFL("S -> c | d").toString(), toString()) }

    """
      S -> C | D
      C -> c
      D -> d
    """.let { CFL(it) }
    .run { assertEquals(CFL("S -> c | d").toString(), toString()) }
  }
}