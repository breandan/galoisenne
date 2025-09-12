package ai.hypergraph.kaliningraph.automata

import ai.hypergraph.kaliningraph.graphs.LabeledGraph
import ai.hypergraph.kaliningraph.parsing.*
import ai.hypergraph.kaliningraph.repair.MAX_RADIUS
import ai.hypergraph.kaliningraph.tokenizeByWhitespace
import ai.hypergraph.kaliningraph.types.*
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.time.TimeSource

typealias Arc = Π3A<Σᐩ>
typealias TSA = Set<Arc>
fun Arc.pretty() = "$π1 -<$π2>-> $π3"
fun Σᐩ.coords(): Pair<Int, Int> = (length / 2 - 1).let { substring(2, it + 2).toInt() to substring(it + 3).toInt() }
// Triple representing (1) the global index of the state in the LA and the (2) x, (3) y coordinates
typealias STC = Triple<Int, Int, Int>
fun STC.coords() = π2 to π3

// TODO: Add support for incrementally growing the FSA by adding new transitions
open class FSA constructor(open val Q: TSA, open val init: Set<Σᐩ>, open val final: Set<Σᐩ>) {
  open val alphabet by lazy { Q.map { it.π2 }.toSet() }
  val isNominalizable by lazy { alphabet.any { it.startsWith("[!=]") } }
  val nominalForm: NOM by lazy { nominalize() } // Converts FSA to nominal form

  val transit: Map<Σᐩ, List<Pair<Σᐩ, Σᐩ>>> by lazy {
    Q.groupBy { it.π1 }.mapValues { (_, v) -> v.map { it.π2 to it.π3 } }
  }

  val revtransit: Map<Σᐩ, List<Pair<Σᐩ, Σᐩ>>> by lazy {
    Q.groupBy { it.π3 }.mapValues { (_, v) -> v.map { it.π2 to it.π1 } }
  }

  val states: Set<Σᐩ> by lazy { Q.states() }
  open val stateLst: List<Σᐩ> by lazy { TODO() } //states.toList() }

  fun allIndexedTxs1(unitProds: Set<Π2A<Σᐩ>>): List<Π3<Int, Σᐩ, Int>> {
    val triples = mutableListOf<Π3<Int, Σᐩ, Int>>()
    for ((A, σ) in unitProds) for (arc in nominalForm.flattenedTriples)
      if (arc.π2(σ)) triples.add(Triple(stateMap[arc.π1]!!, σ, stateMap[arc.π3]!!))
    return triples
  }

  fun allIndexedTxs0(unitProds: Set<Π2A<Σᐩ>>, bindex: Bindex<Σᐩ>): List<Π3A<Int>> {
    val triples = mutableListOf<Π3A<Int>>()
    for ((A, σ) in unitProds) for (arc in nominalForm.flattenedTriples)
        if (arc.π2(σ)) triples.add(Triple(stateMap[arc.π1]!!, bindex[A], stateMap[arc.π3]!!))
    return triples
  }

  val numStates: Int by lazy { states.size }

  val stateMap: Map<Σᐩ, Int> by lazy { stateLst.withIndex().associate { it.value to it.index } }
  // Index of every state pair of states the FSA to the shortest path distance between them
  val APSP: Map<Pair<Int, Int>, Int> by lazy {
    graph.APSP.map { (k, v) ->
//      println("Hashing: ${k.first.label} -> ${k.second.label} == $v")
      Pair(stateMap[k.first.label]!! to stateMap[k.second.label]!!, v)
    }.toMap()
  }

  // TODO: should be a way to compute this on the fly for L-automata (basically a Cartesian grid)
  open val allPairs: List<List<List<Int>?>> by lazy {
    val aps: List<MutableList<MutableList<Int>?>> =
      List(states.size) { MutableList(states.size) { null } }
    graph.allPairs.entries.forEach { (a, b) ->
      val temp = b.map { stateMap[it.label]!! }.toMutableList()
      aps[stateMap[a.first.label]!!][stateMap[a.second.label]!!] = temp
    }
    aps
  }

  open val adjList: IntArray by lazy {
    Q.map { (fromLabel, _, toLabel) -> listOf(stateMap[fromLabel]!!, stateMap[toLabel]!!) }.flatten().toIntArray()
  }

  open val midpoints: List<List<List<Int>>> by lazy { TODO() }

  val finalIdxs by lazy { final.map { stateMap[it]!! }.filter { 0 < idsToCoords[it]!!.second } }

