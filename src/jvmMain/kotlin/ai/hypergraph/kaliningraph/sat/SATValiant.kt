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
import kotlin.collections.filter

@JvmName("joinFormula")
fun CFG.join(left: List<Formula>, right: List<Formula>): List<Formula> =
  if (left.isEmpty() || right.isEmpty()) emptyList()
  else List(left.size) { i ->
    bimap[variables.elementAt(i)].filter { 1 < it.size }.map { it[0] to it[1] }
      .map { (B, C) -> left[variables.indexOf(B)] and right[variables.indexOf(C)] }
      .fold(BLit(false)) { acc, satf -> acc or satf }
  }

@JvmName("joinBitVector")
fun CFG.join(left: List<Boolean>, right: List<Boolean>): List<Boolean> =
  if (left.isEmpty() || right.isEmpty()) emptyList()
  else List(left.size) { i ->
    bimap[variables.elementAt(i)].filter { 1 < it.size }.map { it[0] to it[1] }
      .map { (B, C) -> left[variables.indexOf(B)] and right[variables.indexOf(C)] }
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

fun CFG.toBitVec(nonterminals: Set<String>): List<Boolean> = variables.map { it in nonterminals }
fun CFG.toNTSet(nonterminals: List<Boolean>): Set<String> =
  nonterminals.mapIndexedNotNull { i, it -> if(it) variables.elementAt(i) else null }.toSet()

fun List<Boolean>.decodeWith(cfg: CFG): Set<String> =
  mapIndexedNotNull { i, it -> if(it) cfg.variables.elementAt(i) else null }.toSet()

fun List<Formula>.allFalse(): Formula = reduce { acc, satf -> acc or satf }.negate()

infix fun List<Formula>.vecEq(that: List<Formula>): Formula =
  if (isEmpty() && that.isEmpty()) T
  else if (isEmpty()) that.allFalse() else if (that.isEmpty()) this.allFalse()
  else zip(that).map { (a, b) -> a eq b }.reduce { acc, satf -> acc and satf }

infix fun FreeMatrix<List<Formula>>.matEq(that: FreeMatrix<List<Formula>>): Formula =
  data.zip(that.data).map { (a, b) -> a vecEq b }.reduce { acc, satf -> acc and satf }

infix fun FreeMatrix<List<Formula>>.fixedpointMatEq(that: FreeMatrix<List<Formula>>): Formula =
  List(numRows - 2) {
    i -> List(numCols - i - 2) { j -> this[i, i + j + 2] vecEq that[i, i + j + 2] }
        .reduce { acc, satf -> acc and satf }
  }.reduce { acc, satf -> acc and satf }

fun CFG.isInGrammar(mat: FreeMatrix<List<Formula>>): Formula =
  mat[0].last()[variables.indexOf(START_SYMBOL)]

// Encodes the constraint that a bit-vector representing a unary production
// should not contain mixed nonterminals e.g. given A->(, B->(, C->), D->)
// grammar, the bitvector must not have the configuration [A=1 B=1 C=0 D=1],
// it should be either [A=1 B=1 C=0 D=0] or [A=0 B=0 C=1 D=1].
fun CFG.mustBeOnlyOneTerminal(bitvec: List<Formula>): Formula =
  // terminal        set of nonterminals it can represent
  alphabet.map { bimap[listOf(it)] }.map { nts ->
    val (insiders, outsiders) = variables.partition { it in nts }
    (insiders.map { nt -> bitvec[variables.indexOf(nt)] } + // All of these
      outsiders.map { nt -> bitvec[variables.indexOf(nt)].negate() }) // None of these
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
    nil = List(variables.size) { F },
    one = List(variables.size) { T },
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

fun CFG.constructInitialMatrix(
  tokens: List<String>,
  holeVariables: MutableList<List<Formula>> = mutableListOf(),
  // Precompute permanent upper diagonal submatrices
  literalMatrix: FreeMatrix<List<Boolean>?> =
    FreeMatrix(makeSATLitAlgebra(), tokens.size + 1) { r, c ->
      if (c == r + 1) {
        if (tokens[c - 1].isHoleToken()) null
        else toBitVec(setOf(tokens[c - 1]))
      } else emptyList()
    }.seekFixpoint {
      // println("Literal matrix:\n${it.summarize()}")
      it + it * it
    }
): Π2<FreeMatrix<List<Formula>>, MutableList<List<Formula>>> =
  (FreeMatrix(makeSATAlgebra(), tokens.size + 1) { r, c ->
    if (c == r + 1) {
      val word = tokens[c - 1]
      if (word == "_")
        List(variables.size) { k -> BVar("B_${r}_${c}_$k") }
          .also { holeVariables.add(it) } // Blank
      else if (word.startsWith("<") && word.endsWith(">"))
        setOf(word.drop(1).dropLast(1)).let { nts -> variables.map { BLit(it in nts) } } // Terminal
      else bimap[listOf(word)].let { nts -> variables.map { BLit(it in nts) } } // Terminal
    }
    else if (c > r + 1) {
      val permanentBitVec = literalMatrix[r, c]
      if (permanentBitVec == null || permanentBitVec.isEmpty())
        List(variables.size) { k -> BVar("B_${r}_${c}_$k") }
      else permanentBitVec.map { if(it) T else F }
    }
    else emptyList()
  }
  //.also { println("SAT matrix[$i]:\n${it.summarize()}") }
  to holeVariables)

@JvmName("summarizeBooleanMatrix")
fun FreeMatrix<List<Boolean>?>.summarize() =
  map {
    when {
      it == null -> "?"
      it.toString().length < 5 -> ""
      else -> "1"
    }
  }

@JvmName("summarizeFormulaMatrix")
fun FreeMatrix<List<Formula>>.summarize() =
  map {
    when {
      it.isEmpty() -> ""
      it[0] is Variable -> "V"
      it[0] is Constant -> "C"
      else -> "?"
    }
  }

fun CFG.nonterminals(bitvec: List<Boolean>): Set<String> =
  bitvec.mapIndexedNotNull { i, it -> if (it) variables.elementAt(i) else null }.toSet()

fun CFG.terminal(
  bitvec: List<Boolean>,
  nonterminals: Set<String> = nonterminals(bitvec)
): String? = alphabet.firstOrNull { word -> bimap[listOf(word)] == nonterminals }

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
     .run { synthesize(tokenize(this@synthesizeFrom, join)) }

private fun CFG.synthesize(tokens: List<String>, join: String = ""): Sequence<String> =
  sequence {
    val (fixpointMatrix, holeVariables) = constructInitialMatrix(tokens)

    val valiantParses =
      isInGrammar(fixpointMatrix) and
        uniquenessConstraints(holeVariables) and
        (fixpointMatrix fixedpointMatEq fixpointMatrix * fixpointMatrix)

    var (solver, solution) = valiantParses.let { f ->
      try { f.solveIncrementally() } catch (npe: NullPointerException) { return@sequence }
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