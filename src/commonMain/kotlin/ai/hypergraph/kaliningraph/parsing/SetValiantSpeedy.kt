package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.sampling.MDSamplerWithoutReplacementNK


// Fully-parallelizable version of the Valiant repair algorithm, just append a .parallelize() call
fun newRepair(prompt: Σᐩ, cfg: CFG, edits: Int = 3, skip: Int = 1, shift: Int = 0): Sequence<String> =
  generateLevenshteinEdits(cfg.terminals - cfg.blocked, prompt.tokenizeByWhitespace(), edits, skip, shift)
    .filter { it.matches(cfg) }

fun generateLevenshteinEdits(
  tokens: Set<Σᐩ>,
  promptTokens: List<Σᐩ>,
  edits: Int,
  skip: Int = 1,
  shift: Int = 0
) =
  MDSamplerWithoutReplacementNK(tokens, n = promptTokens.size, k = edits, skip, shift)
    .map { (editLocs, tokens) ->
      val toReplaceWith = tokens.toMutableList()
      val newTokens = promptTokens.mapIndexed { i, ot ->
        if (i in editLocs) toReplaceWith.removeFirst() else ot
      }
      newTokens.joinToString(" ")
    }
    .map { it.replace("ε", "").replace(Regex("\\s+"), " ") }