package ai.hypergraph.kaliningraph.repair

import ai.hypergraph.kaliningraph.*
import ai.hypergraph.kaliningraph.parsing.*
import ai.hypergraph.kaliningraph.sampling.choose
import ai.hypergraph.kaliningraph.types.*
import kotlin.jvm.JvmName
import kotlin.math.*
import kotlin.time.TimeSource

val COMMON_BRACKETS = "()[]{}".map { "$it" }.toSet()
fun Σᐩ.defaultTokenizer(): List<Σᐩ> =
  split(Regex("[\\(\\)\\[\\]{}]|___".let { "((?<=($it))|(?=($it)))" }))

fun minimizeFixStr(
  broke: Σᐩ,
  tokenize: Σᐩ.() -> List<Σᐩ>,
  fixed: Σᐩ,
  separator: Σᐩ = "",
  isValid: Σᐩ.() -> Boolean
): Π3A<Σᐩ> {
  //    val startTime = TimeSource.Monotonic.markNow()
  val (brokeTokens, fixedTokens) = broke.tokenize() to fixed.tokenize()

//  val brokeJoin = brokeTokens.joinToString("")
  val fixedJoin = fixedTokens.joinToString("")
//  val pdiffTok = prettyDiffs(listOf(brokeJoin, fixedJoin), listOf("broken", "original fix"))

  val patch: Patch = extractPatch(brokeTokens, fixedTokens)
  val time = TimeSource.Monotonic.markNow()
  val minEdit: List<Int> = deltaDebug(
    patch.changedIndices(),
    timeout = { 5 < time.elapsedNow().inWholeSeconds }
  ) { idxs -> patch.apply(idxs, separator).isValid() }
// deltaDebug only minimizes contiguous chunks, so here we find the minimal configuration of edits (if tractable)
    // Computing the patch powerset takes 2^n so is only really tractable for relatively small patches, which is
    // why we first apply the DD minimizer to reduce contiguous runs. This is a heuristic and not guaranteed to
    // find the absolute minimum, but should be close enough for most purposes.
    .let { if (it.size < 8) it.minimalSubpatch { patch.apply(this).isValid() } else it }

//  val pdiff = prettyDiffs(listOf(brokeJoin, minFix), listOf("broken", "minimized fix"))
//  if(pdiff.any { it == '\u001B' } && pdiffTok.filter { !it.isWhitespace() } != pdiff.filter { !it.isWhitespace() }) println(pdiffTok + "\n\n" + pdiff)

//    println("Reduced from ${patch.changes().size} to ${minEdit.size} edits in ${startTime.elapsedNow().inWholeMilliseconds}ms")

//    if(!minFix.isValidPython()) println("Minimized fix is invalid Python: $minFix")

  val minfix = patch.apply(minEdit, separator)

  return broke to fixedJoin to minfix
}

fun minimizeFix(
  brokeTokens: List<Σᐩ>,
  fixedTokens: List<Σᐩ>,
  isValid: Σᐩ.() -> Boolean
): Sequence<Σᐩ> {
  val patch: Patch = extractPatch(brokeTokens, fixedTokens)
  val changedIndices = patch.changedIndices()
  val time = TimeSource.Monotonic.markNow()
  return deltaDebug(changedIndices,
    timeout = { 5 < time.elapsedNow().inWholeSeconds }
  ) { idxs -> patch.apply(idxs, " ").isValid() }
    .minimalSubpatches { patch.apply(this).isValid() }
    .map { patch.apply(it, " ").tokenizeByWhitespace().joinToString(" ") }
}

fun minimizeFixInt(
  brokeTokens: List<Int>,
  fixedTokens: List<Int>,
  isValid: List<Int>.() -> Boolean
): Sequence<List<Int>> {
  val patch = extractPatch(brokeTokens, fixedTokens)
  val changedIndices = patch.changedIndices()
  val time = TimeSource.Monotonic.markNow()
  return deltaDebug(changedIndices, n = 1,
    timeout = { 5 < time.elapsedNow().inWholeSeconds }
  ) { idxs -> patch.apply(idxs).isValid() }
    .minimalSubpatches { patch.apply(this).isValid() }
    .map { patch.apply(it) }
}

typealias Edit = Π2A<Σᐩ>
typealias Patch = List<Edit>
val Edit.old: Σᐩ get() = first
// If new is empty, then this is a deletion
val Edit.new: Σᐩ get() = second
fun Patch.prettyPrint(): String = unzip().let { (a, b) ->
  a.mapIndexed { i, s ->
    val padded = s.padEnd(max(s.length, b[i].length))
    if (b[i].isEmpty()) "$ANSI_RED_BACKGROUND$padded$ANSI_RESET"
    else if (s.isEmpty() || s == b[i]) padded
    else "$ANSI_YELLOW_BACKGROUND$padded$ANSI_RESET"
 }.joinToString(" ") + "\n" + b.mapIndexed { i, s ->
    val padded = s.padEnd(max(s.length, a[i].length))
    if (a[i].isEmpty()) "$ANSI_GREEN_BACKGROUND$padded$ANSI_RESET"
    else if (s.isEmpty() || s == a[i]) padded
    else "$ANSI_YELLOW_BACKGROUND$padded$ANSI_RESET"
  }.joinToString(" ")
}

