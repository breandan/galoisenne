package ai.hypergraph.kaliningraph.automata

import ai.hypergraph.kaliningraph.parsing.Σᐩ
import ai.hypergraph.kaliningraph.types.*

data class StrPred(val arg: Σᐩ) {
  operator fun invoke(s: Σᐩ) =
    if (arg == "[.*]") true
    else if (arg.startsWith("[!=]")) s != arg.drop(4)
    else s == arg
}

// https://arxiv.org/pdf/1402.0897.pdf
// https://cs.ru.nl/~freek/courses/mfocs-2021/slides/steven.pdf
// https://lipn.univ-paris13.fr/~choppy/IFIP/AUSSOIS/AUSSOIS-DATA/Klin.pdf
class NOM(override val Q: TSA, override val init: Set<Σᐩ>, override val final: Set<Σᐩ>): FSA(Q, init, final) {
  // Only supports != right now
  override val alphabet: Set<Σᐩ> by lazy {
    Q.map { it.second }.filter { it.startsWith("[!=]") }.toSet()
  }

  val mapF: Map<Σᐩ, List<Π2<StrPred, Σᐩ>>> by lazy {
    Q.map { q -> Triple(q.first, StrPred(q.second), q.third) }.groupBy { it.first }
      .mapValues { (_, v) -> v.map { Pair(it.second, it.third) } }
  }

  val flattenedTriples: Set<Triple<Σᐩ, StrPred, Σᐩ>> by lazy { Q.map { (a, b, c) -> a to StrPred(b) to c }.toSet() }

  override fun recognizes(str: List<Σᐩ>): Boolean =
    str.fold(init) { acc, sym ->
      acc.flatMap {
        mapF[it]?.filter { it.first(sym) }?.map { it.second } ?: emptyList()
      }.toSet()
    }.any { it in final }

//  enum class PredKind { Wild, Eq, Ne }
//  data class IEdge(val from: Int, val to: Int)
//  data class ClassArc(val kind: PredKind, val sym: String?, val edge: IEdge)
//
//  override val stateLst by lazy { states.groupBy { it.coords().let { (a, b) -> a + b } }.values.flatten() }
//
//  val classifiedArcs: List<ClassArc> by lazy {
//    Q.map { (a, b, c) ->
//      val from = stateMap[a]!!
//      val to   = stateMap[c]!!
//      val (kind, sym) = when {
//        b == "[.*]"                  -> PredKind.Wild to null
//        b.startsWith("[!=]") -> PredKind.Ne   to b.drop(4)
//        else                         -> PredKind.Eq   to b
//      }
//      ClassArc(kind, sym, IEdge(from, to))
//    }
//  }
//
//  val wildArcs: List<IEdge> by lazy { classifiedArcs.asSequence().filter { it.kind == PredKind.Wild }.map { it.edge }.toList() }
//
//  val eqArcs: Map<String, List<IEdge>> by lazy {
//    classifiedArcs.asSequence()
//      .filter { it.kind == PredKind.Eq }
//      .groupBy({ it.sym!! }, { it.edge })
//  }
//
//  val neArcs: Map<String, List<IEdge>> by lazy {
//    classifiedArcs.asSequence()
//      .filter { it.kind == PredKind.Ne }
//      .groupBy({ it.sym!! }, { it.edge }) // arcs that match any σ != key
//  }
//
//  val allNeArcs: List<IEdge> by lazy { neArcs.values.flatten() }
}

fun FSA.nominalize() = NOM(Q, init, final)