package ai.hypergraph.kaliningraph.sat

import ai.hypergraph.kaliningraph.joinToScalar
import ai.hypergraph.kaliningraph.tensor.Matrix
import ai.hypergraph.kaliningraph.types.Ring
import org.logicng.formulas.*
import org.logicng.formulas.FormulaFactoryConfig.FormulaMergeStrategy.IMPORT
import org.logicng.solvers.MiniSat

val ff = FormulaFactory(FormulaFactoryConfig.builder().formulaMergeStrategy(IMPORT).build())
fun BVar(name: String): Formula = ff.variable(name)
fun BLit(b: Boolean): Formula = ff.constant(b)

fun Formula.solve(): Map<Variable, Boolean> =
  ff.let { ff ->
    val vars = variables()
    val model = MiniSat.miniSat(ff).apply { add(this@solve); sat() }.model()
    vars.associateWith { model.evaluateLit(it) }
  }

fun Formula.solveIncremental(
  miniSat: MiniSat = MiniSat.miniSat(ff)
): Pair<MiniSat, Map<Variable, Boolean>> =
  ff.let { ff ->
    val vars = variables()
    val model = miniSat.apply { add(this@solveIncremental); sat() }.model()
    miniSat to vars.associateWith { model.evaluateLit(it) }
  }

// Ensures that at least one of the formulas in stale are fresh
fun Map<Variable, Boolean>.areFresh() =
  entries.map { (v, b) -> v neq BLit(b) }.reduce { acc, satf -> acc or satf }

/** See [org.logicng.io.parsers.PropositionalParser] */
infix fun Formula.and(that: Formula): Formula = ff.and(this, that)
infix fun Formula.or(that: Formula): Formula = ff.or(this, that)
infix fun Formula.xor(that: Formula): Formula = eq(that).negate()
infix fun Formula.neq(that: Formula): Formula = xor(that)
infix fun Formula.eq(that: Formula): Formula = ff.equivalence(this, that)
val T: Formula = ff.verum()
val F: Formula = ff.falsum()

fun Formula.toBool() = "$this".drop(1).toBooleanStrict()

// Only compare upper triangular entries of the matrix
infix fun Matrix<Formula, *, *>.eqUT(that: Matrix<Formula, *, *>) =
  joinToScalar(this, that, filter = { r, c -> r < c }, join = { a, b -> a eq b }, reduce = { a, b -> a and b })

infix fun Matrix<Formula, *, *>.eq(that: Matrix<Formula, *, *>) =
  joinToScalar(this, that, join = { a, b -> a eq b }, reduce = { a, b -> a and b })

infix fun Matrix<Formula, *, *>.neq(that: Matrix<Formula, *, *>) =
  (this eq that).negate()

val XOR_SAT_ALGEBRA =
  Ring.of(
    nil = F,
    one = T,
    plus = { a, b -> a xor b },
    times = { a, b -> a and b }
  )