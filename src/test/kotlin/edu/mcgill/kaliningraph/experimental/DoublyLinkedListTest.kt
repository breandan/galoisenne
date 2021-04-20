package edu.mcgill.kaliningraph.experimental

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DoublyLinkedListTest {
  @Test
  fun testDLL() {
    var head = DLL("a").let { ('c'..'f').fold(it) { a, b -> a + b.toString() } }
//  println(head)
    assertDLL(head)

    head = head.insert("b")
    println(head + head)
    assertDLL(head)
    println(head.reversed())
  }

  private fun assertDLL(head: DLL<String>) =
    (1 until head.size - 1).map { head[it] }.forEach {
      val isDLLForward = it.next.prev === it
      val isDLLBack = it.prev.next === it
      println(head)
      assertTrue(isDLLForward) { "${it.next.prev} != $it" }
      assertTrue(isDLLBack) { "${it.prev.next} != $it" }
    }
}