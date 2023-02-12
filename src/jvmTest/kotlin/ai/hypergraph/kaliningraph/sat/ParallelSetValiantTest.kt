package ai.hypergraph.kaliningraph.sat

import ai.hypergraph.kaliningraph.hasBalancedBrackets
import ai.hypergraph.kaliningraph.parsing.*
import ai.hypergraph.kaliningraph.sampling.MDSamplerWithoutReplacement
import org.junit.jupiter.api.Test
import prettyPrint
import java.util.stream.Stream
import kotlin.streams.*
import kotlin.test.assertTrue
import kotlin.time.*

class ParallelSetValiantTest {
/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.ParallelSetValiantTest.testParallelSetValiant"
*/
  @OptIn(ExperimentalTime::class)
  @Test
  fun testParallelSetValiant() {
    println(Runtime.getRuntime().availableProcessors())
    measureTime {
    """S -> ( ) | [ ] | ( S ) | [ S ] | S S""".parseCFG(validate = true).toClass().let { CFG: CCFG ->
      println("CFL parsed: ${CFG.prettyPrint()}")
      List(10) { "_" }.psolveFast(CFG)
//        .map { println(it); it }
        .take(10000).toList()
        .shuffled().take(100).forEach { println(it) }
    }}.also { println("Fast matcher: $it") }

  measureTime {
    """S -> ( ) | [ ] | ( S ) | [ S ] | S S""".parseCFG(validate = true).let { CFG: CFG ->
      println("CFL parsed: ${CFG.prettyPrint()}")
      List(10) { "_" }.psolveSlow(CFG)
//        .map { println(it); it }
        .take(10000).toList()
        .shuffled().take(100).forEach { println(it) }
    }}.also { println("Slow matcher: $it") }
    }


  @OptIn(ExperimentalTime::class)
  fun List<Σᐩ>.psolveSlow(CFG: CFG, fillers: Set<Σᐩ> = CFG.terminals): Sequence<Σᐩ> =
    pgenCandidates(CFG, (fillers - CFG.blocked).also { println("Allowed hole fillers: $it") })
//      .filter { measureTimedValue { it.fastMatch(CFG) }.also { println("Match (${it.value}: ${it.duration}") }.value }
      .filter { it.matches(CFG) }
      .asSequence()

  @OptIn(ExperimentalTime::class)
  fun List<Σᐩ>.psolveFast(CFG: CCFG, fillers: Set<Σᐩ> = CFG.terminals): Sequence<Σᐩ> =
    pgenCandidates(CFG, (fillers - CFG.blocked).also { println("Allowed hole fillers: $it") })
//      .filter { measureTimedValue { it.fastMatch(CFG) }.also { println("Match (${it.value}: ${it.duration}") }.value }
      .filter { it.fastMatch(CFG) }
      .asSequence()

  fun List<Σᐩ>.pgenCandidates(CFG: CFG, fillers: Set<Σᐩ> = CFG.terminals): Stream<String> {
    var t = System.nanoTime()
    var q = 0.0
    var count = 0.0
    return MDSamplerWithoutReplacement(fillers, count { it == HOLE_MARKER })
      .map {
        val elapsed = System.nanoTime() - t;
        q += elapsed;
        count++;
//        println("Elapsed: ${elapsed}us // LFSRT: ${q / count}us");
        t = System.nanoTime();
        it }
      .asStream().parallel().map {
        fold("" to it) { (a, b), c ->
          if (c == HOLE_MARKER) (a + " " + b.first()) to b.drop(1) else ("$a $c") to b
        }.first.replace("ε ", "")
      }
  }
}