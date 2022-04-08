package ai.hypergraph.markovian.experiments

import ai.hypergraph.kaliningraph.types.*
import jetbrains.letsPlot.*
import jetbrains.letsPlot.geom.*
import umontreal.ssj.probdist.NormalDist
import umontreal.ssj.randvar.*
import umontreal.ssj.rng.F2NL607
import kotlin.random.Random

fun main() {
  val rand = F2NL607()

  val a = NormalGen(rand, NormalDist(1.0, 0.1))
  val b = NormalGen(rand, NormalDist(0.0, 0.1))
  val v = GammaGen(rand, 0.01, 0.01)

  val ts = (0..20).map {
    a.nextDouble() to b.nextDouble()
  }
  println(ts)

  val points = (0..20).map {
    val x = Random.nextDouble()
    x to a.nextDouble() * x + b.nextDouble() +
    NormalGen(rand, NormalDist(0.0, v.nextDouble())).nextDouble()
  }
  println(points)

//  plot(ts, points)

  val compare = (0..10000).map { a.nextDouble() cc b.nextDouble() }
  val (at, bt) = compare.unzip()

  val yt = compare.map { (a, b) ->
    val x = Random.nextDouble()
    (a * x + b + NormalGen(rand, NormalDist(0.0, v.nextDouble().coerceAtLeast(0.01))).nextDouble()).coerceIn(-3.0, 3.0)
  }

  compare(at, bt, yt).display()
}

fun plot(samples: List<V2<Double>>, points: List<V2<Double>>) =
  ggplot(
    mapOf(
      "a" to samples.unzip().first,
      "b" to samples.unzip().second,
      "x" to points.unzip().first,
      "y" to points.unzip().second
    )
  ) { x = "x"; y = "y" }.let {
    samples.fold(it) { plot, (a, b) ->
      plot + geomABLine(alpha = .1, slope = a, intercept = b)
    } + ggsize(500, 250) + geomPoint(color = "red", shape = 21, fill = "red", size = 5)
//    it + geomPoint(shape=1)
  }.display()