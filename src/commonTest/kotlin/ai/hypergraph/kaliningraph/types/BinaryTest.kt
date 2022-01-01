package ai.hypergraph.kaliningraph.types

import kotlin.test.*

class BinaryTest {
    @Test
    fun binaryTest() {
      val fifteen = T<Nothing>().plus1()
        .plus1()
        .plus1()
        .plus1()
        .plus1()
        .plus1()
        .plus1()
        .plus1()
        .plus1()
        .plus1()
        .plus1()
        .plus1()
        .plus1()
        .plus1()

      assertEquals(15, fifteen.toInt())
    }

  @Test
  fun ltrTest() {
    val fifteen = T.T.T.T

    assertEquals(15, fifteen.toInt())
  }
}