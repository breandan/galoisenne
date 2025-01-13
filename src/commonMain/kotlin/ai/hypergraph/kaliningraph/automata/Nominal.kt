package ai.hypergraph.kaliningraph.automata

import ai.hypergraph.kaliningraph.parsing.Σᐩ
import ai.hypergraph.kaliningraph.types.*

typealias StrPred = (Σᐩ) -> Bln

// https://arxiv.org/pdf/1402.0897.pdf
// https://cs.ru.nl/~freek/courses/mfocs-2021/slides/steven.pdf
// https://lipn.univ-paris13.fr/~choppy/IFIP/AUSSOIS/AUSSOIS-DATA/Klin.pdf
class NOM(override val Q: TSA, override val init: Set<Σᐩ>, override val final: Set<Σᐩ>): FSA(Q, init, final) {
  // Only supports != right now
  override val alphabet: Set<Σᐩ> by lazy {
    Q.map { it.second }.filter { it.startsWith("[!=]") }.toSet()
  }

  fun Σᐩ.predicate(): (Σᐩ) -> Boolean =
    if (this == "[.*]") { s: Σᐩ -> true }
    else if (startsWith("[!=]")) { s: Σᐩ -> s != drop(4) }
    else { s: Σᐩ -> s == this }

  val mapF: Map<Σᐩ, List<Π2<StrPred, Σᐩ>>> by lazy {
    Q.map { q -> q.first to q.second.predicate() to q.third }.groupBy { it.first }
      .mapValues { (_, v) -> v.map { it.second to it.third } }
  }

  val flattenedTriples: Set<Triple<Σᐩ, StrPred, Σᐩ>> by lazy {
    Q.map { (a, b, c) -> a to b.predicate() to c }.toSet()
  }

  override fun recognizes(str: List<Σᐩ>): Boolean =
    str.fold(init) { acc, sym ->
      acc.flatMap {
        mapF[it]?.filter { it.first(sym) }?.map { it.second } ?: emptyList()
      }.toSet()
    }.any { it in final }
}

fun FSA.nominalize() = NOM(Q, init, final)