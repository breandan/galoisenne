package edu.mcgill.kaliningraph.circuits

import edu.mcgill.kaliningraph.*
import edu.mcgill.kaliningraph.circuits.Dyad.*
import edu.mcgill.kaliningraph.circuits.Gate.Companion.wrap
import edu.mcgill.kaliningraph.circuits.Polyad.λ
import guru.nidi.graphviz.attribute.Color.*
import guru.nidi.graphviz.attribute.Label
import kotlin.reflect.KProperty

// Mutable environment with support for variable overwriting/reassignment
class CircuitBuilder {
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

  companion object {
    operator fun invoke(builder: CircuitBuilder.() -> Unit) =
      CircuitBuilder().also { it.builder() }.graph
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

class ComputationGraph(override val vertices: Set<Gate> = setOf()):
  Graph<ComputationGraph, UnlabeledEdge, Gate>(vertices)

interface Op
enum class Monad: Op { `+`, `-`, sin, cos, tan, id, ᵀ }
enum class Dyad: Op { `+`, `-`, `*`, `⊙`, `÷`, `=`, dot, pow, log, d }
enum class Polyad: Op { λ, Σ, Π, map }

open class Gate(
  id: String = randomString(),
  val op: Op = Monad.id,
  override val edgeMap: (Gate) -> Set<UnlabeledEdge>
) : Vertex<ComputationGraph, UnlabeledEdge, Gate>(id) {
  constructor(op: Op = Monad.id, vararg gates: Gate) : this(randomString(), op, *gates)
  constructor(id: String = randomString(), vararg gates: Gate) : this(id, Monad.id, *gates)
  constructor(id: String = randomString(), op: Op = Monad.id, vararg gates: Gate) :
    this(id, op, { s -> gates.toSet().map { t -> UnlabeledEdge(s, t) }.toSet() })

  companion object {
    fun wrap(value: Any): Gate = if (value is Gate) value else Gate(value.toString())
    fun wrap(left: Any, right: Any, op: (Gate, Gate) -> Gate): Gate = op(wrap(left), wrap(right))
    fun wrapAll(vararg values: Any): Array<Gate> = values.map { wrap(it) }.toTypedArray()
  }

  override fun toString() = if(op == Monad.id) id else op.toString()

  operator fun plus(that: Any) = Gate(`+`, this, wrap(that))
  operator fun minus(that: Any) = Gate(`-`, this, wrap(that))
  operator fun times(that: Any) = Gate(`*`, this, wrap(that))
  operator fun div(that: Any) = Gate(`÷`, this, wrap(that))
  infix fun pow(that: Any) = Gate(pow, this, wrap(that))
  infix fun log(that: Any) = Gate(log, this, wrap(that))

  operator fun unaryMinus() = Gate(Monad.`-`, this)

  fun sin() = Gate(Monad.sin, this)
  fun cos() = Gate(Monad.cos, this)
  fun tan() = Gate(Monad.tan, this)
  fun d(that: Any) = Gate(Dyad.d, this, wrap(that))

  override fun G(vertices: Set<Gate>) = ComputationGraph(vertices)
  override fun E(s: Gate, t: Gate) = UnlabeledEdge(s, t)
  override fun V(newId: String, edgeMap: (Gate) -> Set<UnlabeledEdge>) =
    Gate(newId, Monad.id, edgeMap)

  override operator fun getValue(a: Any?, prop: KProperty<*>): Gate = Gate(prop.name)
  open operator fun setValue(builder: CircuitBuilder, prop: KProperty<*>, value: Gate) {
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
    "Could not invoke $name(${params.joinToString()}) with ${args.joinToString()}"

  fun arityMatches(vararg args: Any) = args.size == params.size
  operator fun getValue(nothing: Nothing?, property: KProperty<*>): NFunction =
    NFunction(property.name, params, body)
}

open class UnlabeledEdge(override val source: Gate, override val target: Gate):
  Edge<ComputationGraph, UnlabeledEdge, Gate>(source, target) {
  override fun render() = (target.render() - source.render()).add(Label.of(""))
    .add(if (source.neighbors.size == 1) BLACK else if (source.outgoing.indexOf(this) % 2 == 0) BLUE else RED)
}