package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.graphs.LGVertex
import ai.hypergraph.kaliningraph.graphs.LabeledGraph
import ai.hypergraph.kaliningraph.tensor.FreeMatrix
import ai.hypergraph.kaliningraph.types.*

typealias TreeMatrix = FreeMatrix<Forest>
typealias Forest = Set<Tree>

class Tree constructor(
  val root: Σᐩ,
  val terminal: Σᐩ? = null,
  vararg val children: Tree,
  val span: IntRange = children.fold(Int.MAX_VALUE to Int.MIN_VALUE) { (a, b), t ->
    minOf(a, t.span.first) to maxOf(b, t.span.last)}.let { it.first..it.second }
) {
  val hash by lazy { root.hashCode() + terminal.hashCode() + contents().hashCode() }
  override fun toString() = root
  override fun hashCode() = hash
  override fun equals(other: Any?) = hashCode() == other.hashCode()

  fun activeSymbols(): Set<Σᐩ> = setOf(root) + children.flatMap { it.activeSymbols() } +
    if (terminal != null) setOf(terminal) else emptySet()

  fun structureEncode(): Σᐩ =
//    if (terminal == "ε") ""
    if (children.isEmpty()) "()"
    else children.joinToString("", prefix = "(", postfix = ")") { it.structureEncode() }

  fun triples(): List<Π3A<Σᐩ>> =
    if (children.size != 2) listOf(Π3A(root, "$terminal", "ε"))
    else listOf(Π3A(root, children[0].root, children[1].root)) +
      children.flatMap { it.triples() }

  fun quintuples(parent: String = "NIL", lsibling: String = "NIL", rsibling: String = "NIL"): List<Π5A<Σᐩ>> =
    if (children.size != 2) listOf(Π5A(parent, lsibling, rsibling, "$terminal", "ε"))
    else listOf(Π5A(parent, lsibling, rsibling, children[0].root, children[1].root)) +
      children[0].quintuples(root, children[0].root + "*", children[1].root) +
      children[1].quintuples(root, children[0].root, children[1].root + "*")

  fun logProb(pcfgMap: Map<Π3A<Σᐩ>, Int>): Double =
    if (children.isEmpty()) 0.0
    else (pcfgMap[root to children[0].root to children[1].root]?.toDouble() ?: 0.0) +
      children.sumOf { it.logProb(pcfgMap) }

  fun toGraph(j: Σᐩ = "0"): LabeledGraph =
    LabeledGraph { LGVertex(root, "$root.$j").let { it - it } } +
      children.foldIndexed(
        LabeledGraph {
          children.forEachIndexed { i, it ->
            LGVertex(root, "$root.$j") - LGVertex(it.root, "${it.root}.$j.$i")
          }
        }
      ) { i, acc, it -> acc + it.toGraph("$j.$i") }

  val indxInfo by lazy { if (span.first < Int.MAX_VALUE) " [${span.first}]" else "" }
  val spanInfo by lazy { if (span.first < Int.MAX_VALUE) " [$span]" else "" }

  fun prettyPrint(buffer: Σᐩ = "", prefix: Σᐩ = "", nextPrefix: Σᐩ = ""): Σᐩ =
    if (children.isEmpty()) (buffer + prefix + "${terminal?.htmlify()}$indxInfo\n")
    else children.foldIndexed("$buffer$prefix" + root.htmlify() +
      (if (-1 !in span) spanInfo else "") + "\n") { i: Int, acc: Σᐩ, it: Tree ->
        if (i == children.size - 1)
          it.prettyPrint(acc + "", "$nextPrefix└── ", "$nextPrefix    ")
        else it.prettyPrint(acc, "$nextPrefix├── ", "$nextPrefix│   ")
      }

  fun latexify(): Σᐩ = "\\Tree ${qtreeify()}"

  private fun qtreeify(): Σᐩ =
   if (children.isEmpty()) "\\texttt{$terminal}"
   else "[.\\texttt{$root} " + children.joinToString(" ", "", " ]") { it.qtreeify() }

  private fun Σᐩ.htmlify() =
    replace("->", "→").replace('<', '⟨').replace('>', '⟩')

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

  fun contents(removeEpsilon: Boolean = false): Σᐩ =
    if (children.isEmpty()) "$terminal"
    else children.map { it.contents(removeEpsilon) }
      .let { if (removeEpsilon) it.filter { it != "ε" } else it }
      .joinToString(" ")
}