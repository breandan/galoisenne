package ai.hypergraph.kaliningraph.parsing

class Tree(val name: String, vararg val children: Tree) {
  fun prettyPrint(): String {
    val buffer = StringBuilder(50)
    print(buffer, "", "")
    return buffer.toString()
  }

  override fun toString() = name
  override fun hashCode() = name.hashCode()
  override fun equals(other: Any?) = hashCode() == other.hashCode()

  private fun print(
    buffer: StringBuilder,
    prefix: String,
    childrenPrefix: String
  ) {
    buffer.append(prefix)
    buffer.append(name)
    buffer.append('\n')
    val it = children.iterator()
    while (it.hasNext()) {
      val next = it.next()
      if (it.hasNext())
        next.print(buffer, "$childrenPrefix├── ", "$childrenPrefix│   ")
      else next.print(buffer, "$childrenPrefix└── ", "$childrenPrefix    ")
    }
  }
}