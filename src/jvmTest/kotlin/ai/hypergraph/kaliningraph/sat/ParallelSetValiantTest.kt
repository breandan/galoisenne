package ai.hypergraph.kaliningraph.sat

import ai.hypergraph.kaliningraph.parsing.*
import ai.hypergraph.kaliningraph.sampling.MDSamplerWithoutReplacement
import org.junit.jupiter.api.Test
import prettyPrint
import java.util.stream.Collectors
import java.util.stream.Stream
import kotlin.streams.*
import kotlin.test.*
import kotlin.time.*

class ParallelSetValiantTest {
  /*
  ./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.ParallelSetValiantTest.testParallelSetValiant"
  */
  @OptIn(ExperimentalTime::class)
  @Test
  fun testParallelSetValiant() {
    println(Runtime.getRuntime().availableProcessors())
    val template = List(8) { "_" }
    val CFG = """S -> ( ) | [ ] | ( S ) | [ S ] | S S""".parseCFG(validate = true)
    val q = measureTimedValue { template.parallelSolve(CFG) }
      .also { println("Parallel generator: ${it.duration}") }.value

    val t: Set<Σᐩ> = measureTimedValue { template.solve(CFG).toSet() }
      .also { println("Serial generator: ${it.duration}") }.value

    assertEquals(t, q)

    val s: Set<Σᐩ> = measureTimedValue {
      template.joinToString(" ").synthesizeIncrementally(CFG).toSet()
    }.also { println("SAT generator: ${it.duration}") }.value

//    assertEquals(s, q)
//    println("q-s=${q - s}")
//    println("s-q=${s - q}")
  }

  // This experiment essentially tries every possible combination of fillers in parallel
  fun List<Σᐩ>.parallelSolve(CFG: CFG, fillers: Set<Σᐩ> = CFG.terminals): Set<String> =
    parallelGenCandidates(CFG, (fillers - CFG.blocked))
      .filter { it.matches(CFG) }
      .collect(Collectors.toSet())
//      .filter { measureTimedValue { it.fastMatch(CFG) }.also { println("Decided ${it.value} in ${it.duration}") }.value }

  fun List<Σᐩ>.parallelGenCandidates(CFG: CFG, fillers: Set<Σᐩ> = CFG.terminals): Stream<String> =
    MDSamplerWithoutReplacement(fillers, count { it == HOLE_MARKER })
      .asStream().parallel()
      .map {
        fold("" to it) { (a, b), c ->
          if (c == HOLE_MARKER) (a + " " + b.first()) to b.drop(1) else ("$a $c") to b
        }.first.replace("ε ", "")
      }
}