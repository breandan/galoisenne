package ai.hypergraph.kaliningraph.types

import kotlin.test.Test
import kotlin.test.assertEquals

class BinaryTest {
    @Test
    fun binaryTest() {
      val fifteen = U1.plus1()
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
        .plus1().toInt()

      assertEquals(15, fifteen.toInt())
    }
}