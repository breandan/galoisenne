package ai.hypergraph.kaliningraph.typefamily

import ai.hypergraph.kaliningraph.*
import ai.hypergraph.kaliningraph.matrix.*
import ai.hypergraph.kaliningraph.theory.wl
import com.github.benmanes.caffeine.cache.Caffeine
import guru.nidi.graphviz.attribute.Label
import guru.nidi.graphviz.model.*
import org.ejml.kotlin.minus
import java.lang.reflect.*
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.reflect.*

// Reified constructors
@Suppress("FunctionName", "UNCHECKED_CAST")
interface IGF<G: IGraph<G, E, V>, E: IEdge<G, E, V>, V: IVertex<G, E, V>> {
  fun G(vertices: Set<V> = setOf()): G =
    if (vertices isA G.declaringClass) vertices as G // G(graph) -> graph
    else G.newInstance(vertices)

  fun E(s: V, t: V): E = E.newInstance(s, t)

  fun V(newId: String = "", edgeMap: (V) -> Set<E>): V = V.newInstance(newId, edgeMap)

  fun V(old: V, edgeMap: (V) -> Set<E>): V =
    // If no default constructor is provided, implementors must override V
    try { V(old.id, edgeMap) } catch (e: Exception) { old.V(old, edgeMap) }

  fun V(newId: String = "", out: Set<V> = emptySet()): V =
    V(newId) { s -> out.map { t -> E(s, t) }.toSet() }

  fun G(vararg graphs: G): G = G(graphs.toList())
  fun G(vararg vertices: V): G = G(vertices.map { it.graph })

  fun <T: Any> G(
    vararg adjList: Pair<T, T>,
    p2v: (Pair<T, T>) -> V = { (s, t) -> V("$s", setOf(V("$t"))) }
  ): G = adjList.map { p2v(it) }.fold(G()) { acc, v -> acc + v.graph }

  fun <T: Any> G(list: List<T> = emptyList()): G = when {
    list.isEmpty() -> setOf()
    list allAre G() -> list.fold(G()) { it, acc -> it + acc as G }
    list allAre V() -> list.map { it as V }.toSet()
    list anyAre IGF::class -> list.first { it is IGF<*, *, *> }
      .let { throw Exception("Unsupported: Graph(${it::class.java})") }
    else -> G(*list.zipWithNext().toTypedArray())
  }.let { G(it) }

  // Gafter's gadget! http://gafter.blogspot.com/2006/12/super-type-tokens.html
  val gev: Array<Class<*>> get() = memoize {
      (generateSequence(javaClass) { it.superclass as Class<IGF<G, E, V>> }
        .first { it.genericSuperclass is ParameterizedType }
        .genericSuperclass as ParameterizedType).actualTypeArguments
        .map { (if (it is ParameterizedType) it.rawType else it) as Class<*> }
        .toTypedArray()
  }

  val G: Constructor<G> get() = gev[0].getConstructor(Set::class.java) as Constructor<G>
  val E: Constructor<E> get() = gev.let { it[1].getConstructor(it[2], it[2]) } as Constructor<E>
  /** TODO: Generify first argument to support [TypedVertex] */
  val V: Constructor<V> get() = gev[2].getConstructor(String::class.java, Function1::class.java) as Constructor<V>

  /**
   * Memoizes the result of evaluating a pure function, indexed by:
   *
   * (1) instance reference, cf. [Graph.hashCode].
   * (2) the most direct caller on the stack (i.e. qualified method name)
   * (3) its arguments if caller is not niladic and [args] are supplied.
   */

  fun <T> memoize(
    classRef: Int = System.identityHashCode(this),
    methodRef: Int = Throwable().stackTrace[1].hashCode(),
    args: Array<*>? = null,
    computation: () -> T
  ): T = memo.get(Triple(classRef, methodRef, args)) { computation() } as T

  companion object {
    // https://github.com/ben-manes/caffeine/issues/160#issuecomment-305681211
    val memo = Caffeine.newBuilder().buildAsync<Triple<*, *, *>, Any>().synchronous()
  }
}

