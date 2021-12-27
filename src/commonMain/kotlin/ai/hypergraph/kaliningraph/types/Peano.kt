@file:Suppress("UNUSED_PARAMETER", "UNCHECKED_CAST")
package ai.hypergraph.kaliningraph.types

import kotlin.jvm.JvmName

open class S<X>(val x: S<X>?)
object O: S<O>(null)
fun S<*>.toInt(i: Int = 0): Int = x?.toInt(i + 1) ?: i

val S1 = S(O)
val S2 = S1.plus1()
val S3 = S1.plus2()
val S4 = S2.plus2()
val S5 = S2.plus3()
val S6 = S3.plus3()
val S7 = S3.plus4()
val S8 = S4.plus4()
val S9 = S4.plus5()
private typealias L1 = S<O>
private typealias L2 = S<S<O>>
private typealias L3 = S<S<S<O>>>
private typealias L4 = S<S<S<S<O>>>>
private typealias L5 = S<S<S<S<S<O>>>>>
private typealias L6 = S<S<S<S<S<S<O>>>>>>
private typealias L7 = S<S<S<S<S<S<S<O>>>>>>>
private typealias L8 = S<S<S<S<S<S<S<S<O>>>>>>>>
private typealias L9 = S<S<S<S<S<S<S<S<S<O>>>>>>>>>
private typealias Q1 = S<*>
private typealias Q2<T> = S<S<T>>
private typealias Q3<T> = S<S<S<T>>>
private typealias Q4<T> = S<S<S<S<T>>>>
private typealias Q5<T> = S<S<S<S<S<T>>>>>
private typealias Q6<T> = S<S<S<S<S<S<T>>>>>>
private typealias Q7<T> = S<S<S<S<S<S<S<T>>>>>>>
private typealias Q8<T> = S<S<S<S<S<S<S<S<T>>>>>>>>
private typealias Q9<T> = S<S<S<S<S<S<S<S<S<T>>>>>>>>>

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

@JvmName("n+0") operator fun <W: S<*>> W.plus(x: O) = this
@JvmName("0+n") operator fun <X: S<*>> O.plus(x: X) = x
@JvmName("n+1") operator fun <W: S<*>, X: S<O>> W.plus(x: X) = plus1()
@JvmName("1+n") operator fun <W: S<*>, X: S<O>> X.plus(w: W) = w.plus1()
@JvmName("n-1") operator fun <W: S<*>, X: S<W>, Y: S<O>> X.minus(y: Y) = minus1()
@JvmName("n÷1") operator fun <W: S<*>, X: S<O>> W.div(x: X) = this
@JvmName("n*1") operator fun <W: S<*>, X: S<O>> W.times(x: X) = this
@JvmName("1*n") operator fun <W: S<*>, X: S<O>> X.times(w: W) = w
@JvmName("n*0") operator fun <W: S<*>> W.times(x: O) = O
@JvmName("0*n") operator fun <X: S<*>> O.times(x: X) = O

@JvmName("n+2") operator fun <W: L2, X: S<*>> X.plus(x: W) = plus2()
@JvmName("n+3") operator fun <W: L3, X: S<*>> X.plus(x: W) = plus3()
@JvmName("n+4") operator fun <W: L4, X: S<*>> X.plus(x: W) = plus4()
@JvmName("n+5") operator fun <W: L5, X: S<*>> X.plus(x: W) = plus5()
@JvmName("n+6") operator fun <W: L6, X: S<*>> X.plus(x: W) = plus6()
@JvmName("n+7") operator fun <W: L7, X: S<*>> X.plus(x: W) = plus7()
@JvmName("n+8") operator fun <W: L8, X: S<*>> X.plus(x: W) = plus8()
@JvmName("n+9") operator fun <W: L9, X: S<*>> X.plus(x: W) = plus9()

@JvmName("n-2") operator fun <V: L2, W: S<*>, X: Q2<W>> X.minus(v: V) = minus2()
@JvmName("n-3") operator fun <V: L3, W: S<*>, X: Q3<W>> X.minus(v: V) = minus3()
@JvmName("n-4") operator fun <V: L4, W: S<*>, X: Q4<W>> X.minus(v: V) = minus4()
@JvmName("n-5") operator fun <V: L5, W: S<*>, X: Q5<W>> X.minus(v: V) = minus5()
@JvmName("n-6") operator fun <V: L6, W: S<*>, X: Q6<W>> X.minus(v: V) = minus6()
@JvmName("n-7") operator fun <V: L7, W: S<*>, X: Q7<W>> X.minus(v: V) = minus7()
@JvmName("n-8") operator fun <V: L8, W: S<*>, X: Q8<W>> X.minus(v: V) = minus8()
@JvmName("n-9") operator fun <V: L9, W: S<*>, X: Q9<W>> X.minus(v: V) = minus9()

@JvmName("2*2") operator fun <W: L2, X: L2> W.times(x: X) = S4
@JvmName("2*3") operator fun <W: L2, X: L3> W.times(x: X) = S6
@JvmName("2*4") operator fun <W: L2, X: L4> W.times(x: X) = S8
@JvmName("3*2") operator fun <W: L3, X: L2> W.times(x: X) = S6
@JvmName("3*3") operator fun <W: L3, X: L3> W.times(x: X) = S9
@JvmName("4*2") operator fun <W: L4, X: L2> W.times(x: X) = S8
@JvmName("4÷2") operator fun <W: L4, X: L2> W.div(x: X) = S2
@JvmName("6÷2") operator fun <W: L6, X: L2> W.div(x: X) = S3
@JvmName("6÷3") operator fun <W: L6, X: L3> W.div(x: X) = S2
@JvmName("8÷2") operator fun <W: L8, X: L2> W.div(x: X) = S4
@JvmName("8÷4") operator fun <W: L8, X: L4> W.div(x: X) = S2
@JvmName("9÷3") operator fun <W: L9, X: L3> W.div(x: X) = S3