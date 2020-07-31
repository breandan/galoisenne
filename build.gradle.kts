import org.gradle.api.JavaVersion.VERSION_1_8

plugins {
  java
  maven
  kotlin("jvm") version "1.4.0-rc"
}

group = "edu.mcgill"
version = "0.0.4-SNAPSHOT"

repositories {
  mavenCentral()
  maven("https://jitpack.io")
  maven("https://dl.bintray.com/kotlin/kotlin-eap")
  jcenter()
//  maven("https://dl.bintray.com/mipt-npm/dev")
}

dependencies {
  implementation(kotlin("stdlib-jdk8"))
  testImplementation("junit", "junit", "4.13")
  val ejmlVersion = "0.39"
  implementation("org.ejml:ejml-kotlin:$ejmlVersion")
  implementation("org.ejml:ejml-all:$ejmlVersion")
  implementation("guru.nidi:graphviz-kotlin:0.17.0")
  implementation("org.apache.commons:commons-rng-examples-sampling:1.3")
  implementation("com.github.kwebio:kweb-core:0.7.20")
  implementation("org.slf4j:slf4j-simple:1.7.30")
  implementation("com.github.breandan:tensor:master-SNAPSHOT")

//  val kmathVersion by extra { "0.1.4-dev-8" }
//  implementation("scientifik:kmath-core:$kmathVersion")
//  implementation("scientifik:kmath-ast:$kmathVersion")
//  implementation("scientifik:kmath-prob:$kmathVersion")

  testImplementation("com.github.ajalt:clikt:2.6.0")

  testImplementation("com.redislabs:jredisgraph:2.0.2")
  testImplementation("io.lacuna:bifurcan:0.2.0-alpha1")

  testImplementation("org.junit.jupiter:junit-jupiter:5.6.2")
  val jgraphtVersion by extra { "1.5.0" }
  testImplementation("org.jgrapht:jgrapht-core:$jgraphtVersion")
  testImplementation("org.jgrapht:jgrapht-opt:$jgraphtVersion")
  testImplementation("org.jgrapht:jgrapht-ext:$jgraphtVersion")

  val tinkerpopVersion by extra { "3.4.6" }
  testImplementation("org.apache.tinkerpop:gremlin-core:$tinkerpopVersion")
  testImplementation("org.apache.tinkerpop:tinkergraph-gremlin:$tinkerpopVersion")
}

configure<JavaPluginConvention> {
  sourceCompatibility = VERSION_1_8
}

tasks {
  compileKotlin {
    kotlinOptions.jvmTarget = VERSION_1_8.toString()
    kotlinOptions.freeCompilerArgs += "-XXLanguage:+NewInference"
  }
  listOf("HelloKaliningraph", "PrefAttach").forEach { fileName ->
    register(fileName, JavaExec::class) {
      main = "edu.mcgill.kaliningraph.${fileName}Kt"
      classpath = sourceSets["main"].runtimeClasspath
    }
  }

  listOf("RegexDemo").forEach { fileName ->
    register(fileName, JavaExec::class) {
      main = "edu.mcgill.kaliningraph.automata.${fileName}Kt"
      classpath = sourceSets["main"].runtimeClasspath
    }
  }

  test {
    useJUnitPlatform()
    testLogging { events("passed", "skipped", "failed") }
  }
}