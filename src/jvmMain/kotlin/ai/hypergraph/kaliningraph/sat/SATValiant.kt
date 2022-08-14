package ai.hypergraph.kaliningraph.sat

import ai.hypergraph.kaliningraph.graphs.*
import ai.hypergraph.kaliningraph.image.toHtmlPage
import ai.hypergraph.kaliningraph.parsing.*
import ai.hypergraph.kaliningraph.tensor.*
import ai.hypergraph.kaliningraph.types.*
import ai.hypergraph.kaliningraph.visualization.*
import org.logicng.formulas.Constant
import org.logicng.formulas.Formula
import org.logicng.formulas.Variable
import kotlin.collections.filter

@JvmName("joinFormula")
fun CFG.join(left: List<Formula>, right: List<Formula>): List<Formula> =
  if (left.isEmpty() || right.isEmpty()) emptyList()
  else List(left.size) { i ->
    bimap[bindex[i]].filter { 1 < it.size }.map { it[0] to it[1] }
      .map { (B, C) -> left[bindex[B]] and right[bindex[C]] }
      .fold(BLit(false)) { acc, satf -> acc or satf }
  }

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

@JvmName("satFormulaUnion")
infix fun List<Formula>.union(that: List<Formula>): List<Formula> =
  if (isEmpty()) that else if (that.isEmpty()) this
  else List(size) { i -> this[i] or that[i] }

fun List<Boolean>.toLitVec(): List<Formula> = map { BLit(it) }

fun CFG.toBitVec(nts: Set<String>): List<Boolean> = nonterminals.map { it in nts }
fun CFG.toNTSet(nts: List<Boolean>): Set<String> =
  nts.mapIndexedNotNull { i, it -> if(it) bindex[i] else null }.toSet()

fun List<Boolean>.decodeWith(cfg: CFG): Set<String> =
  mapIndexedNotNull { i, it -> if(it) cfg.bindex[i] else null }.toSet()

infix fun List<Formula>.vecEq(that: List<Formula>): Formula =
  if (isEmpty() || that.isEmpty() || size != that.size) throw Exception("Shape mismatch!")
  else zip(that).partition { (l, r) -> l == r }
//    .also { (a, b) -> if(a.isNotEmpty()) println("Eliminated ${a.size}/${a.size + b.size} identical SAT variables") }
    .second.map { (a, b) -> a eq b }
    .let { if(it.isEmpty()) T else it.reduce { acc, satf -> acc and satf } }

infix fun UTMatrix<List<Formula>>.valiantMatEq(that: UTMatrix<List<Formula>>): Formula =
  if (shape() != that.shape()) throw Exception("Shape mismatch, incomparable!")
  else data.zip(that.data)
    // Only compare nonempty bitvectors pairs
    .filter { (l, r) -> l.isNotEmpty() && r.isNotEmpty() }
    // Only compare bitvector pairs which are not trivially identical
    .partition { (l, r) -> l.zip(r).all { (a, b) -> a == b } }
    .also { (a, b) ->
      if(a.isNotEmpty()) println("Eliminated ${a.size}/${a.size + b.size} identical bitvectors") }
    .second.map { (a, b) -> a vecEq b }.reduce { acc, satf -> acc and satf }
//    .also { println("Grammar constraints: ${it.numberOfAtoms()}") }

fun CFG.isInGrammar(mat: UTMatrix<List<Formula>>): Formula =
  mat.diagonals.last().first()[bindex[START_SYMBOL]]

// Encodes the constraint that bit-vectors representing a unary production
// should not contain mixed nonterminals e.g. given A->(, B->(, C->), D->)
// the bitvector cannot have the configuration [A=1 B=1 C=0 D=1], it must
// be either [A=1 B=1 C=0 D=0] or [A=0 B=0 C=1 D=1].
fun CFG.mustBeOnlyOneTerminal(bitvec: List<Formula>): Formula =
  // terminal        possible nonterminals it can represent
  terminals.map { bitvec.join(bimap[listOf(it)], nonterminals) }.map { possibleNTs ->
    val (insiders, outsiders) = bitvec.join(nonterminals).partition { it in possibleNTs }
    (insiders + outsiders.map { it.negate() }).reduce { acc, satf -> acc and satf }
  }.reduce { acc, satf -> acc xor satf }

