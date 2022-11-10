package ai.hypergraph.reasoning

import kotlin.test.Test


class KoSATTest {
/*
./gradlew jvmTest --tests "ai.hypergraph.reasoning.KoSATTest.testKoSAT"
*/
  @Test
  fun testKoSAT() {
    val cnf = (-1 v 2) ʌ (1 v 2) ʌ (-1 v -2)

    // Allocate two variables:
    // solver.addClause(1, -2) // UNSAT with this clause

    // Solve the SAT problem:
    println("result = ${cnf.solution}")

    // Get the model:
    println("model = ${cnf.solution}")
  }
}