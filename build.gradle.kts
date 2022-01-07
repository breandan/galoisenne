plugins {
  idea
  id("com.github.ben-manes.versions") version "0.39.0"
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
  configure<io.github.gradlenexus.publishplugin.NexusPublishExtension> {
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



idea.module {
  excludeDirs.add(file("latex"))
  isDownloadJavadoc = true
  isDownloadSources = true
}

allprojects {
  repositories {
    mavenCentral()
    maven("https://jitpack.io")
  }

  group = "ai.hypergraph"
  version = "0.4.7"
}