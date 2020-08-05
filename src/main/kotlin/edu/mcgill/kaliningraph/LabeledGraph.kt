package edu.mcgill.kaliningraph

/**
 * DSL for constructing simple graphs - just enumerate paths. Duplicates will be merged.
 */

class LabeledGraphBuilder {
  var mutGraph = LabeledGraph()

  val a by LGVertex(); val b by LGVertex(); val c by LGVertex(); val d by LGVertex()
  val e by LGVertex(); val f by LGVertex(); val g by LGVertex(); val h by LGVertex()
  val i by LGVertex(); val j by LGVertex(); val k by LGVertex(); val l by LGVertex()

  operator fun LGVertex.minus(v: LGVertex) =
    LGVertex(v.id) { v.outgoing + LabeledEdge(v, this) }.also { mutGraph += it.graph }

  operator fun LGVertex.plus(edge: LabeledEdge) =
    new { outgoing + edge }.also { mutGraph += it.graph }

  operator fun LGVertex.plus(vertex: LGVertex) = (graph + vertex.graph).also { mutGraph += it }

  class ProtoEdge(val source: LGVertex, val label: String)

  // Arithmetic is right-associative, so we construct in reverse and flip after
  operator fun LGVertex.minus(symbols: String) = ProtoEdge(this, symbols)
  operator fun ProtoEdge.minus(target: LGVertex) = target + LabeledEdge(target, source, label)

  companion object {
    operator fun invoke(builder: LabeledGraphBuilder.() -> Unit) =
      LabeledGraphBuilder().also { it.builder() }.mutGraph.reversed()
  }
}

class LabeledGraph(override val vertices: Set<LGVertex> = setOf()):
  Graph<LabeledGraph, LabeledEdge, LGVertex>(vertices) {
  constructor(vararg vertices: LGVertex): this(vertices.toSet())
  override fun new(vertices: Set<LGVertex>) = LabeledGraph(vertices)
}

class LGVertex(
  id: String = randomString(),
  override val edgeMap: (LGVertex) -> Collection<LabeledEdge>
) : Vertex<LabeledGraph, LabeledEdge, LGVertex>(id) {
  constructor(id: String? = randomString(), out: Set<LGVertex> = emptySet()) :
    this(id ?: randomString(), { s -> out.map { t -> LabeledEdge(s, t) } })
  constructor(out: Set<LGVertex> = setOf()) : this(randomString(), { s ->  out.map { t -> LabeledEdge(s, t) } })

  override fun graph(vertices: Set<LGVertex>) = LabeledGraph(vertices)
  override fun new(newId: String, out: Set<LGVertex>) = LGVertex(newId, out)
  override fun new(newId: String, edgeMap: (LGVertex) -> Collection<LabeledEdge>) = LGVertex(newId, edgeMap)
}

open class LabeledEdge(override val source: LGVertex, override val target: LGVertex, val label: String? = null) :
  Edge<LabeledGraph, LabeledEdge, LGVertex>(source, target) {
  override fun new(source: LGVertex, target: LGVertex) = LabeledEdge(source, target, label)
}