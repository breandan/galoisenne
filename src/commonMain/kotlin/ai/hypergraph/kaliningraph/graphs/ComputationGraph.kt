package ai.hypergraph.kaliningraph.graphs

import ai.hypergraph.kaliningraph.graphs.Gate.Companion.wrap
import ai.hypergraph.kaliningraph.randomString
import ai.hypergraph.kaliningraph.types.*
import kotlin.reflect.KProperty

// Mutable environment with support for variable overwriting/reassignment
class CircuitBuilder {
  var graph = ComputationGraph()

  var a by Var(); var b by Var(); var c by Var(); var d by Var()
  var e by Var(); var f by Var(); var g by Var(); var h by Var()
  var i by Var(); var j by Var(); var k by Var(); var l by Var()

  operator fun Any.plus(that: Any) = wrap(this, that) { a, b -> a + b }
  operator fun Any.minus(that: Any) = wrap(this, that) { a, b -> a - b }
  operator fun Any.times(that: Any) = wrap(this, that) { a, b -> a * b }
  operator fun Any.div(that: Any) = wrap(this, that) { a, b -> a / b }

  fun wrap(left: Any, right: Any, op: (Gate, Gate) -> Gate): Gate =
    op(wrap(left), wrap(right))
}

val a by Var(); val b by Var(); val c by Var(); val d by Var()
val e by Var(); val f by Var(); val g by Var(); val h by Var()
val i by Var(); val j by Var(); val k by Var(); val l by Var()
fun def(vararg params: Gate, body: (Array<out Gate>) -> Gate) =
  NFunction(randomString(), params, body)

operator fun Any.plus(that: Gate) = wrap(this, that) { a, b -> a + b }
operator fun Any.minus(that: Gate) = wrap(this, that) { a, b -> a - b }
operator fun Any.times(that: Gate) = wrap(this, that) { a, b -> a * b }
operator fun Any.div(that: Gate) = wrap(this, that) { a, b -> a / b }

interface CGFamily: IGF<ComputationGraph, UnlabeledEdge, Gate> {
  override val E: (s: Gate, t: Gate) -> UnlabeledEdge
    get() = { s: Gate, t: Gate -> UnlabeledEdge(s, t) }
  override val V: (old: Gate, edgeMap: (Gate) -> Set<UnlabeledEdge>) -> Gate
    get() = { old: Gate, edgeMap: (Gate) -> Set<UnlabeledEdge> -> Gate(old, edgeMap) }
  override val G: (vertices: Set<Gate>) -> ComputationGraph
    get() = { vertices: Set<Gate> -> ComputationGraph(vertices) }
}

open class ComputationGraph(override val vertices: Set<Gate> = setOf(),
                            val root: Gate? = vertices.firstOrNull()) :
  Graph<ComputationGraph, UnlabeledEdge, Gate>(vertices), CGFamily {
    constructor(vertices: Set<Gate> = setOf()) : this(vertices, vertices.firstOrNull())
    constructor(builder: CircuitBuilder.() -> Unit) : this(CircuitBuilder().also { it.builder() }.graph)
}

interface Op
interface Dyad: Op
interface Monad: Op
interface Polyad: Op
interface TrigFun: Monad
@Suppress("ClassName")
object Ops {
  abstract class TopOp { override fun toString() = this::class.simpleName!! }
  object sum : TopOp(), Monad, Dyad
  object sub : TopOp(), Monad, Dyad
  object sin : TopOp(), TrigFun
  object cos : TopOp(), TrigFun
  object tan : TopOp(), TrigFun
  object id : TopOp(), Monad
  object transpose: TopOp(), Monad

  object prod : TopOp(), Dyad
  object odot : TopOp(), Dyad
  object ratio : TopOp(), Dyad

  object eql : TopOp(), Dyad
  object dot : TopOp(), Dyad
  object pow : TopOp(), Dyad
  object log : TopOp(), Dyad
  object d : TopOp(), Dyad

