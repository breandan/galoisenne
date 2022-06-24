package ai.hypergraph.kaliningraph.automata

import kotlin.test.Test

class ECATest {
  /*
 ./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.automata.ECATest.testSimpleECA"
 */
  @Test
  fun testSimpleECA() {
    val init = makeVec(10)
    val ruleMat = init.genMat()
    var next = (ruleMat * init).nonlinearity().also { it.print() }
    next = (ruleMat * init).nonlinearity().also { it.print() }
    next = (ruleMat * init).nonlinearity().also { it.print() }
  }
}