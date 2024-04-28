package ai.hypergraph.kaliningraph.image

import ai.hypergraph.markovian.execute
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

class GIFTest {
//  @Test
  fun testBMPCollage() {
    val images = (0..255).map { i ->
      List(200) { List(200) { i }.toIntArray() }.toTypedArray()
    }

    val dirName = "bmp" + System.currentTimeMillis()
    val tmpDir = Files.createTempDirectory(dirName)
    images.forEachIndexed { i, it ->
      File("$tmpDir/b_${i.toString().padEnd(3, '0')}.bmp")
        .writeBytes(BMP().saveBMP(it))
    }

    ("convert -delay 1 -loop 0 $tmpDir/*.bmp $dirName.gif || " +
      "echo 'This command requires ImageMagik'").execute()
  }
}