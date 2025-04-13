package ai.hypergraph.kaliningraph.sat

import ai.hypergraph.kaliningraph.parsing.*
import ai.hypergraph.kaliningraph.repair.TIMEOUT_MS
import ai.hypergraph.kaliningraph.sampling.*
import ai.hypergraph.kaliningraph.tokenizeByWhitespace
import org.junit.jupiter.api.Test
import java.util.stream.*
import kotlin.streams.*
import kotlin.test.*
import kotlin.time.*

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.ParallelSetValiantTest"
*/
class ParallelSetValiantTest {
  val CFG = """S -> ( ) | [ ] | ( S ) | [ S ] | S S""".parseCFG()
    .apply { blocked += setOf("<S>", "ε") }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.ParallelSetValiantTest.testParallelSetValiant"
*/
//  @Test
  fun testParallelSetValiant() {
    println(Runtime.getRuntime().availableProcessors())
    val template = List(8) { "_" }

    val t: Set<Σᐩ> = measureTimedValue { template.solve(CFG).map { it.trim() }.toSet() }
      .also { println("Serial SetValiant generated ${it.value.size} models in ${it.duration}") }.value

    val q: Set<Σᐩ> = measureTimedValue { template.parallelSolve(CFG.asCJL).collect(Collectors.toSet()) }
      .also { println("Parallel SetValiant generated ${it.value.size} models in ${it.duration}") }.value

    assertEquals(t, q)

    val s: Set<Σᐩ> = measureTimedValue {
      template.joinToString(" ").synthesizeIncrementally(CFG).toSet()
    }.also { println("Incremental SATValiant generated ${it.value.size} models in ${it.duration}") }.value

    assertEquals(s, q)
  }

  // This experiment essentially tries every possible combination of fillers in parallel
  fun List<Σᐩ>.parallelSolve(
    CJL: CJL,
    fillers: Set<Σᐩ> = CFG.terminals - CFG.blocked
  ) =
    MDSamplerWithoutReplacement(fillers, count { it == HOLE_MARKER }).asStream().parallel()
      .map {
        fold("" to it) { (a, b), c ->
          if (c == HOLE_MARKER) (a + " " + b.first()) to b.drop(1) else ("$a $c") to b
        }.first.replace("ε ", "").trim()
      }
      .filter { it.matches(CJL) }
//      .filter { measureTimedValue { it.fastMatch(CFG) }.also { println("Decided ${it.value} in ${it.duration}") }.value }

  // Tries to parallelize the PRNG using leapfrog method, no demonstrable speedup observed
  // https://surface.syr.edu/cgi/viewcontent.cgi?article=1012&context=npac
  fun List<Σᐩ>.parallelSolve(
    CFG: CFG,
    fillers: Set<Σᐩ> = CFG.terminals - CFG.blocked,
    cores: Int = Runtime.getRuntime().availableProcessors().also { println("Cores: $it") }
  ) =
    (0..<cores).toSet().parallelStream().flatMap { i ->
      MDSamplerWithoutReplacement(fillers, count { it == HOLE_MARKER })
        .filterIndexed { index, _ -> index % cores == i }
        .asStream()
        .map {
          fold("" to it) { (a, b), c ->
            if (c == HOLE_MARKER) (a + " " + b.first()) to b.drop(1) else ("$a $c") to b
          }.first.replace("ε ", "").trim()
        }.filter { it.matches(CFG) }
        .map { println("Thread ($i): $it"); it }
    }.collect(Collectors.toSet())

  val sumCFG = """
      START -> S
      O -> +
      S -> S O S | N | - N | ( S )
      N -> N1 | N2 | N3 | N4 | N5 | N6 | N7 | N8 | N9
      N1 -> 1 
      N2 -> 2 
      N3 -> 3
      N4 -> 4
      N5 -> 5
      N6 -> 6
      N7 -> 7
      N8 -> 8
      N9 -> 9
    """.trimIndent().parseCFG()

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.ParallelSetValiantTest.benchmarkRepairsWithFixedTimeout"
*/
  @Test

