@file:Suppress("UNUSED_PARAMETER", "UNCHECKED_CAST", "NonAsciiCharacters")
package ai.hypergraph.kaliningraph.types

import kotlin.jvm.JvmName

typealias 𝔹 = Boolean
typealias 𝔹ⁿ = BooleanArray

sealed class B<X, P : B<X, P>>(open val x: X? = null) {
  val T: T<P> get() = T(this as P)
  val F: F<P> get() = F(this as P)

  abstract fun flip(): B<X, *>
  override fun equals(other: Any?) = toString() == other.toString()
  override fun hashCode() = this::class.hashCode() + x.hashCode()
  override fun toString() = "" + (x ?: "") + if (this is T) "1" else "0"
  fun toInt(): Int = toInt(toString())
  tailrec fun toInt(s: String, sum: Int = 0): Int =
    if (s.isEmpty()) sum else toInt(s.substring(1), (sum shl 1) + s[0].digitToInt())
}

open class T<X>(override val x: X = Ø as X) : B<X, T<X>>(x) {
  companion object: T<Ø>(Ø)
  override fun flip(): F<X> = F(x)
}

open class F<X>(override val x: X = Ø as X) : B<X, F<X>>(x) {
  companion object: F<Ø>(Ø)
  override fun flip(): T<X> = T(x)
}

// Unchecked / checked at runtime
open class U(val i: Int) : B<Any, U>() {
  override fun flip(): U = TODO()
}

@Suppress("NonAsciiCharacters", "ClassName")
object Ø: B<Ø, Ø>(null) { override fun flip() = Ø }

/**
 *     i │  0  1  …  k-1  k  │  k+1  k+2  …  k+c  │  k+c+1  …  k+c+k
 *    ───┼───────────────────┼────────────────────┼───────────────────┐ ┐
 *     0 │                   │                    │                __/  │
 *     1 │                   │                    │             __/XXX  │
 *     … │       i ± i       │        k ± i       │          __/XXXXXX  ├ ┐
 *   k-1 │                   │                    │      ___/XXXXXXXXX  │ │
 *     k │                   │                    │  ___╱XXXXXXXXXXXXX  │ │
 *   ────┼───────────────────┼────────────────────┴─┘XXXXXXXXXXXXXXXXX  ┘ │
 *   k+1 │                   │XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX    │
 *   k+2 │                   │XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX    │
 *     … │       i ± k       │XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX    │
 *     … │                   │XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX    │
 *   k+c │                   │XXXXXXXXXXXX                XXXXXXXXXXXX    │
 *  ─────┼───────────────────┤XXXXXXXXXXXX    Run-time    XXXXXXXXXXXX    │
 * k+c+1 │               ___/XXXXXXXXXXXXX  type checked  XXXXXXXXXXXX    │
 *     … │           ___/XXXXXXXXXXXXXXXXX                XXXXXXXXXXXX    │
 *     … │       ___/XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX    │
 *     … │   ___/XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX    │
 * k+c+k │__/XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX    │
 *
 *       └─────────┬─────────┘                               Compile-time
 *                 └─────────────────────────────────────    type checked
 */

// Left padded with 0, B_0N indicates a binary string *0{B_0N}
// Enumerate (max(2ⁱ-k, 0), 2ⁱ+k) ∀0≤i≤⌊log₂(k+c+k)⌋
// i.e.: {0, 1, 2, *(4-k, 4+k), *(8-k, 8+k), *(16-k, 16+k),..., *(2^⌊log₂(k+c+k)⌋-k, 2^⌊log₂(k+c+k)⌋+k)}

