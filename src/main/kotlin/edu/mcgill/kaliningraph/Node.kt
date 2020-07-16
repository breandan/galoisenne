package edu.mcgill.kaliningraph

import java.util.*
import kotlin.reflect.KProperty

open class Node<T: Node<T>> constructor (open val id: String = randomString(),
                            open var occupied: Boolean = false, edgeMap: (T) -> Collection<Edge<T>>) {
  constructor(id: String? = randomString(), out: Set<T> = emptySet()) :
    this(id ?: randomString(), false, { out.map { Edge<T>(it) } })

  constructor(out: Set<T> = setOf()) : this(randomString(), false, { out.map { Edge<T>(it) } })

  companion object {
    fun randomString() = UUID.randomUUID().toString().take(5)
  }

  open fun new(id: String? = randomString(), out: Set<T> = emptySet()) = Node(id, out) as T
  open fun new(id: String = randomString(), edgeMap: (T) -> Collection<Edge<T>>): T = Node(id, false, edgeMap) as T

  open val edges by lazy { edgeMap(this as T).toSet() }
  open val neighbors by lazy { edges?.map { it.target }.toSet() }

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

class Vertex(id: String? = randomString(), out: Set<Vertex> = emptySet()): Node<Vertex>(id, out)
