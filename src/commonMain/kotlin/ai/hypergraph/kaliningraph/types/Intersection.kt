package ai.hypergraph.kaliningraph.types

import kotlin.reflect.KClass

// From: https://discuss.kotlinlang.org/t/current-intersection-type-options-in-kotlin/20903/3

@Suppress("BOUNDS_NOT_ALLOWED_IF_BOUNDED_BY_TYPE_PARAMETER")
// R is automatically inferred to just be T1 & T2 & T3.
inline fun <reified T1: Any, reified T2: Any, reified T3: Any, reified R>
    Any.asIntersection3(t1: KClass<T1>, t2: KClass<T2>, t3: KClass<T3>): R? where R : T1, R : T2, R : T3 =
  if (this is T1 && this is T2 && this is T3) this as R else null

interface Interface1 { val i1: String get() = "one" }
interface Interface2 { val i2: Int get() = 2 }
interface Interface3 { val i3: Boolean get() = true }

class Both : Interface1, Interface2, Interface3

class TypeWrapper3<out T1, out T2, out T3>

fun <T1, T2, T3> type(): TypeWrapper3<T1, T2, T3> = TypeWrapper3()
