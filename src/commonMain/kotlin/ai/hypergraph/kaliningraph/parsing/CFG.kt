@file:Suppress("NonAsciiCharacters")
package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.*
import ai.hypergraph.kaliningraph.graphs.LabeledGraph
import ai.hypergraph.kaliningraph.sampling.choose
import ai.hypergraph.kaliningraph.types.*
import kotlin.jvm.JvmName
import kotlin.random.Random
import kotlin.to

typealias Σᐩ = String
typealias Production = Π2<Σᐩ, List<Σᐩ>>
typealias IProduction = Π2<Int, List<Int>>
// TODO: make this immutable
typealias CFG = Set<Production>

val Production.LHS: Σᐩ get() = first
val Production.RHS: List<Σᐩ> get() = second
// Not sure why this was added, but we don't have time for it in production
//  second.let { if (it.size == 1 && 2 < it.first().length && it.first().first() == '`') it.map(Σᐩ::stripEscapeChars) else it }

/**
 * "Freezes" the enclosed CFG, making it immutable and caching its hashCode for
 * much faster equality checks unlike the default LinkedHashSet implementation,
 * which must recompute hashCode(), incurring O(n) cost in the size of the CFG.
 * This is only necessary because we are using the cache { ... } pattern, which
 * will be slow to compute the first time, but much faster on subsequent calls.
 * Storing the hashCode() in a field avoids recomputing it on every read.
 */
fun CFG.freeze(): CFG = this as? FrozenCFG ?: FrozenCFG(this)
private class FrozenCFG(val cfg: CFG): CFG by cfg {
  val cfgId = cfg.hashCode()
  override fun equals(other: Any?) =
    ((other as? FrozenCFG)?.cfgId == cfgId) || (other as? CFG) == cfg
  override fun hashCode(): Int = cfgId
  val stats = calcStats()
}

val CFG.language: CFL by cache { CFL(this) }
val CFG.delimiters: Array<Σᐩ> by cache { (terminals.sortedBy { -it.length } + arrayOf(HOLE_MARKER, " ")).toTypedArray() }
val CFG.nonterminals: Set<Σᐩ> by cache { /*setOf(START_SYMBOL) + */map { it.LHS }.toSet() }
val CFG.symbols: Set<Σᐩ> by cache { nonterminals + flatMap { it.RHS } }
val CFG.terminals: Set<Σᐩ> by cache { symbols - nonterminals }
val CFG.terminalUnitProductions: Set<Production> by cache { filter { it.RHS.size == 1 && it.RHS[0] !in nonterminals } }
val CFG.unitProductions: Set<Pair<Σᐩ, Σᐩ>> by cache { filter { it.RHS.size == 1 }.map { it.LHS to it.RHS[0] }.toSet() }
val CFG.grpUPs: Map<Σᐩ, List<Σᐩ>> by cache { unitProductions.groupBy({ it.first }, { it.second }) }
val CFG.nonterminalProductions: Set<Production> by cache { filter { it !in terminalUnitProductions } }
val CFG.unitNonterminals: Set<Σᐩ> by cache { terminalUnitProductions.map { it.LHS }.toSet() }
val CFG.bimap: BiMap by cache { BiMap(this) }
// Maps nonterminal sets to their terminals, n.b., each terminal can be generated
// by multiple nonterminals, and each nonterminal can generate multiple terminals
val CFG.tmap: Map<Set<Σᐩ>, Set<Σᐩ>> by cache {
  terminals.map { bimap[listOf(it)] to it }.groupBy { it.first }
    .mapValues { it.value.map { it.second }.toSet() }
}

val CFG.unicodeMap by cache { terminals.associateBy { Random(it.hashCode()).nextInt().toChar().toUnicodeEscaped() } }

val CFG.symLst by cache { (symbols + "ε").toList() }
val CFG.symMap by cache { symLst.mapIndexed { i, s -> s to i }.toMap() }

val CFG.tmLst: List<Σᐩ> by cache { terminals.toList() }
val CFG.tmDict: TermDict by cache { TermDict(terminals) }
val CFG.tmMap: Map<Σᐩ, Int> by cache { tmLst.mapIndexed { i, s -> s to i }.toMap() }
val CFG.tmToVidx: List<List<Int>> by cache { List(tmLst.size) { bimap.TDEPS[tmLst[it]]!!.map { bindex[it] } } }
val CFG.terminalLists: List<Set<Σᐩ>> by cache { nonterminals.map { bimap.UNITS[it] ?: emptySet() } }

