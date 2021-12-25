package ai.hypergraph.kaliningraph.types

data class T1<A>(val e1: A)
data class T2<A, B>(val e1: A, val e2: B)
data class T3<A, B, C>(val e1: A, val e2: B, val e3: C)
data class T4<A, B, C, D>(val e1: A, val e2: B, val e3: C, val e4: D)

data class A1<A>(val e1: A)
data class A2<A>(val e1: A, val e2: A)
data class A3<A>(val e1: A, val e2: A, val e3: A)
data class A4<A>(val e1: A, val e2: A, val e3: A, val e4: A)
