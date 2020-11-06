package edu.mcgill.kaliningraph

import edu.mcgill.kaliningraph.typefamily.*
import guru.nidi.graphviz.attribute.Label
import guru.nidi.graphviz.model.*
import kweb.shoebox.toArrayList
import org.apache.commons.rng.sampling.DiscreteProbabilityCollectionSampler
import org.apache.commons.rng.simple.RandomSource
import org.apache.commons.rng.simple.RandomSource.JDK
import org.ejml.kotlin.*
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.reflect.KProperty

abstract class Graph<G, E, V>(override val vertices: Set<V> = setOf()):
  Set<V> by vertices,
  IGraph<G, E, V>,
  // TODO: Compare graph as a function V -> Set<V> vs. a multimap graph[g]
  // https://github.com/snowleopard/alga-paper/releases/download/final/algebraic-graphs.pdf
    (V) -> Set<V> by { it: V -> it.neighbors }
  where G: Graph<G, E, V>, E: Edge<G, E, V>, V: Vertex<G, E, V> {
  open fun new(vararg graphs: G): G = new(graphs.toList())
  open fun new(vararg vertices: V): G = new(vertices.map { it.graph })
  open fun new(graphs: List<G>): G = new(graphs.fold(new()) { it, acc -> it + acc }.vertices)
  open fun new(adjList: Map<V, Set<E>>): G = new(adjList.map { (k, v) -> k.Vertex { v } }.toSet())
  abstract fun new(vertices: Set<V> = setOf()): G

  open val prototype: V? by lazy { vertices.firstOrNull() }

  val totalEdges: Int by lazy { vertices.map { it.neighbors.size }.sum() }
  protected val index: VIndex<G, E, V> by lazy { VIndex(vertices) }

  protected class VIndex<G: Graph<G, E, V>, E : Edge<G, E, V>, V : Vertex<G, E, V>>(val set: Set<V>) {
    operator fun plus(vertexIdx: VIndex<G, E, V>) = VIndex(set + vertexIdx.set)
    val array: ArrayList<V> = set.toList().toArrayList()
    val map: Map<V, Int> = array.mapIndexed { index, a -> a to index }.toMap()
    operator fun get(it: Vertex<G, E, V>): Int? = map[it]
    operator fun get(it: Int): V = array[it]
  }

  operator fun get(vertexIdx: Int): V = index[vertexIdx]

  val edgList: List<Pair<V, E>> by lazy { vertices.flatMap { s -> s.outgoing.map { s to it } } }
  val adjList: List<Pair<V, V>> by lazy { edgList.map { (v, e) -> v to e.target } }
  val edgMap: Map<V, Set<E>> by lazy { vertices.map { it to it.outgoing }.toMap() }
  val edges: Set<E> by lazy { edgMap.values.flatten().toSet() }

  // Degree matrix
  val D: SpsMat by lazy { elwise(size) { i, j -> if(i == j) this[i].neighbors.size.toDouble() else 0.0 } }

  // Adjacency matrix
  val A: SpsMat by lazy { vwise { _, _ -> 1.0 } }
  val A_AUG: SpsMat by lazy { A + A.transpose() + I }

  // Symmetric normalized adjacency
  val ASYMNORM: SpsMat by lazy {
    vwise { v, n -> 1.0 / (sqrt(v.degree.toDouble()) * sqrt(n.degree.toDouble())) }
  }

  // Laplacian matrix
  val L: SpsMat by lazy { D - A }
  val I: SpsMat by lazy { elwise(size) }
  // Symmetric normalized Laplacian
  val LSYMNORM: SpsMat by lazy { I - ASYMNORM }

  val ENCODED: SpsMat by lazy { vertices.map { it.encode() }.toTypedArray().toEJMLSparse() }

  // TODO: Implement APSP distance matrix using algebraic Floyd-Warshall
  //       https://doi.org/10.1137/1.9780898719918.ch5

  inline fun vwise(crossinline lf: Graph<G, E, V>.(V, V) -> Double?): SpsMat =
    elwise(size) { i, j ->
      (this[i] to this[j]).let { (v, n) -> if (n in v.neighbors) lf(v, n) else null }
    }

  val degMap: Map<V, Int> by lazy { vertices.map { it to it.neighbors.size }.toMap() }

  operator fun SpsMat.get(n0: V, n1: V) = this[index[n0]!!, index[n1]!!]
  operator fun SpsMat.set(n0: V, n1: V, value: Double) {
    this[index[n0]!!, index[n1]!!] = value
  }

  // Implements graph merge. For all vertices in common, merge their neighbors.
  // TODO: Figure out how to implement this operator "correctly"
  // https://github.com/snowleopard/alga-paper/releases/download/final/algebraic-graphs.pdf
  open operator fun plus(that: G): G =
    new((this - that) + (this join that) + (that - this))

  infix fun join(that: G): Set<V> =
    (vertices intersect that.vertices).sortedBy { it.id }.toSet()
      .zip((that.vertices intersect vertices).sortedBy { it.id }.toSet())
      .map { (left, right) -> left.Vertex { left.outgoing + right.outgoing } }.toSet()

  operator fun minus(graph: G): G = new(vertices - graph.vertices)

  // TODO: Reimplement using matrix transpose
  fun reversed(): G = new(
    vertices.map { it to setOf<E>() }.toMap() +
      vertices.flatMap { src ->
        src.outgoing.map { edge -> edge.target to edge.new(edge.target, src) }
      }.groupBy({ it.first }, { it.second }).mapValues { (_, v) -> v.toSet() }
  )

  fun randomWalk(r: Random = DEFAULT_RANDOM) = RandomWalk(r, this as G)

  val histogram: Map<V, Int> by lazy { aggregateBy { it.size } }

  /* (𝟙 + A)ⁿ[a, b] counts the number of walks between vertices a, b of length n
   * Let i be the smallest natural number such that (𝟙 + A)ⁱ has no zeros.
   * Fact: i is the length of the longest shortest path in G.
   *
   * TODO: implement O(M(n)log(n)) version based on Booth & Lipton (1981)
   *       https://doi.org/10.1007/BF00264532
   */

  tailrec fun diameter(i: Int = 1, walks: SpsMat = A_AUG): Int =
    if (walks.isFull) i else diameter(i = i + 1, walks = walks * A_AUG)

  /* Weisfeiler-Lehman isomorphism test:
   * http://www.jmlr.org/papers/volume12/shervashidze11a/shervashidze11a.pdf#page=6
   * http://davidbieber.com/post/2019-05-10-weisfeiler-lehman-isomorphism-test/
   * https://breandan.net/2020/06/30/graph-computation/#weisfeiler-lehman
   */

  tailrec fun wl(k: Int = 5, label: (V) -> Int = { histogram[it]!! }): Map<V, Int> {
    val updates = aggregateBy { it.map(label).sorted().hashCode() }
    return if (k <= 0 || all { label(it) == updates[it] }) updates
    else wl(k - 1) { updates[it]!! }
  }

  /* Graph-level GNN implementation
   * https://www.cs.mcgill.ca/~wlh/grl_book/files/GRL_Book-Chapter_5-GNNs.pdf#page=6
   * H^t := σ(AH^(t-1)W^(t) + H^(t-1)W^t)
   *
   * TODO:
   *   Pooling: https://www.cs.mcgill.ca/~wlh/grl_book/files/GRL_Book-Chapter_5-GNNs.pdf#page=18
   *   Convolution: https://arxiv.org/pdf/2004.03519.pdf#page=2
   */

  tailrec fun gnn(
    // Message passing rounds
    t: Int = diameter() * 10,
    // Matrix of node representations ℝ^{|V|xd}
    H: SpsMat = ENCODED,
    // (Trainable) weight matrix ℝ^{dxd}
    W: SpsMat = randomMatrix(H.numCols),
    // Bias term ℝ^{dxd}
    b: SpsMat = randomMatrix(size, H.numCols),
    // Nonlinearity ℝ^{*} -> ℝ^{*}
    σ: (SpsMat) -> SpsMat = ACT_TANH,
    // Layer normalization ℝ^{*} -> ℝ^{*}
    z: (SpsMat) -> SpsMat = NORM_AVG,
    // Message ℝ^{*} -> ℝ^{*}
    m: Graph<G, E, V>.(SpsMat) -> SpsMat = { σ(z(A * it * W + it * W + b)) }
  ): SpsMat = if(t == 0) H else gnn(t = t - 1, H = m(H), W = W, b = b)

  fun isomorphicTo(that: G) =
    this.size == that.size &&
      totalEdges == that.totalEdges &&
      hashCode() == that.hashCode()

  override fun equals(other: Any?) =
    super.equals(other) || (other as? G)?.isomorphicTo(this as G) ?: false

  override fun hashCode() = wl().values.sorted().hashCode()

  fun <T> aggregateBy(aggregate: (Set<V>) -> T): Map<V, T> =
    vertices.map { it to aggregate(this(it)) }.toMap()

  fun toMap() = vertices.map { it to it.neighbors }.toMap()

  // https://en.wikipedia.org/wiki/Barab%C3%A1si%E2%80%93Albert_model#Algorithm

  tailrec fun prefAttach(graph: G = this as G, vertices: Int = 1, degree: Int = 3): G =
    if (vertices <= 0) graph
    else prefAttach(graph.attachRandomT(degree), vertices - 1, degree)

  fun attachRandomT(degree: Int): G =
    this + (prototype?.Vertex(
      newId = size.toString(),
      out = if (vertices.isEmpty()) emptySet()
      else DiscreteProbabilityCollectionSampler(RandomSource.create(JDK),
        degMap.map { (k, v) -> k to (v + 1.0) / (totalEdges + 1.0) }.toMap())
        .run { generateSequence { sample() }.take(degree.coerceAtMost(size)) }.toSet()
    )?.graph ?: new())

  // https://web.engr.oregonstate.edu/~erwig/papers/InductiveGraphs_JFP01.pdf#page=6
  override fun toString() =
    "(" + vertices.joinToString(", ", "{", "}") + ", " +
      edgList.joinToString(", ", "{", "}") { (v, e) -> "${v.id}→${e.target.id}" } + ")"

  open fun render() = toGraphviz()
}

