package ai.hypergraph.kaliningraph.types

import org.junit.jupiter.api.Test

// ./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.types.GraphTest"
class GenBinTest {
  @Test
  fun genBinTest() {
    (0..10).forEach { a ->
      (0..a).forEach { b ->
        println(Integer.toBinaryString(a).reversed() + " + " + Integer.toBinaryString(b).reversed() + " = " + Integer.toBinaryString(a + b).reversed())
      }
    }
  }
}