package edu.mcgill.kaliningraph.typefamily

import edu.mcgill.kaliningraph.*

// Inheritable constructors
interface IGF<G, E, V>
  where G: IGraph<G, E, V>, E: IEdge<G, E, V>, V: IVertex<G, E, V> {
  // Inheritors must implement these three
  fun Vertex(newId: String, edgeMap: (V) -> Set<E>): V
  fun Graph(vertices: Set<V> = setOf()): G
  fun Edge(s: V, t: V): E

  fun Graph(vararg graphs: G): G = Graph(graphs.toList())
  fun Graph(vararg vertices: V): G = Graph(vertices.map { it.graph })
  fun Graph(graphs: List<G>): G =
    Graph(graphs.fold(Graph()) { it, acc -> it + acc }.vertices)

  fun Vertex(newId: String, out: Set<V> = emptySet()): V =
    Vertex(newId) { s -> out.map { t -> Edge(s, t) }.toSet() }

  fun <T> Graph(
    vararg adjList: Pair<T, T>,
    toVertex: (Pair<T, T>) -> V =
      { (s, t) -> Vertex(s.toString(), setOf(Vertex(t.toString()))) }
  ): G = adjList.map { toVertex(it) }.fold(Graph()) { acc, v -> acc + v.graph }

  fun Graph(graph: String) = graph.split(" ").map {
    Graph(*it.zipWithNext().map { (c1, c2) -> "$c1" to "$c2" }.toTypedArray())
  }.fold(Graph()) { acc, g -> acc + g }.reversed()
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

  // Implements graph merge. For all vertices in common, merge their neighbors.
  // TODO: Figure out how to implement this operator "correctly"
  // https://github.com/snowleopard/alga-paper/releases/download/final/algebraic-graphs.pdf
  operator fun plus(that: G): G =
    Graph((this - that) + (this join that) + (that - this))

  operator fun minus(graph: G): G = Graph(vertices - graph.vertices)

  infix fun join(that: G): Set<V> =
    (vertices intersect that.vertices).sortedBy { it.id }.toSet()
      .zip((that.vertices intersect vertices).sortedBy { it.id }.toSet())
      .map { (left, right) -> Vertex(left.id) { left.outgoing + right.outgoing } }
      .toSet()

  // TODO: Reimplement using matrix transpose
  fun reversed(): G = Graph(
    (vertices.associateWith { setOf<E>() } +
      vertices.flatMap { src ->
        src.outgoing.map { edge -> edge.target to Edge(edge.target, src) }
      }.groupBy({ it.first }, { it.second }).mapValues { (_, v) -> v.toSet() })
      .map { (k, v) -> Vertex(k.id) { v } }.toSet()
  )
}

interface IEdge<G, E, V>: IGF<G, E, V>
  where G: IGraph<G, E, V>, E: IEdge<G, E, V>, V: IVertex<G, E, V> {
  val graph: G
  val source: V
  val target: V
}

interface IVertex<G, E, V>: IGF<G, E, V>
  where G: IGraph<G, E, V>, E: IEdge<G, E, V>, V: IVertex<G, E, V> {
  val id: String
  val graph: G
  val incoming: Set<E>
  val outgoing: Set<E>
  val edgeMap: (V) -> Collection<E> // Make a self-loop by passing this
}

// https://github.com/amodeus-science/amod
//abstract class Map : IGraph<Map, Road, City>
//abstract class Road : IEdge<Map, Road, City>
//abstract class City : IVertex<Map, Road, City>