  // TODO: Implement Lev state pairing function to avoid this pain
  val idsToCoords by lazy { stateLst.mapIndexed { i, it -> i to it.coords() }.toMap() }
  val coordsToIds by lazy { stateLst.mapIndexed { i, it -> Pair(it.coords(), i) }.toMap() }
  val stateCoords: Sequence<STC> by lazy { states.map { it.coords().let { (i, j) -> Triple(stateMap[it]!!, i, j) } }.asSequence() }
  var height = 0
  var width = 0

  val validTriples by lazy { stateCoords.let { it * it * it }.filter { it.isValidStateTriple() }.toList() }
  val validPairs by lazy { stateCoords.let { it * it }.filter { it.isValidStatePair() }.toSet() }

  private fun Pair<Int, Int>.dominates(other: Pair<Int, Int>) =
    first <= other.first && second <= other.second &&
        (first < other.first || second < other.second)

  fun Π2A<STC>.isValidStatePair(): Boolean =
    first.coords().dominates(second.coords())

  fun Π3A<STC>.isValidStateTriple(): Boolean =
    first.coords().dominates(second.coords()) &&
    second.coords().dominates(third.coords())

  val edgeLabels: Map<Pair<Σᐩ, Σᐩ>, Σᐩ> by lazy {
    Q.groupBy { (a, b, c) -> a to c }
      .mapValues { (_, v) -> v.map { it.π2 }.toSet().joinToString(",") }
  }

  val map: Map<Π2A<Σᐩ>, Set<Σᐩ>> by lazy {
    Q.groupBy({ (a, b, _) -> a to b }, { (_, _, c) -> c })
      .mapValues { (_, v) -> v.toSet() }
//      .also { it.map { println("${it.key}=${it.value.joinToString(",", "[", "]"){if(it in init) "$it*" else if (it in final) "$it@" else it}}") } }
  }

  fun allOutgoingArcs(from: Σᐩ) = Q.filter { it.π1 == from }

  val graph: LabeledGraph by lazy {
    LabeledGraph {
      Q.forEach { (a, b, c) -> a[b] = c } }.also {
        if (it.size != states.size)
          throw Exception("Contained ${states.size} states but ${it.size} vertices:\n" +
              "T:${Q.joinToString("") { (a, b, c) -> ("($a -[$b]-> $c)") }}\n" +
              "V:${it.vertices.map { it.label }.sorted().joinToString(",")}\n" +
              "Q:${Q.states().sorted().joinToString(",")}\n" +
              "S:${states.sorted().joinToString(",")}"
          )
      }
  }

  val parikhVector: MutableMap<IntRange, ParikhVector> = mutableMapOf()

  fun parikhVector(from: Int, to: Int): ParikhVector =
    parikhVector.getOrPut(from..to) { levString.subList(from, to).parikhVector() }

  var levString: List<Σᐩ> = emptyList()

