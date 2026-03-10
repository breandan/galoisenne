package ai.hypergraph.kaliningraph.repair

import ai.hypergraph.kaliningraph.KBitSet
import ai.hypergraph.kaliningraph.automata.*
import java.util.*
import kotlin.time.TimeSource

fun GRE.toDFSMAntimirov(tmLst: List<String>): DFSM {
  val sigma = tmLst.size
  val timer = TimeSource.Monotonic.markNow()

  class IntArrayList(capacity: Int = 4) {
    var data = IntArray(maxOf(1, capacity))
    var size = 0

    fun add(x: Int) {
      if (size == data.size) data = data.copyOf(data.size * 2)
      data[size++] = x
    }

    fun addAll(xs: IntArray) {
      val n = xs.size
      if (n == 0) return
      val minCap = size + n
      if (minCap > data.size) {
        var newCap = data.size * 2
        if (newCap < minCap) newCap = minCap
        data = data.copyOf(newCap)
      }
      xs.copyInto(data, destinationOffset = size)
      size += n
    }

    fun clear() {
      size = 0
    }

    fun toIntArray(): IntArray = data.copyOf(size)

    fun toDistinctIntArray(): IntArray {
      if (size == 0) return IntArray(0)
      val xs = data.copyOf(size)
      xs.sort()
      var k = 1
      for (i in 1 until xs.size) {
        if (xs[i] != xs[i - 1]) xs[k++] = xs[i]
      }
      return xs.copyOf(k)
    }
  }

  data class IntArrayKey(val a: IntArray) {
    override fun hashCode(): Int = a.contentHashCode()
    override fun equals(other: Any?): Boolean =
      other is IntArrayKey && a.contentEquals(other.a)
  }

  data class DerivRow(val labels: IntArray, val next: IntArray)

  fun indexOfSorted(xs: IntArray, x: Int): Int {
    var lo = 0
    var hi = xs.size - 1
    while (lo <= hi) {
      val mid = (lo + hi) ushr 1
      val v = xs[mid]
      when {
        v < x -> lo = mid + 1
        v > x -> hi = mid - 1
        else -> return mid
      }
    }
    return -1
  }

  fun mergeSortedDistinct(a: IntArray, b: IntArray): IntArray {
    if (a.isEmpty()) return b
    if (b.isEmpty()) return a

    if (a[a.lastIndex] < b[0]) {
      val out = IntArray(a.size + b.size)
      a.copyInto(out, 0)
      b.copyInto(out, a.size)
      return out
    }
    if (b[b.lastIndex] < a[0]) {
      val out = IntArray(a.size + b.size)
      b.copyInto(out, 0)
      a.copyInto(out, b.size)
      return out
    }

    val out = IntArray(a.size + b.size)
    var i = 0
    var j = 0
    var k = 0
    var haveLast = false
    var last = 0

    while (i < a.size || j < b.size) {
      val x = when {
        j == b.size || (i < a.size && a[i] < b[j]) -> a[i++]
        i == a.size || b[j] < a[i] -> b[j++]
        else -> {
          val v = a[i]
          i++
          j++
          v
        }
      }

      if (!haveLast || x != last) {
        out[k++] = x
        last = x
        haveLast = true
      }
    }

    return out.copyOf(k)
  }

  fun pairKey(a: Int, b: Int): Long =
    (a.toLong() shl 32) xor (b.toLong() and 0xffffffffL)

  val nodes = ArrayList<R>()
  val nullableMemo = ArrayList<Int>()        // -1 unknown, 0 false, 1 true
  val firstMemo = ArrayList<IntArray?>()
  val rowMemo = ArrayList<DerivRow?>()

  fun addNode(node: R): Int {
    val id = nodes.size
    nodes.add(node)
    nullableMemo.add(-1)
    firstMemo.add(null)
    rowMemo.add(null)
    return id
  }

  val EMPTY = addNode(R.Empty)
  val EPS = addNode(R.Eps)

  val setIntern = HashMap<IntArrayKey, Int>()
  val altIntern = HashMap<IntArrayKey, Int>()
  val catIntern = HashMap<Long, Int>()
  val normCatMemo = HashMap<Long, Int>()

  fun labelsOf(bs: KBitSet): IntArray {
    val out = IntArrayList()
    val d = bs.data
    for (w in d.indices) {
      var x = d[w]
      while (x != 0L) {
        val lsb = x and -x
        val bit = lsb.countTrailingZeroBits()
        val a = (w shl 6) + bit
        if (a in 0 until sigma) out.add(a)
        x = x xor lsb
      }
    }
    return out.toDistinctIntArray()
  }

  fun internSet(raw: IntArray): Int {
    if (raw.isEmpty()) return EMPTY
    val frozen = raw.copyOf()
    val key = IntArrayKey(frozen)
    return setIntern[key] ?: addNode(R.Set(frozen)).also { setIntern[key] = it }
  }

  fun internAlt(raw: IntArray): Int {
    if (raw.isEmpty()) return EMPTY

    val acc = IntArrayList(raw.size)
    for (id in raw) {
      when (val n = nodes[id]) {
        is R.Empty -> Unit
        is R.Alt -> acc.addAll(n.kids)
        else -> acc.add(id)
      }
    }

    val kids = acc.toDistinctIntArray()
    if (kids.isEmpty()) return EMPTY
    if (kids.size == 1) return kids[0]

    val frozen = kids.copyOf()
    val key = IntArrayKey(frozen)
    return altIntern[key] ?: addNode(R.Alt(frozen)).also { altIntern[key] = it }
  }

  fun internCatBase(l: Int, r: Int): Int {
    if (l == EMPTY || r == EMPTY) return EMPTY
    if (l == EPS) return r
    if (r == EPS) return l

    val key = pairKey(l, r)
    return catIntern[key] ?: addNode(R.Cat(l, r)).also { catIntern[key] = it }
  }

  fun normConcat(l: Int, r: Int): Int {
    if (l == EMPTY || r == EMPTY) return EMPTY
    if (l == EPS) return r
    if (r == EPS) return l

    val key = pairKey(l, r)
    normCatMemo[key]?.let { return it }

    val ans = when (val n = nodes[l]) {
      is R.Alt -> {
        val acc = IntArrayList(n.kids.size)
        for (kid in n.kids) {
          val c = normConcat(kid, r)
          if (c != EMPTY) acc.add(c)
        }
        internAlt(acc.toIntArray())
      }
      else -> internCatBase(l, r)
    }

    normCatMemo[key] = ans
    return ans
  }

  val importMemo = HashMap<GRE, Int>()

  fun importGre(g: GRE): Int =
    importMemo[g] ?: when (g) {
      is GRE.EPS -> EPS
      is GRE.SET -> internSet(labelsOf(g.s))
      is GRE.CUP -> {
        val acc = IntArrayList(g.args.size)
        for (child in g.args) acc.add(importGre(child))
        internAlt(acc.toIntArray())
      }
      is GRE.CAT -> internCatBase(importGre(g.l), importGre(g.r))
    }.also { importMemo[g] = it }

  fun nullable(id: Int): Boolean {
    val cached = nullableMemo[id]
    if (cached >= 0) return cached == 1

    val ans = when (val n = nodes[id]) {
      is R.Empty -> false
      is R.Eps -> true
      is R.Set -> false
      is R.Alt -> n.kids.any(::nullable)
      is R.Cat -> nullable(n.l) && nullable(n.r)
    }

    nullableMemo[id] = if (ans) 1 else 0
    return ans
  }

  fun firstLabels(id: Int): IntArray {
    val cached = firstMemo[id]
    if (cached != null) return cached

    val ans = when (val n = nodes[id]) {
      is R.Empty -> IntArray(0)
      is R.Eps -> IntArray(0)
      is R.Set -> n.labels
      is R.Alt -> {
        var acc = IntArray(0)
        for (kid in n.kids) acc = mergeSortedDistinct(acc, firstLabels(kid))
        acc
      }
      is R.Cat -> {
        val left = firstLabels(n.l)
        if (nullable(n.l)) mergeSortedDistinct(left, firstLabels(n.r)) else left
      }
    }

    firstMemo[id] = ans
    return ans
  }

  fun ensureRow(id: Int): DerivRow {
    rowMemo[id]?.let { return it }

    val row = when (val n = nodes[id]) {
      is R.Empty -> DerivRow(IntArray(0), IntArray(0))

      is R.Eps -> DerivRow(IntArray(0), IntArray(0))

      is R.Set -> {
        val labels = n.labels
        val next = IntArray(labels.size) { EPS }
        DerivRow(labels, next)
      }

      is R.Alt -> {
        val childRows = Array(n.kids.size) { i -> ensureRow(n.kids[i]) }

        var labels = IntArray(0)
        for (cr in childRows) labels = mergeSortedDistinct(labels, cr.labels)

        val next = IntArray(labels.size)
        val acc = IntArrayList()

        for (i in labels.indices) {
          val a = labels[i]
          acc.clear()

          for (cr in childRows) {
            val j = indexOfSorted(cr.labels, a)
            if (j >= 0) acc.add(cr.next[j])
          }

          next[i] = internAlt(acc.toIntArray())
        }

        DerivRow(labels, next)
      }

      is R.Cat -> {
        val leftRow = ensureRow(n.l)
        val leftNullable = nullable(n.l)
        val rightRow = if (leftNullable) ensureRow(n.r) else null

        val labels = firstLabels(id)
        val next = IntArray(labels.size)
        val acc = IntArrayList()

        for (i in labels.indices) {
          val a = labels[i]
          acc.clear()

          val jl = indexOfSorted(leftRow.labels, a)
          if (jl >= 0) {
            val c = normConcat(leftRow.next[jl], n.r)
            if (c != EMPTY) acc.add(c)
          }

          if (leftNullable) {
            val rr = rightRow!!
            val jr = indexOfSorted(rr.labels, a)
            if (jr >= 0) acc.add(rr.next[jr])
          }

          next[i] = internAlt(acc.toIntArray())
        }

        DerivRow(labels, next)
      }
    }

    rowMemo[id] = row
    return row
  }

  val start = importGre(this)

  val residualToStateId = LinkedHashMap<Int, Int>()
  val queue = ArrayDeque<Int>()
  val deltaInt = ArrayList<MutableMap<Int, Int>>()
  val finalsInt = LinkedHashSet<Int>()

  residualToStateId[start] = 0
  queue.addLast(start)
  deltaInt.add(HashMap())

  while (queue.isNotEmpty()) {
    val residual = queue.removeFirst()
    val sid = residualToStateId[residual] ?: continue

    if (nullable(residual)) finalsInt.add(sid)

    val drow = ensureRow(residual)
    val row = HashMap<Int, Int>(maxOf(4, drow.labels.size * 2))

    for (i in drow.labels.indices) {
      val a = drow.labels[i]
      val nextResidual = drow.next[i]
      if (nextResidual == EMPTY) continue

      val tid = residualToStateId[nextResidual] ?: run {
        val fresh = residualToStateId.size
        residualToStateId[nextResidual] = fresh
        queue.addLast(nextResidual)
        deltaInt.add(HashMap())
        fresh
      }

      row[a] = tid
    }

    deltaInt[sid] = row
  }

  val qCount = residualToStateId.size
  val Q = LinkedHashSet<String>(qCount)
  for (i in 0 until qCount) Q.add("q$i")

  val deltaMap = LinkedHashMap<String, Map<Int, String>>(qCount)
  for (i in 0 until qCount) {
    val src = deltaInt[i]
    val row = HashMap<Int, String>(src.size)
    for ((a, j) in src) row[a] = "q$j"
    deltaMap["q$i"] = row
  }

  val F = finalsInt.mapTo(LinkedHashSet()) { "q$it" }

  return DFSM(
    Q = Q,
    deltaMap = deltaMap,
    q_alpha = "q0",
    F = F,
    width = sigma
  ).also {
    println("toDFSMAntimirov (row-cached) took: ${timer.elapsedNow()} | |Q|=$qCount | residuals=${nodes.size}")
  }
}

sealed class R {
  data object Empty : R()
  data object Eps : R()
  class Set(val labels: IntArray) : R()
  class Alt(val kids: IntArray) : R()
  class Cat(val l: Int, val r: Int) : R()
}