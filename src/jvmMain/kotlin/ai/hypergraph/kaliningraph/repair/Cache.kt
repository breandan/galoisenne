package ai.hypergraph.kaliningraph.repair

import ai.hypergraph.kaliningraph.parsing.langCache
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream

fun decompressZipToString(zipBytes: ByteArray): String {
  ByteArrayInputStream(zipBytes).use { byteArrayInputStream ->
    ZipInputStream(byteArrayInputStream).use { zipInputStream ->
      val entry = zipInputStream.nextEntry
      if (entry != null) {
        return InputStreamReader(zipInputStream, StandardCharsets.UTF_8).use { reader ->
          reader.readText()
        }
      } else {
        throw IllegalArgumentException("The zip file is empty.")
      }
    }
  }
}

object LangCache {
  // Loads the Python Parikh map into memory
  fun prepopPythonLangCache() {
    if (1566012639 in langCache) return
    val filename = "1566012639.cache.zip"
    val zippedText = object {}.javaClass.classLoader.getResource(filename)!!.readBytes()
    // Now decompress and load into memory
    val str = decompressZipToString(zippedText)

    val upperBound = str.lines().last().substringBefore(" ")
    println("Prepopulated Parikh Map ($upperBound) for vanillaS2PCFG from $filename")

    langCache[1566012639] = str
  }
}