package edu.mcgill.kaliningraph

import astminer.common.model.Node
import guru.nidi.graphviz.engine.*
import guru.nidi.graphviz.engine.Engine.DOT
import guru.nidi.graphviz.engine.Format.SVG
import guru.nidi.graphviz.model.*
import guru.nidi.graphviz.toGraphviz
import org.ejml.data.*
import org.ejml.dense.row.*
import org.ejml.ops.ConvertDMatrixStruct
import java.awt.image.BufferedImage
import java.io.*
import java.math.*
import java.util.*
import javax.imageio.ImageIO
import kotlin.random.Random
import kotlin.system.measureTimeMillis

fun Node.toKGraph() =
  LabeledGraphBuilder {
    closure(
      toVisit = setOf(this@toKGraph),
      successors = { flatMap { setOfNotNull(it.getParent()) + it.getChildren() }.toSet() }
    ).forEach { parent ->
      getChildren().forEach { child ->
        LGVertex(id = parent.toString(), label = parent.getToken()) - LGVertex(id = child.toString(), label = child.getToken())
        LGVertex(id = child.toString(), label = child.getToken()) - LGVertex(id = parent.toString(), label = parent.getToken())
      }
    }
  }

tailrec fun <T> closure(
  toVisit: Set<T> = emptySet(),
  visited: Set<T> = emptySet(),
  successors: Set<T>.() -> Set<T>
): Set<T> =
  if (toVisit.isEmpty()) visited
  else closure(
    toVisit = toVisit.successors() - visited,
    visited = visited + toVisit,
    successors = successors
  )

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

val DEFAULT_RANDOM = Random(1)

private operator fun <K, V> Pair<K, V>.component2(): V = second
private operator fun <K, V> Pair<K, V>.component1(): K = first
operator fun MutableNode.minus(target: LinkTarget): Link = addLink(target).links().last()!!

fun DMatrixSparseCSC.elwise(op: (Double) -> Double) =
  copy().also { copy -> createCoordinateIterator().forEach { copy[it.row, it.col] = op(it.value) } }

fun randomMatrix(rows: Int, cols: Int, rand: () -> Double = { DEFAULT_RANDOM.nextDouble() }) =
  Array(rows) { Array(cols) { rand() }.toDoubleArray() }.toEJMLSparse()

fun randomVector(size: Int, rand: () -> Double = { DEFAULT_RANDOM.nextDouble() }) =
  Array(size) { rand() }.toDoubleArray()

fun Array<DoubleArray>.toEJMLSparse() = DMatrixSparseCSC(size, this[0].size, sumBy { it.count { it == 0.0 } })
  .also { s -> for (i in indices) for (j in this[0].indices) this[i][j].let { if (0.0 < it) s[i, j] = it } }

fun Array<DoubleArray>.toEJMLDense() = DMatrixRMaj(this)

fun Double.round(precision: Int = 10) = BigDecimal(this, MathContext(precision)).toDouble()

fun Array<DoubleArray>.round(precision: Int = 3): Array<DoubleArray> =
  map { it.map { it.round(precision) }.toDoubleArray() }.toTypedArray()

fun <T> powBench(constructor: T, matmul: (T, T) -> T): Long =
  measureTimeMillis { constructor.power(100, matmul) }

fun <T> T.power(exp: Int, matmul: (T, T) -> T) =
  (0..exp).fold(this) { acc, i -> matmul(acc, this) }

fun String.vectorize(len: Int = DEFAULT_FEATURE_LEN) = Random(hashCode())
  .let { randomVector(len) { it.nextDouble() } }