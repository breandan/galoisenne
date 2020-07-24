package edu.mcgill.kaliningraph

/**
 * DSL for constructing simple graphs - just enumerate paths. Duplicates will be merged.
 */

class LabeledGraph {
  var graph = Graph<Vertex, LabeledEdge>()

  val a by Vertex(); val b by Vertex(); val c by Vertex(); val d by Vertex()
  val e by Vertex(); val f by Vertex(); val g by Vertex(); val h by Vertex()
  val i by Vertex(); val j by Vertex(); val k by Vertex(); val l by Vertex()

  operator fun Vertex.minus(v: Vertex) =
    Vertex(v.id) { v.edges + LabeledEdge(this) }.also { graph += Graph(it) }

  operator fun Vertex.plus(edge: LabeledEdge) =
    new { edges + edge }.also { graph += Graph(it) }

  operator fun Vertex.plus(vertex: Vertex) =
    (asGraph() + vertex.asGraph()).also { graph += it }

  class ProtoEdge(val source: Vertex, val label: String)

  // Arithmetic is right-associative, so we construct in reverse and flip after
  operator fun Vertex.minus(symbols: String) = ProtoEdge(this, symbols)
  operator fun ProtoEdge.minus(target: Vertex) = target + LabeledEdge(source, label)

  companion object {
    operator fun invoke(builder: LabeledGraph.() -> Unit) =
      LabeledGraph().also { it.builder() }.graph.reversed()
  }
}

class Vertex(
  id: String = randomString(),
  override val edgeMap: (Vertex) -> Collection<LabeledEdge>
) : Node<Vertex, LabeledEdge>(id) {
  constructor(id: String? = randomString(), out: Set<Vertex> = emptySet()) :
    this(id ?: randomString(), { out.map { LabeledEdge(it) } })

  constructor(out: Set<Vertex> = setOf()) : this(randomString(), { out.map { LabeledEdge(it) } })

  override fun equals(other: Any?) = (other as? Vertex)?.id == id
  override fun hashCode() = id.hashCode()
  override fun toString() = id
  override fun new(newId: String, out: Set<Vertex>) = Vertex(newId, out)
  override fun new(newId: String, edgeMap: (Vertex) -> Collection<LabeledEdge>) = Vertex(newId, edgeMap)
}

open class LabeledEdge(
  override val target: Vertex,
  val label: String? = null
) : Edge<LabeledEdge, Vertex>(target) {
  override fun newTarget(target: Vertex) = LabeledEdge(target, label)
}