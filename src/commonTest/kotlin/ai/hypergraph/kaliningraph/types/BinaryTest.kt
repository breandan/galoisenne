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
          .plus2().also { i+=2; assertEquals(i, it.toInt()) }
          .plus3().also { i+=3; assertEquals(i, it.toInt()) }
          .plus2().also { i+=2; assertEquals(i, it.toInt()) }
          .plus1().also { assertEquals(i++, it.toInt()) }
          .plus1().also { assertEquals(i++, it.toInt()) }
          .plus1().also { assertEquals(i++, it.toInt()) }
          .plus1()

      assertEquals(T.T.T.T, fifteen)
      assertEquals(T.F.F.F.T, fifteen.plus1().plus1())
    }

  @Test
  fun binaryPlus2Test() {
    val thirteen =
      T.also { assertEquals(1, it.toInt()) }
        .plus2().also { assertEquals(3, it.toInt()) }
        .plus2().also { assertEquals(5, it.toInt()) }
        .plus2().also { assertEquals(7, it.toInt()) }
        .plus2().also { assertEquals(9, it.toInt()) }
        .plus2().also { assertEquals(11, it.toInt()) }
        .plus2().also { assertEquals(13, it.toInt()) }

    assertEquals(T.T.F.T, thirteen)

    val twelve =
      F.also { assertEquals(0, it.toInt()) }
        .plus2().also { assertEquals(2, it.toInt()) }
        .plus2().also { assertEquals(4, it.toInt()) }
        .plus2().also { assertEquals(6, it.toInt()) }
        .plus2().also { assertEquals(8, it.toInt()) }
        .plus2().also { assertEquals(10, it.toInt()) }
        .plus2().also { assertEquals(12, it.toInt()) }

  }

  @Test
  fun shlTest() {
    assertEquals(T.T.shl().shl(), T.T.F.F)
  }

  @Test
  fun ltrTest() {
    val fifteen = T.T.T.T

    assertEquals(15, fifteen.toInt())
  }

  @Test
  fun plusMinusTest() {
    val t0: T<T<Ø>> = T(T(Ø))
    val t1: T<T<Ø>> = F(F(T(Ø))).minus3().plus2()
    assertEquals(t0, t1)
    val t2: T<T<Ø>> = t1.minus1().plus1()
    assertEquals(t1, t2)
    val t3: T<T<Ø>> = t2.minus2().plus1().plus1()
    assertEquals(t2, t3)
  }

//  @Test
//  fun flipAllTest() {
//    val q = T(T(F(T(F)))).flipAll()
//  }
}