typealias B_0<B> = F<B>
typealias B_1<B> = T<B>
typealias B_2<B> = F<T<B>>
typealias B_3<B> = T<T<B>>
typealias B_4<B> = F<F<T<B>>>
typealias B_5<B> = T<F<T<B>>>
typealias B_6<B> = F<T<T<B>>>
typealias B_7<B> = T<T<T<B>>>
typealias B_8<B> = F<F<F<T<B>>>>
typealias B_9<B> = T<F<F<T<B>>>>
typealias B_10<B> = F<T<F<T<B>>>>
typealias B_13<B> = T<F<T<T<B>>>>
typealias B_14<B> = F<T<T<T<B>>>>
typealias B_15<B> = T<T<T<T<B>>>>
typealias B_16<B> = F<F<F<F<T<B>>>>>
typealias B_17<B> = T<F<F<F<T<B>>>>>
typealias B_18<B> = F<T<F<F<T<B>>>>>
typealias B_29<B> = T<F<T<T<T<B>>>>>
typealias B_30<B> = F<T<T<T<T<B>>>>>
typealias B_31<B> = T<T<T<T<T<B>>>>>
typealias B_32<B> = F<F<F<F<F<T<B>>>>>>
typealias B_33<B> = T<F<F<F<F<T<B>>>>>>
typealias B_34<B> = F<T<F<F<F<T<B>>>>>>

typealias B0 = F<Ø>
typealias B1 = T<Ø>
typealias B2 = F<T<Ø>>
typealias B3 = T<T<Ø>>
typealias B4 = F<F<T<Ø>>>
typealias B5 = T<F<T<Ø>>>
typealias B6 = F<T<T<Ø>>>
typealias B7 = T<T<T<Ø>>>

val b0: B0 = F
val b1: B1 = T
val b2: B2 = T.F
val b3: B3 = T.T
val b4: B4 = T.F.F
val b5: B5 = T.F.T
val b6: B6 = T.T.F
val b7: B7 = T.T.T

@JvmName("bnp1") operator fun Ø.plus(t: T<Ø>) = b1
@JvmName("b0p1") operator fun B_0<Ø>.plus(t: T<Ø>) = b1
@JvmName("b1p1") operator fun B_1<Ø>.plus(t: T<Ø>): B_2<Ø> = F(x + b1)
@JvmName("b3p1") operator fun B_3<Ø>.plus(t: T<Ø>): B_4<Ø> = F(x + b1)
@JvmName("b7p1") operator fun B_7<Ø>.plus(t: T<Ø>): B_8<Ø> = F(x + b1)
@JvmName("b15p1") operator fun B_15<Ø>.plus(t: T<Ø>): B_16<Ø> = F(x + b1)
@JvmName("b31p1") operator fun B_31<Ø>.plus(t: T<Ø>): B_32<Ø> = F(x + b1)

@JvmName("b?0p1") operator fun <K: B<*, *>> B_0<K>.plus(t: T<Ø>) = T(x)
@JvmName("b?01p1") operator fun <K: B<*, *>> B_1<F<K>>.plus(t: T<Ø>) = F(x + b1)
@JvmName("b?03p1") operator fun <K: B<*, *>> B_3<F<K>>.plus(t: T<Ø>) = F(x + b1)
@JvmName("b?07p1") operator fun <K: B<*, *>> B_7<F<K>>.plus(t: T<Ø>) = F(x + b1)
@JvmName("b?015p1") operator fun <K: B<*, *>> B_15<F<K>>.plus(t: T<Ø>) = F(x + b1)
@JvmName("b?031p1") operator fun <K: B<*, *>> B_31<F<K>>.plus(t: T<Ø>) = F(x + b1)

@JvmName("b1m1") operator fun B_1<Ø>.minus(t: T<Ø>): B_0<Ø> = b0
@JvmName("b2m1") operator fun B_2<Ø>.minus(t: T<Ø>): B_1<Ø> = b1
@JvmName("b4m1") operator fun B_4<Ø>.minus(t: T<Ø>): B_3<Ø> = T(x - b1)
@JvmName("b8m1") operator fun B_8<Ø>.minus(t: T<Ø>): B_7<Ø> = T(x - b1)
@JvmName("b16m1") operator fun B_16<Ø>.minus(t: T<Ø>): B_15<Ø> = T(x - b1)
@JvmName("b32m1") operator fun B_32<Ø>.minus(t: T<Ø>): B_31<Ø> = T(x - b1)

@JvmName("b?1p1") operator fun <K: B<*, *>> B_1<K>.minus(t: T<Ø>) = F(x)
@JvmName("b?2m1") operator fun <K: B<*, *>> B_2<K>.minus(t: T<Ø>) = T(x - b1)
@JvmName("b?4m1") operator fun <K: B<*, *>> B_4<K>.minus(t: T<Ø>) = T(x - b1)
@JvmName("b?8m1") operator fun <K: B<*, *>> B_8<K>.minus(t: T<Ø>) = T(x - b1)
@JvmName("b?16m1") operator fun <K: B<*, *>> B_16<K>.minus(t: T<Ø>) = T(x - b1)
@JvmName("b?32m1") operator fun <K: B<*, *>> B_32<K>.minus(t: T<Ø>) = T(x - b1)

