package edu.mcgill.kaliningraph

open class Edge<E: Edge<E, T>, T: Node<T, E>>(open val target: T)

open class LabeledEdge(override val target: Vertex, val label: String? = null): Edge<LabeledEdge, Vertex>(target)