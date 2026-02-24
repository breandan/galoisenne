package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.automata.FSA
import ai.hypergraph.kaliningraph.automata.GRE
import ai.hypergraph.kaliningraph.automata.latestLangEditDistance
import ai.hypergraph.kaliningraph.repair.LED_BUFFER
import ai.hypergraph.kaliningraph.repair.MAX_RADIUS
import ai.hypergraph.kaliningraph.types.cache
import kotlin.math.absoluteValue
import kotlin.time.TimeSource

private val Int.wordIdx get() = this shr 6
private val Int.bitMask get() = 1L shl (this and 63)
private fun LongArray.setBit(idx: Int) { this[idx.wordIdx] = this[idx.wordIdx] or idx.bitMask }
private fun LongArray.hasInter(other: LongArray): Boolean {
  for (i in indices) if ((this[i] and other[i]) != 0L) return true
  return false
}
private infix fun LongArray.orAssign(other: LongArray) { for (i in indices) this[i] = this[i] or other[i] }

data class UVW(val U: LongArray, val V: LongArray, val W: LongArray)

// Computes maximal triclique cover with a greedy heuristic
val CFG.greedyTricliqueCover: Array<UVW> by cache {
  val timer = TimeSource.Monotonic.markNow()
  val numWords = (nonterminals.size + 63) shr 6

  // 1. Flatten all rules into R = {(A, B, C)}
  val R = mutableSetOf<Triple<Int, Int, Int>>()
  vindex.withIndex().forEach { (A, rhs) -> for (i in 0 until rhs.size step 2) R.add(Triple(A, rhs[i], rhs[i+1])) }

  // Adjacency maps for fast intersection queries
  val aMap = mutableMapOf<Pair<Int, Int>, Set<Int>>()
  val bMap = mutableMapOf<Pair<Int, Int>, Set<Int>>()
  val cMap = mutableMapOf<Pair<Int, Int>, Set<Int>>()

  R.forEach { (a, b, c) ->
    (aMap.getOrPut(b to c) { mutableSetOf() } as MutableSet).add(a)
    (bMap.getOrPut(a to c) { mutableSetOf() } as MutableSet).add(b)
    (cMap.getOrPut(a to b) { mutableSetOf() } as MutableSet).add(c)
  }

  // Helpers to fetch intersection of valid nonterminals
  fun getU(W: Set<Int>, V: Set<Int>): Set<Int> {
    var res: Set<Int>? = null
    for (a in W) for (c in V) {
      val s = bMap[a to c]
      if (s.isNullOrEmpty()) return emptySet()
      res = res?.intersect(s) ?: s
    }
    return res ?: emptySet()
  }

  fun getV(W: Set<Int>, U: Set<Int>): Set<Int> {
    var res: Set<Int>? = null
    for (a in W) for (b in U) {
      val s = cMap[a to b]
      if (s.isNullOrEmpty()) return emptySet()
      res = res?.intersect(s) ?: s
    }
    return res ?: emptySet()
  }

  fun getW(U: Set<Int>, V: Set<Int>): Set<Int> {
    var res: Set<Int>? = null
    for (b in U) for (c in V) {
      val s = aMap[b to c]
      if (s.isNullOrEmpty()) return emptySet()
      res = res?.intersect(s) ?: s
    }
    return res ?: emptySet()
  }

  // 2. Discover maximal 3-bicliques (Alternating Optimization)
  data class Rect(val W: Set<Int>, val U: Set<Int>, val V: Set<Int>) {
    val edges: Set<Triple<Int, Int, Int>> by lazy {
      val e = mutableSetOf<Triple<Int, Int, Int>>()
      for (a in W) for (b in U) for (c in V) e.add(Triple(a, b, c))
      e
    }
  }

  val maxRects = mutableSetOf<Rect>()
  for (seed in R) {
    // Start with a greedy seed: max out W immediately for a given (B, C)
    var W = aMap[seed.second to seed.third]!!
    var U = setOf(seed.second)
    var V = setOf(seed.third)

    // Bounce between dimensions until the biclique stops growing
    while (true) {
      val nextU = getU(W, V)
      val nextV = getV(W, nextU)
      val nextW = getW(nextU, nextV)

      if (W == nextW && U == nextU && V == nextV) break
      W = nextW; U = nextU; V = nextV
    }
    maxRects.add(Rect(W, U, V))
  }

  // 3. Greedy Set Cover Phase
  val uncov = R.toMutableSet()
  val cover = mutableListOf<UVW>()
  val pool = maxRects.toMutableList()

  while (uncov.isNotEmpty()) {
    // Find the rectangle that eats the biggest chunk of remaining rules
    val best = pool.maxByOrNull { rect -> rect.edges.count { it in uncov } }

    // Safety break, though theoretically impossible if generated from R
    if (best == null || best.edges.none { it in uncov }) break

    cover.add(UVW(
      LongArray(numWords).apply { best.U.forEach { setBit(it) } },
      LongArray(numWords).apply { best.V.forEach { setBit(it) } },
      LongArray(numWords).apply { best.W.forEach { setBit(it) } }
    ))

    // Remove the covered edges and discard the rectangle from the pool
    uncov.removeAll(best.edges)
    pool.remove(best)
  }

  println("Computed greedy biclique cover in ${timer.elapsedNow()}")
  cover.toTypedArray()//.also { it.prettyPrint(this) }
}

