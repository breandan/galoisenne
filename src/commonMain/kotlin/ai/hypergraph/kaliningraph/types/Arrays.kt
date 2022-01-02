package ai.hypergraph.kaliningraph.types

// Multi-typed arrays
data class Y1<A>(val e1: A)
data class Y2<A, B>(val e1: A, val e2: B)
data class Y3<A, B, C>(val e1: A, val e2: B, val e3: C)
data class Y4<A, B, C, D>(val e1: A, val e2: B, val e3: C, val e4: D)

//typealias A1<A> = Y1<A>
//typealias A2<A> = Y2<A, A>
//typealias A3<A> = Y3<A, A, A>
//typealias A4<A> = Y4<A, A, A, A>