//@JvmName("b_p_") operator fun <K: B<*, *>> K.plus(k: K) = F(k)
//@JvmName("b_m_") operator fun <K: B<*, *>> K.minus(k: K) = F(Ø)

@JvmName("bop0p2") operator fun B_0<Ø>.plus(r: B_2<Ø>): B_2<Ø> = plus(b1) + b1
@JvmName("bop1p2") operator fun B_1<Ø>.plus(r: B_2<Ø>): B_3<Ø> = plus(b1) + b1
@JvmName("bop2p2") operator fun B_2<Ø>.plus(r: B_2<Ø>): B_4<Ø> = plus(b1) + b1
@JvmName("bop3p2") operator fun B_3<Ø>.plus(r: B_2<Ø>): B_5<Ø> = plus(b1) + b1
@JvmName("bop6p2") operator fun B_6<Ø>.plus(r: B_2<Ø>): B_8<Ø> = plus(b1) + b1
@JvmName("bop7p2") operator fun B_7<Ø>.plus(r: B_2<Ø>): B_9<Ø> = plus(b1) + b1
@JvmName("bop14p2") operator fun B_14<Ø>.plus(r: B_2<Ø>): B_16<Ø> = plus(b1) + b1
@JvmName("bop15p2") operator fun B_15<Ø>.plus(r: B_2<Ø>): B_17<Ø> = plus(b1) + b1
@JvmName("bop30p2") operator fun B_30<Ø>.plus(r: B_2<Ø>): B_32<Ø> = plus(b1) + b1
@JvmName("bop31p2") operator fun B_31<Ø>.plus(r: B_2<Ø>): B_33<Ø> = plus(b1) + b1

@JvmName("bop?00p2") operator fun <K: B<*, *>> B_0<F<K>>.plus(r: B_2<Ø>) = plus(b1) + b1
@JvmName("bop?01p2") operator fun <K: B<*, *>> B_1<F<K>>.plus(r: B_2<Ø>) = plus(b1) + b1
@JvmName("bop?02p2") operator fun <K: B<*, *>> B_2<F<K>>.plus(r: B_2<Ø>) = plus(b1) + b1
@JvmName("bop?03p2") operator fun <K: B<*, *>> B_3<F<K>>.plus(r: B_2<Ø>) = plus(b1) + b1
@JvmName("bop?06p2") operator fun <K: B<*, *>> B_6<F<K>>.plus(r: B_2<Ø>) = plus(b1) + b1
@JvmName("bop?07p2") operator fun <K: B<*, *>> B_7<F<K>>.plus(r: B_2<Ø>) = plus(b1) + b1
@JvmName("bop?014p2") operator fun <K: B<*, *>> B_14<F<K>>.plus(r: B_2<Ø>) = plus(b1) + b1
@JvmName("bop?015p2") operator fun <K: B<*, *>> B_15<F<K>>.plus(r: B_2<Ø>) = plus(b1) + b1
@JvmName("bop?030p2") operator fun <K: B<*, *>> B_30<F<K>>.plus(r: B_2<Ø>) = plus(b1) + b1
@JvmName("bop?031p2") operator fun <K: B<*, *>> B_31<F<K>>.plus(r: B_2<Ø>) = plus(b1) + b1

