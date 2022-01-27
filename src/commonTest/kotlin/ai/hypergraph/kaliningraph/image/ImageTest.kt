package ai.hypergraph.kaliningraph.image

import kotlin.test.*

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.image.ImageTest"
*/

class ImageTest {
  fun Array<IntArray>.to2DList() = map { it.toList() }

  @Test
  fun testRow() {
    val m1x3 = arrayOf(intArrayOf(1, 2, 3))
    val m2x6 = arrayOf(intArrayOf(1, 1, 2, 2, 3, 3), intArrayOf(1, 1, 2, 2, 3, 3))
    assertEquals(m1x3.enlarge(2).to2DList(), m2x6.to2DList())
  }

  @Test
  fun testCol() {
    val m3x1 = arrayOf(intArrayOf(1), intArrayOf(2), intArrayOf(3))
    val m6x2 = arrayOf(intArrayOf(1, 1), intArrayOf(1, 1), intArrayOf(2, 2), intArrayOf(2, 2), intArrayOf(3, 3), intArrayOf(3, 3))
    assertEquals(m3x1.enlarge(2).to2DList(), m6x2.to2DList())
  }
}