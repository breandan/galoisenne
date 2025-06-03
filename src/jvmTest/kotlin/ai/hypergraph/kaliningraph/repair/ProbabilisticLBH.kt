package ai.hypergraph.kaliningraph.repair

import Grammars
import ai.hypergraph.kaliningraph.automata.repairWithGRE
import ai.hypergraph.kaliningraph.automata.toDFA
import ai.hypergraph.kaliningraph.parsing.*
import ai.hypergraph.kaliningraph.tokenizeByWhitespace
import ai.hypergraph.markovian.stdDev
import org.junit.jupiter.api.Test
import org.kosat.round
import java.util.stream.Collectors
import kotlin.random.Random
import kotlin.reflect.KFunction2
import kotlin.test.*
import kotlin.time.*
import kotlin.time.Duration.Companion.seconds

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.repair.ProbabilisticLBH"
*/
class ProbabilisticLBH {
  init { LangCache.prepopPythonLangCache() }
  val pythonTestCases =
    invalidPythonStatements.lines().zip(validPythonStatements.lines())
      // This ensures the LBH grammar is nonempty, otherwise extragrammatical symbols produce an error
//    .map { it.first.tokenizeByWhitespace().map { if (it in vanillaS2PCFG.noEpsilonOrNonterminalStubs.terminals) it else "." }.joinToString(" ") to it.second }
      .filter { it.first.tokenizeByWhitespace().all { it in vanillaS2PCFG.terminals } }
      .shuffled(Random(seed = 1)).filter { (a, b) ->
        ("$a NEWLINE" !in vanillaS2PCFG.language).also { if (!it) println("Failed invalid") }
            && ("$b NEWLINE" in vanillaS2PCFG.language).also { if (!it) println("Failed valid") }
            && (levenshtein(a, b).also { if (it !in 1..3) println("Failed distance: $it") } in 1..3)
      }.distinct().filter { it.first.tokenizeByWhitespace().size < 23 }
/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.repair.ProbabilisticLBH.testSubgrammarEquivalence"
*/
  @Test
  fun testSubgrammarEquivalence() {
    val terminalImage = setOf<String>() + "NEWLINE" + validPythonStatements.tokenizeByWhitespace().toSet()
    val s2pg = vanillaS2PCFG
    val subgrammar = s2pg.subgrammar(terminalImage)

    (validPythonStatements + invalidPythonStatements).lines()
      .forEach { assertEquals(s2pg.parse(it), subgrammar.parse(it)) }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.repair.ProbabilisticLBH.testExactDist"
*/
  @Test
  fun testExactDist() {
    val gram = vanillaS2PCFG.noEpsilonOrNonterminalStubs

    val anything = """START -> START START | ${gram.terminals.joinToString(" | ") { it }}"""
      .trimIndent().parseCFG().noEpsilonOrNonterminalStubs

    (1..2).forEach { r ->
      gram.sliceSample(6).take(10).forEach { orig ->
        val exactLevFSA = makeExactLevCFL(str = orig.tokenizeByWhitespace(), radius=r)
        anything.jvmIntersectLevFSA(exactLevFSA)
          .toPTree().sampleStrWithoutReplacement().take(10)
          .also { println("\nOrig:  $orig") }.toList()
          .also { assertTrue(it.isNotEmpty()) }.forEach {
            val dist = levenshtein(orig, it)
            println("Lev-$dist: ${levenshteinAlign(orig, it).paintANSIColors()}")
          }.also { println() }
      }
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.repair.ProbabilisticLBH.testSubgrammar"
*/
  @Test
  fun testSubgrammar() {
    val terminalImage = setOf<String>() + "NEWLINE" + validPythonStatements.tokenizeByWhitespace().toSet()
    val s2pg = vanillaS2PCFG
    val subgrammar = s2pg.subgrammar(terminalImage)
    println("Original size: ${s2pg.size}")
    println("Subgrammar size: ${subgrammar.size}")

//    println("Must generate:\n${s2pg.mustGenerate.filter { it.value.isNotEmpty() }.entries.joinToString("\n") { "${it.key} -> ${it.value}" }}")
//    println("::::::::::::")
//    subgrammar.forEach { println("${it.LHS} ->" + it.RHS.joinToString(" ")) }

//    fun Forest.summarize() = joinToString("\n") { it.root + "-[${it.children.joinToString(","){it.root}}]" }

    pythonTestCases.forEach { (_, it) ->
      val pp ="$it NEWLINE" .also { println(it) }

//      val z1=  subgrammar.initialUTMatrix(pp.tokenizeByWhitespace()).seekFixpoint().diagonals
//      val z2 = s2pg.initialUTMatrix(pp.tokenizeByWhitespace()).seekFixpoint().diagonals
////        .zip(s2pg.initialUTMatrix(pp.tokenizeByWhitespace()).diagonals)
//      println(z1.size)
//      println(z2.size)
//      val lastGoodDiag = z1.indexOfLast { it.any { it.summarize().isNotEmpty() } }
//      println(lastGoodDiag)
//      val lastGood = z1.last { it.any { it.summarize().isNotEmpty() } }
//      println(lastGood.map { it.summarize() }.joinToString("\n"))
//      println(z2[lastGoodDiag].map { it.summarize() }.sorted().joinToString("\n"))
////        .first { (a, b) -> a != b }.let {  (sgd, s2gd) ->
////          sgd.zip(s2gd).forEach { (f1, f2) -> println(f1.summarize() + "\n" + f2.summarize()) }
////        }
//
////      subgrammar.parseInvalidWithMaximalFragments(pp).forEach { println(it.prettyPrint() + "\n\n") }
//      println(s2pg.parse(pp)!!.prettyPrint())
//      println(lastGood.first { it.isNotEmpty() }.first().prettyPrint())
      assertTrue(pp in s2pg.language, "$it\nnot in vanillaS2PCFG!")
      assertTrue(pp in subgrammar.language, "$it\nnot in subgrammar!")
    }
    subgrammar.sampleSeq(List(20) {"_"}).take(100).forEach { pp ->
      assertTrue(pp in vanillaS2PCFG.language, "$pp\nnot in vanillaS2PCFG!")
      assertTrue(pp in subgrammar.language, "$pp\nnot in subgrammar!")
    }
  }

  val topTerms by lazy {
    contextCSV.allProbs.entries
      .filter { it.key.type != EditType.DEL }
      .groupingBy { vanillaS2PCFG.getS2PNT(it.key.newMid) }
      .aggregate { _, acc: Int?, it, _ -> (acc ?: 0) + it.value }
      .map { (k, v) -> k to v }
      .sortedBy { -it.second }
//      .onEach { println("${it.first}≡${vanillaS2PCFG.bimap[it.first]}: ${it.second}") }
      .mapNotNull { vanillaS2PCFG.bimap[it.first].firstOrNull() }
      .take(20)
      .toSet()
  }


/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.repair.ProbabilisticLBH.threeEditRepair"
*/
  @Test
  fun threeEditRepair() {
    val source = "NAME = { STRING = NUMBER , STRING = NUMBER , STRING = NUMBER } NEWLINE"
    val repair = "NAME = { STRING : NUMBER , STRING : NUMBER , STRING : NUMBER } NEWLINE"
    val gram = vanillaS2PCFG
//    MAX_TOKENS = source.tokenizeByWhitespace().size + 5
//    MAX_RADIUS = 3
    val levDist = 3
    assertTrue(repair in gram.language && levenshtein(source, repair) <= levDist)

    val clock = TimeSource.Monotonic.markNow()
    val levBall = makeLevFSA(source.tokenizeByWhitespace(), levDist)
    val intGram = gram.jvmIntersectLevFSA(levBall)
    println("Finished ${intGram.size}-prod ∩-grammar in ${clock.elapsedNow()}")
    val lbhSet = intGram.toPTree().sampleDirectlyWOR()
      .takeWhile { clock.elapsedNow().inWholeSeconds < 30 }.collect(Collectors.toSet())
    println("Sampled ${lbhSet.size} repairs using Levenshtein/Bar-Hillel in ${clock.elapsedNow()}")
    assertTrue(repair in intGram.language)
    println(repair in lbhSet)
  }

  /*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.repair.ProbabilisticLBH.testInvalidLines"
*/
//  @Test
  fun testInvalidLines() {
    invalidPythonStatements.lines().shuffled().take(10).forEach {
      val toRepair = "$it NEWLINE".tokenizeByWhitespace()
      println("Repairing: ${toRepair.joinToString(" ")}\nRepairs:\n")
      vanillaS2PCFG.fasterRepairSeq(toRepair)
        .filter { it.isNotEmpty() }.distinct().take(10).forEach {
          println(levenshteinAlign(toRepair, it.tokenizeByWhitespace()).paintANSIColors())
        }
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.repair.ProbabilisticLBH.testCompleteness"
*/
//  @Test
  fun testCompleteness() {
    val s2pg = vanillaS2PCFG
    val TIMEOUT_MINS = 2
    val totalTrials = 10
    var currentTrials = 0
    var successTrials = 0
    var avgTimeSec = 0
    fun printStats(broke: String, fixed: String, totalRepairs: Int) {
      println("Precision at $TIMEOUT_MINS minutes: $successTrials / $currentTrials")
      println("Mean time to find human repair: ${avgTimeSec.toDouble() / successTrials}s ($successTrials trials)")
      if (totalRepairs == -1) println("LBH yielded empty grammar: ${levenshteinAlign(broke, fixed).paintANSIColors()}\n")
      else println("# of unique repairs discovered: ${totalRepairs}\n")
    }
    pythonTestCases.take(totalTrials).forEach { (broke, fixed) ->
      val clock = TimeSource.Monotonic.markNow()
      val origBroke = "$broke NEWLINE"
      val origFixed = "$fixed NEWLINE"
      println("Fixing: $origBroke")
      val toRepair = origBroke.tokenizeByWhitespace()
      val humanRepair = origFixed.tokenizeByWhitespace()
      val levDist = 2

      val levBall = makeLevFSA(toRepair, levDist)
      val humanRepairANSI = levenshteinAlign(toRepair, humanRepair).paintANSIColors()
//        levBall.debug(humanRepair)

      assertTrue(levBall.recognizes(humanRepair),
        "Human repair not recognized by LevFSA (${levenshtein(origBroke, origFixed)}): $humanRepairANSI")

      try {
        val intGram = s2pg.jvmIntersectLevFSA(levBall)
        println("Finished intersection in ${clock.elapsedNow()}")

        val template = List(toRepair.size + levDist) { "_" }

        assertTrue(humanRepair in s2pg.language, "Human repair not recognized by CFG: $humanRepairANSI")
//        assertTrue(humanRepair in intGram.language, "Human repair not recognized by LBH: $humanRepairANSI")
        if (humanRepair !in intGram.language) {
          currentTrials++
          println("Human repair not recognized by LBH: $humanRepairANSI")
          return@forEach
        }

        var foundHumanRepair = false
        intGram.parallelEnumSeqMinimalWR(template, toRepair) {
            clock.elapsedNow().inWholeMinutes < TIMEOUT_MINS && !foundHumanRepair
        }.onEachIndexed { i, it ->
          val alignment = levenshteinAlign(origBroke, it).paintANSIColors()
          if (i < 100) println(alignment)

          assertTrue(levenshtein(origBroke, it) <= levDist, "LBH repair too far: $alignment")
          assertTrue(it in s2pg.language, "CFG did not recognize: $alignment")
          assertTrue(levBall.recognizes(it), "LevFSA did not recognize: $alignment")
          if (it.tokenizeByWhitespace() == humanRepair) {
            println("Human repair found after $i samples and ${clock.elapsedNow()}")
            foundHumanRepair = true
          }
        }.toList()
        .also {
          currentTrials++
          if (origFixed !in it) println("Human repair not found:\n$humanRepairANSI")
          else { successTrials++; avgTimeSec += clock.elapsedNow().inWholeSeconds.toInt() }
          printStats(origBroke, origFixed, it.size)
        }
      } catch (exception: NoSuchElementException) { printStats(origBroke, origFixed, -1) }
    }
  }

  fun CFG.getS2PNT(string: String) =
    (if (string.trim().startsWith("'") && string.trim().endsWith("'"))
        bimap[listOf(string.trim().drop(1).dropLast(1))]
      else bimap[listOf(string.trim())])

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.repair.ProbabilisticLBH.testTinyC"
*/
  @Test
  fun testTinyC() {
    println(pythonTestCases.size)
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.repair.ProbabilisticLBH.testPerfectRecall"
*/
//  @Test
  fun testPerfectRecall() {
    // Perfect recall on first 20 repairs takes ~7 minutes on a 2019 MacBook Pro
    var errorRate = 0
    var (recall, total) = 0 to 0
    val sampleTimeByLevDist = mutableMapOf(1 to 0.0, 2 to 0.0, 3 to 0.0)
    val allTimeByLevDist = mutableMapOf(1 to 0.0, 2 to 0.0, 3 to 0.0)
    val samplesBeforeMatchByLevDist = mutableMapOf(1 to 0.0, 2 to 0.0, 3 to 0.0)
    val s2pg = vanillaS2PCFG

    invalidPythonStatements.lines().zip(validPythonStatements.lines())
//      .filter { (invalid, valid) -> 3 == levenshtein(invalid, valid) }.take(50)
//      .take(10)
      .forEach { (invalid, valid) ->
        val allTime = TimeSource.Monotonic.markNow()
        val toRepair = "$invalid NEWLINE".tokenizeByWhitespace()
        val humanRepair = "$valid NEWLINE".tokenizeByWhitespace()
        val target = humanRepair.joinToString(" ")
        val levDist = levenshtein(toRepair, humanRepair)

        val levBall = makeLevFSA(toRepair, levDist)
        val humanRepairANSI = levenshteinAlign(toRepair, humanRepair).paintANSIColors()
        val intGram = try { s2pg.jvmIntersectLevFSA(levBall) }
        catch (e: Exception) {
          println("Encountered error: ${e.message}")
          println("Recall: $recall / $total, errors: ${++errorRate}")
          return@forEach
        }

        total++
        assertTrue(humanRepair in s2pg.language)
        assertTrue(levBall.recognizes(humanRepair))
        assertTrue(humanRepair in intGram.language)
        println("Ground truth repair: $humanRepairANSI")
        val clock = TimeSource.Monotonic.markNow()
        var samplesBeforeMatch = 0
        var matchFound = false
        val timeout = 120.seconds
        run untilDone@{
          intGram.sampleDirectlyWR(stoppingCriterion = { clock.elapsedNow() < timeout }).distinct().forEach {
            samplesBeforeMatch++
            if (it == target) {
              matchFound = true
              val elapsed = clock.elapsedNow().inWholeMilliseconds
              val allElapsed = allTime.elapsedNow().inWholeMilliseconds
              println("Found human repair (${clock.elapsedNow()}): $humanRepairANSI")
              println("Found length-$levDist repair in $elapsed ms, $allElapsed ms, $samplesBeforeMatch samples")
              println("Recall / samples : ${++recall} / $total, errors: $errorRate")
              sampleTimeByLevDist[levDist] = sampleTimeByLevDist[levDist]!! + elapsed
              println("Draw timings (ms): ${sampleTimeByLevDist.mapValues { it.value / recall }}")
              allTimeByLevDist[levDist] = allTimeByLevDist[levDist]!! + allElapsed
              println("Full timings (ms): ${allTimeByLevDist.mapValues { it.value / recall }}")
              samplesBeforeMatchByLevDist[levDist] = samplesBeforeMatchByLevDist[levDist]!! + samplesBeforeMatch
              println("Avg samples drawn: ${samplesBeforeMatchByLevDist.mapValues { it.value / recall }}")
//              return@untilDone
//            } else {
//              val ascii = levenshteinAlign(toRepair, it.tokenizeByWhitespace()).paintANSIColors()
//              println("Found valid repair (${clock.elapsedNow()}): $ascii")
            }
          }
        }

        if (!matchFound) println("Drew $samplesBeforeMatch samples in $timeout, length-$levDist human repair not found")

        println()
      }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.repair.ProbabilisticLBH.diagnoseWholeGrammarDeletion"
*/
/** This is most likely related to [isCompatibleWith] and invalid pruning. This reduces memory consumption
 * but is sometimes invalid. TODO: figure out a more precise heuristic that is an exact over-approximation. */
//  @Test
  fun diagnoseWholeGrammarDeletion() {
    // Sometimes the whole grammar is deleted because there are no generating or reachable productions
  //  val toRepair = "NAME . NAME ( STRING , class = STRING ) . NAME ( STRING , NAME = NAME . NAME ( STRING ) ) NEWLINE".tokenizeByWhitespace()
//    val toRepair = "NAME = NAME ( NAME , NAME = lambda NAME : ( NAME ( NAME [ NUMBER ] ) , NAME ( NAME [ NUMBER ] ) ) NEWLINE".tokenizeByWhitespace()
      val toRepair = "NAME = STRING NEWLINE NAME = NAME ( NAME , NAME [ NUMBER : - NUMBER ] . NAME ( STRING ) NEWLINE".tokenizeByWhitespace()
      val clock = TimeSource.Monotonic.markNow()

      val levDist = 2
      val s2pg = vanillaS2PCFG
      val levBall = makeLevFSA(toRepair, levDist)
      val intGram = s2pg.jvmIntersectLevFSA(levBall)
      val template = List(toRepair.size + levDist) { "_" }

      intGram.parallelEnumSeqMinimalWR(template, toRepair)
        .onEachIndexed { i, it ->
        val alignment = levenshteinAlign(toRepair.joinToString(" "), it).paintANSIColors()
        println(alignment)
      }.take(39).toList()
        .also { println("TOTAL LBH REPAIRS (${clock.elapsedNow()}): ${it.size}\n\n") }
    }

  fun String.maskRandomIndices(toMask: Int) =
    tokenizeByWhitespace().let { tks ->
      val indicesToMask = tks.indices.shuffled().take(toMask)
      tks.mapIndexed { i, it -> if (i in indicesToMask) "_" else it }
    }

  fun cfgToValidStrings(holes: Int) =
    validPythonStatements
      .lines()
      .shuffled()
      .flatMap { (0..10).map { _ -> vanillaS2PCFG to it.maskRandomIndices(holes) } }
      .filter { (a, b) ->
        val clock = TimeSource.Monotonic.markNow()
        a.sampleSWOR(b).takeWhile { clock.elapsedNow() < 2.seconds }.distinct().toList().size > 1
      }.take(100)

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.repair.ProbabilisticLBH.testRandomGrammarCompletion"
*/
//  @Test
  fun testRandomGrammarCompletion() {
    val duration: Duration = 10.seconds
    fun List<Pair<CFG, List<String>>>.benchmark(f: KFunction2<CFG, List<String>, Sequence<String>>, holes: Int) =
      parallelStream().map { (cfg, seq) ->
        val clock = TimeSource.Monotonic.markNow()

        val results = f(cfg, seq).takeWhile { clock.elapsedNow() < duration }.distinct().toList()
        results.size.toDouble()//.also { println("Found $it distinct results in $duration") }
      }.toList().also {
        println(
          "Average # $holes-hole results for ${f.name} found in $duration: " +
            "${it.average().round(3)}, ${it.stdDev().round(3)}"
        )
      }

    for (holes in 1..6) {
      val templates = cfgToValidStrings(holes)
      val tq = templates.benchmark(CFG::enumSeq, holes)
      val pq = templates.benchmark(CFG::sampleSWOR, holes)
      val mq = templates.benchmark(CFG::sampleSeq, holes)
      val rr = templates.benchmark(CFG::solveSeq, holes)
      println()
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.repair.ProbabilisticLBH.testMulticoreCompletion"
*/
//  @Test
  fun testMulticoreCompletion() {
    val duration = 10.seconds

    fun List<Pair<CFG, List<String>>>.benchmark(f: CFG.(List<String>) -> List<String>) =
      map { (cfg, seq) ->
        val results = f(cfg, seq).distinct().toList()
        results.size.toDouble()//.also { println("$name found $it distinct results in $duration") }
      }

    (2..6).forEach { holes ->
      val templates = cfgToValidStrings(holes)
      var clock = TimeSource.Monotonic.markNow()
      val singleCoreSWR =
        templates.benchmark { parallelEnumListWR(it, 1) { clock.elapsedNow() < duration } }
          .also {
            println(
              "Average ${" PSWR[cores=1,holes=$holes]"} found in $duration: " +
                  "${it.average().round(3)}, ${it.stdDev().round(3)}"
            )
            println("Relative improvement over single core: 0.0")
          }.average()
      clock = TimeSource.Monotonic.markNow()
      val singleCoreSWoR =
        templates.benchmark { parallelEnumListWOR(it, 1) { clock.elapsedNow() < duration } }
          .also {
            println(
              "Average ${"PSWoR[cores=1,holes=$holes]"} found in $duration: " +
                  "${it.average().round(3)}, ${it.stdDev().round(3)}"
            )
            println("Relative improvement over single core: 0.0")
          }.average()
      println()
      (2..10).forEach { cores ->
        clock = TimeSource.Monotonic.markNow()
        templates.benchmark { parallelEnumListWR(it, cores) { clock.elapsedNow() < duration } }
          .also {
            println(
              "Average ${" PSWR[cores=$cores,holes=$holes]"} found in $duration: " +
                  "${it.average().round(3)}, ${it.stdDev().round(3)}"
            )
            println(
              "Relative improvement over single core: " +
                  "${(it.average() - singleCoreSWR) / singleCoreSWR}"
            )
          }
        clock = TimeSource.Monotonic.markNow()
        templates.benchmark { parallelEnumListWOR(it, cores) { clock.elapsedNow() < duration } }
          .also {
            println(
              "Average ${"PSWoR[cores=$cores,holes=$holes]"} found in $duration: " +
                  "${it.average().round(3)}, ${it.stdDev().round(3)}"
            )
            println(
              "Relative improvement over single core: " +
                  "${(it.average() - singleCoreSWoR) / singleCoreSWoR}"
            )
          }
        println()
      }
    }
  }

  /*
  ./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.repair.ProbabilisticLBH.testDeadSimple"
  */
  @Test
  fun testDeadSimple() {
    val prompt = "( ) )"
    val ds = Grammars.dsNorm
    val la = makeLevFSA(prompt.tokenizeByWhitespace(), 4)

    val gre: GRE = repairWithGRE(prompt.tokenizeByWhitespace(), ds)!!
//    gre.showEditable()

//  println(la.stateLst)
//  val tikzAdj   = la.adjMat().toLaTeX()
//  val tikzReach = la.reachMat().toLaTeX()
//  println(tikzAdj)
//  println(tikzReach)

    val ig = ds.intersectLevFSA(la)

    println(ig.prettyPrint())

    assertTrue("( )" in ds.language)
    assertFalse(prompt in ds.language)
    assertFalse("( ) )" in ds.language)

    val solutions = ig.toPTree().sampleStrWithoutReplacement().toSet()
    solutions.forEach { println(it) }

    println(ig.toPTree().toDFA(true)!!.toDot())
  }
}

// NAME . NAME ( STRING , class = STRING ) . NAME ( STRING , NAME = NAME . NAME ( STRING ) ) NEWLINE
//    NAME . NAME ( STRING , class ** STRING ) . NAME ( STRING , NAME = NAME . NAME ( STRING ) ) NEWLINE
//    NAME . NAME ( STRING , class = STRING ) . NAME ( STRING , NAME = NAME . NAME ( STRING ) ) NEWLINE
//    NAME . NAME ( STRING , NAME = STRING ) . NAME ( STRING , NAME = NAME . NAME ( STRING ) ) NEWLINE
//    NAME . NAME ( STRING , STRING = STRING ) . NAME ( STRING , NAME = NAME . NAME ( STRING ) ) NEWLINE
//    NAME . NAME ( STRING , ) = STRING ) . NAME ( STRING , NAME = NAME . NAME ( STRING ) ) NEWLINE
//    NAME . NAME ( STRING , NUMBER = STRING ) . NAME ( STRING , NAME = NAME . NAME ( STRING ) ) NEWLINE
//    NAME . NAME ( STRING , class + STRING ) . NAME ( STRING , NAME = NAME . NAME ( STRING ) ) NEWLINE
//    NAME . NAME ( STRING , ... = STRING ) . NAME ( STRING , NAME = NAME . NAME ( STRING ) ) NEWLINE
//    NAME . NAME ( STRING , ) = ( ) . NAME ( STRING , NAME = NAME . NAME ( STRING ) ) NEWLINE
//    NAME ( NAME ( STRING , ) = STRING ) . NAME ( STRING , NAME = NAME . NAME ( STRING ) ) NEWLINE
//    NAME . NAME ( STRING , class * STRING ) . NAME ( STRING , NAME = NAME . NAME ( STRING ) ) NEWLINE
//    NAME . NAME ( STRING , class - STRING ) . NAME ( STRING , NAME = NAME . NAME ( STRING ) ) NEWLINE
//    NAME . NAME ( STRING , class not STRING ) . NAME ( STRING , NAME = NAME . NAME ( STRING ) ) NEWLINE
//    NAME . NAME ( STRING , not + STRING ) . NAME ( STRING , NAME = NAME . NAME ( STRING ) ) NEWLINE
//    NAME . NAME ( STRING , ) ( STRING ) . NAME ( STRING , NAME = NAME . NAME ( STRING ) ) NEWLINE
//    NAME . NAME ( STRING , * + STRING ) . NAME ( STRING , NAME = NAME . NAME ( STRING ) ) NEWLINE
//    NAME . NAME ( STRING ( ) = STRING ) . NAME ( STRING , NAME = NAME . NAME ( STRING ) ) NEWLINE
//    NAME . NAME ( STRING , ** + STRING ) . NAME ( STRING , NAME = NAME . NAME ( STRING ) ) NEWLINE
//    NAME . NAME ( STRING , * - STRING ) . NAME ( STRING , NAME = NAME . NAME ( STRING ) ) NEWLINE
//    NAME . NAME ( STRING , ) = STRING ( ) . NAME ( STRING , NAME = NAME . NAME ( STRING ) ) NEWLINE
//    NAME . NAME ( STRING , * not STRING ) . NAME ( STRING , NAME = NAME . NAME ( STRING ) ) NEWLINE
//    NAME . NAME ( STRING , ( ) = STRING ) . NAME ( STRING , NAME = NAME . NAME ( STRING ) ) NEWLINE
//    NAME . NAME ( STRING , + + STRING ) . NAME ( STRING , NAME = NAME . NAME ( STRING ) ) NEWLINE
//    NAME . NAME ( STRING , ** - STRING ) . NAME ( STRING , NAME = NAME . NAME ( STRING ) ) NEWLINE
//    NAME . NAME ( STRING , None = STRING ) . NAME ( STRING , NAME = NAME . NAME ( STRING ) ) NEWLINE
//    NAME . NAME ( STRING , ** not STRING ) . NAME ( STRING , NAME = NAME . NAME ( STRING ) ) NEWLINE
//    NAME . NAME ( STRING , - + STRING ) . NAME ( STRING , NAME = NAME . NAME ( STRING ) ) NEWLINE
//    NAME . NAME ( STRING , + - STRING ) . NAME ( STRING , NAME = NAME . NAME ( STRING ) ) NEWLINE
//    NAME . NAME ( STRING , True = STRING ) . NAME ( STRING , NAME = NAME . NAME ( STRING ) ) NEWLINE
//    NAME . NAME ( STRING , not - STRING ) . NAME ( STRING , NAME = NAME . NAME ( STRING ) ) NEWLINE
//    NAME . NAME ( STRING , ) = ( STRING ) . NAME ( STRING , NAME = NAME . NAME ( STRING ) ) NEWLINE
//    NAME . NAME ( STRING , - - STRING ) . NAME ( STRING , NAME = NAME . NAME ( STRING ) ) NEWLINE
//    NAME . NAME ( STRING , [ ] = STRING ) . NAME ( STRING , NAME = NAME . NAME ( STRING ) ) NEWLINE
//    NAME . NAME ( STRING , { } = STRING ) . NAME ( STRING , NAME = NAME . NAME ( STRING ) ) NEWLINE
//    NAME . NAME ( STRING , not not STRING ) . NAME ( STRING , NAME = NAME . NAME ( STRING ) ) NEWLINE
//    NAME . NAME ( ( STRING , ) = STRING ) . NAME ( STRING , NAME = NAME . NAME ( STRING ) ) NEWLINE
//    NAME . NAME ( [ STRING , ] = STRING ) . NAME ( STRING , NAME = NAME . NAME ( STRING ) ) NEWLINE
//    NAME . NAME ( STRING , lambda : STRING ) . NAME ( STRING , NAME = NAME . NAME ( STRING ) ) NEWLINE
//    NAME . NAME ( { STRING , } = STRING ) . NAME ( STRING , NAME = NAME . NAME ( STRING ) ) NEWLINE