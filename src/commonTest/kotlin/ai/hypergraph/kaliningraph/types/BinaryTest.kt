package ai.hypergraph.kaliningraph.types

import kotlin.test.*

// ./gradlew cleanTest jvmTest --tests "ai.hypergraph.kaliningraph.types.BinaryTest"
class BinaryTest {
    @Test
    fun binaryTest() {
      var i = 0
      val fifteen =
        F<Nothing>().also { assertEquals(i++, it.toInt()) }
          .plus1().also { assertEquals(i++, it.toInt()) }
          .plus1().also { assertEquals(i++, it.toInt()) }
          .plus1().also { assertEquals(i++, it.toInt()) }
          .plus1().also { assertEquals(i++, it.toInt()) }
          .plus1().also { assertEquals(i++, it.toInt()) }
          .plus1().also { assertEquals(i++, it.toInt()) }
          .plus1().also { assertEquals(i++, it.toInt()) }
          .plus1().also { assertEquals(i++, it.toInt()) }
          .plus1().also { assertEquals(i++, it.toInt()) }
          .plus1().also { assertEquals(i++, it.toInt()) }
          .plus1().also { assertEquals(i++, it.toInt()) }
          .plus1().also { assertEquals(i++, it.toInt()) }
          .plus1().also { assertEquals(i++, it.toInt()) }
          .plus1().also { assertEquals(i++, it.toInt()) }
          .plus1()

      assertEquals(T.T.T.T, fifteen)
      assertEquals(T.F.F.F.T, fifteen.plus1().plus1())
    }

  @Test
  fun ltrTest() {
    val fifteen = T.T.T.T

    assertEquals(15, fifteen.toInt())
  }
}