package ai.hypergraph.markovian.mcmc

import ai.hypergraph.kaliningraph.cache.LRUCache
import ai.hypergraph.kaliningraph.sampling.*
import ai.hypergraph.markovian.*
import ai.hypergraph.markovian.concurrency.*
import org.apache.datasketches.frequencies.ItemsSketch
import org.apache.datasketches.frequencies.ErrorType.NO_FALSE_POSITIVES
import org.jetbrains.kotlinx.multik.api.*
import org.jetbrains.kotlinx.multik.ndarray.data.*
import org.jetbrains.kotlinx.multik.ndarray.operations.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.*
import kotlin.random.Random

/**
 * Marginalizes/sums out all dimensions not contained in [dims],
 * producing a rank-(dims.size) tensor consisting of [dims].
 */
fun NDArray<Double, DN>.sumOnto(vararg dims: Int = intArrayOf(0)) =
  (0 until dim.d).fold(this to 0) { (t, r), b ->
    if (b in dims) t to r + 1
    else mk.math.sum<Double, DN, DN>(t, r) to r
  }.first

/**
 * Each entry in [dimToIdx] represents a unique N-1 dimensional hyperplane,
 * which intersect to form a rank-(N-dimToIdx.size) tensor.
 */
fun NDArray<Double, DN>.disintegrate(dimToIdx: Map<Int, Int>): NDArray<Double, DN> =
// TODO: Is this really disintegration or something else?
// http://www.stat.yale.edu/~jtc5/papers/ConditioningAsDisintegration.pdf
  // https://en.wikipedia.org/wiki/Disintegration_theorem
  (0 until dim.d).fold(this to 0) { (t, r), b ->
    if (b in dimToIdx) t.view(dimToIdx[b]!!, r).asDNArray() to r
    else t to r + 1
  }.first

fun <T> Sequence<T>.toMarkovChain(memory: Int = 3) =
  MarkovChain(train = this, memory = memory)

// One-to-one hashmap of Ts to indices
class Bijection<T>(
  val list: List<T>,
  val map: Map<T, Int> = list.zip(list.indices).toMap(),
  val rmap: Map<Int, T> = map.entries.associate { (k, v) -> v to k }
) : Map<T, Int> by map {
  // 𝒪(1) surrogate for List<T>.indexOf(...)
  operator fun get(key: Int): T = rmap[key]!!
  override fun get(key: T): Int = map[key]!!
  operator fun contains(value: Int) = value in rmap
}

// Maximum number of unique Ts
val maxUniques: Int = 2000

