import java.io.File
import java.util.*

fun main() {
    // Extracts the original code from the log file
    // and writes it to a file called "log.txt"
    val content = File("log_2023-07-07-07-08.txt").readText().lines().asSequence()
    val repairPfx = "Repairing (80 cores): "
    val numTks = content.filter { it.startsWith(repairPfx) }
        .map { it.substringAfter(repairPfx).count { it == ' ' } + 1 }.iterator()
    val repairSfx = "repairs per second."
    val totalReps = content.filter { it.endsWith(repairSfx) }
        .map { it.substringAfter("Found ").substringBefore(" valid repairs in ").toInt() }
        .iterator()
    val repairInfx = "-edit ranking statistics across "
    val totalEdts = content.filter { repairInfx in it }
        .map { it.drop(2).take(1).toInt() }.iterator()


    val sb = StringBuilder()
    try {
        sb.appendLine("numTks,totalValid,totalEdts")
        while (true) {
            val (numTk, totalRep, totalEdt) = listOf(numTks.next(), totalReps.next(), totalEdts.next())
            sb.appendLine("$numTk, $totalRep, $totalEdt")
        }
    } catch (e: Exception) {}

    File("log.csv").writeText(sb.toString())
}