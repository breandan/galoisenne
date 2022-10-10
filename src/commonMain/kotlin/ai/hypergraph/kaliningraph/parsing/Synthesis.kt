package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.types.cache
import ai.hypergraph.kaliningraph.types.isStrictSubsetOf

typealias Reconstructor = MutableList<Pair<String, String>>

// Generates a lazy sequence of solutions to sketch-based synthesis problems
fun String.synthesizeWithVariations(
  cfg: CFG,
  allowNTs: Boolean = true,
  enablePruning: Boolean = false,
  variations: List<String.() -> Sequence<String>> = listOf({ sequenceOf() }),
  updateProgress: (String) -> Unit = {},
  skipWhen: (List<String>) -> Boolean = { false },
  synthesizer: (List<String>, Reconstructor) -> Sequence<String>
): Sequence<String> {
  val cfg_ = if (!allowNTs) cfg.noNonterminalStubs else cfg

  val (stringToSolve, reconstructor) =
    if (enablePruning) cfg.prune(this) else this to mutableListOf()
  if (this != stringToSolve) println("Before pruning: $this\nAfter pruning: $stringToSolve")

  val allVariants: Sequence<String> =
    variations.fold(sequenceOf(stringToSolve)) { a, b -> a + b() }
      .distinct().rejectTemplatesContainingImpossibleBigrams(cfg)
  return allVariants.map { updateProgress(it); it }
    .flatMap {
      val variantTokens = tokenize(it)
      if (skipWhen(variantTokens)) emptySequence()
      else cfg_.run { synthesizer(variantTokens, reconstructor) }
        .ifEmpty {
          variantTokens.rememberImpossibleBigrams(cfg, synthesizer)
          emptySequence()
        }
    }.distinct()
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
  string: String,
  minimumWidth: Int = 4,
  // Maps nonterminal stubs from pruned branches back to original string
  reconstructor: Reconstructor =
    tokenize(string).filter { it.isNonterminalStubIn(this) }
      .map { it to it }.toMutableList()
): Pair<String, Reconstructor> {
  val tokens = tokenize(string)
  val stubs = parseWithStubs(string).second
    .fold(setOf<Tree>()) { acc, t ->
      if (acc.any { t.span isStrictSubsetOf it.span }) acc else acc + t
    }.sortedBy { it.span.first }

  val treesToBeChopped =
    stubs.filter { "START" in equivalenceClass(setOf(it.root)) }
      .map { it.span to it }.let {
        val (spans, trees) = it.unzip()
        // Find trees corresponding to ranges which have an unambiguous parse tree
        trees.filter { tree ->
          minimumWidth < tree.span.run { last - first } &&
            spans.filter { it != tree.span }
              .none { tree.span.intersect(it).isNotEmpty() }
        }
      }//.onEach { println(it.prettyPrint()) }

  if (treesToBeChopped.isEmpty()) string to reconstructor

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
val CFG.impossibleBigrams by cache { mutableMapOf<Int, Set<String>>() }
// Underapproximates impossible substrings for a sketch template of a given length by tracking
// the impossible substrings that cannot fit inside an equal- or longer-length string, i.e.,
// if a string does not fit in Σ^100, then it definitely will not fit in Σ^k<100. In the worst case
// it will be a false negative and we do unnecessary work trying to solve an impossible template.
fun Map<Int, Set<String>>.unableToFitInside(k: Int): Set<String> =
  values.flatten().toSet() // May not work for ngrams but for bigrams it should be fine
//  keys.filter { k <= it }.flatMap { this[it] ?: setOf() }.toSet()

// These strings all appear in an arbitrary-length string in the language defined by this grammar
val CFG.possibleBigrams by cache { mutableSetOf<String>() }

fun Sequence<String>.rejectTemplatesContainingImpossibleBigrams(cfg: CFG) =
  filter { sketch ->
    val numTokens = sketch.count { it == ' ' }
    cfg.impossibleBigrams.unableToFitInside(numTokens).none { iss ->
      (iss in sketch).also {
        if (it) println("$sketch rejected because it contains an impossible bigram: $iss")
      }
    }
  }

fun List<String>.rememberImpossibleBigrams(
  cfg: CFG,
  synthesizer: (List<String>, Reconstructor) -> Sequence<String>
) {
  windowed(2).asSequence().filter {
    it.all { it in cfg.terminals } && it.joinToString(" ") !in cfg.possibleBigrams
  }.forEach {
    val holes = List((size / 2).coerceIn(4..8)) { "_" }.joinToString(" ")
    val substring = it.joinToString(" ")
    val tokens = tokenize("$holes $substring $holes")
    if (synthesizer(tokens, mutableListOf()).firstOrNull() == null)
      cfg.impossibleBigrams[tokens.size] =
        cfg.impossibleBigrams.getOrPut(tokens.size) { setOf() } + substring
    else cfg.possibleBigrams.add(substring)
  }
}
