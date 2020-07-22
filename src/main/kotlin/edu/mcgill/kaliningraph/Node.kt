package edu.mcgill.kaliningraph

import kotlin.reflect.KProperty

interface Node<T: Node<T, E>, E: Edge<E, T>> {
  fun new(id: String? = randomString(), out: Set<T> = emptySet()): T
  fun new(id: String = randomString(), edgeMap: (T) -> Collection<Edge<E, T>>): T

  val id: String
  val edges: Set<Edge<E, T>>
  val neighbors: Set<T>
  var occupied: Boolean

  tailrec fun neighbors(k: Int = 0, vertices: Set<T> = neighbors + this as T): Set<T> =
    if (k == 0 || vertices.neighbors() == vertices) vertices
    else neighbors(k - 1, vertices + vertices.neighbors() + this as T)

  // Removes all edges pointing outside the set
  private fun Set<T>.closure(): Set<T> =
    map { vertex ->
      vertex.new(vertex.id) { vertex.edges.filter { it.target in this } }
    }.toSet()

  private fun Set<T>.neighbors(): Set<T> = flatMap { it.neighbors() }.toSet()

  fun asGraph() = Graph(neighbors(-1))
  fun neighborhood() = Graph(neighbors(0).closure())

  operator fun getValue(a: Any?, prop: KProperty<*>): T = new(prop.name)
}

class Vertex(
  override val id: String = randomString(),
  edgeMap: (Vertex) -> Collection<Edge<LabeledEdge, Vertex>>
): Node<Vertex, LabeledEdge> {
  constructor(id: String? = randomString(), out: Set<Vertex> = emptySet()) :
    this(id ?: randomString(), { out.map { LabeledEdge(it) } })

  constructor(out: Set<Vertex> = setOf()) : this(randomString(), { out.map { LabeledEdge(it) } })

  override var occupied = false
  override val edges by lazy { edgeMap(this).toSet() }
  override val neighbors by lazy { edges.map { it.target }.toSet() }

  override fun equals(other: Any?) = (other as? Vertex)?.id == id
  override fun hashCode() = id.hashCode()
  override fun toString() = id
  override fun new(id: String?, out: Set<Vertex>) = Vertex(id, out)
  override fun new(id: String, edgeMap: (Vertex) -> Collection<Edge<LabeledEdge, Vertex>>) = Vertex(id, edgeMap)
}
