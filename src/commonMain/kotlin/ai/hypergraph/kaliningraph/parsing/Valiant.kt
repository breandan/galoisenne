package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.parsing.CFG.Companion.expandOr
import ai.hypergraph.kaliningraph.tensor.FreeMatrix
import ai.hypergraph.kaliningraph.types.*
import kotlin.math.exp

class CFG(
  val grammar: List<Π2<String, List<String>>>,
  val normalForm: List<Π2<String, List<String>>> = grammar.normalize()
): List<Π2<String, List<String>>> by normalForm {
  constructor(vararg productions: String): this(
    productions.filter { it.isNotBlank() }.map {
      it.split("->").map { it.trim() }.let { it[0] pp it[1] }
    }.map { (k, v) -> k pp v.split(" ") }
  )

  constructor(grammar: String): this(*grammar.lines().toTypedArray())

  val nonterminals = normalForm.filter { it.second.size == 2 }
    .map { (k, v) -> k pp (v[0] pp v[1]) }.toSet()
  val terminals = normalForm.filter { it.second.size == 1 }
    .map { (k, v) -> k pp v[0] }.toSet()

  companion object {
    val freshNames: List<String> = (('A'..'Z') + ('0'..'9')).let { it.map { "$it" } }
      .let { it + (it.toSet() * it.toSet()).map { it.toVT().joinToString("") } }

    private fun <T: Π2<String, List<String>>> List<T>.normalize(): List<T> =
      expandOr().elimVarUnitProductions().refactorRHS().terminalsToUnitProds()

    private fun <T: Π2<String, List<String>>> List<T>.expandOr(): List<T> =
      map { (a, b) ->
        b.fold(listOf(listOf<String>())) { acc, s ->
          if (s == "|") (acc + listOf())
          else (acc.dropLast(1) + listOf(acc.last() + s))
        }.map { a pp it }
      }.flatten() as List<T>

    // Drop variable unit productions: (A -> B, B -> C, B -> D) -> (A -> C, A -> D)
    private fun <T: Π2<String, List<String>>> List<T>.elimVarUnitProductions(
      previouslyDeleted: List<String> = emptyList() // TODO: Is it needed?
    ): List<T> {
      val variables = unzip().first.toSet()
      val upToDelete =
        firstOrNull { (_, b) -> b.size == 1 && b.first() in variables } ?: return this
      val newGrammar: List<T> = filter { it != upToDelete }.map { (a, b) ->
        val (replacement, toRepl) = upToDelete
        (if(a == toRepl.first()) replacement else a) pp b
      }.elimVarUnitProductions() as List<T>
      return if (this == newGrammar) this else newGrammar
    }

    // Refactors long productions, e.g., (A -> BCD) -> (A -> BE, E -> CD)
    private fun <T: Π2<String, List<String>>> List<T>.refactorRHS(): List<T> {
      val variables = unzip().first.toSet()
      val longProduction = firstOrNull { it.second.size > 2 } ?: return this
      val (longLHS, longRHS) = longProduction
      val freshName = freshNames.firstOrNull { it !in variables }!!
      val newProduction = freshName pp longRHS.takeLast(2)
      val shorterProduction = longLHS pp (longRHS.dropLast(2) + freshName)
      val newGrammar = (map { if(it == longProduction) shorterProduction else it } + newProduction) as List<T>
      return if (this == newGrammar) this else newGrammar.refactorRHS()
    }

    private fun <T: Π2<String, List<String>>> List<T>.terminalsToUnitProds(): List<T> {
      val variables = unzip().first.toSet()
      val hasTerminalOnRHS = firstOrNull {
        it.second.any { it !in variables } && it.second.any { it in variables }
      } ?: return this
      val freshName = freshNames.firstOrNull { it !in variables }!!
      val idxOfTerminal = hasTerminalOnRHS.second.indexOfFirst { it !in variables }
      val freshRHS = hasTerminalOnRHS.second.mapIndexed { i, s -> if (i == idxOfTerminal) freshName else s }
      val newProduction = freshName pp listOf(hasTerminalOnRHS.second[idxOfTerminal])
      val newGrammar = (map { if(it == hasTerminalOnRHS) (hasTerminalOnRHS.first pp freshRHS) else it } + newProduction) as List<T>
      return if (this == newGrammar) this else newGrammar.terminalsToUnitProds()
    }
  }

  override fun toString() =
    normalForm.joinToString("\n") { (a, b) -> "$a -> ${b.joinToString(" ")}"}
}

// This is not a proper ring, but close enough.
// TODO: https://aclanthology.org/J99-4004.pdf#page=8
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
      infix fun Set<String>.join(that: Set<String>): Set<String> =
        cfg.nonterminals
          .filter { (_, A) -> A.first in this && A.second in that }
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
