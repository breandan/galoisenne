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

fun makeLevFSA(
  str: List<Σᐩ>,
  dist: Int,
  alphabet: Set<Σᐩ>,
  digits: Int = (str.size * dist).toString().length
): FSA =
  (upArcs(str, dist, alphabet, digits) +
    diagArcs(str, dist, alphabet, digits) +
    str.mapIndexed { i, it -> rightArcs(i, dist, it, digits) }.flatten() +
    str.mapIndexed { i, it -> knightArcs(i, dist, it, digits) }.flatten())
  .let { Q ->
    val initialStates = setOf("q_" + pd(0, digits).let { "$it/$it" })
    fun Σᐩ.unpackCoordinates() =
      substringAfter('_').split('/')
        .let { (i, j) -> i.toInt() to j.toInt() }

    val finalStates = mutableSetOf<String>()
    Q.states.forEach {
      val (i, j) = it.unpackCoordinates()
      if ((str.size - i + j).absoluteValue <= dist) finalStates.add(it)
    }

    FSA(Q, initialStates, finalStates)
  }

fun pd(i: Int, digits: Int) = i.toString().padStart(digits, '0')

/*
  s∈Σ i∈[0,n] j∈[1,k]
-----------------------
 (q_i,j−1 -s→ q_i,j)∈δ
*/

fun upArcs(str: List<Σᐩ>, dist: Int, alphabet: Set<Σᐩ>, digits: Int): TSA =
  ((0..<str.size + dist).toSet() * (1..dist).toSet() * alphabet)
    .filter { (i, _, s) -> str.size <= i || str[i] != s }
    .filter { (i, j, _) -> i <= str.size || i - str.size < j }
    .map { (i, j, s) -> i to j - 1 to s to i to j }.postProc(digits)

/*
   s∈Σ i∈[1,n] j ∈[1,k]
-------------------------
 (q_i−1,j−1 -s→ q_i,j)∈δ
*/

fun diagArcs(str: List<Σᐩ>, dist: Int, alphabet: Set<Σᐩ>, digits: Int): TSA =
  ((1..<str.size + dist).toSet() * (1..dist).toSet() * alphabet)
    .filter { (i, _, s) -> str.size <= i - 1 || str[i - 1] != s }
    .filter { (i, j, _) -> i <= str.size || i - str.size <= j }
    .map { (i, j, s) -> i - 1 to j - 1 to s to i to j }.postProc(digits)

/*
 s=σ_i i∈[1,n] j∈[0,k]
-----------------------
 (q_i−1,j -s→ q_i,j)∈δ
*/

fun rightArcs(idx: Int, dist: Int, letter: Σᐩ, digits: Int): TSA =
  (setOf(idx + 1) * (0..dist).toSet() * setOf(letter))
    .map { (i, j, s) -> i - 1 to j to s to i to j }.postProc(digits)

/*
  s=σ_i i∈[2,n] j∈[1,k]
-------------------------
 (q_i−2,j−1 -s→ q_i,j)∈δ
*/

fun knightArcs(idx: Int, dist: Int, letter: Σᐩ, digits: Int): TSA =
  if (idx < 1) setOf()
  else (setOf(idx + 1) * (1..dist).toSet() * setOf(letter))
    .map { (i, j, s) -> i - 2 to j - 1 to s to i to j }.postProc(digits)

fun List<Π5<Int, Int, Σᐩ, Int, Int>>.postProc(digits: Int) =
  map { (a, b, s, d, e) ->
    pd(a, digits) to pd(b, digits) to s to pd(d, digits) to pd(e, digits)
  }.map { (a, b, s, d, e) ->
    "q_$a/$b" to s to "q_$d/$e"
  }.toSet()