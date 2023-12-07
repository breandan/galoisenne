@file:Suppress("UNUSED_PARAMETER")

package ai.hypergraph.kaliningraph.automata

import ai.hypergraph.kaliningraph.tensor.FreeMatrix
import ai.hypergraph.kaliningraph.tensor.transpose
import ai.hypergraph.kaliningraph.types.*
import kotlin.math.absoluteValue

// Since we cannot encode the ECA rule into + and * directly, we need to keep
// track of the receptive field of the convolutional kernel (i.e. the neighbors)
// in order to apply rule (nonlinearity) after computing the dot product.
typealias PKernel<A> = Π3A<A?>? // Partially constructed kernel
typealias FKernel<A> = Π3A<A> // Fully constructed kernel
typealias KernelMatrix<A> = FreeMatrix<PKernel<A>>

fun <A> PKernel<A>.nullity(): FKernel<Int> =
  if (this == null) 2 to 2 to 2 // Represents null
  else (π1 != null).compareTo(false) to
    (π2  != null).compareTo(false) to
    (π3 != null).compareTo(false)

val ecaAlgebra = kernelAlgebra<Bln>()
fun initializeECA(len: Int, cc: (Int) -> Bln = { true }) =
  FreeMatrix(ecaAlgebra, len, 1) { r, c -> null to cc(r) to null }

// Create a tridiagonal (Toeplitz) matrix
// https://en.wikipedia.org/wiki/Toeplitz_matrix#Discrete_convolution
// https://leimao.github.io/blog/Convolution-Transposed-Convolution-As-Matrix-Multiplication/
fun <A> KernelMatrix<A>.genMat(algebra: Ring<PKernel<A>> = kernelAlgebra<A>()): KernelMatrix<A> =
  FreeMatrix(algebra, numRows, numRows) { r, c ->
    if (2 <= (r - c).absoluteValue && setOf(r, c) != setOf(0, numRows - 1)) null
    else null to null to null
  }

fun Blns.toECA() = initializeECA(size) { this[it] }
fun Blns.evolve(steps: Int = 1): Blns =
  toECA().evolve(steps = steps, rule = { (π2 && !π1) || (π2 xor π3) }).data.map { it!!.second!! }.toBooleanArray()

tailrec fun <A> KernelMatrix<A>.evolve(
  rule: FKernel<A>.() -> A,
  circulantMatrix: KernelMatrix<A> = genMat(),
  steps: Int = 1
): KernelMatrix<A> =
  if (steps == 0) this
  else (circulantMatrix * this).nonlinearity(rule).evolve(rule, circulantMatrix, steps - 1)

fun FreeMatrix<PKernel<Bln>>.str() = transpose.map { if (it?.π2 == true) "1" else " " }.toString()
fun FreeMatrix<PKernel<Bln>>.print() = println(str())

fun <A> KernelMatrix<A>.nonlinearity(rule: FKernel<A>.() -> A): KernelMatrix<A> =
  FreeMatrix(numRows, 1) { r, c -> null to (this[r, c] as FKernel<A>).rule() to null }

fun <A> kernelTimes(a: PKernel<A>, b: PKernel<A>): PKernel<A> =
  when (a.nullity() to b.nullity()) {
    (0 to 0 to 0) to (0 to 1 to 0) -> (null to b!!.π2 to null) // null + !null
    else -> null
  }

// We can do this because there is never more than three columns
// in a circulant matrix, so we fill in A with the contents of B
// and accumulate the dot product in a PKernel until it is full.
// When the kernel straddles an array boundary we must shift the
// accumulated contents left or right depending on the location.
fun <A> kernelPlus(a: PKernel<A>, b: PKernel<A>): PKernel<A> =
  when (a.nullity() to b.nullity()) {
    (2 to 2 to 2) to (2 to 2 to 2) -> null // null + null
    (2 to 2 to 2) to (0 to 1 to 0) -> (b!!.π2 to null to null) // null to !null
    a!! to a!! -> throw Exception("Ruled out cases where a is nullable")
    (0 to 1 to 0) to (2 to 2 to 2) -> (null to null to a.π2) // 010 + null => 001
    (1 to 0 to 0) to (2 to 2 to 2) -> (null to null to a.π1)
    (1 to 1 to 0) to (2 to 2 to 2) -> (null to a.π1 to a.π2)
    (1 to 1 to 1) to (2 to 2 to 2) -> a // 111 + null => 111
    (0 to 1 to 1) to (2 to 2 to 2) -> a // 011 + null => 111
    (0 to 0 to 1) to (2 to 2 to 2) -> a // 001 + null => 111
    b!! to b!! -> throw Exception("Ruled out cases where b is nullable")
    (0 to 0 to 0) to (0 to 1 to 0) -> (b.π2 to null to null) // 000 + 010 => 100
    (0 to 1 to 0) to (0 to 1 to 0) -> (a.π2 to b.π2 to null) // 010 + 010 => 110
    (0 to 0 to 1) to (0 to 1 to 0) -> (b.π2 to null to a.π3) // 001 + 010 => 101
    (1 to 0 to 1) to (0 to 1 to 0) -> (a.π1 to b.π2 to a.π3) // 101 + 010 => 111
    (0 to 1 to 1) to (0 to 1 to 0) -> (b.π2 to a.π2 to a.π3) // 011 + 010 => 111
    (1 to 0 to 0) to (0 to 1 to 0) -> (a.π1 to b.π2 to null) // 100 + 010 => 110
    (1 to 1 to 0) to (0 to 1 to 0) -> (a.π1 to a.π2 to b.π2) // 110 + 010 => 111
    else -> throw Exception("This should never have occurred!")
  }

// We want to have a stateless algebra
fun <T> kernelAlgebra() =
  Ring.of<PKernel<T>>(
    nil = null,
    times = { a: PKernel<T>, b: PKernel<T> -> kernelTimes(a, b) },
    plus = { a: PKernel<T>, b: PKernel<T> -> kernelPlus(a, b) }
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

val eca4 = BVec(T, F, F, T)
  .eca(::r, ::r, ::r, ::r)
  .eca(::r, ::r, ::r, ::r)
  .eca(::r, ::r, ::r, ::r)
  .eca(::r, ::r, ::r, ::r)
  .eca(::r, ::r, ::r, ::r)
  .eca(::r, ::r, ::r, ::r)

fun <
  B0, B1, B2, B3,
  Y0, Y1, Y2, Y3
  > BVec4<B0, B1, B2, B3>.eca(
  // Encodes periodic boundary conditions
  op0: (B3, B0, B1) -> Y0,
  op1: (B0, B1, B2) -> Y1,
  op2: (B1, B2, B3) -> Y2,
  op3: (B2, B3, B0) -> Y3,
): BVec4<Y0, Y1, Y2, Y3> =
  BVec4(
    op0(d, a, b),
    op1(a, b, c),
    op2(b, c, d),
    op3(c, d, a),
  )

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