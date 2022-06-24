package ai.hypergraph.kaliningraph.automata

import ai.hypergraph.kaliningraph.graphs.LabeledGraph
import ai.hypergraph.kaliningraph.sampling.randomString
import ai.hypergraph.kaliningraph.types.*
import kotlin.reflect.KProperty

open class Automaton(override val vertices: Set<State> = setOf(State()))
  : Graph<Automaton, Transition, State>(vertices), AGFamily

open class Transition(override val source: State, override val target: State, val string: String? = null) :
  Edge<Automaton, Transition, State>(source, target), AGFamily

open class State(
  id: String = randomString(),
  override val edgeMap: (State) -> Set<Transition>
) : Vertex<Automaton, Transition, State>(id), AGFamily {
  constructor(id: String? = null, out: Set<State> = setOf()) : this(id = id ?: randomString(),
    edgeMap = { s -> out.map { t -> Transition(s, t) }.toSet() })

  constructor(state: State, edgeMap: (State) -> Set<Transition>): this(state.id, edgeMap)

  operator fun getValue(a: Any?, prop: KProperty<*>): State = State(prop.name)
  override fun G(list: List<Any>): Automaton {
    TODO("Not yet implemented")
  }
}

interface AGFamily: IGF<Automaton, Transition, State> {
  override val G: (vertices: Set<State>) -> Automaton
    get() = { vertices -> Automaton(vertices) }
  override val E: (s: State, t: State) -> Transition
    get() = { s, t -> Transition(s, t) }
  override val V: (old: State, edgeMap: (State) -> Set<Transition>) -> State
    get() = { old, edgeMap -> State(old, edgeMap)}
}

class AutomatonBuilder {
  var automaton = Automaton()

  val a by State(); val b by State(); val c by State(); val d by State()
  val e by State(); val f by State(); val g by State(); val h by State()
  val i by State(); val j by State(); val k by State(); val l by State()

  operator fun State.minus(v: State) =
    V(this) { v.outgoing + Transition(v, this) }.also { automaton += it.graph }

  operator fun State.plus(edge: Transition) =
    V(this) { outgoing + edge }.also { automaton += it.graph }

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