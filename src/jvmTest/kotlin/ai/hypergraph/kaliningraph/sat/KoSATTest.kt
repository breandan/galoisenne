package ai.hypergraph.kaliningraph.sat

import org.junit.jupiter.api.Test
import org.kosat.Kosat


class KoSATTest {
/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.KoSATTest.testKoSAT"
*/
  @Test
  fun testKoSAT() {
    val solver = Kosat(mutableListOf(), 0)

    // Allocate two variables:
    solver.addVariable()
    solver.addVariable()

    // Encode TIE-SHIRT problem:
    solver.addClause(-1, 2)
    solver.addClause(1, 2)
    solver.addClause(-1, -2)
    // solver.addClause(1, -2) // UNSAT with this clause

    // Solve the SAT problem:
    val result = solver.solve()
    println("result = $result")

    // Get the model:
    val model = solver.getModel()
    println("model = $model")
  }
}