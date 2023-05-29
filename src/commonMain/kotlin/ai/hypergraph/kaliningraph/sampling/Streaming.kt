package ai.hypergraph.kaliningraph.sampling

import kotlin.math.pow
import kotlin.random.Random

fun <T> Sequence<T>.reservoirSample(size: Int = 1000, score: (T) -> Double): Sequence<T> {
  // Maintains a sorted list of edits, sorted by score
  class PriorityQueue {
    val edits = mutableListOf<Pair<T, Double>>()

    fun insert(item: T, score: Double) {
      edits.binarySearch { it.second.compareTo(score) }.let { idx ->
        if (idx < 0) edits.add(-idx - 1, item to score)
        else edits.add(idx, item to score)
      }
    }

    fun extractMin(): T? = edits.removeFirstOrNull()?.first

    fun count() = edits.size
  }

  val pq = PriorityQueue()
//  val t = TimeSource.Monotonic.markNow()
  return map { edit ->
    val score = score(edit)
    val r = Random.nextDouble().pow(1f / score)
    pq.insert(edit, r)
    if (pq.count() > size) pq.extractMin() else null
  }.filterNotNull() +
    // Measure time till first sample
//    .onEachIndexed { i, _ -> if (i == 0)
//      println("Time to fill reservoir: ${t.elapsedNow().inWholeMilliseconds}ms")
//    } +
    generateSequence { pq.extractMin() }
}