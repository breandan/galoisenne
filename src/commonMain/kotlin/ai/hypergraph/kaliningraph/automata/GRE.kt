package ai.hypergraph.kaliningraph.automata

import ai.hypergraph.kaliningraph.parsing.*
import ai.hypergraph.kaliningraph.tensor.UTMatrix
import ai.hypergraph.kaliningraph.tokenizeByWhitespace
import ai.hypergraph.kaliningraph.types.*

// Generalized regular expression: https://planetmath.org/generalizedregularexpression
// Parsing with derivatives: https://matt.might.net/papers/might2011derivatives.pdf
sealed class GRE(open vararg val args: GRE) {
  class EPS: GRE()
  class ONE(val s: Σᐩ): GRE()
  class SET(val s: Set<Σᐩ>): GRE()
  class UNI(override vararg val args: GRE): GRE(*args)
  class CAT(val l: GRE, val r: GRE): GRE(l, r)

  fun words(): Sequence<Σᐩ> =
    enumerate().map { it.tokenizeByWhitespace().joinToString(" ") }.distinct()

  // F_s(g) = { s | ∂_s(g) != ∅ }
  fun GRE.followSet(): Set<Σᐩ> = when (this) {
    is EPS -> emptySet()
    is ONE -> setOf(s)
    is SET -> s
    is UNI -> args.map { it.followSet() }.fold (emptySet()) { a, b -> a + b }
    is CAT -> l.followSet()
  }

  fun enumerate(): Sequence<Σᐩ> = sequence {
    when (this@GRE) {
      is EPS -> yield("")
      is ONE -> yield(s.replace("ε", ""))
      is SET -> yieldAll(s.map { it.replace("ε", "") })
      is UNI -> for (a in args) yieldAll(a.enumerate())
//      yieldAll(args.map { it.enumerate().toSet() }.reduce { a, b -> a + b })
      is CAT -> for (lhs in l.enumerate()) for (rhs in r.enumerate())
        if (lhs.isEmpty()) {
          if (rhs.isEmpty()) yield("") else rhs
        } else {
          if (rhs.isEmpty()) yield(lhs)
          else yield("$lhs $rhs")
        }
    }
  }

  // We treat SET(emptySet()) as the empty language.
  val NIL by lazy { SET(emptySet()) }

  // ∂_s(g) = { w | s·w ∈ L(g) }
  fun dv(s: Σᐩ): GRE = when (this) {
    is EPS -> NIL // ∂_s(ε) = ∅
    is ONE -> if (s == this.s) EPS() else NIL
    is SET -> if (s in this.s) EPS() else NIL
    is UNI -> args.reduce { a, b -> a + b }
    is CAT -> {
      // ∂_s(E1 · E2) = (∂_s(E1)) · E2   ∪   [if E1 nullable => ∂_s(E2)]
      val dLeft = l.dv(s) * r
      if (l.nullable()) dLeft + r.dv(s) else dLeft
    }
  }

  // Check whether 'g' accepts the empty string ε.
  fun nullable(): Boolean = when (this) {
    is EPS -> true
    is ONE -> false
    is SET -> false
    is UNI -> args.any { it.nullable() }
    is CAT -> l.nullable() && r.nullable()
  }

  operator fun plus(g: GRE): GRE = UNI(this, g)
  operator fun times(g: GRE): GRE = CAT(this, g)

  override fun toString() = when (this) {
    is EPS -> "ε"
    is ONE -> s
    is SET -> if (s.isEmpty()) "∅" else "( ${s.joinToString(" ")} )"
    is UNI -> "( ${args.joinToString(" ∪ "){ "$it" }} )"
    is CAT -> "$l $r"
  }
}

fun CFG.initGREListMat(tokens: List<Σᐩ>): UTMatrix<List<GRE?>> =
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

fun CFG.startGRE(tokens: List<Σᐩ>): GRE? =
  initGREListMat(tokens).seekFixpoint().diagonals.last()[0][bindex[START_SYMBOL]]