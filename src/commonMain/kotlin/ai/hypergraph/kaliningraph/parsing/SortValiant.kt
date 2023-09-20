@file:Suppress("NonAsciiCharacters")

package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.levenshtein
import ai.hypergraph.kaliningraph.tensor.UTMatrix
import ai.hypergraph.kaliningraph.types.*

// Returns all syntactically strings ordered by distance to withRespect
fun CFG.sortAll(s: Σᐩ, withRespectTo: Σᐩ): Set<Σᐩ> =
  try { solveSortedFP(s.tokenizeByWhitespace(), withRespectTo)[START_SYMBOL]
    ?.map { it.first.tokenizeByWhitespace().filterNot { "ε" in it }.joinToString(" ") }?.toSet() ?: setOf() }
  catch (e: Exception) { e.printStackTrace(); setOf() }

fun CFG.solveSortedFP(
  tokens: List<Σᐩ>,
  withRespectTo: Σᐩ,
  utMatrix: UTMatrix<Sort> =
    initialUTSMatrix(tokens, sortwiseAlgebra(metric = { levenshtein(it.first, withRespectTo) })),
) = utMatrix.seekFixpoint().toFullMatrix()[0].last()

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
          .map { it.first() }.toSet().map { it to if ("ε" in token) 0 else 1 }
        else listOf(token to if ("ε" in token) 0 else 1)
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

operator fun SRec.plus(s2: SRec): SRec =
  "$first ${s2.first}" to second + s2.second

// X ⊗ Z := { w | <x, z> ∈ X × Z, (w -> xz) ∈ P }
fun CFG.join(s1: Sort, s2: Sort, metric: (SRec) -> Int = { it.second }): Sort =
  (s1.keys * s2.keys).map { (x, z) ->
    bimap[listOf(x, z)].map { it to x to z }
  }.flatten().map { (w, x, z) ->
    ((s1[x] ?: listOf()).toSet() * (s2[z] ?: listOf()).toSet())
      .map { (q, r) -> w to (q + r) }
  }.flatten().groupingBy { it.first }
    .aggregate { _, acc, it, _ ->
      val toInsert = it.second.let { it.first to metric(it) }
      ((acc ?: listOf()) + toInsert).sortedBy { it.second }.take(10)
    }

fun union(s1: Sort, s2: Sort): Sort =
  (s1.keys + s2.keys).associateWith { k ->
    ((s1[k] ?: listOf()) + (s2[k] ?: listOf())).sortedBy { it.second }.take(100)
  }

// Mutable list that maintains a sorted order and has a fixed capacity.

// Map of root to the possible sets of leaves
// This is like a tree where we do not store the internal nodes
// The same root can have multiple derivations, but we only care about unique leaf sequences
typealias Sort = Map<Σᐩ, List<SRec>>
// Substring and some metric (e.g., number of blanks)
// TODO: Associate a more concrete semantics with second value,
//       but for now just the number of terminals. For example,
//       we could use perplexity of a Markov chain or the length
//       of the longest common substring with the original string.
typealias SRec = Π2<Σᐩ, Int>
