package ai.hypergraph.kaliningraph.types

import kotlin.jvm.JvmName

// Multi-typed arrays
data class Y1<A>(val e1: A)
data class Y2<A, B>(val e1: A, val e2: B)
data class Y3<A, B, C>(val e1: A, val e2: B, val e3: C)
data class Y4<A, B, C, D>(val e1: A, val e2: B, val e3: C, val e4: D)

typealias U1<A> = Y1<A>
typealias U2<A> = Y2<A, A>
typealias U3<A> = Y3<A, A, A>
typealias U4<A> = Y4<A, A, A, A>

fun <A> U(a1: A, a2: A, a3: A): Y3<A, A, A> = U3(a1, a2, a3)

open class Vec<E, L: S<*>> internal constructor(val a: List<E>): List<E> by a {
  internal constructor(vararg es: E): this(es.toList())

  operator fun get(intRange: IntRange): List<E> = subList(intRange.first, intRange.last)
}

typealias V1<E> = Vec<E, L1>
typealias V2<E> = Vec<E, L2>
typealias V3<E> = Vec<E, L3>
typealias V4<E> = Vec<E, L4>
typealias V5<E> = Vec<E, L5>
typealias V6<E> = Vec<E, L6>
typealias V7<E> = Vec<E, L7>
typealias V8<E> = Vec<E, L8>
typealias V9<E> = Vec<E, L9>

typealias Mat<E, R, C> = Vec<Vec<E, C>, R>
typealias M1x1<E> = Mat<E, L1, L1>
typealias M1x2<E> = Mat<E, L1, L2>
typealias M2x1<E> = Mat<E, L2, L1>
typealias M1x3<E> = Mat<E, L1, L3>
typealias M3x1<E> = Mat<E, L3, L1>
typealias M3x2<E> = Mat<E, L3, L2>
typealias M2x3<E> = Mat<E, L2, L3>
typealias M3x3<E> = Mat<E, L3, L3>

fun <E> Vec(v1: E) = V1(v1)
fun <E> Vec(v1: E, v2: E) = V2(v1, v2)
fun <E> Vec(v1: E, v2: E, v3: E) = V3(v1, v2, v3)
fun <E> Vec(v1: E, v2: E, v3: E, v4: E) = V4(v1, v2, v3, v4)
fun <E> Vec(v1: E, v2: E, v3: E, v4: E, v5: E) = V5(v1, v2, v3, v4, v5)
fun <E> Vec(v1: E, v2: E, v3: E, v4: E, v5: E, v6: E) = V6(v1, v2, v3, v4, v5, v6)
fun <E> Vec(v1: E, v2: E, v3: E, v4: E, v5: E, v6: E, v7: E) = V7(v1, v2, v3, v4, v5, v6, v7)
fun <E> Vec(v1: E, v2: E, v3: E, v4: E, v5: E, v6: E, v7: E, v8: E) = V8(v1, v2, v3, v4, v5, v6, v7, v8)
fun <E> Vec(v1: E, v2: E, v3: E, v4: E, v5: E, v6: E, v7: E, v8: E, v9: E) = V9(v1, v2, v3, v4, v5, v6, v7, v8, v9)

class MatProto<E, D1: S<*>, D2: S<*>>(d1: D1, d2: D2, vararg es: E): Mat<E, D1, D2>(es.toList().chunked(d1, d2))

fun <E, D1: S<*>, D2: S<*>> List<E>.chunked(d1: D1, d2: D2): List<Vec<E, D2>> = chunked(d1.toInt()).map { Vec(it) }

fun <E, D1: S<*>, D2: S<*>> Mat(d1: D1, d2: D2) = Vec<Vec<E, D2>, D1>()
fun <E> Mat2x1(t1: E, t2: E): M2x1<E> = MatProto(S2, S1, t1, t2)
fun <E> Mat1x2(t1: E, t2: E): M1x2<E> = MatProto(S1, S2, t1, t2)
//...

// Matmul
operator fun <E, R: S<*>, C1: S<*>, C2: S<*>> Vec<Vec<E, C1>, R>.times(that: Vec<Vec<E, C2>, C1>) = Mat<E, R, C2>()

@JvmName("get1") operator fun <R, L : Q1<R>, E> Vec<E, L>.get(i: L1) = a[0]
@JvmName("get2") operator fun <R, L : Q2<R>, E> Vec<E, L>.get(i: L2) = a[1]
@JvmName("get3") operator fun <R, L : Q3<R>, E> Vec<E, L>.get(i: L3) = a[3]
@JvmName("get4") operator fun <R, L : Q4<R>, E> Vec<E, L>.get(i: L4) = a[4]

operator fun <T> Array<T>.get(range: IntRange) = sliceArray(range)

private inline fun <E, L : S<*>, reified F : Q1<P>, P, T : Q1<P>, R: S<*>> Vec<E, L>.take(from: F = S0 as F, to: T) = Vec<E, R>(this[from..to])
fun <E, Z: Q1<P>, P> Vec<E, Z>.take1() = Vec<E, L1>(this[S0..S1])
fun <E, Z: Q2<P>, P> Vec<E, Z>.take2() = Vec<E, L2>(this[S0..S2])
fun <E, Z: Q3<P>, P> Vec<E, Z>.take3() = Vec<E, L3>(this[S0..S3])
fun <E, Z: Q4<P>, P> Vec<E, Z>.take4() = Vec<E, L3>(this[S0..S4])

operator fun <A, B> S<A>.rangeTo(x: S<B>) = toInt()..x.toInt()