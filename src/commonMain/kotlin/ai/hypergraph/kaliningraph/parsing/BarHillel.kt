package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.types.*

infix fun FSA.intersect(cfg: CFG) = cfg.intersect(this)
// http://www.cs.umd.edu/~gasarch/BLOGPAPERS/cfg.pdf

infix fun CFG.intersect(fsa: FSA): CFG {
  val initFinal =
    (fsa.init * fsa.final).map { (q, r) -> "START -> [$q,START,$r]" }

  val transits =
    fsa.Q.map { (q, a, r) -> "[$q,$a,$r] -> $a" }

  // For each production A → BC in P , for every p, q, r ∈ Q,
  // we have the production [p,A,r] → [p,B,q] [q,C,r] in P′.
  val binaryProds =
    nonterminalProductions.map {
      val triples = fsa.states * fsa.states * fsa.states
      val (A, B, C) = it.π1 to it.π2[0] to it.π2[1]
      triples.map { (p, q, r) -> "[$p,$A,$r] -> [$q,$B,$q] [$q,$C,$r]" }
    }.flatten()

  // For every production A → σ in P, for every (p, σ, q) ∈ Q × Σ × Q
  // such that δ(p, σ) = q we have the production [p, A, q] → σ in P′.
  val unitProds =
    unitProductions.map { (lhs, rhs) ->
      val relevantTransits = fsa.Q.filter { it.π1 == rhs[0] }
      relevantTransits.map { (q, a, r) -> "[$q,$lhs,$r] -> $a" }
    }.flatten()

  return (initFinal + transits + binaryProds + unitProds).joinToString("\n")
    .parseCFG(normalize = false).removeVestigalProductions()
}