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
typealias B_0x1<B> = F<B>
typealias B_0x2<B> = B_0x1<F<B>>
typealias B_0x3<B> = B_0x2<F<B>>
typealias B_0x4<B> = B_0x3<F<B>>

typealias B_1<B> = T<B>
typealias B_2<B> = F<T<B>>
typealias B_3<B> = T<B_1<B>>
typealias B_6<B> = F<B_3<B>>
typealias B_7<B> = T<B_3<B>>
typealias B_14<B> = F<B_7<B>>
typealias B_15<B> = T<B_7<B>>
typealias B_30<B> = F<B_15<B>>
typealias B_31<B> = T<B_15<B>>

@JvmName("b0p1") fun B_0x1<Ø>.plus1() = T(Ø)
@JvmName("b1p1") fun B_1<Ø>.plus1() = F(x.plus1())
@JvmName("b3p1") fun B_3<Ø>.plus1() = F(x.plus1())
@JvmName("b7p1") fun B_7<Ø>.plus1() = F(x.plus1())
@JvmName("b15p1") fun B_15<Ø>.plus1() = F(x.plus1())

@JvmName("b_0p1")  fun <K: B<*, *>> Ø.plus1() = T(Ø)
@JvmName("b_0p1")  fun <K> F<K>.plus1() = T(x)
@JvmName("b_01p1") fun <K> B_1<F<K>>.plus1() = F(x.plus1())
@JvmName("b_03p1") fun <K> B_3<F<K>>.plus1() = F(x.plus1())
@JvmName("b_07p1") fun <K> B_7<F<K>>.plus1() = F(x.plus1())

@JvmName("b0p2") fun Ø.plus2() = plus1().plus1()
@JvmName("b0p2") fun F<Ø>.plus2() = plus1().plus1()
@JvmName("b1p2") fun B_1<Ø>.plus2() = plus1().plus1()
@JvmName("b2p2") fun B_2<Ø>.plus2() = plus1().plus1()
@JvmName("b3p2") fun B_3<Ø>.plus2() = plus1().plus1()
@JvmName("b6p2") fun B_6<Ø>.plus2() = plus1().plus1()
@JvmName("b7p2") fun B_7<Ø>.plus2() = plus1().plus1()
@JvmName("b14p2") fun B_14<Ø>.plus2() = plus1().plus1()
@JvmName("b15p2") fun B_15<Ø>.plus2() = plus1().plus1()

@JvmName("b?0x2p2") fun <K: B<*, *>> B_0x2<K>.plus2() = plus1().plus1()
@JvmName("b?01p2") fun <K: B<*, *>> B_1<F<K>>.plus2() = plus1().plus1()
@JvmName("b?02p2") fun <K: B<*, *>> B_2<F<K>>.plus2() = plus1().plus1()
@JvmName("b?03p2") fun <K: B<*, *>> B_3<F<K>>.plus2() = plus1().plus1()
@JvmName("b?06p2") fun <K: B<*, *>> B_6<F<K>>.plus2() = plus1().plus1()
@JvmName("b?07p2") fun <K: B<*, *>> B_7<F<K>>.plus2() = plus1().plus1()

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