// TODO: Support continuous state spaces?
// https://www.colorado.edu/amath/sites/default/files/attached-files/2_28_2018.pdf
// https://en.wikipedia.org/wiki/Variable-order_Markov_model
open class MarkovChain<T>(
  train: Sequence<T> = sequenceOf(),
  val memory: Int = 3,
  val counter: Counter<T> = Counter(train, memory)
) {
  private val mgr = ResettableLazyManager()

  private val dictionary: Bijection<T> by resettableLazy(mgr) {
    counter.rawCounts.getFrequentItems(NO_FALSE_POSITIVES)
      // Is taking maxTokens-most frequent unigrams always the right choice?
      .take(maxUniques).map { it.item }.let { Bijection(it) }
  }

  val size: Int by resettableLazy(mgr) { dictionary.size }

  /**
   * Transition tensor representing the probability of observing
   * a subsequence t₁t₂...tₙ, i.e.:
   *
   * P(T₁=t₁,T₂=t₂,…,Tₙ=tₙ) = P(Tₙ=tₙ|Tₙ₋₁=tₙ₋₁, Tₙ₋₂=tₙ₋₂, …,T₁=t₁)
   *
   * Where the tensor rank n=[memory], T₁...ₙ are random variables
   * and t₁...ₙ are their concrete instantiations. This tensor is
   * a hypercube with shape [size]ⁿ, indexed by [dictionary].
   */

  val tt: NDArray<Double, DN> by resettableLazy(mgr) {
    mk.dnarray<Double, DN>(IntArray(memory) { size }) { 0.0 }.asDNArray()
      .also { mt: NDArray<Double, DN> ->
        counter.memCounts.getFrequentItems(NO_FALSE_POSITIVES)
          .map { it.item to it.estimate.toInt() }
          .filter { (item, _) -> item.all { it in dictionary } }
          .forEach { (item, count) ->
            val idx = item.map { dictionary[it] }.toIntArray()
            if (idx.size == memory) mt[idx] = count.toDouble()
          }
      }.let { it / it.sum() } // Normalize across all entries in tensor
    // TODO: May be possible to precompute fiber/slice PMFs via tensor renormalization?
    // https://mathoverflow.net/questions/393427/generalization-of-sinkhorn-s-theorem-to-stochastic-tensors
    // https://arxiv.org/pdf/1702.08142.pdf
    // TODO: Look into copulae
    // https://en.wikipedia.org/wiki/Copula_(probability_theory)
  }

  fun topK(k: Int = 10): List<T> =
    counter.rawCounts.getFrequentItems(NO_FALSE_POSITIVES).take(k).map { it.item }

  // TODO: mergeable cache?
  // Maps the coordinates of a transition tensor fiber to a memoized distribution
  val dists: LRUCache<List<Int>, Dist> = LRUCache()

  // Computes perplexity of a sequence normalized by sequence length
  fun score(seq: List<T>): Double =
    seq.windowed(memory)
      .map { get(*it.mapIndexed { i, t -> i to t }.toTypedArray()).coerceAtLeast(0.00000001) }
      .sumOf { -ln(it) }

  operator fun get(vararg variables: T?): Double =
    get(*variables.mapIndexed { i, t -> i to t }.toTypedArray())

  operator fun get(vararg variables: Pair<Int, T?>): Double =
    variables.associate { (a, b) -> a to b }
      .let { map -> (0 until memory).map { map[it] } }.let {
        counter.nrmCounts.getEstimate(it).toDouble() / counter.total.toDouble()
      }

  // https://www.cs.utah.edu/~jeffp/papers/merge-summ.pdf
  operator fun plus(mc: MarkovChain<T>) =
    MarkovChain<T>(memory = memory, counter = counter + mc.counter)

  /**
   * TODO: construct [Dist] using precomputed normalization constants [Counter.nrmCounts]
   */
  fun sample(
    seed: () -> T = {
      dictionary[Dist(tt.sumOnto().toList(), normConst=counter.total.toDouble()).sample()]
    },
    next: (T) -> T = { t: T ->
      val pmf = tt.view(dictionary[t]).asDNArray().sumOnto().toList()
      // P(Tₙ | Tₙ₋₁ = t) := P([t, *, *, ...])
      // val conditional = List(memory) { if (it == 0) t else null }
      // Precompute normalization constant Σ(Tₙ | Tₙ₋₁) in norm sketch
      // val nrmConst = counter.nrmCounts.getEstimate(conditional).toDouble()
      dictionary[Dist(pmf/*, normConst = TODO()*/).sample()]
    },
    memSeed: () -> Sequence<T> = {
      // TODO: Seed the chain properly:
      //  P(Tₙ) -> P(Tₙ | Tₙ₋₁)
      //  P(Tₙ | Tₙ₋₁ = t) -> P(Tₙ | Tₙ₋₁, Tₙ₋₂)
      //  P(Tₙ | Tₙ₋₁, Tₙ₋₂) -> P(Tₙ | Tₙ₋₁, Tₙ₋₂, Tₙ₋₃)
      //  ...
      //  P(Tₙ | Tₙ₋₁, Tₙ₋₂, Tₙ₋₃, ..., T_{n-[memory-1]})
      // Currently implementation: P(Tₙ) x [memory]
      generateSequence(seed, next).take(memory - 1)
    },
    memNext: (Sequence<T>) -> (Sequence<T>) = { ts ->
      val idxs = ts.map { dictionary[it] }.toList()
      val dist = dists.getOrPut(idxs) {
        // seems to work? I wonder why we don't need to use multiplication
        // to express conditional probability? Just disintegration?
        // https://blog.wtf.sg/posts/2021-03-14-smoothing-with-backprop
        // https://homes.sice.indiana.edu/ccshan/rational/disintegrator.pdf
        val slices = idxs.indices.zip(idxs).toMap()
        // Intersect conditional slices to produce a 1D count fiber
        val intersection = tt.disintegrate(slices).toList()
        // Turns 1D count fiber into a probability vector
        val normConst = counter.nrmCounts.getEstimate(ts.toList() + null).toDouble() /
          counter.total.toDouble()
        // val pmfSum = intersection.sum()
        // println("normConst: $normConst pmfSum: $pmfSum total: ${counter.total.toDouble()}")
        Dist(intersection, normConst=normConst)
//        Dist(intersection)
      }

      ts.drop(1) + dictionary[dist.sample()]
    }
  ) = generateSequence(memSeed, memNext).map { it.last() }

  /**
   * Treats each subsequence of length-[memory] as a single token
   * and counts how many times it occurs in the sequence using
   * the Count-min sketch implemented by [ItemsSketch].
   */
  class Counter<T>(
    toCount: Sequence<T> = sequenceOf(),
    val memory: Int,
    val total: AtomicInteger = AtomicInteger(0),
    val rawUniques: Int = 2.pow(log2(maxUniques) + 5),
    val nrmUniques: Int = 2.pow(log2(memory * maxUniques) + 8),
    val memUniques: Int = 2.pow(log2(memory * maxUniques) + 2),
    // Counts unique instances of T
    val rawCounts: ItemsSketch<T> = ItemsSketch(rawUniques),
    /**
     * Precomputes the normalizing constants for all multivariate
     * marginals of a length-[memory] sequence, e.g., for the
     * sequence 'abc', we need to increment the normalizing
     * constants for all the following eight marginals:
     *
     *  P[a * *] += 1    P[a b *] += 1    P[a b c] += 1
     *  P[* b *] += 1    P[* b c] += 1    P[* * *] += 1
     *  P[* * c] += 1    P[a * c] += 1
     *
     *  In general, requires O(2ⁿ) ops for a length-n sequence.
     */
    val nrmCounts: ItemsSketch<List<T?>> = ItemsSketch(nrmUniques),
    // Counts unique subsequences of Ts up to length memory
    val memCounts: ItemsSketch<List<T>> = ItemsSketch<List<T>>(memUniques)
  ) {
    // Walks [toCount] counting Hamming subspaces of sequences up to length [memory]
    init {
      toCount.windowed(memory, 1).forEach { buffer: List<T> ->
        total.incrementAndGet()
        buffer.let { memCounts.update(it) }
        buffer.forEach { rawCounts.update(it) }
        buffer.allMasks().forEach { nrmCounts.update(it) }
      }
    }

    operator fun plus(other: Counter<T>) =
      Counter(
        memory = minOf(memory, other.memory),
        total = AtomicInteger(total.toInt() + other.total.toInt()),
        rawCounts = rawCounts.merge(other.rawCounts),
        nrmCounts = nrmCounts.merge(other.nrmCounts),
        memCounts = memCounts.merge(other.memCounts)
      )
  }
}

