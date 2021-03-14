package edu.mcgill.kaliningraph.rewriting

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
// Each index has a length-k canonical form. e.g. k=4
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
// Need a streaming algorithm, i.e. string convolution
// on the random walk trace.

fun main() {
  val seq = "quicken".toCharArray().toList()
  val sls = seq.prefixSublists()
  println(sls)
  val enc = sls.map { (lst, idx) -> lst.canonicalize() to idx }
  println(enc)

  val strA = "abcadb"
  val strB = "qrsqtr"
  val isomorphicSubstrings = lcis(strA, strB)
  println("Longest common isomorphic subsequences:")
  isomorphicSubstrings.forEach { (a, b) ->
    println("$a / $b")
  }
}

fun <E> List<E>.sublists(k: Int = 4) =
  (1 until size + k).map {
    subList((it - k).coerceAtLeast(0), it.coerceAtMost(size)) to
      (it - k).coerceAtLeast(0)
  }

fun <E> List<E>.prefixes() =
  (1..size).map { subList(0, it) }

fun <E> List<E>.prefixSublists(k: Int = 4) =
  sublists(k).map { (lst, idx) -> lst.prefixes().map { it to idx } }
    .flatten().toSet()

fun <E> List<E>.canonicalize() =
  fold(listOf<Int>() to setOf<E>()) { (l, s), e ->
    if (e in s) l + s.indexOf(e) to s
    else l + s.size to s + e
  }.first

fun <E> List<E>.buildSubsequenceIndex() =
  prefixSublists().associate { (lst, idx) -> lst.canonicalize() to idx }

// Longest common isomorphic subsequences
fun lcis(strA: String, strB: String): List<Pair<String, String>> {
  val traceA = strA.toCharArray().toList().buildSubsequenceIndex()
  val traceB = strB.toCharArray().toList().buildSubsequenceIndex()
  val lcs = (traceA.keys intersect traceB.keys).sortedBy { -it.size }.dropLast(1)
  return lcs.map {
    strA.substring(traceA[it]!!.let { idxA -> idxA until idxA + it.size }) to
      strB.substring(traceB[it]!!.let { idxB -> idxB until idxB + it.size })
  }
}