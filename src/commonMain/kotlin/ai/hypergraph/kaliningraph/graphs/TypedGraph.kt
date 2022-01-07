package ai.hypergraph.kaliningraph.graphs

import ai.hypergraph.kaliningraph.types.*
import ai.hypergraph.kaliningraph.vectorize

// TODO: convert to/from other graph types
// TODO: should we lift TypedGraph and inherit other concrete graphs or introduce a Sheaf type?
// https://www.jakobhansen.org/publications/gentleintroduction.pdf
open class TypedGraph<T: Any>
constructor(override val vertices: Set<TypedVertex<T>> = setOf()):
  Graph<TypedGraph<T>, TypedEdge<T>, TypedVertex<T>>(vertices), TGFamily<T> {
  constructor(vararg vertices: TypedVertex<T>): this(vertices.toSet())
}

interface TGFamily<T: Any>: IGF<TypedGraph<T>, TypedEdge<T>, TypedVertex<T>> {
  override val G: (vertices: Set<TypedVertex<T>>) -> TypedGraph<T>
    get() = {vertices -> TypedGraph(vertices) }
  override val E: (s: TypedVertex<T>, t: TypedVertex<T>) -> TypedEdge<T>
    get() = { s, t -> TypedEdge(s, t) }
  override val V: (old: TypedVertex<T>, edgeMap: (TypedVertex<T>) -> Set<TypedEdge<T>>) -> TypedVertex<T>
    get() = { old, edgeMap -> TypedVertex(old, edgeMap) }
}

/** TODO: This does not work properly due to [IVertex.id] */
class TypedVertex<T: Any> constructor(
  val t: T? = null,
  var occupied: Boolean = false,
  override val edgeMap: (TypedVertex<T>) -> Set<TypedEdge<T>>,
) : Vertex<TypedGraph<T>, TypedEdge<T>, TypedVertex<T>>(t.toString()), TGFamily<T> {
  constructor(out: Set<TypedVertex<T>> = setOf()) :
    this(edgeMap = { s ->  out.map { t -> TypedEdge<T>(s, t) }.toSet() })
  constructor(t: T, out: Set<TypedVertex<T>> = emptySet()) :
    this(t = t, edgeMap = { s -> out.map { t -> TypedEdge<T>(s, t) }.toSet() })
  constructor(t: T, edgeMap: (TypedVertex<T>) -> Set<TypedEdge<T>>) :
    this(t = t, occupied = false, edgeMap = edgeMap)
  constructor(tv: TypedVertex<T>, edgeMap: (TypedVertex<T>) -> Set<TypedEdge<T>>):
    this(tv.t, tv.occupied, edgeMap)
  override fun encode() = (t?.toString() ?: "").vectorize()
}

open class TypedEdge<T: Any>(override val source: TypedVertex<T>, override val target: TypedVertex<T>, val v: String? = null) :
  Edge<TypedGraph<T>, TypedEdge<T>, TypedVertex<T>>(source, target), TGFamily<T> {
}