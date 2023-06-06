package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.sampling.*
import ai.hypergraph.kaliningraph.splitProd
import ai.hypergraph.kaliningraph.tensor.*
import ai.hypergraph.kaliningraph.types.*

// SetValiant interface
//=====================================================================================
fun Σᐩ.matches(cfg: Σᐩ): Boolean = matches(cfg.validate().parseCFG())
fun Σᐩ.matches(CFG: CFG): Boolean = CFG.isValid(tokenizeByWhitespace())
fun Σᐩ.matches(CJL: CJL): Boolean = CJL.cfgs.all { matches(it) }
fun Σᐩ.parse(s: Σᐩ): Tree? = parseCFG().parse(s)
fun CFG.parse(s: Σᐩ): Tree? =
  try { parseForest(s).firstOrNull { it.root == START_SYMBOL }?.denormalize() }
  catch (e: Exception) { null }

/**
 * Checks whether a given string is valid by computing the transitive closure
 * of the matrix constructed by [initialMatrix]. If the upper-right corner entry
 * is empty, the string is invalid. If the entry is S, it parses.
 */

private fun List<Σᐩ>.pad3(): List<Σᐩ> =
  if (isEmpty()) listOf("ε", "ε", "ε")
  else if (size == 1) listOf("ε", first(), "ε")
  else this

fun CFG.isValid(str: Σᐩ): Boolean = isValid(str.tokenizeByWhitespace())
fun CFG.isValid(str: List<Σᐩ>): Boolean =
  initialUTBMat(str.pad3()).seekFixpoint().diagonals
//    .also { it.forEachIndexed { r, d -> d.forEachIndexed { i, it -> println("$r, $i: ${toNTSet(it)}") } } }
    .last().first()//.also { println("Last: ${it.joinToString(",") {if (it) "1" else "0"}}") }
    .let { corner -> corner[bindex[START_SYMBOL]] }

fun CFG.parseForest(str: Σᐩ): Forest = solveFixedpoint(str.tokenizeByWhitespace())[0].last()
fun CFG.parseTable(str: Σᐩ): TreeMatrix = solveFixedpoint(str.tokenizeByWhitespace())

fun CFG.parse(
  tokens: List<Σᐩ>,
  utMatrix: UTMatrix<Forest> = initialUTMatrix(tokens),
): Forest = utMatrix.seekFixpoint().diagonals.last().firstOrNull() ?: emptySet()
//  .also { if (it) println("Sol:\n$finalConfig") }

fun CFG.solveFixedpoint(
  tokens: List<Σᐩ>,
  utMatrix: UTMatrix<Forest> = initialUTMatrix(tokens),
): TreeMatrix = utMatrix.seekFixpoint().toFullMatrix()

// Returns first valid whole-parse tree if the string is syntactically valid, and if not,
// a sequence of partial trees ordered by the length of the substring that can be parsed.
fun CFG.parseWithStubs(s: Σᐩ): Pair<Forest, List<Tree>> =
  solveFixedpoint(s.tokenizeByWhitespace()).toUTMatrix().diagonals.asReversed().let {
    it.first()[0].filter { it.root == START_SYMBOL }.map { it.denormalize() }.toSet() to
      it.flatten().flatten().map { it.denormalize() }
  }

fun CFG.parseInvalidWithMaximalFragments(s: Σᐩ): List<Tree> =
  parseWithStubs(s).second.fold(setOf<Tree>()) { acc, t ->
    if (acc.any { t.span isStrictSubsetOf it.span }) acc else acc + t
  }.sortedBy { it.span.first }

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

fun CFG.makeForestAlgebra(): Ring<Forest> =
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

//fun CFG.setJoin(left: Set<Σᐩ>, right: Set<Σᐩ>): Set<Σᐩ> = joinMap[left, right]
fun CFG.setJoin(left: Set<Σᐩ>, right: Set<Σᐩ>): Set<Σᐩ> =
  (left * right).flatMap { bimap[it.toList()] }.toSet()

fun CFG.toBitVec(nts: Set<Σᐩ>): BooleanArray =
  if (1 < nts.size) nonterminals.map { it in nts }.toBooleanArray()
  else BooleanArray(nonterminals.size) { false }
    .also { if (1 == nts.size) it[bindex[nts.first()]] = true }

