package ai.hypergraph.kaliningraph.visualization

import ai.hypergraph.kaliningraph.KBitSet
import ai.hypergraph.kaliningraph.automata.FSA
import ai.hypergraph.kaliningraph.automata.GRE
import ai.hypergraph.kaliningraph.automata.GRE.CAT
import ai.hypergraph.kaliningraph.automata.GRE.CUP
import ai.hypergraph.kaliningraph.automata.GRE.EPS
import ai.hypergraph.kaliningraph.automata.GRE.SET
import ai.hypergraph.kaliningraph.graphs.*
import ai.hypergraph.kaliningraph.image.*
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
import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.*
import kotlin.IllegalArgumentException
import kotlin.text.StringBuilder

const val THICKNESS = 4.0
const val DARKMODE = false

fun MutableGraph.render(format: Format, layout: Engine = DOT): Renderer =
   toGraphviz().apply { engine(layout) }.render(format)

fun String.alsoCopy() = also {
  try {
    StringSelection(this).let {
      Toolkit.getDefaultToolkit().systemClipboard.setContents(it, it)
    }
  } catch (e: Exception) { println("Error copying to clipboard: $e") }
}

fun GRE.toDOTGraph(rankDir: String = "TB", font: String = "Helvetica", dedupLeaves: Boolean = true): String {
  fun Int.toUnicodeSubscript(): String = when (this) {
    0 -> "\u2080"
    1 -> "\u2081"
    2 -> "\u2082"
    3 -> "\u2083"
    4 -> "\u2084"
    5 -> "\u2085"
    6 -> "\u2086"
    7 -> "\u2087"
    8 -> "\u2088"
    9 -> "\u2089"
    else -> throw IllegalArgumentException("Input must be between 0 and 9")
  }

  fun KBitSet.labelize(): String =
    (0 until n).mapNotNull {  if (this[it]) "Σ${it.toUnicodeSubscript()}" else null }.joinToString(",", "{", "}")

  data class Key(val kind: String, val payload: String)

  val nodeId   = mutableMapOf<Int, String>()
  val nodeDecl = StringBuilder()
  val edgeDecl = StringBuilder()
  var nextId = 0

  fun newNodeId() = "n${nextId++}"

  var i = 0
  fun declareNodeA(label: String, shape: String = "circle", style: String = ", style=\"rounded\"", extra: String = ""): String =
    nodeId.getOrPut(i++) {
      val id = newNodeId()
      nodeDecl.append("  $id [label=\"$label\", shape=$shape $style $extra];\n")
      id
    }

  fun declareNodeB(key: Key, label: String, shape: String = "circle"): String =
    nodeId.getOrPut(i++) {
      val id = newNodeId()
      nodeDecl.append("  $id [label=\"$label\", shape=$shape, style=\"rounded\"];\n")
      id
    }

  fun visit(g: GRE): String = when (g) {
    is EPS -> declareNodeB(Key("EPS", ""), "ε", "plaintext")

    is SET -> declareNodeA(g.s.labelize(), "box", extra = ", width=0.5")

    is CUP -> {
      if (!isLeafCup()) {
        val id = declareNodeB(Key("CUP${g.hash()}", ""), "∨")
        for (child in g.args) { edgeDecl.append("  $id -> ${visit(child)};\n") }
        id
      } else {
        val q = g.toSet() as SET
        val key = if (dedupLeaves) Key("SET", q.s.toString())
        else Key("SET${g.hashCode()}", "")
        declareNodeB(key, q.s.labelize(), "box")
      }
    }

    is CAT -> {
      val id = declareNodeA("·", "invhouse", ",", "width=0.5")
      val lId = visit(g.l);  edgeDecl.append("  $id -> $lId;\n")
      val rId = visit(g.r);  edgeDecl.append("  $id -> $rId;\n")
      id
    }
  }

  visit(this)

  return buildString {
    appendLine("strict digraph GRE {")
    appendLine("  rankdir=$rankDir;")
    appendLine("  node [order=out];")
    append(nodeDecl)
    append(edgeDecl)
    appendLine("}")
  }
}

fun GRE.showEditable() {
  ProcessBuilder(browserCmd,
    URLEncoder.encode(toDOTGraph())
      .replace("+", "%20")
      .let { "https://dreampuf.github.io/GraphvizOnline/#$it" }
  ).start()
}

fun String.show() = File.createTempFile("" + hashCode(), ".html")
  .apply { writeText(this@show) }.show()
fun IGraph<*, *, *>.html() = toGraphviz().render(SVG).toString()
fun IGraph<*, *, *>.show(filename: String = "temp") =
  toGraphviz().render(SVG).toString().show()

