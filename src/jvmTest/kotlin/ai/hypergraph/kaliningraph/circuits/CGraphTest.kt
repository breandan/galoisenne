package ai.hypergraph.kaliningraph.circuits

import ai.hypergraph.kaliningraph.graphs.ComputationGraph
import ai.hypergraph.kaliningraph.graphs.def
import ai.hypergraph.kaliningraph.visualization.show
import org.junit.jupiter.api.Test

class CGraphTest {
  @Test
  fun testCircuitBuilder() {
     ComputationGraph {
      val funA by def(a, b, c) { a + b + c }
      j = funA(3, 2, 1) + b * c
      d = j + e * f
      g = 1 - h
      i = a + d + g
    }.show()
      //.reversed() //TODO: What is this even supposed to mean?
      // Maybe this whole tower of abstractions wasn't such a good idea after all
      //.show()
  }
}