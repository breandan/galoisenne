package edu.mcgill.kaliningraph.circuits

import edu.mcgill.kaliningraph.*
import edu.mcgill.kaliningraph.circuits.Gate.Companion.wrap
import kotlin.reflect.KProperty

// Mutable environment with support for variable overwriting/reassignment
class Notebook {
  var graph = Graph<Gate, UnlabeledEdge>()

  var a by Gate(); var b by Gate(); var c by Gate(); var d by Gate()
  var e by Gate(); var f by Gate(); var g by Gate(); var h by Gate()
  var i by Gate(); var j by Gate(); var k by Gate(); var l by Gate()

  operator fun Any.plus(that: Any) = wrap(this, that) { a, b -> a + b }
  operator fun Any.minus(that: Any) = wrap(this, that) { a, b -> a - b }
  operator fun Any.times(that: Any) = wrap(this, that) { a, b -> a * b }
  operator fun Any.div(that: Any) = wrap(this, that) { a, b -> a / b }

  operator fun Gate.plus(that: Gate) = join(this, that, "+")
  operator fun Gate.minus(that: Gate) = join(this, that, "-")
  operator fun Gate.times(that: Gate) = join(this, that, "*")
  operator fun Gate.div(that: Gate) = join(this, that, "/")

  fun wrap(left: Any, right: Any, op: (Gate, Gate) -> Gate): Gate =
    op(wrap(left), wrap(right))

  fun join(left: Gate, right: Gate, label: String) =
    Gate(label, left, right).also { graph += Graph(it) }

  companion object {
    operator fun invoke(builder: Notebook.() -> Unit) =
      Notebook().also { it.builder() }.graph.reversed()
  }
}

val a by Gate(); val b by Gate(); val c by Gate(); val d by Gate()
val e by Gate(); val f by Gate(); val g by Gate(); val h by Gate()
val i by Gate(); val j by Gate(); val k by Gate(); val l by Gate()
fun def(vararg params: Gate, body: (Array<out Gate>) -> Gate) =
  NFunction(randomString(), params, body)

operator fun Any.plus(that: Any) = wrap(this, that) { a, b -> a + b }
operator fun Any.minus(that: Any) = wrap(this, that) { a, b -> a - b }
operator fun Any.times(that: Any) = wrap(this, that) { a, b -> a * b }
operator fun Any.div(that: Any) = wrap(this, that) { a, b -> a / b }

operator fun Gate.plus(that: Gate) = join(this, that, "+")
operator fun Gate.minus(that: Gate) = join(this, that, "-")
operator fun Gate.times(that: Gate) = join(this, that, "*")
operator fun Gate.div(that: Gate) = join(this, that, "/")

fun Gate.toGraph() = Graph(this)

fun wrap(left: Any, right: Any, op: (Gate, Gate) -> Gate): Gate =
  op(wrap(left), wrap(right))

fun join(left: Gate, right: Gate, label: String) = Gate(label, left, right)

open class Gate(
  id: String = randomString(),
  val label: String = id,
  override val edgeMap: (Gate) -> Collection<UnlabeledEdge>
) : Node<Gate, UnlabeledEdge>(id) {
  constructor(id: String = randomString(), label: String, vararg gates: Gate) :
    this(id, label, { gates.toSet().map { UnlabeledEdge(it) } })
  constructor(label: String = "", vararg gates: Gate) : this(randomString(), label, *gates)

  override fun equals(other: Any?) = (other as? Gate)?.id == id
  override fun hashCode() = id.hashCode()
  override fun toString() = label
  override fun new(newId: String, out: Set<Gate>) = Gate(newId, label, *out.toTypedArray())
  override fun new(newId: String, edgeMap: (Gate) -> Collection<UnlabeledEdge>) = Gate(newId, label, edgeMap)

  companion object {
    fun wrap(value: Any) = if (value is Gate) value else Gate(value.toString())
    fun wrapAll(vararg values: Any) = values.map { wrap(it) }.toTypedArray()
  }

  override operator fun getValue(a: Any?, prop: KProperty<*>): Gate = Gate(prop.name, prop.name)
  open operator fun setValue(builder: Notebook, prop: KProperty<*>, value: Gate) {
    builder.graph += Graph(Gate(prop.name, prop.name, Gate("=", value)))
  }
}

class NFunction(
  val name: String = randomString(),
  val params: Array<out Gate> = arrayOf(),
  val body: (Array<out Gate>) -> Gate = { Gate(name) }
): Gate(id = name, edgeMap = { setOf(UnlabeledEdge(body(params))) }) {
  operator fun invoke(vararg args: Any): Gate =
    if (arityMatches(*args))
      Gate("λ", *wrapAll(*args).let { it.plusElement(Gate(name, Gate("=", body(it)))) })
    else throw Exception(invokeError(*args))

  fun invokeError(vararg args: Any) =
    "Could not invoke $name(${params.joinToString(", ")}) with ${args.joinToString(", ")}"

  fun arityMatches(vararg args: Any) = args.size == params.size
  operator fun getValue(nothing: Nothing?, property: KProperty<*>): NFunction =
    NFunction(property.name, params, body)
}

open class UnlabeledEdge(override val target: Gate): Edge<UnlabeledEdge, Gate>(target) {
  override fun newTarget(target: Gate) = UnlabeledEdge(target)
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