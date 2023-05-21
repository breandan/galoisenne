package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.sampling.*
import kotlin.math.pow
import kotlin.random.Random


// Fully-parallelizable version of the Valiant repair algorithm, just append a .parallelize() call
fun newRepair(prompt: List<Σᐩ>, cfg: CFG, edits: Int = 3, skip: Int = 1, shift: Int = 0): Sequence<String> =
  generateLevenshteinEdits(cfg.terminals - cfg.blocked, prompt, edits, skip, shift)
    .map { prompt.apply(it) }
    .filter { it.matches(cfg) }

// Indices of the prompt tokens to be replaced and the tokens to replace them with
typealias Edit = Map<Int, Σᐩ>

// Enumerates the powerset from the bottom up, skipping the empty set
fun Edit.subedits(): Sequence<Edit> = (1..size).asSequence()
  .flatMap { keys.choose(it).map { it.associateWith { this[it]!! } } }

fun List<Σᐩ>.apply(edit: Edit): Σᐩ =
  mapIndexed { i, ot -> if (i in edit) edit[i]!! else ot }
    .filter { it != "ε" && it.isNotBlank() }.joinToString(" ")

// If this fails, it's probably because the sample space is too large.
// Short of migrating to a 64-bit LFSR, the solution is to reduce the
// number of tokens^edits to be less than ~2^31, i.e. 2,147,483,647.
fun generateLevenshteinEdits(
  deck: Set<Σᐩ>,
  promptTokens: List<Σᐩ>,
  edits: Int,
  skip: Int = 1,
  shift: Int = 0,
) =
  MDSamplerWithoutReplacementNK(deck, n = promptTokens.size, k = edits, skip, shift)

fun generateLevenshteinEditsUpTo(
  deck: Set<Σᐩ>,
  promptTokens: List<Σᐩ>,
  edits: Int,
  skip: Int = 1,
  shift: Int = 0
) =
  (1 .. edits).asSequence().flatMap {
    generateLevenshteinEdits(deck, promptTokens, edits = it, skip, shift)
  }