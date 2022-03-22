@file:Suppress("ClassName", "NonAsciiCharacters")

package ai.hypergraph.kaliningraph.types

import ai.hypergraph.kaliningraph.allPairs
import kotlin.jvm.JvmName

// Multi-typed arrays
data class Π1<A>(val π1: A)/*: V1<A> by VT(π1)*/
data class Π2<A, B>(val π1: A, val π2: B) {
  val first = π1
  val second = π2
}
data class Π3<A, B, C>(val π1: A, val π2: B, val π3: C) {
  val first = π1
  val second = π2
  val third = π3
}
data class Π4<A, B, C, D>(val π1: A, val π2: B, val π3: C, val π4: D)

fun <A: T, B: T, T> Π2<A, B>.toVT(): V2<T> = VT(π1, π2)
fun <A: T, B: T, C: T, T> Π3<A, B, C>.toVT(): V3<T> = VT(π1, π2, π3)
fun <A: T, B: T, C: T, D: T, T> Π4<A, B, C, D>.toVT(): V4<T> = VT(π1, π2, π3, π4)

fun <A, B> List<Π2<A, B>>.toMap() = associate { it.π1 to it.π2 }
@JvmName("unzipSequence") fun <A, B> Sequence<Π2<A, B>>.unzip() =
  map { it.π1 to it.π2 }.unzip().let { (a, b) -> a pp b }
@JvmName("unzipList") fun <A, B> List<Π2<A, B>>.unzip() =
  map { it.π1 to it.π2 }.unzip()
fun <A> List<V2<A>>.unzip() = map { it[S1] to it[S2] }.unzip()

fun <A, B> Π(π1: A, π2: B) = Π2(π1, π2)
fun <A, B, C> Π(π1: A, π2: B, π3: C) = Π3(π1, π2, π3)
fun <A, B, C, D> Π(π1: A, π2: B, π3: C, π4: D) = Π4(π1, π2, π3, π4)

infix fun <A, Z> A.pp(that: Z) = Π(this, that)
infix fun <A, B, Z> Π2<A, B>.pp(that: Z) = Π(π1, π2, that)
infix fun <A, B, C, Z> Π3<A, B, C>.pp(that: Z) = Π(π1, π2, π3, that)

operator fun <A, Z> Set<A>.times(s: Set<Z>): Set<Π2<A, Z>> =
  flatMap { l -> s.map { r -> Π(l, r) }.toSet() }.toSet()

@JvmName("cartProdPair") operator fun <E: Π2<A, B>, A, B, Z> Set<E>.times(s: Set<Z>): Set<Π3<A, B, Z>> =
  flatMap { l -> s.map { r -> Π(l.π1, l.π2, r) }.toSet() }.toSet()

@JvmName("cartProdPairPair") operator fun <E: Π2<A, B>, A, B, U: Π2<C, D>, C, D> Set<E>.times(s: Set<U>): Set<Π2<E, U>> =
  flatMap { l -> s.map { r -> Π(l, r) }.toSet() }.toSet()

@JvmName("cartProdTriple") operator fun <E: Π3<A, B, C>, A, B, C, Z> Set<E>.times(s: Set<Z>): Set<Π4<A, B, C, Z>> =
  flatMap { l -> s.map { r -> Π(l.π1, l.π2, l.π3, r) }.toSet() }.toSet()

interface VT<E, L: S<*>> : List<E> {
  open val len: L
  open val l: List<E>
  fun <A: S<*>, B: S<*>> fetch(intRange: Pair<A, B>): List<E> = subList(intRange.first.toInt(), intRange.second.toInt())

  class of<E, L: S<*>>(override val len: L, override val l: List<E>): VT<E, L>, List<E> by l {
    internal constructor(l: L, vararg es: E): this(l, es.toList())
  }
}

infix fun <T> T.cc(that: T) = VT(this, that)

fun <T1: VT<E, L>, E, L: S<*>> T1.append(that: E): VT<E, Q1<L>> = VT.of(this.len + S1, this.l + listOf(that))
fun <T1: VT<E, L>, E, L: S<*>> T1.prepend(that: E): VT<E, Q1<L>> = VT.of(this.len + S1, listOf(that) + this.l)

