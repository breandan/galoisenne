package ai.hypergraph.kaliningraph.sat

import ai.hypergraph.kaliningraph.graphs.*
import ai.hypergraph.kaliningraph.image.toHTML
import ai.hypergraph.kaliningraph.parsing.*
import ai.hypergraph.kaliningraph.tensor.*
import ai.hypergraph.kaliningraph.types.*
import ai.hypergraph.kaliningraph.visualization.*
import org.logicng.formulas.Constant
import org.logicng.formulas.Formula
import org.logicng.formulas.Variable
import org.logicng.transformations.qmc.QuineMcCluskeyAlgorithm
import kotlin.collections.filter

@JvmName("joinFormula")
fun CFG.join(left: List<Formula>, right: List<Formula>): List<Formula> =
  if (left.isEmpty() || right.isEmpty()) emptyList()
  else List(left.size) { i ->
    bimap[nonterminals.elementAt(i)].filter { 1 < it.size }.map { it[0] to it[1] }
      .map { (B, C) -> left[nonterminals.indexOf(B)] and right[nonterminals.indexOf(C)] }
      .fold(BLit(false)) { acc, satf -> acc or satf }
  }

@JvmName("joinBitVector")
fun CFG.join(left: List<Boolean>, right: List<Boolean>): List<Boolean> =
  if (left.isEmpty() || right.isEmpty()) emptyList()
  else List(left.size) { i ->
    bimap[nonterminals.elementAt(i)].filter { 1 < it.size }.map { it[0] to it[1] }
      .map { (B, C) -> left[nonterminals.indexOf(B)] and right[nonterminals.indexOf(C)] }
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
  nts.mapIndexedNotNull { i, it -> if(it) nonterminals.elementAt(i) else null }.toSet()

fun List<Boolean>.decodeWith(cfg: CFG): Set<String> =
  mapIndexedNotNull { i, it -> if(it) cfg.nonterminals.elementAt(i) else null }.toSet()

infix fun List<Formula>.vecEq(that: List<Formula>): Formula =
  if (isEmpty() || that.isEmpty() || size != that.size) throw Exception("Shape mismatch!")
  else zip(that).map { (a, b) -> a eq b }.reduce { acc, satf -> acc and satf }

infix fun FreeMatrix<List<Formula>>.matEq(that: FreeMatrix<List<Formula>>): Formula =
  data.zip(that.data).filter { (l, r) -> l.isNotEmpty() && r.isNotEmpty() }
    .map { (a, b) -> a vecEq b }.reduce { acc, satf -> acc and satf }

fun CFG.isInGrammar(mat: FreeMatrix<List<Formula>>): Formula =
  mat[0].last()[nonterminals.indexOf(START_SYMBOL)]

// Encodes the constraint that a bit-vector representing a unary production
// should not contain mixed nonterminals e.g. given A->(, B->(, C->), D->)
// grammar, the bitvector must not have the configuration [A=1 B=1 C=0 D=1],
// it should be either [A=1 B=1 C=0 D=0] or [A=0 B=0 C=1 D=1].
fun CFG.mustBeOnlyOneTerminal(bitvec: List<Formula>): Formula =
  // terminal        set of nonterminals it can represent
  terminals.map { bimap[listOf(it)] }.map { nts ->
    val (insiders, outsiders) = nonterminals.partition { it in nts }
    (insiders.map { nt -> bitvec[nonterminals.indexOf(nt)] } + // All of these
      outsiders.map { nt -> bitvec[nonterminals.indexOf(nt)].negate() }) // None of these
      .reduce { acc, satf -> acc and satf }
  }.reduce { acc, satf -> acc xor satf }

// Encodes that each blank can only be one nonterminal
fun CFG.uniquenessConstraints(holeVariables: List<List<Formula>>): Formula =
  holeVariables.map { bitvec -> mustBeOnlyOneTerminal(bitvec) }
    .fold(T) { acc, it -> acc and it }

fun CFG.makeSATLitAlgebra(): Ring<List<Boolean>?> =
  Ring.of(
    nil = List(nonterminals.size) { false },
    plus = { x, y -> maybeUnion(x, y) },
    times = { x, y -> maybeJoin(x, y) }
  )

fun CFG.makeSATAlgebra() =
  Ring.of(
    nil = List(nonterminals.size) { F },
    one = List(nonterminals.size) { T },
    plus = { a, b -> a union b },
    times = { a, b -> join(a, b) }
  )

fun FreeMatrix<Set<Tree>>.toGraphTable(): FreeMatrix<String> =
  data.map {
    it.mapIndexed { i, t -> t.toGraph("$i") }
    .fold(LabeledGraph()) { ac, lg -> ac + lg }.html()
  }.let { FreeMatrix(it) }

fun CFG.parseHTML(s: String): String = parseTable(s).toGraphTable().toHTML()

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
  literalMatrix: FreeMatrix<List<Boolean>?> =
    FreeMatrix(makeSATLitAlgebra(), tokens.size + 1) { r, c ->
      if (r + 1 == c) {
        val word = tokens[c - 1]
        if (tokens[c - 1].isHoleToken()) null
        else bimap[listOf(word)].let { nts -> nonterminals.map { it in nts } }
      } else emptyList()
    }
      //.also { println("Literal matrix:\n${it.summarize()}") }
      .seekFixpoint { it + it * it },
  formulaMatrix: FreeMatrix<List<Formula>> =
    FreeMatrix(makeSATAlgebra(), tokens.size + 1) { r, c ->
      if (r + 1 == c && tokens[c - 1].isHoleToken()) { // Off-diagonal
        val word = tokens[c - 1]
        if (word == "_")
          List(nonterminals.size) { k -> BVar("B_${r}_${c}_$k") }
            .also { holeVariables.add(it) } // Blank
        else setOf(word.drop(1).dropLast(1))
           .let { nts -> nonterminals.map { BLit(it in nts) } } // Terminal
      } else if (r + 1 <= c) { // Upper right triangular matrix entries
        val permanentBitVec = literalMatrix[r, c]
        if (permanentBitVec.isNullOrEmpty())
          List(nonterminals.size) { k -> BVar("B_${r}_${c}_$k") }
        // else if (literalMatrix.isFullyResolved(r, c)) emptyList()
        else permanentBitVec.map { if (it) T else F }
      } else emptyList()
    }
): Π2<FreeMatrix<List<Formula>>, MutableList<List<Formula>>> =
    (formulaMatrix
  //.also { println("SAT matrix[$i]:\n${it.summarize()}") }
    to holeVariables)

