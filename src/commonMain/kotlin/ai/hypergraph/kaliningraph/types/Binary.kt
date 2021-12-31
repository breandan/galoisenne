@file:Suppress("UNUSED_PARAMETER", "UNCHECKED_CAST")
package ai.hypergraph.kaliningraph.types

sealed class B<X>(open val x: X? = null)
class T<X>(override val x: X? = null) : B<X>(x)
class F<X>(override val x: X? = null) : B<X>(x)
object E

fun B<*>?.toInt(i: Int = 0, j: Int = 1): Int =
  if (this == null) i
  else if (this is T<*>) (x as B<*>?).toInt(i + 2 * j, j * 2)
  else (x as B<*>?).toInt(i, j * 2)

val F_: F<E> = F()
val T_: T<E> = T()
val U1 = T_
val U2 = F(T_)
val U3 = T(T_)
val U4 = F(F(T_))
val U5 = T(F(T_))
val U6 = F(T(T_))
val U7 = T(T(T_))
val U8 = F(F(F(T_)))
val U9 = T(F(F(T_)))

fun T<E>.plus1(): F<T<E>> = F(T())
fun <B> T<B>.plus1(): T<B> = T(x)
fun <B> F<B>.plus1(): T<B> = T(x)
fun <B> T<F<B>>.plus1(): F<T<B>> = F(T(x!!.x))
fun T<T<E>>.plus1(): F<F<T<E>>> = F(F(T()))
fun <B> T<T<F<B>>>.plus1(): F<F<T<B>>> = F(F(T(x!!.x!!.x)))
fun T<T<T<E>>>.plus1(): F<F<F<T<E>>>> = F(F(F(T(x!!.x!!.x))))

fun main() {
  U1.plus1()
    .plus1()
    .plus1()
    .plus1()
    .plus1()
    .plus1()
    .plus1()
    .plus1()
    .plus1()
    .plus1()
    .plus1()
    .plus1()
    .plus1()
    .plus1()
}