val CFG.tripleIntProds: Set<Π3A<Int>> by cache { bimap.TRIPL.map { (a, b, c) -> Triple(bindex[a], bindex[b], bindex[c]) }.toSet() }
val CFG.revUnitProds: Map<Σᐩ, List<Int>> by cache { terminals.associate { it to bimap[listOf(it)].map { bindex[it] } } }

// Maps each nonterminal to the set of nonterminal pairs that can generate it,
// which is then flattened to a list of adjacent pairs of nonterminal indices
val CFG.vindex: Array<IntArray> by cache {
  Array(bindex.indexedNTs.size) { i ->
//    val lhs = bindex[i]
    bimap[bindex[i]].filter { it.size == 2 }
//      .map { it to -(PCFG3_BIFI[lhs to it[0] to it[1]] ?: 0).also { s -> println("$lhs -> ${it[0]} ${it[1]} ($s)" )} }
//      .sortedBy { it.second }.map { it.first }
      .map { it.map { bindex[it] } }.flatten()
      .toIntArray()
  }
}

val CFG.vindex2: Array<List<List<Int>>> by cache {
  Array(bindex.indexedNTs.size) { i ->
    bimap[bindex[i]].filter { it.size > 1 }
      .map { listOf(bindex[it[0]], bindex[it[1]]) }
  }
}

data class PackedAdj(val other: IntArray, val aIdx: IntArray) {
  inline fun forEachIfIn(bitset: KBitSet, f: (c: Int, a: Int) -> Unit) {
    val os = other; val asz = aIdx
    var i = 0
    while (i < os.size) {
      val c = os[i]
      if (bitset[c]) f(c, asz[i])
      i++
    }
  }
}

val CFG.leftAdj by cache {
  val W = nonterminals.size
  val bags = Array(W) { mutableListOf<Pair<Int, Int>>() } // per B: (C,A)

  vindex.forEachIndexed { A, indexArray ->
    var j = 0
    while (j < indexArray.size) {
      val B = indexArray[j]
      val C = indexArray.getOrElse(j + 1) { -1 }
      if (C >= 0) bags[B].add(C to A)
      j += 2
    }
  }

  val out = arrayOfNulls<PackedAdj>(W)
  var b = 0
  while (b < W) {
    val lst = bags[b]
    if (lst.isNotEmpty()) {
      val os = IntArray(lst.size)
      val asz = IntArray(lst.size)
      var i = 0
      while (i < lst.size) {
        val (c, a) = lst[i]
        os[i] = c; asz[i] = a; i++
      }
      out[b] = PackedAdj(os, asz)
    }
    b++
  }

  out
}

val CFG.bindex: Bindex<Σᐩ> by cache { Bindex(nonterminals) }
val CFG.normalForm: CFG by cache { normalize() }
val CFG.depGraph: LabeledGraph by cache { dependencyGraph() }
val CFG.revDepGraph: LabeledGraph by cache { revDependencyGraph() }

// Terminals which are blocked from being synthesized by a solver
val CFG.blocked: MutableSet<Σᐩ> by cache { mutableSetOf() }

val CFG.originalForm: CFG by cache { rewriteHistory[this]?.get(0) ?: this }
val CFG.nonparametricForm: CFG by cache { rewriteHistory[this]!![1] }
//val CFG.originalForm by cache { rewriteHistory[this]!![0] }
//val CFG.nonparametricForm by cache { rewriteHistory[this]!![1] }

/** Backing fields for [reachableSymbols], [reachableSymbolsViaUnitProds]
 *  TODO: back the fields with functions instead of vis versa using mutable maps?
 *        - Pros: early accesses are faster with a gradually-filled map
 *        - Cons: immutable fields follow convention, easier to reason about
 */
val CFG.reachability by cache { mutableMapOf<Σᐩ, Set<Σᐩ>>() }

fun CFG.calcStats() = "CFG(|Σ|=${terminals.size}, |V|=${nonterminals.size}, |P|=${nonterminalProductions.size})"
fun CFG.stats() = if (this is FrozenCFG) stats else calcStats()

