package edu.mcgill.kaliningraph

import org.junit.Assert.assertEquals
import org.junit.jupiter.api.Test

class TranslationTest {
  val randomGraph = Graph<Vertex>().prefAttach(vertices = 10)

  @Test
  fun testTinkerpopTranslationInvariance() =
    randomGraph.let { assertEquals(it, it.toTinkerpop().toKaliningraph()) }

  @Test
  fun testJGraphTTranslationInvariance() =
    randomGraph.let { assertEquals(it, it.toJGraphT().toKaliningraph()) }

  @Test
  fun testGraphvizTranslationInvariance() =
    randomGraph.let { assertEquals(it, it.toGraphviz().toKaliningraph()) }
}