package ai.hypergraph.kaliningraph.sat

import ai.hypergraph.kaliningraph.parsing.*
import ai.hypergraph.kaliningraph.tensor.*
import ai.hypergraph.kaliningraph.types.*
import org.logicng.formulas.*
import kotlin.collections.filter

typealias SATVector = Array<Formula>
typealias SATRubix = UTMatrix<SATVector>

val SATRubix.stringVariables by cache { diagonals.first() }

@JvmName("joinFormula1")
fun CFG.join(left: SATVector, right: SATVector): SATVector =
  if (left.isEmpty() || right.isEmpty()) arrayOf()
  else Array(left.size) { i ->
    bimap[bindex[i]].filter { 1 < it.size }.map { it[0] to it[1] }
      .map { (B, C) -> left[bindex[B]] and right[bindex[C]] }
      .fold(F) { acc, satf -> acc or satf }
//      .map { (B, C) -> left[bindex[B]].let{ it.factory().and(it, right[bindex[C]])} }
//      .fold(left.first().factory().falsum() as Formula) { acc, satf -> acc.factory().or(acc, satf) }
  }

@JvmName("joinFormula2")
fun join(left: SATVector, right: SATVector, bindex: Bindex, bimap: BiMap): SATVector =
  if (left.isEmpty() || right.isEmpty()) arrayOf()
  else
    Array(left.size) { i ->
      bimap[bindex[i]].filter { 1 < it.size }.map { it[0] to it[1] }
        .map { (B, C) -> left[bindex[B]] and right[bindex[C]] }
        .fold(F) { acc, satf -> acc or satf }
//      .map { (B, C) -> left[bindex[B]].let{ it.factory().and(it, right[bindex[C]])} }
//      .fold(left.first().factory().falsum() as Formula) { acc, satf -> acc.factory().or(acc, satf) }
    }

@JvmName("satFormulaUnion")
infix fun SATVector.union(that: SATVector): SATVector =
  if (isEmpty()) that else if (that.isEmpty()) this
  else Array(size) { i -> this[i] or that[i] }
//  if (isEmpty()) that else if (that.isEmpty()) this
//  else Array(size) { i -> this[i].let { it.factory().or(it, that[i]) } }

fun BooleanArray.toLitVec(): SATVector = map { BLit(it) }.toTypedArray()

fun vecsEqAtIndices(a: SATVector, b: SATVector, indices: Set<Int>): Formula =
  if (a.isEmpty() || b.isEmpty() || a.size != b.size) throw Exception("Shape mismatch! (${a.size}, ${b.size})")
  else indices.map { i -> a[i] eq b[i] }.reduce { acc, satf -> acc and satf }

infix fun SATVector.vecEq(that: SATVector): Formula =
  if (isEmpty() || that.isEmpty() || size != that.size) throw Exception("Shape mismatch! ($size, ${that.size})")
  else if (contentEquals(that)) T
  else zip(that).partition { (l, r) -> l == r }
    .second.map { (a, b) -> a eq b }
    .let { if (it.isEmpty()) T else it.reduce { acc, satf -> acc and satf } }

fun CFG.valiantMatEq(l: SATRubix, r: SATRubix): Formula =
  if (l.shape() != r.shape()) throw Exception("Shape mismatch! (${l.shape()}, ${r.shape()})")
  else (l eq r) and startFormula(l.diagonals.last().first(), r.diagonals.last().first())

infix fun SATRubix.eq(that: SATRubix): Formula =
  diagonals.drop(1).dropLast(1).flatten().zip(that.diagonals.drop(1).dropLast(1).flatten())
    .map { (va, vb) -> va vecEq vb }.reduce { acc, satf -> acc and satf }

fun CFG.startFormula(ltop: SATVector, rtop: SATVector) =
  startSymbols.map { bindex[it] }.map { ltop[it] eq rtop[it] }.reduce { acc, satf -> acc and satf }

fun CFG.downwardsReachabilitySeq() = graph
  .let { it.reachSequence(it.vertices.filter { it.label in startSymbols }.toSet()) }
  .map { it.map { it.label }.toSet() }

fun CFG.upwardsReachabilitySeq() = graph
  .let { it.reachSequence(it.vertices.filter { it.label in terminals }.toSet(), it.A_AUG.transpose) }
  .drop(1).map { it.map { it.label }.toSet() }