  companion object {
    // Decides intersection non-emptiness for Levenshtein ball ∩ CFG
    fun nonemptyLevInt(str: List<Σᐩ>, cfg: CFG, radius: Int, levFSA: FSA = makeLevFSA(str, radius)): Boolean {
      val bindex = cfg.bindex
      val width = cfg.nonterminals.size
      val vindex = cfg.vindex
      val ups = cfg.unitProductions
      val aps: List<List<List<Int>?>> = levFSA.allPairs
      val dp = Array(levFSA.numStates) { Array(levFSA.numStates) { BooleanArray(width) { false } } }

      levFSA.allIndexedTxs0(ups, bindex).forEach { (q0, nt, q1) -> dp[q0][q1][nt] = true }

      val startIdx = bindex[START_SYMBOL]

      // For pairs (p,q) in topological order
      for (dist in 1..<levFSA.numStates) {
        for (iP in 0..<levFSA.numStates - dist) {
          val p = iP
          val q = iP + dist
          val appq = aps[p][q] ?: continue
          for ((A, indexArray) in vindex.withIndex()) {
            outerloop@for(j in 0..<indexArray.size step 2) {
              val B = indexArray[j]
              val C = indexArray[j + 1]
              for (r in appq) {
                if (dp[p][r][B] && dp[r][q][C]) {
                  dp[p][q][A] = true
                  break@outerloop
                }
              }
            }

            if (p == 0 && A == startIdx && q in levFSA.finalIdxs && dp[p][q][A]) return true
          }
        }
      }

      return false
    }

    fun LED(
      cfg: CFG,
      brokeToks: List<Σᐩ>,
      upperBound: Int = 2 * MAX_RADIUS,
      monoEditBounds: Pair<Int, Int> = cfg.maxParsableFragmentB(brokeToks, pad = upperBound)
    ): Int =
      (1..<upperBound).firstOrNull {
        FSA.nonemptyLevInt(brokeToks, cfg, it, makeLevFSA(brokeToks, it, monoEditBounds))
      } ?: upperBound


    fun intersectPTree(brokenStr: List<Σᐩ>, cfg: CFG, radius: Int,
                       levFSA: FSA = makeLevFSA(brokenStr, radius)): PTree? {
      val timer = TimeSource.Monotonic.markNow()
      val bindex = cfg.bindex
      val bimap = cfg.bimap
      val width = cfg.nonterminals.size
      val vindex = cfg.vindex
      val ups = cfg.unitProductions

      val nStates = levFSA.numStates
      val startIdx = bindex[START_SYMBOL]

      // 1) Create dp array of parse trees
      val dp: Array<Array<Array<PTree?>>> = Array(nStates) { Array(nStates) { Array(width) { null } } }

      // 2) Initialize terminal productions A -> a
      val aitx = levFSA.allIndexedTxs1(ups)
      for ((p, σ, q) in aitx) {
        val Aidxs = bimap.TDEPS[σ]!!.map { bindex[it] }
        for (Aidx in Aidxs) {
          val newLeaf = PTree(root = "[$p~${bindex[Aidx]}~$q]", branches = PSingleton(σ))
          dp[p][q][Aidx] = newLeaf + dp[p][q][Aidx]
        }
      }

      // 3) CYK + Floyd Warshall parsing
      for (dist in 1..<nStates) {
        for (p in 0..<(nStates - dist)) {
          val q = p + dist
          val appq = levFSA.allPairs[p][q] ?: continue
          for ((Aidx, indexArray) in vindex.withIndex()) {
            val rhsPairs = dp[p][q][Aidx]?.branches?.toMutableList() ?: mutableListOf()
            outerLoop@for (j in 0..<indexArray.size step 2) {
              val Bidx = indexArray[j]
              val Cidx = indexArray[j + 1]
              for (r in appq) {
                val left = dp[p][r][Bidx] ?: continue
                val right = dp[r][q][Cidx] ?: continue
                rhsPairs += left to right
              }
            }

            if (rhsPairs.isNotEmpty()) dp[p][q][Aidx] = PTree("[$p~${bindex[Aidx]}~$q]", rhsPairs)
          }
        }
      }

      println("Completed parse matrix in: ${timer.elapsedNow()}")

      // 4) Gather final parse trees from dp[0][f][startIdx], for all final states f
      val allParses = levFSA.finalIdxs.mapNotNull { q -> dp[0][q][startIdx] }

      return PTree(START_SYMBOL, allParses.flatMap { forest -> forest.branches })
    }
  }

  fun walk(from: Σᐩ, next: (Σᐩ, List<Σᐩ>) -> Int): List<Σᐩ> {
    val startVtx = from
    val path = mutableListOf<Σᐩ>()

    fun Σᐩ.step(og: List<Pair<Σᐩ, Σᐩ>>? = transit[this]) =
      if (this in transit && og != null) next(this, og.map { it.second }).let {
        if (it !in og.indices) null
        else og[it].also { path.add(it.first) }.second
      } else null

    var nextVtx = startVtx.step()

    while (nextVtx != null) nextVtx = nextVtx.step()

    return path
  }

  fun revWalk(from: Σᐩ, next: (Σᐩ, List<Σᐩ>) -> Int): List<Σᐩ> {
    val startVtx = from
    val path = mutableListOf<Σᐩ>()

    fun Σᐩ.step(og: List<Pair<Σᐩ, Σᐩ>>? = revtransit[this]) =
      if (this in revtransit && og != null)
        next(this, og.map { it.second }).let {
          if (it !in og.indices) null
          else og[it].also { path.add(it.first) }.second
        }
      else null

    var nextVtx = startVtx.step()

    while (nextVtx != null) { nextVtx = nextVtx.step() }

    return path
  }

  fun sample() = revWalk(final.random()) { _, lst ->
//    lst.indices.random()
    // Sample indices by exponentially weighted decaying probability
    val weights = lst.indices.map { 1.0 / (it + 10) }
    val sum = weights.sum()
    val r = weights.map { it / sum }
    val c = r.scan(0.0) { acc, it -> acc + it }
    val p = Random.nextDouble()
    c.indexOfFirst { it >= p }.coerceIn(lst.indices)
  }

  // TODO: Sample paths uniformly from **ALL** paths
  fun samplePaths(alphabet: Set<Σᐩ> = setOf("OTHER")) =
    generateSequence { sample().map { if (it in alphabet) it else alphabet.random() }.reversed().joinToString(" ") }

