@file:Suppress("NonAsciiCharacters")

package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.tensor.UTMatrix
import ai.hypergraph.kaliningraph.types.*

fun CFG.sortAll(s: Σᐩ): Set<Σᐩ> =
  try { solveSortedFP(s.tokenizeByWhitespace())[START_SYMBOL]?.map { it.first }?.toSet() ?: setOf() }
  catch (e: Exception) { setOf() }

fun CFG.solveSortedFP(
  tokens: List<Σᐩ>,
  utMatrix: UTMatrix<Sort> = initialUTSMatrix(tokens),
) = utMatrix.seekFixpoint().toFullMatrix()[0].last()

fun CFG.initialUTSMatrix(tokens: List<Σᐩ>, bmp: BiMap = bimap): UTMatrix<Sort> =
  UTMatrix(
    ts = tokens.map { token ->
      (if (token == HOLE_MARKER)
        unitReachability.values.flatten().toSet().map { root ->
          bmp[root].filter { it.size == 1 }
            .map { it.first() }.filter { it in terminals }
        }.flatten().toSet()
      else bmp[listOf(token)]).associateWith {
        listOf(token to if (token == "ε") 0 else 1)
      }
    }.toTypedArray().also {
      it.forEach { println(it.size) }
    },
    algebra = sortwiseAlgebra
  )

// Maintains a sorted list of nonterminal roots and their leaves
val CFG.sortwiseAlgebra: Ring<Sort> by cache {
    Ring.of(
      nil = mapOf(),
      plus = { x, y -> union(x, y) },
      times = { x, y -> join(x, y) }
    )
}

operator fun SRec.plus(s2: SRec): SRec =
  first + s2.first to second + s2.second

fun CFG.join(s1: Sort, s2: Sort): Sort =
  bimap.L2RHS.entries.associate { (k, v) ->
    k to v.filter { it.size == 2 }.map { (a, b) ->
      val left = s1[a]
      val right = s2[b]
      if (left != null || right != null) {
        (left!!.toSet() * right!!.toSet())
          .map { (q, r) -> q + r }
      } else mutableListOf()
    }.flatten()
  }

fun union(s1: Sort, s2: Sort): Sort =
  s1.mapValues { (k, v) ->
    if (k in s2) { v }
    else {
      val (a, b) = v.iterator() to s2[k]!!.iterator()
      val newList = mutableListOf<SRec>()
      while (a.hasNext() || b.hasNext()) {
        val toAdd =
          if (!a.hasNext()) b.next()
          else if (!b.hasNext()) a.next()
          else {
            val (a1, a2) = a.next()
            val (b1, b2) = b.next()
            if (a2 < b2) a1 to a2
            else if (b2 < a2) b1 to b2
            else a1 to a2
          }

        if (newList.last() != toAdd) newList.add(toAdd)
      }

      newList
    }
  }

typealias Sort = Map<Σᐩ, List<SRec>>
// Substring and some metric (e.g., number of blanks)
// TODO: Associate a more concrete semantics with second value,
//       but for now just the number of terminals. For example,
//       we could use perplexity of a Markov chain or the length
//       of the longest common substring with the original string.
typealias SRec = Π2<Σᐩ, Int>