/**
 * Only compare nonterminals at locations that are bidirectionally reachable, i.e., which
 * could possibly participate in any parse forest at each level in the trellis automaton.
 * This is like a sandwich, where the bottom and topmost layers are the most constrained
 * and the middle layers have the most degrees of freedom.
 */
val CFG.possibleNonterminalsAtEachLevel: MutableMap<Int, List<Set<Int>>> by cache { mutableMapOf() }
fun CFG.possibleNonterminalsAtEachLevel(levels: Int): List<Set<Int>> =
  possibleNonterminalsAtEachLevel.getOrPut(levels) {
    downwardsReachabilitySeq().take(levels).toList()
      .zip(upwardsReachabilitySeq().take(levels).toList().reversed())
      .map { (a, b) -> a intersect b intersect nonterminals }
      .map { it.map { bindex[it] }.toSet() }.also { possibleNonterminalsAtEachLevel[levels] = it }
  }

/**
 * Only compare nonterminals at locations that are bidirectionally reachable, i.e., which
 * could possibly participate in any parse forest at each level in the trellis automaton.
 * This is like a sandwich, where the bottom and topmost layers are the most constrained
 * and the middle layers have the most degrees of freedom.
 */
fun CFG.sandwichConstraints(l: SATRubix, r: SATRubix): Formula =
  possibleNonterminalsAtEachLevel(l.diagonals.size - 2).let { reachSeq ->
    l.diagonals.zip(r.diagonals).drop(1).dropLast(1).reversed()
      .mapIndexed { i, (va, vb) ->
        va.zip(vb).map { (a, b) -> vecsEqAtIndices(a, b, reachSeq[i]) }
          .reduce { acc, satf -> acc and satf }
      }.reduce { acc, satf -> acc and satf }
  }

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
      nonparametricForm.equivalenceClass(nt)
        .map { hf eq BVecLit(toBitVec(setOf(it))) }
        .fold(F) { a, b -> a xor b }
    }.flatten().fold(T) { a, b -> a and b }

// Computes equivalences between unit nonterminals in each CFG
fun CJL.alignNonterminals(rubices: List<SATRubix>): Formula {
  if (rubices.size == 1) return T

  // For each terminal shared by every CFG, compute the nonterminal sets each one is generated by
  // i.e., computes the groups of nonterminals that are equivalent.
  // TODO: maybe simplify using a more brittle equivalence relation, e.g. NT name equality
  val terminalsToNTs =
    cfgs.map { it.terminals }.intersect().map { terminal ->
//      println("====$terminal====")
      cfgs.map { cfg ->
        val nts = cfg.bimap[listOf(terminal)]
//      println("NT: ${nts.joinToString(",", "[", "]") { "($it -> $terminal)" }}")

        /* Heuristic to select the smallest terminal preimage, e.g., given two CFGs
         * [[A -> a | d], D -> a] ⊆ CFG1
         * [[M -> a | p], Q -> a] ⊆ CFG2
         * we want to select D and Q as the preimage of a, instead of A and M.
        */
        val firstBijectiveNT = nts.minByOrNull { cfg.bimap[it].size }!!
//      println("First bijective NT: $firstBijectiveNT")
        cfg.bindex[firstBijectiveNT]
      }
//        .also { println("=========\n") }
    }

  if (terminalsToNTs.isEmpty()) return F.also { println("No terminals in common!") }

  // For each group of equivalent nonterminals, bind them together using == constraints, e.g.
  // [[A, B, C], [E, F, G]] -> [A == B] ʌ [B == C] ʌ [E == F] ʌ [F == G]
  // except we use indices to track the nonterminals positions in each rubix
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
    times = { a, b -> join(a, b, bindex, bimap) }
  )
}

fun CFG.encodeTokenAsSATVec(token: Σᐩ): SATVector =
  bimap[listOf(token)].let { nts -> nonterminals.map { it in nts } }
    .toBooleanArray().toLitVec()

fun CFG.encodeTokens(rubix: SATRubix, tokens: List<Σᐩ>): Formula =
  rubix.stringVariables.zip(tokens).fold(T) { acc: Formula, (v, b) ->
    acc and v.vecEq(if (b.isHoleTokenIn(cfg = this)) v else encodeTokenAsSATVec(b))
  }

