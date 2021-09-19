package ai.hypergraph.kaliningraph

import ai.hypergraph.kaliningraph.matrix.BMat
import ai.hypergraph.kaliningraph.typefamily.*
import guru.nidi.graphviz.*
import guru.nidi.graphviz.attribute.*
import guru.nidi.graphviz.attribute.Arrow.NORMAL
import guru.nidi.graphviz.attribute.Color.*
import guru.nidi.graphviz.attribute.GraphAttr.*
import guru.nidi.graphviz.attribute.Rank.RankDir.LEFT_TO_RIGHT
import guru.nidi.graphviz.attribute.Style.lineWidth
import guru.nidi.graphviz.engine.*
import guru.nidi.graphviz.engine.Engine.DOT
import guru.nidi.graphviz.engine.Format.SVG
import guru.nidi.graphviz.model.*
import org.ejml.data.*
import org.ejml.data.MatrixType.DDRM
import org.ejml.dense.row.*
import org.ejml.dense.row.CommonOps_DDRM.kron
import org.ejml.ops.ConvertMatrixType
import java.awt.image.BufferedImage
import java.awt.image.BufferedImage.TYPE_INT_RGB
import java.io.*
import java.math.*
import java.net.URL
import java.util.*
import javax.imageio.ImageIO
import kotlin.math.*
import kotlin.random.Random
import kotlin.system.measureTimeMillis

typealias SpsMat = DMatrixSparseCSC

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

const val THICKNESS = 4.0
const val DARKMODE = false

fun MutableGraph.render(format: Format, layout: Engine = DOT): Renderer =
   toGraphviz().apply { engine(layout) }.render(format)

fun IGraph<*, *, *>.html() = toGraphviz().render(SVG).toString()
fun IGraph<*, *, *>.show(filename: String = "temp") =
  toGraphviz().render(SVG).run {
    toFile(File.createTempFile(filename, ".svg"))
  }.show()
fun BMat.show(filename: String = "temp") = matToImg().let { data ->
  File.createTempFile(filename, ".html").apply {
    writeText("<html><body><img src=\"$data\" height=\"t00\" width=\"500\"/></body></html>")
  }
}.show()

val browserCmd = System.getProperty("os.name").lowercase().let { os ->
  when {
    "win" in os -> "rundll32 url.dll,FileProtocolHandler"
    "mac" in os -> "open"
    "nix" in os || "nux" in os -> "x-www-browser"
    else -> throw Exception("Unable to open browser for unknown OS: $os")
  }
}

fun File.show() = ProcessBuilder(browserCmd, path).start()
fun URL.show() = ProcessBuilder(browserCmd, toString()).start()

fun SpsMat.matToImg(f: Int = 20): String {
  val rescaled = DMatrixRMaj(numRows * f, numCols * f)
  val dense = ConvertMatrixType.convert(this, DDRM) as DMatrixRMaj
  kron(dense, DMatrixRMaj(f, f, false, *DoubleArray(f * f) { 1.0 }), rescaled)

  val bi = BufferedImage(rescaled.numCols, rescaled.numRows, TYPE_INT_RGB)
  DMatrixComponent.renderMatrix(rescaled, bi, 1.0)

  val os = ByteArrayOutputStream()
  ImageIO.write(bi, "png", os)
  return "data:image/jpg;base64," + Base64.getEncoder().encodeToString(os.toByteArray())
}

fun BMat.matToImg(f: Int = 20) = toEJMLSparse().matToImg(f)

fun randomString() = UUID.randomUUID().toString().take(5)

operator fun MutableNode.minus(target: LinkTarget): Link = addLink(target).links().last()!!

fun randomMatrix(rows: Int, cols: Int = rows, rand: () -> Double = { Random.Default.nextDouble() }) =
  Array(rows) { Array(cols) { rand() }.toDoubleArray() }.toEJMLSparse()

fun randomVector(size: Int, rand: () -> Double = { Random.Default.nextDouble() }) =
  Array(size) { rand() }.toDoubleArray()

