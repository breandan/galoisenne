package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.levenshtein
import kotlin.test.*
import kotlin.time.*

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.BarHillelTest"
*/
class BarHillelTest {
/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.BarHillelTest.testManualBarHillel"
*/
@Test
  fun testManualBarHillel() {
    // Generated from https://github.com/breandan/bar-hillel/blob/7527d2ad1a007fb4667b85cec295a43c56a237db/rayuela/test/cfg/test_epsilon_Bar_Hillel.py
    val bhcfg = """
        START -> [1,START,4]
        START -> [3,START,4]
        [1,+,3] -> +
        [1,+,3] -> *
        [1,L,1] -> [1,O,1] [1,N,1]
        [1,L,1] -> [1,O,3] [3,N,1]
        [1,L,1] -> [1,O,4] [4,N,1]
        [1,L,3] -> [1,O,1] [1,N,3]
        [1,L,3] -> [1,O,3] [3,N,3]
        [1,L,3] -> [1,O,4] [4,N,3]
        [1,L,4] -> [1,O,1] [1,N,4]
        [1,L,4] -> [1,O,3] [3,N,4]
        [1,L,4] -> [1,O,4] [4,N,4]
        [1,N,1] -> [1,a,1]
        [1,N,1] -> [1,b,1]
        [1,N,1] -> [1,N,1] [1,N,1]
        [1,N,1] -> [1,N,3] [3,N,1]
        [1,N,1] -> [1,N,4] [4,N,1]
        [1,N,3] -> [1,a,3]
        [1,N,3] -> [1,b,3]
        [1,N,3] -> [1,N,1] [1,N,3]
        [1,N,3] -> [1,N,3] [3,N,3]
        [1,N,3] -> [1,N,4] [4,N,3]
        [1,N,4] -> [1,a,4]
        [1,N,4] -> [1,b,4]
        [1,N,4] -> [1,N,1] [1,N,4]
        [1,N,4] -> [1,N,3] [3,N,4]
        [1,N,4] -> [1,N,4] [4,N,4]
        [1,O,1] -> [1,x,1]
        [1,O,1] -> [1,+,1]
        [1,O,3] -> [1,x,3]
        [1,O,3] -> [1,+,3]
        [1,O,4] -> [1,x,4]
        [1,O,4] -> [1,+,4]
        [1,START,1] -> [1,N,1] [1,L,1]
        [1,START,1] -> [1,N,3] [3,L,1]
        [1,START,1] -> [1,N,4] [4,L,1]
        [1,START,3] -> [1,N,1] [1,L,3]
        [1,START,3] -> [1,N,3] [3,L,3]
        [1,START,3] -> [1,N,4] [4,L,3]
        [1,START,4] -> [1,N,1] [1,L,4]
        [1,START,4] -> [1,N,3] [3,L,4]
        [1,START,4] -> [1,N,4] [4,L,4]
        [1,a,1] -> a
        [3,L,1] -> [3,O,1] [1,N,1]
        [3,L,1] -> [3,O,3] [3,N,1]
        [3,L,1] -> [3,O,4] [4,N,1]
        [3,L,3] -> [3,O,1] [1,N,3]
        [3,L,3] -> [3,O,3] [3,N,3]
        [3,L,3] -> [3,O,4] [4,N,3]
        [3,L,4] -> [3,O,1] [1,N,4]
        [3,L,4] -> [3,O,3] [3,N,4]
        [3,L,4] -> [3,O,4] [4,N,4]
        [3,N,1] -> [3,a,1]
        [3,N,1] -> [3,b,1]
        [3,N,1] -> [3,N,1] [1,N,1]
        [3,N,1] -> [3,N,3] [3,N,1]
        [3,N,1] -> [3,N,4] [4,N,1]
        [3,N,3] -> [3,a,3]
        [3,N,3] -> [3,b,3]
        [3,N,3] -> [3,N,1] [1,N,3]
        [3,N,3] -> [3,N,3] [3,N,3]
        [3,N,3] -> [3,N,4] [4,N,3]
        [3,N,4] -> [3,a,4]
        [3,N,4] -> [3,b,4]
        [3,N,4] -> [3,N,1] [1,N,4]
        [3,N,4] -> [3,N,3] [3,N,4]
        [3,N,4] -> [3,N,4] [4,N,4]
        [3,O,1] -> [3,x,1]
        [3,O,1] -> [3,+,1]
        [3,O,3] -> [3,x,3]
        [3,O,3] -> [3,+,3]
        [3,O,4] -> [3,x,4]
        [3,O,4] -> [3,+,4]
        [3,START,1] -> [3,N,1] [1,L,1]
        [3,START,1] -> [3,N,3] [3,L,1]
        [3,START,1] -> [3,N,4] [4,L,1]
        [3,START,3] -> [3,N,1] [1,L,3]
        [3,START,3] -> [3,N,3] [3,L,3]
        [3,START,3] -> [3,N,4] [4,L,3]
        [3,START,4] -> [3,N,1] [1,L,4]
        [3,START,4] -> [3,N,3] [3,L,4]
        [3,START,4] -> [3,N,4] [4,L,4]
        [3,b,4] -> b
        [4,+,1] -> +
        [4,+,1] -> *
        [4,L,1] -> [4,O,1] [1,N,1]
        [4,L,1] -> [4,O,3] [3,N,1]
        [4,L,1] -> [4,O,4] [4,N,1]
        [4,L,3] -> [4,O,1] [1,N,3]
        [4,L,3] -> [4,O,3] [3,N,3]
        [4,L,3] -> [4,O,4] [4,N,3]
        [4,L,4] -> [4,O,1] [1,N,4]
        [4,L,4] -> [4,O,3] [3,N,4]
        [4,L,4] -> [4,O,4] [4,N,4]
        [4,N,1] -> [4,a,1]
        [4,N,1] -> [4,b,1]
        [4,N,1] -> [4,N,1] [1,N,1]
        [4,N,1] -> [4,N,3] [3,N,1]
        [4,N,1] -> [4,N,4] [4,N,1]
        [4,N,3] -> [4,a,3]
        [4,N,3] -> [4,b,3]
        [4,N,3] -> [4,N,1] [1,N,3]
        [4,N,3] -> [4,N,3] [3,N,3]
        [4,N,3] -> [4,N,4] [4,N,3]
        [4,N,4] -> [4,a,4]
        [4,N,4] -> [4,b,4]
        [4,N,4] -> [4,N,1] [1,N,4]
        [4,N,4] -> [4,N,3] [3,N,4]
        [4,N,4] -> [4,N,4] [4,N,4]
        [4,O,1] -> [4,x,1]
        [4,O,1] -> [4,+,1]
        [4,O,3] -> [4,x,3]
        [4,O,3] -> [4,+,3]
        [4,O,4] -> [4,x,4]
        [4,O,4] -> [4,+,4]
        [4,START,1] -> [4,N,1] [1,L,1]
        [4,START,1] -> [4,N,3] [3,L,1]
        [4,START,1] -> [4,N,4] [4,L,1]
        [4,START,3] -> [4,N,1] [1,L,3]
        [4,START,3] -> [4,N,3] [3,L,3]
        [4,START,3] -> [4,N,4] [4,L,3]
        [4,START,4] -> [4,N,1] [1,L,4]
        [4,START,4] -> [4,N,3] [3,L,4]
        [4,START,4] -> [4,N,4] [4,L,4]
        [4,b,4] -> b
      """.trimIndent().parseCFG().noNonterminalStubs

    println(bhcfg.pretty)

    // bhcfg is the intersection of
    val cfg = """
        START -> N L
        N -> N N | a | b
        O -> *
        O -> +
        L -> O N
      """.parseCFG().noNonterminalStubs

    val fsa = """
        INIT: 1, 3 FINAL: 4
        1 -[a]-> 1
        1 -[+]-> 3
        3 -[b]-> 4
        4 -[+]-> 1
        4 -[b]-> 4
      """.trimIndent()

    val fsaCfg = """
        START -> START b | 3 b
        3 -> 1 +
        3 -> 1 *
        1 -> a | 1 a | START + | START *
      """.parseCFG().noEpsilonOrNonterminalStubs

    println("Grammar size: ${bhcfg.size}")
    println("Solutions:")

    val template = List(60) { "_" }.joinToString(" ")
  //    measureTime {
  //      template.tokenizeByWhitespace()
  //        .solve(bhcfg, fillers = bhcfg.terminals)
  //        .map { it.replace("ε", "").tokenizeByWhitespace().joinToString(" ") }
  //        .distinct()
  //        .onEach { println(it) }
  //        .toList().also { println("Found ${it.size} solutions.") }
  //    }.also { println("Brute force solver took: ${it.inWholeMilliseconds}ms") }

    var clock = TimeSource.Monotonic.markNow()

    measureTime {
      bhcfg.sampleSeq(template).onEach {
  //        println("${clock.elapsedNow().inWholeMilliseconds}ms: " + it)
        assertTrue { it in bhcfg.language }
        assertTrue { it in fsaCfg.language }
        assertTrue { it in cfg.language }
      }.take(300).toList().also { println("Found ${it.size} solutions.") }
    }.also { println("Sampling solver took: ${it.inWholeMilliseconds}ms") }

    clock = TimeSource.Monotonic.markNow()
    measureTime {
      bhcfg.solveSeq(template).onEach {
  //        println("${clock.elapsedNow().inWholeMilliseconds}ms: " + it)
        assertTrue { it in bhcfg.language }
        assertTrue { it in fsaCfg.language }
        assertTrue { it in cfg.language }
      }.toList().also { println("Found ${it.size} solutions.") }
    }.also { println("Sequential solver took: ${it.inWholeMilliseconds}ms") }

    clock = TimeSource.Monotonic.markNow()
    measureTime {
      bhcfg.solve(template) { it.weight }.onEach {
  //        println("${clock.elapsedNow().inWholeMilliseconds}ms: " + it)
        assertTrue { it in bhcfg.language }
        assertTrue { it in fsaCfg.language }
        assertTrue { it in cfg.language }
      }.toList().also { println("Found ${it.size} solutions.") }
    }.also { println("Sort solver took: ${it.inWholeMilliseconds}ms") }
  }

