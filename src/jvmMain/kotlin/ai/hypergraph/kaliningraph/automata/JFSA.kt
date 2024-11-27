package ai.hypergraph.kaliningraph.automata

import ai.hypergraph.kaliningraph.graphs.LabeledGraph
import ai.hypergraph.kaliningraph.parsing.*
import ai.hypergraph.markovian.mcmc.MarkovChain
import dk.brics.automaton.Automaton.*
import dk.brics.automaton.Transition
import java.util.PriorityQueue
import kotlin.random.Random
import kotlin.time.*

typealias BState = dk.brics.automaton.State
typealias BAutomaton = dk.brics.automaton.Automaton
typealias JAutomaton<S, K> = net.jhoogland.jautomata.Automaton<S, K>

fun JAutomaton<String, Double>.toDot(processed: MutableSet<Any> = mutableSetOf()) =
  LabeledGraph {
    val stateQueue = mutableListOf<Any>()
    initialStates().forEach { stateQueue.add(it) }
    while (true) {
      if (stateQueue.isEmpty()) break
      val state = stateQueue.removeAt(0)
      transitionsOut(state).forEach {
        val label = label(it) + "/" + transitionWeight(it).toString().take(4)
        val next = this@toDot.to(it)
        val initws = initialWeight(state)
        val finalws = finalWeight(state)
        val initwn = initialWeight(next)
        val finalwn = finalWeight(next)
        (state.hashCode().toString() + "#$initws/$finalws")[label] = next.hashCode().toString() + "#$initwn/$finalwn"
        if (next !in processed) {
          processed.add(next)
          stateQueue.add(next)
        }
      }
    }
  }.toDot()
    // States are typically unlabeled in FSA diagrams
    .replace("Mrecord\"", "Mrecord\", label=\"\"")
    // Final states are suffixed with /1.0 and drawn as double circles
    .replace("/1.0\" [\"shape\"=\"Mrecord\"", "/1.0\" [\"shape\"=\"doublecircle\"")
    .replace("Mrecord", "circle") // FSA states should be circular
    .replace("null", "ε") // null label = ε-transition

/*
 * Returns a sequence trajectories through a DFA sampled using the Markov chain.
 * The DFA is expected to be deterministic. We use the Markov chain to steer the
 * random walk through the DFA by sampling the best transitions conditioned on the
 * previous n-1 transitions, i.e., q' ~ argmax_{q'} P(q' | q_{t-1}, ..., q_{t-n+1})
 */

data class FSATrajectory(val traj: List<Σᐩ?>, val lastState: BState, val score: Double, val id: Int = traj.hashCode()) {
  val isComplete: Boolean = lastState.isAccept
  val tokens by lazy { traj.reversed().filterNotNull() }
  fun append(tok: Σᐩ?, state: BState, score: Double) =
    FSATrajectory(listOf(tok) + traj, state, score, id * 31 + tok.hashCode())
  override fun toString() = tokens.joinToString(" ")
  override fun equals(other: Any?): Boolean = other is FSATrajectory && id == other.id
}

fun BAutomaton.min(): BAutomaton = minimize(this)

fun PTree.toDFA(
  minimize: Boolean = false,
  unitRule: (String) -> dk.brics.automaton.Automaton = {
    BAutomaton.makeChar(Random(it.hashCode()).nextInt().toChar())
  }
) =
  measureTimedValue {
    BAutomaton.setMinimization(MINIMIZE_BRZOZOWSKI)
    val period = 5
    var i = 0
    var j = 0
    propagator(
      both = { a, b -> if (a == null) b else if (b == null) a
        // Only periodically minimize the automata during construction
        else if (i++ % period == 0) a.concatenate(b).min() else a.concatenate(b) },
      either = { a, b -> if (a == null) b else if (b == null) a
        else if (j++ % period == 0) a.union(b).min() else a.union(b) },
      unit = { a -> if ("ε" in a.root) null else unitRule(a.root) }
    )
  }.also { println("Took ${it.duration} to build FSA") }.value
  ?.also { println("Original automata had ${it
    .let { "${it.numberOfStates} states and ${it.numberOfTransitions} transitions"}}")
    if (minimize) measureTimedValue { BAutomaton.minimize(it) }
      .also { println("Minimization took ${it.duration}") }.value
//            .also { it.toDot().replaceAll(stbl).alsoCopy() }
      .also {
        // Minimal automata had 92 states and 707 transitions
        println("Minimal automata had ${
          it.let { "${it.numberOfStates} states and ${it.numberOfTransitions} transitions" }
        }")
      }
  }

