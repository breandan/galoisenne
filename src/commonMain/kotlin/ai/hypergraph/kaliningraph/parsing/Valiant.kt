package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.tensor.*
import ai.hypergraph.kaliningraph.types.*
import kotlin.properties.ReadOnlyProperty

typealias Production = Π2<String, List<String>>
typealias Grammar = Set<Production>

val Production.LHS: String get() = first
val Production.RHS: List<String> get() = second
val Grammar.symbols: Set<String>
  by lazy { variables + terminals.map { it.π2 }.flatten().toSet() }

val Grammar.variables: Set<String> by lazy { map { it.π1 }.toSet() }
val Grammar.nonterminals: Set<Production>
  by lazy { filter { it !in terminals }.toSet() }
val Grammar.terminals: Set<Production>
  by lazy {
    filter { it.RHS.size == 1 && it.RHS[0] !in variables }
      .map { (lhs, rhs) -> lhs to rhs }.toSet()
  }

fun <T> lazy(caller: Int = getCaller(), fn: Grammar.() -> T) =
  ReadOnlyProperty<Grammar, T> { grammar, _ ->
    cache.getOrPut("${grammar.hashCode()}$caller") { grammar.fn() as Any } as T
  }

fun Grammar.prettyPrint() =
  joinToString("\n") { it.LHS + " -> " + it.RHS.joinToString(" ") }

