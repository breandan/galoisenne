package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.*
import ai.hypergraph.kaliningraph.automata.*
import ai.hypergraph.kaliningraph.types.*
import ai.hypergraph.kaliningraph.types.times
import org.kosat.swap
import kotlin.math.*
import kotlin.time.TimeSource

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

fun makeExactLevCFL(
  str: List<Σᐩ>,
  radius: Int, // Levenshtein distance
  digits: Int = (str.size * radius).toString().length
): FSA =
  (upArcs(str, radius, digits) +
    diagArcs(str, radius, digits) +
    str.mapIndexed { i, it -> rightArcs(i, radius, it, digits) }.flatten() +
    str.mapIndexed { i, it -> knightArcs(i, radius, it, digits, str) }.flatten())
  .let { Q ->
    val initialStates = setOf("q_" + pd(0, digits).let { "$it/$it" })
    val finalStates = Q.states.filter { it.unpackCoordinates().let { (i, j) -> ((str.size - i + j).absoluteValue == radius) } }

    FSA(Q, initialStates, finalStates)
      .also { it.height = radius; it.width = str.size; it.levString = str }
      .also { println("Levenshtein-${str.size}x$radius automaton had ${Q.size} arcs!") }
  }

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
  monoEditBounds: Pair<Int, Int> = str.size to 0,
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
           (0 < j || i <= monoEditBounds.first) // Prunes bottom right
            && (j < maxRad || i >= monoEditBounds.second - 2) // Prunes top left
            && (1 < j || i <= multiEditBounds.last + 1 || maxRad == 1) // Prunes bottom right
            && (j < maxRad - 1 || i > multiEditBounds.first - 1 || maxRad == 1) // Prunes top left
        }
    }
    .let { Q ->
      val initialStates = setOf("q_" + pd(0, digits).let { "$it/$it" })
      val finalStates = Q.states.filter { it.unpackCoordinates().let { (i, j) -> ((str.size - i + j).absoluteValue <= maxRad) } }

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

fun blockForward(tokens: List<String>, i: Int, pad: Int = 3): List<String> =
  List(pad) { "_" } + tokens.mapIndexed { j, t -> if (j < i) t else "_" } + List(pad) { "_" }

fun blockBackward(tokens: List<String>, i: Int, pad: Int = 3): List<String> =
  List(pad) { "_" } + tokens.mapIndexed { j, t -> if (tokens.size - i < j) t else "_" } + List(pad) { "_" }

// Binary search for the max parsable fragment. Equivalent to the linear search, but faster
fun CFG.maxParsableFragmentB(tokens: List<String>, pad: Int = 3): Pair<Int, Int> {
  val boundsTimer = TimeSource.Monotonic.markNow()
  val monoEditBounds = ((1..tokens.size).toList().binarySearch { i ->
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

  val delta = monoEditBounds.run { second - first }.let { if(it < 0) "$it" else "+$it" }
  println("Mono-edit bounds (R=${monoEditBounds.first}, " +
      "L=${monoEditBounds.second})/${tokens.size} [delta=$delta] in ${boundsTimer.elapsedNow()}")

//  if (monoEditBounds != 0..tokens.size) {
//    println("Mono-edit fragment (R): " + maskEverythingButRange(tokens, 0..monoEditBounds.first).joinToString(" "))
//    println("Mono-edit fragment (L): " + maskEverythingButRange(tokens, monoEditBounds.second..tokens.size).joinToString(" "))
//  }
  return monoEditBounds
}

fun maskEverythingButRange(tokens: List<String>, range: IntRange): List<String> =
  tokens.mapIndexed { i, t -> if (i in range) t else "_" }

var hypothesis = 0
fun CFG.hasMonoEditRepair(tokens: List<String>, unmaskedRange: IntRange, alreadyChecked: IntRange = -1..-1): Boolean =
  maskEverythingButRange(tokens, unmaskedRange).let { premask ->
    val toCheck = if (unmaskedRange.first < 0) List(-unmaskedRange.first) { "_" } + premask
    else if (tokens.size <= unmaskedRange.last) premask + List(unmaskedRange.last - tokens.size) { "_" }
    else premask

    val range = (maxOf(0, unmaskedRange.first) until minOf(tokens.size + 1, unmaskedRange.last + 2))
    val indices = range.toMutableList().apply { if (hypothesis in range) swap(0, hypothesis - range.first) }

    indices.filter { it !in alreadyChecked }.any { i -> (
        (toCheck.mapIndexed { j, t -> if (j == i) "_ _" else t }.joinToString(" ")
//          .also { println(it) }
            in language) // Check both
            && (toCheck.mapIndexed { j, t -> if (j == i) "_" else t } in language // Check substitutions
                || (toCheck.take(i) + "_" + toCheck.drop(i)) in language) // Check insertions
      ).also { if (it) hypothesis = i }
    }
  }

// Tries to shrink multi-edit bounds until it has a single edit repair
fun CFG.tryShrinkingMultiEditBounds(tokens: List<String>, bounds: IntRange): IntRange {
  fun IntRange.tryToShrinkLeft(): IntRange {
    val left = first + 1
    return if (last - 2 <= left || hasMonoEditRepair(tokens, left..last)) first..last
    else (left..last).tryToShrinkLeft()
  }

  fun IntRange.tryToShrinkRight(): IntRange {
    val right = last - 1
    return if (right - 2 <= first || hasMonoEditRepair(tokens, first..right)) first..last
    else (first..right).tryToShrinkRight()
  }

//  val time = TimeSource.Monotonic.markNow()
  val old = bounds.tryToShrinkLeft().tryToShrinkRight()
//  println("Old: $old (${time.elapsedNow()})")
//  val timeNew = TimeSource.Monotonic.markNow()
//  val new = tryToShrinkMultiEditRange(tokens, bounds)
//  println("New: $new (${timeNew.elapsedNow()})")

  return old
}

// Tries to shrink a multi-edit range until it has a single edit repair
fun CFG.tryToShrinkMultiEditRange(tokens: List<String>, range: IntRange): IntRange {
//  println("Trying to shrink multi-edit bounds from $range")
  fun IntRange.tryToShrinkLeft(): IntRange {
    var left = first + 1
    var (start, end) = left to last
    // Binary search for rightmost lower bound
    while (left in (0.. last - 2)) {
      val right = hasMonoEditRepair(tokens, left + 1 until last)
      val me = hasMonoEditRepair(tokens, left until last)
      if (right && !me) break
      else if (!right && !me) { start = left; left += (end - left) / 2 }
      else { end = left; val dec = (left - start) / 2; left -= dec.coerceAtLeast(1) }
    }
    return left.coerceAtLeast(0) until last
  }

  fun IntRange.tryToShrinkRight(): IntRange {
    var right = last
    var (start, end) = first to right
    // Binary search for leftmost lower bound
    while (first < right - 2 && right <= tokens.size) {
      val left = hasMonoEditRepair(tokens, first until right - 1)
      val me = hasMonoEditRepair(tokens, first until right)
      if (left && !me) break
      else if (!left && !me) { end = right; right -= (right - start) / 2 }
      if (0.6 * tokens.size < right - first) return 0..tokens.size
      else { start = right; val inc = (end - right) / 2; right += inc.coerceAtLeast(1) }
    }
    return first..right.coerceAtMost(tokens.size)
  }

  return range.tryToShrinkLeft().tryToShrinkRight()
}

// Tries to grow single-edit bounds from both sides until it must have a multi-edit repair, then shrinks it until minimal
fun CFG.tryGrowingMonoEditBounds(tokens: List<String>, range: IntRange, i: Int = 0): IntRange {
//  println("Trying to grow mono-edit bounds from $range")
  fun IntRange.expandBothSides(): IntRange =
    (first - (first.toDouble() / 2).roundToInt().coerceAtLeast(1)).coerceAtLeast(0) ..
        (last + ((tokens.size - last).toDouble() / 2).toInt().coerceAtLeast(1)).coerceAtMost(tokens.size)

  val expandedRange = range.expandBothSides()
  val hasMonoEditRepair = hasMonoEditRepair(tokens, expandedRange)

  return if (hasMonoEditRepair && range == expandedRange) range
  else if (hasMonoEditRepair) tryGrowingMonoEditBounds(tokens, expandedRange, i+1)
  else tryToShrinkMultiEditRange(tokens, expandedRange)
}

/**
 * Returns a minimal range that must contain a multi-edit repair. A minimal range,
 *
 * (1) Must not contain any single-edit repair within the specified range.
 * (2) No substring of that range can provably contain at least two edits.
 *
 * If no such range exists, returns vacuous bounds (i.e., the entire string).
 * If more than one such range exists, returns the first minimal range found.
 */

fun CFG.findMinimalMultiEditBounds(tokens: List<String>, pair: Pair<Int, Int>, levDist: Int): IntRange {
  val meBoundsTimer = TimeSource.Monotonic.markNow()
  val (left, right) = (min(pair.first, pair.second) - levDist) to (max(pair.first, pair.second) + levDist)

  val range = left until right
  val multiEditBounds = if (right - left <= 1) 0..tokens.size
  else if (hasMonoEditRepair(tokens, range)) tryGrowingMonoEditBounds(tokens, range)
  else tryToShrinkMultiEditRange(tokens, range)

  println("Multi-edit bounds (lower=${multiEditBounds.first}, " +
      "upper=${multiEditBounds.last})/${tokens.size} in ${meBoundsTimer.elapsedNow()}")

  if (multiEditBounds != 0..tokens.size)
    println("Shrunken multiedit fragment: " + maskEverythingButRange(tokens, multiEditBounds).joinToString(" "))

  return multiEditBounds
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