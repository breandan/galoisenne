plugins {
  java
  maven
  kotlin("jvm") version "1.3.72"
}

group = "edu.mcgill"
version = "0.1-SNAPSHOT"

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
  implementation("guru.nidi:graphviz-kotlin:0.16.0")
  implementation("org.apache.commons:commons-math3:3.6.1")
  implementation("com.github.kwebio:kweb-core:0.7.20")
  implementation("org.slf4j:slf4j-simple:1.7.30")

  testImplementation("com.github.ajalt:clikt:2.6.0")

  testImplementation("com.redislabs:jredisgraph:2.0.2")
  testImplementation("io.lacuna:bifurcan:0.2.0-alpha1")

  testImplementation("org.junit.jupiter:junit-jupiter:5.6.2")
  val jgraphtVersion by extra { "1.4.0" }
  testImplementation("org.jgrapht:jgrapht-core:$jgraphtVersion")
  testImplementation("org.jgrapht:jgrapht-ext:$jgraphtVersion")

  val tinkerpopVersion by extra { "3.4.6" }
  testImplementation("org.apache.tinkerpop:gremlin-core:$tinkerpopVersion")
  testImplementation("org.apache.tinkerpop:tinkergraph-gremlin:$tinkerpopVersion")
}

configure<JavaPluginConvention> {
  sourceCompatibility = JavaVersion.VERSION_1_8
}

tasks {
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