fun fastJoin(vindex: Array<IntArray>, left: BooleanArray, right: BooleanArray): BooleanArray {
  if (left.isEmpty() || right.isEmpty()) return booleanArrayOf()

  val result = BooleanArray(vindex.size)
  for (i in vindex.indices) {
    val indexArray = vindex[i]
    for (j in indexArray.indices step 2) {
      val B = indexArray[j]
      val C = indexArray[j + 1]
      if (left[B] && right[C]) {
        result[i] = true
        break
      }
    }
  }
  return result
}

//  if (left.isEmpty() || right.isEmpty()) booleanArrayOf()
//  else vindex.map { it.any { (B, C) -> left[B] and right[C] } }.toBooleanArray()

fun CFG.join(left: BooleanArray, right: BooleanArray): BooleanArray = fastJoin(vindex, left, right)

fun maybeJoin(vindexFast: Array<IntArray>, left: BooleanArray?, right: BooleanArray?): BooleanArray? =
  if (left == null || right == null) null else fastJoin(vindexFast, left, right)

fun maybeUnion(left: BooleanArray?, right: BooleanArray?): BooleanArray? =
  if (left == null || right == null) { left ?: right }
  else if (left.isEmpty() && right.isNotEmpty()) right
  else if (left.isNotEmpty() && right.isEmpty()) left
  else left.zip(right) { l, r -> l or r }.toBooleanArray()

fun union(left: BooleanArray, right: BooleanArray): BooleanArray {
  val result = BooleanArray(left.size)
  for (i in left.indices) result[i] = left[i] or right[i]
  return result
}

val CFG.bitwiseAlgebra: Ring<BooleanArray> by cache {
  vindex.let {
    Ring.of(
      nil = BooleanArray(nonterminals.size) { false },
      plus = { x, y -> union(x, y) },
      times = { x, y -> fastJoin(it, x, y) }
    )
  }
}

// Like bitwiseAlgebra, but with nullable bitvector literals for free variables
val CFG.satLitAlgebra: Ring<BooleanArray?> by cache {
  vindex.let {
    Ring.of(
      nil = BooleanArray(nonterminals.size) { false },
      plus = { x, y -> maybeUnion(x, y) },
      times = { x, y -> maybeJoin(it, x, y) }
    )
  }
}

fun CFG.toNTSet(nts: BooleanArray): Set<Σᐩ> =
  nts.mapIndexed { i, it -> if (it) bindex[i] else null }.filterNotNull().toSet()

fun BooleanArray.decodeWith(cfg: CFG): Set<Σᐩ> =
  mapIndexed { i, it -> if (it) cfg.bindex[i] else null }.filterNotNull().toSet()

fun CFG.toBooleanArray(nts: Set<Σᐩ>): BooleanArray =
  BooleanArray(nonterminals.size) { i -> bindex[i] in nts }

//=====================================================================================

val HOLE_MARKER = "_"
fun Σᐩ.containsHole(): Boolean = HOLE_MARKER in this
fun Σᐩ.isHoleTokenIn(cfg: CFG) = containsHole() || isNonterminalStubIn(cfg)
//val ntRegex = Regex("<[^\\s>]*>")
fun Σᐩ.isNonterminalStub() = isNotEmpty() && first() == '<' && last() == '>'
fun Σᐩ.isNonterminalStubIn(cfg: CFG): Boolean = isNonterminalStub() && drop(1).dropLast(1) in cfg.nonterminals
fun Σᐩ.isNonterminalStubIn(CJL: CJL): Boolean = CJL.cfgs.map { isNonterminalStubIn(it) }.all { it }
fun String.containsNonterminal(): Boolean = Regex("<[^\\s>]*>") in this

// Converts tokens to UT matrix via constructor: σ_i = { A | (A -> w[i]) ∈ P }
fun CFG.initialMatrix(str: List<Σᐩ>): TreeMatrix =
  FreeMatrix(makeForestAlgebra(), str.size + 1) { i, j ->
    if (i + 1 != j) emptySet()
    else bimap[listOf(str[j - 1])].map {
      Tree(root = it, terminal = str[j - 1], span = (j - 1) until j)
    }.toSet()
  }

fun CFG.initialUTBMat(tokens: List<Σᐩ>): UTMatrix<BooleanArray> =
  UTMatrix(
    ts = tokens.map { it ->
      bimap[listOf(it)].let { nts ->
        if (tokens.none { it.isNonterminalStubIn(this) }) nts
        // We use the original form because A -> B -> C can be normalized
        // to A -> C, and we want B to be included in the equivalence class
        else nts.map { originalForm.equivalenceClass(it) }.flatten().toSet()
      }.let { nts -> nonterminals.map { it in nts } }.toBooleanArray()
    }.toTypedArray(),
    algebra = bitwiseAlgebra
  )

