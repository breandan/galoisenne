package edu.mcgill.kaliningraph

import java.util.*


class Vertex(val id: String = randomString(), edgeMap: (Vertex) -> Collection<Edge>) {
  constructor(id: String = randomString(), out: Set<Vertex> = emptySet()) :
    this(id, { out.map { Edge(it) } })
  constructor(out: Set<Vertex> = emptySet()) :
    this(randomString(), { out.map { Edge(it) } })

  companion object {
    fun randomString() = UUID.randomUUID().toString().take(5)
  }

  val edges = edgeMap(this).toSet()
  val neighbors: Set<Vertex> = edges.map { it.target }.toSet()

  tailrec fun neighbors(k: Int = 0, vertices: Set<Vertex> = neighbors + this): Set<Vertex> =
    if (k == 0 || vertices.neighbors() == vertices) vertices
    else neighbors(k - 1, vertices + vertices.neighbors() + this)

  // Removes all edges pointing outside the set
  private fun Set<Vertex>.closure() = map { vertex ->
    Vertex(vertex.id) { vertex.edges.filter { it.target in this@closure } }
  }.toSet()

  private fun Set<Vertex>.neighbors() = flatMap { it.neighbors() }.toSet()

  fun asGraph() = Graph(neighbors(-1))
  fun egoGraph() = Graph(neighbors(0).closure())

  override fun equals(other: Any?) = (other as? Vertex)?.id == id
  override fun hashCode() = id.hashCode()
  override fun toString() = id
}