// Returns list elements matching the intersection between set and on (indexed by on)
fun <E, T> List<E>.join(set: Set<T>, on: Set<T> = set): Set<E> =
  if (size != on.size) throw Exception("Size mismatch: List[$size] != Set[${on.size}]")
  else set.intersect(on).map { this[on.indexOf(it)] }.toSet()

// Encodes that each blank can only be one nonterminal
fun CFG.uniquenessConstraints(holeVariables: List<List<Formula>>): Formula =
  holeVariables.map { bitvec -> mustBeOnlyOneTerminal(bitvec) }
    .fold(T) { acc, it -> acc and it }
//    .also { println("Uniqueness constraints: ${it.numberOfAtoms()}") }

val CFG.satLitAlgebra: Ring<List<Boolean>?> by cache {
  Ring.of(
    nil = List(nonterminals.size) { false },
    plus = { x, y -> maybeUnion(x, y) },
    times = { x, y -> maybeJoin(x, y) }
  )
}

val CFG.satAlgebra by cache {
  Ring.of(
    nil = emptyList(),
    one = List(nonterminals.size) { T },
    plus = { a, b -> a union b },
    times = { a, b -> join(a, b) }
  )
}

fun FreeMatrix<Set<Tree>>.toGraphTable(): FreeMatrix<String> =
  data.map {
    it.mapIndexed { i, t -> t.toGraph("$i") }
    .fold(LabeledGraph()) { ac, lg -> ac + lg }.html()
  }.let { FreeMatrix(it) }

fun CFG.parseHTML(s: String): String = parseTable(s).toGraphTable().toHtmlPage()

fun String.isHoleToken() = this == "_" || (first() == '<' && last() == '>')

/*
Do we need Lee to do [en/de]coding? https://arxiv.org/pdf/cs/0112018.pdf#page=10
It seems Valiant gives a reduction from CFL parsing to BMM, i.e., CFL→BMM and
Lee shows that a faster procedure for BMM would automatically give a fast
procedure for CFL parsing, i.e., BMM⇄CFL. Once we can reduce from semirings to
BMM, encoding to SAT becomes straightforward using Tseitin (1968).

Lower this matrix onto SAT. Steps:
  1.) Encode CFL as BMM.
  2.) Symbolically evaluate BMM to get a Boolean formula.
  3.) Encode symbolic Boolean formula as CNF using Tsetin.
  4.) Run SAT solver and decode variable assignments.

  https://people.csail.mit.edu/virgi/6.s078/papers/valiant.pdf#page=13
  https://www.ps.uni-saarland.de/courses/seminar-ws06/papers/07_franziska_ebert.pdf#page=6
 */

fun CFG.constructInitialMatrix(
  tokens: List<String>,
  holeVariables: MutableList<List<Formula>> = mutableListOf(),
    // Precompute permanent upper right triangular submatrices
  literalUDM: UTMatrix<List<Boolean>?> = UTMatrix(
    ts = tokens.map { it ->
      if (it.isHoleToken()) null
      else bimap[listOf(it)].let { nts -> nonterminals.map { it in nts } }
    }.toTypedArray(),
    algebra = satLitAlgebra
  ).seekFixpoint(),
  literalMatrix: FreeMatrix<List<Boolean>?> = literalUDM.toFullMatrix()
    .map { if(it == null || toNTSet(it).isEmpty()) emptyList() else it },
  formulaMatrix: UTMatrix<List<Formula>> =
    FreeMatrix(satAlgebra, tokens.size + 1) { r, c ->
      if (r + 1 == c && tokens[c - 1].isHoleToken()) { // Superdiagonal
        val word = tokens[c - 1]
        if (word == "_")
          List(nonterminals.size) { k -> BVar("B_${r}_${c}_$k") }
            .also { holeVariables.add(it) } // Blank
        else setOf(word.drop(1).dropLast(1))
           .let { nts -> nonterminals.map { BLit(it in nts) } } // Terminal
      } else if (r + 1 <= c) { // Strictly upper triangular matrix entries
        val permanentBitVec = literalMatrix[r, c]
        if (permanentBitVec.isNullOrEmpty())
          List(nonterminals.size) { k -> BVar("B_${r}_${c}_$k") }
        else permanentBitVec.map { if (it) T else F }
      } else emptyList()
    }.toUTMatrix()
): Pair<UTMatrix<List<Formula>>, MutableList<List<Formula>>> =
    (formulaMatrix
//  .also { println("SAT matrix[$i]:\n${it.summarize(this)}") }
    to holeVariables)

