import guru.nidi.graphviz.attribute.*
import guru.nidi.graphviz.edge
import guru.nidi.graphviz.engine.Format
import guru.nidi.graphviz.engine.Renderer
import guru.nidi.graphviz.graph
import guru.nidi.graphviz.model.Factory.mutNode
import guru.nidi.graphviz.node
import guru.nidi.graphviz.toGraphviz
import java.io.File
import java.util.*

fun main() {
    println("Hello Kaliningraph!")

    val t = Node("t", Node("q", Node("t", Node("p"))))

    val dg = buildGraph {
        val t = d - a - c - b - e
        val g = d - c - e

        val m = a - b - c - d
        val n = c - d - e

        Graph(t, g, d - e).reversed()
    }

    println("Nodes: " + dg.V)

    dg.show()
}

class Node(
    val id: String = UUID.randomUUID().toString(),
    val out: Set<Node> = emptySet()
) {
    constructor(id: String = UUID.randomUUID().toString(), vararg nodes: Node) :
            this(id, nodes.toSet())

    val edges: Set<Edge> = out.map { Edge(this, it) }.toSet()

    fun Set<Node>.neighbors() = flatMap { it.neighbors() }.toSet()

    tailrec fun neighbors(k: Int = 0, neighbors: Set<Node> = out + this): Set<Node> =
        if (k == 0 || neighbors.neighbors() == neighbors) neighbors
        else neighbors(k - 1, neighbors + neighbors.neighbors() + this)

    fun asGraph() = Graph(neighbors(-1))

    override fun toString() = id

    override fun hashCode() = id.hashCode()

    operator fun minus(node: Node) = Node(node.id, node.out + this)

    operator fun plus(node: Node) = asGraph() + node.asGraph()

    override fun equals(other: Any?) = (other as? Node)?.id == id
}

class Edge(
    val source: Node, val target: Node,
    val id: String = UUID.randomUUID().toString()
)

class Graph(
    val V: Set<Node> = emptySet(),
    val A: Map<Node, Set<Node>> = V.map { it to it.out }.toMap()
) : Set<Node> by V {
    constructor(vararg graphs: Graph) : this(graphs.toList())
    constructor(vararg nodes: Node) : this(nodes.map { it.asGraph() })
    constructor(graphs: List<Graph>) : this(graphs.fold(Graph()) { it, acc -> it + acc }.V)
    constructor(adjacency: Map<Node, Set<Node>>) : this(adjacency.keys, adjacency)

    val nodesById = V.map { it.id to it }.toMap()

    operator fun plus(that: Graph) = Graph(
        (this - that) +
         intersect(that).map { Node(it.id, A[it] as Set<Node> + that.A[it] as Set<Node>) } +
        (that - this)
    )

    fun intersect(graph: Graph) = V.intersect(graph.V)

    operator fun minus(graph: Graph) = Graph(V - graph.V)

    operator fun get(node: Node) = nodesById[node.id]

    fun Map<Node, Set<Node>>.reversed(): Map<Node, Set<Node>> =
        keys.map { it to emptySet<Node>() }.toMap() +
                flatMap { (k, v) -> v.map { it to k } }.groupBy { it.first }
                    .map { (k, v) -> k to v.map { it.second }.toSet() }.toMap()

    fun reversed(): Graph = Graph(A.reversed())
}

object GraphBuilder {
    val a = Node("a")
    val b = Node("b")
    val c = Node("c")
    val d = Node("d")
    val e = Node("e")
}

fun buildGraph(builder: GraphBuilder.() -> Graph) = builder(GraphBuilder)

val DARKMODE = false
val THICKNESS = 2

inline fun render(format: Format = Format.SVG, crossinline op: () -> Unit) =
    graph(directed = true) {
        val color = Color.BLACK

        edge[color, Arrow.NORMAL, Style.lineWidth(THICKNESS)]

        graph[Rank.dir(Rank.RankDir.LEFT_TO_RIGHT), Color.TRANSPARENT.background()]

        node[color, color.font(), Font.config("Helvetica", 20), Style.lineWidth(THICKNESS)]

        op()
    }.toGraphviz().render(format)

fun Renderer.show() = toFile(File.createTempFile("temp", ".svg")).show()
fun Graph.show() = render {
    A.forEach { (k, v) -> v.forEach { mutNode(it.id).addLink(k.id) } }
}.show()

fun File.show() = ProcessBuilder("x-www-browser", path).start()