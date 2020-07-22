package edu.mcgill.kaliningraph

import guru.nidi.graphviz.*
import guru.nidi.graphviz.attribute.*
import guru.nidi.graphviz.attribute.Color.BLACK
import guru.nidi.graphviz.attribute.Color.RED
import guru.nidi.graphviz.engine.Engine
import guru.nidi.graphviz.engine.Engine.DOT
import guru.nidi.graphviz.engine.Format
import guru.nidi.graphviz.engine.Format.SVG
import guru.nidi.graphviz.model.Factory
import org.apache.commons.math3.util.Pair
import org.ejml.data.DMatrixSparseCSC
import org.ejml.data.DMatrixSparseTriplet
import org.ejml.ops.ConvertDMatrixStruct
import java.io.File
import java.util.*

val THICKNESS = 4

fun Graph<*, *>.render(layout: Engine = DOT, format: Format = SVG) =
  toGraphviz().toGraphviz().apply { engine(layout) }.render(format)

fun Graph<*, *>.html() = render().toString()
fun Graph<*, *>.show() = render().toFile(File.createTempFile("temp", ".svg")).show()
fun File.show() = ProcessBuilder("x-www-browser", path).start()

fun Graph<*, *>.toGraphviz() =
  graph(directed = true) {
    val color = BLACK
    edge[color, Arrow.NORMAL, Style.lineWidth(THICKNESS)]
//    graph[Rank.dir(Rank.RankDir.LEFT_TO_RIGHT), Color.TRANSPARENT.background()]
    node[color, color.font(), Font.config("Helvetica", 20), Style.lineWidth(THICKNESS), Shape.CIRCLE]

    V.forEach { vertex ->
      for(neighbor in vertex.neighbors) {
        val source = Factory.mutNode(vertex.id).also { if (vertex.occupied) it[Style.FILLED, RED.fill()] else it[BLACK] }
        val target = Factory.mutNode(neighbor.id)//.also { if (neighbor.occupied) it[RED] else it[BLACK] }
        (source - target).add(Label.of("")).also {  if (vertex.occupied) it[RED] else it[BLACK]  }
      }
    }
  }

fun DMatrixSparseTriplet.toCSC() = ConvertDMatrixStruct.convert(this, null as DMatrixSparseCSC?)

fun randomString() = UUID.randomUUID().toString().take(5)

private operator fun <K, V> Pair<K, V>.component2(): V = second
private operator fun <K, V> Pair<K, V>.component1(): K = first