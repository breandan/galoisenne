package edu.mcgill.kaliningraph

import edu.mcgill.kaliningraph.typefamily.IEdge
import edu.mcgill.kaliningraph.typefamily.IGraph
import edu.mcgill.kaliningraph.typefamily.IVertex
import kweb.shoebox.toArrayList
import org.apache.commons.rng.sampling.DiscreteProbabilityCollectionSampler
import org.apache.commons.rng.simple.RandomSource
import org.apache.commons.rng.simple.RandomSource.JDK
import org.ejml.data.DMatrixSparseCSC
import org.ejml.data.DMatrixSparseTriplet
import org.ejml.kotlin.minus
import kotlin.reflect.KProperty

abstract class Graph<G : Graph<G, E, V>, E : Edge<G, E, V>, V : Vertex<G, E, V>>
constructor(override val vertices: Set<V> = setOf()) : Set<V> by vertices, IGraph<G, E, V> {
  open fun new(vararg graphs: G): G = new(graphs.toList())
  open fun new(vararg vertices: V): G = new(vertices.map { it.graph })
  open fun new(graphs: List<G>): G = new(graphs.fold(new()) { it, acc -> it + acc }.vertices)
  open fun new(adjList: Map<V, List<E>>): G = new(adjList.map { (k, v) -> k.new { v } }.toSet())
  abstract fun new(vertices: Set<V> = setOf()): G

  override val graph: G = this as G
  open val prototype: V? by lazy { vertices.firstOrNull() }

  val totalEdges: Int by lazy { vertices.map { it.neighbors.size }.sum() }
  private val index: VIndex<G, E, V> by lazy { VIndex(vertices) }

  private class VIndex<G: Graph<G, E, V>, E : Edge<G, E, V>, V : Vertex<G, E, V>>(set: Set<V>) {
    val array: ArrayList<V> = set.toList().toArrayList()
    val map: Map<V, Int> = array.mapIndexed { index, a -> a to index }.toMap()
    operator fun get(it: Vertex<G, E, V>) = map[it]
    operator fun get(it: Int) = array[it]
  }

  operator fun get(vertex: V): Int? = index[vertex]
  operator fun get(vertexIdx: Int): V = index[vertexIdx]

  val edgList: Sequence<Pair<V, E>> by lazy { vertices.flatMap { s -> s.outgoing.map { s to it } }.asSequence() }
  val adjList: Sequence<Pair<V, V>> by lazy { edgList.map { (v, e) -> v to e.target } }
  val edgMap: Map<V, Set<E>> by lazy { vertices.map { it to it.outgoing }.toMap() }

  // Degree matrix
  val D: DMatrixSparseCSC by lazy {
    DMatrixSparseTriplet(vertices.size, vertices.size, totalEdges).also { degMat ->
      vertices.forEach { v -> degMat[v, v] = v.neighbors.size.toDouble() }
    }.toCSC()
  }

  // Adjacency matrix
  val A: DMatrixSparseCSC by lazy {
    DMatrixSparseTriplet(vertices.size, vertices.size, totalEdges).also { adjMat ->
      vertices.forEach { v -> v.neighbors.forEach { n -> adjMat[v, n] = 1.0 } }
    }.toCSC()
  }

  // Laplacian matrix
  val L by lazy { D - A }

  fun S() = DMatrixSparseTriplet(vertices.size, 1, totalEdges).also { state ->
    vertices.forEach { v -> state[index[v]!!, 0] = if (v.occupied) 1.0 else 0.0 }
  }.toCSC()

  val degMap by lazy { vertices.map { it to it.neighbors.size }.toMap() }
  operator fun DMatrixSparseTriplet.get(n0: V, n1: V) = this[index[n0]!!, index[n1]!!]
  operator fun DMatrixSparseTriplet.set(n0: V, n1: V, value: Double) {
    this[index[n0]!!, index[n1]!!] = value
  }

  // Implements graph merge. For all vertices in common, merge their neighbors.
  open operator fun plus(that: G): G =
    new((this - that) + (this join that) + (that - this))

  infix fun join(that: G): Set<V> =
    (vertices intersect that.vertices).sortedBy { it.id }.toSet()
      .zip((that.vertices intersect vertices).sortedBy { it.id }.toSet())
      .map { (left, right) -> left.new { left.outgoing + right.outgoing } }.toSet()

  operator fun minus(graph: G): G = new(vertices - graph.vertices)

  fun reversed(): G =
    new(vertices.map { it to listOf<E>() }.toMap() +
      vertices.flatMap { src -> src.outgoing.map { edge -> edge.target to edge.new(edge.target, src) } }
        .groupBy({ it.first }, { it.second }).mapValues { (_, v) -> v })

  val histogram by lazy { poolingBy { size } }

  /*
   * Weisfeiler-Lehman isomorphism test:
   * http://www.jmlr.org/papers/volume12/shervashidze11a/shervashidze11a.pdf#page=6
   * https://davidbieber.com/post/2019-05-10-weisfeiler-lehman-isomorphism-test/
   */

  tailrec fun wl(k: Int = 5, labels: Map<V, Int> = histogram): Map<V, Int> {
    val next = poolingBy { map { labels[it]!! }.sorted().hashCode() }
    return if (k <= 0 || labels == next) labels else wl(k - 1, next)
  }

  fun isomorphicTo(that: G) =
    vertices.size == that.vertices.size &&
      totalEdges == that.totalEdges &&
      hashCode() == that.hashCode()

  override fun equals(other: Any?) =
    super.equals(other) || (other as? G)?.isomorphicTo(this as G) ?: false

  override fun hashCode() = wl().values.sorted().hashCode()

  fun poolingBy(stat: Set<V>.() -> Int): Map<V, Int> =
    vertices.map { it to stat(it.neighbors()) }.toMap()

  fun attachRandomT(degree: Int): G =
      this + (prototype?.new(
        newId = vertices.size.toString(),
        out = if (vertices.isEmpty()) emptySet()
        else DiscreteProbabilityCollectionSampler(RandomSource.create(JDK),
            degMap.map { (k, v) -> k to (v + 1.0) / (totalEdges + 1.0) }.toMap())
         .run { generateSequence { sample() }.take(degree.coerceAtMost(vertices.size)) }.toSet()
      )?.graph ?: new())

  var done = mutableSetOf<String>()
  var string = ""
  fun propagate() {
    val (previousStates, unoccupied) = vertices.partition { it.occupied }
    val nextStates = unoccupied.intersect(previousStates.flatMap { it.neighbors }.toSet())
    previousStates.forEach { it.occupied = false }
    nextStates.forEach { it.occupied = true; done.add(it.id) }
    string = "y = " + done.joinToString(" + ")
  }

  // https://en.wikipedia.org/wiki/Barab%C3%A1si%E2%80%93Albert_model#Algorithm
  tailrec fun prefAttach(graph: G = this as G, vertices: Int = 1, degree: Int = 3): G =
    if (vertices <= 0) graph
    else prefAttach(graph.attachRandomT(degree), vertices - 1, degree)

  // https://web.engr.oregonstate.edu/~erwig/papers/InductiveGraphs_JFP01.pdf#page=6
  override fun toString() =
    "(" + vertices.joinToString(", ", "{", "}") + ", " +
      edgList.map { (v, e) -> "${v.id}→${e.target.id}" }.joinToString(", ", "{", "}") + ")"
}

