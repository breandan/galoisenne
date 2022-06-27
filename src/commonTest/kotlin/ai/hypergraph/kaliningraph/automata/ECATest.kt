package ai.hypergraph.kaliningraph.automata

import kotlin.test.Test

class ECATest {
/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.automata.ECATest.testSimpleECA"
*/
  @Test
  fun testSimpleECA() = makeVec(20).evolve()
}