fun Matrix<*, *, *>.show(filename: String = "temp") = matToBase64Img().let { data ->
  File.createTempFile(filename, ".html").apply {
    writeText("<html><body><img src=\"$data\" height=\"500\" width=\"500\"/></body></html>")
  }
}.show()

fun TypedVertex<*>.render(): MutableNode = (this as IVertex<*, *, *>).render()
  .also { if (occupied) it.add(Style.FILLED, RED.fill()) else it.add(BLACK) }
fun TypedEdge<*>.render(): Link =
  (this as IEdge<*, *, *>).render().also { it.add(if (source.occupied) RED else BLACK) }

fun IGraph<*, *, *>.render(): MutableGraph = toGraphviz()
fun String.dotSanitize() =
  replace("{", "\\{").replace("}", "\\}")
    .replace("<", "\\<").replace(">", "\\>")
fun IVertex<*, *, *>.render(): MutableNode = Factory.mutNode(id).add(Label.of(toString().dotSanitize()))
fun IEdge<*, *, *>.render(): Link = (source.render() - target.render()).add(Label.of(""))
fun LGVertex.render(): MutableNode =
  (this as IVertex<*, *, *>).render().also { if (occupied) it.add(Style.FILLED, RED.fill()) else it.add(BLACK) }
fun UnlabeledEdge.render(): Link = (target.render() - source.render()).add(Label.of(""))
  .add(if (source.neighbors.size == 1) BLACK else if (source.outgoing.indexOf(this) % 2 == 0) BLUE else RED)
fun LabeledEdge.render(): Link =
  (this as IEdge<*, *, *>).render().also { it.add(if (source.occupied) RED else BLACK) }

val browserCmd: String = System.getProperty("os.name").lowercase().let { os ->
  when {
    "win" in os -> "rundll32 url.dll,FileProtocolHandler"
    "mac" in os -> "open"
    "nix" in os || "nux" in os -> "x-www-browser"
    else -> throw Exception("Unable to open browser for unknown OS: $os")
  }
}

fun String.render() = Factory.mutNode(this).add(Label.of(toString()))

fun File.show() = ProcessBuilder(browserCmd, path).start()
fun URL.show() = ProcessBuilder(browserCmd, toString()).start()
fun FSA.show() = toGraphviz().render(SVG).toString().show()

fun FSA.showEditable() {
  ProcessBuilder(browserCmd,
    URLEncoder.encode(toDot())
      .replace("+", "%20")
      .let { "https://dreampuf.github.io/GraphvizOnline/#$it" }
  ).start()
}

fun FSA.showLevEditable() {
  ProcessBuilder(browserCmd,
    URLEncoder.encode(levToDot())
      .replace("+", "%20")
      .let { "https://dreampuf.github.io/GraphvizOnline/#$it" }
  ).start()
}

operator fun MutableNode.minus(target: LinkTarget): Link = addLink(target).links().last()!!

fun FSA.toGraphviz() =
  graph(directed = true, strict = true) {
    val color = if (DARKMODE) WHITE else BLACK
    edge[color, NORMAL, lineWidth(THICKNESS)]
    graph[CONCENTRATE, Rank.dir(LEFT_TO_RIGHT),
      TRANSPARENT.background(), margin(0.0),
      COMPOUND, Attributes.attr("nslimit", "20")]
    node[color, color.font(), Font.config("JetBrains Mono", 15),
      lineWidth(THICKNESS), Attributes.attr("shape", "Mrecord")]

    for (vertex in states) Factory.mutNode(vertex).add(Label.of(toString()))
      .add(if (vertex in final) Style.FILLED else Style.SOLID)
      .add(Attributes.attr("fillcolor", if (vertex in final) "lightgray" else WHITE))
    for ((a, b) in edgeLabels.keys)
      (a.render() - b.render()).add(Label.of(""))
  }

fun <G : IGraph<G, E, V>, E : IEdge<G, E, V>, V : IVertex<G, E, V>>
  IGraph<G, E, V>.toGraphviz(): MutableGraph =
  graph(directed = true, strict = true) {
    val color = if (DARKMODE) WHITE else BLACK
    edge[color, NORMAL, lineWidth(THICKNESS)]
    graph[CONCENTRATE, Rank.dir(LEFT_TO_RIGHT),
      TRANSPARENT.background(), margin(0.0),
      COMPOUND, Attributes.attr("nslimit", "20")]
    node[color, color.font(), Font.config("JetBrains Mono", 15),
      lineWidth(THICKNESS), Attributes.attr("shape", "Mrecord")]

    for (vertex in vertices) vertex.render()
    for ((vertex, edge) in edgList)
      edge.render().also { if (vertex is LGVertex && vertex.occupied) it.add(RED) }
  }