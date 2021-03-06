package edu.mcgill.kaliningraph.notebook

import org.jetbrains.kotlinx.jupyter.api.annotations.JupyterLibrary
import org.jetbrains.kotlinx.jupyter.api.*
import org.jetbrains.kotlinx.jupyter.api.libraries.*

@JupyterLibrary
// https://github.com/Kotlin/kotlin-jupyter/blob/master/docs/libraries.md#integration-using-kotlin-api
internal class Integration: JupyterIntegration() {
  override fun Builder.onLoaded() {
    listOf(
      "edu.mcgill.kaliningraph.*",
      "edu.mcgill.kaliningraph.matrix.*",
      "edu.mcgill.kaliningraph.circuits.*",
      "org.ejml.data.*",
      "org.ejml.kotlin.*"
    ).forEach { import(it) }
//    render<MyClass> { HTML(it.toHTML()) }
    // https://github.com/nikitinas/dataframe/blob/master/src/main/kotlin/org/jetbrains/dataframe/jupyter/Integration.kt
  }
}