@JvmName("bop2m2") operator fun B_2<Ø>.minus(r: B_2<Ø>): B_0<Ø> = minus(b1) - b1
@JvmName("bop3m2") operator fun B_3<Ø>.minus(r: B_2<Ø>): B_1<Ø> = minus(b1) - b1
@JvmName("bop4m2") operator fun B_4<Ø>.minus(r: B_2<Ø>): B_2<Ø> = minus(b1) - b1
@JvmName("bop5m2") operator fun B_5<Ø>.minus(r: B_2<Ø>): B_3<Ø> = minus(b1) - b1
@JvmName("bop8m2") operator fun B_8<Ø>.minus(r: B_2<Ø>): B_6<Ø> = minus(b1) - b1
@JvmName("bop9m2") operator fun B_9<Ø>.minus(r: B_2<Ø>): B_7<Ø> = minus(b1) - b1
@JvmName("bop16m2") operator fun B_16<Ø>.minus(r: B_2<Ø>): B_14<Ø> = minus(b1) - b1
@JvmName("bop17m2") operator fun B_17<Ø>.minus(r: B_2<Ø>): B_15<Ø> = minus(b1) - b1
@JvmName("bop32m2") operator fun B_32<Ø>.minus(r: B_2<Ø>): B_30<Ø> = minus(b1) - b1
@JvmName("bop33m2") operator fun B_33<Ø>.minus(r: B_2<Ø>): B_31<Ø> = minus(b1) - b1

@JvmName("bop?2m2") operator fun <K: B<*, *>> B_2<K>.minus(r: B_2<Ø>) = minus(b1) - b1
@JvmName("bop?3m2") operator fun <K: B<*, *>> B_3<K>.minus(r: B_2<Ø>) = minus(b1) - b1
@JvmName("bop?4m2") operator fun <K: B<*, *>> B_4<K>.minus(r: B_2<Ø>) = minus(b1) - b1
@JvmName("bop?5m2") operator fun <K: B<*, *>> B_5<K>.minus(r: B_2<Ø>) = minus(b1) - b1
@JvmName("bop?8m2") operator fun <K: B<*, *>> B_8<K>.minus(r: B_2<Ø>) = minus(b1) - b1
@JvmName("bop?9m2") operator fun <K: B<*, *>> B_9<K>.minus(r: B_2<Ø>) = minus(b1) - b1
@JvmName("bop?16m2") operator fun <K: B<*, *>> B_16<K>.minus(r: B_2<Ø>) = minus(b1) - b1
@JvmName("bop?17m2") operator fun <K: B<*, *>> B_17<K>.minus(r: B_2<Ø>) = minus(b1) - b1
@JvmName("bop?32m2") operator fun <K: B<*, *>> B_32<K>.minus(r: B_2<Ø>) = minus(b1) - b1
@JvmName("bop?33m2") operator fun <K: B<*, *>> B_33<K>.minus(r: B_2<Ø>) = minus(b1) - b1


@JvmName("bop0p3") operator fun B_0<Ø>.plus(r: B_3<Ø>): B_3<Ø> = plus(b2) + b1
@JvmName("bop1p3") operator fun B_1<Ø>.plus(r: B_3<Ø>): B_4<Ø> = plus(b2) + b1
@JvmName("bop2p3") operator fun B_2<Ø>.plus(r: B_3<Ø>): B_5<Ø> = plus(b2) + b1
@JvmName("bop3p3") operator fun B_3<Ø>.plus(r: B_3<Ø>): B_6<Ø> = plus(b2) + b1
@JvmName("bop5p3") operator fun B_5<Ø>.plus(r: B_3<Ø>): B_8<Ø> = plus(b2) + b1
@JvmName("bop6p3") operator fun B_6<Ø>.plus(r: B_3<Ø>): B_9<Ø> = plus(b2) + b1
@JvmName("bop7p3") operator fun B_7<Ø>.plus(r: B_3<Ø>): B_10<Ø> = plus(b2) + b1
@JvmName("bop13p3") operator fun B_13<Ø>.plus(r: B_3<Ø>): B_16<Ø> = plus(b2) + b1
@JvmName("bop14p3") operator fun B_14<Ø>.plus(r: B_3<Ø>): B_17<Ø> = plus(b2) + b1
@JvmName("bop15p3") operator fun B_15<Ø>.plus(r: B_3<Ø>): B_18<Ø> = plus(b2) + b1
@JvmName("bop29p3") operator fun B_29<Ø>.plus(r: B_3<Ø>): B_32<Ø> = plus(b2) + b1
@JvmName("bop30p3") operator fun B_30<Ø>.plus(r: B_3<Ø>): B_33<Ø> = plus(b2) + b1
@JvmName("bop31p3") operator fun B_31<Ø>.plus(r: B_3<Ø>): B_34<Ø> = plus(b2) + b1

