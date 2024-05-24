package ai.hypergraph.kaliningraph.notebook

//import io.kotest.matchers.string.shouldContain
//import org.intellij.lang.annotations.Language
//import org.jetbrains.kotlinx.jupyter.testkit.JupyterReplTestCase
//import org.junit.jupiter.api.Test
//import kotlin.test.*
//
///*
//./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.notebook.RenderingTests"
//*/
//class RenderingTests: JupyterReplTestCase() {
//  @Test
//  fun `labeled graph is rendered to html`() {
//    @Language("kts")
//    val html = execHtml(
//      """LabeledGraph { a - b - c - a }""".trimIndent()
//    )
//    assertTrue("polygon" in html)
//  }
//
//  @Test
//  fun `adjacency matrix is rendered to bmp`() {
//    @Language("kts")
//    val html = execHtml(
//      """LabeledGraph { a - b - c - a - d - c }.A""".trimIndent()
//    )
//    assertTrue("data:image/bmp;base64," in html)
//  }
//
//  @Test
//  fun `random matrix is rendered to bmp`() {
//    @Language("kts")
//    val html = execHtml(
//      """randomMatrix(200, 200)""".trimIndent()
//    )
//    assertTrue("data:image/bmp;base64," in html)
//  }
//}