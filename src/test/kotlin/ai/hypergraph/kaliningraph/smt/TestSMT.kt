package ai.hypergraph.kaliningraph.smt

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.*
import org.sosy_lab.java_smt.SolverContextFactory
import org.sosy_lab.java_smt.SolverContextFactory.Solvers
import org.sosy_lab.java_smt.api.*
import org.sosy_lab.java_smt.api.NumeralFormula.IntegerFormula
import org.sosy_lab.java_smt.api.SolverContext.ProverOptions.*
import kotlin.math.pow
import kotlin.reflect.KProperty

class TestSMT {
  @Test
  fun testTaxiCab() = SMTInstance().solve {
    val a by IntVar()
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
  fun testNonLinear() = SMTInstance().solve {
    val a by IntVar()
    val b by IntVar()
    val c by IntVar()

    val exp = 2
    val f = ((a pwr exp) pls (b pwr exp)) eq ((c pwr exp) pls exp)

    println(f)
    val solution = solveFor(a, b, c).subjectTo(f)
    println(solution)

    Assertions.assertEquals(
      solution[a]!!.pow(exp) + solution[b]!!.pow(exp),
      solution[c]!!.pow(exp) + exp
    )
  }

  @Test
  fun testBistochastic() = SMTInstance() .solve {
    val dim = 7
    val m = List(dim) { i -> List(dim) { j -> fm.makeVariable("v$i$j") } }

    val sum by IntVar()

    val sameRowSum = m.map { it.reduce { a, b -> a pls b } eq sum }.reduce { a, b -> a and b }
    val sameColSum = (0 until dim)
      .map { i -> (0 until dim).map { j -> m[j][i] }.reduce { a, b -> a pls b } eq sum }
      .reduce { a, b -> a and b }

    val assymetric = (0 until dim)
      .map { i -> (0 until dim).mapNotNull { j -> if (i != j) m[j][i] neq m[i][j] else null } }
      .flatten().reduce { a, b -> a and b }

    val positive = (0 until dim)
      .map { i -> (0 until dim).mapNotNull { j -> m[j][i] gt 0 } }
      .flatten().reduce { a, b -> a and b }

    val solution = solveFor(*m.flatten().toTypedArray())
      .subjectTo(sameRowSum and sameColSum and assymetric and positive)

    val sol = m.map { i -> i.map { solution[it]!! } }
    sol.forEach { println(it.joinToString(" ")) }
  }

  @Test
  fun testIsAssociative() = SMTInstance().solve {
    val dim = 2
    val a = List(dim) { i -> List(dim) { j -> fm.makeVariable("a$i$j") } }
    val b = List(dim) { i -> List(dim) { j -> fm.makeVariable("b$i$j") } }
    val c = List(dim) { i -> List(dim) { j -> fm.makeVariable("c$i$j") } }

    infix fun List<List<IntegerFormula>>.mpls(b: List<List<IntegerFormula>>) =
      this.zip(b).map { (a, b) -> a.zip(b).map { (a, b) -> a pls b } }

    infix fun List<List<IntegerFormula>>.mmul(b: List<List<IntegerFormula>>) =
      this.zip(b).map { (a, b) -> a.zip(b).map { (a, b) -> a mul b } }

    val plusAssoc = ((a mpls b) mpls c) eq (a mpls (b mpls c))
//    val multAssoc = ((a mmul b) mmul c) eq (a mmul (b mmul c))

    val goal = qm.forall((a + b + c).flatten(), plusAssoc)
    val shouldBeTrue = prove(goal)

    println(shouldBeTrue)

    Assertions.assertTrue(shouldBeTrue)

//    val a by IntVar()
//    val b by IntVar()
//    val c by IntVar()
//
//    val assoc = (a pls (b pls c)) eq ((a pls b) pls c)
//
//    println(prove(qm.forall(listOf(a, b, c), assoc)))
  }

  class SMTInstance(
    val solverContext: SolverContext = SolverContextFactory.createSolverContext(Solvers.PRINCESS),
    val fm: IntegerFormulaManager = solverContext.formulaManager.integerFormulaManager,
    val bm: BooleanFormulaManager = solverContext.formulaManager.booleanFormulaManager,
    val qm: QuantifiedFormulaManager = solverContext.formulaManager.quantifiedFormulaManager
  ) {
    fun solve(function: SMTInstance.() -> Unit) = this.function()

    fun IntVar(name: String? = null) = SMTVar(fm, name)
    class SMTVar(val fm: IntegerFormulaManager, val name: String? = null) : IntegerFormula {
      operator fun getValue(nothing: Nothing?, property: KProperty<*>) = this@SMTVar.fm.makeVariable(property.name)
    }

    fun solveFor(vararg fs: IntegerFormula) = ProofContext(fs)

    class ProofContext(val fs: Array<out IntegerFormula>)

    fun ProofContext.subjectTo(vararg bs: BooleanFormula) =
      solverContext.newProverEnvironment(GENERATE_MODELS).use { prover ->
        for (f in bs) prover.addConstraint(f)
        assert(!prover.isUnsat) { "Unsat!" }
        prover.model.use { fs.map { a -> a.toString() to it.evaluate(a)!!.toInt() } }
      }.associate { soln -> fs.first { it.toString() == soln.first } to soln.second }
    
    fun prove(goal: BooleanFormula) =
      solverContext.newProverEnvironment().use { prover ->
        prover.push(goal)
        !prover.isUnsat
      }

    fun wrap(number: Any): IntegerFormula =
      when (number) {
        is Number -> fm.makeNumber("$number")
        is IntegerFormula -> number
        else -> throw NumberFormatException("Bad number $number")
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

    infix fun List<List<IntegerFormula>>.eq(other: List<List<IntegerFormula>>) =
      zip(other)
        .map { (a, b) -> a.zip(b).map { (a, b) -> a eq b } }
    .flatten().reduce { a, b -> a and b }
  }
}

fun main() =
  TestSMT().run {
    testTaxiCab()
    testSumOfCubes()
    testNonLinear()
    testBistochastic()
    testIsAssociative()
  }