@JvmName("bop?00p3") operator fun <K: B<*, *>> B_0<F<K>>.plus(r: B_3<Ø>) = plus(b2) + b1
@JvmName("bop?01p3") operator fun <K: B<*, *>> B_1<F<F<K>>>.plus(r: B_3<Ø>) = plus(b2) + b1
@JvmName("bop?02p3") operator fun <K: B<*, *>> B_2<F<K>>.plus(r: B_3<Ø>) = plus(b2) + b1
@JvmName("bop?03p3") operator fun <K: B<*, *>> B_3<F<K>>.plus(r: B_3<Ø>) = plus(b2) + b1
@JvmName("bop?05p3") operator fun <K: B<*, *>> B_5<F<K>>.plus(r: B_3<Ø>) = plus(b2) + b1
@JvmName("bop?06p3") operator fun <K: B<*, *>> B_6<F<K>>.plus(r: B_3<Ø>) = plus(b2) + b1
@JvmName("bop?07p3") operator fun <K: B<*, *>> B_7<F<K>>.plus(r: B_3<Ø>) = plus(b2) + b1
@JvmName("bop?013p3") operator fun <K: B<*, *>> B_13<F<K>>.plus(r: B_3<Ø>) = plus(b2) + b1
@JvmName("bop?014p3") operator fun <K: B<*, *>> B_14<F<K>>.plus(r: B_3<Ø>) = plus(b2) + b1
@JvmName("bop?015p3") operator fun <K: B<*, *>> B_15<F<K>>.plus(r: B_3<Ø>) = plus(b2) + b1
@JvmName("bop?029p3") operator fun <K: B<*, *>> B_29<F<K>>.plus(r: B_3<Ø>) = plus(b2) + b1
@JvmName("bop?030p3") operator fun <K: B<*, *>> B_30<F<K>>.plus(r: B_3<Ø>) = plus(b2) + b1
@JvmName("bop?031p3") operator fun <K: B<*, *>> B_31<F<K>>.plus(r: B_3<Ø>) = plus(b2) + b1

@JvmName("bop3m3") operator fun B_3<Ø>.minus(r: B_3<Ø>): B_0<Ø> = minus(b2) - b1
@JvmName("bop4m3") operator fun B_4<Ø>.minus(r: B_3<Ø>): B_1<Ø> = minus(b2) - b1
@JvmName("bop5m3") operator fun B_5<Ø>.minus(r: B_3<Ø>): B_2<Ø> = minus(b2) - b1
@JvmName("bop6m3") operator fun B_6<Ø>.minus(r: B_3<Ø>): B_3<Ø> = minus(b2) - b1
@JvmName("bop8m3") operator fun B_8<Ø>.minus(r: B_3<Ø>): B_5<Ø> = minus(b2) - b1
@JvmName("bop9m3") operator fun B_9<Ø>.minus(r: B_3<Ø>): B_6<Ø> = minus(b2) - b1
@JvmName("bop10m3") operator fun B_10<Ø>.minus(r: B_3<Ø>): B_7<Ø> = minus(b2) - b1
@JvmName("bop16m3") operator fun B_16<Ø>.minus(r: B_3<Ø>): B_13<Ø> = minus(b2) - b1
@JvmName("bop17m3") operator fun B_17<Ø>.minus(r: B_3<Ø>): B_14<Ø> = minus(b2) - b1
@JvmName("bop18m3") operator fun B_18<Ø>.minus(r: B_3<Ø>): B_15<Ø> = minus(b2) - b1
@JvmName("bop32m3") operator fun B_32<Ø>.minus(r: B_3<Ø>): B_29<Ø> = minus(b2) - b1
@JvmName("bop33m3") operator fun B_33<Ø>.minus(r: B_3<Ø>): B_30<Ø> = minus(b2) - b1
@JvmName("bop34m3") operator fun B_34<Ø>.minus(r: B_3<Ø>): B_31<Ø> = minus(b2) - b1

