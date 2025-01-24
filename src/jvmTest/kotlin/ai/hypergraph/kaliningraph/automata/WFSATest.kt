package ai.hypergraph.kaliningraph.automata

import Grammars
import ai.hypergraph.kaliningraph.parsing.*
import ai.hypergraph.kaliningraph.repair.LangCache
import ai.hypergraph.kaliningraph.repair.vanillaS2PCFG
import ai.hypergraph.kaliningraph.repair.vanillaS2PCFGWE
import ai.hypergraph.kaliningraph.tokenizeByWhitespace
import ai.hypergraph.markovian.mcmc.MarkovChain
import dk.brics.automaton.Automaton
import dk.brics.automaton.RegExp
import net.jhoogland.jautomata.*
import net.jhoogland.jautomata.operations.Concatenation
import net.jhoogland.jautomata.semirings.RealSemiring
import java.io.File
import kotlin.system.measureTimeMillis
import kotlin.test.*
import kotlin.test.Test
import kotlin.time.measureTimedValue


class WFSATest {
  init { LangCache.prepopPythonLangCache() }
  val MARKOV_MEMORY = 4
  // Python3 snippets
// https://github.com/michiyasunaga/BIFI?tab=readme-ov-file#about-the-github-python-dataset
  val P_BIFI: MarkovChain<Σᐩ> by lazy {
//  readBIFIContents()
    val csv = File(File("").absolutePath + "/src/jvmTest/resources/ngrams_BIFI_$MARKOV_MEMORY.csv")
    MarkovChain.deserialize(csv.readText())
      .apply { scorePrefix = listOf("BOS", "NEWLINE"); scoreSuffix = listOf("EOS") }
      .also { println("Loaded ${it.counter.total} BIFI $MARKOV_MEMORY-grams from ${csv.absolutePath}") }
  }

  // Python2 snippets, about ~20x longer on average than BIFI
// https://www.sri.inf.ethz.ch/py150
  val P_PY150: MarkovChain<Σᐩ> by lazy {
    val csv = File(File("").absolutePath + "/src/jvmTest/resources/ngrams_PY150_$MARKOV_MEMORY.csv")
    MarkovChain.deserialize(csv.readText())
      .apply { scorePrefix = listOf("BOS", "NEWLINE"); scoreSuffix = listOf("EOS") }
      .also { println("Loaded ${it.counter.total} PY150 $MARKOV_MEMORY-grams from ${csv.absolutePath}") }
  }

  val P_BIFI_PY150: MarkovChain<Σᐩ> by lazy { P_BIFI + P_PY150 }

