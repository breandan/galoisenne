package ai.hypergraph.kaliningraph.rewriting

import ai.hypergraph.kaliningraph.types.*
import info.debatty.java.stringsimilarity.Levenshtein

// Experiment: probabilistic subgraph ismorphism as
// substring matching on a random walk trace. E.g.:
//
// GUID: ... 1ef71 258xu 289xy 1ef71 2kfg1 258xu ... \
//             |     |     |     |     |     |       |- G1 Trace
// AZID: ...   a     b     c     a     d     b   .../
//
// GUID: ... qq371 as3gh mai12 qq371 129vk as3gh ... \
//             |     |     |     |     |     |       |- G2 Trace
// AZID: ...   q     r     s     q     t     r   .../
//
// Length-5 match: abcadb == qrsqtr    \
//                 ||||||    ||||||    |- Isomorphic substrings
//                 mnompn == 123142   /
//
// Looking for the longest common isomorphic subsequence.
// Each index has a length-k canonical form. e.g., k=4
//
// a b c b a d c...
// 1 2 3 2...          1: (1), (1, 2), (1, 2, 3), (1, 2, 3, 2)
//   1 2 1 3...        2: (1), (1, 2), (1, 2, 1), (1, 2, 1, 3)
//     1 2 3 4...      3: (1), (1, 2), (1, 2, 3), (1, 2, 3, 4)
//       1 2 3 4...    4: (1), (1, 2), (1, 2, 3), (1, 2, 3, 4)
//
// Given two graphs, may be possible to treat subgraph
// matching as a longest common subsequence problem on
// a caesar cipher of length k, where k is the window.
//
// TODO: Need a streaming algorithm, i.e. string convolution on the trace.

const val MAX_LEN = 10

fun main() {
  val seq = "abcadb".toCharArray().toList()
  val sls = seq.prefixSublists()
  println(sls)
  val enc = sls.map { (lst, idx) -> lst.canonicalize() to idx }
  println(enc)

  val strA = "abcadbzzzz".also { println(it) }.toList()
  val strB = "qrsqtrr".also { println(it) }.toList()
  val isomorphicSubstrings = isogramSearch(strA, strB)

  println("Longest common isograms up to length $MAX_LEN:")
  isomorphicSubstrings.forEach { (a, b) -> println("$a / $b") }
}

/**
 * Turns a list into canonical form, e.g.:
 * [A B B A B C C D] -> [0 1 1 0 1 2 2 3],
 * [W X X W X Y Y Z] -> [0 1 1 0 1 2 2 3]
 */

fun <E> List<E>.canonicalize(): List<Int> =
  fold(listOf<Int>() to setOf<E>()) { (l, s), e ->
    if (e in s) l + s.indexOf(e) to s
    else l + s.size to s + e
  }.first

/**
 * Indexes all isomorphic subsequences up to [MAX_LEN].
 */

fun <E> List<E>.isogramIndex(): Map<List<Int>, List<Int>> =
  prefixSublists().fold(mutableMapOf()) { map, (lst, idx) ->
    val key = lst.canonicalize() // Subgraph fingerprint
    map[key] = map.getOrDefault(key, listOf()) + idx
    map
  }

/**
 * Takes a random walk [trace] and a [query], and returns a
 * list of closest matches according to Levenshtein distance.
 */

fun <E, F> isogramSearch(
  trace: List<E>, query: List<F>,
  takeTopK: Int = 4,
  metric: (List<Int>) -> Int = {
    val queryCF = query.canonicalize().joinToString()
    val candidate = it.joinToString()
    Levenshtein().distance(queryCF, candidate).toInt()
  }
): List<Π2<List<E>, List<F>>> {
  val traceA = trace.isogramIndex()
  val traceB = query.isogramIndex()
  val lcs = (traceA.keys intersect traceB.keys).sortedBy(metric)
  return lcs.map {
    val a = traceA[it]!!.map { idx -> trace.subList(idx, idx + it.size) }
    val b = traceB[it]!!.map { idx -> query.subList(idx, idx + it.size) }
    a.first() to b.first() // Take first occurrence of n matches
  }.take(takeTopK)
}

fun <E> List<E>.sublists(k: Int = MAX_LEN) =
  (1 until size + k).map {
    subList((it - k).coerceAtLeast(0), it.coerceAtMost(size)) to
      (it - k).coerceAtLeast(0)
  }

fun <E> List<E>.prefixes() = (1..size).map { subList(0, it) }

fun <E> List<E>.prefixSublists() =
  sublists().map { (lst, idx) -> lst.prefixes().map { it to idx } }
    .flatten().toSet()