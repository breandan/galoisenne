fun main() {
    println("Hello Kaliningraph!")

//    val dg = buildGraph {
//        Graph(
//            (e + d + c) * (b + c + e),
//            e * (a + b + c)
//        )
//    }
    val t = Node("t", Node("q", Node("t", Node("p"))))

    println("Neighbors: " + t.neighbors.first())
}

class Node(val label: String, val neighbors: Set<Node>) {
    constructor(node: Node, newNeighbors: Graph) : this(node.label, node.neighbors + newNeighbors)
    constructor(label: String, vararg neighbors: Node): this(label, neighbors.toSet())
    override fun hashCode() = label.hashCode()

    override fun equals(other: Any?) = when {
        this === other -> true
        javaClass != other?.javaClass -> false
        label != (other as Node).label -> false
        else -> true
    }

    fun plus(other: Graph) = other + this

    fun Set<Node>.neighbors() = Graph(flatMap { it.neighbors() })

    tailrec fun neighbors(k: Int = 0, neighbors: Set<Node> = this.neighbors): Graph =
        if (k == 0 || neighbors.neighbors() == neighbors) Graph(neighbors)
        else neighbors(k - 1, neighbors.neighbors())

    override fun toString() = label
}

open class Graph(open val nodes: Set<Node>) : Set<Node> by nodes {
    constructor(nodes: List<Node>):
            this(nodes.map { it.neighbors(-1) }.fold(Graph()) { acc, graph -> acc + graph })

    constructor() : this(emptySet())

    constructor(vararg graphs: Graph):
            this(graphs.fold(Graph()) { acc, graph -> acc + graph }.nodes)

    val adjacency = HashMap<Node, Node>()

    open operator fun plus(other: Node): Graph =
        if(other in this)
            Graph(first { it == other }.neighbors() +
                    other.neighbors + filter { it != other })
        else
            Graph(nodes + other)

    operator fun plus(other: Graph): Graph = TODO()

    open operator fun times(graph: Graph) = Graph(nodes.map { Node(it.label, graph) })
    override fun contains(element: Node) = nodes.contains(element)
    override fun containsAll(elements: Collection<Node>) = nodes.containsAll(elements)
    override fun isEmpty() = nodes.isEmpty()
//    override fun iterator() = nodes.iterator()

    override fun toString() = nodes.joinToString { it.toString() }
}

object GraphBuilder {
    val a = Node("a")
    val b = Node("b")
    val c = Node("c")
    val d = Node("d")
    val e = Node("e")
}

fun buildGraph(builder: GraphBuilder.() -> Graph) = builder(GraphBuilder)