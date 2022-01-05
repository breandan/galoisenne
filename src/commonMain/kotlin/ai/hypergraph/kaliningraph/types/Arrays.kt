package ai.hypergraph.kaliningraph.types

import ai.hypergraph.kaliningraph.*
import kotlin.jvm.JvmName

// Multi-typed arrays
data class Y1<A>(val e1: A)
data class Y2<A, B>(val e1: A, val e2: B)
data class Y3<A, B, C>(val e1: A, val e2: B, val e3: C)
data class Y4<A, B, C, D>(val e1: A, val e2: B, val e3: C, val e4: D)

open class Vec<E, L: S<*>> internal constructor(val len: L, val a: List<E>): List<E> by a {
  internal constructor(l: L, vararg es: E): this(l, es.toList())

  internal fun <A: S<*>, B: S<*>> fetch(intRange: Y2<A, B>): List<E> = subList(intRange.e1.toInt(), intRange.e2.toInt())
}

/** TODO: Unify this representation with [ai.hypergraph.kaliningraph.tensor.Matrix] */
typealias Mat<E, R, C> = Vec<Vec<E, C>, R>

infix fun <T> T.cc(that: T) = Vec(this, that)

fun <T1: Vec<E, L>, E, L: S<*>> T1.append(that: E): Vec<E, Q1<L>> = Vec(this.len + S1, this.a + listOf(that))
fun <T1: Vec<E, L>, E, L: S<*>> T1.prepend(that: E): Vec<E, Q1<L>> = Vec(this.len + S1, listOf(that) + this.a)

@JvmName("cc2") infix fun <T1: Vec<E, L>, T2: Vec<E, L1>, E, L: S<*>> T1.cc(that: T2): Vec<E, Q1<L>> = Vec(this.len + that.len, this.a + that.a)
@JvmName("cc3") infix fun <T1: Vec<E, L>, T2: Vec<E, L2>, E, L: S<*>> T1.cc(that: T2): Vec<E, Q2<L>> = Vec(this.len + that.len, this.a + that.a)
@JvmName("cc4") infix fun <T1: Vec<E, L>, T2: Vec<E, L3>, E, L: S<*>> T1.cc(that: T2): Vec<E, Q3<L>> = Vec(this.len + that.len, this.a + that.a)
@JvmName("cc5") infix fun <T1: Vec<E, L>, T2: Vec<E, L4>, E, L: S<*>> T1.cc(that: T2): Vec<E, Q4<L>> = Vec(this.len + that.len, this.a + that.a)
@JvmName("cc6") infix fun <T1: Vec<E, L>, T2: Vec<E, L5>, E, L: S<*>> T1.cc(that: T2): Vec<E, Q5<L>> = Vec(this.len + that.len, this.a + that.a)

fun <E> Vec(v1: E) = Vec(S1, v1)
fun <E> Vec(v1: E, v2: E) = Vec(S2, v1, v2)
fun <E> Vec(v1: E, v2: E, v3: E) = Vec(S3, v1, v2, v3)
fun <E> Vec(v1: E, v2: E, v3: E, v4: E) = Vec(S4, v1, v2, v3, v4)
fun <E> Vec(v1: E, v2: E, v3: E, v4: E, v5: E) = Vec(S5, v1, v2, v3, v4, v5)
fun <E> Vec(v1: E, v2: E, v3: E, v4: E, v5: E, v6: E) = Vec(S6, v1, v2, v3, v4, v5, v6)
fun <E> Vec(v1: E, v2: E, v3: E, v4: E, v5: E, v6: E, v7: E) = Vec(S7, v1, v2, v3, v4, v5, v6, v7)
fun <E> Vec(v1: E, v2: E, v3: E, v4: E, v5: E, v6: E, v7: E, v8: E) = Vec(S8, v1, v2, v3, v4, v5, v6, v7, v8)
fun <E> Vec(v1: E, v2: E, v3: E, v4: E, v5: E, v6: E, v7: E, v8: E, v9: E) = Vec(S9, v1, v2, v3, v4, v5, v6, v7, v8, v9)

typealias V1<E> = Vec<E, L1>
typealias V2<E> = Vec<E, L2>
typealias V3<E> = Vec<E, L3>
typealias V4<E> = Vec<E, L4>
typealias V5<E> = Vec<E, L5>
typealias V6<E> = Vec<E, L6>
typealias V7<E> = Vec<E, L7>
typealias V8<E> = Vec<E, L8>
typealias V9<E> = Vec<E, L9>

