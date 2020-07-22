package edu.mcgill.kaliningraph

open class Edge<E: Edge<E, T>, T: Node<T, E>>(open val target: T)

class LabeledEdge<T: Node<T, LabeledEdge<T>>>(override val target: T, val label: String): Edge<LabeledEdge<T>, T>(target)