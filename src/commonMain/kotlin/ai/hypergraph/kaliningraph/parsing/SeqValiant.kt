package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.tensor.UTMatrix
import ai.hypergraph.kaliningraph.types.*
import com.ionspin.kotlin.bignum.integer.*
import kotlin.time.measureTimedValue

fun PSingleton(v: String): List<Π2A<PTree>> = listOf(PTree(v) to PTree())

// Algebraic data type / polynomial functor for parse forests
class PTree(val root: String = "ε", val branches: List<Π2A<PTree>> = listOf()) {
  // TODO: Use weighted choice mechanism
  val shuffledBranches by lazy { branches.shuffled() }
  val totalTrees: BigInteger by lazy {
    if (branches.isEmpty()) BigInteger.ONE
    else branches.map { (l, r) -> l.totalTrees * r.totalTrees }
      .reduce { acc, it -> acc + it }
  }

  val depth: Int by lazy {
    if (branches.isEmpty()) 0
    else branches.maxOf { (l, r) -> maxOf(l.depth, r.depth) + 1 }
  }

  private val choice by lazy {
    if (branches.isEmpty()) listOf(if ("ε" in root) "" else root)
    else shuffledBranches.flatMap { (l, r) ->
      (l.choose() * r.choose()).map { (a, b) ->
        if (a.isEmpty()) b else if (b.isEmpty()) a else "$a $b"
      }
    }.distinct()
  }

  fun choose(): Sequence<String> = choice.asSequence()

  private fun decodeString(i: BigInteger): Pair<String, BigInteger> {
    if (branches.isEmpty()) return (if ("ε" in root) "" else root) to i
    val (quotient1, remainder) = i.divrem(branches.size.toBigInteger())
    val (lb, rb) = shuffledBranches[remainder.intValue()]
    val (l, quotient2) = lb.decodeString(quotient1)
    val (r, quotient3) = rb.decodeString(quotient2)
    val concat = (if(l.isEmpty()) r else if(r.isEmpty()) l else "$l $r")
    return concat to quotient3
  }

  private fun decodeTree(i: BigInteger): Pair<Tree, BigInteger> {
    if (branches.isEmpty()) return Tree(root) to i
    val (quotient1, remainder) =
      i.div(branches.size) to i.mod(branches.size.toBigInteger())
    val (lb, rb) = shuffledBranches[remainder.toString().toInt()]
    val (l, quotient2) = lb.decodeTree(quotient1)
    val (r, quotient3) = rb.decodeTree(quotient2)
    val concat = Tree(l.root, children = arrayOf(l, r))
    return concat to quotient3
  }

  fun sampleTreesWithoutReplacement() =
    sequence {
      println("Total trees in PTree: $totalTrees")
      var i = BigInteger.ZERO
      while (i < totalTrees) yield(decodeTree(i++).first)
    }.distinct()

  fun sampleStrWithoutReplacement(): Sequence<String> = sequence {
    println("Total trees in PTree: $totalTrees")
    var i = BigInteger.ZERO
    while (i < totalTrees) yield(decodeString(i++).first)
  }.distinct()

  // Samples instantaneously from the parse forest, but may return duplicates
  // and only returns a fraction of the number of distinct strings when compared
  // to SWOR on medium-sized finite sets under the same wall-clock timeout. If
  // the set is sufficiently large, distinctness will never be a problem.
  fun sampleWithReplacement(): Sequence<String> = generateSequence { sample() }

  fun sample(): String =
    if (branches.isEmpty()) if ("ε" in root) "" else root
    else branches.random().let { (l, r) ->
      val (a, b) = l.sample() to r.sample()
      if (a.isEmpty()) b else if (b.isEmpty()) a else "$a $b"
    }
}

fun CFG.startPTree(tokens: List<Σᐩ>) = measureTimedValue {
  initPForestMat(tokens).seekFixpoint().diagonals.last()[0][START_SYMBOL]
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

fun CFG.sliceSolve(size: Int): Sequence<Σᐩ> = solveSeq(List(size) { "_" })

fun CFG.sliceSample(size: Int): Sequence<Σᐩ> = sampleSeq(List(size) { "_" })

// Lazily computes all syntactically strings compatible with the given template
// Generally slow, but guaranteed to return all solutions
fun CFG.solveSeq(tokens: List<Σᐩ>): Sequence<String> =
  startPTree(tokens)?.choose()?.distinct() ?: sequenceOf()

// This should never return duplicates and is the second fastest.
// Eventually, this will become the default method for sampling.
fun CFG.enumSeq(tokens: List<Σᐩ>): Sequence<String> =
  startPTree(tokens)?.sampleStrWithoutReplacement() ?: sequenceOf()

// This is generally the fastest method, but may return duplicates
fun CFG.sampleSeq(tokens: List<Σᐩ>): Sequence<String> =
  startPTree(tokens)?.sampleWithReplacement() ?: sequenceOf()

fun CFG.enumTree(tokens: List<Σᐩ>): Sequence<Tree> =
  startPTree(tokens)?.sampleTreesWithoutReplacement() ?: sequenceOf()