package ai.hypergraph.kaliningraph.repair

import ai.hypergraph.kaliningraph.KBitSet
import ai.hypergraph.kaliningraph.automata.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.IntStream
import kotlin.time.TimeSource

/**
 * See [GRE.toDFSMDirect] for the serial version.
 */
fun GRE.toDFSMDirectParallel(tmLst: List<String>): DFSM {
  val timer = TimeSource.Monotonic.markNow()
  val sigma = tmLst.size
  val END = sigma

  val endSet = GRE.SET(KBitSet(sigma + 1).apply { set(END) })
  val root = GRE.CAT(this, endSet)

  fun countSetOccurrences(g: GRE): Int = when (g) {
    is GRE.SET -> 1
    is GRE.CUP -> g.args.sumOf { countSetOccurrences(it) }
    is GRE.CAT -> countSetOccurrences(g.l) + countSetOccurrences(g.r)
    is GRE.EPS -> 0
  }

  val P = countSetOccurrences(root)
  val posSyms = ArrayList<KBitSet>(P)

  class IntArrayList(capacity: Int = 4) {
    var data = IntArray(capacity)
    var size = 0
    fun add(element: Int) {
      if (size == data.size) data = data.copyOf(data.size * 2)
      data[size++] = element
    }
    fun toDistinctIntArray(): IntArray {
      if (size == 0) return IntArray(0)
      val sorted = data.copyOf(size)
      sorted.sort()
      var unique = 1
      for (i in 1 until size) {
        if (sorted[i] != sorted[i - 1]) sorted[unique++] = sorted[i]
      }
      return sorted.copyOf(unique)
    }
    fun addAll(elements: IntArray) {
      val numNew = elements.size
      if (numNew == 0) return
      val minCap = size + numNew
      if (minCap > data.size) {
        var newCap = data.size * 2
        if (newCap < minCap) newCap = minCap
        data = data.copyOf(newCap)
      }
      // copyInto compiles down to lightning-fast System.arraycopy on JVM
      elements.copyInto(data, destinationOffset = size)
      size += minCap
    }
  }

  val followList = Array(P) { IntArrayList() }
  fun emptyPosSet() = KBitSet(P)
  data class Info(val first: KBitSet, val last: KBitSet, val nullable: Boolean)

  var nextPos = 0
  var endPos = -1

  fun info(g: GRE): Info = when (g) {
    is GRE.EPS -> Info(emptyPosSet(), emptyPosSet(), true)
    is GRE.SET -> {
      val id = nextPos++
      posSyms += g.s
      if (g === endSet) endPos = id
      val firstSet = emptyPosSet().apply { set(id) }
      val lastSet = emptyPosSet().apply { set(id) }
      Info(firstSet, lastSet, false)
    }
    is GRE.CUP -> {
      if (g.args.isEmpty()) {
        Info(emptyPosSet(), emptyPosSet(), false)
      } else {
        val I = info(g.args[0])
        val first = I.first
        val last = I.last
        var nullb = I.nullable
        for (i in 1 until g.args.size) {
          val nextI = info(g.args[i])
          first.or(nextI.first)
          last.or(nextI.last)
          nullb = nullb || nextI.nullable
        }
        Info(first, last, nullb)
      }
    }
    is GRE.CAT -> {
      val L = info(g.l)
      val R = info(g.r)

      // 1. Extract R into a flat array once
      val rCard = R.first.cardinality()
      val rArray = IntArray(rCard)
      var rIdx = 0
      R.first.forEachSetBit { rArray[rIdx++] = it }

      // 2. Extract L to an array so we can parallelize over it
      val lCard = L.last.cardinality()
      val lArray = IntArray(lCard)
      var lIdx = 0
      L.last.forEachSetBit { lArray[lIdx++] = it }

      // 3. Parallel block-copy (Thread-safe because every 'i' is unique)
      if (lCard * rCard > 10_000) {
        // Spin up threads only if the workload justifies the ForkJoin overhead
        IntStream.of(*lArray).parallel().forEach { i -> followList[i].addAll(rArray) }
      } else {
        // Serial fallback for smaller sets
        for (idx in 0 until lCard) followList[lArray[idx]].addAll(rArray)
      }

      val first = if (L.nullable) { L.first.or(R.first); L.first } else L.first
      val last  = if (R.nullable) { R.last.or(L.last); R.last } else R.last
      Info(first, last, L.nullable && R.nullable)
    }
  }

  val rootInfo = info(root)
  check(nextPos == P) { "Position allocation mismatch: expected $P, got $nextPos" }
  check(endPos >= 0) { "Internal endmarker was not assigned a position" }

  // Parallelize array deduplication
  val follow = Array(P) { IntArray(0) }
  IntStream.range(0, P).parallel().forEach { i ->
    follow[i] = followList[i].toDistinctIntArray()
  }

  // Parallelize alphabet index precomputation
  val posBySym: Array<KBitSet> = Array(sigma) { KBitSet(P) }
  IntStream.range(0, sigma).parallel().forEach { a ->
    val set = KBitSet(P)
    for (p in 0 until P) {
      if (p != endPos && posSyms[p][a]) set.set(p)
    }
    posBySym[a] = set
  }

  data class IntKey(val a: IntArray) {
    override fun hashCode() = a.contentHashCode()
    override fun equals(other: Any?) = other is IntKey && a.contentEquals(other.a)
  }

  fun keyOf(bits: KBitSet): IntKey {
    val xs = ArrayList<Int>()
    bits.forEachSetBit { xs += it }
    return IntKey(xs.toIntArray())
  }

  // Thread-safe collections for parallel BFS processing
  val stateIdCounter = AtomicInteger(1)
  val subset2name = ConcurrentHashMap<IntKey, String>()
  val queue = ConcurrentLinkedQueue<KBitSet>()
  val deltaMap = ConcurrentHashMap<String, MutableMap<Int, String>>()
  val finals = ConcurrentHashMap.newKeySet<String>()

  val start = rootInfo.first
  subset2name[keyOf(start)] = "q0"
  queue.add(start)

  while (queue.isNotEmpty()) {
    val S = queue.poll() ?: break
    val sName = subset2name[keyOf(S)]!!
    if (S[endPos]) finals.add(sName)

    // A thread-safe row is needed because multiple characters might map to valid states concurrently
    val row = ConcurrentHashMap<Int, String>()
    deltaMap[sName] = row

    // Parallelize the inner transition loop
    IntStream.range(0, sigma).parallel().forEach { a ->
      var any = false
      val T = KBitSet(P)

      val sData = S.data
      val symData = posBySym[a].data

      // Evaluate 64 positions simultaneously
      for (w in sData.indices) {
        var overlap = sData[w] and symData[w]

        // Rapidly iterate only through the bits that are SET in both
        while (overlap != 0L) {
          val lsb = overlap and -overlap
          val bit = lsb.countTrailingZeroBits()
          val p = (w shl 6) + bit

          // Apply transitions
          val followP = follow[p]
          for (i in followP.indices) {
            T.set(followP[i])
          }
          any = true

          // Clear the lowest set bit to move to the next one
          overlap = overlap xor lsb
        }
      }

      if (any) {
        val k = keyOf(T)
        // computeIfAbsent guarantees atomicity
        val tName = subset2name.computeIfAbsent(k) {
          val n = "q${stateIdCounter.getAndIncrement()}"
          queue.add(T)
          n
        }
        row[a] = tName
      }
    }
  }

  val Qd = subset2name.values.toSet()
  return DFSM(Qd, deltaMap, "q0", finals, sigma)
    .also { println("Direct DFSM construction took: ${timer.elapsedNow()}") }
}