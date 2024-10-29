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

fun makeLevFSA(str: Σᐩ, dist: Int): FSA = makeLevFSA(str.tokenizeByWhitespace(), dist)

fun Σᐩ.unpackCoordinates() =
  substringAfter('_').split('/')
    .let { (i, j) -> i.toInt() to j.toInt() }

/** Uses nominal arc predicates. See [NOM] for denominalization. */
fun makeLevFSA(
  str: List<Σᐩ>,
  maxRad: Int, // Maximum Levenshtein distance the automaton should accept
  /**
   * (x, y) where x is the first index where 1+ edit must have occurred already, and y
   * is the last index where there is at least one more edit left to make in the string.
   * We can use (x,y) to prune states representing trajectories which have spent their
   * entire edit allocation (with provably one edit left to make) or which have made no
   * edits so far (with provably at least one edit necessary) to reach a parsable state.
   * See [maxParsableFragment] for how these bounds are proven.
   */
  singleEditBounds: Pair<Int, Int> = str.size to 0,
  /**
   * Range provably containing two or more edits -- should be minimal for efficiency.
   * We can use this to prune states representing trajectories which have 1 or fewer
   * edits in their budget, but need at least 2+ to reach a final parsable state, or
   * which have only used one edit out of their budget but must have made 2+ edits
   * by this point in order to reach a parsable state. This proof is expensive to
   * find but worthwhile for long strings. See [smallestRangeWithNoSingleEditRepair].
   */
  multiEditBounds: IntRange = 0 until str.size,
  digits: Int = (str.size * maxRad).toString().length,
): FSA =
  (upArcs(str, maxRad, digits) +
    diagArcs(str, maxRad, digits) +
    str.mapIndexed { i, it -> rightArcs(i, maxRad, it, digits) }.flatten() +
    str.mapIndexed { i, it -> knightArcs(i, maxRad, it, digits, str) }.flatten())
    .also {
      println("Levenshtein-${str.size}x$maxRad automaton had ${it.size} arcs initially!")
    }.filter { arc ->
      listOf(arc.first.unpackCoordinates(), arc.third.unpackCoordinates())
        .all { (i, j) ->
           (0 < j || i <= singleEditBounds.first) // Prunes bottom right
            && (j < maxRad || i >= singleEditBounds.second - 2) // Prunes top left
            && (1 < j || i <= multiEditBounds.last + 1 || maxRad == 1) // Prunes bottom right
            && (j < maxRad - 1 || i > multiEditBounds.first - 1 || maxRad == 1) // Prunes top left
        }
    }
    .let { Q ->
      val initialStates = setOf("q_" + pd(0, digits).let { "$it/$it" })

      val finalStates = mutableSetOf<String>()
      Q.states.forEach {
        val (i, j) = it.unpackCoordinates()
        if ((str.size - i + j).absoluteValue <= maxRad) finalStates.add(it)
      }

      FSA(Q, initialStates, finalStates)
        .also { it.height = maxRad; it.width = str.size; it.levString = str }
//        .nominalize()
        .also { println("Levenshtein-${str.size}x$maxRad automaton had ${Q.size} arcs after pruning!") }
    }

private fun pd(i: Int, digits: Int) = i.toString().padStart(digits, '0')

/**
     upArcs and diagArcs are the most expensive operations taking ~O(2n|Σ|) to construct.
     Later, the Bar-Hillel construction creates a new production for every triple QxQxQ, so it
     increases the size of generated grammar by (2n|Σ|)^3. To fix this, we instead create
     a nominal or parametric CFG with arcs which denote infinite alphabets.

     See also: [ai.hypergraph.kaliningraph.repair.CEAProb]
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

fun upArcs(str: List<Σᐩ>, dist: Int, digits: Int): TSA =
  ((0..str.size).toSet() * (1..dist).toSet())
//    .filter { (i, _, s) -> str.size <= i || str[i] != s }
//    .filter { (i, j) -> i <= str.size || i - str.size < j }
    .map { (i, j) -> i to j to if (i < str.size) str[i] else "###" }
    .map { (i, j, s) -> i to j - 1 to "[!=]$s" to i to j }
    .postProc(digits)

/*
   s∈Σ i∈[1,n] j ∈[1,k]
-------------------------
 (q_i−1,j−1 -s→ q_i,j)∈δ
*/

