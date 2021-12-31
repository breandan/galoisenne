@file:Suppress("unused", "ObjectPropertyName", "ClassName", "PropertyName")

package ai.hypergraph.kaliningraph.types

import kotlin.jvm.JvmName

sealed class D<X, T: D<X, T>>(open val x: X? = null) {
  val _0_ get() = _0_(this as T)
  val _1_ get() = _1_(this as T)
  val _2_ get() = _2_(this as T)
  val _3_ get() = _3_(this as T)
  val _4_ get() = _4_(this as T)
  val _5_ get() = _5_(this as T)
  val _6_ get() = _6_(this as T)
  val _7_ get() = _7_(this as T)
  val _8_ get() = _8_(this as T)
  val _9_ get() = _9_(this as T)
}
open class _0_<X>(override val x: X? = null) : D<X, _0_<X>>(x) { companion object: _0_<Nothing>() }
open class _1_<X>(override val x: X? = null) : D<X, _1_<X>>(x) { companion object: _1_<Nothing>() }
open class _2_<X>(override val x: X? = null) : D<X, _2_<X>>(x) { companion object: _2_<Nothing>() }
open class _3_<X>(override val x: X? = null) : D<X, _3_<X>>(x) { companion object: _3_<Nothing>() }
open class _4_<X>(override val x: X? = null) : D<X, _4_<X>>(x) { companion object: _4_<Nothing>() }
open class _5_<X>(override val x: X? = null) : D<X, _5_<X>>(x) { companion object: _5_<Nothing>() }
open class _6_<X>(override val x: X? = null) : D<X, _6_<X>>(x) { companion object: _6_<Nothing>() }
open class _7_<X>(override val x: X? = null) : D<X, _7_<X>>(x) { companion object: _7_<Nothing>() }
open class _8_<X>(override val x: X? = null) : D<X, _8_<X>>(x) { companion object: _8_<Nothing>() }
open class _9_<X>(override val x: X? = null) : D<X, _9_<X>>(x) { companion object: _9_<Nothing>() }

val i10 = _1_._0_
val i12 = _1_._2_
val i13 = _1_._3_
val i14 = _1_._4_
val i15 = _1_._5_
val i16 = _1_._6_
val i17 = _1_._7_
val i18 = _1_._8_
val i19 = _1_._9_

typealias t19 = _9_<_1_<Nothing>>

tailrec fun D<*, *>?.toInt(i: Int = 0, j: Int = 1): Int =
  if (this == null) i else (x as D<*, *>?).toInt(i + this::class.simpleName!![1].digitToInt() * j, 10 * j)

@JvmName("_0p1") fun <T> _0_<T>.plus1(): _1_<T> = _1_(x)
@JvmName("_1p1") fun <T> _1_<T>.plus1(): _2_<T> = _2_(x)
@JvmName("_2p1") fun <T> _2_<T>.plus1(): _3_<T> = _3_(x)
@JvmName("_3p1") fun <T> _3_<T>.plus1(): _4_<T> = _4_(x)
@JvmName("_4p1") fun <T> _4_<T>.plus1(): _5_<T> = _5_(x)
@JvmName("_5p1") fun <T> _5_<T>.plus1(): _6_<T> = _6_(x)
@JvmName("_6p1") fun <T> _6_<T>.plus1(): _7_<T> = _7_(x)
@JvmName("_7p1") fun <T> _7_<T>.plus1(): _8_<T> = _8_(x)
@JvmName("_8p1") fun <T> _8_<T>.plus1(): _9_<T> = _9_(x)
@JvmName("_9p1") fun _9_<Nothing>.plus1(): _0_<_1_<Nothing>> = _0_(_1_())
@JvmName("_19p1") fun <T> _9_<_1_<T>>.plus1(): _0_<_2_<Nothing>> = _0_(_2_())
@JvmName("_29p1") fun <T> _9_<_2_<T>>.plus1(): _0_<_3_<Nothing>> = _0_(_3_())
@JvmName("_39p1") fun <T> _9_<_3_<T>>.plus1(): _0_<_4_<Nothing>> = _0_(_4_())
@JvmName("_49p1") fun <T> _9_<_4_<T>>.plus1(): _0_<_5_<Nothing>> = _0_(_5_())
@JvmName("_59p1") fun <T> _9_<_5_<T>>.plus1(): _0_<_6_<Nothing>> = _0_(_6_())
@JvmName("_69p1") fun <T> _9_<_6_<T>>.plus1(): _0_<_7_<Nothing>> = _0_(_7_())
@JvmName("_79p1") fun <T> _9_<_7_<T>>.plus1(): _0_<_8_<Nothing>> = _0_(_8_())
@JvmName("_89p1") fun <T> _9_<_8_<T>>.plus1(): _0_<_9_<Nothing>> = _0_(_9_())
@JvmName("_99p1") fun <T> _9_<_9_<T>>.plus1(): _0_<_0_<_1_<Nothing>>> = _0_(_0_(_1_()))
