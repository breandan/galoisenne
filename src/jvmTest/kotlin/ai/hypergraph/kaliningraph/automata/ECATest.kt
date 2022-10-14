package ai.hypergraph.kaliningraph.automata

import ai.hypergraph.kaliningraph.sampling.findAll
import ai.hypergraph.kaliningraph.sat.*
import org.logicng.formulas.Formula
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.fail

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.automata.ECATest"
*/
class ECATest {
/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.automata.ECATest.testSimpleECA"
*/
  @Test
  fun testSimpleECA() { List(20) { true }.evolve(steps = 100) }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.automata.ECATest.testTypeLevelECA4"
*/
  @Test
  fun testTypeLevelECA4() {
    val init = BVec(T, F, F, F)
    fun BVec.bits() = data.map { it == T }
    var i = 1; (init to init.bits())
    .also { (a, b) -> assertEquals(a.bits(), b) }
    .let { it.first.eca(::r, ::r, ::r, ::r) to it.second.evolve() }
    .also { (a, b) -> assertEquals(a.bits(), b) }
    .let { it.first.eca(::r, ::r, ::r, ::r) to it.second.evolve() }
    .also { (a, b) -> assertEquals(a.bits(), b) }
    .let { it.first.eca(::r, ::r, ::r, ::r) to it.second.evolve() }
    .also { (a, b) -> assertEquals(a.bits(), b) }
    .let { it.first.eca(::r, ::r, ::r, ::r) to it.second.evolve() }
    .also { (a, b) -> assertEquals(a.bits(), b) }
    .let { it.first.eca(::r, ::r, ::r, ::r) to it.second.evolve() }
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
    (2..4).mapNotNull { j ->
      val i = List(64) { i -> BVar("$i") }
      val t = (i matEq i.evolve()).negate() and (i matEq i.evolve(steps = j))
      val sol = t.solve()
      if(sol.isEmpty()) null else i.map { sol[it]!! }.also { println("Looper ($j): $it") } to j
    }.forEach { (bits, j) ->
      assertNotEquals(bits, bits.evolve())
      assertEquals(bits, bits.evolve(steps = j))
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.automata.ECATest.testOrphan"
*/
  @Test
  fun testOrphan() {
    // Can we do better? https://wpmedia.wolfram.com/uploads/sites/13/2018/02/22-4-3.pdf
    findAll(setOf(true, false), 128).first { orphan ->
      val i = List(16) { i -> BVar("$i") }
      val fs = orphan.map { ff.constant(it) }
      val t = (i.evolve() matEq List(16) { fs[it] })
        try { t.solve(); false } catch (e: Exception ) { true}
    }.also { println(it) }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.automata.ECATest.testEndling"
*/
  @Test
  fun testEndling() {
    for (j in 1..3) {
      val i = List(128) { i -> BVar("$i") }
      val t =
        (i.evolve(steps = j) matEq i.evolve(steps = j + 1)).negate() and
          (i.evolve(steps = j + 1) matEq i.evolve(steps = j + 2))
      try {
        val sol = t.solve()
        val bits = i.map { sol[it]!! }.also { println("Endling ($j): $it") }
        assertNotEquals(bits.evolve(steps = j), bits.evolve(steps = j + 1))
        assertEquals(bits.evolve(steps = j + 1), bits.evolve(steps = j + 2))
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
    println(listOf(true, false, false, true).evolve().pretty())
    val i = List(128) { i -> BVar("i$i") }
    val j = List(128) { i -> BVar("j$i") }
    val k = List(128) { i -> BVar("k$i") }
    val neqIJK = (i matEq j).negate() and (j matEq k).negate() and (k matEq i).negate()

    val (fi, fj, fk) =
      Triple(i.evolve(), j.evolve(), k.evolve())

    val cstr = neqIJK and (fi matEq fj) and (fj matEq fk) and
      fk.map { it.negate() }.fold(ff.falsum() as Formula) { a, b -> a.or(b) }

    val sol = cstr.solve()

    val (r, s, t) =
      Triple(i.map { sol[it]!! }, j.map { sol[it]!! }, k.map { sol[it]!! })

    println("r:${r.pretty()}\ns:${s.pretty()}\nt:${t.pretty()}")

    assertNotEquals(r, s)
    assertNotEquals(s, t)
    assertNotEquals(t, r)

    val (fr, fs, ft) =
      Triple(r.evolve(), s.evolve(), t.evolve())
    println("f(r):${fr.pretty()}\nf(s):${fs.pretty()}\nf(t):${ft.pretty()}")

    assertEquals(fr, fs, "f(r) != f(s)")
    assertEquals(fs, ft, "f(s) != f(t)")
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.automata.ECATest.testTargetPatternPredecessor"
*/
  @Test
  fun testTargetPatternPredecessor() {
    val pp = List(16) { i -> BVar("i$i") }
    val p = "1100110111111011".toBitVector()
    val t = pp.evolve() matEq p
    try {
      val sol = t.solve()
      val bits = pp.map { sol[it]!! }.also { println("Predecessor: $it") }
      assertEquals(bits.evolve().map { ff.constant(it) }, p)
    } catch (e: Exception) { println("No predecessor was found!") }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.automata.ECATest.testECAPrint"
*/
  @Test
  fun testECAPrint() {
  val ts = "0100011111100010".map { it == '1' }
    ts.evolve(0).also { it.toRingBuffer() }
      .evolve().also { it.toRingBuffer() }
      .evolve().also { it.toRingBuffer() }
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