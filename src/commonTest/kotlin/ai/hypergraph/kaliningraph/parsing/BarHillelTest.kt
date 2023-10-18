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
    val cfg = """
        START -> N L
        N -> N N | a | b
        O -> *
        O -> +
        L -> O N
      """.parseCFG().noNonterminalStubs

    val fsa = """
        INIT -> 1 | 3
        DONE -> 4
        1 -<a>-> 1
        1 -<+>-> 3
        1 -<*>-> 3
        3 -<b>-> 4
        4 -<+>-> 1
        4 -<*>-> 1
        4 -<b>-> 4
      """.parseFSA()

    val bhcfg = cfg.intersect(fsa)

    val fsaCfg = """
        START -> START b | 3 b
        3 -> 1 +
        3 -> 1 *
        1 -> a | 1 a | START + | START *
      """.parseCFG().noEpsilonOrNonterminalStubs

    println("Grammar size: ${bhcfg.size}")

    val template = List(60) { "_" }
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
      }.take(300).toList().also { println("Sampling solver found ${it.size} solutions.") }
    }.also { println("Sampling solver took: ${it.inWholeMilliseconds}ms") }

    clock = TimeSource.Monotonic.markNow()
    measureTime {
      bhcfg.solveSeq(template).onEach {
  //        println("${clock.elapsedNow().inWholeMilliseconds}ms: " + it)
        assertTrue { it in bhcfg.language }
        assertTrue { it in fsaCfg.language }
        assertTrue { it in cfg.language }
      }.toList().also { println("Sequential solver found ${it.size} solutions.") }
    }.also { println("Sequential solver took: ${it.inWholeMilliseconds}ms") }

    clock = TimeSource.Monotonic.markNow()
    measureTime {
      bhcfg.solve(template) { it.weight }.onEach {
  //        println("${clock.elapsedNow().inWholeMilliseconds}ms: " + it)
        assertTrue { it in bhcfg.language }
        assertTrue { it in fsaCfg.language }
        assertTrue { it in cfg.language }
      }.toList().also { println("Sort solver found ${it.size} solutions.") }
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

    val levCFG = levFSA.intersectLevFSA(simpleCFG)

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

    val template = List(5) { "_" }
    val solutions = levCFG.enumSeq(template).toList().onEach { println(it) }
    println("Found ${solutions.size} solutions within Levenshtein distance 2 of \"$origStr\"")
  }

  /*
  ./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.BarHillelTest.testBooleanBarHillel"
  */
  @Test
  fun testBooleanBarHillel() {
    val arithCFG = """
      START -> START and START | START or START | ( START ) | T | F | ! START
    """.parseCFG()

    val arithCFGNoEps = arithCFG.noEpsilonOrNonterminalStubs

    val origStr = "T and ! ( F )"
    val levCFG = arithCFGNoEps.intersectLevFSA(makeLevFSA(origStr, 1, arithCFG.terminals))

    val template = List(8) { "_" }
    val lbhSet = levCFG.solveSeq(template).toSet()//.onEach { println(it) }
      .also { println("Found ${it.size} solutions using Levenshtein/Bar-Hillel") }

    val efset = arithCFG.solveSeq(template).toList()
      .filter { levenshtein(it, origStr) < 2 }.toSet()
//      .onEach { println(it) }
      .also { println("Found ${it.size} solutions using enumerative filtering") }

    assertEquals(lbhSet, efset, "Levenshtein/Bar-Hillel and enumerative filtering should return the same solutions")
  }

  /*
  ./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.BarHillelTest.testDyckBarHillel"
  */
  @Test
  fun testDyckBarHillel() {
    val arithCFG = """
      START -> ( START ) | ( ) | START START | [ ] | [ START ] | { } | { START }
    """.parseCFG()

    val arithCFGNoEps = arithCFG.noEpsilonOrNonterminalStubs

    val origStr = "( ( ) ) [ { }"
    val levCFG = arithCFGNoEps.intersectLevFSA(makeLevFSA(origStr, 2, arithCFG.terminals))

    val template = List(9) { "_" }
    val lbhSet = levCFG.solveSeq(template).toSet()//.onEach { println(it) }
      .also { println("Found ${it.size} solutions using Levenshtein/Bar-Hillel") }

    val totalParticipatingNonterminals =
      lbhSet.map { levCFG.parseTable(it).data.map { it.map { it.root } } }.flatten().flatten().toSet()

    println("Participation ratio: " + totalParticipatingNonterminals.size + "/" + levCFG.nonterminals.size)
    println("Active nonterminals: $totalParticipatingNonterminals")
    println("Inactive nonterminals: ${levCFG.nonterminals - totalParticipatingNonterminals}")

    val efset = arithCFG.solveSeq(template).toList()
      .filter { levenshtein(it, origStr) < 3 }.toSet()
//      .onEach { println(it) }
      .also { println("Found ${it.size} solutions using enumerative filtering") }

    assertEquals(lbhSet, efset, "Levenshtein/Bar-Hillel and enumerative" +
      " filtering should return the same solutions, but disjoint union was: " +
      "${(lbhSet + efset) - (lbhSet intersect efset)}")
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.BarHillelTest.testPythonBarHillel"
*/
  @Test
  fun testPythonBarHillel() {
    val gram = Grammars.seq2parsePythonCFG.noEpsilonOrNonterminalStubs
    val toRepair = "NAME = ( NAME .".tokenizeByWhitespace()
    val levBall = makeLevFSA(toRepair, 3, gram.terminals)
    val intGram = gram.intersectLevFSA(levBall)
//      .also { println("LEV ∩ CFG grammar:\n${it.pretty}") }
    val clock = TimeSource.Monotonic.markNow()

    val template = List(toRepair.size + 2) { "_" }
    val lbhSet = intGram.enumSeq(template)
      .onEach {
        println(it)
        assertTrue(it in gram.language)
        assertTrue(levBall.recognizes(it))
      }.toList()

    println("Found ${lbhSet.size} solutions using Levenshtein/Bar-Hillel")
    println("Enumerative solver took ${clock.elapsedNow().inWholeMilliseconds}ms")

//    val totalParticipatingNonterminals =
//      lbhSet.map { intGram.parseTable(it).data.map { it.map { it.root } } }.flatten().flatten().toSet()
//
//    println("Participation ratio: " + totalParticipatingNonterminals.size + "/" + intGram.nonterminals.size)
//    println(intGram.depGraph.toDot())
  }
}