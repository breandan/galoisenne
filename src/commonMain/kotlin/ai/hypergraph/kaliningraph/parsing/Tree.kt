package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.graphs.LGVertex
import ai.hypergraph.kaliningraph.graphs.LabeledGraph
import ai.hypergraph.kaliningraph.tensor.FreeMatrix

typealias TreeMatrix = FreeMatrix<Forest>
typealias Forest = Set<Tree>

class Tree constructor(
  val root: Σᐩ,
  val terminal: Σᐩ? = null,
  vararg val children: Tree,
  val span: IntRange = children.fold(Int.MAX_VALUE to Int.MIN_VALUE) { (a, b), t ->
    minOf(a, t.span.first) to maxOf(b, t.span.last)}.let { it.first..it.second }
) {
  override fun toString() = root
  override fun hashCode() = root.hashCode()
  override fun equals(other: Any?) = hashCode() == other.hashCode()

  fun toGraph(j: Σᐩ = "0"): LabeledGraph =
    LabeledGraph { LGVertex(root, "$root.$j").let { it - it } } +
      children.foldIndexed(
        LabeledGraph {
          children.forEachIndexed { i, it ->
            LGVertex(root, "$root.$j") - LGVertex(it.root, "${it.root}.$j.$i")
          }
        }
      ) { i, acc, it -> acc + it.toGraph("$j.$i") }

  fun prettyPrint(buffer: Σᐩ = "", prefix: Σᐩ = "", nextPrefix: Σᐩ = ""): Σᐩ =
    if (children.isEmpty()) (buffer + prefix + "${terminal?.htmlify()} [${span.first}]\n")
    else children.foldIndexed("$buffer$prefix" + root.htmlify() +
      (if (-1 !in span) " [$span]" else "") + "\n") { i: Int, acc: Σᐩ, it: Tree ->
        if (i == children.size - 1)
          it.prettyPrint(acc + "", "$nextPrefix└── ", "$nextPrefix    ")
        else it.prettyPrint(acc, "$nextPrefix├── ", "$nextPrefix│   ")
      }

  fun latexify(): Σᐩ = "\\Tree ${qtreeify()}"

  private fun qtreeify(): Σᐩ =
   if (children.isEmpty()) "\\texttt{$terminal}"
   else "[.\\texttt{$root} " + children.joinToString(" ", "", " ]") { it.qtreeify() }

  private fun Σᐩ.htmlify() =
    replace('<', '⟨').replace('>', '⟩')

  // Xujie's algorithm - it works! :-D
  fun denormalize(): Tree {
    fun Tree.removeSynthetic(
      refactoredChildren: List<Tree> = children.map { it.removeSynthetic() }.flatten(),
      isSynthetic: (Tree) -> Boolean = { 2 <= root.split('.').size }
    ): List<Tree> =
      if (children.isEmpty()) listOf(Tree(root, terminal, span = span))
      else if (isSynthetic(this)) refactoredChildren
      else listOf(Tree(root, children = refactoredChildren.toTypedArray(), span = span))

    return removeSynthetic().first()
  }

  fun contents(): Σᐩ =
    if (children.isEmpty()) "$terminal"
    else children.joinToString(" ") { it.contents() }
}