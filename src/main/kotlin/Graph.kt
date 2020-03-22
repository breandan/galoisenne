fun main() {
    println("Hello Kaliningraph!")

    val dg = buildGraph {
        Graph(
            (e + d + c) * (b + c + e),
            e * (a + b + c)
        )
    }
}

class Node(val label: String, override val neighbors: Graph = Graph()) : Graph() {
    constructor(node: Node, newNeighbors: Graph) : this(node.label, node.neighbors + newNeighbors)

    override val nodes = setOf(this)
    override fun hashCode() = label.hashCode()
    override fun equals(other: Any?) = when {
        this === other -> true
        javaClass != other?.javaClass -> false
        label != (other as Node).label -> false
        else -> true
    }

    tailrec fun neighbors(k: Int = 0, neighbors: Graph = this.neighbors): Graph =
        if (k == 0 || neighbors.neighbors == neighbors) neighbors
        else neighbors(k - 1, neighbors.neighbors)
}

open class Graph(nodeConfig: List<Node>): Set<Node> {
    open val nodes: Set<Node> = nodeConfig.map { it.neighbors(-1) }.fold(Graph()) { acc, graph -> acc + graph }
    open val neighbors: Graph = Graph(nodes.flatMap { it.neighbors })
    constructor(vararg graphs: Graph) : this(graphs.fold(Graph()) { j, k -> j + k }.nodes)
    open operator fun plus(other: Graph) = Graph(nodes + other.nodes)
    constructor(nodes: Set<Node>) : this(nodes.toList())

    open operator fun times(graph: Graph) = Graph(nodes.map { Node(it.label, graph) })
    override val size: Int = nodes.size
    override fun contains(element: Node) = nodes.contains(element)
    override fun containsAll(elements: Collection<Node>) = nodes.containsAll(elements)
    override fun isEmpty() = nodes.isEmpty()
    override fun iterator() = nodes.iterator()
}

object GraphBuilder {
    val a = Node("a")
    val b = Node("b")
    val c = Node("c")
    val d = Node("d")
    val e = Node("e")
}

fun buildGraph(builder: GraphBuilder.() -> Graph) = builder(GraphBuilder)