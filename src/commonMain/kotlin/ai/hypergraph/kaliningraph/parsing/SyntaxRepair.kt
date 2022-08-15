package ai.hypergraph.kaliningraph.sat

import ai.hypergraph.kaliningraph.parsing.CFG
import ai.hypergraph.kaliningraph.parsing.drop
import ai.hypergraph.kaliningraph.parsing.mergeHoles
import ai.hypergraph.kaliningraph.sampling.choose
import ai.hypergraph.kaliningraph.types.powerset

/*
 * Generates all single character replacements and insertions.
 * Original: www
 * Variants: _www w_ww ww_w www_
 *           _ww w_w ww_
 */

fun String.singleCharSubstitutionsAndInsertions(): Sequence<String> =
  multiCharSubstitutionsAndInsertions(numberOfEdits = 1)

fun String.multiCharSubstitutionsAndInsertions(
  tokens: List<String> = allTokensExceptHoles(),
  numberOfEdits: Int = minOf(2, tokens.size)
): Sequence<String> =
    (tokens + "").allSubstitutions(numberOfEdits) { "_ $it" } +
    tokens.allSubstitutions(numberOfEdits) { "_" }

fun List<String>.allSubstitutions(numEdits: Int, sub: (String) -> String) =
  (1..numEdits).asSequence().flatMap {
    indices.toSet().choose(numEdits)
      .map { idxs -> mapIndexed { i, it -> if (i !in idxs) it else sub(it) }.joinToString(" ") }
  }

fun String.allTokensExceptHoles() = split(" ").filter { it.isNotBlank() }

/*
 * Treats contiguous underscores as a single hole and lazily enumerates every
 * hole configuration in the powerset of all holes within a snippet.
 * Original: ___w__w_w__w___ -> _w_w_w_w_
 * Variants: _wwww  _w_www _w_w_ww ... _w_w_w_w_
 *           w_www  _ww_ww _w_ww_w
 *           ww_ww  _www_w _w_www_
 *           ...    ...    ...
 */

fun String.everySingleHoleConfig(): Sequence<String> {
  val new = replace(Regex("(_( )*)+"), "_")
  val toks = new.toList().map { it.toString() }
  val indices = toks.indices.filter { toks[it] == "_" }.powerset()
  return indices.map { ids -> toks.drop(setOf("_"), ids).joinToString("") }
}

/*
 * Lazily enumerates all underscores chunkings in order of increasing length up
 * to the lesser of (1) its original size or (2) the longest underscore chunk.
 * Original: ___w__w_w__w___
 * Variants: _w_w_w_w_
 *           __w__w_w__w__
 *           ___w__w_w__w___
 */

fun String.increasingLengthChunks(): Sequence<String> {
  val chunks = mergeHoles().split(Regex("((?<=[^_])|(?=[^_]))"))
  return (2..chunks.maxOf { it.length }).asSequence()
    .map { l -> chunks.joinToString("") { if ("_" in it) it.take(l) else it } }
}