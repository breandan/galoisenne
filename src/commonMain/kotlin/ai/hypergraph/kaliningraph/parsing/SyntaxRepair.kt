package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.*
import ai.hypergraph.kaliningraph.sampling.choose
import ai.hypergraph.kaliningraph.types.powerset
import kotlin.math.absoluteValue
import ai.hypergraph.kaliningraph.types.cache
import ai.hypergraph.kaliningraph.types.Π2A
import kotlin.math.pow
import kotlin.time.*

var MAX_SAMPLE = 20 // Maximum number of repairs to sample
var MAX_TOKENS = 80 // Maximum number of tokens per repair
var TIMEOUT_MS = 90_000 // Timeout for each repair attempt (default, modify elsewhere)
var MAX_REPAIR = 2 // Maximum number of edits per repair

typealias Reconstructor = MutableList<Π2A<Σᐩ>>
// Takes a string and a set of invariant indices and returns mutated strings
typealias Mutator = (Σᐩ, Set<Int>) -> Sequence<Σᐩ>

// Terminals which are blocked from being synthesized by a solver
val CFG.blocked: MutableSet<Σᐩ> by cache { mutableSetOf() }

fun repair(
  prompt: Σᐩ,
  cfg: CFG,
  coarsen: Σᐩ.() -> Σᐩ = { this },
  uncoarsen: Σᐩ.(Σᐩ) -> Σᐩ = { this },
  synthesizer: CFG.(List<Σᐩ>) -> Sequence<Σᐩ>,
  updateProgress: (Σᐩ) -> Unit = {},
  filter: (Σᐩ.() -> Boolean)? = null,
  diagnostic: ((String) -> Unit)? = null,
  score: (Σᐩ) -> Float = { levenshtein(it, prompt).toFloat() },
  variations: List<Mutator> =
    listOf(
      { a, b -> a.randomInsertions() },
      { a, b -> a.randomDeletions(b) },
      { a, b -> a.randomSingleSubtitutions(exclusions = b) },
      { a, b -> a.randomDoubleSubstitutions(numberOfEdits = MAX_REPAIR, exclusions = b) }
    )
): List<Σᐩ> {
  val coarsened = prompt.coarsen()
  println("Repairing: $prompt" + if (coarsened != prompt) "\nCoarsened: $coarsened" else "" )

//  if (cfg.parse(coarsened) != null) return emptyList()
  val tokens = coarsened.tokenizeByWhitespace()
  val tokensWithHoles = tokens.map { if (it in cfg.terminals) it else HOLE_MARKER }
  val sanitized: Σᐩ = tokensWithHoles.joinToString(" ")

  var totalSamples = 0

  val t = TimeSource.Monotonic.markNow()
  val repairs: List<Σᐩ> = sanitized.synthesizeWithVariations(
    cfg = cfg,
    synthesizer = synthesizer,
    allowNTs = false,
    updateProgress = updateProgress,
    takeMoreWhile = { t.elapsedNow().inWholeMilliseconds < TIMEOUT_MS },
    variations = variations,
  )
    .map { totalSamples++; it.uncoarsen(prompt) }
    .let { if (filter != null) it.filter(filter) else it }
    .let { if (diagnostic != null) it.onEach { diagnostic(it) } else it }
    .map { it to score(it) }
    .take(MAX_SAMPLE).toList().sortedBy { it.second }
    .also { println("Best score: (${it.firstOrNull()?.second})") }
    .map { it.first.trim() }

  if (filter != null) println("Filtered out ${totalSamples - repairs.size}/${totalSamples} invalid samples!")

  return repairs
}

fun repairLazily(
  prompt: Σᐩ,
  cfg: CFG,
  edits: Int = 3,
  coarsen: Σᐩ.() -> Σᐩ = { this },
  uncoarsen: Σᐩ.(Σᐩ) -> Σᐩ = { this },
  synthesizer: CFG.(List<Σᐩ>) -> Sequence<Σᐩ>,
  filter: (Σᐩ.() -> Boolean)? = null,
): Sequence<Σᐩ> {
  println("Repairing: $prompt")
  val coarsened = prompt.coarsen()
//  if (cfg.parse(coarsened) != null) return emptyList()
  val tokens = coarsened.tokenizeByWhitespace()
  val tokensWithHoles = tokens.map { if (it in cfg.terminals) it else HOLE_MARKER }
  val sanitized: Σᐩ = tokensWithHoles.joinToString(" ")

  val variations: List<Mutator> =
    listOf({ a, b -> a.randomDoubleSubstitutions(numberOfEdits = edits, exclusions = b)})
  var totalSamples = 0
  return sanitized.synthesizeWithVariations(
    cfg = cfg,
    synthesizer = synthesizer,
    allowNTs = false,
    variations = variations,
  )
    .map { totalSamples++; it.uncoarsen(prompt) }
    .let { if (filter != null) it.filter(filter) else it }
}

