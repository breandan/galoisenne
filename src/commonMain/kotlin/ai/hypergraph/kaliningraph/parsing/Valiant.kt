package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.tensor.FreeMatrix
import ai.hypergraph.kaliningraph.types.*

typealias Production = Π2<String, List<String>>
typealias Grammar = Set<Production>

class CFL(
  val grammar: Grammar,
  val normalForm: Grammar = grammar.normalize()
): Grammar by normalForm {
  constructor(vararg productions: String): this(
    productions.filter { it.isNotBlank() }.map {
      it.split("->").map { it.trim() }.let { it[0] pp it[1] }
    }.map { (k, v) -> k pp v.split(" ") }.toSet()
  )

  constructor(grammar: String): this(*grammar.lines().toTypedArray())

  val nonterminals = normalForm.filter { it.second.size == 2 }
    .map { (k, v) -> k pp (v[0] pp v[1]) }.toSet()
  val terminals = normalForm.filter { it.second.size == 1 }
    .map { (k, v) -> k pp v[0] }.toSet()
  val variables = normalForm.unzip().first.toSet()

  companion object {
    val freshNames: List<String> = (('A'..'Z') + ('0'..'9')).let { it.map { "$it" } }
      .let { it + (it.toSet() * it.toSet()).map { it.toVT().joinToString("") } }

    // http://firsov.ee/cert-norm/cfg-norm.pdf
    private fun Grammar.normalize(): Grammar =
      expandOr().elimVarUnitProds().refactorRHS().terminalsToUnitProds()

    private fun Grammar.expandOr(): Grammar =
      map { (a, b) ->
        b.fold(listOf(listOf<String>())) { acc, s ->
          if (s == "|") (acc + listOf())
          else (acc.dropLast(1) + listOf(acc.last() + s))
        }.map { a pp it }
      }.flatten().toSet()

    // Drop variable unit productions: (A -> B, B -> C, B -> D) -> (A -> C, A -> D)
    private fun Grammar.elimVarUnitProds(): Grammar {
      val variables = unzip().first.toSet()
      val upToDelete =
        firstOrNull { (_, b) -> b.size == 1 && b.first() in variables } ?: return this
      val newGrammar: Grammar = filter { it != upToDelete }.map { (a, b) ->
        val (replacement, toRepl) = upToDelete
        (if(a == toRepl.first()) replacement else a) pp b
      }.toSet().elimVarUnitProds() as Grammar
      return if (this == newGrammar) this else newGrammar
    }

    // Refactors long productions, e.g., (A -> BCD) -> (A -> BE, E -> CD)
    private fun Grammar.refactorRHS(): Grammar {
      val variables = unzip().first.toSet()
      val longProduction = firstOrNull { it.second.size > 2 } ?: return this
      val (longLHS, longRHS) = longProduction
      val freshName = freshNames.firstOrNull { it !in variables }!!
      val newProduction = freshName pp longRHS.takeLast(2)
      val shorterProduction = longLHS pp (longRHS.dropLast(2) + freshName)
      val newGrammar = (map { if(it == longProduction) shorterProduction else it } + newProduction).toSet()
      return if (this == newGrammar) this else newGrammar.refactorRHS()
    }

    private fun Grammar.terminalsToUnitProds(): Grammar {
      val variables = unzip().first.toSet()
      val mixedTermVarProd = firstOrNull {
        it.second.any { it !in variables } && it.second.any { it in variables }
      } ?: return this
      val freshName = freshNames.firstOrNull { it !in variables }!!
      val idxOfTerminal = mixedTermVarProd.second.indexOfFirst { it !in variables }
      val freshRHS = mixedTermVarProd.second.mapIndexed { i, s -> if (i == idxOfTerminal) freshName else s }
      val newProduction = freshName pp listOf(mixedTermVarProd.second[idxOfTerminal])
      val newGrammar = (map { if(it == mixedTermVarProd) (mixedTermVarProd.first pp freshRHS) else it } + newProduction).toSet()
      return if (this == newGrammar) this else newGrammar.terminalsToUnitProds()
    }
  }

  override fun toString() =
    normalForm.joinToString("\n") { (a, b) -> "$a -> ${b.joinToString(" ")}"}
}

// This is not a proper ring, but close enough.
// TODO: https://aclanthology.org/J99-4004.pdf#page=8
fun makeAlgebra(cfl: CFL): Ring<Set<String>> =
  Ring.of(
    // 0 = ∅
    nil = setOf(),
    // TODO: Seems unused, maybe find a more specific algebra?
    one = setOf(),
    // x + y = x ∪ y
    plus = { x, y -> x union y },
    // x · y = {A0 | A1 ∈ x, A2 ∈ y, (A0 -> A1 A2) ∈ P}
    times = { x, y ->
      infix fun Set<String>.join(that: Set<String>): Set<String> =
        cfl.nonterminals
          .filter { (_, A) -> A.first in this && A.second in that }
          .map { it.first }
          .toSet()

      x join y
    }
  )

// Converts tokens to UT matrix using constructor: σi = {A | (A -> w[i]) ∈ P}
fun List<String>.toMatrix(cfl: CFL): FreeMatrix<Set<String>> =
  FreeMatrix(makeAlgebra(cfl), size + 1) { i, j ->
    if (i + 1 != j) emptySet() // Enforce upper triangularity
    else cfl.terminals.filter { (_, v) -> this[j - 1] == v }
      .unzip().first.toSet()
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

fun CFL.isValid(
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