import io.github.gradlenexus.publishplugin.NexusPublishExtension
import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
import org.gradle.api.tasks.testing.logging.TestLogEvent.*

plugins {
  signing
  `maven-publish`
  kotlin("multiplatform") version "1.6.0-RC"
  id("com.google.devtools.ksp") version "1.6.0-RC-1.0.1-RC"
  kotlin("jupyter.api") version "0.10.3-33"
  id("com.github.ben-manes.versions") version "0.39.0"
  id("io.github.gradle-nexus.publish-plugin") version "1.1.0"
}

val sonatypeApiUser = providers.gradleProperty("sonatypeApiUser")
val sonatypeApiKey = providers.gradleProperty("sonatypeApiKey")
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

val signingKeyId = providers.gradleProperty("signing.gnupg.keyId")
val signingKeyPassphrase = providers.gradleProperty("signing.gnupg.passphrase")
signing {
  useGpgCmd()
  if (signingKeyId.isPresent && signingKeyPassphrase.isPresent) {
    useInMemoryPgpKeys(signingKeyId.get(), signingKeyPassphrase.get())
    sign(extensions.getByType<PublishingExtension>().publications)
  } else {
    logger.info("PGP signing key not defined, skipping signing configuration")
  }
}

group = "ai.hypergraph"
version = "0.1.9"

repositories {
  mavenCentral()
  maven("https://jitpack.io")
}

kotlin {
  jvm()

  sourceSets {
    val commonMain by getting {
      dependencies {
        implementation(kotlin("stdlib-common"))
        implementation(kotlin("reflect"))
      }
    }

    val jvmMain by getting {
      dependencies {
        implementation(project.dependencies.platform(kotlin("bom")))
        implementation(kotlin("stdlib"))
        implementation(kotlin("reflect"))
        // Used to cache graph lookups
        implementation("com.github.ben-manes.caffeine:caffeine:3.0.4")
//  implementation("io.github.reactivecircus.cache4k:cache4k:0.3.0")
//  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.2")
//  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.5.2")

        val ejmlVersion = "0.41"
        implementation("org.ejml:ejml-kotlin:$ejmlVersion")
        implementation("org.ejml:ejml-all:$ejmlVersion")
        implementation("org.graalvm.js:js:21.3.0")
        implementation("guru.nidi:graphviz-kotlin:0.18.1")
      }
    }

    val jvmTest by getting {
      dependencies {
        // Property-based testing
        val kotestVersion = "5.0.0.M4"
        implementation("io.kotest:kotest-runner-junit5:$kotestVersion")
        implementation("io.kotest:kotest-assertions-core:$kotestVersion")
        implementation("io.kotest:kotest-property:$kotestVersion")
        implementation("org.junit.jupiter:junit-jupiter:5.8.1")

        implementation("junit:junit:4.13.2")
        compileOnly("org.jetbrains:annotations:23.0.0")
        implementation("org.slf4j:slf4j-simple:1.7.32")

        val multikVersion = "0.1.1"
        implementation("org.jetbrains.kotlinx:multik-api:$multikVersion")
        implementation("org.jetbrains.kotlinx:multik-default:$multikVersion")

        implementation("com.github.kwebio:kweb-core:0.7.33")

        // I think we were going to use this to prove termination of graph rewriting
        implementation("org.sosy-lab:java-smt:3.11.0")
        //implementation("org.sosy-lab:javasmt-solver-z3:4.8.10")
        //implementation("org.sosy-lab:javasmt-solver-z3-native:z3-4.4.1-788-g8df145d")
        //implementation("org.sosy-lab:javasmt-solver-mathsat5:5.6.6")

        // http://www.ti.inf.uni-due.de/fileadmin/public/tools/grez/grez-manual.pdf
        // implementation(files("$projectDir/libs/grez.jar"))

        // http://www.informatik.uni-bremen.de/agbkb/lehre/rbs/seminar/AGG-ShortManual.pdf
        // implementation(files("$projectDir/libs/aggEngine_V21_classes.jar"))

        // https://github.com/jgralab/jgralab/wiki
        //  implementation("de.uni-koblenz.ist:jgralab:8.1.0")

        implementation("com.redislabs:jredisgraph:2.6.0-RC2")
        implementation("io.lacuna:bifurcan:0.2.0-alpha6")

        val jgraphtVersion by extra { "1.5.1" }
        implementation("org.jgrapht:jgrapht-core:$jgraphtVersion")
        implementation("org.jgrapht:jgrapht-opt:$jgraphtVersion")
        implementation("org.jgrapht:jgrapht-ext:$jgraphtVersion")

        val tinkerpopVersion by extra { "3.5.1" }
        implementation("org.apache.tinkerpop:gremlin-core:$tinkerpopVersion")
        implementation("org.apache.tinkerpop:tinkergraph-gremlin:$tinkerpopVersion")
        implementation("info.debatty:java-string-similarity:2.0.0")
      }
    }

    val commonTest by getting {
      dependencies {
        implementation(kotlin("test"))
        implementation(kotlin("test-common"))
        implementation(kotlin("test-annotations-common"))
      }
    }
  }
}

tasks {
  processJupyterApiResources {
    libraryProducers = listOf("ai.hypergraph.kaliningraph.notebook.Integration")
  }

  listOf(
    "HelloKaliningraph", "Rewriter", "PrefAttach",
    "rewriting.CipherSolver", "RegexDemo", "GraphRewrite", "smt.TestSMT"
  ).forEach { fileName ->
    register(fileName, org.gradle.api.tasks.JavaExec::class) {
      mainClass.set("ai.hypergraph.kaliningraph.${fileName}Kt")
      classpath += objects.fileCollection().from(configurations.named("jvmTestCompileClasspath"))
    }
  }

  named<Test>("jvmTest") {
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

  /*
   * To deploy to Maven Local and start the notebook, run:
   *
   * ./gradlew [build publishToMavenLocal] jupyterRun -x test
   */

  val jupyterRun by creating(org.gradle.api.tasks.Exec::class) {
    commandLine("jupyter", "notebook", "--notebook-dir=notebooks")
  }
}


publishing {
  publications.create<MavenPublication>("default") {
    from(components["kotlin"])
    artifact(tasks["sourcesJar"])

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