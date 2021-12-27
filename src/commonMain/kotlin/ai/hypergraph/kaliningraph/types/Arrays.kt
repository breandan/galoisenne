package ai.hypergraph.kaliningraph.types

data class T1<A>(val e1: A)
data class T2<A, B>(val e1: A, val e2: B)
data class T3<A, B, C>(val e1: A, val e2: B, val e3: C)
data class T4<A, B, C, D>(val e1: A, val e2: B, val e3: C, val e4: D)

typealias A1<A> = T1<A>
typealias A2<A> = T2<A, A>
typealias A3<A> = T3<A, A, A>
typealias A4<A> = T4<A, A, A, A>