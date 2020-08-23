package edu.mcgill.kaliningraph

import edu.mcgill.kaliningraph.typefamily.*
import guru.nidi.graphviz.attribute.*
import guru.nidi.graphviz.attribute.Arrow.NORMAL
import guru.nidi.graphviz.attribute.Color.*
import guru.nidi.graphviz.attribute.Style.lineWidth
import guru.nidi.graphviz.graph
import guru.nidi.graphviz.model.*
import kweb.shoebox.toArrayList
import org.apache.commons.rng.sampling.DiscreteProbabilityCollectionSampler
import org.apache.commons.rng.simple.RandomSource
import org.apache.commons.rng.simple.RandomSource.JDK
import org.ejml.data.DMatrixSparseCSC
import org.ejml.data.DMatrixSparseTriplet
import org.ejml.kotlin.*
import kotlin.math.tanh
import kotlin.random.Random
import kotlin.reflect.KProperty

abstract class Graph<G : Graph<G, E, V>, E : Edge<G, E, V>, V : Vertex<G, E, V>>
constructor(override val vertices: Set<V> = setOf())
  : Set<V> by vertices, IGraph<G, E, V>, (V) -> Set<V> by { it: V -> it.neighbors } {
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
    operator fun get(it: Vertex<G, E, V>) = map[it]
    operator fun get(it: Int) = array[it]
  }

  operator fun get(vertexIdx: Int): V = index[vertexIdx]

  val edgList: Sequence<Pair<V, E>> by lazy { vertices.flatMap { s -> s.outgoing.map { s to it } }.asSequence() }
  val adjList: Sequence<Pair<V, V>> by lazy { edgList.map { (v, e) -> v to e.target } }
  val edgMap: Map<V, Set<E>> by lazy { vertices.map { it to it.outgoing }.toMap() }
  val edges: Set<E> by lazy { edgMap.values.flatten().toSet() }

  // Degree matrix
  val D: DMatrixSparseCSC by lazy {
    DMatrixSparseTriplet(size, size, totalEdges).also { degMat ->
      vertices.forEach { v -> degMat[v, v] = v.neighbors.size.toDouble() }
    }.toCSC()
  }

  // Adjacency matrix
  val A: DMatrixSparseCSC by lazy {
    DMatrixSparseTriplet(size, size, totalEdges).also { adjMat ->
      vertices.forEach { v -> v.neighbors.forEach { n -> adjMat[v, n] = 1.0 } }
    }.toCSC()
  }

  // Laplacian matrix
  val L by lazy { D - A }

  val H0 by lazy {
    DMatrixSparseTriplet(size, size, totalEdges).also { featMat ->
      vertices.encode().forEachIndexed { i, row ->
        row.forEachIndexed { j, it -> if(it != 0.0) featMat[i, j] = it }
      }
    }.toCSC()
  }

  // TODO: implement one-hot encoder for finite alphabet
  fun Set<V>.encode() = Array(size) { i -> Array(size) { j -> if(i == j) 1.0 else 0.0 } }

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
      .map { (left, right) -> left.Vertex { left.outgoing + right.outgoing } }.toSet()

  operator fun minus(graph: G): G = new(vertices - graph.vertices)

  fun reversed(): G =
    new(vertices.map { it to setOf<E>() }.toMap() +
      vertices.flatMap { src -> src.outgoing.map { edge -> edge.target to edge.new(edge.target, src) } }
        .groupBy({ it.first }, { it.second }).mapValues { (_, v) -> v.toSet() })

  val histogram: Map<V, Int> by lazy { aggregateBy { it.size } }
  val labelFunc: (V) -> Int = { v: V -> histogram[v]!! }

  /*
   * Weisfeiler-Lehman isomorphism test:
   * http://www.jmlr.org/papers/volume12/shervashidze11a/shervashidze11a.pdf#page=6
   * http://davidbieber.com/post/2019-05-10-weisfeiler-lehman-isomorphism-test/
   * https://breandan.net/2020/06/30/graph-computation/#weisfeiler-lehman
   */

  // TODO: implement GNN as recurrence relation/sparse matmul
  tailrec fun wl(k: Int = 5, label: (V) -> Int = { histogram[it]!! }): Map<V, Int> {
    val updates = aggregateBy { it.map(label).sorted().hashCode() }
    return if (k <= 0 || all { label(it) == updates[it] }) updates
    else wl(k - 1) { updates[it]!! }
  }

  // https://www.cs.mcgill.ca/~wlh/grl_book/files/GRL_Book-Chapter_5-GNNs.pdf#page=6
  // H^t := σ(AH^(t-1)W^(t) + H^(t-1)W^t)
  @Suppress("NonAsciiCharacters")
  tailrec fun mpnn(
    t: Int = 10,
    H: DMatrixSparseCSC = H0,
    W: DMatrixSparseCSC = randomMatrix(size, size) { Random.nextDouble() },
    b: DMatrixSparseCSC = randomMatrix(size, size) { Random.nextDouble() },
    σ: (DMatrixSparseCSC) -> DMatrixSparseCSC = { it.elwise { tanh(it) } }
  ): DMatrixSparseCSC =
    if(t == 0) H
    else
      mpnn(
        t = t - 1,
        H = σ(A * H * W + H * W + b),
//        H = A + A,
        W = W,
        b = b
      )

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
      edgList.map { (v, e) -> "${v.id}→${e.target.id}" }.joinToString(", ", "{", "}") + ")"

  open fun render(): MutableGraph = graph(directed = true) {
    val color = if (DARKMODE) WHITE else BLACK
    edge[color, NORMAL, lineWidth(THICKNESS)]
    graph[Rank.dir(Rank.RankDir.LEFT_TO_RIGHT), TRANSPARENT.background(), GraphAttr.margin(0.0), Attributes.attr("compound", "true"), Attributes.attr("nslimit", "20")]
    node[color, color.font(), Font.config("Helvetica", 20), lineWidth(THICKNESS), Attributes.attr("shape", "Mrecord")]

    vertices.forEach { vertex ->
      vertex.outgoing.forEach { edge ->
        edge.render().also { if (vertex is LGVertex && vertex.occupied) it.add(RED) }
      }
    }
  }
}

