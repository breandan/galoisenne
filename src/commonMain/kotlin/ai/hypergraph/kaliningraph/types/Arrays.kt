package ai.hypergraph.kaliningraph.types

import ai.hypergraph.kaliningraph.*
import kotlin.jvm.JvmName

// Multi-typed arrays
data class Y1<A>(val e1: A)
data class Y2<A, B>(val e1: A, val e2: B)
data class Y3<A, B, C>(val e1: A, val e2: B, val e3: C)
data class Y4<A, B, C, D>(val e1: A, val e2: B, val e3: C, val e4: D)

open class Vec<E, L: S<*>> internal constructor(val a: List<E>): List<E> by a {
  internal constructor(vararg es: E): this(es.toList())

  operator fun get(intRange: IntRange): List<E> = subList(intRange.first, intRange.last)
}

/** TODO: Unify this representation with [ai.hypergraph.kaliningraph.tensor.Matrix] */
typealias Mat<E, R, C> = Vec<Vec<E, C>, R>

fun <E> Vec(v1: E) = V1(v1)
fun <E> Vec(v1: E, v2: E) = V2(v1, v2)
fun <E> Vec(v1: E, v2: E, v3: E) = V3(v1, v2, v3)
fun <E> Vec(v1: E, v2: E, v3: E, v4: E) = V4(v1, v2, v3, v4)
fun <E> Vec(v1: E, v2: E, v3: E, v4: E, v5: E) = V5(v1, v2, v3, v4, v5)
fun <E> Vec(v1: E, v2: E, v3: E, v4: E, v5: E, v6: E) = V6(v1, v2, v3, v4, v5, v6)
fun <E> Vec(v1: E, v2: E, v3: E, v4: E, v5: E, v6: E, v7: E) = V7(v1, v2, v3, v4, v5, v6, v7)
fun <E> Vec(v1: E, v2: E, v3: E, v4: E, v5: E, v6: E, v7: E, v8: E) = V8(v1, v2, v3, v4, v5, v6, v7, v8)
fun <E> Vec(v1: E, v2: E, v3: E, v4: E, v5: E, v6: E, v7: E, v8: E, v9: E) = V9(v1, v2, v3, v4, v5, v6, v7, v8, v9)

typealias V1<E> = Vec<E, L1>
typealias V2<E> = Vec<E, L2>
typealias V3<E> = Vec<E, L3>
typealias V4<E> = Vec<E, L4>
typealias V5<E> = Vec<E, L5>
typealias V6<E> = Vec<E, L6>
typealias V7<E> = Vec<E, L7>
typealias V8<E> = Vec<E, L8>
typealias V9<E> = Vec<E, L9>

fun <E, D1: S<*>, D2: S<*>> List<E>.chunked(d1: D1): List<Vec<E, D2>> = chunked(d1.toInt()).map { Vec(it) }

inline fun <reified R: S<*>> asInt() = R::class.simpleName!!.drop(1).toInt()
fun <E, R: S<*>, C: S<*>> Mat(r: R, c: C, vararg es: E): Mat<E, R, C> =
  Mat(es.toList().chunked(r.toInt()).map { Vec(it) })
fun <E, R: S<*>, C: S<*>> Mat(r: R, c: C, es: List<E>): Mat<E, R, C> =
  Mat(es.chunked(r.toInt()).map { Vec(it) })
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
@JvmName("get3") operator fun <R, L : Q3<R>, E> Vec<E, L>.get(i: L3) = a[3]
@JvmName("get4") operator fun <R, L : Q4<R>, E> Vec<E, L>.get(i: L4) = a[4]

operator fun <T> Array<T>.get(range: IntRange) = sliceArray(range)

fun <E, Z: Q1<P>, P> Vec<E, Z>.take1() = Vec<E, L1>(this[S0..S1])
fun <E, Z: Q2<P>, P> Vec<E, Z>.take2() = Vec<E, L2>(this[S0..S2])
fun <E, Z: Q3<P>, P> Vec<E, Z>.take3() = Vec<E, L3>(this[S0..S3])
fun <E, Z: Q4<P>, P> Vec<E, Z>.take4() = Vec<E, L3>(this[S0..S4])

operator fun <A, B> S<A>.rangeTo(x: S<B>) = toInt()..x.toInt()

// ============================= Naperian Functors ==============================

// https://www.cs.ox.ac.uk/people/jeremy.gibbons/publications/aplicative.pdf
// https://github.com/NickHu/naperian-functors/blob/master/src/Data/Naperian.hs
// https://github.com/NickHu/naperian-functors/blob/master/src/Data/Naperian/Vector.hs
// https://github.com/NickHu/naperian-functors/blob/master/src/Data/Naperian/Symbolic.hs
// "The main idea is that a rank-n array is essentially a data structure of type
// D₁(D₂(...(Dₙ a))), where each Dᵢ is a dimension : a container type, categorically
// a functor; one might think in the first instance of lists."

