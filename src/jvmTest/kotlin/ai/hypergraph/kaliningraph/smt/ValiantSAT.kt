package ai.hypergraph.kaliningraph.smt

import ai.hypergraph.kaliningraph.parsing.*
import ai.hypergraph.kaliningraph.tensor.FreeMatrix
import ai.hypergraph.kaliningraph.types.*
import kotlin.collections.filter


@JvmName("joinSATF")
fun CFG.join(left: List<SATF>, right: List<SATF>): List<SATF> =
  List(left.size) { i ->
    bimap[variables.elementAt(i)].filter { 1 < it.size }.map { it[0] to it[1] }
      .map { (B, C) -> (left[variables.indexOf(B)] and right[variables.indexOf(C)]) }
      .fold(left[0].ctx.Literal(false)) { acc, satf -> acc or satf }
  }

@JvmName("joinBool")
fun CFG.join(left: List<Boolean>, right: List<Boolean>): List<Boolean> =
  List(left.size) { i ->
    bimap[variables.elementAt(i)].filter { 1 < it.size }.map { it[0] to it[1] }
      .map { (B, C) -> (left[variables.indexOf(B)] and right[variables.indexOf(C)]) }
      .fold(false) { acc, satf -> acc or satf }
  }

infix fun List<SATF>.union(that: List<SATF>): List<SATF> =
  List(size) { i -> this[i] or that[i] }

fun List<Boolean>.toLitVec(using: SMTInstance): List<SATF> =
  with(using) { map { Literal(it) } }

fun CFG.toBitVec(nonterminals: Set<String>): List<Boolean> = variables.map { it in nonterminals }
fun CFG.toNTSet(nonterminals: List<Boolean>): Set<String> =
  nonterminals.mapIndexedNotNull { i, it -> if(it) variables.elementAt(i) else null }.toSet()

fun List<Boolean>.decodeWith(cfg: CFG): Set<String> =
  mapIndexedNotNull { i, it -> if(it) cfg.variables.elementAt(i) else null }.toSet()

infix fun List<SATF>.vecEq(that: List<SATF>): SATF =
  zip(that).map { (a, b) -> a eq b }.reduce { acc, satf -> acc and satf }

infix fun FreeMatrix<List<SATF>>.matEq(that: FreeMatrix<List<SATF>>): SATF =
  data.zip(that.data).map { (a, b) -> a vecEq b }.reduce { acc, satf -> acc and satf }

fun CFG.isInGrammar(mat: FreeMatrix<List<SATF>>): SATF =
  mat[0].last()[variables.indexOf(START_SYMBOL)]

// Encodes the constraint that a bit-vector representing a unary production
// should not contain mixed nonterminals e.g. given A->(, B->(, C->), D->)
// grammar, the bitvector must not have the configuration [A=1 B=1 C=0 D=1],
// it should be either [A=1 B=1 C=0 D=0] or [A=0 B=0 C=1 D=1].
fun CFG.mustBeOnlyOneTerminal(bitvec: List<SATF>): SATF =
  // terminal        set of nonterminals it can represent
  alphabet.map { bimap[listOf(it)] }.map { nts ->
    val (insiders, outsiders) = variables.partition { it in nts }
    (insiders.map { nt -> bitvec[variables.indexOf(nt)] } + // All of these
      outsiders.map { nt -> bitvec[variables.indexOf(nt)].negate() }) // None of these
      .reduce { acc, satf -> acc and satf }
  }.reduce { acc, satf -> acc xor satf }

// Encodes that each blank can only be one nonterminal
fun CFG.uniquenessConstraints(
  smtInstance: SMTInstance,
  holeVariables: List<List<SATF>>
): SATF =
  holeVariables.map { bitvec -> mustBeOnlyOneTerminal(bitvec) }
    .fold(smtInstance.Literal(true)) { acc, it -> acc and it }

