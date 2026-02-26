package ai.hypergraph.kaliningraph.automata

import ai.hypergraph.kaliningraph.KBitSet
import ai.hypergraph.kaliningraph.parsing.*
import ai.hypergraph.kaliningraph.repair.*
import ai.hypergraph.kaliningraph.sampling.bigLFSRSequence
import ai.hypergraph.kaliningraph.tensor.UTMatrix
import ai.hypergraph.kaliningraph.types.*
import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlinx.coroutines.delay
import kotlin.math.*
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.TimeSource

// Generalized regular expression: https://planetmath.org/generalizedregularexpression
// Parsing with derivatives: https://matt.might.net/papers/might2011derivatives.pdf
sealed class GRE(open vararg val args: GRE) {
  class EPS: GRE()
  class SET(val s: KBitSet): GRE() { constructor(size: Int): this(KBitSet(size)) }
  class CUP(override vararg val args: GRE): GRE(*args)
  class CAT(val l: GRE, val r: GRE): GRE(l, r)

  fun words(terminals: List<Σᐩ>, shouldContinue: () -> Boolean = { true }): Sequence<Σᐩ> =
    enumerate(shouldContinue).takeWhile { shouldContinue() }
      .map { it.mapNotNull { terminals[it].let { if (it == "ε") null else it } }.joinToString(" ") }

  fun wordsOrdered(
    terminals: List<Σᐩ>,
    ngrams: MutableMap<List<String>, Double>,
    shouldContinue: () -> Boolean = { true }
  ) = enumerateWithPriority(ngrams, terminals).takeWhile { shouldContinue() }.distinct()
      .map { it.mapNotNull { terminals[it].let { if (it == "ε") null else it } }.joinToString(" ") }

  val admits: KBitSet by lazy { followSet() }

  // F_s(g) = { s | ∂_s(g) != ∅ }
  fun GRE.followSet(width: Int = this.width): KBitSet = when (this) {
    is EPS -> KBitSet(width)
    is SET -> s
    is CUP -> args.map { it.followSet() }.fold (KBitSet(width)) { a, b -> a.apply { or(b) } }
    is CAT -> l.followSet()
  }

  val width: Int by lazy {
    when (this) {
      is EPS -> 0
      is SET -> s.n
      is CUP -> args.maxOf { it.width }
      is CAT -> max(l.width, r.width)
    }
  }

  fun enumerate(shouldContinue: () -> Boolean = { true }): Sequence<List<Int>> = sequence {
    if (!shouldContinue()) emptySequence<List<Int>>()
    else when (this@GRE) {
      is EPS -> emptyList<Int>()
      is SET -> yieldAll(s.toList().map { listOf(it) })
      is CUP -> for (a in args.toList().shuffled()) yieldAll(a.enumerate(shouldContinue))
//      yieldAll(args.map { it.enumerate().toSet() }.reduce { a, b -> a + b })
      is CAT ->
        for (lhs in l.enumerate(shouldContinue))
          for (rhs in r.enumerate(shouldContinue))
            if (lhs.isEmpty()) { if (rhs.isEmpty()) yield(emptyList()) else rhs              }
            else               { if (rhs.isEmpty()) yield(lhs)         else yield(lhs + rhs) }
    }
  }

  // Greedy LTR decoding
  fun enumerateWithPriority(
    ngrams: MutableMap<List<String>, Double>,
    tmLst: List<Σᐩ>,
    prefix: List<Σᐩ> = listOf("BOS", "NEWLINE")
  ): Sequence<List<Int>> = sequence {
    val pfx = if (prefix.size == ngrams.keys.first().size) prefix.drop(1) else prefix
//    println("pfx: ${pfx.joinToString(" ")}")
    when (this@GRE) {
      is EPS -> emptyList<Int>()
      is SET ->
        yieldAll(s.toList().map { -(ngrams[pfx + tmLst[it]] ?: 0.0) to it }
          .sortedBy { it.first }.map { listOf(it.second) })
//        yieldAll(s.toList().map { listOf(it) })
      is CUP -> {
        val orderedChoices = admits.toList()
          .map { -(ngrams[pfx + tmLst[it]] ?: 0.0) to it }
          .sortedBy { it.first }.map { it.second }
        for (tk in orderedChoices) for (g in args.filter { it.admits[tk] })
          yieldAll(g.enumerateWithPriority(ngrams, tmLst, pfx + tmLst[tk]))
      }
//      yieldAll(args.map { it.enumerate().toSet() }.reduce { a, b -> a + b })
      is CAT ->
        for (lhs in l.enumerateWithPriority(ngrams, tmLst, pfx))
          for (rhs in r.enumerateWithPriority(ngrams, tmLst, pfx))
            if (lhs.isEmpty()) { if (rhs.isEmpty()) yield(emptyList()) else rhs              }
            else               { if (rhs.isEmpty()) yield(lhs)         else yield(lhs + rhs) }
    }
  }