// This gives us something like a Church encoded list
class P<E, T/*: P<E, T> /*Recursive type will blow up the compiler*/*/>
(val head: E, val tail: T? = null) : S<T>(tail) {
  operator fun get(i: Int): E =
    if (i == 0) head else if (tail is P<*, *>) tail[i - 1] as E else throw IndexOutOfBoundsException()
  operator fun get(s: S<*>): E = get(s.toInt() - 1)
  fun size(): Int = if (tail == null) 1 else if (tail is P<*, *>) 1 + tail.size() else 1
  /** TODO: Maybe possible to make bidirectional, see [ai.hypergraph.experimental.DLL] */
}

// Product
typealias P1<E> = P<E, Nothing>
typealias P2<E> = P<E, P1<E>>
typealias P3<E> = P<E, P2<E>>
typealias P4<E> = P<E, P3<E>>
typealias P5<E> = P<E, P4<E>>
typealias P6<E> = P<E, P5<E>>
typealias P7<E> = P<E, P6<E>>
typealias P8<E> = P<E, P7<E>>
typealias P9<E> = P<E, P8<E>>

// Array quotient
typealias PQ1<E, F> = P<E, F>
typealias PQ2<E, F> = P<E, PQ1<E, F>>
typealias PQ3<E, F> = P<E, PQ2<E, F>>
typealias PQ4<E, F> = P<E, PQ3<E, F>>
typealias PQ5<E, F> = P<E, PQ4<E, F>>
typealias PQ6<E, F> = P<E, PQ5<E, F>>
typealias PQ7<E, F> = P<E, PQ6<E, F>>
typealias PQ8<E, F> = P<E, PQ7<E, F>>
typealias PQ9<E, F> = P<E, PQ8<E, F>>

typealias PM1x1<E> = P1<P1<E>>
typealias PM1x2<E> = P1<P2<E>>
typealias PM2x1<E> = P2<P1<E>>
typealias PM2x2<E> = P2<P2<E>>
typealias PM3x1<E> = P3<P1<E>>
typealias PM1x3<E> = P1<P3<E>>
typealias PM3x2<E> = P3<P2<E>>
typealias PM2x3<E> = P2<P3<E>>
typealias PM3x3<E> = P3<P3<E>>

typealias PMRx2<E, R> = P<P3<E>, R>
typealias PMRx3<E, R> = P<P3<E>, R>
typealias PM2xC<E, C> = P2<PQ1<E, C>>
typealias PM3xC<E, C> = P3<PQ1<E, C>>
typealias PMat<E, R, C> = P<P<E, C>, R> // I think this is a bush?

fun <E> PV(v1: E): P1<E> = P(v1, null)
fun <E> PV(v1: E, v2: E): P2<E> = P(v1, P(v2, null))
fun <E> PV(v1: E, v2: E, v3: E): P3<E> = P(v1, P(v2, P(v3, null)))
fun <E> PV(v1: E, v2: E, v3: E, v4: E): P4<E> = P(v1, P(v2, P(v3, P(v4, null))))
fun <E> PV(v1: E, v2: E, v3: E, v4: E, v5: E): P5<E> = P(v1, P(v2, P(v3, P(v4, P(v5, null)))))
fun <E> PV(v1: E, v2: E, v3: E, v4: E, v5: E, v6: E): P6<E> = P(v1, P(v2, P(v3, P(v4, P(v5, P(v6, null))))))
fun <E> PV(v1: E, v2: E, v3: E, v4: E, v5: E, v6: E, v7: E): P7<E> = P(v1, P(v2, P(v3, P(v4, P(v5, P(v6, P(v7, null)))))))
fun <E> PV(v1: E, v2: E, v3: E, v4: E, v5: E, v6: E, v7: E, v8: E): P8<E> = P(v1, P(v2, P(v3, P(v4, P(v5, P(v6, P(v7, P(v8, null))))))))
fun <E> PV(v1: E, v2: E, v3: E, v4: E, v5: E, v6: E, v7: E, v8: E, v9: E): P9<E> = P(v1, P(v2, P(v3, P(v4, P(v5, P(v6, P(v7, P(v8, P(v9, null)))))))))


fun <E, Z: PQ1<E, P>, P> Z.take1(): P1<E> = P(head, null)
fun <E, Z: PQ2<E, P>, P> Z.take2(): P2<E> = P(head, tail!!.take1())
fun <E, Z: PQ3<E, P>, P> Z.take3(): P3<E> = P(head, tail!!.take2())
fun <E, Z: PQ4<E, P>, P> Z.take4(): P4<E> = P(head, tail!!.take3())

fun <E> PMat2x1(t1: E, t2: E): P<P<E, Nothing>, P<P<E, Nothing>, Nothing>> = PV(PV(t1), PV(t2))
fun <E> PMat1x2(t1: E, t2: E): P<P<E, P<E, Nothing>>, Nothing> = PV(PV(t1, t2))

// TODO: How do we express matrix multiplication? Not sure how to match the inner dimension...