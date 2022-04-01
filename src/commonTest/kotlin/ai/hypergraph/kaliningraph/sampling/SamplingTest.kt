package ai.hypergraph.kaliningraph.sampling

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
  fun testExhaustiveSearch() {
    // Checks whether the exhaustive search is truly exhaustive
    ((2..5).toSet() * (2..5).toSet()).forEach { (s, dim) ->
      val base = (0 until s).map { it.digitToChar().toString() }.toSet()
      val sfc = exhaustiveSearch(base, dim)
      assertEquals(s.toDouble().pow(dim).toInt(), sfc.toList().size)
      assertEquals(s.toDouble().pow(dim).toInt(), sfc.distinct().toList().size)
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sampling.SamplingTest.testLFSR"
*/
  @Test
  fun testLFSR() {
    // Tests whether LFSR cycles through its maximal period
    for (i in 4..10) {
      val list = LFSR(i).toList()
      val distinct = list.distinct()
      println("$i: ${list.size + 1} / ${2.0.pow(i).toInt()}")
      assertEquals(2.0.pow(i).toInt(), list.size + 1)
      assertEquals(2.0.pow(i).toInt(), distinct.size + 1)
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sampling.SamplingTest.testMDSampler"
*/
  @Test
  fun testMDSampler() {
    ((4..6 step 2).toSet() * (4..6).toSet()).forEach { (s, dim) ->
      val base = (0 until s).map { it.digitToChar().toString() }.toSet()
      val sfc = MDSamplerWithoutReplacement(base, dim)
      assertEquals(s.toDouble().pow(dim).toInt(), sfc.toList().size)
      assertEquals(s.toDouble().pow(dim).toInt(), sfc.distinct().toList().size)
    }
  }
}