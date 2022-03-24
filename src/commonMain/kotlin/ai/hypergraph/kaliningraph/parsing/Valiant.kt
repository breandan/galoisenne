package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.tensor.*
import ai.hypergraph.kaliningraph.types.*

typealias Production = Π2<String, List<String>>
val Production.LHS: String get() = first
val Production.RHS: List<String> get() = second
typealias Grammar = Set<Production>
val Grammar.variables: Set<String> get() = unzip().first.toSet()

class CFL(
  val grammar: Grammar,
  val normalForm: Grammar = grammar.normalize()
): Grammar by normalForm {
  constructor(vararg productions: String): this(
    productions.filter { it.isNotBlank() }.map { line ->
      val prod = line.split("->").map { it.trim() }
      if (2 == prod.size && " " !in prod[0]) prod[0] to prod[1]
      else throw Exception("Invalid production: $line")
    }.map { (k, v) -> k to v.split(" ") }.toSet()
  )

  constructor(grammar: String): this(*grammar.lines().toTypedArray())

  val nonterminals = normalForm.filter { it.RHS.size == 2 }
    .map { (lhs, rhs) -> lhs to (rhs[0] to rhs[1]) }.toSet()
  val terminals = normalForm.filter { it.RHS.size == 1 }
    .map { (lhs, rhs) -> lhs to rhs[0] }.toSet()
  val variables = normalForm.variables

  companion object {
    val freshNames: List<String> = (('A'..'Z') + ('0'..'9')).let { it.map { "$it" } }
      .let { it + (it.toSet() * it.toSet()).map { it.toVT().joinToString("") } }

    // http://firsov.ee/cert-norm/cfg-norm.pdf
    private fun Grammar.normalize(): Grammar =
      expandOr().elimVarUnitProds().refactorRHS().terminalsToUnitProds()

    // Expands RHS | productions, e.g., (A -> B | C) -> (A -> B, A -> C)
    private fun Grammar.expandOr(): Grammar =
      flatMap { prod ->
        prod.RHS.fold(listOf(listOf<String>())) { acc, s ->
          if (s == "|") (acc + listOf())
          else (acc.dropLast(1) + listOf(acc.last() + s))
        }.map { prod.LHS to it }
      }.toSet()

    /*
    TODO: Eliminate ε productions
        Determine nullable variables, i.e., those which contain ε on the RHS
        For each production omit every possible subset of nullable variables,
            e.g., (P -> AxB, A -> ε, B -> ε) -> (P -> xB, P -> Ax, P -> x)
        Delete all productions with an empty RHS
     */

    // Drop variable unit productions: (A -> B, B -> C, B -> D) -> (A -> C, A -> D)
    private fun Grammar.elimVarUnitProds(): Grammar {
      val unitProdToDrop =
        firstOrNull { it.RHS.size == 1 && it.RHS[0] in variables } ?: return this
      val newGrammar: Grammar = filter { it != unitProdToDrop }.map { (a, b) ->
        (if(a == unitProdToDrop.RHS[0]) unitProdToDrop.LHS else a) to b
      }.toSet()
      return if (this == newGrammar) this else newGrammar.elimVarUnitProds()
    }

    // Refactors long productions, e.g., (A -> BCD) -> (A -> BE, E -> CD)
    private fun Grammar.refactorRHS(): Grammar {
      val longProd = firstOrNull { it.RHS.size > 2 } ?: return this
      val freshName = freshNames.firstOrNull { it !in variables }!!
      val newProd = freshName to longProd.RHS.takeLast(2)
      val shortProd = longProd.LHS to (longProd.RHS.dropLast(2) + freshName)
      val newGrammar = (map { if(it == longProd) shortProd else it } + newProd).toSet()
      return if (this == newGrammar) this else newGrammar.refactorRHS()
    }

    // Replaces terminals in non-unit productions, e.g., (A -> bC) -> (A -> BC, B -> b)
    private fun Grammar.terminalsToUnitProds(): Grammar {
      val mixedProd = firstOrNull {
        it.RHS.any { it !in variables } && it.RHS.any { it in variables }
      } ?: return this
      val freshName = freshNames.firstOrNull { it !in variables }!!
      val idxOfTerminal = mixedProd.RHS.indexOfFirst { it !in variables }
      val freshRHS = mixedProd.RHS.mapIndexed { i, s -> if (i == idxOfTerminal) freshName else s }
      val newProduction = freshName to listOf(mixedProd.RHS[idxOfTerminal])
      val newGrammar = (map { if(it == mixedProd) (mixedProd.LHS to freshRHS) else it } + newProduction).toSet()
      return if (this == newGrammar) this else newGrammar.terminalsToUnitProds()
    }
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

  fun isValid(
    s: String = "",
    tokens: List<String> = s.split(" "),
    matrix: FreeMatrix<Set<String>> = tokens.toMatrix()
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

  // This is not a proper ring, but close enough.
// TODO: https://aclanthology.org/J99-4004.pdf#page=8
  fun makeAlgebra(): Ring<Set<String>> =
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
          nonterminals
            .filter { (_, A) -> A.first in this && A.second in that }
            .map { it.first }
            .toSet()

        x join y
      }
    )

  // Converts tokens to UT matrix using constructor: σi = {A | (A -> w[i]) ∈ P}
  fun List<String>.toMatrix(): FreeMatrix<Set<String>> =
    FreeMatrix(makeAlgebra(), size + 1) { i, j ->
      if (i + 1 != j) emptySet() // Enforce upper triangularity
      else terminals.filter { (_, v) -> this[j - 1] == v }
        .unzip().first.toSet()
    }

  override fun toString() =
    normalForm.joinToString("\n") { (a, b) -> "$a -> ${b.joinToString(" ")}"}
}