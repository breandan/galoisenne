package ai.hypergraph.kaliningraph.automata

import ai.hypergraph.kaliningraph.graphs.LabeledGraph
import ai.hypergraph.kaliningraph.parsing.*
import ai.hypergraph.kaliningraph.repair.MAX_RADIUS
import ai.hypergraph.kaliningraph.repair.vanillaS2PCFGWE
import ai.hypergraph.kaliningraph.tokenizeByWhitespace
import ai.hypergraph.kaliningraph.types.*
import kotlin.random.Random

typealias Arc = Π3A<Σᐩ>
typealias TSA = Set<Arc>
fun Arc.pretty() = "$π1 -<$π2>-> $π3"
fun Σᐩ.coords(): Pair<Int, Int> =
  (length / 2 - 1).let { substring(2, it + 2).toInt() to substring(it + 3).toInt() }
// Triple representing (1) the global index of the state in the LA and the (2) x, (3) y coordinates
typealias STC = Triple<Int, Int, Int>
fun STC.coords() = π2 to π3

class ACYC_FSA constructor(override val Q: TSA, override val init: Set<Σᐩ>, override val final: Set<Σᐩ>): FSA(Q, init, final) {
  // Since the FSA is acyclic, we can use a more efficient topological ordering
  override val stateLst by lazy {
    graph.topSort().map { it.label }.also {
      if (it.size != states.size)
        throw Exception("Contained ${states.size} but ${it.size} topsorted indices:\n" +
            "T:${Q.joinToString("") { (a, b, c) -> ("($a -[$b]-> $c)") }}\n" +
            "V:${graph.vertices.map { it.label }.sorted().joinToString(",")}\n" +
            "Q:${Q.states().sorted().joinToString(",")}\n" +
            "S:${states.sorted().joinToString(",")}"
        )
    }
  }
}

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
  open val stateLst: List<Σᐩ> by lazy { states.toList() }

  fun allIndexedTxs1(cfg: CFG): List<Π3<Int, Σᐩ, Int>> =
    (cfg.unitProductions * nominalForm.flattenedTriples).filter { (_, σ: Σᐩ, arc) -> (arc.π2)(σ) }
      .map { (A: Σᐩ, σ: Σᐩ, arc) -> Triple(stateMap[arc.π1]!!, σ, stateMap[arc.π3]!!) }

  fun allIndexedTxs0(cfg: CFG): List<Π3A<Int>> =
    (cfg.unitProductions * nominalForm.flattenedTriples).filter { (_, σ: Σᐩ, arc) -> (arc.π2)(σ) }
      .map { (A: Σᐩ, _, arc) -> Triple(stateMap[arc.π1]!!, cfg.bindex[A], stateMap[arc.π3]!!) }

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
  open val allPairs: Map<Pair<Int, Int>, Set<Int>> by lazy {
    graph.allPairs.entries.associate { (a, b) ->
      Pair(Pair(stateMap[a.first.label]!!, stateMap[a.second.label]!!), b.map { stateMap[it.label]!! }.toSet())
    }
  }

  val finalIdxs by lazy { final.map { stateMap[it]!! } }

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
//  by lazy {
//    val t = stateCoords.filter { it.π3 == 0 }.maxOf { it.π2 }
//    val maxY = stateCoords.maxOf { it.π3 }
//    val pad = (t * maxY).toString().length
////    println("Max state: $t")
//    val padY = "0".padStart(pad, '0')
//    (0..<t).map { "q_${it.toString().padStart(pad, '0')}/$padY" to "q_${(it+1).toString().padStart(pad, '0')}/$padY" }
//      .map { (a, b) ->
//        val lbl = edgeLabels[a to b]
////        if (lbl == null) {
////          println("Failed to lookup: $a to $b")
////          println(edgeLabels)
////        }
//        lbl!!
//      }
//  }

  companion object {
    // Decides intersection non-emptiness for Levenshtein ball ∩ CFG
    fun nonemptyLevInt(str: List<Σᐩ>, cfg: CFG, radius: Int, levFSA: FSA = makeLevFSA(str, radius)): Boolean {
      val dp = Array(levFSA.numStates) { Array(levFSA.numStates) { BooleanArray(cfg.nonterminals.size) { false } } }

      levFSA.allIndexedTxs0(cfg).forEach { (q0, nt, q1) -> dp[q0][q1][nt] = true }

//      println("BEFORE (sum=${dp.sumOf { it.sumOf { it.sumOf { if(it) 1.0 else 0.0 } } }})")
//      println(dp.joinToString("\n") { it.joinToString(" ") { if (it.any { it }) "1" else "0" } })
//      println(dp.joinToString("\n") { it.joinToString(" ") { it.joinToString("", "[", "]") { if (it) "1" else "0"} } })

      val startIdx = cfg.bindex[START_SYMBOL]

      // For pairs (p,q) in topological order
      for (dist in 0 until levFSA.numStates) {
        for (iP in 0 until levFSA.numStates - dist) {
          val p = iP
          val q = iP + dist
          // For each A -> B C
          for ((A, B, C) in cfg.tripleIntProds) {
            if (!dp[p][q][A]) {
              for (r in (levFSA.allPairs[p to q] ?: emptySet())) {
                if (dp[p][r][B] && dp[r][q][C]) {
                  if (p == 0 && A == startIdx && q in levFSA.finalIdxs) return true
                  dp[p][q][A] = true
                  break
                }
              }
            }
          }
        }
      }

//      println("AFTER (sum=${dp.sumOf { it.sumOf { it.sumOf { if(it) 1.0 else 0.0 } } }})")
//      println(dp.joinToString("\n") { it.joinToString(" ") { if (it.any { it }) "1" else "0" } })
//      println(dp.joinToString("\n") { it.joinToString(" ") { it.joinToString("", "[", "]") { if (it) "1" else "0"} } })

      return false
    }

    fun LED(
      cfg: CFG,
      brokeToks: List<Σᐩ>,
      upperBound: Int = 2 * MAX_RADIUS,
      monoEditBounds: Pair<Int, Int> = cfg.maxParsableFragmentB(brokeToks, pad = upperBound)
    ): Int =
      (1 until upperBound).firstOrNull {
        FSA.nonemptyLevInt(brokeToks, cfg, it, makeLevFSA(brokeToks, it, monoEditBounds))
      } ?: upperBound

    fun intersectPTree(brokenStr: List<Σᐩ>, cfg: CFG, radius: Int, levFSA: FSA = makeLevFSA(brokenStr, radius)): PTree? {
      val nStates = levFSA.numStates
      val startIdx = cfg.bindex[START_SYMBOL]

      // 1) Create dp array of parse trees
      val dp: Array<Array<Array<PTree?>>> = Array(nStates) { Array(nStates) { Array(cfg.nonterminals.size) { null } } }

      // 2) Initialize terminal productions A -> a
      for ((p, σ, q) in levFSA.allIndexedTxs1(cfg)) {
        val Aidxs = cfg.bimap.TDEPS[σ]!!.map { cfg.bindex[it] }
        for (Aidx in Aidxs) {
          val newLeaf = PTree(root = "[$p~${cfg.bindex[Aidx]}~$q]", branches = PSingleton(σ))
          dp[p][q][Aidx] = newLeaf + dp[p][q][Aidx]
        }
      }

      // 3) CYK + Floyd Warshall parsing
      for (dist in 0 until nStates) {
        for (p in 0 until (nStates - dist)) {
          val q = p + dist
//          val splp = levFSA.SPLP(p, q).let { (it.first - 1)..(it.last + 2) }

          val possibleProds = cfg.tripleIntProds
//              .filter { // Potential optimization:
//                val ntlb = cfg.parikhMap.ntLengthBounds[it.first]
//                println("SPLP (${levFSA.stateLst[p]}, ${levFSA.stateLst[q]}): $splp / $ntlb")
//                splp.overlaps(ntlb)
//              }
          for ((Aidx, /*->*/ Bidx, Cidx) in possibleProds) {
            // Check all possible midpoint states r in the DAG from p to q
            for (r in (levFSA.allPairs[p to q] ?: emptySet())) { // Sparse dot prod
              val left = dp[p][r][Bidx]
              val right = dp[r][q][Cidx]
              if (left != null && right != null) {
                val newTree = PTree("[$p~${cfg.bindex[Aidx]}~$q]", listOf(left to right))
                dp[p][q][Aidx] = newTree + dp[p][q][Aidx]
              }
            }
          }
        }
      }

      // 4) Gather final parse trees from dp[0][f][startIdx], for all final states f
      val allParses = levFSA.finalIdxs.mapNotNull { q -> dp[0][q][startIdx] }

      // 5) Combine them under a single "super‐root"
      return if (allParses.isEmpty()) null
        else PTree(START_SYMBOL, allParses.flatMap { forest -> forest.branches })
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