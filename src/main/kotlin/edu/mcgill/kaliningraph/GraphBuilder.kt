package edu.mcgill.kaliningraph

import org.apache.commons.math3.distribution.EnumeratedDistribution
import org.apache.commons.math3.util.Pair

/**
 * DSL for constructing simple graphs - just enumerate paths. Duplicates will be merged.
 */

class GraphBuilder {
  var graph = Graph()

  val a = Vertex("a")
  val b = Vertex("b")
  val c = Vertex("c")
  val d = Vertex("d")
  val e = Vertex("e")
  val f = Vertex("f")
  val g = Vertex("g")
  val h = Vertex("h")
  val i = Vertex("i")
  val j = Vertex("j")
  val k = Vertex("k")
  val l = Vertex("l")

  operator fun Vertex.minus(v: Vertex) =
    Vertex(v.id) { v.edges + Edge(this) }.also { graph += Graph(it) }

  operator fun Vertex.plus(edge: Edge) =
    Vertex(id) { edges + edge }.also { graph += Graph(it) }

  operator fun Vertex.plus(vertex: Vertex) =
    (asGraph() + vertex.asGraph()).also { graph += it }

  class ProtoEdge(val source: Vertex, val label: String)

  // Arithmetic is right-associative, so we construct in reverse and flip after
  operator fun Vertex.minus(symbols: String) = ProtoEdge(this, symbols)
  operator fun ProtoEdge.minus(target: Vertex) = target + Edge(source, label)
}