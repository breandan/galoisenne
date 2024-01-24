package ai.hypergraph.kaliningraph.automata

import ai.hypergraph.kaliningraph.parsing.Σᐩ
import ai.hypergraph.kaliningraph.types.*

typealias StrProp = (Σᐩ) -> Bln

// https://arxiv.org/pdf/1402.0897.pdf
// https://cs.ru.nl/~freek/courses/mfocs-2021/slides/steven.pdf
// https://lipn.univ-paris13.fr/~choppy/IFIP/AUSSOIS/AUSSOIS-DATA/Klin.pdf
class NAM(override val Q: TSA, override val init: Set<Σᐩ>, override val final: Set<Σᐩ>): FSA(Q, init, final) {
  // Only supports != right now
  override val alphabet: Set<Σᐩ> by lazy {
    Q.map { it.second }.filter { it.startsWith("[!=]") }.toSet()
  }

  val mapF: Map<Σᐩ, List<Π2<StrProp, Σᐩ>>> by lazy {
    Q.map { q ->
      q.first to (
        if (q.second.startsWith("[!=]")) { s: Σᐩ -> s != q.second.drop(4) }
        else { s: Σᐩ -> s == q.second }
      ) to q.third
    }.groupBy { it.first }
      .mapValues { (_, v) -> v.map { it.second to it.third } }
  }

  override fun recognizes(str: List<Σᐩ>): Boolean =
    str.fold(init) { acc, sym ->
      acc.flatMap { q ->
        mapF[sym]?.filter { it.first(sym) }?.map { it.second } ?: emptyList()
      }.toSet()
    }.any { it in final }
}

fun FSA.nominalize() = NAM(Q, init, final)