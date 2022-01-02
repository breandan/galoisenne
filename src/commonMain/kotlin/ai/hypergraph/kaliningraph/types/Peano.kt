@file:Suppress("UNUSED_PARAMETER", "UNCHECKED_CAST")
package ai.hypergraph.kaliningraph.types

import kotlin.jvm.JvmName

open class S<X>(val x: X?)
object O: S<O>(null)
fun S<*>.toInt(i: Int = 0): Int = (x as? S<*>)?.toInt(i + 1) ?: i

operator fun Number.plus(s: S<*>): Int = toInt() + s.toInt()
operator fun Number.minus(s: S<*>): Int = toInt() - s.toInt()
operator fun Number.times(s: S<*>): Int = toInt() * s.toInt()
operator fun Number.div(s: S<*>): Int = toInt() / s.toInt()

operator fun S<*>.plus(n: Number): Int = toInt() + n.toInt()
operator fun S<*>.minus(n: Number): Int = toInt() - n.toInt()
operator fun S<*>.times(n: Number): Int = toInt() * n.toInt()
operator fun S<*>.div(n: Number): Int = toInt() / n.toInt()

class A<E, L: S<*>> internal constructor(vararg val a: E)

fun <E> A(t1: E) = A<E, L1>(t1)
fun <E> A(t1: E, t2: E) = A<E, L2>(t1, t2)
fun <E> A(t1: E, t2: E, t3: E) = A<E, L3>(t1, t2, t3)
fun <E> A(t1: E, t2: E, t3: E, t4: E) = A<E, L4>(t1, t2, t3, t4)
fun <E> A(t1: E, t2: E, t3: E, t4: E, t5: E) = A<E, L5>(t1, t2, t3, t4, t5)
fun <E> A(t1: E, t2: E, t3: E, t4: E, t5: E, t6: E) = A<E, L6>(t1, t2, t3, t4, t5, t6)
fun <E> A(t1: E, t2: E, t3: E, t4: E, t5: E, t6: E, t7: E) = A<E, L7>(t1, t2, t3, t4, t5, t6, t7)
fun <E> A(t1: E, t2: E, t3: E, t4: E, t5: E, t6: E, t7: E, t8: E) = A<E, L8>(t1, t2, t3, t4, t5, t6, t7, t8)
fun <E> A(t1: E, t2: E, t3: E, t4: E, t5: E, t6: E, t7: E, t8: E, t9: E) = A<E, L9>(t1, t2, t3, t4, t5, t6, t7, t8, t9)

operator fun <R, L: Q1<R>, E> A<E, L>.get(i1: L0) = a[0]
operator fun <R, L: Q2<R>, E> A<E, L>.get(i1: L1) = a[1]
operator fun <R, L: Q3<R>, E> A<E, L>.get(i1: L3) = a[3]
operator fun <R, L: Q4<R>, E> A<E, L>.get(i1: L4) = a[4]

fun <L: Q1<*>, E> A<E, L>.take1() = A<E, L1>(*a.sliceArray(0..0))
fun <L: Q2<*>, E> A<E, L>.take2() = A<E, L2>(*a.sliceArray(0..1))
fun <L: Q3<*>, E> A<E, L>.take3() = A<E, L3>(*a.sliceArray(0..2))
fun <L: Q4<*>, E> A<E, L>.take4() = A<E, L4>(*a.sliceArray(0..3))

val S0: L0 = O
val S1: L1 = S(O)
val S2: L2 = S(S1)
val S3: L3 = S(S2)
val S4: L4 = S(S3)
val S5: L5 = S(S4)
val S6: L6 = S(S5)
val S7: L7 = S(S6)
val S8: L8 = S(S7)
val S9: L9 = S(S8)

typealias L0 = O
typealias L1 = S<O>
typealias L2 = Q2<O>
typealias L3 = Q3<O>
typealias L4 = Q4<O>
typealias L5 = Q5<O>
typealias L6 = Q6<O>
typealias L7 = Q7<O>
typealias L8 = Q8<O>
typealias L9 = Q9<O>
typealias Q1<T> = S<T>
typealias Q2<T> = S<S<T>>
typealias Q3<T> = S<S<S<T>>>
typealias Q4<T> = S<S<S<S<T>>>>
typealias Q5<T> = S<S<S<S<S<T>>>>>
typealias Q6<T> = S<S<S<S<S<S<T>>>>>>
typealias Q7<T> = S<S<S<S<S<S<S<T>>>>>>>
typealias Q8<T> = S<S<S<S<S<S<S<S<T>>>>>>>>
typealias Q9<T> = S<S<S<S<S<S<S<S<S<T>>>>>>>>>

