import org.gradle.api.JavaVersion.VERSION_1_8

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  `maven-publish`
  kotlin("jvm") version "1.4.30"
  id("com.github.ben-manes.versions") version "0.36.0"
}

group = "com.github.breandan"
version = "0.1.4"

repositories {
  mavenCentral()
  maven("https://jitpack.io")
  maven("https://dl.bintray.com/egor-bogomolov/astminer")
  maven("https://dl.bintray.com/mipt-npm/dev")
  maven("https://clojars.org/repo")
  maven("https://dl.bintray.com/kotlin/kotlin-datascience")
}

dependencies {
  implementation(kotlin("stdlib-jdk8"))
  val ejmlVersion = "0.39"
  api("org.ejml:ejml-kotlin:$ejmlVersion")
  api("org.ejml:ejml-all:$ejmlVersion")
  api("guru.nidi:graphviz-kotlin:0.18.0")

  val commonsRngVersion = "1.3"
  implementation("org.apache.commons:commons-rng-sampling:$commonsRngVersion")
  implementation("org.apache.commons:commons-rng-simple:$commonsRngVersion")
  implementation("org.slf4j:slf4j-simple:1.7.30")

  testImplementation("com.github.breandan:tensor:master-SNAPSHOT")

  val multik_version = "0.0.1-dev-13"
  testImplementation("org.jetbrains.kotlinx.multik:multik-api:$multik_version")
  testImplementation("org.jetbrains.kotlinx.multik:multik-default:$multik_version")

  testImplementation("org.jetbrains.kotlin:kotlin-scripting-jsr223")
  testImplementation("com.github.kwebio:kweb-core:0.7.33")
  testImplementation("org.sosy-lab:java-smt:3.7.0")
  testImplementation("org.sosy-lab:javasmt-solver-mathsat5:5.6.5")

  // http://www.ti.inf.uni-due.de/fileadmin/public/tools/grez/grez-manual.pdf
  // implementation(files("$projectDir/libs/grez.jar"))

  // http://www.informatik.uni-bremen.de/agbkb/lehre/rbs/seminar/AGG-ShortManual.pdf
  testImplementation(files("$projectDir/libs/aggEngine_V21_classes.jar"))

  // https://github.com/jgralab/jgralab/wiki
  testImplementation("de.uni-koblenz.ist:jgralab:8.1.0")

//  val kmathVersion by extra { "0.2.0-dev-2" }
//  implementation("scientifik:kmath-core:$kmathVersion")
//  implementation("scientifik:kmath-ast:$kmathVersion")
//  implementation("scientifik:kmath-prob:$kmathVersion")

  testImplementation("junit", "junit", "4.13.1")
  testImplementation("com.github.ajalt.clikt:clikt:3.1.0")
  testImplementation("com.redislabs:jredisgraph:2.3.0")
  testImplementation("io.lacuna:bifurcan:0.2.0-alpha4")
  testImplementation("org.junit.jupiter:junit-jupiter:5.7.0")
  val jgraphtVersion by extra { "1.5.0" }
  testImplementation("org.jgrapht:jgrapht-core:$jgraphtVersion")
  testImplementation("org.jgrapht:jgrapht-opt:$jgraphtVersion")
  testImplementation("org.jgrapht:jgrapht-ext:$jgraphtVersion")

  val tinkerpopVersion by extra { "3.4.10" }
  testImplementation("org.apache.tinkerpop:gremlin-core:$tinkerpopVersion")
  testImplementation("org.apache.tinkerpop:tinkergraph-gremlin:$tinkerpopVersion")
}

configure<JavaPluginConvention> {
  sourceCompatibility = VERSION_1_8
}

tasks {
//  compileKotlin {
  withType<KotlinCompile> {
      kotlinOptions {
//        languageVersion = "1.5"
//        apiVersion = "1.5"
        jvmTarget = VERSION_1_8.toString()
      }
  }

  listOf("HelloKaliningraph", "Rewriter", "PrefAttach", "rewriting.CipherSolver").forEach { fileName ->
    register(fileName, JavaExec::class) {
      main = "edu.mcgill.kaliningraph.${fileName}Kt"
      classpath = sourceSets["test"].runtimeClasspath
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

  val sourcesJar by registering(org.gradle.jvm.tasks.Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
  }
}

publishing {
  publications.create<MavenPublication>("default") {
    from(components["java"])
    artifact(tasks["sourcesJar"])

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