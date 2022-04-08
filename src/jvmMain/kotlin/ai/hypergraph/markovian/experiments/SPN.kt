package ai.hypergraph.markovian.experiments//import kotlin.reflect.KProperty
//
//// https://hanzhaoml.github.io/papers/ICML2015/SPN-supplementary.pdf
//// http://proceedings.mlr.press/v37/zhaoc15.pdf
//// https://cedar.buffalo.edu/~srihari/CSE674/Chap9/9.3-VE-Algorithm.pdf#page=24
//
//sealed class SPN(open val name: String? = null) {
//  open operator fun plus(that: SPN): SPN = SNode(this, that)
//  open operator fun times(that: SPN): SPN = PNode(this, that)
//
//  operator fun invoke(vararg pairs: Pair<SPN, GaussianMixture>): SPN =
//    invoke(pairs.toMap())
//
//  operator fun invoke(map: Map<SPN, GaussianMixture>): SPN =
//    when (this) {
//      is Dist -> this
//      is Leaf -> map[this]?.let { Dist(it) } ?: this
//      is SNode -> left(map) + right(map)
//      is PNode -> left(map) * right(map)
//    }
//
//  fun toDist() = (this as Dist).g
//
//  operator fun getValue(nothing: Nothing?, property: KProperty<*>) =
//    when (this) {
//      is Leaf -> Leaf(property.name)
//      else -> this
//    }
//
//  override fun toString(): String = when (this) {
//    is Dist -> g.toString()
//    is Leaf -> super.toString()
//    is SNode -> "$left + $right"
//    is PNode -> "$left * $right"
//  }
//}
//
//class Leaf(override val name: String? = null): SPN(name) {
//  override fun equals(other: Any?) = (other as? Leaf)?.name == name ?: false
//  override fun hashCode() = name.hashCode()
//}
//
//class Dist(val g: GaussianMixture): SPN() {
//  override fun plus(that: SPN): SPN = TODO()
////    if (that is Dist) Dist(g + that.g) else super.plus(that)
//
//  override fun times(that: SPN): SPN =
//    if (that is Dist) Dist(g * that.g) else super.plus(that)
//}
//
//class SNode(val left: SPN, val right: SPN): SPN()
//class PNode(val left: SPN, val right: SPN): SPN()
//
//
//fun main() {
//  val a by Leaf()
//  val b by Leaf()
//  val c by Leaf()
//  val x by Gaussian()
//  val y by Gaussian()
//  val z by Gaussian()
//
//  val spn1 = a * (b + c)
//  val res1 = spn1(a to x, b to y, c to z)
//
//  val spn2 = a * b + a * c
//  val res2: SPN = spn2.invoke(a to x, b to y, c to z)
//
//  compare(res1.toDist(), res2.toDist()).display()
//}