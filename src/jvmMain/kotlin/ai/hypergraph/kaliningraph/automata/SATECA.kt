package ai.hypergraph.kaliningraph.automata

import ai.hypergraph.kaliningraph.sat.*
import ai.hypergraph.kaliningraph.sat.F
import ai.hypergraph.kaliningraph.sat.T
import ai.hypergraph.kaliningraph.sat.and
import ai.hypergraph.kaliningraph.tensor.FreeMatrix
import ai.hypergraph.kaliningraph.tensor.transpose
import ai.hypergraph.kaliningraph.types.Ring
import ai.hypergraph.kaliningraph.types.π1
import ai.hypergraph.kaliningraph.types.π2
import org.logicng.formulas.Formula
import kotlin.math.absoluteValue


val ecaSatAlgebra = satContextAlgebra()
fun initializeSATECA(string: String) =
  string.map { it == '1' }.let { initializeSATECA(it.size) { i -> if(it[i]) T else F } }
fun initializeSATECA(len: Int, cc: (Int) -> Formula) =
  FreeMatrix(ecaSatAlgebra, len, 1) { r, c -> Context(cc((r-1).mod(len)), cc(r), cc((r+1).mod(len))) }

// Create a tridiagonal (Toeplitz) matrix
// https://en.wikipedia.org/wiki/Toeplitz_matrix#Discrete_convolution
// https://leimao.github.io/blog/Convolution-Transposed-Convolution-As-Matrix-Multiplication/
fun FreeMatrix<Context<Formula?>?>.genMat(): FreeMatrix<Context<Formula?>?> =
  FreeMatrix(ecaSatAlgebra, numRows, numRows) { r, c ->
    if (2 <= (r - c).absoluteValue) null
    else Context(null, null, null)
  }

infix fun FreeMatrix<Context<Formula?>?>.matEq(f: FreeMatrix<Context<Formula?>?>) =
  data.zip(f.data).fold(T) { acc, (a, b) -> acc and (a!!.π2!! eq b!!.π2!!) }

fun List<Formula>.toSATECA() = initializeSATECA(size) {this[it]}

tailrec fun FreeMatrix<Context<Formula?>?>.evolve(
  rule: FreeMatrix<Context<Formula?>?> = genMat(),
  steps: Int = 100
): FreeMatrix<Context<Formula?>?> =
  if (steps == 0) this
  else map { it?.applyRule() }.data.map { it!!.π2!! }.toSATECA().evolve(rule, steps - 1)
// TODO: else (rule * this).nonlinearity().evolve(rule, steps - 1)

fun Context<Formula?>.applyRule(
  // https://www.wolframalpha.com/input?i=rule+110
  rule: (Formula, Formula, Formula) -> Formula = { p, q, r -> (q and p.negate()) or (q xor r) }
): Context<Formula?> = Context(null, rule(p!!, q!!, r!!), null)

fun FreeMatrix<Context<Formula?>?>.nonlinearity() =
  FreeMatrix(numRows, 1) { r, c -> this[r, c]?.applyRule() }

fun satContextAlgebra() =
  Ring.of<Context<Formula?>?>(
    nil = null,
    times = { a: Context<Formula?>?, b: Context<Formula?>? ->
      if (a == null && b == null) null
      else if (a != null && b != null) Context(null, b.π2, null)
      else null
    },
    plus = { a: Context<Formula?>?, b: Context<Formula?>? ->
      if (a == null && b != null) Context(b.π2, null, null)
      else if (a != null && b != null)
        if (a.π2 == null) Context(a.π1, b.π2, null)
        else Context(a.π1, a.π2, b.π2)
      else if (a != null && b == null) a
      else null
    }
  )