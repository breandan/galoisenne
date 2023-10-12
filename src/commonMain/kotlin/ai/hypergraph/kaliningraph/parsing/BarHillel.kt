package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.types.*
import kotlin.time.TimeSource

infix fun FSA.intersect(cfg: CFG) = cfg.intersect(this)
// http://www.cs.umd.edu/~gasarch/BLOGPAPERS/cfg.pdf#page=2
// https://browse.arxiv.org/pdf/2209.06809.pdf#page=5

infix fun CFG.intersect(fsa: FSA): CFG {
  val clock = TimeSource.Monotonic.markNow()
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
      triples.map { (p, q, r) -> "[$p,$A,$r] -> [$p,$B,$q] [$q,$C,$r]" }
    }.flatten()

  // For every production A → σ in P, for every (p, σ, q) ∈ Q × Σ × Q
  // such that δ(p, σ) = q we have the production [p, A, q] → σ in P′.
  val unitProds =
    unitProductions.map { (A, rhs) ->
      val relevantTransits = fsa.Q.filter { it.π2 == rhs[0] }
      relevantTransits.map { (p, σ, q) -> "[$p,$A,$q] -> $σ" }
    }.flatten()

  return (initFinal + transits + binaryProds + unitProds).joinToString("\n")
    .parseCFG(normalize = false)
    .also { print("∩-grammar has ${it.size} total productions") }
    .removeVestigalProductions().normalForm.noNonterminalStubs
    .also { println("∩-grammar has ${it.size} useful productions") }
    .also { println("∩-grammar construction took: ${clock.elapsedNow().inWholeMilliseconds}ms") }
//    .also { println(it.pretty) }
//    .also { println(it.size) }
}