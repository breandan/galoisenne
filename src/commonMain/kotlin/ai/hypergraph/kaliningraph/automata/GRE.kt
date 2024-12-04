package ai.hypergraph.kaliningraph.automata

import ai.hypergraph.kaliningraph.parsing.*
import ai.hypergraph.kaliningraph.tensor.UTMatrix
import ai.hypergraph.kaliningraph.types.*

// Generalized regular expression: https://planetmath.org/generalizedregularexpression
// Parsing with derivatives: https://matt.might.net/papers/might2011derivatives.pdf
sealed class GRE(vararg val args: GRE) {
  companion object { operator fun invoke(s: Σᐩ) = ONE(s) }

  class EPS: GRE()
  class ONE(val s: Σᐩ): GRE()
  class SET(val s: Set<Σᐩ>): GRE()
  class NEG(val g: GRE): GRE(g)
  class UNI(val l: GRE, val r: GRE): GRE(l, r)
  class CAT(val l: GRE, val r: GRE): GRE(l, r)
  class INT(val l: GRE, val r: GRE): GRE(l, r)

  infix fun and(a: GRE): GRE = INT(this, a)
  operator fun plus(g: GRE): GRE = UNI(this, g)
  operator fun times(g: GRE): GRE = CAT(this, g)
  operator fun not(): GRE = NEG(this)

  override fun toString(): String = when (this) {
    is ONE -> s
    is SET -> "( ${s.joinToString(" ")} )"
    is NEG -> "! ( $g )"
    is UNI -> "( $l ∪ $r )"
    is CAT -> "$l $r"
    is INT -> "$l ∩ $r"
    is EPS -> "ε"
  }
}


fun CFG.initGREListMat(tokens: List<String>): UTMatrix<List<GRE?>> =
  UTMatrix(
    ts = tokens.map { token ->
      val ptreeList = MutableList<GRE?>(nonterminals.size) { null }
      (if (token != HOLE_MARKER) bimap[listOf(token)] else unitNonterminals)
        .associateWith { nt ->
          if (token != HOLE_MARKER) GRE.ONE(token)
          else bimap.UNITS[nt]?.let { GRE.SET(it) }
        }.forEach { (k, v) -> ptreeList[bindex[k]] = v }
      ptreeList
    }.toTypedArray(),
    algebra = greAlgebra
  )

val CFG.greAlgebra: Ring<List<GRE?>> by cache {
  vindex.let {
    Ring.of(
      nil = List(nonterminals.size) { null },
      plus = { x, y -> greUnion(x, y) },
      times = { x, y -> greJoin(x, y) }
    )
  }
}

fun greUnion(l: List<GRE?>, r: List<GRE?>) =
  l.zip(r) { l, r -> if (l == null) r else if (r == null) l else l + r }

fun CFG.greJoin(left: List<GRE?>, right: List<GRE?>): List<GRE?> = vindex2.map {
  val t = it.map { (B, C) -> if (left[B] != null && right[C] != null) left[B]!! * right[C]!! else null }
  if (t.isEmpty()) null else t.reduce { acc, int -> if (acc == null) int else if (int == null) acc else acc + int }
}

fun CFG.startGRE(tokens: List<String>): GRE? =
  initGREListMat(tokens).seekFixpoint().diagonals.last()[0][bindex[START_SYMBOL]]

