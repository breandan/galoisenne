import io.github.gradlenexus.publishplugin.NexusPublishExtension
import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
import org.gradle.api.tasks.testing.logging.TestLogEvent.*
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompile
import org.jetbrains.kotlin.gradle.targets.js.nodejs.*
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  signing
  `maven-publish`
  kotlin("multiplatform") version "1.6.20-RC2"
  id("com.google.devtools.ksp") version "1.6.20-RC2-1.0.4"
  kotlin("jupyter.api") version "0.11.0-62"
  id("com.github.ben-manes.versions") version "0.42.0"
  id("io.github.gradle-nexus.publish-plugin") version "1.1.0"
}

// Stub secrets to let the project sync and build without the publication values set up
ext["signing.keyId"] = null
ext["signing.password"] = null
ext["signing.secretKeyRingFile"] = null
ext["ossrhUsername"] = null
ext["ossrhPassword"] = null

val keyId = providers.gradleProperty("signing.gnupg.keyId")
val password = providers.gradleProperty("signing.gnupg.password")
val secretKey = providers.gradleProperty("signing.gnupg.key")
val sonatypeApiUser = providers.gradleProperty("sonatypeApiUser")
val sonatypeApiKey = providers.gradleProperty("sonatypeApiKey")
if (keyId.isPresent && password.isPresent && secretKey.isPresent) {
  ext["signing.keyId"] = keyId
  ext["signing.password"] = password
  ext["signing.key"] = secretKey
  ext["ossrhUsername"] = sonatypeApiUser
  ext["ossrhPassword"] = sonatypeApiKey
}

if (sonatypeApiUser.isPresent && sonatypeApiKey.isPresent) {
  configure<NexusPublishExtension> {
    repositories {
      sonatype {
        nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
        snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))
        username.set(sonatypeApiUser)
        password.set(sonatypeApiKey)
        useStaging.set(true)
      }
    }
  }
} else {
  logger.info("Sonatype API key not defined, skipping configuration of Maven Central publishing repository")
}

fun getExtraString(name: String) = ext[name]?.toString()

signing {
  useGpgCmd()
  if (keyId.isPresent && password.isPresent) {
    useInMemoryPgpKeys(keyId.get(), secretKey.get(), password.get())
    sign(publishing.publications)
  } else {
    logger.info("PGP signing key not defined, skipping signing configuration")
  }
}

group = "ai.hypergraph"
version = "0.2.0"

repositories {
  mavenCentral()
  maven("https://jitpack.io")
}

val javadocJar by tasks.registering(Jar::class) { archiveClassifier.set("javadoc") }

rootProject.plugins.withType<NodeJsRootPlugin> {
  rootProject.the<NodeJsRootExtension>().nodeVersion = "16.0.0"
}