fun CFG.makeSATAlgebra(smtInstance: SMTInstance) =
  with(smtInstance) {
    Ring.of(
      nil = List(variables.size) { Literal(false) },
      one = List(variables.size) { Literal(false) },
      plus = { a, b -> a union b },
      times = { a, b -> join(a, b) }
    )
  }

fun CFG.constructSATMatrix(
  smtInstance: SMTInstance,
  words: List<String>,
  holeVariables: MutableList<List<SATF>> = mutableListOf(),
): Π2<FreeMatrix<List<SATF>>, MutableList<List<SATF>>> =
  with(smtInstance) {
    FreeMatrix(makeSATAlgebra(smtInstance), words.size + 1) { r, c ->
      if (c == r + 1) {
        val word = words[c - 1]
        if (word == "_") List(variables.size) { k -> BoolVar("B.$r.$c.$k") }
          .also { holeVariables.add(it) } // Blank
        else bimap[listOf(word)].let { nts -> variables.map { Literal(it in nts) } } // Terminal
      } else List(variables.size) { Literal(false) }
    } to holeVariables
  }


fun CFG.nonterminals(bitvec: List<Boolean>): Set<String> =
  bitvec.mapIndexedNotNull { i, it -> if (it) variables.elementAt(i) else null }.toSet()

fun CFG.terminal(
  bitvec: List<Boolean>,
  nonterminals: Set<String> = nonterminals(bitvec)
): String? = alphabet.firstOrNull { word -> bimap[listOf(word)] == nonterminals }

// Summarize fill structure of bit vector variables
fun FreeMatrix<List<SATF>>.fillStructure(): FreeMatrix<String> =
  FreeMatrix(numRows, numCols) { r, c ->
    this[r, c].let {
      if (it.all { "$it" == "false" }) "0"
      else if (it.all { "$it" in setOf("false", "true") }) "LV$r$c"
      else "BV$r$c[len=${it.toString().length}]"
    }
  }

fun <T> Set<T>.encodeAsMatrix(
  smtInstance: SMTInstance,
  universe: Set<T>,
  rows: Int,
  cols: Int = universe.size,
) = with(smtInstance) {
  FreeMatrix(SAT_ALGEBRA, rows, cols) { i, j ->
    Literal(if (size <= i) false else elementAt(i) == universe.elementAt(j))
  }
}

// Depleted powerset, i.e., contains no empty sets
fun <T> Collection<T>.depletedPS(): Set<Set<T>> =
  if (1 < size) drop(1).depletedPS().let { it + it.map { it + first() } }
  else setOf(setOf(first()))

fun String.synthesizeFrom(cfg: CFG, smtInstance: SMTInstance): String = with(smtInstance) {
  val strToSolve = this@synthesizeFrom
  val words = strToSolve.map { "$it" }
  val (initialMatrix, holeVariables) = cfg.constructSATMatrix(this, words)
  val decodeMat = FreeMatrix(initialMatrix.algebra, initialMatrix.numRows) { r, c ->
    List(initialMatrix.data.first().size) { BoolVar("D.$r.$c.$it") }
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
      uniquenessConstraints(this@with, holeVariables) and
      (fixpointMatrix matEq decodeMat)
  }

  val solution = solveBoolean(constraint)

//    println("Number of participating variables and resolved nonterminals")
//    FreeMatrix(initialMatrix.numRows) { r, c ->
//      val bitVec = decodeMat[r, c]
//      val decoded = bitVec.map { solution[it] }
//      if (decoded.all { it != null } && c == r + 1)
//        decoded.map { it!! }.let { bv ->
//          cfg.terminal(bv) + "=" + cfg.nonterminals(bv).joinToString(",", "[", "]")
//        }
//      else if (decoded.all { it == null }) {
//        if (bitVec.all { it == Literal(false) }) "0"
//        else if (bitVec.all { it in setOf(Literal(false), Literal(true)) })
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

  val decodedString = strToSolve.map { it }
    .joinToString("") { if (it == '_') fillers.removeAt(0)!! else "$it" }
  return decodedString
}