  // ∂_s(g) = { w | s·w ∈ L(g) }
  fun dv(σ: Int): GRE = when (this) {
    is EPS -> null!! // ∂_s(ε) = ∅
    is SET -> if (s[σ]) EPS() else null!!
    is CUP -> args.filter { it.admits[σ] }.reduce { a, b -> a + b }
    // ∂_s(E1 · E2) = (∂_s(E1)) · E2   ∪   [if E1 nullable => ∂_s(E2)]
    is CAT -> (l.dv(σ) * r).let { dl -> if (l.nullable) dl + r.dv(σ) else dl }
  }

  val nullable by lazy { isNullable() }

  // Check whether 'g' accepts the empty string ε.
  fun isNullable(): Boolean = when (this) {
    is EPS -> true
    is SET -> false
    is CUP -> args.any { it.isNullable() }
    is CAT -> l.isNullable() && r.isNullable()
  }

  operator fun plus(g: GRE): GRE = CUP(this, g)
  operator fun times(g: GRE): GRE = CAT(this, g)

  fun flatunion(): GRE =
    if (this is CUP && args.all { it is CUP }) CUP(*args.flatMap { it.args.toList() }.toTypedArray())
    else this
  fun normalForm(): GRE = removeUnary().toSet()
  fun dedupe(): GRE = if (this is CUP) CUP(*args.associateBy { it.hash() }.values.toTypedArray()) else this
  fun removeUnary(): GRE = if (this is CUP && args.map { it.hash() }.toSet().size == 1) args.first() else this
  fun isLeafCup() = this is CUP && args.all { it is SET }
  fun toSet(): GRE = if (isLeafCup()) SET(args.map { (it as SET).s }.reduce { a, b -> a.merge(b) }) else this
  fun hash() = enumerate().toList().toSet().toString()//str()//(toString() + "#" + randomString()).also { println(it) }
  fun str(): String = when (this) {
    is EPS -> "ε"
    is SET -> "SET(${s.toList()})"
    is CUP -> "CUP(${args.joinToString(", ") { it.str() }})"
    is CAT -> "CAT(${l.str()}, ${r.str()})"
  }

//  override fun toString() = when (this) {
//    is EPS -> "ε"
//    is SET -> if (s.isEmpty()) "∅" else "( ${s.joinToString(" ")} )"
//    is UNI -> "( ${args.joinToString(" ∪ "){ "$it" }} )"
//    is CAT -> "$l $r"
//  }

  /** Like [words], but sampled pseudorandomly from the space of all derivations */
  fun sampleStrWithoutReplacement(terminals: List<Σᐩ>, stride: Int = 1, offset: Int = 0): Sequence<Σᐩ> {
    val memo = hashMapOf<GRE, GreMemo>()
    val total = this.sizeMemo(memo).size
    if (total.isZero()) return emptySequence()

    val idxSeq =
      if (6 < total.bitLength()) bigLFSRSequence(total)
      else sequence { var i = BigInteger.ZERO; while (i < total) { yield(i); i++ } }

    return idxSeq.mapIndexedNotNull { ix, bi ->
      if (ix % stride != offset) return@mapIndexedNotNull null
      val buf = ArrayList<Int>(32)
      unrank(bi, memo, buf)
      buf.mapNotNull { terminals[it].let { t -> if (t == "ε") null else t } }.joinToString(" ")
    }
  }

  private data class GreMemo(val size: BigInteger, val ranges: List<Pair<BigInteger, BigInteger>>? = null)

  // Counts derivations (like PTree.totalTrees), not unique strings.
  private fun sizeMemo(m: MutableMap<GRE, GreMemo>): GreMemo = m[this] ?: run {
    val memo = when (this) {
      is EPS -> GreMemo(BigInteger.ONE)
      is SET -> GreMemo(BigInteger.fromInt(s.cardinality()))
      is CAT -> {
        val lm = l.sizeMemo(m); val rm = r.sizeMemo(m)
        GreMemo(lm.size * rm.size)
      }
      is CUP -> {
        val child = args.map { it.sizeMemo(m).size }
        val total = child.fold(BigInteger.ZERO) { a, b -> a + b }
        val ranges =
          child.fold(listOf(BigInteger.ZERO)) { acc, it -> acc + (acc.last() + it) }
            .windowed(2) { (a, b) -> a to (b - BigInteger.ONE) }
        GreMemo(total, ranges)
      }
    }
    m[this] = memo
    memo
  }

  private fun unrank(i: BigInteger, m: MutableMap<GRE, GreMemo>, out: MutableList<Int>) {
    when (this) {
      is EPS -> return

      is SET -> {
        // pick the i-th element of the set in iteration order
        val idx = i.intValue(true)
        var k = 0
        for (t in s.iterator()) {
          if (k++ == idx) { out.add(t); return }
        }
        return
      }

      is CAT -> {
        val rm = r.sizeMemo(m).size
        val (iLeft, iRight) = i.divrem(rm)
        l.unrank(iLeft, m, out)
        r.unrank(iRight, m, out)
      }

      is CUP -> {
        val memo = sizeMemo(m)
        val ranges = memo.ranges!!
        val t = ranges.indexOfFirst { (lo, hi) -> i in lo..hi }
        val child = args[t]
        val offset = i - ranges[t].first
        child.unrank(offset, m, out)
      }
    }
  }
}

