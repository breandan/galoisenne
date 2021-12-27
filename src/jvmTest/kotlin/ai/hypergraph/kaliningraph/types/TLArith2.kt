package ai.hypergraph.kaliningraph.types

import kotlin.reflect.KType
import kotlin.reflect.full.allSupertypes
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.typeOf

fun main() {
    val ab = op(object : B {}, object : C {})
    println(ab.i)
}

interface Z { val i get() = 0 }
interface A: Z { override val i get() = 1 }
interface B: A { override val i get() = 2 }
interface C: A, D { override val i get() = 3 }
interface D { val i get() = 4 }

fun <A: Z, B: Z, C: Z, Z> op(a: A, b: B): C = join(a, b)
fun <A: Z, B: Z, C: Z, Z> join(a: A, b: B): C =
    meet(a!!::class.allSupertypes, b!!::class.allSupertypes) as C

fun meet(la: Collection<KType>, lb: Collection<KType>,
): Z = la.intersect(lb).first().run {
    when {
        isA<D>() -> object: D{}
        isA<C>() -> object: C{}
        isA<B>() -> object: B{}
        isA<A>() -> object: A{}
        isA<Z>() -> object: Z{}
        else -> TODO()
    }
} as Z

inline fun <reified T> KType.isA() = isSubtypeOf(typeOf<T>())