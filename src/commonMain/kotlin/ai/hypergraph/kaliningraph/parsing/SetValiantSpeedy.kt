package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.*
import ai.hypergraph.kaliningraph.sampling.*
import ai.hypergraph.kaliningraph.types.*


// Fully-parallelizable version of the Valiant repair algorithm, just append a .parallelize() call
fun newRepair(prompt: List<Σᐩ>, cfg: CFG, edits: Int = 3, skip: Int = 1, shift: Int = 0): Sequence<String> =
  generateLevenshteinEdits(cfg.terminals - cfg.blocked, prompt, edits, skip, shift)
    .map { prompt.apply(it) }
    .filter { it.matches(cfg) }
    .map { it.joinToString(" ") }

// Indices of the prompt tokens to be replaced and the tokens to replace them with
typealias Edit = Map<Int, Σᐩ>

// Enumerates powerset levels from the bottom up, skipping the empty set
private fun Edit.subedits(): Sequence<Sequence<Map<Int, Σᐩ>>> =
  (1..size).asSequence()
  .map { keys.choose(it).map { it.associateWith { this[it]!! } } }

fun List<Σᐩ>.apply(edit: Edit): List<Σᐩ> {
  val res = toMutableList()
  edit.forEach { (i, nt) -> res[i] = nt }
  return res.filter { it != "ε" && it.isNotBlank() }
}

class Repair constructor(val orig: List<Σᐩ>, val edit: Edit, val result: List<Σᐩ>, val score: Double) {
  var timeMS: Long = -1

  fun resToStr() = result.joinToString(" ")

  val editSignature: String by lazy {
    orig.mapIndexed { i, ot -> ot to if (i in edit) edit[i]!! else ot }
      .map { (ot, nt) ->
        when {
          ot == nt -> "E" // Same token
          ot != "" && nt == "ε" -> "D" // Deletion
          ot != "" && nt != "ε" -> "C.${nt.cfgType()}" // Substitution
          ot == "" && nt == "ε" -> "" // No-op
          ot == "" && nt != "ε" -> "I.${nt.cfgType()}" // Insertion
          else -> throw Exception("Unreachable")
        }
      }.filter { it.isNotBlank() }.joinToString(" ")
  }

  fun matches(groundTruth: String): Boolean = resToStr() == groundTruth

  // Computes a "fingerprint" of the repair to avoid redundant results
  // Each fingerprint can be lazily expanded to a sequence of repairs
  // formed by the Cartesian product of tokens at each change position
  // e.g., "C + C" -> "1 + 2", "1 + 3", "2 + 1", "2 + 3", "3 + 1", "3 + 2"... etc.
  override fun hashCode(): Int = result.hashCode()
  override fun equals(other: Any?): Boolean =
    if (other is Repair) result == other.result else false

  fun elapsed(): String = (if (timeMS == -1L) "N/A" else "${timeMS / 1000.0}").take(4) + "s"
  fun scoreStr(): String = "$score".take(5)

  /**
   * This can be used to generate a sequence of repairs with the same edit fingerprint but alternate
   * tokens at each change location. This method may optionally be called on any Repair, but for the
   * sake of specificity, should only be called on repairs minimized by [minimalAdmissibleSubrepairs].
   */
  fun editSignatureEquivalenceClass(tokens: Set<Σᐩ>, filter: (List<Σᐩ>) -> Boolean, score: (List<Σᐩ>) -> Double): Repair =
    (sequenceOf(this) + edit.values.map { tokens }.cartesianProduct()
      .map {
        val edt = edit.keys.zip(it).toMap()
        val res = orig.apply(edt)
        edt to res
      }
      .filter { filter(it.second) }
      .map { (edt, res) -> Repair(orig, edt, res, score(res)) }
      ).let {
        val esec = it.toList().sortedBy { it.score }
        // Take lowest-scoring repair as representative of the equivalence class
        esec.first().also { it.equivalenceClass = esec.drop(1) }
      }

  var equivalenceClass: List<Repair> = listOf()

  /**
   * Collapses a large repair (which may contain many extraneous edits) into a
   * minimal repair that is still admissible (i.e., still matches the grammar).
   * This is done by locating the smallest admissible edit, and then enumerating
   * all other subedits of the same size.
   */
  fun minimalAdmissibleSubrepairs(filter: (List<Σᐩ>) -> Boolean, score: (List<Σᐩ>) -> Double): Sequence<Repair> =
    edit.subedits()
      .map { it.filter { filter(orig.apply(it)) } }
      .firstOrNull { it.any() }?.map { subedit ->
        val result = orig.apply(subedit)
        Repair(orig, subedit, result, score(result))
      } ?: sequenceOf(this)
}

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