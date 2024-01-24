package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.*
import ai.hypergraph.kaliningraph.automata.*
import ai.hypergraph.kaliningraph.repair.*
import ai.hypergraph.kaliningraph.types.*
import ai.hypergraph.kaliningraph.types.times
import kotlin.math.*

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
  digits: Int = (str.size * dist).toString().length,
  ceaDist: CEADist? = null
): FSA =
  (upArcs(str, dist, (if (ceaDist == null) alphabet else alphabet.intersect(ceaDist.topIns)) + str, digits, ceaDist) +
    diagArcs(str, dist, (if (ceaDist == null) alphabet else alphabet.intersect(ceaDist.topSub)) + str, digits, ceaDist) +
    str.mapIndexed { i, it -> rightArcs(i, dist, it, digits) }.flatten() +
    str.mapIndexed { i, it -> knightArcs(i, dist, it, digits, str) }.flatten())
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
//      .nominalize()
      .also { println("Levenshtein automata size: ${Q.size}") }
  }

private fun pd(i: Int, digits: Int) = i.toString().padStart(digits, '0')

/**
   TODO: upArcs and diagArcs are the most expensive operations taking ~O(2n|Σ|) to construct.
     Later, the Bar-Hillel construction creates a new production for every triple QxQxQ, so it
     increases the size of generated grammar by (2n|Σ|)^3. To fix this, we must instead create
     a nominal or parametric CFG with arcs which denote infinite alphabets.

     See: [ai.hypergraph.kaliningraph.repair.CEAProb]
*//*
  References
    - https://arxiv.org/pdf/1402.0897.pdf#section.7
    - https://arxiv.org/pdf/2311.03901.pdf#subsection.2.2
*/

/*
  s∈Σ i∈[0,n] j∈[1,k]
-----------------------
 (q_i,j−1 -s→ q_i,j)∈δ
*/

fun upArcs(str: List<Σᐩ>, dist: Int, alphabet: Set<Σᐩ>, digits: Int, ceaDist: CEADist? = null): TSA =
  ((0..<str.size + dist).toSet() * (1..dist).toSet() * alphabet)
    .filter { (i, _, s) -> str.size <= i || str[i] != s }
    .filter { (i, j, _) -> i <= str.size || i - str.size < j }
//    .filter { (i, j, s) ->
//      if (ceaDist == null || j != 1) true
////      else s in (ceaDist.insLeft[str.getOrElse(i - 1) { "BOS" }] ?: setOf())
//      else s in ceaDist.topIns
//    }
    .map { (i, j, s) -> i to j - 1 to s to i to j }.postProc(digits)

/*
   s∈Σ i∈[1,n] j ∈[1,k]
-------------------------
 (q_i−1,j−1 -s→ q_i,j)∈δ
*/

fun diagArcs(str: List<Σᐩ>, dist: Int, alphabet: Set<Σᐩ>, digits: Int, ceaDist: CEADist? = null): TSA =
  ((1..<str.size + dist).toSet() * (1..dist).toSet() * alphabet)
    .filter { (i, _, s) -> str.size <= i - 1 || str[i - 1] != s }
    .filter { (i, j, _) -> i <= str.size || i - str.size <= j }
//    .filter { (i, j, s) ->
//      if (ceaDist == null || j != 1) true
////      else s in (ceaDist.subLeft[str.getOrElse(i - 2) { "BOS" }] ?: setOf())
//      else s in ceaDist.topSub.intersect(alphabet) + str
//    }
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

fun knightArcs(idx: Int, dist: Int, letter: Σᐩ, digits: Int, str: List<Σᐩ>): TSA =
  (1..dist).flatMap { d ->
    (setOf(idx) * (0..dist).toSet())
      .filter { (i, j) -> i + d + 1 <= str.size && j + d <= dist }
      .map { (i, j) -> i to j to str[i + d] to (i + d + 1) to (j + d) }
  }.postProc(digits)

fun List<Π5<Int, Int, Σᐩ, Int, Int>>.postProc(digits: Int) =
  map { (a, b, s, d, e) ->
    pd(a, digits) to pd(b, digits) to s to pd(d, digits) to pd(e, digits)
  }.map { (a, b, s, d, e) ->
    "q_$a/$b" to s to "q_$d/$e"
  }.toSet()

fun allPairsLevenshtein(s1: Set<Σᐩ>, s2: Set<Σᐩ>) =
  (s1 * s2).sumOf { (a, b) -> levenshtein(a, b) }

fun levenshtein(s1: Σᐩ, s2: Σᐩ): Int =
  levenshtein(s1.tokenizeByWhitespace().toList(), s2.tokenizeByWhitespace().toList())

fun <T> levenshtein(o1: List<T>, o2: List<T>): Int {
  var prev = IntArray(o2.size + 1)
  for (j in 0 until o2.size + 1) prev[j] = j
  for (i in 1 until o1.size + 1) {
    val curr = IntArray(o2.size + 1)
    curr[0] = i
    for (j in 1 until o2.size + 1) {
      val d1 = prev[j] + 1
      val d2 = curr[j - 1] + 1
      val d3 = prev[j - 1] + if (o1[i - 1] == o2[j - 1]) 0 else 1
      curr[j] = min(min(d1, d2), d3)
    }

    prev = curr
  }
  return prev[o2.size]
}

fun levenshteinAlign(a: Σᐩ, b: Σᐩ): List<Pair<Σᐩ?, Σᐩ?>> =
  levenshteinAlign(a.tokenizeByWhitespace(), b.tokenizeByWhitespace())

fun <T> levenshteinAlign(a: List<T>, b: List<T>): List<Pair<T?, T?>> {
  val costs = Array(a.size + 1) { IntArray(b.size + 1) }
  for (j in 0..b.size) costs[0][j] = j
  for (i in 1..a.size) {
    costs[i][0] = i
    for (j in 1..b.size) {
      val temp = costs[i - 1][j - 1] + (if (a[i - 1] == b[j - 1]) 0 else 1)
      costs[i][j] = minOf(1 + minOf(costs[i - 1][j], costs[i][j - 1]), temp)
    }
  }

  val aPathRev = mutableListOf<T?>()
  val bPathRev = mutableListOf<T?>()
  var i = a.size
  var j = b.size
  while (i > 0 && j > 0) {
    val temp = costs[i - 1][j - 1] + (if (a[i - 1] == b[j - 1]) 0 else 1)
    when (costs[i][j]) {
      temp -> {
        aPathRev.add(a[--i])
        bPathRev.add(b[--j])
      }
      1 + costs[i-1][j] -> {
        aPathRev.add(a[--i])
        bPathRev.add(null)
      }
      1 + costs[i][j-1] -> {
        aPathRev.add(null)
        bPathRev.add(b[--j])
      }
    }
  }

  while (i > 0) {
    aPathRev.add(a[--i])
    bPathRev.add(null)
  }

  while (j > 0) {
    aPathRev.add(null)
    bPathRev.add(b[--j])
  }

  val revPathA = aPathRev.reversed()
  val revPathB = bPathRev.reversed()
  return revPathA.zip(revPathB)
}

fun <T> List<Pair<T?, T?>>.paintANSIColors(): Σᐩ =
  joinToString(" ") { (a, b) ->
    when {
      // Green (insertion)
      a == null -> "$ANSI_GREEN_BACKGROUND$b$ANSI_RESET"
      // Red (deletion)
      b == null -> "$ANSI_RED_BACKGROUND$a$ANSI_RESET"
      // Orange (substitution)
      a != b -> "$ANSI_ORANGE_BACKGROUND$b$ANSI_RESET"
      else -> b.toString()
    }
  }