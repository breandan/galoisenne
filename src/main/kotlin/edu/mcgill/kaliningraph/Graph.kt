package edu.mcgill.kaliningraph

import guru.nidi.graphviz.*
import guru.nidi.graphviz.minus
import guru.nidi.graphviz.attribute.*
import guru.nidi.graphviz.engine.Engine
import guru.nidi.graphviz.engine.Format
import guru.nidi.graphviz.engine.Renderer
import guru.nidi.graphviz.model.Factory.mutNode
import org.apache.commons.math3.distribution.EnumeratedDistribution
import org.apache.commons.math3.util.Pair
import org.ejml.data.DMatrixRMaj
import org.ejml.kotlin.minus
import java.io.File
import java.util.*
import kotlin.collections.minus

fun randomString() = UUID.randomUUID().toString().take(5)

class Vertex(val id: String = randomString(), edgeMap: (Vertex) -> Collection<Edge>) {
  constructor(id: String = randomString(), out: Set<Vertex> = emptySet()) :
    this(id, { vertex -> out.map { Edge(it) } })

  val edges = edgeMap(this).toSet()
  val neighbors: Set<Vertex> = edges.map { it.target }.toSet()

  tailrec fun neighbors(k: Int = 0, vertices: Set<Vertex> = neighbors + this): Set<Vertex> =
    if (k == 0 || vertices.neighbors() == vertices) vertices
    else neighbors(k - 1, vertices + vertices.neighbors() + this)

  // Removes all edges pointing outside the set
  private fun Set<Vertex>.closure() = map { vertex ->
    Vertex(vertex.id) { vertex.edges.filter { it.target in this@closure } }
  }.toSet()

  private fun Set<Vertex>.neighbors() = flatMap { it.neighbors() }.toSet()

  fun asGraph() = Graph(neighbors(-1))
  fun egoGraph() = Graph(neighbors(0).closure())

  operator fun minus(vertex: Vertex) = Vertex(vertex.id) { vertex.edges + Edge(this) }
  operator fun plus(edge: Edge) = Vertex(id) { edges + edge }
  operator fun plus(vertex: Vertex) = asGraph() + vertex.asGraph()

  override fun equals(other: Any?) = (other as? Vertex)?.id == id
  override fun hashCode() = id.hashCode()
  override fun toString() = id
}

data class Edge(val target: Vertex, val label: String = "") {
  override fun equals(other: Any?) = (other as? Edge)?.target == target
  override fun hashCode() = target.hashCode() + label.hashCode()
  override fun toString() = target.id
}

class Graph(val V: Set<Vertex> = emptySet()) : Set<Vertex> by V {
  constructor(vararg graphs: Graph) : this(graphs.fold(Graph()) { it, acc -> it + acc }.V)
  constructor(vararg vertices: Vertex) : this(vertices.map { it.asGraph() })
  constructor(graphs: List<Graph>) : this(*graphs.toTypedArray())
  constructor(adjList: Map<Vertex, List<Edge>>) :
    this(adjList.map { (k, v) -> Vertex(k.id) { v } }.toSet())

  val totalEdges = V.map { it.neighbors.size }.sum()
  val index = VIndex(V)

  class VIndex(val set: Set<Vertex>) {
    val array: Array<Vertex> = set.toTypedArray()
    val map: Map<Vertex, Int> = array.mapIndexed { index, a -> a to index }.toMap()
    operator fun get(it: Vertex) = map[it]
    operator fun get(it: Int) = array[it]
  }

  operator fun get(vertex: Vertex) = index[vertex]
  operator fun get(vertexIdx: Int) = index[vertexIdx]

  // Degree matrix
  val D by lazy {
    DMatrixRMaj(V.size, V.size).also { degMat ->
      V.forEach { v -> degMat[v, v] = v.neighbors.size.toDouble() }
    }
  }

  // Adjacency matrix
  val A: DMatrixRMaj by lazy {
    DMatrixRMaj(V.size, V.size).also { adjMat ->
      V.forEach { v -> v.neighbors.forEach { n -> adjMat[v, n] = 1.0 } }
    }
  }

