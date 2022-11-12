package ai.hypergraph.reasoning

import kotlin.test.Test


class KoSATTest {
/*
./gradlew jvmTest --tests "ai.hypergraph.reasoning.KoSATTest.testKoSAT"
*/
  @Test
  fun testKoSAT() {
    val cnf = ((-3 v 4) ʌ (3 v 4) ʌ (-3 v -4)) v ((-4 v 5) ʌ (4 v 5) ʌ (-4 v -5))
//  val cnf = (1 ʌ 2 ʌ -3) v (T as CNF)

    // Allocate two variables:
    // solver.addClause(1, -2) // UNSAT with this clause

    // Get the model:
    println("model = ${cnf.solution}")
  }
}