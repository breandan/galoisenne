package ai.hypergraph.reasoning

import kotlin.test.Test
import kotlin.test.assertTrue


class KoSATTest {
/*
./gradlew jvmTest --tests "ai.hypergraph.reasoning.KoSATTest.testKoSAT1"
*/
  @Test
  fun testKoSAT1() {
    val cnf: CNF = ((-3 v 4) ʌ (3 v 4) ʌ (-3 v -4)) v ((-4 v 5) ʌ (4 v 5) ʌ (-4 v -5))
//  val cnf = (1 ʌ 2 ʌ -3) v (T as CNF)

    // Allocate two variables:
    // solver.addClause(1, -2) // UNSAT with this clause

    // Get the model:
    cnf.models.forEach {
      println("model = $it")
      assertTrue(cnf(it))
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.reasoning.KoSATTest.testIdempotentMatrixMultiplication"
*/
  @Test
  fun testIdempotentMatrixMultiplication() {
    // Boolean matrix multiplication: A * B
    //  A     B
    // 1 2   1 2
    // 3 4   3 4
    val cnf: CNF =
        (1.asCNF() eq ((1 ʌ 1) v (2 ʌ 3))) ʌ
        (2.asCNF() eq ((1 ʌ 2) v (2 ʌ 4))) ʌ
        (3.asCNF() eq ((3 ʌ 1) v (4 ʌ 3))) ʌ
        (4.asCNF() eq ((3 ʌ 2) v (4 ʌ 4)))

    // Why isn't this allowing the solution where 1, 4 = true and 2, 3 = false?

    cnf.models.forEach {
      println("model = $it")
      println("""
        |${it[1]} ${it[2]}
        |${it[3]} ${it[4]}
      """.trimMargin())
      assertTrue(cnf(it))
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.reasoning.KoSATTest.testKoSATEq"
*/
  @Test
  fun testKoSATEq() {
    val cnf: CNF = (-1).asCNF() eq 2.asCNF()

    // Get the model:
    cnf.models.forEach {
      println("model = $it")
      assertTrue(cnf(it))
    }
  }
}