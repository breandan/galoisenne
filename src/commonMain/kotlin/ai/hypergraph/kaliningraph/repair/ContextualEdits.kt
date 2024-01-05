package ai.hypergraph.kaliningraph.repair

import ai.hypergraph.kaliningraph.parsing.*
import kotlin.random.Random

enum class EditType { INS, DEL, SUB }
data class ContextEdit(val type: EditType, val context: Context, val newMid: String) {
  override fun toString(): String = context.run {
    "$type, (( " + when (type) {
      EditType.INS -> "$left [${newMid}] $right"
      EditType.DEL -> "$left ~${mid}~ $right"
      EditType.SUB -> "$left [${mid} -> ${newMid}] $right"
    } + " // " + when (type) {
      EditType.INS -> "$left [${newMid}] $right"
      EditType.DEL -> "$left ~${mid}~ $right"
      EditType.SUB -> "$left [${mid} -> ${newMid}] $right"
    } + " ))"
  }
}

data class CEAProb(val cea: ContextEdit, val idx: Int, val frequency: Int) {
  override fun equals(other: Any?): Boolean = when (other) {
    is CEAProb -> cea == other.cea && idx == other.idx
    else -> false
  }
  override fun hashCode(): Int = 31 * cea.hashCode() + idx
  override fun toString(): String = "[[ $cea, $idx, $frequency ]]"
}

data class Context(val left: String, val mid: String, val right: String) {
  override fun equals(other: Any?) = when (other) {
    is Context -> left == other.left && mid == other.mid && right == other.right
    else -> false
  }

  override fun hashCode(): Int {
    var result = left.hashCode()
    result = 31 * result + mid.hashCode()
    result = 31 * result + right.hashCode()
    return result
  }
}

data class CEADist(val allProbs: Map<ContextEdit, Int>) {
  val P_delSub = allProbs.filter { it.key.type != EditType.INS }
  val P_insert = allProbs.filter { it.key.type == EditType.INS }
  val P_delSubOnCtx = P_delSub.keys.groupBy { it.context }
  val P_insertOnCtx = P_insert.keys.groupBy { it.context }
  val subLeft: Map<String, Set<String>> = allProbs.keys.filter { it.type == EditType.SUB }
    .groupBy { it.context.left }.mapValues { it.value.map { it.newMid }.toSet() }
  val insLeft: Map<String, Set<String>> = allProbs.keys.filter { it.type == EditType.INS }
    .groupBy { it.context.left }.mapValues { it.value.map { it.newMid }.toSet() }
  val topThreshold = 30
  val topIns = allProbs.entries
    .filter { it.key.type == EditType.INS }.map { it.key.newMid to it.value }
    .groupBy { it.first }.mapValues { it.value.sumOf { it.second } }
    .entries.sortedBy { -it.value }.take(topThreshold).map { it.key }.toSet()
  val topSub = allProbs.entries
    .filter { it.key.type == EditType.SUB }.map { it.key.newMid to it.value }
    .groupBy { it.first }.mapValues { it.value.sumOf { it.second } }
    .entries.sortedBy { -it.value }.take(topThreshold).map { it.key }.toSet()
//  val insLeftRight: Map<Pair<String, String>, Set<String>> = allProbs.entries
//    .filter { it.key.type == EditType.INS }.filter { 10 < it.value }.map { it.key }
//    .groupBy { it.context.left to it.context.right }.mapValues { it.value.map { it.newMid }.toSet() }
}

fun CFG.contextualRepair(broken: List<String>): Sequence<List<String>> {
  val initREAs: List<CEAProb> = contextCSV.relevantEditActions(broken)
  // Bonuses for previously sampled edits that produced a valid repair
  val bonusProbs = mutableMapOf<ContextEdit, Int>()

//    println("Total relevant edit actions: ${initREAs.size}\n${initREAs.take(5).joinToString("\n")}\n...")
  val samplerTimeout = 10000L
  var (total, uniqueValid) = 0 to 0

  return generateSequence { broken }.map {
    try { it.sampleEditTrajectoryV0(contextCSV, initREAs,
      bonusProbs ) }
    catch (e: Exception) {
      println(broken.joinToString(" ")); e.printStackTrace(); listOf<String>() to listOf()
    }
  }.mapNotNull { (finalSeq, edits ) ->
    if (finalSeq in language) {
      edits.forEach { bonusProbs[it.cea] = (bonusProbs[it.cea] ?: 0) + 1 }

      uniqueValid++
      finalSeq
    }
    else null
  }.distinct()
}

