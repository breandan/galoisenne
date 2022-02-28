package ai.hypergraph.kaliningraph.types

import kotlin.test.*

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.types.算数测试"
*/
@Suppress("ClassName", "NonAsciiCharacters")
class 算数测试 {
  @Test
  fun 算数测试() {
    val t = 十七 加 四
    val fortyTwo = 十七
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

    assertEquals(42, fortyTwo.toInt())
  }
}