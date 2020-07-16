package edu.mcgill.kaliningraph

import org.apache.commons.math3.distribution.EnumeratedDistribution
import org.apache.commons.math3.util.Pair
import org.ejml.data.DMatrixSparseCSC
import org.ejml.data.DMatrixSparseTriplet
import org.ejml.kotlin.minus
import org.ejml.ops.ConvertDMatrixStruct

open class Graph<T: Node<T>>(open val V: Set<T> = emptySet()) : Set<T> by V {
  constructor(vararg graphs: Graph<T>) :
    this(graphs.fold(Graph<T>()) { it, acc -> it + acc }.V)

  constructor(vararg vertices: T) : this(vertices.map { it.asGraph() })
  constructor(graphs: List<Graph<T>>) : this(*graphs.toTypedArray())
  constructor(adjList: Map<T, List<Edge<T>>>) :
    this(adjList.map { (k, v) -> Node<T>(k.id) { v } }.toSet() as Set<T>)
  
  val prototype: Node<T> by lazy { V.firstOrNull() ?: Node<T>() }

  val totalEdges = V.map { it.neighbors.size }.sum()
  private val index = VIndex<T>(V)

  private class VIndex<T: Node<T>>(set: Set<Node<T>>) {
    val array: Array<Node<T>> = set.toTypedArray()
    val map: Map<Node<T>, Int> = array.mapIndexed { index, a -> a to index }.toMap()
    operator fun get(it: Node<T>) = map[it]
    operator fun get(it: Int) = array[it]
  }

  operator fun get(vertex: T) = index[vertex]
  operator fun get(vertexIdx: Int) = index[vertexIdx]

  // Degree matrix
  val D by lazy {
    ConvertDMatrixStruct.convert(
      DMatrixSparseTriplet(V.size, V.size, totalEdges).also { degMat ->
        V.forEach { v -> degMat[v, v as T] = v.neighbors.size.toDouble() }
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
  operator fun DMatrixSparseTriplet.get(n0: T, n1: T) = this[index[n0]!!, index[n1]!!]
  operator fun DMatrixSparseTriplet.set(n0: T, n1: T, value: Double) {
    this[index[n0]!!, index[n1]!!] = value
  }

  // Implements graph merge. For all vertices in common, merge their neighbors.
  operator fun plus(that: Graph<T>) =
    Graph((this - that) as Set<T> + (this join that) + (that - this))

  infix fun join(that: Graph<T>): Set<T> =
    (V intersect that.V).toSortedSet(compareBy { it.id })
      .zip((that.V intersect V).toSortedSet(compareBy { it.id }))
      .map { (left, right) -> prototype.new(left.id) { left.edges + right.edges } as T}.toSet()

  operator fun minus(graph: Graph<T>): Graph<T> = Graph(V - graph.V)

  fun reversed(): Graph<T> =
    Graph(V.map { it to listOf<Edge<T>>() }.toMap() +
      V.flatMap { src -> src.edges.map { edge -> edge.target to Edge(src, edge.label) } as List<Pair<T, Edge<T>>> }
        .groupBy({ it.first }, { it.second }).mapValues { (_, v) -> v })

  val histogram by lazy { poolingBy { size } }

  /*
   * Weisfeiler-Lehman isomorphism test:
   * http://www.jmlr.org/papers/volume12/shervashidze11a/shervashidze11a.pdf#page=6
   * https://davidbieber.com/post/2019-05-10-weisfeiler-lehman-isomorphism-test/
   */

  tailrec fun wl(k: Int = 5, labels: Map<T, Int> = histogram): Map<T, Int> =
    if (k <= 0) labels
    else wl(k - 1, poolingBy { map { labels[it]!! }.sorted().hashCode() })

  fun isomorphicTo(that: Graph<T>) =
    V.size == that.V.size && totalEdges == that.totalEdges && hashCode() == that.hashCode()

  override fun equals(other: Any?) =
    super.equals(other) || (other as? Graph<T>)?.isomorphicTo(this) ?: false

  override fun hashCode() = wl().values.sorted().hashCode()

  fun poolingBy(stat: Set<T>.() -> Int): Map<T, Int> =
    V.map { it to stat(it.neighbors()) }.toMap()

  fun attachRandomT(degree: Int) =
    this + prototype.new(
      V.size.toString(),
      if (V.isEmpty()) emptySet() else EnumeratedDistribution(
        degMap.map { (k, v) -> Pair(k, (v + 1.0) / (totalEdges + 1.0)) })
        .run { (0..degree.coerceAtMost(V.size)).map { sample() } }.toSet()
    ).asGraph()

  // https://en.wikipedia.org/wiki/Barab%C3%A1si%E2%80%93Albert_model#Algorithm
  tailrec fun prefAttach(graph: Graph<T> = this, vertices: Int = 1, degree: Int = 3): Graph<T> =
    if (vertices <= 0) graph
    else prefAttach(graph.attachRandomT(degree), vertices - 1, degree)

  override fun toString() =
    "(" + V.joinToString(", ", "{", "}") + ", " +
      edgList.map { (v, e) -> "${v.id}→${e.target.id}" }.joinToString(", ", "{", "}") + ")"
}