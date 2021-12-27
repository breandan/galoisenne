package ai.hypergraph.kaliningraph.types

import kotlin.jvm.JvmName

open class S<X: S<X>>(val x: S<X>?)
object O: S<O>(null)
fun S<*>.toInt(i: Int = 0): Int = x?.toInt(i + 1) ?: i

@Suppress("UNCHECKED_CAST")
fun <W: S<*>, X: S<W>> W.plus1(): X = S(this) as X
fun <W: S<*>, X: S<S<W>>> W.plus2(): X = plus1().plus1()
fun <W: S<*>, X: S<S<S<W>>>> W.plus3(): X = plus1().plus2()
fun <W: S<*>, X: S<S<S<S<W>>>>> W.plus4(): X = plus2().plus2()

@Suppress("UNCHECKED_CAST")
fun <W: S<*>, X: S<W>> X.minus1(): W = x as W
fun <W: S<*>, X: S<S<W>>> X.minus2(): W = minus1().minus1()
fun <W: S<*>, X: S<S<S<W>>>> X.minus3(): W = minus1().minus2()
fun <W: S<*>, X: S<S<S<S<W>>>>> X.minus4(): W = minus2().minus2()

@JvmName("n+0") operator fun <W: S<*>, X: S<O>> W.plus(x: X) = this
@JvmName("0+n") operator fun <W: S<O>, X: S<*>> W.plus(x: X) = x

@JvmName("1+1") operator fun <W: S<O>, X: S<O>> W.plus(x: X) = plus1()
@JvmName("2+1") operator fun <W: S<S<O>>, X: S<O>> W.plus(x: X) = plus1()
@JvmName("3+1") operator fun <W: S<S<S<O>>>, X: S<O>> W.plus(x: X) = plus1()
@JvmName("4+1") operator fun <W: S<S<S<S<O>>>>, X: S<O>> W.plus(x: X) = plus1()

@JvmName("1+2") operator fun <W: S<O>, X: S<S<O>>> W.plus(x: X) = plus2()
@JvmName("2+2") operator fun <W: S<S<O>>, X: S<S<O>>> W.plus(x: X) = plus2()
@JvmName("3+2") operator fun <W: S<S<S<O>>>, X: S<S<O>>> W.plus(x: X) = plus2()
@JvmName("4+2") operator fun <W: S<S<S<S<O>>>>, X: S<S<O>>> W.plus(x: X) = plus2()

@JvmName("1+3") operator fun <W: S<O>, X: S<S<S<O>>>> W.plus(x: X) = plus3()
@JvmName("2+3") operator fun <W: S<S<O>>, X: S<S<S<O>>>> W.plus(x: X) = plus3()
@JvmName("3+3") operator fun <W: S<S<S<O>>>, X: S<S<S<O>>>> W.plus(x: X) = plus3()
@JvmName("4+3") operator fun <W: S<S<S<S<O>>>>, X: S<S<S<O>>>> W.plus(x: X) = plus3()

@JvmName("1+4") operator fun <W: S<O>, X: S<S<S<S<O>>>>> W.plus(x: X) = plus4()
@JvmName("2+4") operator fun <W: S<S<O>>, X: S<S<S<S<O>>>>> W.plus(x: X) = plus4()
@JvmName("3+4") operator fun <W: S<S<S<O>>>, X: S<S<S<S<O>>>>> W.plus(x: X) = plus4()
@JvmName("4+4") operator fun <W: S<S<S<S<O>>>>, X: S<S<S<S<O>>>>> W.plus(x: X) = plus4()

@JvmName("n-n") operator fun <W: S<*>> W.minus(x: W) = O
@JvmName("2-1") operator fun <W: S<S<O>>, X: S<O>> W.minus(x: X) = minus1()
@JvmName("3-1") operator fun <W: S<S<S<O>>>, X: S<O>> W.minus(x: X) = minus1()
@JvmName("4-1") operator fun <W: S<S<S<S<O>>>>, X: S<O>> W.minus(x: X) = minus1()
@JvmName("3-2") operator fun <W: S<S<S<O>>>, X: S<S<O>>> W.minus(x: X) = minus2()
@JvmName("4-2") operator fun <W: S<S<S<S<O>>>>, X: S<S<O>>> W.minus(x: X) = minus2()
@JvmName("4-3") operator fun <W: S<S<S<S<O>>>>, X: S<S<S<O>>>> W.minus(x: X) = minus3()

@JvmName("n*1") operator fun <W: S<*>, X: S<O>> W.times(x: X) = this
@JvmName("1*n") operator fun <W: S<O>, X: S<*>> W.times(x: X) = x
@JvmName("n*0") operator fun <W: S<*>> W.times(x: O) = this
@JvmName("0*n") operator fun <X: S<*>> O.times(x: X) = x
@JvmName("2*2") operator fun <W: S<S<O>>, X: S<S<O>>> W.times(x: X) = plus2()