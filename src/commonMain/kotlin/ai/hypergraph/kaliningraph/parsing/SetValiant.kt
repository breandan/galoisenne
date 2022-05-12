package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.automata.*
import ai.hypergraph.kaliningraph.sampling.*
import ai.hypergraph.kaliningraph.tensor.*
import ai.hypergraph.kaliningraph.types.*
import kotlin.math.*

typealias Production = Π2<String, List<String>>
typealias CFG = Set<Production>

val Production.LHS: String get() = first
val Production.RHS: List<String> get() =
  second.let { if(it.size == 1) it.map { it.stripEscapeChars() } else it }
val CFG.symbols: Set<String> by cache { variables + alphabet }
val CFG.alphabet: Set<String> by cache { terminals.flatMap { it.RHS }.toSet() }
val CFG.variables: Set<String> by cache { map { it.LHS }.toSet() }
val CFG.nonterminals: Set<Production> by cache { filter { it !in terminals } }
val CFG.bimap: BiMap by cache { BiMap(this) }
val CFG.terminals: Set<Production>
  by cache { filter { it.RHS.size == 1 && it.RHS[0] !in variables }.toSet() }
val CFG.normalForm: CFG by cache { normalize() }

// Maps variables to expansions and expansions to variables in a Grammar
class BiMap(CFG: CFG) {
  val L2RHS = CFG.groupBy({ it.LHS }, { it.RHS }).mapValues { it.value.toSet() }
  val R2LHS = CFG.groupBy({ it.RHS }, { it.LHS }).mapValues { it.value.toSet() }
  operator fun get(p: List<String>): Set<String> = R2LHS[p] ?: emptySet()
  operator fun get(p: String): Set<List<String>> = L2RHS[p] ?: emptySet()
}

fun CFG.prettyPrint(cols: Int = 4): String =
  sortedWith(compareBy({ -it.RHS.size }, { -it.LHS.length }, { it.LHS }))
    .map { it.LHS + " -> " + it.RHS.joinToString(" ") }.let { productions ->
      val (cols, rows) = cols to ceil(productions.size.toDouble() / cols).toInt()
      val padded = productions + List(cols * rows - productions.size) { "" }
      FreeMatrix(cols, rows, padded).transpose.toString()
    }

/*
 * Takes a grammar and a partially complete string where '_' denotes holes, and
 * returns a set of completed strings consistent with that grammar. Naive search
 * over all holes takes O(|Σ|^n) where n is the number of holes.
 */

fun String.solve(CFG: CFG, fillers: Set<String> = CFG.alphabet): Sequence<String> =
  genCandidates(CFG, fillers).filter {
    (it.matches(CFG) to it.dyckCheck()).also { (valiant, stack) ->
      // Should never see either of these statements if we did our job correctly
      if (!valiant && stack) println("Valiant under-approximated Stack: $it")
      else if (valiant && !stack) println("Valiant over-approximated Stack: $it")
    }.first
  }

val HOLE_MARKER = '_'

fun String.genCandidates(CFG: CFG, fillers: Set<String> = CFG.alphabet) =
  MDSamplerWithoutReplacement(fillers, count { it == HOLE_MARKER }).map {
    fold("" to it) { (a, b), c ->
      if (c == '_') (a + b.first()) to b.drop(1) else (a + c) to b
    }.first
  }

fun String.matches(cfg: String): Boolean = matches(cfg.validate().parseCFG())
fun String.matches(CFG: CFG): Boolean = CFG.isValid(this)
fun String.parse(s: String): Tree? = parseCFG().parse(s)
fun CFG.parse(s: String): Tree? = parseForest(s).firstOrNull()

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

fun makeAlgebra(CFG: CFG): Ring<Set<Tree>> =
  Ring.of(// Not a proper ring, but close enough.
    // 0 = ∅
    nil = setOf(),
    // x + y = x ∪ y
    plus = { x, y -> x union y },
    // x · y = { A0 | A1 ∈ x, A2 ∈ y, (A0 -> A1 A2) ∈ P }
    times = { x, y -> CFG.treeJoin(x, y) }
  )

fun CFG.treeJoin(left: Set<Tree>, right: Set<Tree>): Set<Tree> =
  (left * right).flatMap { (left, right) ->
    bimap[listOf(left.root, right.root)]
        .map { Tree(it, null, left, right) }
  }.toSet()

fun CFG.setJoin(left: Set<String>, right: Set<String>): Set<String> =
  (left * right).flatMap { bimap[it.toList()] }.toSet()

