package ai.hypergraph.kaliningraph.automata

import kotlin.math.ln

fun GRE.enumerateWithBeamSearch(
  ngrams: Map<List<String>, Double>,
  tmLst: List<String>,
  beamWidth: Int = 100,
  prefix: List<String> = listOf("BOS", "NEWLINE")
): Sequence<String> = sequence {
  val beam = mutableListOf<GRETrajectory>()
  beam.add(GRETrajectory(traj = prefix, lastState = this@enumerateWithBeamSearch, score = 0.0))

  while (beam.isNotEmpty()) {
    val nextBeam = mutableListOf<GRETrajectory>()

    for (partTraj in beam)
      expandOneStep(partTraj, ngrams, tmLst).forEach { traj ->
//        println(traj.traj.joinToString(" "))
        if (traj.isComplete) yield(traj.traj.joinToString(" "))
        if (traj.lastState.admits.toList().isNotEmpty()) yieldAll(enumerateWithBeamSearch(ngrams, tmLst, beamWidth, prefix))
      }

    nextBeam.sort()
    beam.clear()
    beam.addAll(nextBeam.take(beamWidth))
  }
}

fun expandOneStep(
  partTraj: GRETrajectory,
  ngrams: Map<List<String>, Double>,
  tmLst: List<String>
): List<GRETrajectory> {
  val (traj, lastState) = partTraj
  val k = ngrams.keys.first().size
  val pfxTokens = traj.takeLast(k - 1)

  return when (lastState) {
    is GRE.EPS -> emptyList()
    is GRE.SET ->
      lastState.s.toList().map { ix ->
        val tokenStr = tmLst[ix]
        val tkScore = -ln(ngrams[pfxTokens + tokenStr] ?: 0.0)
        partTraj.append(tokenStr, GRE.EPS(), tkScore)
      }
    is GRE.CUP -> {
      val expansions = mutableListOf<GRETrajectory>()
      for (tk in lastState.admits.toList()) {
        val tokenStr = tmLst[tk]
        val tkScore = -ln(ngrams[pfxTokens + tokenStr] ?: 0.0)
        for (gre in lastState.args.filter { it.admits[tk] })
          expansions += partTraj.append(tokenStr, gre, tkScore)
      }
      expansions
    }
    is GRE.CAT -> {
      val expansions = mutableListOf<GRETrajectory>()
      if (!lastState.l.isNullable())
        expandOneStep(partTraj.copy(lastState = lastState.l), ngrams, tmLst)
          .forEach { e -> expansions += e.copy(lastState = GRE.CAT(e.lastState, lastState.r)) }
      else {
        val leftExps = expandOneStep(partTraj.copy(lastState = lastState.l), ngrams, tmLst)
        leftExps.forEach { e -> expansions += e.copy(lastState = GRE.CAT(e.lastState, lastState.r)) }
        expansions += expandOneStep(partTraj.copy(lastState = lastState.r), ngrams, tmLst)
      }
      expansions
    }
  }
}

data class GRETrajectory(
  val traj: List<String>,
  val lastState: GRE,
  val score: Double,
  val id: Int = traj.hashCode(),
): Comparable<GRETrajectory> {
  val isComplete = lastState.isNullable()
  val lenNormedScore = score / traj.size.coerceAtLeast(1)

  override fun compareTo(other: GRETrajectory): Int =
    lenNormedScore.compareTo(other.lenNormedScore)

  fun append(tok: String, state: GRE, nextScore: Double): GRETrajectory =
    GRETrajectory(traj + listOf(tok), state, score + nextScore)

  override fun toString(): String = traj.joinToString(" ")
}