  val laplacian by lazy { D - A }
  val degMap by lazy { V.map { it to it.neighbors.size }.toMap() }
  operator fun DMatrixRMaj.get(n0: Vertex, n1: Vertex) = this[index[n0]!!, index[n1]!!]
  operator fun DMatrixRMaj.set(n0: Vertex, n1: Vertex, value: Double) {
    this[index[n0]!!, index[n1]!!] = value
  }

  // Implements graph merge. For all vertices in common, merge their neighbors.
  operator fun plus(that: Graph) = Graph((this - that) + (this join that) + (that - this))

  infix fun join(that: Graph) =
    (V intersect that.V).toSortedSet(compareBy { it.id })
      .zip((that.V intersect V).toSortedSet(compareBy { it.id }))
      .map { (left, right) -> Vertex(left.id) { left.edges + right.edges } }

  operator fun minus(graph: Graph) = Graph(V - graph.V)

  fun reversed(): Graph =
    Graph(V.map { it to listOf<Edge>() }.toMap() +
      V.flatMap { src -> src.edges.map { edge -> edge.target to Edge(src, edge.label) } }
        .groupBy({ it.first }, { it.second }).mapValues { it.value })

  val histogram by lazy { poolingBy { size } }

  /*
   * Weisfeiler-Lehman isomorphism test:
   * http://www.jmlr.org/papers/volume12/shervashidze11a/shervashidze11a.pdf#page=6
   */

  tailrec fun wl(k: Int = 5, labels: Map<Vertex, Int> = histogram): Map<Vertex, Int> =
    if (k <= 0) labels
    else wl(k - 1, poolingBy { map { labels[it]!! }.sorted().hashCode() })

  fun isomorphicTo(that: Graph) =
    V.size == that.V.size && totalEdges == that.totalEdges && hashCode() == that.hashCode()

  override fun equals(other: Any?) =
    super.equals(other) || (other as? Graph)?.isomorphicTo(this) ?: false

  override fun hashCode() = wl().values.sorted().hashCode()

  fun <R> poolingBy(op: Set<Vertex>.() -> R): Map<Vertex, R> =
    V.map { it to op(it.neighbors()) }.toMap()
}

object GraphBuilder {
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

  class ProtoEdge(val source: Vertex, val label: String)

  // Arithmetic is right-associative, so we construct in reverse and flip after
  operator fun Vertex.minus(symbols: String) = ProtoEdge(this, symbols)
  operator fun ProtoEdge.minus(target: Vertex) = target + Edge(source, label)

  fun Graph.attach(neighbors: Int) =
    this + Vertex(randomString(), EnumeratedDistribution(
      degMap.map { (k, v) -> Pair(k, (v + 1.0) / (totalEdges + 1.0)) })
      .run { (0..neighbors).map { sample() }.toSet() }
    ).asGraph()

  // https://en.wikipedia.org/wiki/Barab%C3%A1si%E2%80%93Albert_model#Algorithm
  tailrec fun prefAttach(graph: Graph, vertices: Int = 0, neighbors: Int = 3): Graph =
    if (vertices <= 0) graph else prefAttach(graph.attach(neighbors), vertices - 1)
}

// Flips edge direction to correct for right associativity of minus operator
fun buildGraph(builder: GraphBuilder.() -> Graph) = builder(GraphBuilder).reversed()

val DARKMODE = false
val THICKNESS = 2

inline fun render(format: Format = Format.SVG, crossinline op: () -> Unit) =
  graph(directed = true) {
    val color = Color.BLACK

    edge[color, Arrow.NORMAL, Style.lineWidth(THICKNESS)]

//    graph[Rank.dir(Rank.RankDir.LEFT_TO_RIGHT), Color.TRANSPARENT.background()]

    node[color, color.font(), Font.config("Helvetica", 20), Style.lineWidth(THICKNESS)]

    op()
  }.toGraphviz().apply { engine(Engine.CIRCO) }.render(format)

fun Renderer.show() = toFile(File.createTempFile("temp", ".svg")).show()
fun Graph.render() = render {
  V.forEach { vertex ->
    vertex.edges.forEach { edge ->
      (mutNode(vertex.id) - mutNode(edge.target.id)).add(Label.of(edge.label))
    }
  }
}

fun Graph.html() = render().toString()

fun Graph.show() = render().show()

fun File.show() = ProcessBuilder("x-www-browser", path).start()