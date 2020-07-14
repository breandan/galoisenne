package edu.mcgill.kaliningraph

open class Edge(val target: Vertex, val label: String? = null) {
  override fun equals(other: Any?) = (other as? Edge)?.target == target
  override fun hashCode() = target.hashCode() + label.hashCode()
  override fun toString() = target.id
}