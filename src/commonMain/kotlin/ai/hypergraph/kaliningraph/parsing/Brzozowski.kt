package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.types.*

// https://matt.might.net/papers/might2011derivatives.pdf
// TODO: We want a gadget that respects linearity d(a⊗b) = d(a)⊗b ⊕ a⊗d(b)
fun CFG.dl(r: Set<String>, x: Set<String>): Set<String> =
  (r * x * nonterminals)
    .fold(setOf()) { acc, (w, x, y) ->
      if (w to listOf(x, y) in this) acc + y else acc
    }

fun CFG.dr(r: Set<String>, y: Set<String>): Set<String> =
  (r * nonterminals * y)
    .fold(setOf()) { acc, (w, x, y) ->
      if (w to listOf(x, y) in this) acc + x else acc
    }


//fun CFG.leftQuotientGLB(r: Set<String>, x: Set<String>): Set<String> =
//  (nonterminals * x * nonterminals).fold(setOf()) { acc, (w, x, y) ->
//    if (w to listOf(x, y) in this) acc + y else acc
//  }