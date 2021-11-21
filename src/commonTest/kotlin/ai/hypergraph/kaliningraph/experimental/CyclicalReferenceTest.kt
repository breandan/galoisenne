package ai.hypergraph.kaliningraph.experimental

import ai.hypergraph.experimental.*
import kotlin.test.*

class CyclicalReferenceTest {
  @Test
  fun testCyclicalReference() {
    val owl = Owl { Egg { it } }
    println(owl)
    println(owl.egg)
    println(owl.egg.owl)
    println(owl.egg.owl.egg)
    assertSame(owl, owl.egg.owl)
    assertSame(owl.egg, owl.egg.owl.egg)

    val owl2 = Owl { grandOwl -> Egg { Owl { Egg { grandOwl } } } }
    println(owl2)
    println(owl2.egg.owl)
    println(owl2.egg.owl.egg.owl)
    assertSame(owl2, owl2.egg.owl.egg.owl)
  }
}