// Equivalence class of an NT B are all NTs, A ->* B ->* C
// reachable via unit productions (in forward or reverse)
//val CFG.unitReachability: Map<Σᐩ, Set<Σᐩ>> by cache {
//  symbols.associateWith { from ->
//    LabeledGraph {
//      unitProductions.map { it.first to it.second }
////      .filter { (a, b) -> nonterminals.containsAll(listOf(a, b)) }
//        .forEach { (a, b) -> a - b }
//    }.let {
//      setOf(from) + (it.transitiveClosure(setOf(from)) +
//        it.reversed().transitiveClosure(setOf(from)))
//    }.filter { it in nonterminals }
//  }
//}

val CFG.unitReachability: Map<Σᐩ, Set<Σᐩ>> by cache { unitReachabilityFast() }

private fun CFG.unitReachabilityFast(): Map<Σᐩ, Set<Σᐩ>> {
  class IVec(cap: Int = 8) {
    private var a = IntArray(cap)
    var size = 0; private set
    fun add(x: Int) { if (size == a.size) a = a.copyOf(a.size * 2); a[size++] = x }
    operator fun get(i: Int) = a[i]
  }
  fun csr(n: Int, deg: IntArray, u: IVec, v: IVec): Pair<IntArray, IntArray> {
    val off = IntArray(n + 1)
    var s = 0
    for (i in 0 until n) { off[i] = s; s += deg[i] }
    off[n] = s
    val to = IntArray(s)
    val cur = off.copyOf()
    for (i in 0 until u.size) {
      val uu = u[i]
      val p = cur[uu]
      cur[uu] = p + 1
      to[p] = v[i]
    }
    return off to to
  }
  fun ctz(x: Long): Int {
    var n = 0
    var y = x
    while ((y and 1L) == 0L) { y = y ushr 1; n++ }
    return n
  }

  val nts = nonterminals.toList()
  val W = nts.size
  if (W == 0) return symbols.associateWith { emptySet() }

  val id = HashMap<Σᐩ, Int>(W * 2).apply { nts.forEachIndexed { i, s -> put(s, i) } }

  val outDeg = IntArray(W)
  val inDeg = IntArray(W)
  val eu = IVec(); val ev = IVec()
  val termGen = HashMap<Σᐩ, IVec>() // symbol -> A indices where A -> symbol

  for ((a, b) in unitProductions) {
    val ai = id[a] ?: continue
    val bi = id[b]
    if (bi != null) {
      eu.add(ai); ev.add(bi); outDeg[ai] = outDeg[ai] + 1; inDeg[bi] = inDeg[bi] + 1
    } else termGen.getOrPut(b) { IVec() }.add(ai)
  }

  val (outOff, outTo) = csr(W, outDeg, eu, ev)
  val (inOff, inTo)   = csr(W, inDeg, ev, eu) // reverse

  // ---- Kosaraju SCC (iterative) ----
  val order = IntArray(W)
  run {
    val seen = BooleanArray(W)
    val st = IntArray(W * 2)
    val it = IntArray(W * 2)
    var os = 0
    for (s in 0 until W) if (!seen[s]) {
      var sp = 0
      st[sp] = s; it[sp] = outOff[s]; sp++; seen[s] = true
      while (sp > 0) {
        val v = st[sp - 1]
        val p = it[sp - 1]
        val end = outOff[v + 1]
        if (p < end) {
          val w = outTo[p]
          it[sp - 1] = p + 1
          if (!seen[w]) { st[sp] = w; it[sp] = outOff[w]; sp++; seen[w] = true }
        } else { sp--; order[os++] = v }
      }
    }
  }

  val comp = IntArray(W) { -1 }
  val members = ArrayList<IVec>()
  var C = 0
  run {
    val st = IntArray(W)
    for (k in W - 1 downTo 0) {
      val s = order[k]
      if (comp[s] != -1) continue
      val c = C++
      val mem = IVec(); members.add(mem)
      var sp = 0
      st[sp++] = s; comp[s] = c
      while (sp > 0) {
        val v = st[--sp]
        mem.add(v)
        for (i in inOff[v] until inOff[v + 1]) {
          val w = inTo[i]
          if (comp[w] == -1) { comp[w] = c; st[sp++] = w }
        }
      }
    }
  }

  // ---- Condensation DAG ----
  val compOut = Array(C) { IVec() }
  val indegC = IntArray(C)
  val seenT = IntArray(C); var stamp = 1
  for (c in 0 until C) {
    stamp++
    val mem = members[c]
    for (mi in 0 until mem.size) {
      val u = mem[mi]
      for (ei in outOff[u] until outOff[u + 1]) {
        val d = comp[outTo[ei]]
        if (d == c) continue
        if (seenT[d] != stamp) {
          seenT[d] = stamp
          compOut[c].add(d)
          indegC[d] = indegC[d] + 1
        }
      }
    }
  }

  // topo order
  val topo = IntArray(C)
  run {
    val q = IntArray(C)
    val indeg = indegC.copyOf()
    var h = 0; var t = 0; var z = 0
    for (c in 0 until C) if (indeg[c] == 0) q[t++] = c
    while (h < t) {
      val c = q[h++]
      topo[z++] = c
      val outs = compOut[c]
      for (i in 0 until outs.size) {
        val d = outs[i]
        val nd = indeg[d] - 1
        indeg[d] = nd
        if (nd == 0) q[t++] = d
      }
    }
  }

  // ---- Bitset DP: anc/desc over components ----
  val words = (W + 63) ushr 6
  fun bits() = LongArray(words)
  fun setBit(b: LongArray, i: Int) { val w = i ushr 6; b[w] = b[w] or (1L shl (i and 63)) }
  fun orInto(a: LongArray, b: LongArray) { for (i in a.indices) a[i] = a[i] or b[i] }

  val anc = Array(C) { bits() }
  val desc = Array(C) { bits() }

  for (c in 0 until C) {
    val mem = members[c]
    for (i in 0 until mem.size) {
      val v = mem[i]
      setBit(anc[c], v); setBit(desc[c], v)
    }
  }

  for (ti in 0 until C) { // ancestors
    val c = topo[ti]
    val outs = compOut[c]
    for (i in 0 until outs.size) orInto(anc[outs[i]], anc[c])
  }
  for (ti in C - 1 downTo 0) { // descendants
    val c = topo[ti]
    val outs = compOut[c]
    for (i in 0 until outs.size) orInto(desc[c], desc[outs[i]])
  }

  fun toSet(b: LongArray): Set<Σᐩ> {
    val out = LinkedHashSet<Σᐩ>()
    for (wi in b.indices) {
      var w = b[wi]
      while (w != 0L) {
        val lsb = w and -w
        val bit = ctz(lsb)
        out.add(nts[(wi shl 6) + bit])
        w = w xor lsb
      }
    }
    return out
  }

  val rel = Array(C) { c ->
    val b = bits(); orInto(b, anc[c]); orInto(b, desc[c]); toSet(b)
  }

  val res = HashMap<Σᐩ, Set<Σᐩ>>(symbols.size * 2)
  for (i in 0 until W) res[nts[i]] = rel[comp[i]]

  for (s in symbols) if (s !in nonterminals) {
    val gens = termGen[s]
    if (gens == null || gens.size == 0) res[s] = emptySet()
    else {
      val b = bits()
      for (i in 0 until gens.size) orInto(b, anc[comp[gens[i]]])
      res[s] = toSet(b)
    }
  }

  return res
}

