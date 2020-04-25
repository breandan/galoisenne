package edu.mcgill.kaliningraph

import guru.nidi.graphviz.*
import guru.nidi.graphviz.attribute.*
import guru.nidi.graphviz.engine.Engine
import guru.nidi.graphviz.engine.Format
import guru.nidi.graphviz.engine.Renderer
import guru.nidi.graphviz.model.Factory
import java.io.File

val THICKNESS = 2

inline fun render(format: Format = Format.SVG, crossinline op: () -> Unit) =
  graph(directed = true) {
    val color = Color.BLACK

    edge[color, Arrow.NORMAL, Style.lineWidth(THICKNESS)]

//    graph[Rank.dir(Rank.RankDir.LEFT_TO_RIGHT), Color.TRANSPARENT.background()]

    node[color, color.font(), Font.config("Helvetica", 20), Style.lineWidth(THICKNESS)]

    op()
  }.toGraphviz().apply { engine(Engine.CIRCO) }.render(format)

fun Renderer.show() = toFile(File.createTempFile("temp", ".svg")).show()
fun Graph.render() = render {
  V.forEach { vertex ->
    vertex.edges.forEach { edge ->
      (Factory.mutNode(vertex.id) - Factory.mutNode(edge.target.id)).add(Label.of(edge.label))
    }
  }
}

fun Graph.html() = render().toString()

fun Graph.show() = render().show()

fun File.show() = ProcessBuilder("x-www-browser", path).start()