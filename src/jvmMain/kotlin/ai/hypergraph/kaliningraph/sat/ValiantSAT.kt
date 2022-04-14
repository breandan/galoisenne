package ai.hypergraph.kaliningraph.sat

import ai.hypergraph.kaliningraph.parsing.*
import ai.hypergraph.kaliningraph.tensor.FreeMatrix
import ai.hypergraph.kaliningraph.types.*
import kotlin.collections.filter

@JvmName("joinFormula")
fun CFG.join(left: List<Formula>, right: List<Formula>): List<Formula> =
  List(left.size) { i ->
    bimap[variables.elementAt(i)].filter { 1 < it.size }.map { it[0] to it[1] }
      .map { (B, C) -> (left[variables.indexOf(B)] and right[variables.indexOf(C)]) }
      .fold(BLit(false)) { acc, satf -> acc or satf }
  }

@JvmName("joinBool")
fun CFG.join(left: List<Boolean>, right: List<Boolean>): List<Boolean> =
  List(left.size) { i ->
    bimap[variables.elementAt(i)].filter { 1 < it.size }.map { it[0] to it[1] }
      .map { (B, C) -> (left[variables.indexOf(B)] and right[variables.indexOf(C)]) }
      .fold(false) { acc, satf -> acc or satf }
  }

infix fun List<Formula>.union(that: List<Formula>): List<Formula> =
  List(size) { i -> this[i] or that[i] }

fun List<Boolean>.toLitVec(): List<Formula> = map { BLit(it) }

fun CFG.toBitVec(nonterminals: Set<String>): List<Boolean> = variables.map { it in nonterminals }
fun CFG.toNTSet(nonterminals: List<Boolean>): Set<String> =
  nonterminals.mapIndexedNotNull { i, it -> if(it) variables.elementAt(i) else null }.toSet()

fun List<Boolean>.decodeWith(cfg: CFG): Set<String> =
  mapIndexedNotNull { i, it -> if(it) cfg.variables.elementAt(i) else null }.toSet()

infix fun List<Formula>.vecEq(that: List<Formula>): Formula =
  zip(that).map { (a, b) -> a eq b }.reduce { acc, satf -> acc and satf }

infix fun FreeMatrix<List<Formula>>.matEq(that: FreeMatrix<List<Formula>>): Formula =
  data.zip(that.data).map { (a, b) -> a vecEq b }.reduce { acc, satf -> acc and satf }

infix fun FreeMatrix<List<Formula>>.fixedpointMatEq(that: FreeMatrix<List<Formula>>): Formula =
        List(numRows-2) {
            i -> List(numCols - i - 2) { j -> this[i, i + j + 2] vecEq that[i, i + j + 2] }
                .reduce{ acc, satf -> acc and satf }
        }.reduce {acc, satf -> acc and satf}

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

fun CFG.makeSATAlgebra() =
    Ring.of(
      nil = List(variables.size) { F },
      one = List(variables.size) { T },
      plus = { a, b -> a union b },
      times = { a, b -> join(a, b) }
    )

fun CFG.constructSATMatrix(
  words: List<String>,
  holeVariables: MutableList<List<Formula>> = mutableListOf(),
): Π2<FreeMatrix<List<Formula>>, MutableList<List<Formula>>> =
    FreeMatrix(makeSATAlgebra(), words.size + 1) { r, c ->
      if (c == r + 1) {
        val word = words[c - 1]
        if (word == "_") List(variables.size) { k -> BVar("B_${r}_${c}_$k") }
          .also { holeVariables.add(it) } // Blank
        else bimap[listOf(word)].let { nts -> variables.map { BLit(it in nts) } } // Terminal
      } else List(variables.size) { F }
    } to holeVariables

fun CFG.constructInitFixedpointMatrix(
        words: List<String>,
        holeVariables: MutableList<List<Formula>> = mutableListOf(),
): Π2<FreeMatrix<List<Formula>>, MutableList<List<Formula>>> =
    FreeMatrix(makeSATAlgebra(), words.size + 1) { r, c ->
      if (c == r + 1) {
        val word = words[c - 1]
        if (word == "_") List(variables.size) { k -> BVar("B_${r}_${c}_$k") }
                .also { holeVariables.add(it) } // Blank
        else bimap[listOf(word)].let { nts -> variables.map { BLit(it in nts) } } // Terminal
      } else if (c > r + 1) {
          List(variables.size) { k -> BVar("B_${r}_${c}_$k") }
      }
      else List(variables.size) { BLit(false) }
    } to holeVariables

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

fun <T> Set<T>.encodeAsMatrix(
  universe: Set<T>,
  rows: Int,
  cols: Int = universe.size,
) = 
  FreeMatrix(SAT_ALGEBRA, rows, cols) { i, j ->
    BLit(if (size <= i) false else elementAt(i) == universe.elementAt(j))
  }

