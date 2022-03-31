package ai.hypergraph.kaliningraph.graphs

import ai.hypergraph.kaliningraph.sampling.randomString
import ai.hypergraph.kaliningraph.types.*
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

    var RAM by RTLVar(builder = this)
    val mostRecent: MutableMap<RTLGate, RTLGate> = mutableMapOf()

    operator fun Any.plus(that: RTLGate) = that * this// wrap(this, that) { a, b -> a + b }
    operator fun Any.times(that: RTLGate) = that * this// wrap(this, that) { a, b -> a * b }
    var vhdl = ""

    val Number.w: RTLGate get() = RTLGate.wrap(this, this@RTLBuilder)
//    fun malloc(that: Any) = RTLGate(RTLOps.malloc, this, RTLGate.wrap(that))
    fun malloc(that: Int) = RTLGate(RTLOps.malloc, this, RTLGate.wrap(that))
}

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

    constructor(vertices: Set<RTLGate> = setOf()) : this(vertices, vertices.firstOrNull())
    constructor(build: RTLBuilder.() -> Unit) : this(RTLBuilder().also { it.build() }.graph.reversed())

    fun vhdl(): String = last().bldr().vhdl
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
        fun wrap(value: Any, builder: RTLBuilder? = null): RTLGate = if (value is RTLGate) value.mostRecentInstance() else RTLGate(value.toString(), builder)
        fun wrap(left: Any, right: Any, op: (RTLGate, RTLGate) -> RTLGate): RTLGate = op(wrap(left), wrap(right))
        fun wrapAll(vararg values: Any): Array<RTLGate> = values.map { wrap(it) }.toTypedArray()
    }

    override fun toString() = if(op == RTLOps.id) id else op.toString()

    fun asCircuit(l: String = neighbors.firstOrNull()?.asCircuit() ?:"",
                  r: String = neighbors.lastOrNull()?.asCircuit() ?: "",
                  i: String = neighbors.firstOrNull { it.id.toIntOrNull() != null }?.id ?: ""): String = when(op) {
        RTLOps.malloc -> (1..neighbors.first().id.toInt()).joinToString("\n") { " '0'" }
        RTLOps.id -> id.dropLastWhile { it == '\'' }.let { if(it.toIntOrNull() == null) it else "'$it'" }
        RTLOps.get -> l + i
        RTLOps.sum ->
            if (l.toIntOrNull() != null && r.toIntOrNull() != null)
                "(" + (if (l.toInt() == 1 || r.toInt() == 1) 1 else 0) + ")"
            else if (l.toIntOrNull() != null) "(${if (l.toInt() > 0) '1' else '0'} or $r)"
            else if (r.toIntOrNull() != null) "($l or ${if (r.toInt() > 0) '1' else '0'})"
            else "($l or $r)"
        RTLOps.prod ->
            if (l.toIntOrNull() != null && r.toIntOrNull() != null)
                "(" + (if (l.toInt() == 1 && r.toInt() == 1) 1 else 0) + ")"
            else if (l.toIntOrNull() != null) "(${if (l.toInt() > 0) '1' else '0'} and $r)"
            else if (r.toIntOrNull() != null) "($l and ${if (r.toInt() > 0) '1' else '0'})"
            else "($l and $r)"
        else -> ""
    }

    operator fun get(that: Any) = RTLGate(RTLOps.get, bldr(), mostRecentInstance(), wrap(that))
    operator fun plus(that: Any) = RTLGate(RTLOps.sum, bldr(), mostRecentInstance(), wrap(that))
    operator fun times(that: Any) = RTLGate(RTLOps.prod, bldr(), mostRecentInstance(), wrap(that))

    operator fun getValue(a: Any?, prop: KProperty<*>): RTLGate = RTLGate(prop.name, a as? RTLBuilder)
    open operator fun setValue(builder: RTLBuilder, prop: KProperty<*>, value: Any) {
        val builder = bldr()
        val newGate = RTLGate(
            bldr().mostRecent[this]?.let { it.id + "'" } ?: prop.name,
            builder,
            RTLGate(RTLOps.set, builder, wrap(value))
        )
        builder.graph += newGate.let { RTLGraph(it.graph, it) }
        builder.vhdl += (value as RTLGate).asCircuit().lines().let {
            if (it.size > 1)
                it.mapIndexed { i, l -> "${prop.name}$i := $l;" }.joinToString("\n", "\n", "\n")
            else it.joinToString("\n", "\n", "\n") { l -> "${prop.name} := $l;" }
        }
        builder.mostRecent[this] = newGate
    }

    fun bldr() = (if (builder != null) builder else graph.vertices.firstOrNull { it.builder != null }!!.builder)!!

    fun mostRecentInstance() = bldr().mostRecent[this] ?: this

    operator fun set(index: Any, value: Any) {
        val builder = bldr()
        val mri = mostRecentInstance()
        val newGate = RTLGate(
            mri.id + "'",
            builder,
            RTLGate(
                RTLOps.set,
                builder,
                mri,
                wrap(value),
                wrap(index),
            )
        )
        builder.vhdl += "$id$index := " + (value as RTLGate).asCircuit() + ";\n"
        builder.graph += newGate.let { RTLGraph(vertices = it.graph, root = it) }
        builder.mostRecent[this] = newGate
    }
}

class RTLVar(val name: String = randomString(), override val builder: RTLBuilder) : RTLGate(name, builder)

open class RTLEdge(override val source: RTLGate, override val target: RTLGate):
    Edge<RTLGraph, RTLEdge, RTLGate>(source, target), RTLFamily