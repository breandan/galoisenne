package edu.mcgill.kaliningraph

open class Edge<T: Node<T>>(val target: T, val label: String? = null) {
  override fun equals(other: Any?) = (other as? Edge<T>)?.target == target
  override fun hashCode() = target.hashCode() + label.hashCode()
  override fun toString() = target.id
}