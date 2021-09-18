package ai.hypergraph.kaliningraph

import ai.hypergraph.kaliningraph.typefamily.Encodable
import guru.nidi.graphviz.attribute.Color.BLACK
import guru.nidi.graphviz.attribute.Color.RED
import guru.nidi.graphviz.attribute.Style

// TODO: convert to/from other graph types
open class TypedGraph<T: Encodable>
constructor(override val vertices: Set<TypedVertex<T>> = setOf()):
  Graph<TypedGraph<T>, TypedEdge<T>, TypedVertex<T>>(vertices) {
  constructor(vararg vertices: TypedVertex<T>): this(vertices.toSet())
  companion object {} // Pseudoconstructor inheritance
}

class TypedVertex<T: Encodable> constructor(
  val t: T? = null,
  var occupied: Boolean = false,
  override val edgeMap: (TypedVertex<T>) -> Set<TypedEdge<T>>,
) : Vertex<TypedGraph<T>, TypedEdge<T>, TypedVertex<T>>(t.toString()) {
  constructor(out: Set<TypedVertex<T>> = setOf()) :
    this(edgeMap = { s ->  out.map { t -> TypedEdge<T>(s, t) }.toSet() })
  constructor(t: T, out: Set<TypedVertex<T>> = emptySet()) :
    this(t = t, edgeMap = { s -> out.map { t -> TypedEdge<T>(s, t) }.toSet() })

  override fun encode() = (t?.toString() ?: "").vectorize()
  override fun render() = super.render().also {
    if (occupied) it.add(Style.FILLED, RED.fill()) else it.add(BLACK)
  }

  override fun V(newId: String, edgeMap: (TypedVertex<T>) -> Set<TypedEdge<T>>) =
    TypedVertex(t, occupied, edgeMap)
}

open class TypedEdge<T: Encodable>(override val source: TypedVertex<T>, override val target: TypedVertex<T>, val v: String? = null) :
  Edge<TypedGraph<T>, TypedEdge<T>, TypedVertex<T>>(source, target) {
  override fun render() = super.render().also { it.add(if (source.occupied) RED else BLACK) }
}