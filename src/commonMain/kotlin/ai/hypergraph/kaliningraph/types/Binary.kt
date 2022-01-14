@file:Suppress("UNUSED_PARAMETER", "UNCHECKED_CAST")
package ai.hypergraph.kaliningraph.types

import kotlin.jvm.JvmName

sealed class B<X, P : B<X, P>>(open val x: X? = null) {
  val T: T<P> get() = T(this as P)
  val F: F<P> get() = F(this as P)

  override fun equals(other: Any?) = toString() == other.toString()
  override fun hashCode() = this::class.hashCode() + x.hashCode()
  override fun toString() = "" + (x ?: "") + if (this is T) "1" else "0"
}

open class T<X>(override val x: X? = null) : B<X, T<X>>(x) { companion object: T<Nothing>() }
open class F<X>(override val x: X? = null) : B<X, F<X>>(x) { companion object: F<Nothing>() }

fun B<*, *>.toInt(): Int = toInt(toString())
tailrec fun toInt(s: String, sum: Int = 0): Int =
  if (s.isEmpty()) sum else toInt(s.substring(1), (sum shl 1) + s[0].digitToInt())

// Left padded with 0, B_0N indicates a binary string *0{B_0N}
typealias B_01<B> = T<B_0<B>>
typealias B_03<B> = T<B_01<B>>
typealias B_07<B> = T<B_03<B>>
typealias B_015<B> = T<B_07<B>>

typealias B_0<B> = F<B>
typealias B_00<B> = B_0<F<B>>
typealias B_02<B> = F<B_1<F<B>>>
typealias B_06<B> = F<B_03<F<B>>>
typealias B_014<B> = F<B_07<F<B>>>
typealias B_030<B> = F<B_15<F<B>>>

// Exact binary strings, B_N indicates a binary string 2^N-1
typealias B_1<B> = T<B>
typealias B_3<B> = T<B_1<B>>
typealias B_7<B> = T<B_3<B>>
typealias B_15<B> = T<B_7<B>>
typealias B_31<B> = T<B_15<B>>

@JvmName("b0p1") fun B_0<Nothing>.plus1() = T<Nothing>()
@JvmName("b1p1") fun B_1<Nothing>.plus1() = F(this)
@JvmName("b3p1") fun B_3<Nothing>.plus1() = F(x!!.plus1())
@JvmName("b7p1") fun B_7<Nothing>.plus1() = F(x!!.plus1())
@JvmName("b15p1") fun B_15<Nothing>.plus1() = F(x!!.plus1())

@JvmName("b_0p1") fun <B> B_0<B>.plus1() = T(x)
@JvmName("b_01p1") fun <B> B_01<B>.plus1() = F(x!!.plus1())
@JvmName("b_03p1") fun <B> B_03<B>.plus1() = F(x!!.plus1())
@JvmName("b_07p1") fun <B> B_07<B>.plus1() = F(x!!.plus1())

@JvmName("b0p2") fun B_0<Nothing>.plus2() = plus1().plus1()
@JvmName("b1p2") fun B_1<Nothing>.plus2() = plus1().plus1()
@JvmName("b3p2") fun B_3<Nothing>.plus2() = plus1().plus1()
@JvmName("b7p2") fun B_7<Nothing>.plus2() = plus1().plus1()
@JvmName("b15p2") fun B_15<Nothing>.plus2() = plus1().plus1()

@JvmName("b?00p2") fun <K> B_00<K>.plus2() = plus1().plus1()
@JvmName("b?01p2") fun <K> B_01<K>.plus2() = plus1().plus1()
@JvmName("b?02p2") fun <K> B_02<K>.plus2() = plus1().plus1()
@JvmName("b?03p2") fun <K> B_03<K>.plus2() = plus1().plus1()
@JvmName("b?06p2") fun <K> B_06<K>.plus2() = plus1().plus1()
@JvmName("b?07p2") fun <K> B_07<K>.plus2() = plus1().plus1()

// TODO: Enumerate all binary summands [2^N - D, 2^n - 1] + D for all (D, N)
///  *00 + 10 -> *10
///  *01 + 10 -> *11
/// *010 + 10 -> *100
/// *011 + 10 -> *101
///*0110 + 10 -> *1000
///*0111 + 10 -> *1001

// TODO: Alternatively, we could just implement RCA/CLA in full
// https://en.wikipedia.org/wiki/Adder_(electronics)#Adders_supporting_multiple_bits
//@JvmName("bp101") infix fun <A: T<F<*>>, B: F<Nothing>, C: F<Nothing>> Pair<A, B>.plus(c: C): A = first
//@JvmName("bp101") infix fun <L: T<F<Q>>, Q: B<*, *>, R: F<Nothing>, C: T<Nothing>> Pair<L, R>.plus(c: C): F<T<F<Q>>> = F(T(first.x))
