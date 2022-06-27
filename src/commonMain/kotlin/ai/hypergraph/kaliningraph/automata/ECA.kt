package ai.hypergraph.kaliningraph.automata

import ai.hypergraph.kaliningraph.tensor.FreeMatrix
import ai.hypergraph.kaliningraph.tensor.transpose
import ai.hypergraph.kaliningraph.types.*
import kotlin.math.absoluteValue

typealias Context<A> = Π3<A, A, A>
val <A> Context<A>.p get() = π1
val <A> Context<A>.q get() = π2
val <A> Context<A>.r get() = π3

val ecaAlgebra = algebra()
fun makeVec(len: Int) =
  FreeMatrix(ecaAlgebra, len, 1) { r, c ->
    if (len - 1 == r) Context(null, true, null)
    else Context(null, false, null)
  }

// Create a tridiagonal matrix
fun FreeMatrix<Context<Boolean?>?>.genMat(): FreeMatrix<Context<Boolean?>?> =
  FreeMatrix(ecaAlgebra, numRows, numRows) { r, c ->
    if ((r - c).absoluteValue < 2) Context(null, null, null) else null
  }

tailrec fun FreeMatrix<Context<Boolean?>?>.evolve(
  rule: FreeMatrix<Context<Boolean?>?> = genMat(),
  steps: Int = 100,
  hashes: Set<Int> = emptySet(),
  hashCode: Int = str().hashCode()
): FreeMatrix<Context<Boolean?>?> =
  if (steps == 0 || hashCode in hashes) this.also { it.print() }
  else (rule * this.also { it.print() }).nonlinearity().evolve(rule, steps - 1,hashes + hashCode)

fun FreeMatrix<Context<Boolean?>?>.str() = transpose.map { if(it?.q == true) "1" else " " }.toString()
fun FreeMatrix<Context<Boolean?>?>.print() = println(str())

fun Context<Boolean?>.applyRule(
  // https://www.wolframalpha.com/input?i=rule+110
  rule: (Boolean, Boolean, Boolean) -> Boolean = { p, q, r -> (q && !p) || (q xor r) }
): Context<Boolean?> = Context(null, rule(p ?: false, q!!, r ?: false), null)

fun FreeMatrix<Context<Boolean?>?>.nonlinearity() =
  FreeMatrix(numRows, 1) { r, c -> this[r, c]?.applyRule() }

fun algebra() =
  Ring.of<Context<Boolean?>?>(
    nil = null,
    times = { a: Context<Boolean?>?, b: Context<Boolean?>? ->
      if (a == null && b == null) null
      else if (a != null && b != null) Context(null, b.π2, null)
      else null
    },
    plus = { a: Context<Boolean?>?, b: Context<Boolean?>? ->
//      (a to b).also { println(it) }
      (if (a == null && b != null) Context(b.π2, null, null)
      else if (a != null && b != null)
//        if (a.π1 == null) Context(b.π2, null, null)
        if (a.π2 == null) Context(a.π1, b.π2, null)
        else Context(a.π1, a.π2, b.π2)
      else if (a != null && b == null) a
      else null)//.also { println("$a + $b = $it") }
    }
  )