fun <E, D1: S<*>, D2: S<*>> List<E>.chunked(d1: D1, d2: D2): List<Vec<E, D2>> = chunked(d1.toInt()).map { Vec(d2, it) }

inline fun <reified R: S<*>> asInt() = R::class.simpleName!!.drop(1).toInt()
fun <E, R: S<*>, C: S<*>> Mat(r: R, c: C, vararg es: E): Mat<E, R, C> = Mat(r, c, es.toList())
fun <E, R: S<*>, C: S<*>> Mat(r: R, c: C, es: List<E>): Mat<E, R, C> = Mat(r, es.chunked(r, c))
fun <E, R: S<*>, C: S<*>> Mat(r: R, c: C, f: (Int, Int) -> E): Mat<E, R, C> =
  Mat(r, c, allPairs(r.toInt(), c.toInt()).map { (r, c) -> f(r, c) })

fun <E> Mat2x1(t1: E, t2: E): Mat<E, L2, L1> = Mat(S2, S1, t1, t2)
fun <E> Mat1x2(t1: E, t2: E): Mat<E, L1, L2> = Mat(S1, S2, t1, t2)
//...Optional pseudoconstructors

operator fun <E, R: S<*>, C1: S<*>, C2: S<*>> Mat<E, R, C1>.times(that: Mat<E, C1, C2>): Mat<E, R, C2> = TODO()
operator fun <E, R: S<*>, C: S<*>> Mat<E, R, C>.get(r: Int, c: Int): E = a[r][c]
//fun <E, R: S<*>, C: S<*>> Mat<E, R, C>.transpose(): Mat<E, C, R> = Mat { r, c -> this[c][r]}

@JvmName("get1") operator fun <R, L : Q1<R>, E> Vec<E, L>.get(i: L1) = a[0]
@JvmName("get2") operator fun <R, L : Q2<R>, E> Vec<E, L>.get(i: L2) = a[1]
@JvmName("get3") operator fun <R, L : Q3<R>, E> Vec<E, L>.get(i: L3) = a[2]
@JvmName("get4") operator fun <R, L : Q4<R>, E> Vec<E, L>.get(i: L4) = a[3]
@JvmName("get5") operator fun <R, L : Q5<R>, E> Vec<E, L>.get(i: L5) = a[4]
@JvmName("get6") operator fun <R, L : Q6<R>, E> Vec<E, L>.get(i: L6) = a[5]
@JvmName("get7") operator fun <R, L : Q7<R>, E> Vec<E, L>.get(i: L7) = a[6]
@JvmName("get8") operator fun <R, L : Q8<R>, E> Vec<E, L>.get(i: L8) = a[7]
@JvmName("get9") operator fun <R, L : Q9<R>, E> Vec<E, L>.get(i: L9) = a[8]

val <R, L : Q1<R>, E> Vec<E, L>.first: E get() = component1()
val <R, L : Q2<R>, E> Vec<E, L>.second: E get() = component2()
val <R, L : Q3<R>, E> Vec<E, L>.third: E get() = component3()

operator fun <T> Array<T>.get(range: IntRange) = sliceArray(range)

fun <E, Z : Q1<P>, P> Vec<E, Z>.take1(): Vec<E, L1> = Vec(S1, fetch(S0..S1))
fun <E, Z : Q2<P>, P> Vec<E, Z>.take2(): Vec<E, L2> = Vec(S2, fetch(S0..S2))
fun <E, Z : Q3<P>, P> Vec<E, Z>.take3(): Vec<E, L3> = Vec(S3, fetch(S0..S3))
fun <E, Z : Q4<P>, P> Vec<E, Z>.take4(): Vec<E, L4> = Vec(S4, fetch(S0..S4))

fun <E, Z : Q2<P>, P> Vec<E, Z>.drop1(): Vec<E, S<P>> = Vec(len - S1, fetch(S1..len))
fun <E, Z : Q3<P>, P> Vec<E, Z>.drop2(): Vec<E, S<P>> = Vec(len - S2, fetch(S2..len))
fun <E, Z : Q4<P>, P> Vec<E, Z>.drop3(): Vec<E, S<P>> = Vec(len - S3, fetch(S3..len))
fun <E, Z : Q5<P>, P> Vec<E, Z>.drop4(): Vec<E, S<P>> = Vec(len - S4, fetch(S4..len))

