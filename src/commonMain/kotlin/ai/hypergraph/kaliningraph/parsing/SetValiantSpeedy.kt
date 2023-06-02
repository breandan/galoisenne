package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.*
import ai.hypergraph.kaliningraph.sampling.*


// Fully-parallelizable version of the Valiant repair algorithm, just append a .parallelize() call
fun newRepair(prompt: List<Σᐩ>, cfg: CFG, edits: Int = 3, skip: Int = 1, shift: Int = 0): Sequence<String> =
  generateLevenshteinEdits(cfg.terminals - cfg.blocked, prompt, edits, skip, shift)
    .map { prompt.apply(it) }
    .filter { it.matches(cfg) }

// Indices of the prompt tokens to be replaced and the tokens to replace them with
typealias Edit = Map<Int, Σᐩ>

// Enumerates the powerset from the bottom up, skipping the empty set
private fun Edit.subedits() =
  (1..size).asSequence()
  .map { keys.choose(it).map { it.associateWith { this[it]!! } } }

fun List<Σᐩ>.apply(edit: Edit): Σᐩ =
  mapIndexed { i, ot -> if (i in edit) edit[i]!! else ot }
    .filter { it != "ε" && it.isNotBlank() }.joinToString(" ").trim()

class Repair constructor(val orig: List<Σᐩ>, val edit: Edit, val result: Σᐩ, val score: Double) {
  var time: Long = -1

  val editSignature: String =
    orig.mapIndexed { i, ot -> ot to if (i in edit) edit[i]!! else ot }
      .map { (ot, nt) ->
        when {
          ot == nt -> "E"
          ot != "" && nt == "ε" -> "D"
          ot != "" && nt != "ε" -> "C.${nt.type()}"
          ot == "" && nt == "ε" -> ""
          ot == "" && nt != "ε" -> "I.${nt.type()}"
          else -> throw Exception("Unreachable")
        }
      }.filter { it.isNotBlank() }.joinToString(" ")

  override fun hashCode(): Int = editSignature.hashCode()
  override fun equals(other: Any?): Boolean =
    if (other is Repair) result == other.result else false

  fun String.type() = when {
    isNonterminalStub() -> "NT/$this"
    // Is a Java or Kotlin identifier character in Kotlin common library (no isJavaIdentifierPart)
    Regex("[a-zA-Z0-9_]+").matches(this) -> "ID/$this"
    any { it in BRACKETS } -> "BK/$this"
    else -> "OT"
  }

  fun elapsed(): String = (if (time == -1L) "N/A" else "${time / 1000.0}").take(4) + "s"
  fun scoreStr(): String = "$score".take(5)

  fun minimalAdmissibleSubrepairs(filter: (Σᐩ) -> Boolean, score: (Σᐩ) -> Double): Sequence<Repair> =
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