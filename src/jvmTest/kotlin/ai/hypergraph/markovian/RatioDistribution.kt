package ai.hypergraph.markovian

import kotlin.math.*

// https://en.wikipedia.org/wiki/Ratio_distribution#Means_and_variances_of_random_ratios
fun main() {
  val t1 = (0..10).map { Math.random() }
  val t2 = (0..10).map { Math.random() }

  val t2_t1_var = t2.zip(t1).map { (a, b) -> a / b }.variance()
  println(t2_t1_var)

  // σ²[t2/t1] == 𝔼([t2/t1]²) - 𝔼²(t2/t1)
  val var_t2_t1 = t2.zip(t1).let {
    it.map { (a, b) -> (a / b).pow(2) }.average() -
      it.map { (a, b) -> (a / b) }.average().pow(2)
  }

  println(var_t2_t1)
}