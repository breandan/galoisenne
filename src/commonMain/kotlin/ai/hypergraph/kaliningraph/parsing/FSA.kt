package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.graphs.*
import ai.hypergraph.kaliningraph.types.*

typealias Arc = Π3A<Σᐩ>
typealias TSA = Set<Arc>

data class FSA(val Q: TSA, val init: Set<Σᐩ>, val final: Set<Σᐩ>) {
  val alphabet by lazy { Q.map { it.π2 }.toSet() }
  val states by lazy { Q.states }

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
}

val TSA.states by cache { flatMap { listOf(it.π1, it.π3) }.toSet() }