package edu.mcgill.kaliningraph

import kweb.shoebox.toArrayList
import org.apache.commons.rng.sampling.DiscreteProbabilityCollectionSampler
import org.apache.commons.rng.simple.RandomSource
import org.apache.commons.rng.simple.RandomSource.JDK
import org.ejml.data.DMatrixSparseTriplet
import org.ejml.kotlin.minus
import kotlin.reflect.KProperty

// TODO: Extend type family interface
open class Graph<T : Node<T, E>, E : Edge<E, T>>(open val V: Set<T> = emptySet()) : Set<T> by V {
  constructor(vararg graphs: Graph<T, E>) :
    this(graphs.fold(Graph<T, E>()) { it, acc -> it + acc }.V)

  constructor(vararg vertices: T) : this(vertices.map { it.asGraph() })
  constructor(graphs: List<Graph<T, E>>) : this(*graphs.toTypedArray())
  constructor(adjList: Map<T, List<E>>) : this(adjList.map { (k, v) -> k.new { v } }.toSet())

  open val prototype: Node<T, E>? by lazy { V.firstOrNull() }

  val totalEdges by lazy { V.map { it.neighbors.size }.sum() }
  private val index by lazy { VIndex(V) }

  private class VIndex<T : Node<T, E>, E : Edge<E, T>>(set: Set<T>) {
    val array: ArrayList<T> = set.toList().toArrayList()
    val map: Map<T, Int> = array.mapIndexed { index, a -> a to index }.toMap()
    operator fun get(it: Node<T, E>) = map[it]
    operator fun get(it: Int) = array[it]
  }

  operator fun get(vertex: T) = index[vertex]
  operator fun get(vertexIdx: Int) = index[vertexIdx]

  val edgList by lazy { V.flatMap { s -> s.edges.map { s to it } }.asSequence() }
  val adjList by lazy { edgList.map { (v, e) -> v to e.target } }

  // Degree matrix
  val D by lazy {
    DMatrixSparseTriplet(V.size, V.size, totalEdges).also { degMat ->
      V.forEach { v -> degMat[v, v] = v.neighbors.size.toDouble() }
    }.toCSC()
  }

  // Adjacency matrix
  val A by lazy {
    DMatrixSparseTriplet(V.size, V.size, totalEdges).also { adjMat ->
      V.forEach { v -> v.neighbors.forEach { n -> adjMat[v, n] = 1.0 } }
    }.toCSC()
  }

  // Laplacian matrix
  val L by lazy { D - A }

  fun S() = DMatrixSparseTriplet(V.size, 1, totalEdges).also { state ->
    V.forEach { v -> state[index[v]!!, 0] = if (v.occupied) 1.0 else 0.0 }
  }.toCSC()

  val degMap by lazy { V.map { it to it.neighbors.size }.toMap() }
  operator fun DMatrixSparseTriplet.get(n0: T, n1: T) = this[index[n0]!!, index[n1]!!]
  operator fun DMatrixSparseTriplet.set(n0: T, n1: T, value: Double) {
    this[index[n0]!!, index[n1]!!] = value
  }

  // Implements graph merge. For all vertices in common, merge their neighbors.
  operator fun plus(that: Graph<T, E>): Graph<T, E> =
    Graph((this - that) as Set<T> + (this join that) + (that - this))

  infix fun join(that: Graph<T, E>): Set<T> =
    (V intersect that.V).sortedBy { it.id }.toSet()
      .zip((that.V intersect V).sortedBy { it.id }.toSet())
      .map { (left, right) -> left.new { left.edges + right.edges } }.toSet()

  operator fun minus(graph: Graph<T, E>): Graph<T, E> = Graph(V - graph.V)

  fun reversed(): Graph<T, E> =
    Graph(V.map { it to listOf<E>() }.toMap() +
      V.flatMap { src -> src.edges.map { edge -> edge.target to edge.newTarget(src) } }
        .groupBy({ it.first }, { it.second }).mapValues { (_, v) -> v })

  val histogram by lazy { poolingBy { size } }