  object λ : TopOp(), Polyad
  object Σ : TopOp(), Polyad
  object Π : TopOp(), Polyad
  object map : TopOp(), Polyad
}

open class Gate(
  id: String = randomString(),
  val op: Op = Ops.id,
  override val edgeMap: (Gate) -> Set<UnlabeledEdge>
) : Vertex<ComputationGraph, UnlabeledEdge, Gate>(id), CGFamily {
  constructor(op: Op = Ops.id, vararg gates: Gate) : this(randomString(), op, *gates)
  constructor(id: String = randomString(), vararg gates: Gate) : this(id, Ops.id, *gates)
  constructor(id: String = randomString(), op: Op = Ops.id, vararg gates: Gate) :
    this(id, op, { s -> gates.toSet().map { t -> UnlabeledEdge(s, t) }.toSet() })
  constructor(id: String = randomString(), edgeMap: (Gate) -> Set<UnlabeledEdge>): this(id, Ops.id, edgeMap)
  constructor(gate: Gate, edgeMap: (Gate) -> Set<UnlabeledEdge>) :
    this(id = gate.id, edgeMap = edgeMap)

  companion object {
    fun wrap(value: Any): Gate = if (value is Gate) value else Gate(value.toString())
    fun wrap(left: Any, right: Any, op: (Gate, Gate) -> Gate): Gate = op(wrap(left), wrap(right))
    fun wrapAll(vararg values: Any): Array<Gate> = values.map { wrap(it) }.toTypedArray()
  }

  override fun toString() = if(op == Ops.id) id else op.toString()

  operator fun plus(that: Any) = Gate(Ops.sum, this, wrap(that))
  operator fun minus(that: Any) = Gate(Ops.sub, this, wrap(that))
  operator fun times(that: Any) = Gate(Ops.prod, this, wrap(that))
  operator fun div(that: Any) = Gate(Ops.ratio, this, wrap(that))
  infix fun pow(that: Any) = Gate(Ops.pow, this, wrap(that))
  infix fun log(that: Any) = Gate(Ops.log, this, wrap(that))

  operator fun unaryMinus() = Gate(Ops.sub, this)

  fun sin() = Gate(Ops.sin, this)
  fun cos() = Gate(Ops.cos, this)
  fun tan() = Gate(Ops.tan, this)
  fun d(that: Any) = Gate(Ops.d, this, wrap(that))

  operator fun getValue(a: Any?, prop: KProperty<*>): Gate = Gate(prop.name)
  open operator fun setValue(builder: CircuitBuilder, prop: KProperty<*>, value: Gate) {
    builder.graph += Gate(prop.name, Gate(Ops.eql, value)).let {
     ComputationGraph(vertices=it.graph/* TODO: Is this double-boxing a problem? */, root = it)
    }
  }
}

class Var(val name: String = randomString()) : Gate(name)

class NFunction(
  val name: String = randomString(),
  val params: Array<out Gate> = arrayOf(),
  val body: (Array<out Gate>) -> Gate = { Gate(name) }
): Gate(id = name, edgeMap = { s -> setOf(UnlabeledEdge(s, body(params))) }), CGFamily {
  operator fun invoke(vararg args: Any): Gate =
    if (arityMatches(*args))
      Gate(Ops.λ, *wrapAll(*args).let { it.plusElement(Gate(name, Gate(Ops.eql, body(it)))) })
    else throw Exception(invokeError(*args))

  fun invokeError(vararg args: Any) =
    "Could not invoke $name(${params.joinToString()}) with ${args.joinToString()}"

  fun arityMatches(vararg args: Any) = args.size == params.size
  operator fun getValue(nothing: Nothing?, property: KProperty<*>): NFunction =
    NFunction(property.name, params, body)
}

open class UnlabeledEdge(override val source: Gate, override val target: Gate):
  Edge<ComputationGraph, UnlabeledEdge, Gate>(source, target), CGFamily