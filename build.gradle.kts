import io.github.gradlenexus.publishplugin.NexusPublishExtension
import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
import org.gradle.api.tasks.testing.logging.TestLogEvent.*
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.targets.js.nodejs.*

plugins {
  signing
  `maven-publish`
  kotlin("multiplatform") version "2.0.0-Beta2"
//  kotlin("jupyter.api") version "0.11.0-225"
  id("com.github.ben-manes.versions") version "0.50.0"
  id("io.github.gradle-nexus.publish-plugin") version "2.0.0-rc-1"
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
        nexusUrl = uri("https://s01.oss.sonatype.org/service/local/")
        snapshotRepositoryUrl = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
        username = sonatypeApiUser
        password = sonatypeApiKey
        useStaging = true
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
version = "0.2.2"

repositories {
  mavenCentral {
    metadataSources {
      mavenPom()
    }
  }
  mavenCentral {
    metadataSources {
      artifact()
    }
  }
}

val javadocJar by tasks.registering(Jar::class) { archiveClassifier = "javadoc" }

rootProject.plugins.withType<NodeJsRootPlugin> {
  rootProject.the<NodeJsRootExtension>().nodeVersion = "16.0.0"
}

kotlin {
  jvm()
  js(IR) {
    browser {
      testTask {
        enabled = false
      }
    }
    binaries.executable()
  }

  sourceSets {
//    all {
//      languageSettings.apply {
//        languageVersion = "2.0"
//      }
//    }
    val commonMain by getting {
      dependencies {
        implementation(kotlin("stdlib-common"))
        implementation(kotlin("reflect"))

        implementation("com.ionspin.kotlin:bignum:0.3.8")
      }
    }

    val jvmMain by getting {
      dependencies {
        implementation(project.dependencies.platform(kotlin("bom")))
        implementation(kotlin("stdlib"))
        implementation(kotlin("reflect"))
        // TODO: Figure out how to package viz.js directly for Kotlin Jupyter
        // https://github.com/mipt-npm/kmath/issues/449#issuecomment-1009660734
        implementation("guru.nidi:graphviz-kotlin:0.18.1")
        implementation("org.graalvm.js:js:23.0.2")

        // Markovian deps
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0-RC")

        implementation("org.jetbrains.lets-plot:platf-awt-jvm:4.1.0")
        implementation("org.jetbrains.lets-plot:lets-plot-kotlin-jvm:4.5.0")

//  https://arxiv.org/pdf/1908.10693.pdf
//  implementation("com.datadoghq:sketches-java:0.7.0")

        // Cache PMF/CDF lookups for common queries

        implementation("org.apache.datasketches:datasketches-java:5.0.0")

//  implementation("com.github.analog-garage:dimple:master-SNAPSHOT")

//  implementation("com.github.TUK-CPS:jAADD:-SNAPSHOT")
        implementation("ca.umontreal.iro.simul:ssj:3.3.2")

        // MPJ (required for Poon's SPN)
//  implementation(files("$projectDir/libs/mpj-0.44.jar"))

        implementation("org.sosy-lab:common:0.3000-529-g6152d88")
        implementation("org.sosy-lab:java-smt:4.1.0")

        // val z3Version = "4.12.2-glibc_2.27"
        // runtimeOnly("org.sosy-lab:javasmt-solver-z3:$z3Version:com.microsoft.z3@jar")
        // runtimeOnly("org.sosy-lab:javasmt-solver-z3:$z3Version:libz3@so")
        // runtimeOnly("org.sosy-lab:javasmt-solver-z3:$z3Version:libz3java@so")
        // runtimeOnly("org.sosy-lab:javasmt-solver-mathsat5:5.6.10:libmathsat5j@so")

        // TODO: Replace LogicNG with KoSAT?
        // https://github.com/UnitTestBot/kosat
        implementation("org.logicng:logicng:2.4.1")

        val multikVersion = "0.2.2"
        implementation("org.jetbrains.kotlinx:multik-core:$multikVersion")
        implementation("org.jetbrains.kotlinx:multik-default:$multikVersion")
      }
    }

    val jvmTest by getting {
      dependencies {
        // Property-based testing
        val kotestVersion = "5.5.5" // Unable to update?
        implementation("io.kotest:kotest-runner-junit5:$kotestVersion")
        implementation("io.kotest:kotest-assertions-core:$kotestVersion")
        implementation("io.kotest:kotest-property:$kotestVersion")
        implementation("org.junit.jupiter:junit-jupiter:5.10.1")

        implementation("junit:junit:4.13.2")
        implementation("org.jetbrains:annotations:24.1.0")
        implementation("org.slf4j:slf4j-simple:2.0.9")

        // http://www.ti.inf.uni-due.de/fileadmin/public/tools/grez/grez-manual.pdf
        // implementation(files("$projectDir/libs/grez.jar"))

        // http://www.informatik.uni-bremen.de/agbkb/lehre/rbs/seminar/AGG-ShortManual.pdf
        // implementation(files("$projectDir/libs/aggEngine_V21_classes.jar"))

        // https://github.com/jgralab/jgralab/wiki
        // implementation("de.uni-koblenz.ist:jgralab:8.1.0")

        implementation("com.redislabs:jredisgraph:2.6.0-RC2")
        implementation("io.lacuna:bifurcan:0.2.0-alpha7")

        val jgraphtVersion by extra { "1.5.2" }
        implementation("org.jgrapht:jgrapht-core:$jgraphtVersion")
        implementation("org.jgrapht:jgrapht-opt:$jgraphtVersion")
        implementation("org.jgrapht:jgrapht-ext:$jgraphtVersion")

        val tinkerpopVersion by extra { "3.7.1" }
        implementation("org.apache.tinkerpop:gremlin-core:$tinkerpopVersion")
        implementation("org.apache.tinkerpop:tinkergraph-gremlin:$tinkerpopVersion")
        implementation("info.debatty:java-string-similarity:2.0.0")
        val eccVersion = "12.0.0.M3"
        implementation("org.eclipse.collections:eclipse-collections-api:$eccVersion")
        implementation("org.eclipse.collections:eclipse-collections:$eccVersion")

        implementation(kotlin("scripting-jsr223"))
      }
    }

    val commonTest by getting {
      dependencies {
        implementation(kotlin("test"))
        implementation(kotlin("test-common"))
        implementation(kotlin("test-annotations-common"))
        implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")
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
          url = "https://github.com/breandan/kaliningraph"
          name = "Kaliningraph"
          description = "A purely functional algebraic graph library"
          licenses {
            license {
              name = "The Apache Software License, Version 2.0"
              url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
              distribution = "repo"
            }
          }
          developers {
            developer {
              id = "Breandan Considine"
              name = "Breandan Considine"
              email = "bre@ndan.co"
              organization = "McGill University"
            }
          }
          scm {
            url = "https://github.com/breandan/kaliningraph"
          }
        }
      }
    }
  }
}

tasks {
  withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
  }

  withType<Test> {
    minHeapSize = "1g"
    maxHeapSize = "3g"
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

    if (project.hasProperty("leaseExcludeBenchmarks")) exclude("**/**Benchmarks.class")
  }

//  processJupyterApiResources {
//    libraryProducers = listOf("ai.hypergraph.kaliningraph.notebook.Integration")
//  }

  listOf(
    "Rewriter", "PrefAttach",
    "rewriting.CipherSolver", "RegexDemo", "smt.TestSMT",
  ).forEach { fileName ->
    register(fileName, org.gradle.api.tasks.JavaExec::class) {
      mainClass = "ai.hypergraph.kaliningraph.${fileName}Kt"
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