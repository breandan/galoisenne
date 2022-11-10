package ai.hypergraph.kaliningraph.sat

import org.junit.jupiter.api.Test
import org.kosat.Kosat
import ai.hypergraph.reasoning.*


class KoSATTest {
/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.KoSATTest.testKoSAT"
*/
  @Test
  fun testKoSAT() {
  val cnf = (-1 lor 2) land
      (1 lor 2) land
      (-1 lor -2)

    val solver = cnf.solver

    // Allocate two variables:
    // solver.addClause(1, -2) // UNSAT with this clause

    // Solve the SAT problem:
    val result = solver.solve()
    println("result = $result")

    // Get the model:
    val model = solver.getModel()
    println("model = $model")
  }
}