class Dist(
  counts: Collection<Number>,
  val normConst: Double = counts.sumOf { it.toDouble() },
  // https://en.wikipedia.org/wiki/Probability_mass_function
  val pmf: List<Double> = counts.map { i -> i.toDouble() / normConst },
  // https://en.wikipedia.org/wiki/Cumulative_distribution_function
  val cdf: List<Double> = pmf.runningReduce { acc, d -> d + acc }
) {
  private val U = DoubleArray(pmf.size) // Probability table
  private val K = IntArray(pmf.size) { it } // Alias table

  //  https://en.wikipedia.org/wiki/Alias_method#Table_generation
  init {
    assert(pmf.isNotEmpty())
    val n = pmf.size

    val (underfull, overfull) = ArrayList<Int>() to ArrayList<Int>()
    pmf.forEachIndexed { i, prob ->
      U[i] = n * prob
      (if (U[i] < 1.0f) underfull else overfull).add(i)
    }

    while (underfull.isNotEmpty() && overfull.isNotEmpty()) {
      val (under, over) = underfull.removeLast() to overfull.removeLast()
      K[under] = over
      U[over] = (U[over] + U[under]) - 1.0f
      (if (U[over] < 1.0f) underfull else overfull).add(over)
    }
  }

  // Default sampler
  fun sample() = aliasSample()

  // Computes KS-transform using binary search
  fun bsSample(
    rng: Random = Random.Default,
    target: Double = rng.nextDouble()
  ): Int = cdf.binarySearch { it.compareTo(target) }
    .let { if (it < 0) abs(it) - 1 else it }

  fun aliasSample(
    rng: Random = Random.Default,
    i: Int = rng.nextInt(K.size)
  ): Int = if (rng.nextDouble() < U[i]) i else K[i]
}
