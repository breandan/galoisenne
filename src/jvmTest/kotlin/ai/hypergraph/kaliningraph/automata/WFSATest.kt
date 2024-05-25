package ai.hypergraph.kaliningraph.automata

import Grammars
import Grammars.shortS2PParikhMap
import ai.hypergraph.kaliningraph.graphs.LabeledGraph
import ai.hypergraph.kaliningraph.parsing.*
import net.jhoogland.jautomata.*
import net.jhoogland.jautomata.Automaton
import net.jhoogland.jautomata.operations.*
import net.jhoogland.jautomata.semirings.RealSemiring
import kotlin.test.*
import kotlin.time.measureTimedValue


class WFSATest {
  /*
  ./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.automata.WFSATest.testWFSA"
  */
  @Test
  fun testWFSA() {
    val a: Automaton<String, Double> =
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

  fun Automaton<String, Double>.toDot(processed: MutableSet<Any> = mutableSetOf()) =
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
  ./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.automata.WFSATest.testPTreeVsWFSA"
  */
  @Test
  fun testPTreeVsWFSA() {
    val toRepair = "NAME : NEWLINE NAME = STRING NEWLINE NAME = NAME . NAME ( STRING ) NEWLINE"
    val radius = 2
    val pt = Grammars.seq2parsePythonCFG.makeLevPTree(toRepair, radius, shortS2PParikhMap)
    println(pt.totalTrees.toString())
    val maxResults = 10_000
    val ptreeRepairs = measureTimedValue {
      pt.sampleStrWithoutReplacement().distinct().take(maxResults).toSet()
    }
    measureTimedValue {
      pt.propagator<Automaton<String, Double>>(
        both = { a, b -> if (a == null) b else if (b == null) a else Concatenation(a, b) },
        either = { a, b -> if (a == null) b else if (b == null) a else Union(a, b) },
        unit = { a ->
          if ("ε" in a.root) null
          else EditableAutomaton<String, Double>(RealSemiring()).apply {
            val s1 = addState(1.0, 0.0)
            val s2 = addState(0.0, 1.0)
            addTransition(s1, s2, a.root, 1.0)
          }
        }
      )
//        ?.also { println("\n" + Operations.determinizeER(it).toDot().alsoCopy() + "\n") }
//        .also { println("Total: ${Automata.transitions(it).size} arcs, ${Automata.states(it).size}") }
        .let { Automata.bestStrings(it, maxResults).map { it.label.joinToString(" ") }.toSet() }
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