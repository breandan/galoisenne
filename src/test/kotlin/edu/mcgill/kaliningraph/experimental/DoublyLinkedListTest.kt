package edu.mcgill.kaliningraph.experimental

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DoublyLinkedListTest {
  @Test
  fun testDLL() {
    var head = DLL("a").let { ('c'..'f').fold(it) { a, b -> a + b.toString() } }
    println(head)
    assertDLL(head)

    head = head.insert("b")
    println(head)
// TODO: this seems broken. is it really?
//    head = head.next.next.insert("c")
//    println(head)
    var tail = DLL("g").let { ('h'..'l').fold(it) { a, b -> a + b.toString() } }
    println(head + tail)
    assertDLL(head)
    println(head.reversed())
  }

  private fun assertDLL(head: DLL<String>) =
    (1 until head.size - 1).map { head[it] }.forEach {
      val isDLLForward = it.next.prev === it && it.next !== it
      val isDLLBack = it.prev.next === it && it.prev !== it
      assertTrue(isDLLForward) { "${it.next.prev} != $it" }
      assertTrue(isDLLBack) { "${it.prev.next} != $it" }
    }
}