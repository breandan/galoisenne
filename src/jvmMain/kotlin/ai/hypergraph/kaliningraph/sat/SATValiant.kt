package ai.hypergraph.kaliningraph.sat

import ai.hypergraph.kaliningraph.graphs.*
import ai.hypergraph.kaliningraph.parsing.*
import ai.hypergraph.kaliningraph.tensor.*
import ai.hypergraph.kaliningraph.types.*
import ai.hypergraph.kaliningraph.visualization.*
import org.logicng.formulas.Formula
import kotlin.collections.filter
import kotlin.time.*

typealias SATVector = Array<Formula>
typealias SATRubix = UTMatrix<SATVector>

val SATRubix.stringVariables by cache { diagonals.first() }

@JvmName("joinFormula")
fun CFG.join(left: SATVector, right: SATVector): SATVector =
  if (left.isEmpty() || right.isEmpty()) arrayOf()
  else Array(left.size) { i ->
    bimap[bindex[i]].filter { 1 < it.size }.map { it[0] to it[1] }
      .map { (B, C) -> left[bindex[B]] and right[bindex[C]] }
      .fold(F) { acc, satf -> acc or satf }
  }

@JvmName("satFormulaUnion")
infix fun SATVector.union(that: SATVector): SATVector =
  if (isEmpty()) that else if (that.isEmpty()) this
  else Array(size) { i -> this[i] or that[i] }

fun BooleanArray.toLitVec(): SATVector = map { BLit(it) }.toTypedArray()

infix fun SATVector.vecEq(that: SATVector): Formula =
  if (isEmpty() || that.isEmpty() || size != that.size) throw Exception("Shape mismatch! ($size, ${that.size})")
  else if (contentEquals(that)) T
  else zip(that).partition { (l, r) -> l == r }
//    .also { (a, b) -> if (a.isNotEmpty()) println("Eliminated ${a.size}/${a.size + b.size} identical SAT variables") }
    .second.map { (a, b) -> a eq b }
    .let { if (it.isEmpty()) T else it.reduce { acc, satf -> acc and satf } }

infix fun SATRubix.valiantMatEq(that: SATRubix): Formula =
  if (shape() != that.shape()) throw Exception("Shape mismatch! (${shape()}, ${that.shape()})")
  else diagonals.flatten().zip(that.diagonals.flatten())
    .filter { (l, r) -> l.isNotEmpty() && r.isNotEmpty() }
    .map { (a, b) -> a vecEq b }.reduce { acc, satf -> acc and satf }

@OptIn(ExperimentalTime::class)
fun CFG.isInGrammar(mat: SATRubix): Formula =
    startSymbols.fold(F) { acc, it -> acc or mat.diagonals.last().first()[bindex[it]] } and
      // TODO: Cache this to speedup computation!
      (mat valiantMatEq measureTimedValue{ mat * mat }.also { println("Matmul took: ${it.duration}") }.value)

// Encodes the constraint that bit-vectors representing a unary production
// should not contain mixed NT symbols, e.g., given A->(, B->(, C->), D->)
// the bitvector cannot have the configuration [A=1 B=1 C=0 D=1], it must
// be either [A=1 B=1 C=0 D=0] or [A=0 B=0 C=1 D=1].
fun CFG.mustBeOnlyOneTerminal(bitvec: SATVector): Formula {
  val ntbv = bitvec.projectOnto(nonterminals)
  // terminal                 possible nonterminals it can represent
  return (terminals - blocked).map { bitvec.projectOnto(bimap[listOf(it)], nonterminals) }.map { possibleNTs ->
    val (insiders, outsiders) = ntbv.partition { it in possibleNTs }
    (insiders + outsiders.map { it.negate() }).reduce { acc, satf -> acc and satf }
  }.reduce { acc, satf -> acc xor satf }
}

// Returns list elements matching the intersection between set and on (indexed by on)
fun <E, T> Array<E>.projectOnto(set: Set<T>, on: Set<T> = set): Set<E> =
  if (size != on.size) throw Exception("Size mismatch: List[$size] != Set[${on.size}]")
  else set.intersect(on).map { this[on.indexOf(it)] }.toSet()

// Encodes that each blank can only be a single terminal
fun CFG.uniquenessConstraints(rubix: SATRubix, tokens: List<Σᐩ>): Formula =
  rubix.stringVariables.zip(tokens)
    .filter { it.second.isHoleTokenIn(this) }
    .map { mustBeOnlyOneTerminal(it.first) }
    .fold(T) { acc, it -> acc and it }

