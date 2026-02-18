package ai.hypergraph.kaliningraph.approximation

import ai.hypergraph.kaliningraph.automata.*
import ai.hypergraph.kaliningraph.languages.Python
import ai.hypergraph.kaliningraph.languages.Python.subwords
import ai.hypergraph.kaliningraph.parsing.*
import ai.hypergraph.kaliningraph.parsing.NFA.Companion.toNFA
import ai.hypergraph.kaliningraph.parsing.approximations.*
import ai.hypergraph.kaliningraph.repair.toyPython
import ai.hypergraph.kaliningraph.repair.vanillaS2PCFG
import ai.hypergraph.kaliningraph.tokenizeByWhitespace
import ai.hypergraph.markovian.mcmc.toNgramMap
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.time.measureTime

fun NFA.save(name: String) = File(name).writeBytes(toSafeTensors())

class ApproximationTest {
/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.approximation.ApproximationTest.testApproximation"
*/
  @Test
  fun testApproximation() {
    val cfg = Grammars.seq2parsePythonVanillaCFG
    val subwords = Python.subwords
//  val subwords = cfg.extractSubwords(4).sortedBy { it.joinToString(" ") }
//  println("Subwords: " + subwords.size)
//  File("python_subwords.txt").writeText(subwords.joinToString("\n"){ it.joinToString(" ")})
//  throw Exception("asdf")

    val approx = cfg.toNederhofNFA(historyDepth = 2).removeEpsilons()
    println("Original NFA ${approx.summary()}")
//    approx.sampleStrings(20).take(1000).filter { it in cfg.language }.forEach { println(it) }
//    val compl = approx.slice(33)

    val ngramNFA = subwords.toNFA().minimize()
    println("n-gram NFA: ${ngramNFA.summary()}")
    val intNFA = approx.intersect(ngramNFA)
    println("Intersection NFA: ${intNFA.summary()}")
    val minNFA = intNFA.minimize()
    println("Minimized NFA: ${minNFA.summary()}")
  val minDFA = minNFA.determinize()
  println("Determinized NFA: ${minDFA.summary()}")

  File("det_nfa.dot").writeText(minDFA.toGraphviz())

//    val validNgrams = minNFA.extractNgrams(4)//.also { println("Extracted ${it.keys.size} n-grams") }
//    val realNgrams = Python.P_BIFI_PY150.toNgramMap()
//    val allNgrams = (realNgrams + validNgrams)
//    val novelNgrams = (realNgrams.keys - subwords)
//    println("Real novel n-grams: " + novelNgrams.size)
//    novelNgrams.forEach {println(it.joinToString(" "))}

//    var itr = minNFA
//    var i = 0
//    var ratePer100 = 0
//
//  (0..10_000_000).forEach {
//    val w = itr.sample().first()
//    i++
//    itr = (if (w in cfg.language) { ratePer100++; itr } else {
//      cfg.findShortestInvalidSubstrings(w.tokenizeByWhitespace(), maxLen = 4, allNgrams=subwords)
//        .also { if (it.isNotEmpty()) println("Found ${it.size} invalid substrings (len=${it.first().size}), ${it.joinToString(" :: "){ it.joinToString(" ")}}") }
//        .forEach { itr = itr.removeFactor(it); println("Summary: ${itr.summary()}") }
//      itr.let { if (i % 100 == 0) { println("Rate: $ratePer100"); ratePer100 = 0; it.minimize() } else it }
//    println("Iteration: $i")
//    if (i % 1_000 == 0) { println("Validating..."); validate(itr, cfg) }
//    })

  cfg.sliceSample(23).take(100_000)
    .sortedBy { Python.P_BIFI_PY150.score(it.tokenizeByWhitespace()) }
    .distinct().take(1000)
//    .onEach { assertTrue(minNFA.recognizes(it.tokenizeByWhitespace())) }
    .forEach {
      val w = it.tokenizeByWhitespace();
      println("${ngramNFA.recognizes(w)} / ${approx.recognizes(w)} / ${minNFA.recognizes(w)} / ${minDFA.recognizes(w)} :: $it")
    }
  }