  /*
  ./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.BarHillelTest.testAutoBarHillel"
  */
  @Test
  fun testAutoBarHillel() {
    val simpleCFG = """
      START -> E
      O -> + | *
      E -> N O N | E O N
      N -> 1 | 2
    """.parseCFG().noEpsilonOrNonterminalStubs

    val origStr = "1 + 1"
    val levFSA = makeLevFSA(origStr, 2, simpleCFG.terminals)

    val levCFG = levFSA.intersect(simpleCFG)

    fun testLevenshteinAcceptance(s: Σᐩ) {
      assertTrue(levFSA.recognizes(s))
      assertTrue(s in simpleCFG.language)
      assertTrue(s in levCFG.language)
    }

    val neighbor = "1 * 2"
    assertEquals(2, levenshtein(origStr, neighbor))
    testLevenshteinAcceptance(neighbor)

    val foreign = "1 + 1 + 1"
    testLevenshteinAcceptance(foreign)

    val testFail = "2 * 2"
    assertFalse(testFail in levCFG.language)

    val template = List(5) { "_" }.joinToString(" ")
    val solutions = levCFG.solveSeq(template).toList().onEach { println(it) }
    println("Found ${solutions.size} solutions within Levenshtein distance 2 of \"$origStr\"")
  }

  /*
  ./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.BarHillelTest.testBooleanBarHillel"
  */
  @Test
  fun testBooleanBarHillel() {
    val arithCFG = """
      START -> START and START | START or START | ( START ) | true | false | ! START
    """.parseCFG()

    val arithCFGNoEps = arithCFG.noEpsilonOrNonterminalStubs

    val origStr = "true and ! ( true )"
    val levCFG = arithCFGNoEps.intersect(makeLevFSA(origStr, 1, arithCFG.terminals))

    val template = List(8) { "_" }.joinToString(" ")
    val lbhSet = levCFG.solveSeq(template).toSet()//.onEach { println(it) }
      .also { println("Found ${it.size} solutions using Levenshtein/Bar-Hillel") }

    val efset = arithCFG.solveSeq(template).toList()
      .filter { levenshtein(it, origStr) < 2 }.toSet()
//      .onEach { println(it) }
      .also { println("Found ${it.size} solutions using enumerative filtering") }

    assertEquals(lbhSet, efset, "Levenshtein/Bar-Hillel and enumerative filtering should return the same solutions")
  }
}