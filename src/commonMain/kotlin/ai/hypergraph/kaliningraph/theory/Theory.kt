package ai.hypergraph.kaliningraph.theory

import ai.hypergraph.kaliningraph.*
import ai.hypergraph.kaliningraph.tensor.*
import ai.hypergraph.kaliningraph.types.*

// https://en.wikipedia.org/wiki/Barab%C3%A1si%E2%80%93Albert_model#Algorithm
tailrec fun <G : IGraph<G, E, V>, E : IEdge<G, E, V>, V : IVertex<G, E, V>> IGraph<G, E, V>.prefAttach(
  graph: G = this as G,
  vertices: Int = 1,
  degree: Int = 3,
  attach: G.(Int) -> G
): G = if (vertices <= 0) graph
else prefAttach(graph.attach(degree), vertices - 1, degree, attach)

/* (A')ⁿ[a, b] counts the number of walks between vertices a, b of
 * length n. Let i be the smallest natural number such that (A')ⁱ
 * has no zeros. i is the length of the longest shortest path in G.
 */

tailrec fun <G : IGraph<G, E, V>, E : IEdge<G, E, V>, V : IVertex<G, E, V>> IGraph<G, E, V>.slowDiameter(
  i: Int = 1,
  walks: BooleanMatrix = A_AUG
): Int =
  if (walks.isFull) i
  else slowDiameter(i = i + 1, walks = walks * A_AUG)

// Based on Booth & Lipton (1981): https://doi.org/10.1007/BF00264532

tailrec fun <G : IGraph<G, E, V>, E : IEdge<G, E, V>, V : IVertex<G, E, V>> IGraph<G, E, V>.diameter(
  i: Int = 1,
  prev: BooleanMatrix = A_AUG,
  next: BooleanMatrix = prev
): Int =
  if (next.isFull) slowDiameter(i / 2, prev)
  else diameter(i = 2 * i, prev = next, next = next * next)

/* Weisfeiler-Lehman isomorphism test:
 * http://www.jmlr.org/papers/volume12/shervashidze11a/shervashidze11a.pdf#page=6
 * http://davidbieber.com/post/2019-05-10-weisfeiler-lehman-isomorphism-test/
 * https://breandan.net/2020/06/30/graph-computation/#weisfeiler-lehman
 */

tailrec fun <G : IGraph<G, E, V>, E : IEdge<G, E, V>, V : IVertex<G, E, V>> IGraph<G, E, V>.wl(
  k: Int = 5,
  label: (V) -> Int = { histogram[it]!! }
): Map<V, Int> {
  val updates = associateWith { it.neighbors.map(label).sorted().hashCode() }
  return if (k <= 0 || all { label(it) == updates[it] }) updates
  else wl(k - 1) { updates[it]!! }
}

/* IGraph-level GNN implementation
 * https://www.cs.mcgill.ca/~wlh/grl_book/files/GRL_Book-Chapter_5-GNNs.pdf#page=6
 * H^t := σ(AH^(t-1)W^(t) + H^(t-1)W^t)
 *
 * TODO:
 *   Pooling: https://www.cs.mcgill.ca/~wlh/grl_book/files/GRL_Book-Chapter_5-GNNs.pdf#page=18
 *   Convolution: https://arxiv.org/pdf/2004.03519.pdf#page=2
 */

tailrec fun <G : IGraph<G, E, V>, E : IEdge<G, E, V>, V : IVertex<G, E, V>> IGraph<G, E, V>.gnn(
  // Message passing rounds
  t: Int = diameter() * 10,
  // Matrix of node representations ℝ^{|V|xd}
  H: DoubleMatrix = ENCODED,
  // (Trainable) weight matrix ℝ^{dxd}
  W: DoubleMatrix = randomMatrix(H.numCols),
  // Bias term ℝ^{dxd}
  b: DoubleMatrix = randomMatrix(size, H.numCols),
  // Nonlinearity ℝ^{*} -> ℝ^{*}
  σ: (DoubleMatrix) -> DoubleMatrix = ACT_TANH,
  // Layer normalization ℝ^{*} -> ℝ^{*}
  z: (DoubleMatrix) -> DoubleMatrix = NORM_AVG,
  // Message ℝ^{*} -> ℝ^{*}
  m: (DoubleMatrix) -> DoubleMatrix = { σ(z(A * it * W + it * W + b)) }
): DoubleMatrix = if (t == 0) H else gnn(t = t - 1, H = m(H), W = W, b = b)

// https://fabianmurariu.github.io/posts/scala3-typeclassery-graphs/
// https://doisinkidney.com/pdfs/algebras-for-weighted-search.pdf
// https://www.youtube.com/watch?v=n6oS6X-DOlg

tailrec fun <G : IGraph<G, E, V>, E : IEdge<G, E, V>, V : IVertex<G, E, V>> IGraph<G, E, V>.dfs(
  init: V = random(),
  cond: (V) -> Boolean
): V = TODO()

tailrec fun <G : IGraph<G, E, V>, E : IEdge<G, E, V>, V : IVertex<G, E, V>> IGraph<G, E, V>.bfs(
  init: V = random(),
  cond: (V) -> Boolean
): V = TODO()

tailrec fun <G : IGraph<G, E, V>, E : IEdge<G, E, V>, V : IVertex<G, E, V>> IGraph<G, E, V>.beamsearch(
  init: V = random(),
  cond: (V) -> Boolean
): V = TODO()