// Try "hollowing out" permanent UT submatrices
//...
//  V V V V V      V V V V V
//    C C C V        C C C V
//      C C V  ->        C V
//        C V            C V
//          V              V
// Returns whether the entries to the left and bottom are fully resolved
private fun FreeMatrix<List<Boolean>?>.isFullyResolved(r: Int, c: Int) =
  (
    if (r + 1 == c && 0 < r && c + 1 < numCols)
      listOf(this[r, c + 1], this[r - 1, c])
    else if(r + 1 == c && r == 0)
      listOf(this[r, c + 1])
    else if(r + 1 == c && c + 1 == numCols)
      listOf(this[r - 1, c])
    else
    if (numCols <= c + 1 && 0 < r)
      listOf(this[r, c], this[r - 1, c], this[r, c - 1])
    else if (0 == r && c + 1 < numCols)
      listOf(this[r, c], this[r, c + 1], this[1, c])
    else if (0 < r && c + 1 < numCols)
      listOf(this[r, c], this[r - 1, c], this[r + 1, c], this[r, c - 1], this[r, c + 1])
    else listOf(emptyList())
  ).all { it?.isNotEmpty() ?: false }

@JvmName("summarizeBooleanMatrix")
fun FreeMatrix<List<Boolean>?>.summarize() =
  map {
    when {
      it == null -> "?"
      it.toString().length < 5 -> ""
      else -> "C"
    }
  }

@JvmName("summarizeFormulaMatrix")
fun FreeMatrix<List<Formula>>.summarize() =
  map {
    when {
      it.isEmpty() -> ""
      it[0] is Variable -> "V"
      it[0] is Constant -> "C"
      else -> "F"
    }
  }

fun CFG.nonterminals(bitvec: List<Boolean>): Set<String> =
  bitvec.mapIndexedNotNull { i, it -> if (it) nonterminals.elementAt(i) else null }.toSet()

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

val SAT_ALGEBRA =
  Ring.of(
    nil = BLit(false),
    one = BLit(true),
    plus = { a, b -> a or b },
    times = { a, b -> a and b }
  )

fun String.synthesizeFrom(cfg: CFG, join: String = "", allowNTs: Boolean = true): Sequence<String> =
  cfg.let { if (allowNTs) it.generateStubs() else it }
     .run { synthesize(tokenize(this@synthesizeFrom), join) }

private fun CFG.synthesize(tokens: List<String>, join: String = ""): Sequence<String> =
  sequence {
    val (fixpointMatrix, holeVariables) = constructInitialMatrix(tokens)

    val valiantParses =
      isInGrammar(fixpointMatrix) and
        uniquenessConstraints(holeVariables) and
        (fixpointMatrix matEq fixpointMatrix * fixpointMatrix)

    var (solver, solution) = valiantParses.let { f ->
      try { f.solveIncrementally() }
      catch (npe: NullPointerException) { return@sequence }
    }
    var isFresh = T
    while (true)
      try {
        val fillers = holeVariables.map { bits -> terminal(bits.map { solution[it]!! }) }.toMutableList()

        yield(tokens.joinToString(join) { if (it == "_") fillers.removeAt(0)!! else it })

        val holes = holeVariables.flatten()
        isFresh = isFresh and solution.filter { it.key in holes }.areFresh()

        val model = solver.run { add(isFresh); sat(); model() }
        solution = solution.keys.associateWith { model.evaluateLit(it) }
      } catch (e: Exception) { e.printStackTrace(); break }

    ff.clear()
  }