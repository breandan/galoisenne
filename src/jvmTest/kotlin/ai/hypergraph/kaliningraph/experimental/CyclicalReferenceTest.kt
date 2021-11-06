package ai.hypergraph.kaliningraph.experimental

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CyclicalReferenceTest {
  @Test
  fun testCyclicalReference() {
    val owl = Owl { Egg { it } }
    println(owl)
    println(owl.egg)
    println(owl.egg.owl)
    println(owl.egg.owl.egg)
    assertTrue(owl === owl.egg.owl)
    assertTrue(owl.egg === owl.egg.owl.egg)

    val owl2 = Owl { grandOwl -> Egg { Owl { Egg { grandOwl } } } }
    println(owl2)
    println(owl2.egg.owl)
    println(owl2.egg.owl.egg.owl)
    assertTrue(owl2 === owl2.egg.owl.egg.owl)
  }
}