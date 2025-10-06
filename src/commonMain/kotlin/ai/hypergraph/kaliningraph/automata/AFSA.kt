package ai.hypergraph.kaliningraph.automata

import ai.hypergraph.kaliningraph.KBitSet
import ai.hypergraph.kaliningraph.parsing.Bindex
import ai.hypergraph.kaliningraph.parsing.Σᐩ
import ai.hypergraph.kaliningraph.types.Π3A
import kotlin.time.TimeSource

// Acyclic finite state automaton
class AFSA(override val Q: TSA, override val init: Set<Σᐩ>, override val final: Set<Σᐩ>): FSA(Q, init, final) {
  fun topSort(): List<Σᐩ> {
    // 1) Build adjacency lists (only next-states) from `transit`.
    //    We also need to track in-degrees of each state.
    val adjacency = mutableMapOf<Σᐩ, MutableList<Σᐩ>>()
    val inDegree  = mutableMapOf<Σᐩ, Int>()

    // Initialize adjacency and inDegree for all states
    for (s in states) {
      adjacency[s] = mutableListOf()
      inDegree[s]  = 0
    }

    // Fill adjacency and in-degree
    for ((fromState, outEdges) in transit) {
      // outEdges is a list of (symbol, toState) pairs
      for ((_, toState) in outEdges) {
        adjacency[fromState]!!.add(toState)
        inDegree[toState] = inDegree[toState]!! + 1
      }
    }

    // 2) Collect all states with in-degree 0 into a queue
    val zeroQueue = ArrayDeque<Σᐩ>()
    for ((st, deg) in inDegree) if (deg == 0) zeroQueue.add(st)

    // 3) Repeatedly pop from queue, and decrement in-degree of successors
    val result = mutableListOf<Σᐩ>()
    while (zeroQueue.isNotEmpty()) {
      val s = zeroQueue.removeFirst()
      result.add(s)

      for (next in adjacency[s]!!) {
        val d = inDegree[next]!! - 1
        inDegree[next] = d
        if (d == 0) {
          zeroQueue.add(next)
        }
      }
    }

    // 4) The 'result' is our topological ordering.
    return result
  }

  /** See [FSA.intersectPTree] for why this is needed*/
  override val stateLst by lazy {
    // Since the FSA is acyclic, we can use a more efficient topsort -
    // This trick will only work for Levenshtein FSAs (otherwise use topSort())
//    states.groupBy { it.coords().let { (a, b) -> a + b } }.values.flatten()
//    var flip = true
    states.groupBy { it.coords().let { (a, b) -> a + b } }.values.flatten()
//    topSort()
//      .also {
//      if (it.size != states.size)
//        throw Exception("Contained ${states.size} but ${it.size} topsorted indices:\n" +
//            "T:${Q.joinToString("") { (a, b, c) -> ("($a -[$b]-> $c)") }}\n" +
//            "V:${graph.vertices.map { it.label }.sorted().joinToString(",")}\n" +
//            "Q:${Q.states().sorted().joinToString(",")}\n" +
//            "S:${states.sorted().joinToString(",")}"
//        )
//    }
  }

  // Assumes stateLst is already in topological order:
  override val allPairs: List<List<List<Int>?>> by lazy {
    val fwdAdj = Array(numStates) { mutableListOf<Int>() }
    val revAdj = Array(numStates) { mutableListOf<Int>() }

    for ((fromLabel, _, toLabel) in Q) {
      val i = stateMap[fromLabel]!!
      val j = stateMap[toLabel]!!
      fwdAdj[i].add(j)
      revAdj[j].add(i)
    }

    // 1) Prepare KBitSets for post[] and pre[]
    val post = Array(numStates) { KBitSet(numStates) }
    val pre  = Array(numStates) { KBitSet(numStates) }

    // 2) Compute post[i] in reverse topological order
    for (i in (numStates - 1) downTo 0) {
      post[i].set(i)
      for (k in fwdAdj[i]) post[i].or(post[k])
    }

    // 3) Compute pre[i] in forward topological order
    for (i in 0..<numStates) {
      pre[i].set(i)
      for (p in revAdj[i]) pre[i].or(pre[p])
    }

    // 4) Build allPairs by intersecting post[i] and pre[j]
    //    We can skip the intersection if j not reachable from i,
    //    i.e. if post[i].get(j) == false => empty set.
    //
    //    We'll reuse a single KBitSet 'tmp' to avoid allocations:
    val result: List<MutableList<List<Int>?>> = List(states.size) { MutableList(states.size) { null } }

    for (i in 0..<numStates) for (j in i + 1..<numStates)
      when {
        !post[i].get(j) -> { }
        // i < j and j is reachable from i => do the intersection of post[i] & pre[j].
        else -> result[i][j] = KBitSet(numStates).apply { or(post[i]); and(pre[j]) }.toList()
      }

    result
  }

  override val midpoints: List<List<List<Int>>> by lazy {
    val t0 = TimeSource.Monotonic.markNow()
    val fwdAdj = Array(numStates) { mutableListOf<Int>() }
    val revAdj = Array(numStates) { mutableListOf<Int>() }

    adjList.asList().windowed(2, 2).forEach { (i, j) ->
      fwdAdj[i].add(j)
      revAdj[j].add(i)
    }

    // 1) Prepare KBitSets for post[] and pre[]
    val post = Array(numStates) { KBitSet(numStates) }
    val pre  = Array(numStates) { KBitSet(numStates) }

    // 2) Compute post[i] in reverse topological order
    for (i in (numStates - 1) downTo 0) {
      post[i].set(i)
      for (k in fwdAdj[i]) post[i].or(post[k])
    }

    // 3) Compute pre[i] in forward topological order
    for (i in 0..<numStates) {
      pre[i].set(i)
      for (p in revAdj[i]) pre[i].or(pre[p])
    }

    // 4) Build allPairs by intersecting post[i] and pre[j]
    //    We can skip the intersection if j not reachable from i,
    //    i.e. if post[i].get(j) == false => empty set.
    //
    //    We'll reuse a single KBitSet 'tmp' to avoid allocations:
    val result: List<MutableList<List<Int>>> = List(states.size) { MutableList(states.size) { mutableListOf() } }

    for (i in 0..<numStates) for (j in i + 1..<numStates)
      when {
        !post[i].get(j) -> { }
        // i < j and j is reachable from i => do the intersection of post[i] & pre[j].
        else -> result[i][j] = KBitSet(numStates).apply { or(post[i]); and(pre[j]) }.toList()
      }

    println("Computed midpoints in ${t0.elapsedNow()}")
    result
  }
}