  fun asCFG(alphabet: Set<Σᐩ>) =
    (final.joinToString("\n") { "S -> $it" } + "\n" +
    Q.groupBy({ it.π3 }, { it.π1 to it.π2 })
      .mapValues { (_, v) -> v.map { it.first to it.second } }
      .flatMap { (k, v) ->
        v.map { (a, b) -> "$k -> $a ${if (b in alphabet) b else "OTHER"}" }
      }
      .joinToString("\n")).also { println("CFG size: ${it.lines().size}") }
      .parseCFG().noEpsilonOrNonterminalStubs

  fun debug(str: List<Σᐩ>) =
    (0..str.size).forEachIndexed { i, it ->
      val states = str.subList(0, it).fold(init) { acc, sym ->
        val nextStates = acc.flatMap { map[it to sym] ?: emptySet() }.toSet()
        nextStates
      }
      println("Step ($i): ${states.joinToString(", ")}")
    }.also { println("Allowed final states: ${final.joinToString(", ")}") }

  open fun recognizes(str: List<Σᐩ>) =
    if (isNominalizable) nominalForm.recognizes(str)
    else (str.fold(init) { acc, sym ->
      val nextStates = acc.flatMap { map[it to sym] ?: emptySet() }.toSet()
  //      println("$acc --$sym--> $nextStates")
      nextStates//.also { println("Next states: $it") }
    } intersect final).isNotEmpty()

  open fun recognizes(str: Σᐩ) = recognizes(str.tokenizeByWhitespace())

  fun toDot(): String {
    fun String.htmlify() =
      replace("<", "&lt;").replace(">", "&gt;")
    return """
      strict digraph {
          graph ["concentrate"="false","rankdir"="LR","bgcolor"="transparent","margin"="0.0","compound"="true","nslimit"="20"]
          ${
      states.joinToString("\n") {
        """"${it.htmlify()}" ["color"="black","fontcolor"="black","fontname"="JetBrains Mono","fontsize"="15","penwidth"="2.0","shape"="Mrecord"${if(it in final)""","fillcolor"=lightgray,"style"=filled""" else ""}]""" }
    } 
      ${edgeLabels.entries.joinToString("\n") { (v, e) ->
      val (src, tgt) = v.first to v.second
      """"$src" -> "$tgt" ["arrowhead"="normal","penwidth"="2.0"]""" }
    }
      }
    """.trimIndent()
  }

  fun levToDot(): String {
    fun String.htmlify() =
      replace("<", "&lt;").replace(">", "&gt;")
    return """
      strict digraph {
          nodesep=0.25;
          splines=ortho;
          graph ["concentrate"="false","rankdir"="LR","bgcolor"="transparent","margin"="0.0","compound"="true","nslimit"="20"]
          ${
      states.sortedBy { it.split("/").last() }.reversed().joinToString("\n") {
        """"${it.htmlify()}" [group=${it.split("/").last().toInt()}]["color"="black","fontcolor"="black","fontname"="JetBrains Mono","fontsize"="15","penwidth"="2.0","shape"="rect"${if(it in final)""",peripheries=2""" else ""}, margin="0.8,0.2"]""" }
    } 
      ${edgeLabels.entries.joinToString("\n") { (v, e) ->
      val (src, tgt) = v.first to v.second
      val (s1, s2) = src.drop(2).split("/").map { it.toInt() }
      val (t1, t2) = tgt.drop(2).split("/").map { it.toInt() }
      fun Int.toColor() = when (this) {
        3 -> "blue"
        5 -> "red"
        7 -> "purple"
        9 -> "fuchsia"
        else -> "black"
      }
      val color = ((t2-s2)+(t1-s1)).toColor()
      """"$src" -> "$tgt" ["arrowhead"="normal", minlen="2.5", "penwidth"="2.0", color=$color]""" }
    }
      }
    """.trimIndent()
  }

  fun adjMat(): BooleanArray {
    val n   = numStates
    val out = BooleanArray(n * n)
    val e   = adjList
    for (i in e.indices step 2) {
      val from = e[i]
      val to   = e[i + 1]
      out[from * n + to] = true
    }
    return out
  }

  fun reachMat(): BooleanArray {
    val n        = numStates
    val reach    = BooleanArray(n * n)
    val outgoing = Array(n) { mutableListOf<Int>() }

    val e = adjList
    for (i in e.indices step 2) outgoing[e[i]].add(e[i + 1])

    val q        = ArrayDeque<Int>()
    val visited  = BooleanArray(n)
    repeat(n) { s ->
      visited.fill(false)
      q.clear(); q += s; visited[s] = true
      while (q.isNotEmpty()) {
        val u = q.removeFirst()
        if (u != s) reach[s * n + u] = true
        outgoing[u].forEach { v -> if (!visited[v]) { visited[v] = true; q += v } }
      }
    }
    return reach
  }
}

