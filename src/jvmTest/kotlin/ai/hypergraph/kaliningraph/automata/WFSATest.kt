package ai.hypergraph.kaliningraph.automata

import Grammars
import Grammars.shortS2PParikhMap
import ai.hypergraph.kaliningraph.graphs.LabeledGraph
import ai.hypergraph.kaliningraph.parsing.*
import ai.hypergraph.markovian.mcmc.MarkovChain
import dk.brics.automaton.Transition
import net.jhoogland.jautomata.*
import net.jhoogland.jautomata.operations.Concatenation
import net.jhoogland.jautomata.semirings.RealSemiring
import java.io.File
import java.util.PriorityQueue
import kotlin.random.Random
import kotlin.test.*
import kotlin.time.*

typealias BState = dk.brics.automaton.State
typealias BAutomaton = dk.brics.automaton.Automaton
typealias JAutomaton<S, K> = net.jhoogland.jautomata.Automaton<S, K>

class WFSATest {
  val MARKOV_MEMORY = 4
  // Python3 snippets
// https://github.com/michiyasunaga/BIFI?tab=readme-ov-file#about-the-github-python-dataset
  val P_BIFI: MarkovChain<Σᐩ> by lazy {
//  readBIFIContents()
    val csv = File(File("").absolutePath + "/src/jvmTest/resources/ngrams_BIFI_$MARKOV_MEMORY.csv")
    MarkovChain.deserialize(csv.readText())
      .also { println("Loaded ${it.counter.total} BIFI $MARKOV_MEMORY-grams from ${csv.absolutePath}") }
  }

  // Python2 snippets, about ~20x longer on average than BIFI
// https://www.sri.inf.ethz.ch/py150
  val P_PY150: MarkovChain<Σᐩ> by lazy {
    val csv = File(File("").absolutePath + "/src/jvmTest/resources/ngrams_PY150_$MARKOV_MEMORY.csv")
    MarkovChain.deserialize(csv.readText())
      .also { println("Loaded ${it.counter.total} PY150 $MARKOV_MEMORY-grams from ${csv.absolutePath}") }
  }

  val P_BIFI_PY150: MarkovChain<Σᐩ> by lazy { P_BIFI + P_PY150 }

  /*
  ./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.automata.WFSATest.testWFSA"
  */
  @Test
  fun testWFSA() {
    val a: JAutomaton<String, Double> =
      EditableAutomaton<String, Double>(RealSemiring()).apply {
        val s1: Int = addState(1.0, 0.0) // Create initial state (initial weight 1.0, final weight 0.0)
        val s2: Int = addState(0.0, 1.0) // Create final state (initial weight 0.0, final weight 1.0)
        addTransition(s1, s2, "b", 0.4) // Create transition from s1 to s2
        addTransition(s2, s2, "a", 0.6) // Create transition from s2 to s2
      } // probabilistic semiring uses RealSemiring

    val aa = Concatenation(*Array(100) { a })

    for (i in 0 until 10000 step 1000)
      measureTimedValue { Automata.bestStrings(aa, i) }
        .also { println("Took ${it.duration} to decode ${it.value.size} best strings") }
  }

/*
 ./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.automata.WFSATest.testBRICS"
*/
  @Test
  fun testBRICS() {
    val ab = BAutomaton.makeString("a").concatenate(BAutomaton.makeString("b"))
    val aa = BAutomaton.makeString("a").concatenate(BAutomaton.makeString("a"))
    val a = ab.union(aa)
    val ag = List(6) { a }.fold(a) { acc, automaton -> acc.concatenate(automaton) }

    println(ag.getFiniteStrings(-1))
    println()
    println(BAutomaton.minimize(ag.also { it.determinize() }).toDot())
  }

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

