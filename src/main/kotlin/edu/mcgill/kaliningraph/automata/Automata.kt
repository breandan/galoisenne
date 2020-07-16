package edu.mcgill.kaliningraph.automata

import edu.mcgill.kaliningraph.Edge
import edu.mcgill.kaliningraph.Graph
import edu.mcgill.kaliningraph.Node

open class State(transition: (State) -> Collection<Transition>, val occupied: Boolean): Node<State>() {
  override val edges = transition(this).toSet()
  override val neighbors = edges.map { it.nextState }.toSet()
}

open class Transition(val nextState: State, val string: String): Edge<State>(nextState, string)

open class Automata(override val V: Set<State>): Graph<State>()