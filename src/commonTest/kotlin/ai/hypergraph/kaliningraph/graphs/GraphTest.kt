package ai.hypergraph.kaliningraph.graphs

import ai.hypergraph.kaliningraph.theory.diameter
import ai.hypergraph.kaliningraph.types.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.graphs.GraphTest"
*/
class GraphTest {
  val graph = LabeledGraph { a - b - c - d - e; a - c - e }
  @Test
  fun testIsomorphic() =
    assertEquals(graph, LabeledGraph { b - c - d - e - f; b - d - f })

  @Test
  fun testEquivalence() {
    val abcde = LabeledGraph { a - b - c - d - e }
    val ace = LabeledGraph { a - c - e }
    val graph2 = abcde + ace

    assertEquals(graph, graph2)

    val abcdead = LabeledGraph { a - b - c - d - e - a - d }
    val edcbace = LabeledGraph { e - d - c - b - a - c - e }
    assertNotEquals(abcdead, edcbace)
  }

  @Test
  fun testLabelIsDistinctFromId() {
    val graph4 = (LabeledGraph {
      FreshLGVertex("a") - LGVertex("b")
      FreshLGVertex("a") - LGVertex("c")
    })
    println(graph4)
    assertEquals(4, graph4.size)

    val graph3 = LabeledGraph {
      LGVertex("a") - LGVertex("b")
      LGVertex("a") - LGVertex("c")
    }
    println(graph3)
    assertEquals(3, graph3.size)
  }

  @Test
  fun testAdjListConstructor() = assertEquals(
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
  fun testStringConstructor() =
    assertEquals(graph, LabeledGraph("abcde ace"))

  @Test
  fun testClosure() =
    assertEquals(graph.toSet(), graph.vertices.flatMap { it.neighbors + it }.toSet())

  @Test
  fun testDiameter() = assertEquals(2, graph.diameter())

  // TODO: Test whether graph is closed under other circumstances
}