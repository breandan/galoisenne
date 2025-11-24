package ai.hypergraph.kaliningraph.repair

import ai.hypergraph.kaliningraph.automata.initiateSerialRepair
import ai.hypergraph.kaliningraph.parsing.*
import ai.hypergraph.kaliningraph.tokenizeByWhitespace
import com.sun.net.httpserver.*
import java.net.*
import java.nio.charset.StandardCharsets
import java.util.*
import kotlin.math.min
import kotlin.test.Test

class RepairServer {
  val map : Map<Int, CFG> = mutableMapOf()

  fun handleRequest(template: String, cfg: String): String {
    val cfgHash = cfg.hashCode()
    val cfg = if (cfgHash in map) map[cfgHash] else {
      cfg.trimIndent().lines().map { it.split(" -> ").let { Pair(it[0], it[1].split(" ")) } }
        .toSet().freeze()
    }!!

    val tks = template.tokenizeByWhitespace()
    return (if (tks.any { it == "_" })
      cfg.startPTree(tks)?.sampleStrWithoutReplacement()
    else
      initiateSerialRepair(tks,cfg)
    )?.take(1000000)?.joinToString("\n")?: "null"
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.repair.RepairServer.testRepairServer"
*/
//  @Test
  fun testRepairServer() {
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 8088), 0)

    server.createContext("/repair") { ex ->
      try {
        val params = mutableMapOf<String, String>()

        val ctype = ex.requestHeaders.getFirst("Content-Type")?.lowercase() ?: ""

        // Fallbacks: GET ?s1_b64=&s2_b64= or POST form
        ex.requestURI.rawQuery?.let { params.putAll(parseForm(it)) }
        if (ex.requestMethod.equals("POST", true) &&
          ctype.startsWith("application/x-www-form-urlencoded")) {
          val body = ex.requestBody.bufferedReader(StandardCharsets.UTF_8).readText()
          params.putAll(parseForm(body))
        }

        val s1 = decodeB64ToUtf8(params["s1_b64"])
        val s2 = decodeB64ToUtf8(params["s2_b64"])

        // Log first 10 code points of each (emoji-safe)
        println("RepairServer: s1[0..10]='${firstTen(s1)}' | s2[0..10]='${firstTen(s2)}'")

        // Respond with concatenation of the *decoded* strings
        val resp = handleRequest(s1, s2)
        val bytes = resp.toByteArray(StandardCharsets.UTF_8)
        ex.responseHeaders.add("Content-Type", "text/plain; charset=utf-8")
        ex.sendResponseHeaders(200, bytes.size.toLong())
        ex.responseBody.use { it.write(bytes) }
      } catch (_: Throwable) {
        val msg = "bad request\n".toByteArray(StandardCharsets.UTF_8)
        ex.sendResponseHeaders(400, msg.size.toLong())
        ex.responseBody.use { it.write(msg) }
      } finally {
        ex.close()
      }
    }

    server.createContext("/ping") { ex: HttpExchange ->
      val bytes = "pong\n".toByteArray(StandardCharsets.UTF_8)
      ex.sendResponseHeaders(200, bytes.size.toLong())
      ex.responseBody.use { it.write(bytes) }
      ex.close()
    }

    server.executor = null
    server.start()
    println("RepairServer listening on http://127.0.0.1:8088/repair (expects Base64 s1_b64, s2_b64)")
    Thread.sleep(Long.MAX_VALUE) // run forever
  }

  private fun parseForm(s: String): Map<String, String> =
    if (s.isEmpty()) emptyMap() else buildMap {
      s.split("&").forEach { kv ->
        val i = kv.indexOf('=')
        if (i >= 0) put(urlDecode(kv.substring(0, i)), urlDecode(kv.substring(i + 1)))
        else if (kv.isNotEmpty()) put(urlDecode(kv), "")
      }
    }

  private fun urlDecode(x: String) = URLDecoder.decode(x, StandardCharsets.UTF_8)

  private fun decodeB64ToUtf8(b64: String?): String =
    try {
      if (b64.isNullOrEmpty()) ""
      else String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8)
    } catch (_: IllegalArgumentException) {
      "" // invalid Base64 -> treat as empty
    }

  private fun firstTen(s: String): String {
    val want = min(10, s.codePointCount(0, s.length))
    val endIdx = s.offsetByCodePoints(0, want)
    return s.take(endIdx)
  }
}
