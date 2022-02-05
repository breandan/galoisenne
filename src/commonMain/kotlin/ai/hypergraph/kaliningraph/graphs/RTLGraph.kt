package ai.hypergraph.kaliningraph.graphs

import ai.hypergraph.kaliningraph.randomString
import ai.hypergraph.kaliningraph.types.Edge
import ai.hypergraph.kaliningraph.types.Graph
import ai.hypergraph.kaliningraph.types.IGF
import ai.hypergraph.kaliningraph.types.Vertex
import kotlin.reflect.KProperty

class RTLBuilder {
    var graph = RTLGraph()

    var A by RTLVar("A", builder=this); var B by RTLVar("B", builder=this); var C by RTLVar("C",builder=this); var D by RTLVar("D", builder=this)
    var E by RTLVar("E", builder=this); var F by RTLVar("F", builder=this); var G by RTLVar("G",builder=this); var H by RTLVar("H", builder=this)
    var I by RTLVar("I", builder=this); var J by RTLVar("J", builder=this); var K by RTLVar("K",builder=this); var L by RTLVar("L", builder=this)
    var M by RTLVar("M", builder=this); var N by RTLVar("N", builder=this); var O by RTLVar("O",builder=this); var P by RTLVar("P", builder=this)
    var Q by RTLVar("Q", builder=this); var R by RTLVar("R", builder=this); var S by RTLVar("S",builder=this); var T by RTLVar("T", builder=this)
    var U by RTLVar("U", builder=this); var V by RTLVar("V", builder=this); var W by RTLVar("W",builder=this); var X by RTLVar("X", builder=this)
    var Y by RTLVar("Y", builder=this); var Z by RTLVar("Z", builder=this)

    var RAM by RTLVar(builder=this)
    val mostRecent: MutableMap<RTLGate, RTLGate> by lazy {
        mutableMapOf(
            RAM to RAM,
            A to A, B to B, C to C, D to D, E to E, F to F, G to G, H to H, I to I, J to J, K to K, L to L, M to M,
            N to N, O to O, P to P, Q to Q, R to R, S to S, T to T, U to U, V to V, W to W, X to X, Y to Y, Z to Z,
        )
    }

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
    open val builder: RTLBuilder? = null,
    override val edgeMap: (RTLGate) -> Set<RTLEdge>
) : Vertex<RTLGraph, RTLEdge, RTLGate>(id), RTLFamily {
    constructor(op: Op = RTLOps.id, builder: RTLBuilder? = null, vararg gates: RTLGate) : this(randomString(), op, builder, *gates)
    constructor(id: String, builder: RTLBuilder? = null, vararg gates: RTLGate) : this(id, RTLOps.id, builder, *gates)
    constructor(id: String, op: Op = RTLOps.id, builder: RTLBuilder? = null, vararg gates: RTLGate) :
            this(id, op, builder, { s -> gates.toSet().map { t -> RTLEdge(s, t) }.toSet() })
    constructor(id: String, builder: RTLBuilder? = null, edgeMap: (RTLGate) -> Set<RTLEdge>): this(id, RTLOps.id, builder, edgeMap)
    constructor(gate: RTLGate, edgeMap: (RTLGate) -> Set<RTLEdge>) : this(gate.id, null, edgeMap)

    companion object {
        fun wrap(value: Any): RTLGate = if (value is RTLGate) value.mostRecentInstance() else RTLGate(value.toString())
        fun wrap(left: Any, right: Any, op: (RTLGate, RTLGate) -> RTLGate): RTLGate = op(wrap(left), wrap(right))
        fun wrapAll(vararg values: Any): Array<RTLGate> = values.map { wrap(it) }.toTypedArray()
    }

    override fun toString() = if(op == RTLOps.id) id else op.toString()

    operator fun get(that: Any) = RTLGate(RTLOps.get, findBuilder(), mostRecentInstance(), wrap(that))
    operator fun plus(that: Any) = RTLGate(RTLOps.sum, findBuilder(), mostRecentInstance(), wrap(that))
    operator fun times(that: Any) = RTLGate(RTLOps.prod, findBuilder(), mostRecentInstance(), wrap(that))

    operator fun getValue(a: Any?, prop: KProperty<*>): RTLGate = RTLGate(prop.name, a as? RTLBuilder)
    open operator fun setValue(builder: RTLBuilder, prop: KProperty<*>, value: RTLGate) {
        val builder = findBuilder()
        val mri = mostRecentInstance()
        val newGate = RTLGate(prop.name, builder, RTLGate(RTLOps.set, builder, value))
        builder.graph += newGate.let { RTLGraph(it.graph, it) }
        builder.mostRecent[this] = newGate
    }

    fun findBuilder() = (if (builder != null) builder else graph.vertices.firstOrNull { it.builder != null }!!.builder)!!

    fun mostRecentInstance() = findBuilder().mostRecent[this] ?: this

    operator fun set(index: Any, value: Any) {
        val builder = findBuilder()
        val mri = mostRecentInstance()
        val newGate = RTLGate(
            mri.id + "'", builder, RTLGate(
                RTLOps.set,
                builder,
                mri, //this, //TODO: WHY??
                wrap(value),
                wrap(index),
            )
        )
        builder.graph += newGate.let { RTLGraph(vertices = it.graph, root = it) }
        builder.mostRecent[this] = newGate
    }
}

class RTLVar(val name: String = randomString(), override val builder: RTLBuilder) : RTLGate(name)

open class RTLEdge(override val source: RTLGate, override val target: RTLGate):
    Edge<RTLGraph, RTLEdge, RTLGate>(source, target), RTLFamily