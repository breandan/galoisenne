package ai.hypergraph.kaliningraph.smt

import org.sosy_lab.java_smt.SolverContextFactory
import org.sosy_lab.java_smt.SolverContextFactory.Solvers
import org.sosy_lab.java_smt.api.BooleanFormula
import org.sosy_lab.java_smt.api.NumeralFormula.IntegerFormula
import org.sosy_lab.java_smt.api.SolverContext.ProverOptions.GENERATE_MODELS
import javax.script.*
import kotlin.reflect.KProperty

val solverContext = SolverContextFactory.createSolverContext(Solvers.SMTINTERPOL)
val fm = solverContext.formulaManager.integerFormulaManager

fun main() {
    val a by SMTVar()
    val b by SMTVar()
    val c by SMTVar()
//    val v = (a / (b + c)) + (b / (a + c)) + (c / (a + b))
    val v = a + b + c
//    val f = fm.equal(v, fm.makeNumber(4))
    val f = fm.equal(v, fm.makeNumber(9))
    val g = fm.greaterThan(a, fm.makeNumber(2))
    val h = fm.greaterThan(b, fm.makeNumber(2))
    val i = fm.greaterThan(c, fm.makeNumber(2))
    val solution = solveFor(a, b, c).subjectTo(f, g, h, i)
    val engine = ScriptEngineManager().getEngineByExtension("kts")

    // Check solution is correct by evaluating it
    engine.run {
        val bnds = solution.associate { it.first to it.second }
        setBindings(SimpleBindings(bnds), ScriptContext.ENGINE_SCOPE)
        val e = "(a / (b + c)) + (b  / (a + c)) + (c / (a + b))"
        println("f${solution.map { (a, b) -> "$a = $b" }} = $e = ${eval(e)}")
    }
  }

class SMTVar(val name: String? = null): IntegerFormula {
  operator fun getValue(nothing: Nothing?, property: KProperty<*>) = fm.makeVariable(property.name)
}

fun solveFor(vararg fs: IntegerFormula) = ProofContext(fs)

class ProofContext(val fs: Array<out IntegerFormula>)

fun ProofContext.subjectTo(vararg bs: BooleanFormula) =
  solverContext.newProverEnvironment(GENERATE_MODELS).use { prover ->
    for (f in bs) prover.addConstraint(f)
    assert(!prover.isUnsat) { "Unsat!" }
    prover.model.use { fs.map { a -> a.toString() to it.evaluate(a)!!.toInt() } }
  }

fun wrap(number: Number) = fm.makeNumber("$number")

operator fun IntegerFormula.plus(b: IntegerFormula) = fm.add(this, b)

operator fun IntegerFormula.minus(b: IntegerFormula) = fm.subtract(this, b)

operator fun IntegerFormula.times(b: IntegerFormula) = fm.multiply(this, b)

operator fun IntegerFormula.div(b: IntegerFormula) = fm.divide(this, b)

operator fun Number.plus(b: IntegerFormula) = fm.add(wrap(this), b)

operator fun Number.minus(b: IntegerFormula) = fm.subtract(wrap(this), b)

operator fun Number.times(b: IntegerFormula) = fm.multiply(wrap(this), b)

operator fun Number.div(b: IntegerFormula) = fm.divide(wrap(this), b)

operator fun IntegerFormula.plus(b: Number) = fm.add(this, wrap(b))

operator fun IntegerFormula.minus(b: Number) = fm.subtract(this, wrap(b))

operator fun IntegerFormula.times(b: Number) = fm.multiply(this, wrap(b))

operator fun IntegerFormula.div(b: Number) = fm.divide(this, wrap(b))