//                              ┌────j────┐    ┌────k────┐    where j, j are the relative offsets Y - X, Z - Y respectively
// Encodes the constraint:  P < X    <    Y && Y    <    Z    where X, Y are the start and end of range in a vector of length Z
@JvmName("sv121") operator fun <E, X: Q1<P>, Y: Q2<X>, Z : Q1<Y>, P> Vec<E, Z>.get(r: Y2<X, Y>): Vec<E, L2> = Vec(S2, fetch(r))
@JvmName("sv122") operator fun <E, X: Q1<P>, Y: Q2<X>, Z : Q2<Y>, P> Vec<E, Z>.get(r: Y2<X, Y>): Vec<E, L2> = Vec(S2, fetch(r))
@JvmName("sv221") operator fun <E, X: Q2<P>, Y: Q2<X>, Z : Q1<Y>, P> Vec<E, Z>.get(r: Y2<X, Y>): Vec<E, L2> = Vec(S2, fetch(r))
@JvmName("sv222") operator fun <E, X: Q2<P>, Y: Q2<X>, Z : Q2<Y>, P> Vec<E, Z>.get(r: Y2<X, Y>): Vec<E, L2> = Vec(S2, fetch(r))

operator fun <A, B> S<A>.rangeTo(that: S<B>) = Y2(this, that)

// ============================= Naperian Functors ==============================

// https://www.cs.ox.ac.uk/people/jeremy.gibbons/publications/aplicative.pdf
// https://github.com/NickHu/naperian-functors/blob/master/src/Data/Naperian.hs
// https://github.com/NickHu/naperian-functors/blob/master/src/Data/Naperian/Vector.hs
// https://github.com/NickHu/naperian-functors/blob/master/src/Data/Naperian/Symbolic.hs
// "The main idea is that a rank-n array is essentially a data structure of type
// D₁(D₂(...(Dₙ a))), where each Dᵢ is a dimension : a container type, categorically
// a functor; one might think in the first instance of lists."

// This gives us something like a Church-encoded list
// Using a recursive type bound T: Ts<H, T> will blow up the compiler
class Ts<H, T>(val head: H, val tail: T? = null) {
  operator fun get(i: Int): H =
    if (i == 0) head else if (tail is Ts<*, *>) tail[i - 1] as H else throw IndexOutOfBoundsException()
  fun size(): Int = if (tail == null) 1 else if (tail is Ts<*, *>) 1 + tail.size() else 1
  /** TODO: Maybe possible to make bidirectional, see [ai.hypergraph.experimental.DLL] */
}

// Product
typealias Ts1<H> = Ts<H, Nothing>
typealias Ts2<H> = Ts<H, Ts1<H>>
typealias Ts3<H> = Ts<H, Ts2<H>>
typealias Ts4<H> = Ts<H, Ts3<H>>
typealias Ts5<H> = Ts<H, Ts4<H>>
typealias Ts6<H> = Ts<H, Ts5<H>>
typealias Ts7<H> = Ts<H, Ts6<H>>
typealias Ts8<H> = Ts<H, Ts7<H>>
typealias Ts9<H> = Ts<H, Ts8<H>>

// Array quotient
typealias TQ1<H, F> = Ts<H, F>
typealias TQ2<H, F> = Ts<H, TQ1<H, F>>
typealias TQ3<H, F> = Ts<H, TQ2<H, F>>
typealias TQ4<H, F> = Ts<H, TQ3<H, F>>
typealias TQ5<H, F> = Ts<H, TQ4<H, F>>
typealias TQ6<H, F> = Ts<H, TQ5<H, F>>
typealias TQ7<H, F> = Ts<H, TQ6<H, F>>
typealias TQ8<H, F> = Ts<H, TQ7<H, F>>
typealias TQ9<H, F> = Ts<H, TQ8<H, F>>

typealias TM1x1<H> = Ts1<Ts1<H>>
typealias TM1x2<H> = Ts1<Ts2<H>>
typealias TM2x1<H> = Ts2<Ts1<H>>
typealias TM2x2<H> = Ts2<Ts2<H>>
typealias TM3x1<H> = Ts3<Ts1<H>>
typealias TM1x3<H> = Ts1<Ts3<H>>
typealias TM3x2<H> = Ts3<Ts2<H>>
typealias TM2x3<H> = Ts2<Ts3<H>>
typealias TM3x3<H> = Ts3<Ts3<H>>

typealias TMRx2<H, R> = Ts<Ts3<H>, R>
typealias TMRx3<H, R> = Ts<Ts3<H>, R>
typealias TM2xC<H, C> = Ts2<TQ1<H, C>>
typealias TM3xC<H, C> = Ts3<TQ1<H, C>>
typealias TMat<E, R, C> = Ts<Ts<E, C>, R> // I think this is a bush?