// Encodes that nonterminal stubs can only be replaced by reachable nonterminals
fun CFG.reachabilityConstraints(tokens: List<Σᐩ>, rubix: SATRubix): Formula =
  tokens.zip(rubix.stringVariables)
    .filter { (word, _) -> word.isNonterminalStubIn(cfg = this) }
    .map { (nonterminalStub, hf) ->
      val nt = nonterminalStub.drop(1).dropLast(1)
      nonparametricForm.reachableSymbols(from = nt)
        .also { println("Transitive closure: $nt ->* $it") }
        .map { hf eq BVecLit(toBitVec(setOf(it))) }
        .fold(F) { a, b -> a xor b }
    }.flatten().fold(T) { a, b -> a and b }

// Computes equivalences between unit nonterminals in each CFG
fun CSL.alignNonterminals(rubices: List<SATRubix>): Formula {
  if (rubices.size == 1) return T

  val terminalsToNTs = cfgs.map { it.terminals }.intersect()
    .map { terminal -> cfgs.map { it.bindex[it.bimap[listOf(terminal)].first()] } }

  if (terminalsToNTs.isEmpty()) return F.also { println("No terminals in common!") }

  return rubices.map { it.stringVariables }
    .let { FreeMatrix(rubices.size, it.first().size, it.flatten()) }.cols
    .map { vecs ->
      terminalsToNTs.map {
        it.windowed(2).map { it[0] to it[1] }
          .zip(vecs.windowed(2).map { it[0] to it[1] })
          .map { (a, b) ->
            val (i1, i2) = a
            val (v1, v2) = b
            v1[i1] eq v2[i2]
          }
      }
    }.flatten().flatten().fold(T) { a, b -> a and b }
}

val CFG.satAlgebra by cache {
  Ring.of(
    nil = arrayOf(),
    one = Array(nonterminals.size) { T },
    plus = { a, b -> a union b },
    times = { a, b -> join(a, b) }
  )
}

// Precomputes literals in the fixpoint to avoid solving for invariant entries
fun CFG.constructRubix(numTokens: Int): SATRubix =
  FreeMatrix(satAlgebra, numTokens + 1) { r, c ->
    // Strictly upper triangular matrix entries
    if (r + 1 <= c) BVecVar(nonterminals.size) { i -> "HV[r=${r}][c=${c}][cfgHash=${hashCode()}]" }
    // Diagonal and subdiagonal
    else arrayOf()
  }.toUTMatrix()

fun CFG.generateConstraints(
  tokens: List<Σᐩ>,
  rubix: SATRubix = constructRubix(tokens.size)
): Pair<Formula, SATRubix> =
  isInGrammar(rubix) and
  encodeTokens(rubix, tokens) and
  uniquenessConstraints(rubix, tokens) and
  reachabilityConstraints(tokens, rubix) to rubix

fun CFG.encodeTokenAsSATVec(token: Σᐩ): SATVector =
  bimap[listOf(token)].let { nts -> nonterminals.map { it in nts } }
    .toBooleanArray().toLitVec()

fun CFG.encodeTokens(rubix: SATRubix, strings: List<Σᐩ>): Formula =
  if (strings.size == 1) {
    //  Precompute permanent upper right triangular submatrices
    val literalUDM: UTMatrix<BooleanArray?> = UTMatrix(
      ts = strings.map { it ->
        // Nulls on the superdiagonal will cast either a rectangular or pentagonal
        // shadow of bitvector variables on UTMatrix, which we represent as nulls
        if (it.isHoleTokenIn(cfg = this)) null
        // Terminals will cast a triangular shadow of bitvector literals on UTMatrix
        else bimap[listOf(it)].let { nts -> nonterminals.map { it in nts } }.toBooleanArray()
      }.toTypedArray(),
      algebra = satLitAlgebra
    ).seekFixpoint()
//    println(rubix.toFullMatrix().summarize(this))
//    println(FreeMatrix(literalUDM.data.map { it?.toLitVec() ?: arrayOf() }).summarize(this))
    rubix.data.zip(literalUDM.data).fold(T) { acc, (a, b) ->
      if (b == null || b.isEmpty() || a.isEmpty()) acc else acc and (a.vecEq(b.toLitVec()))
    }
  } else
    rubix.stringVariables.zip(strings).fold(T) { acc: Formula, (v, b) ->
      acc and v.vecEq(if (b.isHoleTokenIn(this)) v else encodeTokenAsSATVec(b))
    }

