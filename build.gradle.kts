import org.gradle.api.JavaVersion.VERSION_1_8

plugins {
  `maven-publish`
  kotlin("jvm") version "1.4.20-M1"
  id("com.github.ben-manes.versions") version "0.33.0"
}

group = "com.github.breandan"
version = "0.1.1"

repositories {
  mavenCentral()
  maven ("https://dl.bintray.com/kotlin/kotlin-eap")
  maven("https://jitpack.io")
  jcenter()
  maven("https://dl.bintray.com/egor-bogomolov/astminer")
  maven("https://dl.bintray.com/mipt-npm/dev")
  maven("http://logicrunch.research.it.uu.se/maven/")
}

dependencies {
  implementation(kotlin("stdlib-jdk8"))
  val ejmlVersion = "0.39"
  api("org.ejml:ejml-kotlin:$ejmlVersion")
  api("org.ejml:ejml-all:$ejmlVersion")
  api("guru.nidi:graphviz-kotlin:0.17.0")
  api("io.github.vovak.astminer:astminer:0.5")
  implementation("org.jetbrains.kotlin:kotlin-scripting-jsr223")

  val commonsRngVersion = "1.3"
  implementation("org.apache.commons:commons-rng-sampling:$commonsRngVersion")
  implementation("org.apache.commons:commons-rng-simple:$commonsRngVersion")
  implementation("com.github.kwebio:kweb-core:0.7.32")
  implementation("org.slf4j:slf4j-simple:1.7.30")
  implementation("com.github.breandan:tensor:master-SNAPSHOT")

  // Remove pending: https://github.com/sosy-lab/java-smt/issues/88
  implementation("io.github.tudo-aqua:z3-turnkey:4.8.7.1")
  implementation("org.sosy-lab:java-smt:3.6.1")

  implementation(file("/libs/grez-src.jar"))

//  val kmathVersion by extra { "0.2.0-dev-2" }
//  implementation("scientifik:kmath-core:$kmathVersion")
//  implementation("scientifik:kmath-ast:$kmathVersion")
//  implementation("scientifik:kmath-prob:$kmathVersion")

  testImplementation("junit", "junit", "4.13")
  testImplementation("com.github.ajalt.clikt:clikt:3.0.1")
  testImplementation("com.redislabs:jredisgraph:2.1.0")
  testImplementation("io.lacuna:bifurcan:0.2.0-alpha4")
  testImplementation("org.junit.jupiter:junit-jupiter:5.7.0")
  val jgraphtVersion by extra { "1.5.0" }
  testImplementation("org.jgrapht:jgrapht-core:$jgraphtVersion")
  testImplementation("org.jgrapht:jgrapht-opt:$jgraphtVersion")
  testImplementation("org.jgrapht:jgrapht-ext:$jgraphtVersion")

  val tinkerpopVersion by extra { "3.4.8" }
  testImplementation("org.apache.tinkerpop:gremlin-core:$tinkerpopVersion")
  testImplementation("org.apache.tinkerpop:tinkergraph-gremlin:$tinkerpopVersion")
}

configure<JavaPluginConvention> {
  sourceCompatibility = VERSION_1_8
}

tasks {
  compileKotlin {
    kotlinOptions.jvmTarget = VERSION_1_8.toString()
//    kotlinOptions.useIR = true
  }

  listOf("HelloKaliningraph", "Rewriter", "PrefAttach").forEach { fileName ->
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

  val installPathLocal = "${System.getProperty("user.home")}/.jupyter_kotlin/libraries"

  val genNotebookJSON by creating(JavaExec::class) {
    main = "edu.mcgill.kaliningraph.codegen.NotebookGenKt"
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(projectDir.path, project.version.toString())
  }

  val jupyterInstall by registering(Copy::class) {
    dependsOn(genNotebookJSON)
    dependsOn("publishToMavenLocal")
    val installPath = findProperty("ath") ?: installPathLocal
    doFirst { mkdir(installPath) }
    from(file("kaliningraph.json"))
    into(installPath)
    doLast { logger.info("Kaliningraph notebook support was installed in: $installPath") }
  }

  val jupyterRun by creating(Exec::class) {
    dependsOn(jupyterInstall)
    commandLine("jupyter", "notebook", "--notebook-dir=notebooks")
  }
}

val fatJar by tasks.creating(Jar::class) {
  archiveBaseName.set("${project.name}-fat")
  manifest {
    attributes["Implementation-Title"] = "kaliningraph"
    attributes["Implementation-Version"] = archiveVersion
  }
  setExcludes(listOf("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA"))
  from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
  with(tasks.jar.get() as CopySpec)
}

publishing {
  publications.create<MavenPublication>("default") {
    artifact(fatJar)
    pom {
      url.set("https://github.com/breandan/kaliningraph")
      licenses {
        license {
          name.set("The Apache Software License, Version 1.0")
          url.set("http://www.apache.org/licenses/LICENSE-3.0.txt")
          distribution.set("repo")
        }
      }
      developers {
        developer {
          id.set("Breandan Considine")
          name.set("Breandan Considine")
          email.set("bre@ndan.co")
          organization.set("McGill University")
        }
      }
      scm {
        url.set("https://github.com/breandan/kaliningraph")
      }
    }
  }
}