package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.automata.*
import ai.hypergraph.kaliningraph.sampling.*
import ai.hypergraph.kaliningraph.tensor.*
import ai.hypergraph.kaliningraph.types.*
import kotlin.jvm.JvmName
import kotlin.math.*

// SetValiant interface
//=====================================================================================
fun String.matches(cfg: String): Boolean = matches(cfg.validate().parseCFG())
fun String.matches(CFG: CFG): Boolean = CFG.isValid(this)
fun String.parse(s: String): Tree? = parseCFG().parse(s)
fun CFG.parse(s: String): Tree? =
  try { parseForest(s).firstOrNull { it.root == START_SYMBOL }?.denormalize() }
  catch (e: Exception) { null }

/**
 * Checks whether a given string is valid by computing the transitive closure
 * of the matrix constructed by [initialMatrix]. If the upper-right corner entry
 * is empty, the string is invalid. If the entry is S, it parses.
 */

fun CFG.isValid(str: String): Boolean =
  tokenize(str).let { START_SYMBOL in parse(it).map { it.root } }

fun CFG.parseForest(str: String): Forest = solveFixedpoint(tokenize(str))[0].last()
fun CFG.parseTable(str: String): TreeMatrix = solveFixedpoint(tokenize(str))

fun CFG.parse(
  tokens: List<String>,
  utMatrix: UTMatrix<Forest> = initialUTMatrix(tokens),
): Forest = utMatrix.seekFixpoint().diagonals.last().firstOrNull() ?: emptySet()
//  .also { if (it) println("Sol:\n$finalConfig") }

fun CFG.solveFixedpoint(
  tokens: List<String>,
  utMatrix: UTMatrix<Forest> = initialUTMatrix(tokens),
): TreeMatrix = utMatrix.seekFixpoint().toFullMatrix()

// Returns first valid whole-parse tree if the string is syntactically valid, and if not,
// a sequence of partial trees ordered by the length of the substring that can be parsed.
fun CFG.parseWithStubs(s: String): Pair<Forest, List<Tree>> =
  solveFixedpoint(tokenize(s)).toUTMatrix().diagonals.asReversed().let {
    it.first()[0].filter { it.root == START_SYMBOL }.map { it.denormalize() }.toSet() to
      it.flatten().flatten().map { it.denormalize() }
  }

