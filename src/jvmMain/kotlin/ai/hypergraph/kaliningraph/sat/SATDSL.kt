package ai.hypergraph.kaliningraph.sat

import ai.hypergraph.kaliningraph.joinToScalar
import ai.hypergraph.kaliningraph.tensor.FreeMatrix
import ai.hypergraph.kaliningraph.tensor.Matrix
import ai.hypergraph.kaliningraph.types.Ring
import org.logicng.formulas.Formula
import org.logicng.formulas.FormulaFactory
import org.logicng.formulas.FormulaFactoryConfig
import org.logicng.formulas.FormulaFactoryConfig.FormulaMergeStrategy.IMPORT
import org.logicng.formulas.FormulaFactoryConfig.FormulaMergeStrategy.PANIC
import org.logicng.formulas.Variable
import org.logicng.solvers.MaxSATSolver
import org.logicng.solvers.MiniSat
import org.logicng.solvers.SATSolver

typealias Model = Map<Variable, Boolean>
//val ffCache = mutableMapOf<String, FormulaFactory>()

val ff: FormulaFactory =
    FormulaFactory(FormulaFactoryConfig.builder().formulaMergeStrategy(IMPORT).build())

//fun elimFormulaFactory() = ffCache.remove(Thread.currentThread().id)

fun BVar(name: String): Formula = ff.variable(name)
//  (if(name.startsWith("HV"))
//  ffCache.getOrPut(name.substringAfter("cfgHash::").substringBefore("_")) {
//    ffCache.keys.forEach { ffCache.remove(it)?.clear() }
//    FormulaFactory(FormulaFactoryConfig.builder().formulaMergeStrategy(PANIC).build())
//  } else ff).variable(name)
fun BVecVar(size: Int, prefix: String = "", pfx: (Int) -> String = { prefix }): SATVector =
   Array(size) { k -> BVar("${pfx(k)}_f::$k") }
fun BMatVar(name: String, algebra: Ring<Formula>, rows: Int, cols: Int = rows) =
  FreeMatrix(algebra, rows, cols) { i, j -> BVar("$name$i$j") }
fun BLit(b: Boolean): Formula = ff.constant(b)
fun BVecLit(l: BooleanArray): SATVector = l.map { ff.constant(it)  as Formula }.toTypedArray()
fun BVecLit(l: List<Boolean>): SATVector = BVecLit(l.toBooleanArray())
fun BVecLit(size: Int, f: (Int) -> Formula): SATVector = Array(size) { f(it) }

fun Formula.solve(): Model =
  ff.let { ff: FormulaFactory ->
    val vars = variables()
    val model = MiniSat.miniSat(ff).apply { add(this@solve); sat() }.model()
    if (model == null) mapOf() else vars.associateWith { model.evaluateLit(it) }
  }

fun Formula.solveMaxSat(
  softConstaints: SATVector,
  maxiSat: MaxSATSolver = MaxSATSolver.incWBO(ff)
): Pair<MaxSATSolver, Model> =
  maxiSat to maxiSat.apply {
    addHardFormula(this@solveMaxSat)
    softConstaints.forEach { addSoftFormula(it, 1) }
    solve()
  }.model()
    .let { model -> variables().associateWith { model.evaluateLit(it) } }

// Trick to "remove" a clause: https://groups.google.com/g/minisat/c/ffXxBpqKh90
fun SATSolver.removeConstraintAndSolve(f: Formula): Model = TODO()

fun SATSolver.addConstraintAndSolve(f: Formula): Model {
  val model = run { add(f); sat(); model() }
  return if (model == null) mapOf() else model.negativeVariables()
    .associateWith { false } + model.positiveVariables().associateWith { true }
}
  //      val model = solver.run { addHardFormula(isFresh); solve(); model() }

fun Formula.solveIncrementally(
  miniSat: MiniSat = MiniSat.miniSat(ff)
//    miniSat: MiniSat = MiniSat.glucose(ff)
): Pair<MiniSat, Model> =
  miniSat to miniSat.apply { add(this@solveIncrementally); sat() }.model().let { model ->
    if (model == null) mapOf() else variables().associateWith { model.evaluateLit(it) }
  }

// Ensures that at least one of the formulas in stale are fresh
fun Model.areFresh() =
  entries.map { (v, _) -> v.negate() as Formula }
//    .shuffled().let { it.take(it.size / 2) }
//    .also { println("${it.size} new constraints added!") }
    .reduce { acc, satf -> acc or satf }

/** See [org.logicng.io.parsers.PropositionalParser] */
infix fun Formula.and(that: Formula): Formula = ff.and(this, that)
infix fun Formula.or(that: Formula): Formula = ff.or(this, that)
infix fun Formula.xor(that: Formula): Formula = eq(that).negate()
infix fun Formula.neq(that: Formula): Formula = xor(that)
infix fun Formula.eq(that: Formula): Formula = ff.equivalence(this, that)
val T: Formula get() = ff.verum()
val F: Formula get() = ff.falsum()

fun Formula.toBool() = "$this".drop(1).toBooleanStrict()

infix fun SATVector.eq(that: SATVector): Formula =
  if (size != that.size) throw Exception("Shape mismatch, incomparable!")
  else zip(that).map { (a, b) -> a eq b }.reduce { a, b -> a and b }

// Only compare upper triangular entries of the matrix
infix fun Matrix<Formula, *, *>.eqUT(that: Matrix<Formula, *, *>): Formula =
  joinToScalar(this, that, filter = { r, c -> r < c }, join = { a, b -> a eq b }, reduce = { a, b -> a and b })

infix fun Matrix<Formula, *, *>.eq(that: Matrix<Formula, *, *>): Formula =
  if (shape() != that.shape()) throw Exception("Shape mismatch, incomparable!")
  else joinToScalar(this, that, join = { a, b -> a eq b }, reduce = { a, b -> a and b })

infix fun Matrix<Formula, *, *>.neq(that: Matrix<Formula, *, *>): Formula =
  (this eq that).negate()

val XOR_SAT_ALGEBRA get() =
  Ring.of(
    nil = F,
    one = T,
    plus = { a, b -> a xor b },
    times = { a, b -> a and b }
  )

val SAT_ALGEBRA get() =
  Ring.of(
    nil = F,
    one = T,
    plus = { a, b -> a or b },
    times = { a, b -> a and b }
  )