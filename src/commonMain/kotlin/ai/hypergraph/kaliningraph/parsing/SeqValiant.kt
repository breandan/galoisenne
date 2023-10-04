package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.tensor.UTMatrix
import ai.hypergraph.kaliningraph.types.*

fun PSingleton(v: String): List<Π2A<PTree>> = listOf(PTree(v) to PTree())

// Algebraic data type / polynomial functor for parse forests
class PTree(val root: String = "ε", val branches: List<Π2A<PTree>> = listOf()) {
  val choice by lazy {
    if (branches.isEmpty()) listOf(if ("ε" in root) "" else root)
    else branches.flatMap { (l, r) ->
    // TODO: Use weighted choice mechanism
      (l.choose() * r.choose()).map { (a, b) ->
        if (a.isEmpty()) b else if (b.isEmpty()) a else "$a $b"
      }
    }.distinct().toList()
  }

  // Returns the set of all strings derivable from the given PTree
  fun choose(): Sequence<String> = choice.asSequence()
}

// Lazily computes all syntactically strings compatible with the given template
fun CFG.solveSeq(s: String): Sequence<String> =
    initPForestMat(s.tokenizeByWhitespace()).seekFixpoint()
      .diagonals.last()[0][START_SYMBOL]?.choose()?.distinct() ?: emptySequence()

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