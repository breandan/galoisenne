package ai.hypergraph.kaliningraph.sat

import ai.hypergraph.kaliningraph.graphs.*
import ai.hypergraph.kaliningraph.parsing.*
import ai.hypergraph.kaliningraph.tensor.*
import ai.hypergraph.kaliningraph.types.*
import ai.hypergraph.kaliningraph.visualization.*
import org.logicng.formulas.Formula
import kotlin.collections.filter

typealias SATVector = List<Formula>
typealias SATRubix = UTMatrix<SATVector>

@JvmName("joinFormula")
fun CFG.join(left: SATVector, right: SATVector): SATVector =
  if (left.isEmpty() || right.isEmpty()) emptyList()
  else List(left.size) { i ->
    bimap[bindex[i]].filter { 1 < it.size }.map { it[0] to it[1] }
      .map { (B, C) -> left[bindex[B]] and right[bindex[C]] }
      .fold(F) { acc, satf -> acc or satf }
  }

@JvmName("satFormulaUnion")
infix fun SATVector.union(that: SATVector): SATVector =
  if (isEmpty()) that else if (that.isEmpty()) this
  else List(size) { i -> this[i] or that[i] }

fun List<Boolean>.toLitVec(): SATVector = map { BLit(it) }

infix fun SATVector.vecEq(that: SATVector): Formula =
  if (isEmpty() || that.isEmpty() || size != that.size) throw Exception("Shape mismatch!")
  else if (this == that) T
  else zip(that).partition { (l, r) -> l == r }
//    .also { (a, b) -> if (a.isNotEmpty()) println("Eliminated ${a.size}/${a.size + b.size} identical SAT variables") }
    .second.map { (a, b) -> a eq b }
    .let { if (it.isEmpty()) T else it.reduce { acc, satf -> acc and satf } }

infix fun SATRubix.valiantMatEq(that: SATRubix): Formula =
  if (shape() != that.shape()) throw Exception("Shape mismatch, incomparable!")
  else diagonals.flatten().zip(that.diagonals.flatten())
    .filter { (l, r) -> l.isNotEmpty() && r.isNotEmpty() }
    .map { (a, b) -> a vecEq b }.reduce { acc, satf -> acc and satf }

fun CFG.isInGrammar(mat: SATRubix): Formula =
  mat.diagonals.last().first()[bindex[START_SYMBOL]] and
    mat.let { it valiantMatEq (it * it)
//    .also { println(it.toFullMatrix().summarize(this)) }
    }

// Encodes the constraint that bit-vectors representing a unary production
// should not contain mixed NT symbols, e.g., given A->(, B->(, C->), D->)
// the bitvector cannot have the configuration [A=1 B=1 C=0 D=1], it must
// be either [A=1 B=1 C=0 D=0] or [A=0 B=0 C=1 D=1].
fun CFG.mustBeOnlyOneTerminal(bitvec: SATVector): Formula =
  // terminal        possible nonterminals it can represent
  terminals.map { bitvec.join(bimap[listOf(it)], nonterminals) }.map { possibleNTs ->
    val (insiders, outsiders) = bitvec.join(nonterminals).partition { it in possibleNTs }
    (insiders + outsiders.map { it.negate() }).reduce { acc, satf -> acc and satf }
  }.reduce { acc, satf -> acc xor satf }

// Returns list elements matching the intersection between set and on (indexed by on)
fun <E, T> List<E>.join(set: Set<T>, on: Set<T> = set): Set<E> =
  if (size != on.size) throw Exception("Size mismatch: List[$size] != Set[${on.size}]")
  else set.intersect(on).map { this[on.indexOf(it)] }.toSet()

// Encodes that each blank can only be a single terminal
fun CFG.uniquenessConstraints(holeVariables: List<SATVector>): Formula =
  holeVariables.map { bitvec -> mustBeOnlyOneTerminal(bitvec) }
    .fold(T) { acc, it -> acc and it }
//    .also { println("Uniqueness constraints: ${it.numberOfAtoms()}") }

// Encodes that nonterminal stubs can only be replaced by reachable nonterminals
fun CFG.reachabilityConstraints(tokens: List<String>, holeVariables: List<SATVector>): Formula =
  tokens.filter { it.isHoleTokenIn(cfg = this) }.zip(holeVariables)
    .filter { (word, _) -> word.isNonterminalStubIn(cfg = this) }
    .map { (nonterminalStub, hf) ->
      val nt = nonterminalStub.drop(1).dropLast(1)
      nonparametricForm.reachableSymbols(from = nt)
        .also { println("Transitive closure: $nt ->* $it") }
        .map { hf eq BVecLit(toBitVec(setOf(it))) }
        .fold(F) { a, b -> a xor b }
    }.flatten().fold(T) { a, b -> a and b }

