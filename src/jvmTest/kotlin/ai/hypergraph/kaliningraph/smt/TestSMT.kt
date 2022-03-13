package ai.hypergraph.kaliningraph.smt

import ai.hypergraph.kaliningraph.tensor.*
import ai.hypergraph.kaliningraph.times
import ai.hypergraph.kaliningraph.types.*
import org.junit.jupiter.api.*
import org.sosy_lab.java_smt.SolverContextFactory
import org.sosy_lab.java_smt.SolverContextFactory.Solvers.PRINCESS
import org.sosy_lab.java_smt.api.*
import org.sosy_lab.java_smt.api.NumeralFormula.IntegerFormula
import org.sosy_lab.java_smt.api.SolverContext.ProverOptions.GENERATE_MODELS
import kotlin.math.*
import kotlin.random.Random
import kotlin.reflect.KProperty
import kotlin.test.assertEquals

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

    val solution = solveFor(*m.data.toTypedArray())
      .subjectTo(isBistochastic and assymetric and positive)

    val sol = m.rows.map { i -> i.map { solution[it]!! } }
    sol.forEach { println(it.joinToString(" ")) }

    (m.rows + m.cols).windowed(2).map { twoSlices ->
      val (a, b) = twoSlices[0] to twoSlices[1]
      Assertions.assertEquals(a.sumOf { solution[it]!! }, b.sumOf { solution[it]!! })
    }
  }

  class SMTMatrix(
    override val numRows: Int,
    override val numCols: Int = numRows,
    override val data: List<SMTF>,
    override val algebra: Ring.of<SMTF>,
  ) : Matrix<SMTF, Ring.of<SMTF>, SMTMatrix> {
    constructor(algebra: Ring.of<SMTF>, elements: List<SMTF>) : this(
      algebra = algebra,
      numRows = sqrt(elements.size.toDouble()).toInt(),
      data = elements
    )

    constructor(
      numRows: Int,
      numCols: Int = numRows,
      algebra: Ring.of<SMTF>,
      f: (Int, Int) -> SMTF
    ) : this(
      algebra = algebra,
      numRows = numRows,
      numCols = numCols,
      data = List(numRows * numCols) { f(it / numRows, it % numCols) }
    )

    override fun new(numRows: Int, numCols: Int, data: List<SMTF>, algebra: Ring.of<SMTF>) =
      SMTMatrix(numRows, numCols, data, algebra)
  }

  fun DefaultSMTAlgebra(instance: SMTInstance) =
    Ring.of(
      nil = instance.nil,
      one = instance.one,
      plus = { a, b -> a.run { this + b } },
      times = { a, b -> a.run { this * b } }
    )

  @Test
  fun testIsAssociative() = SMTInstance().solve {
    val SMT_ALGEBRA = DefaultSMTAlgebra(this)

    val dim = 2
    val a = SMTMatrix(dim, dim, SMT_ALGEBRA) { i, j -> IntVar("a$i$j") }
    val b = SMTMatrix(dim, dim, SMT_ALGEBRA) { i, j -> IntVar("b$i$j") }
    val c = SMTMatrix(dim, dim, SMT_ALGEBRA) { i, j -> IntVar("c$i$j") }

    val plusAssoc = ((a + b) + c) eq (a + (b + c))
//    val multAssoc = ((a * b) * c) eq (a * (b * c)) //TODO: why is this so slow?

    val goal = qfm.forall((a.data + b.data + c.data).map { it.formula }, plusAssoc)
    val shouldBeTrue = prove(goal)

    println(shouldBeTrue)

    Assertions.assertTrue(shouldBeTrue)
  }

  @Test
  fun testIsDistributive() = SMTInstance().solve {
    val SMT_ALGEBRA = DefaultSMTAlgebra(this)

    val dim = 2
    val a = SMTMatrix(dim, dim, SMT_ALGEBRA) { i, j -> IntVar("a$i$j") }
    val b = SMTMatrix(dim, dim, SMT_ALGEBRA) { i, j -> IntVar("b$i$j") }
    val c = SMTMatrix(dim, dim, SMT_ALGEBRA) { i, j -> IntVar("c$i$j") }

    val plusDistrib = (a * (b + c)) neq (a * b + a * c)

    val goal = qfm.exists((a.data + b.data + c.data).map { it.formula }, plusDistrib)
    val shouldBeFalse = prove(goal)

    println(shouldBeFalse)

    Assertions.assertFalse(shouldBeFalse)
  }


