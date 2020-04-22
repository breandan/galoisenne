plugins {
  java
  kotlin("jvm") version "1.3.72"
}

group = "edu.mcgill"
version = "1.0-SNAPSHOT"

repositories {
  mavenCentral()
  maven("https://jitpack.io")
  jcenter()
}

dependencies {
  implementation(kotlin("stdlib-jdk8"))
  testImplementation("junit", "junit", "4.13")
  val ejmlVersion = "0.39"
  implementation("org.ejml:ejml-kotlin:$ejmlVersion")
  implementation("org.ejml:ejml-all:$ejmlVersion")
  implementation("guru.nidi:graphviz-kotlin:0.15.1")
  implementation("org.apache.commons:commons-math3:3.6.1")
  implementation("com.github.kwebio:kweb-core:0.7.4")
  implementation("org.slf4j:slf4j-simple:1.7.30")
}

configure<JavaPluginConvention> {
  sourceCompatibility = JavaVersion.VERSION_1_8
}

tasks {
  compileKotlin {
    kotlinOptions.jvmTarget = "1.8"
  }
  compileTestKotlin {
    kotlinOptions.jvmTarget = "1.8"
  }

  listOf("HelloKaliningraph").forEach { fileName ->
    register(fileName, JavaExec::class) {
      main = "edu.mcgill.kaliningraph.${fileName}Kt"
      classpath = sourceSets["main"].runtimeClasspath
    }
  }
}