package ai.hypergraph.reasoning

import ai.hypergraph.kaliningraph.types.cache
import ai.hypergraph.kaliningraph.types.*
import org.kosat.Kosat
import kotlin.jvm.JvmName
import kotlin.math.absoluteValue

typealias CNF = Set<Clause>
typealias Clause = Set<Literal>
typealias Literal = Int

val CNF.variables by cache { flatten().map { it.absoluteValue }.toSet() }
val CNF.solver by cache { Kosat(map { it.toMutableList() }.toMutableList(), variables.size) }

// Follows Jason's "Building up CNF Formulas Directly" strategy:
// https://www.cs.jhu.edu/~jason/tutorials/convert-to-CNF.html
@JvmName("lol") infix fun Literal.lor(l: Literal): Clause = setOf(this, l)
@JvmName("col") infix fun Clause.lor(l: Literal): Clause = plus(l)
@JvmName("loc") infix fun Literal.lor(l: Clause): Clause = l.plus(this)
@JvmName("coc") infix fun Clause.lor(c: Clause): Clause = plus(c)
@JvmName("foc") infix fun CNF.lor(c: Clause): CNF = map { it + c }.toSet()
@JvmName("fol") infix fun CNF.lor(l: Literal): CNF = map { it + setOf(l) }.toSet()
@JvmName("cof") infix fun Clause.lor(c: CNF): CNF = c.map { it + this }.toSet()
@JvmName("lof") infix fun Literal.lor(l: CNF): CNF = l.map { it + setOf(this) }.toSet()
@JvmName("fof") infix fun CNF.lor(c: CNF): CNF = (this * c).map { (a, b) -> a + b }.toSet()

@JvmName("lal") infix fun Literal.land(l: Literal): CNF = setOf(setOf(this), setOf(l))
@JvmName("cac") infix fun Clause.land(c: Clause): CNF = setOf(this, c)
@JvmName("cal") infix fun Clause.land(l: Literal): CNF = setOf(this, setOf(l))
@JvmName("lac") infix fun Literal.land(c: Clause): CNF = setOf(setOf(this), c)
@JvmName("fal") infix fun CNF.land(l: Literal): CNF = plus(setOf(setOf(l)))
@JvmName("laf") infix fun Literal.land(c: CNF): CNF = c.plus(setOf(setOf(this)))
@JvmName("fac") infix fun CNF.land(c: Clause): CNF = plus(setOf(c))
@JvmName("caf") infix fun Clause.land(c: CNF): CNF = c.plus(setOf(this))
@JvmName("faf") infix fun CNF.land(c: CNF): CNF = plus(c)