fun List<Σᐩ>.isSetValiantOptimalFor(cfg: CFG): Boolean =
    none { it.isNonterminalStubIn(cfg) } &&
    (cfg.terminals - cfg.blocked).size.toDouble().pow(count { it.isHoleTokenIn(cfg) }) < 512

// Generates a lazy sequence of mutations for a broken string
// and feeds them to the synthesizer for completion.
fun Σᐩ.synthesizeWithVariations(
  cfg: CFG,
  allowNTs: Boolean = true,
  enablePruning: Boolean = false,
  variations: List<Mutator> = listOf({ a, b -> sequenceOf() }),
  takeMoreWhile: () -> Boolean = { true },
  updateProgress: (Σᐩ) -> Unit = {},
  synthesizer: CFG.(List<Σᐩ>) -> Sequence<Σᐩ>
): Sequence<Σᐩ> {
  val cfg_ = (if (!allowNTs) cfg.noNonterminalStubs else cfg).freeze()

  val (stringToSolve, reconstructor: Reconstructor) =
    if (enablePruning) cfg_.prune(this) else this to mutableListOf()
  if (this != stringToSolve) println("Before pruning: $this\nAfter pruning: $stringToSolve")

  val tokens = stringToSolve.tokenizeByWhitespace()
  if (MAX_TOKENS < tokens.size) return sequenceOf<Σᐩ>()
    .also { println("Too many tokens: $stringToSolve") }

  val recStubs = reconstructor.map { it.first }.toSet()
  val exclude =
      tokens.indices.filter { i -> tokens[i].let { it in cfg_.blocked || it in recStubs } }.toSet()

  val allVariants: Sequence<Σᐩ> =
    variations.fold(sequenceOf(stringToSolve)) { a, b -> a + b(stringToSolve, exclude) }.distinct()
//      .filter { !cfg_.containsImpossibleBigram(it) }

  return allVariants
    .filter { s -> s.tokenizeByWhitespace().any { it.isHoleTokenIn(cfg) } }
    .onEach { updateProgress(it) }
    .flatMap { variant ->
      val variantTokens = variant.tokenizeByWhitespace()
      cfg_.run { synthesizer(variantTokens) }
//        .ifEmpty { cfg_.rememberBigramPolarity(variantTokens, synthesizer) }
//        .map { cfg_.rememberPossibleBigrams(variantTokens); it }
    }.takeWhile { takeMoreWhile() }.distinct()
//    .distinctBy(levenshteinFingerprint)
    .map {
      val rec: Reconstructor = reconstructor.toList().toMutableList()
      it.tokenizeByWhitespace().mapIndexed { i, it ->
        if ("ε" in it) ""
        else if (it.isNonterminalStubIn(cfg_) && it == rec.firstOrNull()?.first) rec.removeFirst().second
        else it
      }.filter { it.isNotBlank() }.joinToString(" ")
    }
}

/**
 * Attempts to reduce parsable subsequences into a single token to reduce total
 * token count, e.g. ( w ) + _ => <S> + _ resulting in two fewer tokens overall.
 * Consider 3 + 5 * _ != <S> * _ for checked arithmetic, so context-insensitive
 * pruning is not always sound, thus we should err on the side of caution.
 *
 * TODO: A proper solution requires ruling out whether the left- and right-
 *       quotients of the root nonterminal ever yield another derivation.
 */

