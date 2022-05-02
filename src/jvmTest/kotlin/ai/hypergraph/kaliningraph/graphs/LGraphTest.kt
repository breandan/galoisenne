package ai.hypergraph.kaliningraph.graphs

import ai.hypergraph.kaliningraph.visualization.show
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class LGraphTest {
/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.graphs.LGraphTest.testLabelIsDistinctFromId"
*/
  @Test
  fun testLabelIsDistinctFromId() {
    val graph4 = (LabeledGraph {
      FreshLGVertex("a") - LGVertex("b")
      FreshLGVertex("a") - LGVertex("c")
    }).apply { show() }
    println(graph4)
    assertEquals(4, graph4.size)

    val graph3 = LabeledGraph {
      LGVertex("a") - LGVertex("b")
      LGVertex("a") - LGVertex("c")
    }.apply { show() }
    println(graph3)
    assertEquals(3, graph3.size)
  }
}