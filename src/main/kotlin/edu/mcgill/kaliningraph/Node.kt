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

  fun asGraph() = Graph<T, E>(neighbors(-1))
  fun neighborhood() = Graph<T, E>(neighbors(0).closure())

  operator fun getValue(a: Any?, prop: KProperty<*>): T = new(prop.name)
}

class Vertex<E: Edge<E, Vertex<E>>>(
  override val id: String = randomString(),
  edgeMap: (Vertex<E>) -> Collection<Edge<E, Vertex<E>>>
): Node<Vertex<E>, E> {
  constructor(id: String? = randomString(), out: Set<Vertex<E>> = emptySet()) :
    this(id ?: randomString(), { out.map { Edge<E, Vertex<E>>(it) } })

  constructor(out: Set<Vertex<E>> = setOf()) : this(randomString(), { out.map { Edge<E, Vertex<E>>(it) } })

  override var occupied = false
  override val edges by lazy { edgeMap(this).toSet() }
  override val neighbors by lazy { edges.map { it.target }.toSet() }

  override fun equals(other: Any?) = (other as? Vertex<E>)?.id == id
  override fun hashCode() = id.hashCode()
  override fun toString() = id
  override fun new(id: String, edgeMap: (Vertex<E>) -> Collection<Edge<E, Vertex<E>>>) = Vertex(id, edgeMap)
  override fun new(id: String?, out: Set<Vertex<E>>) = Vertex(id, out)
}
