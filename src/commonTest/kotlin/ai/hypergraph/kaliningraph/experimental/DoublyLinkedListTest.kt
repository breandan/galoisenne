package ai.hypergraph.kaliningraph.experimental

import ai.hypergraph.experimental.DLL
import kotlin.test.*

class DoublyLinkedListTest {
  @Test
  fun testDLL() {
    var head = DLL("a").let { ('c'..'f').fold(it) { a, b -> a + b.toString() } }
    println(head)
    assertDLL(head)

    head = head.insert("b")
    println(head)
    assertDLL(head)

// TODO: this seems broken. is it really?
//    head = head.next.next.insert("c")
//    println(head)
    var tail = DLL("g").let { ('h'..'l').fold(it) { a, b -> a + b.toString() } }
    head += tail
    println(head)
    assertDLL(head)

    head = head.reversed()
    println(head)
    assertDLL(head)
  }

  private fun assertDLL(head: DLL<String>) =
    (1..<head.size - 1).map { head[it] }.forEach {
      assertTrue("${it.next.prev} != $it") { it.next.prev === it && it.next !== it }
      assertTrue("${it.prev.next} != $it") { it.prev.next === it && it.prev !== it }
    }
}