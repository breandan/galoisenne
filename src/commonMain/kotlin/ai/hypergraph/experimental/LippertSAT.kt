package ai.hypergraph.experimental

// https://learn.microsoft.com/en-us/archive/blogs/ericlippert/lambda-expressions-vs-anonymous-methods-part-five

object T
object F
typealias DT = (T) -> Unit
typealias DF = (F) -> Unit
fun M(dt: DT) {
  println("true");
  dt(T)
}
fun M(df: DF) {
  println("false")
  df(F)
}
fun Or(a1: T, a2: T, a3: T): T = T
fun Or(a1: T, a2: T, a3: F): T = T
fun Or(a1: T, a2: F, a3: T): T = T
fun Or(a1: T, a2: F, a3: F): T = T
fun Or(a1: F, a2: T, a3: T): T = T
fun Or(a1: F, a2: T, a3: F): T = T
fun Or(a1: F, a2: F, a3: T): T = T
fun Or(a1: F, a2: F, a3: F): F = F
fun And(a1: T, a2: T): T = T
fun And(a1: T, a2: F): F = F
fun And(a1: F, a2: T): F = F
fun And(a1: F, a2: F): F = F
fun Not(a: T): F = F
fun Not(a: F): T = T
fun MustBeT(t: T){}

fun main() {
  // Introduce enough variables and then encode any Boolean predicate:
  // eg, here we encode (!x3) & ((!x1) & ((x1 | x2 | x1) & (x2 | x3 | x2)))
//  M({x1->M({x2->M({x3->MustBeT(
//    And(
//      Not(x3),
//      And(
//        Not(x1),
//        And(
//          Or(x1, x2, x1),
//          Or(x2, x3, x2)))))})})});
}