val CFG.noNonterminalStubs: CFG by cache {
//  try { throw Exception() } catch (e: Exception) { e.printStackTrace() }
  println("Disabling nonterminal stubs!")
  filter { it.RHS.none { it.isNonterminalStubIn(this) } }.toSet().freeze()
    .also { rewriteHistory.put(it, freeze().let { rewriteHistory[it]!! + listOf(it)}) }
    .also { it.blocked.addAll(blocked) }
}

val CFG.noEpsilon: CFG by cache {
//  try { throw Exception() } catch (e: Exception) { e.printStackTrace() }
  println("Disabling ε!")
  filter { "ε" !in it.toString() }.toSet().freeze()
    .also { rewriteHistory.put(it, freeze().let { rewriteHistory[it]!! + listOf(it)}) }
    .also { it.blocked.addAll(blocked) }
}

val CFG.noEpsilonOrNonterminalStubs: CFG by cache {
//  try { throw Exception() } catch (e: Exception) { e.printStackTrace() }
  println("Disabling nonterminal stubs!")
  filter { it.RHS.none { it.isNonterminalStubIn(this) } }
    .filter { "ε" !in it.toString() }.toSet().freeze()
    .also { rewriteHistory.put(it, freeze().let { rewriteHistory[it]!! + listOf(it)}) }
    .also { it.blocked.addAll(blocked) }
}

val CFG.parikhFPCache: Map<Σᐩ, BitvecPosetInterval> by cache { TODO() }

