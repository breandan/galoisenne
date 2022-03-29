package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.tensor.*
import ai.hypergraph.kaliningraph.types.*

typealias Production = Π2<String, List<String>>
typealias CFL = Set<Production>

val Production.LHS: String get() = first
val Production.RHS: List<String> get() = second
val CFL.symbols: Set<String> by cache { variables + alphabet }
val CFL.alphabet: Set<String> by cache { terminals.flatMap { it.RHS }.toSet() }
val CFL.variables: Set<String> by cache { map { it.LHS }.toSet() }
val CFL.nonterminals: Set<Production> by cache { filter { it !in terminals } }
val CFL.bimap: BiMap by cache { BiMap(this) }
val CFL.terminals: Set<Production> by cache {
  filter { it.RHS.size == 1 && it.RHS[0] !in variables }.toSet()
}
val CFL.normalForm: CFL by cache { normalize() }

// Maps variables to expansions and expansions to variables in a Grammar
class BiMap(cfl: CFL) {
  val L2RHS = cfl.groupBy({ it.LHS }, { it.RHS }).mapValues { it.value.toSet() }
  val R2LHS = cfl.groupBy({ it.RHS }, { it.LHS }).mapValues { it.value.toSet() }
  operator fun get(p: List<String>): Set<String> = R2LHS[p] ?: emptySet()
  operator fun get(p: String): Set<List<String>> = L2RHS[p] ?: emptySet()
}

fun CFL.toString() = joinToString("\n") { it.LHS + " -> " + it.RHS.joinToString(" ") }

/*
 * Takes a grammar and a partially complete string where '_' denotes holes, and
 * returns a set of completed strings consistent with that grammar. Naive search
 * over all holes takes O(|Σ|^n) where n is the number of holes.
 */

fun String.solve(
  cfl: CFL,
  fillers: Set<String> = cfl.alphabet,
  numSamples: Int = 10
): Set<String> =
  if ("_" !in this) if (matches(cfl)) setOf(this) else emptySet()
  else (fillers * holeIndices()).shuffled().asSequence()
    .flatMap { (s, i) -> replaceHole(i, s).solve(cfl, fillers) }
    .take(numSamples).toSet()

fun String.holeIndices() =
  mapIndexedNotNull { i, c -> if(c == '_') i else null }.toSet()

fun String.replaceHole(idx: Int, with: String): String =
  mapIndexed { i, c -> if(i == idx) with else "$c" }.joinToString("")

fun String.matches(cfl: String): Boolean = matches(cfl.validate().parseCFL())
fun String.matches(cfl: CFL): Boolean = cfl.isValid(this)

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

fun makeAlgebra(cfl: CFL): Ring<Set<String>> =
  Ring.of(// Not a proper ring, but close enough.
    // 0 = ∅
    nil = setOf(),
    // x + y = x ∪ y
    plus = { x, y -> x union y },
    // x · y = { A0 | A1 ∈ x, A2 ∈ y, (A0 -> A1 A2) ∈ P }
    times = { x, y -> cfl.join(x, y) }
  )

private fun CFL.join(l: Set<String>, r: Set<String>): Set<String> =
  (l * r).flatMap { bimap[it.toList()] }.toSet()

// Converts tokens to UT matrix via constructor: σ_i = { A | (A -> w[i]) ∈ P }
private fun CFL.toMatrix(str: List<String>): FreeMatrix<Set<String>> =
  FreeMatrix(makeAlgebra(this), str.size + 1) { i, j ->
    if (i + 1 != j) emptySet() else bimap[listOf(str[j - 1])]
  }

/**
 * Checks whether a given string is valid by computing the transitive closure
 * of the matrix constructed by [toMatrix]. If the upper-right corner entry is
 * empty, the string is invalid. If the entry is S, it parses.
 */

fun CFL.isValid(str: String): Boolean =
  str.split(" ").let { if (it.size == 1) str.map { "$it" } else it }
    .filter(String::isNotBlank).let(::isValid)

fun CFL.isValid(
  tokens: List<String>,
  matrix: FreeMatrix<Set<String>> = toMatrix(tokens).also {
    println("Checking input string (length=${it.numRows}) = $tokens")
    println("Initial configuration:\n$it\n")
  }
): Boolean = matrix.seekFixpoint { it + it * it }
  .also { println("Final configuration:\n$it\n") }[0].last()
  .let { START_SYMBOL in it }

private val freshNames: Set<String> = ('A'..'Z').map { "$it" }.toSet()
  .let { it + (it * it).map { (a, b) -> a + b } }.toSet()

fun String.parseCFL(): CFL =
  lines().filter(String::isNotBlank).map { line ->
    val prod = line.split("->").map { it.trim() }
    if (2 == prod.size && " " !in prod[0]) prod[0] to prod[1].split(" ")
    else throw Exception("Invalid production: $line")
  }.toSet().normalForm

