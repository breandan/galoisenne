package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.*
import ai.hypergraph.kaliningraph.sampling.*
import ai.hypergraph.kaliningraph.types.*
import kotlin.math.*
import kotlin.random.Random


// Fully-parallelizable version of the Valiant repair algorithm, just append a .parallelize() call
fun newRepair(prompt: List<Σᐩ>, cfg: CFG, edits: Int = 3, skip: Int = 1, shift: Int = 0): Sequence<String> =
  generateLevenshteinEdits(cfg.terminals - cfg.blocked, prompt, edits, skip, shift)
    .map { prompt.apply(it) }
    .filter { it.matches(cfg) }
    .map { it.joinToString(" ") }

// Indices of the prompt tokens to be replaced and the tokens to replace them with
typealias Edit = List<Pair<Int, Σᐩ>>

fun Edit.intIdentifier(): Int = hashCode()

private fun Int.newEditLoc(range: IntRange): Int {
  val lambda = 1.0  // Mean of exponential distribution
  var newLoc: Int
  do {
    val randomExponential = -lambda * ln(Random.nextDouble())
    val offset = randomExponential.roundToInt()
    val direction = if (Random.nextBoolean()) 1 else -1  // Randomly choose direction of offset
    newLoc = this + direction * offset
  } while (newLoc !in range)  // Continue until the new location is within the range

  return newLoc
}

interface FastRandomSet<T>: Set<T> { fun randomOrNull(): T? }

fun genDefaultEdits(hints: List<Int>, maxEdits: Int, deck: List<Σᐩ>, seen: Set<Int>): Sequence<Edit> =
  generateSequence {
    val editSize = (1.. maxEdits).random()
    generateSequence { hints.random() }
      .zip(generateSequence { deck.random() })
      .filter { it.hashCode() !in seen }
      .take(editSize).toList()
  }

// Samples toTake random edits whose indices are a small distance from this edit
fun FastRandomSet<Edit>.resample(maxTake: Int,
                                 strLen: Int,
                                 deck: List<Σᐩ>,
                                 seen: Set<Int>): List<Edit> {
  val edits = mutableListOf<Edit>()

  while (edits.size < maxTake) {
    val edit = randomOrNull() ?: return emptyList()
    val locationsToEdit = Random.nextInt(1, edit.size + 1)
    // Bias the edits towards the original edit location
    val newEdit = edit.toMutableList()
    repeat(locationsToEdit) {
      val idx = Random.nextInt(0, edit.size)
      // Generates a peaky distribution of edits whose mode is the original edit location
      // and extrema are [0, strLen)
      val originalEditLocation = newEdit[idx].first
      val newEditLocation: Int =
        originalEditLocation.newEditLoc(0 until strLen)
      newEdit[idx] = newEditLocation to deck.random()
    }

//    if (Random.nextBoolean())
//      newEdit += newEdit.random().first.newEditLoc(0 until strLen) to deck.random()

    if (newEdit.hashCode() !in seen) edits.add(newEdit)
  }

  return edits
}

// Enumerates powerset levels from the bottom up, skipping the empty set
private fun Edit.subedits(): Sequence<Sequence<List<Pair<Int, Σᐩ>>>> =
  (1..size).asSequence()
  .map { choose(it).map { it.toList() } }

fun List<Σᐩ>.apply(edit: Edit): List<Σᐩ> {
  val res = toMutableList()
  edit.forEach { (i, nt) -> res[i] = nt }
  return res.filter { it != "ε" && it.isNotBlank() }
}

class Repair constructor(val orig: List<Σᐩ>, val edit: Edit, val result: List<Σᐩ>, val score: Double) {
  var timeMS: Long = -1

  fun resToStr() = result.joinToString(" ")

  val editSignature: String by lazy {
    orig.mapIndexed { i, ot -> ot to (edit.firstOrNull { i == it.first }?.second ?: ot) }
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


  override fun hashCode(): Int = result.hashCode()
  override fun equals(other: Any?): Boolean =
    if (other is Repair) result == other.result else false

  fun elapsed(): String = (if (timeMS == -1L) "N/A" else "${timeMS / 1000.0}").take(4) + "s"
  fun scoreStr(): String = "$score".take(5)

  // TODO: Computes a "fingerprint" of the repair to avoid redundant results
  //  Each fingerprint can be lazily expanded to a sequence of repairs
  //  formed by the Cartesian product of tokens at each change position
  //  e.g., "C + C" -> "1 + 2", "1 + 3", "2 + 1", "2 + 3", "3 + 1", "3 + 2"... etc.

  /**
   * This can be used to generate a sequence of repairs with the same edit fingerprint but alternate
   * tokens at each change location. This method may optionally be called on any Repair, but for the
   * sake of specificity, should only be called on repairs minimized by [minimalAdmissibleSubrepairs].
   */
  fun editSignatureEquivalenceClass(tokens: Set<Σᐩ>, filter: (List<Σᐩ>) -> Boolean, score: (List<Σᐩ>) -> Double): Repair =
    (sequenceOf(this) + edit.map { tokens }.cartesianProduct()
      .map {
        val edt = edit.mapIndexed { i, l -> l.first to it[i] }
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
      } ?: sequenceOf(Repair(orig, edit, result, score(result)))
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