package ai.hypergraph.kaliningraph.tensor

// An n-dimensional count-based histogram

class NDTable(val binning: (Array<out Any>) -> Array<out Any> = { it }) {
  // TODO: should we use the last dimension for counts or the tail value of key
  val map: MutableMap<Array<out Any>, Int> = mutableMapOf()
  val marginals: MutableMap<Any, Int> = mutableMapOf()

  // TODO: curry? NDTables composed of NDTables?
  operator fun get(vararg indices: Any): Int = map.getOrPut(indices) { 0 }

  operator fun plus(other: NDTable): NDTable = TODO()

  // TODO: need to discretize into an unnormalized histogram
  fun print(): String = TODO()

  operator fun set(vararg indices: Any, int: Int) {
    val diff = int - this[indices]
    map[indices] = int
    indices.forEach { i -> marginals.compute(i) { _, v -> (v ?: 0) + diff } }
  }
}

fun main() {
  val d = NDTable()

  d[1, "b", 3.0]++
  d[3, "a", 2.2]++
  d[2, "a", 2.1]++
  d[1, "b", 2.1]++
  //...

  d.print()
}
