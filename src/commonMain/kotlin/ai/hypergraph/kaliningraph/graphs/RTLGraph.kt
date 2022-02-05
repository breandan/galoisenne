package ai.hypergraph.kaliningraph.graphs

import ai.hypergraph.kaliningraph.randomString
import ai.hypergraph.kaliningraph.types.Edge
import ai.hypergraph.kaliningraph.types.Graph
import ai.hypergraph.kaliningraph.types.IGF
import ai.hypergraph.kaliningraph.types.Vertex
import kotlin.reflect.KProperty

class RTLBuilder {
    var graph = RTLGraph()

    var A by RTLVar("A", this); var B by RTLVar("B", this)
    var C by RTLVar("C", this); var D by RTLVar("D", this)
    var E by RTLVar("E", this); var F by RTLVar("F", this)
    var G by RTLVar("G", this); var H by RTLVar("H", this)
    var I by RTLVar("I", this); var J by RTLVar("J", this)
    var K by RTLVar("K", this); var L by RTLVar("L", this)
    var M by RTLVar("M", this); var N by RTLVar("N", this)
    var O by RTLVar("O", this); var P by RTLVar("P", this)
    var Q by RTLVar("Q", this); var R by RTLVar("R", this)
    var S by RTLVar("S", this); var T by RTLVar("T", this)
    var U by RTLVar("U", this); var V by RTLVar("V", this)
    var W by RTLVar("W", this); var X by RTLVar("X", this)
    var Y by RTLVar("Y", this); var Z by RTLVar("Z", this)

    var RAM by RTLVar(builder=this)
    val mostRecent: MutableMap<RTLGate, RTLGate> = mutableMapOf()

    operator fun Any.plus(that: RTLGate) = that * this// wrap(this, that) { a, b -> a + b }
    operator fun Any.times(that: RTLGate) = that * this// wrap(this, that) { a, b -> a * b }

    fun malloc(that: Any) = RTLGate(RTLOps.malloc, this, RTLGate.wrap(that))

    fun wrap(left: Any, right: Any, op: (RTLGate, RTLGate) -> RTLGate): RTLGate =
        op(RTLGate.wrap(left), RTLGate.wrap(right))
}

operator fun Any.plus(that: RTLGate) = RTLGate.wrap(this, that) { a, b -> a + b }
operator fun Any.times(that: RTLGate) = RTLGate.wrap(this, that) { a, b -> a * b }

interface RTLFamily: IGF<RTLGraph, RTLEdge, RTLGate> {
    override val E: (s: RTLGate, t: RTLGate) -> RTLEdge
        get() = { s: RTLGate, t: RTLGate -> RTLEdge(s, t) }
    override val V: (old: RTLGate, edgeMap: (RTLGate) -> Set<RTLEdge>) -> RTLGate
        get() = { old: RTLGate, edgeMap: (RTLGate) -> Set<RTLEdge> -> RTLGate(old, edgeMap) }
    override val G: (vertices: Set<RTLGate>) -> RTLGraph
        get() = { vertices: Set<RTLGate> -> RTLGraph(vertices) }
}

open class RTLGraph(override val vertices: Set<RTLGate> = setOf(), val root: RTLGate? = vertices.firstOrNull()) :
    Graph<RTLGraph, RTLEdge, RTLGate>(vertices), RTLFamily {
    constructor(vertices: Set<RTLGate> = setOf()): this(vertices, vertices.firstOrNull())
    constructor(build: RTLBuilder.() -> Unit) : this(RTLBuilder().also { it.build() }.graph.reversed())
}

@Suppress("ClassName")
object RTLOps {
    abstract class TopRTLOp { override fun toString() = this::class.simpleName!! }
    object id : TopRTLOp(), Monad
    object sum : TopRTLOp(), Monad, Dyad
    object prod : TopRTLOp(), Dyad
    object set : Ops.TopOp(), Dyad
    object get: Ops.TopOp(), Monad
    object malloc: Ops.TopOp(), Monad
}

open class RTLGate(
    id: String = randomString(),
    val op: Op = RTLOps.id,
    // Allows a RTLGate to access its builder and update the graph before finalization
    open val builder: RTLBuilder? = null,
    override val edgeMap: (RTLGate) -> Set<RTLEdge>
) : Vertex<RTLGraph, RTLEdge, RTLGate>(id), RTLFamily {
    constructor(op: Op = RTLOps.id, builder: RTLBuilder? = null, vararg gates: RTLGate) : this(randomString(), op, builder, *gates)
    constructor(id: String, builder: RTLBuilder? = null, vararg gates: RTLGate) : this(id, RTLOps.id, builder, *gates)
    constructor(id: String, op: Op = RTLOps.id, builder: RTLBuilder? = null, vararg gates: RTLGate) :
            this(id, op, builder, { s -> gates.toSet().map { t -> RTLEdge(s, t) }.toSet() })
    constructor(id: String, builder: RTLBuilder? = null, edgeMap: (RTLGate) -> Set<RTLEdge>): this(id, RTLOps.id, builder, edgeMap)
    constructor(gate: RTLGate, edgeMap: (RTLGate) -> Set<RTLEdge>) : this(gate.id, gate.op, null, edgeMap)

    companion object {
        fun wrap(value: Any): RTLGate = if (value is RTLGate) value.mostRecentInstance() else RTLGate(value.toString())
        fun wrap(left: Any, right: Any, op: (RTLGate, RTLGate) -> RTLGate): RTLGate = op(wrap(left), wrap(right))
        fun wrapAll(vararg values: Any): Array<RTLGate> = values.map { wrap(it) }.toTypedArray()
    }

    override fun toString() = if(op == RTLOps.id) id else op.toString()

    operator fun get(that: Any) = RTLGate(RTLOps.get, builder(), mostRecentInstance(), wrap(that))
    operator fun plus(that: Any) = RTLGate(RTLOps.sum, builder(), mostRecentInstance(), wrap(that))
    operator fun times(that: Any) = RTLGate(RTLOps.prod, builder(), mostRecentInstance(), wrap(that))

    operator fun getValue(a: Any?, prop: KProperty<*>): RTLGate = RTLGate(prop.name, a as? RTLBuilder)
    open operator fun setValue(builder: RTLBuilder, prop: KProperty<*>, value: RTLGate) {
        val builder = builder()
        val newGate = RTLGate(
            builder().mostRecent[this]?.let { it.id + "'" } ?: prop.name,
            builder,
            RTLGate(RTLOps.set, builder, value)
        )
        builder.graph += newGate.let { RTLGraph(it.graph, it) }
        builder.mostRecent[this] = newGate
    }

    fun builder() = (if (builder != null) builder else graph.vertices.firstOrNull { it.builder != null }!!.builder)!!

    fun mostRecentInstance() = builder().mostRecent[this] ?: this

    operator fun set(index: Any, value: Any) {
        val builder = builder()
        val mri = mostRecentInstance()
        val newGate = RTLGate(
            mri.id + "'", builder, RTLGate(
                RTLOps.set,
                builder,
                mri,
                wrap(value),
                wrap(index),
            )
        )
        builder.graph += newGate.let { RTLGraph(vertices = it.graph, root = it) }
        builder.mostRecent[this] = newGate
    }
}

class RTLVar(val name: String = randomString(), override val builder: RTLBuilder) : RTLGate(name, builder)

open class RTLEdge(override val source: RTLGate, override val target: RTLGate):
    Edge<RTLGraph, RTLEdge, RTLGate>(source, target), RTLFamily