@JvmName("cc2") infix fun <T1: VT<E, L>, T2: VT<E, L1>, E, L: S<*>> T1.cc(that: T2): VT<E, Q1<L>> = VT.of(this.len + that.len, this.l + that.l)
@JvmName("cc3") infix fun <T1: VT<E, L>, T2: VT<E, L2>, E, L: S<*>> T1.cc(that: T2): VT<E, Q2<L>> = VT.of(this.len + that.len, this.l + that.l)
@JvmName("cc4") infix fun <T1: VT<E, L>, T2: VT<E, L3>, E, L: S<*>> T1.cc(that: T2): VT<E, Q3<L>> = VT.of(this.len + that.len, this.l + that.l)
@JvmName("cc5") infix fun <T1: VT<E, L>, T2: VT<E, L4>, E, L: S<*>> T1.cc(that: T2): VT<E, Q4<L>> = VT.of(this.len + that.len, this.l + that.l)
@JvmName("cc6") infix fun <T1: VT<E, L>, T2: VT<E, L5>, E, L: S<*>> T1.cc(that: T2): VT<E, Q5<L>> = VT.of(this.len + that.len, this.l + that.l)

fun <E> VT(v1: E): VT<E, L1> = VT.of(S1, v1)
fun <E> VT(v1: E, v2: E): VT<E, L2> = VT.of(S2, v1, v2)
fun <E> VT(v1: E, v2: E, v3: E): VT<E, L3> = VT.of(S3, v1, v2, v3)
fun <E> VT(v1: E, v2: E, v3: E, v4: E): VT<E, L4> = VT.of(S4, v1, v2, v3, v4)
fun <E> VT(v1: E, v2: E, v3: E, v4: E, v5: E): VT<E, L5> = VT.of(S5, v1, v2, v3, v4, v5)
fun <E> VT(v1: E, v2: E, v3: E, v4: E, v5: E, v6: E): VT<E, L6> = VT.of(S6, v1, v2, v3, v4, v5, v6)
fun <E> VT(v1: E, v2: E, v3: E, v4: E, v5: E, v6: E, v7: E): VT<E, L7> = VT.of(S7, v1, v2, v3, v4, v5, v6, v7)
fun <E> VT(v1: E, v2: E, v3: E, v4: E, v5: E, v6: E, v7: E, v8: E): VT<E, L8> = VT.of(S8, v1, v2, v3, v4, v5, v6, v7, v8)
fun <E> VT(v1: E, v2: E, v3: E, v4: E, v5: E, v6: E, v7: E, v8: E, v9: E): VT<E, L9> = VT.of(S9, v1, v2, v3, v4, v5, v6, v7, v8, v9)

typealias V1<E> = VT<E, L1>
typealias V2<E> = VT<E, L2>
typealias V3<E> = VT<E, L3>
typealias V4<E> = VT<E, L4>
typealias V5<E> = VT<E, L5>
typealias V6<E> = VT<E, L6>
typealias V7<E> = VT<E, L7>
typealias V8<E> = VT<E, L8>
typealias V9<E> = VT<E, L9>

fun <E, D1: S<*>, D2: S<*>> List<E>.chunked(d1: D1, d2: D2): List<VT<E, D2>> = chunked(d1.toInt()).map { VT.of(d2, it) }

/** TODO: Unify this representation with [ai.hypergraph.kaliningraph.tensor.Matrix] */
typealias Mat<E, R, C> = VT<VT<E, C>, R>

inline fun <reified R: S<*>> asInt() = R::class.simpleName!!.drop(1).toInt()
fun <E, R: S<*>, C: S<*>> Mat(r: R, c: C, vararg es: E): Mat<E, R, C> = Mat(r, c, es.toList())
fun <E, R: S<*>, C: S<*>> Mat(r: R, c: C, es: List<E>): Mat<E, R, C> = VT.of(r, es.chunked(r, c))
fun <E, R: S<*>, C: S<*>> Mat(r: R, c: C, f: (Int, Int) -> E): Mat<E, R, C> =
  Mat(r, c, allPairs(r.toInt(), c.toInt()).map { (r, c) -> f(r, c) })

