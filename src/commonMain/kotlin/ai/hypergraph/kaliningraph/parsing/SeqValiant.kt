package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.*
import ai.hypergraph.kaliningraph.repair.*
import ai.hypergraph.kaliningraph.sampling.*
import ai.hypergraph.kaliningraph.tensor.UTMatrix
import ai.hypergraph.kaliningraph.types.*
import com.ionspin.kotlin.bignum.integer.*
import kotlin.jvm.JvmName
import kotlin.math.*
import kotlin.random.Random
import kotlin.time.measureTimedValue

typealias PForest = Map<String, PTree> // ℙ₃
// Algebraic data type / polynomial functor for parse forests (ℙ₂)
class PTree(val root: String = ".ε", val branches: List<Π2A<PTree>> = listOf()) {
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

  // Average time: 436.96ms, total time 43696.959ms (testRandomCFG)
  private fun decodeString(i: BigInteger): Pair<String, BigInteger> {
    if (branches.isEmpty()) return (if ("ε" in root) "" else root) to i
    val (quotient1, remainder) = i.divrem(branches.size.toBigInteger())
    val (lb, rb) = shuffledBranches[remainder.intValue()]
    val (l, quotient2) = lb.decodeString(quotient1)
    val (r, quotient3) = rb.decodeString(quotient2)
    val concat = (if(l.isEmpty()) r else if(r.isEmpty()) l else "$l $r")
    return concat to quotient3
  }

  // Average time: 328.99ms, total time 32899.708ms (testRandomCFG)
  private fun decodeStringFast(i: Long): Pair<String, Long> {
    if (branches.isEmpty()) return (if ("ε" in root) "" else root) to i
    val (quotient1, remainder) = i / branches.size.toLong() to (i % branches.size.toLong())
    val (lb, rb) = shuffledBranches[remainder.toInt()]
    val (l, quotient2) = lb.decodeStringFast(quotient1)
    val (r, quotient3) = rb.decodeStringFast(quotient2)
    val concat = (if(l.isEmpty()) r else if(r.isEmpty()) l else "$l $r")
    return concat to quotient3
  }

  private fun decodeTree(i: BigInteger): Pair<Tree, BigInteger> {
    if (branches.isEmpty()) return Tree(root) to i
    val (quotient1, remainder) = i.divrem(branches.size.toBigInteger())
    val (lb, rb) = shuffledBranches[remainder.toString().toInt()]
    val (l, quotient2) = lb.decodeTree(quotient1)
    val (r, quotient3) = rb.decodeTree(quotient2)
    val isSingleton = l.children.isEmpty() && r.root == ".ε"
    return (if (isSingleton) Tree(root, terminal = l.root)
    else Tree(root, children = arrayOf(l, r))) to quotient3
  }

  fun sampleTreesWithoutReplacement(): Sequence<Tree> = sequence {
      var i = BigInteger.ZERO
      while (i < 3 * totalTrees) yield(decodeTree(i++).first)
    }

  fun sampleStrWithoutReplacement(stride: Int = 1, offset: Int = 0): Sequence<String> =
    sequence {
      var i = BigInteger.ZERO
      while (i < 9 * totalTrees) yield(decodeString(i++ * stride + offset).first)
    }

  // Samples instantaneously from the parse forest, but may return duplicates
  // and only returns a fraction of the number of distinct strings when compared
  // to SWOR on medium-sized finite sets under the same wall-clock timeout. If
  // the set is sufficiently large, distinctness will never be a problem.
  fun sampleWithReplacement(): Sequence<String> = generateSequence { sample() }

  fun sampleDiverseTrees(): Sequence<Tree> =
    sampleTreesWithoutReplacement().distinctBy { it.structureEncode() }

  fun sampleTree(): Tree =
    if (branches.isEmpty()) Tree(root)
    else shuffledBranches.random().let { (l, r) ->
      val (a, b) = l.sampleTree() to r.sampleTree()
      Tree(root, children = arrayOf(a, b))
    }

  fun sample(): String =
    if (branches.isEmpty()) if ("ε" in root) "" else root
    else branches.random().let { (l, r) ->
      val (a, b) = l.sample() to r.sample()
      if (a.isEmpty()) b else if (b.isEmpty()) a else "$a $b"
    }

