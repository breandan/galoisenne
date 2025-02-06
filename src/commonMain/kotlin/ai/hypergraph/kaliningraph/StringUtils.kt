package ai.hypergraph.kaliningraph

import ai.hypergraph.kaliningraph.automata.*
import ai.hypergraph.kaliningraph.image.escapeHTML
import ai.hypergraph.kaliningraph.parsing.*
import ai.hypergraph.kaliningraph.tensor.FreeMatrix
import ai.hypergraph.kaliningraph.tensor.transpose
import ai.hypergraph.kaliningraph.types.*
import kotlin.math.*

fun Σᐩ.tokenizeByWhitespace(): List<Σᐩ> = split(Regex("\\s+")).filter { it.isNotBlank() }

fun Σᐩ.tokenizeByWhitespaceAndKeepDelimiters(): List<Σᐩ> =
  split(Regex("(?<=\\s)|(?=\\s)"))

infix fun Char.closes(that: Char) =
  if (this == ')' && that == '(') true
  else if (this == ']' && that == '[') true
  else if (this == '}' && that == '{') true
  else this == '>' && that == '<'

val BRACKETS = "()[]{}<>".toCharArray().toSet()
val JUST_PARENS = "()[]{}".toCharArray().toSet()
fun Σᐩ.hasBalancedBrackets(brackets: Set<Char> = BRACKETS): Boolean =
  filter { it in brackets }.fold(Stack<Char>()) { stack, c ->
    stack.apply { if (isNotEmpty() && c.closes(peek())) pop() else push(c) }
  }.isEmpty() && brackets.any { it in this }

fun Σᐩ.splitProd() = replaceFirst("->", "→").split('→').map { it.trim() }

fun List<Σᐩ>.formatAsGrid(cols: Int = -1): FreeMatrix<Σᐩ> {
  fun Σᐩ.tok() = splitProd()
  fun Σᐩ.LHS() = tok()[0]
  fun Σᐩ.RHS() = tok()[1]
  val groups = groupBy { it.LHS() }

  fun List<Σᐩ>.rec() = if (cols == -1) // Minimize whitespace over all grids with a predefined number of columns
    (3..5).map { formatAsGrid(it) }.minBy { it.toString().length }
  else sortedWith(compareBy(
    { groups[it.LHS()]!!.maxOf { it.length } }, // Shortest longest pretty-printed production comes first
    { -groups[it.LHS()]!!.size }, // Take small groups first
    { it.LHS() }, // Must never split up two LHS productions
    { it.length }
  )).let { productions ->
    val (cols, rows) = cols to ceil(productions.size.toDouble() / cols).toInt()
    val padded = productions + List(cols * rows - productions.size) { "" }
    FreeMatrix(cols, rows, padded).transpose
  }.let { up ->
    FreeMatrix(up.numRows, up.numCols) { r, c ->
      if (up[r, c].isEmpty()) return@FreeMatrix ""
      val (lhs, rhs) = up[r, c].splitProd().let { it[0] to it[1] }
      val lp = lhs.padStart(up.transpose[c].maxOf { it.substringBefore(" -> ").length })
      val rp = rhs.padEnd(up.transpose[c].maxOf { it.substringAfter(" -> ").length })
      "$lp → $rp"
    }
  }

  return rec()
}

private fun <T> List<List<T>>.col(i: Int) = map { it[i] }

// https://en.wikipedia.org/wiki/Seam_carving
fun Σᐩ.carveSeams(toRemove: Regex = Regex("\\s{2,}")): Σᐩ =
  replace("  |  ", "    ")
    .lines().filter { it.isNotBlank() }.map { it.split('→') }.let { toMerge ->
    val minCols = toMerge.minOf { it.size }
    val takeAway = (0 until minCols).map { toMerge.col(it).minOf { toRemove.find(it)!!.value.length } }
    val subs = takeAway.map { List(it) { " " }.joinToString("") }
    toMerge.joinToString("\n", "\n") {
      it.mapIndexed { i, it -> if (i < minCols) it.replaceFirst(subs[i], "   ") else it }
        .joinToString("→").drop(4).dropLast(3)
    }
  }

fun <T> List<Pair<T?, T?>>.paintDiffs(): String =
  joinToString(" ") { (a, b) ->
    when {
      a == null -> "<span style=\"color: green\">${b.toString().escapeHTML()}</span>"
      b == null -> "<span style=\"background-color: gray\"><span class=\"noselect\">${List(a.toString().length){" "}.joinToString("")}</span></span>"
      a == "_" -> "<span style=\"color: green\">${b.toString().escapeHTML()}</span>"
      a != b -> "<span style=\"color: orange\">${b.toString().escapeHTML()}</span>"
      else -> b.toString().escapeHTML()
    }
  }

fun multisetManhattanDistance(s1: Σᐩ, s2: Σᐩ): Int =
  multisetManhattanDistance(s1.tokenizeByWhitespace().toList(), s2.tokenizeByWhitespace().toList())

fun <T> multisetManhattanDistance(q1: List<T>, q2: List<T>): Int {
  val (s1, s2) = listOf(q1, q2).map { it.groupingBy { it }.eachCount() }

  val totalDiff = s1.keys.union(s2.keys)
    .sumOf { t -> (s1.getOrElse(t) { 0 } - s2.getOrElse(t) { 0 }).absoluteValue }

  return totalDiff
}

fun String.removeEpsilon() = tokenizeByWhitespace().filter { it != "ε" }.joinToString(" ")
fun String.stripStub() = substring(1, length - 1) // A stub is a token like <...> enclosing an NT

// Intersperses "" in between every token in a list of tokens
fun List<Σᐩ>.intersperse(i: Int = 1, tok: Σᐩ = "", spacer: List<Σᐩ> = List(i) { tok }): List<Σᐩ> =
  fold(spacer) { acc, s -> acc + spacer + s } + spacer

fun String.cfgType() = when {
  isNonterminalStub() -> "NT/$this"
  // Is a Java or Kotlin identifier character in Kotlin common library (no isJavaIdentifierPart)
  Regex("[a-zA-Z0-9_]+").matches(this) -> "ID/$this"
  any { it in BRACKETS } -> "BK/$this"
  else -> "OT"
}

const val ANSI_RESET = "\u001B[0m"
const val ANSI_BLACK = "\u001B[30m"
const val ANSI_RED = "\u001B[31m"
const val ANSI_GREEN = "\u001B[32m"
const val ANSI_YELLOW = "\u001B[33m"
const val ANSI_BLUE = "\u001B[34m"
const val ANSI_PURPLE = "\u001B[35m"
const val ANSI_CYAN = "\u001B[36m"
const val ANSI_WHITE = "\u001B[37m"

const val ANSI_BLACK_BACKGROUND = "\u001B[40m"
const val ANSI_RED_BACKGROUND = "\u001B[41m"
const val ANSI_GREEN_BACKGROUND = "\u001B[42m"
const val ANSI_ORANGE_BACKGROUND = "\u001B[43m"
const val ANSI_YELLOW_BACKGROUND = "\u001B[43m"
const val ANSI_BLUE_BACKGROUND = "\u001B[44m"
const val ANSI_PURPLE_BACKGROUND = "\u001B[45m"
const val ANSI_CYAN_BACKGROUND = "\u001B[46m"
const val ANSI_WHITE_BACKGROUND = "\u001B[47m"

fun Char.toUnicodeEscaped() = "\\u${code.toString(16).padStart(4, '0')}"
fun Σᐩ.replaceAll(tbl: Map<String, String>) = tbl.entries.fold(this) { acc, (k, v) -> acc.replace(k, v) }