// Maps each symbol to the set of nonterminals that can generate it
val CFG.generators: Map<Σᐩ, Set<Σᐩ>> by cache {
  map { prod -> prod.RHS.map { it to prod.LHS } }.flatten()
    .groupBy { it.first }.mapValues { it.value.map { it.second }.toSet() }
}

val CFG.nonterminalFormulas: Map<Σᐩ, Σᐩ> by cache {
  nonterminals.associateWith { nt -> toFormula(nt) }
}

/**
 * Maps each nonterminal to terminals that can be reached from it. At least one of
 * each of these terminals must be present in the input string for the nonterminal
 * to be matched. If a string does not contain any of these terminals, we know the
 * nonterminal is not contained in the parse tree, and can prune it from the CFG.
 *
 *       Γ |- A -> a
 *       -----------------------
 *       Γ |- φ[A] = a
 *
 *       Γ |- A -> B C
 *       -----------------------
 *       Γ |- φ[A] = φ[B] ʌ φ[C]
 *
 *       Γ |- A -> B | C
 *       -----------------------
 *       Γ |- φ[A] = φ[B] v φ[C]
 */

fun CFG.toFormula(nt: Σᐩ): Σᐩ =
  when (nt) {
    in terminals -> nt
    !in nonterminals -> "false"
    else -> bimap[nt].joinToString(" or ", "( ", " )") {
      it.joinToString(" and ", "( ", " )") { toFormula(it) }
    }
  } // TODO: fix stack blowup when there is a cycle in the CFG


// Prunes all nonterminals that represent a finite set of terminals down to the root
// Usually this is a tree-like structure, but it can also be a DAG of nonterminals
val CFG.pruneTreelikeNonterminals: CFG by cache {
  println("Pruning treelike nonterminals!")
  filter { it.RHS.any { !it.isTreelikeNonterminalIn(this) } || "ε" in it.LHS }.toSet()
    .let { cfg ->
      val brokenReferences = cfg.terminals
      cfg +
        // Restore preexisting nonterminal stubs for all remaining treelike nonterminals
        brokenReferences.filter { "<$it>" in terminals }.map { it to listOf("<$it>") } +
        cfg.nonterminals.filter { it.isOrganicNonterminal() }.map { it to listOf("<$it>") } +
        // Restore old nonterminal stubs for unreferenced unit productions
        brokenReferences.filter { it.isSyntheticNonterminal() && it in nonterminals }
          .map { l -> filter { it.LHS == l }.map { l to it.RHS } }
          .flatten()
//          .first()
          .toSet().also { println("Restored productions: ${it.prettyPrint()}") }
    }
    .let { it.transformIntoCNF() }
    .also { rewriteHistory.put(it, freeze().let { listOf(rewriteHistory[it]!![0]) + listOf(it)}) }
    .also { it.blocked.addAll(blocked) }
}

// Returns true iff the receiver is a nonterminal whose descendants
// are themselves either (1) treelike nonterminals or (2) terminals
private fun Σᐩ.isTreelikeNonterminalIn(
  cfg: CFG,
  reachables: Set<Σᐩ> = cfg.reachableSymbols(this) - this,
  nonTreeLike: Set<Σᐩ> = setOf(this)
): Bln = when {
  "ε" in this -> true
  (reachables intersect nonTreeLike).isNotEmpty() -> false
  else -> reachables.all { it in cfg.terminals ||
      it.isTreelikeNonterminalIn(cfg, nonTreeLike = nonTreeLike + reachables) }
}

val CFG.joinMap: JoinMap by cache { JoinMap(this) }
class JoinMap(val CFG: CFG) {
  // TODO: Doesn't appear to confer any significant speedup? :/
  val precomputedJoins: MutableMap<Π2A<Set<Σᐩ>>, Set<Π3A<Σᐩ>>> =
    CFG.nonterminals.choose(1..3).let { it * it }
      .associateWith { subsets -> subsets.let { (l, r) -> join(l, r) } }
      .also { println("Precomputed join map has ${it.size} entries.") }.toMutableMap()

  fun join(l: Set<Σᐩ>, r: Set<Σᐩ>, tryCache: Bln = false): Set<Π3A<Σᐩ>> =
    if (tryCache) precomputedJoins[l to r] ?: join(l, r, false).also { precomputedJoins[l to r] = it }
    else (l * r).flatMap { (l, r) -> CFG.bimap[listOf(l, r)].map { Triple(it, l, r) } }.toSet()

