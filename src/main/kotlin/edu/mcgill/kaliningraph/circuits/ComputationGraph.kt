package edu.umontreal.kotlingrad.experimental.ir

import edu.mcgill.kaliningraph.*
import edu.umontreal.kotlingrad.experimental.ir.Gate.Companion.wrap
import edu.umontreal.kotlingrad.experimental.ir.Polyad.*
import edu.umontreal.kotlingrad.experimental.ir.Dyad.*
import kotlin.reflect.KProperty

// Mutable environment with support for variable overwriting/reassignment
class Notebook {
  var graph = ComputationGraph()

  var a by Var(); var b by Var(); var c by Var(); var d by Var()
  var e by Var(); var f by Var(); var g by Var(); var h by Var()
  var i by Var(); var j by Var(); var k by Var(); var l by Var()

  operator fun Any.plus(that: Any) = wrap(this, that) { a, b -> a + b }
  operator fun Any.minus(that: Any) = wrap(this, that) { a, b -> a - b }
  operator fun Any.times(that: Any) = wrap(this, that) { a, b -> a * b }
  operator fun Any.div(that: Any) = wrap(this, that) { a, b -> a / b }

  fun wrap(left: Any, right: Any, op: (Gate, Gate) -> Gate): Gate =
    op(wrap(left), wrap(right))

  fun join(left: Gate, right: Gate, label: String) =
    Gate(label, left, right).also { graph += it.graph }

  companion object {
    operator fun invoke(builder: Notebook.() -> Unit) =
      Notebook().also { it.builder() }.graph.reversed()
  }
}

val a by Var(); val b by Var(); val c by Var(); val d by Var()
val e by Var(); val f by Var(); val g by Var(); val h by Var()
val i by Var(); val j by Var(); val k by Var(); val l by Var()
fun def(vararg params: Gate, body: (Array<out Gate>) -> Gate) =
  NFunction(randomString(), params, body)

operator fun Any.plus(that: Gate) = wrap(this, that) { a, b -> a + b }
operator fun Any.minus(that: Gate) = wrap(this, that) { a, b -> a - b }
operator fun Any.times(that: Gate) = wrap(this, that) { a, b -> a * b }
operator fun Any.div(that: Gate) = wrap(this, that) { a, b -> a / b }

class ComputationGraph(override val vertices: Set<Gate> = setOf()): Graph<ComputationGraph, UnlabeledEdge, Gate>(vertices) {
  override fun new(vertices: Set<Gate>) = ComputationGraph(vertices)
}

interface Op
enum class Monad: Op { `-`, sin, cos, tan, id }
enum class Dyad: Op { `+`, `-`, `*`, `÷`, `=`, pow, d }
enum class Polyad: Op { λ, Σ, Π }

open class Gate constructor(
  id: String = randomString(),
  val op: Op = Monad.id,
  override val edgeMap: (Gate) -> Collection<UnlabeledEdge>
) : Vertex<ComputationGraph, UnlabeledEdge, Gate>(id) {
  constructor(op: Op = Monad.id, vararg gates: Gate) : this(randomString(), op, *gates)
  constructor(id: String = randomString(), vararg gates: Gate) : this(id, Monad.id, *gates)
  constructor(id: String = randomString(), op: Op = Monad.id, vararg gates: Gate) :
    this(id, op, { s -> gates.toSet().map { t -> UnlabeledEdge(s, t) } })

  companion object {
    fun wrap(value: Any): Gate = if (value is Gate) value else Gate(value.toString())
    fun wrap(left: Any, right: Any, op: (Gate, Gate) -> Gate): Gate = op(wrap(left), wrap(right))
    fun wrapAll(vararg values: Any): Array<Gate> = values.map { wrap(it) }.toTypedArray()
  }

  override fun toString() = if(op != Monad.id) op.toString() else id

  operator fun plus(that: Any) = Gate(`+`, this, wrap(that))
  operator fun minus(that: Any) = Gate(`-`, this, wrap(that))
  operator fun times(that: Any) = Gate(`*`, this, wrap(that))
  operator fun div(that: Any) = Gate(`÷`, this, wrap(that))
  infix fun pow(that: Any) = Gate(pow, this, wrap(that))

  operator fun unaryMinus() = Gate(Monad.`-`, this)

  fun sin() = Gate(Monad.sin, this)
  fun cos() = Gate(Monad.cos, this)
  fun tan() = Gate(Monad.tan, this)

  override fun Graph(vertices: Set<Gate>) = ComputationGraph(vertices)
  override fun Edge(s: Gate, t: Gate) = UnlabeledEdge(s, t)
  override fun Vertex(newId: String, edgeMap: (Gate) -> Collection<UnlabeledEdge>) = 
    Gate(newId, Monad.id, edgeMap)

  override operator fun getValue(a: Any?, prop: KProperty<*>): Gate = Gate(prop.name)
  open operator fun setValue(builder: Notebook, prop: KProperty<*>, value: Gate) {
    builder.graph += Gate(prop.name, Gate(`=`, value)).graph
  }
}

class Var(val name: String = randomString()) : Gate(name)

class NFunction(
  val name: String = randomString(),
  val params: Array<out Gate> = arrayOf(),
  val body: (Array<out Gate>) -> Gate = { Gate(name) }
): Gate(id = name, edgeMap = { s -> setOf(UnlabeledEdge(s, body(params))) }) {
  operator fun invoke(vararg args: Any): Gate =
    if (arityMatches(*args))
      Gate(λ, *wrapAll(*args).let { it.plusElement(Gate(name, Gate(`=`, body(it)))) })
    else throw Exception(invokeError(*args))

  fun invokeError(vararg args: Any) =
    "Could not invoke $name(${params.joinToString(", ")}) with ${args.joinToString(", ")}"

  fun arityMatches(vararg args: Any) = args.size == params.size
  operator fun getValue(nothing: Nothing?, property: KProperty<*>): NFunction =
    NFunction(property.name, params, body)
}

open class UnlabeledEdge(override val source: Gate, override val target: Gate):
  Edge<ComputationGraph, UnlabeledEdge, Gate>(source, target) {
  override fun new(source: Gate, target: Gate) = UnlabeledEdge(source, target)
}

fun main() {
  Notebook {
    val funA by def(a, b, c) { a + b + c }
    j = funA(3, 2, 1)
    j = b * c
    d = e * f
    g = 1 - h
    i = a + d + g
  }.show()
}