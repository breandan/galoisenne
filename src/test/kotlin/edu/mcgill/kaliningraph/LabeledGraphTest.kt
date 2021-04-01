package edu.mcgill.kaliningraph

import org.junit.Assert.*
import org.junit.jupiter.api.Test

class LabeledGraphTest {
  val graph = LabeledGraph { a - b - c - d - e; a - c - e }

  @Test
  fun testAdjListConstructor() = assertEquals(
    graph,
    LabeledGraph(
      "a" to "b",
      "b" to "c",
      "c" to "d",
      "d" to "e",
      "a" to "c",
      "c" to "e"
    )
  )

  @Test
  fun testStringConstructor() = assertEquals(graph, LabeledGraph("abcde ace"))
}