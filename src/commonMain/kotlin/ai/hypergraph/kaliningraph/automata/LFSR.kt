package ai.hypergraph.kaliningraph.automata

sealed class Bool<B, nB, aT, oF, xoT, xoF>(
  val t: B,
  val nb: nB,
  val at: aT,
  val of: oF,
  val xot: xoT,
  val xof: xoF
) {
  open fun flip(): nB = nb
  open fun or(f: F): oF = of
  open fun and(t: T): aT = at
  open fun xor(t: T): xoT = xot
  open fun xor(f: F): xoF = xof
  open fun or(t: T): T = T
  open fun and(f: F): F = F
}
object T: Bool<T, F, T, T, F, T>(T, F, T, T, F, T)
object F: Bool<F, T, F, F, T, F>(F, T, F, F, T, F)

class BVec2<A, B>(val a: A, val b: B)
class BVec4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
class BVec5<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)

fun <A, B> BVec2<A, B>.rot1() = BVec2(b, a)
fun <
  A: Bool<A, nA, *, *, *, *>, B: Bool<B, nB, *, *, *, *>,
  nA: Bool<nA, A, *, *, *, *>, nB: Bool<nB, B, *, *, *, *>
> BVec2<A, B>.flip() = BVec2(a.flip(), b.flip())

fun <A, B> BVec(a: A, b: B) = BVec2(a, b)
fun <A, B, C, D> BVec(a: A, b: B, c: C, d: D) = BVec4(a, b, c, d)
fun <A, B, C, D, E> BVec(a: A, b: B, c: C, d: D, e: E) = BVec5(a, b, c, d, e)

fun <A, B, C, D> BVec4<A, B, C, D>.rot1() = BVec4(d, a, b, c)

fun <
  A: Bool<A, nA, *, *, *, *>, B: Bool<B, nB, *, *, *, *>, C: Bool<C, nC, *, *, *, *>, D: Bool<D, nD, *, *, *, *>,
  nA: Bool<nA, A, *, *, *, *>, nB: Bool<nB, B, *, *, *, *>, nC: Bool<nC, C, *, *, *, *>, nD: Bool<nD, D, *, *, *, *>
> BVec4<A, B, C, D>.flip() = BVec4(a.flip(), b.flip(), c.flip(), d.flip())

inline fun <
  reified A: Bool<A, *, *, *, *, *>,
  reified B: Bool<B, *, *, *, *, *>,
  reified C: Bool<C, *, *, *, *, *>,
  reified D: Bool<D, *, *, *, *, *>, Y
> BVec4<A, B, C, D>.lfsr(op: (C, D) -> Y) = BVec4(op(c, d), a, b, c)

val lfsr = BVec(F, F, F, T)
  .let { it.lfsr { c, d -> c.xor(d) } }
  .let { it.lfsr { c, d -> c.xor(d) } }
  .let { it.lfsr { c, d -> c.xor(d) } }
  .let { it.lfsr { c, d -> c.xor(d) } }
  .let { it.lfsr { c, d -> c.xor(d) } }
  .let { it.lfsr { c, d -> c.xor(d) } }
  .let { it.lfsr { c, d -> c.xor(d) } }
  .let { it.lfsr { c, d -> c.xor(d) } }
  .let { it.lfsr { c, d -> c.xor(d) } }
  .let { it.lfsr { c, d -> c.xor(d) } }
  .let { it.lfsr { c, d -> c.xor(d) } }
  .let { it.lfsr { c, d -> c.xor(d) } }
  .let { it.lfsr { c, d -> c.xor(d) } }
  .let { it.lfsr { c, d -> c.xor(d) } }
  .let { it.lfsr { c, d -> c.xor(d) } }
  .let { it.lfsr { c, d -> c.xor(d) } }