// Depleted powerset, i.e., contains no empty sets
fun <T> Collection<T>.depletedPS(): Set<Set<T>> =
  if (1 < size) drop(1).depletedPS().let { it + it.map { it + first() } }
  else setOf(setOf(first()))

fun String.synthesizeFromFPSolving(cfg: CFG): Sequence<String> =
  sequence {
    val strToSolve = this@synthesizeFromFPSolving
    val words = strToSolve.map { "$it" }

    val (fixpointMatrix, holeVariables) = cfg.constructInitFixedpointMatrix(words)

    val valiantParses = cfg.run {
      cfg.isInGrammar(fixpointMatrix) and
        uniquenessConstraints(holeVariables) and
        (fixpointMatrix fixedpointMatEq (fixpointMatrix * fixpointMatrix))
    }

    var isFresh = T
    while (true)
      try {
        val solution = (valiantParses and isFresh).solve()
        isFresh = isFresh and
          holeVariables.map { bitVec -> bitVec.map { it neq BLit(solution[it]!!) }.reduce { acc, satf -> acc or satf } }
            .reduce { acc, satf -> acc or satf }
        val fillers = holeVariables.map { bitVec -> bitVec.map { solution[it]!! } }
          .map { cfg.terminal(it) }.toMutableList()

        yield(strToSolve.map { it }
          .joinToString("") { if (it == '_') fillers.removeAt(0)!! else "$it" })
      } catch (e: Exception) { e.printStackTrace(); break }
  }

fun String.synthesizeFrom(cfg: CFG): String {
  val strToSolve = this@synthesizeFrom
  val words = strToSolve.map { "$it" }
  val (initialMatrix, holeVariables) = cfg.constructSATMatrix(words)
  val decodeMat = FreeMatrix(initialMatrix.algebra, initialMatrix.numRows) { r, c ->
    List(initialMatrix.data.first().size) { BVar("D_${r}_${c}_$it") }
  }

//    println("Initial  matrix:\n${initialMatrix}")
//    val diag = initialMatrix.getElements { r, c -> c == r + 1 }
//    println("Index    :" + cfg.variables.joinToString(", ", "[", "]") { "'$it'".padEnd(8) })
//    diag.forEachIndexed { i, it -> println("BV$i${i+1}~`${words[i]}`: ${it.joinToString(", ", "[", "]") { "$it".padEnd(8) }}") }

  val fixpointMatrix = words.fold(initialMatrix) { acc, it -> acc + acc * acc }

//    println("Fixpoint matrix:\n${fixpointMatrix.fillStructure()}")
//    val fpDiag = fixpointMatrix.getElements { r, c -> c == r + 1 }
//    println("Index   : " + cfg.variables.joinToString(", ", "[", "]") { "'$it'".padEnd(8) })
//    fpDiag.forEachIndexed { i, it -> println("BV$i${i+1}~`${words[i]}`: ${it.joinToString(", ", "[", "]") { "$it".padEnd(8) }}") }

  val constraint = cfg.run {
    cfg.isInGrammar(fixpointMatrix) and
      uniquenessConstraints(holeVariables) and
      (fixpointMatrix matEq decodeMat)
  }

  val solution = constraint.solve()

//    println("Number of participating variables and resolved nonterminals")
//    FreeMatrix(initialMatrix.numRows) { r, c ->
//      val bitVec = decodeMat[r, c]
//      val decoded = bitVec.map { solution[it] }
//      if (decoded.all { it != null } && c == r + 1)
//        decoded.map { it!! }.let { bv ->
//          cfg.terminal(bv) + "=" + cfg.nonterminals(bv).joinToString(",", "[", "]")
//        }
//      else if (decoded.all { it == null }) {
//        if (bitVec.all { it == BLit(false) }) "0"
//        else if (bitVec.all { it in setOf(BLit(false), BLit(true)) })
//          cfg.terminal(bitVec.map { it.toBool()!! }) ?: "UNK"
//        else "MIX"
//      } else //if (r == 0 && c == initialMatrix.numCols - 1)
//        decoded.mapIndexedNotNull { i, b ->
//          when (b) {
//            true -> cfg.variables.elementAt(i)
//            null -> cfg.variables.elementAt(i) + "?"
//            else -> null
//          }
//        }.joinToString(",", "[", "]")
//    }.also { println("$it\n") }

  val fillers: MutableList<String?> = holeVariables.map { bitVec ->
    cfg.terminal(bitVec.map { solution[it]!! })
  }.toMutableList()

  return strToSolve.map { it }
    .joinToString("") { if (it == '_') fillers.removeAt(0)!! else "$it" }
}