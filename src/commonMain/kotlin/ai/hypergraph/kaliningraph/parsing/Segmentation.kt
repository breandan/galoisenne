package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.*
import ai.hypergraph.kaliningraph.image.escapeHTML

data class Segmentation(
  val valid: List<Int> = emptyList(),
  val invalid: List<Int> = emptyList(),
  val illegal: List<Int> = emptyList(),
  val line: String = ""
) {
  companion object {
    private fun Σᐩ.illegalWordIndices(cfg: CFG) =
      tokenizeByWhitespace().mapIndexedNotNull { idx: Int, s: Σᐩ -> if (s !in cfg.terminals) idx else null }

    fun build(cfg: CFG, line: Σᐩ): Segmentation {
      val tokens = line.tokenizeByWhitespace()
      return when {
        "_" in tokens -> emptyList<Int>() to emptyList()
        line in cfg.language -> emptyList<Int>() to emptyList()
        tokens.size < 4 -> emptyList<Int>() to tokens.indices.toList()
        else -> cfg.parseInvalidWithMaximalFragments(line)
          .map { it.span }.filter { 2 < (it.last - it.first) }.flatten()
          .let { it to tokens.indices.filterNot { i -> i in it } }
      }.let {
        Segmentation(
          valid = it.first,
          invalid = it.second,
          illegal = line.illegalWordIndices(cfg),
          line = line
        )
      }
    }
  }

  val parseableRegions = valid.map { it..it }.mergeContiguousRanges().map { it.charIndicesOfWordsInString(line) }
  val unparseableRegions = invalid.filter { it !in illegal }.map { it..it }.mergeContiguousRanges().map { it.charIndicesOfWordsInString(line) }
  val illegalRegions = illegal.map { it..it }.map { it.charIndicesOfWordsInString(line) }

  fun toColorfulHTMLString(): String {
    val illegalRegions =
      unparseableRegions.map { it to "orange" } +
        illegalRegions.map { it to "red" }

    val regions =
      (parseableRegions.map { it to "other" } + illegalRegions).sortedBy { it.first.first }

    if (illegalRegions.isEmpty()) return line.escapeHTML()

    val coloredLine = StringBuilder().append("<u>")
    regions.forEach { (range, color) ->
      coloredLine.append("<span class=\"$color\">${line.substring(range).escapeHTML()}</span>")
    }
    coloredLine.append("</u>")

    return coloredLine.toString()
  }

  fun toColorfulString(): String {
    val coloredLine = StringBuilder()

    val regions =
      parseableRegions.map { it to ANSI_GREEN } +
      unparseableRegions.map { it to ANSI_YELLOW } +
      illegalRegions.map { it to ANSI_RED }

    for (i in line.indices) {
      val color = regions.find { i in it.first }?.second ?: ANSI_RESET
      coloredLine.append(color).append(line[i])
    }

    coloredLine.append(ANSI_RESET)
    return coloredLine.toString()
  }

  fun List<IntRange>.mergeContiguousRanges(): List<IntRange> =
    sortedBy { it.first }.fold(mutableListOf()) { acc, range ->
      if (acc.isEmpty()) acc.add(range)
      else if (acc.last().last + 1 >= range.first) acc[acc.lastIndex] = acc.last().first..range.last
      else acc.add(range)
      acc
    }

  // Takes an IntRange of word indices and a String of words delimited by one or more whitespaces,
// and returns the corresponding IntRange of character indices in the original string.
// For example, if the input is (1..2, "a__bb___ca d e f"), the output is 3..10
  fun IntRange.charIndicesOfWordsInString(str: String): IntRange {
    // All tokens, including whitespaces
    val wordTokens = str.split("\\s+".toRegex()).filter { it.isNotEmpty() }
    val whitespaceTokens = str.split("\\S+".toRegex())

    val allTokens = wordTokens.zip(whitespaceTokens)
    val polarity = str.startsWith(wordTokens.first())
    val interwoven = allTokens.flatMap {
      if (polarity) listOf(it.first, it.second)
      else listOf(it.second, it.first)
    }

    val s = start * 2
    val l = last * 2
    val (startIdx, endIdx) = (s) to (l + 1)

    val adjust = if (startIdx == 0) 0 else 1

    val startOffset = interwoven.subList(0, startIdx).sumOf { it.length } + adjust
    val endOffset = interwoven.subList(0, endIdx + 1).sumOf { it.length }
    return startOffset..endOffset
  }
}