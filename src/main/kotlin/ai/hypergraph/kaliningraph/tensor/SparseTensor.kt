package ai.hypergraph.kaliningraph.tensor

// TODO: Naperian functors
// https://www.cs.ox.ac.uk/people/jeremy.gibbons/publications/aplicative.pdf
// "The main idea is that a rank-n array is essentially a data structure of type
// D₁(D₂(...(Dₙ a))), where each Dᵢ is a dimension : a container type, categorically
// a functor; one might think in the first instance of lists."

// Alternatively: a length-2ⁿ array which can be "parsed" into a certain shape?
// See: http://conal.net/talks/can-tensor-programming-be-liberated.pdf

interface SparseTensor<T/*Should be a named tuple or dataclass of some kind*/> {
// TODO: Precompute specific [Borel] subsets of T's attributes that we expect to be queried at runtime
// e.g. (n-1)-D slices and 1D fibers
// https://mathoverflow.net/questions/393427/generalization-of-sinkhorn-s-theorem-to-stochastic-tensors
// private val marginals: MutableMap<List<T>, Int> = mutableMapOf()
  val map: MutableMap<T, Int>
  operator fun get(t: T) = map.getOrElse(t) { 0 }

//  TODO: Support mutability but also map-reduce-ability/merge-ability for parallelism
//  operator fun plus(other: SparseTensor<T>) = SparseTensor(map = this.map + other.map)
//  operator fun MutableMap<T, Int>.plus(map: MutableMap<T, Int>): MutableMap<T, Int> =
//    HashMap(this).apply { map.forEach { (k, v) -> merge(k, v, Int::plus) } }

  operator fun set(index: T, i: Int) { map[index] = i }

  fun count(selector: (T) -> Boolean) =
    map.entries.sumOf { if(selector(it.key)) it.value else 0 }
}

fun main() {
  data class T3(val x: Int, val n: String, val c: Double)
  class SomeTensor<T>(override val map: MutableMap<T, Int> = mutableMapOf()): SparseTensor<T> {
    override fun toString() = map.entries.joinToString("\n"){ (k, v) ->"$k to $v" }
  }
  // Probability DSL for Markovian
  fun <T> SparseTensor<T>.P(that: (T) -> Boolean, given: (T) -> Boolean = { true }) =
    map.entries.fold(0 to 0) { (n, d), (k, v) ->
      val (a, b) = given(k) to that(k)
      when {
        a && b -> n + v to d + v
        a -> n to d + v
        else -> n to d
      }
    }.let { (n, d) -> n.toDouble() / d.toDouble().coerceAtLeast(1.0) }

  val spt = SomeTensor<T3>()

  spt[T3(x = 1, n = "b", c = 3.0)]++
  spt[T3(x = 1, n = "b", c = 3.0)]++
  spt[T3(x = 3, n = "a", c = 2.1)]++
  spt[T3(x = 2, n = "a", c = 2.1)]++
  spt[T3(x = 2, n = "b", c = 3.0)]++

  val condProb = spt.P(that = { it.x == 1 }, given = { it.n == "b" })
  println("Query: $condProb")

  println(spt.toString())
}