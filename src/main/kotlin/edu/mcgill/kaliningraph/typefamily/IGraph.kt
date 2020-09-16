package edu.mcgill.kaliningraph.typefamily

// https://www.cs.cornell.edu/~ross/publications/shapes/shapes-pldi14-tr.pdf#page=3

interface IGraph<G: IGraph<G, E, V>, E: IEdge<G, E, V>, V: IVertex<G, E, V>> {
  val vertices: Set<V>
}

interface IEdge<G: IGraph<G, E, V>, E: IEdge<G, E, V>, V: IVertex<G, E, V>> {
  val graph: G
  val source: V
  val target: V
}

interface IVertex<G: IGraph<G, E, V>, E: IEdge<G, E, V>, V: IVertex<G, E, V>> {
  val graph: G
  val incoming: Set<E>
  val outgoing: Set<E>
}

// https://github.com/amodeus-science/amod
abstract class Map : IGraph<Map, Road, City>
abstract class Road : IEdge<Map, Road, City>
abstract class City : IVertex<Map, Road, City>