@JvmName("summarizeBooleanMatrix")
fun FreeMatrix<List<Boolean>?>.summarize(cfg: CFG): String =
  map {
    when {
      it == null -> "?"
      it.toString().length < 5 -> ""
//      else -> "C"
      cfg.toNTSet(it).isEmpty() -> it.distinct()
      else -> "${cfg.toNTSet(it)}".replace("START", "S")
    }
  }.toString()

@JvmName("summarizeFormulaMatrix")
fun FreeMatrix<List<Formula>>.summarize(cfg: CFG): String =
  map {
    when {
      it.isEmpty() -> ""
      it.all { it is Variable } -> "V[${it.size}]"
      it.all { it is Constant } -> "C[${cfg.toNTSet(it.map { it == T })}]"
//      it.all { it is Constant } -> "C[${it.count { it == T }}/${it.size}]"
      it.any { it is Variable } -> "M"
      else -> "F[${it.sumOf(Formula::numberOfAtoms)}]"
    }
  }.toString()

// TODO: Compactify [en/de]coding: https://news.ycombinator.com/item?id=31442706#31442719
fun CFG.nonterminals(bitvec: List<Boolean>): Set<String> =
  bitvec.mapIndexedNotNull { i, it -> if (it) bindex[i] else null }.toSet()

fun CFG.terminal(
  bitvec: List<Boolean>,
  nonterminals: Set<String> = nonterminals(bitvec)
): String? = terminals.firstOrNull { word -> bimap[listOf(word)] == nonterminals }

// Summarize fill structure of bit vector variables
fun FreeMatrix<List<Formula>>.fillStructure(): FreeMatrix<String> =
  FreeMatrix(numRows, numCols) { r, c ->
    this[r, c].let {
      if (it.all { it == F }) "0"
      else if (it.all { it in setOf(T, F) }) "LV$r$c"
      else "BV$r$c[len=${it.toString().length}]"
    }
  }

/*
 * Treats contiguous underscores as a single hole and lazily enumerates every
 * hole configuration in the powerset of all holes within a snippet.
 * Original: ___w__w_w__w___ -> _w_w_w_w_
 * Variants: _wwww  _w_www _w_w_ww ... _w_w_w_w_
 *           w_www  _ww_ww _w_ww_w
 *           ww_ww  _www_w _w_www_
 *           ...    ...    ...
 */

fun String.everySingleHoleConfig(): Sequence<String> {
  val new = replace(Regex("(_( )*)+"), "_")
  val toks = new.toList().map { it.toString() }
  val indices = toks.indices.filter { toks[it] == "_" }.powerset()
  return indices.map { ids -> toks.drop(setOf("_"), ids).joinToString("") }
}

/*
 * Lazily enumerates all underscores chunkings in order of increasing length up
 * to the lesser of (1) its original size or (2) the longest underscore chunk.
 * Original: ___w__w_w__w___
 * Variants: _w_w_w_w_
 *           __w__w_w__w__
 *           ___w__w_w__w___
 */
fun String.increasingLengthChunks(): Sequence<String> {
  val chunks = mergeHoles().split(Regex("((?<=[^_])|(?=[^_]))"))
  return (2..chunks.maxOf { it.length }).asSequence()
    .map { l -> chunks.joinToString("") { if ("_" in it) it.take(l) else it } }
}

fun String.mergeHoles() = replace(Regex("(?<=_)\\s+(?=_)"), "")

