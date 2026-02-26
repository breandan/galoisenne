package ai.hypergraph.kaliningraph.sat

import ai.hypergraph.kaliningraph.parsing.*
import com.google.ortools.Loader
import com.google.ortools.sat.*
import kotlin.system.measureTimeMillis
import kotlin.time.measureTimedValue

// Tries to find the minimal triclique cover for a CFG in CNF
// Usually the greedy heuristic is very close to optimal
class TricliqueCoverSolver(val cfg: CFG, var bcc: Array<UVW>? = null) {
  private val Int.wordIdx get() = this shr 6
  private val Int.bitMask get() = 1L shl (this and 63)
  private fun LongArray.setBit(idx: Int) { this[idx.wordIdx] = this[idx.wordIdx] or idx.bitMask }

  private val ntById: List<String> by lazy { cfg.nonterminals.toList() }
  private fun ntName(idx: Int): String = ntById.getOrElse(idx) { "<nt:$idx>" }

  private inline fun LongArray.forEachSetBit(f: (Int) -> Unit) {
    for (w in indices) {
      var x = this[w]
      while (x != 0L) {
        val b = x.countTrailingZeroBits()
        f((w shl 6) + b)
        x = x and (x - 1L)
      }
    }
  }

  private fun LongArray.popCount(): Int = sumOf { it.countOneBits() }

  private fun LongArray.render(symbolOf: (Int) -> String): String =
    buildString {
      append("{")
      var first = true
      forEachSetBit { idx ->
        if (!first) append(", ")
        append(symbolOf(idx))
        first = false
      }
      append("}")
    }

  private fun UVW.volume(): Long = W.popCount().toLong() * U.popCount().toLong() * V.popCount().toLong()

  /**
   * Summarize the largest tricliques in the chosen cover.
   *
   * W = parent nonterminals A
   * U = left-child nonterminals B
   * V = right-child nonterminals C
   *
   * By default, symbols are printed as raw integer ids. If you have an inverse
   * index, pass it in via `symbolOf`.
   */
  fun summary(k: Int = 10): String {
    val cover = bcc ?: return "Triclique cover is null."
    if (cover.isEmpty()) return "Triclique cover is empty."

    val top = cover.indices
      .map { i -> i to cover[i] }
      .sortedWith(
        compareByDescending<Pair<Int, UVW>> { it.second.volume() }
          .thenByDescending { it.second.W.popCount() }
          .thenByDescending { it.second.U.popCount() }
          .thenByDescending { it.second.V.popCount() }
          .thenBy { it.first }
      )
      .take(k.coerceAtLeast(0))

    val totalVolume = cover.sumOf { it.volume() }

    return buildString {
      appendLine("Triclique cover: ${cover.size} tricliques")
      appendLine("Total covered cartesian volume: $totalVolume")
      appendLine("Top ${top.size} tricliques by |W|×|U|×|V|:")
      for ((rank, entry) in top.withIndex()) {
        val (idx, t) = entry
        val wN = t.W.popCount()
        val uN = t.U.popCount()
        val vN = t.V.popCount()
        appendLine("#${rank + 1} [cover[$idx]] volume=${t.volume()} ($wN × $uN × $vN)")
        appendLine("  W / parents A ($wN): ${t.W.render(::ntName)}")
        appendLine("  U / left    B ($uN): ${t.U.render(::ntName)}")
        appendLine("  V / right   C ($vN): ${t.V.render(::ntName)}")
      }
    }.trimEnd()
  }

  init {
    if (bcc == null) {
      var greedyCoverSize = 0
      measureTimeMillis { greedyCoverSize = cfg.greedyTricliqueCover.size }
        .also { println("Greedy Heuristic found cover of size: $greedyCoverSize in ${it}ms") }

      bcc = measureTimedValue { computeExactCover(greedyCoverSize, 999.0) }
        .also { println("CP-SAT Solver found optimal cover of size: ${it.value.size} in ${it.duration}") }
        .value
    }
  }

  private fun LongArray.serializeStr(): String = joinToString(",") { it.toULong().toString(16) }
  fun serialize(): String = bcc!!.joinToString(";") { "${it.U.serializeStr()}|${it.V.serializeStr()}|${it.W.serializeStr()}" }

