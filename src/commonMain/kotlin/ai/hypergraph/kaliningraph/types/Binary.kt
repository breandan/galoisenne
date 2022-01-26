@file:Suppress("UNUSED_PARAMETER", "UNCHECKED_CAST")
package ai.hypergraph.kaliningraph.types

import kotlin.jvm.JvmName

sealed class B<X, P : B<X, P>>(open val x: X? = null) {
  val T: T<P> get() = T(this as P)
  val F: F<P> get() = F(this as P)

  abstract fun flip(): B<X, *>
  override fun equals(other: Any?) = toString() == other.toString()
  override fun hashCode() = this::class.hashCode() + x.hashCode()
  override fun toString() = "" + (x ?: "") + if (this is T) "1" else "0"
}

open class T<X>(override val x: X = Ø as X) : B<X, T<X>>(x) {
  companion object: T<Ø>(Ø)
  override fun flip(): F<X> = F(x)
}
open class F<X>(override val x: X = Ø as X) : B<X, F<X>>(x) {
  companion object: F<Ø>(Ø)
  override fun flip(): T<X> = T(x)
}
@Suppress("NonAsciiCharacters", "ClassName")
object Ø: B<Ø, Ø>(null) { override fun flip() = Ø }

fun B<*, *>.toInt(): Int = toInt(toString())
tailrec fun toInt(s: String, sum: Int = 0): Int =
  if (s.isEmpty()) sum else toInt(s.substring(1), (sum shl 1) + s[0].digitToInt())

// Left padded with 0, B_0N indicates a binary string *0{B_0N}
typealias B_0<B> = F<B>
typealias B_1<B> = T<B>
typealias B_2<B> = F<T<B>>
typealias B_3<B> = T<B_1<B>>
typealias B_4<B> = F<B_2<B>>
typealias B_5<B> = T<F<T<F<B>>>>
typealias B_6<B> = F<B_3<B>>
typealias B_7<B> = T<B_3<B>>
typealias B_8<B> = F<B_4<B>>
typealias B_9<B> = T<B_4<B>>
typealias B_10<B> = F<T<B_2<B>>>
typealias B_13<B> = T<F<B_3<B>>>
typealias B_14<B> = F<B_7<B>>
typealias B_15<B> = T<B_7<B>>
typealias B_16<B> = F<B_8<B>>
typealias B_17<B> = T<B_8<B>>
typealias B_18<B> = F<B_9<B>>
typealias B_30<B> = F<B_15<B>>
typealias B_31<B> = T<B_15<B>>

fun <K: B<*, *>> K.shl() = F
fun <K: B<*, *>> K.times2() = F
fun <K: B<*, *>> K.times4() = F.F
fun <K: B<*, *>> K.times8() = F.F.F
fun <K: B<*, *>> K.times16() = F.F.F.F

@JvmName("bnp1") fun Ø.plus1() = T(Ø)
@JvmName("b0p1") fun B_0<Ø>.plus1() = T(Ø)
@JvmName("b1p1") fun B_1<Ø>.plus1() = F(x.plus1())
@JvmName("b3p1") fun B_3<Ø>.plus1() = F(x.plus1())
@JvmName("b7p1") fun B_7<Ø>.plus1() = F(x.plus1())
@JvmName("b15p1") fun B_15<Ø>.plus1() = F(x.plus1())

@JvmName("b_0p1")  fun <K: B<*, *>> B_0<K>.plus1() = T(x)
@JvmName("b_01p1") fun <K: B<*, *>> B_1<F<K>>.plus1() = F(x.plus1())
@JvmName("b_03p1") fun <K: B<*, *>> B_3<F<K>>.plus1() = F(x.plus1())
@JvmName("b_07p1") fun <K: B<*, *>> B_7<F<K>>.plus1() = F(x.plus1())
@JvmName("b_015p1") fun <K: B<*, *>> B_15<F<K>>.plus1() = F(x.plus1())

@JvmName("b1m1") fun B_1<Ø>.minus1() = F(Ø)
@JvmName("b2m1") fun B_2<Ø>.minus1() = T(Ø)
@JvmName("b4m1") fun B_4<Ø>.minus1() = T(x.minus1())
@JvmName("b8m1") fun B_8<Ø>.minus1() = T(x.minus1())
@JvmName("b16m1") fun B_16<Ø>.minus1() = T(x.minus1())

@JvmName("b_1m1") fun <K: B<*, *>> B_1<K>.minus1() = F(x)
@JvmName("b_2m1") fun <K: B<*, *>> B_2<K>.minus1() = T(x.minus1())
@JvmName("b_4m1") fun <K: B<*, *>> B_4<K>.minus1() = T(x.minus1())
@JvmName("b_8m1") fun <K: B<*, *>> B_8<K>.minus1() = T(x.minus1())
@JvmName("b_16m1") fun <K: B<*, *>> B_16<K>.minus1() = T(x.minus1())