fun CFG.initGREListMat(tokens: List<Σᐩ>): UTMatrix<List<GRE?>> =
  UTMatrix(
    ts = tokens.map { token ->
      val ptreeList = MutableList<GRE?>(nonterminals.size) { null }
      (if (token != HOLE_MARKER) bimap[listOf(token)] else unitNonterminals)
        .associateWith { nt ->
          if (token != HOLE_MARKER) GRE.SET(KBitSet(terminals.size, tmMap[token]!!))
          else bimap.UNITS[nt]?.let { GRE.SET(KBitSet(tmLst.size, it.map { tmMap[it]!! })) }
        }.forEach { (k, v) -> ptreeList[bindex[k]] = v }
      ptreeList
    }.toTypedArray(),
    algebra = greAlgebra
  )

val CFG.greAlgebra: Ring<List<GRE?>> by cache {
  vindex.let {
    Ring.of(
      nil = List(nonterminals.size) { null },
      plus = { x, y -> greUnion(x, y) },
      times = { x, y -> greJoin(x, y) }
    )
  }
}

fun greUnion(l: List<GRE?>, r: List<GRE?>) =
  l.zip(r) { l, r -> if (l == null) r else if (r == null) l else l + r }

fun CFG.greJoin(left: List<GRE?>, right: List<GRE?>): List<GRE?> = vindex2.map {
  val t = it.map { (B, C) -> if (left[B] != null && right[C] != null) left[B]!! * right[C]!! else null }
  if (t.isEmpty()) null else t.reduce { acc, int -> if (acc == null) int else if (int == null) acc else acc + int }
}

fun CFG.startGRE(tokens: List<Σᐩ>): GRE? =
  initGREListMat(tokens).seekFixpoint().diagonals.last()[0][bindex[START_SYMBOL]]

fun repairWithGREAtDist(brokenStr: List<Σᐩ>, cfg: CFG, d: Int): Pair<GRE.CUP, Int>? {
  val upperBound = MAX_RADIUS * 3
//  val monoEditBounds = cfg.maxParsableFragmentB(brokenStr, pad = upperBound)
  val timer = TimeSource.Monotonic.markNow()
  val bindex = cfg.bindex
  val width = cfg.nonterminals.size
  val vindex = cfg.vindex
  val ups = cfg.grpUPs
  val t2vs = cfg.tmToVidx
  val maxBranch = vindex.maxOf { it.size }
  val startIdx = bindex[START_SYMBOL]

  fun nonemptyLevInt(levFSA: FSA): Int? {
    val ap: List<List<List<Int>?>> = levFSA.allPairs
    val dp = Array(levFSA.numStates) { Array(levFSA.numStates) { BooleanArray(width) { false } } }

    levFSA.allIndexedTxs0(ups, bindex).forEach { (q0, nt, q1) -> dp[q0][q1][nt] = true }
    var minRad: Int = Int.MAX_VALUE

    // For pairs (p,q) in topological order
    for (dist: Int in 1..<dp.size) {
      for (p: Int in 0..<dp.size - dist) {
        val q = p + dist
        val appq = ap[p][q] ?: continue
        for ((A: Int, indexArray: IntArray) in vindex.withIndex()) {
          outerloop@for(j: Int in 0..<indexArray.size step 2) {
            val B = indexArray[j]
            val C = indexArray[j + 1]
            for (r in appq)
              if (dp[p][r][B] && dp[r][q][C]) {
                dp[p][q][A] = true
                break@outerloop
              }
          }

          if (p == 0 && A == startIdx && q in levFSA.levFinalIdxs && dp[p][q][A]) {
            val (x, y) = levFSA.idsToCoords[q]!!
            /** See final state conditions for [makeExactLevCFL] */
            // The minimum radius such that this final state is included in the L-FSA
            minRad = minOf(minRad, (brokenStr.size - x + y).absoluteValue)
          }
        }
      }
    }

    return if (minRad == Int.MAX_VALUE) null else minRad
  }

  val led = (1..<upperBound)
    .firstNotNullOfOrNull { nonemptyLevInt(makeLevFSA(brokenStr, it)) }
      ?: upperBound.also { println("Hit upper bound") }
  val diff = d - led
  val radius = d

//  println("Identified LED=$led, radius=$radius in ${timer.elapsedNow()}")

  val levFSA = makeLevFSA(brokenStr, radius)

  val nStates = levFSA.numStates
  val tml = cfg.tmLst
  val tms = tml.size
  val tmm = cfg.tmMap

  // 1) Create dp array of parse trees
  val dp: Array<Array<Array<GRE?>>> = Array(nStates) { Array(nStates) { Array(width) { null } } }

  // 2) Initialize terminal productions A -> a
  val aitx = levFSA.allIndexedTxs1(ups)
  for ((p, σ, q) in aitx) for (Aidx in t2vs[tmm[σ]!!])
    dp[p][q][Aidx] = ((dp[p][q][Aidx] as? GRE.SET) ?: GRE.SET(tms))
      .apply { s.set(tmm[σ]!!)/*; dq[p][q].set(Aidx)*/ }

  var maxChildren = 0
//  var location = -1 to -1

  // 3) CYK + Floyd Warshall parsing
  for (dist in 1..<nStates) {
    for (p in 0..<(nStates - dist)) {
      val q = p + dist
      val appq = levFSA.allPairs[p][q] ?: continue

      for ((Aidx, indexArray) in vindex.withIndex()) {
        //      println("${cfg.bindex[Aidx]}(${pm!!.ntLengthBounds[Aidx]}):${levFSA.stateLst[p]}-${levFSA.stateLst[q]}(${levFSA.SPLP(p, q)})")
        val rhsPairs = dp[p][q][Aidx]?.let { mutableListOf(it) } ?: mutableListOf()
        outerLoop@for (j in 0..<indexArray.size step 2) {
          val Bidx = indexArray[j]
          val Cidx = indexArray[j + 1]
          for (r in appq) {
            val left = dp[p][r][Bidx] ?: continue
            val right = dp[r][q][Cidx] ?: continue
            // Found a parse for A
            rhsPairs += left * right
            //            if (rhsPairs.size > 10) break@outerLoop
          }
        }

        val list = rhsPairs.toTypedArray()
        if (rhsPairs.isNotEmpty()) {
          if (list.size > maxChildren) maxChildren = list.size
          dp[p][q][Aidx] = if (list.size == 1) list.first() else GRE.CUP(*list)
        }
      }
    }
  }

  // 4) Gather final parse trees from dp[0][f][startIdx], for all final states f
  val allParses = levFSA.levFinalIdxs.mapNotNull { q -> dp[0][q][startIdx] }

  // 5) Combine under a single GRE
  return (if (allParses.isEmpty()) null else GRE.CUP(*allParses.toTypedArray()) to diff)
}

