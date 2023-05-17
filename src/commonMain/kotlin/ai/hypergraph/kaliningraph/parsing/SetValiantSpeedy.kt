package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.sampling.MDSamplerWithoutReplacementNK
import kotlin.math.pow
import kotlin.random.Random


// Fully-parallelizable version of the Valiant repair algorithm, just append a .parallelize() call
fun newRepair(prompt: Σᐩ, cfg: CFG, edits: Int = 3, skip: Int = 1, shift: Int = 0): Sequence<String> =
  generateLevenshteinEdits(cfg.terminals - cfg.blocked, prompt.tokenizeByWhitespace(), edits, skip, shift)
    .filter { it.matches(cfg) }

typealias Edit = Pair<Set<Int>, List<Σᐩ>>
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
    .mapIndexed { i, (editLocs, tokens) ->
//      if (i % 100 == 0) println("$i-edit: $tokens, $editLocs")
      val toReplaceWith = tokens.toMutableList()
      val newTokens = promptTokens.mapIndexed { i, ot ->
        if (i in editLocs) toReplaceWith.removeFirst() else ot
      }
      newTokens.filter { it != "ε" }.joinToString(" ")
    }

fun Sequence<Edit>.reservoirSample(size: Int = 1000, scoreEdit: (Edit) -> Float): Sequence<Edit> {
  val pq = PriorityQueue()
  return map { edit ->
    val score = scoreEdit(edit)
    val r = Random.nextFloat().pow(1f / score)
    pq.insert(edit, -r)
    if (pq.count() > size) pq.extractMin() else null
  }.filterNotNull() + generateSequence { pq.extractMin() }
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