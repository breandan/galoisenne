package ai.hypergraph.kaliningraph.automata

import ai.hypergraph.kaliningraph.tensor.FreeMatrix
import ai.hypergraph.kaliningraph.tensor.transpose
import ai.hypergraph.kaliningraph.types.*
import kotlin.math.absoluteValue

// Since we cannot encode the ECA rule into + and * directly, we need to keep
// track of the receptive field of the convolutional kernel (i.e. the neighbors)
// in order to apply rule (nonlinearity) after computing the dot product.
typealias Context<A> = Π3<A, A, A>
val <A> Context<A>.p get() = π1
val <A> Context<A>.q get() = π2
val <A> Context<A>.r get() = π3

val ecaAlgebra = contextAlgebra()
fun initializeECA(len: Int, cc: (Int) -> Boolean = { true }) =
  FreeMatrix(ecaAlgebra, len, 1) { r, c ->
    Context(null, cc(r), null)
  }

// Create a tridiagonal (Toeplitz) matrix
// https://en.wikipedia.org/wiki/Toeplitz_matrix#Discrete_convolution
// https://leimao.github.io/blog/Convolution-Transposed-Convolution-As-Matrix-Multiplication/
fun FreeMatrix<Context<Boolean?>?>.genMat(): FreeMatrix<Context<Boolean?>?> =
  FreeMatrix(ecaAlgebra, numRows, numRows) { r, c ->
    if (2 <= (r - c).absoluteValue) null
    else Context(null, null, null)
  }

tailrec fun FreeMatrix<Context<Boolean?>?>.evolve(
  rule: FreeMatrix<Context<Boolean?>?> = genMat(),
  steps: Int = 100,
  hashes: Set<Int> = emptySet(),
  hashCode: Int = str().hashCode()
): FreeMatrix<Context<Boolean?>?> =
  if (steps == 0 || hashCode in hashes) this.also { it.print() }
  else (rule * this.also { it.print() }).nonlinearity().evolve(rule, steps - 1,hashes + hashCode)

fun FreeMatrix<Context<Boolean?>?>.str() = transpose.map { if (it?.q == true) "1" else " " }.toString()
fun FreeMatrix<Context<Boolean?>?>.print() = println(str())

fun Context<Boolean?>.applyRule(
  // https://www.wolframalpha.com/input?i=rule+110
  rule: (Boolean, Boolean, Boolean) -> Boolean = { p, q, r -> (q && !p) || (q xor r) }
): Context<Boolean?> = Context(null, rule(p ?: false, q!!, r ?: false), null)

fun FreeMatrix<Context<Boolean?>?>.nonlinearity() =
  FreeMatrix(numRows, 1) { r, c -> this[r, c]?.applyRule() }

fun contextAlgebra() =
  Ring.of<Context<Boolean?>?>(
    nil = null,
    times = { a: Context<Boolean?>?, b: Context<Boolean?>? ->
      if (a == null && b == null) null
      else if (a != null && b != null) Context(null, b.π2, null)
      else null
    },
    plus = { a: Context<Boolean?>?, b: Context<Boolean?>? ->
      if (a == null && b != null) Context(b.π2, null, null)
      else if (a != null && b != null)
        if (a.π2 == null) Context(a.π1, b.π2, null)
        else Context(a.π1, a.π2, b.π2)
      else if (a != null && b == null) a
      else null
    }
  )

// Rule 110 Encoding
fun r(p: T, q: T, r: T) = F //(q and p.flip()) or (q xor r)
fun r(p: T, q: T, r: F) = T //(q and p.flip()) or (q xor r)
fun r(p: T, q: F, r: T) = T //(q and p.flip()) or (q xor r)
fun r(p: T, q: F, r: F) = F //(q and p.flip()) or (q xor r)
fun r(p: F, q: T, r: T) = T //(q and p.flip()) or (q xor r)
fun r(p: F, q: T, r: F) = T //(q and p.flip()) or (q xor r)
fun r(p: F, q: F, r: T) = T //(q and p.flip()) or (q xor r)
fun r(p: F, q: F, r: F) = F //(q and p.flip()) or (q xor r)

