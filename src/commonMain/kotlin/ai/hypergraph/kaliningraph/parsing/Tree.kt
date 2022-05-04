package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.graphs.*

class Tree(val root: String, vararg val children: Tree) {
  override fun toString() = root
  override fun hashCode() = root.hashCode()
  override fun equals(other: Any?) = hashCode() == other.hashCode()

  fun toGraph(j: String = "0"): LabeledGraph =
    children.foldIndexed(LabeledGraph { children.forEachIndexed { i, it ->
      LGVertex(root, "$root.$j") - LGVertex(root, "${it.root}.$j.$i") }
    }) { i, acc, it -> acc + it.toGraph("$j.$i") }

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