package edu.mcgill.kaliningraph.notebook

import edu.mcgill.kaliningraph.*
import edu.mcgill.kaliningraph.circuits.Gate
import edu.mcgill.kaliningraph.matrix.BMat
import org.jetbrains.kotlinx.jupyter.api.annotations.JupyterLibrary
import org.jetbrains.kotlinx.jupyter.api.*
import org.jetbrains.kotlinx.jupyter.api.libraries.*

@JupyterLibrary
internal class Integration: JupyterIntegration() {
  override fun Builder.onLoaded() {
    listOf(
      "edu.mcgill.kaliningraph.*",
      "edu.mcgill.kaliningraph.matrix.*",
      "edu.mcgill.kaliningraph.circuits.*",
      "org.ejml.data.*",
      "org.ejml.kotlin.*"
    ).forEach { import(it) }

    render<BMat> { HTML("<img src=\"${it.matToImg()}\"/>") }
    render<Graph<*, *, *>> { HTML(it.html()) }
    render<Gate> { HTML(it.graph.html()) }
    render<SpsMat> { HTML("<img src=\"${it.matToImg()}\"/>") }

    // https://github.com/Kotlin/kotlin-jupyter/blob/master/docs/libraries.md#integration-using-kotlin-api
    // https://github.com/nikitinas/dataframe/blob/master/src/main/kotlin/org/jetbrains/dataframe/jupyter/Integration.kt
    // https://github.com/mipt-npm/visionforge/blob/dev/demo/jupyter-playground/src/main/kotlin/hep/dataforge/playground/VisionForgePlayGroundForJupyter.kt
  }
}