fun Array<UVW>.prettyPrint(cfg: CFG) {
  println("Biclique Cover (K=${size} components):")

  // Helper to decode the bitset back into nonterminal names
  fun LongArray.decode(): String {
    val items = mutableListOf<String>()
    for (w in indices) {
      var word = this[w]
      while (word != 0L) {
        val bit = word.countTrailingZeroBits()
        val idx = (w shl 6) + bit
        items.add(cfg.nonterminals.toList()[idx])
        word = word and (word - 1L) // Clear lowest set bit
      }
    }
    return items.joinToString(", ", "{", "}")
  }

  this.forEachIndexed { index, comp ->
    val wStr = comp.W.decode()
    val uStr = comp.U.decode()
    val vStr = comp.V.decode()
    println("  [${index.toString().padStart(3, '0')}] $wStr -> $uStr $vStr")
  }
}

private fun String.deserializeLongArray(): LongArray =
  if (isEmpty()) LongArray(0) else split(",").map { it.toULong(16).toLong() }.toLongArray()

fun String.deserializeUVW(): Array<UVW> =
  if (isEmpty()) emptyArray() else split(";").map { uvwStr ->
    val parts = uvwStr.split("|")
    UVW(
      U = parts[0].deserializeLongArray(),
      V = parts[1].deserializeLongArray(),
      W = parts[2].deserializeLongArray()
    )
  }.toTypedArray()

