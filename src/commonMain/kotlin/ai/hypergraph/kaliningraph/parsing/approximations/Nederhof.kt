package ai.hypergraph.kaliningraph.parsing.approximations

import ai.hypergraph.kaliningraph.parsing.*
import kotlin.time.TimeSource


// ------------------------------------------------------------
// Nederhof RTN superset approximation: CFG -> NFA (regular over-approx)
//  - historyDepth = 1  => unparameterized RTN method (Figure 5-style)
//  - historyDepth = d  => parameterized RTN method with |H| < d-1
// ------------------------------------------------------------

private const val EPS_SYM = "ε"

private fun List<Σᐩ>.normalizeEpsilonRhs(): List<Σᐩ> =
  if (size == 1 && this[0] == EPS_SYM) emptyList() else this

private fun CFG.sortedProductions(): List<Production> =
  this.map { (lhs, rhs) -> lhs to rhs.normalizeEpsilonRhs() }
    .sortedWith(compareBy<Production>({ it.LHS }, { it.RHS.joinToString("\u0001") }))

data class ProdRec(val lhs: Σᐩ, val rhs: List<Σᐩ>)

private fun packItem(prodId: Int, dot: Int): Long = (prodId.toLong() shl 32) or (dot.toLong() and 0xffffffffL)

private fun unpackProdId(item: Long): Int = (item ushr 32).toInt()
private fun unpackDot(item: Long): Int = (item and 0xffffffffL).toInt()

/** History = newest-first list of packed items. */
private data class HistKey(val items: LongArray) {
  override fun equals(other: Any?): Boolean =
    other is HistKey && items.contentEquals(other.items)
  override fun hashCode(): Int = items.contentHashCode()

  companion object { val EMPTY = HistKey(LongArray(0)) }

  fun pushed(newItem: Long, maxLen: Int): HistKey {
    if (maxLen <= 0) return EMPTY
    val n = minOf(maxLen, items.size + 1)
    val out = LongArray(n)
    out[0] = newItem
    // keep most-recent items; discard oldest if overflow
    var i = 1
    while (i < n) {
      out[i] = items[i - 1]
      i++
    }
    return HistKey(out)
  }
}

private sealed interface RTNStateKey {
  data class Entry(val nt: Σᐩ, val h: HistKey) : RTNStateKey   // q_{A,H}
  data class Exit(val nt: Σᐩ, val h: HistKey) : RTNStateKey    // q'_{A,H}
  data class Item(val item: Long, val h: HistKey) : RTNStateKey // q_{I,H}
}

private fun MutableMap<Int, MutableList<NFA.Edge>>.addEdge(src: Int, label: Σᐩ?, dst: Int) {
  val lst = getOrPut(src) { mutableListOf() }
  if (lst.none { it.label == label && it.target == dst }) lst.add(NFA.Edge(label, dst))
}

fun CFG.toNederhofNFA(
  startSymbol: Σᐩ = "START",
  historyDepth: Int = 1,
  removeEpsilons: Boolean = false,
  trim: Boolean = true
): NFA {
  val timer = TimeSource.Monotonic.markNow()
  require(historyDepth >= 1) { "historyDepth must be >= 1" }

  // max |H| (screenshots: |H| strictly smaller than d; operationally keep d-1 call-site items)
  val maxHistLen = maxOf(0, historyDepth - 1)

  val prodList = sortedProductions().map { ProdRec(it.LHS, it.RHS) }
  val nonterminals = prodList.map { it.lhs }.toSet()

  // index productions by LHS
  val byLhs: Map<Σᐩ, IntArray> = run {
    val tmp = mutableMapOf<Σᐩ, MutableList<Int>>()
    for (i in prodList.indices) tmp.getOrPut(prodList[i].lhs) { mutableListOf() }.add(i)
    tmp.mapValues { (_, v) -> v.toIntArray() }
  }

  // state-id allocator (reachable-only)
  val idOf = HashMap<RTNStateKey, Int>(1024)
  val queue = ArrayDeque<RTNStateKey>()
  fun ensure(k: RTNStateKey): Int {
    val existing = idOf[k]
    if (existing != null) return existing
    val id = idOf.size
    idOf[k] = id
    queue.addLast(k)
    return id
  }

  val transitions = mutableMapOf<Int, MutableList<NFA.Edge>>()

  val h0 = HistKey.EMPTY
  val startKey = RTNStateKey.Entry(startSymbol, h0)
  val startId = ensure(startKey)
  val finalId = ensure(RTNStateKey.Exit(startSymbol, h0))

  while (queue.isNotEmpty()) {
    val st = queue.removeFirst()
    val src = idOf.getValue(st)

    when (st) {
      is RTNStateKey.Entry -> {
        val prods = byLhs[st.nt] ?: intArrayOf()
        for (pid in prods) {
          // q_{A,H} --ε--> q_{[A -> • rhs],H}
          val item0 = RTNStateKey.Item(packItem(pid, 0), st.h)
          val dst = ensure(item0)
          transitions.addEdge(src, null, dst)
        }
      }

      is RTNStateKey.Item -> {
        val pid = unpackProdId(st.item)
        val dot = unpackDot(st.item)
        val prod = prodList[pid]
        val rhs = prod.rhs

        if (dot >= rhs.size) {
          // completed item: q_{I,H} --ε--> q'_{A,H}
          val dst = ensure(RTNStateKey.Exit(prod.lhs, st.h))
          transitions.addEdge(src, null, dst)
        } else {
          val sym = rhs[dot]
          val nextItem = RTNStateKey.Item(packItem(pid, dot + 1), st.h)
          val nextId = ensure(nextItem)

          if (sym in nonterminals) {
            // nonterminal "call": q_{I,H} --ε--> q_{B,H'}  and  q'_{B,H'} --ε--> q_{I',H}
            val hPrime = st.h.pushed(st.item, maxHistLen)
            val entryB = ensure(RTNStateKey.Entry(sym, hPrime))
            val exitB = ensure(RTNStateKey.Exit(sym, hPrime))

            transitions.addEdge(src, null, entryB)
            transitions.addEdge(exitB, null, nextId)
          } else {
            // terminal consumption: q_{I,H} --a--> q_{I',H}
            transitions.addEdge(src, sym, nextId)
          }
        }
      }

      is RTNStateKey.Exit -> {
        // No intrinsic outgoing edges; callers add (q'_{B,H'} --ε--> q_{I',H})
      }
    }
  }

  val raw = NFA(
    startStates = setOf(startId),
    finalStates = setOf(finalId),
    transitions = transitions.mapValues { it.value }
  )

  val n1 = if (trim) raw.trim() else raw
  val n2 = if (removeEpsilons) n1.removeEpsilons() else n1
  return (if (trim) n2.trim() else n2)
    .also { println("Nederhof approximation constructed in ${timer.elapsedNow()}") }
}

fun CFG.toNederhofNFA(history: Int = 1): NFA =
  toNederhofNFA(startSymbol = "START", historyDepth = history, removeEpsilons = true, trim = true)