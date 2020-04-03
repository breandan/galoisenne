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

//    val dg = buildGraph {
//        Graph(
//            (e + d + c) * (b + c + e),
//            e * (a + b + c)
//        )
//    }
    val t = Node("t", Node("q", Node("t", Node("p"))))

    val dg = buildGraph {
        val t = d - a - c - b - e
        val g = d - c - e

        val m = a - b - c - d
        val n = c - d - e

        Graph(m, n)
    }

    dg + dg

    println("Neighbors: " + dg.nodes)

    dg.show()
}

class Node(
    val id: String = UUID.randomUUID().toString(),
    val neighbors: Set<Node> = hashSetOf()
) {
    constructor(id: String = UUID.randomUUID().toString(), vararg nodes: Node) :
            this(id, nodes.toSet())

    val edges: Set<Edge> = neighbors.map { Edge(this, it) }.toSet()

    fun Set<Node>.neighbors() = flatMap { it.neighbors() }.toSet()

    tailrec fun neighbors(k: Int = 0, neighbors: Set<Node> = this.neighbors + this): Set<Node> =
        if (k == 0 || neighbors.neighbors() == neighbors) neighbors
        else neighbors(k - 1, neighbors + neighbors.neighbors() + this)

    fun asGraph() = Graph(neighbors(-1))

    override fun toString() = id

    override fun hashCode() = id.hashCode()

    operator fun minus(node: Node) = Node(id, neighbors + node)

    override fun equals(other: Any?) = (other as? Node)?.id == id
}

class Edge(
    val source: Node, val target: Node,
    val id: String = UUID.randomUUID().toString()
)

class Graph(val nodes: Set<Node> = emptySet()): Set<Node> by nodes {
    constructor(graph: Graph) : this(graph.nodes)
    constructor(graphs: List<Graph>) : this(graphs.fold(Graph()) { it, acc -> it + acc})

    val nodesById = nodes.map { it.id to it }.toMap()
    val adjacency = nodes.map { it.id to it.neighbors }.toMap()

    //    constructor(vararg graphs: Graph)
    constructor(vararg nodes: Node) : this(nodes.map { it.asGraph() })

    operator fun plus(that: Graph) = Graph(
            (this - that) +
             intersect(that).map { Node(it, adjacency[it] as Set<Node> + that.adjacency[it] as Set<Node>) } +
            (that - this)
        )

    fun intersect(graph: Graph) = nodes.intersect(graph.nodes).map { it.id }

    operator fun minus(graph: Graph) = Graph(nodes - graph.nodes)

    operator fun get(node: Node) = nodesById[node.id]

//    fun render(): MutableNode =
//        nodes.flatMap { node -> node.neighbors.map { neighbor -> Pair(node, neighbor) } }
//            .map { mutNode(it.first.id) - mutNode(it.second.id) }
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
fun Graph.show() = render { nodes.forEach { node -> node.neighbors.forEach { neighbor -> mutNode(node.id).addLink(neighbor.id) } } }.show()
fun File.show() = ProcessBuilder("x-www-browser", path).start()