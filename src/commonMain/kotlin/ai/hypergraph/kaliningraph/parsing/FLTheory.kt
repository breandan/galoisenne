package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.types.cache
import ai.hypergraph.kaliningraph.types.Π2

// https://en.wikipedia.org/wiki/Regular_grammar
typealias REG = Set<Π2<String, String>>
val REG.language: REL by cache { REL(this) }
val REG.asCFG: CFG by cache { map { (a, b) -> a to listOf(b) }.toSet() }

data class REL(val reg: REG) // https://en.wikipedia.org/wiki/Regular_language#Closure_properties
data class CFL(val cfg: CFG) // https://en.wikipedia.org/wiki/Context-free_language#Closure_properties
// May need to stage set expressions to support both ∪ and ∩,
// but currently just represents the intersection of CFL(s).
class Decidable(vararg val cfls: CFL) // https://en.wikipedia.org/wiki/Recursive_language#Closure_properties

operator fun CFL.contains(s: String): Boolean = cfg.parse(s) != null
operator fun REL.contains(s: String): Boolean = s in reg.asCFG.language

// https://arxiv.org/pdf/2209.06809.pdf
// http://www.cs.umd.edu/~gasarch/BLOGPAPERS/cfg.pdf
infix fun CFL.intersect(rel: REL): CFL = TODO("Implement Bar-Hillel")
// https://sites.cs.ucsb.edu/~cappello/136/lectures/17cfls/slides.pdf#page=9
infix fun CFL.intersect(cfl: CFL): Decidable = Decidable(this, cfl)
infix fun Decidable.intersect(cfl: CFL): Decidable = Decidable(*cfls, cfl)