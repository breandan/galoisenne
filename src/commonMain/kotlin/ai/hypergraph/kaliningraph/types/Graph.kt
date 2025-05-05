package ai.hypergraph.kaliningraph.types

import ai.hypergraph.kaliningraph.*
import ai.hypergraph.kaliningraph.cache.LRUCache
import ai.hypergraph.kaliningraph.graphs.*
import ai.hypergraph.kaliningraph.tensor.*
import ai.hypergraph.kaliningraph.theory.wl
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator
import kotlin.collections.orEmpty
import kotlin.js.JsName
import kotlin.math.sqrt
import kotlin.properties.ReadOnlyProperty
import kotlin.random.Random

// Provides caching and inheritable constructors for reified parameters <G, E, V>
// Interfaces are our only option because we need multiple inheritance
@Suppress("FunctionName", "UNCHECKED_CAST")
interface IGF<G, E, V> where G: IGraph<G, E, V>, E: IEdge<G, E, V>, V: IVertex<G, E, V> {
  @JsName("G0") val G: (vertices: Set<V>) -> G // Graph constructor
  @JsName("E0") val E: (s: V, t: V) -> E // Edge constructor
  @JsName("V0") val V: (old: V, edgeMap: (V) -> Set<E>) -> V // Vertex constructor
  fun V(out: Set<V>): V = TODO("Must override me if you want a fresh vertex")

  val deepHashCode: Long
  @JsName("G1") fun G() = G(setOf())
  @JsName("G2") fun G(vararg graphs: G): G = G(graphs.toList())
  @JsName("G3") fun G(vararg vertices: V): G = G(vertices.map { it.graph })
  @JsName("G4") fun G(list: List<Any>): G = when {
    list.isEmpty() -> setOf()
    list allAre G() -> list.fold(G()) { it, acc -> it + acc as G }
    list allAre list.first() -> list.map { it as V }.toSet()
    else -> throw Exception("Unsupported constructor: G(${list.joinToString(",") { it::class.simpleName!! }})")
  }.let { G(it) }
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

  // TODO: Move the following ceremony into named tensor
  //-------
  operator fun get(cond: (V) -> Boolean): Set<V> = vertices.filter(cond)
  val index: VIndex<G, E, V> get() = VIndex(vertices)
  operator fun get(vertexIdx: Int): V = index[vertexIdx]
  class VIndex<G: IGraph<G, E, V>, E : IEdge<G, E, V>, V : IVertex<G, E, V>>(val set: Set<V>) {
    val array: List<V> = set.toList()
    val map: Map<V, Int> = array.mapIndexed { index, a -> a to index }.toMap()
    //    operator fun get(it: IVertex<G, E, V>): Int? = map[it]
    operator fun get(it: Int): V = array[it]
    operator fun get(v: V): Int = map[v] ?: -1
  }
//  operator fun SpsMat.get(n0: V, n1: V) = this[index[n0]!!, index[n1]!!]
//  operator fun SpsMat.set(n0: V, n1: V, value: Double) {
//    this[index[n0]!!, index[n1]!!] = value
//  }
  //-------

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

  fun reachSequence(from: Set<V>, ADJ: BooleanMatrix = A_AUG, terminateOnFixpoint: Boolean = false): Sequence<Set<V>> =
    sequence {
      var B = BooleanMatrix(vertices.size, 1, vertices.map { it in from })
      while (true) {
        // Check if fixpoint reached
        val OLD_B = B
        B = ADJ * B
        val toYield = B.data.mapIndexed { i, b -> if (b) index[i] else null }.filterNotNull().toSet()
        val same = B == OLD_B
        if (same && terminateOnFixpoint) break
        else if(same) while(true) { yield(toYield) }
        else yield(toYield)
      }
    }

  fun reachability(from: Set<V>, steps: Int): Set<V> =
    (A_AUG.pow(steps - 1) * BooleanMatrix(vertices.size, 1, vertices.map { it in from }).also { println("v: ${it.shape()}") }).data
      .mapIndexed { i, b -> if (b) index[i] else null }.filterNotNull().toSet()

  fun transitiveClosure(vtxs: Set<V>): Set<V>  =
    // edges.filter { it.source in vtxs }.map { it.target }
    // TODO: Why does this work but the previous line does not?!
    (edgList.filter { it.first in vtxs }.map { it.second.target }.toSet() - vtxs)
      .let { if (it.isEmpty()) vtxs else transitiveClosure(vtxs + it) }

