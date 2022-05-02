package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.graphs.LabeledGraph

class Tree(val root: String, vararg val children: Tree) {
  override fun toString() = root
  override fun hashCode() = root.hashCode()
  override fun equals(other: Any?) = hashCode() == other.hashCode()

  fun toGraph(): LabeledGraph =
    children.fold(LabeledGraph { children.forEach { root - it.root } }) { acc, it -> acc + it.toGraph() }

  fun prettyPrint(
    buffer: String = "",
    prefix: String = "",
    childrenPrefix: String = "",
  ): String =
    children.foldIndexed("$buffer$prefix$root\n") { i, acc, it ->
      if (i == children.size - 1)
        it.prettyPrint(acc, "$childrenPrefix└── ", "$childrenPrefix    ")
      else it.prettyPrint(acc, "$childrenPrefix├── ", "$childrenPrefix│   ")
    }
}