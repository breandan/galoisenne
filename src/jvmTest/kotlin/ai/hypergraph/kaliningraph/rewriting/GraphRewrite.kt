package ai.hypergraph.kaliningraph.rewriting

import ai.hypergraph.kaliningraph.graphs.*
import ai.hypergraph.kaliningraph.visualization.show
import org.junit.jupiter.api.*

class GraphRewriteTests {
  @Test
  fun simpleRewriteTest() {
    val originalGraph = ComputationGraph { f = a + 3 }
    val substitution = ComputationGraph { f = 3 + a }
    val rewrittenGraph = originalGraph.replace(
      replacementPattern = originalGraph.root!!,
      substitution = { substitution.root!! }
    ).also { it.show() }

    Assertions.assertNotEquals(originalGraph, rewrittenGraph)
  }
}

fun ComputationGraph.replace(replacementPattern: Gate, substitution: (Gate) -> Gate): ComputationGraph =
    root!!.replace(replacementPattern, substitution).graph

private fun Gate.type(): TypedVertex<Any> =
  if (incoming.isEmpty()) TypedVertex(myType(), emptySet())
  else TypedVertex(t = myType(), out = incoming.map { it.source.type() }.toSet())

fun Gate.myType(): Class<*> = op.javaClass.interfaces.first()

fun Gate.replace(replacementPattern: Gate, substitution: (Gate) -> Gate): Gate =
  if (this.matches(replacementPattern)) substitution(this)
  else Gate(op, *incoming.map { it.source.replace(replacementPattern, substitution) }.toTypedArray())

// Type check a computation graph
fun Gate.matches(other: Gate): Boolean =
  if (incoming.isEmpty() && other.incoming.isEmpty()) op == other.op as Any
  else if (incoming.isEmpty() || other.incoming.isEmpty() || !(op == other.op)) false
  else incoming.zip(other.incoming).map { (a, b) -> a.source to b.source }
    .all { (a, b) -> a.matches(b) }

// TODO: implement subgraph isomorphism search using SAT solver?