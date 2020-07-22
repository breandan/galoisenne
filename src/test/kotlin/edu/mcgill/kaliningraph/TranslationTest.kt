package edu.mcgill.kaliningraph

import guru.nidi.graphviz.model.MutableGraph
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource
import org.apache.tinkerpop.gremlin.structure.Edge
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph
import org.ejml.data.DMatrixSparseCSC
import org.ejml.data.DMatrixSparseTriplet
import org.ejml.ops.ConvertDMatrixStruct
import org.jgrapht.graph.DefaultEdge
import org.jgrapht.graph.SimpleDirectedGraph
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

  fun MutableGraph.toKaliningraph() =
    GraphBuilder { edges().forEach { Vertex(it.from()?.name()?.value()) - Vertex(it.to().name().value()) } }

  fun <T : Node<T>> Graph<T>.toJGraphT() =
    SimpleDirectedGraph<String, DefaultEdge>(DefaultEdge::class.java).apply {
      V.forEach { addVertex(it.id) }
      edgList.forEach { (source, edge) -> addEdge(source.id, edge.target.id) }
    }

  fun <E> org.jgrapht.Graph<String, E>.toKaliningraph() =
    GraphBuilder { edgeSet().map { e -> Vertex(getEdgeSource(e)) - Vertex(getEdgeTarget(e)) } }

  fun <T : Node<T>> Graph<T>.toTinkerpop(): GraphTraversalSource =
    TinkerGraph.open().traversal().apply {
      val map = V.map { it to addV(it.id).next() }.toMap()
      edgList.forEach { (v, e) ->
        (map[v] to map[e.target])
          .also { (t, s) -> addE(e.label ?: Edge.DEFAULT_LABEL).from(s).to(t).next() }
      }
    }

  fun GraphTraversalSource.toKaliningraph() =
    GraphBuilder { E().toList().forEach { Vertex(it.inVertex().label()) - Vertex(it.outVertex().label()) } }
}