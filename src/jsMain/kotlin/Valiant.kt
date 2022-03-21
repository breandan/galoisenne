import ai.hypergraph.kaliningraph.graphs.*
import kotlinx.browser.document
import kotlinx.html.dom.append
import kotlinx.html.p
import org.w3c.dom.events.KeyboardEvent

val canonical: MutableList<LabeledGraph> = mutableListOf(
  LabeledGraph {
    "0:A" - "01:AA"
    "1:A" - "01:AA"
    "2:A" - "2:XA,2:AA"
    "3:B" - "3:YB,3:BB"
    "4:B" - "45:BB"
    "5:B" - "45:BB"
  },
  LabeledGraph {
    "2:XA,2:AA" - "2:XA,2:X"
    "3:YB,3:BB" - "3:YB,3:Y"
    "01:AA" - "01:X"
    "45:BB" - "45:Y"
  },
  LabeledGraph {
    "01:X" - "02:X"
    "2:XA,2:X" - "02:X"
    "3:YB,3:Y" - "35:Y"
    "45:Y" - "35:Y"
  },
  LabeledGraph {
    "02:X" - "05:XY"
    "35:Y" - "05:XY"
  },
  LabeledGraph {
    "05:XY" - "05:S"
  },
)
var i = 0

fun cfgParser(toParse: String = "aaabbb",
              mutableList: MutableList<String> = toParse.mapIndexed { i, c -> "$i:$c" }
                .toMutableList()
) =
  animate(
    initial = LabeledGraph {
      //https://www.ps.uni-saarland.de/courses/seminar-ws06/papers/07_franziska_ebert.pdf#page=3
      "S" - "XY".also { it - "X"; it - "Y" }
      "X" - "XA".also { it - "A"; it - "X" }; "X" - "AA".also { it - "A" }
      "Y" - "YB".also { it - "B"; it - "Y" }; "Y" - "BB".also { it - "B" }
      "A" - a; "B" - b

      mutableList.forEach { LGVertex(it).let { it - it } }
    }.reversed(),
    false
  ) { it: KeyboardEvent, graphs ->
    val last = graphs.last()
    i++
    when {
      "k" in it.key -> { graphs.dropLast(1) }
      "j" in it.key -> {
        val newList = mutableListOf<String>()
        val chunks = mutableList.mapIndexedNotNull { i, lbl ->
          val aVert = last[{ it.label == lbl }].first()
          val nonterminals =
            last[{ it.label == lbl.substringAfter(":") }].first().neighbors
          if (nonterminals.isNotEmpty()) {
            val mergeLabel = nonterminals.joinToString(",") { "$i:${it.label}" }
            newList.add(mergeLabel)
            LabeledGraph { LGVertex(mergeLabel).let { aVert - it } }
          } else null
        }
        if (i < 2) {
          graphs.add(last + chunks.reduce { lg1, lg2 -> lg1 + lg2 })
          mutableList.clear(); mutableList.addAll(newList)
        } else {
          graphs.add(last + canonical.removeFirst())
        }
      }
    }
  }.also {
    document.body?.append {
      p { +"""Initial string: $toParse""" }
      p {
        +"""
      |CFG = {
      |  S → XY,
      |  X → XA | AA,
      |  Y → YB | BB,
      |  A → a,
      |  B → b
      |}
    """.trimMargin()
      }
    }
  }
