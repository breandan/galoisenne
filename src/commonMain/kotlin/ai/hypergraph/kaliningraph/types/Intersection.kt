package ai.hypergraph.kaliningraph.types

// From: https://discuss.kotlinlang.org/t/current-intersection-type-options-in-kotlin/20903/3

@Suppress("BOUNDS_NOT_ALLOWED_IF_BOUNDED_BY_TYPE_PARAMETER")
// R is automatically inferred to just be T1 & T2 & T3. I used the TypeWrapper trick so that then
// the callsite doesn't have to supply a type for R since you can't explicitly write out intersection types.
inline fun <reified T1, reified T2, reified T3, reified R> Any.asIntersection3(type: TypeWrapper3<T1, T2, T3>): R?
    where R : T1, R : T2, R : T3
{
  // I tried to use takeIf here and it didn't work by the way; bummer!
  return if(this is T1 && this is T2 && this is T3) this as R else null
}
interface Interface1 {
  val i1: String
}
interface Interface2 {
  val i2: Int
}
interface Interface3 {
  val i3: Boolean
}
class Both : Interface1, Interface2, Interface3 {
  override val i1: String = "one"
  override val i2: Int = 2
  override val i3: Boolean = true
}
fun anyType() = Both() // We mask the type here just to add another layer of "Any" for fun.

fun main() {
  val bar = anyType()

  bar.asIntersection3(type<Interface1, Interface2, Interface3>())?.let { baz ->
    baz // Check the type here in Intellij with `ctrl+shift+p`
    println(baz.i1)
    println(baz.i2)
    println(baz.i3)
  }
}

class TypeWrapper3<out T1, out T2, out T3>

fun <T1, T2, T3> type(): TypeWrapper3<T1, T2, T3> = TypeWrapper3()
