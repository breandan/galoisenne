package ai.hypergraph.kaliningraph.types

import kotlin.test.*

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.types.算数测试"
*/
@Suppress("ClassName", "NonAsciiCharacters")
class 算盘测试 {
  @Test
  fun 算数测试() {
    val 二十一 = 十七 加 四

    val 四十二 = 十七
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
  }
}