package ai.hypergraph.kaliningraph.types

import kotlin.test.Test
import kotlin.test.assertEquals

class PeanoTest {
  @Test
  fun peanoTest() {
    val five = O
      .plus2()
      .let { it + S3 }
      .plus4()
      .minus3()
      .minus3()
      .let { it + it }
      .minus3()
      .let { it * S2 }
      .minus4()
      .let { it * it }
      .let { it - S2 }
      .let { it + S3 }

    assertEquals(5, five.toInt())
  }

  @Test
  fun arrayTest() {
    val t = V(1, 2, 3)
//  val e4 = t[S4] // Compile error
    val e3 = t[S3] // Okay

    assertEquals(3, e3)

    val m3x3 = V(V(1, 2, 3), V(1, 2, 3), V(1, 2, 3))
    val m3x2 = V(V(1, 2), V(1, 2), V(1, 2))

    m3x3.times(m3x2)
//  m3x2.times(m3x2) // Compile error
  }

  @Test
  fun takeTest() {
    val q = V(1, 2, 3, 4)
    val r = q.take2(from=S0)
//    q.take(from=S2, to=S1) // Compile error
  }
}