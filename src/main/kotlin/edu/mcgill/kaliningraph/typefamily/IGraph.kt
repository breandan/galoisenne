package edu.mcgill.kaliningraph.typefamily

import edu.mcgill.kaliningraph.*
import guru.nidi.graphviz.attribute.Label
import guru.nidi.graphviz.model.*
import java.lang.reflect.ParameterizedType
import kotlin.reflect.*

// Reified constructors
@Suppress("FunctionName")
/**
 * TODO: can we lift builders somehow? e.g.:
 * [edu.mcgill.kaliningraph.LGBuilder]
 * [edu.mcgill.kaliningraph.TypedGraphBuilder]
 * [edu.mcgill.kaliningraph.automata.AutomatonBuilder]
 * [edu.mcgill.kaliningraph.circuits.CircuitBuilder]
 */
interface IGF<G: IGraph<G, E, V>, E: IEdge<G, E, V>, V: IVertex<G, E, V>> {
  fun G(vertices: Set<V> = setOf()): G = g().newInstance(vertices) as G

  fun E(s: V, t: V): E = e().newInstance(s, t) as E

  fun V(newId: String = "", edgeMap: (V) -> Set<E>): V =
    v().newInstance(newId, edgeMap) as V

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

  fun <T: Any> G(list: List<T>): G = G(
    when {
      list.isEmpty() -> setOf()
      list allAre G() -> G(list.fold(G()) { it, acc -> it + acc as G }.vertices)
      list allAre V() -> G(list.map { it as V }.toSet())
      list anyAre IGF::class -> list.first { it is IGF<*, *, *> }
        .let { throw Exception("Unsupported: Graph(${it::class.java})") }
      else -> G(*list.toList().zipWithNext().toTypedArray())
    }
  )

  // TODO: generify, only works for labeled graphs
  fun G(graph: String): G =
    graph.split(" ").fold(G()) { acc, it -> acc + G(it.toCharArray().toList()) }

  // Gafter's gadget! http://gafter.blogspot.com/2006/12/super-type-tokens.html
  private fun gev(): Array<Class<*>> =
    (generateSequence(javaClass) { it.superclass as Class<IGF<G, E, V>> }
      .first { it.genericSuperclass is ParameterizedType }
      .genericSuperclass as ParameterizedType).actualTypeArguments
      .map { (if (it is ParameterizedType) it.rawType else it) as Class<*> }
      .toTypedArray()

  private fun g() = gev()[0].getConstructor(Set::class.java)
  private fun e() = gev().let { it[1].getConstructor(it[2], it[2]) }
  private fun v() = gev()[2].getConstructor(String::class.java, Function1::class.java)
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
 * Algebraic perspective    : https://github.com/snowleopard/alga-paper/releases/download/final/algebraic-graphs.pdf
 * Type-family perspective  : https://www.cs.cornell.edu/~ross/publications/shapes/shapes-pldi14-tr.pdf#page=3
 * Inductive perspective    : https://web.engr.oregonstate.edu/~erwig/papers/InductiveGraphs_JFP01.pdf
 * Mathematical perspective : https://doi.org/10.1007/978-0-387-75450-5
 * Semiring perspective     : http://stedolan.net/research/semirings.pdf
 */

  where G: IGraph<G, E, V>, E: IEdge<G, E, V>, V: IVertex<G, E, V> {
  val vertices: Set<V>
  val edgList: List<Pair<V, E>> get() = vertices.flatMap { s -> s.outgoing.map { s to it } }
  val adjList: List<Pair<V, V>> get() = edgList.map { (v, e) -> v to e.target }
  val edgMap: Map<V, Set<E>> get() = vertices.associateWith { it.outgoing }
  val edges: Set<E> get() = edgMap.values.flatten().toSet()

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
  fun reversed(): G = G(
    (vertices.associateWith { setOf<E>() } +
      vertices.flatMap { src ->
        src.outgoing.map { edge -> edge.target to E(edge.target, src) }
      }.groupBy({ it.first }, { it.second }).mapValues { (_, v) -> v.toSet() })
      .map { (k, v) -> V(k.id) { v } }.toSet()
  )

  fun isomorphicTo(that: G) =
    this.size == that.size &&
      edges.size == that.edges.size &&
      hashCode() == that.hashCode()

  fun render() = toGraphviz()
}

interface IEdge<G, E, V>: IGF<G, E, V>
  where G: IGraph<G, E, V>, E: IEdge<G, E, V>, V: IVertex<G, E, V> {
  val graph: G
  val source: V
  val target: V

  fun render(): Link = (source.render() - target.render()).add(Label.of(""))
}

// TODO: Make this a "view" of the container graph
interface IVertex<G, E, V>: IGF<G, E, V>, Encodable
  where G: IGraph<G, E, V>, E: IEdge<G, E, V>, V: IVertex<G, E, V> {
  val id: String
  val graph: G get() = G(neighbors(-1))
  val incoming: Set<E> get() = graph.reversed().edgMap[this] ?: emptySet()
  val outgoing: Set<E> get() = edgeMap(this as V).toSet()
  val edgeMap: (V) -> Set<E> // Make a self-loop by passing this

  open val neighbors get() = outgoing.map { it.target }.toSet()
  open val outdegree get() = neighbors.size

  // tailrec prohibited on open members? may be possible with deep recursion
  // https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-deep-recursive-function/
  fun neighbors(k: Int = 0, vertices: Set<V> = neighbors + this as V): Set<V> =
    if (k == 0 || vertices.neighbors() == vertices) vertices
    else neighbors(k - 1, vertices + vertices.neighbors() + this as V)

  // Removes all edges pointing outside the set
  private fun Set<V>.closure(): Set<V> =
    map { vertex -> V(id) { vertex.outgoing.filter { it.target in this }.toSet() } }.toSet()

  private fun Set<V>.neighbors(): Set<V> = flatMap { it.neighbors() }.toSet()

  fun neighborhood(): G = G(neighbors(0).closure())

  override fun encode(): DoubleArray = id.vectorize()
  operator fun getValue(a: Any?, prop: KProperty<*>): V = V(prop.name)
  fun render(): MutableNode = Factory.mutNode(id).add(Label.of(toString()))
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
//abstract class Map : IGraph<Map, Road, City>
//abstract class Road : IEdge<Map, Road, City>
//abstract class City : IVertex<Map, Road, City>