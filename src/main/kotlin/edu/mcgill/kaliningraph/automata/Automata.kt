package edu.mcgill.kaliningraph.automata

import edu.mcgill.kaliningraph.*

open class Automaton(override val vertices: Set<State> = setOf(State()))
  : Graph<Automaton, Transition, State>(vertices) {
  override fun new(vertices: Set<State>) = Automaton(vertices)
}

open class Transition(override val source: State, override val target: State, val string: String? = null) :
  Edge<Automaton, Transition, State>(source, target) {
  override fun new(source: State, target: State) = Transition(source, target, string)
}

open class State(
  id: String = randomString(),
  override val edgeMap: (State) -> Set<Transition>
) : Vertex<Automaton, Transition, State>(id) {
  constructor(id: String? = null, out: Set<State> = setOf()) : this(id = id ?: randomString(),
    edgeMap = { s -> out.map { t -> Transition(s, t) }.toSet() })

  override fun Graph(vertices: Set<State>) = Automaton(vertices)
  override fun Edge(s: State, t: State) = Transition(s, t)
  override fun Vertex(newId: String, edgeMap: (State) -> Set<Transition>): State = State(id, edgeMap)
}

class AutomatonBuilder {
  var automaton = Automaton()

  val a by State(); val b by State(); val c by State(); val d by State()
  val e by State(); val f by State(); val g by State(); val h by State()
  val i by State(); val j by State(); val k by State(); val l by State()

  operator fun State.minus(v: State) =
    State().Vertex { v.outgoing + Transition(v, this) }.also { automaton += it.graph }

  operator fun State.plus(edge: Transition) =
    State().Vertex { outgoing + edge }.also { automaton += it.graph }

  operator fun State.plus(vertex: State) =
    (graph + vertex.graph).also { automaton += it }

  class ProtoEdge(val source: State, val label: String)

  // Arithmetic is right-associative, so we construct in reverse and flip after
  operator fun State.minus(symbols: String) = ProtoEdge(this, symbols)
  operator fun ProtoEdge.minus(target: State) = target + Transition(target, source, label)

  companion object {
    operator fun invoke(builder: LabeledGraph.() -> Unit) =
      LabeledGraph().also { it.builder() }.graph.reversed()
  }
}