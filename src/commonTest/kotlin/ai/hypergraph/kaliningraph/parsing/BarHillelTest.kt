package ai.hypergraph.kaliningraph.parsing

import Grammars
import Grammars.shortS2PParikhMap
import ai.hypergraph.kaliningraph.*
import ai.hypergraph.kaliningraph.automata.*
import ai.hypergraph.kaliningraph.repair.vanillaS2PCFG
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
    val levDist = 2
    val levFSA = makeLevFSA(origStr, levDist)

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

    val template = List(origStr.tokenizeByWhitespace().size + levDist) { "_" }
    val solutions = levCFG.enumSeq(template).toList().onEach {
      val actDist = levenshtein(origStr, it)
      assertTrue(actDist <= levDist)
//      val levAlgn = levenshteinAlign(origStr, it).paintANSIColors()
//      println(levAlgn)
    }
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
    val levCFG = arithCFGNoEps.intersectLevFSA(makeLevFSA(origStr, 1))

    val lbhSet = levCFG.toPTree().sampleStrWithoutReplacement().toSet()
      .onEach { println(levenshteinAlign(it, origStr).paintANSIColors()) }
      .also { println("Found ${it.size} solutions using Levenshtein/Bar-Hillel") }

    val efset = arithCFG.solveSeq(List(8) { "_" }).toList()
      .filter { levenshtein(it, origStr) < 2 }.toSet()
      .onEach { println(levenshteinAlign(it, origStr).paintANSIColors()) }
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

    val levDist = 2

    val origStr = "( ( ) ) [ { }"
    val levCFG = arithCFGNoEps.intersectLevFSA(makeLevFSA(origStr, levDist))

    val lbhSet = levCFG.toPTree().sampleStrWithoutReplacement().toSet()//.onEach { println(it) }
      .onEach {
        assertTrue(levenshtein(it, origStr) <= levDist,
          "Lev dist ($levDist) exceeded: ${levenshteinAlign(it, origStr).paintANSIColors()}")
      }.also { println("Found ${it.size} solutions using Levenshtein/Bar-Hillel") }

    val totalParticipatingNonterminals =
      lbhSet.map { levCFG.parseTable(it).data.map { it.map { it.root } } }.flatten().flatten().toSet()

    println("Participation ratio: " + totalParticipatingNonterminals.size + "/" + levCFG.nonterminals.size)
    println("Active nonterminals: $totalParticipatingNonterminals")
    println("Inactive nonterminals: ${levCFG.nonterminals - totalParticipatingNonterminals}")

    val efset = arithCFG.solveSeq(List(9) { "_" }).toList()
      .filter { levenshtein(it, origStr) <= levDist }.toSet()
