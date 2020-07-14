package edu.mcgill.kaliningraph

import org.apache.commons.math3.distribution.EnumeratedDistribution
import org.apache.commons.math3.util.Pair
import org.ejml.data.DMatrixSparseCSC
import org.ejml.data.DMatrixSparseTriplet
import org.ejml.kotlin.minus
import org.ejml.ops.ConvertDMatrixStruct

open class Graph(open val V: Set<Vertex> = emptySet()) : Set<Vertex> by V {
  constructor(builder: GraphBuilder.() -> Unit) :
    this(GraphBuilder().also { it.builder() }.graph.reversed())

  constructor(vararg graphs: Graph) :
    this(graphs.fold(Graph()) { it, acc -> it + acc }.V)

  constructor(vararg vertices: Vertex) : this(vertices.map { it.asGraph() })
  constructor(graphs: List<Graph>) : this(*graphs.toTypedArray())
  constructor(adjList: Map<Vertex, List<Edge>>) :
    this(adjList.map { (k, v) -> Vertex(k.id) { v } }.toSet())

  val totalEdges = V.map { it.neighbors.size }.sum()
  private val index = VIndex(V)

  private class VIndex(set: Set<Vertex>) {
    val array: Array<Vertex> = set.toTypedArray()
    val map: Map<Vertex, Int> = array.mapIndexed { index, a -> a to index }.toMap()
    operator fun get(it: Vertex) = map[it]
    operator fun get(it: Int) = array[it]
  }

  operator fun get(vertex: Vertex) = index[vertex]
  operator fun get(vertexIdx: Int) = index[vertexIdx]

  // Degree matrix
  val D by lazy {
    ConvertDMatrixStruct.convert(
      DMatrixSparseTriplet(V.size, V.size, totalEdges).also { degMat ->
        V.forEach { v -> degMat[v, v] = v.neighbors.size.toDouble() }
      }, null as DMatrixSparseCSC?
    )
  }

  // Adjacency matrix
  val A by lazy {
    ConvertDMatrixStruct.convert(
      DMatrixSparseTriplet(V.size, V.size, totalEdges).also { adjMat ->
        V.forEach { v -> v.neighbors.forEach { n -> adjMat[v, n] = 1.0 } }
      }, null as DMatrixSparseCSC?
    )
  }

  // Laplacian matrix
  val L by lazy { D - A }

  val edgList by lazy { V.flatMap { s -> s.edges.map { s to it } }.asSequence() }
  val adjList by lazy { V.flatMap { s -> s.neighbors.map { t -> Pair(s, t) } } }

  val degMap by lazy { V.map { it to it.neighbors.size }.toMap() }
  operator fun DMatrixSparseTriplet.get(n0: Vertex, n1: Vertex) = this[index[n0]!!, index[n1]!!]
  operator fun DMatrixSparseTriplet.set(n0: Vertex, n1: Vertex, value: Double) {
    this[index[n0]!!, index[n1]!!] = value
  }

  // Implements graph merge. For all vertices in common, merge their neighbors.
  operator fun plus(that: Graph) =
    Graph((this - that) + (this join that) + (that - this))

  infix fun join(that: Graph) =
    (V intersect that.V).toSortedSet(compareBy { it.id })
      .zip((that.V intersect V).toSortedSet(compareBy { it.id }))
      .map { (left, right) -> Vertex(left.id) { left.edges + right.edges } }

  operator fun minus(graph: Graph) = Graph(V - graph.V)

  fun reversed(): Graph =
    Graph(V.map { it to listOf<Edge>() }.toMap() +
      V.flatMap { src -> src.edges.map { edge -> edge.target to Edge(src, edge.label) } }
        .groupBy({ it.first }, { it.second }).mapValues { (_, v) -> v })

  val histogram by lazy { poolingBy { size } }

  /*
   * Weisfeiler-Lehman isomorphism test:
   * http://www.jmlr.org/papers/volume12/shervashidze11a/shervashidze11a.pdf#page=6
   * https://davidbieber.com/post/2019-05-10-weisfeiler-lehman-isomorphism-test/
   */

  tailrec fun wl(k: Int = 5, labels: Map<Vertex, Int> = histogram): Map<Vertex, Int> =
    if (k <= 0) labels
    else wl(k - 1, poolingBy { map { labels[it]!! }.sorted().hashCode() })

  fun isomorphicTo(that: Graph) =
    V.size == that.V.size && totalEdges == that.totalEdges && hashCode() == that.hashCode()

  override fun equals(other: Any?) =
    super.equals(other) || (other as? Graph)?.isomorphicTo(this) ?: false

  override fun hashCode() = wl().values.sorted().hashCode()

  fun poolingBy(stat: Set<Vertex>.() -> Int): Map<Vertex, Int> =
    V.map { it to stat(it.neighbors()) }.toMap()

  fun attachRandomVertex(degree: Int) =
    this + Vertex(
      V.size.toString(),
      if (V.isEmpty()) emptySet() else EnumeratedDistribution(
        degMap.map { (k, v) -> Pair(k, (v + 1.0) / (totalEdges + 1.0)) })
        .run { (0..degree.coerceAtMost(V.size)).map { sample() } }.toSet()
    ).asGraph()

  // https://en.wikipedia.org/wiki/Barab%C3%A1si%E2%80%93Albert_model#Algorithm
  tailrec fun prefAttach(graph: Graph = this, vertices: Int = 1, degree: Int = 3): Graph =
    if (vertices <= 0) graph
    else prefAttach(graph.attachRandomVertex(degree), vertices - 1, degree)

  override fun toString() =
    "(" + V.joinToString(", ", "{", "}") + ", " +
      edgList.map { (v, e) -> "${v.id}→${e.target.id}" }.joinToString(", ", "{", "}") + ")"
}