//@OptIn(ExperimentalTime::class)
fun CFG.isInGrammar(mat: SATRubix): Formula =
  startSymbols.fold(F) { acc, it -> acc or mat.diagonals.last().first()[bindex[it]] } and
    // TODO: Cache this to speedup computation?
    // n.b. there is a tradeoff here between the number of constraints and message passing!
    // Encoding the fixpoint M = M * M where M is populated by variables is simpler to encode,
    // but does not pass any messages between the formulas that would reduce the formula size.
    // By encoding M' = (M * M) + M, we can eliminate impossible nonterminals, but may increase
    // the overall formula size. This can be desirable when the string contains a mixture of
    // variables and constants, as the constants can be used to eliminate certain nonterminals.
    // https://github.com/breandan/galoisenne/pull/6
    valiantMatEq(mat, mat * mat)//measureTimedValue{ mat * mat }.also { println("Matmul took: ${it.duration}") }.value)

fun CFG.constructRubix(numTokens: Int): SATRubix =
//  possibleNonterminalsAtEachLevel(numTokens).reversed().let { pnts ->
//    println("PNTS: ${pnts.map { it.map { bindex[it] } }}")
//    FreeMatrix(satAlgebra, numTokens + 1) { r, c ->
//      // Strictly upper triangular matrix entries
//      if (r + 1 <= c) Array(nonterminals.size) { k ->
//        if (k !in pnts[c - r - 1]) F
//        else BVar("HV_r::${r}_c::${c}_cfgHash::${hashCode()}_f::$k")
//      }
//      // Diagonal and subdiagonal
//      else arrayOf()
//    }.toUTMatrix()
//  }
  FreeMatrix(satAlgebra, numTokens + 1) { r, c ->
    // Strictly upper triangular matrix entries
    if (r + 1 <= c) BVecVar(nonterminals.size) { "HV_r::${r}_c::${c}_cfgHash::${hashCode()}" }
    // Diagonal and subdiagonal
    else arrayOf()
  }.toUTMatrix()

// TODO: incrementalize
fun CJL.generateConstraints(tokens: List<Σᐩ>): Pair<Formula, SATRubix> {
  ff.clear()
  println("Synthesizing (${tokens.size}): ${tokens.joinToString(" ")}")
  val timeToFormConstraints = System.currentTimeMillis()
  val (t, q) = cfgs.map { it.generateConstraints(tokens) }.unzip()
  val parsingConstraints = t.fold(T) { a, b -> a and b } and alignNonterminals(q)

  val timeElapsed = System.currentTimeMillis() - timeToFormConstraints
  print("Solver formed ${parsingConstraints.numberOfNodes()} constraints in ${timeElapsed}ms, and ")

  return parsingConstraints to q.first()
}

fun CFG.generateConstraints(
  tokens: List<Σᐩ>,
  rubix: SATRubix = constructRubix(tokens.size)
    .eliminateImpossibleDerivations(this, tokens)
//    .propagateConstantFormulae(this)
): Pair<Formula, SATRubix> =
  // TODO: check if solving time is sensitive to constraint ordering
//  parikhConstraints(rubix, tokens) and
    isInGrammar(rubix)/*.also { print("FormulaSize={isInGrammar: ${it.numberOfNodes()},")}*/ and
//  encodeTokens(litRbx, tokens)/*.also { print("encodeTokens: ${it.numberOfNodes()},")}*/ and
    uniquenessConstraints(rubix, tokens)/*.also { print("uniquenessConstraints: ${it.numberOfNodes()},")}*/ and
    reachabilityConstraints(tokens, rubix)/*.also { println("reachabilityConstraints: ${it.numberOfNodes()}}")}*/ to rubix

// Propagates constants through the matrix
private fun SATRubix.propagateConstantFormulae(cfg: CFG): SATRubix {
  val init = toFullMatrix().summarize(cfg)
  println("INIT: \n${init}")
  val sf = seekFixpoint(succ= { (it * it) + it },
    stop = { i, a, b -> a.toFullMatrix().summarize(cfg) == b.toFullMatrix().summarize(cfg) })
  val ps = sf.toFullMatrix().summarize(cfg)
  println("PS: \n$ps")
  val ss = (sf * sf) + sf
  val pss = ss.toFullMatrix().summarize(cfg)
  println("PSS: \n$pss")
  println("Equal: ${ps == pss}")
  return sf
}

