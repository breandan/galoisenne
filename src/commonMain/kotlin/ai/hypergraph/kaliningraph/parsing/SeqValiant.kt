package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.tensor.UTMatrix
import ai.hypergraph.kaliningraph.types.*
import kotlin.time.measureTimedValue

fun PSingleton(v: String): List<Π2A<PTree>> = listOf(PTree(v) to PTree())

// Algebraic data type / polynomial functor for parse forests
class PTree(val root: String = "ε", val branches: List<Π2A<PTree>> = listOf()) {
  val totalTrees: ULong by lazy {
    if (branches.isEmpty()) 1uL
    else branches.sumOf { (l, r) -> l.totalTrees * r.totalTrees }
  }
  val shuffledBranches by lazy { branches.shuffled() }
  private val choice by lazy {
    if (branches.isEmpty()) listOf(if ("ε" in root) "" else root)
    else branches.flatMap { (l, r) ->
    // TODO: Use weighted choice mechanism
      (l.choose() * r.choose()).map { (a, b) ->
        if (a.isEmpty()) b else if (b.isEmpty()) a else "$a $b"
      }
    }.distinct().toList()
  }

  private fun choose(): Sequence<String> = choice.asSequence()

  // Returns the sequence of all strings derivable from the given PTree
  // but needs a few seconds to warm up.
  fun sampleWithoutReplacement(): Sequence<String> = choose()

  // Samples instantaneously from the parse forest, but may return duplicates
  // and only returns a fraction of the number of distinct strings when compared
  // to SWOR on medium-sized finite sets under the same wall-clock timeout. If
  // the set is sufficiently large, distinctness will never a problem.
  fun sampleWithReplacement(): Sequence<String> = sequence { while(true) yield(sample()) }

  fun sample(): String =
    if (branches.isEmpty()) if ("ε" in root) "" else root
    else branches.random().let { (l, r) ->
      val (a, b) = l.sample() to r.sample()
      if (a.isEmpty()) b else if (b.isEmpty()) a else "$a $b"
    }

  // TODO: Is there a sampler with no warmup that doesn't return duplicates?
  //       We want one that is as fast as SWR but with no dupes like SWOR.
}

fun CFG.startPTree(s: String) =
  measureTimedValue {
    initPForestMat(s.tokenizeByWhitespace())
      .seekFixpoint().diagonals.last()[0][START_SYMBOL]
  }.also { println("Time to compute parse forest: ${it.duration}") }.value

fun CFG.initPForestMat(tokens: List<String>): UTMatrix<PForest> =
  UTMatrix(
    ts = tokens.map { token ->
      (if (token != HOLE_MARKER) bimap[listOf(token)] else unitNonterminals)
        .associateWith { nt ->
          if (token != HOLE_MARKER) PSingleton(token)
          else bimap.UNITS[nt]?.map { PSingleton(it) }?.flatten() ?: listOf()
        }.map { (k, v) -> k to PTree(k, v) }.toMap()
    }.toTypedArray(),
    algebra = Ring.of(
      nil = emptyMap(),
      plus = { x, y -> merge(x, y) },
      times = { x, y -> joinSeq(x, y) },
    )
  )

typealias PForest = Map<String, PTree>
fun merge(X: PForest, Z: PForest): PForest =
  (X.keys + Z.keys).associateWith { k ->
    PTree(k, (X[k]?.branches ?: listOf()) + (Z[k]?.branches ?: listOf()))
  }

//fun merge(X: PForest, Z: PForest): PForest =
//  (X.keys + Z.keys).associateWith { k ->
//    PTree(k, ((X[k]?.branches ?: listOf()) + (Z[k]?.branches ?: listOf()))
//      .groupBy { it.first.root to it.second.root }.map { (k, v) ->
//        PTree(k.first, v.map { it.first.branches }.flatten()) to PTree(k.second, v.map { it.second.branches }.flatten())
//      }
//    )
//  }

// X ⊗ Z := { w | <x, z> ∈ X × Z, (w -> xz) ∈ P }
fun CFG.joinSeq(X: PForest, Z: PForest): PForest =
  bimap.TRIPL.filter { (_, x, z) -> x in X && z in Z }
    .map { (w, x, z) -> PTree(w, listOf(X[x]!! to Z[z]!!)) }
    .fold(mapOf()) { acc, it -> merge(mapOf(it.root to it), acc) }
//    .groupingBy { it.first }.aggregate { _, acc: List<Π2A<PTree>>?, it, _->
//      val pair = X[it.second]!! to Z[it.third]!!
//      if (acc == null) listOf(pair) else acc + pair
//    }.map { (k, v) -> k to PTree(k, v) }.toMap()

fun CFG.sliceSolve(size: Int): Sequence<Σᐩ> =
  solveSeq(List(size) { "_" }.joinToString(" "))

fun CFG.sliceSample(size: Int): Sequence<Σᐩ> =
  sampleSeq(List(size) { "_" }.joinToString(" "))

// Lazily computes all syntactically strings compatible with the given template
fun CFG.solveSeq(s: String): Sequence<String> =
  startPTree(s)?.sampleWithoutReplacement()?.distinct() ?: sequenceOf()

fun CFG.sampleSeq(s: String): Sequence<String> =
  startPTree(s)?.sampleWithReplacement() ?: sequenceOf()