fun <E> TV(v1: E): Ts1<E> = Ts(v1, null)
fun <E> TV(v1: E, v2: E): Ts2<E> = Ts(v1, Ts(v2, null))
fun <E> TV(v1: E, v2: E, v3: E): Ts3<E> = Ts(v1, Ts(v2, Ts(v3, null)))
fun <E> TV(v1: E, v2: E, v3: E, v4: E): Ts4<E> = Ts(v1, Ts(v2, Ts(v3, Ts(v4, null))))
fun <E> TV(v1: E, v2: E, v3: E, v4: E, v5: E): Ts5<E> = Ts(v1, Ts(v2, Ts(v3, Ts(v4, Ts(v5, null)))))
fun <E> TV(v1: E, v2: E, v3: E, v4: E, v5: E, v6: E): Ts6<E> = Ts(v1, Ts(v2, Ts(v3, Ts(v4, Ts(v5, Ts(v6, null))))))
fun <E> TV(v1: E, v2: E, v3: E, v4: E, v5: E, v6: E, v7: E): Ts7<E> = Ts(v1, Ts(v2, Ts(v3, Ts(v4, Ts(v5, Ts(v6, Ts(v7, null)))))))
fun <E> TV(v1: E, v2: E, v3: E, v4: E, v5: E, v6: E, v7: E, v8: E): Ts8<E> = Ts(v1, Ts(v2, Ts(v3, Ts(v4, Ts(v5, Ts(v6, Ts(v7, Ts(v8, null))))))))
fun <E> TV(v1: E, v2: E, v3: E, v4: E, v5: E, v6: E, v7: E, v8: E, v9: E): Ts9<E> = Ts(v1, Ts(v2, Ts(v3, Ts(v4, Ts(v5, Ts(v6, Ts(v7, Ts(v8, Ts(v9, null)))))))))

@JvmName("len1") fun <H> Ts1<H>.len(): L1 = S1
@JvmName("len2") fun <H> Ts2<H>.len(): L2 = S2
@JvmName("len3") fun <H> Ts3<H>.len(): L3 = S3
@JvmName("len4") fun <H> Ts4<H>.len(): L4 = S4
@JvmName("len5") fun <H> Ts5<H>.len(): L5 = S5

@JvmName("pget1") operator fun <E, Z: TQ1<E, Ts>, Ts> Z.get(i: L1): E = this[0]
@JvmName("pget2") operator fun <E, Z: TQ2<E, Ts>, Ts> Z.get(i: L2): E = this[1]
@JvmName("pget3") operator fun <E, Z: TQ3<E, Ts>, Ts> Z.get(i: L3): E = this[2]
@JvmName("pget4") operator fun <E, Z: TQ4<E, Ts>, Ts> Z.get(i: L4): E = this[3]

fun <E, Z: TQ1<E, Ts>, Ts> Z.take1(): Ts1<E> = Ts(head, null)
fun <E, Z: TQ2<E, Ts>, Ts> Z.take2(): Ts2<E> = Ts(head, tail!!.take1())
fun <E, Z: TQ3<E, Ts>, Ts> Z.take3(): Ts3<E> = Ts(head, tail!!.take2())
fun <E, Z: TQ4<E, Ts>, Ts> Z.take4(): Ts4<E> = Ts(head, tail!!.take3())

fun <E, Z: TQ2<E, Ts>, Ts> Z.drop1(): TQ1<E, Ts> = tail!!
fun <E, Z: TQ3<E, Ts>, Ts> Z.drop2(): TQ1<E, Ts> = drop1().drop1()
fun <E, Z: TQ4<E, Ts>, Ts> Z.drop3(): TQ1<E, Ts> = drop2().drop1()
fun <E, Z: TQ5<E, Ts>, Ts> Z.drop4(): TQ1<E, Ts> = drop2().drop2()

fun <E> TM2x1(t1: E, t2: E): Ts<Ts<E, Nothing>, Ts<Ts<E, Nothing>, Nothing>> = TV(TV(t1), TV(t2))
fun <E> TM1x2(t1: E, t2: E): Ts<Ts<E, Ts<E, Nothing>>, Nothing> = TV(TV(t1, t2))

operator fun <E, R, T> Ts<E, R>.plus(o: Ts<E, R>): Ts<E, R> = TODO()
operator fun <E, R, T> Ts<E, R>.minus(o: Ts<E, R>): Ts<E, R> = TODO()
// TODO: How do we express matrix multiplication? Not sure how to match the inner dimension...
//fun <E, R, T: TsQ3<E, C>, C, C1> TsQ3<R, T>.times(o: TsQ1<>): Ts<T