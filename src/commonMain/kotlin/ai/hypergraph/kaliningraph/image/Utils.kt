package ai.hypergraph.kaliningraph.image

import ai.hypergraph.kaliningraph.minMaxNorm
import ai.hypergraph.kaliningraph.tensor.*
import ai.hypergraph.kaliningraph.types.ℤⁿ
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.roundToInt

fun String.escapeHTML() =
  map { ch ->
    when (ch) {
      '\'' -> "&apos;"
      '\"' -> "&quot"
      '&' -> "&amp;"
      '<' -> "&lt;"
      '>' -> "&gt;"
      else -> ch
    }
  }.joinToString("")

fun <T> FreeMatrix<T>.toHtmlTable(): String {
  var html = "<table>\n"
  for (row in rows) {
    html += "\t<tr >\n"
    for (col in this[0].indices) html +=
      """<td style="vertical-align:top"><pre><code>${row[col]}</code></pre></td>"""
    html += "\t</tr>\n"
  }
  html += "</table>"
  return html
}

fun <T> FreeMatrix<T>.toHtmlPage(): String =
  """
    <html>
    <head>
    <style>
    table, th, td {
      border: 1px solid black;
      border-collapse: collapse;
      table-layout: auto;
      min-width: 300px;
      height: 200px;
      text-align: center;
    }
    </style>
    </head>
    <body>${toHtmlTable()}</body>
    </html>
  """.trimIndent()

@OptIn(ExperimentalEncodingApi::class)
fun Matrix<*, *, *>.matToBase64Img(
  pixelsPerEntry: Int = (200 / numRows).coerceIn(1..20),
  arr: Array<ℤⁿ> = when (this) {
    is BooleanMatrix -> data.map { if (it) 255 else 0 }
    is DoubleMatrix -> minMaxNorm().data.map { (it * 255).roundToInt() }
    else -> TODO("Renderer is undefined")
  }.let { FreeMatrix(it).rows.map { it.toIntArray() }.toTypedArray() }.enlarge(pixelsPerEntry),
): String = "data:image/bmp;base64," + Base64.encode(BMP().saveBMP(arr))

fun Array<ℤⁿ>.enlarge(factor: Int = 2): Array<ℤⁿ> =
  map { row -> row.flatMap { col -> (0..<factor).map { col } }
    .let { r -> (0..<factor).map { r.toIntArray() } } }.flatten().toTypedArray()

class BMP {
  lateinit var bytes: ByteArray
  fun saveBMP(rgbValues: Array<ℤⁿ>): ByteArray {
    bytes = ByteArray(54 + 3 * rgbValues.size * rgbValues[0].size +
      getPadding(rgbValues[0].size) * rgbValues.size)
    saveFileHeader()
    saveInfoHeader(rgbValues.size, rgbValues[0].size)
    saveBitmapData(rgbValues)
    return bytes
  }

  private fun saveFileHeader() {
    var a = intToByteCouple(BMP_CODE)
    bytes[0] = a[1]
    bytes[1] = a[0]
    a = intToFourBytes(bytes.size)
    bytes[5] = a[0]
    bytes[4] = a[1]
    bytes[3] = a[2]
    bytes[2] = a[3]

    //data offset
    bytes[10] = 54
  }

  private fun saveInfoHeader(height: Int, width: Int) {
    bytes[14] = 40
    var a = intToFourBytes(width)
    bytes[22] = a[3]
    bytes[23] = a[2]
    bytes[24] = a[1]
    bytes[25] = a[0]
    a = intToFourBytes(height)
    bytes[18] = a[3]
    bytes[19] = a[2]
    bytes[20] = a[1]
    bytes[21] = a[0]
    bytes[26] = 1
    bytes[28] = 24
  }

  private fun saveBitmapData(rgbValues: Array<ℤⁿ>) {
    for (i in rgbValues.indices) writeLine(i, rgbValues)
  }

  private fun writeLine(row: Int, rgbValues: Array<ℤⁿ>) {
    val offset = 54
    val rowLength: Int = rgbValues[row].size
    val padding = getPadding(rgbValues[0].size)
    for (i in 0..<rowLength) {
      val rgb = rgbValues[row][i]
      val temp = offset + 3 * (i + rowLength * row) + row * padding
      bytes[temp] = (rgb shr 16).toByte()
      bytes[temp + 1] = (rgb shr 8).toByte()
      bytes[temp + 2] = rgb.toByte()
    }
    val temp = offset + 3 * ((rowLength - 1) + rowLength * row) + row * padding + 3
    for (j in 0..<padding) bytes[temp + j] = 0
  }

  private fun intToByteCouple(x: Int): ByteArray {
    val array = ByteArray(2)
    array[1] = x.toByte()
    array[0] = (x shr 8).toByte()
    return array
  }

  private fun intToFourBytes(x: Int): ByteArray {
    val array = ByteArray(4)
    array[3] = x.toByte()
    array[2] = (x shr 8).toByte()
    array[1] = (x shr 16).toByte()
    array[0] = (x shr 24).toByte()
    return array
  }

  private fun getPadding(rowLength: Int): Int {
    var padding = 3 * rowLength % 4
    if (padding != 0) padding = 4 - padding
    return padding
  }

  private val BMP_CODE = 19778
}