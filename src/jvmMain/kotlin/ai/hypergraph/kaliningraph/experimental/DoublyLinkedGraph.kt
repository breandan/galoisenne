package ai.hypergraph.kaliningraph.experimental

// TODO: Finish implementing doubly linked graph
interface Vtx<C> {
  val contents: C
  fun outgoing(): Set<Vtx<C>> = setOf(this)
  fun incoming(): Set<Vtx<C>> = setOf(this)

  operator fun minus(other: C): Vtx<C> =
    Self(
      contents = other,
      incoming = { setOf(this) },
      outgoing = { setOf(it) }
    )

  fun Self(
    contents: C,
    incoming: (Vtx<C>) -> Set<Vtx<C>> = { setOf(it) },
    outgoing: (Vtx<C>) -> Set<Vtx<C>> = { setOf(it) },
  ): Vtx<C>

  companion object {
    operator fun <C> invoke(
      contents: C,
      incoming: (Vtx<C>) -> Set<Vtx<C>> = { setOf(it) },
      outgoing: (Vtx<C>) -> Set<Vtx<C>> = { setOf(it) },
    ): Vtx<C> =
      object: Vtx<C> {
        override val contents = contents
        override fun outgoing(): Set<Vtx<C>> = outgoing(this)
        override fun incoming(): Set<Vtx<C>> = incoming(this)
        override fun Self(contents: C,
                          incoming: (Vtx<C>) -> Set<Vtx<C>>,
                          outgoing: (Vtx<C>) -> Set<Vtx<C>>) =
          invoke(contents, incoming, outgoing)
      }
  }
}

fun main() {
  val q = Vtx("qqq")
  val t = q - "asdf" - "abcde"
  println(t.contents)
  t.outgoing().forEach { println(it.contents) }
}