var latestLangEditDistance = 0
fun repairWithGRE(brokenStr: List<Σᐩ>, cfg: CFG): GRE? {
  val upperBound = MAX_RADIUS * 3
//  val monoEditBounds = cfg.maxParsableFragmentB(brokenStr, pad = upperBound)
  val timer = TimeSource.Monotonic.markNow()
  val bindex = cfg.bindex
  val width = cfg.nonterminals.size
  val vindex = cfg.vindex
  val ups = cfg.grpUPs
  val t2vs = cfg.tmToVidx
  val maxBranch = vindex.maxOf { it.size }
  val startIdx = bindex[START_SYMBOL]

  fun nonemptyLevInt(levFSA: FSA): Int? {
    val ap: List<List<List<Int>?>> = levFSA.allPairs
    val dp = Array(levFSA.numStates) { Array(levFSA.numStates) { BooleanArray(width) { false } } }
    levFSA.allIndexedTxs2(ups, bindex).forEach { (q0, nt, q1) -> dp[q0][q1][nt] = true }
    var minRad = Int.MAX_VALUE
    // For pairs (p,q) in topological order
    for (dist in 1..<dp.size) {
      for (p in 0..<dp.size - dist) {
        val q = p + dist
        val appq = ap[p][q] ?: continue
        for ((A, indexArray) in vindex.withIndex()) {
          outerloop@for(j in 0..<indexArray.size step 2) {
            val B = indexArray[j]
            val C = indexArray[j + 1]
            for (r in appq)
              if (dp[p][r][B] && dp[r][q][C]) {
                dp[p][q][A] = true
                if (p == 0 && A == startIdx && levFSA.isFinal[q]) {
                  val (x, y) = levFSA.idsToCoords[q]!!
                  /** See final state conditions for [makeExactLevCFL] */
                  // The minimum radius such that this final state is included in the L-FSA
                  minRad = minOf(minRad, (brokenStr.size - x + y).absoluteValue)
                  if (minRad == 1) return 1
                }
                break@outerloop
              }
          }
        }
      }
    }
    return if (minRad == Int.MAX_VALUE) null else minRad
  }

  val led = (3..<upperBound)
    .firstNotNullOfOrNull { nonemptyLevInt(makeLevFSA(brokenStr, it)) } ?:
  upperBound.also { println("Hit upper bound") }
  val radius = (led + LED_BUFFER).coerceAtMost(MAX_RADIUS.coerceAtLeast(led))
  latestLangEditDistance = led

  println("Identified LED=$led, radius=$radius in ${timer.elapsedNow()}")

  val levFSA = makeLevFSA(brokenStr, radius)

  val nStates = levFSA.numStates
  val tml = cfg.tmLst
  val tms = tml.size
  val tmm = cfg.tmMap

  // 1) Create dp array of parse trees
  val dp: Array<Array<Array<GRE?>>> = Array(nStates) { Array(nStates) { Array(width) { null } } }

  // 2) Initialize terminal productions A -> a
  val aitx = levFSA.allIndexedTxs1(ups)
  for ((p, σ, q) in aitx) for (Aidx in t2vs[tmm[σ]!!])
    dp[p][q][Aidx] = ((dp[p][q][Aidx] as? GRE.SET) ?: GRE.SET(tms))
      .apply { s.set(tmm[σ]!!)/*; dq[p][q].set(Aidx)*/ }

  var maxChildren = 0
//  var location = -1 to -1

  // 3) CYK + Floyd Warshall parsing
  for (dist in 1..<nStates) {
    for (p in 0..<(nStates - dist)) {
      val q = p + dist
      val appq = levFSA.allPairs[p][q] ?: continue

      for ((Aidx, indexArray) in vindex.withIndex()) {
        //      println("${cfg.bindex[Aidx]}(${pm!!.ntLengthBounds[Aidx]}):${levFSA.stateLst[p]}-${levFSA.stateLst[q]}(${levFSA.SPLP(p, q)})")
        val rhsPairs = dp[p][q][Aidx]?.let { mutableListOf(it) } ?: mutableListOf()
        outerLoop@for (j in 0..<indexArray.size step 2) {
          val Bidx = indexArray[j]
          val Cidx = indexArray[j + 1]
          for (r in appq) {
            val left = dp[p][r][Bidx] ?: continue
            val right = dp[r][q][Cidx] ?: continue
            // Found a parse for A
            rhsPairs += left * right
            //            if (rhsPairs.size > 10) break@outerLoop
          }
        }

        val list = rhsPairs.toTypedArray()
        if (rhsPairs.isNotEmpty()) {
          if (list.size > maxChildren) maxChildren = list.size
          dp[p][q][Aidx] = if (list.size == 1) list.first() else GRE.CUP(*list)
        }
      }
    }
  }

//  var max = 0
//  var tot = 0
//  var dnm = 0
//  val nts = cfg.nonterminals.size
//  for (p in 0..<nStates) for (q in 0..<nStates)
//    dp[p][q].count { it != null }.also { cnt -> tot += cnt; dnm += nts; if (cnt > max) max = cnt }
//  println("Max: $max / tot: $tot / avg: ${tot.toDouble() / dnm} / NTs: ${cfg.nonterminals.size}")

  println("Completed parse matrix in: ${timer.elapsedNow()}")

  // 4) Gather final parse trees from dp[0][f][startIdx], for all final states f
  val allParses = levFSA.levFinalIdxs.mapNotNull { q -> dp[0][q][startIdx] }

  // 5) Combine under a single GRE
  return if (allParses.isEmpty()) null else GRE.CUP(*allParses.toTypedArray())
}