  @JvmName("setJoin")
  operator fun get(l: Set<Σᐩ>, r: Set<Σᐩ>): Set<Σᐩ> =
    join(l, r, false).map { it.first }.toSet()

  @JvmName("treeJoin")
  operator fun get(left: Forest, right: Forest): Forest =
    join(left.map { it.root }.toSet(), right.map { it.root }.toSet(), false)
      .map { (rt, l, r) ->
        Tree(rt, null, left.first { it.root == l }, right.first { it.root == r })
      }.toSet()
}

// Maps indices to nonterminals and nonterminals to indices
class Bindex<T>(
  val set: Set<T>,
  val indexedNTs: List<T> = set.toList(),
  val ntIndices: Map<T, Int> = indexedNTs.zip(indexedNTs.indices).toMap()
): List<T> by indexedNTs {
  constructor(map: Map<Int, T>) : this(map.values.toSet(), map.values.toList(), map.entries.associate { it.value to it.key })
  operator fun get(s: T): Int = ntIndices[s] ?: 1.also {
    println("Unknown nonterminal: $s");
    try {
      throw IllegalArgumentException("Unknown nonterminal: $s")
    } catch (e: IllegalArgumentException) {e.printStackTrace()}
    null!! }
  fun getUnsafe(s: T): Int? = ntIndices[s]
  override fun toString(): String = indexedNTs.mapIndexed { i, it -> "$i: $it" }.joinToString("\n", "Bindex:\n", "\n")
}
// Maps variables to expansions and expansions to variables in a grammar
class BiMap(val cfg: CFG) {
  val L2RHS by lazy { cfg.groupBy({ it.LHS }, { it.RHS }).mapValues { it.value.toSet() } }
  val R2LHS by lazy { cfg.groupBy({ it.RHS }, { it.LHS }).mapValues { it.value.toSet() } }
  val R2LHSV by lazy { cfg.filter { it.RHS.all { it in cfg.nonterminals } }.groupBy({ it.RHS }, { it.LHS }).mapValues { it.value.toSet() } }
  val R2LHSI by lazy {
    val mmap = List(cfg.nonterminals.size) { List(cfg.nonterminals.size) { mutableListOf<Int>() } }
    R2LHSV.forEach {
      val rhs = it.key.map { cfg.bindex[it] }
      mmap[rhs[0]][rhs[1]] += it.value.map { cfg.bindex[it] }
    }
//    R2LHSV.map { it.key.map { cfg.bindex[it] } to it.value.map { cfg.bindex[it] } }.toMap()
    mmap
  }

  val TDEPS: Map<Σᐩ, MutableSet<Σᐩ>> by lazy { // Maps all symbols to NTs that can generate them
    mutableMapOf<Σᐩ, MutableSet<Σᐩ>>().apply {
      for ((l, r) in cfg) for (symbol in r)
          getOrPut(symbol) { mutableSetOf() }.add(l)
    }
  }
  val NDEPS: Map<Σᐩ, MutableSet<Σᐩ>> by lazy { // Maps all NTs to the symbols they can generate
    mutableMapOf<Σᐩ, MutableSet<Σᐩ>>().apply {
      for ((l, r) in cfg) for (symbol in r)
          getOrPut(l) { mutableSetOf() }.add(symbol)
    }
  }
  val TRIPL: List<Π3A<Σᐩ>> by lazy {
    R2LHS.filter { it.key.size == 2 }
      .map { it.value.map { v -> Triple(v, it.key[0], it.key[1]) } }.flatten()
  }
  val X2WZ: Map<Σᐩ, List<Π3A<Σᐩ>>> by lazy {
    TRIPL.groupBy { it.second }.mapValues { it.value }
  }
  val UNITS by lazy {
    cfg.filter { it.RHS.size == 1 && it.RHS[0] !in cfg.nonterminals }
      .groupBy({ it.LHS }, { it.RHS[0] }).mapValues { it.value.toSet() }
  }
  operator fun get(p: List<Σᐩ>): Set<Σᐩ> = R2LHS[p] ?: emptySet()
  operator fun get(p: Σᐩ): Set<List<Σᐩ>> = L2RHS[p] ?: emptySet()
  operator fun get(p: Set<Σᐩ>): Set<Σᐩ> = TDEPS.entries.filter { it.value == p }.map { it.key }.toSet()
}

