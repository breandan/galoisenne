package org.kosat

//import kotlinx.collections.immutable.PersistentList
//import kotlinx.collections.immutable.persistentListOf
import kotlin.math.abs

// DPLL
fun solveCnfDPLL(cnf: CnfRequest): List<Int>? {
    val clauses = ArrayList(cnf.clauses.map { ArrayList(it.lits.toList()) })
    return dpll(clauses)?.sortedBy { abs(it) }
}


fun unitPropagate(clauses: ArrayList<ArrayList<Int>>): List<Int>? {
    val res = mutableListOf<Int>()
    while (true) {
        // If a clause is a unit clause, i.e. it contains only a single unassigned literal, this clause can only be
        // satisfied by assigning the necessary value to make this literal true.
        val clauseToRemove = clauses.firstOrNull { it.size == 1 } ?: return res

        // unit clause's literal
        val literal = clauseToRemove[0]
        res.add(literal)

        if (!substitute(clauses, literal))
            return null
    }
}

fun chooseLiteral(clauses: ArrayList<ArrayList<Int>>): Int {
    // dumb strategy, place your heuristics here
    return clauses.first()[0]
}

// returns false only if after substitution some clause becomes empty
fun substitute(clauses: ArrayList<ArrayList<Int>>, literal: Int): Boolean {
    // removing every clause containing literal
    clauses.removeAll { it.contains(literal) }

    // discarding the complement of literal from every clause containing that complement
    clauses.forEach {
        it.remove(-literal)
        if (it.isEmpty())
            return false
    }

    return true
}

fun dpll(clauses: ArrayList<ArrayList<Int>>): MutableList<Int>? {
    if (clauses.isEmpty())
        return mutableListOf()
//        return persistentListOf()

    if (clauses.any { it.isEmpty() })
        return null

    val unitLits = unitPropagate(clauses) ?: return null
    if (clauses.isEmpty())
    return mutableListOf<Int>().apply { addAll(unitLits) }
//        return persistentListOf<Int>().addAll(unitLits)

    val chosenLit = chooseLiteral(clauses)

    // make clone
    val clone = ArrayList<ArrayList<Int>>(clauses.size)
    clauses.forEach { clone.add(ArrayList(it)) }

    // use clone for literal substitution
    if (substitute(clone, chosenLit)) {
        val recursiveLits = dpll(clone)
        if (recursiveLits != null)
            return recursiveLits.apply { add(chosenLit); addAll(unitLits) }
//                .add(chosenLit).addAll(unitLits)
    }

    // use clauses for complement substitution
    if (substitute(clauses, -chosenLit)) {
        val recursiveLits = dpll(clauses)
        if (recursiveLits != null)
            return recursiveLits.apply { add(-chosenLit); addAll(unitLits) }
//        return recursiveLits.add(-chosenLit).addAll(unitLits)
    }

    return null
}