fun initiateSerialRepair(brokenStr: List<Σᐩ>, cfg: CFG): Sequence<Σᐩ> {
  val repair = repairWithSparseGRE(brokenStr, cfg)
//  val repair = repairWithGRE(brokenStr, cfg)
  val clock = TimeSource.Monotonic.markNow()
  return repair?.words(cfg.tmLst) { clock.elapsedNow().inWholeMilliseconds < TIMEOUT_MS } ?: emptySequence()
}

// Same as serial repair, but with strategic pauses to prevent stuttering on single-threaded runtimes
suspend fun initiateSuspendableRepair(brokenStr: List<Σᐩ>, cfg: CFG): GRE? {
  val upperBound = MAX_RADIUS * 3
//  val monoEditBounds = cfg.maxParsableFragmentB(brokenStr, pad = upperBound)
  val timer = TimeSource.Monotonic.markNow()
  val bindex = cfg.bindex
  val width = cfg.nonterminals.size
  val vindex = cfg.vindex
  val ups = cfg.grpUPs
  val t2vs = cfg.tmToVidx
  val maxBranch = vindex.maxOf { it.size }
  val startIdx = bindex[START_SYMBOL]

  var spin = 0; suspend fun pause(mask: Int = (1 shl 18) - 1) { if ((++spin and mask) == 0) delay(50.nanoseconds) }

  suspend fun nonemptyLevInt(levFSA: FSA): Int? {
    val ap: List<List<List<Int>?>> = levFSA.allPairs
    val dp = Array(levFSA.numStates) { Array(levFSA.numStates) { BooleanArray(width) { false } } }

    levFSA.allIndexedTxs2(ups, bindex).forEach { (q0, nt, q1) -> dp[q0][q1][nt] = true }
    var minRad: Int = Int.MAX_VALUE

    // For pairs (p,q) in topological order
    for (dist: Int in 1..<dp.size) {
      for (p: Int in 0..<dp.size - dist) {
        val q = p + dist
        val appq = ap[p][q] ?: continue
        for ((A: Int, indexArray: IntArray) in vindex.withIndex()) {
          pause()
          outerloop@for(j: Int in 0..<indexArray.size step 2) {
            val B = indexArray[j]
            val C = indexArray[j + 1]
            for (r in appq)
              if (dp[p][r][B] && dp[r][q][C]) {
                dp[p][q][A] = true
                if (p == 0 && A == startIdx && levFSA.isFinal[q]) {
                  val (x, y) = levFSA.idsToCoords[q]!!
                  /** See final state conditions for [makeExactLevCFL] */
                  // The minimum radius such that this final state is included in the L-FSA
                  minRad = minOf(minRad, (brokenStr.size - x + y).absoluteValue)
                  if (minRad == 1) return 1
                }
                break@outerloop
              }
          }
        }
      }
    }

    return if (minRad == Int.MAX_VALUE) null else minRad
  }

  val led = (3..<upperBound)
    .firstNotNullOfOrNull { nonemptyLevInt(makeLevFSA(brokenStr, it)) } ?:
  upperBound.also { println("Hit upper bound") }
  val radius = led + LED_BUFFER

  println("Identified LED=$led, radius=$radius in ${timer.elapsedNow()}")

  val levFSA = makeLevFSA(brokenStr, radius)

  val nStates = levFSA.numStates
  val tml = cfg.tmLst
  val tms = tml.size
  val tmm = cfg.tmMap

  // 1) Create dp array of parse trees
  val dp: Array<Array<Array<GRE?>>> = Array(nStates) { Array(nStates) { Array(width) { null } } }

  // 2) Initialize terminal productions A -> a
  val aitx = levFSA.allIndexedTxs1(ups)
  for ((p, σ, q) in aitx) for (Aidx in t2vs[tmm[σ]!!])
    dp[p][q][Aidx] = ((dp[p][q][Aidx] as? GRE.SET) ?: GRE.SET(tms))
      .apply { pause(); s.set(tmm[σ]!!)/*; dq[p][q].set(Aidx)*/ }

  var maxChildren = 0
//  var location = -1 to -1

  // 3) CYK + Floyd Warshall parsing
  for (dist in 1 until nStates) {
    for (p in 0 until (nStates - dist)) {
      val q = p + dist
      val appq = levFSA.allPairs[p][q] ?: continue

      for ((Aidx, indexArray) in vindex.withIndex()) {
        //      println("${cfg.bindex[Aidx]}(${pm!!.ntLengthBounds[Aidx]}):${levFSA.stateLst[p]}-${levFSA.stateLst[q]}(${levFSA.SPLP(p, q)})")
        val rhsPairs = dp[p][q][Aidx]?.let { mutableListOf(it) } ?: mutableListOf()
        outerLoop@for (j in 0..<indexArray.size step 2) {
          pause()
          val Bidx = indexArray[j]
          val Cidx = indexArray[j + 1]
          for (r in appq) {
            val left = dp[p][r][Bidx] ?: continue
            val right = dp[r][q][Cidx] ?: continue
            // Found a parse for A
            rhsPairs += left * right
            //            if (rhsPairs.size > 10) break@outerLoop
          }
        }

        val list = rhsPairs.toTypedArray()
        if (rhsPairs.isNotEmpty()) {
          if (list.size > maxChildren) maxChildren = list.size
          dp[p][q][Aidx] = if (list.size == 1) list.first() else GRE.CUP(*list)
        }
      }
    }
  }

  println("Completed parse matrix in: ${timer.elapsedNow()}")

  // 4) Gather final parse trees from dp[0][f][startIdx], for all final states f
  val allParses = levFSA.levFinalIdxs.mapNotNull { q -> dp[0][q][startIdx] }

  println("Parsing took ${timer.elapsedNow()} with |σ|=${brokenStr.size}, " +
//      "|Q|=$nStates, |G|=${cfg.size}, maxBranch=$maxBranch, |V|=$width, |Σ|=$tms, maxChildren=$maxChildren@$location")
      "|Q|=$nStates, |G|=${cfg.size}, maxBranch=$maxBranch, |V|=$width, |Σ|=$tms")
  // 5) Combine them under a single GRE
  return if (allParses.isEmpty()) null else GRE.CUP(*allParses.toTypedArray())
}