fun SATRubix.eliminateImpossibleDerivations(cfg: CFG, tokens: List<Σᐩ>): SATRubix {
  val parikRubix = map { it.map { v ->
    if ((v as Variable).isPossibleDerivation(cfg, tokens)) v else F
  }.toTypedArray() }

// Precomputes constants (permanent upper right triangular submatrices) in
// the fixpoint to avoid solving for invariant entries that are fixed.
  val litUDM: UTMatrix<BooleanArray?> = UTMatrix(
    ts = tokens.map { it ->
      // Nulls on the superdiagonal will cast either a rectangular or pentagonal
      // shadow of bitvector variables on UTMatrix, which we represent as nulls
      if (it.isHoleTokenIn(cfg = cfg)) null
      // Terminals will cast a triangular shadow of bitvector literals on UTMatrix
      else cfg.bimap[listOf(it)].let { nts -> cfg.nonterminals.map { it in nts } }.toBooleanArray()
    }.toTypedArray(),//.also { println("Array: ${it.joinToString(" :: ") { it.contentToString()}}") },
    algebra = cfg.satLitAlgebra
  ).seekFixpoint()//.also { println("After: \n" + it.toFullMatrix().summarize()) },

  val holeIndices: Set<Int> = tokens.indices.filter { tokens[it].isHoleTokenIn(cfg = cfg) }.toSet()
  val litRbx: SATRubix = FreeMatrix(cfg.satAlgebra, tokens.size + 1) { r, c ->
    // Strictly upper triangular matrix entries
    if (r + 1 <= c) {
      if (holeIndices.any { it in r until c }) parikRubix[r, c]
      else litUDM[r, c]!!.toLitVec()
    }
    // Diagonal and subdiagonal
    else arrayOf()
  }.toUTMatrix()

// Tries to propagate upper right triangular permanent entries as far as possible
//  fxbix: SATRubix? =
//    if ((terminals intersect tokens.toSet()).isEmpty()) null
//    else FreeMatrix(satAlgebra, tokens.size + 1) { r, c ->
//      if(c - r == tokens.size) toBooleanArray(startSymbols).toLitVec()
//      else if(c-r==1) toBooleanArray(bimap[listOf(tokens[r])]).toLitVec()
//      else rubix[r, c]
////        litUDM[r, c].also { println("Entry: $it") }?.map { if (it) T else F }?.toTypedArray() ?: rubix[r, c].also { println("r=$r, c=$c") }
//    }.also { println("Summary: \n" + it.summarize(this)) }.toUTMatrix()
//  .seekFixpoint(stop = { i, _, _ -> 5 < i },//, tt -> t.numConsts == tt.numConsts },
//    succ = { (it * it + it).also { println("Consts: " + it.numConsts + "\n${it.toFullMatrix().summarize(this)}") } })
//    .also { it.toFullMatrix().summarize(this) }

  return litRbx
}

/**
  TODO: Ideas to reduce number of constraints:

  - For each entry the matrix, determine the minimum number of each terminal that must be present
    to possibly derive the nonterminal. Then, block all nonterminals that do not have at least the
    required number of terminals, accounting for the number of available holes. For example, if a
    nonterminal has 1/5 required terminals and there are 2 available holes, then it can be blocked.
  - Use Parikh's theorem to construct a language formula for each nonterminal at each layer, then
    use SAT solver to determine reachable nonterminals based on the preimage of the Parik vector.
  - For downward constraints, e.g., start symbols, compute minimum and maximum distance to the NE
    corner, then block all nonterminals that are unreachable within that number of join operations.
    For example, suppose we have productions S -> B C, B -> D E, D -> F G, and F -> H I, and we
    know a nonterminal variable X must be within [1, 2] joins from the NE corner, we can proceed
    to block F, G, H and I, since they are unreachable in fewer than two joins.
  - For upward constraints, e.g., terminals, compute minimum and maximum distance from terminals,
    then block all nonterminals that are unreachable within that number of join operations.
  - Precompute constants (permanent upper right triangular submatrices) in the fixpoint to avoid
    solving for invariant entries that are fixed.
  - Use Brzozowski message passing, with a ternary logic of "must contain":=⊤, "may contain":=?, and
    "must not contain":=⊥, then propagate until a matrix fixpoint is reached. For all indeterminates
    use a SAT variable, for all determinates use a SAT literal. For example, suppose we have:

       {A}       |  A -> B C    D -> d    G -> g    |
    {F} ? {!B}   |  B -> D E    E -> e              | => ? = C
       {G}       |  C -> F G    F -> f              |

  If I am ?, and I know the directly adjacenct sets of nonterminals ∂V/∂A∂? in my context, then I
  can compute the set of nonterminals that I must contain, must not contain, and may contain:

   !(v∈V) -> ∀ q. ∂V/∂q∂? ∈ G, !(q∈?)
   !(v∈V) -> ∀ r. ∂V/∂?∂r ∈ G, !(r∈?)

  - Use a mixture of M = M * M and M' = M * M + M fixpoints with layerwise propagation to eliminate
    redundant constraints between SAT instances.
 */

