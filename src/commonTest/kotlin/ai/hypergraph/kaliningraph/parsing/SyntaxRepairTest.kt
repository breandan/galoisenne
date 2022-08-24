package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.sampling.choose
import kotlin.test.Test
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SyntaxRepairTest"
*/
class SyntaxRepairTest {
/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SyntaxRepairTest.testLazySortTime"
*/
  @ExperimentalTime
  @Test
  fun testLazySortTime() {
    val seq = (0..30).joinToString(" ")
      .multiTokenSubstitutionsAndInsertions(fishyLocations = (30 downTo 0 step 4).toList())
    measureTime { seq.take(100).forEach { println(it) } }.also { println("Finished in ${it.inWholeMilliseconds}ms") }
  }
}
