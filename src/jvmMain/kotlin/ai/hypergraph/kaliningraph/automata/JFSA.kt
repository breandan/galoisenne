package ai.hypergraph.kaliningraph.automata

import NUM_CORES
import ai.hypergraph.kaliningraph.graphs.LabeledGraph
import ai.hypergraph.kaliningraph.parsing.*
import ai.hypergraph.markovian.mcmc.MarkovChain
import dk.brics.automaton.Automaton.*
import dk.brics.automaton.Transition
import java.util.concurrent.*
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

data class FSATrajectory(val traj: List<Σᐩ?>, val lastState: BState, val score: Double) {
  val isComplete: Boolean = lastState.isAccept
  val tokens by lazy { traj.reversed().filterNotNull() }
  override fun toString() = tokens.joinToString(" ")
}

fun BAutomaton.min(): BAutomaton = minimize(this)

fun PTree.toDFA(minimize: Boolean = false) =
  measureTimedValue {
    BAutomaton.setMinimization(MINIMIZE_BRZOZOWSKI)
    var i = 0
    var j = 0
    propagator(
      both = { a, b -> if (a == null) b else if (b == null) a
      // Only periodically minimize the automata during construction
      else if (i++ % 13 == 0) a.concatenate(b).min() else a.concatenate(b) },
      either = { a, b -> if (a == null) b else if (b == null) a
      else if (j++ % 13 == 0) a.union(b).min() else a.union(b) },
      unit = { a ->
        if ("ε" in a.root) null
        else BAutomaton.makeChar(Random(a.root.hashCode()).nextInt().toChar())
      }
    )
  }.also { println("Took ${it.duration} to build FSA") }.value
  ?.also { println("Original automata had ${it
    .let { "${it.numberOfStates} states and ${it.numberOfTransitions} transitions"}}") }
  ?.also {
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
  parallelize: Boolean = false
): List<Σᐩ> {
  val startTime = TimeSource.Monotonic.markNow()
  val load = 100_000
  val fullTrajectories = PriorityBlockingQueue<FSATrajectory>(load, compareBy { it.score / it.traj.size })
  val partTrajectories = Array(if(parallelize) NUM_CORES else 1) {
    PriorityBlockingQueue<FSATrajectory>(load, compareBy { it.score / it.traj.size })
      .apply { add(FSATrajectory(List(mc.memory) { null }, initialState, 0.0)) }
  }

  fun task(id: Int = 0) {
    var i = 0
    while (
      fullTrajectories.size < topK &&
      partTrajectories.any { it.size > 0 } &&
      startTime.elapsedNow() < timeout
    ) {
      if (partTrajectories[id].isEmpty()) continue
// Checks for balanced distribution of work across cores
//    if (i++ % 9999 == 0) println("Trajectories[$id]: ${partTrajectories.map {it.size}}")
      val partTraj = partTrajectories[id].remove()
      val lastToks = partTraj.traj.take(mc.memory - 1).reversed()
      partTraj.lastState.transitions.forEach { next: Transition ->
        (next.min..next.max).forEach { tok ->
          val decTok = dec[tok]
          val nextToks = lastToks + decTok
          val nextScore = partTraj.score + mc.scoreChunk(nextToks)
          val traj = FSATrajectory(listOf(decTok) + partTraj.traj, next.dest, nextScore)
          val bin = if (parallelize) Random(traj.score.hashCode()).nextInt(NUM_CORES) else 0
          if (!traj.isComplete) partTrajectories[bin].add(traj)
          else {
            fullTrajectories.add(traj)
            callback(traj.toString())
            if (traj.lastState.transitions.isNotEmpty())
              partTrajectories[bin].add(traj)
          }
        }
      }
    }
  }

  if (parallelize) (0..<NUM_CORES).toList().parallelStream().forEach { task(it) } else task(0)

  // Deduplicate and resort by final score
  val deduped = fullTrajectories.parallelStream().map { it.toString() to mc.score(it.tokens) }
    .distinct().toList().sortedBy { it.second }.map { it.first }

//  println("Top 10 trajectories:")
//  fullTrajectories.take(10).forEach { println(it.score.toString().take(5) + ": $it") }
  println("Took ${startTime.elapsedNow()} to decode ${deduped.size} trajectories")

  return deduped
}