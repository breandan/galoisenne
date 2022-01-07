package ai.hypergraph.kaliningraph

import org.junit.jupiter.api.*
import ai.hypergraph.kaliningraph.graphs.*
import ai.hypergraph.kaliningraph.types.cc

class LabeledGraphTest {
  val graph = LabeledGraph { a - b - c - d - e; a - c - e }

  @Test
  fun testAdjListConstructor() = Assertions.assertEquals(
    graph, LabeledGraph.P(
      "a" cc "b",
      "b" cc "c",
      "c" cc "d",
      "d" cc "e",
      "a" cc "c",
      "c" cc "e"
    )
  )

  @Test
  fun testStringConstructor() = Assertions.assertEquals(graph, LabeledGraph("abcde ace"))
}