kotlin {
  jvm()
  js {
    browser {
      testTask {
        enabled = false
      }
    }
  }

  sourceSets {
    val commonMain by getting {
      dependencies {
          implementation(kotlin("stdlib-common"))
          implementation(kotlin("reflect"))
          implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0-native-mt")
      }
    }

    val jsMain by getting {
      dependencies {
        implementation(kotlin("stdlib-js"))
        implementation(kotlin("stdlib-common"))
        implementation(kotlin("reflect"))

        implementation("org.jetbrains.kotlinx:kotlinx-html:0.7.3")
      }
    }

    val jvmMain by getting {
      dependencies {
        implementation(project.dependencies.platform(kotlin("bom")))
        implementation(kotlin("stdlib"))
        implementation(kotlin("reflect"))
        // TODO: Figure out how to package viz.js directly for Kotlin Jupyter
        implementation("guru.nidi:graphviz-kotlin:0.18.1")
        implementation("org.graalvm.js:js:22.0.0.2")
      }
    }

    val jvmTest by getting {
      dependencies {
        // Property-based testing
        val kotestVersion = "5.2.1"
        implementation("io.kotest:kotest-runner-junit5:$kotestVersion")
        implementation("io.kotest:kotest-assertions-core:$kotestVersion")
        implementation("io.kotest:kotest-property:$kotestVersion")
        implementation("org.junit.jupiter:junit-jupiter:5.8.2")

        implementation("junit:junit:4.13.2")
        compileOnly("org.jetbrains:annotations:23.0.0")
        implementation("org.slf4j:slf4j-simple:1.7.32")

        val multikVersion = "0.1.1"
        implementation("org.jetbrains.kotlinx:multik-api:$multikVersion")
        implementation("org.jetbrains.kotlinx:multik-default:$multikVersion")

        // I think we were going to use this to prove termination of graph rewriting
        implementation("org.sosy-lab:java-smt:3.12.0")

        val libZ3Version = "4.8.14"
//        implementation("org.sosy-lab:javasmt-solver-z3:$libZ3Version")
//        implementation("org.sosy-lab:javasmt-solver-z3:libz3:$libZ3Version:so")
//        implementation("org.sosy-lab:javasmt-solver-z3:libz3java:$libZ3Version:so")
        implementation("org.sosy-lab:javasmt-solver-mathsat5:5.6.5")

        // http://www.ti.inf.uni-due.de/fileadmin/public/tools/grez/grez-manual.pdf
        // implementation(files("$projectDir/libs/grez.jar"))

        // http://www.informatik.uni-bremen.de/agbkb/lehre/rbs/seminar/AGG-ShortManual.pdf
        // implementation(files("$projectDir/libs/aggEngine_V21_classes.jar"))

        // https://github.com/jgralab/jgralab/wiki
        // implementation("de.uni-koblenz.ist:jgralab:8.1.0")

        implementation("com.redislabs:jredisgraph:2.6.0-RC2")
        implementation("io.lacuna:bifurcan:0.2.0-alpha6")

        val jgraphtVersion by extra { "1.5.1" }
        implementation("org.jgrapht:jgrapht-core:$jgraphtVersion")
        implementation("org.jgrapht:jgrapht-opt:$jgraphtVersion")
        implementation("org.jgrapht:jgrapht-ext:$jgraphtVersion")

        val tinkerpopVersion by extra { "3.5.2" }
        implementation("org.apache.tinkerpop:gremlin-core:$tinkerpopVersion")
        implementation("org.apache.tinkerpop:tinkergraph-gremlin:$tinkerpopVersion")
        implementation("info.debatty:java-string-similarity:2.0.0")
        implementation("org.eclipse.collections:eclipse-collections-api:11.0.0")
        implementation("org.eclipse.collections:eclipse-collections:11.0.0")
      }
    }

    val commonTest by getting {
      dependencies {
        implementation(kotlin("test"))
        implementation(kotlin("test-common"))
        implementation(kotlin("test-annotations-common"))
        implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.6.0-native-mt")
      }
    }
  }

  /*
   * Publishing instructions:
   *
   *  (1) ./gradlew publishAllPublicationsToSonatypeRepository
   *  (2) Visit https://s01.oss.sonatype.org/index.html#stagingRepositories
   *  (3) Close and check content tab.
   *  (4) Release.
   *
   * Adapted from: https://dev.to/kotlin/how-to-build-and-publish-a-kotlin-multiplatform-library-going-public-4a8k
   */

  publishing {
    publications {
      withType<MavenPublication> {
        artifact(javadocJar.get())
        pom {
          url.set("https://github.com/breandan/kaliningraph")
          name.set("Kaliningraph")
          description.set("A purely functional algebraic graph library")
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
  }
}

tasks {
  withType<Test> {
    useJUnitPlatform()
    testLogging {
      events = setOf(
        FAILED,
        PASSED,
        SKIPPED,
        STANDARD_OUT
      )
      exceptionFormat = FULL
      showExceptions = true
      showCauses = true
      showStackTraces = true
      showStandardStreams = true
    }
  }

  processJupyterApiResources {
    libraryProducers = listOf("ai.hypergraph.kaliningraph.notebook.Integration")
  }

  listOf(
    "Rewriter", "PrefAttach",
    "rewriting.CipherSolver", "RegexDemo", "smt.TestSMT",
  ).forEach { fileName ->
    register(fileName, org.gradle.api.tasks.JavaExec::class) {
      mainClass.set("ai.hypergraph.kaliningraph.${fileName}Kt")
      classpath += objects.fileCollection().from(configurations.named("jvmTestCompileClasspath"))
    }
  }

  /*
   * To deploy to Maven Local and start the notebook, run:
   *
   * ./gradlew [build publishToMavenLocal] jupyterRun -x test
   */

  val jupyterRun by creating(Exec::class) {
    commandLine("jupyter", "notebook", "--notebook-dir=notebooks")
  }
}