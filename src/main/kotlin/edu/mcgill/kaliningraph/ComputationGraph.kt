package edu.mcgill.kaliningraph

import kotlin.reflect.KProperty

class Notebook {
  var graph = Graph<Value, UnlabeledEdge>()

  var a by Value(); var b by Value(); var c by Value(); var d by Value()
  var e by Value(); var f by Value(); var g by Value(); var h by Value()
  var i by Value(); var j by Value(); var k by Value(); var l by Value()

  operator fun Value.minus(value: Any) =
    Value("-" + randomString()) {
      setOf(UnlabeledEdge(this), UnlabeledEdge(wrap(value)))
    }.also { graph += Graph(it) }

  operator fun Value.plus(value: Any) =
    Value("+" + randomString()) {
      setOf(UnlabeledEdge(this), UnlabeledEdge(wrap(value)))
    }.also { graph += Graph(it) }


  fun wrap(value: Any) = if(value is Value) value else Value(value.toString())

  companion object {
    operator fun invoke(builder: Notebook.() -> Unit) =
      Notebook().also { it.builder() }.graph.reversed()
  }
}

class Value(
  id: String = randomString(),
  override val edgeMap: (Value) -> Collection<UnlabeledEdge>
) : Node<Value, UnlabeledEdge>(id) {
  constructor(id: String? = randomString(), out: Set<Value> = emptySet()) :
    this(id ?: randomString(), { out.map { UnlabeledEdge(it) } })

  constructor(out: Set<Value> = setOf()) : this(randomString(), { out.map { UnlabeledEdge(it) } })

  override fun equals(other: Any?) = (other as? Value)?.id == id
  override fun hashCode() = id.hashCode()
  override fun toString() = id.first().toString()
  override fun new(id: String?, out: Set<Value>) = Value(id, out)
  override fun new(id: String, edgeMap: (Value) -> Collection<UnlabeledEdge>) = Value(id, edgeMap)

  operator fun setValue(builder: Notebook, prop: KProperty<*>, value: Value) {
    builder.graph += Graph(Value(prop.name) {
      setOf(UnlabeledEdge(Value("=" + randomString()) { setOf(UnlabeledEdge(value)) }))
    })
  }
}

open class UnlabeledEdge(override val target: Value): Edge<UnlabeledEdge, Value>(target) {
  override fun newTarget(target: Value) = UnlabeledEdge(target)
}
