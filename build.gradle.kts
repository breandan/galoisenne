plugins {
  java
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
  implementation("guru.nidi:graphviz-kotlin:0.15.1")
  implementation("org.apache.commons:commons-math3:3.6.1")
  implementation("com.github.kwebio:kweb-core:0.7.5")
  implementation("org.slf4j:slf4j-simple:1.7.30")
  testImplementation("org.junit.jupiter:junit-jupiter:5.6.2")
}

configure<JavaPluginConvention> {
  sourceCompatibility = JavaVersion.VERSION_1_8
}

tasks {
  listOf("HelloKaliningraph").forEach { fileName ->
    register(fileName, JavaExec::class) {
      main = "edu.mcgill.kaliningraph.${fileName}Kt"
      classpath = sourceSets["main"].runtimeClasspath
    }
  }

  test {
    useJUnitPlatform()
    testLogging {
      events("passed", "skipped", "failed")
    }
  }
}