package ai.hypergraph.kaliningraph.automata

import ai.hypergraph.kaliningraph.KBitSet
import ai.hypergraph.kaliningraph.parsing.*
import ai.hypergraph.kaliningraph.repair.*
import ai.hypergraph.kaliningraph.tensor.UTMatrix
import ai.hypergraph.kaliningraph.types.*
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
    enumerate(shouldContinue).takeWhile { shouldContinue() }.distinct()
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
      is CUP -> for (a in args) yieldAll(a.enumerate(shouldContinue))
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

//  fun dv(σ: Int): GRE? = when (this) {
//    is EPS -> null // ∂_σ(ε) = ∅
//    is SET -> if (s[σ]) EPS() else null // ∂_σ({a}) = ε if σ = a, else ∅
//    is CUP -> {
//      val derivatives = args.mapNotNull { it.dv(σ) }
//      if (derivatives.isEmpty()) null
//      else derivatives.reduce { acc, next -> CUP(acc, next) }
//    }
//    is CAT -> {
//      val dl = l.dv(σ) // Left derivative
//      val leftPart = dl?.let { CAT(it, r) }
//      if (l.nullable) {
//        val dr = r.dv(σ) // Right derivative
//        when {
//          leftPart != null && dr != null -> CUP(leftPart, dr)
//          leftPart != null -> leftPart
//          dr != null -> dr
//          else -> null
//        }
//      } else {
//        leftPart
//      }
//    }
//  }

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

  fun toDOTGraph(rankDir: String = "TB", font: String = "Helvetica", dedupLeaves: Boolean = true): String {
    fun Int.toUnicodeSubscript(): String = when (this) {
      0 -> "\u2080"
      1 -> "\u2081"
      2 -> "\u2082"
      3 -> "\u2083"
      4 -> "\u2084"
      5 -> "\u2085"
      6 -> "\u2086"
      7 -> "\u2087"
      8 -> "\u2088"
      9 -> "\u2089"
      else -> throw IllegalArgumentException("Input must be between 0 and 9")
    }

    fun KBitSet.labelize(): String =
      (0 until n).mapNotNull {  if (this[it]) "Σ${it.toUnicodeSubscript()}" else null }.joinToString(",", "{", "}")

    data class Key(val kind: String, val payload: String)

    val nodeId   = mutableMapOf<Int, String>()
    val nodeDecl = StringBuilder()
    val edgeDecl = StringBuilder()
    var nextId = 0

    fun newNodeId() = "n${nextId++}"

    var i = 0
    fun declareNodeA(label: String, shape: String = "circle", style: String = ", style=\"rounded\"", extra: String = ""): String =
      nodeId.getOrPut(i++) {
        val id = newNodeId()
        nodeDecl.append("  $id [label=\"$label\", shape=$shape $style $extra];\n")
        id
      }

    fun declareNodeB(key: Key, label: String, shape: String = "circle"): String =
      nodeId.getOrPut(i++) {
        val id = newNodeId()
        nodeDecl.append("  $id [label=\"$label\", shape=$shape, style=\"rounded\"];\n")
        id
      }

    fun visit(g: GRE): String = when (g) {
      is EPS -> declareNodeB(Key("EPS", ""), "ε", "plaintext")

      is SET -> declareNodeA(g.s.labelize(), "box", extra = ", width=0.5")

      is CUP -> {
        if (!isLeafCup()) {
          val id = declareNodeB(Key("CUP${g.hash()}", ""), "∨")
          for (child in g.args) { edgeDecl.append("  $id -> ${visit(child)};\n") }
          id
        } else {
          val q = g.toSet() as SET
          val key = if (dedupLeaves) Key("SET", q.s.toString())
          else Key("SET${g.hashCode()}", "")
          declareNodeB(key, q.s.labelize(), "box")
        }
      }

      is CAT -> {
        val id = declareNodeA("·", "invhouse", ",", "width=0.5")
        val lId = visit(g.l);  edgeDecl.append("  $id -> $lId;\n")
        val rId = visit(g.r);  edgeDecl.append("  $id -> $rId;\n")
        id
      }
    }

    visit(this)

    return buildString {
      appendLine("strict digraph GRE {")
      appendLine("  rankdir=$rankDir;")
      appendLine("  node [order=out];")
      append(nodeDecl)
      append(edgeDecl)
      appendLine("}")
    }
  }

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
  val ups = cfg.unitProductions
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
      for (iP: Int in 0..<dp.size - dist) {
        val p = iP
        val q = iP + dist
        if (ap[p][q] == null) continue
        val appq = ap[p][q]!!
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

          if (p == 0 && A == startIdx && q in levFSA.finalIdxs && dp[p][q][A]) {
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
  var location = -1 to -1

  // 3) CYK + Floyd Warshall parsing
  for (dist in 1..<nStates) {
    for (p in 0..<(nStates - dist)) {
      val q = p + dist
      if (levFSA.allPairs[p][q] == null) continue
      val appq = levFSA.allPairs[p][q]!!

      for ((Aidx, indexArray) in vindex.withIndex()) {
        //      println("${cfg.bindex[Aidx]}(${pm!!.ntLengthBounds[Aidx]}):${levFSA.stateLst[p]}-${levFSA.stateLst[q]}(${levFSA.SPLP(p, q)})")
        val rhsPairs = dp[p][q][Aidx]?.let { mutableListOf(it) } ?: mutableListOf()
        outerLoop@for (j in 0..<indexArray.size step 2) {
          val Bidx = indexArray[j]
          val Cidx = indexArray[j + 1]
          for (r in appq) {
            val left = dp[p][r][Bidx]
            if (left == null) continue
            val right = dp[r][q][Cidx]
            if (right == null) continue
            // Found a parse for A
            rhsPairs += left * right
            //            if (rhsPairs.size > 10) break@outerLoop
          }
        }

        val list = rhsPairs.toTypedArray()
        if (rhsPairs.isNotEmpty()) {
          if (list.size > maxChildren) {
            maxChildren = list.size
            location = p to q
          }
          dp[p][q][Aidx] = if (list.size == 1) list.first() else GRE.CUP(*list)
        }
      }
    }
  }

  // 4) Gather final parse trees from dp[0][f][startIdx], for all final states f
  val allParses = levFSA.finalIdxs.mapNotNull { q -> dp[0][q][startIdx] }

  // 5) Combine under a single GRE
  return (if (allParses.isEmpty()) null else GRE.CUP(*allParses.toTypedArray()) to diff)
}

fun repairWithGRE(brokenStr: List<Σᐩ>, cfg: CFG): GRE? {
  val upperBound = MAX_RADIUS * 3
//  val monoEditBounds = cfg.maxParsableFragmentB(brokenStr, pad = upperBound)
  val timer = TimeSource.Monotonic.markNow()
  val bindex = cfg.bindex
  val width = cfg.nonterminals.size
  val vindex = cfg.vindex
  val ups = cfg.unitProductions
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
      for (iP: Int in 0..<dp.size - dist) {
        val p = iP
        val q = iP + dist
        if (ap[p][q] == null) continue
        val appq = ap[p][q]!!
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

          if (p == 0 && A == startIdx && q in levFSA.finalIdxs && dp[p][q][A]) {
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

  val led = (3..<upperBound)
    .firstNotNullOfOrNull { nonemptyLevInt(makeLevFSA(brokenStr, it)) } ?:
  upperBound.also { println("Hit upper bound") }
  val radius = (led + LED_BUFFER).coerceAtMost(MAX_RADIUS)

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
  var location = -1 to -1

  // 3) CYK + Floyd Warshall parsing
  for (dist in 1..<nStates) {
    for (p in 0..<(nStates - dist)) {
      val q = p + dist
      if (levFSA.allPairs[p][q] == null) continue
      val appq = levFSA.allPairs[p][q]!!

      for ((Aidx, indexArray) in vindex.withIndex()) {
        //      println("${cfg.bindex[Aidx]}(${pm!!.ntLengthBounds[Aidx]}):${levFSA.stateLst[p]}-${levFSA.stateLst[q]}(${levFSA.SPLP(p, q)})")
        val rhsPairs = dp[p][q][Aidx]?.let { mutableListOf(it) } ?: mutableListOf()
        outerLoop@for (j in 0..<indexArray.size step 2) {
          val Bidx = indexArray[j]
          val Cidx = indexArray[j + 1]
          for (r in appq) {
            val left = dp[p][r][Bidx]
            if (left == null) continue
            val right = dp[r][q][Cidx]
            if (right == null) continue
            // Found a parse for A
            rhsPairs += left * right
            //            if (rhsPairs.size > 10) break@outerLoop
          }
        }

        val list = rhsPairs.toTypedArray()
        if (rhsPairs.isNotEmpty()) {
          if (list.size > maxChildren) {
            maxChildren = list.size
            location = p to q
          }
          dp[p][q][Aidx] = if (list.size == 1) list.first() else GRE.CUP(*list)
        }
      }
    }
  }

  println("Completed parse matrix in: ${timer.elapsedNow()}")

  // 4) Gather final parse trees from dp[0][f][startIdx], for all final states f
  val allParses = levFSA.finalIdxs.mapNotNull { q -> dp[0][q][startIdx] }

  // 5) Combine under a single GRE
  return if (allParses.isEmpty()) null else GRE.CUP(*allParses.toTypedArray())
}

fun initiateSerialRepair(brokenStr: List<Σᐩ>, cfg: CFG): Sequence<Σᐩ> {
  val repair = repairWithGRE(brokenStr, cfg)
  val clock = TimeSource.Monotonic.markNow()
  return repair?.words(cfg.tmLst) { clock.elapsedNow().inWholeMilliseconds < TIMEOUT_MS } ?: emptySequence()
}

// Same as serial repair, but with strategic pauses to prevent stuttering on single-threaded runtimes
suspend fun initiateSuspendableRepair(brokenStr: List<Σᐩ>, cfg: CFG): GRE? {
  var i = 0
  val upperBound = MAX_RADIUS * 3
//  val monoEditBounds = cfg.maxParsableFragmentB(brokenStr, pad = upperBound)
  val timer = TimeSource.Monotonic.markNow()
  val bindex = cfg.bindex
  val width = cfg.nonterminals.size
  val vindex = cfg.vindex
  val ups = cfg.unitProductions
  val t2vs = cfg.tmToVidx
  val maxBranch = vindex.maxOf { it.size }
  val startIdx = bindex[START_SYMBOL]

  suspend fun pause(freq: Int = 300_000) { if (i++ % freq == 0) { delay(50.nanoseconds) }}

  suspend fun nonemptyLevInt(levFSA: FSA): Int? {
    val ap: List<List<List<Int>?>> = levFSA.allPairs
    val dp = Array(levFSA.numStates) { Array(levFSA.numStates) { BooleanArray(width) { false } } }

    levFSA.allIndexedTxs0(ups, bindex).forEach { (q0, nt, q1) -> dp[q0][q1][nt] = true }
    var minRad: Int = Int.MAX_VALUE

    // For pairs (p,q) in topological order
    for (dist: Int in 1..<dp.size) {
      for (iP: Int in 0..<dp.size - dist) {
        val p = iP
        val q = iP + dist
        if (ap[p][q] == null) continue
        val appq = ap[p][q]!!
        for ((A: Int, indexArray: IntArray) in vindex.withIndex()) {
          pause()
          outerloop@for(j: Int in 0..<indexArray.size step 2) {
            val B = indexArray[j]
            val C = indexArray[j + 1]
            for (r in appq)
              if (dp[p][r][B] && dp[r][q][C]) {
                dp[p][q][A] = true
                break@outerloop
              }
          }

          if (p == 0 && A == startIdx && q in levFSA.finalIdxs && dp[p][q][A]) {
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
  var location = -1 to -1

  // 3) CYK + Floyd Warshall parsing
  for (dist in 1 until nStates) {
    for (p in 0 until (nStates - dist)) {
      val q = p + dist
      if (levFSA.allPairs[p][q] == null) continue
      val appq = levFSA.allPairs[p][q]!!

      for ((Aidx, indexArray) in vindex.withIndex()) {
        //      println("${cfg.bindex[Aidx]}(${pm!!.ntLengthBounds[Aidx]}):${levFSA.stateLst[p]}-${levFSA.stateLst[q]}(${levFSA.SPLP(p, q)})")
        val rhsPairs = dp[p][q][Aidx]?.let { mutableListOf(it) } ?: mutableListOf()
        outerLoop@for (j in 0..<indexArray.size step 2) {
          pause()
          val Bidx = indexArray[j]
          val Cidx = indexArray[j + 1]
          for (r in appq) {
            val left = dp[p][r][Bidx]
            if (left == null) continue
            val right = dp[r][q][Cidx]
            if (right == null) continue
            // Found a parse for A
            rhsPairs += left * right
            //            if (rhsPairs.size > 10) break@outerLoop
          }
        }

        val list = rhsPairs.toTypedArray()
        if (rhsPairs.isNotEmpty()) {
          if (list.size > maxChildren) {
            maxChildren = list.size
            location = p to q
          }
          dp[p][q][Aidx] = if (list.size == 1) list.first() else GRE.CUP(*list)
        }
      }
    }
  }

  println("Completed parse matrix in: ${timer.elapsedNow()}")

  // 4) Gather final parse trees from dp[0][f][startIdx], for all final states f
  val allParses = levFSA.finalIdxs.mapNotNull { q -> dp[0][q][startIdx] }

  println("Parsing took ${timer.elapsedNow()} with |σ|=${brokenStr.size}, " +
      "|Q|=$nStates, |G|=${cfg.size}, maxBranch=$maxBranch, |V|=$width, |Σ|=$tms, maxChildren=$maxChildren@$location")
  // 5) Combine them under a single GRE
  return if (allParses.isEmpty()) null else GRE.CUP(*allParses.toTypedArray())
}