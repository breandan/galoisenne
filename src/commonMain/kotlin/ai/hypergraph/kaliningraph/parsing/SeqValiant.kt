package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.tensor.UTMatrix
import ai.hypergraph.kaliningraph.types.*

typealias PForest = Set<PTree>
operator fun PForest.contains(v: Σᐩ) = PTree(v) in this
fun PSingleton(v: Σᐩ): List<Π2A<PTree>> = listOf(PTree(v) to PTree())

// Algebraic data type / polynomial functor for parse forests
class PTree(val root: Σᐩ = "ε", val branches: List<Π2A<PTree>> = listOf()) {
  // Returns the set of all strings derivable from the given PTree
  fun choose(): Sequence<Σᐩ> =
    if (branches.isEmpty()) sequenceOf(if("ε" in root) "" else root)
    else branches.asSequence().flatMap { (l, r) ->
      // TODO: Use weighted choice mechanism
      (l.choose() * r.choose()).map { (a, b) ->
        if (a == "") b else if (b == "") a else "$a $b"
      }
    }

  override fun hashCode(): Int = root.hashCode()
  override fun equals(other: Any?) = hashCode() == other.hashCode()
}

// Lazily computes all syntactically strings compatible with the given template
fun CFG.solveSeq(s: Σᐩ): Sequence<Σᐩ> =
  initPForestMat(s.tokenizeByWhitespace()).seekFixpoint().diagonals.last()[0]
    .firstOrNull { it.root == START_SYMBOL }?.choose() ?: emptySequence()

fun CFG.initPForestMat(tokens: List<Σᐩ>): UTMatrix<PForest> =
  UTMatrix(
    ts = tokens.map { token ->
      (if (token != HOLE_MARKER) bimap[listOf(token)] else unitNonterminals)
        .associateWith { nt ->
          if (token != HOLE_MARKER) PSingleton(token)
          else bimap.UNITS[nt]?.map { PSingleton(it) }?.flatten() ?: listOf()
        }.map { (k, v) -> PTree(k, v) }.toSet()
    }.toTypedArray(),
    algebra = Ring.of(
      nil = emptySet(),
      plus = { x, y -> merge(x, y) },
      times = { x, y -> joinSeq(x, y) },
    )
  )

fun merge(X: PForest, Z: PForest): PForest =
  (X.toList() + Z).groupBy { it.root }.map { (k, v) ->
    PTree(k, v.map { it.branches }.flatten())
  }.toSet()

// X ⊗ Z := { w | <x, z> ∈ X × Z, (w -> xz) ∈ P }
fun CFG.joinSeq(X: PForest, Z: PForest): PForest =
  bimap.TRIPL.filter { (_, x, z) -> x in X && z in Z }
    .groupingBy { it.first }.aggregate { _, acc: List<Π2A<PTree>>?, it, _-> 
      val (w, x, z) = it
      val ptreeX = X.first { it.root == x }
      val ptreeZ = Z.first { it.root == z }
      val pair = ptreeX to ptreeZ
      if (acc == null) listOf(pair) else acc + pair
    }.map { (k, v) -> PTree(k, v) }.toSet()