  // Steers a random walk using the last n-1 transitions from the Markov Chain
  fun BAutomaton.steerableRandomWalk(
    mc: MarkovChain<Σᐩ>,
    // BAutomata uses a Unicode alphabet, and the Markov Chain recognizes a
    // string-based alphabet, so we need a way to translate between the two
    dec: Map<Char, String>, // Maps unicode characters back to strings
    topK: Int = 10_000_000 // Total number of top-K results to return
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

  /*
  ./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.automata.WFSATest.testPTreeVsWFSA"
  */
  @Test
  fun testPTreeVsWFSA() {
    println("${P_BIFI_PY150.memory}-gram Markov chain is ready.")
//    val toRepair = "from NAME import NAME NEWLINE NAME = NAME ( STRING , STRING ) NEWLINE NAME STRING . NAME ( NAME ) NEWLINE"
//    val groundTr = "NEWLINE from NAME import NAME NEWLINE NAME = NAME ( STRING , STRING ) NEWLINE NAME ( STRING . NAME ( NAME ) ) NEWLINE"
//    val radius = 3
    val toRepair = "NAME : NEWLINE NAME = STRING NEWLINE NAME = NAME . NAME ( STRING ) NEWLINE"
    val groundTr = "+ NAME : True NEWLINE NAME = STRING NEWLINE NAME = NAME . NAME ( STRING ) NEWLINE"
    val radius = 2
    val pt = Grammars.seq2parsePythonCFG.makeLevPTree(toRepair, radius, shortS2PParikhMap)
    fun Char.toUnicodeEscaped() = "\\u${code.toString(16).padStart(4, '0')}"

    val ctbl = Grammars.seq2parsePythonCFG.terminals.associateBy { Random(it.hashCode()).nextInt().toChar() }
    val stbl = Grammars.seq2parsePythonCFG.terminals.associateBy { Random(it.hashCode()).nextInt().toChar().toUnicodeEscaped() }
    fun Σᐩ.replaceAll(tbl: Map<String, String>) = tbl.entries.fold(this) { acc, (k, v) -> acc.replace(k, v) }

    println("Total trees: " + pt.totalTrees.toString())
    val maxResults = 10_000
    val ptreeRepairs = measureTimedValue {
      pt.sampleStrWithoutReplacement().distinct().take(maxResults).toSet()
    }
    measureTimedValue {
      pt.propagator(
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
          measureTimedValue { BAutomaton.minimize(it) }
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
        ?.steerableRandomWalk(P_BIFI_PY150, ctbl) ?: emptyList()
    }.also {
      assertTrue(groundTr in it.value, "Ground truth not found in ${it.value.size} repairs")
      println("Index: ${it.value.indexOf(groundTr)}")
//      // Print side by side comparison of repairs
//      repairs.sorted().forEach {
//        val a = it
//        val b = if (it in repairs) it else ""
//        val colorA = levenshteinAlign(toRepair, a).paintANSIColors()
//        val colorB = if (b.isEmpty()) "" else levenshteinAlign(toRepair, b).paintANSIColors()
//        println("$colorA\n$colorB\n")
//      }
      assertEquals(ptreeRepairs.value.size, it.value.size)

      it.value.forEach {
//        println(levenshteinAlign(toRepair, it).paintANSIColors())
        assertTrue(levenshtein(toRepair, it) <= radius)
        assertTrue(it in Grammars.seq2parsePythonCFG.language)
      }

      println("Found ${it.value.size} unique repairs by decoding WFSA in ${it.duration}")
      println("Found ${ptreeRepairs.value.size} unique repairs by enumerating PTree in ${ptreeRepairs.duration}")
    }.also { println("Decoding ${it.value.size} repairs took ${it.duration}") }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.automata.WFSATest.testBijection"
*/
  @Test
  fun testBijection() {
    val toRepair = "NAME : NEWLINE NAME = STRING NEWLINE NAME = NAME ( STRING ) NEWLINE"
    val radius = 2
    val pt = Grammars.seq2parsePythonCFG.makeLevPTree(toRepair, radius, shortS2PParikhMap)
    println(pt.totalTrees.toString())
    val maxResults = 10_000
    val repairs = pt.sampleStrWithoutReplacement().take(maxResults).toList()
    println("Found ${repairs.size} total repairs by enumerating PTree")
    val distinct = repairs.toSet().size
    println("Found $distinct unique repairs by enumerating PTree")
  }
}