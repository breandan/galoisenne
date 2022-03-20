package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.tensor.FreeMatrix
import org.junit.jupiter.api.Test
import ai.hypergraph.kaliningraph.types.*
import org.junit.jupiter.api.Assertions.assertTrue

class Valiant {
// http://www.cse.chalmers.se/~patrikj/talks/IFIP2.1ZeegseJansson_ParParseAlgebra.org

// The above procedure specifies a recogniser: by finding the closure of I(w)
// one finds if w is parsable, but not the corresponding parse tree. However,
// one can obtain a proper parser by using sets of parse trees (instead of non-
// terminals) and extending (·) to combine parse trees.

// https://arxiv.org/pdf/1601.07724.pdf#page=3

  class CFG(
    val grammar: List<Π2<String, List<String>>>
  ): List<Π2<String, List<String>>> by grammar {
    constructor(vararg productions: String): this(
      productions.map {
        it.split("::=").map { it.trim() }.let { it[0] pp it[1] }
      }.map { (k, v) -> k pp v.split(" ") }
    )

    constructor(grammar: String): this(*grammar.lines().toTypedArray())
  }

  fun makeAlgebra(cfg: CFG): Ring<Set<String>> =
    Ring.of(
      // 0 = ∅
      nil = setOf(),
      // TODO: Unneeded, find a more suitable algebra?
      one = setOf(),
      // x + y = x ∪ y
      plus = { x, y -> x union y },
      // x · y = {A0 | A1 ∈ x, A2 ∈ y, (A0 ::= A1 A2) ∈ P}
      times = { x, y ->
        cfg.filter { (_, A) -> A.size == 2 && A[0] in x && A[1] in y }
          .map { it.first }.toSet()
      }
    )

// σi = {A | (A ::= w[i]) ∈ P}

  fun List<String>.toMatrix(cfg: CFG): FreeMatrix<Set<String>> =
    FreeMatrix(makeAlgebra(cfg), size) { i, j ->
      if (i != j) emptySet() // Enforce upper triangularity
      else cfg.filter { (_, v) -> v.size == 1 && this[j] == v[0] }.unzip().first.toSet()
    }

  fun CFG.isValid(
    s: String, 
    tokens: List<String> = s.split(" "),
    matrix: FreeMatrix<Set<String>> = tokens.toMatrix(this).also { println(it) }
  ) = matrix
// Not good, because multiplication is not associative?
    .let { W -> W + (W * W) + (W * W * W) + (W * W * W * W) }
// Valiant's (1975) original definition producing all bracketings:
//  .let { W -> W + W * W + W * (W * W) + (W * W) * W + (W * W) * (W * W) }
// Bernardy and Jansson uses the smallest solution to the following equation:
//  .seekFixpoint { it + it * it }
    .also { println(it) }[0, tokens.size - 1]
    .isNotEmpty()

  tailrec fun <T: FreeMatrix<S>, S> T.seekFixpoint(op: (T) -> T): T =
    if (this.also { println(it.toString()) } == op(this)) this else op(this).seekFixpoint(op)

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.Valiant.testSimpleGrammar"
*/

//  @Test
  fun testSimpleGrammar() {
    val cfg = CFG(
        "   S ::= NP VP ", // -- a Noun Phrase + a Verb Phrase
        "  VP ::= eats  ", //
        "  VP ::= VP PP ", // -- a VP can end with a PP or an NP
        "  VP ::= VP NP ", //
        "  PP ::= P NP  ", // -- Preposition Phrase ("with a fork")
        "   P ::= with  ", // -- Proposition(s)
        "  NP ::= she   ", //
        "  NP ::= Det N ", // -- Noun Phrase ("a fish")
        "  NP ::= NP PP ", // -- optional "a fish with a fork"
        "   N ::= fish  ", // -- Nouns
        "   N ::= fork  ", //
        " Det ::= a     ", // -- Determiner
    )

    assertTrue(cfg.isValid("she eats fish with a fork"))
  }
}