package ai.hypergraph.reasoning

import kotlin.test.Test


class KoSATTest {
/*
./gradlew jvmTest --tests "ai.hypergraph.reasoning.KoSATTest.testKoSAT"
*/
  @Test
  fun testKoSAT() {
    val cnf = (-1 v 2) ʌ (1 v 2) ʌ (-1 v -2)
//  val cnf = (1 ʌ 2) eq (1 ʌ 2 v 3)

    // Allocate two variables:
    // solver.addClause(1, -2) // UNSAT with this clause

    // Get the model:
    println("model = ${cnf.solution}")
  }
}