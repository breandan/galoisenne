package ai.hypergraph.kaliningraph.types

import ai.hypergraph.kaliningraph.*
import ai.hypergraph.kaliningraph.cache.LRUCache
import ai.hypergraph.kaliningraph.graphs.LGVertex
import ai.hypergraph.kaliningraph.tensor.*
import ai.hypergraph.kaliningraph.theory.wl
import kotlin.js.JsName
import kotlin.math.sqrt
import kotlin.random.Random

// Provides caching and inheritable constructors for reified parameters <G, E, V>
// Interfaces are our only option because we need multiple inheritance
@Suppress("FunctionName", "UNCHECKED_CAST")
interface IGF<G, E, V> where G: IGraph<G, E, V>, E: IEdge<G, E, V>, V: IVertex<G, E, V> {
  @JsName("G0") val G: (vertices: Set<V>) -> G // Graph constructor
  @JsName("E0") val E: (s: V, t: V) -> E // Edge constructor
  @JsName("V0") val V: (old: V, edgeMap: (V) -> Set<E>) -> V // Vertex constructor

  fun G() = G(setOf())
  fun G(vararg graphs: G): G = G(graphs.toList())
  fun G(vararg vertices: V): G = G(vertices.map { it.graph })
  fun G(list: List<Any>): G = when {
    list.isEmpty() -> setOf()
    list allAre G() -> list.fold(G()) { it, acc -> it + acc as G }
    list allAre list.first() -> list.map { it as V }.toSet()
    else -> throw Exception("Unsupported constructor: G(${list.joinToString(",") { it::class.simpleName!! }})")
  }.let { G(it) }

  val deepHashCode: Int // Uniquely identifies this class instance, regardless of structure

  /**
   * Memoizes the result of evaluating a pure subclass function, indexed by:
   *
   * (1) instance reference, cf. [Graph.hashCode].
   * (2) the most direct caller on the stack (i.e. qualified method name)
   * (3) its arguments if caller is not niladic and [args] are supplied.
   */
  fun <T> memoize(
    classRef: Int = deepHashCode,
    methodRef: Int = Throwable().stackTraceToString().lines()[2].hashCode(),
    args: Array<*>? = null,
    argsRef: Int = args?.contentDeepHashCode() ?: 0,
    computation: () -> T
  ): T = memo.getOrPut(VT(classRef, methodRef, argsRef)) { computation() as Any } as T

  companion object {
    val memo = LRUCache<V3<Int>, Any>(1000)
  }
}

