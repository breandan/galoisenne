package ai.hypergraph.kaliningraph.parsing

val LOR = "%OR%"
val AND = "%AND%"

val booleanFormulaCFG = """
  O -> $LOR | $AND
  S -> S O S | ( S )
  S -> true | false
""".parseCFG()

private fun Tree.middle(): Σᐩ? = children.drop(1).firstOrNull()?.terminal

fun Tree.evalToBool(
  left: Boolean? = children.firstOrNull()?.evalToBool(),
  right: Boolean? = children.lastOrNull()?.evalToBool()
): Boolean? = when {
  middle() == LOR && left != null && right != null -> left or right
  middle() == AND && left != null && right != null -> left and right
  terminal?.toBooleanStrictOrNull() != null -> terminal.toBooleanStrictOrNull()
  terminal in listOf("(", ")") -> null
  else -> children.asSequence().map { it.evalToBool() }.firstNotNullOfOrNull { it }
}