@JvmName("b0p2") fun B_0<Ø>.plus2(): F<T<Ø>> = plus1().plus1()
@JvmName("b1p2") fun B_1<Ø>.plus2(): T<T<Ø>> = plus1().plus1()
@JvmName("b2p2") fun B_2<Ø>.plus2(): F<F<T<Ø>>> = plus1().plus1()
@JvmName("b3p2") fun B_3<Ø>.plus2(): T<F<T<Ø>>> = plus1().plus1()
@JvmName("b6p2") fun B_6<Ø>.plus2(): F<F<F<T<Ø>>>> = plus1().plus1()
@JvmName("b7p2") fun B_7<Ø>.plus2(): T<F<F<T<Ø>>>> = plus1().plus1()
@JvmName("b14p2") fun B_14<Ø>.plus2(): F<F<F<F<T<Ø>>>>> = plus1().plus1()
@JvmName("b15p2") fun B_15<Ø>.plus2(): T<F<F<F<T<Ø>>>>> = plus1().plus1()

@JvmName("b?00p2") fun <K: B<*, *>> B_0<F<K>>.plus2(): F<T<K>> = plus1().plus1()
@JvmName("b?01p2") fun <K: B<*, *>> B_1<F<K>>.plus2(): T<T<K>> = plus1().plus1()
@JvmName("b?02p2") fun <K: B<*, *>> B_2<F<K>>.plus2(): F<F<T<K>>> = plus1().plus1()
@JvmName("b?03p2") fun <K: B<*, *>> B_3<F<K>>.plus2(): T<F<T<K>>> = plus1().plus1()
@JvmName("b?06p2") fun <K: B<*, *>> B_6<F<K>>.plus2(): F<F<F<T<K>>>> = plus1().plus1()
@JvmName("b?07p2") fun <K: B<*, *>> B_7<F<K>>.plus2(): T<F<F<T<K>>>> = plus1().plus1()
@JvmName("b?014p2") fun <K: B<*, *>> B_14<F<K>>.plus2(): F<F<F<F<T<K>>>>> = plus1().plus1()
@JvmName("b?015p2") fun <K: B<*, *>> B_15<F<K>>.plus2(): T<F<F<F<T<K>>>>> = plus1().plus1()

@JvmName("b2m2") fun B_2<Ø>.minus2() = minus1().minus1()
@JvmName("b3m2") fun B_3<Ø>.minus2() = minus1().minus1()
@JvmName("b4m2") fun B_4<Ø>.minus2() = minus1().minus1()
@JvmName("b5m2") fun B_5<Ø>.minus2() = minus1().minus1()
@JvmName("b8m2") fun B_8<Ø>.minus2() = minus1().minus1()
@JvmName("b9m2") fun B_9<Ø>.minus2() = minus1().minus1()
@JvmName("b16m2") fun B_16<Ø>.minus2() = minus1().minus1()
@JvmName("b17m2") fun B_17<Ø>.minus2() = minus1().minus1()

@JvmName("b_2m2") fun <K: B<*, *>> B_2<K>.minus2() = minus1().minus1()
@JvmName("b_3m2") fun <K: B<*, *>> B_3<K>.minus2() = minus1().minus1()
@JvmName("b_4m2") fun <K: B<*, *>> B_4<K>.minus2() = minus1().minus1()
@JvmName("b_5m2") fun <K: B<*, *>> B_5<K>.minus2() = minus1().minus1()
@JvmName("b_8m2") fun <K: B<*, *>> B_8<K>.minus2() = minus1().minus1()
@JvmName("b_9m2") fun <K: B<*, *>> B_9<K>.minus2() = minus1().minus1()
@JvmName("b_16m2") fun <K: B<*, *>> B_16<K>.minus2() = minus1().minus1()
@JvmName("b_17m2") fun <K: B<*, *>> B_17<K>.minus2() = minus1().minus1()

@JvmName("b0p3") fun F<Ø>.plus3(): T<T<Ø>> = plus2().plus1()
@JvmName("b2p3") fun B_2<Ø>.plus3(): T<F<T<Ø>>> = plus2().plus1()
@JvmName("b3p3") fun B_3<Ø>.plus3(): F<T<T<Ø>>> = plus2().plus1()
@JvmName("b6p3") fun B_6<Ø>.plus3(): T<F<F<T<Ø>>>> = plus2().plus1()
@JvmName("b7p3") fun B_7<Ø>.plus3(): F<T<F<T<Ø>>>> = plus2().plus1()
@JvmName("b14p3") fun B_14<Ø>.plus3(): T<F<F<F<T<Ø>>>>> = plus2().plus1()
@JvmName("b15p3") fun B_15<Ø>.plus3(): F<T<F<F<T<Ø>>>>> = plus2().plus1()