fun CFLCFL(names: Map<String, String>) = """
    CFL -> PRD | CFL ::NL:: CFL
    PRD -> VAR ::= RHS
    VAR -> ${names.values.joinToString(" | ")}
    RHS -> VAR | RHS RHS | RHS ::OR:: RHS
  """.parseCFL()

fun String.validate(
  presets: Map<String, String> = mapOf("|" to "::OR::", "->" to "::="),
  space: Regex = Regex("\\s+"),
  nameDict: Map<String, String> = split(space)
    .filter { it.isNotBlank() && it !in presets }.toSet()
    .zip(freshNames.filter { it !in this }).toMap(),
  names: Map<String, String> = (presets + nameDict)
): String = lines().filter(String::isNotBlank).joinToString(" ::NL:: ")
  .split(space).filter(String::isNotBlank).joinToString(" ") { names[it] ?: it }
  .let { println(it); if (CFLCFL(names).isValid(it)) this else throw Exception("!CFL: $it") }

// http://firsov.ee/cert-norm/cfg-norm.pdf
// https://www.cs.rit.edu/~jmg/courses/cs380/20051/slides/7-1-chomsky.pdf
private fun CFL.normalize(): CFL =
  addGlobalStartSymbol().expandOr().elimVarUnitProds()
    .refactorRHS().terminalsToUnitProds().removeUselessSymbols()

val START_SYMBOL = "START"

private fun CFL.addGlobalStartSymbol() =
  this + variables.map { START_SYMBOL to listOf(it) }

// Expands RHS | productions, e.g., (A -> B | C) -> (A -> B, A -> C)
private fun CFL.expandOr(): CFL =
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

private fun CFL.removeUselessSymbols(
  generating: Set<String> = generatingSymbols(),
  reachable: Set<String> = reachableSymbols(),
): CFL = filter { (s, _) -> s in generating && s in reachable }

private fun CFL.reachableSymbols(
  src: List<String> = listOf(START_SYMBOL),
  reachables: Set<String> = src.toSet()
): Set<String> = if (src.isEmpty()) reachables else filter { it.LHS in src }
  .flatMap { (_, rhs) -> rhs.filter { it in variables && it !in reachables } }
  .let { reachableSymbols(it, reachables + it) }

private fun CFL.generatingSymbols(
  from: List<String> = terminals.flatMap { it.RHS },
  generating: Set<String> = from.toSet()
): Set<String> = if (from.isEmpty()) generating
else filter { it.LHS !in generating && it.RHS.all { it in generating } }
  .map { it.LHS }.let { generatingSymbols(it, generating + it) }

/* Drops variable unit productions, for example:
 * Initial grammar: (A -> B, B -> c, B -> d) ->
 * After expansion: (A -> B, A -> c, A -> d, B -> c, B -> d) ->
 * After elimination: (A -> c, A -> d, B -> c, B -> D)
 */
private tailrec fun CFL.elimVarUnitProds(
  toVisit: Set<String> = variables,
  vars: Set<String> = variables,
  toElim: String? = toVisit.firstOrNull()
): CFL {
  fun Production.isVariableUnitProd() = RHS.size == 1 && RHS[0] in vars
  if (toElim == null) return filter { !it.isVariableUnitProd() }
  val varsThatMapToMe =
    filter { it.RHS.size == 1 && it.RHS[0] == toElim }.map { it.LHS }.toSet()
  val thingsIMapTo = filter { it.LHS == toElim }.map { it.RHS }.toSet()
  return (varsThatMapToMe * thingsIMapTo).fold(this) { g, p -> g + p }
    .elimVarUnitProds(toVisit.drop(1).toSet(), vars)
}

// Refactors long productions, e.g., (A -> BCD) -> (A -> BE, E -> CD)
private tailrec fun CFL.refactorRHS(): CFL {
  val longProd = firstOrNull { it.RHS.size > 2 } ?: return this
  val freshName = freshNames.firstOrNull { it !in symbols }!!
  val newProd = freshName to longProd.RHS.takeLast(2)
  val shortProd = longProd.LHS to (longProd.RHS.dropLast(2) + freshName)
  val newGrammar = this - longProd + shortProd + newProd
  return if (this == newGrammar) this else newGrammar.refactorRHS()
}

// Replaces terminals in non-unit productions, e.g., (A -> bC) -> (A -> BC, B -> b)
private tailrec fun CFL.terminalsToUnitProds(): CFL {
  val mixProd = nonterminals.firstOrNull { it.RHS.any { it !in variables } } ?: return this
  val freshName = freshNames.firstOrNull { it !in symbols }!!
  val termIdx = mixProd.RHS.indexOfFirst { it !in variables }
  val freshRHS = mixProd.RHS.toMutableList().also { it[termIdx] = freshName }
  val newProd = freshName to listOf(mixProd.RHS[termIdx])
  val newGrammar = this - mixProd + (mixProd.LHS to freshRHS) + newProd
  return if (this == newGrammar) this else newGrammar.terminalsToUnitProds()
}