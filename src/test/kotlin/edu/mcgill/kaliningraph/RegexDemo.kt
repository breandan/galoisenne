package edu.mcgill.kaliningraph

import edu.mcgill.kaliningraph.automata.KRegex
import guru.nidi.graphviz.*
import guru.nidi.graphviz.attribute.*
import guru.nidi.graphviz.attribute.Rank.RankDir.LEFT_TO_RIGHT
import guru.nidi.graphviz.engine.Format
import guru.nidi.graphviz.model.*
import kweb.*
import kweb.state.KVar
import java.net.URL

@ExperimentalStdlibApi
fun main() {
  val port = 16097
  Kweb(port = port) {
    doc.body.new {
      element("p").innerHTML("Enter a regular expression:")
      val regexVar = KVar("a|b*")
      textArea().apply { value = regexVar }
      element("div").innerHTML(regexVar.map { makeNFA(it).toGraphviz() })
      val disp = KVar("bbb")
      element("p").innerHTML("Enter a string to test:")
      textArea().apply { value = disp }
      p().text(disp.map { regex.check(it) })
    }
  }


  URL("http://0.0.0.0:$port").show()
}

var regex = KRegex("ε")
fun makeNFA(input: String) =
  if (regex.regex != input)
    try {
      KRegex(input.ifEmpty { "ε" }).also { regex = it }
    }catch(ex: Exception) {regex}
  else regex

fun KRegex.toGraphviz() =
  graph(directed = true) {
    val color = Color.BLACK
    edge[color, Arrow.NORMAL, Style.lineWidth(THICKNESS)]
    graph[Rank.dir(LEFT_TO_RIGHT), Color.TRANSPARENT.background()]
    node[color, color.font(), Font.config("Helvetica", 20), Style.lineWidth(THICKNESS)]

    transitions.forEach { transition ->
      (Factory.mutNode(transition.from.stateId.toString()).colorIt() -
        Factory.mutNode(transition.to.stateId.toString()).colorIt())
        .add(Label.of(transition.sym))
    }
  }.toGraphviz().render(Format.SVG).toString()

private fun MutableNode.colorIt(): MutableNode {
  if (name().value() in regex.initialStates.map { it.stateId.toString() })
    this.attrs().add("peripheries", 2)
  else if (name().value() in regex.finalStates.map { it.stateId.toString() })
    this.attrs().add("peripheries", 3)
  return this
}