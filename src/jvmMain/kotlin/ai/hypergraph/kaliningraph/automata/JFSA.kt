package ai.hypergraph.kaliningraph.automata

import ai.hypergraph.kaliningraph.graphs.LabeledGraph
import ai.hypergraph.kaliningraph.parsing.*
import ai.hypergraph.markovian.mcmc.MarkovChain
import dk.brics.automaton.Automaton.*
import dk.brics.automaton.Transition
import java.util.PriorityQueue
import java.util.concurrent.PriorityBlockingQueue
import kotlin.random.Random
import kotlin.time.*

typealias BState = dk.brics.automaton.State
typealias BAutomaton = dk.brics.automaton.Automaton
typealias JAutomaton<S, K> = net.jhoogland.jautomata.Automaton<S, K>

fun BState.options(dec: Map<Char, Σᐩ>) =
  transitions.flatMap { next -> (next.min..next.max).map { tok -> dec[tok] to next.dest } }.toMap()

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

data class FSATrajectory(val traj: List<Σᐩ?>, val lastState: BState,
                         val score: Double, val id: Int = traj.hashCode()): Comparable<FSATrajectory> {
  val isComplete: Boolean = lastState.isAccept
  val tokens by lazy { traj.reversed().filterNotNull() }
  val lenNormedScore = score / traj.size
  fun append(tok: Σᐩ?, state: BState, score: Double) =
    FSATrajectory(listOf(tok) + traj, state, score, id * 31 + tok.hashCode())
  override fun toString() = tokens.joinToString(" ")
//  override fun equals(other: Any?): Boolean = other is FSATrajectory && lenNormedScore == other.lenNormedScore
  override fun equals(other: Any?): Boolean = other is FSATrajectory && id == other.id
  override fun compareTo(other: FSATrajectory): Int = lenNormedScore.compareTo(other.lenNormedScore)
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
  dec: Map<Char, Σᐩ>, // Maps unicode characters back to strings
  callback: (Σᐩ) -> Unit = {},
  timeout: Duration = Duration.INFINITE,
  beamWidth: Long = 1_000_000L, // Maximum number of trajectories to keep at each step
): List<Σᐩ> {
  val startTime = TimeSource.Monotonic.markNow()
  val fullTrajectories = PriorityBlockingQueue<FSATrajectory>(10000) // Max-heap for full trajectories
  val beam = PriorityQueue<FSATrajectory>() // Beam for partial trajectories

  beam.add(FSATrajectory(List(mc.memory) { null }, initialState, 0.0))

  while (
    fullTrajectories.size < beamWidth &&
    beam.isNotEmpty() &&
    startTime.elapsedNow() < timeout
  ) {
    val nextBeam = beam.parallelStream().flatMap { partTraj ->
      val lastToks = partTraj.traj.take(mc.memory - 1).reversed()
      partTraj.lastState.transitions.flatMap { next ->
        (next.min..next.max).map { tok ->
          val decTok = dec[tok]
          val nextScore = partTraj.score + mc.scoreChunk(lastToks + decTok)
          partTraj.append(decTok, next.dest, nextScore)
        }
      }.flatMap { traj ->
        if (traj.isComplete) {
          fullTrajectories.add(traj)
          callback(traj.toString())
          if (traj.lastState.transitions.isNotEmpty()) listOf(traj) else emptyList()
        } else { listOf(traj) }
      }.stream()
    }.sorted().limit(beamWidth).toList()

    beam.clear()
    beam.addAll(nextBeam)
  }

  val deduped = fullTrajectories.distinct().map { it.toString() }.toList()

  println("Took ${startTime.elapsedNow()} to decode ${deduped.size} trajectories, with ${beam.size} in queue")
  return deduped
}

fun BAutomaton.decodeDFA(
  dec: Map<Char, Σᐩ>, // Maps unicode characters back to strings because BAutomata uses Unicode
  take: Int = 10_000,
) = getFiniteStrings(take).map { it.map { dec[it]!! }.joinToString(" ") }