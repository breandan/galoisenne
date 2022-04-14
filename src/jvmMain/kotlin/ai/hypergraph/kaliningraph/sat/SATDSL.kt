package ai.hypergraph.kaliningraph.sat

import ai.hypergraph.kaliningraph.tensor.Matrix
import ai.hypergraph.kaliningraph.types.Ring
import org.logicng.formulas.FormulaFactory
import org.logicng.io.parsers.PropositionalParser
import org.logicng.solvers.MiniSat

typealias Formula = String
fun BVar(name: String) = name
fun BLit(b: Boolean) = if (b) T else F

fun Formula.solve(): Map<String, Boolean> =
  FormulaFactory().let { ff ->
    val cnf = PropositionalParser(ff).parse(this)
    val vars = cnf.variables()
    val model = MiniSat.miniSat(ff).apply { add(cnf); sat() }.model()
    vars.associate { it.name() to model.evaluateLit(it) }
  }

/** See [org.logicng.io.parsers.PropositionalParser] */
infix fun Formula.and(that: Formula) =
  when {
    this ==  F || that == F -> F
    this == T -> that
    that == T -> this
    else -> "($this) & ($that)"
  }

infix fun Formula.or(that: Formula) =
  when {
    this == T || that == T -> T
    this == F -> that
    that == F -> this
    else -> "($this) | ($that)"
  }

fun Formula.negate() =
  when(this) {
    T -> F
    F -> T
    else -> "~($this)"
  }

infix fun Formula.xor(that: Formula) = eq(that).negate()
infix fun Formula.neq(that: Formula) = xor(that)
infix fun Formula.eq(that: Formula) = "($this) <=> ($that)"
const val T: Formula = "\$true"
const val F: Formula = "\$false"

fun Formula.toBool() = drop(1).toBooleanStrictOrNull()

fun <T> makeFormula(
  m1: Matrix<T, *, *>,
  m2: Matrix<T, *, *>,
  filter: (Int, Int) -> Boolean = { _, _ -> true },
  ifmap: (T, T) -> Formula
) =
  if (m1.shape() != m2.shape())
    throw Exception("Shape mismatch: ${m1.shape()} != ${m2.shape()}")
  else m1.data.zip(m2.data)
    .filterIndexed { i, _ -> filter(i / m1.numCols, i % m1.numCols) }
    .map { (a, b) -> ifmap(a, b) }
    .reduce { a, b -> a and b }

// Only compare upper triangular entries of the matrix
infix fun Matrix<Formula, *, *>.eqUT(that: Matrix<Formula, *, *>) =
  makeFormula(this, that, { r, c -> r < c }) { a, b -> a eq b  }

infix fun Matrix<Formula, *, *>.eq(that: Matrix<Formula, *, *>) =
  makeFormula(this, that) { a, b -> a eq b }

infix fun Matrix<Formula, *, *>.neq(that: Matrix<Formula, *, *>) =
  (this eq that).negate()

val XOR_SAT_ALGEBRA =
  Ring.of(
    nil = F,
    one = T,
    plus = { a, b -> a xor b },
    times = { a, b -> a and b }
  )