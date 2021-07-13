package edu.mcgill.kaliningraph.notebook

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import jupyter.kotlin.DependsOn
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlinx.jupyter.*
import org.jetbrains.kotlinx.jupyter.api.*
import org.jetbrains.kotlinx.jupyter.libraries.EmptyResolutionInfoProvider
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.script.experimental.jvm.util.classpathFromClassloader


class RenderingTests: AbstractReplTest() {
  @Test
  fun `labeled graph is rendered to html`() {
    @Language("kts")
    val html = execHtml(
      """LabeledGraph { a - b - c - a }""".trimIndent()
    )
    html shouldContain "polygon"
  }
}

abstract class AbstractReplTest {
  private val repl: ReplForJupyter = ReplForJupyterImpl(
    EmptyResolutionInfoProvider, classpath, isEmbedded = true
  )

  @BeforeEach
  fun initRepl() {
    // Jupyter integration is loaded after some code was executed, so we do it here
    // We also define here a class to retrieve values without rendering
    exec("class $WRAP(val $WRAP_VAL: Any?)")
  }

  fun exec(code: Code): Any? = repl.eval(code).resultValue

  @JvmName("execTyped")
  inline fun <reified T: Any> exec(code: Code): T {
    val res = exec(code)
    res.shouldBeInstanceOf<T>()
    return res
  }

  fun execHtml(code: Code): String {
    val res = exec<MimeTypedResult>(code)
    val html = res["text/html"]
    html.shouldNotBeNull()
    return html
  }

  companion object {
    @JvmStatic
    protected val WRAP = "W"

    private const val WRAP_VAL = "v"

    private val classpath = classpathFromClassloader(DependsOn::class.java.classLoader).orEmpty()
  }
}