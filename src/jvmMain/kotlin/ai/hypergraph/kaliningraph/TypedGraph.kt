package ai.hypergraph.kaliningraph

import ai.hypergraph.kaliningraph.typefamily.*
import guru.nidi.graphviz.attribute.Color.*
import guru.nidi.graphviz.attribute.Style

// TODO: convert to/from other graph types
// TODO: should we lift TypedGraph and inherit other concrete graphs or introduce a Sheaf type?
// https://www.jakobhansen.org/publications/gentleintroduction.pdf
open class TypedGraph<T: Any>
constructor(override val vertices: Set<TypedVertex<T>> = setOf()):
  Graph<TypedGraph<T>, TypedEdge<T>, TypedVertex<T>>(vertices) {
  constructor(vararg vertices: TypedVertex<T>): this(vertices.toSet())
  companion object {} // Pseudoconstructor inheritance
}

/** TODO: This does not work properly due to [IVertex.id] */
class TypedVertex<T: Any> constructor(
  val t: T? = null,
  var occupied: Boolean = false,
  override val edgeMap: (TypedVertex<T>) -> Set<TypedEdge<T>>,
) : Vertex<TypedGraph<T>, TypedEdge<T>, TypedVertex<T>>(t.toString()) {
  constructor(out: Set<TypedVertex<T>> = setOf()) :
    this(edgeMap = { s ->  out.map { t -> TypedEdge<T>(s, t) }.toSet() })
  constructor(t: T, out: Set<TypedVertex<T>> = emptySet()) :
    this(t = t, edgeMap = { s -> out.map { t -> TypedEdge<T>(s, t) }.toSet() })
  constructor(t: T, edgeMap: (TypedVertex<T>) -> Set<TypedEdge<T>>) :
    this(t = t, occupied = false, edgeMap = edgeMap)
  constructor(tv: TypedVertex<T>, edgeMap: (TypedVertex<T>) -> Set<TypedEdge<T>>):
    this(tv.t, tv.occupied, edgeMap)
  override fun encode() = (t?.toString() ?: "").vectorize()
  override fun render() = super.render().also {
    if (occupied) it.add(Style.FILLED, RED.fill()) else it.add(BLACK)
  }

}

open class TypedEdge<T: Any>(override val source: TypedVertex<T>, override val target: TypedVertex<T>, val v: String? = null) :
  Edge<TypedGraph<T>, TypedEdge<T>, TypedVertex<T>>(source, target) {
  override fun render() = super.render().also { it.add(if (source.occupied) RED else BLACK) }
}