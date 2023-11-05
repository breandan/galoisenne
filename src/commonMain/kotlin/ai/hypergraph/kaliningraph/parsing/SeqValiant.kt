package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.*
import ai.hypergraph.kaliningraph.sampling.choose
import ai.hypergraph.kaliningraph.tensor.UTMatrix
import ai.hypergraph.kaliningraph.types.*
import com.ionspin.kotlin.bignum.integer.*
import kotlin.math.*
import kotlin.random.Random
import kotlin.time.measureTimedValue

fun PSingleton(v: String): List<Π2A<PTree>> = listOf(PTree(v) to PTree())

// Algebraic data type / polynomial functor for parse forests
class PTree(val root: String = "ε.", val branches: List<Π2A<PTree>> = listOf()) {
//  val hash by lazy { root.hashCode() + if (branches.isEmpty()) 0 else branches.hashCode() }
//  override fun hashCode(): Int = hash

  val branchRatio: Pair<Double, Double> by lazy { if (branches.isEmpty()) 0.0 to 0.0 else
    (branches.size.toDouble() + branches.sumOf { (l, r) -> l.branchRatio.first + r.branchRatio.first }) to
    (1 + branches.sumOf { (l, r) -> l.branchRatio.second + r.branchRatio.second })
  }

  val allTerminals: Set<String> by lazy {
    if (branches.isEmpty()) setOf(root)
    else branches.map { (l, r) -> l.allTerminals + r.allTerminals }.flatten().toSet()
  }

  // Σ^n/|T(n)|, if < 1 then we know the grammar is surely ambiguous
  val inverseDensity by lazy {
    measureTimedValue { allTerminals.size.toBigInteger().pow(depth) / totalTrees }
      .also { println("Solution density was: 1/${it.value} (${it.duration})") }.value
  }

  // TODO: Use weighted choice mechanism
  val shuffledBranches by lazy { branches.shuffled().sortedBy { "ε" !in it.first.root + it.second.root } }
  val totalTrees: BigInteger by lazy {
    if (branches.isEmpty()) BigInteger.ONE
    else branches.map { (l, r) -> l.totalTrees * r.totalTrees }
      .reduce { acc, it -> acc + it }
  }

  // e.g., if we want to prioritize shorter strings we can sort by total epsilons
  val numEpsilons: BigInteger by lazy {
    if (branches.isEmpty()) if (root == "ε") BigInteger.ONE else BigInteger.ZERO
    else branches.map { (l, r) -> l.totalTrees * r.totalTrees }.reduce { acc, it -> acc + it }
//    else branches.maxOf { (l, r) -> l.numEpsilons + r.numEpsilons }
  }

  fun Π2A<PTree>.countEpsilons() = first.numEpsilons + second.numEpsilons

