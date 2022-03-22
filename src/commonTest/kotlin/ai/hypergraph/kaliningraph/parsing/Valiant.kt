package ai.hypergraph.kaliningraph.parsing

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
    val cfg = CFG(
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

    assertTrue(cfg.isValid("she eats a fish with a fork"))
    assertFalse(cfg.isValid("she eats fish with"))
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.Valiant.testAABB"
*/
  @Test
  fun testAABB() {
//    https://www.ps.uni-saarland.de/courses/seminar-ws06/papers/07_franziska_ebert.pdf#page=3
    val cfg = CFG("""
     S -> X Y
     X -> X A
     X -> A A
     Y -> Y B
     Y -> B B
     A -> a
     B -> b
    """)

    assertTrue(cfg.isValid(tokens = "aaabbb".map { it.toString() }))
    assertTrue(cfg.isValid(tokens = "aabb".map { it.toString() }))
    assertFalse(cfg.isValid(tokens = "abab".map { it.toString() }))
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.Valiant.testDyckLanguage"
*/
  @Test
  fun testDyckLanguage() {
    val cfg = CFG("""
     S -> A B
     S -> A C
     S -> S S
     C -> S B
     A -> (
     B -> )
    """)

    assertTrue(cfg.isValid(tokens = "()(()())()".map { it.toString() }))
    assertFalse(cfg.isValid(tokens = "()(()()()".map { it.toString() }))
    assertTrue(cfg.isValid(tokens = "()(())".map { it.toString() }))
    assertTrue(cfg.isValid(tokens = "()()".map { it.toString() }))
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.Valiant.testNormalization"
*/
  @Test
  fun testNormalization() {
    val cfg = CFG("""
      S -> a X b X 
      X -> a Y | b Y
      Y -> X | c
    """.trimIndent())

    assertTrue(cfg.normalForm.all { (_, b) -> b.size in 1..2 })
  }
}