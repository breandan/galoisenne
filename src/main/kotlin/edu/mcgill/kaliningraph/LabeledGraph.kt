package edu.mcgill.kaliningraph

import edu.mcgill.kaliningraph.matrix.BMat
import guru.nidi.graphviz.attribute.Color.BLACK
import guru.nidi.graphviz.attribute.Color.RED
import guru.nidi.graphviz.attribute.Style.FILLED

/**
 * DSL for constructing simple graphs - just enumerate paths. Duplicates will be merged.
 */

class LGBuilder {
  var mutGraph = LabeledGraph()

  val a by LGVertex(); val b by LGVertex(); val c by LGVertex()
  val d by LGVertex(); val e by LGVertex(); val f by LGVertex()
  val g by LGVertex(); val h by LGVertex(); val i by LGVertex()
  val j by LGVertex(); val k by LGVertex(); val l by LGVertex()

  operator fun LGVertex.minus(v: LGVertex) =
    LGVertex(v.id) { v.outgoing + LabeledEdge(v, this) }.also { mutGraph += it.graph }
  operator fun LGVertex.minus(v: String): LGVertex = this - LGVertex(v)
  operator fun String.minus(v: LGVertex): LGVertex = LGVertex(this) - v
  operator fun String.minus(v: String): LGVertex = LGVertex(this) - LGVertex(v)

  operator fun LGVertex.plus(edge: LabeledEdge) =
    Vertex { outgoing + edge }.also { mutGraph += it.graph }

  operator fun LGVertex.plus(vertex: LGVertex) =
    (graph + vertex.graph).also { mutGraph += it }

  class ProtoEdge(val source: LGVertex, val label: String)

  // Arithmetic is right-associative, so we construct in reverse and flip after
  operator fun ProtoEdge.minus(target: LGVertex) =
    target + LabeledEdge(target, source, label)
}

// TODO: convert to/from other graph types
open class LabeledGraph(override val vertices: Set<LGVertex> = setOf()):
  Graph<LabeledGraph, LabeledEdge, LGVertex>(vertices) {
  constructor(vararg vertices: LGVertex): this(vertices.toSet())

  /**
   * TODO: Any way to move this into [Graph]?
   * Constructors cannot be inherited, but invoke() can.
   * May be possible to define a generic "constructor".
   */

  companion object {
    operator fun invoke(builder: LGBuilder.() -> Unit) =
      LGBuilder().also { it.builder() }.mutGraph

    operator fun <T> invoke(
      vararg adjList: Pair<T, T>,
      toVertex: T.() -> LGVertex = { LGVertex(toString()) }
    ): LabeledGraph =
      adjList.fold(adjList.first().first.toVertex().graph) { acc, (s, t) ->
        acc + LabeledGraph { s.toVertex() - t.toVertex() }
      }

    operator fun invoke(graph: String) =
      this {
        graph.split(" ").forEach {
          it.substring(1).fold(LGVertex(it[0].toString())) { acc, c ->
            acc - c.toString()
          }
        }
      }.reversed()
  }

  override fun new(vertices: Set<LGVertex>) = LabeledGraph(vertices)
  var accumuator = mutableSetOf<String>()
  var description = ""

  fun S() = BMat(vertices.size, 1) { i, j -> this[i].occupied }

  fun rewrite(substitution: Pair<String, String>) =
    randomWalk().take(200).toList().joinToString("")
      .replace(substitution.first, substitution.second)
      .let { LabeledGraph(it) }

  fun propagate() {
    val (previousStates, unoccupied) = vertices.partition { it.occupied }
    val nextStates = unoccupied.intersect(previousStates.flatMap { it.neighbors }.toSet())
    previousStates.forEach { it.occupied = false }
    nextStates.forEach { it.occupied = true; accumuator.add(it.id) }
  }
}

// TODO: Move occupancy, propagation and accumulator/description here
class StatefulGraph: LabeledGraph()

class LGVertex constructor(
  val label: String = "",
  var occupied: Boolean = false,
  override val edgeMap: (LGVertex) -> Set<LabeledEdge>,
) : Vertex<LabeledGraph, LabeledEdge, LGVertex>(label) {
  constructor(out: Set<LGVertex> = setOf()) :
    this(randomString(), edgeMap = { s ->  out.map { t -> LabeledEdge(s, t) }.toSet() })
  constructor(label: String, out: Set<LGVertex> = emptySet()) :
    this(label = label, edgeMap = { s -> out.map { t -> LabeledEdge(s, t) }.toSet() })

  override fun encode() = label.vectorize()
  override fun render() = super.render().also { if (occupied) it.add(FILLED, RED.fill()) else it.add(BLACK) }
//  override fun toString(): String = label
  override fun Graph(vertices: Set<LGVertex>) = LabeledGraph(vertices)
  override fun Edge(s: LGVertex, t: LGVertex) = LabeledEdge(s, t)
  override fun Vertex(newId: String, edgeMap: (LGVertex) -> Set<LabeledEdge>) = LGVertex(newId, occupied, edgeMap)
}

open class LabeledEdge(override val source: LGVertex, override val target: LGVertex, val label: String? = null) :
  Edge<LabeledGraph, LabeledEdge, LGVertex>(source, target) {
  override fun render() = super.render().also { it.add(if (source.occupied) RED else BLACK) }
  override fun new(source: LGVertex, target: LGVertex) = LabeledEdge(source, target, label)
}