@JvmName("bop?3m3") operator fun <K: B<*, *>> B_3<K>.minus(r: B_3<Ø>) = minus(b2) - b1
@JvmName("bop?4m3") operator fun <K: B<*, *>> B_4<K>.minus(r: B_3<Ø>) = minus(b2) - b1
@JvmName("bop?5m3") operator fun <K: B<*, *>> B_5<K>.minus(r: B_3<Ø>) = minus(b2) - b1
@JvmName("bop?6m3") operator fun <K: B<*, *>> B_6<K>.minus(r: B_3<Ø>) = minus(b2) - b1
@JvmName("bop?8m3") operator fun <K: B<*, *>> B_8<K>.minus(r: B_3<Ø>) = minus(b2) - b1
@JvmName("bop?9m3") operator fun <K: B<*, *>> B_9<K>.minus(r: B_3<Ø>) = minus(b2) - b1
@JvmName("bop?10m3") operator fun <K: B<*, *>> B_10<K>.minus(r: B_3<Ø>) = minus(b2) - b1
@JvmName("bop?16m3") operator fun <K: B<*, *>> B_16<K>.minus(r: B_3<Ø>) = minus(b2) - b1
@JvmName("bop?17m3") operator fun <K: B<*, *>> B_17<K>.minus(r: B_3<Ø>) = minus(b2) - b1
@JvmName("bop?18m3") operator fun <K: B<*, *>> B_18<K>.minus(r: B_3<Ø>) = minus(b2) - b1
@JvmName("bop?32m3") operator fun <K: B<*, *>> B_32<K>.minus(r: B_3<Ø>) = minus(b2) - b1
@JvmName("bop?33m3") operator fun <K: B<*, *>> B_33<K>.minus(r: B_3<Ø>) = minus(b2) - b1
@JvmName("bop?34m3") operator fun <K: B<*, *>> B_34<K>.minus(r: B_3<Ø>) = minus(b2) - b1


@JvmName("b_t0") operator fun <K: B<*, *>> K.times(t: F<Ø>) = t
@JvmName("b0t_") operator fun <K: B<*, *>> F<Ø>.times(t: K) = this
@JvmName("b_t1") operator fun <K: B<*, *>> K.times(t: T<Ø>) = this
@JvmName("b1t_") operator fun <K: B<*, *>> T<Ø>.times(t: K) = t
@JvmName("b_t2") operator fun <K: B<*, *>> K.times(t: B_2<Ø>) = F(this)
@JvmName("b2t_") operator fun <K: B<*, *>> B_2<Ø>.times(t: K) = F(t)
@JvmName("b_t4") operator fun <K: B<*, *>> K.times(t: B_4<Ø>) = F(F(this))
@JvmName("b4t_") operator fun <K: B<*, *>> B_4<Ø>.times(t: K) = F(F(t))
@JvmName("b_t8") operator fun <K: B<*, *>> K.times(t: B_8<Ø>) = F(F(F(this)))
@JvmName("b8t_") operator fun <K: B<*, *>> B_8<Ø>.times(t: K) = F(F(F(t)))

@JvmName("b3t3") operator fun B_3<Ø>.times(t: B_3<Ø>) = T(F(F(T(Ø))))
@JvmName("b3t5") operator fun B_3<Ø>.times(t: B_5<Ø>) = T(T(T(T(Ø))))
@JvmName("b3t6") operator fun B_3<Ø>.times(t: B_6<Ø>) = F(T(F(F(T(Ø)))))
@JvmName("b3t7") operator fun B_3<Ø>.times(t: B_7<Ø>) = T(F(T(F(T(Ø)))))
@JvmName("b5t3") operator fun B_5<Ø>.times(t: B_3<Ø>) = T(T(T(T(Ø))))
@JvmName("b5t5") operator fun B_5<Ø>.times(t: B_5<Ø>) = T(F(F(T(T(Ø)))))
@JvmName("b5t6") operator fun B_5<Ø>.times(t: B_6<Ø>) = F(T(T(T(T(Ø)))))
@JvmName("b5t7") operator fun B_5<Ø>.times(t: B_7<Ø>) = T(T(F(F(F(T(Ø))))))
@JvmName("b6t3") operator fun B_6<Ø>.times(t: B_3<Ø>) = F(T(F(F(T(Ø)))))
@JvmName("b6t5") operator fun B_6<Ø>.times(t: B_5<Ø>) = F(T(T(T(T(Ø)))))
@JvmName("b6t6") operator fun B_6<Ø>.times(t: B_6<Ø>) = F(F(T(F(F(T(Ø))))))
@JvmName("b6t7") operator fun B_6<Ø>.times(t: B_7<Ø>) = F(T(F(T(F(T(Ø))))))
@JvmName("b7t3") operator fun B_7<Ø>.times(t: B_3<Ø>) = T(F(T(F(T(Ø)))))
@JvmName("b7t5") operator fun B_7<Ø>.times(t: B_5<Ø>) = T(T(F(F(F(T(Ø))))))
@JvmName("b7t6") operator fun B_7<Ø>.times(t: B_6<Ø>) = F(T(F(T(F(T(Ø))))))
@JvmName("b7t7") operator fun B_7<Ø>.times(t: B_7<Ø>) = T(F(F(F(T(T(Ø))))))

