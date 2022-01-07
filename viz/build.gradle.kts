import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin

plugins {
  kotlin("multiplatform") version "1.6.10"
}

group = "ai.hypergraph"
version = "1.0-SNAPSHOT"

repositories {
  mavenCentral()
}

rootProject.plugins.withType<NodeJsRootPlugin> {
  rootProject.the<NodeJsRootExtension>().nodeVersion = "16.0.0"
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
  sourceSets {

  }
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