// Sparse repair, for large grammars w/ 10^9+ nonterminals
fun repairWithSparseGRE(brokenStr: List<Σᐩ>, cfg: CFG): GRE? {
  val upperBound = MAX_RADIUS * 3
  val timer = TimeSource.Monotonic.markNow()
  val startIdx = cfg.bindex[START_SYMBOL]
  val ladj = cfg.leftAdj

  fun nonemptyLevIntSparse(levFSA: FSA): Int? {
    val n = levFSA.numStates
    val ap = levFSA.allPairs
    val W = cfg.nonterminals.size

    val dp = Array(n) { Array(n) { KBitSet(W) } }
    // aCount[p][q]: quick zero test to skip empty chart entries
    val aCount = Array(n) { IntArray(n) }

    levFSA.allIndexedTxs2(cfg.grpUPs, cfg.bindex)
      .forEach { (p, nt, q) -> if (!dp[p][q][nt]) { dp[p][q].set(nt); aCount[p][q]++ } }

    var minRad = Int.MAX_VALUE

    for (dist in 1 until n) {
      var p = 0
      while (p < n - dist) {
        val q = p + dist
        val appq = ap[p][q]
        if (appq != null) {
          val tgt = dp[p][q]

          var i = 0
          while (i < appq.size) {
            val r = appq[i]

            if (aCount[p][r] == 0 || aCount[r][q] == 0) { i++; continue }

            val leftBits = dp[p][r]
            val rightBits = dp[r][q]

            for (B in leftBits.iterator()) {
              val adj = ladj[B] ?: continue
              adj.forEachIfIn(rightBits) { _, A ->
                if (!tgt[A]) {
                  tgt.set(A); aCount[p][q]++

                  if (p == 0 && A == startIdx && levFSA.isFinal[q]) {
                    val (x, y) = levFSA.idsToCoords[q]!!
                    minRad = minOf(minRad, abs(brokenStr.size - x + y))
                    if (minRad == 1) return 1
                  }
                }
              }
            }
            i++
          }
        }
        p++
      }
    }

    return if (minRad == Int.MAX_VALUE) null else minRad
  }

  val led = (3..<upperBound).firstNotNullOfOrNull { r -> nonemptyLevIntSparse(makeLevFSA(brokenStr, r)) }
    ?: upperBound.also { println("Hit upper bound") }

  val radius = (led + LED_BUFFER).coerceAtMost(MAX_RADIUS.coerceAtLeast(led))
  latestLangEditDistance = led
  println("Identified LED=$led, radius=$radius in ${timer.elapsedNow()}")

  val levFSA = makeLevFSA(brokenStr, radius)
  val n = levFSA.numStates
  val tmm = cfg.tmMap
  val t2vs = cfg.tmToVidx
  val tms = cfg.tmLst.size
  val W = cfg.nonterminals.size

  val active: Array<Array<KBitSet>> = Array(n) { Array(n) { KBitSet(W) } }
  val dp: Array<Array<MutableMap<Int, GRE>>> = Array(n) { Array(n) { mutableMapOf() } }

  levFSA.allIndexedTxs1(cfg.grpUPs).forEach { (p, σ, q) ->
    val tIdx = tmm[σ] ?: return@forEach
    val cell = dp[p][q]
    for (A in t2vs[tIdx]) {
      if (!active[p][q][A]) active[p][q].set(A)
      val existing = cell[A] as? GRE.SET
      cell[A] = (existing ?: GRE.SET(tms)).apply { s.set(tIdx) }
    }
  }

  var maxChildren = 0

  val acc: MutableMap<Int, MutableList<GRE>> = hashMapOf()

  for (dist in 1 until n) {
    var p = 0
    while (p < n - dist) {
      val q = p + dist
      val appq = levFSA.allPairs[p][q]; if (appq == null) { p++; continue }

      acc.clear()

      var i = 0
      while (i < appq.size) {
        val r = appq[i]
        val leftBits = active[p][r]
        val rightBits = active[r][q]
        val leftMap = dp[p][r]
        val rightMap = dp[r][q]

          for (B in leftBits.iterator()) {
            val adj = ladj[B] ?: continue
            adj.forEachIfIn(rightBits) { C, A ->
              val l = leftMap[B] ?: return@forEachIfIn
              val rgre = rightMap[C] ?: return@forEachIfIn
              acc.getOrPut(A) { mutableListOf() }.add(l * rgre)
            }
          }

        i++
      }

      if (acc.isNotEmpty()) {
        val cell = dp[p][q]
        val actBits = active[p][q]
        for ((A, parts) in acc) {
          if (!actBits[A]) actBits.set(A)
          val combined = if (parts.size == 1) parts[0] else GRE.CUP(*parts.toTypedArray()).flatunion()
          val prev = cell[A]
          cell[A] = if (prev == null) combined else (prev + combined).flatunion()
          if (parts.size > maxChildren) maxChildren = parts.size
        }
      }

      p++
    }
  }

//    var max = 0; var tot = 0; var dnm = 0
//    for (p in 0 until n) for (q in 0 until n) {
//      val cnt = dp[p][q].size
//      tot += cnt; dnm += W
//      if (cnt > max) max = cnt
//    }
//    println("Sparse Max: $max / tot: $tot / avg: ${tot.toDouble() / dnm} / NTs: $W")

  println("Completed sparse parse matrix in: ${timer.elapsedNow()} |Q|=$n, |G|=${cfg.size}, |V|=$W, |Σ|=$tms, maxChildren=$maxChildren")

  val allParses = levFSA.levFinalIdxs.mapNotNull { q -> dp[0][q][startIdx] }
  return if (allParses.isEmpty()) null else GRE.CUP(*allParses.toTypedArray()).flatunion()
}