fun CFG.prune(
  string: Σᐩ,
  minimumWidth: Int = 4,
  // Maps nonterminal stubs from pruned branches back to original string
  reconstructor: Reconstructor =
    string.tokenizeByWhitespace().filter { it.isNonterminalStubIn(this) }
      .map { it to it }.toMutableList()
): Pair<Σᐩ, Reconstructor> {
  val tokens = string.tokenizeByWhitespace()
  val stubs = parseInvalidWithMaximalFragments(string)

  val treesToBeChopped =
    stubs.filter { "START" in unitReachability[it.root]!! }
      .map { it.span to it }.let {
        val (spans, trees) = it.unzip()
        // Find trees corresponding to ranges which have an unambiguous parse tree
        trees.filter { tree ->
          minimumWidth < tree.span.run { last - first } &&
            spans.filter { it != tree.span }
              .none { tree.span.intersect(it).isNotEmpty() }
        }
      }

  var totalPruned = 0
  var previousNonterminals = 0
  val prunedString = tokens.indices.mapNotNull { i ->
    val possibleTree = treesToBeChopped.firstOrNull { i in it.span }
    if (possibleTree != null)
      if (i == possibleTree.span.first) "<${possibleTree.root}>".also {
        val (a, b) = it to possibleTree.contents()
        println("Reduced: $b => $a")
        reconstructor.add(previousNonterminals++, a to b)
      } else { totalPruned++; null }
    else tokens[i].also { if (it.isNonterminalStubIn(this)) previousNonterminals++ }
  }.joinToString(" ")

  println("Pruned $totalPruned tokens in total")
  return if (totalPruned == 0) string to reconstructor
  else prune(prunedString, minimumWidth, reconstructor)
}

// TODO: implement complete substring decider
// https://nokyotsu.com/me/papers/cic01.pdf
// https://cs.stackexchange.com/questions/154130/minimal-length-strings-which-are-substrings-of-no-string-in-a-given-cfl
// These strings must never appear in any length-k string in the language defined by this grammar
val CFG.impossibleBigrams by cache { mutableSetOf<Σᐩ>() }
// Underapproximates impossible substrings for a sketch template of a given length by tracking
// the impossible substrings that cannot fit inside an equal- or longer-length string, i.e., if
// a string does not fit in Σ^100, then it definitely will not fit in Σ^k<100. In the worst case
// it will be a false negative and we do unnecessary work trying to solve an impossible template.

// These strings all appear in an arbitrary-length string in the language defined by this grammar
val CFG.possibleBigrams by cache { mutableSetOf<Σᐩ>() }

fun CFG.containsImpossibleBigram(str: Σᐩ): Boolean =
  str.tokenizeByWhitespace().windowed(2).any { bigram ->
    val bg = bigram.joinToString(" ")
    (bg in impossibleBigrams).also {
      if (it) println("$str was rejected because it contains an impossible bigram: $bg")
    }
  }

val CFG.startSymbols by cache { mutableSetOf(START_SYMBOL) }
fun CFG.rememberPossibleBigrams(str: List<Σᐩ>) =
  possibleBigrams.addAll(str.windowed(2).asSequence().map { it.joinToString(" ")})
// Caches possible and impossible bigrams in the language defined by this grammar on a per-query basis
fun CFG.rememberBigramPolarity(str: List<Σᐩ>, synthesizer: CFG.(List<Σᐩ>) -> Sequence<Σᐩ>): Sequence<Σᐩ> =
  str.windowed(2).asSequence().filter {
    it.all { it in terminals } && it.joinToString(" ") !in (possibleBigrams + impossibleBigrams)
  }.forEach {
    val holes = List(8) { HOLE_MARKER }.joinToString(" ")
    val substring = it.joinToString(" ")
    val tokens = "$holes $substring $holes".tokenizeByWhitespace()

    startSymbols.addAll(nonterminals) // If anything can be derived from the whole string, it is "possible"
    val blockers = blocked.toSet()
    blocked.removeAll(blockers)

    if (synthesizer(tokens).firstOrNull() == null)
      impossibleBigrams.add(substring.also { println("\"$it\" determined to be an impossible bigram using:\n${prettyPrint()}\n") })
    else possibleBigrams.add(substring)

    startSymbols.removeAll { it != START_SYMBOL }
    blocked.addAll(blockers)
  }.let { emptySequence() }

// TODO: Instead of haphazardly splattering holes everywhere and hoping to hit the lottery
//       we should work out a principled way to localize holes using the language quotient.
//       For example, we can do this bottom-up, by localizing substrings which are known to
//       be outside the language, e.g., for the following grammar and string:
//             E → E+E | E*E | (E) | x                      (+)+x*x+x+(x*x)
//       we know that the substring (+) cannot be in the grammar, so we can infer (_+_).
//             https://nokyotsu.com/me/papers/cic01.pdf
//
// Idea: Generate minimal strings which cannot be repaired by left or right insertion,
//       these will become our initial set. Whenever we encounter one of these substrings
//       in the candidate string, we know that without repairing that part of the string
//       candidate, its full string can never be in the language defined by the given CFG.
//
//       { S | |S| < k & !∃ S' ∈ L(CFG) s.t. S is a substring of S' }
//       This will help us refine where the repairs must happen.

