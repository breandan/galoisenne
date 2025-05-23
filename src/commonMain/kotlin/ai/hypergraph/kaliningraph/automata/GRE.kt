package ai.hypergraph.kaliningraph.automata

import ai.hypergraph.kaliningraph.KBitSet
import ai.hypergraph.kaliningraph.parsing.*
import ai.hypergraph.kaliningraph.tensor.UTMatrix
import ai.hypergraph.kaliningraph.types.*
import kotlin.collections.plus
import kotlin.math.max

// Generalized regular expression: https://planetmath.org/generalizedregularexpression
// Parsing with derivatives: https://matt.might.net/papers/might2011derivatives.pdf
sealed class GRE(open vararg val args: GRE) {
  class EPS: GRE()
  class SET(val s: KBitSet): GRE() { constructor(size: Int): this(KBitSet(size)) }
  class CUP(override vararg val args: GRE): GRE(*args)
  class CAT(val l: GRE, val r: GRE): GRE(l, r)

  fun words(terminals: List<Σᐩ>, shouldContinue: () -> Boolean = { true }): Sequence<Σᐩ> =
    enumerate(shouldContinue).takeWhile { shouldContinue() }.distinct()
      .map { it.mapNotNull { terminals[it].let { if (it == "ε") null else it } }.joinToString(" ") }

  fun wordsOrdered(
    terminals: List<Σᐩ>,
    ngrams: MutableMap<List<String>, Double>,
    shouldContinue: () -> Boolean = { true }
  ) =
    enumerateWithPriority(ngrams, terminals).takeWhile { shouldContinue() }.distinct()
      .map { it.mapNotNull { terminals[it].let { if (it == "ε") null else it } }.joinToString(" ") }

  val admits: KBitSet by lazy { followSet() }

  // F_s(g) = { s | ∂_s(g) != ∅ }
  fun GRE.followSet(width: Int = this.width): KBitSet = when (this) {
    is EPS -> KBitSet(width)
    is SET -> s
    is CUP -> args.map { it.followSet() }.fold (KBitSet(width)) { a, b -> a.apply { or(b) } }
    is CAT -> l.followSet()
  }

  val width: Int by lazy {
    when (this) {
      is EPS -> 0
      is SET -> s.n
      is CUP -> args.maxOf { it.width }
      is CAT -> max(l.width, r.width)
    }
  }

  fun enumerate(shouldContinue: () -> Boolean = { true }): Sequence<List<Int>> = sequence {
    if (!shouldContinue()) emptySequence<List<Int>>()
    else when (this@GRE) {
      is EPS -> emptyList<Int>()
      is SET -> yieldAll(s.toList().map { listOf(it) })
      is CUP -> for (a in args) yieldAll(a.enumerate(shouldContinue))
//      yieldAll(args.map { it.enumerate().toSet() }.reduce { a, b -> a + b })
      is CAT ->
        for (lhs in l.enumerate(shouldContinue))
        for (rhs in r.enumerate(shouldContinue))
          if (lhs.isEmpty()) { if (rhs.isEmpty()) yield(emptyList()) else rhs              }
          else               { if (rhs.isEmpty()) yield(lhs)         else yield(lhs + rhs) }
    }
  }

  // Greedy LTR decoding
  fun enumerateWithPriority(
    ngrams: MutableMap<List<String>, Double>,
    tmLst: List<Σᐩ>,
    prefix: List<Σᐩ> = listOf("BOS", "NEWLINE")
  ): Sequence<List<Int>> = sequence {
    val pfx = if (prefix.size == ngrams.keys.first().size) prefix.drop(1) else prefix
//    println("pfx: ${pfx.joinToString(" ")}")
    when (this@GRE) {
      is EPS -> emptyList<Int>()
      is SET ->
        yieldAll(s.toList().map { -(ngrams[pfx + tmLst[it]] ?: 0.0) to it }
          .sortedBy { it.first }.map { listOf(it.second) })
//        yieldAll(s.toList().map { listOf(it) })
      is CUP -> {
        val orderedChoices = admits.toList()
          .map { -(ngrams[pfx + tmLst[it]] ?: 0.0) to it }
          .sortedBy { it.first }.map { it.second }
        for (tk in orderedChoices) for (g in args.filter { it.admits[tk] })
          yieldAll(g.enumerateWithPriority(ngrams, tmLst, pfx + tmLst[tk]))
      }
//      yieldAll(args.map { it.enumerate().toSet() }.reduce { a, b -> a + b })
      is CAT ->
        for (lhs in l.enumerateWithPriority(ngrams, tmLst, pfx))
        for (rhs in r.enumerateWithPriority(ngrams, tmLst, pfx))
            if (lhs.isEmpty()) { if (rhs.isEmpty()) yield(emptyList()) else rhs              }
            else               { if (rhs.isEmpty()) yield(lhs)         else yield(lhs + rhs) }
    }
  }

  // ∂_s(g) = { w | s·w ∈ L(g) }
  fun dv(σ: Int): GRE = when (this) {
    is EPS -> null!! // ∂_s(ε) = ∅
    is SET -> if (s[σ]) EPS() else null!!
    is CUP -> args.filter { it.admits[σ] }.reduce { a, b -> a + b }
    // ∂_s(E1 · E2) = (∂_s(E1)) · E2   ∪   [if E1 nullable => ∂_s(E2)]
    is CAT -> (l.dv(σ) * r).let { dl -> if (l.nullable) dl + r.dv(σ) else dl }
  }

  val nullable by lazy { isNullable() }

  // Check whether 'g' accepts the empty string ε.
  fun isNullable(): Boolean = when (this) {
    is EPS -> true
    is SET -> false
    is CUP -> args.any { it.isNullable() }
    is CAT -> l.isNullable() && r.isNullable()
  }

  operator fun plus(g: GRE): GRE = CUP(this, g)
  operator fun times(g: GRE): GRE = CAT(this, g)

//  override fun toString() = when (this) {
//    is EPS -> "ε"
//    is SET -> if (s.isEmpty()) "∅" else "( ${s.joinToString(" ")} )"
//    is UNI -> "( ${args.joinToString(" ∪ "){ "$it" }} )"
//    is CAT -> "$l $r"
//  }
}

fun CFG.initGREListMat(tokens: List<Σᐩ>): UTMatrix<List<GRE?>> =
  UTMatrix(
    ts = tokens.map { token ->
      val ptreeList = MutableList<GRE?>(nonterminals.size) { null }
      (if (token != HOLE_MARKER) bimap[listOf(token)] else unitNonterminals)
        .associateWith { nt ->
          if (token != HOLE_MARKER) GRE.SET(KBitSet(terminals.size, tmMap[token]!!))
          else bimap.UNITS[nt]?.let { GRE.SET(KBitSet(tmLst.size, it.map { tmMap[it]!! })) }
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