val CFG.satAlgebra by cache {
  Ring.of(
    nil = emptyList(),
    one = List(nonterminals.size) { T },
    plus = { a, b -> a union b },
    times = { a, b -> join(a, b) }
  )
}

// Precomputes literals in the fixpoint to avoid solving for invariant entries
fun CFG.constructInitialMatrix(
  tokens: List<String>,
  stringVars: MutableList<SATVector> = mutableListOf(),
  // Precompute permanent upper right triangular submatrices
  literalUDM: UTMatrix<List<Boolean>?> = UTMatrix(
    ts = tokens.map { it ->
      // Nulls on the superdiagonal will cast either a rectangular or pentagonal
      // shadow of bitvector variables on UTMatrix, which we represent as nulls
      if (it.isHoleTokenIn(cfg = this)) null
      // Terminals will cast a triangular shadow of bitvector literals on UTMatrix
      else bimap[listOf(it)].let { nts -> nonterminals.map { it in nts } }
    }.toTypedArray(),
    algebra = satLitAlgebra
  ).seekFixpoint(),
  literalMatrix: FreeMatrix<List<Boolean>?> = literalUDM.toFullMatrix()
    .map { if (it == null || toNTSet(it).isEmpty()) emptyList() else it },
  rubix: SATRubix =
    FreeMatrix(satAlgebra, tokens.size + 1) { r, c ->
      // Superdiagonal
      if (r + 1 == c && tokens[c - 1].isHoleTokenIn(cfg = this))
        BVecVar("B_${r}_${c}", nonterminals.size).also { stringVars.add(it) }
      // Strictly upper triangular matrix entries
      else if (r + 1 <= c) {
        val permanentBitVec = literalMatrix[r, c]
        if (permanentBitVec.isNullOrEmpty()) BVecVar("B_${r}_${c}_${hashCode()}", nonterminals.size)
        else permanentBitVec.map { if (it) T else F }
      }
      // Diagonal and subdiagonal
      else emptyList()
    }.toUTMatrix(),
): Pair<SATRubix, List<SATVector>> =
   /** TODO: Remove unnecessary return value [stringVars] and decode from naming scheme where needed*/
  (rubix to stringVars)
//  .also { println("SAT matrix[$i]:\n${it.first.toFullMatrix().summarize(this)}") }

/** Currently just a JVM wrapper around the multiplatform [synthesizeWithVariations] */
fun String.synthesizeIncrementally(
  cfg: CFG,
  allowNTs: Boolean = true,
  enablePruning: Boolean = false,
  variations: List<String.() -> Sequence<String>> = listOf { sequenceOf() },
  updateProgress: (String) -> Unit = {},
  skipWhen: (List<String>) -> Boolean = { false }
): Sequence<String> = synthesizeWithVariations(
  cfg, allowNTs, enablePruning, variations, updateProgress, skipWhen,
  synthesizer = { a, b -> synthesize(a, b) }
)

// TODO: Compactify [en/de]coding: https://news.ycombinator.com/item?id=31442706#31442719
fun CFG.nonterminals(bitvec: List<Boolean>): Set<String> =
  bitvec.mapIndexedNotNull { i, it -> if (it) bindex[i] else null }.toSet()

private fun CFG.handleSingleton(s: String): Set<String> =
  if (s == "_") terminals
  else if (s.matches(Regex("<.+>")))
    bimap[s.substring(1, s.length - 1)]
      .mapNotNull { if (it.size == 1) it[0] else null }.toSet()
  else setOf()

/*
Does Lee's method give demonstrable speedup? https://arxiv.org/pdf/cs/0112018.pdf#page=10
It seems Valiant gives a reduction from CFL parsing to BMM, i.e., CFL→BMM and
Lee shows that a faster procedure for BMM would automatically give a fast
procedure for CFL parsing, i.e., BMM⇄CFL.

Lowers Valiant matrix onto SAT. Steps:
  1.) Encode CFL as BMM.
  2.) Symbolically evaluate BMM to get a Boolean formula.
  3.) Encode symbolic Boolean formula as CNF using Tsetin.
  4.) Run SAT solver and decode variable assignments.

  https://people.csail.mit.edu/virgi/6.s078/papers/valiant.pdf#page=13
  https://www.ps.uni-saarland.de/courses/seminar-ws06/papers/07_franziska_ebert.pdf#page=6
 */

fun CFG.synthesize(
  tokens: List<String>,
  // Used to restore well-formed trees that were pruned
  reconstructor: Reconstructor = mutableListOf()
): Sequence<String> = asCSL.synthesize(tokens, reconstructor)