fun List<Tree>.allIndicesInsideParseableRegions(): Set<Int> =
  map { it.span }.filter { 3 < it.last - it.first }
    .flatMap { (it.first + 1) until it.last }.toSet()

/*
 * Generates all single character replacements and insertions.
 * Original: www
 * Variants: _www w_ww ww_w www_
 *           _ww w_w ww_
 */

fun Σᐩ.singleTokenSubtitutionsAndInsertions(): Sequence<Σᐩ> =
  multiTokenSubstitutionsAndInsertions(numberOfEdits = 1)

fun Σᐩ.randomInsertions(
  tokens: List<Σᐩ> = tokenizeByWhitespace() + "",
  numberOfEdits: Int = 1,
): Sequence<Σᐩ> =
  tokens.indices.toSet().let { sortedIndices ->
    (1..numberOfEdits).asSequence().flatMap { sortedIndices.choose(it) }
  }.map { idxs -> tokens.substitute(idxs) { it, _ -> "_ $it" } }

fun Σᐩ.randomDeletions(
  exclusions: Set<Int>,
  tokens: List<Σᐩ> = tokenizeByWhitespace() + "",
  numberOfEdits: Int = 1,
): Sequence<Σᐩ> =
  tokens.indices.toSet().filterNot { it in exclusions }.let { sortedIndices ->
    (1..numberOfEdits).asSequence().flatMap { sortedIndices.choose(it) }
  }.map { idxs -> tokens.substitute(idxs) { it, _ -> "_" } }

fun Σᐩ.randomSingleSubtitutions(
  tokens: List<Σᐩ> = tokenizeByWhitespace(),
  numberOfEdits: Int = 1,
  exclusions: Set<Int> = setOf(),
): Sequence<Σᐩ> =
  tokens.indices.toSet().let { sortedIndices ->
    (1..numberOfEdits).asSequence().flatMap { sortedIndices.choose(it) }
  }.map { idxs -> tokens.substitute(idxs) { it, i -> if (i in exclusions) "$it _" else "_" } }

fun Σᐩ.randomDoubleSubstitutions(
  tokens: List<Σᐩ> = tokenizeByWhitespace(),
  padded: List<Σᐩ> = listOf("", *tokens.toTypedArray(), ""),
  numberOfEdits: Int = minOf(2, tokens.size),
  exclusions: Set<Int> = setOf(),
  shiftedExclusions: Set<Int> = exclusions.map { it + 1 }.toSet(),
): Sequence<Σᐩ> =
  (padded.indices.toSet())//.also { println("Exclusions: $exclusions") })// - exclusions.map { it + 1 }.toSet())
    .let { sortedIndices -> (1..numberOfEdits).asSequence().flatMap { sortedIndices.choose(it) } }
    .map { idxs -> padded.substitute(idxs) { it, i -> if (i in shiftedExclusions) "_ $it _" else "_ _" } }

fun Σᐩ.multiTokenSubstitutionsAndInsertions(
  tokens: List<Σᐩ> = tokenizeByWhitespace(),
  padded: List<Σᐩ> = listOf("", *tokens.toTypedArray(), ""),
  numberOfEdits: Int = minOf(2, tokens.size),
  exclusions: Set<Int> = setOf(),
  // Sorted list of locations believed to be erroneous
  fishyLocations: List<Int> = listOf(tokens.size)
): Sequence<Σᐩ> =
  allSubstitutions(padded.indices.toSet() - exclusions.map { it + 1 }.toSet(), numberOfEdits, fishyLocations)
    .map { idxs -> padded.substitute(idxs) { _, _ -> "_ _" } }
//    .apply {
//      println("Exclusions: ${tokens.mapIndexed { i, it -> if (i !in exclusions) HOLE_MARKER.padEnd(it.length) else it }.joinToString(" ")}")
//      println("Fishy toks: ${tokens.mapIndexed { i, it -> if (i in fishyLocations) HOLE_MARKER.padEnd(it.length) else it }.joinToString(" ")}")
//    }

