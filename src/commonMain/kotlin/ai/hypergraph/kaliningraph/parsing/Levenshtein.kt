package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.types.*
import kotlin.math.absoluteValue

// Only accept states that are within radius dist of (strLen, 0)
fun acceptStates(strLen: Int, dist: Int) =
  ((strLen - dist..strLen) * (0..dist))
    .filter { (i, j) -> ((strLen - i) + j).absoluteValue <= dist }
    .map { (i, j) -> "d:$i:$j" }

fun backtrace(x: Int, y: Int, sym: Σᐩ) =
    if (x == 0 && y == 0) sym else if (x < 0) "" else "d:$x:$y $sym"

// https://fulmicoton.com/posts/levenshtein#observations-lets-count-states
private fun levenshteinTransitions(symbols: List<Σᐩ>, i: Int) =
  "d:0:$i -> ${if(i == 1) "" else "d:0:${i - 1} "}*\n" +
    symbols.mapIndexed { j, s ->
      "d:${j + 1}:$i -> " +
          // Inbound transitions
          backtrace(j, i, s) + " | " +
          backtrace(j, i - 1, "*") + " | " +
          backtrace(j + 1, i - 1, "*") +
          if (0 < j) " | " + backtrace(j - 1, i - 1, symbols.getOrElse(j) { "" }) else ""
    }.joinToString("\n")

fun constructLevenshteinCFG(symbols: List<Σᐩ>, dist: Int, alphabet: Set<Σᐩ> = symbols.toSet() + "ε"): Σᐩ =
  """
     START -> ${acceptStates(symbols.size, dist).joinToString(" | ")}
     * -> ${(alphabet + symbols).joinToString(" | ") { "%$it" }}
  """.trimIndent() +
      (alphabet + symbols).joinToString("\n", "\n", "\n") { "%$it -> $it" } + "d:1:0 -> ${symbols[0]}\n" +
      symbols.drop(1).mapIndexed { i, symbol -> "d:${i+2}:0 -> d:${i+1}:0 $symbol" }.joinToString("\n", "\n") +
      (1..dist).joinToString("\n\n", "\n") { levenshteinTransitions(symbols, it) }

/**
 * Takes a [CFG], an [unparseable] string, and a [solver], and returns a sequence of
 * parseable strings each within Levenshtein distance δ([unparseable], ·) <= [maxDist].
 * @see [CJL.alignNonterminals]
 */
fun CFG.levenshteinRepair(maxDist: Int, unparseable: List<Σᐩ>, solver: CJL.(List<Σᐩ>) -> Sequence<Σᐩ>): Sequence<Σᐩ> {
  val alphabet =  terminals + unparseable + "ε"
  val levCFG = constructLevenshteinCFG(unparseable, maxDist, alphabet).parseCFG().noNonterminalStubs
  return (this intersect levCFG).solver(List(unparseable.size + maxDist) { "_" })
    .map { it.replace("ε", "").tokenizeByWhitespace().joinToString(" ") }.distinct()
}

// TODO: http://www.cs.umd.edu/~gasarch/BLOGPAPERS/cfg.pdf