fun Array<DoubleArray>.toEJMLSparse() = SpsMat(size, this[0].size, sumOf { it.count { it == 0.0 } })
  .also { s -> for (i in indices) for (j in this[0].indices) this[i][j].let { if (0.0 < it) s[i, j] = it } }

fun Array<DoubleArray>.toEJMLDense() = DMatrixRMaj(this)

fun Double.round(precision: Int = 10) = BigDecimal(this, MathContext(precision)).toDouble()

fun Array<DoubleArray>.round(precision: Int = 3): Array<DoubleArray> =
  map { it.map { it.round(precision) }.toDoubleArray() }.toTypedArray()

fun <T> powBench(constructor: T, matmul: (T, T) -> T): Long =
  measureTimeMillis { constructor.power(100, matmul) }

fun <T> T.power(exp: Int, matmul: (T, T) -> T) =
  generateSequence(this) { matmul(it, this) }.take(exp)

const val DEFAULT_FEATURE_LEN = 20
fun String.vectorize(len: Int = DEFAULT_FEATURE_LEN) =
  Random(hashCode()).let { randomVector(len) { it.nextDouble() } }

fun SpsMat.elwise(copy: Boolean = false, op: (Double) -> Double) =
  (if(copy) copy() else this).also { mat ->
    createCoordinateIterator().forEach { mat[it.row, it.col] = op(it.value) }
  }

val ACT_TANH: (SpsMat) -> SpsMat = { it.elwise { tanh(it) } }
val NORM_AVG: (SpsMat) -> SpsMat = { it.meanNorm() }

fun SpsMat.meanNorm(copy: Boolean = false) =
  nz_values.fold(Triple(0.0, 0.0, 0.0)) { (a, b, c), e ->
    Triple(a + e / nz_length.toDouble(), min(b, e), max(c, e))
  }.let { (μ, min, max) ->
    elwise(copy) { e -> (e - μ) / (max - min) }
  }

inline fun elwise(rows: Int, cols: Int = rows, nonZeroes: Int = rows,
                  crossinline lf: (Int, Int) -> Double? = ::kroneckerDelta) =
  SpsMat(rows, cols, nonZeroes).also { sprsMat ->
    for (v in 0 until rows) for (n in 0 until cols)
      lf(v, n)?.let { if (it != 0.0) sprsMat[v, n] = it }
  }

fun kroneckerDelta(i: Int, j: Int) = if(i == j) 1.0 else 0.0

fun <G : IGraph<G, E, V>, E : IEdge<G, E, V>, V : IVertex<G, E, V>>
  IGraph<G, E, V>.toGraphviz() =
  graph(directed = true, strict = true) {
    val color = if (DARKMODE) WHITE else BLACK
    edge[color, NORMAL, lineWidth(THICKNESS)]
    graph[CONCENTRATE, Rank.dir(LEFT_TO_RIGHT),
      TRANSPARENT.background(), GraphAttr.margin(0.0),
      COMPOUND, Attributes.attr("nslimit", "20")]
    node[color, color.font(), Font.config("Helvetica", 20),
      lineWidth(THICKNESS), Attributes.attr("shape", "Mrecord")]

    for((vertex, edge) in edgList)
      edge.render().also { if (vertex is LGVertex && vertex.occupied) it.add(RED) }
  }

// Samples from unnormalized counts with normalized frequency
fun <T> Map<T, Number>.sample(random: Random = Random.Default) =
  entries.map { (k, v) -> k to v }.unzip().let { (keys, values) ->
    val cdf = values.cdf()
    generateSequence { keys[cdf.sample(random)] }
  }

fun Collection<Number>.cdf() = CDF(
  sumOf { it.toDouble() }
    .let { sum -> map { i -> i.toDouble() / sum } }
    .runningReduce { acc, d -> d + acc }
)

class CDF(val cdf: List<Double>): List<Double> by cdf

// Draws a single sample using KS-transform w/binary search
fun CDF.sample(random: Random = Random.Default,
               target: Double = random.nextDouble()) =
  cdf.binarySearch { it.compareTo(target) }
    .let { if (it < 0) abs(it) - 1 else it }