fun <W: S<*>, X: S<W>> W.plus1(): X = S(this) as X
fun <W: S<*>, X: S<W>> X.minus1(): W = x as W
fun <W: S<*>, X: Q2<W>> W.plus2(): X = plus1().plus1()
fun <W: S<*>, X: Q2<W>> X.minus2(): W = minus1().minus1()
fun <W: S<*>, X: Q3<W>> W.plus3(): X = plus1().plus2()
fun <W: S<*>, X: Q3<W>> X.minus3(): W = minus1().minus2()
fun <W: S<*>, X: Q4<W>> W.plus4(): X = plus2().plus2()
fun <W: S<*>, X: Q4<W>> X.minus4(): W = minus2().minus2()
fun <W: S<*>, X: Q5<W>> W.plus5(): X = plus2().plus3()
fun <W: S<*>, X: Q5<W>> X.minus5(): W = minus2().minus3()
fun <W: S<*>, X: Q6<W>> W.plus6(): X = plus3().plus3()
fun <W: S<*>, X: Q6<W>> X.minus6(): W = minus3().minus3()
fun <W: S<*>, X: Q7<W>> W.plus7(): X = plus3().plus4()
fun <W: S<*>, X: Q7<W>> X.minus7(): W = minus3().minus4()
fun <W: S<*>, X: Q8<W>> W.plus8(): X = plus4().plus4()
fun <W: S<*>, X: Q8<W>> X.minus8(): W = minus4().minus4()
fun <W: S<*>, X: Q9<W>> W.plus9(): X = plus4().plus5()
fun <W: S<*>, X: Q9<W>> X.minus9(): W = minus4().minus5()

@JvmName("n+0") operator fun <W: S<*>> W.plus(x: O): W = this
@JvmName("0+n") operator fun <X: S<*>> O.plus(x: X): X = x
@JvmName("n+1") operator fun <W: S<*>, X: S<O>> W.plus(x: X): S<W> = plus1()
@JvmName("1+n") operator fun <W: S<*>, X: S<O>> X.plus(w: W): S<W> = w.plus1()
@JvmName("n-1") operator fun <W: S<*>, X: S<W>, Y: S<O>> X.minus(y: Y): W = minus1()
@JvmName("n÷1") operator fun <W: S<*>, X: S<O>> W.div(x: X): W = this
@JvmName("n*1") operator fun <W: S<*>, X: S<O>> W.times(x: X): W = this
@JvmName("1*n") operator fun <W: S<*>, X: S<O>> X.times(w: W): W = w
@JvmName("n*0") operator fun <W: S<*>> W.times(x: O): O = O
@JvmName("0*n") operator fun <X: S<*>> O.times(x: X): O = O

@JvmName("n+2") operator fun <W: L2, X: S<*>> X.plus(x: W): Q2<X> = plus2()
@JvmName("n+3") operator fun <W: L3, X: S<*>> X.plus(x: W): Q3<X> = plus3()
@JvmName("n+4") operator fun <W: L4, X: S<*>> X.plus(x: W): Q4<X> = plus4()
@JvmName("n+5") operator fun <W: L5, X: S<*>> X.plus(x: W): Q5<X> = plus5()
@JvmName("n+6") operator fun <W: L6, X: S<*>> X.plus(x: W): Q6<X> = plus6()
@JvmName("n+7") operator fun <W: L7, X: S<*>> X.plus(x: W): Q7<X> = plus7()
@JvmName("n+8") operator fun <W: L8, X: S<*>> X.plus(x: W): Q8<X> = plus8()
@JvmName("n+9") operator fun <W: L9, X: S<*>> X.plus(x: W): Q9<X> = plus9()

@JvmName("n-2") operator fun <V: L2, W: S<*>, X: Q2<W>> X.minus(v: V): W = minus2()
@JvmName("n-3") operator fun <V: L3, W: S<*>, X: Q3<W>> X.minus(v: V): W = minus3()
@JvmName("n-4") operator fun <V: L4, W: S<*>, X: Q4<W>> X.minus(v: V): W = minus4()
@JvmName("n-5") operator fun <V: L5, W: S<*>, X: Q5<W>> X.minus(v: V): W = minus5()
@JvmName("n-6") operator fun <V: L6, W: S<*>, X: Q6<W>> X.minus(v: V): W = minus6()
@JvmName("n-7") operator fun <V: L7, W: S<*>, X: Q7<W>> X.minus(v: V): W = minus7()
@JvmName("n-8") operator fun <V: L8, W: S<*>, X: Q8<W>> X.minus(v: V): W = minus8()
@JvmName("n-9") operator fun <V: L9, W: S<*>, X: Q9<W>> X.minus(v: V): W = minus9()

@JvmName("2*2") operator fun <W: L2, X: L2> W.times(x: X): L4 = S4
@JvmName("2*3") operator fun <W: L2, X: L3> W.times(x: X): L6 = S6
@JvmName("2*4") operator fun <W: L2, X: L4> W.times(x: X): L8 = S8
@JvmName("3*2") operator fun <W: L3, X: L2> W.times(x: X): L6 = S6
@JvmName("3*3") operator fun <W: L3, X: L3> W.times(x: X): L9 = S9
@JvmName("4*2") operator fun <W: L4, X: L2> W.times(x: X): L8 = S8
@JvmName("4÷2") operator fun <W: L4, X: L2> W.div(x: X): L2 = S2
@JvmName("6÷2") operator fun <W: L6, X: L2> W.div(x: X): L3 = S3
@JvmName("6÷3") operator fun <W: L6, X: L3> W.div(x: X): L2 = S2
@JvmName("8÷2") operator fun <W: L8, X: L2> W.div(x: X): L4 = S4
@JvmName("8÷4") operator fun <W: L8, X: L4> W.div(x: X): L2 = S2
@JvmName("9÷3") operator fun <W: L9, X: L3> W.div(x: X): L3 = S3
