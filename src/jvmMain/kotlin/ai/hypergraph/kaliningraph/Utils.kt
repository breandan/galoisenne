package ai.hypergraph.kaliningraph

import ai.hypergraph.kaliningraph.circuits.UnlabeledEdge
import ai.hypergraph.kaliningraph.image.matToBase64Img
import ai.hypergraph.kaliningraph.tensor.*
import ai.hypergraph.kaliningraph.types.*
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


const val THICKNESS = 4.0
const val DARKMODE = false

fun MutableGraph.render(format: Format, layout: Engine = DOT): Renderer =
   toGraphviz().apply { engine(layout) }.render(format)

fun IGraph<*, *, *>.html() = toGraphviz().render(SVG).toString()
fun IGraph<*, *, *>.show(filename: String = "temp") =
  toGraphviz().render(SVG).run {
    toFile(File.createTempFile(filename, ".svg"))
  }.show()

fun <T> Matrix<T, *, *>.show(filename: String = "temp") = matToBase64Img().let { data ->
  File.createTempFile(filename, ".html").apply {
    writeText("<html><body><img src=\"$data\" height=\"500\" width=\"500\"/></body></html>")
  }
}.show()

fun TypedVertex<*>.render() = (this as IVertex<*, *, *>).render().also {
  if (occupied) it.add(Style.FILLED, RED.fill()) else it.add(BLACK)}
fun TypedEdge<*>.render() = (this as IEdge<*, *, *>).render().also { it.add(if (source.occupied) RED else BLACK) }

fun IGraph<*, *, *>.render() = toGraphviz()
fun IVertex<*, *, *>.render(): MutableNode = Factory.mutNode(id).add(Label.of(toString()))
fun IEdge<*, *, *>.render(): Link = (source.render() - target.render()).add(Label.of(""))
fun LGVertex.render() = (this as IVertex<*, *, *>).render().also { if (occupied) it.add(Style.FILLED, RED.fill()) else it.add(BLACK) }
fun UnlabeledEdge.render(): Link = (target.render() - source.render()).add(Label.of(""))
  .add(if (source.neighbors.size == 1) BLACK else if (source.outgoing.indexOf(this) % 2 == 0) BLUE else RED)
fun LabeledEdge.render() =
  (this as IEdge<*, *, *>).render().also { it.add(if (source.occupied) RED else BLACK) }

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

fun randomString() = UUID.randomUUID().toString().take(5)

operator fun MutableNode.minus(target: LinkTarget): Link = addLink(target).links().last()!!

fun <T> powBench(constructor: T, matmul: (T, T) -> T): Long =
  measureTimeMillis { constructor.power(100, matmul) }

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