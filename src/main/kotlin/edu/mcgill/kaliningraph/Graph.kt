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

class Node(val id: String = randomString(), edgeMap: (Node) -> Collection<Edge>) {
  constructor(id: String = randomString(), out: Set<Node> = emptySet()) :
    this(id, { node -> out.map { Edge(it) } })

  val edges = edgeMap(this).toSet()
  fun Set<Node>.neighbors() = flatMap { it.neighbors() }.toSet()

  val neighbors: Set<Node> = edges.map { it.target }.toSet()

  tailrec fun neighbors(k: Int = 0, nodes: Set<Node> = neighbors + this): Set<Node> =
    if (k == 0 || nodes.neighbors() == nodes) nodes
    else neighbors(k - 1, nodes + nodes.neighbors() + this)

  fun asGraph() = Graph(neighbors(-1))

  fun egoGraph() = Graph(neighbors(0).closure())

  private fun Set<Node>.closure() = map { node ->
    Node(node.id) { node.edges.filter { it.target in this@closure } }
  }.toSet()

  operator fun minus(node: Node) = Node(node.id) { node.edges + Edge(this) }
  operator fun plus(edge: Edge) = Node(id) { edges + edge }
  operator fun plus(node: Node) = asGraph() + node.asGraph()

  override fun equals(other: Any?) = (other as? Node)?.id == id
  override fun hashCode() = id.hashCode()
  override fun toString() = id
}

data class Edge(val target: Node, val label: String = "") {
  override fun equals(other: Any?) = (other as? Edge)?.target == target
  override fun hashCode() = target.hashCode() + label.hashCode()
  override fun toString() = target.id
}

class Graph(val V: Set<Node> = emptySet()) : Set<Node> by V {
  constructor(vararg graphs: Graph) : this(graphs.fold(Graph()) { it, acc -> it + acc }.V)
  constructor(vararg nodes: Node) : this(nodes.map { it.asGraph() })
  constructor(graphs: List<Graph>) : this(*graphs.toTypedArray())
  constructor(adjList: Map<Node, List<Edge>>) :
    this(adjList.map { (k, v) -> Node(k.id) { v } }.toSet())

  val nodesById = V.map { it.id to it }.toMap()
  val numEdges = V.map { it.neighbors.size }.sum()
  val index = NodeIndex(V)

  class NodeIndex(val set: Set<Node>) {
    val array: Array<Node> = set.toTypedArray()
    val map: Map<Node, Int> = array.mapIndexed { index, a -> a to index }.toMap()
    operator fun get(it: Node) = map[it]
    operator fun get(it: Int) = array[it]
  }

  operator fun get(node: Node) = nodesById[node.id]
  operator fun get(nodeId: String) = nodesById[nodeId]
  operator fun get(nodeIdx: Int) = index[nodeIdx]

  // Degree matrix
  val D by lazy {
    DMatrixRMaj(V.size, V.size).apply {
      V.forEachIndexed { i, node -> set(i, i, node.neighbors.size.toDouble()) }
    }
  }

  // Adjacency matrix
  val A by lazy {
    DMatrixRMaj(V.size, V.size).apply {
      V.forEach { node ->
        node.neighbors.forEach { neighbor ->
          set(index[node]!!, index[neighbor]!!, 1.0)
        }
      }
    }
  }

  val degMap by lazy { V.map { it to it.neighbors.size }.toMap() }
  val laplacian by lazy { D - A }

  // Implements graph merge. For all nodes in common, merge their neighbors.
  operator fun plus(that: Graph) = Graph((this - that) + (this join that) + (that - this))

  infix fun join(that: Graph) =
    (V intersect that.V).toSortedSet(compareBy { it.id })
      .zip((that.V intersect V).toSortedSet(compareBy { it.id }))
      .map { (left, right) -> Node(left.id) { left.edges + right.edges } }

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

  tailrec fun computeWL(k: Int = 5, labels: Map<Node, Int> = histogram): Map<Node, Int> =
    if (k <= 0) labels
    else computeWL(k - 1, poolingBy { map { labels[it]!! }.sorted().hashCode() })

  fun isomorphicTo(that: Graph) =
    V.size == that.V.size && numEdges == that.numEdges && hashCode() == that.hashCode()

  override fun equals(other: Any?) =
    super.equals(other) || (other as? Graph)?.isomorphicTo(this) ?: false

  override fun hashCode() = computeWL().values.sorted().hashCode()

  fun <R> poolingBy(op: Set<Node>.() -> R): Map<Node, R> =
    V.map { it to op(it.neighbors()) }.toMap()
}

object GraphBuilder {
  val a = Node("a")
  val b = Node("b")
  val c = Node("c")
  val d = Node("d")
  val e = Node("e")
  val f = Node("f")
  val g = Node("g")
  val h = Node("h")
  val i = Node("i")
  val j = Node("j")
  val k = Node("k")
  val l = Node("l")

  class ProtoEdge(val source: Node, val label: String)

  // Arithmetic is right-associative, so we construct in reverse and flip after
  operator fun Node.minus(symbols: String) = ProtoEdge(this, symbols)
  operator fun ProtoEdge.minus(target: Node) = target + Edge(source, label)

  fun Graph.attachNode(neighbors: Int) =
    this + Node(randomString(), EnumeratedDistribution(
      degMap.map { (k, v) -> Pair(k, (v + 1.0) / (numEdges + 1.0)) })
      .run { (0..neighbors).map { sample() }.toSet() }
    ).asGraph()

  // https://en.wikipedia.org/wiki/Barab%C3%A1si%E2%80%93Albert_model#Algorithm
  tailrec fun prefAttach(graph: Graph, nodes: Int = 0, neighbors: Int = 3): Graph =
    if (nodes <= 0) graph else prefAttach(graph.attachNode(neighbors), nodes - 1)
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
  V.forEach { node ->
    node.edges.forEach { edge ->
      (mutNode(node.id) - mutNode(edge.target.id)).add(Label.of(edge.label))
    }
  }
}

fun Graph.html() = render().toString()

fun Graph.show() = render().show()

fun File.show() = ProcessBuilder("x-www-browser", path).start()