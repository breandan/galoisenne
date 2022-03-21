package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.tensor.FreeMatrix
import org.junit.jupiter.api.Test
import ai.hypergraph.kaliningraph.types.*
import org.junit.jupiter.api.Assertions.*

class Valiant {
  class CFG(
    val grammar: List<Π2<String, List<String>>>
  ): List<Π2<String, List<String>>> by grammar {
    constructor(vararg productions: String): this(
      productions.map {
        it.split("->").map { it.trim() }.let { it[0] pp it[1] }
      }.map { (k, v) -> k pp v.split(" ") }
    )

    constructor(grammar: String): this(*grammar.lines().toTypedArray())

    val nonterminals = grammar.filter { it.π2.size == 2 }
      .map { (k, v) -> k pp (v[0] pp v[1]) }.toSet()
    val terminals = grammar.filter { it.π2.size == 1 }
      .map { (k, v) -> k pp v[0] }.toSet()
  }

  // This is not a proper ring, but close enough.
  fun makeAlgebra(cfg: CFG): Ring<Set<String>> =
    Ring.of(
      // 0 = ∅
      nil = setOf(),
      // TODO: Seems unused, maybe find a more specific algebra?
      one = setOf(),
      // x + y = x ∪ y
      plus = { x, y -> x union y },
      // x · y = {A0 | A1 ∈ x, A2 ∈ y, (A0 -> A1 A2) ∈ P}
      times = { x, y ->
        infix fun Set<String>.join(that: Set<String>) =
          cfg.nonterminals
            .filter { (_, A) -> A.π1 in this && A.π2 in that }
            .map { it.first }
            .toSet()

        x join y
      }
    )

  // Converts tokens to UT matrix using constructor: σi = {A | (A -> w[i]) ∈ P}
  fun List<String>.toMatrix(cfg: CFG): FreeMatrix<Set<String>> =
    FreeMatrix(makeAlgebra(cfg), size + 1) { i, j ->
      if (i + 1 != j) emptySet() // Enforce upper triangularity
      else cfg.terminals.filter { (_, v) -> this[j - 1] == v }.unzip().first.toSet()
    }

  /**
   * Checks whether a given string is valid by computing the transitive closure
   * of the matrix constructed by [toMatrix]. If the upper-right corner entry is
   * empty, the string is invalid. If the entry is S, it parses.
   *
   * See: http://www.cse.chalmers.se/~patrikj/talks/IFIP2.1ZeegseJansson_ParParseAlgebra.org
   *
   * "The following procedure specifies a recogniser: by finding the closure of
   *  I(w) one finds if w is parsable, but not the corresponding parse tree.
   *  However, one can obtain a proper parser by using sets of parse trees
   *  (instead of non-terminals) and extending (·) to combine parse trees."
   *
   * Taken from: https://arxiv.org/pdf/1601.07724.pdf#page=3
   */

  fun CFG.isValid(
    s: String = "",
    tokens: List<String> = s.split(" "),
    matrix: FreeMatrix<Set<String>> = tokens.toMatrix(this)
      .also { println("Initial configuration:\n$it\n") }
  ) = matrix
// Not good, because multiplication is not associative?
//  .let { W -> W + (W * W) + (W * W * W) + (W * W * W * W) }
// Valiant's (1975) original definition produces all bracketings:
//  .let { W -> W + W * W + W * (W * W) + (W * W) * W + (W * W) * (W * W) /*...*/ }
// Bernardy and Jansson uses the smallest solution to: C = W + C * C
  .seekFixpoint { it + it * it }
    .also { println("Final configuration:\n$it\n") }[0].last()
    .isNotEmpty()

  tailrec fun <T: FreeMatrix<S>, S> T.seekFixpoint(i: Int = 0, op: (T) -> T): T =
    if (this.also { println("Iteration $i.)\n$it\n") } == op(this)) this
    else op(this).seekFixpoint(i + 1, op)

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
    """.trimIndent())

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
    """.trimIndent())

    assertTrue(cfg.isValid(tokens = "()(()())()".map { it.toString() }))
    assertFalse(cfg.isValid(tokens = "()(()()()".map { it.toString() }))
    assertTrue(cfg.isValid(tokens = "()(())".map { it.toString() }))
  }
}