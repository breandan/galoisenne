import ai.hypergraph.kaliningraph.*
import ai.hypergraph.kaliningraph.parsing.*
import ai.hypergraph.kaliningraph.sampling.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ln
import kotlin.random.Random
import kotlin.streams.*
import kotlin.time.*


val NUM_CORES = Runtime.getRuntime().availableProcessors()

fun <E> ((Int, Int) -> Sequence<E>).parallelize(cores: Int = NUM_CORES) =
  (0 until cores).toSet().parallelStream()
  .flatMap { i -> this(cores, i).asStream() }

class ConcurrentRankedProbabilisticSet<T>(
  private val keys: MutableSet<T> = ConcurrentHashMap.newKeySet()
) : Set<T> by keys, FastRandomSet<T> {

  val atomicSize: AtomicInteger = AtomicInteger(-1)
  val mostLikely: ConcurrentSkipListMap<Double, T> = ConcurrentSkipListMap<Double, T>()

  fun add(element: T, perplexity: Double) {
    if (keys.add(element)) {
      atomicSize.incrementAndGet()
      mostLikely[perplexity] = element
    }
  }

  override fun contains(element: T): Boolean = element in keys

  // Samples sorted elements with probability proportional the harmonic series of their ranked index
  override fun randomOrNull(): T? {
    val size = mostLikely.size
    if (size <= 0) return null
    val totalWeight = ln(size.toDouble())
    val randValue = Random.nextDouble() * totalWeight
    var cumulativeWeight = 0.0
    for ((index, item) in mostLikely.values.withIndex()) {
      cumulativeWeight += 1.0 / (index + 1)
      if (cumulativeWeight >= randValue) return item
    }
    return null
  }
}

fun bijectiveRepair(
  promptTokens: List<Σᐩ>,
  // A list of tokens to be used as fillers (may contain repeats for higher probability of sampling)
  deck: List<Σᐩ>,
  maxEdits: Int = 2,
  takeMoreWhile: () -> Boolean = { true },
  admissibilityFilter: List<Σᐩ>.() -> Boolean = { true },
  diagnostic: ((Repair) -> Unit)? = null,
  scoreEdit: (List<Σᐩ>) -> Double = { 0.0 },
): Sequence<Repair> {
//  println("Repairing: $promptTokens")
//  println("Fillers: $fillers")
//  println("Using deck: $deck")
  val deckUnique = deck.toSet()

  val clock: TimeSource.Monotonic.ValueTimeMark = TimeSource.Monotonic.markNow()
  var (pass, fail) = 0 to 0
  val seen = ConcurrentSkipListSet<Int>()
  val goodEdits = ConcurrentRankedProbabilisticSet<Edit>()

  fun genSeq(skip: Int = 1, shift: Int = 0): Sequence<Repair> =
    // This samples edits uniformly at random without replacement (extremely fast)
    MDSamplerWithoutReplacementNK(deckUnique, n = promptTokens.size, k = maxEdits, skip, shift)
      .takeWhile { takeMoreWhile() }
      .flatMap {
        val elapsed = (clock.elapsedNow().inWholeMilliseconds / 200)
          .toInt().coerceAtMost(10)
        // 1 to 3 is one "explore" (uniform random) to five exploit (resampled good edits)
        listOf(it) + goodEdits.resample(
          maxTake = 1 + elapsed, // This controls the growth rate of the resampling ratio
          strLen = promptTokens.size,
          deck = deck,
          seen = seen
        )
      }
      .map { it: Edit ->
        val result = promptTokens.apply(it)
        Repair(promptTokens, it, result, 0.0)
      }
// This is slow, so let's rerank by score only after filtering
//      .let { it.reservoirSample(score = { it.score }) }
      .onEach { seen.add(it.hashCode()) }
      .filter { it.result.admissibilityFilter() }
      .flatMap { it.minimalAdmissibleSubrepairs(admissibilityFilter, scoreEdit) }
      .onEach {
        goodEdits.add(it.edit, it.score)
        it.timeMS = clock.elapsedNow().inWholeMilliseconds
        if (diagnostic != null) { diagnostic(it) }
      }

  /** Selects distinct repairs according to [Repair.hashCode] */
  return ::genSeq.parallelize().distinct().asSequence()
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
  val repairs = sanitized.synthesizeWithVariationsInParallel(
    // <<<<<<<<<< Parallelization happens here
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