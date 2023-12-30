@file:Suppress("NonAsciiCharacters")

package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.*
import ai.hypergraph.kaliningraph.sampling.*
import ai.hypergraph.kaliningraph.tensor.*
import ai.hypergraph.kaliningraph.types.*
import kotlin.random.Random


// SetValiant interface
//=====================================================================================
fun Σᐩ.matches(cfg: Σᐩ): Bln = matches(cfg.parseCFG())
fun Σᐩ.matches(CFG: CFG): Bln = CFG.isValid(tokenizeByWhitespace())
fun Σᐩ.matches(CJL: CJL): Bln = CJL.cfgs.all { matches(it) }
fun List<Σᐩ>.matches(CFG: CFG): Bln = CFG.isValid(this)
fun Σᐩ.parse(s: Σᐩ): Tree? = parseCFG().parse(s)
fun CFG.parse(s: Σᐩ): Tree? =
  try {
//    enumTree(s.tokenizeByWhitespace()).firstOrNull()?.denormalize()
    parseForest(s).firstOrNull { it.root == START_SYMBOL }?.denormalize()
  } catch (e: Exception) { checkUnitWord(s).ifEmpty { null }?.firstOrNull() }

fun CFG.checkUnitWord(str: Σᐩ): Forest =
  if (str.tokenizeByWhitespace().size == 1 && START_SYMBOL in bimap[listOf(str)]) {
    setOf(Tree(START_SYMBOL, str))
  } else setOf()

fun CFG.parseAll(s: Σᐩ): Forest =
  try { parseForest(s).filter { it.root == START_SYMBOL }.map { it.denormalize() }.toSet() }
  catch (e: Exception) { setOf() }

/**
 * Checks whether a given string is valid by computing the transitive closure
 * of the matrix constructed by [initialMatrix]. If the upper-right corner entry
 * is empty, the string is invalid. If the entry is S, it parses.
 */

private fun List<Σᐩ>.pad3(): List<Σᐩ> =
  if (isEmpty()) listOf("ε", "ε", "ε")
  else if (size == 1) listOf("ε", first(), "ε")
  else this

fun CFG.isValid(str: Σᐩ): Bln = isValid(str.tokenizeByWhitespace())
fun CFG.isValid(str: List<Σᐩ>): Bln =
  if (str.size == 1) checkUnitWord(str.first()).isNotEmpty()
  else initialUTBMatrix(str.pad3()).seekFixpoint().diagonals.last()[0]
    //.also { it.forEachIndexed { r, d -> d.forEachIndexed { i, it -> println("$r, $i: ${toNTSet(it)}") } } }
    //.also { println("Last: ${it.joinToString(",") {if (it) "1" else "0"}}") }
    .let { corner -> corner[bindex[START_SYMBOL]] }

fun CFG.corner(str: Σᐩ) =
 solveFixedpoint(str.tokenizeByWhitespace())[0].last().map { it.root }.toSet()
//  START_SYMBOL in solveFixedpoint(str.tokenizeByWhitespace())[0].last().map { it.root }.toSet()

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
    it[0][0].filter { it.root == START_SYMBOL }.map { it.denormalize() }.toSet() to
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

fun CFG.toBitVec(nts: Set<Σᐩ>): Blns =
  if (1 < nts.size) nonterminals.map { it in nts }.toBooleanArray()
  else BooleanArray(nonterminals.size) { false }
    .also { if (1 == nts.size) it[bindex[nts.first()]] = true }

fun fastJoin(/**[vindex]*/vidx: Array<ℤⁿ>, left: Blns, right: Blns): Blns {
  if (left.isEmpty() || right.isEmpty()) return booleanArrayOf()

  val result = BooleanArray(vidx.size)
  for ((i, indexArray) in vidx.withIndex()) {
    var j = 0
    while (j < indexArray.size) {
      if (left[indexArray[j]] && right[indexArray[j + 1]]) {
        result[i] = true
        break
      }
      j += 2
    }
  }
  return result
}

//  if (left.isEmpty() || right.isEmpty()) booleanArrayOf()
//  else vindex.map { it.any { (B, C) -> left[B] and right[C] } }.toBooleanArray()

fun CFG.join(left: Blns, right: Blns): Blns = fastJoin(vindex, left, right)

fun maybeJoin(vindexFast: Array<ℤⁿ>, left: Blns?, right: Blns?): Blns? =
  if (left == null || right == null) null else fastJoin(vindexFast, left, right)