  /*
   * Weisfeiler-Lehman isomorphism test:
   * http://www.jmlr.org/papers/volume12/shervashidze11a/shervashidze11a.pdf#page=6
   * https://davidbieber.com/post/2019-05-10-weisfeiler-lehman-isomorphism-test/
   */

  tailrec fun wl(k: Int = 5, labels: Map<T, Int> = histogram): Map<T, Int> {
    val next = poolingBy { map { labels[it]!! }.sorted().hashCode() }
    return if (k <= 0 || labels == next) labels else wl(k - 1, next)
  }

  fun isomorphicTo(that: Graph<T, E>) =
    V.size == that.V.size && totalEdges == that.totalEdges && hashCode() == that.hashCode()

  override fun equals(other: Any?) =
    super.equals(other) || (other as? Graph<T, E>)?.isomorphicTo(this) ?: false

  override fun hashCode() = wl().values.sorted().hashCode()

  fun poolingBy(stat: Set<T>.() -> Int): Map<T, Int> =
    V.map { it to stat(it.neighbors()) }.toMap()

  fun attachRandomT(degree: Int): Graph<T, E> =
      this + (prototype?.new(
        newId = V.size.toString(),
        out = if (V.isEmpty()) emptySet()
        else DiscreteProbabilityCollectionSampler(RandomSource.create(JDK),
            degMap.map { (k, v) -> k to (v + 1.0) / (totalEdges + 1.0) }.toMap())
         .run { generateSequence { sample() }.take(degree.coerceAtMost(V.size)) }.toSet()
      )?.asGraph() ?: Graph())

  var done = mutableSetOf<String>()
  var string = ""
  fun propagate() {
    val (previousStates, unoccupied) = V.partition { it.occupied }
    val nextStates = unoccupied.intersect(previousStates.flatMap { it.neighbors }.toSet())
    previousStates.forEach { it.occupied = false }
    nextStates.forEach { it.occupied = true; done.add(it.id) }
    string = "y = " + done.joinToString(" + ")
  }

  // https://en.wikipedia.org/wiki/Barab%C3%A1si%E2%80%93Albert_model#Algorithm
  tailrec fun prefAttach(graph: Graph<T, E> = this, vertices: Int = 1, degree: Int = 3): Graph<T, E> =
    if (vertices <= 0) graph
    else prefAttach(graph.attachRandomT(degree), vertices - 1, degree)

  override fun toString() =
    "(" + V.joinToString(", ", "{", "}") + ", " +
      edgList.map { (v, e) -> "${v.id}→${e.target.id}" }.joinToString(", ", "{", "}") + ")"
}

abstract class Edge<E: Edge<E, T>, T: Node<T, E>>(open val target: T) {
  abstract fun newTarget(target: T): E
}

// TODO: Link to graph and make a "view" of the container graph
abstract class Node<T : Node<T, E>, E : Edge<E, T>>(val id: String) {
  abstract fun new(newId: String = id, out: Set<T> = emptySet()): T
  abstract fun new(newId: String = id, edgeMap: (T) -> Collection<E>): T

  abstract val edgeMap: (T) -> Collection<E>
  open val edges by lazy { edgeMap(this as T).toSet() }
  open val neighbors by lazy { edges.map { it.target }.toSet() }
  open var occupied = false

  tailrec fun neighbors(k: Int = 0, vertices: Set<T> = neighbors + this as T): Set<T> =
    if (k == 0 || vertices.neighbors() == vertices) vertices
    else neighbors(k - 1, vertices + vertices.neighbors() + this as T)

  // Removes all edges pointing outside the set
  private fun Set<T>.closure(): Set<T> =
    map { vertex -> vertex.new { vertex.edges.filter { it.target in this } } }.toSet()

  private fun Set<T>.neighbors(): Set<T> = flatMap { it.neighbors() }.toSet()

  fun asGraph() = Graph(neighbors(-1))
  fun neighborhood() = Graph(neighbors(0).closure())

  open operator fun getValue(a: Any?, prop: KProperty<*>): T = new(prop.name)
}