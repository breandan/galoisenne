package ai.hypergraph.kaliningraph.automata

import ai.hypergraph.kaliningraph.sampling.findAll
import ai.hypergraph.kaliningraph.sat.*
import ai.hypergraph.kaliningraph.types.π2
import org.logicng.formulas.Formula
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.automata.ECATest"
*/
class ECATest {
/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.automata.ECATest.testSimpleECA"
*/
  @Test
  fun testSimpleECA() { initializeECA(20).evolve() }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.automata.ECATest.testTypeLevelECA4"
*/
  @Test
  fun testTypeLevelECA4() {
    val init = BVec(T, F, F, F)
    fun BVec.bits() = data.map { it == T }
    var i = 1; init
      .eca(::r, ::r, ::r, ::r).also { assertEquals(it.bits(), init.bits().evolve(steps = i++)) }
      .eca(::r, ::r, ::r, ::r).also { assertEquals(it.bits(), init.bits().evolve(steps = i++)) }
      .eca(::r, ::r, ::r, ::r).also { assertEquals(it.bits(), init.bits().evolve(steps = i++)) }
      .eca(::r, ::r, ::r, ::r).also { assertEquals(it.bits(), init.bits().evolve(steps = i++)) }
      .eca(::r, ::r, ::r, ::r).also { assertEquals(it.bits(), init.bits().evolve(steps = i++)) }
      .eca(::r, ::r, ::r, ::r).also { assertEquals(it.bits(), init.bits().evolve(steps = i++)) }
      .eca(::r, ::r, ::r, ::r).also { assertEquals(it.bits(), init.bits().evolve(steps = i++)) }
      .eca(::r, ::r, ::r, ::r).also { assertEquals(it.bits(), init.bits().evolve(steps = i++)) }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.automata.ECATest.testTypeLevelECA10"
*/
  @Test
  fun testTypeLevelECA10() {
    val init = BVec(T, F, F, F, T, F, F, F, F, F)
    fun BVec.bits() = data.map { it == T }
    var i = 1; init
      .eca(::r, ::r, ::r, ::r,::r, ::r, ::r, ::r,::r, ::r)
      .also { assertEquals(it.bits(), init.bits().evolve(steps = i++)) }
      .eca(::r, ::r, ::r, ::r,::r, ::r, ::r, ::r,::r, ::r)
      .also { assertEquals(it.bits(), init.bits().evolve(steps = i++)) }
      .eca(::r, ::r, ::r, ::r,::r, ::r, ::r, ::r,::r, ::r)
      .also { assertEquals(it.bits(), init.bits().evolve(steps = i++)) }
      .eca(::r, ::r, ::r, ::r,::r, ::r, ::r, ::r,::r, ::r)
      .also { assertEquals(it.bits(), init.bits().evolve(steps = i++)) }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.automata.ECATest.testLooper"
*/
  @Test
  fun testLooper() {
    for (j in 2..5) {
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
      val fs = orphan.map { ff.constant(it) }
      val t = (i.evolve(steps = 1) matEq initializeSATECA(16) { fs[it] })
        try { t.solve(); false } catch (e: Exception ) { true}
    }.also { println(it) }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.automata.ECATest.testEndling"
*/
  @Test
  fun testEndling() {
    for (j in 0..5) {
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

  fun List<Boolean>.pretty(): String = joinToString("") { if (it) "1" else "0" }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.automata.ECATest.testChimera"
*/
  @Test
  fun testChimera() {
    println(listOf(true, false, false, true).evolve(steps = 1).pretty())
    val i = initializeSATECA(128) { i -> BVar("i$i") }
    val j = initializeSATECA(128) { i -> BVar("j$i") }
    val k = initializeSATECA(128) { i -> BVar("k$i") }
    val neqIJK = (i matEq j).negate() and (j matEq k).negate() and (k matEq i).negate()

    val (fi, fj, fk) =
      Triple(i.evolve(steps = 1), j.evolve(steps = 1), k.evolve(steps = 1))

    val cstr = neqIJK and (fi matEq fj) and (fj matEq fk) and
      fk.data.map { it!!.second!!.negate() }.fold(ff.falsum() as Formula) { a, b -> a.or(b) }

    val sol = cstr.solve()

    val (r, s, t) =
      Triple(i.data.map { sol[it!!.π2!!]!! }, j.data.map { sol[it!!.π2!!]!! }, k.data.map { sol[it!!.π2!!]!! })

    println("r:${r.pretty()}\ns:${s.pretty()}\nt:${t.pretty()}")

    assertNotEquals(r, s)
    assertNotEquals(s, t)
    assertNotEquals(t, r)

    val (fr, fs, ft) =
      Triple(r.evolve(steps = 1), s.evolve(steps = 1), t.evolve(steps = 1))
    println("f(r):${fr.pretty()}\nf(s):${fs.pretty()}\nf(t):${ft.pretty()}")

    assertEquals(fr, fs, "f(r) != f(s)")
    assertEquals(fs, ft, "f(s) != f(t)")
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.automata.ECATest.testTargetPatternPredecessor"
*/
  @Test
  fun testTargetPatternPredecessor() {
    val pp = initializeSATECA(16) { i -> BVar("i$i") }
    val p = initializeSATECA("1100110111111011")
    val t = pp.evolve(steps=1) matEq p
    try {
      val sol = t.solve()
      println(pp.data.map { sol[it!!.π2!!]!! })
    } catch (e: Exception) { println("No predecessor was found!") }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.automata.ECATest.testECAPrint"
*/
  @Test
  fun testECAPrint() {
  val ts = "0100011111100010".map { it == '1' }
    ts.evolve(0).also { it.toRingBuffer() }
      .evolve(steps = 1).also { it.toRingBuffer() }
      .evolve(steps = 1).also { it.toRingBuffer() }
  }

    fun List<Boolean>.toRingBuffer() {
    val degs = 360.0 / size
    for (i in indices) {
      val start = 90.0 - i * degs
      val end = 90.0 - (i + 1) * degs
      val color = if (this[i]) "gray" else "white"
      println("\\fill [$color] (0,0) -- ($start:1) arc [end angle=$end, start angle=$start, radius=1] -- cycle;")
    }
    println()
  }
}