  /*
  ./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.automata.WFSATest.testWFSA"
  */
  @Test
  fun testWFSA() {
    val a: JAutomaton<String, Double> =
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
 ./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.automata.WFSATest.testBRICS"
*/
  @Test
  fun testBRICS() {
    val ab = BAutomaton.makeString("a").concatenate(BAutomaton.makeString("b"))
    val aa = BAutomaton.makeString("a").concatenate(BAutomaton.makeString("a"))
    val a = ab.union(aa)
    val ag = List(6) { a }.fold(a) { acc, automaton -> acc.concatenate(automaton) }

    println(ag.getFiniteStrings(-1))
    println()
    println(BAutomaton.minimize(ag.also { it.determinize() }).toDot())
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.automata.WFSATest.testIntersectionNonemptiness"
*/
  @Test
  fun testIntersectionNonemptiness() {
    val ab = BAutomaton.makeString("(").concatenate(BAutomaton.makeString(")"))
    val aa = BAutomaton.makeString("(").concatenate(BAutomaton.makeString("("))
    val ac = BAutomaton.makeString("(")
    val az = RegExp("((\\(+(\\(+\\)+){3,5}\\)+)|\\+)+").toAutomaton()

//  val template = List(10) { "_" } // Takes 3m?
    val template = List(8) { "_" }
    val ds = Grammars.dyckEmbedded.startPTree(template)!!
      .toDFA(false) { BAutomaton.makeString(it) }!!

//    val a = ab.union(aa).union(ac)
    val ag = RegExp("\\(|\\)|+|\\*").toAutomaton().repeat(7)
//    val ag = a.repeat(19, 25).union(az)
    println("SA:" + ag.getShortestExample(true))
    println("SD:" + ds.getShortestExample(true))
    println("SS:" + ds.intersection(ag).getShortestExample(true))

    measureTimeMillis { assertTrue(!ds.intersection(ag).isEmpty) }
      .also { println("Time: $it ms") }
  }

  /*
  ./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.automata.WFSATest.testPTreeVsWFSA"
  */
  @Test
  fun testPTreeVsWFSA() {
    println("${P_BIFI_PY150.memory}-gram Markov chain is ready.")
//    val toRepair = "from NAME import NAME NEWLINE NAME = NAME ( STRING , STRING ) NEWLINE NAME STRING . NAME ( NAME ) NEWLINE"
//    val groundTr = "NEWLINE from NAME import NAME NEWLINE NAME = NAME ( STRING , STRING ) NEWLINE NAME ( STRING . NAME ( NAME ) ) NEWLINE"
//    val radius = 3
    val toRepair = "NAME : NEWLINE NAME = STRING NEWLINE NAME = NAME . NAME ( STRING ) NEWLINE"
    val groundTr = "+ NAME : True NEWLINE NAME = STRING NEWLINE NAME = NAME . NAME ( STRING ) NEWLINE"
    val radius = 2
    val gram = vanillaS2PCFG.run { jvmIntersectLevFSAP(makeLevFSA(toRepair, radius), parikhMap) }
    val pt = gram.toPTree()

    println("Total trees: " + pt.totalTrees.toString())
    val maxResults = 10_000
    val ptreeRepairs = measureTimedValue {
      pt.sampleStrWithoutReplacement().distinct().take(maxResults).toSet()
    }
    measureTimedValue { pt.toDFA()!!.decodeDFA(P_BIFI_PY150, dec = pt.termDict) }.also {
      assertTrue(groundTr in it.value, "Ground truth not found in ${it.value.size} repairs")
      println("Index: ${it.value.indexOf(groundTr)}")
//      // Print side by side comparison of repairs
//      repairs.sorted().forEach {
//        val a = it
//        val b = if (it in repairs) it else ""
//        val colorA = levenshteinAlign(toRepair, a).paintANSIColors()
//        val colorB = if (b.isEmpty()) "" else levenshteinAlign(toRepair, b).paintANSIColors()
//        println("$colorA\n$colorB\n")
//      }
      assertEquals(ptreeRepairs.value.size, it.value.size)

      it.value.forEach {
//        println(levenshteinAlign(toRepair, it).paintANSIColors())
        assertTrue(levenshtein(toRepair, it) <= radius)
        assertTrue(it in vanillaS2PCFG.language)
      }

      println("Found ${it.value.size} unique repairs by decoding WFSA in ${it.duration}")
      println("Found ${ptreeRepairs.value.size} unique repairs by enumerating PTree in ${ptreeRepairs.duration}")
    }.also { println("Decoding ${it.value.size} repairs took ${it.duration}") }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.automata.WFSATest.testRepairMembership"
 */
  @Test
  fun testRepairMembership() {
    val toRepair = "if STRING in NAME : return [ NEWLINE"
    val groundTr = "if STRING in NAME : return NUMBER NEWLINE"
    println(groundTr in vanillaS2PCFG.language)
    val radius = 1
    val fsa = makeLevFSA(toRepair, radius)
    val gram = vanillaS2PCFG.run { jvmIntersectLevFSAP(fsa, parikhMap) }
    val pt = gram.toPTree()

    pt.toDFA(true)!!.decodeDFA(pt.termDict).toSet()
      .also { assertTrue(it.isNotEmpty()) }.onEach {
        assertTrue(fsa.recognizes(it) && it in vanillaS2PCFG.language)
        println(levenshteinAlign(toRepair, it).paintANSIColors())
      }.also { assertEquals(pt.sampleStrWithoutReplacement().toSet(), it) }

    assertTrue(groundTr in gram.language)
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.automata.WFSATest.testBijection"
*/
  @Test
  fun testBijection() {
    val toRepair = "NAME : NEWLINE NAME = STRING NEWLINE NAME = NAME ( STRING ) NEWLINE"
    val radius = 2
    val pt = vanillaS2PCFG.makeLevPTree(toRepair, radius)
    println(pt.totalTrees.toString())
    val maxResults = 10_000
    val repairs = pt.sampleStrWithoutReplacement().take(maxResults).toList()
    println("Found ${repairs.size} total repairs by enumerating PTree")
    val distinct = repairs.toSet().size
    println("Found $distinct unique repairs by enumerating PTree")
  }

  /*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.automata.WFSATest.testSelfInt"
*/
  @Test
  fun testSelfInt() {
    val startTime = System.currentTimeMillis()
    val toRepair = "_ _ _ _".split(" ")
    val toRepair1 = "True _ _ True _ _".split(" ")
    val pt = vanillaS2PCFG.startPTree(toRepair)!!
    val pt1 = vanillaS2PCFGWE.startPTree(toRepair1)!!
    val int = pt.toDFA(true)!!.intersection(pt1.toDFA(true))
    assertFalse(int.isEmpty)
    int.decodeDFA(pt1.termDict).toSet()
      .also { it.forEach { println(it) }; println("Size: ${it.size}") }
    println("Total time: ${System.currentTimeMillis() - startTime} ms")
  }

  /*
  ./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.automata.WFSATest.testRepulsivePointProcess"
  */
  @Test
  fun testRepulsivePointProcess() {
    Automaton.setMinimization(Automaton.MINIMIZE_BRZOZOWSKI)
    // One-hot vector where 1s are separated by at least three 0s
    val a1 = RegExp("[01]{13}&(0{3,5}(10{3,5}){2,}1?0{3,5})").toAutomaton().apply { minimize() }
    println(a1.toDot()
      .replace("0-1", "□|■")
      .replace("[label=\"", "[fontsize=\"20\", label=\"")
      .replace("\"0\"", "\"□\"")
      .replace("\"1\"", "\"■\"")
    )
    a1.finiteStrings.forEach {
      var minDist = Int.MAX_VALUE
      var lastIdx = -4
      it.forEachIndexed { idx, c ->
        if (c == '1') {
          minDist = minOf(minDist, idx - lastIdx)
          lastIdx = idx
        }
      }

      assertTrue(minDist >= 3)
    }
  }
}