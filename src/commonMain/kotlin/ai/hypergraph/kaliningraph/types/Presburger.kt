package ai.hypergraph.kaliningraph.types

open class S<X: S<X>>(val x: S<X>?)
class O: S<O>(null)
fun S<*>.toInt(i: Int = 0): Int = x?.toInt(i + 1) ?: i

fun <W: S<*>> W.plus0(): W = this
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

//fun <W: S<*>> W.times0(): O = O()
//fun <W: S<*>> W.times1(): W = this