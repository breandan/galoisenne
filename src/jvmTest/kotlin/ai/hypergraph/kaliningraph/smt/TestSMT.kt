package ai.hypergraph.kaliningraph.smt

import ai.hypergraph.kaliningraph.tensor.*
import ai.hypergraph.kaliningraph.times
import ai.hypergraph.kaliningraph.types.*
import org.junit.jupiter.api.Test
import org.sosy_lab.java_smt.SolverContextFactory
import org.sosy_lab.java_smt.SolverContextFactory.Solvers.PRINCESS
import org.sosy_lab.java_smt.api.*
import org.sosy_lab.java_smt.api.NumeralFormula.IntegerFormula
import org.sosy_lab.java_smt.api.SolverContext.ProverOptions.GENERATE_MODELS
import java.math.BigInteger
import kotlin.math.*
import kotlin.reflect.KProperty
import kotlin.test.*

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.smt.TestSMT"
 */
class TestSMT {
  @Test
  // https://en.wikipedia.org/wiki/Taxicab_number
  fun testTaxiCab() = SMTInstance().solve {
    val a by IntVar()
    println(a)
    val b by IntVar()
    val c by IntVar()
    val d by IntVar()

    val exp = 3
    val f = ((a pwr exp) pls (b pwr exp)) eq ((c pwr exp) pls (d pwr exp))

    val bigNum = 2

    val containsNegative = (a lt 0) or (b lt 0) or (c lt 0)

    val areLarge =
      (a pwr 2 gt bigNum) and
        (b pwr 2 gt bigNum) and
        (c pwr 2 gt bigNum) and
        (d pwr 2 gt bigNum)

    val isNontrivial = f and containsNegative and areLarge and ifm.distinct(listOf(a, b, c, d))

    val solution = solveInteger(isNontrivial)
    println(solution)

    assertEquals(
      solution[a]!!.pow(exp) + solution[b]!!.pow(exp),
      solution[c]!!.pow(exp) + solution[d]!!.pow(exp)
    )
  }

  @Test
  // https://en.wikipedia.org/wiki/Sums_of_three_cubes
  fun testSumOfCubes() = SMTInstance().solve {
    val a by IntVar()
    val b by IntVar()
    val c by IntVar()
    val d by IntVar()

    val exp = 3
    val f = (a pwr exp) pls (b pwr exp) pls (c pwr exp) eq d

    val bigNum = 3

    val containsNegative = (a lt 0) or (b lt 0) or (c lt 0)

    val areLarge =
      (a pwr 2 gt bigNum) and
        (b pwr 2 gt bigNum) and
        (c pwr 2 gt bigNum) and
        (d pwr 2 gt bigNum)

    val isNontrivial = f and containsNegative and areLarge

    val solution = solveInteger(isNontrivial)
    println(solution)

    assertEquals(
      solution[a]!!.pow(exp) + solution[b]!!.pow(exp) + solution[c]!!.pow(exp),
      solution[d]
    )
  }

