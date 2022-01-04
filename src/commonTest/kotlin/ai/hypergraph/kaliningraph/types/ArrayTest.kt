package ai.hypergraph.kaliningraph.types

import kotlin.test.*

class ArrayTest {
  @Test
  fun vectorTest() {
    val t: V3<Int> = Vec(1, 2, 3)
    assertEquals(3, t.size)
//  val e4 = t[S4] // Compile error
    val e3 = t[S3] // Okay

    assertEquals(3, e3)
    assertEquals(t[S1], 1)

    val r = t.take3()
//    t.take4() // Compile error
  }

  @Test
  fun naperianVectorTest() {
    val t: Ts3<Int> = TV(1, 2, 3)
    assertEquals(3, t.size())
//  val e4 = t[S4] // Compile error
    val e3 = t[S3] // Okay

    assertEquals(3, e3)
    assertEquals(t[S1], 1)

    val r = t.take3()
//    r.take4() // Compile error
  }

  @Test
  fun matMulTest() {
    val m3x3 = Vec(Vec(1, 2, 3), Vec(1, 2, 3), Vec(1, 2, 3))
    val m3x2 = Vec(Vec(1, 2), Vec(1, 2), Vec(1, 2))
    val m2x2 = Vec(Vec(1, 2), Vec(1, 2))

    try { m3x3 * m3x2 * m2x2 } catch (e: NotImplementedError) {}
//  m3x2 * m3x2 // Compile error
  }

  @Test
  fun naperianMatTest() {
    val m3x3: TM3x3<Int> = TV(TV(1, 2, 3), TV(1, 2, 3), TV(1, 2, 3))
    val m3x2: TM3x2<Int> = TV(TV(1, 2), TV(1, 2), TV(1, 2))
    val m2x2: TM2x2<Int> = TV(TV(1, 2), TV(1, 2))

//    try { m3x3 * m3x2 * m2x2 } catch (e: NotImplementedError) {}
//  m3x2 * m3x2 // Compile error
  }
}