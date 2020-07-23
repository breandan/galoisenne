package edu.mcgill.kaliningraph

import kotlin.reflect.KProperty

class Notebook {
  var graph = Graph<Slot, UnlabeledEdge>()

  var a by Slot(); var b by Slot(); var c by Slot(); var d by Slot()
  var e by Slot(); var f by Slot(); var g by Slot(); var h by Slot()
  var i by Slot(); var j by Slot(); var k by Slot(); var l by Slot()

  operator fun Any.plus(that: Any) = wrap(this, that) { a, b -> a + b }
  operator fun Any.minus(that: Any) = wrap(this, that) { a, b -> a - b }
  operator fun Any.times(that: Any) = wrap(this, that) { a, b -> a * b }
  operator fun Any.div(that: Any) = wrap(this, that) { a, b -> a / b }

  operator fun Slot.plus(that: Slot) = join(this, that, "+")
  operator fun Slot.minus(that: Slot) = join(this, that, "-")
  operator fun Slot.times(that: Slot) = join(this, that, "*")
  operator fun Slot.div(that: Slot) = join(this, that, "/")

  fun wrap(left: Any, right: Any, op: (Slot, Slot) -> Slot) =
    op(wrap(left), wrap(right))

  fun join(left: Slot, right: Slot, label: String) =
    Slot(label + randomString()) {
      setOf(UnlabeledEdge(left), UnlabeledEdge(right))
    }.also { graph += Graph(it) }

  fun wrap(value: Any) = if(value is Slot) value else Slot(value.toString())

  companion object {
    operator fun invoke(builder: Notebook.() -> Unit) =
      Notebook().also { it.builder() }.graph.reversed()
  }
}

class Slot(
  id: String = randomString(),
  override val edgeMap: (Slot) -> Collection<UnlabeledEdge>
) : Node<Slot, UnlabeledEdge>(id) {
  constructor(id: String? = randomString(), out: Set<Slot> = emptySet()) :
    this(id ?: randomString(), { out.map { UnlabeledEdge(it) } })

  constructor(out: Set<Slot> = setOf()) : this(randomString(), { out.map { UnlabeledEdge(it) } })

  override fun equals(other: Any?) = (other as? Slot)?.id == id
  override fun hashCode() = id.hashCode()
  override fun toString() = id.first().toString()
  override fun new(id: String?, out: Set<Slot>) = Slot(id, out)
  override fun new(id: String, edgeMap: (Slot) -> Collection<UnlabeledEdge>) = Slot(id, edgeMap)

  operator fun setValue(builder: Notebook, prop: KProperty<*>, value: Slot) {
    builder.graph += Graph(Slot(prop.name) {
      setOf(UnlabeledEdge(Slot("=" + randomString()) { setOf(UnlabeledEdge(value)) }))
    })
  }
}

open class UnlabeledEdge(override val target: Slot): Edge<UnlabeledEdge, Slot>(target) {
  override fun newTarget(target: Slot) = UnlabeledEdge(target)
}

fun main() {
  Notebook {
    a = b + c
    d = e * f
    g = 1 - h
    i = a + d + g
  }.show()
}