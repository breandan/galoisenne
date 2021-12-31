@file:Suppress("UNUSED_PARAMETER", "UNCHECKED_CAST")
package ai.hypergraph.kaliningraph.types

import kotlin.jvm.JvmName

sealed class B<X>(open val x: X? = null)
open class T<X>(override val x: X? = null) : B<X>(x) { companion object: T<Nothing>() }
open class F<X>(override val x: X? = null) : B<X>(x) { companion object: F<Nothing>() }
object E

tailrec fun B<*>?.toInt(i: Int = 0, j: Int = 1): Int =
  if (this == null) i else (x as B<*>?).toInt(i + if (this is T) j else 0, 2 * j)

val U1 = T
val U2 = F(T)
val U3 = T(T)
val U4 = F(F(T))
val U5 = T(F(T))
val U6 = F(T(T))
val U7 = T(T(T))
val U8 = F(F(F(T)))
val U9 = T(F(F(T)))

@JvmName("0p1") fun F<Nothing>.plus1(): T<Nothing> = T()
@JvmName("1p1") fun T<Nothing>.plus1(): F<T<Nothing>> = F(T())
@JvmName("ibpi") fun <B> T<B>.plus1(): T<B> = T(x)
@JvmName("obpi") fun <B> F<B>.plus1(): T<B> = T(x)
@JvmName("oibpi") fun <B> T<F<B>>.plus1(): F<T<B>> = F(T(x!!.x))
@JvmName("3p1") fun T<T<Nothing>>.plus1(): F<F<T<Nothing>>> = F(F(T()))
@JvmName("iiobpi") fun <B> T<T<F<B>>>.plus1(): F<F<T<B>>> = F(F(T(x!!.x!!.x)))
@JvmName("iiiepi") fun T<T<T<Nothing>>>.plus1(): F<F<F<T<Nothing>>>> = F(F(F(T(x!!.x!!.x))))