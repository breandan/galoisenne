package edu.mcgill.kaliningraph.automata

import edu.mcgill.kaliningraph.*

open class State(
  id: String = randomString(),
  override val edgeMap: (State) -> Collection<Transition>
) : Node<State, Transition>(id) {
  constructor(id: String? = null, out: Set<State> = setOf()) : this(
    id = id ?: randomString(),
    edgeMap = { out.map { Transition(it) } })

//  override val edges = transition(this).toSet()
//  override val neighbors = edges.map { it.target }.toSet()
  override fun new(id: String?, out: Set<State>): State = State(id, out)
  override fun new(id: String, edgeMap: (State) -> Collection<Transition>): State =
    State(id, edgeMap)
}

open class Transition(val nextState: State, val string: String? = null) : Edge<Transition, State>(nextState) {
  override fun newTarget(target: State) = Transition(target, string)
}

open class Automaton(override val V: Set<State> = setOf(State())) : Graph<State, Transition>() {
  constructor(vararg states: State) : this(states.toSet())

  override val prototype: State by lazy { V.firstOrNull() ?: State() }
}

class AutomatonBuilder {
  var graph: Graph<State, Transition> = Automaton()

  val a by State(); val b by State(); val c by State(); val d by State()
  val e by State(); val f by State(); val g by State(); val h by State()
  val i by State(); val j by State(); val k by State(); val l by State()

  operator fun State.minus(v: State) =
    State().new(v.id) { v.edges + Transition(this) }.also { graph += Automaton(it) }

  operator fun State.plus(edge: Transition) =
    State().new { edges + edge }.also { graph += Graph(it) }

  operator fun State.plus(vertex: State) =
    (asGraph() + vertex.asGraph()).also { graph += it }

  class ProtoEdge(val source: State, val label: String)

  // Arithmetic is right-associative, so we construct in reverse and flip after
  operator fun State.minus(symbols: String) = ProtoEdge(this, symbols)
  operator fun ProtoEdge.minus(target: State) = target + Transition(source, label)

  companion object {
    operator fun invoke(builder: GraphBuilder.() -> Unit) =
      GraphBuilder().also { it.builder() }.graph.reversed()
  }
}