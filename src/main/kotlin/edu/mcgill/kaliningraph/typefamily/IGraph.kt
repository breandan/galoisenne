package edu.mcgill.kaliningraph.typefamily

// https://www.cs.cornell.edu/~ross/publications/shapes/shapes-pldi14-tr.pdf#page=3

interface IGraph<G: IGraph<G, E, V>, E: IEdge<G, E, V>, V: IVertex<G, E, V>> {
  val vertices: Set<V>

  // TODO: Possible to use typeclass here? https://kotlin.christmas/2020/7
  fun new(vararg graphs: G): G = new(graphs.toList())
  fun new(vararg vertices: V): G = new(vertices.map { it.graph })
  fun new(graphs: List<G>): G = new(graphs.fold(new()) { it, acc -> it + acc }.vertices)
  fun new(adjList: kotlin.collections.Map<V, Set<E>>): G = new(adjList.map { (k, v) -> k.Vertex { v } }.toSet())
  fun new(vertices: Set<V> = setOf()): G

  operator fun plus(that: G): G
}

interface IEdge<G: IGraph<G, E, V>, E: IEdge<G, E, V>, V: IVertex<G, E, V>> {
  val graph: G
  val source: V
  val target: V

  fun new(source: V, target: V): E
}

interface IVertex<G: IGraph<G, E, V>, E: IEdge<G, E, V>, V: IVertex<G, E, V>> {
  val id: String
  val graph: G
  val incoming: Set<E>
  val outgoing: Set<E>

  fun Vertex(newId: String = id, edgeMap: (V) -> Set<E>): V
  fun Vertex(newId: String = id, out: Set<V> = emptySet()): V =
    Vertex(newId) { s -> out.map { t -> Edge(s, t) }.toSet() }
  fun Graph(vertices: Set<V>): G
  fun Edge(s: V, t: V): E
}

// https://github.com/amodeus-science/amod
abstract class Map : IGraph<Map, Road, City>
abstract class Road : IEdge<Map, Road, City>
abstract class City : IVertex<Map, Road, City>