package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.types.*

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

fun List<Boolean>.includes(other: List<Boolean>): Boolean =
  if (size != other.size) throw IllegalArgumentException("Lists must be of equal size")
  else zip(other).all { (a, b) -> a == b || (a && !b) }

fun List<Boolean>.includesDistance(other: List<Boolean>): Int =
  if (size != other.size) throw IllegalArgumentException("Lists must be of equal size")
  else zip(other).count { (a, b) -> a != b && (a && !b) }

typealias BitvecPosetInterval = Π2A<List<Boolean>>
val BitvecPosetInterval.lower get() = first
val BitvecPosetInterval.upper get() = second

operator fun BitvecPosetInterval.contains(query: List<Boolean>) =
  if (first.size != query.size) throw IllegalArgumentException("Lists must be of equal size")
  else query.includes(lower) && upper.includes(query)