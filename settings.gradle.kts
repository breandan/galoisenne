rootProject.name = "kaliningraph"

pluginManagement.repositories {
  mavenCentral()
  gradlePluginPortal()
}

plugins {
  kotlin("multiplatform") version "1.6.10" apply false
  kotlin("js") version "1.6.10" apply false
}