  fun randomWalk(r: Random = Random.Default) = RandomWalk(r, this as G)

  fun asString() =
    edgList.map { "${it.first} -> ${it.second.target}" }.formatAsGrid().toString()

  fun toDot(highlight: Set<V> = setOf()): String {
    fun String.htmlify() =
      replace("<", "&lt;").replace(">", "&gt;")
    return """
      strict digraph {
          graph ["concentrate"="false","rankdir"="LR","bgcolor"="transparent","margin"="0.0","compound"="true","nslimit"="20"]
          ${
      vertices.joinToString("\n") {
        """"${it.id.htmlify()}" ["shape"="Mrecord","color"="black","fontcolor"="black","fontname"="JetBrains Mono","fontsize"="15","penwidth"="2.0"${if(it in highlight)""","fillcolor"=lightgray,"style"=filled""" else ""}]""" }
          } 
          ${edgList.joinToString("\n") { (v, e) -> 
            val (src, tgt) = v.id.htmlify() to e.target.id.htmlify()
        """"$src" -> "$tgt" ["color"="${ if (v is LGVertex && v.occupied) "red" else "black" }","fontname"="JetBrains Mono","arrowhead"="normal","penwidth"="2.0","label"="${(e as? LabeledEdge)?.label ?: ""}"]""" }
          }
      }
    """.trimIndent()
  }
}

val <G: IGraph<G, E, V>, E: IEdge<G, E, V>, V: IVertex<G, E, V>> IGraph<G, E, V>.D: DoubleMatrix         by cache { DoubleMatrix(size) { i, j -> if (i == j) this[i].neighbors.size.toDouble() else 0.0 } }

// Adjacency matrix
val <G: IGraph<G, E, V>, E: IEdge<G, E, V>, V: IVertex<G, E, V>> IGraph<G, E, V>.A: BooleanMatrix        by cache { BooleanMatrix(size) { i, j -> this[j] in this[i].neighbors } }
val <G: IGraph<G, E, V>, E: IEdge<G, E, V>, V: IVertex<G, E, V>> IGraph<G, E, V>.A_AUG: BooleanMatrix    by cache { A + A.transpose + BooleanMatrix.one(size) }

// Symmetric normalized adjacency
val <G: IGraph<G, E, V>, E: IEdge<G, E, V>, V: IVertex<G, E, V>> IGraph<G, E, V>.ASYMNORM: DoubleMatrix  by cache { vwise { v, n -> 1.0 / sqrt(v.outdegree.toDouble() * n.outdegree.toDouble()) } }

// Graph Laplacian matrix
val <G: IGraph<G, E, V>, E: IEdge<G, E, V>, V: IVertex<G, E, V>> IGraph<G, E, V>.L: DoubleMatrix         by cache { D - A }
val <G: IGraph<G, E, V>, E: IEdge<G, E, V>, V: IVertex<G, E, V>> IGraph<G, E, V>.I: DoubleMatrix         by cache { DoubleMatrix(size, size, ::kroneckerDelta) }
// Symmetric normalized Laplacian
val <G: IGraph<G, E, V>, E: IEdge<G, E, V>, V: IVertex<G, E, V>> IGraph<G, E, V>.LSYMNORM: DoubleMatrix  by cache { I - ASYMNORM }

val <G: IGraph<G, E, V>, E: IEdge<G, E, V>, V: IVertex<G, E, V>> IGraph<G, E, V>.ENCODED: DoubleMatrix   by cache { vertices.map { it.encode() }.toTypedArray().toDoubleMatrix() }

// TODO: Implement APSP distance matrix using algebraic Floyd-Warshall
//       https://doi.org/10.1137/1.9780898719918.ch5

// All pairs shortest path
val <G: IGraph<G, E, V>, E: IEdge<G, E, V>, V: IVertex<G, E, V>> IGraph<G, E, V>.APSP: Map<Pair<V, V>, Int>     by cache {
  val dist = mutableMapOf<Pair<V, V>, Int>()
  for ((u, v) in vertices * vertices) {
      dist[v to u] = if (v == u) 0 else Int.MAX_VALUE
  }
  for (e in adjList) { dist[e.first to e.second] = 1 }
  while (true) {
    var done = true
    for ((k, i, j) in vertices * vertices * vertices) {
      if (dist[i to k]!! < Int.MAX_VALUE && dist[k to j]!! < Int.MAX_VALUE) {
        val newDist = dist[i to k]!! + dist[k to j]!!
        if (newDist < dist[i to j]!!) { dist[i to j] = newDist; done = false }
      }
    }
    if (done) break
  }
  dist
}