abstract class Edge<G, E, V>(override val source: V, override val target: V): IEdge<G, E, V>
  where G: Graph<G, E, V>, E: Edge<G, E, V>, V: Vertex<G, E, V> {
  override val graph by lazy { target.graph }
  abstract fun new(source: V, target: V): E
  open fun render(): Link = (source.render() - target.render()).add(Label.of(""))
  operator fun component1() = source
  operator fun component2() = target
}

// TODO: Link to graph and make a "view" of the container graph
// TODO: Possible to extend Graph?
abstract class Vertex<G, E, V>(open val id: String): IVertex<G, E, V>, Encodable
  where G: Graph<G, E, V>, E: Edge<G, E, V>, V: Vertex<G, E, V> {
  abstract fun Graph(vertices: Set<V>): G
  abstract fun Edge(s: V, t: V): E
  abstract fun Vertex(newId: String = id, edgeMap: (V) -> Set<E>): V

  fun Vertex(newId: String = id, out: Set<V> = emptySet()): V =
    Vertex(newId) { s -> out.map { t -> Edge(s, t) }.toSet() }

  override val graph: G by lazy { Graph(neighbors(-1)) }
  abstract val edgeMap: (V) -> Collection<E> // Make a self-loop by passing this
  override val outgoing by lazy { edgeMap(this as V).toSet() }
  override val incoming by lazy { graph.reversed().edgMap[this] ?: emptySet() }
  open val neighbors by lazy { outgoing.map { it.target }.toSet() }
  open val degree by lazy { neighbors.size }

  override fun encode(): DoubleArray = id.vectorize()

  tailrec fun neighbors(k: Int = 0, vertices: Set<V> = neighbors + this as V): Set<V> =
    if (k == 0 || vertices.neighbors() == vertices) vertices
    else neighbors(k - 1, vertices + vertices.neighbors() + this as V)

  // Removes all edges pointing outside the set
  private fun Set<V>.closure(): Set<V> =
    map { vertex -> Vertex { vertex.outgoing.filter { it.target in this }.toSet() } }.toSet()

  private fun Set<V>.neighbors(): Set<V> = flatMap { it.neighbors() }.toSet()

  fun neighborhood(): G = Graph(neighbors(0).closure())

  open operator fun getValue(a: Any?, prop: KProperty<*>): V = Vertex(prop.name)
  open fun render(): MutableNode = Factory.mutNode(id).add(Label.of(toString()))
  override fun equals(other: Any?) =
    (other as? Vertex<*, *, *>)?.encode().contentEquals(encode())
  override fun hashCode() = id.hashCode()
  override fun toString() = id
}

class RandomWalk<G, E, V>(
  val rand: Random = DEFAULT_RANDOM,
  val graph: G,
  val head: V = graph.random()
): Sequence<RandomWalk<G, E, V>>
  where G: Graph<G, E, V>, E: Edge<G, E, V>, V: Vertex<G, E, V> {
  val tail by lazy {
    RandomWalk(
      graph = graph,
      head = graph.edgMap[head]!!.random(rand).target,
      rand = rand
    )
  }

  override fun toString() = head.toString()

  override fun iterator() = generateSequence(this) { it.tail }.iterator()
}

interface Encodable { fun encode(): DoubleArray }