/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.smt.TestSMT.testMatInv"
*/

  @Test
  fun testMatInv() = SMTInstance().solve {
    val SMT_ALGEBRA = DefaultSMTAlgebra(this)
    val dim = 3
    val a = SMTMatrix(dim, dim, SMT_ALGEBRA) { i, j -> SMTF(i + j) }
    val b = SMTMatrix(dim, dim, SMT_ALGEBRA) { i, j -> IntVar("a$i$j") }

    val isInverse = (a * b * a).data.zip(a.data)
      .map { (a1, a2) -> a1.eq(a2) }
      .reduce { i, j -> i and j }

    val solution = solveFor(*b.data.toTypedArray()).subjectTo(isInverse)

    val sol = b.rows.map { i -> i.map { solution[it]!! } }
    val maxLen = sol.flatten().maxOf { it.toString().length }
    sol.forEach { println(it.joinToString(" ") { it.toString().padStart(maxLen) }) }

    val aDoub = DoubleMatrix(dim, dim) { i, j -> (i + j).toDouble() }
    val sDoub = DoubleMatrix(dim, dim) { i, j -> sol[i][j].toDouble() }
    assertEquals(aDoub * sDoub * aDoub, aDoub)
  }

  open class SMTF(open val ctx: SMTInstance, val formula: IntegerFormula):
    IntegerFormula by formula, Group<SMTF> {
    private fun SMTF(f: SMTInstance.() -> Any) = SMTF(ctx, ctx.wrap(ctx.f()))
    
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

    fun IntVar() = IntVrb(ifm)
    fun IntVar(name: String) = SMTF(this, ifm.makeVariable(name))

    fun SMTF(i: Int) = SMTF(this, ifm.makeNumber(i.toLong()))
    val nil: SMTF = SMTF(this, ifm.makeNumber(0))
    val one: SMTF = SMTF(this, ifm.makeNumber(1))

    class IntVrb(val mgr: IntegerFormulaManager) {
      operator fun getValue(nothing: Nothing?, property: KProperty<*>) = mgr.makeVariable(property.name)
    }

    fun solveFor(vararg fs: IntegerFormula) =
      ProofContext(*fs.map {
        // TODO: Necessary to unwrap because error: "Cannot get the formula info of type Formula in the Solver!"
        if (it is SMTF) it.formula else it
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

    fun wrap(input: Any): IntegerFormula =
      when (input) {
        is Number -> ifm.makeNumber("$input")
        is SMTF -> input.formula
        is IntegerFormula -> input
        else -> throw NumberFormatException("Bad number $input (${input.javaClass.name})")
      }

    infix fun Any.pls(b: Any) = ifm.add(wrap(this), wrap(b))

    infix fun Any.mns(b: Any) = ifm.subtract(wrap(this), wrap(b))

    infix fun Any.mul(b: Any) = ifm.multiply(wrap(this), wrap(b))

    infix fun Any.dvd(b: Any) = ifm.divide(wrap(this), wrap(b))

    infix fun Any.pwr(b: Int) = (2..b).fold(wrap(this)) { a, _ -> a mul this }

    infix fun Any.lt(b: Any) = ifm.lessThan(wrap(this), wrap(b))
    infix fun Any.gt(b: Any) = ifm.greaterThan(wrap(this), wrap(b))
    infix fun Any.eq(b: Any) = ifm.equal(wrap(this), wrap(b))
    infix fun Any.neq(b: Any) = bfm.not(eq(b))

    infix fun BooleanFormula.and(b: BooleanFormula) = bfm.and(this, b)
    infix fun BooleanFormula.or(b: BooleanFormula) = bfm.or(this, b)

    fun Int.pow(i: Int): Int = toInt().toDouble().pow(i).toInt()

    fun makeFormula(m1: Matrix<SMTF, *, *>, m2: Matrix<SMTF, *, *>, ifmap: (SMTF, SMTF) -> BooleanFormula) =
      m1.rows.zip(m2.rows)
        .map { (a, b) -> a.zip(b).map { (a, b) -> ifmap(a, b) } }
        .flatten().reduce { a, b -> a and b }

    infix fun Matrix<SMTF, *, *>.eq(other: Matrix<SMTF, *, *>) =
      makeFormula(this, other) { a, b -> a eq b }

    infix fun Matrix<SMTF, *, *>.neq(other: Matrix<SMTF, *, *>) =
      makeFormula(this, other) { a, b -> a neq b }
  }
}