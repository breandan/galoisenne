@file:Suppress("FunctionName", "UNUSED_PARAMETER", "unused")

package ai.hypergraph.reasoning

import ai.hypergraph.kaliningraph.joinToScalar
import ai.hypergraph.kaliningraph.tensor.FreeMatrix
import ai.hypergraph.kaliningraph.tensor.Matrix
import ai.hypergraph.kaliningraph.tensor.UTMatrix
import ai.hypergraph.kaliningraph.types.cache
import ai.hypergraph.kaliningraph.types.*
import org.kosat.Kosat
import kotlin.jvm.JvmName
import kotlin.math.absoluteValue
import kotlin.random.Random

typealias CNF = Set<Clause>
typealias Clause = Set<Literal>
typealias Literal = Int
typealias SATVector = Array<CNF>
typealias SATRubix = UTMatrix<CNF>

fun CNF.pretty() = joinToString("\n") { it.joinToString(",") { it.toString() } }

val CNF.asInt by cache { flatten().first() }
val CNF.variables by cache { flatten().map { it.absoluteValue }.toSet() }
val CNF.hashToIdx by cache { variables.mapIndexed { idx, hash -> hash to idx + 1 }.toMap() }
val CNF.solver by cache {
  val t =
    map { it.map { hashToIdx[it.absoluteValue]!! * if (it < 0) -1 else 1 }.toMutableList() }.toMutableList()
  println("Formula: ${t.joinToString("\n") { it.joinToString(",")}.let { "$it\nLength: ${it.length}" }}")
//  println("Formula: ${t.joinToString("\n") { it.joinToString(",")}.length}")
  Kosat(t, variables.size)
}

val CNF.model: Model by cache { models.first() }

val CNF.models: Sequence<Model> by cache {
  var prev: Set<Int>
  generateSequence {
    val t = solver.apply { if (!solve()) return@generateSequence null }
      .getModel().also { prev = it.toSet() }.toSet().let { positiveLiterals ->
        Model(variables.associateWith { hashToIdx[it] in positiveLiterals })
      }
    solver.addClause(prev.map { -it }) // Ensures each model is unique
    t
  }
}

operator fun CNF.invoke(model: Model): Boolean =
  if (model.isEmpty()) false else map { clause ->
    clause.map { lit ->
      val v = model[lit.absoluteValue]!!
      if (lit < 0) !v else v
    }.any()
  }.all { it }

class Model(val varMap: Map<Int, Boolean>): Map<Int, Boolean> by varMap {
  operator fun get(cnf: CNF): Boolean? = varMap[cnf.flatten().first()]
  override fun get(key: Int): Boolean? = varMap[key]
  override fun toString() = varMap.toString()
}

fun Boolean.toCNF(): CNF = if (this) T else F
fun Int.asCNF(): CNF = setOf(setOf(this))

object T: CNF by setOf(setOf())
object F: CNF by setOf(setOf())

infix fun T.v(t: T) = T
infix fun T.v(t: F) = T
infix fun F.v(t: T) = T
infix fun F.v(t: F) = F

infix fun T.ʌ(t: T) = T
infix fun T.ʌ(t: F) = F
infix fun F.ʌ(t: T) = F
infix fun F.ʌ(t: F) = F

fun Set<Literal>.dontCare() = map { (it v -it) }.fold (T as CNF) { a, b -> a ʌ b }

@JvmName("fob") infix fun CNF.v(t: F) = this
@JvmName("lob") infix fun Literal.v(t: F) = this
@JvmName("fot") infix fun CNF.v(t: T) = variables.dontCare()
@JvmName("lot") infix fun Literal.v(t: T) = setOf(this).dontCare()
@JvmName("tof") infix fun T.v(t: CNF) = t.variables.dontCare()
@JvmName("tol") infix fun T.v(t: Literal) = setOf(t).dontCare()
@JvmName("bof") infix fun F.v(c: CNF) = c
@JvmName("bol") infix fun F.v(l: Literal) = l

@JvmName("fab") infix fun CNF.ʌ(t: F) = F
@JvmName("lab") infix fun Literal.ʌ(t: F) = F
@JvmName("fat") infix fun CNF.ʌ(t: T) = this
@JvmName("lat") infix fun Literal.ʌ(t: T) = this
@JvmName("taf") infix fun T.ʌ(t: CNF) = t
@JvmName("tal") infix fun T.ʌ(t: Literal) = t
@JvmName("baf") infix fun F.ʌ(t: CNF) = F
@JvmName("bal") infix fun F.ʌ(t: Literal) = F

@JvmName("lal") infix fun Literal.ʌ(l: Literal): CNF = setOf(setOf(this), setOf(l))
@JvmName("fal") infix fun CNF.ʌ(l: Literal): CNF = plus(setOf(setOf(l)))
@JvmName("laf") infix fun Literal.ʌ(c: CNF): CNF = c.plus(setOf(setOf(this)))
@JvmName("faf") infix fun CNF.ʌ(c: CNF): CNF = plus(c)

