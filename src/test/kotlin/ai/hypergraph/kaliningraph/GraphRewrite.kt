package ai.hypergraph.kaliningraph.rewriting

import ai.hypergraph.kaliningraph.*
import ai.hypergraph.kaliningraph.circuits.*
import ai.hypergraph.kaliningraph.typefamily.isA

fun main() {
    val originalGraph = ComputationGraph { f = a + 3 }.also { it.show() }
    val rewrittenGraph = originalGraph.replace(
        replacementPattern = ComputationGraph { f = a + 3 }.toTypedGraph().first { it.outdegree == 0 },
        substitution = { ComputationGraph { f = 3 + a }.root!! }
    ).also { it.show() }
}

fun ComputationGraph.replace(replacementPattern: TypedVertex<Any>, substitution: (Gate) -> Gate): ComputationGraph =
    root!!.replace(replacementPattern, substitution).graph

fun ComputationGraph.toTypedGraph() = root!!.type().graph.reversed()

private fun Gate.type(): TypedVertex<Any> =
    if (incoming.isEmpty()) TypedVertex(myType(), emptySet())
    else TypedVertex(t = myType(), out = incoming.map { it.source.type() }.toSet())

fun Gate.myType(): Class<*> = op.javaClass.interfaces.first()

fun Gate.replace(replacementPattern: TypedVertex<Any>, substitution: (Gate) -> Gate): Gate =
    if (this.matches(replacementPattern)) substitution(this)
    else Gate(op, *incoming.map { it.source.replace(replacementPattern, substitution) }.toTypedArray())

// Type check a computation graph
fun Gate.matches(other: TypedVertex<*>): Boolean =
    if (incoming.isEmpty() && other.incoming.isEmpty()) op isA other.t as Any
    else if (incoming.isEmpty() || other.incoming.isEmpty() || !(op isA other.t as Any)) false
    else incoming.zip(other.incoming).map { (a, b) -> a.source to b.source }
        .all { (a, b) -> a.matches(b) }

// TODO: implement subgraph isomorphism search using SAT solver?