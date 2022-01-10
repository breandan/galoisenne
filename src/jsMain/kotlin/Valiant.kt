import ai.hypergraph.kaliningraph.graphs.*
import kotlinx.browser.document
import kotlinx.html.dom.append
import kotlinx.html.p
import org.w3c.dom.events.KeyboardEvent

fun cfgParser(toParse: String = "aaabbb", mutableList: MutableList<String> = toParse.mapIndexed { i, c -> "$i:$c" }.toMutableList()) =
  animate(initial = LabeledGraph {
    //https://www.ps.uni-saarland.de/courses/seminar-ws06/papers/07_franziska_ebert.pdf#page=3
    S - LGVertex("XY").also { it - X; it - Y};
    X - LGVertex("XA").also{ it - A  ; it - X}; X - LGVertex("AA").also { it - A};
    Y - LGVertex("YB").also { it - B ; it - Y}; Y - LGVertex("BB").also { it - B};
    A - a; B - b
    mutableList.forEach { LGVertex(it).let { it - it } }
  }.reversed(),
    false) { it: KeyboardEvent, graphs ->
    val last = graphs.last()
    when {
      "k" in it.key -> { graphs.dropLast(1) }
      "j" in it.key -> {
        val newList = mutableListOf<String>()
        val chunks =  mutableList.mapIndexedNotNull { i, lbl ->
          val aVert = last[{ it.label == lbl }].first()
          val nonterminals = last[{ it.label == lbl.substringAfter(":") }].first().neighbors
          if(nonterminals.isNotEmpty()) {
            val mergeLabel = nonterminals.joinToString(",") { "$i:${it.label}" }
            newList.add(mergeLabel)
            LabeledGraph { LGVertex(mergeLabel).let { aVert - it } }
          } else null
        }
        graphs.add(last + chunks.reduce { lg1, lg2 -> lg1 + lg2 })
        mutableList.clear(); mutableList.addAll(newList)
      }
    }
  }.also {
    document.body?.append?.p { +"Initial string: $toParse" }
  }
