package ai.hypergraph.kaliningraph.types

// https://github.com/breandan/kotlingrad/blob/b026340b18d8ef6392d10026436c44bef6186217/core/src/commonMain/kotlin/ai/hypergraph/kotlingrad/typelevel/Arithmetic.kt#L52-L89
fun main() {
    val t = op(object: II{}, object: III{}, object: PLUS{}) // If we press Alt+Enter and select "Specify type explicitly", it will produce
    // val t: P_II_III = plus(object: I{}, object: III{})
    // If we omit the type for t and "Specify type explicitly" on the following type, it is "Three". With the inferred type above, it is "Five"
    val r = apply(t, object: EVAL {})
    // val r: Five = apply(t, object: EVAL {})
}

interface N { val i: Int }
interface I   : N, One, P_I_II, P_I_III, P_I_IV, P_I_V, M_I_II, M_I_III, M_I_IV { override val i get() = 1 }
interface II  : N, Two, P_I_II, P_II_III, P_II_IV, M_I_II, M_II_III             { override val i get() = 2 }
interface III : N, Three, P_I_III, P_II_III, M_I_III, M_II_III                  { override val i get() = 3 }
interface IV  : N, Four, P_I_IV, P_II_IV, M_I_IV                                { override val i get() = 4 }
interface V   : N, Five, P_I_V                                                  { override val i get() = 5 }
interface VI  : N, Six                                                          { override val i get() = 6 }

interface One   : N { override val i get() = 1 }
interface Two   : N { override val i get() = 2 }
interface Three : N { override val i get() = 3 }
interface Four  : N { override val i get() = 4 }
interface Five  : N { override val i get() = 5 }
interface Six   : N { override val i get() = 6 }

interface M_I_II   : Two
interface M_I_III  : Three
interface M_I_IV   : Four
interface M_II_III : Six
interface M_II_II  : Four

interface P_I_II   : Three
interface P_I_III  : Four
interface P_I_IV   : Five
interface P_II_III : Five
interface P_II_IV  : Six
interface P_I_V    : Six
interface EVAL     : One, Two, Three, Four, Five, Six           { override val i get() = TODO() }
interface PLUS     : P_I_II, P_I_III, P_I_IV, P_II_III, P_II_IV { override val i get() = TODO() }
interface TIMES    : M_I_II, M_I_III, M_I_IV, M_II_III          { override val i get() = TODO() }

fun <X: T, Y: T, Z: T, Q: T, T> op(x: X, y: Y, op: Z): Q = TODO()
fun <X:Z, Y: Z, Z: T, T> apply(x: X, op: Y): Z = TODO()