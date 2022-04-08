package ai.hypergraph.kaliningraph.smt

import ai.hypergraph.kaliningraph.sampling.randomString
import ai.hypergraph.kaliningraph.tensor.*
import ai.hypergraph.kaliningraph.types.*
import com.google.common.collect.ImmutableList
import org.sosy_lab.java_smt.SolverContextFactory
import org.sosy_lab.java_smt.SolverContextFactory.Solvers
import org.sosy_lab.java_smt.api.*
import org.sosy_lab.java_smt.api.Model.ValueAssignment
import org.sosy_lab.java_smt.api.NumeralFormula.*
import org.sosy_lab.java_smt.api.SolverContext.ProverOptions.*
import java.math.BigInteger
import kotlin.math.pow
import kotlin.reflect.KProperty

/**
 * java_smt API is a mess to debug, maybe output smt2 script directly by
 * dogfooding our own DSL [ai.hypergraph.kaliningraph.graphs.ComputationGraph]
 * Plus, it would allow us to run this on multiplatform and not just JVM.
 * Builder for SAT/SMT Instance - single use only!
 */
// https://mathsat.fbk.eu/smt2examples.html
class SMTInstance(
  val solver: Solvers = Solvers.SMTINTERPOL,
  val context: SolverContext = SolverContextFactory.createSolverContext(solver),
  val ifm: IntegerFormulaManager = context.formulaManager.integerFormulaManager,
  val bfm: BooleanFormulaManager = context.formulaManager.booleanFormulaManager,
): IntegerFormulaManager by ifm {
  val SMT_ALGEBRA =
    Ring.of(
      nil = Literal(0),
      one = Literal(1),
      plus = { a, b -> a + b },
      times = { a, b -> a * b }
    )

  val XOR_SAT_ALGEBRA =
    Ring.of(
      nil = Literal(false),
      one = Literal(true),
      plus = { a, b -> a xor b },
      times = { a, b -> a and b }
    )

  val GF2_SMT_ALGEBRA =
    Ring.of(
      nil = Literal(0),
      one = Literal(1),
      plus = { a, b ->
        if (a == nil) b
        else if (b == nil) a
        else if (a == one && b == one) nil
        else (a + b) mod 2
      },
      // TODO: * Unsupported by SMTInterpol
      times = { a, b ->
        if (a == nil || b == nil) nil
        else if (a == one) b
        else if (b == one) a
        else (a * b) mod 2
      }
    )

  val SAT_ALGEBRA =
    Ring.of(
      nil = Literal(false),
      one = Literal(true),
      plus = { a, b ->
          if (a == one || b == one) one
          else if (a == nil) b
          else if (b == nil) a
          else a or b
      },
      times = { a, b ->
          if (a == nil || b == nil) nil
          else if (a == one) b
          else if (b == one) a
          else a and b
      }
    )

  fun solve(function: SMTInstance.() -> Unit) = function()
  fun BoolVar(): BoolVrb = BoolVrb(this)
  fun BoolVar(name: String = randomString()): SATF = SATF(this, bfm.makeVariable(name))
  fun Literal(b: Boolean): SATF = SATF(this, bfm.makeBoolean(b))

  fun IntVar() = IntVrb(this)
  fun IntVar(name: String) = SMTF(this, makeVariable(name))
  fun Literal(i: Int) = SMTF(this, makeNumber(i.toLong()))

  class IntVrb(val smtInstance: SMTInstance) {
    operator fun getValue(nothing: Nothing?, property: KProperty<*>) =
      smtInstance.let { SMTF(it, it.makeVariable(property.name)) }
  }

  class BoolVrb(val smtInstance: SMTInstance) {
    operator fun getValue(nothing: Nothing?, property: KProperty<*>) =
      smtInstance.let { SATF(it, it.bfm.makeVariable(property.name)) }
  }

  fun solveFormula(vararg bs: BooleanFormula): ImmutableList<ValueAssignment> =
    context.newProverEnvironment(GENERATE_MODELS, GENERATE_UNSAT_CORE)
      .use { prover ->
        for (f in bs) prover.addConstraint(f)

        if(prover.isUnsat) {
          println("Unsat core:\n" + prover.unsatCore.joinToString("\n"))
          throw Exception("Unsat!")
        }

        prover.modelAssignments// This may not assign all free variables?
//        associateWith { prover.model.evaluate(it) /*Can be null?*/ }
      }

  class Solution<T>(dict: Map<*, T>) {
    val dict = dict.map { (k, v) -> k.toString() to v }.toMap()
    val keys = dict.keys
    operator fun get(any: Any): T? = dict[any.toString()]
  }

  fun solveInteger(vararg constraints: BooleanFormula): Solution<Int> =
    solveFormula(*constraints)
      .associate { it.key as IntegerFormula to (it.value as BigInteger).toInt() }
      .let { Solution(it) }

  fun solveInteger(satf: SATF): Solution<Int> = solveInteger(satf.formula)

  fun solveBoolean(satf: SATF): Solution<Boolean> =
    solveFormula(satf.formula)
      .associate { it.key as BooleanFormula to it.value as Boolean }
      .let { Solution(it) }

  fun prove(goal: BooleanFormula) =
    context.newProverEnvironment().use { prover ->
      prover.push(goal)
      !prover.isUnsat
    }

  fun wrapInt(input: Any): IntegerFormula =
    when (input) {
      is Number -> makeNumber("$input")
      is SMTF -> input.formula
      is IntegerFormula -> input
      else -> throw NumberFormatException("Bad number $input (${input.javaClass.name})")
    }

  fun wrapBool(input: Any): BooleanFormula =
    when (input) {
      is Boolean -> bfm.makeBoolean(input)
      is SATF -> input.formula
      is BooleanFormula -> input
      else -> throw NumberFormatException("Bad boolean $input (${input.javaClass.name})")
    }

  infix fun Any.pls(b: Any): IntegerFormula = add(wrapInt(this), wrapInt(b))
  infix fun Any.mns(b: Any): IntegerFormula = subtract(wrapInt(this), wrapInt(b))
  infix fun Any.mul(b: Any): IntegerFormula = multiply(wrapInt(this), wrapInt(b))
  infix fun Any.dvd(b: Any): IntegerFormula = divide(wrapInt(this), wrapInt(b))
  infix fun Any.pwr(b: Int): IntegerFormula = (2..b).fold(wrapInt(this)) { a, _ -> a mul this }
  infix fun Any.mod(b: Any): IntegerFormula = modulo(wrapInt(this), wrapInt(b))

  infix fun Any.lt(b: Any): BooleanFormula = lessThan(wrapInt(this), wrapInt(b))
  infix fun Any.gt(b: Any): BooleanFormula = greaterThan(wrapInt(this), wrapInt(b))
  infix fun Any.eq(b: Any): BooleanFormula =
    if (listOf(this, b).all { it is BooleanFormula || it is Boolean || it is SATF })
      bfm.xor(wrapBool(this), wrapBool(b)).negate()
    else equal(wrapInt(this), wrapInt(b))

  infix fun Any.neq(b: Any): BooleanFormula = eq(b).negate()

  fun Any.negate(): BooleanFormula = bfm.not(wrapBool(this))
  infix fun Any.and(b: Any): BooleanFormula = bfm.and(wrapBool(this), wrapBool(b))
  infix fun Any.or(b: Any): BooleanFormula = bfm.or(wrapBool(this), wrapBool(b))
  infix fun Any.xor(b: Any): BooleanFormula = bfm.xor(wrapBool(this), wrapBool(b))

  fun Int.pow(i: Int): Int = toInt().toDouble().pow(i).toInt()

  fun <T> makeFormula(
    m1: Matrix<T, *, *>,
    m2: Matrix<T, *, *>,
    filter: (Int, Int) -> Boolean = { _, _ -> true },
    ifmap: (T, T) -> BooleanFormula
  ) =
    if (m1.shape() != m2.shape())
      throw Exception("Shape mismatch: ${m1.shape()} != ${m2.shape()}")
    else m1.data.zip(m2.data)
      .filterIndexed { i, _ -> filter(i / m1.numCols, i % m1.numCols) }
      .map { (a, b) -> ifmap(a, b) }
      .reduce { a, b -> a and b }

  // Only compare upper triangular entries of the matrix
  infix fun <T> Matrix<T, *, *>.eqUT(that: Matrix<T, *, *>): SATF =
    makeFormula(this, that, { r, c -> r < c }) { a, b -> a as Any eq b as Any }
      .let { SATF(this@SMTInstance, it) }

  infix fun <T> Matrix<T, *, *>.eq(that: Matrix<T, *, *>): SATF =
    makeFormula(this, that) { a, b -> a as Any eq b as Any }
      .let { SATF(this@SMTInstance, it) }

  infix fun <T> Matrix<T, *, *>.neq(that: Matrix<T, *, *>): SATF =
    (this eq that).negate()
}