fun CNF.unitPropagate(l: Literal): CNF =
  mapNotNull { clause -> if (l in clause) null else clause - (-l) }.toSet()

// Nothing very interesting happens up until this point.
// Follows Jason's "Building up CNF Formulas Directly" strategy:
// https://www.cs.jhu.edu/~jason/tutorials/convert-to-CNF.html
@JvmName("lol") infix fun Literal.v(l: Literal): CNF = setOf(setOf(this, l))
@JvmName("fol") infix fun CNF.v(l: Literal): CNF = this v l.asCNF()
@JvmName("lof") infix fun Literal.v(l: CNF): CNF = l v asCNF()
@JvmName("fof") infix fun CNF.v(that: CNF): CNF = when {
  this is T -> T
  this is F -> that
  that is F -> this
  that is T -> T
  that.size == 1 -> this.map { it + that.first() }.toSet()
  this.size == 1 -> that.map { it + this.first() }.toSet()
  // P v Q <=> CONVERT(~Z v P) and CONVERT(Z v Q)
  else -> FreshLit(variables + that.variables).let { (-it v this) ʌ (it v that) }
}

fun FreshLit(s: Set<Int> = emptySet()): Literal =
  generateSequence { Random.nextInt(Int.MAX_VALUE / 2, Int.MAX_VALUE) }.first { it !in s }

// TODO: Not sure how quickly this will blow up, but let's see...
@JvmName("fef") infix fun CNF.eq(that: CNF): CNF = (this ʌ that) v (this.negate() ʌ that.negate())
@JvmName("fxf") infix fun CNF.ⴲ(that: CNF): CNF = (this ʌ that.negate()) v (this.negate() ʌ that)
@JvmName("lng") fun Literal.negate(): Literal = -this
operator fun CNF.unaryMinus(): CNF = negate()
@JvmName("fng") fun CNF.negate(): CNF = when {
  this.size == 1 && this.first().size == 1 -> (-this.first().first()).asCNF()
  // If φ has the form ~(P v Q), then return CONVERT(~P ^ ~Q).  // de Morgan's Law
  this.size == 1 -> this.first().map { -it }.fold(T as CNF) { a, b -> a ʌ b }
  // If φ has the form ~(P ^ Q), then return CONVERT(~P v ~Q).  // de Morgan's Law
  else -> map { setOf(it).negate() }.fold(F as CNF) { a, b -> a v b }
}

val RXOR_SAT_ALGEBRA get() =
  Ring.of(
    nil = F,
    one = T,
    plus = { a, b -> a ⴲ b },
    times = { a, b -> a ʌ b }
  )

val RSAT_ALGEBRA get() =
  Ring.of(
    nil = F,
    one = T,
    plus = { a, b -> a v b },
    times = { a, b -> a ʌ b }
  )

fun BVar(name: String): CNF = name.hashCode().absoluteValue.asCNF()
fun BVecVar(size: Int, prefix: String = "", pfx: (Int) -> String = { prefix }): SATVector =
  Array(size) { k -> BVar("${pfx(k)}_f::$k") }
fun RMatVar(name: String, algebra: Ring<CNF>, rows: Int, cols: Int = rows) =
  FreeMatrix(algebra, rows, cols) { i, j -> BVar("$name$i$j") }
fun BLit(b: Boolean): CNF = b.toCNF()
fun BVecLit(l: BooleanArray): SATVector = l.map { it.toCNF() }.toTypedArray()
fun BVecLit(l: List<Boolean>): SATVector = BVecLit(l.toBooleanArray())
fun BVecLit(size: Int, f: (Int) -> CNF): SATVector = Array(size) { f(it) }

infix fun SATVector.eq(that: SATVector): CNF =
  if (size != that.size) throw Exception("Shape mismatch, incomparable!")
  else zip(that).map { (a, b) -> a eq b }.reduce { a, b -> a ʌ b }

// Only compare upper triangular entries of the matrix
infix fun Matrix<CNF, *, *>.eqUT(that: Matrix<CNF, *, *>): CNF =
  joinToScalar(this, that, filter = { r, c -> r < c }, join = { a, b -> a eq b }, reduce = { a, b -> a ʌ b })

infix fun Matrix<CNF, *, *>.eq(that: Matrix<CNF, *, *>): CNF =
  if (shape() != that.shape()) throw Exception("Shape mismatch, incomparable!")
  else joinToScalar(this, that, join = { a, b -> a eq b }, reduce = { a, b -> a ʌ b })

infix fun Matrix<CNF, *, *>.neq(that: Matrix<CNF, *, *>): CNF = (this eq that).negate()