typealias AdjList<V> = List<V2<V>>
interface IGraph<G, E, V>: IGF<G, E, V>, Set<V>, Encodable
/*
 * TODO: Which primary interface should we expect graphs to fulfill?
 *
 * 1. a set Set<V>
 *   - Pros: Simple, has precedent cf. https://github.com/maxitg/SetReplace/
 *   - Cons: Finite, no consistency constraints on edges
 * 2. a [partial] function E ⊆ V×V / (V) -> Set<V>
 *   - Pros: Mathematically analogous, can represent infinite graphs
 *   - Cons: Disallowed on JS, see https://discuss.kotlinlang.org/t/extending-function-in-class/15176
 * 3. a [multi]map Map<V, Set<V>>
 *   - Pros: Computationally efficient representation, graph[v] <=> graph(v)
 *   - Cons: Finite, incompatible with Set<V> perspective
 * 4. a semiring, see https://en.wikipedia.org/wiki/Semiring#Definition
 *   - Pros: Useful for describing many algebraic path problems
 *   - Cons: Esoteric API / unsuitable as an abstract interface
 *
 * Algebraic perspective   : https://github.com/snowleopard/alga-paper/releases/download/final/algebraic-graphs.pdf
 *                         : https://arxiv.org/pdf/1909.04881.pdf
 * Type-family perspective : https://www.cs.cornell.edu/~ross/publications/shapes/shapes-pldi14-tr.pdf#page=3
 *                         : https://www.cs.cornell.edu/andru/papers/familia/familia.pdf#page=8
 * Inductive perspective   : https://web.engr.oregonstate.edu/~erwig/papers/InductiveGraphs_JFP01.pdf
 *                         : https://doi.org/10.1145/258949.258955
 *                         : https://www.cs.utexas.edu/~wcook/Drafts/2012/graphs.pdf
 * Semiring perspective    : http://stedolan.net/research/semirings.pdf
 *                         : https://doi.org/10.1007/978-0-387-75450-5
 *                         : https://doi.org/10.2200/S00245ED1V01Y201001CNT003
 */

  where G: IGraph<G, E, V>, E: IEdge<G, E, V>, V: IVertex<G, E, V> {
  val vertices: Set<V>
  val edgList: List<Π2<V, E>> get() = memoize { vertices.flatMap { s -> s.outgoing.map { s to it } } }
  val adjList: AdjList<V>       get() = memoize { edgList.map { (v, e) -> v cc e.target } }
  val edgMap: Map<V, Set<E>>    get() = memoize { vertices.associateWith { it.outgoing } }
  val edges: Set<E>             get() = memoize { edgMap.values.flatten().toSet() }
  val histogram: Map<V, Int>    get() = memoize { associateWith { it.neighbors.size } }

  // TODO: Move the following ceremony into named tensor
  //-------
  operator fun get(cond: (V) -> Boolean): Set<V> = vertices.filter(cond).toSet()
  val index: VIndex<G, E, V>    get() = memoize { VIndex(vertices) }
  operator fun get(vertexIdx: Int): V = index[vertexIdx]
  class VIndex<G: IGraph<G, E, V>, E : IEdge<G, E, V>, V : IVertex<G, E, V>>(val set: Set<V>) {
    val array: List<V> = set.toList()
    val map: Map<V, Int> = array.mapIndexed { index, a -> a to index }.toMap()
    //    operator fun get(it: IVertex<G, E, V>): Int? = map[it]
    operator fun get(it: Int): V = array[it]
  }
//  operator fun SpsMat.get(n0: V, n1: V) = this[index[n0]!!, index[n1]!!]
//  operator fun SpsMat.set(n0: V, n1: V, value: Double) {
//    this[index[n0]!!, index[n1]!!] = value
//  }
  //-------

  val D: DoubleMatrix get() = memoize { DoubleMatrix(size) { i, j -> if(i == j) this[i].neighbors.size.toDouble() else 0.0 } }

  // Adjacency matrix
  val A: BooleanMatrix get() = memoize { BooleanMatrix(size) { i, j -> this[j] in this[i].neighbors } }
  val A_AUG: BooleanMatrix get() = memoize { A + A.transpose() + BooleanMatrix.one(size) }

  // Symmetric normalized adjacency
  val ASYMNORM: DoubleMatrix
    get() = memoize { vwise { v, n -> 1.0 / sqrt(v.outdegree.toDouble() * n.outdegree.toDouble()) } }

  // Graph Laplacian matrix
  val L: DoubleMatrix get() = memoize { D - A }
  val I: DoubleMatrix get() = memoize { DoubleMatrix(size, size, ::kroneckerDelta) }
  // Symmetric normalized Laplacian
  val LSYMNORM: DoubleMatrix get() = memoize { I - ASYMNORM }

  val ENCODED: DoubleMatrix get() = memoize { vertices.map { it.encode() }.toTypedArray().toDoubleMatrix() }

  // TODO: Implement APSP distance matrix using algebraic Floyd-Warshall
  //       https://doi.org/10.1137/1.9780898719918.ch5

  val degMap: Map<V, Int> get() = memoize { vertices.associateWith { it.neighbors.size } }

  // Implements graph merge. For all vertices in common, merge their neighbors.
  // TODO: Figure out how to implement this operator "correctly"
  // https://github.com/snowleopard/alga-paper/releases/download/final/algebraic-graphs.pdf
  operator fun plus(that: G): G =
    G((this - that) + (this join that) + (that - this))

  operator fun minus(graph: G): G = G(vertices - graph.vertices)

  infix fun join(that: G): Set<V> =
    (vertices intersect that.vertices).sortedBy { it.id }.toSet()
      .zip((that.vertices intersect vertices).sortedBy { it.id }.toSet())
      .map { (left, right) -> V(left) { left.outgoing + right.outgoing } }
      .toSet()

  // TODO: Reimplement using matrix transpose
  fun reversed(): G =
    (vertices.associateWith { setOf<E>() } +
      vertices.flatMap { src ->
        src.outgoing.map { edge -> edge.target to E(edge.target, src) }
      }.groupBy({ it.first }, { it.second }).mapValues { (_, v) -> v.toSet() })
      .map { (k, v) -> V(k) { v } }.toSet().let { G(it) }

  fun isomorphicTo(that: G): Boolean =
    this.size == that.size &&
      this.edges.size == that.edges.size &&
      this.encode().contentEquals(that.encode())

  fun vwise(lf: IGraph<G, E, V>.(V, V) -> Double): DoubleMatrix =
    DoubleMatrix(size) { i, j ->
      (this[i] cc this[j]).let { (v, n) ->
        if (n in v.neighbors) lf(v, n) else 0.0
      }
    }

  fun randomWalk(r: Random = Random.Default) = RandomWalk(r, this as G)

  fun asString() =
    "(" + vertices.joinToString(", ", "{", "}") + ", " +
      edgList.joinToString(", ", "{", "}") { (v, e) -> "${v.id}→${e.target.id}" } + ")"

  fun toDot() =
    """
      strict digraph {
          graph ["concentrate"="true","rankdir"="LR","bgcolor"="transparent","margin"="0.0","compound"="true","nslimit"="20"]
          ${
      vertices.joinToString("\n") {
        """"${it.id}" ["color"="black","fontcolor"="black","fontname"="Helvetica","fontsize"="20","penwidth"="4.0","shape"="Mrecord", "label"="$it"]""" }
          } 
          ${edgList.joinToString("\n") { (v, e) -> 
        """"${v.id}" -> "${e.target.id}" ["color"="${ if (v is LGVertex && v.occupied) "red" else "black" }","arrowhead"="normal","penwidth"="4.0","label"=""]""" }
          }
      }
    """.trimIndent()
}