// Steers a random walk using the last n-1 transitions from the Markov Chain
fun BAutomaton.decodeDFA(
  mc: MarkovChain<Σᐩ>,
  // BAutomata uses a Unicode alphabet, and the Markov Chain recognizes a
  // string-based alphabet, so we need a way to translate between the two
  dec: Map<Char, Σᐩ>, // Maps unicode characters back to strings because BAutomata uses Unicode
  callback: (Σᐩ) -> Unit = {},
  topK: Int = 10_000_000, // Total number of top-K results to return
  timeout: Duration = Duration.INFINITE,
): List<Σᐩ> {
  val startTime = TimeSource.Monotonic.markNow()
  val load = 100_000
  val fullTrajectories = PriorityQueue<FSATrajectory>(load, compareBy { it.score / it.traj.size })
  val partTrajectories =
    PriorityQueue<FSATrajectory>(load, compareBy { it.score / it.traj.size })
      .apply { add(FSATrajectory(List(mc.memory) { null }, initialState, 0.0)) }

    while (
      fullTrajectories.size < topK &&
      partTrajectories.size > 0 &&
      startTime.elapsedNow() < timeout
    ) {
      val partTraj = partTrajectories.poll()
      val lastToks = partTraj.traj.take(mc.memory - 1).reversed()
      partTraj.lastState.transitions.flatMap { next ->
        (next.min..next.max).map { tok ->
          val decTok = dec[tok]
          val nextScore = partTraj.score + mc.scoreChunk(lastToks + decTok)

          Triple(next, decTok, nextScore)
        }
      }
//        .sortedBy { (_, _, nextScore) -> -nextScore }.take(100)
        .forEach { (next: Transition, decTok: String?, nextScore: Double) ->
          val traj = partTraj.append(decTok, next.dest, nextScore)
          if (!traj.isComplete) { partTrajectories.add(traj) }
          else {
            fullTrajectories.add(traj.also { callback(it.toString()) })
            if (traj.lastState.transitions.isNotEmpty()) partTrajectories.add(traj)
          }
        }
      }

  val deduped = fullTrajectories.map { it.toString() }.distinct().toList()
//    .map { it.toString() to mc.score(it.tokens) }
//    .distinct().toList().sortedBy { it.second }.map { it.first }

//  println("Top 10 trajectories:")
//  fullTrajectories.take(10).forEach { println(it.score.toString().take(5) + ": $it") }
  println("Took ${startTime.elapsedNow()} to decode ${deduped.size} trajectories, with ${partTrajectories.size} in queue")

  return deduped
}

fun BAutomaton.decodeDFAWithBeamSearch(
  mc: MarkovChain<Σᐩ>,
  dec: Map<Char, Σᐩ>, // Maps unicode characters back to strings
  callback: (Σᐩ) -> Unit = {},
  topK: Int = 10_000_000, // Total number of top-K results to return
  timeout: Duration = Duration.INFINITE,
  beamWidth: Int = 100_000, // Maximum number of trajectories to keep at each step
): List<Σᐩ> {
  val startTime = TimeSource.Monotonic.markNow()
  val fullTrajectories = PriorityQueue<FSATrajectory>(compareBy { it.score / it.traj.size }) // Max-heap for full trajectories
  val beam = PriorityQueue<FSATrajectory>(compareBy { it.score / it.traj.size }) // Beam for partial trajectories

  beam.add(FSATrajectory(List(mc.memory) { null }, initialState, 0.0))

  while (
    fullTrajectories.size < topK &&
    beam.isNotEmpty() &&
    startTime.elapsedNow() < timeout
  ) {
    val nextBeam = PriorityQueue<FSATrajectory>(compareBy { it.score / it.traj.size })

    while (beam.isNotEmpty() && startTime.elapsedNow() < timeout) {
      val partTraj = beam.poll()
      val lastToks = partTraj.traj.take(mc.memory - 1).reversed()

      partTraj.lastState.transitions.flatMap { next ->
        (next.min..next.max).map { tok ->
          val decTok = dec[tok]
          val nextScore = partTraj.score + mc.scoreChunk(lastToks + decTok)
          partTraj.append(decTok, next.dest, nextScore)
        }
      }.forEach { traj ->
        if (traj.isComplete) {
          if (traj.lastState.transitions.isNotEmpty()) nextBeam.add(traj)
          fullTrajectories.add(traj)
          callback(traj.toString())
        } else {
          nextBeam.add(traj)
        }
      }
    }

    beam.clear()
    beam.addAll(nextBeam.take(beamWidth))
  }

  val deduped = fullTrajectories.map { it.toString() }.distinct().toList()

  println("Took ${startTime.elapsedNow()} to decode ${deduped.size} trajectories, with ${beam.size} in queue")
  return deduped
}

fun BAutomaton.decodeDFA(
  dec: Map<Char, Σᐩ>, // Maps unicode characters back to strings because BAutomata uses Unicode
  take: Int = 10_000,
) = getFiniteStrings(take).map { it.map { dec[it]!! }.joinToString(" ") }