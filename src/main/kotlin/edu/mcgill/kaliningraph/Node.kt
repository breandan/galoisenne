package edu.mcgill.kaliningraph

import java.util.*
import kotlin.reflect.KProperty

abstract class Node<T: Node<T>> {
  companion object {
    fun randomString() = UUID.randomUUID().toString().take(5)
  }

  abstract fun new(id: String? = randomString(), out: Set<T> = emptySet()): T
  abstract fun new(id: String = randomString(), edgeMap: (T) -> Collection<Edge<T>>): T

  abstract val id: String
  abstract val edges: Set<Edge<T>>
  abstract val neighbors: Set<T>
  abstract var occupied: Boolean

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

  override fun equals(other: Any?) = (other as? T)?.id == id
  override fun hashCode() = id.hashCode()
  override fun toString() = id
  operator fun getValue(a: Any?, prop: KProperty<*>): T = new(prop.name)
}

class Vertex(
  override val id: String = randomString(),
  edgeMap: (Vertex) -> Collection<Edge<Vertex>>
): Node<Vertex>() {
  constructor(id: String? = randomString(), out: Set<Vertex> = emptySet()) :
    this(id ?: randomString(), { out.map { Edge<Vertex>(it) } })

  constructor(out: Set<Vertex> = setOf()) : this(randomString(), { out.map { Edge<Vertex>(it) } })

  override var occupied = false
  override val edges by lazy { edgeMap(this).toSet() }
  override val neighbors by lazy { edges.map { it.target }.toSet() }

  override fun new(id: String, edgeMap: (Vertex) -> Collection<Edge<Vertex>>) = Vertex(id, edgeMap)
  override fun new(id: String?, out: Set<Vertex>) = Vertex(id, out)
}