abstract class Edge<G : Graph<G, E, V>, E : Edge<G, E, V>, V : Vertex<G, E, V>>
constructor(override val source: V, override val target: V): IEdge<G, E, V> {
  override val graph by lazy { target.graph }
  abstract fun new(source: V, target: V): E
}

// TODO: Link to graph and make a "view" of the container graph
abstract class Vertex<G : Graph<G, E, V>, E : Edge<G, E, V>, V : Vertex<G, E, V>>
constructor(val id: String) : IVertex<G, E, V> {
  abstract fun new(newId: String = id, out: Set<V> = emptySet()): V
  abstract fun new(newId: String = id, edgeMap: (V) -> Collection<E>): V

  abstract val edgeMap: (V) -> Collection<E>
  override val outgoing by lazy { edgeMap(this as V).toSet() }
  override val incoming by lazy { graph.reversed().edgMap.toMap()[this]!! }
  open val neighbors by lazy { outgoing.map { it.target }.toSet() }
  open var occupied = false
  override val graph: G by lazy { graph(neighbors(-1)) }
  abstract fun graph(vertices: Set<V>): G

  tailrec fun neighbors(k: Int = 0, vertices: Set<V> = neighbors + this as V): Set<V> =
    if (k == 0 || vertices.neighbors() == vertices) vertices
    else neighbors(k - 1, vertices + vertices.neighbors() + this as V)

  // Removes all edges pointing outside the set
  private fun Set<V>.closure(): Set<V> =
    map { vertex -> vertex.new { vertex.outgoing.filter { it.target in this } } }.toSet()

  private fun Set<V>.neighbors(): Set<V> = flatMap { it.neighbors() }.toSet()

  fun neighborhood(): G = graph(neighbors(0).closure())

  open operator fun getValue(a: Any?, prop: KProperty<*>): V = new(prop.name)
  override fun equals(other: Any?) = (other as? LGVertex)?.id == id
  override fun hashCode() = id.hashCode()
  override fun toString() = id
}