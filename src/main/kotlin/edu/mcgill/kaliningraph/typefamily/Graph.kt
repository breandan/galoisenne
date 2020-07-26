package edu.mcgill.kaliningraph.typefamily

interface Graph<G: Graph<G, E, V>, E: Edge<G, E, V>, V: Vertex<G, E, V>> {
  val vertices: List<V>
}

interface Edge<G: Graph<G, E, V>, E: Edge<G, E, V>, V: Vertex<G, E, V>> {
  val source: V
  val target: V
}

interface Vertex<G: Graph<G, E, V>, E: Edge<G, E, V>, V: Vertex<G, E, V>> {
  val graph: G
  val incoming: List<E>
  val outgoing: List<E>
}

abstract class Map : Graph<Map, Road, City>
abstract class Road : Edge<Map, Road, City>
abstract class City : Vertex<Map, Road, City>