fun String.synthesizeFrom(
  cfg: CFG,
  join: String = "",
  allowNTs: Boolean = true,
  variations: List<String.() -> Sequence<String>> = listOf { sequenceOf(this) }
): Sequence<String> {
  val cfg_ = cfg.let { if (allowNTs) it.generateStubs() else it }
  val allVariants = variations.fold(sequenceOf(this)) { a, b -> a + b() }.map { it.mergeHoles() }.distinct()
  return allVariants.flatMap { cfg_.run { synthesize(tokenize(it), join) } }.distinct()
}

fun Formula.toPython(
  params: String = variables().joinToString(", ") { it.name() },
  bodyY: String = toString().replace("~", "neg/").replace("|", "|V|").replace("&", "|Λ|"),
  bodyX: String = toString().replace("~", "not ").replace("|", "or").replace("&", "and")
) = """
def y_constr($params):
    return $bodyY
    
def x_constr($params):
    return $bodyX
""".trimIndent()

fun Map<Variable, Boolean>.toPython() =
  "assert x_constr(" + entries.joinToString(","){ (k, v) -> k.name() + "=" +
          v.toString().let { it[0].uppercase() + it.substring(1) } } + ")"

private fun CFG.handleSingleton(s: String): Sequence<String> =
  if (s == "_") terminals.asSequence()
  else if (s.matches(Regex("<.+>")))
    bimap[s.substring(1, s.length - 1)]
      .mapNotNull { if(it.size == 1) it[0] else null }.asSequence()
  else emptySequence()

private fun CFG.synthesize(tokens: List<String>, join: String = ""): Sequence<String> =
  if (tokens.none { it == "_" }) emptySequence()
  else if (tokens.size == 1) handleSingleton(tokens[0])
  else sequence {
    ff.clear()
    println("Synthesizing: " + tokens.joinToString(" "))
    val (matrix, holeVecVars) = constructInitialMatrix(tokens)
    val holeVars = holeVecVars.flatten().toSet()

    val fixpoint = matrix * matrix
//    println(fixpoint.summarize(this@synthesize))

    // TODO: Replace contiguous (i.e. hole-free) subexpressions with their corresponding
    //       nonterminal in the original string to reduce fixedpoint matrix size.
    val parsingConstraints =
      try {
        isInGrammar(matrix) and
          uniquenessConstraints(holeVecVars) and
          (matrix valiantMatEq fixpoint)
      } catch (e: Exception) { return@sequence }

//  Sometimes simplification can take longer or even switch SAT->UNSAT?
//  println("Original: ${parsingConstraints.numberOfNodes()}")
//  parsingConstraints = AdvancedSimplifier().apply(parsingConstraints, false)
//  parsingConstraints = BackboneSimplifier.get().apply(parsingConstraints, false)
//  println("Reduction: ${parsingConstraints.numberOfNodes()}")
//  println(parsingConstraints.cnf().toPython())


    var (solver, solution) =
      parsingConstraints.let { f ->
        try { f.solveIncrementally() }
        catch (npe: NullPointerException) { return@sequence }
      }
//    var freshnessConstraints = 0L
    while (true)
      try {
//      println(solution.toPython())
        val fillers = holeVecVars.map { bits -> terminal(bits.map { solution[it]!! }) }.toMutableList()

//      val bMat = FreeMatrix(matrix.data.map { it.map { if(it is Variable) solution[it]!! else if(it is Constant) it == T else false } as List<Boolean>? })
//      println(bMat.summarize(this@synthesize))
        val completion = tokens.map { if (it == "_") fillers.removeAt(0)!! else it }
          .filterNot { it == "ε" }.joinToString(join)

        if (completion.trim().isNotBlank()) yield(completion)

        val isFresh = solution.filter { (k, v) -> k in holeVars && v }.areFresh()
//        freshnessConstraints += isFresh.numberOfAtoms()
//        println("Freshness constraints: $freshnessConstraints")

        val model = solver.run { add(isFresh); sat(); model() }
        solution = solution.keys.associateWith { model.evaluateLit(it) }
      } catch (e: Exception) { break }

    ff.clear()
    elimFormulaFactory()
  }