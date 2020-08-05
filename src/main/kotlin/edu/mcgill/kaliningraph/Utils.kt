package edu.mcgill.kaliningraph

import guru.nidi.graphviz.*
import guru.nidi.graphviz.attribute.*
import guru.nidi.graphviz.attribute.Arrow.*
import guru.nidi.graphviz.attribute.Color.BLACK
import guru.nidi.graphviz.attribute.Color.RED
import guru.nidi.graphviz.attribute.Shape.*
import guru.nidi.graphviz.attribute.Style.*
import guru.nidi.graphviz.engine.Engine
import guru.nidi.graphviz.engine.Engine.DOT
import guru.nidi.graphviz.engine.Format
import guru.nidi.graphviz.engine.Format.SVG
import guru.nidi.graphviz.model.Factory
import org.ejml.data.DMatrixRMaj
//import org.hipparchus.distribution.EnumeratedDistribution
//import org.hipparchus.random.RandomDataGenerator
//import org.hipparchus.util.Pair
import org.ejml.data.DMatrixSparseCSC
import org.ejml.data.DMatrixSparseTriplet
import org.ejml.dense.row.CommonOps_DDRM
import org.ejml.dense.row.DMatrixComponent
import org.ejml.ops.ConvertDMatrixStruct
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.*
import javax.imageio.ImageIO

val THICKNESS = 4

fun Graph<*, *, *>.render(layout: Engine = DOT, format: Format = SVG) =
  toGraphviz().toGraphviz().apply { engine(layout) }.render(format)

fun Graph<*, *, *>.html() = render().toString()
fun Graph<*, *, *>.show() = render().toFile(File.createTempFile("temp", ".svg")).show()
fun File.show() = ProcessBuilder("x-www-browser", path).start()

fun Graph<*, *, *>.toGraphviz() =
  graph(directed = true) {
    val color = BLACK
    edge[color, NORMAL, lineWidth(THICKNESS)]
//    graph[Rank.dir(Rank.RankDir.LEFT_TO_RIGHT), Color.TRANSPARENT.background()]
    node[color, color.font(), Font.config("Helvetica", 20), lineWidth(THICKNESS), Attributes.attr("shape", "Mrecord")]

    vertices.forEach { node ->
      for (neighbor in node.neighbors) {
        val source = Factory.mutNode(node.id).add(Label.of(node.toString())).also { if (node.occupied) it[FILLED, RED.fill()] else it[BLACK] }
        val target = Factory.mutNode(neighbor.id).add(Label.of(neighbor.toString()))//.also { if (neighbor.occupied) it[RED] else it[BLACK] }
        (source - target).add(Label.of("")).also { if (node.occupied) it[RED] else it[BLACK] }
      }
    }
  }

fun DMatrixSparseTriplet.toCSC() = ConvertDMatrixStruct.convert(this, null as DMatrixSparseCSC?)


fun DMatrixSparseCSC.adjToMat(f: Int = 20): String {
  val rescaled = DMatrixRMaj(numRows * f, numCols * f)
  val dense = ConvertDMatrixStruct.convert(this, null as DMatrixRMaj?)
  CommonOps_DDRM.kron(dense, DMatrixRMaj(f, f, false, *DoubleArray(f * f) { 1.0 }), rescaled)

  val bi = BufferedImage(rescaled.numCols, rescaled.numRows, BufferedImage.TYPE_INT_RGB)
  DMatrixComponent.renderMatrix(rescaled, bi, 1.0)

  val os = ByteArrayOutputStream()
  ImageIO.write(bi, "png", os)
  return "data:image/jpg;base64," + Base64.getEncoder().encodeToString(os.toByteArray())
}

fun randomString() = UUID.randomUUID().toString().take(5)

private operator fun <K, V> Pair<K, V>.component2(): V = second
private operator fun <K, V> Pair<K, V>.component1(): K = first