// Naming convention: HV_r::${r}_c::${c}..._f::${idx}
fun Variable.toRowColNT(cfg: CFG): Triple<Int, Int, String> {
  val (r, c, idx) = name().split("_").let { it[1].substringAfter("r::").toInt() to
      it[2].substringAfter("c::").toInt() to it.last().substringAfter("f::").toInt() }
  return Π(r, c, cfg.bindex[idx])
}

fun Variable.isPossibleDerivation(cfg: CFG, tokens: List<Σᐩ>): Boolean {
  val (i, j, nt) = toRowColNT(cfg)
  val parikhSet= tokens.subList(i, j).filter { !it.isHoleTokenIn(cfg) }.toSet()
  val terminalClosure = cfg.reachableSymbols(nt) intersect cfg.terminals
  return parikhSet.isEmpty() || parikhSet in terminalClosure
//      .also { println("NT: $nt, Parikh set: $parikhSet, terminal closure: $terminalClosure") }
}

fun CFG.parikhConstraints(rubix: SATRubix, tokens: List<Σᐩ>): Formula {
  val numHoleTokens = tokens.count { it.isHoleTokenIn(this) }

  if (numHoleTokens == tokens.size) return T

  val variables = rubix.data.asSequence()
    .map { entry -> entry.map { it.variables() } }.flatten().flatten().toSet()

  return variables.mapNotNull { v -> if (v.isPossibleDerivation(this, tokens)) null else v eq F }
    .also { println("Blocking ${it.size} nonterminal variables in UT matrix") }
    .fold(T) { a, b -> a and b }
}

val SATRubix.numConsts by cache { data.map { it.toList() }.flatten().count { it is Constant } }

/** Currently just a JVM wrapper around the multiplatform [synthesizeWithVariations] */
fun Σᐩ.synthesizeIncrementally(
  cfg: CFG,
  allowNTs: Boolean = true,
  enablePruning: Boolean = false,
  variations: List<Mutator> = listOf({ _, _ -> sequenceOf() }),
  updateProgress: (Σᐩ) -> Unit = {},
  takeMoreWhile: () -> Boolean = { !Thread.currentThread().isInterrupted },
  synthesizer: CFG.(List<Σᐩ>) -> Sequence<Σᐩ> = {
    if (it.isSetValiantOptimalFor(this))
      it.also { println("Synthesizing with SetValiant: ${it.joinToString(" ")}") }
      .solve(this, takeMoreWhile = takeMoreWhile)
    else asCJL.synthesize(it, takeMoreWhile = takeMoreWhile)
  }
): Sequence<Σᐩ> = synthesizeWithVariations(
  cfg = cfg,
  allowNTs = allowNTs,
  variations = variations,
  enablePruning = enablePruning,
  updateProgress = updateProgress,
  takeMoreWhile = takeMoreWhile,
  synthesizer = synthesizer
)

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

fun CFG.synthesize(tokens: List<Σᐩ>): Sequence<Σᐩ> =
  asCJL
//    .also { println("Solving (Complexity: ${(terminals - blocked).size.pow(tokens.count { it == "_" })}): ${tokens.joinToString(" ")}") }
    .synthesize(tokens)