// n.b., this only works if the CFG is acyclic, i.e., L(G) is finite otherwise it will loop forever
fun CFG.toPTree(from: Σᐩ = START_SYMBOL, origCFG: CFG = this): PTree =
  PTree(from, bimap[from].map { toPTree(it[0], origCFG) to if (it.size == 1) PTree() else toPTree(it[1], origCFG) })
    .also { it.ntIdx = (origCFG.symMap[(if('~' in from) from.split('~')[1] else from)] ?: Int.MAX_VALUE) }

/*
Γ ⊢ ∀ v.[α→*]∈G ⇒ α→[β]       "If all productions rooted at α
----------------------- □β     yield β, then α necessarily yields β"
Γ ⊢ □ α→[β]

Γ ⊢ □ ω→[α] □ α→[β]
----------------------- trans
Γ ⊢ □ ω → [α]∪[β]

Γ ⊢ s∈Σ\Σ'  v'∈V.□v'→[s]      "Any production containing a nonterminal that
----------------------- elim   necessarily generates a terminal that is not
Γ ⊢ ∀ρ,v∈ρ  G' ← G'\ρ          in the subgrammar can be safely removed."
*/

val CFG.mustGenerate by cache { inevitableSymbols() }

fun CFG.inevitableSymbols(map: Map<Σᐩ, Set<Σᐩ>> = emptyMap()): Map<Σᐩ, Set<Σᐩ>> {
  val newMap = map.toMutableMap()
  symbols.forEach { smb ->
//    println("Testing $smb")
    bimap.TDEPS[smb]?.forEach { nt ->
//      println("Testing $smb -> $nt")
      if (bimap[nt].all { smb in it || nt in it }) {
//        println("Worked! $nt => $smb")
        newMap[nt] = newMap.getOrPut(nt) { setOf(nt) } +
            newMap.getOrPut(smb) { setOf(smb) }
      }
//      else {
//        if (smb == "NEWLINE")
//        println("Failed! $nt !=> $smb, first ${bimap[nt].first { smb !in it }}")
//      }
    }
    newMap[smb] = newMap.getOrPut(smb) { setOf(smb) }
  }
  return if (newMap == map) map else inevitableSymbols(newMap)
}

fun Bln.explain(cfg: CFG, prod: Production, reason: String = "") = this.also{
  if(it) {
    println("Removed [${prod.LHS} -> ${prod.RHS.joinToString(" ")}] because $reason")
    if (cfg.count { it.first == prod.LHS } == 1) println("And no other productions were left for `${prod.LHS}`!")
  }
}

fun CFG.removeTerminalsVerbose(allowed: Set<Σᐩ>, otps: Set<Production> = this.terminalUnitProductions, origTerms: Set<Σᐩ> = this.terminals, mustGenerate: Map<Σᐩ, Set<Σᐩ>> = this.mustGenerate): CFG {
  val deadNTs = mutableSetOf<Σᐩ>()
  val next = toMutableSet().apply { removeAll { prod ->
    (
//        (prod in otps && (prod.RHS.first() !in allowed))
//          .explain(this, prod, "the terminal `${prod.RHS.first()}` is not allowed") ||
        (mustGenerate[prod.LHS]?.any { (it in origTerms && it !in allowed)
          .explain(this, prod, "LHS value `${prod.LHS}` must generate `$it` and `$it` was not allowed") } == true) ||
        prod.RHS.any { rhs -> mustGenerate[rhs]?.any { (it in origTerms && it !in allowed)
          .explain(this, prod, "RHS value `$rhs` must generate `$it` and `$it` was not allowed") } == true }
    ).also { if (it && this.count { it.first == prod.first } == 1) {
        println("Added `${prod.first}` to deadNTs!")
        deadNTs.add(prod.LHS) }
      }
  } }

  next.removeAll { prod ->
    prod.RHS.any { rhs ->
      (rhs in deadNTs).explain(next, prod, "the RHS value `$rhs` is a dead NT!") ||
        (rhs !in origTerms).explain(next, prod, "the RHS terminal `$rhs` was a chopped NT")
    }
  }

  return if (next.size == size) this else next.removeTerminalsVerbose(allowed, otps, origTerms, mustGenerate)
}

