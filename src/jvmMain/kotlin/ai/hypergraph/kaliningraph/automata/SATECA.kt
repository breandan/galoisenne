package ai.hypergraph.kaliningraph.automata

import ai.hypergraph.kaliningraph.sat.*
import ai.hypergraph.kaliningraph.sat.F
import ai.hypergraph.kaliningraph.sat.T
import ai.hypergraph.kaliningraph.sat.and
import ai.hypergraph.kaliningraph.tensor.FreeMatrix
import ai.hypergraph.kaliningraph.types.Ring
import ai.hypergraph.kaliningraph.types.*
import org.logicng.formulas.Formula
import kotlin.math.absoluteValue

val ecaSatAlgebra = satKernelAlgebra()
fun initializeSATECA(string: String) =
  string.map { it == '1' }.let { initializeSATECA(it.size) { i -> if(it[i]) T else F } }
fun initializeSATECA(len: Int, cc: (Int) -> Formula) =
  FreeMatrix(ecaSatAlgebra, len, 1) { r, c -> Kernel(cc((r-1).mod(len)), cc(r), cc((r+1).mod(len))) }

// Create a tridiagonal (Toeplitz) matrix
// https://en.wikipedia.org/wiki/Toeplitz_matrix#Discrete_convolution
// https://leimao.github.io/blog/Convolution-Transposed-Convolution-As-Matrix-Multiplication/
fun FreeMatrix<Kernel<Formula?>?>.genMat(): FreeMatrix<Kernel<Formula?>?> =
  FreeMatrix(ecaSatAlgebra, numRows, numRows) { r, c ->
    if (2 <= (r - c).absoluteValue) null
    else Kernel(null, null, null)
  }

infix fun FreeMatrix<Kernel<Formula?>?>.matEq(f: FreeMatrix<Kernel<Formula?>?>) =
  data.zip(f.data).fold(T) { acc, (a, b) -> acc and (a!!.π2!! eq b!!.π2!!) }

fun List<Formula>.toSATECA() = initializeSATECA(size) {this[it]}

tailrec fun FreeMatrix<Kernel<Formula?>?>.evolve(
  rule: FreeMatrix<Kernel<Formula?>?> = genMat(),
  steps: Int = 100
): FreeMatrix<Kernel<Formula?>?> =
  if (steps == 0) this
  else map { it?.applyRule() }.data.map { it!!.π2!! }.toSATECA().evolve(rule, steps - 1)
// TODO: else (rule * this).nonlinearity().evolve(rule, steps - 1)

fun Kernel<Formula?>.applyRule(
  // https://www.wolframalpha.com/input?i=rule+110
  rule: (Formula, Formula, Formula) -> Formula = { p, q, r -> (q and p.negate()) or (q xor r) }
): Kernel<Formula?> = Kernel(null, rule(π1!!, π2!!, π3!!), null)

fun FreeMatrix<Kernel<Formula?>?>.nonlinearity() =
  FreeMatrix(numRows, 1) { r, c -> this[r, c]?.applyRule() }

fun satKernelAlgebra() =
  Ring.of<Kernel<Formula?>?>(
    nil = null,
    times = { a: Kernel<Formula?>?, b: Kernel<Formula?>? ->
      when (a.nullity() to b.nullity()) {
        (0 to 0 to 0) to (0 to 1 to 0) -> (null to b!!.π2 to null) // null + !null
        else -> null
      }
    },
    plus = { a: Kernel<Formula?>?, b: Kernel<Formula?>? ->
      when (a.nullity() to b.nullity()) {
        (2 to 2 to 2) to (2 to 2 to 2) -> null // null + null
        (2 to 2 to 2) to (0 to 1 to 0) -> (b!!.π2 to null to null) // null to !null
        a!! -> throw Exception("Ruled out cases where a is nullable")
        (0 to 1 to 0) to (2 to 2 to 2) -> (null to null to a.π2) // 010 + null => 001
        (1 to 0 to 0) to (2 to 2 to 2) -> (null to null to a.π1)
        (1 to 1 to 0) to (2 to 2 to 2) -> (null to a.π1 to a.π2)
        (1 to 1 to 1) to (2 to 2 to 2) -> a // 111 + null => 111
        (0 to 1 to 1) to (2 to 2 to 2) -> a // 011 + null => 111
        (0 to 0 to 1) to (2 to 2 to 2) -> a // 001 + null => 111
        b!! -> throw Exception("Ruled out cases where b is nullable")
        (0 to 0 to 0) to (0 to 1 to 0) -> (b.π2 to null to null) // 000 + 010 => 100
        (0 to 1 to 0) to (0 to 1 to 0) -> (a.π2 to b.π2 to null) // 010 + 010 => 110
        (0 to 0 to 1) to (0 to 1 to 0) -> (b.π2 to null to a.π3) // 001 + 010 => 101
        (1 to 0 to 1) to (0 to 1 to 0) -> (a.π1 to b.π2 to a.π3) // 101 + 010 => 111
        (0 to 1 to 1) to (0 to 1 to 0) -> (b.π2 to a.π2 to a.π3) // 011 + 010 => 111
        (1 to 0 to 0) to (0 to 1 to 0) -> (a.π1 to b.π2 to null) // 100 + 010 => 110
        (1 to 1 to 0) to (0 to 1 to 0) -> (a.π1 to a.π2 to b.π2) // 110 + 010 => 111
        else -> throw Exception("This should never have occurred!")
      }
    }
  )