  fun sampleWRGD(): Sequence<String> = generateSequence { sampleStrWithGeomDecay() }

  // Prefers shorter strings, i.e., strings with more ε tokens
  fun sampleStrWithGeomDecay(): String =
    if (branches.isEmpty()) if (".ε" in root) "" else root
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

//  fun List<Π2A<PTree>>.sampleWithGeomDecay(): Π2A<PTree> {
//    val p = 0.5 // Adjust this for different decay rates
//    val rnd = Random.nextDouble()
//    val index = -(1.0 / ln(1 - p)) * ln(1 - rnd)
//    return epsSortedBranches[index.toInt().coerceAtMost(branches.size - 1)]
//  }
}

fun CFG.startPTree(tokens: List<String>) = //measureTimedValue {
  initPForestMat(tokens).seekFixpoint().diagonals.last()[0][START_SYMBOL]
//}.also { println("Took ${it.duration} to compute parse forest") }.value

// Instead of defining a special case, we instead represent the unit production
// as a left child whose sibling is empty like so: Left child to Right child
fun PSingleton(v: String): List<Π2A<PTree>> = listOf(PTree(v) to PTree())

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

fun merge(X: PForest, Z: PForest): PForest =
  X.toMutableMap().apply {
    Z.forEach { (k, v) ->
      if (k in this) this[k] = PTree(k, (this[k]!!.branches + v.branches))
      else this[k] = v
    }
  }.toMap()

// Too slow:
//  (X.keys + Z.keys).associateWith { k ->
////    PTree(k, ((X[k]?.branches ?: listOf()) + (Z[k]?.branches ?: listOf())).toSet().toList())
//    PTree(k, (X[k]?.branches ?: listOf()) + (Z[k]?.branches ?: listOf()))
//  }

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
//    .map { (w, x, z) -> PTree(w, listOf(X[x]!! to Z[z]!!)) }
    .map { (w, x, z) -> Triple(w, X[x]!!, Z[z]!!) }.groupBy { it.first }
    .map { (k, v) -> k to PTree(k, v.map { it.second to it.third }) }
    .toMap()

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
  startPTree(tokens)?.sampleStrWithoutReplacement() ?: sequenceOf()

fun CFG.enumSeqMinimal(
  prompt: List<String>,
  tokens: List<String>,
  stoppingCriterion: () -> Boolean = { true }
): Sequence<String> =
  startPTree(prompt)?.sampleStrWithoutReplacement()
    ?.takeWhile { stoppingCriterion() }
    ?.distinct()
    ?.flatMap { minimizeFix(tokens, it.tokenizeByWhitespace()) { this in language } }
    ?.distinct()
    ?: sequenceOf()

