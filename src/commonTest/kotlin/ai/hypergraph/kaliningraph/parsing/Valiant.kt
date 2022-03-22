package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.types.toVT
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
    val cfl = CFL(
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
    )

    assertTrue(cfl.isValid("she eats a fish with a fork"))
    assertFalse(cfl.isValid("she eats fish with"))
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.Valiant.testAABB"
*/
  @Test
  fun testAABB() {
//    https://www.ps.uni-saarland.de/courses/seminar-ws06/papers/07_franziska_ebert.pdf#page=3
    val cfl = CFL("""
     S -> X Y
     X -> X A
     X -> A A
     Y -> Y B
     Y -> B B
     A -> a
     B -> b
    """)

    assertTrue(cfl.isValid(tokens = "aaabbb".map { it.toString() }))
    assertTrue(cfl.isValid(tokens = "aabb".map { it.toString() }))
    assertFalse(cfl.isValid(tokens = "abab".map { it.toString() }))
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.Valiant.testDyckLanguage"
*/
  @Test
  fun testDyckLanguage() {
    val cfl = CFL("""
     S -> A B
     S -> A C
     S -> S S
     C -> S B
     A -> (
     B -> )
    """)

    assertTrue(cfl.isValid(tokens = "()(()())()".map { it.toString() }))
    assertFalse(cfl.isValid(tokens = "()(()()()".map { it.toString() }))
    assertTrue(cfl.isValid(tokens = "()(())".map { it.toString() }))
    assertTrue(cfl.isValid(tokens = "()()".map { it.toString() }))
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.Valiant.testDyck2Language"
*/
  fun testDyck2Language() {
    // TODO: fix
    val cfl = CFL("""S -> ( ) | [ ] | ( S ) | [ S ]""".trimIndent())

    println(cfl)

    assertTrue(cfl.isValid(tokens = "()[()()]()".map { it.toString() }))
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.Valiant.testNormalization"
*/
  @Test
  fun testNormalization() {
    val cfl = CFL("""
        S -> a X b X 
        X -> a Y | b Y
        Y -> X | c
      """.trimIndent())

    cfl.normalForm
      .forEach { (_, b) -> assertContains(1..2, b.size) }
    cfl.nonterminals.flatMap { it.second.toVT() }
      .forEach { assertContains(cfl.variables, it) }
  }
}