// Converts tokens to UT matrix via constructor: σ_i = { A | (A -> w[i]) ∈ P }
fun CFG.initialMatrix(str: List<String>): FreeMatrix<Set<Tree>> =
  FreeMatrix(makeAlgebra(this), str.size + 1) { i, j ->
    if (i + 1 != j) emptySet()
    else bimap[listOf(str[j - 1])].map { Tree(it, str[j - 1]) }.toSet()
  }

/*
Do we need Lee to do [en/de]coding? https://arxiv.org/pdf/cs/0112018.pdf#page=10
It seems Valiant gives a reduction from CFL parsing to BMM, i.e., CFL→BMM and
Lee shows that a faster procedure for BMM would automatically give a fast
procedure for CFL parsing, i.e., BMM⇄CFL. Once we can reduce from semirings to
BMM, encoding to SAT becomes straightforward using Tseitin (1968).

TODO: Lower this matrix onto SAT. Steps:
  1.) Encode CFL as BMM.
  2.) Symbolically evaluate BMM to get a Boolean formula.
  3.) Encode symbolic Boolean formula as CNF using Tsetin.
  4.) Run SAT solver and decode variable assignments.

  https://people.csail.mit.edu/virgi/6.s078/papers/valiant.pdf#page=13
  https://www.ps.uni-saarland.de/courses/seminar-ws06/papers/07_franziska_ebert.pdf#page=6
 */

/**
 * Checks whether a given string is valid by computing the transitive closure
 * of the matrix constructed by [initialMatrix]. If the upper-right corner entry is
 * empty, the string is invalid. If the entry is S, it parses.
 */

fun CFG.isValid(str: String): Boolean =
  str.split(" ").let { if (it.size == 1) str.map { "$it" } else it }
    .filter(String::isNotBlank).let { START_SYMBOL in parse(it).map { it.root } }

fun CFG.parseForest(str: String): Set<Tree> =
  str.split(" ").let { if (it.size == 1) str.map { "$it" } else it }
    .filter(String::isNotBlank).let(::solveFixedpoint)[0].last()

fun CFG.parseTable(str: String): FreeMatrix<Set<Tree>> =
  str.split(" ").let { if (it.size == 1) str.map { "$it" } else it }
    .filter(String::isNotBlank).let(::solveFixedpoint)

fun CFG.solveFixedpoint(
  tokens: List<String>,
  matrix: FreeMatrix<Set<Tree>> = initialMatrix(tokens),
): FreeMatrix<Set<Tree>> = matrix.seekFixpoint { it + it * it }

fun CFG.parse(
  tokens: List<String>,
  matrix: FreeMatrix<Set<Tree>> = initialMatrix(tokens),
  finalConfig: FreeMatrix<Set<Tree>> = matrix.seekFixpoint { it + it * it }
): Set<Tree> = finalConfig[0].last()
//  .also { if(it) println("Sol:\n$finalConfig") }

private val freshNames: Sequence<String> =
  ('A'..'Z').map { "$it" }.asSequence()
  .let { it + (it * it).map { (a, b) -> a + b } }
    .filter { it != START_SYMBOL }

fun String.parseCFG(
  normalize: Boolean = true,
  validate: Boolean = false
): CFG =
  (if (validate) validate() else this).lines().filter(String::isNotBlank)
     .map { line ->
         val prod = line.split(" -> ").map { it.trim() }
         if (2 == prod.size && " " !in prod[0]) prod[0] to prod[1].split(" ")
         else throw Exception("Invalid production: $line")
     }.toSet().let { if (normalize) it.normalForm else it }

fun String.stripEscapeChars(escapeSeq: String = "`") = replace(escapeSeq, "")

fun CFLCFL(names: Map<String, String>) = """
    START -> CFL
    CFL -> PRD | CFL \n CFL
    PRD -> VAR `->` RHS
    VAR -> ${names.values.joinToString(" | ")}
    RHS -> VAR | RHS RHS | RHS `|` RHS
  """.parseCFG(validate = false)

fun String.validate(
  presets: Set<String> = setOf("|", "->"),
  ws: Regex = Regex("\\s+"),
  tokens: List<String> = split(ws).filter { it.isNotBlank() && it !in presets },
  nameDict: Map<String, String> =
    freshNames.filterNot(this::contains).zip(tokens.asSequence()).toMap(),
  names: Map<String, String> = nameDict
): String = lines().filter(String::isNotBlank).joinToString(" \\n ")
  .split(ws).filter(String::isNotBlank).joinToString(" ") { names[it] ?: it }
  .let { if (CFLCFL(names).isValid(it)) this else throw Exception("!CFL: $it") }

