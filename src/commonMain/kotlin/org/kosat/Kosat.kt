package org.kosat

import kotlin.math.abs

interface Solver {
    /**
     * Number of variables added (via [addVariable]) to the SAT solver.
     */
    val numberOfVariables: Int

    /**
     * Number of clauses added (via [addClause]) to the SAT solver.
     */
    val numberOfClauses: Int

    /**
     * Add a variable to CNF and return its number
     */
    fun addVariable(): Int

    /**
     * Add a clause to CNF as pure literals or list of literals
     */
    fun addClause(lit: Lit)
    fun addClause(lit1: Lit, lit2: Lit)
    fun addClause(lit1: Lit, lit2: Lit, lit3: Lit)
    fun addClause(literals: List<Lit>)
    fun addClause(literals: Iterable<Lit>)

    /**
     * Solve CNF without assumptions
     */
    fun solve(): Boolean

    /**
     *  Solve CNF with the passed `assumptions`
     */
    fun solve(assumptions: List<Lit>): Boolean
    fun solve(assumptions: Iterable<Lit>): Boolean

    /**
     * Query the Boolean value of a literal.
     *
     * **Note:** the solver should be in the SAT state.
     */
    fun getValue(lit: Lit): Boolean

    /**
     * Query the satisfying assignment (model) for the SAT problem.
     *
     * In general, the Solver implementations construct the model on each call to [getModel].
     * The model could have the large size, so make sure to call this method only once.
     *
     * **Note:** the solver should be in the SAT state.
     * Solver return the latest model (cached)
     * even when the solver is already not in the SAT state (due to possibly new added clauses),
     * but it is advisable to query the model right after the call to [solve] which returned `true`.
     */
    fun getModel(): List<Lit>
}

class Kosat(clauses: MutableList<MutableList<Lit>>, vars: Int = 0) : Solver {
    override val numberOfVariables get() = solver.numberOfVariables
    override val numberOfClauses get() = solver.constraints.size + solver.learnts.size

    private var model: List<Lit>? = null
    private val solver = CDCL(clauses.map { Clause(it) }.toMutableList(), vars)

    override fun addVariable(): Int {
        solver.addVariable()
        return solver.numberOfVariables
    }

    override fun addClause(literals: List<Lit>) {
        solver.newClause(Clause(literals.toMutableList()).renumber())
    }

    override fun addClause(lit: Lit) = addClause(listOf(lit))
    override fun addClause(lit1: Lit, lit2: Lit) = addClause(listOf(lit1, lit2))
    override fun addClause(lit1: Lit, lit2: Lit, lit3: Lit) = addClause(listOf(lit1, lit2, lit3))
    override fun addClause(literals: Iterable<Lit>) = addClause(literals.toList())

    override fun solve(): Boolean {
        model = solver.solve()
        return model != null
    }

    override fun solve(assumptions: List<Lit>): Boolean {
        model = solver.solve(assumptions)
        return model != null
    }

    override fun solve(assumptions: Iterable<Lit>): Boolean = solve(assumptions.toList())

    override fun getModel(): List<Lit> = model ?: listOf()

    override fun getValue(lit: Lit): Boolean {
        return model?.get(abs(lit) - 1) == lit
    }
}
