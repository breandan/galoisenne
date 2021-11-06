package ai.hypergraph.kaliningraph.smt

import ai.hypergraph.kaliningraph.tensor.*
import ai.hypergraph.kaliningraph.types.*
import org.junit.jupiter.api.*
import org.sosy_lab.java_smt.SolverContextFactory
import org.sosy_lab.java_smt.SolverContextFactory.Solvers.PRINCESS
import org.sosy_lab.java_smt.api.*
import org.sosy_lab.java_smt.api.NumeralFormula.IntegerFormula
import org.sosy_lab.java_smt.api.SolverContext.ProverOptions.GENERATE_MODELS
import kotlin.math.*
import kotlin.reflect.KProperty

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

    val isNontrivial = f and containsNegative and areLarge and fm.distinct(listOf(a, b, c, d))

    val solution = solveFor(a, b, c, d).subjectTo(isNontrivial)
    println(solution)

    Assertions.assertEquals(
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

    val solution = solveFor(a, b, c, d).subjectTo(isNontrivial)
    println(solution)

    Assertions.assertEquals(
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

        val solution = solveFor(w, x, y).subjectTo(isNontrivial)
        println("$solution, z=$z")

        Assertions.assertEquals(
          solution[w]!!.pow(z) + solution[x]!!.pow(z),
          solution[y]!!.pow(z) + z
        )
      }
    }
  }

  @Test
  fun testBistochastic() = SMTInstance().solve {
    val dim = 7

    val m = FreeMatrix(dim) { i, j -> IntVar("v$i$j") }

    val sum by IntVar()

    fun FreeMatrix<Formula>.isStochastic() =
      rows.map { it.reduce { a, b: Formula -> a.run { this + b } } eq sum }
        .reduce { a, b -> a and b }

    val isBistochastic = m.isStochastic() and m.transpose().isStochastic()

    val assymetric = m.indices
      .mapNotNull { (i, j) -> if (i != j) m[j][i] neq m[i][j] else null }
      .reduce { a, b -> a and b }

    val positive = m.data.map { it gt 0 }.reduce { a, b -> a and b }

    val solution = solveFor(*m.data.toTypedArray()).subjectTo(isBistochastic and assymetric and positive)

    val sol = m.rows.map { i -> i.map { solution[it]!! } }
    sol.forEach { println(it.joinToString(" ")) }

    (m.rows + m.cols).windowed(2).map { twoSlices ->
      val (a, b) = twoSlices[0] to twoSlices[1]
      Assertions.assertEquals(a.sumOf { solution[it]!! }, b.sumOf { solution[it]!! })
    }
  }

  class SMTMatrix(
    override val algebra: MatrixRing<Formula, Ring.of<Formula>>,
    override val numRows: Int,
    override val numCols: Int = numRows,
    override val data: List<Formula>,
  ) : Matrix<Formula, Ring.of<Formula>, SMTMatrix> {
    constructor(algebra: MatrixRing<Formula, Ring.of<Formula>>, elements: List<Formula>) : this(
      algebra = algebra,
      numRows = sqrt(elements.size.toDouble()).toInt(),
      data = elements
    )

    constructor(
      numRows: Int,
      numCols: Int = numRows,
      algebra: MatrixRing<Formula, Ring.of<Formula>>,
      f: (Int, Int) -> Formula
    ) : this(
      algebra = algebra,
      numRows = numRows,
      numCols = numCols,
      data = List(numRows * numCols) { f(it / numRows, it % numCols) }
    )
  }

  class SMTAlgebra(val instance: SMTInstance): MatrixRing<Formula, Ring.of<Formula>> {
    override val algebra = Ring.of(
      nil = instance.nil,
      one = instance.one,
      plus = { a, b -> a.run { this + b } },
      times = { a, b -> a.run { this * b } }
    )
  }

  @Test
  fun testIsAssociative() = SMTInstance().solve {
    val SMT_ALGEBRA = SMTAlgebra(this)

    val dim = 2
    val a = SMTMatrix(dim, dim, SMT_ALGEBRA) { i, j -> IntVar("a$i$j") }
    val b = SMTMatrix(dim, dim, SMT_ALGEBRA) { i, j -> IntVar("b$i$j") }
    val c = SMTMatrix(dim, dim, SMT_ALGEBRA) { i, j -> IntVar("c$i$j") }

    val plusAssoc = ((a + b) + c) eq (a + (b + c))
//    val multAssoc = ((a * b) * c) eq (a * (b * c)) //TODO: why is this so slow?

    val goal = qm.forall((a.data + b.data + c.data).map { it.formula }, plusAssoc)
    val shouldBeTrue = prove(goal)

    println(shouldBeTrue)

    Assertions.assertTrue(shouldBeTrue)
  }

  @Test
  fun testIsDistributive() = SMTInstance().solve {
    val SMT_ALGEBRA = SMTAlgebra(this)

    val dim = 2
    val a = SMTMatrix(dim, dim, SMT_ALGEBRA) { i, j -> IntVar("a$i$j") }
    val b = SMTMatrix(dim, dim, SMT_ALGEBRA) { i, j -> IntVar("b$i$j") }
    val c = SMTMatrix(dim, dim, SMT_ALGEBRA) { i, j -> IntVar("c$i$j") }

    val plusDistrib = (a * (b + c)) neq (a * b + a * c)

    val goal = qm.exists((a.data + b.data + c.data).map { it.formula }, plusDistrib)
    val shouldBeFalse = prove(goal)

    println(shouldBeFalse)

    Assertions.assertFalse(shouldBeFalse)
  }

  open class Formula(
    open val ctx: SolverContext,
    val formula: IntegerFormula,
    val fm: IntegerFormulaManager = ctx.formulaManager.integerFormulaManager
  ) : IntegerFormula by formula, Group<Formula, Formula> {
    override val nil: Formula by lazy { Formula(ctx, fm.makeNumber(0)) }
    override val one: Formula by lazy { Formula(ctx, fm.makeNumber(1)) }
    private operator fun IntegerFormula.plus(t: IntegerFormula): IntegerFormula = fm.add(this, t)
    private operator fun IntegerFormula.times(t: IntegerFormula) = fm.multiply(this, t)
    override fun Formula.plus(t: Formula): Formula = Formula(ctx, formula + t.formula)
    override fun Formula.times(t: Formula): Formula = Formula(ctx, formula * t.formula)

    override fun toString() = formula.toString()
    override fun hashCode() = formula.hashCode()
    override fun equals(other: Any?) =
      other is Formula && other.formula == this.formula || other is IntegerFormula && formula == other
  }

  class SMTInstance(
    val solverContext: SolverContext = SolverContextFactory.createSolverContext(PRINCESS),
    val fm: IntegerFormulaManager = solverContext.formulaManager.integerFormulaManager,
    val bm: BooleanFormulaManager = solverContext.formulaManager.booleanFormulaManager,
    val qm: QuantifiedFormulaManager = solverContext.formulaManager.quantifiedFormulaManager
  ) {
    fun solve(function: SMTInstance.() -> Unit) = this.function()

    fun IntVar() = IntVrb(fm)
    fun IntVar(name: String) = Formula(solverContext, fm.makeVariable(name))

    val nil: Formula = Formula(solverContext, fm.makeNumber(0))
    val one: Formula = Formula(solverContext, fm.makeNumber(1))

    class IntVrb(val mgr: IntegerFormulaManager) {
      operator fun getValue(nothing: Nothing?, property: KProperty<*>) = mgr.makeVariable(property.name)
    }

    fun solveFor(vararg fs: IntegerFormula) =
      ProofContext(*fs.map {
        // TODO: Necessary to unwrap because error: "Cannot get the formula info of type Formula in the Solver!"
        if (it is Formula) it.formula else it
      }.toTypedArray())

    class ProofContext(vararg val fs: IntegerFormula)

    fun ProofContext.subjectTo(vararg bs: BooleanFormula) =
      solverContext.newProverEnvironment(GENERATE_MODELS).use { prover ->
        for (f in bs) prover.addConstraint(f)
        assert(!prover.isUnsat) { "Unsat!" }
        prover.model.use { fs.map { a -> a.toString() to it.evaluate(a)!!.toInt() } }
      }.associate { soln -> fs.first { it.toString() == soln.first } to soln.second }

    // TODO: isUnsat what we really want here?
    fun prove(goal: BooleanFormula) =
      solverContext.newProverEnvironment().use { prover ->
        prover.push(goal)
        !prover.isUnsat
      }

    fun wrap(number: Any): IntegerFormula =
      when (number) {
        is Number -> fm.makeNumber("$number")
        is Formula -> number.formula
        is IntegerFormula -> number
        else -> throw NumberFormatException("Bad number $number (${number.javaClass.name})")
      }

    infix fun Any.pls(b: Any) = fm.add(wrap(this), wrap(b))

    infix fun Any.mns(b: Any) = fm.subtract(wrap(this), wrap(b))

    infix fun Any.mul(b: Any) = fm.multiply(wrap(this), wrap(b))

    infix fun Any.dvd(b: Any) = fm.divide(wrap(this), wrap(b))

    infix fun Any.pwr(b: Int) = (2..b).fold(wrap(this)) { a, _ -> a mul this }

    infix fun Any.lt(b: Any) = fm.lessThan(wrap(this), wrap(b))
    infix fun Any.gt(b: Any) = fm.greaterThan(wrap(this), wrap(b))
    infix fun Any.eq(b: Any) = fm.equal(wrap(this), wrap(b))
    infix fun Any.neq(b: Any) = bm.not(eq(b))

    infix fun BooleanFormula.and(b: BooleanFormula) = bm.and(this, b)
    infix fun BooleanFormula.or(b: BooleanFormula) = bm.or(this, b)

    fun Int.pow(i: Int): Int = toInt().toDouble().pow(i).toInt()

    fun makeFormula(m1: Matrix<Formula, *, *>, m2: Matrix<Formula, *, *>, fmap: (Formula, Formula) -> BooleanFormula) =
      m1.rows.zip(m2.rows)
        .map { (a, b) -> a.zip(b).map {(a, b) ->fmap(a, b)} }
        .flatten().reduce { a, b -> a and b }

    infix fun Matrix<Formula, *, *>.eq(other: Matrix<Formula, *, *>) =
      makeFormula(this, other) { a, b -> a eq b }

    infix fun Matrix<Formula, *, *>.neq(other: Matrix<Formula, *, *>) =
      makeFormula(this, other) { a, b -> a neq b }
  }
}

fun main() = TestSMT().run {
  testTaxiCab()
  testSumOfCubes()
  testNonLinear()
  testBistochastic()
  testIsAssociative()
  testIsDistributive()
}