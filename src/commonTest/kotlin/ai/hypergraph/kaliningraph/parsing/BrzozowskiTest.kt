package ai.hypergraph.kaliningraph.parsing

import Grammars
import ai.hypergraph.kaliningraph.automata.startGRE
import ai.hypergraph.kaliningraph.types.*
import kotlin.test.*
import kotlin.time.measureTime

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.BrzozowskiTest"
*/
class BrzozowskiTest {
/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.BrzozowskiTest.testGRE"
*/
  @Test
  fun testGRE() {
    val t = Grammars.ifThen.startGRE(List(5) { "_" })
    println(t!!.toString().length)
    measureTime { t.words(Grammars.ifThen.tmLst).toList().size.also { println("Words: $it") } }.also { println("Time: $it") }
  }

  /*
  ./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.BrzozowskiTest.testLeftQuotient"
  */
  @Test
  fun testLeftQuotient() {
    """S -> ( ) | [ ] | ( S ) | [ S ] | S S""".parseCFG().run {
      val nts = nonterminals.powerset()
        .let { nonterminals.map { setOf(it) }.shuffled().asSequence() * it.shuffled() * nonterminals.map { setOf(it) }.shuffled().asSequence() }

      println(prettyPrint())

      nts.filter {  (f, g, x) ->
        dl(f, x).isNotEmpty() && dl(g, x).isNotEmpty() && 2 < dl(setJoin(f, g), x).size
      }
        .map { (f, g, x) ->
        val lhs1 = dl(f union g, x)
        val rhs1 = dl(f, x) union dl(g, x)
        assertEquals(lhs1, rhs1)

        // d(f⊗g) = d(f)⊗g ⊕ f⊗d(g)
        val lhs2 = dl(setJoin(f, g), x)
        val rhs2 = setJoin(dl(f, x), g) union setJoin(f, dl(g, x))
//          val lhs2 = dl(setJoin(f, g), f) union dr(setJoin(f, g), g)
//          val rhs2 = setJoin(dl(f, g), g) union setJoin(f, dr(g, x))
        println("f=$f\ng=$g\nx=$x")

        if(lhs2 != rhs2) println("$lhs2 != $rhs2") else println("$lhs2 == $rhs2")
        assertTrue(lhs2.intersect(rhs2).isNotEmpty())
//          assertEquals(lhs2, rhs2)
      }.take(100).toList()
    }
  }
}