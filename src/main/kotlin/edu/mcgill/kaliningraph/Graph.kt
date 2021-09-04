package edu.mcgill.kaliningraph

import edu.mcgill.kaliningraph.matrix.*
import edu.mcgill.kaliningraph.typefamily.*
import org.ejml.kotlin.*
import kotlin.math.sqrt
import kotlin.random.Random

abstract class Graph<G, E, V>(override val vertices: Set<V> = setOf()):
  IGraph<G, E, V>,
  Set<V> by vertices,
  (V) -> Set<V> by { it: V -> it.neighbors }
  where G: Graph<G, E, V>, E: Edge<G, E, V>, V: Vertex<G, E, V> {
  /* (A')ⁿ[a, b] counts the number of walks between vertices a, b of
   * length n. Let i be the smallest natural number such that (A')ⁱ
   * has no zeros. i is the length of the longest shortest path in G.
   */

  tailrec fun slowDiameter(i: Int = 1, walks: BSqMat = A_AUG): Int =
    if (walks.isFull) i
    else slowDiameter(i = i + 1, walks = walks * A_AUG)

  // Based on Booth & Lipton (1981): https://doi.org/10.1007/BF00264532

  tailrec fun diameter(i: Int = 1, prev: BSqMat = A_AUG, next: BSqMat = prev): Int =
    if (next.isFull) slowDiameter(i / 2, prev)
    else diameter(i = 2 * i, prev = next, next = next * next)

  /* Weisfeiler-Lehman isomorphism test:
   * http://www.jmlr.org/papers/volume12/shervashidze11a/shervashidze11a.pdf#page=6
   * http://davidbieber.com/post/2019-05-10-weisfeiler-lehman-isomorphism-test/
   * https://breandan.net/2020/06/30/graph-computation/#weisfeiler-lehman
   */

  tailrec fun wl(k: Int = 5, label: (V) -> Int = { histogram[it]!! }): Map<V, Int> {
    val updates = associateWith { this(it).map(label).sorted().hashCode() }
    return if (k <= 0 || all { label(it) == updates[it] }) updates
    else wl(k - 1) { updates[it]!! }
  }

  /* Graph-level GNN implementation
   * https://www.cs.mcgill.ca/~wlh/grl_book/files/GRL_Book-Chapter_5-GNNs.pdf#page=6
   * H^t := σ(AH^(t-1)W^(t) + H^(t-1)W^t)
   *
   * TODO:
   *   Pooling: https://www.cs.mcgill.ca/~wlh/grl_book/files/GRL_Book-Chapter_5-GNNs.pdf#page=18
   *   Convolution: https://arxiv.org/pdf/2004.03519.pdf#page=2
   */

  tailrec fun gnn(
    // Message passing rounds
    t: Int = diameter() * 10,
    // Matrix of node representations ℝ^{|V|xd}
    H: SpsMat = ENCODED,
    // (Trainable) weight matrix ℝ^{dxd}
    W: SpsMat = randomMatrix(H.numCols),
    // Bias term ℝ^{dxd}
    b: SpsMat = randomMatrix(size, H.numCols),
    // Nonlinearity ℝ^{*} -> ℝ^{*}
    σ: (SpsMat) -> SpsMat = ACT_TANH,
    // Layer normalization ℝ^{*} -> ℝ^{*}
    z: (SpsMat) -> SpsMat = NORM_AVG,
    // Message ℝ^{*} -> ℝ^{*}
    m: Graph<G, E, V>.(SpsMat) -> SpsMat = { σ(z(A * it * W + it * W + b)) }
  ): SpsMat = if(t == 0) H else gnn(t = t - 1, H = m(H), W = W, b = b)

  // https://en.wikipedia.org/wiki/Barab%C3%A1si%E2%80%93Albert_model#Algorithm
  tailrec fun prefAttach(graph: G = this as G, vertices: Int = 1, degree: Int = 3): G =
    if (vertices <= 0) graph
    else prefAttach(graph.attachRandomT(degree), vertices - 1, degree)

  override fun equals(other: Any?) =
    super.equals(other) || (other as? G)?.isomorphicTo(this as G) ?: false

  override fun hashCode() =
    if (isEmpty()) super.hashCode() else wl().values.sorted().hashCode()

  // https://web.engr.oregonstate.edu/~erwig/papers/InductiveGraphs_JFP01.pdf#page=6
  override fun toString() =
    "(" + vertices.joinToString(", ", "{", "}") + ", " +
      edgList.joinToString(", ", "{", "}") { (v, e) -> "${v.id}→${e.target.id}" } + ")"
}

abstract class Edge<G, E, V>(override val source: V, override val target: V): IEdge<G, E, V>
  where G: Graph<G, E, V>, E: Edge<G, E, V>, V: Vertex<G, E, V> {
  override val graph by lazy { target.graph }
}

abstract class Vertex<G, E, V>(override val id: String): IVertex<G, E, V>
  where G: Graph<G, E, V>, E: Edge<G, E, V>, V: Vertex<G, E, V> {
  // TODO: Remove after memoizing?
  override val outgoing by lazy { edgeMap(this as V).toSet() }
  override val incoming by lazy { graph.reversed().edgMap[this] ?: emptySet() }
  override val neighbors by lazy { outgoing.map { it.target }.toSet() }
  override val outdegree by lazy { neighbors.size }

  override fun equals(other: Any?) =
    (other as? Vertex<*, *, *>)?.encode().contentEquals(encode())
  override fun hashCode() = id.hashCode()
  override fun toString() = id
}