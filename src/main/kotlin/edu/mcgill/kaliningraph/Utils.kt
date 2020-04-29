package edu.mcgill.kaliningraph

import guru.nidi.graphviz.*
import guru.nidi.graphviz.attribute.*
import guru.nidi.graphviz.engine.Engine
import guru.nidi.graphviz.engine.Engine.DOT
import guru.nidi.graphviz.engine.Format
import guru.nidi.graphviz.engine.Format.SVG
import guru.nidi.graphviz.model.Factory
import guru.nidi.graphviz.model.MutableGraph
import org.apache.commons.math3.util.Pair
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource
import org.apache.tinkerpop.gremlin.structure.Edge.DEFAULT_LABEL
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph
import org.jgrapht.graph.DefaultEdge
import org.jgrapht.graph.SimpleDirectedGraph
import java.io.File

val THICKNESS = 2

fun Graph.render(layout: Engine = DOT, format: Format = SVG) =
  toGraphviz().toGraphviz().apply { engine(layout) }.render(format)

fun Graph.html() = render().toString()
fun Graph.show() = render().toFile(File.createTempFile("temp", ".svg")).show()
fun File.show() = ProcessBuilder("x-www-browser", path).start()

fun Graph.toGraphviz() =
  graph(directed = true) {
    val color = Color.BLACK
    edge[color, Arrow.NORMAL, Style.lineWidth(THICKNESS)]
//    graph[Rank.dir(Rank.RankDir.LEFT_TO_RIGHT), Color.TRANSPARENT.background()]
    node[color, color.font(), Font.config("Helvetica", 20), Style.lineWidth(THICKNESS)]

    edgList.forEach { (vertex, edge) ->
      (Factory.mutNode(vertex.id) - Factory.mutNode(edge.target.id)).add(Label.of(edge.label ?: ""))
    }
  }

fun MutableGraph.toKaliningraph() =
  Graph { edges().forEach { Vertex(it.from()?.name()?.value()) - Vertex(it.to().name().value()) } }

fun Graph.toJGraphT() =
  SimpleDirectedGraph<String, DefaultEdge>(DefaultEdge::class.java).apply {
    V.forEach { addVertex(it.id) }
    edgList.forEach { (source, edge) -> addEdge(source.id, edge.target.id) }
  }

fun <E> org.jgrapht.Graph<String, E>.toKaliningraph() =
  Graph { edgeSet().map { e -> Vertex(getEdgeSource(e)) - Vertex(getEdgeTarget(e)) } }

fun Graph.toTinkerpop(): GraphTraversalSource =
  TinkerGraph.open().traversal().apply {
    val map = V.map { it to addV(it.id).next() }.toMap()
    edgList.forEach { (v, e) ->
      (map[v] to map[e.target])
        .also { (t, s) -> addE(e.label ?: DEFAULT_LABEL).from(s).to(t).next() }
    }
  }

fun GraphTraversalSource.toKaliningraph() =
  Graph { E().toList().forEach { Vertex(it.inVertex().label()) - Vertex(it.outVertex().label()) } }

private operator fun <K, V> Pair<K, V>.component2(): V = second
private operator fun <K, V> Pair<K, V>.component1(): K = first

