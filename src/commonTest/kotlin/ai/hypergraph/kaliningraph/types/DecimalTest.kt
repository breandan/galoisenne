package ai.hypergraph.kaliningraph.types

import kotlin.test.Test
import kotlin.test.assertEquals

class DecimalTest {
    @Test
    fun binaryTest() {
      val thirtytwo = _9_
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

      assertEquals(32, thirtytwo.toInt())
    }
}