fun repairWithTCC(brokenStr: List<Σᐩ>, cfg: CFG, bcc: Array<UVW> = cfg.greedyTricliqueCover): GRE? {
  val startT = TimeSource.Monotonic.markNow()
  val nT = cfg.nonterminals.size
  val numWords = (nT + 63) shr 6
  val K = bcc.size

  // Buffer for FSA-based boolean parsing
  fun booleanParse(fsa: FSA): Pair<Int, Array<Array<LongArray>>>? {
    val nS = fsa.numStates
    val mat = Array(nS) { Array(nS) { LongArray(numWords) } }

    // Flattened feature arrays for cache locality: [State * K + CompIdx] -> BitVector
    val featWords = (nS + 63) shr 6
    val featStride = K * featWords
    val rowFeats = LongArray(nS * featStride)
    val colFeats = LongArray(nS * featStride)

    // Inline helper to update features for a specific cell (p, q)
    fun update(p: Int, q: Int, cell: LongArray) {
      for (k in 0 until K) {
        val comp = bcc[k]
        val base = k * featWords
        // If cell overlaps U_k, set bit q in rowFeats[p]
        if (cell.hasInter(comp.U)) {
          val idx = p * featStride + base + q.wordIdx
          rowFeats[idx] = rowFeats[idx] or q.bitMask
        }
        // If cell overlaps V_k, set bit p in colFeats[q]
        if (cell.hasInter(comp.V)) {
          val idx = q * featStride + base + p.wordIdx
          colFeats[idx] = colFeats[idx] or p.bitMask
        }
      }
    }

    // 1. Base Cases (Terminals)
    fsa.allIndexedTxs2(cfg.grpUPs, cfg.bindex).forEach { (p, nt, q) ->
      mat[p][q].setBit(nt)
    }

    // Init features for length-1 spans (transitions p -> p+1)
    // FIX: Iterate only up to nS - 1 to prevent IndexOutOfBounds when accessing p+1
    for (p in 0 until nS - 1) {
      fsa.allPairs[p][p + 1]?.let { update(p, p + 1, mat[p][p + 1]) }
    }

    // 2. Main DP Loop (CYK-style)
    var minRad = Int.MAX_VALUE
    for (dist in 1 until nS) {
      for (p in 0 until nS - dist) {
        val q = p + dist
        if (fsa.allPairs[p][q] == null) continue

        val cell = mat[p][q]
        val rBase = p * featStride
        val cBase = q * featStride

        // Vectorized intersection: Check if rowFeats[p] & colFeats[q] overlap for any Component
        for (k in 0 until K) {
          val offset = k * featWords
          // Check intersection of bitsets for component k
          var overlap = false
          for (w in 0 until featWords) {
            if ((rowFeats[rBase + offset + w] and colFeats[cBase + offset + w]) != 0L) {
              overlap = true; break
            }
          }
          if (overlap) cell orAssign bcc[k].W
        }

        if (dist > 1) update(p, q, cell)

        // Check acceptance if at root
        if (p == 0 && fsa.isFinal[q]) {
          val startIdx = cfg.bindex[START_SYMBOL]
          if ((cell[startIdx.wordIdx] and startIdx.bitMask) != 0L) {
            val (x, y) = fsa.idsToCoords[q]!!
            minRad = minOf(minRad, (brokenStr.size - x + y).absoluteValue)
          }
        }
      }
    }
    return if (minRad == Int.MAX_VALUE) null else minRad to mat
  }

  // 1. Iterative Deepening for LED
  val upperBound = MAX_RADIUS * 3
  val led = (3 until upperBound).firstNotNullOfOrNull {
    booleanParse(makeLevFSA(brokenStr, it))?.first
  } ?: upperBound.also { println("Hit upper bound") }

  val radius = (led + LED_BUFFER).coerceIn(led, MAX_RADIUS)
  latestLangEditDistance = led

  // 2. Re-parse with pruning template to build GRE
  val fsa = makeLevFSA(brokenStr, radius)
  val (_, template) = booleanParse(fsa) ?: return null
  println("Identified LED=$led, radius=$radius in ${startT.elapsedNow()}")

  // GRE DP Table
  val dpGRE = Array(fsa.numStates) { Array(fsa.numStates) { Array<GRE?>(nT) { null } } }

  // Initialize terminals
  fsa.allIndexedTxs1(cfg.grpUPs).forEach { (p, σ, q) ->
    val tIdx = cfg.tmMap[σ]!!
    for (A in cfg.tmToVidx[tIdx])
      dpGRE[p][q][A] = (dpGRE[p][q][A] as? GRE.SET ?: GRE.SET(cfg.tmLst.size)).apply { s.set(tIdx) }
  }

  // Parse Forest Reconstruction
  for (dist in 1 until fsa.numStates) {
    for (p in 0 until fsa.numStates - dist) {
      val q = p + dist
      val validPairs = fsa.allPairs[p][q] ?: continue
      val cellMask = template[p][q]

      // Skip processing if template says this cell is empty
      if (cellMask.all { it == 0L }) continue

      // Iterate only set bits in the mask
      for (w in 0 until numWords) {
        var word = cellMask[w]
        while (word != 0L) {
          val bit = word.countTrailingZeroBits()
          val A = (w shl 6) + bit
          word = word and (word - 1L) // Clear LSB

          // Reconstruct A -> B C
          val rhs = cfg.vindex[A].asSequence()
            .chunked(2) { (B, C) ->
              validPairs.mapNotNull { r ->
                val left = dpGRE[p][r][B]
                val right = dpGRE[r][q][C]
                if (left != null && right != null) left * right else null
              }
            }.flatten().toList()

          if (rhs.isNotEmpty()) {
            dpGRE[p][q][A] = if (rhs.size == 1) rhs[0] else GRE.CUP(*rhs.toTypedArray())
          }
        }
      }
    }
  }

  println("Completed parse matrix in: ${startT.elapsedNow()}")
  val rootIdx = cfg.bindex[START_SYMBOL]
  val allParses = fsa.levFinalIdxs.mapNotNull { q -> dpGRE[0][q][rootIdx] }
  return if (allParses.isEmpty()) null else GRE.CUP(*allParses.toTypedArray())
}