package ai.hypergraph.kaliningraph

import org.junit.jupiter.api.*

class LabeledGraphTest {
  val graph = LabeledGraph { a - b - c - d - e; a - c - e }

  @Test
  fun testAdjListConstructor() = Assertions.assertEquals(
    graph, LabeledGraph.P(
      "a" to "b",
      "b" to "c",
      "c" to "d",
      "d" to "e",
      "a" to "c",
      "c" to "e"
    )
  )

  @Test
  fun testStringConstructor() = Assertions.assertEquals(graph, LabeledGraph("abcde ace"))
}