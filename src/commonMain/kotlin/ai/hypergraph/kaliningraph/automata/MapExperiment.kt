package ai.hypergraph.kaliningraph.automata


interface Functor<A> {
  fun <A, B, I: Functor<A>, O: I> map(f: (A) -> B, fc: I): O = TODO()
}