// http://firsov.ee/cert-norm/cfg-norm.pdf
// https://www.cs.rit.edu/~jmg/courses/cs380/20051/slides/7-1-chomsky.pdf
private fun CFG.normalize(): CFG =
  addGlobalStartSymbol()
    .expandOr()
    .elimVarUnitProds()
    .refactorRHS()
    .terminalsToUnitProds()
    .removeUselessSymbols()

val START_SYMBOL = "START"

infix fun Char.matches(that: Char) =
  if (this == ')' && that == '(') true
  else if (this == ']' && that == '[') true
  else if (this == '}' && that == '{') true
  else this == '>' && that == '<'

fun String.dyckCheck() =
  filter { it in "()[]{}<>" }.fold(Stack<Char>()) { stack, c ->
    stack.apply { if(isNotEmpty() && c.matches(peek())) pop() else push(c) }
  }.isEmpty()

private fun CFG.addGlobalStartSymbol() = this +
  if (START_SYMBOL in variables) emptySet()
  else variables.map { START_SYMBOL to listOf(it) }

// Expands RHS `|` productions, e.g., (A -> B | C) -> (A -> B, A -> C)
private fun CFG.expandOr(): CFG =
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

private fun CFG.removeUselessSymbols(
  generating: Set<String> = generatingSymbols(),
  reachable: Set<String> = reachableSymbols(),
): CFG = filter { (s, _) -> s in generating && s in reachable }

private fun CFG.reachableSymbols(
  src: List<String> = listOf(START_SYMBOL),
  reachables: Set<String> = src.toSet()
): Set<String> = if (src.isEmpty()) reachables else filter { it.LHS in src }
  .flatMap { (_, rhs) -> rhs.filter { it in variables && it !in reachables } }
  .let { reachableSymbols(it, reachables + it) }

private fun CFG.generatingSymbols(
  from: List<String> = terminals.flatMap { it.RHS },
  generating: Set<String> = from.toSet()
): Set<String> = if (from.isEmpty()) generating
else filter { it.LHS !in generating && it.RHS.all { it in generating } }
  .map { it.LHS }.let { generatingSymbols(it, generating + it) }

/* Drops variable unit productions, for example:
 * Initial grammar: (A -> B, B -> c, B -> d) ->
 * After expansion: (A -> B, A -> c, A -> d, B -> c, B -> d) ->
 * After elimination: (A -> c, A -> d, B -> c, B -> d)
 */
private tailrec fun CFG.elimVarUnitProds(
  toVisit: Set<String> = variables,
  vars: Set<String> = variables,
  toElim: String? = toVisit.firstOrNull()
): CFG {
  fun Production.isVariableUnitProd() = RHS.size == 1 && RHS[0] in vars
  if (toElim == null) return filter { !it.isVariableUnitProd() }
  val varsThatMapToMe =
    filter { it.RHS.size == 1 && it.RHS[0] == toElim }.map { it.LHS }.toSet()
  val thingsIMapTo = filter { it.LHS == toElim }.map { it.RHS }.toSet()
  return (varsThatMapToMe * thingsIMapTo).fold(this) { g, p -> g + p }
    .elimVarUnitProds(toVisit.drop(1).toSet(), vars)
}

// Refactors long productions, e.g., (A -> BCD) -> (A -> BE, E -> CD)
private tailrec fun CFG.refactorRHS(): CFG {
  val longProd = firstOrNull { it.RHS.size > 2 } ?: return this
  val freshName = longProd.RHS.takeLast(2).joinToString(".")
  val newProd = freshName to longProd.RHS.takeLast(2)
  val shortProd = longProd.LHS to (longProd.RHS.dropLast(2) + freshName)
  val newGrammar = this - longProd + shortProd + newProd
  return if (this == newGrammar) this else newGrammar.refactorRHS()
}

// Replaces terminals in non-unit productions, e.g., (A -> bC) -> (A -> BC, B -> b)
private tailrec fun CFG.terminalsToUnitProds(): CFG {
  val mixProd = nonterminals.firstOrNull { it.RHS.any { it !in variables } } ?: return this
  val termIdx = mixProd.RHS.indexOfFirst { it !in variables }
  val freshName = "F." + mixProd.RHS[termIdx]
  val freshRHS = mixProd.RHS.toMutableList().also { it[termIdx] = freshName }
  val newProd = freshName to listOf(mixProd.RHS[termIdx])
  val newGrammar = this - mixProd + (mixProd.LHS to freshRHS) + newProd
  return if (this == newGrammar) this else newGrammar.terminalsToUnitProds()
}