@JvmName("b_d1") operator fun <K: B<*, *>> K.div(t: T<Ø>) = this
@JvmName("b_d_") operator fun <K> K.div(t: K) = T(Ø)
@JvmName("b_d2") operator fun <K: B<*, *>> F<K>.div(d: F<T<Ø>>) = x
@JvmName("b_d4") operator fun <K: B<*, *>> F<F<K>>.div(d: F<F<T<Ø>>>) = x.x
@JvmName("b_d8") operator fun <K: B<*, *>> F<F<F<K>>>.div(d: F<F<F<T<Ø>>>>) = x.x.x
@JvmName("b_d16") operator fun <K: B<*, *>> F<F<F<F<K>>>>.div(d: F<F<F<F<T<Ø>>>>>) = x.x.x.x

@JvmName("b6d3") operator fun F<T<T<Ø>>>.div(d: T<T<Ø>>) = F(T(Ø))
@JvmName("b9d3") operator fun T<F<F<T<Ø>>>>.div(d: T<T<Ø>>) = T(T(Ø))
@JvmName("b10d5") operator fun F<T<F<T<Ø>>>>.div(d: T<F<T<Ø>>>) = F(T(Ø))
@JvmName("b12d3") operator fun F<F<T<T<Ø>>>>.div(d: T<T<Ø>>) = F(F(T(Ø)))
@JvmName("b12d6") operator fun F<F<T<T<Ø>>>>.div(d: F<T<T<Ø>>>) = F(T(Ø))
@JvmName("b14d7") operator fun F<T<T<T<Ø>>>>.div(d: T<T<T<Ø>>>) = F(T(Ø))
@JvmName("b15d3") operator fun T<T<T<T<Ø>>>>.div(d: T<T<Ø>>) = T(F(T(Ø)))
@JvmName("b15d5") operator fun T<T<T<T<Ø>>>>.div(d: T<F<T<Ø>>>) = T(T(Ø))

@JvmName("b_p_") operator fun <K: B<*, *>, Y: B<*, *>> K.plus(y: Y) = U(toInt() + y.toInt())
@JvmName("b_m_") operator fun <K: B<*, *>, Y: B<*, *>> K.minus(y: Y) = U(toInt() - y.toInt())
@JvmName("b_t_") operator fun <K: B<*, *>, Y: B<*, *>> K.times(y: Y) = U(toInt() * y.toInt())
@JvmName("b_d_") operator fun <K: B<*, *>, Y: B<*, *>> K.div(y: Y) = U(toInt() / y.toInt())

//@JvmName("flipTT") fun <X: T<Y>, Y> X.flipAll()/*:F<???>*/= F(x.flipAll())
//@JvmName("flipFF") fun <X: F<Y>, Y> X.flipAll()/*:T<???>*/= F(x.flipAll())

// TODO: Can we express Boolean predicates >, < efficiently?
// 100100:
// 1000000* >
//  ***** <
// 11**** >
// 101*** >
// 10011* >
// 100101 >
// 1000** <

// TODO: Alternatively, we could just implement RCA/CLA in full
// https://en.wikipedia.org/wiki/Adder_(electronics)#Adders_supporting_multiple_bits
//@JvmName("bp101") infix fun <A: T<F<*>>, B: F<Ø>, C: F<Ø>> Pair<A, B>.plus(c: C): A = first
//@JvmName("bp101") infix fun <L: T<F<Q>>, Q: B<*, *>, R: F<Ø>, C: T<Ø>> Pair<L, R>.plus(c: C): F<T<F<Q>>> = F(T(first.x))