//=====================================================================================
/* Algebraic operations
 *
 * See: http://www.cse.chalmers.se/~patrikj/talks/IFIP2.1ZeegseJansson_ParParseAlgebra.org
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

fun CFG.makeAlgebra(): Ring<Forest> =
  Ring.of(// Not a proper ring, but close enough.
    // 0 = ∅
    nil = setOf(),
    // x + y = x ∪ y
    plus = { x, y -> x union y },
    // x · y = { A0 | A1 ∈ x, A2 ∈ y, (A0 -> A1 A2) ∈ P }
    times = { x, y -> treeJoin(x, y) }
  )

//fun CFG.treeJoin(left: Forest, right: Forest): Forest = joinMap[left, right]
fun CFG.treeJoin(left: Forest, right: Forest): Forest =
  (left * right).flatMap { (lt, rt) ->
    bimap[listOf(lt.root, rt.root)].map { Tree(it, null, lt, rt) }
  }.toSet()

//fun CFG.setJoin(left: Set<String>, right: Set<String>): Set<String> = joinMap[left, right]
fun CFG.setJoin(left: Set<String>, right: Set<String>): Set<String> =
  (left * right).flatMap { bimap[it.toList()] }.toSet()

fun CFG.toBitVec(nts: Set<String>): List<Boolean> = nonterminals.map { it in nts }

@JvmName("joinBitVector")
fun CFG.join(left: List<Boolean>, right: List<Boolean>): List<Boolean> =
  if (left.isEmpty() || right.isEmpty()) emptyList()
  else List(left.size) { i ->
    bimap[bindex[i]].filter { 1 < it.size }.map { it[0] to it[1] }
      .map { (B, C) -> left[bindex[B]] and right[bindex[C]] }
      .fold(false) { acc, satf -> acc or satf }
  }

fun CFG.maybeJoin(left: List<Boolean>?, right: List<Boolean>?): List<Boolean>? =
  if (left == null || right == null) null else join(left, right)

fun maybeUnion(left: List<Boolean>?, right: List<Boolean>?): List<Boolean>? =
  if (left == null || right == null) { left ?: right }
  else if (left.isEmpty() && right.isNotEmpty()) right
  else if (left.isNotEmpty() && right.isEmpty()) left
  else left.zip(right) { l, r -> l or r }

fun CFG.toNTSet(nts: List<Boolean>): Set<String> =
  nts.mapIndexedNotNull { i, it -> if (it) bindex[i] else null }.toSet()

fun List<Boolean>.decodeWith(cfg: CFG): Set<String> =
  mapIndexedNotNull { i, it -> if (it) cfg.bindex[i] else null }.toSet()

val CFG.satLitAlgebra: Ring<List<Boolean>?> by cache {
  Ring.of(
    nil = List(nonterminals.size) { false },
    plus = { x, y -> maybeUnion(x, y) },
    times = { x, y -> maybeJoin(x, y) }
  )
}

//=====================================================================================

// Converts tokens to UT matrix via constructor: σ_i = { A | (A -> w[i]) ∈ P }
fun CFG.initialMatrix(str: List<String>): TreeMatrix =
  FreeMatrix(makeAlgebra(), str.size + 1) { i, j ->
    if (i + 1 != j) emptySet()
    else bimap[listOf(str[j - 1])].map {
      Tree(root = it, terminal = str[j - 1], span = (j - 1) until j)
    }.toSet()
  }

fun String.splitKeeping(str: String): List<String> =
  split(str).flatMap { listOf(it, str) }.dropLast(1)

fun String.mergeHoles() =
  replace(Regex("\\s+"), " ")
    .replace(Regex("(?<=_)\\s(?=_)"), "")

fun tokenize(str: String): List<String> = str.tokenizeByWhitespace()
//  delimiters.fold(listOf(str.mergeHoles())) { l, delim ->
//    l.flatMap { if (it in delimiters) listOf(it) else it.splitKeeping(delim) }
//  }.filter(String::isNotBlank)

fun CFG.initialUTMatrix(tokens: List<String>): UTMatrix<Forest> =
  UTMatrix(
    ts = tokens.mapIndexed { i, terminal ->
      bimap[listOf(terminal)]
        .map { Tree(root = it, terminal = terminal, span = i until (i + 1)) }.toSet()
    }.toTypedArray(),
    algebra = makeAlgebra()
  )

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
      else throw Exception("Invalid production ${prod.size}: $line")
    }.toSet().let { if (normalize) it.normalForm else it }

fun String.stripEscapeChars(escapeSeq: String = "`"): String = replace(escapeSeq, "")

fun CFGCFG(names: Collection<String>): CFG = """
    START -> CFG
    CFG -> PRD | CFG \n CFG
    PRD -> VAR `->` RHS
    VAR -> ${names.joinToString(" | ")}
    RHS -> VAR | RHS RHS | RHS `|` RHS
  """.parseCFG(validate = false)

fun String.validate(
  presets: Set<String> = setOf("|", "->"),
  ws: Regex = Regex("\\s+"),
  tokens: List<String> = split(ws).filter { it.isNotBlank() && it !in presets },
  names: Map<String, String> =
    freshNames.filterNot(this::contains).zip(tokens.asSequence()).toMap(),
): String = lines().filter(String::isNotBlank).joinToString(" \\n ")
  .split(ws).filter(String::isNotBlank).joinToString(" ") { names[it] ?: it }
  .let { if (CFGCFG(names.values).isValid(it)) this else throw Exception("!CFL: $it") }