  fun benchmarkRepairsWithFixedTimeout() {
    TIMEOUT_MS = 30_000
    val cfg = sumCFG.noNonterminalStubs
    val strWithParseErr = "1 + 2 + 3 + + 4 + 7"
    val tokens = strWithParseErr.tokenizeByWhitespace()

    val levenshteinRadius = 2

    var clock = TimeSource.Monotonic.markNow()
    val timeRemains = { clock.elapsedNow().inWholeMilliseconds < TIMEOUT_MS }

    repairInParallel(strWithParseErr, cfg, levenshteinRadius, synthesizer = { a -> a.solve(this, takeMoreWhile = timeRemains) })
      .takeWhile { timeRemains() }
      .distinctBy { cfg.forestHash(it) }
      .onEachIndexed { i, it -> println("#$i, ${clock.elapsedNow().inWholeMilliseconds}ms, $it") }.toList()
      .also { println("Enumerative repair generated ${it.size} models in ${clock.elapsedNow().inWholeMilliseconds}ms") }

    clock = TimeSource.Monotonic.markNow()
    cfg.levenshteinRepair(levenshteinRadius, tokens, solver = { synthesize(it, takeMoreWhile = timeRemains) })
      .takeWhile { timeRemains() }
      .distinctBy { cfg.forestHash(it) }
      .onEachIndexed { i, it -> println("#$i, ${clock.elapsedNow().inWholeMilliseconds}ms, $it") }.toList()
      .also { println("Levenshtein repair generated ${it.size} models in ${clock.elapsedNow().inWholeMilliseconds}ms") }

    clock = TimeSource.Monotonic.markNow()
    fun genSeq(skip: Int = 1, shift: Int = 0) =
      newRepair(strWithParseErr.tokenizeByWhitespace(), cfg, levenshteinRadius * 2, skip, shift)
        .takeWhile { timeRemains() }
        .distinctBy { cfg.forestHash(it) }
        .onEachIndexed { i, it -> println("#$i, PID=$shift, ${clock.elapsedNow().inWholeMilliseconds}ms, $it") }

    ::genSeq.parallelize().toList()
      .also { println("Bijective repair generated ${it.distinctBy { cfg.forestHash(it) }.size}" +
          " models in ${clock.elapsedNow().inWholeMilliseconds}ms") }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.ParallelSetValiantTest.testParallelPRNG"
*/
  @Test

  fun testParallelPRNG() {
    // How many samples can we draw in n seconds?
    //  Drew 4210472 serial samples in 10000ms
    //  Drew 1405941 parallel samples in 10000ms
    //  Drew 246240 bijective samples in 10000ms

    val timeoutMS = 10_000

    var clock = TimeSource.Monotonic.markNow()
    val timeRemains = { clock.elapsedNow().inWholeMilliseconds < TIMEOUT_MS }

    fun genSeq(skip: Int = 1, shift: Int = 0) =
      MDSamplerWithoutReplacement(('a'..'f').toSet(), 10, skip, shift)

    fun genSeqNK(skip: Int = 1, shift: Int = 0) =
      MDSamplerWithoutReplacementNK(('a'..'f').toSet(), 20, 3, skip, shift)

    genSeq().takeWhile { timeRemains() }.toList()
      .also { println("Drew ${it.size} serial samples in ${timeoutMS}ms") }

    clock = TimeSource.Monotonic.markNow()
    ::genSeq.parallelize().takeWhile { timeRemains() }.toList()
      .also { println("Drew ${it.size} parallel samples in ${timeoutMS}ms") }

    clock = TimeSource.Monotonic.markNow()
    genSeqNK().takeWhile { timeRemains() }.toList()
      .also { println("Drew ${it.size} bijective samples in ${timeoutMS}ms") }
  }
}