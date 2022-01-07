import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin

plugins {
  kotlin("js") version "1.6.10"
}

group = "ai.hypergraph"
version = "1.0-SNAPSHOT"

repositories {
  mavenCentral()
}

rootProject.plugins.withType<NodeJsRootPlugin> {
  rootProject.the<NodeJsRootExtension>().nodeVersion = "16.0.0"
}

dependencies {
  testImplementation(kotlin("test"))
  implementation("org.jetbrains.kotlin-wrappers:kotlin-react:17.0.2-pre.240-kotlin-1.5.30")
  implementation("org.jetbrains.kotlin-wrappers:kotlin-react-dom:17.0.2-pre.240-kotlin-1.5.30")
  implementation("org.jetbrains.kotlin-wrappers:kotlin-styled:5.3.1-pre.240-kotlin-1.5.30")
}

kotlin {
  js(IR) {
    binaries.executable()
    browser {
      commonWebpackConfig {
        cssSupport.enabled = true
      }
    }
  }
}