// States, in a topological order (using BFS / Kahn's algorithm)
// TODO: implement this using min-plus semiring: https://en.wikipedia.org/wiki/Topological_sorting#Parallel_algorithms
// Behavior is undefined when the graph contains cycles, so be sure to only call this on acyclic graphs
fun <G: IGraph<G, E, V>, E: IEdge<G, E, V>, V: IVertex<G, E, V>> IGraph<G, E, V>.topSort(): List<V> {
  // 1. Build in-degree map
  val inDegree = vertices.associateWith { 0 }.toMutableMap()

  val transit = vertices.associateWith { it.outgoing.toSet() }
  // For every outgoing edge (s -> t), increment in-degree of t
  for ((s, edges) in transit) {
    for ((_, t) in edges) {
      inDegree[t] = inDegree[t]?.plus(1) ?: 1
    }
  }

  // 2. Initialize queue with states whose in-degree is zero
  val queue = ArrayDeque(inDegree.filterValues { it == 0 }.keys)
  val order = mutableListOf<V>()

  // 3. Repeatedly pop from queue and update in-degree of successors
  while (queue.isNotEmpty()) {
    val s = queue.removeFirst()
    order.add(s)

    // Decrement in-degree for all s -> t
    for ((_, t) in transit[s].orEmpty()) {
      val deg = inDegree[t]!!.minus(1)
      inDegree[t] = deg
      if (deg == 0) queue.addLast(t)
    }
  }

  return order
}

// AllPairs[p, q] is the set of all vertices, r, such that p ->* r ->* q
val <G: IGraph<G, E, V>, E: IEdge<G, E, V>, V: IVertex<G, E, V>> IGraph<G, E, V>.allPairs: Map<Pair<V, V>, Set<V>> by cache {
  // All vertices reachable from v
  val forward: Map<V, Set<V>> = vertices.associateWith { v -> transitiveClosure(setOf(v)) }

  // AAll vertices that can reach v (reachable from v in reversed graph)
  val backward: Map<V, Set<V>> = reversed().let { it.vertices.associateWith { v -> it.transitiveClosure(setOf(v)) } }

  // For every pair (p, q), collect all vertices r that lie on some path p ->* r ->* q
  vertices.flatMap { p -> vertices.map { q -> Pair(Pair(p, q), (forward[p]!! intersect backward[q]!!)) } }
    .filter { it.second.isNotEmpty() }.toMap()
}

val <G: IGraph<G, E, V>, E: IEdge<G, E, V>, V: IVertex<G, E, V>> IGraph<G, E, V>.degMap: Map<V, Int>     by cache { vertices.associateWith { it.neighbors.size } }
val <G: IGraph<G, E, V>, E: IEdge<G, E, V>, V: IVertex<G, E, V>> IGraph<G, E, V>.edges: Set<E>           by cache { edgMap.values.flatten().toSet() }
val <G: IGraph<G, E, V>, E: IEdge<G, E, V>, V: IVertex<G, E, V>> IGraph<G, E, V>.edgList: List<Π2<V, E>> by cache { vertices.flatMap { s -> s.outgoing.map { s to it } } }
val <G: IGraph<G, E, V>, E: IEdge<G, E, V>, V: IVertex<G, E, V>> IGraph<G, E, V>.adjList: AdjList<V>     by cache { edgList.map { (v, e) -> v cc e.target } }
val <G: IGraph<G, E, V>, E: IEdge<G, E, V>, V: IVertex<G, E, V>> IGraph<G, E, V>.edgMap: Map<V, Set<E>>  by cache { vertices.associateWith { it.outgoing } }
val <G: IGraph<G, E, V>, E: IEdge<G, E, V>, V: IVertex<G, E, V>> IGraph<G, E, V>.histogram: Map<V, Int>  by cache { associateWith { it.neighbors.size } }

val cache = LRUCache<String, Any>()

