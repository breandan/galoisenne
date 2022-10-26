package ai.hypergraph.kaliningraph.sat

import ai.hypergraph.kaliningraph.graphs.LabeledGraph
import ai.hypergraph.kaliningraph.image.toHtmlPage
import ai.hypergraph.kaliningraph.parsing.*
import ai.hypergraph.kaliningraph.tensor.FreeMatrix
import ai.hypergraph.kaliningraph.visualization.html
import org.logicng.formulas.*


fun FreeMatrix<Set<Tree>>.toGraphTable(): FreeMatrix<String> =
  data.map {
    it.mapIndexed { i, t -> t.toGraph("$i") }
      .fold(LabeledGraph()) { ac, lg -> ac + lg }.html()
  }.let { FreeMatrix(it) }

fun CFG.parseHTML(s: String): String = parseTable(s).toGraphTable().toHtmlPage()

@JvmName("summarizeBooleanMatrix")
fun FreeMatrix<List<Boolean>?>.summarize(cfg: CFG): String =
  map {
    when {
      it == null -> "?"
      it.toString().length < 5 -> ""
//      else -> "C"
      cfg.toNTSet(it.toBooleanArray()).isEmpty() -> it.distinct()
      else -> "${cfg.toNTSet(it.toBooleanArray())}".replace("START", "S")
    }
  }.toString()

@JvmName("summarizeFormulaMatrix")
fun FreeMatrix<SATVector>.summarize(cfg: CFG): String =
  map {
    when {
      it.isEmpty() -> ""
      it.all { it is Variable } -> "V[${it.size}]"
      it.all { it is Constant } -> "C[${cfg.toNTSet(it.map { it == T }.toBooleanArray())}]"
//      it.all { it is Constant } -> "C[${it.count { it == T }}/${it.size}]"
      it.any { it is Variable } -> "M"
      else -> "F[${it.sumOf(Formula::numberOfAtoms)}]"
    }
  }.toString()

// Summarize fill structure of bit vector variables
fun FreeMatrix<SATVector>.fillStructure(): FreeMatrix<String> =
  FreeMatrix(numRows, numCols) { r, c ->
    this[r, c].let {
      if (it.all { it == F }) "0"
      else if (it.all { it in setOf(T, F) }) "LV$r$c"
      else "BV$r$c[len=${it.toString().length}]"
    }
  }

fun Formula.toPython(
  params: String = variables().joinToString(", ") { it.name() },
  bodyY: String = toString()
    .replace("~", "neg/")
    .replace("|", "|V|")
    .replace("&", "|Λ|"),
  bodyX: String = toString()
    .replace("~", "not ")
    .replace("|", "or")
    .replace("&", "and")
) = """
def y_constr($params):
    return $bodyY
    
def x_constr($params):
    return $bodyX
""".trimIndent()

fun Map<Variable, Boolean>.toPython() =
  "assert x_constr(" + entries.joinToString(","){ (k, v) -> k.name() + "=" +
    v.toString().let { it[0].uppercase() + it.substring(1) } } + ")"

fun SATRubix.startVariable(cfg: CFG) = diagonals.last().first()[cfg.bindex[START_SYMBOL]]