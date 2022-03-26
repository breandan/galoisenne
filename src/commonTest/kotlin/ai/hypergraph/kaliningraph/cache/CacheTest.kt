package ai.hypergraph.kaliningraph.cache

import kotlin.test.Test

class CacheTest {
  fun compute() = "hello".also { println(it) }
  interface A { /*...*/ }
  val A.prop by lazy { compute() }
  /*
 ./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.cache.testPattern"
 */
  @Test
  fun testPattern() {
    val a = object: A { }
    println("Read: ${a.prop}")
    println("Read: ${a.prop}")
  }
}