// If you see a JS error get_first_irdx8n_k, DEPTH is set incorrectly or something is funny with the stacktrace
object PlatformVars { var PLATFORM_CALLER_STACKTRACE_DEPTH: Int = 3 }
// This is somewhat of a hack and may break depending on the platform.
// We do this because Kotlin Common has poor reflection capabilities.
fun getCaller() = Throwable().stackTraceToString()
  .lines()[PlatformVars.PLATFORM_CALLER_STACKTRACE_DEPTH].hashCode()

// Lazily evaluates and caches result for later use, until cache expiry,
// after which said value will be reevaluated and cached if it is needed
// again. If you believe there may be a bug here, it is really important
// to first check hashCode() / deepHashCode - we expect it to be unique!
// We use this to materialize properties that are expensive to compute,
// and that we expect to be used multiple times once computed.

// The advantage of using the cache { ... } pattern versus lazy { ... }
// is that it allows us to do the following:
// typealias TQ = List<String>
// val TQ.hello by cache { "Hello" }
// val TQ.world by cache { hello + " world" }
// Whereas this is not possible with lazy { ... }:
// typealias TQ = List<String>
// val TQ.hello by lazy { "Hello" }
// val TQ.world by lazy { hello + " world" } // Fails
// It also allows us to add persistent properties to interfaces, see:
// https://stackoverflow.com/questions/43476811/can-a-kotlin-interface-cache-a-value/71632459#71632459

fun <T, Y> cache(caller: Int = getCaller(), fn: Y.() -> T) =
  ReadOnlyProperty<Y, T> { y, _ ->
    val id = if (y is IGF<*, *, *>) y.deepHashCode else y.hashCode()
    val csg = "$id$caller"
//    val csg = "${y!!::class.simpleName}${id}$caller"
    (cache.getOrPut(csg) { y.fn() as Any } as T)
//    .also { println("$id :: $caller :: $it") }
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
  val source: V
  val target: V

  operator fun component1() = source
  operator fun component2() = target
}

val <G: IGraph<G, E, V>, E: IEdge<G, E, V>, V: IVertex<G, E, V>> IEdge<G, E, V>.graph: G by cache { target.graph }

// TODO: Make this a "view" of the container graph
interface IVertex<G, E, V> : IGF<G, E, V>, Encodable
        where G : IGraph<G, E, V>, E : IEdge<G, E, V>, V : IVertex<G, E, V> {
  val id: String // TODO: Need to think about this more carefully

  val edgeMap: (V) -> Set<E> // Make a self-loop by passing this

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

val <G: IGraph<G, E, V>, E: IEdge<G, E, V>, V: IVertex<G, E, V>> IVertex<G, E, V>.graph: G          by cache { G(neighbors(-1)) }
val <G: IGraph<G, E, V>, E: IEdge<G, E, V>, V: IVertex<G, E, V>> IVertex<G, E, V>.incoming: Set<E>  by cache { graph.reversed().edgMap[this] ?: emptySet() }
val <G: IGraph<G, E, V>, E: IEdge<G, E, V>, V: IVertex<G, E, V>> IVertex<G, E, V>.outgoing: Set<E>  by cache { edgeMap(this as V).toSet() }
val <G: IGraph<G, E, V>, E: IEdge<G, E, V>, V: IVertex<G, E, V>> IVertex<G, E, V>.neighbors: Set<V> by cache { outgoing.map { it.target }.toSet() }
val <G: IGraph<G, E, V>, E: IEdge<G, E, V>, V: IVertex<G, E, V>> IVertex<G, E, V>.outdegree: Int get() = neighbors.size


abstract class AGF<G, E, V> : IGF<G, E, V>
  where G : IGraph<G, E, V>, E : IEdge<G, E, V>, V : IVertex<G, E, V> {
  override val deepHashCode: Long = Random.nextLong()
  override fun hashCode() = deepHashCode.toInt()
}

abstract class Graph<G, E, V>(override val vertices: Set<V> = setOf()) :
  AGF<G, E, V>(), IGraph<G, E, V>, Set<V> by vertices
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
    val hash by lazy { id.hashCode() }
  override fun equals(other: Any?) = (other as? Vertex<*, *, *>)?.let { id == it.id } ?: false
  override fun encode() = id.vectorize()
  override fun hashCode() = hash
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