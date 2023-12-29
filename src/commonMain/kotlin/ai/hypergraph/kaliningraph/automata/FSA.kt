package ai.hypergraph.kaliningraph.automata

import ai.hypergraph.kaliningraph.graphs.*
import ai.hypergraph.kaliningraph.parsing.Σᐩ
import ai.hypergraph.kaliningraph.tokenizeByWhitespace
import ai.hypergraph.kaliningraph.types.*

typealias Arc = Π3A<Σᐩ>
typealias TSA = Set<Arc>

data class FSA(val Q: TSA, val init: Set<Σᐩ>, val final: Set<Σᐩ>) {
  val alphabet by lazy { Q.map { it.π2 }.toSet() }
  val states by lazy { Q.states }
  val APSP: Map<Pair<Σᐩ, Σᐩ>, Int> by lazy {
    graph.APSP.map { (k, v) ->
      Pair(Pair(k.first.label, k.second.label), v)
    }.toMap()
  }

  val map: Map<Π2A<Σᐩ>, Set<Σᐩ>> by lazy {
    Q.groupBy({ (a, b, _) -> a to b }, { (_, _, c) -> c })
      .mapValues { (_, v) -> v.toSet() }
//      .also { it.map { println("${it.key}=${it.value.joinToString(",", "[", "]"){if(it in init) "$it*" else if (it in final) "$it@" else it}}") } }
  }

  val graph: LabeledGraph by lazy {
    LabeledGraph { Q.forEach { (a, b, c) -> a[b] = c } }
  }

  fun recognizes(str: Σᐩ) =
    (str.tokenizeByWhitespace().fold(init) { acc, sym ->
      val nextStates = acc.flatMap { map[it to sym] ?: emptySet() }.toSet()
//      println("$acc --$sym--> $nextStates")
      nextStates
    } intersect final).isNotEmpty()

  fun toDot() =
    graph.toDot(graph.vertices.filter { it.label in final }.toSet())
}

val TSA.states by cache { flatMap { listOf(it.π1, it.π3) }.toSet() }

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