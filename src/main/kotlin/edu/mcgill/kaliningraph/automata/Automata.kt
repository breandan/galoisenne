package edu.mcgill.kaliningraph.automata

import edu.mcgill.kaliningraph.Edge
import edu.mcgill.kaliningraph.Graph
import edu.mcgill.kaliningraph.GraphBuilder
import edu.mcgill.kaliningraph.Node
import kweb.random

open class State(override val id: String = randomString(), transition: (State) -> Collection<Transition>, override var occupied: Boolean = false) : Node<State>() {
  constructor(id: String? = null, out: Set<State> = setOf()) : this(id ?: randomString(), { out.map { Transition(it) } }, false)
  override val edges = transition(this).toSet()
  override val neighbors = edges.map { it.nextState }.toSet()
  override fun new(id: String?, out: Set<State>): State = State(id, out)
}

open class Transition(val nextState: State, val string: String? = null): Edge<State>(nextState, string)

open class Automaton(override val V: Set<State> = setOf(State())): Graph<State>() {
  constructor(vararg states: State) : this(states.toSet())
  override val prototype: State by lazy { V.firstOrNull() ?: State() }

}

class AutomatonBuilder {
  var graph: Graph<State> = Automaton()

  val a by State(); val b by State(); val c by State(); val d by State()
  val e by State(); val f by State(); val g by State(); val h by State()
  val i by State(); val j by State(); val k by State(); val l by State()

  operator fun State.minus(v: State) =
    State().new(v.id) { v.edges + Transition(this) }.also { graph += Automaton(it) }

  operator fun State.plus(edge: Edge<State>) =
    State().new { edges + edge }.also { graph += Graph(it) }

  operator fun State.plus(vertex: State) =
    (asGraph() + vertex.asGraph()).also { graph += it }

  class ProtoEdge(val source: State, val label: String)

  // Arithmetic is right-associative, so we construct in reverse and flip after
  operator fun State.minus(symbols: String) = ProtoEdge(this, symbols)
  operator fun ProtoEdge.minus(target: State) = target + Edge(source, label)

  companion object {
    operator fun invoke(builder: GraphBuilder.() -> Unit) =
      GraphBuilder().also { it.builder() }.graph.reversed()
  }
}