// returns when there are at least two types of edits (insertions, deletions, changes) choose 2
fun Patch.isInteresting() = changedIndices().let { ch ->
  filterIndexed { index, pair -> index in ch }
    .map { (a, b) -> if (b == "") "D" else if(a == "") "I" else "C" }
    .toSet().size > 1
}
fun Patch.changedIndices(): List<Int> = indices.filter { this[it].old != this[it].new }

@JvmName("changedIndicesInt")
fun List<Pair<Int, Int>>.changedIndices(): List<Int> =
  indices.filter { this[it].run { first != second } }

fun Patch.scan(i: Int, direction: Boolean, age: Edit.() -> Σᐩ): Σᐩ? =
  (if (direction) (i + 1..<size) else (i - 1 downTo 0))
    .firstOrNull { this[it].age() != "" }?.let { this[it].age() }

// Scan [l]eft/[r]ight for first non-empty [n]ew/[o]ld token
fun Patch.sln(i: Int): String = scan(i, false) { new }!!
fun Patch.srn(i: Int): String = scan(i, true) { new }!!
fun Patch.slo(i: Int): String = scan(i, false) { old }!!
fun Patch.sro(i: Int): String = scan(i, true) { old }!!

fun Patch.totalCharacterwiseEditDistance(): Int =
  filter { (a, b) -> a != b }
    .sumOf { (a, b) -> levenshtein(a, b) }

fun List<Int>.minimalSubpatch(filter: List<Int>.() -> Boolean): List<Int> =
  (1..size).asSequence().map { choose(it).map { it.toList() } }
    .map { it.filter { it.filter() } }.firstOrNull { it.any() }?.firstOrNull() ?: this

fun List<Int>.minimalSubpatches(filter: List<Int>.() -> Boolean): Sequence<List<Int>> =
  (1..size).asSequence().map { choose(it).map { it.toList() } }
    .map { it.filter { it.filter() } }.firstOrNull { it.any() } ?: sequenceOf(this)

fun List<Pair<Int, Int>>.apply(indices: List<Int>): List<Int> =
  mapIndexed { i, it -> if (i in indices) it.second else it.first }

fun Patch.apply(indices: List<Int>, separator: Σᐩ = " "): Σᐩ =
  mapIndexed { i, it -> if (i in indices) it.new else it.old }.joinToString(separator)

fun Patch.apply(separator: Σᐩ = ""): Σᐩ = joinToString(separator) { it.new }

fun extractPatch(original: List<Σᐩ>, new: List<Σᐩ>): Patch =
  levenshteinAlign(original, new).map { (old, new) ->
    when {
      old == null -> "" to new!!
      new == null -> old to ""
      else -> old to new
    }
  }

@JvmName("extractPatchInt")
fun extractPatch(original: List<Int>, new: List<Int>): List<Pair<Int, Int>> =
  levenshteinAlign(original, new).map { (old, new) ->
    when {
      old == null -> -1 to new!!
      new == null -> old to -1
      else -> old to new
    }
  }

fun <T> deltaDebug(elements: List<T>, n: Int = 2, timeout: () -> Boolean, checkValid: (List<T>) -> Boolean): List<T> {
  // If n granularity is greater than number of tests, then finished, simply return passed in tests
  if (elements.size < n || timeout()) { return elements }

  // Cut the elements into n equal chunks and try each chunk
  val chunkSize = (elements.size.toDouble() / n).roundToInt()

  val chunks = elements.windowed(chunkSize, chunkSize, true)

  var index = 0
  for (chunk in chunks) {
    if (timeout()) break
    val otherChunk = elements.subList(0, index*chunkSize) +
      elements.subList(min((index+1)*chunkSize, elements.size), elements.size)

    // Try to other, complement chunk first, with theory that valid elements are closer to end
    if (checkValid(otherChunk)) return deltaDebug(otherChunk, 2, timeout, checkValid)

    // Check if running this chunk works
    if (checkValid(chunk)) return deltaDebug(chunk, 2, timeout, checkValid)
    index++
  }

  // If size is equal to number of chunks, we are finished, cannot go down more
  if (elements.size == n) return elements

  // If not chunk/complement work, increase granularity and try again
  return if (elements.size < n * 2) deltaDebug(elements, elements.size, timeout, checkValid)
  else deltaDebug(elements, n * 2, timeout, checkValid)
}
