package ai.hypergraph.kaliningraph.types

import kotlin.test.*

class ArrayTest {
  @Test
  fun vectorTest() {
    val t = VT(1, 2, 3)
    assertEquals(S3, t.len)
    assertEquals(3, t.size)
//  val e4 = t[S4] // Compile error
    val e3 = t[S3] // Okay

    assertEquals(3, e3)
    assertEquals(1, t[S1])

    val r = t.take3()
//    t.take4() // Compile error

    val t2 = t cc t.take2()
    assertEquals(S5, t2.len)
    assertEquals(2, t2[S5])

    t2[S2..S4]
//    t2.subvec(S2, S1) // Compile error

    val t3 = t2.drop3().append(0)
    assertEquals(2, t3[S2])
    assertEquals(S3, t3.len)
  }

  @Test
  fun naperianVTtorTest() {
    val t: TS4<Int> = TV(1, 2, 3, 4)
    assertEquals(S4, t.len())
//  val e4 = t[S5] // Compile error
    val e3 = t[S3] // Okay

    assertEquals(3, e3)
    assertEquals(t[S1], 1)

    val r = t.take4().drop2()
//    t.take5() // Compile error

    assertEquals(3, r[S1])
    assertEquals(S2, r.len())
  }

  @Test
  fun matMulTest() {
    val m3x3 = VT(VT(1, 2, 3), VT(1, 2, 3), VT(1, 2, 3))
    val m3x2 = VT(VT(1, 2), VT(1, 2), VT(1, 2))
    val m2x2 = VT(VT(1, 2), VT(1, 2))

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