package ai.hypergraph.kaliningraph.languages

import ai.hypergraph.kaliningraph.parsing.terminals
import ai.hypergraph.kaliningraph.parsing.Σᐩ
import ai.hypergraph.kaliningraph.repair.invalidPythonStatements
import ai.hypergraph.kaliningraph.repair.validPythonStatements
import ai.hypergraph.kaliningraph.repair.vanillaS2PCFG
import ai.hypergraph.kaliningraph.tokenizeByWhitespace
import ai.hypergraph.markovian.mcmc.MarkovChain
import java.io.File
import kotlin.random.Random

object Python {
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

  val subwords: Set<List<Σᐩ>> by lazy {
    val txt = File(File("").absolutePath + "/src/jvmTest/resources/python_subwords.txt")
      txt.readLines().map { it.tokenizeByWhitespace() }.toSet()
      .also { println("Loaded ${it.size} length-4 Python subwords from ${txt.absolutePath}") }
  }

  val P_BIFI_PY150: MarkovChain<Σᐩ> by lazy { P_BIFI + P_PY150 }

  val testCases by lazy {
    invalidPythonStatements.lines().zip(validPythonStatements.lines())
      .filter { it.first.tokenizeByWhitespace().all { it in vanillaS2PCFG.terminals } }
      .shuffled(Random(seed = 1))
//      .filter { (a, b) ->
//        ("$a NEWLINE" !in vanillaS2PCFG.language).also { if (!it) println("Failed invalid") }
//            && ("$b NEWLINE" in vanillaS2PCFG.language).also { if (!it) println("Failed valid") }
//            && (levenshtein(a, b).also { if (it !in 1..3) println("Failed distance: $it") } in 1..3)
//      }
      .distinct().filter { it.first.tokenizeByWhitespace().size < 43 }
  }
}