open class SATF(
  open val ctx: SMTInstance,
  val formula: BooleanFormula
) : Group<SATF> {
  private fun SATF(f: SMTInstance.() -> Any) = SATF(ctx, ctx.wrapBool(ctx.f()))

  override val nil: SATF by lazy { SATF { false } }
  override val one: SATF by lazy { SATF { true } }
  override fun SATF.plus(t: SATF): SATF = SATF { formula or t.formula }
  override fun SATF.times(t: SATF): SATF = SATF { formula and t.formula }

  infix fun or(t: Any): SATF = SATF { formula or t }
  infix fun xor(t: Any): SATF = SATF { formula xor t }
  infix fun and(t: Any): SATF = SATF { formula and t }
  infix fun eq(t: Any): SATF = SATF { formula eq t }
  fun negate(): SATF = SATF { formula.negate() }

  fun toBool() = formula.toString().toBooleanStrictOrNull()
  override fun toString() = when ("$formula") {
    "true" -> "1"
    "false" -> "0"
    else -> "$formula"
  }
  override fun hashCode() = formula.hashCode()
  override fun equals(other: Any?) =
    other is SATF && other.formula.toString() == this.formula.toString() ||
      other is BooleanFormula && formula == other
}

open class SMTF(
  open val ctx: SMTInstance,
  val formula: IntegerFormula
): IntegerFormula by formula, Group<SMTF> {
  private fun SMTF(f: SMTInstance.() -> Any) = SMTF(ctx, ctx.wrapInt(ctx.f()))

  override val nil: SMTF by lazy { SMTF { 0 } }
  override val one: SMTF by lazy { SMTF { 1 } }
  override fun SMTF.plus(t: SMTF): SMTF = SMTF { this@SMTF pls t }
  override fun SMTF.times(t: SMTF): SMTF = SMTF { this@SMTF mul t }

  infix fun mod(t: Any): SMTF = SMTF { formula mod t }

  fun toInt() = toString().toIntOrNull()
  override fun toString() = formula.toString()
  override fun hashCode() = formula.hashCode()
  override fun equals(other: Any?) =
    other is SMTF && other.formula == this.formula ||
      other is IntegerFormula && formula == other
}