fun <E> Mat2x1(t1: E, t2: E): Mat<E, L2, L1> = Mat(S2, S1, t1, t2)
fun <E> Mat1x2(t1: E, t2: E): Mat<E, L1, L2> = Mat(S1, S2, t1, t2)
//...Optional pseudoconstructors

operator fun <E, R: S<*>, C1: S<*>, C2: S<*>> Mat<E, R, C1>.times(that: Mat<E, C1, C2>): Mat<E, R, C2> = TODO()
operator fun <E, R: S<*>, C: S<*>> Mat<E, R, C>.get(r: Int, c: Int): E = l[r][c]
//fun <E, R: S<*>, C: S<*>> Mat<E, R, C>.transpose(): Mat<E, C, R> = Mat { r, c -> this[c][r]}

@JvmName("get1") operator fun <R, L : Q1<R>, E> VT<E, L>.get(i: L1) = l[0]
@JvmName("get2") operator fun <R, L : Q2<R>, E> VT<E, L>.get(i: L2) = l[1]
@JvmName("get3") operator fun <R, L : Q3<R>, E> VT<E, L>.get(i: L3) = l[2]
@JvmName("get4") operator fun <R, L : Q4<R>, E> VT<E, L>.get(i: L4) = l[3]
@JvmName("get5") operator fun <R, L : Q5<R>, E> VT<E, L>.get(i: L5) = l[4]
@JvmName("get6") operator fun <R, L : Q6<R>, E> VT<E, L>.get(i: L6) = l[5]
@JvmName("get7") operator fun <R, L : Q7<R>, E> VT<E, L>.get(i: L7) = l[6]
@JvmName("get8") operator fun <R, L : Q8<R>, E> VT<E, L>.get(i: L8) = l[7]
@JvmName("get9") operator fun <R, L : Q9<R>, E> VT<E, L>.get(i: L9) = l[8]

val <R, L : Q1<R>, E> VT<E, L>.first: E get() = component1()
val <R, L : Q2<R>, E> VT<E, L>.second: E get() = component2()
val <R, L : Q3<R>, E> VT<E, L>.third: E get() = component3()

operator fun <T> Array<T>.get(range: IntRange) = sliceArray(range)

fun <E, Z : Q1<P>, P> VT<E, Z>.take1(): VT<E, L1> = VT.of(S1, fetch(S0..S1))
fun <E, Z : Q2<P>, P> VT<E, Z>.take2(): VT<E, L2> = VT.of(S2, fetch(S0..S2))
fun <E, Z : Q3<P>, P> VT<E, Z>.take3(): VT<E, L3> = VT.of(S3, fetch(S0..S3))
fun <E, Z : Q4<P>, P> VT<E, Z>.take4(): VT<E, L4> = VT.of(S4, fetch(S0..S4))

fun <E, Z : Q2<P>, P> VT<E, Z>.drop1(): VT<E, S<P>> = VT.of(len - S1, fetch(S1..len))
fun <E, Z : Q3<P>, P> VT<E, Z>.drop2(): VT<E, S<P>> = VT.of(len - S2, fetch(S2..len))
fun <E, Z : Q4<P>, P> VT<E, Z>.drop3(): VT<E, S<P>> = VT.of(len - S3, fetch(S3..len))
fun <E, Z : Q5<P>, P> VT<E, Z>.drop4(): VT<E, S<P>> = VT.of(len - S4, fetch(S4..len))

//                              ┌────j────┐    ┌────k────┐    where j, j are the relative offsets Y - X, Z - Y respectively
// Encodes the constraint:  P < X    <    Y && Y    <    Z    where X, Y are the start and end of range in a vector of length Z
@JvmName("sv121") operator fun <E, P, X: Q1<P>, Y: Q2<X>, Z : Q1<Y>> VT<E, Z>.get(r: Pair<X, Y>): VT<E, L2> = VT.of(S2, fetch(r))
@JvmName("sv122") operator fun <E, P, X: Q1<P>, Y: Q2<X>, Z : Q2<Y>> VT<E, Z>.get(r: Pair<X, Y>): VT<E, L2> = VT.of(S2, fetch(r))
@JvmName("sv221") operator fun <E, P, X: Q2<P>, Y: Q2<X>, Z : Q1<Y>> VT<E, Z>.get(r: Pair<X, Y>): VT<E, L2> = VT.of(S2, fetch(r))
@JvmName("sv222") operator fun <E, P, X: Q2<P>, Y: Q2<X>, Z : Q2<Y>> VT<E, Z>.get(r: Pair<X, Y>): VT<E, L2> = VT.of(S2, fetch(r))

