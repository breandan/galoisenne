package ai.hypergraph.kaliningraph.sat

import ai.hypergraph.kaliningraph.parsing.*
import ai.hypergraph.kaliningraph.repair.multiTokenSubstitutionsAndInsertions
import org.junit.jupiter.api.Test
import kotlin.math.pow
import kotlin.system.measureTimeMillis


/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.SATValiantBenchmarks"
*/
class SATValiantBenchmarks {
/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.SATValiantBenchmarks.testDyckBenchmark"
*/
  @Test
  fun testDyckBenchmark() {
    val dyck1 = """S -> ( ) | ( S ) | S S""".parseCFG()
    val dyck2 = """S -> ( ) | [ ] | ( S ) | [ S ] | S S""".parseCFG()
    val dyck3 = """S -> ( ) | [ ] | { } | ( S ) | [ S ] | { S } | S S""".parseCFG()
    val dyck4 = """S -> ( ) | [ ] | { } | < > | < S > | ( S ) | [ S ] | { S } | S S""".parseCFG()

    fun List<Double>.stddev() = average().let { μ -> map { (it - μ).pow(2) } }.average().pow(0.5)

    fun String.dropHoles(i: Int = 4, idxs: Set<Int> = indices.shuffled().take(i).toSet()) =
      split(' ').mapIndexed { i, it -> if (i in idxs) "_" else it }.joinToString(" ")

    val numSamples = 20
    var data = "holes, d1, d1err, d2, d2err, d3, d3err, d4, d4err"
    for(holes in setOf(6, 8, 10, 12, 14, 16)) {
      data += "\n$holes"
      setOf(dyck1, dyck2, dyck3, dyck4).forEach { cfg ->
        val str =
          List(50) { "_" }.joinToString(" ").synthesizeIncrementally(cfg, allowNTs = false).take(30).toList()
        (0..numSamples).map {
          measureTimeMillis {
            str.random().dropHoles(holes).synthesizeIncrementally(cfg, allowNTs = false).take(1).toList()
          }.toDouble()
        }.let { data += ", " + it.average() + ", " + it.stddev() }
      }
    }

    println(data)
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.SATValiantBenchmarks.testDyckRepairBenchmark"
*/
  @Test
  fun testDyckRepairBenchmark() {
    val dyck1 = """S -> ( ) | ( S ) | S S""".parseCFG()
    val dyck2 = """S -> ( ) | [ ] | ( S ) | [ S ] | S S""".parseCFG()
    val dyck3 = """S -> ( ) | [ ] | { } | ( S ) | [ S ] | { S } | S S""".parseCFG()
    val dyck4 = """S -> ( ) | [ ] | { } | < > | < S > | ( S ) | [ S ] | { S } | S S""".parseCFG()

    fun List<Double>.stddev(): Double =
      average().let { μ -> map { (it - μ).pow(2) } }.average().pow(0.5)

    fun String.makeError(i: Int = 4, idxs: Set<Int> = indices.shuffled().take(i).toSet()): String =
      split(' ').mapIndexed { i, it -> if (i in idxs) "" else it }.joinToString(" ")

    val numSamples = 10
    var data = "errors, d1, d1err, d2, d2err, d3, d3err, d4, d4err"
    for (errors in setOf(1, 2, 3)) {
      data += "\n$errors"
      setOf(dyck1, dyck2, dyck3, dyck4).forEach { cfg ->
        val str =
          List(50) { "_" }.joinToString(" ").synthesizeIncrementally(cfg, allowNTs = false).take(30).toList()
        (0..numSamples).map {
          measureTimeMillis {
            val bad = str.random().makeError(errors)

            bad.synthesizeIncrementally(
              cfg = cfg,
              variations = listOf { a, b ->
                bad.multiTokenSubstitutionsAndInsertions(
                  numberOfEdits = 3,
                  exclusions = b,
                )
              }
            ).also { println(it.take(1).toList()) }
          }.toDouble()
        }.let { data += ", " + it.average() + ", " + it.stddev(); println(data) }
      }
    }

    println(data)
  }
}