interface IGraph<G, E, V>: IGF<G, E, V>, Set<V>, (V) -> Set<V>
/*
 * TODO: Which primary interface should we expect graphs to fulfill?
 *
 * 1. a set Set<V>
 *   - Pros: Simple, has precedent cf. https://github.com/maxitg/SetReplace/
 *   - Cons: Finite, no consistency constraints on edges
 * 2. a [partial] function E ⊆ V×V / (V) -> Set<V> / graph(v)
 *   - Pros: Mathematically analogous, can represent infinite graphs
 *   - Cons: Memoization seems tricky to handle
 * 3. a [multi]map Map<V, Set<V>> / graph[v]
 *   - Pros: Computationally efficient representation
 *   - Cons: Finite, incompatible with Set<V> perspective
 * 4. a semiring
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
  val edgList: List<Pair<V, E>> get() = memoize { vertices.flatMap { s -> s.outgoing.map { s to it } } }
  val adjList: List<Pair<V, V>> get() = memoize { edgList.map { (v, e) -> v to e.target } }
  val edgMap: Map<V, Set<E>>    get() = memoize { vertices.associateWith { it.outgoing } }
  val edges: Set<E>             get() = memoize { edgMap.values.flatten().toSet() }
  val histogram: Map<V, Int>    get() = memoize { associateWith { this(it).size } }

  // TODO: Is this still needed?
  val prototype: V?             get() = memoize { vertices.firstOrNull() }
  // TODO: Move the following ceremony into named tensor
  //-------
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

  val D: SpsMat get() = memoize { elwise(size) { i, j -> if(i == j) this[i].neighbors.size.toDouble() else 0.0 } }

  // Adjacency matrix
  val A: BooleanMatrix get() = memoize { BooleanMatrix(size) { i, j -> this[j] in this[i].neighbors } }
  val A_AUG: BooleanMatrix get() = memoize { A + A.transpose() + BooleanMatrix.one(size) }

  // Symmetric normalized adjacency
  val ASYMNORM: SpsMat get() = memoize {
    vwise { v, n -> 1.0 / sqrt(v.outdegree.toDouble() * n.outdegree.toDouble()) }
  }

  // Graph Laplacian matrix
  val L: SpsMat get() = memoize { D - A }
  val I: SpsMat get() = memoize { elwise(size) }
  // Symmetric normalized Laplacian
  val LSYMNORM: SpsMat get() = memoize { I - ASYMNORM }

  val ENCODED: SpsMat get() = memoize { vertices.map { it.encode() }.toTypedArray().toEJMLSparse() }

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
      .map { (k, v) -> V(k.id) { v } }.toSet().let { G(it) }

  fun isomorphicTo(that: G): Boolean =
    this.size == that.size &&
      edges.size == that.edges.size &&
      hashCode() == that.hashCode()

  fun render() = toGraphviz()

  fun vwise(lf: IGraph<G, E, V>.(V, V) -> Double?): SpsMat =
    elwise(size) { i, j ->
      (this[i] to this[j]).let { (v, n) ->
        if (n in v.neighbors) lf(v, n) else null
      }
    }

  fun randomWalk(r: Random = Random.Default) = RandomWalk(r, this as G)
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

interface IEdge<G, E, V>: IGF<G, E, V>
  where G: IGraph<G, E, V>, E: IEdge<G, E, V>, V: IVertex<G, E, V> {
  val graph: G get() = memoize { target.graph }
  val source: V
  val target: V

  operator fun component1() = source
  operator fun component2() = target

  fun render(): Link = (source.render() - target.render()).add(Label.of(""))
}

// TODO: Make this a "view" of the container graph
interface IVertex<G, E, V>: IGF<G, E, V>, Encodable
  where G: IGraph<G, E, V>, E: IEdge<G, E, V>, V: IVertex<G, E, V> {
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
    map { v -> V(id) { v.outgoing.filter { it.target in this }.toSet() } }.toSet()

  private fun Set<V>.neighbors(): Set<V> = flatMap { it.neighbors() }.toSet()

  fun neighborhood(): G = G(neighbors(0).closure())

  override fun encode(): DoubleArray = id.vectorize()
  operator fun getValue(a: Any?, prop: KProperty<*>): V = V(prop.name)
  fun render(): MutableNode = Factory.mutNode(id).add(Label.of(toString()))
}

abstract class Graph<G, E, V>(override val vertices: Set<V> = setOf()) :
  IGraph<G, E, V>, Set<V> by vertices, (V) -> Set<V> by { it: V -> it.neighbors }
    where G : Graph<G, E, V>, E : Edge<G, E, V>, V : Vertex<G, E, V> {
  override fun equals(other: Any?) =
    super.equals(other) || (other as? G)?.isomorphicTo(this as G) ?: false

  override fun hashCode() =
    if (isEmpty()) super.hashCode() else wl().values.sorted().hashCode()

  // https://web.engr.oregonstate.edu/~erwig/papers/InductiveGraphs_JFP01.pdf#page=6
  override fun toString() =
    "(" + vertices.joinToString(", ", "{", "}") + ", " +
        edgList.joinToString(", ", "{", "}") { (v, e) -> "${v.id}→${e.target.id}" } + ")"
}

abstract class Edge<G, E, V>(override val source: V, override val target: V) : IEdge<G, E, V>
    where G : Graph<G, E, V>, E : Edge<G, E, V>, V : Vertex<G, E, V>

abstract class Vertex<G, E, V>(override val id: String) : IVertex<G, E, V>
    where G : Graph<G, E, V>, E : Edge<G, E, V>, V : Vertex<G, E, V> {
  override fun equals(other: Any?) =
    (other as? Vertex<*, *, *>)?.encode().contentEquals(encode())

  override fun hashCode() = id.hashCode()
  override fun toString() = id
}

interface Encodable { fun encode(): DoubleArray }

// Maybe we can hack reification using super type tokens?
infix fun Any.isA(that: Any) = when (that) {
  is KClass<out Any> -> that.java
  is Class<out Any> -> that
  else -> that::class.java
}.let { this::class.java.isAssignableFrom(it) }

infix fun Collection<Any>.allAre(that: Any) = all { it isA that }
infix fun Collection<Any>.anyAre(that: Any) = any { it isA that }

// https://github.com/amodeus-science/amod
abstract class TMap: IGraph<TMap, TRoad, TCity>
abstract class TRoad: IEdge<TMap, TRoad, TCity>
abstract class TCity: IVertex<TMap, TRoad, TCity>