package ai.hypergraph.kaliningraph.parsing

import kotlin.test.*

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.ParikhTest"
*/
class ParikhTest {
  /*
  ./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.ParikhTest.testParikhBounds"
  */
  @Test
  fun testParikhBounds() {
    val cfg = Grammars.seq2parsePythonCFG.noEpsilonOrNonterminalStubs
    val parikhMap = ParikhMap(cfg, 10)
    (1..10).forEach { i ->
      cfg.enumSeq(List(i) { "_" }).take(10).forEach {
        assertTrue(parikhMap.parikhBounds("START", i)
          ?.subsumes(it.parikhVector()) ?: false)
      }
    }
  }
}