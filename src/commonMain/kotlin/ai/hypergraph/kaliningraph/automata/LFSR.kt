@file:Suppress("NonAsciiCharacters")
package ai.hypergraph.kaliningraph.automata

sealed class Bool<B, nB, aT, oF, xoT, xoF>
  (val t: B, val nb: nB, val at: aT, val of: oF, val xot: xoT, val xof: xoF)
object 龖: Bool<龖, 口, 龖, 龖, 口, 龖>(龖, 口, 龖, 龖, 口, 龖)
object 口: Bool<口, 龖, 口, 口, 龖, 口>(口, 龖, 口, 口, 龖, 口)
typealias T = 龖
typealias F = 口

infix fun <xoT> Bool<*, *, *, *, xoT, *>.xor(t: T) = xot
infix fun <xoF> Bool<*, *, *, *, *, xoF>.xor(f: F) = xof
infix fun <oF> Bool<*, *, *, oF, *, *>.or(f: F) = of
infix fun <aT> Bool<*, *, aT, *, *, *>.and(t: T) = at
infix fun Bool<*, *, *, *, *, *>.or(t: T) = T
infix fun Bool<*, *, *, *, *, *>.and(f: F) = F
fun <nB> Bool<*, nB, *, *, *, *>.flip() = nb

class BVec2<A, B>(val a: A, val b: B)
class BVec3<A, B, C>(val a: A, val b: B, val c: C)
class BVec4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
class BVec5<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)
class BVec10< B0, B1, B2, B3, B4, B5, B6, B7, B8, B9>(val b0: B0,val  b1: B1,val  b2: B2, val b3: B3, val b4: B4, val b5: B5,val  b6: B6,val  b7: B7,val  b8: B8,val  b9: B9)

fun <A, B> BVec(a: A, b: B) = BVec2(a, b)
fun <A, B, C> BVec(a: A, b: B, c: C) = BVec3(a, b, c)
fun <A, B, C, D> BVec(a: A, b: B, c: C, d: D) = BVec4(a, b, c, d)
fun <A, B, C, D, E> BVec(a: A, b: B, c: C, d: D, e: E) = BVec5(a, b, c, d, e)
fun < B0, B1, B2, B3, B4, B5, B6, B7, B8, B9>
    BVec(b0: B0, b1: B1, b2: B2, b3: B3, b4: B4, b5: B5, b6: B6, b7: B7, b8: B8, b9: B9) =
  BVec10(b0, b1, b2, b3, b4, b5, b6, b7, b8, b9)

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

fun <
  A: Bool<A, *, *, *, *, *>,
  B: Bool<B, *, *, *, *, *>,
  C: Bool<C, *, *, *, *, *>,
  D: Bool<D, *, *, *, *, *>,
  E: Bool<E, *, *, *, *, *>, Y
> BVec5<A, B, C, D, E>.lfsr(op: (C, E) -> Y) =
  BVec5(op(c, e), a, b, c, d)

fun rule(a: T, b: F) = a xor b
fun rule(a: T, b: T) = a xor b
fun rule(a: F, b: T) = a xor b
fun rule(a: F, b: F) = a xor b

val lfsr4 = BVec(T, F, F, T)
  .lfsr(::rule)
  .lfsr(::rule)
  .lfsr(::rule)
  .lfsr(::rule)
  .lfsr(::rule)
  .lfsr(::rule)
  .lfsr(::rule)
  .lfsr(::rule)
  .lfsr(::rule)
  .lfsr(::rule)
  .lfsr(::rule)
  .lfsr(::rule)
  .lfsr(::rule)
  .lfsr(::rule)
  .lfsr(::rule)

val lfsr5 = BVec(T, F, F, T, T)
  .lfsr(::rule)
  .lfsr(::rule)
  .lfsr(::rule)
  .lfsr(::rule)
  .lfsr(::rule)
  .lfsr(::rule)
  .lfsr(::rule)
  .lfsr(::rule)
  .lfsr(::rule)
  .lfsr(::rule)
  .lfsr(::rule)
  .lfsr(::rule)