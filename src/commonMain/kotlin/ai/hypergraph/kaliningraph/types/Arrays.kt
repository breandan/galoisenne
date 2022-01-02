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

fun <A> U(a1: A, a2: A, a3: A) = U3(a1, a2, a3)

class V<E, L: S<*>> internal constructor(vararg val a: E)

typealias V1<E> = V<E, L1>
typealias V2<E> = V<E, L2>
typealias V3<E> = V<E, L3>
typealias V4<E> = V<E, L4>
typealias V5<E> = V<E, L5>
typealias V6<E> = V<E, L6>
typealias V7<E> = V<E, L7>
typealias V8<E> = V<E, L8>
typealias V9<E> = V<E, L9>

typealias M<E, R, C> = V<V<E, C>, R>
typealias M1x1<E> = M<E, L1, L1>
typealias M1x2<E> = M<E, L1, L2>
typealias M2x1<E> = M<E, L2, L1>
typealias M1x3<E> = M<E, L1, L3>
typealias M3x1<E> = M<E, L3, L1>
typealias M3x2<E> = M<E, L3, L2>
typealias M2x3<E> = M<E, L2, L3>
typealias M3x3<E> = M<E, L3, L3>

fun <E> V(v1: E) = V1(v1)
fun <E> V(v1: E, v2: E) = V2(v1, v2)
fun <E> V(v1: E, v2: E, v3: E) = V3(v1, v2, v3)
fun <E> V(v1: E, v2: E, v3: E, v4: E) = V4(v1, v2, v3, v4)
fun <E> V(v1: E, v2: E, v3: E, v4: E, v5: E) = V5(v1, v2, v3, v4, v5)
fun <E> V(v1: E, v2: E, v3: E, v4: E, v5: E, v6: E) = V6(v1, v2, v3, v4, v5, v6)
fun <E> V(v1: E, v2: E, v3: E, v4: E, v5: E, v6: E, v7: E) = V7(v1, v2, v3, v4, v5, v6, v7)
fun <E> V(v1: E, v2: E, v3: E, v4: E, v5: E, v6: E, v7: E, v8: E) = V8(v1, v2, v3, v4, v5, v6, v7, v8)
fun <E> V(v1: E, v2: E, v3: E, v4: E, v5: E, v6: E, v7: E, v8: E, v9: E) = V9(v1, v2, v3, v4, v5, v6, v7, v8, v9)

fun <E> M2x1(t1: E, t2: E): M2x1<E> = V(V(t1), V(t2))
fun <E> M1x2(t1: E, t2: E): M1x2<E> = V(V(t1, t2))
val tq = M1x2("", "")
//...

// Matmul
operator fun <E, R: S<*>, C1: S<*>, C2: S<*>> V<V<E, C1>, R>.times(that: V<V<E, C2>, C1>) = M<E, R, C2>()

@JvmName("get1") operator fun <R, L : Q1<R>, E> V<E, L>.get(i1: L0) = a[0]
@JvmName("get2") operator fun <R, L : Q2<R>, E> V<E, L>.get(i1: L1) = a[1]
@JvmName("get3") operator fun <R, L : Q3<R>, E> V<E, L>.get(i1: L3) = a[3]
@JvmName("get4") operator fun <R, L : Q4<R>, E> V<E, L>.get(i1: L4) = a[4]

operator fun <T> Array<T>.get(range: IntRange) = sliceArray(range)

private inline fun <E, L : S<*>, reified F : Q1<P>, P, T : Q1<P>, R: S<*>> V<E, L>.take(from: F = S0 as F, to: T) = V<E, R>(*a[from..to])
fun <E, L : S<*>> V<E, L>.take1(from: S<*> = S0) = V<E, L1>(*a[from..from + S1])
fun <E, L : S<*>> V<E, L>.take2(from: S<*> = S0) = V<E, L2>(*a[from..from + S2])
fun <E, L : S<*>> V<E, L>.take3(from: S<*> = S0) = V<E, L3>(*a[from..from + S3])

operator fun <A, B> S<A>.rangeTo(x: S<B>) = toInt()..x.toInt()