fun CSL.generateConstraints(tokens: List<Σᐩ>): Pair<Formula, SATRubix> {
  ff.clear()
  println("Synthesizing (${tokens.size}): ${tokens.joinToString(" ")}")
//  println("Using grammar:\n${pretty()}")
  val timeToFormConstraints = System.currentTimeMillis()
  val (t, q) = cfgs.map { it.generateConstraints(tokens) }.unzip()
//  println(q.first().toFullMatrix().summarize(cfgs.first()))
  val parsingConstraints = t.fold(T) { a, b -> a and b } and alignNonterminals(q)

  val timeElapsed = System.currentTimeMillis() - timeToFormConstraints
  println("Solver formed ${parsingConstraints.numberOfNodes()} constraints in ${timeElapsed}ms")
//  println("S variables " + parsingConstraints.stringVariables().let { "(${it.size}): $it" })

  return parsingConstraints to q.first()
}

/** Currently just a JVM wrapper around the multiplatform [synthesizeWithVariations] */
fun Σᐩ.synthesizeIncrementally(
  cfg: CFG,
  allowNTs: Boolean = true,
  enablePruning: Boolean = false,
  variations: List<Mutator> = listOf({ a, b -> sequenceOf() }),
  updateProgress: (Σᐩ) -> Unit = {}
): Sequence<Σᐩ> = synthesizeWithVariations(
  cfg = cfg,
  allowNTs = allowNTs,
  variations = variations,
  enablePruning = enablePruning,
  updateProgress = updateProgress,
  synthesizer = { a -> asCSL.synthesize(a) }
)

// TODO: Compactify [en/de]coding: https://news.ycombinator.com/item?id=31442706#31442719
fun CFG.nonterminals(bitvec: List<Boolean>): Set<Σᐩ> =
  bitvec.mapIndexedNotNull { i, it -> if (it) bindex[i] else null }.toSet()

private fun CFG.handleSingleton(s: Σᐩ): Set<Σᐩ> =
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
  3.) Encode symbolic Boolean formula as CNF using Tseitin.
  4.) Run SAT solver and decode variable assignments.

  https://people.csail.mit.edu/virgi/6.s078/papers/valiant.pdf#page=13
  https://www.ps.uni-saarland.de/courses/seminar-ws06/papers/07_franziska_ebert.pdf#page=6
 */

fun CFG.synthesize(tokens: List<Σᐩ>): Sequence<Σᐩ> = asCSL.synthesize(tokens)

fun CSL.synthesize(vararg strs: List<Σᐩ>): Sequence<Σᐩ> {
  val tokens = strs.asList()
  check(tokens.flatten().all { it in symbols || it == "_" || it.startsWith('<') && it.endsWith('>') }) { "All tokens passed into synthesize() must be in all CFGs" }
  check(tokens.all { it.size == tokens[0].size }) { "Size mismatch: ${strs.map { it.size }}" }
  return when {
    tokens.flatten().none { it.isHoleTokenIn(cfg = cfgs.first()) } -> emptySequence<Σᐩ>().also { println("No holes!") }
    tokens.first().size == 1 -> cfgs.map { it.handleSingleton(tokens.first()[0]) }.intersect().asSequence()
    else -> sequence {
      val (parsingConstraints, rubix) = generateConstraints(tokens.first())
      val strVars = rubix.stringVariables.fold(setOf<Formula>()) { a, b -> a + b }

      // Sometimes simplification can take longer or even switch SAT->UNSAT?
      // println("Original: ${parsingConstraints.numberOfNodes()}")
      // parsingConstraints = AdvancedSimplifier().apply(parsingConstraints, false)
      // parsingConstraints = BackboneSimplifier.get().apply(parsingConstraints, false)
      // println("Reduction: ${parsingConstraints.numberOfNodes()}")
      // println(parsingConstraints.cnf().toPython())

      var (solver, model) = parsingConstraints.solveIncrementally()
      model.ifEmpty { ff.clear(); return@sequence }

      //  var freshnessConstraints = 0L
      while (true) try {
        val cfg = cfgs.first()
        val fillers = rubix.stringVariables.zip(tokens.first()).map { (bits, token) ->
            if (cfgs.any { token.isHoleTokenIn(it) }) cfg.tmap[cfg.nonterminals(bits.map { model[it]!! })] else token
        }

        val completion: Σᐩ = fillers.joinToString(" ")
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        if (completion.trim().isNotBlank()) yield(completion)

        val isFresh = model.filter { (k, v) -> k in strVars && v }.areFresh()
        // freshnessConstraints += isFresh.numberOfAtoms()
        // println("Freshness constraints: $freshnessConstraints")

        model = solver.addConstraintAndSolve(isFresh)
          .ifEmpty { ff.clear(); return@sequence }
      } catch (ie: InterruptedException) {
        ff.clear()
        throw ie
      } catch (e: NullPointerException) {
        ff.clear()
        break
      } catch (e: OutOfMemoryError) { // Does this really work?
        ff.clear()
        break
      }
    }
  }
}
