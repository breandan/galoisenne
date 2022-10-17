package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.types.*

// https://en.wikipedia.org/wiki/Regular_grammar
typealias REG = Set<Π2<String, String>>
val REG.language: REL by cache { REL(this) }
val REG.asCFG: CFG by cache { map { (a, b) -> a to listOf(b) }.toSet() }
val CFG.asCSL: CSL by cache { CSL(language) }

infix fun CFG.intersect(that: CFG) = CSL(language, that.language)

data class REL(val reg: REG) // https://en.wikipedia.org/wiki/Regular_language#Closure_properties
data class CFL(val cfg: CFG) // https://en.wikipedia.org/wiki/Context-free_language#Closure_properties
// Not actually context-sensitive, but close enough for now.
// May need to stage set expressions to support both ∪ and ∩,
// but currently just represents the intersection of CFL(s).
// https://en.wikipedia.org/wiki/Context-sensitive_grammar#Closure_properties

class CSL(vararg cfls: CFL) {
  val cfls: Array<CFL> = cfls.toSet().toTypedArray()
  val cfgs by lazy { cfls.map { it.cfg } }
  val nonterminals: Set<String> by lazy { intersect { nonterminals } }
  val terminals: Set<String> by lazy { intersect { terminals } }
  val symbols: Set<String> by lazy { intersect { symbols } }

  private fun <T> intersect(item: CFG.() -> Set<T>): Set<T> = cfgs.map { it.item() }.intersect()
}

// REL ⊂ CFL ⊂ CSL
operator fun REL.contains(s: String): Boolean = s in reg.asCFG.language
operator fun CFL.contains(s: String): Boolean = cfg.parse(s) != null
operator fun CSL.contains(s: String): Boolean = cfls.all { s in it }

// https://arxiv.org/pdf/2209.06809.pdf
// http://www.cs.umd.edu/~gasarch/BLOGPAPERS/cfg.pdf
infix fun CFL.intersect(rel: REL): CFL = TODO("Implement Bar-Hillel construction")
infix fun REL.intersect(cfl: CFL): CFL = cfl intersect this

// https://sites.cs.ucsb.edu/~cappello/136/lectures/17cfls/slides.pdf#page=9
infix fun CFL.intersect(cfl: CFL): CSL = CSL(this, cfl)
infix fun CSL.intersect(cfl: CFL): CSL = CSL(*cfls, cfl)
infix fun CFL.intersect(csl: CSL): CSL = CSL(this, *csl.cfls)
infix fun CSL.intersect(csl: CSL): CSL = CSL(*cfls, *csl.cfls)

// Complement: https://nokyotsu.com/me/papers/cic01.pdf