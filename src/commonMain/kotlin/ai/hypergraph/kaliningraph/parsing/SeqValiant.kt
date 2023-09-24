package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.tensor.UTMatrix
import ai.hypergraph.kaliningraph.types.*

typealias PForest = Set<PTree>
operator fun PForest.contains(v: Σᐩ) = PTree(v) in this
fun PSingleton(v: Σᐩ): List<Π2A<PTree>> = listOf(PTree(v) to PTree())

// Algebraic data type / polynomial functor for parse forests
data class PTree(val root: Σᐩ = "ε", val children: List<Π2A<PTree>> = listOf()) {
  // Returns the set of all strings derivable from the given PTree
  fun choose(): Sequence<Σᐩ> =
    if (children.isEmpty()) sequenceOf(if("ε" in root) "" else root)
    else children.asSequence().flatMap { (l, r) ->
      // TODO: Use weighted choice mechanism
      (l.choose() * r.choose()).map { (a, b) ->
        if (a == "") b else if (b == "") a else "$a $b"
      }
    }

  override fun hashCode(): Int = root.hashCode()
  override fun equals(other: Any?) = other is PTree && root == other.root
}

// Lazily computes all syntactically strings compatible with the given template
fun CFG.solveSeq(s: Σᐩ): Sequence<Σᐩ> = solveSeq(s.tokenizeByWhitespace())

fun CFG.solveSeq(s: List<Σᐩ>): Sequence<Σᐩ> =
  try { solvePTreeFPSeq(s) }
  catch (e: Exception) { e.printStackTrace(); null } ?: sequenceOf()

fun CFG.solvePTreeFPSeq(
  tokens: List<Σᐩ>,
  utMatrix: UTMatrix<PForest> = initPForestMatrix(tokens, pforestAlgebra()),
) =
  utMatrix.seekFixpoint().toFullMatrix()[0].last()
    .firstOrNull { it.root == START_SYMBOL }?.choose() ?: emptySequence()

fun CFG.initPForestMatrix(
  tokens: List<Σᐩ>,
  algebra: Ring<PForest>
): UTMatrix<PForest> =
  UTMatrix(
    ts = tokens.map { token ->
      (if (token != HOLE_MARKER) bimap[listOf(token)] else unitNonterminals)
        .associateWith { nt ->
          if (token != HOLE_MARKER) PSingleton(token)
          else bimap.UNITS[nt]?.map { PSingleton(it) }?.flatten()?.toSet()?.toList() ?: listOf()
        }.map { (k, v) -> PTree(k, v) }.toSet()
    }.toTypedArray(),
    algebra = algebra
  )

// Maintains a sorted list of nonterminal roots and their leaves
fun CFG.pforestAlgebra(): Ring<PForest> =
  Ring.of(
    nil = emptySet(),
    plus = { x, y -> x union y },
    times = { x, y -> joinSeq(x, y) },
  )

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