class CFL(
  val grammar: Grammar,
  val normalForm: Grammar = grammar.normalize()
): Grammar by normalForm {
  constructor(vararg productions: String): this(productions.joinToString("\n"))
  constructor(grammar: String): this(grammar.parse())

  companion object {
    val freshNames: Set<String> = ('A'..'Z').map { "$it" }
      .let { it.toSet().let { it + (it * it).map { (a, b) -> a + b } }.toSet() }

    fun String.parse() =
      lines().filter { it.isNotBlank() }.map { line ->
        val prod = line.split("->").map { it.trim() }
        if (2 == prod.size && " " !in prod[0]) prod[0] to prod[1]
        else throw Exception("Invalid production: $line")
      }.map { (k, v) -> k to v.split(" ") }.toSet()

    fun CFLCFL(names: Map<String, String>) = CFL("""
        GRAMMAR -> PROD | GRAMMAR ::NL:: GRAMMAR
        PROD -> LHS ::= RHS
        NAME -> ${names.values.joinToString(" | ")}
        LHS -> NAME
        RHS -> NAME | RHS RHS | RHS ::OR:: RHS
      """.parse())

    fun String.validate(
      presets: Map<String, String> = mapOf("|" to "::OR::", "->" to "::="),
      whitespace: Regex = Regex("\\s+"),
      nameDict: Map<String, String> = split(whitespace)
        .filter { it.isNotBlank() && it !in presets }.toSet()
        .zip(freshNames.filter { it !in this }).toMap(),
      dict: Map<String, String> = (presets + nameDict)
    ): String = lines().filter { it.isNotBlank() }.joinToString(" ::NL:: ")
      .split(whitespace).filter { it.isNotBlank() }
      .joinToString(" ") { dict[it] ?: it }
      .let { if (CFLCFL(dict).isValid(it)) this else throw Exception("Bad CFL: $it") }

    // http://firsov.ee/cert-norm/cfg-norm.pdf
    // https://www.cs.rit.edu/~jmg/courses/cs380/20051/slides/7-1-chomsky.pdf
    private fun Grammar.normalize(): Grammar =
      addStartSymbol().expandOr().elimVarUnitProds()
        .refactorRHS().terminalsToUnitProds().removeUselessSymbols()

    val START_SYMBOL = "START"

    private fun Grammar.addStartSymbol() =
      this + variables.map { START_SYMBOL to listOf(it) }.toSet()

    // Expands RHS | productions, e.g., (A -> B | C) -> (A -> B, A -> C)
    fun Grammar.expandOr(): Grammar =
      flatMap { prod ->
        prod.RHS.fold(listOf(listOf<String>())) { acc, s ->
          if (s == "|") (acc + listOf(listOf()))
          else (acc.dropLast(1) + listOf(acc.last() + s))
        }.map { prod.LHS to it }
      }.toSet()

    /**
     * TODO: Eliminate ε productions
     *  - Determine nullable variables, i.e., those which contain ε on the RHS
     *  - For each production omit every possible subset of nullable variables,
     *      e.g., (P -> AxB, A -> ε, B -> ε) -> (P -> xB, P -> Ax, P -> x)
     *  - Delete all productions with an empty RHS
     */

    /**
     * Eliminate all non-genereating and unreachable symbols.
     *
     * All terminal-producing symbols are generating.
     * If A -> [..] and all symbols in [..] are generating, then A is generating
     * No other symbols are generating.
     *
     * START is reachable.
     * If S -> [..] is reachable, then all variables in [..] are reachable.
     * No other symbols are reachable.
     *
     * A useful symbol is both generating and reachable.
     */

    private fun Grammar.removeUselessSymbols(
      generating: Set<String> = generatingSymbols(),
      reachable: Set<String> = reachableSymbols(),
    ): Grammar = filter { (s, _) -> s in generating && s in reachable }.toSet()

    private fun Grammar.reachableSymbols(
      src: Set<String> = setOf(START_SYMBOL),
      reachables: Set<String> = src
    ): Set<String> = if (src.isEmpty()) reachables else filter { it.LHS in src }
      .map { (_, rhs) -> rhs.filter { it in variables && it !in reachables } }
      .flatten().toSet().let { reachableSymbols(it, reachables + it) }

    private fun Grammar.generatingSymbols(
      from: Set<String> = terminals.map { it.π2 }.flatten().toSet(),
      generating: Set<String> = from
    ): Set<String> = if (from.isEmpty()) generating
    else filter { it.LHS !in generating && it.RHS.all { it in generating } }
      .map { it.π1 }.toSet().let { generatingSymbols(it, generating + it) }

    /* Drops variable unit productions, for example:
     * Initial grammar: (A -> B, B -> c, B -> d) ->
     * After expansion: (A -> B, A -> c, A -> d, B -> c, B -> d) ->
     * After elimination: (A -> c, A -> d, B -> c, B -> D)
     */
    private tailrec fun Grammar.elimVarUnitProds(
      toVisit: Set<String> = variables,
      vars: Set<String> = variables,
      toElim: String? = toVisit.firstOrNull()
    ): Grammar {
      fun Production.isVariableUnitProd() = RHS.size == 1 && RHS[0] in vars
      if (toElim == null) return filter { !it.isVariableUnitProd() }.toSet()
      val varsThatMapToMe =
        filter { it.RHS.size == 1 && it.RHS[0] == toElim }.map { it.π1 }.toSet()
      val thingsIMapTo = filter { it.LHS == toElim }.map { it.π2 }.toSet()
      return (varsThatMapToMe * thingsIMapTo).fold(this) { g, p -> g + p }
        .elimVarUnitProds(toVisit.drop(1).toSet(), vars)
    }

    // Refactors long productions, e.g., (A -> BCD) -> (A -> BE, E -> CD)
    private tailrec fun Grammar.refactorRHS(): Grammar {
      val longProd = firstOrNull { it.RHS.size > 2 } ?: return this
      val freshName = freshNames.firstOrNull { it !in symbols }!!
      val newProd = freshName to longProd.RHS.takeLast(2)
      val shortProd = longProd.LHS to (longProd.RHS.dropLast(2) + freshName)
      val newGrammar = (map { if(it == longProd) shortProd else it } + newProd).toSet()
      return if (this == newGrammar) this else newGrammar.refactorRHS()
    }

    // Replaces terminals in non-unit productions, e.g., (A -> bC) -> (A -> BC, B -> b)
    private tailrec fun Grammar.terminalsToUnitProds(): Grammar {
      val mixedProd = nonterminals.firstOrNull { it.RHS.any { it !in variables } } ?: return this
      val freshName = freshNames.firstOrNull { it !in symbols }!!
      val idxOfTerminal = mixedProd.RHS.indexOfFirst { it !in variables }
      val freshRHS = mixedProd.RHS.mapIndexed { i, s -> if (i == idxOfTerminal) freshName else s }
      val newProduction = freshName to listOf(mixedProd.RHS[idxOfTerminal])
      val newGrammar = (filter { it != mixedProd } + (mixedProd.LHS to freshRHS) + newProduction).toSet()
      return if (this == newGrammar) this else newGrammar.terminalsToUnitProds()
    }
  }

  /**
   * Checks whether a given string is valid by computing the transitive closure
   * of the matrix constructed by [toMatrix]. If the upper-right corner entry is
   * empty, the string is invalid. If the entry is S, it parses.
   */

  fun isValid(
    s: String = "",
    tokens: List<String> = s.split(" ").filter { it.isNotBlank() },
    matrix: FreeMatrix<Set<String>> = tokens.toMatrix().also {
      println("Checking input string (length=${it.numRows}) = $tokens")
      println("Initial configuration:\n$it\n")
    }
  ) = matrix
// Not good, because multiplication is not associative?
//  .let { W -> W + (W * W) + (W * W * W) + (W * W * W * W) }
// Valiant's (1975) original definition produces all bracketings:
//  .let { W -> W + W * W + W * (W * W) + (W * W) * W + (W * W) * (W * W) /*...*/ }
// Bernardy and Jansson uses the smallest solution to: C = W + C * C
    .seekFixpoint { it + it * it }
    .also { println("Final configuration:\n$it\n") }[0].last()
    .isNotEmpty()

  /* See: http://www.cse.chalmers.se/~patrikj/talks/IFIP2.1ZeegseJansson_ParParseAlgebra.org
   *
   * "The following procedure specifies a recogniser: by finding the closure of
   *  I(w) one finds if w is parsable, but not the corresponding parse tree.
   *  However, one can obtain a proper parser by using sets of parse trees
   *  (instead of non-terminals) and extending (·) to combine parse trees."
   *
   * Taken from: https://arxiv.org/pdf/1601.07724.pdf#page=3
   *
   * TODO: Other algebras? https://aclanthology.org/J99-4004.pdf#page=8
   */

  fun makeAlgebra(): Ring<Set<String>> = // Not a proper ring, but close enough.
    Ring.of(
      // 0 = ∅
      nil = setOf(),
      // TODO: Seems unused, maybe find a more specific algebra?
      one = setOf(),
      // x + y = x ∪ y
      plus = { x, y -> x union y },
      // x · y = {A0 | A1 ∈ x, A2 ∈ y, (A0 -> A1 A2) ∈ P}
      times = { x, y -> x join y }
    )

  private infix fun Set<String>.join(that: Set<String>): Set<String> =
    nonterminals.filter { (_, r) -> r[0] in this && r[1] in that }.map { it.π1 }.toSet()

  // Converts tokens to UT matrix using constructor: σi = {A | (A -> w[i]) ∈ P}
  private fun List<String>.toMatrix(): FreeMatrix<Set<String>> =
    FreeMatrix(makeAlgebra(), size + 1) { i, j ->
      if (i + 1 != j) emptySet() // Enforce upper triangularity
      else terminals.filter { (_, v) -> this[j - 1] == v[0] }.map { it.π1 }.toSet()
    }

  override fun toString() = normalForm.prettyPrint()
}