// TODO: As new keystrokes are received, we should incrementally update
//  existing constraints rather than creating a fresh SAT instance.
/** [generateConstraints] */
fun CJL.synthesize(
  tokens: List<Σᐩ>,
  takeMoreWhile: () -> Boolean = { !Thread.currentThread().isInterrupted },
): Sequence<Σᐩ> {
  check(tokens.all { it in symbols || it == HOLE_MARKER || it.isNonterminalStub() })
  { "All tokens passed into synthesize() must be contained in all CFGs" }
  return when {
    tokens.none { it.isHoleTokenIn(cfg = cfgs.first()) } -> emptySequence<Σᐩ>().also { println("No holes!") }
    tokens.size == 1 -> cfgs.map { it.handleSingleton(tokens.first()) }.intersect().asSequence()
    else -> sequence {
      val (parsingConstraints, rubix) = generateConstraints(tokens)
      val strVars = rubix.stringVariables.fold(setOf<Formula>()) { a, b -> a + b }

      // FormulaDimacsFileWriter.write("dimacs.cnf", parsingConstraints.cnf(), true)
      // https://www.utbot.org/kosat/
      // Sometimes simplification can take longer or even switch SAT->UNSAT?
      // println("Original: ${parsingConstraints.numberOfNodes()}")
      // parsingConstraints = AdvancedSimplifier().apply(parsingConstraints, false)
      // parsingConstraints = BackboneSimplifier.get().apply(parsingConstraints, false)
      // println("Reduction: ${parsingConstraints.numberOfNodes()}")
      // println(parsingConstraints.cnf().toPython())

      val startTime: Long = System.currentTimeMillis()
      var (solver, model) = parsingConstraints.solveIncrementally(takeMoreWhile = takeMoreWhile)
      // LogicNG's Formula datatype is not monoidal/threadsafe, so we cannot run it in parallel.
      // Instead we want an immutable Formula datatype that can be combined without affecting solver.
      // This would enable incremental editing, rollbacks, reset to initial state, etc.
      // TODO: var (solver, model) = parsingConstraints.solveUsingKosat()
      model.ifEmpty { ff.clear(); println("no solutions were found after ${System.currentTimeMillis() - startTime}ms"); return@sequence }

      //  var totalFreshnessConstraints = 0L
      // Tries to enumerate all strings that satisfy the constraints, adding a freshness constraint after each one.
      while (true) try {
        // In the case of intersections, which CFG is used to generate the string does not matter.
        val cfg = cfgs.first()
        // Decode model from SAT solver into the corresponding string
        val fillers = rubix.stringVariables.zip(tokens)
          .map { (bits, token) ->
            // If the token is not a hole token, use the original token.
            if (cfgs.none { token.isHoleTokenIn(it) }) token
            // Otherwise, use the model to decode the bits into a terminal.
            // Since tmap is a many-to-many relation, any representative of the set is valid.
            // TODO: work out a more principled way to choose a representative
            else cfg.tmap[cfg.nonterminals(bits.map { model[it]!! })]!!.random()
          }

        val completion: Σᐩ = fillers.joinToString(" ")

        // YIELD happens here:
        if (completion.trim().isNotBlank()) yield(completion)

        val isFresh = model.filter { (k, v) -> k in strVars && v }.areFresh()
        // freshnessConstraints += isFresh.numberOfAtoms()
        // println("Total freshness constraints: $totalFreshnessConstraints")

        model = solver.addConstraintAndSolve(isFresh, takeMoreWhile)
        // If model is empty or we receive an error, assume that all models have been exhausted.
        .ifEmpty { ff.clear(); println("exhausted all solutions after ${System.currentTimeMillis() - startTime}ms"); return@sequence }
      } catch (ie: InterruptedException) {
        println("Interrupted after ${System.currentTimeMillis() - startTime} ms")
        ff.clear()
        throw ie
      } catch (npe: NullPointerException) {
        System.err.println("NPE when solving: ${tokens.joinToString(" ")}")
        npe.printStackTrace()
        ff.clear()
        return@sequence
      } catch (oom: OutOfMemoryError) { // Does this really work?
        System.err.println("OOM when solving: ${tokens.joinToString(" ")}")
        oom.printStackTrace()
        ff.clear()
        return@sequence
      } catch (e: Exception) {
        System.err.println("Exception when solving: ${tokens.joinToString(" ")}")
        e.printStackTrace()
        ff.clear()
        return@sequence
      }
    }
  }
}