fun diagArcs(str: List<Σᐩ>, dist: Int, digits: Int): TSA =
  ((1..str.size).toSet() * (1..dist).toSet())
//    .filter { (i, _, s) -> str.size <= i - 1 || str[i - 1] != s }
    .filter { (i, j) -> i <= str.size || i - str.size <= j }
    .map { (i, j) -> i to j to str[i - 1] }
    .map { (i, j, s) -> i - 1 to j - 1 to "[!=]$s" to i to j }
    .postProc(digits)

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

/**
 * Levenshtein automata optimizations to identify ranges that must contain an edit to be parsable.
 * These serve as proofs for the unreachability of certain states in the Levenshtein automaton.
 * For example, if we know that a certain range must contain at least one to be parsable, then we
 * have a proof that any states which have not yet made an edit after that range are unreachable,
 * and states which have exhausted all their edits before that range are also unreachable.
 */

fun CFG.maxParsableFragmentL(tokens: List<String>, pad: Int = 3): Pair<Int, Int> =
  ((1..tokens.size).toList().firstOrNull { i ->
      blockForward(tokens, i, pad) !in language
  } ?: tokens.size) to ((2..tokens.size).firstOrNull { i ->
    blockBackward(tokens, i, pad) !in language
  }?.let { tokens.size - it } ?: 0)

fun blockForward(tokens: List<String>, i: Int, pad: Int = 3) =
  tokens.mapIndexed { j, t -> if (j < i) t else "_" } + List(pad) { "_" }

fun blockBackward(tokens: List<String>, i: Int, pad: Int = 3) =
  List(pad) { "_" } + tokens.mapIndexed { j, t -> if (tokens.size - i < j) t else "_" }

// Binary search for the max parsable fragment. Equivalent to the linear search, but faster
fun CFG.maxParsableFragmentB(tokens: List<String>, pad: Int = 3): Pair<Int, Int> =
  ((1..tokens.size).toList().binarySearch { i ->
    val blocked = blockForward(tokens, i, pad)
    val blockedInLang = blocked in language
//    println(blocked.joinToString(" "))
    if (blockedInLang) -1 else {
      val blockedPrev = blockForward(tokens, i - 1, pad)
      val blockedPrevInLang = i == 1 || blockedPrev in language
      if (!blockedInLang && blockedPrevInLang) 0 else 1
    }
  }.let { if (it < 0) tokens.size else it + 1 }) to ((2..tokens.size).toList().binarySearch { i ->
    val blocked = blockBackward(tokens, i, pad)
    val blockedInLang = blocked in language
//    println(blocked.joinToString(" "))
    if (blockedInLang) -1 else {
      val blockedPrev = blockBackward(tokens, i - 1, pad)
      val blockedPrevInLang = i == 2 || blockedPrev in language
      if (!blockedInLang && blockedPrevInLang) 0 else 1
    }
  }.let { if (it < 0) 0 else (tokens.size - it - 2).coerceAtLeast(0) })

fun maskEverythingButRange(tokens: List<String>, range: IntRange): List<String> =
  tokens.mapIndexed { i, t -> if (i in range) t else "_" }

