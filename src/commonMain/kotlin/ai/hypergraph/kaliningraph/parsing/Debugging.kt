import ai.hypergraph.kaliningraph.formatAsGrid
import ai.hypergraph.kaliningraph.parsing.*
import ai.hypergraph.kaliningraph.tensor.FreeMatrix
import ai.hypergraph.kaliningraph.types.cache

fun Production.pretty(): Σᐩ = LHS + " -> " + RHS.joinToString(" ")
val CFG.pretty: FreeMatrix<Σᐩ> by cache { map { it.pretty() }.formatAsGrid(3) }
fun CSL.pretty() = cfgs.joinToString("\nΛ\n") { it.prettyPrint() }
fun CFG.prettyPrint(): Σᐩ = pretty.toString()