package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.sampling.*
import kotlin.math.pow
import kotlin.random.Random


// Fully-parallelizable version of the Valiant repair algorithm, just append a .parallelize() call
fun newRepair(prompt: List<Σᐩ>, cfg: CFG, edits: Int = 3, skip: Int = 1, shift: Int = 0): Sequence<String> =
  generateLevenshteinEdits(cfg.terminals - cfg.blocked, prompt, edits, skip, shift)
    .map { prompt.apply(it) }
    .filter { it.matches(cfg) }

// Indices of the prompt tokens to be replaced and the tokens to replace them with
typealias Edit = Map<Int, Σᐩ>

// Enumerates the powerset from the bottom up, skipping the empty set
fun Edit.subedits(): Sequence<Edit> = (1..size).asSequence()
  .flatMap { keys.choose(it).map { it.associateWith { this[it]!! } } }

fun List<Σᐩ>.apply(edit: Edit): Σᐩ =
  mapIndexed { i, ot -> if (i in edit) edit[i]!! else ot }
    .filter { it != "ε" && it.isNotBlank() }.joinToString(" ")

// If this fails, it's probably because the sample space is too large.
// Short of migrating to a 64-bit LFSR, the solution is to reduce the
// number of tokens^edits to be less than ~2^31, i.e. 2,147,483,647.
fun generateLevenshteinEdits(
  deck: Set<Σᐩ>,
  promptTokens: List<Σᐩ>,
  edits: Int,
  skip: Int = 1,
  shift: Int = 0,
  scoreEdit: ((Edit) -> Float)? = null
) =
  MDSamplerWithoutReplacementNK(deck, n = promptTokens.size, k = edits, skip, shift)
    .let { if (scoreEdit != null) it.reservoirSample(scoreEdit = scoreEdit) else it }

//@OptIn(ExperimentalTime::class)
fun Sequence<Edit>.reservoirSample(size: Int = 1000, scoreEdit: (Edit) -> Float): Sequence<Edit> {
  val pq = PriorityQueue()
//  val t = TimeSource.Monotonic.markNow()
  return map { edit ->
    val score = scoreEdit(edit)
    val r = Random.nextFloat().pow(1f / score)
    pq.insert(edit, r)
    if (pq.count() > size) pq.extractMin() else null
  }.filterNotNull() +
  // Measure time till first sample
//    .onEachIndexed { i, _ -> if (i == 0)
//      println("Time to fill reservoir: ${t.elapsedNow().inWholeMilliseconds}ms")
//    } +
  generateSequence { pq.extractMin() }
}

// Maintains a sorted list of edits, sorted by score
private class PriorityQueue {
  val edits = mutableListOf<Pair<Edit, Float>>()

  fun insert(edit: Edit, score: Float) {
    edits.binarySearch { it.second.compareTo(score) }.let { idx ->
      if (idx < 0) edits.add(-idx - 1, edit to score)
      else edits.add(idx, edit to score)
    }
  }

  fun extractMin(): Edit? = edits.removeFirstOrNull()?.first

  fun count() = edits.size
}

fun generateLevenshteinEditsUpTo(
  deck: Set<Σᐩ>,
  promptTokens: List<Σᐩ>,
  edits: Int,
  skip: Int = 1,
  shift: Int = 0
) =
  (1 .. edits).asSequence().flatMap {
    generateLevenshteinEdits(deck, promptTokens, edits = it, skip, shift)
  }