fun List<String>.sampleEditTrajectoryV0(
  ceaDist: CEADist,
  initREAs: List<CEAProb>,
  // Bonuses for previously sampled edits that produced a valid repair
  bonusProbs: Map<ContextEdit, Int>? = null,
  lengthCDF: List<Double> = listOf(0.5, 0.8, 1.0)
): Pair<List<String>, List<CEAProb>> {
  // First sample the length of the edit trajectory from the length distribution
  val rand = Random.nextDouble()
  val length = lengthCDF.indexOfFirst { rand < it } + 1

  if (initREAs.isEmpty()) return this to listOf()
  val ceaProbs = mutableListOf<CEAProb>()
  // Now sample an edit trajectory of that length from the edit distribution
  var listPrime =
    initREAs.normalizeAndSampleV0(bonusProbs)
      .also { ceaProbs.add(it) }
      .let { applyEditAction(it.cea, it.idx + 1) }

  for (i in 1..length) {
    val relevantEditActions = ceaDist.relevantEditActions(listPrime)
    if (relevantEditActions.isEmpty()) {
//      println("$i-th iteration, no relevant edit actions for: ${listPrime.joinToString(" ") { it.toPyRuleName() }}")
      return listPrime to ceaProbs
    }
    val sampledEdit = relevantEditActions.normalizeAndSampleV0(bonusProbs)
      .also { ceaProbs.add(it) }
    listPrime = listPrime.applyEditAction(sampledEdit.cea, sampledEdit.idx + 1)
  }
  return listPrime to ceaProbs
}

// Faster than the above
fun List<CEAProb>.normalizeAndSampleV0(bonusProbs: Map<ContextEdit, Int>?): CEAProb {
  val cdf: List<Int> = (if (bonusProbs == null) map { it.frequency }
  else map { it.frequency + bonusProbs.getOrElse(it.cea) { 0 } * 100 })
    .let { freqs ->
      val cdf = mutableListOf<Int>()
      var sum = 0
      for (i in freqs.indices) {
        sum += freqs[i]
        cdf.add(sum)
      }
      cdf
    }
  val sample: Int = Random.nextInt(cdf.last())
  return this[cdf.binarySearch(sample).let { if (it < 0) -it - 1 else it }.coerceIn(indices)]
}

fun CEADist.relevantEditActions(snippet: List<String>): List<CEAProb> {
  val relevantEditActions = mutableListOf<CEAProb>()
  for (i in 0 until snippet.size - 2) {
    val ctx = Context(snippet[i], snippet[i + 1], snippet[i + 2])
    P_insertOnCtx[Context(ctx.left, "", ctx.mid)]?.forEach {
      relevantEditActions.add(CEAProb(it, i, P_insert[it]!!))
    }
    if (i == snippet.size - 3)
      P_insertOnCtx[Context(ctx.mid, "", ctx.right)]?.forEach {
        relevantEditActions.add(CEAProb(it, i, P_insert[it]!!))
      }
    P_delSubOnCtx[ctx]?.forEach {
      relevantEditActions.add(CEAProb(it, i, P_delSub[it]!!))
    }
  }
  return relevantEditActions
}

fun List<String>.applyEditAction(cea: ContextEdit, idx: Int): List<String> =
  when (cea.type) {                                                       // 6409ms, 20%
    EditType.INS -> subList(0, idx) + cea.newMid + subList(idx + 1, size) // 17937ms, 55%
    EditType.DEL -> subList(0, idx) + subList(idx + 1, size)              // 2607ms, 8%
    EditType.SUB -> subList(0, idx) + cea.newMid + subList(idx + 1, size) // 5552ms, 17%
  }//.also { println("Start:$this\n${cea.type}/${cea.context}/${cea.newMid}/${idx}\nAfter:$it") }

