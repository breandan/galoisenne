package ai.hypergraph.kaliningraph.types

import kotlin.test.*

// ./gradlew cleanTest jvmTest --tests "ai.hypergraph.kaliningraph.types.BinaryTest"
class BinaryTest {
    @Test
    fun binaryMixedTest() {
      var i = 0
      val fifteen =
        F.also { assertEquals(i++, it.toInt()) }
          .plus1().also { assertEquals(i++, it.toInt()) }
          .plus1().also { assertEquals(i++, it.toInt()) }
          .plus1().also { assertEquals(i++, it.toInt()) }
          .plus1().also { assertEquals(i++, it.toInt()) }
          .plus2().also { i++; assertEquals(i++, it.toInt()) }
          .plus1().also { assertEquals(i++, it.toInt()) }
          .plus1().also { assertEquals(i++, it.toInt()) }
          .plus1().also { assertEquals(i++, it.toInt()) }
          .plus2().also { i++; assertEquals(i++, it.toInt()) }
          .plus1().also { assertEquals(i++, it.toInt()) }
          .plus1().also { assertEquals(i++, it.toInt()) }
          .plus1().also { assertEquals(i++, it.toInt()) }
          .plus1()

      assertEquals(T.T.T.T, fifteen)
      assertEquals(T.F.F.F.T, fifteen.plus1().plus1())
    }

  @Test
  fun binaryPlus2Test() {
    val thirteen: T<F<T<T<Ø>>>> =
      T.also { assertEquals(1, it.toInt()) }
        .plus2().also { assertEquals(3, it.toInt()) }
        .plus2().also { assertEquals(5, it.toInt()) }
        .plus2().also { assertEquals(7, it.toInt()) }
        .plus2().also { assertEquals(9, it.toInt()) }
        .plus2().also { assertEquals(11, it.toInt()) }
        .plus2().also { assertEquals(13, it.toInt()) }

    assertEquals(T.T.F.T, thirteen)

    val twelve: F<F<T<T<Ø>>>> =
      F.also { assertEquals(0, it.toInt()) }
        .plus2().also { assertEquals(2, it.toInt()) }
        .plus2().also { assertEquals(4, it.toInt()) }
        .plus2().also { assertEquals(6, it.toInt()) }
        .plus2().also { assertEquals(8, it.toInt()) }
        .plus2().also { assertEquals(10, it.toInt()) }
        .plus2().also { assertEquals(12, it.toInt()) }

    assertEquals(T.T.F.F, twelve)
  }

  @Test
  fun ltrTest() {
    val fifteen = T.T.T.T

    assertEquals(15, fifteen.toInt())
  }
}