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

        Graph(t, g)
    }

    println("Neighbors: " + dg.nodes)
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

class Graph(val nodes: Set<Node>) {
    constructor(graphs: List<Graph>) : this(graphs.flatMap { it.nodes }.toSet())

    //    constructor(vararg graphs: Graph)
    constructor(vararg nodes: Node) : this(nodes.map { it.asGraph() })

    operator fun plus(graph: Graph) =
        Graph(nodes + graph.nodes)
//            (nodes - graph.nodes) +
//             nodes.intersect(graph.nodes).map {  } +
//            (graph.nodes - nodes)
//        )
}

object GraphBuilder {
    val a = Node("a")
    val b = Node("b")
    val c = Node("c")
    val d = Node("d")
    val e = Node("e")
}

fun buildGraph(builder: GraphBuilder.() -> Graph) = builder(GraphBuilder)