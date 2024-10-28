package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.repair.MAX_RADIUS
import ai.hypergraph.kaliningraph.types.*

// https://en.wikipedia.org/wiki/Regular_grammar
typealias REG = Set<Π2A<Σᐩ>>
val REG.language: REL by cache { REL(this) }

// Subsumption holds a fortiori: REG <: CFG <: CJL
val REG.asCFG: CFG by cache { map { (a, b) -> a to listOf(b) }.toSet() }
val CFG.asCJL: CJL by cache { CJL(language) }

infix fun CFG.intersect(that: CFG) = CJL(language, that.language)

data class REL(val reg: REG) // https://en.wikipedia.org/wiki/Regular_language#Closure_properties
data class CFL(val cfg: CFG) // https://en.wikipedia.org/wiki/Context-free_language#Closure_properties

// TODO: Conjunctive grammar: https://en.wikipedia.org/wiki/Conjunctive_grammar
// TODO: Boolean grammar: https://en.wikipedia.org/wiki/Boolean_grammar
// May need to stage set expressions to support both ∪ and ∩,
// but currently just represents the intersection of CFL(s).

class CJL(vararg cfls: CFL) {
  val cfls: Array<CFL> = cfls.toSet().toTypedArray()
  val cfgs by cache { cfls.map { it.cfg } }
  val nonterminals: Set<Σᐩ> by cache { intersect { nonterminals } }
  val terminals: Set<Σᐩ> by cache { intersect { terminals } }
  val symbols: Set<Σᐩ> by cache { intersect { symbols } }

  private fun <T> intersect(item: CFG.() -> Set<T>): Set<T> = cfgs.map { it.item() }.intersect()
}

fun CJL.upwardClosure(terminals: Set<Σᐩ>): CJL =
  CJL(*cfgs.map { CFL(it.upwardClosure(terminals)) }.toTypedArray())

// Given a set of tokens from a string, find the upward closure of the CFG w.r.t. the tokens.
fun CFG.upwardClosure(tokens: Set<Σᐩ>): CFG =
  tokens.intersect(terminals).let {
    if (it.isEmpty()) this
    else (depGraph.reversed().transitiveClosure(tokens) - terminals)
      .let { closure -> filter { it.LHS in closure } }
  }

fun pruneInactiveRules(cfg: CFG): CFG =
  TODO("Identify and prune all nonterminals t generating" +
    "a finite language rooted at t and disjoint from the upward closure.")


// REL ⊂ CFL ⊂ CJL
operator fun REL.contains(s: Σᐩ): Bln = s in reg.asCFG.language
operator fun CFL.contains(s: Σᐩ): Bln = cfg.isValid(s)
operator fun CJL.contains(s: Σᐩ): Bln = cfls.all { s in it }
operator fun CFL.contains(s: List<Σᐩ>): Bln = cfg.isValid(s)

// https://arxiv.org/pdf/2209.06809.pdf
// http://www.cs.umd.edu/~gasarch/BLOGPAPERS/cfg.pdf
infix fun CFL.intersect(rel: REL): CFL = TODO("Implement Bar-Hillel construction")
infix fun REL.intersect(cfl: CFL): CFL = cfl intersect this

// https://sites.cs.ucsb.edu/~cappello/136/lectures/17cfls/slides.pdf#page=9
infix fun CFL.intersect(cfl: CFL): CJL = CJL(this, cfl)
infix fun CJL.intersect(cfl: CFL): CJL = CJL(*cfls, cfl)
infix fun CFL.intersect(cjl: CJL): CJL = CJL(this, *cjl.cfls)
infix fun CJL.intersect(cjl: CJL): CJL = CJL(*cfls, *cjl.cfls)

// Complement: https://nokyotsu.com/me/papers/cic01.pdf