// Typelevel implementation of Rule 110
val eca10 = BVec(F, F, F, F, F, F, F, F, F, T)
  .eca(::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r) // BVec10<F, F, F, F, F, F, F, F, F, T>
  .eca(::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r)
  .eca(::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r)
  .eca(::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r)
  .eca(::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r)
  .eca(::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r)
  .eca(::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r)
  .eca(::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r)
  .eca(::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r)
  .eca(::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r)
  .eca(::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r)
  .eca(::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r)
  .eca(::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r)
  .eca(::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r)
  .eca(::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r)
  .eca(::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r)
  .eca(::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r)
  .eca(::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r)
  .eca(::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r)
  .eca(::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r)
  .eca(::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r)
  .eca(::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r)
  .eca(::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r)
  .eca(::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r)
  .eca(::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r)
  .eca(::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r)
  .eca(::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r)

fun <
  B0, B1, B2, B3, B4, B5, B6, B7, B8, B9,
  Y0, Y1, Y2, Y3, Y4, Y5, Y6, Y7, Y8, Y9,
> BVec10<B0, B1, B2, B3, B4, B5, B6, B7, B8, B9>.eca(
  // Encodes periodic boundary conditions
  op0: (B9, B0, B1) -> Y0,
  op1: (B0, B1, B2) -> Y1,
  op2: (B1, B2, B3) -> Y2,
  op3: (B2, B3, B4) -> Y3,
  op4: (B3, B4, B5) -> Y4,
  op5: (B4, B5, B6) -> Y5,
  op6: (B5, B6, B7) -> Y6,
  op7: (B6, B7, B8) -> Y7,
  op8: (B7, B8, B9) -> Y8,
  op9: (B8, B9, B0) -> Y9,
): BVec10<Y0, Y1, Y2, Y3, Y4, Y5, Y6, Y7, Y8, Y9> =
  BVec10(
    op0(b9, b0, b1),
    op1(b0, b1, b2),
    op2(b1, b2, b3),
    op3(b2, b3, b4),
    op4(b3, b4, b5),
    op5(b4, b5, b6),
    op6(b5, b6, b7),
    op7(b6, b7, b8),
    op8(b7, b8, b9),
    op9(b8, b9, b0),
  )

// Try to eliminate use-site dispatching via subtype constraint solving
// https://kotlinlang.org/spec/kotlin-type-constraints.html#type-constraint-solving

sealed interface XBool<B, nB> {
  val b: B
  val nb: nB
}

interface X: XBool<X, O>
//  XXO<X, X, O>,
//  XOX<X, O, X>,
//  OXX<O, X, X>,
//  OXO<O, X, O>,
//  OOX<O, O, X>
{
  override val b: X
  override val nb: O
}
interface O: XBool<O, X>
//  XXX,
//  XOO,
//  OOO
{
  override val b: O
  override val nb: X
}

interface OOO : Tr<O,O,O>
interface OOX : Tr<O,O,X>
interface OXO : Tr<O,X,O>
interface OXX : Tr<O,X,X>
interface XOO : Tr<X,O,O>
interface XOX : Tr<X,O,X>
interface XXO : Tr<X,X,O>
interface XXX : Tr<X,X,X>

interface Tr<A, B, C>

// https://discuss.kotlinlang.org/t/bug-in-where/25011
// https://discuss.kotlinlang.org/t/current-intersection-type-options-in-kotlin/20903
@Suppress("BOUNDS_NOT_ALLOWED_IF_BOUNDED_BY_TYPE_PARAMETER")
inline fun <
  reified B0, reified B1, reified B2,
  Y0, Y1, Y2
> BVec3<B0, B1, B2>.ecac(): BVec3<Y0, Y1, Y2> where
  Y0 : B2, Y0: B0, Y0: B1,
  Y1 : B0, Y1: B1, Y1: B2,
  Y2 : B1, Y2: B2, Y2: B0
= BVec3(null, null, null) as BVec3<Y0, Y1, Y2>