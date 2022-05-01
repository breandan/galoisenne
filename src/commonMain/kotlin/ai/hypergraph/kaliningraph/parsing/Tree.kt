package ai.hypergraph.kaliningraph.parsing

class Tree(val root: String, vararg val children: Tree) {
  override fun toString() = root
  override fun hashCode() = root.hashCode()
  override fun equals(other: Any?) = hashCode() == other.hashCode()

  fun prettyPrint(
    buffer: String = "",
    prefix: String = "",
    childrenPrefix: String = "",
  ): String {
    var buffer = "$buffer$prefix$root\n"
    val it = children.iterator()
    while (it.hasNext()) {
      val next = it.next()
      buffer = if (it.hasNext())
        next.prettyPrint(buffer, "$childrenPrefix├── ", "$childrenPrefix│   ")
      else next.prettyPrint(buffer, "$childrenPrefix└── ", "$childrenPrefix    ")
    }

    return buffer
  }
}