  fun validate(nfa: NFA, cfg: CFG, max: Int = 30) {
    val sampleSize = 1_000
    (5..max step 1).forEach {
      val total = nfa.slice(it).sampleAcyclic().take(sampleSize).count { it in cfg.language }
      println("$it, ${total / sampleSize.toDouble()}")
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.approximation.ApproximationTest.testWFA"
*/
  @Test
  fun testWFA() {
  val wfa = makeWFA(toyPython, Python.P_BIFI_PY150.toNgramMap(), subwords)
//  val wfa = makeWFA(vanillaS2PCFG, Python.P_BIFI_PY150.toNgramMap(), subwords)
//    File("pdfa4.safetensor").writeBytes(wfa.toSafeTensors())
    File("wfsa.dot").writeText(wfa.toGraphviz())
  }

  /*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.approximation.ApproximationTest.testPythonEquivalence"
*/
  @Test
  fun testPythonEquivalence() {
    val cfg = Grammars.seq2parsePythonCFG
  measureTime {
    var precision = 0
    val total = 100
    Python.testCases.take(total).forEach { (broke, fixed) ->
//    val t = initiateSerialRepair(broke.tokenizeByWhitespace(), cfg)
//      .take(100)

      println(broke)
      val gre0 = repairWithGRE(broke.tokenizeByWhitespace(), cfg)
      val gre1 = repairWithCompactCircuit(broke.tokenizeByWhitespace(), cfg)
      val gre0_dfa0 = gre0!!.toDFSMDirect(cfg.tmLst)
      val gre0_dfa1 = gre0!!.toDFSM(cfg.tmLst)
      val gre1_dfa0 = gre1!!.toDFSMDirect(cfg.tmLst)
      val gre1_dfa1 = gre1!!.toDFSM(cfg.tmLst)
      val gre0_dfa0_min = gre0_dfa0!!.minimize()
      val gre0_dfa1_min = gre0_dfa1!!.minimize()
      val gre1_dfa0_min = gre1_dfa0!!.minimize()
      val gre1_dfa1_min = gre1_dfa1!!.minimize()
      println("    GRE0+DFA0 => |L|: ${gre0_dfa0.countWords()}")
      println("    GRE0+DFA1 => |L|: ${gre0_dfa1.countWords()}")
      println("    GRE1+DFA0 => |L|: ${gre1_dfa0.countWords()}")
      println("    GRE1+DFA1 => |L|: ${gre1_dfa1.countWords()}")
      println("GRE0+DFA0+MIN => |L|: ${gre0_dfa0_min.countWords()}")
      println("GRE0+DFA1+MIN => |L|: ${gre0_dfa1_min.countWords()}")
      println("GRE1+DFA0+MIN => |L|: ${gre1_dfa0_min.countWords()}")
      println("GRE1+DFA1+MIN => |L|: ${gre1_dfa1_min.countWords()}")
//      println("Min fast size: ${fastGRE.toDFSMDirect(cfg.tmLst).minimize().countWords()}")
//      println("Min slow size: ${slowGRE.toDFSMDirect(cfg.tmLst).minimize().countWords()}")
      val t = gre1
        .also { gRE -> println("DFSM size: ${gRE.toDFSM(cfg.tmLst)?.minimize()?.Q?.size}") }
        .sampleStrWithoutReplacement(cfg.tmLst)
        .take(100).toList() ?: emptyList<Σᐩ>()

//      t.onEach {
//        assertTrue(it in vanillaS2PCFG.language)
//        if (3 < levenshtein(broke, it)) println(levenshteinAlign(broke, it).paintANSIColors())
//      }.toList().also { assertTrue(it.isNotEmpty(), "Empty repair!\n$broke") }

      if (fixed in t) precision++
    }
    println("Precision: ${precision / total.toDouble()}")
  }.also { println("Took: $it") }
  }
}