abstract class Edge<G : Graph<G, E, V>, E : Edge<G, E, V>, V : Vertex<G, E, V>>
constructor(override val source: V, override val target: V): IEdge<G, E, V> {
  override val graph by lazy { target.graph }
  abstract fun new(source: V, target: V): E
  open fun render(): Link = (source.render() - target.render()).add(Label.of(""))
  operator fun component1() = source
  operator fun component2() = target
}

// TODO: Link to graph and make a "view" of the container graph
// TODO: Possible to extend Graph?
abstract class Vertex<G : Graph<G, E, V>, E : Edge<G, E, V>, V : Vertex<G, E, V>>
constructor(val id: String) : IVertex<G, E, V> {
  abstract fun Graph(vertices: Set<V>): G
  abstract fun Edge(s: V, t: V): E
  abstract fun Vertex(newId: String = id, edgeMap: (V) -> Set<E>): V

  fun Vertex(newId: String = id, out: Set<V> = emptySet()): V =
    Vertex(newId) { s -> out.map { t -> Edge(s, t) }.toSet() }

  override val graph: G by lazy { Graph(neighbors(-1)) }
  abstract val edgeMap: (V) -> Collection<E> // Allows self-loops by passing this
  override val outgoing by lazy { edgeMap(this as V).toSet() }
  override val incoming by lazy { graph.reversed().edgMap.toMap()[this]!! }
  open val neighbors by lazy { outgoing.map { it.target }.toSet() }

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
  override fun equals(other: Any?) = (other as? LGVertex)?.id == id
  override fun hashCode() = id.hashCode()
  override fun toString() = id
}