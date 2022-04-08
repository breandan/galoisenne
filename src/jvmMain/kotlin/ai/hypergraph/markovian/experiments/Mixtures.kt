@file:Suppress("NonAsciiCharacters")

package ai.hypergraph.markovian.experiments

import umontreal.ssj.probdist.*
import kotlin.math.*
import kotlin.random.Random
import kotlin.reflect.KProperty

// TODO: https://en.wikipedia.org/wiki/Plate_notation
// cf. https://github.com/todesking/platebuilder

// https://en.wikipedia.org/wiki/Propagation_of_uncertainty#Example_formulae
// https://en.wikipedia.org/wiki/Exponential_family#Table_of_distributions
// https://en.wikipedia.org/wiki/Cumulant
// http://indico.ictp.it/event/a0143/contribution/2/material/0/0.pdf
// https://en.wikipedia.org/wiki/Stable_distribution
// https://en.wikipedia.org/wiki/Relationships_among_probability_distributions

// https://en.wikipedia.org/wiki/Algebra_of_random_variables
class RandomVariable(val distribution: Dist<*>): (Double) -> Double {
// https://en.wikipedia.org/wiki/Sum_of_normally_distributed_random_variables
// https://en.wikipedia.org/wiki/List_of_convolutions_of_probability_distributions
// https://stats.stackexchange.com/questions/21549/how-to-add-two-dependent-random-variables
// https://math.stackexchange.com/questions/1402520/pdf-of-sum-of-two-dependent-random-variables
  operator fun plus(that: RandomVariable): RandomVariable =
    RandomVariable(distribution + that.distribution)

// https://en.wikipedia.org/wiki/Product_distribution
// http://www.math.wm.edu/~leemis/2003csada.pdf
  operator fun times(that: RandomVariable): RandomVariable =
    RandomVariable(distribution * that.distribution)

  // https://en.wikipedia.org/wiki/Ratio_distribution
  operator fun div(that: RandomVariable): RandomVariable = TODO()

  infix fun given(that: RandomVariable): Nothing = TODO()

  override fun invoke(p1: Double) = distribution(p1)
}

class Gaussian(
  override val name: String = "",
  override val μ: Double = 0.1,
  override val σ: Double = 1.0
): Dist<Gaussian> {
  override val density: ContinuousDistribution = NormalDist(μ, σ)
  override fun new(name: String): Gaussian = Gaussian(name, μ, σ)

  // TODO: can we get the graph compiler to infer this?
  // https://math.stackexchange.com/questions/1112866/product-of-two-gaussian-pdfs-is-a-gaussian-pdf-but-product-of-two-gaussian-vari
  operator fun times(that: Gaussian) =
    combine(this, that) { μ1, μ2, σ1, σ2 ->
      Gaussian(
        "$name * ${that.name}",
        μ = (μ1 * σ2 * σ2 + μ2 * σ1 * σ1) / (σ1 * σ1 + σ2 * σ2),
        σ = (σ1 * σ2).pow(2) / (σ1 * σ1 + σ2 * σ2)
      )
    }

  override fun times(that: Dist<*>): Dist<*> =
    when(that) {
      is Gaussian -> that * this
      is Mixture<*> -> that * this
      else -> TODO()
    }

  override fun plus(that: Dist<*>): Dist<*> =
    when(that) {
      is Gaussian -> Mixture<Gaussian>(this) + that
      is Mixture<*> -> that + this
      else -> TODO()
    }

  companion object {
    fun combine(
      g1: Gaussian, g2: Gaussian,
      f: (Double, Double, Double, Double) -> Gaussian
    ) = f(g1.μ, g2.μ, g1.σ, g2.σ)
  }

  operator fun times(c: Double) = Gaussian(μ = c * μ, σ = c * c * σ)
  operator fun plus(c: Double) = Gaussian(μ = c + μ, σ = σ)
}

interface Dist<D: Dist<D>> : (Double) -> Double {
  open val name: String
  abstract val μ: Double
  abstract val σ: Double

  open operator fun getValue(nothing: Nothing?, property: KProperty<*>): D =
    new(property.name)
  open val density: ContinuousDistribution

  abstract fun new(name: String): D
  override fun invoke(p1: Double): Double = density.inverseF(p1)
  fun sample() = RandomVariable(this)
  open fun pdf(x: Double) = density.density(x)

