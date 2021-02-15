package edu.mcgill.kaliningraph.typefamily

interface IGraph<G, E, V>: Set<V>, (V) -> Set<V>
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

  // TODO: Possible to use typeclass here? https://kotlin.christmas/2020/7
  fun new(vararg graphs: G): G = new(graphs.toList())
  fun new(vararg vertices: V): G = new(vertices.map { it.graph })
  fun new(graphs: List<G>): G =
    new(graphs.fold(new()) { it, acc -> it + acc }.vertices)
  fun new(vertices: Set<V> = setOf()): G

//  companion object {
//    operator fun <G: IGraph<G, E, V>, E: IEdge<G, E, V>, V: IVertex<G, E, V>> invoke(
//      graph: (Set<V>) -> G,
//      edge: (V, V) -> E,
//      vertex: (Set<V>) -> V
//    ) =
//      object: IGraph<G, E, V> {
//
//      }
//  }

//  fun Vertex(newId: String = randomString(), edgeMap: (V) -> Set<E>): V
//  fun Graph(vertices: Set<V>): G
//  fun Edge(s: V, t: V): E

  // Implements graph merge. For all vertices in common, merge their neighbors.
  // TODO: Figure out how to implement this operator "correctly"
  // https://github.com/snowleopard/alga-paper/releases/download/final/algebraic-graphs.pdf
  operator fun plus(that: G): G =
    new((this - that) + (this join that) + (that - this))

  operator fun minus(graph: G): G = new(vertices - graph.vertices)

  infix fun join(that: G): Set<V> =
    (vertices intersect that.vertices).sortedBy { it.id }.toSet()
      .zip((that.vertices intersect vertices).sortedBy { it.id }.toSet())
      .map { (left, right) -> left.Vertex { left.outgoing + right.outgoing } }
      .toSet()

  // TODO: Reimplement using matrix transpose
  fun reversed(): G = new(
    (vertices.associateWith { setOf<E>() } +
      vertices.flatMap { src ->
        src.outgoing.map { edge -> edge.target to edge.new(edge.target, src) }
      }.groupBy({ it.first }, { it.second }).mapValues { (_, v) -> v.toSet() })
      .map { (k, v) -> k.Vertex { v } }.toSet()
  )
}

interface IEdge<G, E, V>
  where G: IGraph<G, E, V>, E: IEdge<G, E, V>, V: IVertex<G, E, V> {
  val graph: G
  val source: V
  val target: V

  fun new(source: V, target: V): E
}

interface IVertex<G, E, V>
  where G: IGraph<G, E, V>, E: IEdge<G, E, V>, V: IVertex<G, E, V> {
  val id: String
  val graph: G
  val incoming: Set<E>
  val outgoing: Set<E>
  val edgeMap: (V) -> Collection<E> // Make a self-loop by passing this

  fun Vertex(newId: String = id, edgeMap: (V) -> Set<E>): V
  fun Vertex(newId: String = id, out: Set<V> = emptySet()): V =
    Vertex(newId) { s -> out.map { t -> Edge(s, t) }.toSet() }

  fun Graph(vertices: Set<V>): G
  fun Edge(s: V, t: V): E
}

// https://github.com/amodeus-science/amod
//abstract class Map : IGraph<Map, Road, City>
//abstract class Road : IEdge<Map, Road, City>
//abstract class City : IVertex<Map, Road, City>