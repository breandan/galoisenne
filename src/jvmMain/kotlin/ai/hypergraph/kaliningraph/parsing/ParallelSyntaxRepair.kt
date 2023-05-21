import ai.hypergraph.kaliningraph.*
import ai.hypergraph.kaliningraph.parsing.*
import ai.hypergraph.kaliningraph.sampling.*
import kotlin.streams.*
import kotlin.time.*

fun <E> ((Int, Int) -> Sequence<E>).parallelize(
  cores: Int = Runtime.getRuntime().availableProcessors()
) =
  (0 until cores).toSet().parallelStream()
  .flatMap { i -> this(cores, i).asStream() }


//@OptIn(ExperimentalTime::class)
fun bijectiveRepair(
  toRepair: Σᐩ,
  fillers: Set<Σᐩ>,
  edits: Int = 2,
  takeMoreWhile: () -> Boolean = { true },
  filter: Σᐩ.() -> Boolean = { true },
  diagnostic: ((Σᐩ) -> Unit)? = null,
  scoreString: ((Σᐩ) -> Double)? = null,
  scoreRepair: (Σᐩ) -> Double = { levenshtein(it, toRepair).toDouble() },
): List<Σᐩ> {
//  println("Repairing: $toRepair")
//  println("Fillers: $fillers")
  val promptTokens = listOf("") + toRepair.tokenizeByWhitespace().intersperse() + listOf("")
  val deck = (fillers + promptTokens).shuffled().toSet()

//  val clock: TimeSource.Monotonic.ValueTimeMark = TimeSource.Monotonic.markNow()
  var (pass, fail) = 0 to 0
  fun genSeq(skip: Int = 1, shift: Int = 0) =
    MDSamplerWithoutReplacementNK(deck, n = promptTokens.size, k = edits, skip, shift)
      .takeWhile { takeMoreWhile() }
      .map { promptTokens.apply(it) }
      .let { if (scoreString != null) it.reservoirSample(score = scoreString) else it }
      .filter {
         it.filter()
//         .also { result ->
//           if (result) pass++ else fail++
//           if (pass + fail % 20000 == 0)
//             println("$it\nPass: $pass, Fail: $fail, ${clock.elapsedNow().inWholeMilliseconds}ms")
//         }
      }
      .onEachIndexed { i, it ->
        if (diagnostic != null) {
//          println("#$i, PID=$shift, ${clock.elapsedNow().inWholeMilliseconds}ms, $it")
          diagnostic(it)
        }
      }
      .map { Triple(it, scoreRepair(it), if(scoreString != null) scoreString(it) else 0.0) }

  return ::genSeq.parallelize().distinct()
//    .limit(MAX_SAMPLE.toLong())
    .toList()
    // Sort with it.second then by it.third
    .sortedWith(compareBy({ it.second }, { it.third }))
//    .also { println("Best score: (${it.firstOrNull()?.second})") }
    .map { it.first.trim() }.toList()
}

// This experiment essentially tries every possible combination of fillers in parallel
fun List<Σᐩ>.parallelSolve(fillers: Set<Σᐩ>) =
  MDSamplerWithoutReplacement(fillers, count { it == HOLE_MARKER })
    .asStream().parallel().map {
      fold("" to it) { (a, b), c ->
        if (c == HOLE_MARKER) (a + " " + b.first()) to b.drop(1) else ("$a $c") to b
      }.first.replace("ε ", "").trim()
    }
//      .filter { measureTimedValue { it.fastMatch(CFG) }.also { println("Decided ${it.value} in ${it.duration}") }.value }

// This is fairly slow compared to bijective repair, but it's a good baseline for comparison
fun repairInParallel(
  prompt: Σᐩ,
  cfg: CFG,
  edits: Int = 3,
  coarsen: Σᐩ.() -> Σᐩ = { this },
  uncoarsen: Σᐩ.(Σᐩ) -> Σᐩ = { this },
  synthesizer: CFG.(List<Σᐩ>) -> Sequence<Σᐩ>,
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
  println("Repairing: $prompt")
  val coarsened = prompt.coarsen()
//  if (cfg.parse(coarsened) != null) return emptyList()
  val tokens = coarsened.tokenizeByWhitespace()
  val tokensWithHoles = tokens.map { if (it in cfg.terminals) it else HOLE_MARKER }
  val sanitized: Σᐩ = tokensWithHoles.joinToString(" ")

  var totalSamples = 0
  val repairs = sanitized.synthesizeWithVariationsInParallel( // <<<<<<<<<< Parallelization happens here
    cfg = cfg,
    synthesizer = synthesizer,
    allowNTs = false,
    variations = variations,
  )
    .map { totalSamples++; it.uncoarsen(prompt) }
    .let { if (filter != null) it.filter(filter) else it }
    .let { if (diagnostic != null) it.map { diagnostic(it); it } else it }
    .map { it to score(it) }
    .take(MAX_SAMPLE).toList().sortedBy { it.second }
    .also { println("Best score: (${it.firstOrNull()?.second})") }
    .map { it.first.trim() }
//    .map { totalSamples++; it.uncoarsen(prompt) }
//    .let { if (filter != null) it.filter(filter) else it }

  if (filter != null) println("Filtered out ${totalSamples - repairs.size}/${totalSamples} invalid samples!")
  return repairs
}

// Generates a lazy sequence of mutations for a broken string
// and feeds them to the synthesizer for completion.
@OptIn(ExperimentalTime::class)
fun Σᐩ.synthesizeWithVariationsInParallel(
  cfg: CFG,
  allowNTs: Boolean = true,
  enablePruning: Boolean = false,
  variations: List<Mutator> = listOf({ a, b -> sequenceOf() }),
  updateProgress: (Σᐩ) -> Unit = {},
  synthesizer: CFG.(List<Σᐩ>) -> Sequence<Σᐩ>
): Sequence<Σᐩ> {
  val cfg_ = (if (!allowNTs) cfg.noNonterminalStubs else cfg).freeze()

  val t = TimeSource.Monotonic.markNow()

  val (stringToSolve, reconstructor) =
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
    .asStream().parallel() // <<<<<<<<<< Parallelization happens here
    .filter { s -> s.tokenizeByWhitespace().any { it.isHoleTokenIn(cfg) } }
    .takeWhile { t.elapsedNow().inWholeMilliseconds < TIMEOUT_MS }
    .map { updateProgress(it); it }
    .flatMap { variant ->
      val variantTokens = variant.tokenizeByWhitespace()
      cfg_.run { synthesizer(variantTokens) }.asStream()
//        .ifEmpty { cfg_.rememberBigramPolarity(variantTokens, synthesizer) }
//        .map { cfg_.rememberPossibleBigrams(variantTokens); it }
    }.asSequence().distinct().map {
      val rec: Reconstructor = reconstructor.toList().toMutableList()
      it.tokenizeByWhitespace().mapIndexed { i, it ->
        if ("ε" in it) ""
        else if (it.isNonterminalStubIn(cfg_) && it == rec.firstOrNull()?.first) rec.removeFirst().second
        else it
      }.filter { it.isNotBlank() }.joinToString(" ")
    }
}