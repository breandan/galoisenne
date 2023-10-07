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
//  println("Levenshtein CFG: ${levCFG.prettyPrint()}")
  val template = List(unparseable.size + maxDist) { "_" }
  return (this intersect levCFG).solver(template)
    .map { it.replace("ε", "").tokenizeByWhitespace().joinToString(" ") }.distinct()
}

fun makeLevFSA(str: Σᐩ, dist: Int, alphabet: Set<Σᐩ>): FSA =
  makeLevFSA(str.tokenizeByWhitespace(), dist, alphabet)

fun makeLevFSA(str: List<Σᐩ>, dist: Int, alphabet: Set<Σᐩ>): FSA =
  (upArcs(str.size, dist, alphabet) +
    diagArcs(str.size, dist, alphabet) +
    str.map { rightArcs(str.size, dist, "ε") }.flatten() +
    str.map { knightArcs(str.size, dist, "ε") }.flatten()).let { Q ->
    val initialStates = setOf("q_0,0")
    fun Σᐩ.unpackCoordinates() =
      substringAfter("_").split("/")
        .let { (i, j) -> i.toInt() to j.toInt() }

    val finalStates = mutableSetOf<String>()
    Q.states.forEach {
      val (i, j) = it.unpackCoordinates()
      if ((str.size - i + j).absoluteValue <= dist) finalStates.add(it)
    }

    FSA(Q, initialStates, finalStates)
  }

fun upArcs(len: Int, dist: Int, alphabet: Set<Σᐩ>): TSA =
  ((0..len).toSet() * (1..dist).toSet() * alphabet)
    .map { (i, j, s) -> "q_$i/${j-1}" to s to "q_$i/$j" }.toSet()

fun diagArcs(len: Int, dist: Int, alphabet: Set<Σᐩ>): TSA =
  ((1..len).toSet() * (1..dist).toSet() * alphabet)
    .map { (i, j, s) -> "q_${i-1}/${j-1}" to s to "q_$i/$j" }.toSet()

fun rightArcs(len: Int, dist: Int, letter: Σᐩ): TSA =
  ((1..len).toSet() * (0..dist).toSet() * setOf(letter))
    .map { (i, j, s) -> "q_${i-1}/$j" to s to "q_$i/$j" }.toSet()

fun knightArcs(len: Int, dist: Int, letter: Σᐩ): TSA =
  ((2..len).toSet() * (1..dist).toSet() * setOf(letter))
    .map { (i, j, s) -> "q_${i-2}/${j-1}" to s to "q_$i/$j" }.toSet()