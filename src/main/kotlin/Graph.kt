fun main() {
    println("Hello Kaliningraph!")

    val dg = buildGraph {
        Graph(
            (e + d + c) * (b + c + e),
            e * (a + b + c)
        )
    }
}

class Node(val label: String, val neighbors: Graph = Graph()) : Graph() {
    constructor(node: Node, newNeighbors: Graph) : this(node.label, node.neighbors + newNeighbors)

    override val nodes = setOf(this)
    override fun hashCode() = label.hashCode()

    override fun equals(other: Any?) = when {
        this === other -> true
        javaClass != other?.javaClass -> false
        label != (other as Node).label -> false
        else -> true
    }

    override fun plus(other: Graph) = when(other) {
        is Node -> Node(this, neighbors + other.neighbors)
        else -> super.plus(other)
    }

    fun Set<Node>.neighbors() = Graph(flatMap { it.neighbors() })

    tailrec fun neighbors(k: Int = 0, neighbors: Graph = this.neighbors): Graph =
        if (k == 0 || neighbors.neighbors() == neighbors) neighbors
        else neighbors(k - 1, this + neighbors.neighbors())
}

open class Graph : Set<Node> {
    constructor(nodes: List<Node>) {
        this.nodes = nodes.map { it.neighbors(-1) }.fold(Graph()) { acc, graph -> acc + graph }
        this.size = this.nodes.size
    }

    constructor(nodes: Set<Node>) : this(nodes.toList())

    constructor(vararg graphs: Graph) {
        this.nodes = graphs.flatMap { it.nodes }.toSet()
        this.size = nodes.size
    }

    open val nodes: Set<Node>
    override val size: Int

    open operator fun plus(other: Graph): Graph = when (other) {
        is Node -> other + this
        else -> this
    }

    open operator fun times(graph: Graph) = Graph(nodes.map { Node(it.label, graph) })
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