@JvmName("b?00p3") fun <K: B<*, *>> B_0<F<K>>.plus3(): T<T<K>> = plus2().plus1()
@JvmName("b?02p3") fun <K: B<*, *>> B_2<F<K>>.plus3(): T<F<T<K>>> = plus2().plus1()
@JvmName("b?03p3") fun <K: B<*, *>> B_3<F<K>>.plus3(): F<T<T<K>>> = plus2().plus1()
@JvmName("b?05p3") fun <K: B<*, *>> B_5<F<K>>.plus3(): F<F<F<T<F<K>>>>> = plus2().plus1()
@JvmName("b?06p3") fun <K: B<*, *>> B_6<F<K>>.plus3(): T<F<F<T<K>>>> = plus2().plus1()
@JvmName("b?07p3") fun <K: B<*, *>> B_7<F<K>>.plus3(): F<T<F<T<K>>>> = plus2().plus1()
@JvmName("b?013p3") fun <K: B<*, *>> B_13<F<K>>.plus3(): F<F<F<F<T<K>>>>> = plus2().plus1()
@JvmName("b?014p3") fun <K: B<*, *>> B_14<F<K>>.plus3(): T<F<F<F<T<K>>>>> = plus2().plus1()
@JvmName("b?015p3") fun <K: B<*, *>> B_15<F<K>>.plus3(): F<T<F<F<T<K>>>>> = plus2().plus1()

@JvmName("b3m3") fun B_3<Ø>.minus3() = minus2().minus1()
@JvmName("b4m3") fun B_4<Ø>.minus3() = minus2().minus1()
@JvmName("b5m3") fun B_5<Ø>.minus3() = minus2().minus1()
@JvmName("b6m3") fun B_6<Ø>.minus3() = minus2().minus1()
@JvmName("b8m3") fun B_8<Ø>.minus3() = minus2().minus1()
@JvmName("b9m3") fun B_9<Ø>.minus3() = minus2().minus1()
@JvmName("b10m3") fun B_10<Ø>.minus3() = minus2().minus1()
@JvmName("b16m3") fun B_16<Ø>.minus3() = minus2().minus1()
@JvmName("b17m3") fun B_17<Ø>.minus3() = minus2().minus1()
@JvmName("b18m3") fun B_18<Ø>.minus3() = minus2().minus1()

@JvmName("b_3m3") fun <K: B<*, *>> B_3<K>.minus3() = minus2().minus1()
@JvmName("b_4m3") fun <K: B<*, *>> B_4<K>.minus3() = minus2().minus1()
@JvmName("b_5m3") fun <K: B<*, *>> B_5<K>.minus3() = minus2().minus1()
@JvmName("b_6m3") fun <K: B<*, *>> B_6<K>.minus3() = minus2().minus1()
@JvmName("b_8m3") fun <K: B<*, *>> B_8<K>.minus3() = minus2().minus1()
@JvmName("b_9m3") fun <K: B<*, *>> B_9<K>.minus3() = minus2().minus1()
@JvmName("b_10m3") fun <K: B<*, *>> B_10<K>.minus3() = minus2().minus1()
@JvmName("b_16m3") fun <K: B<*, *>> B_16<K>.minus3() = minus2().minus1()
@JvmName("b_17m3") fun <K: B<*, *>> B_17<K>.minus3() = minus2().minus1()
@JvmName("b_18m3") fun <K: B<*, *>> B_18<K>.minus3() = minus2().minus1()

//@JvmName("flipTT") fun <X: T<Y>, Y> X.flipAll()/*:F<???>*/= F(x.flipAll())
//@JvmName("flipFF") fun <X: F<Y>, Y> X.flipAll()/*:T<???>*/= F(x.flipAll())

// TODO: Enumerate all binary summands [2^N - D, 2^n - 1] + D for all (D, N)
///  *00 + 10 -> *10
///  *01 + 10 -> *11
/// *010 + 10 -> *100
/// *011 + 10 -> *101
///*0110 + 10 -> *1000
///*0111 + 10 -> *1001

// TODO: Alternatively, we could just implement RCA/CLA in full
// https://en.wikipedia.org/wiki/Adder_(electronics)#Adders_supporting_multiple_bits
//@JvmName("bp101") infix fun <A: T<F<*>>, B: F<Ø>, C: F<Ø>> Pair<A, B>.plus(c: C): A = first
//@JvmName("bp101") infix fun <L: T<F<Q>>, Q: B<*, *>, R: F<Ø>, C: T<Ø>> Pair<L, R>.plus(c: C): F<T<F<Q>>> = F(T(first.x))
