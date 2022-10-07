package ai.hypergraph.kaliningraph.cache

import kotlin.test.Test

class CacheTest {
  fun compute() = "hello"
  interface A { /*...*/ }
  val A.prop by lazy { compute().also { println("Wrote: $it") } }
/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.cache.CacheTest.testPattern"
*/
  @Test
  fun testPattern() {
    val a = object: A { }
    println("Read: ${a.prop}")
    println("Read: ${a.prop}")
  }
}