var maxTrees = 50_000
// This should never return duplicates and is the second fastest.
// Eventually, this will become the default method for sampling.
fun CFG.enumSeqSmart(tokens: List<String>): Sequence<String> =
  startPTree(tokens)?.let { pt ->
    if (BigInteger.ONE < pt.inverseDensity) {
      if (pt.totalTrees < BigInteger(maxTrees)) {
        println("Small number of parse trees (${pt.totalTrees}), sampling without replacement!")
        pt.sampleStrWithoutReplacement()
      }
      else {
        println("Large number of parse trees (${pt.totalTrees}), sampling with replacement!")
        pt.sampleWithReplacement()
//        pt.sampleDiverseTrees().map { it.contents(true) }
      }
    }
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

fun CFG.enumTrees(tokens: List<String>): Sequence<Tree> =
  startPTree(tokens)?.sampleTreesWithoutReplacement() ?: sequenceOf()

fun CFG.sampleSWOR(tokens: List<String>): Sequence<String> =
  startPTree(tokens)?.sampleWRGD() ?: sequenceOf()

fun CFG.hammingBallRepair(tokens: List<String>): Sequence<String> =
  tokens.indices.toSet().choose(5)
    .map { tokens.substituteIndices(it) { it, i -> "_" } }
    .flatMap { sampleSWOR(it).take(100) }

fun CFG.repairSeq(tokens: List<String>): Sequence<String> =
  tokens.intersperse(2, "ε").let { prompt ->
    hammingBallRepair(prompt).flatMap {
      val result = it.tokenizeByWhitespace()
      val edit = prompt.calcEdit(it.tokenizeByWhitespace())
      Repair(prompt, edit, result, 0.0)
        .minimalAdmissibleSubrepairs({ it.filter { it != "ε" } in language }, { edit.size.toDouble() })
    }.distinct().map { it.resToStr().removeEpsilon() }.distinct()
  }

fun CFG.fastRepairSeq(tokens: List<String>, spacing: Int = 2, holes: Int = 6): Sequence<String> =
  tokens.intersperse(spacing, "ε").let { prompt ->
    prompt.indices.toSet().choose(minOf(holes, prompt.size - 1))
      .map { prompt.substituteIndices(it) { _, _ -> "_" } }
      // ifEmpty {...} is a hack to ensure the sequence emits values at a steady frequency
      .flatMap { sampleSWOR(it).take(100).ifEmpty { sequenceOf("ε") } }
      .map { it.removeEpsilon() }
  }.flatMap { if (it.isEmpty()) sequenceOf(it) else minimizeFix(tokens, it.tokenizeByWhitespace()) { this in language } }

fun CFG.barHillelRepair(tokens: List<String>): Sequence<String> =
  generateSequence(1) { it + 1 }
    .flatMap { radius ->
    try { intersectLevFSA(makeLevFSA(tokens, radius)).ifEmpty { null } }
    catch (e: Exception) { null }?.toPTree()?.sampleStrWithoutReplacement() ?: sequenceOf()
  }

// Note the repairs are not distinct as we try to avoid long delays between
// repairs, so callees must remember to append .distinct() if they want this.
fun CFG.fasterRepairSeq(tokens: List<String>, spacing: Int = 2, holes: Int = 6): Sequence<String> {
  var levenshteinBlanket = tokens
  var blanketSeq = emptySequence<String>().iterator()
  val uniformSeq = tokens.intersperse(spacing, "ε").let { prompt ->
    prompt.indices.toSet().choose(minOf(holes, prompt.size - 1))
      .map { prompt.substituteIndices(it) { _, _ -> "_" } }
      // ifEmpty {...} is a hack to ensure the sequence emits values at a steady frequency
      .flatMap { sampleSWOR(it).take(100).ifEmpty { sequenceOf("ε") } }
  }.iterator()

  val distinct1 = mutableSetOf<String>()
  val distinct2 = mutableSetOf<String>()

  return generateSequence {
    if (blanketSeq.hasNext() && Random.nextBoolean()) blanketSeq.next()
    else if (uniformSeq.hasNext()) uniformSeq.next()
    else null
  }.map { it.removeEpsilon() }.flatMap {
    if (it.isEmpty() || !distinct1.add(it)) sequenceOf(it)
    else minimizeFix(tokens, it.tokenizeByWhitespace()) { this in language }
      .onEach { minfix ->
        if (minfix !in distinct2) {
          distinct2.add(minfix)
          val newBlanket =
            updateLevenshteinBlanket(levenshteinBlanket, minfix.tokenizeByWhitespace())
          if (newBlanket != levenshteinBlanket && "_" in newBlanket) {
            levenshteinBlanket = newBlanket
            blanketSeq = enumSeqSmart(levenshteinBlanket).iterator()
            println("Levenshtein blanket: ${levenshteinBlanket.joinToString(" ")}")
          }
        }
      }
  }
}

/**
 * We define the Levenshtein blanket as the union of all hole locations that overlap a
 * minimal admissible patch. Crucially, the patches must be minimal, see [minimizeFix].
 */

fun updateLevenshteinBlanket(oldBlanket: List<String>, newRepair: List<String>) =
  levenshteinAlign(oldBlanket, newRepair).map { (old, new) ->
    if (old == null || new == null || old != new) "_" else old
  }

@JvmName("updateLevenshteinBlanketInt")
fun updateLevenshteinBlanket(oldBlanket: List<Int>, newRepair: List<Int>) =
  levenshteinAlign(oldBlanket, newRepair).map { (old, new) ->
    if (old == null || new == null || old != new) -1 else old
  }

fun List<Int>.toStrLevBlanket(imap: (Int) -> String) = map { if (it == -1) "_" else imap(it) }