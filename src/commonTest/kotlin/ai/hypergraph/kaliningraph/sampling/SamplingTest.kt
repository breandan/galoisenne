package ai.hypergraph.kaliningraph.sampling

import ai.hypergraph.kaliningraph.choose
import ai.hypergraph.kaliningraph.types.times
import kotlin.math.pow
import kotlin.test.*

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sampling.SamplingTest"
*/
class SamplingTest {
/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sampling.SamplingTest.testExhaustiveSearch"
*/
  @Test
  fun testExhaustiveSearch() =
    // Checks whether the exhaustive search is truly exhaustive
    ((2..5) * (2..5)).forEach { (s, dim) ->
      val base = (0 until s).map { "${it.digitToChar()}" }.toSet()
      val sfc = findAll(base, dim)
      assertEquals(s.toDouble().pow(dim).toInt(), sfc.toList().size)
      assertEquals(s.toDouble().pow(dim).toInt(), sfc.distinct().toList().size)
    }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sampling.SamplingTest.testLFSR"
*/
  @Test
  fun testLFSR() =
    (0..10).forEach { i ->
      val list = LFSR(i).toList()
      val distinct = list.distinct()
      println("$i: ${list.size + 1} / ${2.0.pow(i).toInt()}")
      assertEquals(2.0.pow(i).toInt(), list.size + 1)
      assertEquals(2.0.pow(i).toInt(), distinct.size + 1)
    }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sampling.SamplingTest.testMDSampler"
*/
  @Test
  fun testMDSampler() =
    ((4..6 step 2) * (4..6)).forEach { (s, dim) ->
      val base = (0 until s).map { "${it.digitToChar()}" }.toSet()
      val sfc = MDSamplerWithoutReplacement(base, dim)
      assertEquals(s.toDouble().pow(dim).toInt(), sfc.toList().size)
      assertEquals(s.toDouble().pow(dim).toInt(), sfc.distinct().toList().size)
    }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sampling.SamplingTest.testCombos"
*/
  @Test
  fun testCombos() {
    val (n, k) = 5 to 3
    val combos = (1 .. n).map { "$it" }.toSet().choose(k).toList()
    println(combos)
    assertEquals(n choose k, combos.size)
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sampling.SamplingTest.testMaculayRepresentation"
*/
  @Test
  fun testMaculayRepresentation() {
    (0..100).forEach { i ->
      assertEquals(
        i.decodeCombo(3).also { print("\n$i => $it") },
        i.decodeCombo(3).encode().also { print(" => $it") }
          .decodeCombo(3).also { print(" => $it") }
      )
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sampling.SamplingTest.testSzudzikBijection"
*/
  @Test
  fun testSzudzikBijection() {
    (0..27).map { it.untupled(3) }.forEach { println(it) }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sampling.SamplingTest.testMDSamplerNK"
*/
  @Test
  fun testMDSamplerNK() {
    val alphabet = setOf("x", "o")
    val totalKCombinations= 6 choose 3
    val totalTuples= alphabet.size.toDouble().pow(3).toInt()
    MDSamplerWithoutReplacementNK(alphabet, 6, 3)
      .map { println("(${it.first.joinToString(",")}) / [${it.second.joinToString(",")}]"); it }.toList()
      .also { assertEquals(totalKCombinations * totalTuples, it.size) }
      .also { assertEquals(it, it.distinct()) }
  }
}