fun allSubstitutions(eligibleIndices: Set<Int>, numEdits: Int, fishyLocations: List<Int>) =
  eligibleIndices.sortedWith(
    compareBy<Int> { a -> fishyLocations.minOf { b -> (a - b).absoluteValue } }
      .thenBy { (it - fishyLocations.first()).absoluteValue }
  ).let { sortedIndices -> setOf(1, numEdits)
    .asSequence().flatMap { sortedIndices.choose(it) } }
//  setOf(1, numEdits).asSequence()
//    .flatMap { eligibleIndices.choose(it) }.map { it.sorted().toSet() }
//    .sortedWith(
//      compareBy<Set<Int>> { it.size }
//        // Out of all chosen indices, how far apart from its nearest fishy neighbor
//        // is the chosen index whose nearest fishy neighbor is the farthest apart?
//        .thenBy { it.maxOf { a -> fishyLocations.minOf { b -> abs(a - b) } } }
//  //  .thenBy { it.sumOf { a -> fishyLocations.indices.minBy { abs(a - fishyLocations[it]) } } } // Sort by precedence?
//        .thenBy { it.fold(0 to it.first()) { (a, b), it -> a + abs(it - b) to it }.first } // Sort by dispersion?
//        .thenBy { a -> a.sumOf { abs(fishyLocations.first() - it) } } // Sort by distance to first fishy location (caret)
//    ).map { it.toSet() }

fun List<Σᐩ>.substituteIndices(idxs: Set<Int>, sub: (Σᐩ, Int) -> Σᐩ): List<Σᐩ> =
  mapIndexed { i, it -> if (i !in idxs) it else sub(it, i) }

private fun List<Σᐩ>.substitute(idxs: Set<Int>, sub: (Σᐩ, Int) -> Σᐩ): Σᐩ =
  substituteIndices(idxs, sub).joinToString(" ").trim()

fun Σᐩ.tokenizeByWhitespace(): List<Σᐩ> = split(Regex("\\s+")).filter { it.isNotBlank() }

// MUCH faster than above (but incorrect)
//fun Σᐩ.tokenizeByWhitespace(): List<Σᐩ> =
//  mutableListOf<Σᐩ>().also { list ->
//    var start = 0
//    var end = 0
//    while (end < length) {
//      while (end < length && this[end].isWhitespace()) end++
//      if (end > start) list.add(substring(start, end))
//      start = end
//      while (end < length && !this[end].isWhitespace()) end++
//      if (end > start) list.add(substring(start, end))
//      start = end
//    }
//  }

/*
 * Treats contiguous underscores as a single hole and lazily enumerates every
 * hole configuration in the powerset of all holes within a snippet.
 * Original: ___w__w_w__w___ -> _w_w_w_w_
 * Variants: _wwww  _w_www _w_w_ww ... _w_w_w_w_
 *           w_www  _ww_ww _w_ww_w
 *           ww_ww  _www_w _w_www_
 *           ...    ...    ...
 */

fun Σᐩ.everySingleHoleConfig(): Sequence<Σᐩ> {
  val new = replace(Regex("($HOLE_MARKER( )*)+"), "$HOLE_MARKER ")
  val toks = new.tokenizeByWhitespace()
  val indices = toks.indices.filter { toks[it] == HOLE_MARKER }.powerset()
  return indices.map { ids -> toks.drop(setOf(HOLE_MARKER), ids).joinToString(" ") }
}

/*
 * Lazily enumerates all underscores chunkings in order of increasing length up
 * to the lesser of (1) its original size or (2) the longest underscore chunk.
 * Original: ___w__w_w__w___
 * Variants: _w_w_w_w_
 *           __w__w_w__w__
 *           ___w__w_w__w___
 */

fun Σᐩ.mergeHoles(): Σᐩ =
  replace(Regex("\\s+"), " ")
    .replace(Regex("(?<=_)\\s(?=_)"), "")

fun Σᐩ.increasingLengthChunks(): Sequence<Σᐩ> {
  val chunks = mergeHoles().split(Regex("((?<=[^_])|(?=[^_]))"))
  return (2..chunks.maxOf { it.length }).asSequence()
    .map { l -> chunks.joinToString("") { if (it.containsHole()) it.take(l).toCharArray().joinToString(" ") else it } }
}