class RandomWalk<G, E, V>(
  val rand: Random = Random.Default,
  val graph: G,
  val head: V = graph.random()
): Sequence<RandomWalk<G, E, V>>
  where G: IGraph<G, E, V>,
        E: IEdge<G, E, V>,
        V: IVertex<G, E, V> {
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

interface IEdge<G, E, V> : IGF<G, E, V>
        where G: IGraph<G, E, V>, E: IEdge<G, E, V>, V: IVertex<G, E, V> {
  val graph: G get() = memoize { target.graph }
  val source: V
  val target: V

  operator fun component1() = source
  operator fun component2() = target
}

// TODO: Make this a "view" of the container graph
interface IVertex<G, E, V> : IGF<G, E, V>, Encodable
        where G : IGraph<G, E, V>, E : IEdge<G, E, V>, V : IVertex<G, E, V> {
  val id: String // TODO: Need to think about this more carefully

  val graph: G get() = memoize { G(neighbors(-1)) }
  val incoming: Set<E> get() = memoize { graph.reversed().edgMap[this] ?: emptySet() }
  val outgoing: Set<E> get() = memoize { edgeMap(this as V).toSet() }
  val edgeMap: (V) -> Set<E> // Make a self-loop by passing this

  val neighbors get() = memoize { outgoing.map { it.target }.toSet() }
  val outdegree get() = neighbors.size

  // tailrec prohibited on open members? may be possible with deep recursion
  // https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-deep-recursive-function/
  fun neighbors(k: Int = 0, vertices: Set<V> = neighbors + this as V): Set<V> =
    if (k == 0 || vertices.neighbors() == vertices) vertices
    else neighbors(k - 1, vertices + vertices.neighbors() + this as V)

  // Removes all edges pointing outside the set
  private fun Set<V>.closure(): Set<V> =
    map { v -> V(this@IVertex as V) { v.outgoing.filter { it.target in this }.toSet() } }.toSet()

  private fun Set<V>.neighbors(): Set<V> = flatMap { it.neighbors() }.toSet()

  fun neighborhood(): G = G(neighbors(0).closure())

  override fun encode(): DoubleArray
}

abstract class AGF<G, E, V> : IGF<G, E, V>
        where G : IGraph<G, E, V>, E : IEdge<G, E, V>, V : IVertex<G, E, V> {
  override val deepHashCode: Int = Random.nextInt()
  override fun hashCode() = deepHashCode
}

abstract class Graph<G, E, V>(override val vertices: Set<V> = setOf()) :
  AGF<G, E, V>(), IGraph<G, E, V>,
  Set<V> by vertices, IGF<G, E, V>
  where G : Graph<G, E, V>, E : Edge<G, E, V>, V : Vertex<G, E, V> {
  override fun equals(other: Any?) =
    super.equals(other) || (other as? G)?.isomorphicTo(this as G) ?: false
  override fun encode() =
    if (isEmpty()) DoubleArray(10) { 0.0 }
    else wl().values.sorted().map { it.toDouble() }.toDoubleArray()
  // https://web.engr.oregonstate.edu/~erwig/papers/InductiveGraphs_JFP01.pdf#page=6
  override fun toString() = asString()
}

abstract class Edge<G, E, V>(override val source: V, override val target: V) :
  AGF<G, E, V>(), IEdge<G, E, V>
  where G : Graph<G, E, V>, E : Edge<G, E, V>, V : Vertex<G, E, V> {
  override fun equals(other: Any?) = (other as? E)?.let { hashCode() == other.hashCode() } ?: false
  override fun hashCode(): Int = source.hashCode() + target.hashCode()
  override fun toString() = "$source→$target"
}

abstract class Vertex<G, E, V>(override val id: String) :
  AGF<G, E, V>(), IVertex<G, E, V>
  where G : Graph<G, E, V>, E : Edge<G, E, V>, V : Vertex<G, E, V> {
  override fun equals(other: Any?) = (other as? Vertex<*, *, *>)?.let { id == it.id } ?: false
  override fun encode() = id.vectorize()
  override fun hashCode() = id.hashCode()
  override fun toString() = id
}

interface Encodable { fun encode(): DoubleArray }


// https://github.com/amodeus-science/amod
abstract class TMap: IGraph<TMap, TRoad, TCity>
abstract class TRoad: IEdge<TMap, TRoad, TCity>
abstract class TCity: IVertex<TMap, TRoad, TCity>

interface SGF<G, E, V> where
  G: SGraph<G, E, V>, E: SEdge<G, E, V>, V: SVertex<G, E, V> { /*...*/ }

interface SGraph<G, E, V>: SGF<G, E, V> where
  G: SGraph<G, E, V>, E: SEdge<G, E, V>, V: SVertex<G, E, V> { /*...*/ }

interface SEdge<G, E, V>: SGF<G, E, V> where
  G: SGraph<G, E, V>, E: SEdge<G, E, V>, V: SVertex<G, E, V> { /*...*/ }

interface SVertex<G, E, V>: SGF<G, E, V> where
  G: SGraph<G, E, V>, E: SEdge<G, E, V>, V: SVertex<G, E, V> { /*...*/ }

class SMap: SGraph<SMap, SRoad, SCity> { /*...*/ }
class SRoad: SEdge<SMap, SRoad, SCity> { /*...*/ }
class SCity: SVertex<SMap, SRoad, SCity> { /*...*/ }