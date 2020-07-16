package edu.mcgill.kaliningraph

/**
 * DSL for constructing simple graphs - just enumerate paths. Duplicates will be merged.
 */

class GraphBuilder {
  var graph = Graph<Vertex>()

  val a by Vertex(); val b by Vertex(); val c by Vertex(); val d by Vertex()
  val e by Vertex(); val f by Vertex(); val g by Vertex(); val h by Vertex()
  val i by Vertex(); val j by Vertex(); val k by Vertex(); val l by Vertex()

  operator fun Vertex.minus(v: Vertex) =
    Vertex().new(v.id) { v.edges + Edge(this as Vertex) }.also { graph += Graph<Vertex>(it) }

  operator fun Vertex.plus(edge: Edge<Vertex>) =
    Vertex().new { edges + edge }.also { graph += Graph(it) }

  operator fun Vertex.plus(vertex: Vertex) =
    (asGraph() + vertex.asGraph()).also { graph += it }

  class ProtoEdge(val source: Vertex, val label: String)

  // Arithmetic is right-associative, so we construct in reverse and flip after
  operator fun Vertex.minus(symbols: String) = ProtoEdge(this, symbols)
  operator fun ProtoEdge.minus(target: Vertex) = target + Edge<Vertex>(source, label)

  companion object {
    operator fun invoke(builder: GraphBuilder.() -> Unit) =
       GraphBuilder().also { it.builder() }.graph.reversed()
  }
}