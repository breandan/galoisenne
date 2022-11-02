package ai.hypergraph.kaliningraph.automata

import ai.hypergraph.kaliningraph.sampling.findAll
import ai.hypergraph.kaliningraph.sat.*
import org.logicng.formulas.Formula
import kotlin.test.*

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.automata.ECATest"
*/
class ECATest {
/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.automata.ECATest.testSimpleECA"
*/
  @Test
  fun testSimpleECA() { BooleanArray(20) { true }.evolve(steps = 100) }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.automata.ECATest.testTypeLevelECA4"
*/
  @Test
  fun testTypeLevelECA4() {
    val init = BVec(T, F, F, F)
    fun BVec.bits() = data.map { it == T }.toBooleanArray()
    (init to init.bits())
    .also { (a, b) -> assertContentEquals(a.bits(), b) }
    .let { it.first.eca(::r, ::r, ::r, ::r) to it.second.evolve() }
    .also { (a, b) -> assertContentEquals(a.bits(), b) }
    .let { it.first.eca(::r, ::r, ::r, ::r) to it.second.evolve() }
    .also { (a, b) -> assertContentEquals(a.bits(), b) }
    .let { it.first.eca(::r, ::r, ::r, ::r) to it.second.evolve() }
    .also { (a, b) -> assertContentEquals(a.bits(), b) }
    .let { it.first.eca(::r, ::r, ::r, ::r) to it.second.evolve() }
    .also { (a, b) -> assertContentEquals(a.bits(), b) }
    .let { it.first.eca(::r, ::r, ::r, ::r) to it.second.evolve() }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.automata.ECATest.testTypeLevelECA10"
*/
  @Test
  fun testTypeLevelECA10() {
    val init = BVec(T, F, F, F, T, F, F, F, F, F)
    fun BVec.bits() = data.map { it == T }.toBooleanArray()
    var i = 1; init
      .eca(::r, ::r, ::r, ::r,::r, ::r, ::r, ::r,::r, ::r)
      .also { assertContentEquals(it.bits(), init.bits().evolve(steps = i++)) }
      .eca(::r, ::r, ::r, ::r,::r, ::r, ::r, ::r,::r, ::r)
      .also { assertContentEquals(it.bits(), init.bits().evolve(steps = i++)) }
      .eca(::r, ::r, ::r, ::r,::r, ::r, ::r, ::r,::r, ::r)
      .also { assertContentEquals(it.bits(), init.bits().evolve(steps = i++)) }
      .eca(::r, ::r, ::r, ::r,::r, ::r, ::r, ::r,::r, ::r)
      .also { assertContentEquals(it.bits(), init.bits().evolve(steps = i++)) }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.automata.ECATest.testLooper"
*/
  @Test
  fun testLooper() {
    (2..4).mapNotNull { j ->
      val i = BVecVar(64) { i -> "$i" }
      val t = (i matEq i.evolve()).negate() and (i matEq i.evolve(steps = j))
      val sol = t.solve()
      if (sol.isEmpty()) null else i.map { sol[it]!! }.toBooleanArray()
          .also { println("Looper ($j): ${it.pretty()}") } to j
    }.forEach { (bits, j) ->
      assertNotEquals(bits, bits.evolve())
      assertContentEquals(bits, bits.evolve(steps = j))
    }
  }

  fun SATVector.isOrphan(): Boolean =
    (BVecVar(size).evolve() matEq this).solve().isEmpty()

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.automata.ECATest.testOrphan"
*/
  @Test
  fun testOrphan() {
    // Can we do better? https://wpmedia.wolfram.com/uploads/sites/13/2018/02/22-4-3.pdf
    val size = 128

    findAll(setOf(true, false), size).first { orphan -> BVecLit(orphan).isOrphan() }
      .toBooleanArray().also { println(it.pretty()) }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.automata.ECATest.testEndling"
*/
  @Test
  fun testEndling() {
    for (j in 1..3) {
      val i = BVecVar(128) { i -> "$i" }
      val t =
        (i.evolve(steps = j) matEq i.evolve(steps = j + 1)).negate() and
          (i.evolve(steps = j + 1) matEq i.evolve(steps = j + 2))

      val sol = t.solve()
      if (sol.isEmpty()) { println("No solutions in $j steps"); continue }
      val bits = i.map { sol[it]!! }.toBooleanArray()
          .also { println("Endling ($j): ${it.pretty()}") }
      assertNotEquals(bits.evolve(steps = j), bits.evolve(steps = j + 1))
      assertContentEquals(bits.evolve(steps = j + 1), bits.evolve(steps = j + 2))
    }
  }

  fun BooleanArray.pretty(): String = joinToString("") { if (it) "1" else "0" }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.automata.ECATest.testHWQ1"
*/
  @Test
  fun testHWQ1() {
    val r = "10100000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001101110110111010000011001"
    val s = "10100000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001101110110100110000011001"
    val t = "01100000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001101001101100110000011001"
    assertContentEquals(r.toBitVector().evolve(), s.toBitVector().evolve())
    assertContentEquals(s.toBitVector().evolve(), t.toBitVector().evolve())
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.automata.ECATest.testHWQ2"
*/
  @Test
  fun testHWQ2() {
    val t = "10000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"
    assertTrue(t.toBitVector().isOrphan())
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.automata.ECATest.testChimera"
*/
  @Test
  fun testChimera() {
    println(booleanArrayOf(true, false, false, true).evolve().pretty())
    val i = BVecVar(128) { i -> "i$i" }
    val j = BVecVar(128) { j -> "j$j" }
    val k = BVecVar(128) { k -> "k$k" }
    val neqIJK = (i matEq j).negate() and (j matEq k).negate() and (k matEq i).negate()

    val (fi, fj, fk) =
      Triple(i.evolve(), j.evolve(), k.evolve())

    val cstr = neqIJK and (fi matEq fj) and (fj matEq fk) and
      fk.map { it.negate() }.fold(ff.falsum() as Formula) { a, b -> a.or(b) }

    val sol = cstr.solve()

    val (r, s, t) =
      Triple(
        i.map { sol[it]!! }.toBooleanArray(),
        j.map { sol[it]!! }.toBooleanArray(),
        k.map { sol[it]!! }.toBooleanArray()
      )

    println("r:${r.pretty()}\ns:${s.pretty()}\nt:${t.pretty()}")

    assertNotEquals(r, s)
    assertNotEquals(s, t)
    assertNotEquals(t, r)

    val (fr, fs, ft) =
      Triple(r.evolve(), s.evolve(), t.evolve())
    println("f(r):${fr.pretty()}\nf(s):${fs.pretty()}\nf(t):${ft.pretty()}")

    assertContentEquals(fr, fs, "f(r) != f(s)")
    assertContentEquals(fs, ft, "f(s) != f(t)")
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.automata.ECATest.testTargetPatternPredecessor"
*/
  @Test
  fun testTargetPatternPredecessor() {
    val p = "00000000000000000000000000000000000111100000000000000000000000000000000000000000000000000000000000111111111000000000000000000000".toBitVector()
    val pp = BVecVar(p.size) { i -> "i$i" }
    val t = pp.evolve() matEq p
    val sol = t.solve()
    if (sol.isEmpty()) { println("No predecessor was found!"); return }
    val bits = pp.map { sol[it]!! }.toBooleanArray().also { println("Predecessor: ${it.pretty()}") }
    assertContentEquals(bits.evolve().map { ff.constant(it) as Formula }.toTypedArray(), p)
  }

  /*
  ./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.automata.ECATest.testECAPrint"
  */
  @Test
  fun testECAPrint() {
    val ts = "0100011111100010".map { it == '1' }.toBooleanArray()
    ts.evolve(0).also { it.toRingBuffer() }
      .evolve().also { it.toRingBuffer() }
      .evolve().also { it.toRingBuffer() }
  }

  fun BooleanArray.toRingBuffer() {
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