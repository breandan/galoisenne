package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.sampling.MDSamplerWithoutReplacementNK


// Fully Parallelizable version of the Valiant algorithm for CFL repair
fun newRepair(prompt: Σᐩ, cfg: CFG, edits: Int = 3, skip: Int = 1, shift: Int = 0): Sequence<String> =
  generateLevenshteinEdits(cfg, prompt.tokenizeByWhitespace(), edits, skip, shift)
    .filter { it.matches(cfg) }

private fun generateLevenshteinEdits(
  cfg: CFG,
  promptTokens: List<Σᐩ>,
  edits: Int,
  skip: Int,
  shift: Int
) =
  MDSamplerWithoutReplacementNK(cfg.terminals, n = promptTokens.size, k = edits, skip, shift)
    .map { (editLocs, tokens) ->
      val toReplaceWith = tokens.toMutableList()
      val newTokens = promptTokens.mapIndexed { i, ot ->
        if (i in editLocs) toReplaceWith.removeFirst() else ot
      }
      newTokens.joinToString(" ")
    }
    .map { it.replace("ε", "").replace(Regex("\\s+"), " ") }