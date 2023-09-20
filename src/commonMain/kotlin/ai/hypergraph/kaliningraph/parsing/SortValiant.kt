@file:Suppress("NonAsciiCharacters")

package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.levenshtein
import ai.hypergraph.kaliningraph.tensor.UTMatrix
import ai.hypergraph.kaliningraph.types.*

// Returns all syntactically strings ordered by distance to withRespect
fun CFG.sortAll(s: Σᐩ, withRespectTo: Σᐩ): Set<Σᐩ> =
  try { solveSortedFP(s.tokenizeByWhitespace(), withRespectTo.tokenizeByWhitespace())
    ?.sortedBy { it.second }
    ?.map { it.first.filterNot { "ε" in it }.joinToString(" ") }?.toSet() ?: setOf() }
  catch (e: Exception) { e.printStackTrace(); setOf() }

fun CFG.solveSortedFP(
  tokens: List<Σᐩ>,
  withRespectTo: List<Σᐩ>,
  utMatrix: UTMatrix<Sort> =
    initialUTSMatrix(tokens,
      sortwiseAlgebra(metric = {
        levenshtein(it.first.filterNot { "ε" in it }, withRespectTo)
      })
    ),
) = utMatrix.seekFixpoint().toFullMatrix()[0].last()[START_SYMBOL]

fun CFG.initialUTSMatrix(
  tokens: List<Σᐩ>,
  algebra: Ring<Sort>
): UTMatrix<Sort> =
  UTMatrix(
    ts = tokens.map { token ->
      (if (token == HOLE_MARKER)
        unitReachability.values.flatten().toSet().filter { root ->
          bimap[root].any { it.size == 1 && it.first() in terminals }
        }.toSet()
      else bimap[listOf(token)])
      .associateWith {
        if (token == HOLE_MARKER)
          bimap[it].filter { it.size == 1 && it.first() in terminals && !it.first().isNonterminalStub() }
          .map { it.first() }.map { listOf(it) to if ("ε" in token) 0 else 1 }.toSet()
        else setOf(listOf(token) to if ("ε" in token) 0 else 1)
      }
    }.toTypedArray(),
    algebra = algebra
  )

// Maintains a sorted list of nonterminal roots and their leaves
fun CFG.sortwiseAlgebra(metric: (SRec) -> Int): Ring<Sort> =
  Ring.of(
    nil = mapOf(),
    plus = { x, y -> union(x, y) },
    times = { x, y -> join(x, y, metric) }
  )

operator fun SRec.plus(s2: SRec): SRec = (π1 + s2.π1) to (π2 + s2.π2)

// X ⊗ Z := { w | <x, z> ∈ X × Z, (w -> xz) ∈ P }
fun CFG.join(s1: Sort, s2: Sort, metric: (SRec) -> Int = { it.second }): Sort =
  bimap.TRIPL.filter { (_, x, z) -> x in s1 && z in s2 }
  .map { (w, x, z) ->
    ((s1[x] ?: setOf()) * (s2[z] ?: setOf()))
      .map { (q, r) -> w to (q + r) }
  }.flatten()
  .groupingBy { it.first }
//  .aggregate { _, acc, it, _ ->
//    val toInsert = it.second.let { it.first to metric(it) }
//    ((acc ?: setOf()) + toInsert).sortedBy { it.second }.take(20).toSet()
//  }
  .aggregate<Pair<Σᐩ, SRec>, Σᐩ, MutableList<SRec>> { _, acc, it, _ ->
    val toInsert = it.second.let { it.first to metric(it) }
    val list = (acc ?: mutableListOf())
    val idx = list.binarySearch(toInsert,
      compareBy<SRec> { it.second }
//        .thenBy { it.first.hashCode() }
    )
    list.add(if (idx < 0) -idx - 1 else idx, toInsert)
//    if (idx < 0) list.add(-idx - 1, toInsert)
    list.apply { if (100 < size) removeLast() }
  }
  .mapValues { it.value.toSet() }

fun union(s1: Sort, s2: Sort): Sort =
  (s1.keys + s2.keys).associateWith { k ->
    ((s1[k] ?: setOf()) + (s2[k] ?: setOf()))
//      .sortedBy { it.second }.take(100).toSet()
  }

// Mutable list that maintains a sorted order and has a fixed capacity.

// Map of root to the possible sets of leaves
// This is like a tree where we do not store the internal nodes
// One root can represent many strings, but we only care about unique leaf sequences
// Maintains a sort ordering based on some metric of the most likely derivations
typealias Sort = Map<Σᐩ, Set<SRec>>
// Substring and some metric (e.g., number of blanks)
// TODO: Associate a more concrete semantics with second value,
//       but for now just the number of terminals. For example,
//       we could use perplexity of a Markov chain or the length
//       of the longest common substring with the original string.
typealias SRec = Π2<List<Σᐩ>, Int>