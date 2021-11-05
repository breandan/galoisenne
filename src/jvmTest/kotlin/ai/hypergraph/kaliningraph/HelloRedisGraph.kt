package ai.hypergraph.kaliningraph

import com.redislabs.redisgraph.RedisGraphTransaction
import com.redislabs.redisgraph.graph_entities.*
import com.redislabs.redisgraph.impl.api.RedisGraph

// First run: docker run -p 6379:6379 -it --rm redislabs/redisgraph

fun main() {
  // general context api. Not bound to graph key or connection
  val graph = RedisGraph()
  val params: Map<String, Any> = mapOf("age" to 30, "name" to "amit")

  // send queries to a specific graph called "social"
  graph.query("social", "CREATE (:person{name:'roi',age:32})")
  graph.query("social", "CREATE (:person{name:\$name,age:\$age})", params)
  graph.query(
    "social",
    "MATCH (a:person), (b:person) WHERE (a.name = 'roi' AND b.name='amit') CREATE (a)-[:knows]->(b)"
  )
  graph.query("social", "MATCH (a:person)-[r:knows]->(b:person) RETURN a, r, b").forEach { record ->
    // get values
    val a: Node = record.getValue("a")
    val r: Edge = record.getValue("r")

    //print record
    println(record.toString())
  }
  graph.query("social", "MATCH p = (:person)-[:knows]->(:person) RETURN p").forEach { record ->
    val p: Path = record.getValue("p")

    // More path API at Javadoc.
    println(p.nodeCount())
  }

  // delete graph
  graph.deleteGraph("social")
  graph.context.use { context ->
    context.query("contextSocial", "CREATE (:person{name:'roi',age:32})")
    context.query("social", "CREATE (:person{name:\$name,age:\$age})", params)
    context.query(
      "contextSocial",
      "MATCH (a:person), (b:person) WHERE (a.name = 'roi' AND b.name='amit') CREATE (a)-[:knows]->(b)"
    )
    // WATCH/MULTI/EXEC
    context.watch("contextSocial")
    val t: RedisGraphTransaction = context.multi()
    t.query("contextSocial", "MATCH (a:person)-[r:knows]->(b:person{name:\$name,age:\$age}) RETURN a, r, b", params)
    // support for Redis/Jedis native commands in transaction
    t.set("x", "1")
    t.get("x")
    // get multi/exec results
    val execResults: List<Any> = t.exec()
    println(execResults.toString())
    context.deleteGraph("contextSocial")
  }
}