fun CFG.removeTerminals(
  allowed: Set<Σᐩ>,
  deadNTs: Set<Σᐩ> = emptySet(),
  origTerms: Set<Σᐩ> = this.terminals,
  mustGenerate: Map<Σᐩ, Set<Σᐩ>> = this.mustGenerate
): CFG {
  val deadNTs = deadNTs.toMutableSet()
  val next = toMutableSet().apply {
    removeAll { prod ->
      (prod.RHS + prod.LHS).toSet().any { mustGenerate[it]?.any { it in origTerms && it !in allowed || it in deadNTs } == true }
        .also { if (it && count { it.first == prod.first } == 1) deadNTs.add(prod.LHS) }
    }
  }

  next.removeAll { prod -> prod.RHS.any { rhs -> rhs in deadNTs || (rhs in next.terminals && rhs !in origTerms) } }

  val new = next.removeUselessSymbols()

  return if (new.size == size) this else new.removeTerminals(allowed, deadNTs, origTerms, mustGenerate)
}

/*
 Specializes the CFG to a set of terminals X, by recursively pruning
 every nonterminal v which necessarily generates a terminal t' ∉ X and
 every nonterminal that necessarily generates v. We call the set of all
 productions that remain after pruning, the preimage of G under T or the "subgrammar".
 */
fun CFG.subgrammar(image: Set<Σᐩ>): CFG =
  removeTerminals(image)
    .also { rewriteHistory.put(it, freeze().let { rewriteHistory[it]!! + listOf(it)}) }
    .freeze()

fun CFG.directSubgrammar(toRemove: Set<Σᐩ>): CFG =
  filter { (it.RHS + it.LHS).all { it !in toRemove } }
    .normalize().noEpsilonOrNonterminalStubs.freeze()
    .also { println("Reduced CFG from $size to ${it.size} rules") }

fun CFG.forestHash(s: Σᐩ) = parseForest(s).map { it.structureEncode() }.hashCode()
fun CFG.nonterminalHash(s: Σᐩ) = s.tokenizeByWhitespace().map { preimage(it) }.hashCode()
fun CFG.preimage(vararg nts: Σᐩ): Set<Σᐩ> = bimap.R2LHS[nts.toList()] ?: emptySet()

fun CFG.dependencyGraph() =
  LabeledGraph { forEach { prod -> prod.second.forEach { rhs -> prod.LHS - rhs } } }

fun CFG.revDependencyGraph() =
  LabeledGraph { forEach { prod -> prod.second.forEach { rhs -> rhs - prod.LHS } } }

fun CFG.jsonify() = "cfg = {\n" +
  bimap.L2RHS.entries.joinToString("\n") {
    ("\"${it.key}\": [${it.value.joinToString(", ") {
      it.joinToString(", ", "(", ")") { "\"$it\"" }
    }}],")
  } + "\n}"

class TermDict(
  val terms: Set<Σᐩ>,
  val dict: Map<Char, Σᐩ> = terms.associateBy { Random(it.hashCode()).nextInt().toChar() },
  val revDict: Map<Σᐩ, Char> = dict.entries.associate { (k, v) -> v to k }
) : Map<Char, Σᐩ> by dict {
  fun encode(str: String) = str.tokenizeByWhitespace().map { revDict[it]!! }.joinToString("")
  fun encode(str: List<String>) = str.map { revDict[it]!! }.joinToString("")
}

data class GrammarEncoding(val flat: IntArray, val offsets: IntArray)

val CFG.grammarEncoding: GrammarEncoding by cache {
  val W = nonterminals.size
  val ntIdx = bindex.ntIndices   // Map<Σᐩ, Int>

  val counts = IntArray(W)
  for ((lhs, rhs) in this) {
    if (rhs.size != 2) continue
    val a = ntIdx[lhs] ?: continue
    val b = ntIdx[rhs[0]] ?: continue
    val c = ntIdx[rhs[1]] ?: continue
    counts[a] += 2
  }

  val offsets = IntArray(W + 1)
  var acc = 0
  for (i in 0 until W) { offsets[i] = acc; acc += counts[i] }
  offsets[W] = acc

  val flat = IntArray(acc)
  val cur = offsets.copyOf()
  for ((lhs, rhs) in this) {
    if (rhs.size != 2) continue
    val a = ntIdx[lhs] ?: continue
    val b = ntIdx[rhs[0]] ?: continue
    val c = ntIdx[rhs[1]] ?: continue
    val p = cur[a]
    flat[p] = b
    flat[p + 1] = c
    cur[a] = p + 2
  }

  GrammarEncoding(flat, offsets)
}