package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.repair.MAX_TOKENS
import ai.hypergraph.kaliningraph.tokenizeByWhitespace
import ai.hypergraph.kaliningraph.types.cache
import kotlin.math.*

// Number of each terminal (necessary, possible)
typealias ParikhBounds = Map<Σᐩ, IntRange>
typealias ParikhVector = Map<Σᐩ, Int>

fun Σᐩ.parikhVector(): ParikhVector = tokenizeByWhitespace().parikhVector()
fun List<Σᐩ>.parikhVector(): ParikhVector = groupingBy { it }.eachCount()

fun ParikhBounds.subsumes(pv: ParikhVector) =
  pv.all { (k, v) ->
//    println("$this subsumes? $k:$v")
    val g = get(k)?.contains(v)
//    ?.let { (it.first - 1)..(it.last + 1) }
//    if (g != true) println("$k:${get(k)} !subsume $k:$v")
    g ?: false }//.also { println("Complete subsumption: $it") }

fun CFG.parikhBounds(nt: Σᐩ, size: Int): ParikhBounds {
  val bounds = mutableMapOf<Σᐩ, IntRange>()
  nonterminalProductions.forEach { (A, rhs) ->
    rhs.forEach { σ ->
      bounds[σ] = bounds[σ]?.let { it.first..it.last + 1 } ?: 0..1
    }
  }
  return bounds
}

class ParikhMap(val cfg: CFG, val size: Int) {
  private val lengthBounds: MutableMap<Int, Set<Σᐩ>> = mutableMapOf()
  private val parikhMap: MutableMap<Int, Map<Σᐩ, ParikhBounds>> = mutableMapOf()

  init {
    val template = List(size) { "_" }
    cfg.initPForestMat(template).seekFixpoint().diagonals
      .forEachIndexed { i, it ->
        lengthBounds[i+1] = it.first().keys
        parikhMap[i+1] = it.first().mapValues { it.value.parikhBounds }
      }
  }

  fun parikhBounds(nt: Σᐩ, size: Int): ParikhBounds? = parikhMap[size]?.get(nt)
//    parikhMap.also { println("Keys (${nt}): " + it.keys.size + ", ${it[size]?.get(nt)}") }[size]?.get(nt)

  override fun toString(): String =
    (parikhMap.size..parikhMap.size).joinToString("\n") { i ->
      "\n\nLength $i:\n\n${parikhMap[i]?.entries?.joinToString("\n") 
      { (k, v) -> "$k:$v" }}"
    }
}

infix fun IntRange.merge(other: IntRange) =
  minOf(start, other.start)..maxOf(endInclusive, other.endInclusive)

operator fun ParikhBounds.plus(other: ParikhBounds) =
  (keys + other.keys).associateWith {
    (get(it) ?: 0..0) merge (other[it] ?: 0..0)
  }

operator fun ParikhBounds.times(other: ParikhBounds) =
  (keys + other.keys).associateWith {
    (get(it) ?: 0..0) join (other[it] ?: 0..0)
  }

infix fun IntRange.join(other: IntRange) =
  (start + other.start)..(endInclusive + other.endInclusive)