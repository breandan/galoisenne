package ai.hypergraph.kaliningraph.repair

import Grammars
import ai.hypergraph.kaliningraph.parsing.*
import ai.hypergraph.kaliningraph.tokenizeByWhitespace
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.random.Random
import kotlin.test.*
import kotlin.time.TimeSource

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.repair.ProbabilisticLBH"
*/
class ProbabilisticLBH {
/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.repair.ProbabilisticLBH.testProbabilisticLBH"
*/
  @Test
  fun testProbabilisticLBH() {
//    Grammars.seq2parsePythonCFG.mustGenerate.entries.forEach {
//      println(it.key + " -> " + it.value)
//    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.repair.ProbabilisticLBH.testSubgrammarEquivalence"
*/
  @Test
  fun testSubgrammarEquivalence() {
    val terminalImage = setOf<String>() + "NEWLINE" + validPythonStatements.tokenizeByWhitespace().toSet()
    val s2pg = Grammars.seq2parsePythonCFG.noEpsilonOrNonterminalStubs
    val subgrammar = s2pg.subgrammar(terminalImage)

    (validPythonStatements + invalidPythonStatements).lines()
      .forEach { assertEquals(s2pg.parse(it), subgrammar.parse(it)) }
  }

  /*
  ./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.repair.ProbabilisticLBH.testSubgrammar"
  */
  @Test
  fun testSubgrammar() {
    val terminalImage = setOf<String>() + "NEWLINE" + validPythonStatements.tokenizeByWhitespace().toSet()
    val s2pg = Grammars.seq2parsePythonCFG.noEpsilonOrNonterminalStubs
    val subgrammar = s2pg.subgrammar(terminalImage)
    println("Original size: ${s2pg.size}")
    println("Subgrammar size: ${subgrammar.size}")

//    println("Must generate:\n${s2pg.mustGenerate.filter { it.value.isNotEmpty() }.entries.joinToString("\n") { "${it.key} -> ${it.value}" }}")
//    println("::::::::::::")
//    subgrammar.forEach { println("${it.LHS} ->" + it.RHS.joinToString(" ")) }

//    fun Forest.summarize() = joinToString("\n") { it.root + "-[${it.children.joinToString(","){it.root}}]" }

    validPythonStatements.lines().forEach {
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
      assertTrue(pp in s2pg.language, "$it\nnot in Grammars.seq2parsePythonCFG!")
      assertTrue(pp in subgrammar.language, "$it\nnot in subgrammar!")
    }
    subgrammar.sampleSeq(List(20) {"_"}).take(100).forEach { pp ->
      assertTrue(pp in Grammars.seq2parsePythonCFG.language, "$pp\nnot in Grammars.seq2parsePythonCFG!")
      assertTrue(pp in subgrammar.language, "$pp\nnot in subgrammar!")
    }
  }

  val topTerms by lazy {
    contextCSV.allProbs.entries
      .filter { it.key.type != EditType.DEL }
      .groupingBy { Grammars.seq2parsePythonCFG.getS2PNT(it.key.newMid) }
      .aggregate { _, acc: Int?, it, _ -> (acc ?: 0) + it.value }
      .map { (k, v) -> k to v }
      .sortedBy { -it.second }
//      .onEach { println("${it.first}≡${Grammars.seq2parsePythonCFG.bimap[it.first]}: ${it.second}") }
      .mapNotNull { Grammars.seq2parsePythonCFG.bimap[it.first].firstOrNull() }
      .take(20)
      .toSet()
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.repair.ProbabilisticLBH.testInvalidLines"
*/
  @Test
  fun testInvalidLines() {
    invalidPythonStatements.lines().forEach {
      val toRepair = "$it NEWLINE".tokenizeByWhitespace()
      println("Repairing: ${toRepair.joinToString(" ")}\nRepairs:\n")
      Grammars.seq2parsePythonCFG.fasterRepairSeq(toRepair)
        .filter { it.isNotEmpty() }.distinct().take(10).forEach {
          println(levenshteinAlign(toRepair, it.tokenizeByWhitespace()).paintANSIColors())
        }
    }
  }

  /*
  ./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.repair.ProbabilisticLBH.testCompleteness"
  */
  @Test
  fun testCompleteness() {
    val s2pg = Grammars.seq2parsePythonCFG.noEpsilonOrNonterminalStubs
    val brokeLines = invalidPythonStatements.lines()
    val fixedLines = validPythonStatements.lines()
    brokeLines.zip(fixedLines).shuffled(Random(seed = 1))
      .filter {
        it.first !in s2pg.language
          && (it.second + " NEWLINE") in s2pg.language
          && levenshtein(it.first, it.second) < 3
      }
      .take(5).forEach { (broke, fixed) ->
      val clock = TimeSource.Monotonic.markNow()
      val origBroke = "$broke NEWLINE"
      val origFixed = "$fixed NEWLINE"
      println("Fixing: $origBroke")
      val toRepair = origBroke.tokenizeByWhitespace()
      val humanRepair = origFixed.tokenizeByWhitespace()
      val levDist = 2

      val levBall = makeLevFSA(toRepair, levDist, s2pg.terminals, ceaDist = contextCSV)
      val humanRepairANSI = levenshteinAlign(toRepair, humanRepair).paintANSIColors()
      assertTrue(levBall.recognizes(humanRepair), "Human repair not recognized by LevFSA (${levenshtein(origBroke, origFixed)}): $humanRepairANSI")

      println("Total transitions in FSA: ${levBall.Q.size}")
      println("Prompt: $origBroke")
      println("Alphabet: ${levBall.alphabet}")
      try {
        val intGram = s2pg.intersectLevFSA(levBall)
        println("Finished intersection in ${clock.elapsedNow()}")
//      intGram.forEach { println("${it.LHS} -> ${it.RHS.joinToString(" ")}") }

        val template = List(toRepair.size + levDist) { "_" }

        assertTrue(humanRepair in intGram.language, "Human repair not recognized by LBH: $humanRepairANSI")

        val lbhSet = intGram.enumSeqMinimal(template, toRepair)
          .onEachIndexed { i, it ->
            if (i < 100) println(levenshteinAlign(origBroke, it).paintANSIColors())

            assertTrue(levenshtein(origBroke, it) <= levDist, "LBH repair too far: $it")
            assertTrue(it in s2pg.language, "CFG did not recognize: $it")
            assertTrue(levBall.recognizes(it), "LevFSA did not recognize: $it")
          }.take(10).toList()
          .also { assertTrue(it.isNotEmpty(), "No repairs found for repairable snippet!") }
          // TOTAL LBH REPAIRS (1m 56.288773333s): 9
          .also { println("TOTAL LBH REPAIRS (${clock.elapsedNow()}): ${it.size}\n\n") }
      } catch (exception: Exception) {
        println("Exception: $origBroke")
        throw exception
      }
    }
  }

  fun CFG.getS2PNT(string: String) =
    (if (string.trim().startsWith("'") && string.trim().endsWith("'"))
        bimap[listOf(string.trim().drop(1).dropLast(1))]
      else bimap[listOf(string.trim())])
}