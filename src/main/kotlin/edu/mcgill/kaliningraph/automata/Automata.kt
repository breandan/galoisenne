package edu.mcgill.kaliningraph.automata

import edu.mcgill.kaliningraph.Edge
import edu.mcgill.kaliningraph.Graph
import edu.mcgill.kaliningraph.Vertex

open class State(transition: (State) -> Collection<Transition>, val occupied: Boolean): Vertex() {
  override val edges = transition(this).toSet()
  override val neighbors: Set<Vertex> = edges.map { it.target }.toSet()
}

open class Transition(val nextState: State, val string: String): Edge(nextState, string)

open class Automata(override val V: Set<State>): Graph()