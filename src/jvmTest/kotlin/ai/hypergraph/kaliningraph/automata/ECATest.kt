package ai.hypergraph.kaliningraph.automata

import ai.hypergraph.kaliningraph.sampling.findAll
import ai.hypergraph.kaliningraph.sat.BVar
import ai.hypergraph.kaliningraph.sat.and
import ai.hypergraph.kaliningraph.sat.solve
import ai.hypergraph.kaliningraph.tensor.FreeMatrix
import ai.hypergraph.kaliningraph.types.π2
import ai.hypergraph.kaliningraph.sat.F
import ai.hypergraph.kaliningraph.sat.T
import kotlin.test.Test

class ECATest {
/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.automata.ECATest.testSimpleECA"
*/
  @Test
  fun testSimpleECA() { initializeECA(20).evolve() }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.automata.ECATest.testLooper"
*/
  @Test
  fun testLooper() {
    for (j in 2..20) {
      val i = initializeSATECA(128) { i -> BVar("$i") }
      val t = (i matEq i.evolve(steps = 1)).negate() and (i matEq i.evolve(steps = j))
      try {
        val sol = t.solve()
        println("$j:" + i.data.map { sol[it!!.π2!!]!! })
      } catch (e: Exception) {}
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.automata.ECATest.testOrphan"
*/
  @Test
  fun testOrphan() {
    // Can we do better? https://wpmedia.wolfram.com/uploads/sites/13/2018/02/22-4-3.pdf
    findAll(setOf(true, false), 128).first { orphan ->
      val i = initializeSATECA(16) { i -> BVar("$i") }
      val fs = orphan.map { if(it) T else F }
      val t = (i.evolve(steps = 1) matEq initializeSATECA(16) { fs[it] })
        try { t.solve(); false } catch (e: Exception ) { true}
    }.also { println(it) }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.automata.ECATest.testEndling"
*/
  @Test
  fun testEndling() {
    for (j in 0..30) {
      val i = initializeSATECA(128) { i -> BVar("$i") }
      val t =
          (i.evolve(steps = j) matEq i.evolve(steps = j+1)).negate() and
          (i.evolve(steps = j+1) matEq i.evolve(steps = j+2))
      try {
        val sol = t.solve()
        println("$j:" + i.data.map { sol[it!!.π2!!]!! })
      } catch (e: Exception) {
        println("No solutions in $j steps")
      }
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.automata.ECATest.testChimera"
*/
  @Test
  fun testChimera() {
      val i = initializeSATECA(128) { i -> BVar("i$i") }
      val j = initializeSATECA(128) { i -> BVar("j$i") }
      val k = initializeSATECA(128) { i -> BVar("k$i") }
    val t =
      (
        (i matEq j).negate() and
        (j matEq k).negate() and
        (k matEq i).negate()
      ) and
      (i.evolve(steps = 1) matEq j.evolve(steps = 1)) and
      (j.evolve(steps = 1) matEq k.evolve(steps = 1))

    val sol = t.solve()
    println(i.data.map { sol[it!!.π2!!]!! })
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.automata.ECATest.testPrint"
*/
  @Test
  fun testPrint() {
    val ts = arrayOf(0,1,0,0,0,1,1,1,1,1,1,0,0,0,1,0)
    initializeECA(16) { i -> ts[i] == 1 }.also { it.toRingBuffer() }
      .evolve(steps = 1).also { it.toRingBuffer() }
      .evolve(steps = 1).also { it.toRingBuffer() }
  }

  fun FreeMatrix<Context<Boolean?>?>.toRingBuffer() {
    val degs = 360.0 / numRows
    for (i in 0 until numRows) {
      val start = 90.0 - i * degs
      val end = 90.0 - (i + 1) * degs
      val color = if (this[i].first()!!.q!!) "gray" else "white"
      println("\\fill [$color] (0,0) -- ($start:1) arc [end angle=$end, start angle=$start, radius=1] -- cycle;")
    }
    println()
  }
}