package edu.mcgill.kaliningraph

//import org.hipparchus.distribution.EnumeratedDistribution
//import org.hipparchus.random.RandomDataGenerator
//import org.hipparchus.util.Pair
import guru.nidi.graphviz.attribute.*
import guru.nidi.graphviz.attribute.Arrow.NORMAL
import guru.nidi.graphviz.attribute.Color.*
import guru.nidi.graphviz.attribute.Style.lineWidth
import guru.nidi.graphviz.engine.Engine
import guru.nidi.graphviz.engine.Engine.DOT
import guru.nidi.graphviz.engine.Format
import guru.nidi.graphviz.engine.Format.SVG
import guru.nidi.graphviz.graph
import guru.nidi.graphviz.model.*
import guru.nidi.graphviz.toGraphviz
import org.ejml.data.*
import org.ejml.dense.row.CommonOps_DDRM
import org.ejml.dense.row.DMatrixComponent
import org.ejml.ops.ConvertDMatrixStruct
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.*
import javax.imageio.ImageIO

val THICKNESS = 4
val DARKMODE = false

fun Graph<*, *, *>.toGraphViz(layout: Engine = DOT, format: Format = SVG) =
   render().toGraphviz().apply { engine(layout) }.render(format)

fun Graph<*, *, *>.html() = toGraphViz().toString()
fun Graph<*, *, *>.show() = toGraphViz().toFile(File.createTempFile("temp", ".svg")).show()
val browserCmd = System.getProperty("os.name").toLowerCase().let { os ->
  when {
    "win" in os -> "rundll32 url.dll,FileProtocolHandler"
    "mac" in os -> "open"
    "nix" in os || "nux" in os -> "x-www-browser"
    else -> throw Exception("Unable to open browser for unknown OS: $os")
  }
}

fun File.show() = ProcessBuilder(browserCmd, path).start()

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
operator fun MutableNode.minus(target: LinkTarget): Link = addLink(target).links().last()!!

fun DMatrixSparseCSC.elwise(op: (Double) -> Double) =
  copy().also { copy -> createCoordinateIterator().forEach { copy[it.row, it.col] = op(it.value) } }

fun randomMatrix(rows: Int, cols: Int, rand: () -> Double) =
  DMatrixSparseTriplet(rows, cols, rows * cols).apply { this[rows, cols] = rand() }.toCSC()