package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.tensor.UTMatrix


fun Σᐩ.fastMatch(CFG: CCFG): Boolean = CFG.isValid(tokenizeByWhitespace())

fun CCFG.initialUTMatrix(tokens: List<Σᐩ>): UTMatrix<Forest> =
  UTMatrix(
    ts = tokens.mapIndexed { i, terminal ->
      bimap[listOf(terminal)].let { representatives ->
        (if (!terminal.isNonterminalStubIn(this)) representatives
        // We use the original form because A -> B -> C can be normalized
        // to A -> C, and we want B to be included in the equivalence class
        else representatives.map { originalForm.equivalenceClass(it) }.flatten().toSet())
//          .also { println("Equivalence class: $terminal -> $representatives -> $it") }
      }.map { Tree(root = it, terminal = terminal, span = i until (i + 1)) }.toSet()
    }.toTypedArray(),
    algebra = makeAlgebra()
  )

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
//  .also { if (it) println("Sol:\n$finalConfig") }

