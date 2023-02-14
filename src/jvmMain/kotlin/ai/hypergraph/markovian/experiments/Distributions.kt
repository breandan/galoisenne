@file:Suppress("NonAsciiCharacters")

package ai.hypergraph.markovian.experiments

import umontreal.ssj.probdist.*
import kotlin.math.pow

// TODO: re-enable pending https://youtrack.jetbrains.com/issue/KT-56646
class Uniform(
  override val name: String = "",
  val lo: Double = 0.0,
  val hi: Double = 1.0,
  override val μ: Double = (hi - lo) / 2.0,
  override val σ: Double = (hi - lo).pow(2) / 12.0
): Dist<Uniform> {
  override val density: ContinuousDistribution = UniformDist(lo, hi)
  override fun new(name: String): Uniform = Uniform(name, lo, hi)
}

class Beta(
  override val name: String = "",
  val α: Double = 2.0,
  val β: Double = 2.0,
  override val μ: Double = α / (α + β),
  override val σ: Double = α * β / ((α + β).pow(2) * (α + β + 1))
): Dist<Beta> {
  override val density: ContinuousDistribution = BetaDist(α, β)
  override fun new(name: String): Beta = Beta(name, α, β)
}

tailrec fun <D: Dist<D>> Dist<D>.cdf(
  z: Double,
  sum: Double = 0.0,
  term: Double = z,
  i: Int = 3
): Double =
  when {
    z < -8.0 -> 0.0
    z > 8.0 -> 1.0
    sum + term == sum -> 0.5 + sum * pdf(z)
    else -> cdf(z, sum + term, term * z * z / i, i + 2)
  }

// Binary search root-finder
tailrec fun <D: Dist<D>> Dist<D>.invcdf(
  y: Double, lo: Double = -4.0, hi: Double = 8.0,
  mid: Double = lo + (hi - lo) / 2
): Double = when {
  hi - lo < precision -> mid
  cdf(mid) < y -> invcdf(y, mid, hi)
  else -> invcdf(y, lo, mid)
}