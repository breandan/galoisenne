package ai.hypergraph.kaliningraph.automata

sealed class Bool<B, nB, aT, oF, xoT, xoF>
  (val t: B, val nb: nB, val at: aT, val of: oF, val xot: xoT, val xof: xoF)
object T: Bool<T, F, T, T, F, T>(T, F, T, T, F, T)
object F: Bool<F, T, F, F, T, F>(F, T, F, F, T, F)

infix fun <xoT> Bool<*, *, *, *, xoT, *>.xor(t: T) = xot
infix fun <xoF> Bool<*, *, *, *, *, xoF>.xor(f: F) = xof
infix fun <oF> Bool<*, *, *, oF, *, *>.or(f: F) = of
infix fun <aT> Bool<*, *, aT, *, *, *>.and(t: T) = at
infix fun Bool<*, *, *, *, *, *>.or(t: T) = T
infix fun Bool<*, *, *, *, *, *>.and(t: F) = F
fun <nB> Bool<*, nB, *, *, *, *>.flip() = nb

class BVec2<A, B>(val a: A, val b: B)
class BVec3<A, B, C>(val a: A, val b: B, val c: C)
class BVec4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
class BVec5<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)

fun <A, B> BVec(a: A, b: B) = BVec2(a, b)
fun <A, B, C> BVec(a: A, b: B, c: C) = BVec3(a, b, c)
fun <A, B, C, D> BVec(a: A, b: B, c: C, d: D) = BVec4(a, b, c, d)
fun <A, B, C, D, E> BVec(a: A, b: B, c: C, d: D, e: E) = BVec5(a, b, c, d, e)

fun <A, B> BVec2<A, B>.rot1() = BVec2(b, a)
fun <A, B, C> BVec3<A, B, C>.rot1() = BVec3(c, a, b)
fun <A, B, C, D> BVec4<A, B, C, D>.rot1() = BVec4(d, a, b, c)

fun <
  A: Bool<A, nA, *, *, *, *>, B: Bool<B, nB, *, *, *, *>,
  nA: Bool<nA, A, *, *, *, *>, nB: Bool<nB, B, *, *, *, *>
> BVec2<A, B>.flip() =
  BVec2(a.flip(), b.flip())

fun <
  A: Bool<A, nA, *, *, *, *>, B: Bool<B, nB, *, *, *, *>, C: Bool<C, nC, *, *, *, *>,
  nA: Bool<nA, A, *, *, *, *>, nB: Bool<nB, B, *, *, *, *>, nC: Bool<nC, C, *, *, *, *>
> BVec3<A, B, C>.flip() =
  BVec3(a.flip(), b.flip(), c.flip())

fun <
  A: Bool<A, nA, *, *, *, *>, B: Bool<B, nB, *, *, *, *>, C: Bool<C, nC, *, *, *, *>, D: Bool<D, nD, *, *, *, *>,
  nA: Bool<nA, A, *, *, *, *>, nB: Bool<nB, B, *, *, *, *>, nC: Bool<nC, C, *, *, *, *>, nD: Bool<nD, D, *, *, *, *>
> BVec4<A, B, C, D>.flip() =
  BVec4(a.flip(), b.flip(), c.flip(), d.flip())

fun <
  A: Bool<A, *, *, *, *, *>,
  B: Bool<B, *, *, *, *, *>,
  C: Bool<C, *, *, *, *, *>,
  D: Bool<D, *, *, *, *, *>, Y
> BVec4<A, B, C, D>.lfsr(op: (C, D) -> Y) =
  BVec4(op(c, d), a, b, c)

val lfsr = BVec(T, F, F, T)
  .let { it.lfsr { c, d -> c xor d } }
  .let { it.lfsr { c, d -> c xor d } }
  .let { it.lfsr { c, d -> c xor d } }
  .let { it.lfsr { c, d -> c xor d } }
  .let { it.lfsr { c, d -> c xor d } }
  .let { it.lfsr { c, d -> c xor d } }
  .let { it.lfsr { c, d -> c xor d } }
  .let { it.lfsr { c, d -> c xor d } }
  .let { it.lfsr { c, d -> c xor d } }
  .let { it.lfsr { c, d -> c xor d } }
  .let { it.lfsr { c, d -> c xor d } }
  .let { it.lfsr { c, d -> c xor d } }
  .let { it.lfsr { c, d -> c xor d } }
  .let { it.lfsr { c, d -> c xor d } }
  .let { it.lfsr { c, d -> c xor d } }
  .let { it.lfsr { c, d -> c xor d } }
