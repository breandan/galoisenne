@file:Suppress("UNUSED_PARAMETER", "UNCHECKED_CAST")
package ai.hypergraph.kaliningraph.types

import kotlin.jvm.JvmName

sealed class B<X, P: B<X, P>>(open val x: X? = null) {
  val T get() = T(this as P)
  val F get() = F(this as P)
}
open class T<X>(override val x: X? = null) : B<X, T<X>>(x) { companion object: T<Nothing>() }
open class F<X>(override val x: X? = null) : B<X, F<X>>(x) { companion object: F<Nothing>() }

tailrec fun B<*, *>?.toInt(i: Int = 0, j: Int = 1): Int =
  if (this == null) i else (x as B<*, *>?).toInt(i + if (this is T) j else 0, 2 * j)

val TF = T.F
val TT = T.T
val TFF = F.F.T
val TFT = T.F.T
val TTF = T.T.F
val TTT = T.T.T
val TFFF = T.F.F.F
val TFFT = T.F.F.T

@JvmName("0p1") fun F<Nothing>.plus1(): T<Nothing> = T()
@JvmName("1p1") fun T<Nothing>.plus1(): F<T<Nothing>> = F(T())
@JvmName("ibpi") fun <B> T<B>.plus1(): T<B> = T(x)
@JvmName("obpi") fun <B> F<B>.plus1(): T<B> = T(x)
@JvmName("oibpi") fun <B> T<F<B>>.plus1(): F<T<B>> = F(T(x!!.x))
@JvmName("3p1") fun T<T<Nothing>>.plus1(): F<F<T<Nothing>>> = F(F(T()))
@JvmName("iiobpi") fun <B> T<T<F<B>>>.plus1(): F<F<T<B>>> = F(F(T(x!!.x!!.x)))
@JvmName("iiiepi") fun T<T<T<Nothing>>>.plus1(): F<F<F<T<Nothing>>>> = F(F(F(T(x!!.x!!.x))))