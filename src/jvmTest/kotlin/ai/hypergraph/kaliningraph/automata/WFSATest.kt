package ai.hypergraph.kaliningraph.automata

import Grammars
import Grammars.shortS2PParikhMap
import ai.hypergraph.kaliningraph.graphs.LabeledGraph
import ai.hypergraph.kaliningraph.parsing.*
import ai.hypergraph.kaliningraph.visualization.alsoCopy
import ai.hypergraph.markovian.mcmc.MarkovChain
import net.jhoogland.jautomata.*
import net.jhoogland.jautomata.Automaton
import net.jhoogland.jautomata.operations.*
import net.jhoogland.jautomata.semirings.RealSemiring
import kotlin.random.Random
import kotlin.test.*
import kotlin.time.*

typealias BAutomaton = dk.brics.automaton.Automaton
typealias JAutomaton<S, K> = Automaton<S, K>

class WFSATest {
  
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

  fun <S, K> JAutomaton<S, K>.randomWalk(mc: MarkovChain<S>, topK: Int = 1000): Sequence<S> {
    val init = initialStates().first()
    val padding = List(mc.memory - 1) { null }
    val ts = transitionsOut(init).map { (it as BasicTransition<S, K>).label() }.map { it to mc.score(padding + it) }
    return TODO()
  }

  /*
  ./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.automata.WFSATest.testPTreeVsWFSA"
  */
  @Test
  fun testPTreeVsWFSA() {
    val toRepair = "NAME : NEWLINE NAME = STRING NEWLINE NAME = NAME . NAME ( STRING ) NEWLINE"
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
        .let { it?.getFiniteStrings(-1) ?: emptySet() }
    }.also {
//      // Print side by side comparison of repairs
//      repairs.sorted().forEach {
//        val a = it
//        val b = if (it in repairs) it else ""
//        val colorA = levenshteinAlign(toRepair, a).paintANSIColors()
//        val colorB = if (b.isEmpty()) "" else levenshteinAlign(toRepair, b).paintANSIColors()
//        println("$colorA\n$colorB\n")
//      }
      assertEquals(it.value.size, ptreeRepairs.value.size)

      it.value.map { it.map { ctbl[it] }.joinToString(" ") }.forEach {
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