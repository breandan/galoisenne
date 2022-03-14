package ai.hypergraph.kaliningraph.smt

import ai.hypergraph.kaliningraph.tensor.BooleanMatrix
import ai.hypergraph.kaliningraph.types.*
import org.junit.jupiter.api.Test
import org.sosy_lab.java_smt.api.*
import kotlin.test.assertEquals

class TestSAT {

  open class SATF(
    open val ctx: TestSMT.SMTInstance,
    val formula: BooleanFormula
  ):
    BooleanFormula by formula, Group<SATF> {
    private fun SATF(f: TestSMT.SMTInstance.() -> Any) =
      SATF(ctx, ctx.wrapBool(ctx.f()))

    override val nil: SATF by lazy { SATF { 0 } }
    override val one: SATF by lazy { SATF { 1 } }
    override fun SATF.plus(t: SATF): SATF = SATF { formula or t.formula }
    override fun SATF.times(t: SATF): SATF = SATF { formula and t.formula }

    override fun toString() = formula.toString()
    override fun hashCode() = formula.hashCode()
    override fun equals(other: Any?) =
      other is SATF && other.formula == this.formula ||
        other is NumeralFormula.IntegerFormula && formula == other
  }

  fun TestSMT.SMTInstance.DefaultSATAlgebra() =
    Ring.of(
      nil = bfm.makeFalse(),
      one = bfm.makeTrue(),
      plus = { a, b -> a or b },
      times = { a, b -> a and b }
    )

/*
TODO: Why doesn't this work?
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.smt.TestSAT.testBMatInv"
*/
  fun testBMatInv() = TestSMT.SMTInstance().solve {
    val SAT_ALGEBRA = DefaultSATAlgebra()
    val dim = 3
    val A = TestSMT.SMTMatrix(dim, dim, SAT_ALGEBRA) { i, j -> SATF(true) }
    val B = TestSMT.SMTMatrix(dim, dim, SAT_ALGEBRA) { i, j -> BoolVar("b$i$j") }

    val isInverse = (A * B * A) eq A

    val solution = solveBoolean(isInverse)

    println(solution.entries.joinToString("\n") { it.key.toString() + "," + it.value })

    val sol = B.rows.map { i -> i.map { solution[it]!! } }
    val maxLen = sol.flatten().maxOf { it.toString().length }
    sol.forEach {
      println(it.joinToString(" ") { it.toString().padStart(maxLen) })
    }

    val aDoub = BooleanMatrix(dim, dim) { i, j -> (i + j) % 2 == 0 }
    val sDoub = BooleanMatrix(dim, dim) { i, j -> sol[i][j] }
    assertEquals(aDoub * sDoub * aDoub, aDoub)
  }
}