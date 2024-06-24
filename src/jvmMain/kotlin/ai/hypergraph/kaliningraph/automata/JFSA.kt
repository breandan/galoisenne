package ai.hypergraph.kaliningraph.automata

import ai.hypergraph.kaliningraph.graphs.LabeledGraph
import ai.hypergraph.kaliningraph.parsing.*
import ai.hypergraph.markovian.mcmc.MarkovChain
import dk.brics.automaton.Automaton.*
import dk.brics.automaton.Transition
import java.util.*
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

data class FSATrajectory(val toks: List<Σᐩ?>, val lastState: BState, val score: Double) {
  val isComplete: Boolean = lastState.isAccept
  override fun toString() = toks.reversed().filterNotNull().joinToString(" ")
}

fun PTree.decodeDFA(mc: MarkovChain<Σᐩ>, topK: Int = 10_000_000): List<Σᐩ> = propagator(
  both = { a, b -> if (a == null) b else if (b == null) a else a.concatenate(b) },
  either = { a, b -> if (a == null) b else if (b == null) a else a.union(b) },
  unit = { a ->
    if ("ε" in a.root) null
    else BAutomaton.makeChar(Random(a.root.hashCode()).nextInt().toChar())
//            EditableAutomaton<String, Double>(RealSemiring()).apply {
//            val s1 = addState(1.0, 0.0)
//            val s2 = addState(0.0, 1.0)
//            addTransition(s1, s2, a.root, 1.0)
//          }
  }
)
//        ?.also { println("\n" + Operations.determinizeER(it).toDot().alsoCopy() + "\n") }
//        .also { println("Total: ${Automata.transitions(it).size} arcs, ${Automata.states(it).size}") }
//        .let { WAutomata.bestStrings(it, maxResults).map { it.label.joinToString(" ") }.toSet() }
  ?.also { println("Original automata had ${it
    .let { "${it.numberOfStates} states and ${it.numberOfTransitions} transitions"}}") }
  ?.also {
    measureTimedValue { BAutomaton.setMinimization(MINIMIZE_BRZOZOWSKI); BAutomaton.minimize(it) }
      .also { println("Minimization took ${it.duration}") }.value
//            .also { it.toDot().replaceAll(stbl).alsoCopy() }
      .also {
        // Minimal automata had 92 states and 707 transitions
        println("Minimal automata had ${
          it.let { "${it.numberOfStates} states and ${it.numberOfTransitions} transitions" }
        }")
      }
  }
//        ?.getFiniteStrings(-1)?.map { it.map { ctbl[it] }.joinToString(" ") } ?: emptySet()
  ?.steerableRandomWalk(
    mc = mc,
    dec = allTerminals.associateBy { Random(it.hashCode()).nextInt().toChar() },
    topK = topK
  ) ?: emptyList()

// Steers a random walk using the last n-1 transitions from the Markov Chain
fun BAutomaton.steerableRandomWalk(
  mc: MarkovChain<Σᐩ>,
  // BAutomata uses a Unicode alphabet, and the Markov Chain recognizes a
  // string-based alphabet, so we need a way to translate between the two
  dec: Map<Char, String>, // Maps unicode characters back to strings
  topK: Int // Total number of top-K results to return
): List<Σᐩ> {
  val startTime = TimeSource.Monotonic.markNow()
  val fullTrajectories = PriorityQueue<FSATrajectory>(compareBy { it.score / it.toks.size })
  val partTrajectories = PriorityQueue<FSATrajectory>(compareBy { it.score / it.toks.size })
    .apply { add(FSATrajectory(List(mc.memory) { null }, initialState, 0.0)) }
  while (fullTrajectories.size < topK && partTrajectories.isNotEmpty()) {
    val partTraj = partTrajectories.remove()
    val lastToks = partTraj.toks.take(mc.memory - 1).reversed()
    partTraj.lastState.transitions.forEach { next: Transition ->
      (next.min..next.max).forEach { tok ->
        val decTok = dec[tok]
        val nextToks = lastToks + decTok
        val nextScore = partTraj.score + mc.scoreChunk(nextToks)
        val traj = FSATrajectory(listOf(decTok) + partTraj.toks, next.dest, nextScore)
        if (!traj.isComplete) partTrajectories.add(traj)
        else {
          fullTrajectories.add(traj)
          if (traj.lastState.transitions.isNotEmpty())
            partTrajectories.add(traj)
        }
      }
    }
  }

  println("Top 10 trajectories:")
  fullTrajectories.take(10).forEach { println(it.score.toString().take(5) + ": $it") }
  println("Took ${startTime.elapsedNow()} to decode ${fullTrajectories.size} trajectories")

  return fullTrajectories.map { it.toString() }
}

