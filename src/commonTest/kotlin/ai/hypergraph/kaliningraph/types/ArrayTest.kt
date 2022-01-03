package ai.hypergraph.kaliningraph.types

import kotlin.test.*

public class ArrayTest {
  @Test
  fun arrayTest() {
    val t: P<Int, P<Int, P<Int, Nothing>>> = PVec(1, 2, 3)
    assertEquals(3, t.size())
//  val e4 = t[S4] // Compile error
    val e3 = t[S3] // Okay

    assertEquals(3, e3)

    val m3x3 = Vec(Vec(1, 2, 3), Vec(1, 2, 3), Vec(1, 2, 3))
    val m3x2 = Vec(Vec(1, 2), Vec(1, 2), Vec(1, 2))
    val m2x2 = Vec(Vec(1, 2), Vec(1, 2))
    assertEquals(t[S1], 1)

    try { m3x3 * m3x2 * m2x2 } catch (e: NotImplementedError) {}
//  m3x2 * m3x2 // Compile error
  }

  @Test
  fun takeTest() {
    val q = PVec(1, 2, 3)
    val r = q.take3()
//    q.take4() // Compile error
  }
}