fun CFG.hasSingleEditRepair(tokens: List<String>, range: IntRange): Boolean =
  maskEverythingButRange(tokens, range).let { premask ->
    val toCheck = if (range.first < 0) List(-range.first) { "_" } + premask
    else if (tokens.size <= range.last) premask + List(range.last - tokens.size) { "_" }
    else premask

    val rangeSub = (maxOf(0, range.first) until minOf(tokens.size, range.last + 1))
    val rangeIns = (maxOf(0, range.first) until minOf(tokens.size + 1, range.last + 2))
    rangeSub.any { i -> toCheck.mapIndexed { j, t -> if (j == i) "_" else t } in language } // Check substitutions
      && rangeIns.any { (toCheck.take(it) + "_" + toCheck.drop(it)) in language } // Check insertions
  }

// Tries to shrink a multi-edit range until it has a single edit repair
fun CFG.tryToShrinkMultiEditRange(tokens: List<String>, range: IntRange): IntRange {
  fun IntRange.tryToShrinkLeft(): IntRange {
    var left = first + 1
    while (left < last - 2 && !hasSingleEditRepair(tokens, left until last)) left++
    return left until last
  }

  fun IntRange.tryToShrinkRight(): IntRange {
    var right = last
    while (first < right - 2 && !hasSingleEditRepair(tokens, first until right)) right--
    return first until right
  }

  return range.tryToShrinkLeft().tryToShrinkRight()
}

fun CFG.shrinkLRBounds(tokens: List<String>, pair: Pair<Int, Int>): IntRange {
  val (left, right) = (min(pair.first, pair.second) - 3).coerceAtLeast(0) to
      (max(pair.first, pair.second) + 3).coerceAtMost(tokens.size)

  return if (right - left <= 1 || hasSingleEditRepair(tokens, left until right)) 0..tokens.size
  else tryToShrinkMultiEditRange(tokens, left until right)
    .let { it -> it.first..(it.last + 1) }
}

fun CFG.smallestRangeWithNoSingleEditRepair(tokens: List<String>, stride: Int = MAX_RADIUS + 2): IntRange {
  if (tokens.size < 30) return 0..tokens.size
  else {
    val rangeLen = (0.4 * tokens.size).toInt()
    val indices = -stride until (tokens.size - rangeLen + stride) step stride
    var rmin = 0..tokens.size
    for (i in indices) {
      println("Checking range $i..${i + rangeLen}")
      val r = i until i + rangeLen
      if (hasSingleEditRepair(tokens, r)) continue
      println("Found multi-edit range $r")
      val rmin1 = tryToShrinkMultiEditRange(tokens, r)
      println("Shrunk to $rmin1")
      if (rmin1.last - rmin1.first < rmin.last - rmin.first) {
        rmin = rmin1
        if (rmin.last - rmin.first < 0.2 * tokens.size && rmin != 0..tokens.size) return rmin
      }
    }

    return rmin
  }
}

/**
 * Utils for calculating Levenshtein distance and alignments between strings.
 */

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

fun <T> List<Pair<T?, T?>>.patchSize(): Int = count { (a, b) -> a != b }

fun <T> List<Pair<T?, T?>>.summarize(): Σᐩ =
  mapIndexed { i, it -> it to i }.filter { (a, b) -> a != b }
    .joinToString(", ") { (a, b, i) ->
      when {
        // Green (insertion)
        a == null -> "I::$b::$i"
        // Red (deletion)
        b == null -> "D::$a::$i"
        // Orange (substitution)
        a != b -> "S::$a::$b::$i"
        else -> b.toString()
      }
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

fun <T> List<Pair<T?, T?>>.printLaTeX(): Σᐩ =
  joinToString(" ") { (a, b) ->
    when {
      // Green (insertion)
      a == null -> "\\hlgreen{$b}"
      // Red (deletion)
      b == null -> "\\hlred{$a}"
      // Orange (substitution)
      a != b -> "\\hlorange{$b}"
      else -> b.toString()
    }
  }

fun FSA.levWalk(from: Σᐩ, to: Σᐩ): List<Σᐩ> =
  walk(from) { me: Σᐩ, neighbors: List<Σᐩ> ->
    if (me == to) -1
    else neighbors.indexOfFirst { it.coords().second == me.coords().second }
  }