  val epsSortedBranches by lazy { branches.sortedBy { -it.countEpsilons() } }

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
    val concat = Tree(root, children = arrayOf(l, r))
    return concat to quotient3
  }

  fun sampleTreesWithoutReplacement() =
    sequence {
      var i = BigInteger.ZERO
      while (i < totalTrees) yield(decodeTree(i++).first)
    }.distinct()

  fun sampleStrWithoutReplacement(): Sequence<String> = sequence {
    var i = BigInteger.ZERO
    while (i < 3 * totalTrees) yield(decodeString(i++).first)
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

  fun sampleWRGD(): Sequence<String> = generateSequence { sampleStrWithGeomDecay() }

  fun sampleStrWithGeomDecay(): String =
    if (branches.isEmpty()) if ("ε." in root) "" else root
    else {
//      val p = 0.9 // Adjust this for different decay rates
//      val rnd = Random.nextDouble()
//      val index =(-(1.0 / ln(1 - p)) * ln(1 - rnd) * branches.size).toInt().coerceIn(branches.indices)
//      println(index)
      epsSortedBranches.sampleWithGeomDecay().let { (l, r) ->
        val (a, b) = l.sampleStrWithGeomDecay() to r.sampleStrWithGeomDecay()
        if (a.isEmpty()) b else if (b.isEmpty()) a else "$a $b"
      }
    }

  fun <T> List<T>.sampleWithGeomDecay(): T {
    if (isEmpty()) throw NoSuchElementException("List is empty.")

    val r = 0.5 // Common ratio; adjust this for different decay rates

    // Compute the total sum of the geometric series up to size
    val total = (1 - r.pow(size)) / (1 - r)

    // Generate a random value between 0 and the total
    val rnd = Random.nextDouble() * total

    // Iterate to find which item this random value corresponds to
    var cumulativeSum = 0.0
    var index = 0
    while (index < size) {
      cumulativeSum +=r.pow(index.toDouble())
      if (rnd < cumulativeSum) break
      index++
    }

    return this[index]
  }

//  fun List<Π2A<PTree>>.sampleWithGeomDecay(): Π2A<PTree> {
//    val p = 0.5 // Adjust this for different decay rates
//    val rnd = Random.nextDouble()
//    val index = -(1.0 / ln(1 - p)) * ln(1 - rnd)
//    return epsSortedBranches[index.toInt().coerceAtMost(branches.size - 1)]
//  }
}

fun CFG.startPTree(tokens: List<String>) = measureTimedValue {
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
//    PTree(k, ((X[k]?.branches ?: listOf()) + (Z[k]?.branches ?: listOf())).toSet().toList())
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

fun CFG.sliceSolve(size: Int): Sequence<String> = solveSeq(List(size) { "_" })

fun CFG.sliceSample(size: Int): Sequence<String> = sampleSeq(List(size) { "_" })

// Lazily computes all syntactically strings compatible with the given template
// Generally slow, but guaranteed to return all solutions
fun CFG.solveSeq(tokens: List<String>): Sequence<String> =
  startPTree(tokens)?.choose()?.distinct() ?: sequenceOf()

// This should never return duplicates and is the second fastest.
// Eventually, this will become the default method for sampling.
fun CFG.enumSeq(tokens: List<String>): Sequence<String> =
  startPTree(tokens)?.let { pt ->
    if (BigInteger.ONE < pt.inverseDensity) pt.sampleStrWithoutReplacement()
    // This means the grammar is highly ambiguous and we would probably be
    // better off sampling from the bottom-up, instead of from the top-down.
    else {
      println("Ambiguity exceeds total solutions, switching to bottom-up naïve search!")
      tokens.solve(this)
    }
  } ?: sequenceOf()

// This is generally the fastest method, but may return duplicates
fun CFG.sampleSeq(tokens: List<String>): Sequence<String> =
  startPTree(tokens)?.sampleWithReplacement() ?: sequenceOf()

fun CFG.enumTree(tokens: List<String>): Sequence<Tree> =
  startPTree(tokens)?.sampleTreesWithoutReplacement() ?: sequenceOf()

fun CFG.enumSWOR(tokens: List<String>): Sequence<String> =
  startPTree(tokens)?.sampleWRGD() ?: sequenceOf()

fun CFG.hammingBallRepair(tokens: List<String>): Sequence<String> =
  tokens.indices.toSet().choose(5)
    .map { tokens.substituteIndices(it) { it, i -> "_" } }
    .flatMap { enumSWOR(it).take(100) }

fun CFG.repairSeq(tokens: List<String>): Sequence<String> =
  tokens.intersperse(2, "ε").let { prompt ->
    hammingBallRepair(prompt).flatMap {
      val result = it.tokenizeByWhitespace()
      val edit = prompt.calcEdit(it.tokenizeByWhitespace())
      Repair(prompt, edit, result, 0.0)
        .minimalAdmissibleSubrepairs({ it.filter { it != "ε" } in language }, { edit.size.toDouble() })
    }.distinct().map { it.resToStr().removeEpsilon() }.distinct()
  }