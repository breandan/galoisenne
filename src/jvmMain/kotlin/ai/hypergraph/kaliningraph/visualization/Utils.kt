package ai.hypergraph.kaliningraph.visualization

import ai.hypergraph.kaliningraph.graphs.*
import ai.hypergraph.kaliningraph.image.matToBase64Img
import ai.hypergraph.kaliningraph.tensor.Matrix
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
import java.io.File
import java.net.URL

const val THICKNESS = 4.0
const val DARKMODE = false

fun MutableGraph.render(format: Format, layout: Engine = DOT): Renderer =
   toGraphviz().apply { engine(layout) }.render(format)

fun String.show() = File.createTempFile("" + hashCode(), ".html")
  .apply { writeText(this@show) }.show()
fun IGraph<*, *, *>.html() = toGraphviz().render(SVG).toString()
fun IGraph<*, *, *>.show(filename: String = "temp") =
  toGraphviz().render(SVG).run {
    toFile(File.createTempFile(filename, ".svg"))
  }.show()

fun Matrix<*, *, *>.show(filename: String = "temp") = matToBase64Img().let { data ->
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

operator fun MutableNode.minus(target: LinkTarget): Link = addLink(target).links().last()!!

fun <G : IGraph<G, E, V>, E : IEdge<G, E, V>, V : IVertex<G, E, V>>
  IGraph<G, E, V>.toGraphviz() =
  graph(directed = true, strict = true) {
    val color = if (DARKMODE) WHITE else BLACK
    edge[color, NORMAL, lineWidth(THICKNESS)]
    graph[CONCENTRATE, Rank.dir(LEFT_TO_RIGHT),
      TRANSPARENT.background(), margin(0.0),
      COMPOUND, Attributes.attr("nslimit", "20")]
    node[color, color.font(), Font.config("Helvetica", 20),
      lineWidth(THICKNESS), Attributes.attr("shape", "Mrecord")]

    for(vertex in vertices) vertex.render()
    for ((vertex, edge) in edgList)
      edge.render().also { if (vertex is LGVertex && vertex.occupied) it.add(RED) }
  }