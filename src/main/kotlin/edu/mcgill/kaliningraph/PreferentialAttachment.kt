package edu.mcgill.kaliningraph

import kweb.Kweb
import kweb.img
import kweb.new
import org.ejml.data.DMatrixRMaj
import org.ejml.dense.row.CommonOps_DDRM
import org.ejml.dense.row.DMatrixComponent
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
  val graphs = mutableListOf(Graph())

  Kweb(port = 16097) {
    doc.body.apply {
      val desc = new { element("p").innerHTML("Use →/← keys...") }
      val el = new { element("div").setAttributeRaw("style", "max-width: 500px;").innerHTML(graphs.last().html()) }
      val mat = new { img(null, mapOf("width" to "200", "height" to "200")) }
      on.keydown {
        when {
          "Left" in it.key -> {
            if (graphs.size == 1) return@keydown
            graphs.removeLastOrNull()
          }
          "Right" in it.key -> {
            graphs.add(graphs.last().prefAttach())
          }
          "Up" in it.key -> {

          }
          "Down" in it.key -> {

          }
        }
        el.innerHTML(graphs.last().html())
        mat.setAttributeRaw("src", graphs.last().toBase64Image())
      }
    }
  }

  ProcessBuilder("x-www-browser", "http://0.0.0.0:16097").start()
}

fun Graph.toBase64Image(f: Int = 20): String {
  val rescaled = DMatrixRMaj(A.numRows * f, A.numCols * f)
  CommonOps_DDRM.kron(A, DMatrixRMaj(f, f, false, *DoubleArray(f * f) { 1.0 }), rescaled)

  val bi = BufferedImage(rescaled.numCols, rescaled.numRows, BufferedImage.TYPE_INT_RGB)
  DMatrixComponent.renderMatrix(rescaled, bi, 1.0)

  val os = ByteArrayOutputStream()
  ImageIO.write(bi, "png", os)
  return "data:image/jpg;base64," + Base64.getEncoder().encodeToString(os.toByteArray())
}