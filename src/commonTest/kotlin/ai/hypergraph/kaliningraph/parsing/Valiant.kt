package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.parsing.CFL.Companion.parse
import ai.hypergraph.kaliningraph.parsing.CFL.Companion.validate
import kotlin.test.*
/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.Valiant"
*/
class Valiant {
/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.Valiant.testSimpleGrammar"
*/
  @Test
  fun testSimpleGrammar() {
    CFL(
        "   S -> NP VP ", // -- a Noun Phrase + a Verb Phrase
        "  VP -> eats  ", //
        "  VP -> VP PP ", // -- a VP can end with a PP or an NP
        "  VP -> VP NP ", //
        "  PP -> P NP  ", // -- Preposition Phrase ("with a fork")
        "   P -> with  ", // -- Proposition(s)
        "  NP -> she   ", //
        "  NP -> Det N ", // -- Noun Phrase ("a fish")
        "  NP -> NP PP ", // -- optional "a fish with a fork"
        "   N -> fish  ", // -- Nouns
        "   N -> fork  ", //
        " Det -> a     ", // -- Determiner
    ).run {
      assertTrue(isValid("she eats a fish with a fork"))
      assertFalse(isValid("she eats fish with"))
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.Valiant.testBNFParsing"
*/
  @Test
  fun testBNFParsing() {
    CFL("""
        S -> A | B
        A -> a | A A
        B -> b | B B
      """.validate().parse()).run {
      assertTrue(isValid(tokens = "aaaa".map { it.toString() }))
      assertTrue(isValid(tokens = "bbbb".map { it.toString() }))
      assertFalse(isValid(tokens = "abab".map { it.toString() }))
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.Valiant.testAABB"
*/
  @Test
  fun testAABB() {
//    https://www.ps.uni-saarland.de/courses/seminar-ws06/papers/07_franziska_ebert.pdf#page=3
    CFL("""
     S -> X Y
     X -> X A
     X -> A A
     Y -> Y B
     Y -> B B
     A -> a
     B -> b
    """).run {
      assertTrue(isValid(tokens = "aaabbb".map { it.toString() }))
      assertTrue(isValid(tokens = "aabb".map { it.toString() }))
      assertFalse(isValid(tokens = "abab".map { it.toString() }))
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.Valiant.testDyckLanguage"
*/
  @Test
  fun testDyckLanguage() {
    CFL("""
     S -> A B
     S -> A C
     S -> S S
     C -> S B
     A -> (
     B -> )
    """).run {
      assertTrue(isValid(tokens = "()(()())()".map { it.toString() }))
      assertFalse(isValid(tokens = "()(()()()".map { it.toString() }))
      assertTrue(isValid(tokens = "()(())".map { it.toString() }))
      assertTrue(isValid(tokens = "()()".map { it.toString() }))
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.Valiant.testDyck2Language"
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
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.Valiant.testDyck3Language"
*/
  @Test
  fun testDyck3Language() {
    CFL("""S -> ( ) | [ ] | { } | ( S ) | [ S ] | { S } | S S""").run {
      assertTrue(isValid(tokens = "{()[(){}()]()}".map { it.toString() }))
      assertFalse(isValid(tokens = "{()[(){()]()}".map { it.toString() }))
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.Valiant.testNormalization"
*/
  @Test
  fun testNormalization() {
    CFL("""
      S -> a X b X
      X -> a Y | b Y
      Y -> X | c
    """).run {
      println(this)
      normalForm.forEach { (_, b) -> assertContains(1..2, b.size) }
      nonterminals.unzip().second.flatten()
        .forEach { assertContains(variables, it) }
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.Valiant.testDropUnitProds"
*/
  @Test
  fun testDropUnitProds() {
    CFL("""
      S -> A
      A -> B
      B -> C
      B -> D
      C -> c
      D -> d
    """).run { assertEquals(CFL("S -> c | d").toString(), toString()) }

    CFL("""
      S -> C | D
      C -> c
      D -> d
    """).run { assertEquals(CFL("S -> c | d").toString(), toString()) }
  }
}