  fun computeExactCover(greedyUpperBound: Int, timeoutSeconds: Double = 30.0): Array<UVW> {
    Loader.loadNativeLibraries()

    if (greedyUpperBound == 0) return emptyArray()
    val numWords = (cfg.nonterminals.size + 63) shr 6
    if (numWords == 0) return emptyArray()

    // ---------------- bitset helpers ----------------
    fun LongArray.isZero(): Boolean { for (x in this) if (x != 0L) return false; return true }
    fun LongArray.cloneBits(): LongArray = this.clone()
    fun LongArray.andAssign(other: LongArray) { for (i in indices) this[i] = this[i] and other[i] }
    fun LongArray.hasBit(idx: Int): Boolean = (this[idx.wordIdx] and idx.bitMask) != 0L

    fun LongArray.forEachSetBit(f: (Int) -> Unit) {
      for (w in indices) {
        var x = this[w]
        while (x != 0L) {
          val b = x.countTrailingZeroBits()
          f((w shl 6) + b)
          x = x and (x - 1L)
        }
      }
    }

    fun pack2(x: Int, y: Int): Long =
      (x.toLong() shl 32) or (y.toLong() and 0xffff_ffffL)

    // Pack (a,b,c) into Long to avoid Triple allocations.
    // Assumes ids < 2^21. If you might exceed that, increase SHIFT.
    val SHIFT = 21
    val MASK = (1L shl SHIFT) - 1L
    fun pack3(a: Int, b: Int, c: Int): Long =
      (a.toLong() shl (2 * SHIFT)) or (b.toLong() shl SHIFT) or (c.toLong() and MASK)
    fun aOf(x: Long): Int = (x ushr (2 * SHIFT)).toInt()
    fun bOf(x: Long): Int = ((x ushr SHIFT) and MASK).toInt()
    fun cOf(x: Long): Int = (x and MASK).toInt()

    // ---------------- 1) Extract rules + adjacency maps ----------------
    val rules = ArrayList<Long>(8192)

    // A(b,c): parents a
    val aMap = HashMap<Long, LongArray>(8192)
    // B(a,c): middle b
    val bMap = HashMap<Long, LongArray>(8192)
    // C(a,b): right c
    val cMap = HashMap<Long, LongArray>(8192)

    fun getOrCreate(map: HashMap<Long, LongArray>, key: Long): LongArray =
      map.getOrPut(key) { LongArray(numWords) }

    cfg.vindex.withIndex().forEach { (A, rhs) ->
      var i = 0
      while (i + 1 < rhs.size) {
        val B = rhs[i]
        val C = rhs[i + 1]
        val t = pack3(A, B, C)
        rules.add(t)

        getOrCreate(aMap, pack2(B, C)).setBit(A)
        getOrCreate(bMap, pack2(A, C)).setBit(B)
        getOrCreate(cMap, pack2(A, B)).setBit(C)

        i += 2
      }
    }

    if (rules.isEmpty()) return emptyArray()

    // ---------------- 2) Closure operator (maximal rectangle) ----------------
    fun getU(W: LongArray, V: LongArray): LongArray {
      var res: LongArray? = null
      var empty = false
      W.forEachSetBit { a ->
        if (empty) return@forEachSetBit
        V.forEachSetBit { c ->
          val s = bMap[pack2(a, c)]
          if (s == null) { empty = true; return@forEachSetBit }
          if (res == null) res = s.cloneBits()
          else { res!!.andAssign(s); if (res!!.isZero()) { empty = true; return@forEachSetBit } }
        }
      }
      return if (empty) LongArray(numWords) else (res ?: LongArray(numWords))
    }

    fun getV(W: LongArray, U: LongArray): LongArray {
      var res: LongArray? = null
      var empty = false
      W.forEachSetBit { a ->
        if (empty) return@forEachSetBit
        U.forEachSetBit { b ->
          val s = cMap[pack2(a, b)]
          if (s == null) { empty = true; return@forEachSetBit }
          if (res == null) res = s.cloneBits()
          else { res!!.andAssign(s); if (res!!.isZero()) { empty = true; return@forEachSetBit } }
        }
      }
      return if (empty) LongArray(numWords) else (res ?: LongArray(numWords))
    }

    fun getW(U: LongArray, V: LongArray): LongArray {
      var res: LongArray? = null
      var empty = false
      U.forEachSetBit { b ->
        if (empty) return@forEachSetBit
        V.forEachSetBit { c ->
          val s = aMap[pack2(b, c)]
          if (s == null) { empty = true; return@forEachSetBit }
          if (res == null) res = s.cloneBits()
          else { res!!.andAssign(s); if (res!!.isZero()) { empty = true; return@forEachSetBit } }
        }
      }
      return if (empty) LongArray(numWords) else (res ?: LongArray(numWords))
    }

    fun closeFromTriple(a: Int, b: Int, c: Int): UVW {
      var W = LongArray(numWords).apply { setBit(a) }
      var U = LongArray(numWords).apply { setBit(b) }
      var V = LongArray(numWords).apply { setBit(c) }

      repeat(32) {
        val U2 = getU(W, V)
        val V2 = getV(W, U2)
        val W2 = getW(U2, V2)
        if (W2.contentEquals(W) && U2.contentEquals(U) && V2.contentEquals(V)) {
          return UVW(U = U2, V = V2, W = W2)
        }
        W = W2; U = U2; V = V2
      }
      return UVW(U = U, V = V, W = W)
    }

    // ---------------- 3) Enumerate ALL maximal rectangles (dedupe) ----------------
    data class Key(val wh: Int, val uh: Int, val vh: Int)
    val pool = ArrayList<UVW>(4096)
    val buckets = HashMap<Key, MutableList<UVW>>(4096)

    fun addRect(r: UVW) {
      if (r.W.isZero() || r.U.isZero() || r.V.isZero()) return
      val key = Key(r.W.contentHashCode(), r.U.contentHashCode(), r.V.contentHashCode())
      val b = buckets.getOrPut(key) { mutableListOf() }
      if (b.any { it.W.contentEquals(r.W) && it.U.contentEquals(r.U) && it.V.contentEquals(r.V) }) return
      b.add(r)
      pool.add(r)
    }

    // Close every incidence triple; duplicates collapse to the set of all triconcepts.
    for (t in rules) addRect(closeFromTriple(aOf(t), bOf(t), cOf(t)))

    if (pool.isEmpty()) return emptyArray()

    // ---------------- 4) Exact minimum set cover over the complete pool ----------------
    val model = CpModel()
    val X = Array(pool.size) { i -> model.newBoolVar("X_$i") }
    model.addLessOrEqual(LinearExpr.sum(X), greedyUpperBound.toLong()) // safe: closing preserves cover size

    // Inverted indices to build coverage clauses fast
    val nSyms = cfg.nonterminals.size
    class IntVec(init: Int = 4) {
      var a = IntArray(init); var n = 0
      fun add(x: Int) { if (n == a.size) a = a.copyOf(maxOf(4, a.size * 2)); a[n++] = x }
      fun toArray(): IntArray = a.copyOf(n)
    }

    val byW = Array(nSyms) { IntVec() }
    val byU = Array(nSyms) { IntVec() }
    val byV = Array(nSyms) { IntVec() }

    for (i in pool.indices) {
      pool[i].W.forEachSetBit { a -> if (a in 0 until nSyms) byW[a].add(i) }
      pool[i].U.forEachSetBit { b -> if (b in 0 until nSyms) byU[b].add(i) }
      pool[i].V.forEachSetBit { c -> if (c in 0 until nSyms) byV[c].add(i) }
    }

    val wLists = Array(nSyms) { byW[it].toArray() }
    val uLists = Array(nSyms) { byU[it].toArray() }
    val vLists = Array(nSyms) { byV[it].toArray() }

    // Stamp-based 3-way intersection
    val stamp = IntArray(pool.size)
    var curStamp = 1
    fun intersect3(a: IntArray, b: IntArray, c: IntArray): IntArray {
      if (a.isEmpty() || b.isEmpty() || c.isEmpty()) return IntArray(0)
      val lists = arrayOf(a, b, c).sortedBy { it.size }
      val s1 = lists[0]; val s2 = lists[1]; val s3 = lists[2]
      curStamp++; val s = curStamp
      for (x in s1) stamp[x] = s
      curStamp++; val s2mark = curStamp
      val tmp = IntArray(s2.size)
      var t = 0
      for (x in s2) if (stamp[x] == s) { stamp[x] = s2mark; tmp[t++] = x }
      val out = IntArray(minOf(t, s3.size))
      var o = 0
      for (x in s3) if (stamp[x] == s2mark) out[o++] = x
      return out.copyOf(o)
    }

    // Coverage constraints
    for (t in rules) {
      val a = aOf(t); val b = bOf(t); val c = cOf(t)
      val cand = intersect3(wLists[a], uLists[b], vLists[c])
      if (cand.isEmpty()) error("No rectangle covers rule ($a,$b,$c) — pool incomplete (bug).")
      model.addBoolOr(Array(cand.size) { i -> X[cand[i]] })
    }

    model.minimize(LinearExpr.sum(X))

    val log = false // set true only when debugging
    val solver = CpSolver().apply {
      parameters
        .setNumSearchWorkers(8)
        .setMaxTimeInSeconds(timeoutSeconds)
        .setLogSearchProgress(log)
        .build()
    }

    val status = solver.solve(model)
    if (status != CpSolverStatus.OPTIMAL && status != CpSolverStatus.FEASIBLE) {
      println("Solver failed or infeasible (Status: $status)")
      return emptyArray()
    }

    val chosen = pool.indices.filter { solver.booleanValue(X[it]) }
    return Array(chosen.size) { i -> pool[chosen[i]] }
  }
}