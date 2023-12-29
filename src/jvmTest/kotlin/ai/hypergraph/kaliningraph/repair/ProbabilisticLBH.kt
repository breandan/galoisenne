package ai.hypergraph.kaliningraph.repair

import ai.hypergraph.kaliningraph.parsing.*
import ai.hypergraph.kaliningraph.repair.TIMEOUT_MS
import ai.hypergraph.kaliningraph.sampling.*
import ai.hypergraph.kaliningraph.tokenizeByWhitespace
import org.junit.jupiter.api.Test
import parallelize
import repairInParallel
import java.io.File
import java.util.stream.*
import kotlin.streams.*
import kotlin.test.*
import kotlin.time.*

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.repair.ProbabilisticLBH"
*/
class ProbabilisticLBH {
  val ceaDist by lazy { File("src/jvmTest/resources/context_edits.csv").readTrigramStats() }
/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.repair.ProbabilisticLBH.testProbabilisticLBH"
*/
  @Test
  fun testProbabilisticLBH() {
  }
}