fun maybeUnion(left: Blns?, right: Blns?): Blns? =
  if (left == null || right == null) { left ?: right }
  else if (left.isEmpty() && right.isNotEmpty()) right
  else if (left.isNotEmpty() && right.isEmpty()) left
  else union(left, right)

fun union(left: Blns, right: Blns): Blns {
  val result = BooleanArray(left.size)
  for (i in left.indices) {
    result[i] = left[i]
    if (result[i]) continue
    result[i] = right[i]
  }
  return result
}

val CFG.bitwiseAlgebra: Ring<Blns> by cache {
  vindex.let {
    Ring.of(
      nil = BooleanArray(nonterminals.size) { false },
      plus = { x, y -> union(x, y) },
      times = { x, y -> fastJoin(it, x, y) }
    )
  }
}

// Like bitwiseAlgebra, but with nullable bitvector literals for free variables
val CFG.satLitAlgebra: Ring<Blns?> by cache {
  vindex.let {
    Ring.of(
      nil = BooleanArray(nonterminals.size) { false },
      plus = { x, y -> maybeUnion(x, y) },
      times = { x, y -> maybeJoin(it, x, y) }
    )
  }
}

fun CFG.toNTSet(nts: Blns): Set<Σᐩ> =
  nts.mapIndexed { i, it -> if (it) bindex[i] else null }.filterNotNull().toSet()

fun Blns.decodeWith(cfg: CFG): Set<Σᐩ> =
  mapIndexed { i, it -> if (it) cfg.bindex[i] else null }.filterNotNull().toSet()

fun CFG.toBooleanArray(nts: Set<Σᐩ>): Blns =
  BooleanArray(nonterminals.size) { i -> bindex[i] in nts }

//=====================================================================================

val HOLE_MARKER = "_"
fun Σᐩ.containsHole(): Bln = HOLE_MARKER in tokenizeByWhitespace()
fun Σᐩ.isHoleTokenIn(cfg: CFG) = containsHole() || isNonterminalStubIn(cfg)
//val ntRegex = Regex("<[^\\s>]*>")
fun Σᐩ.isNonterminalStub() = isNotEmpty() && first() == '<' && last() == '>'
fun Σᐩ.isNonterminalStubInNTs(nts: Set<Σᐩ>): Bln = isNonterminalStub() && drop(1).dropLast(1) in nts
fun Σᐩ.isNonterminalStubIn(cfg: CFG): Bln = isNonterminalStub() && drop(1).dropLast(1) in cfg.nonterminals
fun Σᐩ.isNonterminalStubIn(CJL: CJL): Bln = CJL.cfgs.map { isNonterminalStubIn(it) }.all { it }
fun Σᐩ.containsNonterminal(): Bln = Regex("<[^\\s>]*>") in this

// Converts tokens to UT matrix via constructor: σ_i = { A | (A -> w[i]) ∈ P }
fun CFG.initialMatrix(str: List<Σᐩ>): TreeMatrix =
  FreeMatrix(makeForestAlgebra(), str.size + 1) { i, j ->
    if (i + 1 != j) emptySet()
    else bimap[listOf(str[j - 1])].map {
      Tree(root = it, terminal = str[j - 1], span = (j - 1) until j)
    }.toSet()
  }

fun CFG.initialUTBMatrix(
  tokens: List<Σᐩ>,
  allNTs: Set<Σᐩ> = nonterminals,
  bmp: BiMap = bimap
): UTMatrix<Blns> =
  UTMatrix(
    ts = tokens.map { it ->
      bmp[listOf(it)].let { nts -> allNTs.map { it in nts } }.toBooleanArray()
    }.toTypedArray(),
    algebra = bitwiseAlgebra
  )

fun CFG.initialUTMatrix(
  tokens: List<Σᐩ>,
  bmp: BiMap = bimap,
  unitReach: Map<Σᐩ, Set<Σᐩ>> = originalForm.unitReachability
): UTMatrix<Forest> =
  UTMatrix(
    ts = tokens.mapIndexed { i, terminal ->
      if (terminal == HOLE_MARKER)
        unitReach.values.flatten().toSet().map { root ->
          bmp[root].filter { it.size == 1 }.map { it.first() }.filter { it in terminals }
            .map { Tree(root = root, terminal = it, span = i until (i + 1)) }
        }.flatten().toSet()
      else bmp[listOf(terminal)].map { Tree(root = it, terminal = terminal, span = i until (i + 1)) }.toSet()
    }.toTypedArray(),
    algebra = makeForestAlgebra()
  )