fun CSL.synthesize(
  tokens: List<String>,
  // Used to restore well-formed trees that were pruned
  reconstructor: Reconstructor = mutableListOf()
): Sequence<String> =
  check(tokens.all { it in symbols || it == "_" || it.startsWith('<') && it.endsWith('>') }) { "All tokens passed into synthesize() must be in all CFGs" }.let {
    if (tokens.none { it.isHoleTokenIn(cfg = cfgs.first()) }) emptySequence()
    else if (tokens.size == 1)
      cfgs.map { it.handleSingleton(tokens[0]) }.intersect().asSequence()
    else sequence {
      val (parsingConstraints, holeVecVars) = generateConstraints(tokens)
      val holeVars = holeVecVars.flatten().toSet()

  // Tries to put ε in as many holes as possible to prioritize simple repairs
  // val softConstraints = holeVecVars.map { it[nonterminals.indexOf("EPSILON_DO_NOT_USE")] }

  // Sometimes simplification can take longer or even switch SAT->UNSAT?
  // println("Original: ${parsingConstraints.numberOfNodes()}")
  // parsingConstraints = AdvancedSimplifier().apply(parsingConstraints, false)
  // parsingConstraints = BackboneSimplifier.get().apply(parsingConstraints, false)
  // println("Reduction: ${parsingConstraints.numberOfNodes()}")
  // println(parsingConstraints.cnf().toPython())

      var (solver, solution) =
        parsingConstraints.let { f ->
          try { f.solveIncrementally() }
  //      try { f.solveMaxSat(softConstraints) }
          catch (npe: NullPointerException) { return@sequence }
        }
  //  var freshnessConstraints = 0L
      var totalSolutions = 0
      while (true) try {
        //    println(solution.toPython())
        val cfg = cfgs.first()
        val fillers: MutableList<String?> =
          holeVecVars.map { bits -> cfg.tmap[cfg.nonterminals(bits.map { solution[it]!! })] }.toMutableList()

  //      val bMat = FreeMatrix(matrix.data.map { it.map { if (it is Variable) solution[it]!! else if (it is Constant) it == T else false } as List<Boolean>? })
  //      println(bMat.summarize(this@synthesize))
        val completion: String =
          tokens.map {
            if (it == "_") fillers.removeAt(0)!!
            else if (it.isNonterminalStubIn(this@synthesize)) {
              val stub = fillers.removeAt(0)!!
              if (reconstructor.isEmpty() || it != reconstructor.first().first) stub
              else reconstructor.removeFirst().second
            } else it
          }.filterNot { "ε" in it }.joinToString(" ")

        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        totalSolutions++
        if (completion.trim().isNotBlank()) yield(completion)

        val isFresh = solution.filter { (k, v) -> k in holeVars && v }.areFresh()
  //      freshnessConstraints += isFresh.numberOfAtoms()
  //      println("Freshness constraints: $freshnessConstraints")

        val model = solver.run { add(isFresh); sat(); model() }
  //      val model = solver.run { addHardFormula(isFresh); solve(); model() }
        solution = solution.keys.associateWith { model.evaluateLit(it) }
      } catch(ie: InterruptedException) {
        ff.clear()
        throw ie
      } catch (e: Exception) {
        ff.clear()
        break
      } catch (e: OutOfMemoryError) { // Does this really work?
        ff.clear()
        break
      }
    }
}

fun CFG.generateConstraints(tokens: List<String>): Pair<Formula, List<SATVector>> {
  println("Synthesizing: ${tokens.joinToString(" ")}")
  val (matrix, holeVecVars) = constructInitialMatrix(tokens)

  // TODO: Replace contiguous (i.e. hole-free) subexpressions with their corresponding
  //       nonterminal in the original string to reduce fixedpoint matrix size.
    return isInGrammar(matrix) and
      uniquenessConstraints(holeVecVars) and
      reachabilityConstraints(tokens, holeVecVars) to holeVecVars
}

fun CSL.generateConstraints(tokens: List<String>): Pair<Formula, List<SATVector>> {
  ff.clear()
  val timeToFormConstraints = System.currentTimeMillis()

  val (t, q) = cfgs.map { it.generateConstraints(tokens) }.unzip()
  // TODO: need to constrain superdiagonals of all CFG matrices to use the same variables
  val parsingConstraints = t.fold(T) { a, b -> a and b }
  val holeVecVars = q.first()

  val timeElapsed = System.currentTimeMillis() - timeToFormConstraints
  println("Solver formed ${parsingConstraints.numberOfNodes()} constraints in ${timeElapsed}ms")

  return parsingConstraints to holeVecVars
}