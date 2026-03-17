package ai.hypergraph.kaliningraph.repair

import javax.script.Compilable
import javax.script.ScriptEngine
import javax.script.ScriptEngineManager
import javax.script.ScriptException

// TODO: Migrate to: https://kotlin.github.io/analysis-api/file-compilation.html
object KotlinTypeChecker {
  private val engine: ScriptEngine by lazy {
    ScriptEngineManager().getEngineByExtension("kts")
      ?: ScriptEngineManager().getEngineByName("kotlin")
      ?: error("Kotlin JSR-223 engine not found on the classpath")
  }

  fun typeChecks(src: String): Boolean = try {
    (engine as Compilable).compile(src.replace("Bool", "Boolean"))
    true
  } catch (e: ScriptException) {
    System.err.println(e.message)
    false
  }
}
