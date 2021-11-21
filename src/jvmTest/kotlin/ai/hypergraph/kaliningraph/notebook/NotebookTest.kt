package ai.hypergraph.kaliningraph.notebook

import ai.hypergraph.kaliningraph.*
import ai.hypergraph.kaliningraph.tensor.toBMat
import io.kotest.matchers.string.shouldContain
import org.intellij.lang.annotations.Language
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

  @Test
  fun `adjacency matrix is rendered to bmp`() {
    @Language("kts")
    val html = execHtml(
      """LabeledGraph { a - b - c - a - d - c }.A""".trimIndent()
    )

    println(html)
    html shouldContain "data:image/bmp;base64,"
  }

  @Test
  fun `random matrix is rendered to bmp`() {
    @Language("kts")
    val html = execHtml(
      """randomMatrix(200, 200)""".trimIndent()
    )

    println(html)
    html shouldContain "data:image/bmp;base64,"
  }

}