  @Test
  fun testNonLinear() {
    // Search space of integer quadruplets w, x, y, z s.t. w^z + x^z = y^z + z
    data class Fermat(val w: Int, val x: Int, val y: Int, val z: Int)

    fun Int.pow(exp: Int) = toDouble().pow(exp).toInt()
    fun Fermat.isValidSolution() = w.pow(z) + x.pow(z) == y.pow(z) + z

    val range = (-10..10).toSet()
    val erange = (2..8).toSet()
    range.let { it * it * it * erange }
      .map { Fermat(it.first.first.first, it.first.first.second, it.first.second, it.second) }
      .filter { it.isValidSolution() }
      .forEach { println(it) }

    (1..2).forEach { z ->
      SMTInstance().solve {
        val w by IntVar()
        val x by IntVar()
        val y by IntVar()

        val f = ((w pwr z) pls (x pwr z)) eq ((y pwr z) pls z)

        val bigNum = 3

        val containsNegative = (w lt 0) or (x lt 0) or (y lt 0)

        val areLarge =
          (w pwr 2 gt bigNum) and
            (x pwr 2 gt bigNum) and
            (y pwr 2 gt bigNum)

        val isNontrivial = f and containsNegative and areLarge

        val solution = solveInteger(isNontrivial)
        println("$solution, z=$z")

        assertEquals(
          solution[w]!!.pow(z) + solution[x]!!.pow(z),
          solution[y]!!.pow(z) + z
        )
      }
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.smt.TestSMT.testBistochastic"
*/
  @Test
  fun testBistochastic() = SMTInstance().solve {
    val dim = 7

    val m = FreeMatrix(dim) { i, j -> IntVar("v$i$j") }

    val sum by IntVar()

    fun FreeMatrix<SMTF>.isStochastic() =
      rows.map { it.reduce { a, b: SMTF -> a.run { this + b } } eq sum }
        .reduce { a, b -> a and b }

    val isBistochastic = m.isStochastic() and m.transpose().isStochastic()

    val assymetric = m.indices
      .mapNotNull { (i, j) -> if (i != j) m[j][i] neq m[i][j] else null }
      .reduce { a, b -> a and b }

    val positive = m.data.map { it gt 0 }.reduce { a, b -> a and b }

    val solution = solveInteger(isBistochastic and assymetric and positive)

    val sol = m.rows.map { i -> i.map { solution[it]!! } }
    sol.forEach { println(it.joinToString(" ")) }

    (m.rows + m.cols).windowed(2).map { twoSlices ->
      val (a, b) = twoSlices[0] to twoSlices[1]
      assertEquals(a.sumOf { solution[it]!! }, b.sumOf { solution[it]!! })
    }
  }


  @Test
  fun testIsAssociative() = SMTInstance().solve {
    val SMT_ALGEBRA = DefaultSMTAlgebra()

    val dim = 2
    val a = SMTMatrix(dim, dim, SMT_ALGEBRA) { i, j -> IntVar("a$i$j") }
    val b = SMTMatrix(dim, dim, SMT_ALGEBRA) { i, j -> IntVar("b$i$j") }
    val c = SMTMatrix(dim, dim, SMT_ALGEBRA) { i, j -> IntVar("c$i$j") }

    val plusAssoc = ((a + b) + c) eq (a + (b + c))
//    val multAssoc = ((a * b) * c) eq (a * (b * c)) // TODO: why is this so slow?

    val goal = qfm.forall((a.data + b.data + c.data).map { it.formula }, plusAssoc)
    val shouldBeTrue = prove(goal)

    println(shouldBeTrue)

    assertTrue(shouldBeTrue)
  }

  @Test
  fun testIsDistributive() = SMTInstance().solve {
    val SMT_ALGEBRA = DefaultSMTAlgebra()

    val dim = 2
    val a = SMTMatrix(dim, dim, SMT_ALGEBRA) { i, j -> IntVar("a$i$j") }
    val b = SMTMatrix(dim, dim, SMT_ALGEBRA) { i, j -> IntVar("b$i$j") }
    val c = SMTMatrix(dim, dim, SMT_ALGEBRA) { i, j -> IntVar("c$i$j") }

    val plusDistrib = (a * (b + c)) neq (a * b + a * c)

    val goal = qfm.exists((a.data + b.data + c.data).map { it.formula }, plusDistrib)
    val shouldBeFalse = prove(goal)

    println(shouldBeFalse)

    assertFalse(shouldBeFalse)
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.smt.TestSMT.testMatInv"
*/

  @Test
  fun testMatInv() = SMTInstance().solve {
    val SMT_ALGEBRA = DefaultSMTAlgebra()
    val dim = 10
    val A = SMTMatrix(dim, dim, SMT_ALGEBRA) { i, j -> SMTF(i + j) }
    val B = SMTMatrix(dim, dim, SMT_ALGEBRA) { i, j -> IntVar("a$i$j") }

    val isInverse = (A * B * A) eq A

    val solution = solveInteger(isInverse)

    val sol = B.rows.map { i -> i.map { solution[it]!! } }
    val maxLen = sol.flatten().maxOf { it.toString().length }
    sol.forEach { println(it.joinToString(" ") { it.toString().padStart(maxLen) }) }

    val aDoub = DoubleMatrix(dim, dim) { i, j -> (i + j).toDouble() }
    val sDoub = DoubleMatrix(dim, dim) { i, j -> sol[i][j].toDouble() }
    assertEquals(aDoub * sDoub * aDoub, aDoub)
  }

  class SMTMatrix<T>(
    override val numRows: Int,
    override val numCols: Int = numRows,
    override val data: List<T>,
    override val algebra: Ring.of<T>,
  ) : Matrix<T, Ring.of<T>, SMTMatrix<T>> {
    constructor(algebra: Ring.of<T>, elements: List<T>) : this(
      algebra = algebra,
      numRows = sqrt(elements.size.toDouble()).toInt(),
      data = elements
    )

    constructor(
      numRows: Int,
      numCols: Int = numRows,
      algebra: Ring.of<T>,
      f: (Int, Int) -> T
    ) : this(
      algebra = algebra,
      numRows = numRows,
      numCols = numCols,
      data = List(numRows * numCols) { f(it / numRows, it % numCols) }
    )

    override fun new(numRows: Int, numCols: Int, data: List<T>, algebra: Ring.of<T>) =
      SMTMatrix(numRows, numCols, data, algebra)
  }

  fun SMTInstance.DefaultSMTAlgebra(): Ring.of<SMTF> =
    Ring.of(
      nil = NIL,
      one = ONE,
      plus = { a, b -> a + b },
      times = { a, b -> a * b }
    )
  open class SMTF(open val ctx: SMTInstance, val formula: IntegerFormula):
    IntegerFormula by formula, Group<SMTF> {
    private fun SMTF(f: SMTInstance.() -> Any) = SMTF(ctx, ctx.wrapInt(ctx.f()))
    
    override val nil: SMTF by lazy { SMTF { 0 } }
    override val one: SMTF by lazy { SMTF { 1 } }
    override fun SMTF.plus(t: SMTF): SMTF = SMTF { this@SMTF pls t }
    override fun SMTF.times(t: SMTF): SMTF = SMTF { this@SMTF mul t }

    override fun toString() = formula.toString()
    override fun hashCode() = formula.hashCode()
    override fun equals(other: Any?) =
      other is SMTF && other.formula == this.formula || 
        other is IntegerFormula && formula == other
  }

  class SMTInstance(
    val solverContext: SolverContext = SolverContextFactory.createSolverContext(PRINCESS),
    val ifm: IntegerFormulaManager = solverContext.formulaManager.integerFormulaManager,
    val bfm: BooleanFormulaManager = solverContext.formulaManager.booleanFormulaManager,
    val qfm: QuantifiedFormulaManager = solverContext.formulaManager.quantifiedFormulaManager
  ) {
    fun solve(function: SMTInstance.() -> Unit) = function()

    fun BoolVar() = BoolVrb(bfm)
    fun BoolVar(name: String) = TestSAT.SATF(this, bfm.makeVariable(name))
    fun SATF(b: Boolean) = TestSAT.SATF(this, bfm.makeBoolean(b))

    fun IntVar() = IntVrb(ifm)
    fun IntVar(name: String) = SMTF(this, ifm.makeVariable(name))
    fun SMTF(i: Int) = SMTF(this, ifm.makeNumber(i.toLong()))

    val NIL: SMTF = SMTF(this, ifm.makeNumber(0))
    val ONE: SMTF = SMTF(this, ifm.makeNumber(1))

    class IntVrb(val ifm: IntegerFormulaManager) {
      operator fun getValue(nothing: Nothing?, property: KProperty<*>) = ifm.makeVariable(property.name)
    }

    class BoolVrb(val bfm: BooleanFormulaManager) {
      operator fun getValue(nothing: Nothing?, property: KProperty<*>) = bfm.makeVariable(property.name)
    }

    fun solveFormula(vararg bs: BooleanFormula) =
      solverContext.newProverEnvironment(GENERATE_MODELS).use { prover ->
        for (f in bs) prover.addConstraint(f)
        assert(!prover.isUnsat) { "Unsat!" }
        prover.modelAssignments
      }

    fun solveInteger(vararg constraints: BooleanFormula): Map<IntegerFormula, Int> =
      solveFormula(*constraints).associate { it.key as IntegerFormula to (it.value as BigInteger).toInt() }

    fun solveBoolean(vararg constraints: BooleanFormula): Map<BooleanFormula, Boolean> =
      solveFormula(*constraints).associate { it.key as BooleanFormula to it.value as Boolean }


    // TODO: isUnsat what we really want here?
    fun prove(goal: BooleanFormula) =
      solverContext.newProverEnvironment().use { prover ->
        prover.push(goal)
        !prover.isUnsat
      }

    fun wrapInt(input: Any): IntegerFormula =
      when (input) {
        is Number -> ifm.makeNumber("$input")
        is SMTF -> input.formula
        is IntegerFormula -> input
        else -> throw NumberFormatException("Bad number $input (${input.javaClass.name})")
      }

    fun wrapBool(input: Any): BooleanFormula =
      when (input) {
        is Boolean -> bfm.makeBoolean(input)
        is TestSAT.SATF -> input.formula
        is BooleanFormula -> input
        else -> throw NumberFormatException("Bad boolean $input (${input.javaClass.name})")
      }

    infix fun Any.pls(b: Any) = ifm.add(wrapInt(this), wrapInt(b))

    infix fun Any.mns(b: Any) = ifm.subtract(wrapInt(this), wrapInt(b))

    infix fun Any.mul(b: Any) = ifm.multiply(wrapInt(this), wrapInt(b))

    infix fun Any.dvd(b: Any) = ifm.divide(wrapInt(this), wrapInt(b))

    infix fun Any.pwr(b: Int) = (2..b).fold(wrapInt(this)) { a, _ -> a mul this }

    infix fun Any.lt(b: Any) = ifm.lessThan(wrapInt(this), wrapInt(b))
    infix fun Any.gt(b: Any) = ifm.greaterThan(wrapInt(this), wrapInt(b))
    infix fun Any.eq(b: Any) =
      if (listOf(this, b).all { it is BooleanFormula || it is Boolean })
        bfm.xor(wrapBool(this), wrapBool(b)).negate()
      else ifm.equal(wrapInt(this), wrapInt(b))
    infix fun Any.neq(b: Any) = eq(b).negate()

    fun Any.negate() = bfm.not(wrapBool(this))
    infix fun Any.and(b: Any) = bfm.and(wrapBool(this), wrapBool(b))
    infix fun Any.or(b: Any) = bfm.or(wrapBool(this), wrapBool(b))

    fun Int.pow(i: Int): Int = toInt().toDouble().pow(i).toInt()

    fun <T> makeFormula(
      m1: Matrix<T, *, *>,
      m2: Matrix<T, *, *>,
      ifmap: (T, T) -> BooleanFormula
    ) = m1.rows.zip(m2.rows)
      .map { (a, b) -> a.zip(b).map { (a, b) -> ifmap(a, b) } }
      .flatten().reduce { a, b -> a and b }

    infix fun <T> Matrix<T, *, *>.eq(other: Matrix<T, *, *>): BooleanFormula =
      makeFormula(this, other) { a, b -> a as Any eq b as Any }
    infix fun <T> Matrix<T, *, *>.neq(other: Matrix<T, *, *>): BooleanFormula =
      bfm.not(this eq other)
  }
}