fun CFG.initialUTMatrix(tokens: List<Σᐩ>): UTMatrix<Forest> =
  UTMatrix(
    ts = tokens.mapIndexed { i, terminal ->
      bimap[listOf(terminal)].let { nts ->
        if (tokens.none { it.isNonterminalStubIn(this) }) nts
        // We use the original form because A -> B -> C can be normalized
        // to A -> C, and we want B to be included in the equivalence class
        else nts.map { nt -> originalForm.equivalenceClass(nt).also { println("$nt = $it") } }.flatten().toSet()
      }.map { Tree(root = it, terminal = terminal, span = i until (i + 1)) }.toSet()
    }.toTypedArray(),
    algebra = makeForestAlgebra()
  )

private val freshNames: Sequence<Σᐩ> =
  ('A'..'Z').asSequence().map { "$it" }
  .let { it + (it * it).map { (a, b) -> a + b } }
    .filter { it != START_SYMBOL }

fun Σᐩ.parseCFG(
  normalize: Boolean = true,
  validate: Boolean = false
): CFG =
  (if (validate) validate() else this).lines().filter { "->" in it }.map { line ->
    val prod = line.splitProd()
    if (2 == prod.size && " " !in prod[0]) prod[0] to prod[1].tokenizeByWhitespace()
    else throw Exception("Invalid production ${prod.size}: $line")
  }.toSet().let { if (normalize) it.normalForm else it }

fun Σᐩ.stripEscapeChars(escapeSeq: Σᐩ = "`"): Σᐩ = replace(escapeSeq, "")

fun CFGCFG(names: Collection<Σᐩ>): CFG = """
    START -> CFG
    CFG -> PRD | CFG \n CFG
    PRD -> VAR `->` RHS
    VAR -> ${names.joinToString(" | ")}
    RHS -> VAR | RHS RHS | RHS `|` RHS
  """.parseCFG(validate = false)

fun Σᐩ.validate(
  presets: Set<Σᐩ> = setOf("|", "->"),
  tokens: Sequence<Σᐩ> = tokenizeByWhitespace().filter { it !in presets }.asSequence(),
  names: Map<Σᐩ, Σᐩ> = freshNames.filterNot(::contains).zip(tokens).toMap(),
): Σᐩ = lines().filter(Σᐩ::isNotBlank).joinToString(" \\n ")
  .tokenizeByWhitespace().joinToString(" ") { names[it] ?: it }
  .let { if (it.matches(CFGCFG(names.values))) this
  else throw Exception("!CFL: $it") }

/*
 * Takes a grammar and a partially complete string where '_' denotes holes, and
 * returns a set of completed strings consistent with that grammar. Naive search
 * over all holes takes O(|Σ|^n) where n is the number of holes.
 */

fun List<Σᐩ>.solve(
  CFG: CFG,
  fillers: Set<Σᐩ> = CFG.terminals - CFG.blocked,
  takeMoreWhile: () -> Boolean = { true },
): Sequence<Σᐩ> =
  genCandidates(CFG, fillers)
//    .also { println("Solving (Complexity: ${fillers.size.pow(count { it == "_" })}): ${joinToString(" ")}") }
    .takeWhile { takeMoreWhile() }.filter { it.matches(CFG) }

fun List<Σᐩ>.genCandidates(CFG: CFG, fillers: Set<Σᐩ> = CFG.terminals): Sequence<Σᐩ> =
  MDSamplerWithoutReplacement(fillers, count { it == HOLE_MARKER }).map {
    fold("" to it) { (a, b), c ->
      if (c == HOLE_MARKER) (a + " " + b.first()) to b.drop(1) else ("$a $c") to b
    }.first.replace("ε ", "").trim()
  }

// TODO: Compactify [en/de]coding: https://news.ycombinator.com/item?id=31442706#31442719
fun CFG.nonterminals(bitvec: List<Boolean>): Set<Σᐩ> =
    bitvec.mapIndexedNotNull { i, it -> if (it) bindex[i] else null }.toSet()
        .apply { ifEmpty { throw Exception("Unable to reconstruct NTs from: $bitvec") } }

fun CFG.handleSingleton(s: Σᐩ): Set<Σᐩ> =
    if (s == "_") terminals
    else if (s.matches(Regex("<.+>")))
        bimap[s.substring(1, s.length - 1)]
            .mapNotNull { if (it.size == 1) it[0] else null }.toSet()
    else setOf()