private val freshNames: Sequence<Σᐩ> =
  ('A'..'Z').asSequence().map { "$it" }
  .let { it + (it * it).map { (a, b) -> a + b } }
    .filter { it != START_SYMBOL }

fun generateRandomCFG(
  numProds: IntRange = 10..20,
  numChoicesPerProd: IntRange = 1..10,
  numTokPerChoice: IntRange = 1..3,
): String {
  val existingNames = mutableSetOf(START_SYMBOL)
  fun freshName(): Σᐩ = ('A'..'Z').let { az -> (0..2).joinToString("") { "" + az.random() } }
  var grammar = ""
  repeat(numProds.random()) {
    val LHS = existingNames.random()
    if (it == 0) existingNames -= START_SYMBOL
    val RHS = (1..numChoicesPerProd.random()).joinToString(" | ") {
      generateSequence {
        if (Random.nextDouble() < 0.3 && existingNames.isNotEmpty()) existingNames.random()
        else freshName().also { existingNames.add(it) }
      }.take(numTokPerChoice.random()).joinToString(" ")
    }
    grammar += "$LHS -> $RHS\n"
  }
  return grammar
}

fun Σᐩ.parseCFG(
  normalize: Bln = true,
  validate: Bln = false
): CFG =
  lines().filter { "->" in it }.map { line ->
    if (validate && !line.isValidProd()) throw Exception("Invalid production: $line")
    line.splitProd().let { it[0] to it[1].tokenizeByWhitespace() }
  }.toSet().let { if (normalize) it.normalForm else it }

fun Σᐩ.stripEscapeChars(escapeSeq: Σᐩ = "`"): Σᐩ = replace(escapeSeq, "")

val PRODCFG = Regex("\\s*[^|]+\\s+->\\s+([^|]+\\s+\\|\\s+)*[^|]+\\s*")
fun Σᐩ.isValidProd() = lines().filter { "->" in it }.all { l -> l.matches(PRODCFG) }

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
): Σᐩ = lines().filter { "->" in it }
  .onEach { if (!it.isValidProd()) throw Exception("Invalid production: $it") }
  .joinToString(" \\n ").tokenizeByWhitespace()
  .joinToString(" ") { names[it] ?: it }
  .let { if (it.matches(CFGCFG(names.values))) this
  else throw Exception("!CFL: $it") }

/*
 * Takes a grammar and a partially complete string where '_' denotes holes, and
 * returns a set of completed strings consistent with that grammar. Naïve search
 * over all holes takes O(|Σ|^n) where n is the number of holes.
 */

fun List<Σᐩ>.solve(
  CFG: CFG,
  fillers: Set<Σᐩ> = CFG.terminals - CFG.blocked,
  takeMoreWhile: () -> Bln = { true },
): Sequence<Σᐩ> =
  genCandidates(CFG, fillers)
//    .also { println("Solving (Complexity: ${fillers.size.pow(count { it == "_" })}): ${joinToString(" ")}") }
    .takeWhile { takeMoreWhile() }.filter { it.matches(CFG) }.map { it.removeEpsilon() }

fun List<Σᐩ>.genCandidates(CFG: CFG, fillers: Set<Σᐩ> = CFG.terminals): Sequence<Σᐩ> =
  MDSamplerWithoutReplacement(fillers, count { it == HOLE_MARKER }).map {
    fold("" to it) { (a, b), c ->
      if (c == HOLE_MARKER) (a + " " + b[0]) to b.drop(1) else ("$a $c") to b
    }.first.replace("ε ", "").trim()
  }

// TODO: Compactify [en/de]coding: https://news.ycombinator.com/item?id=31442706#31442719
fun CFG.nonterminals(bitvec: List<Bln>): Set<Σᐩ> =
    bitvec.mapIndexedNotNull { i, it -> if (it) bindex[i] else null }.toSet()
        .apply { ifEmpty { throw Exception("Unable to reconstruct NTs from: $bitvec") } }

fun CFG.handleSingleton(s: Σᐩ): Set<Σᐩ> =
    if (s == "_") terminals
    else if (s.matches(Regex("<.+>")))
        bimap[s.substring(1, s.length - 1)]
            .mapNotNull { if (it.size == 1) it[0] else null }.toSet()
    else setOf()