operator fun <A, B> S<A>.rangeTo(that: S<B>) = this to that

// ============================= Naperian Functors ==============================

// https://www.cs.ox.ac.uk/people/jeremy.gibbons/publications/aplicative.pdf
// https://github.com/NickHu/naperian-functors/blob/master/src/Data/Naperian.hs
// https://github.com/NickHu/naperian-functors/blob/master/src/Data/Naperian/Vector.hs
// https://github.com/NickHu/naperian-functors/blob/master/src/Data/Naperian/Symbolic.hs
// "The main idea is that a rank-n array is essentially a data structure of type
// D₁(D₂(...(Dₙ a))), where each Dᵢ is a dimension : a container type, categorically
// a functor; one might think in the first instance of lists."

// This gives us something like a Church-encoded list
// Using a recursive type bound T: TS<H, T> will blow up the compiler
class TS<H, T>(val head: H, val tail: T? = null) {
  operator fun get(i: Int): H =
    if (i == 0) head else if (tail is TS<*, *>) tail[i - 1] as H else throw IndexOutOfBoundsException()
  fun size(): Int = if (tail == null) 1 else if (tail is TS<*, *>) 1 + tail.size() else 1
  /** TODO: Maybe possible to make bidirectional, see [ai.hypergraph.experimental.DLL] */
}

// Product
typealias TS1<H> = TS<H, Nothing>
typealias TS2<H> = TS<H, TS1<H>>
typealias TS3<H> = TS<H, TS2<H>>
typealias TS4<H> = TS<H, TS3<H>>
typealias TS5<H> = TS<H, TS4<H>>
typealias TS6<H> = TS<H, TS5<H>>
typealias TS7<H> = TS<H, TS6<H>>
typealias TS8<H> = TS<H, TS7<H>>
typealias TS9<H> = TS<H, TS8<H>>

// Array quotient
typealias TQ1<H, F> = TS<H, F>
typealias TQ2<H, F> = TS<H, TQ1<H, F>>
typealias TQ3<H, F> = TS<H, TQ2<H, F>>
typealias TQ4<H, F> = TS<H, TQ3<H, F>>
typealias TQ5<H, F> = TS<H, TQ4<H, F>>
typealias TQ6<H, F> = TS<H, TQ5<H, F>>
typealias TQ7<H, F> = TS<H, TQ6<H, F>>
typealias TQ8<H, F> = TS<H, TQ7<H, F>>
typealias TQ9<H, F> = TS<H, TQ8<H, F>>

typealias TM1x1<H> = TS1<TS1<H>>
typealias TM1x2<H> = TS1<TS2<H>>
typealias TM2x1<H> = TS2<TS1<H>>
typealias TM2x2<H> = TS2<TS2<H>>
typealias TM3x1<H> = TS3<TS1<H>>
typealias TM1x3<H> = TS1<TS3<H>>
typealias TM3x2<H> = TS3<TS2<H>>
typealias TM2x3<H> = TS2<TS3<H>>
typealias TM3x3<H> = TS3<TS3<H>>

typealias TMRx2<H, R> = TS<TS3<H>, R>
typealias TMRx3<H, R> = TS<TS3<H>, R>
typealias TM2xC<H, C> = TS2<TQ1<H, C>>
typealias TM3xC<H, C> = TS3<TQ1<H, C>>
typealias TMat<E, R, C> = TS<TS<E, C>, R> // I think this is a bush?

