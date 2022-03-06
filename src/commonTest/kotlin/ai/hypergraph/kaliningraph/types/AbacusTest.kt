package ai.hypergraph.kaliningraph.types

import kotlin.test.*

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.types.AbacusTest"
*/
@Suppress("ClassName", "NonAsciiCharacters")
class AbacusTest {
  @Test
  fun testAbacus() {
    val 二十一: 一<二<无>> = 十七 加 四

    val 四十二: 二<四<无>> = 十七
      .let { it 加 二 }
      .let { it 加 一 }
      .let { it 加 一 }
      .let { it 加 一 }
      .let { it 加 一 }
      .let { it 加 一 }
      .let { it 加 一 }
      .let { it 加 一 }
      .let { it 加 一 }
      .let { it 加 一 }
      .let { it 加 一 }
      .let { it 加 一 }
      .let { it 加 一 }
      .let { it 加 一 }
      .let { it 加 一 }
      .let { it 加 一 }
      .let { it 加 一 }
      .let { it 加 一 }
      .let { it 加 一 }
      .let { it 加 一 }
      .let { it 加 一 }
      .let { it 加 三 }

    assertEquals(42, 四十二.toInt())

    val 大数: 九<九<九<九<无>>>> = 九.九.九.九
    assertEquals(9999, 大数.toInt())

    val 未知数: 未 = 大数 加 三
    assertEquals(10002, 未知数.toInt())
  }
}