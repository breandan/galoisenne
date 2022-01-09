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

typealias B_0<B> = F<B>
typealias B_01<B> = T<B_0<B>>
typealias B_03<B> = T<B_01<B>>
typealias B_07<B> = T<B_03<B>>
typealias B_015<B> = T<B_07<B>>

typealias B_1<B> = T<B>
typealias B_2<B> = F<B_1<B>>
typealias B_4<B> = F<B_2<B>>
typealias B_8<B> = F<B_4<B>>
typealias B_16<B> = F<B_8<B>>

@JvmName("0p1") fun F<Nothing>.plus1(): B_1<Nothing> = T()
@JvmName("1p1") fun T<Nothing>.plus1(): B_2<Nothing> = F(T())
@JvmName("3p1") fun T<T<Nothing>>.plus1(): B_4<Nothing> = F(F(T()))
@JvmName("7p1") fun T<T<T<Nothing>>>.plus1(): B_8<Nothing> = F(F(F(T(x!!.x!!.x))))

@JvmName("obpi") fun <B> B_0<B>.plus1(): B_1<B> = T(x)
@JvmName("oibpi") fun <B> B_01<B>.plus1(): B_2<B> = F(T(x!!.x))
@JvmName("iiobpi") fun <B> B_03<B>.plus1(): B_4<B> = F(F(T(x!!.x!!.x)))