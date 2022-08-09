package ai.hypergraph.kaliningraph.tensor

import ai.hypergraph.kaliningraph.types.Ring
import kotlin.test.Test
import kotlin.test.assertEquals

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.tensor.UTMatrixTest"
*/
class UTMatrixTest {
  @Test
  fun testIdentity() {
    val im = FreeMatrix(List(4 * 4) { it })
    val um = im.toUTMatrix()

    assertEquals(um.toFullMatrix().toUTMatrix(), um)
    assertEquals(um.toUTMatrix(), um)
    assertEquals(um.data, um.toFullMatrix().data)
  }

  @Test
  fun testAddition() {
    val im = FreeMatrix(INTEGER_FIELD, List(4 * 4) { it })
    val um = im.toUTMatrix().also { println("um: $it") }
    val fm = um.toFullMatrix().also { println("fm: $it") }

    assertEquals(um + um, (fm + fm).toUTMatrix())
  }

  @Test
  fun testMultiplication() {
    val im = FreeMatrix(INTEGER_FIELD, List(4 * 4) { it })
    val um = im.toUTMatrix().also { println("um: $it") }
    val fm = um.toFullMatrix().also { println("fm: $it") }

    assertEquals(um * um, (fm * fm).toUTMatrix())
  }
}