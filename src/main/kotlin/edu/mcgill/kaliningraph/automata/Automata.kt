package edu.mcgill.kaliningraph.automata

import edu.mcgill.kaliningraph.*

open class Automaton(override val vertices: Set<State> = setOf(State()))
  : Graph<Automaton, Transition, State>(vertices)

open class Transition(override val source: State, override val target: State, val string: String? = null) :
  Edge<Automaton, Transition, State>(source, target)

open class State(
  id: String = randomString(),
  override val edgeMap: (State) -> Set<Transition>
) : Vertex<Automaton, Transition, State>(id) {
  constructor(id: String? = null, out: Set<State> = setOf()) : this(id = id ?: randomString(),
    edgeMap = { s -> out.map { t -> Transition(s, t) }.toSet() })

  override fun V(newId: String, edgeMap: (State) -> Set<Transition>): State = State(id, edgeMap)
}

class AutomatonBuilder {
  var automaton = Automaton()

  val a by State(); val b by State(); val c by State(); val d by State()
  val e by State(); val f by State(); val g by State(); val h by State()
  val i by State(); val j by State(); val k by State(); val l by State()

  operator fun State.minus(v: State) =
    V(id) { v.outgoing + Transition(v, this) }.also { automaton += it.graph }

  operator fun State.plus(edge: Transition) =
    V(id) { outgoing + edge }.also { automaton += it.graph }

  operator fun State.plus(vertex: State) =
    (graph + vertex.graph).also { automaton += it }

  class ProtoEdge(val source: State, val label: String)

  // Arithmetic is right-associative, so we construct in reverse and flip after
  operator fun State.minus(symbols: String) = ProtoEdge(this, symbols)
  operator fun ProtoEdge.minus(target: State) = target + Transition(target, source, label)

  companion object {
    operator fun invoke(builder: LabeledGraph.() -> Unit) =
      LabeledGraph().also { it.builder() }.reversed()
  }
}