fun TSA.states() = flatMap { listOf(it.π1, it.π3) }.toSet()

// FSAs looks like this:
/*
INIT -> 1 | 3
DONE -> 4
1 -<a>-> 1
1 -<+>-> 3
3 -<b>-> 4
4 -<+>-> 1
4 -<b>-> 4
 */

fun Σᐩ.parseFSA(): FSA {
  val Q =
    lines().asSequence()
      .filter { it.isNotBlank() }
      .map { it.split("->") }
      .map { (lhs, rhs) ->
        val src = lhs.tokenizeByWhitespace().first()
        val dst = rhs.split('|').map { it.trim() }.toSet()
        val sym = if ("-<" in lhs && lhs.endsWith(">"))
          lhs.split("-<").last().dropLast(1) else ""

        setOf(src) * setOf(sym) * dst
      }.flatten().toList()
      .onEach { println(it) }
  val init = Q.filter { it.π1 == "INIT" }.map { it.π3 }.toSet()
  val final = Q.filter { it.π1 == "DONE" }.map { it.π3 }.toSet()
  return FSA(Q.filter { it.π1 !in setOf("INIT", "DONE") }.toSet(), init, final)
}

const val NEG_LITERAL = 0x40000000u //=1.shl(30)
// Sparse index nonzero entries of the M_0 parse chart
fun FSA.byteFormat(cfg: CFG): IntArray { // TODO: kernelize
  val t0 = TimeSource.Monotonic.markNow()
  val bindex = cfg.bindex
  val terminalLists = cfg.terminalLists

  // 0 and 1 are reserved for (0) no parse exists and (1) parse exists, but an internal nonterminal node
  // Other byte values are used to denote the presence (+) or absence (-) of a leaf terminal
  fun StrPred.predByte(A: Int): Int = (
      if (arg == "[.*]" || (arg.startsWith("[!=]") && arg.drop(4) !in terminalLists[A])) Int.MAX_VALUE - 1 // All possible terminals
      else if (arg.startsWith("[!=]")) (NEG_LITERAL.toInt() + (terminalLists[A].indexOf(arg.drop(4)) + 1).shl(1)) // Represent negation using sign bit
      else (terminalLists[A].indexOf(arg) + 1).shl(1)
      )

  fun buildSparseChart(cfg: CFG, nominalForm: NOM, stateMap: Map<String, Int>, bindex: Bindex<String>): IntArray {
    val rowCount = cfg.unitProductions.sumOf { (_, σ) -> nominalForm.flattenedTriples.count { arc -> arc.second(σ) } }

    val out = IntArray(rowCount * 4)

    var p = 0
    for ((A, σ) in cfg.unitProductions) {
      val Aidx = bindex[A]
      for ((q0, sp, q1) in nominalForm.flattenedTriples) {
        if (!sp(σ)) continue

        out[p++] = stateMap[q0]!!          // q0
        out[p++] = stateMap[q1]!!          // q1
        out[p++] = Aidx                    // non‑terminal
        out[p++] = sp.predByte(Aidx)   // terminal byte
      }
    }
    return out
  }

  val sparseChart = buildSparseChart(cfg, nominalForm, stateMap, bindex)
  println("Byte format took: ${t0.elapsedNow()}")
  return sparseChart
}

fun BooleanArray.toLaTeX(squareUnitSize: String = "0.3cm"): String {
  if (isEmpty()) return """
    \begin{tikzpicture}
      % empty matrix
    \end{tikzpicture}
  """.trimIndent()

  /* Infer N from |matrix| = N² and sanity-check. */
  val n = sqrt(size.toDouble()).roundToInt()
  require(n * n == size) { "BooleanArray size $size is not a perfect square." }

  val body = buildString {
    for (row in 0 until n) {
      for (col in 0 until n) {
        val idx   = row * n + col
        val fill  = if (this@toLaTeX[idx]) "black" else "white"
        val x     = col
        val y     = n - 1 - row
        appendLine("""  \path[fill=$fill] ($x,$y) rectangle ++(1,1);""")
      }
    }
  }

  return """\begin{tikzpicture}[x=$squareUnitSize, y=$squareUnitSize, draw=gray, very thin]
    $body\end{tikzpicture}
  """
}