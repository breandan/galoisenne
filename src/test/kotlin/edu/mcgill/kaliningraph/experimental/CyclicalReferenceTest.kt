package edu.mcgill.kaliningraph.experimental

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CyclicalReferenceTest {
  @Test
  fun testCyclicalReference() {
    val owl = Owl { Egg { this } }
    println(owl)
    println(owl.egg)
    println(owl.egg.owl)
    println(owl.egg.owl.egg)
    assertTrue(owl === owl.egg.owl)
    assertTrue(owl.egg === owl.egg.owl.egg)
  }
}