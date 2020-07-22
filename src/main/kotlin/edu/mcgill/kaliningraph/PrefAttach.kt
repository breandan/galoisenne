package edu.mcgill.kaliningraph

import kweb.ImageElement
import kweb.Kweb
import kweb.html.events.KeyboardEvent
import kweb.img
import kweb.new
import org.ejml.data.DMatrixRMaj
import org.ejml.data.DMatrixSparseCSC
import org.ejml.dense.row.CommonOps_DDRM
import org.ejml.dense.row.DMatrixComponent
import org.ejml.kotlin.times
import org.ejml.kotlin.transpose
import org.ejml.ops.ConvertDMatrixStruct
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.*
import javax.imageio.ImageIO

@ExperimentalStdlibApi
fun main() {
  prefAttachDemo()
}

@ExperimentalStdlibApi
fun prefAttachDemo() {
  val graphs = mutableListOf(Graph(Vertex("0")))

  Kweb(port = 16097) {
    doc.body.apply {
      val desc = new { element("p")
        .innerHTML("Use ↑/↓ to grow graph, and →/← keys to evolve the graph...") }
      val el = new { element("div")
        .setAttributeRaw("style", "max-width: 500px;")
        .innerHTML(graphs.last().html()) }
      val mat = new { img(null, mapOf("width" to "200", "height" to "200")) }
      val vec = new { img(null, mapOf("width" to "200", "height" to "200")) }
      val nex = new { img(null, mapOf("width" to "200", "height" to "200")) }
      val program = new { element("p").innerHTML("") }

      on.keydown { keystroke ->
        handle(keystroke, graphs)

        el.innerHTML(graphs.last().html())
        mat.render(graphs.last()) { it.A }
        vec.render(graphs.last()) { it.S() }
        nex.render(graphs.last()) { it.A.transpose() * it.S() }
      }

      program.innerHTML("<p style=\"font-size:40px\">${graphs.last().string}</p>")
    }
  }

  ProcessBuilder("x-www-browser", "http://0.0.0.0:16097").start()
}

@ExperimentalStdlibApi
private fun handle(it: KeyboardEvent, graphs: MutableList<Graph<Vertex, LabeledEdge>>) {
  when {
    "Left" in it.key -> { }
    "Right" in it.key -> {
      val current = graphs.last()
      if (current.none { it.occupied }) {
        current.sortedBy { -it.id.toInt() + Math.random() * 10 }
          .takeWhile { Math.random() < 0.5 }
          .forEach { it.occupied = true }
        current.done = current.filter { it.occupied }.map { it.id }.toMutableSet()
        current.string = "y = ${current.done.joinToString(" + ")}"
      } else current.propagate()
    }
    "Up" in it.key -> if (graphs.size > 1) graphs.removeLastOrNull()
    "Down" in it.key -> graphs.add(graphs.last().prefAttach())
  }
}

private fun ImageElement.render(graph: Graph<Vertex, LabeledEdge>, renderFun: (Graph<Vertex, LabeledEdge>) -> DMatrixSparseCSC) {
  setAttributeRaw("src", renderFun(graph).adjToMat())
}

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