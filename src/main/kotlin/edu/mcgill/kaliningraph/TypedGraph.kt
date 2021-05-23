package edu.mcgill.kaliningraph

import edu.mcgill.kaliningraph.typefamily.*
import guru.nidi.graphviz.attribute.*
import guru.nidi.graphviz.attribute.Color.*

class TypedGraphBuilder<T: Encodable> {
  var mutGraph = TypedGraph<T>()

  operator fun TypedVertex<T>.minus(v: TypedVertex<T>) =
    TypedVertex(v.t) { v.outgoing + TypedEdge(v, this) }
      .also { mutGraph += it.graph }
  operator fun TypedVertex<T>.minus(t: T): TypedVertex<T> = this - TypedVertex(t)
  operator fun T.minus(t: TypedVertex<T>): TypedVertex<T> = TypedVertex(this) - t
  operator fun T.minus(t: T): TypedVertex<T> = TypedVertex(this) - TypedVertex(t)

  operator fun TypedVertex<T>.plus(edge: TypedEdge<T>) =
    V(id) { outgoing + edge }.also { mutGraph += it.graph }

  operator fun TypedVertex<T>.plus(vertex: TypedVertex<T>) =
    (graph + vertex.graph).also { mutGraph += it }

  operator fun invoke(builder: TypedGraphBuilder<T>.() -> Unit) =
    TypedGraphBuilder<T>().also { it.builder() }.mutGraph
}

// TODO: convert to/from other graph types
open class TypedGraph<T: Encodable>
constructor(override val vertices: Set<TypedVertex<T>> = setOf()):
  Graph<TypedGraph<T>, TypedEdge<T>, TypedVertex<T>>(vertices) {
  constructor(vararg vertices: TypedVertex<T>): this(vertices.toSet())
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