//      .onEach { println(it) }
      .also { println("Found ${it.size} solutions using enumerative filtering") }

    assertEquals(lbhSet, efset, "Levenshtein/Bar-Hillel and enumerative" +
      " filtering should return the same solutions, but disjoint union was: " +
      "${(lbhSet + efset) - (lbhSet intersect efset)}")
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.BarHillelTest.testIfThenBarHillel"
*/
  @Test
  fun testIfThenBarHillel() {
    val gram = Grammars.ifThen
    val clock = TimeSource.Monotonic.markNow()
    val origStr = "if ( true or false then true else 1"
    val tokens = origStr.tokenizeByWhitespace()
    val maxLevDist = 3
    val levBall = makeLevFSA(origStr, maxLevDist)
    val intGram = gram.intersectLevFSA(levBall)
    val lbhSet = intGram.toPTree().sampleStrWithoutReplacement()
      .onEachIndexed { i, it ->
        if (i < 100) {
          val levAlign = levenshteinAlign(origStr, it).paintANSIColors()
          println(levAlign)
        }

        val actDist= levenshtein(origStr, it)
        assertTrue(it in gram.language)
        assertTrue(levBall.recognizes(it))
        assertTrue(actDist <= maxLevDist)
    }.toSet()
      // Found 221 minimal solutions using Levenshtein/Bar-Hillel in 23.28s
      .also { println("Found ${it.size} minimal solutions using " +
        "Levenshtein/Bar-Hillel in ${clock.elapsedNow()}") }

    val prbSet = Grammars.ifThen.fasterRepairSeq(tokens, 1, 3)
      .takeWhile { clock.elapsedNow().inWholeSeconds < 90 }.distinct().mapNotNull {
        val levDistance = levenshtein(origStr, it)
        if (levDistance < maxLevDist) {
          println("Found ($levDistance): " + levenshteinAlign(origStr, it).paintANSIColors())
          assertTrue(it in Grammars.ifThen.language)
          assertTrue(levBall.recognizes(it))
          assertTrue(it in intGram.language)
          assertTrue(it in lbhSet)
          it
        } else null
      }.toList()
      .also { println("Found ${it.size} minimal solutions using " +
          "Probabilistic repair in ${clock.elapsedNow()}") }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.BarHillelTest.testPythonBarHillel"
*/
  @Test
  fun testPythonBarHillel() {
    val gram = vanillaS2PCFG.noEpsilonOrNonterminalStubs
    val origStr = "NAME = ( NAME . NAME ( NAME NEWLINE"
    val toRepair = origStr.tokenizeByWhitespace()
    val maxLevDist = 2
    val levBall = makeLevFSA(toRepair, maxLevDist)
    println("Total transitions in FSA: ${levBall.Q.size}")
//  throw Exception("")
//  println(levBall.toDot())
//  throw Exception("")
    val intGram = gram.intersectLevFSA(levBall)

    val clock = TimeSource.Monotonic.markNow()

    val lbhSet = intGram.toPTree().sampleStrWithoutReplacement()
      .onEachIndexed { i, it ->
        if (i < 100) {
          val levAlign = levenshteinAlign(origStr, it).paintANSIColors()
          println(levAlign)
          val pf = intGram.enumTrees(it.tokenizeByWhitespace()).toList()
          println("Found " + pf.size + " parse trees")
          println(pf.first().prettyPrint())
          println("\n\n")
        }

        assertTrue(levenshtein(origStr, it) <= maxLevDist)
        assertTrue(it in gram.language)
        assertTrue(levBall.recognizes(it))
      }.toSet()
    // Found 6182 minimal solutions using Levenshtein/Bar-Hillel in 9m 12.755585250s
    .also { println("Found ${it.size} minimal solutions using " +
            "Levenshtein/Bar-Hillel in ${clock.elapsedNow()}") }

  //  Found 6987 minimal solutions using Levenshtein/Bar-Hillel
  //  Enumerative solver took 360184ms

    val s2pg = vanillaS2PCFG
    val prbSet = s2pg.fasterRepairSeq(toRepair, 1, 3)
      .takeWhile { clock.elapsedNow().inWholeSeconds < 90 }.distinct()
      .mapIndexedNotNull { i, it ->
        val levDistance = levenshtein(origStr, it)
        if (i < 100) println("Found ($levDistance): " + levenshteinAlign(origStr, it).paintANSIColors())
        if (levDistance < maxLevDist) {
          println("Checking: $it")
          assertTrue(it in s2pg.language)
          assertTrue(levBall.recognizes(it))
          assertTrue(it in intGram.language)
          assertTrue(it in lbhSet)
          it
        } else null
      }.toList()
      // Found 3912 minimal solutions using Probabilistic repair in 11m 51.535605250s
      .also { println("Found ${it.size} minimal solutions using " +
          "Probabilistic repair in ${clock.elapsedNow()}") }

//    val totalParticipatingNonterminals =
//      lbhSet.map { intGram.parseTable(it).data.map { it.map { it.root } } }.flatten().flatten().toSet()
//
//    println("Participation ratio: " + totalParticipatingNonterminals.size + "/" + intGram.nonterminals.size)
//    println(intGram.depGraph.toDot())
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.BarHillelTest.semiRealisticTest"
*/
  @Test
  fun semiRealisticTest() {
    val gram = vanillaS2PCFG.noEpsilonOrNonterminalStubs
    val origStr = "NAME = NAME . NAME ( [ NUMBER , NUMBER , NUMBER ] NEWLINE"
    val toRepair = origStr.tokenizeByWhitespace()
    val levDist = 2
    val levBall = makeLevFSA(toRepair, levDist)
    println(levBall.states.size)
//  println(levBall.toDot())
//  throw Exception("")
    val intGram = gram.intersectLevFSA(levBall)

    val clock = TimeSource.Monotonic.markNow()

    val lbhSet = intGram.toPTree().sampleStrWithoutReplacement()
      .onEachIndexed { i, it ->
        if (i < 100) println(levenshteinAlign(origStr, it).paintANSIColors())

        assertTrue(levenshtein(origStr, it) <= levDist)
        assertTrue(it in gram.language)
        assertTrue(levBall.recognizes(it))
      }.toSet()
      .also { println("Found ${it.size} minimal solutions using " +
          "Levenshtein/Bar-Hillel in ${clock.elapsedNow()}") }

    val s2pg = vanillaS2PCFG
    val prbSet = s2pg.fasterRepairSeq(toRepair, 1, 2)
      .takeWhile { clock.elapsedNow().inWholeSeconds < 90 }.distinct()
      .mapIndexedNotNull { i, it ->
        val levDistance = levenshtein(origStr, it)
        if (levDistance < levDist) {
          println("Found ($levDistance): " + levenshteinAlign(origStr, it).paintANSIColors())
          assertTrue(it in s2pg.language)
          assertTrue(levBall.recognizes(it))
          assertTrue(it in intGram.language)
          assertTrue(it in lbhSet)
          it
        } else null
      }.toList()
      .also { println("Found ${it.size} minimal solutions using " +
          "Probabilistic repair in ${clock.elapsedNow()}") }

//  Found 657 solutions using Levenshtein/Bar-Hillel
//  Enumerative solver took 113329ms

    println("Found ${lbhSet.size} solutions using Levenshtein/Bar-Hillel")
    println("Enumerative solver took ${clock.elapsedNow().inWholeMilliseconds}ms")
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.BarHillelTest.levenshteinBlanketTest"
*/
  @Test
  fun levenshteinBlanketTest() {
    val gram = vanillaS2PCFG.noEpsilonOrNonterminalStubs
    val origStr= "NAME = NAME . NAME ( [ NUMBER , NUMBER , NUMBER ] NEWLINE"
    val toRepair = origStr.tokenizeByWhitespace()
    val levDist = 2
    val levBall = makeLevFSA(toRepair, levDist)
    val clock = TimeSource.Monotonic.markNow()

    val s2pg = vanillaS2PCFG
    s2pg.fasterRepairSeq(toRepair, 1, 2).distinct()
      .mapIndexedNotNull { i, it ->
        val levDistance = levenshtein(origStr, it)
        if (levDistance <= levDist) {
          println("Found ($levDistance): " + levenshteinAlign(origStr, it).paintANSIColors())
          assertTrue(it in s2pg.language)
          assertTrue(levBall.recognizes(it))
          it
        } else null
      }.takeWhile { clock.elapsedNow().inWholeSeconds < 30 }.toList()
      .also { println("Found ${it.size} minimal solutions using " +
        "Probabilistic repair in ${clock.elapsedNow()}") }

    println("Enumerative solver took ${clock.elapsedNow().inWholeMilliseconds}ms")
  }

  /*
  ./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.BarHillelTest.testHammingBallRepair"
  */
  @Test
  fun testHammingBallRepair() {
    val timeout = 30
    val gram = vanillaS2PCFG
    val prompt= "NAME = ( NAME . NAME ( NAME NEWLINE".tokenizeByWhitespace()
    val clock = TimeSource.Monotonic.markNow()
    val lbhSet = gram.repairSeq(prompt).onEach { println(it) }
      .takeWhile { clock.elapsedNow().inWholeSeconds < timeout }.toList()
    println("Found ${lbhSet.size} repairs using Levenshtein/Bar-Hillel in $timeout seconds")
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.BarHillelTest.testAllBlankSampler"
*/
  @Test
  fun testAllBlankSampler() {
    val gram = vanillaS2PCFG
    val n = 10
    gram.startPTree(List(n) { "_" })?.also {
      it.sampleWRGD().map { it.removeEpsilon() }.distinct()
        .take(100).toList().onEach { println(it.removeEpsilon()) }
    }?.also {
      println("Total branches off START: ${it.branches.size}")
      println("Average branching factor: ${it.branchRatio.let { (l, r) -> l / r }}")
      println("Total parse trees off START: ${it.totalTrees}")
      println("Inverse CFL density (Σ^$n/|T($n)|): ~1/${it.inverseDensity}")
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.BarHillelTest.ambiguityTortureTest"
*/
  @Test
  fun ambiguityTortureTest() {
    val gram = Grammars.sss
    val n = 10
    gram.startPTree("b b b b b _ b _ b b b b b b".tokenizeByWhitespace())?.also {
      println("Total branches off START: ${it.branches.size}")
      println("Average branching factor: ${it.branchRatio.let { (l, r) -> l / r }}")
      println("Total parse trees off START: ${it.totalTrees}")
      println("First children: ${it.branches.joinToString("\n") { it.first.root + "," + it.second.root }}")
      println("Inverse CFL density (Σ^$n/|T($n)|): ~1/${it.inverseDensity}")
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.BarHillelTest.testToyArith"
*/
  @Test
  fun testToyArith() {
    val prompt = ") ( (".tokenizeByWhitespace()
    val overwrittenRepairs =
      Grammars.toyArith.barHillelRepair(prompt, 3).toSet()
        .also { println("Found ${it.size} overwritten repairs.") }

    val allTriples = Grammars.toyArith.solveSeq(List(3) { "_" })
      .distinct().toSet().also { println("Found ${it.size} total triples.") }

    val allTriplesMinusOverwritten = overwrittenRepairs - allTriples
    allTriplesMinusOverwritten.forEach { println(it) }
    println("Found ${allTriplesMinusOverwritten.size} non-overwritten triples.")
  }
}