fun completeWithSparseGRE(template: List<Σᐩ>, cfg: CFG): GRE? {
  val timer = TimeSource.Monotonic.markNow()
  val startIdx = cfg.bindex[START_SYMBOL]
  val W = cfg.nonterminals.size
  val nTok = template.size

  val tmm = cfg.tmMap                  // terminal -> terminal index
  val t2vs = cfg.tmToVidx              // terminal index -> IntArray of NT indices
  val unitNTs = cfg.unitNonterminals   // nonterminals that can appear over a HOLE
  val units = cfg.bimap.UNITS          // Map<NT, List<Terminal>> of unit expansions
  val ladj = cfg.leftAdj               // B -> (C -> A) adjacency for A -> B C
  val tms = cfg.tmLst.size

  // -------------------------------
  // PASS 1: Boolean CYK chart
  // -------------------------------
  val active: Array<Array<KBitSet>> = Array(nTok + 1) { Array(nTok + 1) { KBitSet(W) } }

  // Base case: spans of length 1 (i, i+1)
  for (i in 0 until nTok) {
    val tok = template[i]
    val cellBits = active[i][i + 1]

    if (tok == "_" || tok == HOLE_MARKER) {
      for (nt in unitNTs) {
        val exp = units[nt] ?: continue
        if (exp.isEmpty()) continue
        cellBits.set(cfg.bindex[nt])
      }
    } else {
      val tIdx = tmm[tok] ?: continue
      for (A in t2vs[tIdx]) cellBits.set(A)
    }
  }

  // CYK-style DP for spans of length ≥ 2
  for (len in 2..nTok) {
    var i = 0
    while (i + len <= nTok) {
      val j = i + len
      val tgtBits = active[i][j]

      var k = i + 1
      while (k < j) {
        val leftBits = active[i][k]
        val rightBits = active[k][j]
        if (leftBits.isEmpty() || rightBits.isEmpty()) { k++; continue }

        for (B in leftBits.iterator())
          (ladj[B] ?: continue).forEachIfIn(rightBits) { _, A ->
            if (!tgtBits[A]) tgtBits.set(A)
          }

        k++
      }
      i++
    }
  }

  if (!active[0][nTok][startIdx]) {
    println("No completion: START does not derive the template under the hole semantics.")
    return null
  }

  // -------------------------------
  // PASS 2: Sparse GRE chart
  // -------------------------------

  // Precompute GRE.SET for hole-lexical expansions (NT -> SET(terminals))
  val holeGre: Array<GRE?> = arrayOfNulls(W)
  for (nt in unitNTs) {
    val ntIdx = cfg.bindex[nt]
    val exp = units[nt] ?: continue
    if (exp.isEmpty()) continue

    val bs = KBitSet(tms)
    for (term in exp) {
      val tid = tmm[term] ?: continue
      bs.set(tid)
    }
    if (!bs.isEmpty()) holeGre[ntIdx] = GRE.SET(bs)
  }

  // dp[i][j]: map NT-index -> GRE for template[i..j)
  val dp: Array<Array<MutableMap<Int, GRE>>> =
    Array(nTok + 1) { Array(nTok + 1) { mutableMapOf() } }

  // Base case: spans of length 1
  for (i in 0 until nTok) {
    val tok = template[i]
    val cellBits = active[i][i + 1]
    val cellMap = dp[i][i + 1]

    if (tok == "_" || tok == HOLE_MARKER) {
      for (nt in unitNTs) {
        val A = cfg.bindex[nt]
        if (!cellBits[A]) continue
        val g = holeGre[A] ?: continue
        cellMap[A] = g
      }
    } else {
      val tIdx = tmm[tok] ?: continue
      for (A in t2vs[tIdx]) {
        if (!cellBits[A]) continue
        // (Usually unique, but allow merging just in case)
        val prev = cellMap[A] as? GRE.SET
        cellMap[A] = (prev ?: GRE.SET(tms)).apply { s.set(tIdx) }
      }
    }
  }

  var maxChildren = 0
  val acc: MutableMap<Int, MutableList<GRE>> = hashMapOf()

  // Internal spans: 2..nTok
  for (len in 2..nTok) {
    var i = 0
    while (i + len <= nTok) {
      val j = i + len
      val tgtBits = active[i][j]
      if (tgtBits.isEmpty()) { i++; continue }

      acc.clear()

      var k = i + 1
      while (k < j) {
        val leftBits = active[i][k]
        val rightBits = active[k][j]
        if (leftBits.isEmpty() || rightBits.isEmpty()) { k++; continue }

        val leftMap = dp[i][k]
        val rightMap = dp[k][j]

        for (B in leftBits.iterator()) {
          val adj = ladj[B] ?: continue
          val lgre = leftMap[B] ?: continue

          adj.forEachIfIn(rightBits) { C, A ->
            if (!tgtBits[A]) return@forEachIfIn
            val rgre = rightMap[C] ?: return@forEachIfIn
            acc.getOrPut(A) { mutableListOf() }.add(lgre * rgre)
          }
        }

        k++
      }

      if (acc.isNotEmpty()) {
        val cellMap = dp[i][j]
        for ((A, parts) in acc) {
          val combined = if (parts.size == 1) parts[0] else GRE.CUP(*parts.toTypedArray()).flatunion()

          val prev = cellMap[A]
          cellMap[A] = if (prev == null) combined else (prev + combined).flatunion()

          if (parts.size > maxChildren) maxChildren = parts.size
        }
      }

      i++
    }
  }

  val result = dp[0][nTok][startIdx]
  println("Completed sparse completion chart in ${timer.elapsedNow()} |w|=$nTok, |V|=$W, maxChildren=$maxChildren")
  return result?.flatunion()
}