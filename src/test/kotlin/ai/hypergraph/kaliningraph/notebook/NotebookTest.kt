package ai.hypergraph.kaliningraph.notebook

import io.kotest.matchers.string.shouldContain
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlinx.jupyter.*
import org.jetbrains.kotlinx.jupyter.api.*
import org.jetbrains.kotlinx.jupyter.testkit.JupyterReplTestCase
import org.junit.jupiter.api.Test


class RenderingTests: JupyterReplTestCase() {
  @Test
  fun `labeled graph is rendered to html`() {
    @Language("kts")
    val html = execHtml(
      """LabeledGraph { a - b - c - a }""".trimIndent()
    )
    html shouldContain "polygon"
  }
}