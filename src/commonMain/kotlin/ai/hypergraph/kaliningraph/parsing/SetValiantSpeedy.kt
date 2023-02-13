package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.graphs.LabeledGraph
import ai.hypergraph.kaliningraph.tensor.UTMatrix
import ai.hypergraph.kaliningraph.types.*

// Turns out, this is only marginally faster than the original
fun CFG.toClass(): CCFG = CCFG(this)

class CCFG(val cfg: CFG): CFG by cfg {
  val language: CFL = cfg.language
  val delimiters: Array<Σᐩ> = cfg.delimiters
  val nonterminals: Set<Σᐩ> = cfg.nonterminals
  val symbols: Set<Σᐩ>  = cfg.symbols
  val terminals: Set<Σᐩ>  = cfg.terminals
  val terminalUnitProductions: Set<Production> = cfg.terminalUnitProductions
  val unitProductions: Set<Production> = cfg.unitProductions
  val nonterminalProductions: Set<Production> = cfg.nonterminalProductions
  val bimap: BiMap = cfg.bimap
  val tmap = cfg.tmap
  val bindex: Bindex = cfg.bindex
  val joinMap: JoinMap by lazy { cfg.joinMap }
  val graph: LabeledGraph = cfg.graph
  val normalForm: CCFG by lazy { cfg.normalForm.toClass() }
  val originalForm: CCFG by lazy { cfg.originalForm.toClass() }
  val nonparametricForm: CCFG by lazy { cfg.nonparametricForm.toClass() }
  val reachability by lazy { cfg.reachability }
  val unitReachability by lazy { cfg.unitReachability }
  val noNonterminalStubs: CCFG by lazy { cfg.noNonterminalStubs.toClass() }
}

fun Σᐩ.fastMatch(CFG: CCFG): Boolean = CFG.isValid(tokenizeByWhitespace())

fun CCFG.isValid(str: List<Σᐩ>): Boolean =
  START_SYMBOL in parse(str.run {
    if (isEmpty()) listOf("ε", "ε", "ε")
    else if (size == 1) listOf("ε", first(), "ε")
    else this
  }).map { it.root }

fun CCFG.parse(
  tokens: List<Σᐩ>,
  utMatrix: UTMatrix<Forest> = initialUTMatrix(tokens),
): Forest = utMatrix.seekFixpoint().diagonals.last().firstOrNull() ?: emptySet()

fun CCFG.initialUTMatrix(tokens: List<Σᐩ>): UTMatrix<Forest> =
  UTMatrix(
    ts = tokens.mapIndexed { i, terminal ->
      bimap[listOf(terminal)].let { representatives ->
        (if (!terminal.isNonterminalStubInFast(this)) representatives
        // We use the original form because A -> B -> C can be normalized
        // to A -> C, and we want B to be included in the equivalence class
        else representatives.map { originalForm.equivalenceClass(it) }.flatten().toSet())
//          .also { println("Equivalence class: $terminal -> $representatives -> $it") }
      }.map { Tree(root = it, terminal = terminal, span = i until (i + 1)) }.toSet()
    }.toTypedArray(),
    algebra = makeFastAlgebra()
  )

fun CCFG.equivalenceClass(from: Σᐩ): Set<Σᐩ> = reachableSymbolsViaUnitProds(from)

fun CCFG.reachableSymbolsViaUnitProds(from: Σᐩ = START_SYMBOL): Set<Σᐩ> =
  unitReachability.getOrPut(from) {
    LabeledGraph {
      unitProductions.map { it.LHS to it.RHS.first() }
//      .filter { (a, b) -> nonterminals.containsAll(listOf(a, b)) }
        .forEach { (a, b) -> a - b }
    }.let {
      setOf(from) + (it.transitiveClosure(setOf(from)) +
          it.reversed().transitiveClosure(setOf(from)))
    }.filter { it in nonterminals }
  }


fun Σᐩ.isNonterminalStubFast() = first() == '<' && last() == '>'
fun Σᐩ.isNonterminalStubInFast(cfg: CCFG): Boolean = isNonterminalStubFast() && drop(1).dropLast(1) in cfg.nonterminals

fun CCFG.makeFastAlgebra(): Ring<Forest> =
  Ring.of(// Not a proper ring, but close enough.
    // 0 = ∅
    nil = setOf(),
    // x + y = x ∪ y
    plus = { x, y -> x union y },
    // x · y = { A0 | A1 ∈ x, A2 ∈ y, (A0 -> A1 A2) ∈ P }
    times = { x, y -> fastTreeJoin(x, y) }
  )

fun CCFG.fastTreeJoin(left: Forest, right: Forest): Forest =
  (left * right).flatMap { (lt, rt) ->
    bimap[listOf(lt.root, rt.root)].map { Tree(it, null, lt, rt) }
  }.toSet()