fun <E> TV(v1: E): TS1<E> = TS(v1, null)
fun <E> TV(v1: E, v2: E): TS2<E> = TS(v1, TS(v2, null))
fun <E> TV(v1: E, v2: E, v3: E): TS3<E> = TS(v1, TS(v2, TS(v3, null)))
fun <E> TV(v1: E, v2: E, v3: E, v4: E): TS4<E> = TS(v1, TS(v2, TS(v3, TS(v4, null))))
fun <E> TV(v1: E, v2: E, v3: E, v4: E, v5: E): TS5<E> = TS(v1, TS(v2, TS(v3, TS(v4, TS(v5, null)))))
fun <E> TV(v1: E, v2: E, v3: E, v4: E, v5: E, v6: E): TS6<E> = TS(v1, TS(v2, TS(v3, TS(v4, TS(v5, TS(v6, null))))))
fun <E> TV(v1: E, v2: E, v3: E, v4: E, v5: E, v6: E, v7: E): TS7<E> = TS(v1, TS(v2, TS(v3, TS(v4, TS(v5, TS(v6, TS(v7, null)))))))
fun <E> TV(v1: E, v2: E, v3: E, v4: E, v5: E, v6: E, v7: E, v8: E): TS8<E> = TS(v1, TS(v2, TS(v3, TS(v4, TS(v5, TS(v6, TS(v7, TS(v8, null))))))))
fun <E> TV(v1: E, v2: E, v3: E, v4: E, v5: E, v6: E, v7: E, v8: E, v9: E): TS9<E> = TS(v1, TS(v2, TS(v3, TS(v4, TS(v5, TS(v6, TS(v7, TS(v8, TS(v9, null)))))))))

@JvmName("len1") fun <H> TS1<H>.len(): L1 = S1
@JvmName("len2") fun <H> TS2<H>.len(): L2 = S2
@JvmName("len3") fun <H> TS3<H>.len(): L3 = S3
@JvmName("len4") fun <H> TS4<H>.len(): L4 = S4
@JvmName("len5") fun <H> TS5<H>.len(): L5 = S5

@JvmName("pget1") operator fun <E, Z: TQ1<E, TS>, TS> Z.get(i: L1): E = this[0]
@JvmName("pget2") operator fun <E, Z: TQ2<E, TS>, TS> Z.get(i: L2): E = this[1]
@JvmName("pget3") operator fun <E, Z: TQ3<E, TS>, TS> Z.get(i: L3): E = this[2]
@JvmName("pget4") operator fun <E, Z: TQ4<E, TS>, TS> Z.get(i: L4): E = this[3]

fun <E, Z: TQ1<E, TS>, TS> Z.take1(): TS1<E> = TS(head, null)
fun <E, Z: TQ2<E, TS>, TS> Z.take2(): TS2<E> = TS(head, tail!!.take1())
fun <E, Z: TQ3<E, TS>, TS> Z.take3(): TS3<E> = TS(head, tail!!.take2())
fun <E, Z: TQ4<E, TS>, TS> Z.take4(): TS4<E> = TS(head, tail!!.take3())

fun <E, Z: TQ2<E, TS>, TS> Z.drop1(): TQ1<E, TS> = tail!!
fun <E, Z: TQ3<E, TS>, TS> Z.drop2(): TQ1<E, TS> = drop1().drop1()
fun <E, Z: TQ4<E, TS>, TS> Z.drop3(): TQ1<E, TS> = drop2().drop1()
fun <E, Z: TQ5<E, TS>, TS> Z.drop4(): TQ1<E, TS> = drop2().drop2()

fun <E> TM2x1(t1: E, t2: E): TS<TS<E, Nothing>, TS<TS<E, Nothing>, Nothing>> = TV(TV(t1), TV(t2))
fun <E> TM1x2(t1: E, t2: E): TS<TS<E, TS<E, Nothing>>, Nothing> = TV(TV(t1, t2))

operator fun <E, R> TS<E, R>.plus(o: TS<E, R>): TS<E, R> = TODO()
operator fun <E, R> TS<E, R>.minus(o: TS<E, R>): TS<E, R> = TODO()
// TODO: How do we express matrix multiplication? Not sure how to match the inner dimension...
//fun <E, R, T: TSQ3<E, C>, C, C1> TSQ3<R, T>.times(o: TSQ1<>): TS<T