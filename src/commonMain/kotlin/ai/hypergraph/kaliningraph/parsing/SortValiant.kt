@file:Suppress("NonAsciiCharacters")

package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.levenshtein
import ai.hypergraph.kaliningraph.tensor.UTMatrix
import ai.hypergraph.kaliningraph.types.*

// Returns all syntactically strings ordered by distance to withRespect
fun CFG.sortAll(s: Σᐩ, metric: ChoiceMetric): Set<Σᐩ> =
  try { solveSortedFP(s.tokenizeByWhitespace(), metric)
    ?.sortedBy { it.weight }
    ?.map { it.sanitize().joinToString(" ") }?.toSet() ?: setOf() }
  catch (e: Exception) { e.printStackTrace(); setOf() }

fun CFG.solveSortedFP(
  tokens: List<Σᐩ>,
  metric: ChoiceMetric,
  utMatrix: UTMatrix<Sort> = initialUTSMatrix(tokens, sortwiseAlgebra(metric)),
) = utMatrix.seekFixpoint().toFullMatrix()[0].last()[START_SYMBOL]

fun CFG.initialUTSMatrix(
  tokens: List<Σᐩ>,
  algebra: Ring<Sort>
): UTMatrix<Sort> =
  UTMatrix(
    ts = tokens.map { token ->
      (if (token != HOLE_MARKER) bimap[listOf(token)] else unitNonterminals)
      .associateWith { nt ->
        if (token != HOLE_MARKER) setOf(Choice(token))
        else bimap.UNITS[nt]?.map { Choice(it) }?.toSet() ?: setOf()
      }
    }.toTypedArray(),
    algebra = algebra
  )

// Maintains a sorted list of nonterminal roots and their leaves
fun CFG.sortwiseAlgebra(metric: ChoiceMetric): Ring<Sort> =
  Ring.of(
    nil = mapOf(),
    plus = { x, y -> union(x, y) },
    times = { x, y -> join(x, y, metric) },
  )

const val MAX_CAPACITY = 100
// X ⊗ Z := { w | <x, z> ∈ X × Z, (w -> xz) ∈ P }
fun CFG.join(X: Sort, Z: Sort, metric: ChoiceMetric = { it.weight }): Sort =
  bimap.TRIPL.filter { (_, x, z) -> x in X && z in Z }
  .map { (w, x, z) ->
    ((X[x] ?: setOf()) * (Z[z] ?: setOf()))
      .map { (q, r) -> w to (q + r) }
  }.flatten().groupingBy { it.first }
  .aggregate<Pair<Σᐩ, Choice>, Σᐩ, MutableList<Choice>> { _, acc, it, _ ->
    val choice = Choice(it.second.tokens, metric(it.second))
    val list = acc ?: mutableListOf()
    val idx = list.binarySearch(choice, Choice.comparator)
    if (idx < 0) list.add(-idx - 1, choice) // Only if not already present
    list.apply { if (MAX_CAPACITY < size) removeLast() }
  }
  .mapValues { it.value.toSet() }

fun union(l: Sort, r: Sort): Sort =
  (l.keys + r.keys).associateWith { k -> (l[k] ?: setOf()) + (r[k] ?: setOf()) }

// Map of root to the possible sets of token sequences it can produce in context
// This is identical to a forest minus internal branches, just roots and leaves
// Each root represents many strings, we only care about unique leaf sequences
// Maintains a sort ordering based on some metric of the most likely derivations
typealias Sort = Map<Σᐩ, Set<Choice>>
typealias ChoiceMetric = (Choice) -> Float
// Substring and some metric (e.g., number of blanks)
// TODO: Associate a more concrete semantics with second value,
//       but for now just the number of terminals. For example,
//       we could use perplexity of a Markov chain or the length
//       of the longest common substring with the original string.
data class Choice(val tokens: List<Σᐩ>, val weight: Float): Comparable<Choice> {
  constructor(token: Σᐩ): this(listOf(token), if ("ε" in token) 0f else 1f)

  companion object {
    val comparator: Comparator<Choice> =
      compareBy<Choice> { it.weight }.thenBy { it.tokens.hashCode() }
  }

  override fun compareTo(other: Choice): Int = comparator.compare(this, other)

  operator fun plus(other: Choice) =
    Choice(tokens + other.tokens, weight + other.weight)
  
  fun sanitize() = tokens.filterNot { "ε" in it }
}

// Returns a metric measuring Levenshtein distance w.r.t. some reference string
fun levMetric(withRespectTo: Σᐩ): ChoiceMetric =
  withRespectTo.tokenizeByWhitespace()
    .let { wrt -> { levenshtein(it.sanitize(), wrt).toFloat() } }