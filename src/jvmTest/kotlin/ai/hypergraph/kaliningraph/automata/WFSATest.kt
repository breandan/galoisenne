package ai.hypergraph.kaliningraph.automata

import Grammars
import Grammars.shortS2PParikhMap
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

  /*
  ./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.automata.WFSATest.testLBHRepair"
  */
  @Test
  fun testLBHRepair() {
    val toRepair = "NAME : NEWLINE NAME = STRING NEWLINE NAME = NAME . NAME ( STRING ) NEWLINE"
    val radius = 1
    val pt = Grammars.seq2parsePythonCFG.makeLevPTree(toRepair, radius, shortS2PParikhMap)
    val repairs = pt.sampleStrWithoutReplacement().distinct().take(100).toSet()
    println("Found ${repairs.size} repairs by enumerating PTree")
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
      ).also { println("Total: ${Automata.transitions(it).size} arcs, ${Automata.states(it).size}") }
       .let { Automata.bestStrings(it, 1000).map { it.label.joinToString(" ") }.toSet() }
    }.also {
      println("Found ${it.value.size} repairs by decoding WFSA")
      assertEquals(it.value, repairs)
      it.value.forEach {
        println(levenshteinAlign(toRepair, it).paintANSIColors())
        assertTrue(levenshtein(toRepair, it) <= radius)
        assertTrue(it in Grammars.seq2parsePythonCFG.language)
      }
    }.also { println("Decoding ${it.value.size} repairs took ${it.duration}") }
  }
}