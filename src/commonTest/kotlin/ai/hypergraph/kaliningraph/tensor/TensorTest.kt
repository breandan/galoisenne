package ai.hypergraph.kaliningraph.tensor

import kotlin.test.Test

class TensorTest {
  @Test
  fun tensorTest() {
    data class T3(val x: Int, val n: String, val c: Double)
    class SomeTensor<T>(override val map: MutableMap<T, Int> = mutableMapOf()) : SparseTensor<T> {
      override fun toString() = map.entries.joinToString("\n") { (k, v) -> "$k to $v" }
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
}