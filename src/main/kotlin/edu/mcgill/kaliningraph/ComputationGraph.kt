package edu.mcgill.kaliningraph

import edu.mcgill.kaliningraph.Slot.Companion.wrap
import kotlin.reflect.KProperty

class Notebook {
  var graph = Graph<Slot, UnlabeledEdge>()

  var a by Slot(); var b by Slot(); var c by Slot(); var d by Slot()
  var e by Slot(); var f by Slot(); var g by Slot(); var h by Slot()
  var i by Slot(); var j by Slot(); var k by Slot(); var l by Slot()

  var funA by NFunction(); var funB by NFunction(); var funC by NFunction()

  operator fun Any.plus(that: Any) = wrap(this, that) { a, b -> a + b }
  operator fun Any.minus(that: Any) = wrap(this, that) { a, b -> a - b }
  operator fun Any.times(that: Any) = wrap(this, that) { a, b -> a * b }
  operator fun Any.div(that: Any) = wrap(this, that) { a, b -> a / b }

  operator fun Slot.plus(that: Slot) = join(this, that, "+")
  operator fun Slot.minus(that: Slot) = join(this, that, "-")
  operator fun Slot.times(that: Slot) = join(this, that, "*")
  operator fun Slot.div(that: Slot) = join(this, that, "/")

  fun wrap(left: Any, right: Any, op: (Slot, Slot) -> Slot): Slot =
    op(wrap(left), wrap(right))

  fun join(left: Slot, right: Slot, label: String) =
    Slot(label + randomString()) {
      setOf(UnlabeledEdge(left), UnlabeledEdge(right))
    }.also { graph += Graph(it) }

  fun def(vararg params: Slot, body: (Array<out Slot>) -> Slot) =
    NFunction(randomString(), params, body)

  class NFunction(
    val name: String = randomString(),
    val params: Array<out Slot> = arrayOf(),
    val body: (Array<out Slot>) -> Slot = { Slot() }
  ): Slot(name, { setOf(UnlabeledEdge(body(params)))}) {
    operator fun invoke(vararg args: Any): Slot =
      if(arityMatches(*args)) Slot(name, Slot("=", body(wrapAll(args))))
      else throw Exception(invokeError(*args))

    fun invokeError(vararg args: Any) =
      "Could not invoke $name(${params.joinToString(", ")}) with ${args.joinToString(", ")}"

    fun arityMatches(vararg args: Any) = args.size == params.size
    operator fun getValue(nothing: Nothing?, property: KProperty<*>): NFunction =
      NFunction(property.name, params, body)
  }

  companion object {
    operator fun invoke(builder: Notebook.() -> Unit) =
      Notebook().also { it.builder() }.graph.reversed()
  }
}

open class Slot(
  id: String = randomString(),
  override val edgeMap: (Slot) -> Collection<UnlabeledEdge>
) : Node<Slot, UnlabeledEdge>(id) {
  constructor(id: String = randomString(), vararg slots: Slot) :
    this(id, { slots.toSet().map { UnlabeledEdge(it) } })

  override fun equals(other: Any?) = (other as? Slot)?.id == id
  override fun hashCode() = id.hashCode()
  override fun toString() = id.first().toString()
  override fun new(id: String, out: Set<Slot>) = Slot(id, *out.toTypedArray())
  override fun new(id: String, edgeMap: (Slot) -> Collection<UnlabeledEdge>) = Slot(id, edgeMap)

  companion object {
    fun wrap(value: Any) = if (value is Slot) value else Slot(value.toString())
    fun wrapAll(vararg values: Any) = values.map { wrap(it) }.toTypedArray()
  }

  open operator fun setValue(builder: Notebook, prop: KProperty<*>, value: Slot) {
    builder.graph += Graph(Slot(prop.name, Slot("=" + randomString(), value)))
  }
}

open class UnlabeledEdge(override val target: Slot): Edge<UnlabeledEdge, Slot>(target) {
  override fun newTarget(target: Slot) = UnlabeledEdge(target)
}

fun main() {
  Notebook {
    val funA by def(a, b, c) {
      a + b + c
    }

    j = funA(3, 2, 1)
    d = e * f
    g = 1 - h
    i = a + d + g
  }.show()
}