  operator fun plus(that: Dist<*>): Dist<*> = TODO()
  operator fun times(that: Dist<*>): Dist<*> = TODO()
  infix fun convolve(d: Dist<*>): Dist<*> = TODO()

  /**
   * When observing new data, we should:
   *
   *  1. Recurse and bind on a match.
   *  2. Update our prior belief.
   *  3. Propagate uncertainty forward.
   */

  fun observe(vararg pairs: Pair<D, List<Double>>): D = TODO()
}

// https://arxiv.org/pdf/1901.06708.pdf

open class Mixture<T: Dist<T>>(
  override val name: String = "",
  open val components: List<Dist<*>>
): Dist<Mixture<T>> {
  constructor(vararg ds: Dist<*>): this(components = ds.toList())
  // ai.hypergraph.markovian.experiments.Uniform by default
  val weights: List<Double> by lazy {
    List(components.size) { 1.0 / components.size.toDouble() }
  }

  // https://stats.stackexchange.com/a/16609
  override val μ: Double by lazy { partition { it.μ } }
  override val σ: Double by lazy { partition { it.σ * it.σ + it.μ * it.μ } + μ * μ }

  override val density: ContinuousDistribution =
    object: ContinuousDistribution() {
      override fun cdf(x: Double) = partition { it.density.cdf(x) }
      override fun getParams() = TODO("Not yet implemented")
      override fun density(x: Double) = partition { it.density.density(x) }
    }

  class Sum<T: Dist<T>>(
    override val name: String = "",
    override val components: List<Dist<*>>
  ): Mixture<T>(name, components)

  class Prod<T: Dist<T>>(
    override val name: String = "",
    override val components: List<Dist<*>>
  ): Mixture<T>(name, components)

  @JvmName("mixPlus")
  operator fun plus(that: T): Mixture<T> =
    Sum(components = components + that)

  operator fun plus(that: Mixture<T>): Mixture<T> =
    Mixture(components = components + that.components)

  open operator fun times(that: T): Mixture<T> =
    Mixture(components = components.map { it * that })

  operator fun times(that: Mixture<T>): Mixture<T> =
    Mixture(components = components.map { it })

  override fun times(that: Dist<*>): Dist<*> =
    when(that) {
      is Gaussian -> this * that as T
      is Mixture<*> -> this * that
      else -> TODO()
    }

  override fun plus(that: Dist<*>): Dist<*> =
    when(that) {
      is Gaussian -> this + that as T
      is Mixture<*> -> this + that
      else -> TODO()
    }

  fun partition(selector: (Dist<*>) -> Double) =
    components.map { selector(it) }.zip(weights).sumByDouble { (a, b) -> a * b }

  override fun new(name: String) = Mixture<T>(name, components)
}

const val precision = 0.00000001

fun main() {
  val g20 = (0..20).map {
    Gaussian("", Random.nextDouble(0.0, 5.0), Random.nextDouble(1.0, 3.0))
  }.fold(Mixture<Gaussian>(Gaussian("", 0.1, 1.0))) { acc, gaussian ->
    if (Random.nextDouble() < 0.5) acc * gaussian else acc + gaussian
  }

  val g0 = Gaussian("", 0.1, 1.0)
  val g1 = Gaussian("", 5.0, 1.0)
  val g2 = Gaussian("", 10.0, 1.0)
//    val g2 = g0 * g1
  val g3 = g0 + g1 + g2

  val g4 = Gaussian("", 5.0, 2.0)

  val g7 = Gaussian("", 5.0, 2.0)
  val g5 = g3 * g4 + g7

  // TODO: test distributivity holds
  compare(g20).display()
//  val a  by Gaussian("", .0, 9.0)
//  val b  by Gaussian("", .0, 9.0)
//  val f1 by a * 2 + b
//  val f2 by a * 3 + b
//  val v  by Gaussian("", .5, .5)
//  val y1 by Gaussian(f1, v)
//  val y2 by Gaussian(f2, v)
//  compare(f1, f2, y3, y4).show()
}

//  P { g1 < g2 }.given()
//  u <- 1
//  val t: Boolean = true
//  val  = 1
//
//  if(g1 <- g2) {
//    println("test")
//  }
//}
//
//fun P(b: Ctx.() -> Boolean): (Int) ->  Boolean {
//  b(Ctx())
//}
//class Ctx {
//  fun given()
//}