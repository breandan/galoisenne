package ai.hypergraph.kaliningraph

import ai.hypergraph.kaliningraph.parsing.CFG

fun String.antlr2CFG(): CFG {
  val stmts = splitTopLevelStatements()
  return buildSet {
    var i = 0
    for (stmt in stmts) {
      val rule = stmt.parseParserRule() ?: continue
      val alts = rule.body.splitTopLevel('|')
      for (alt in alts) add((if (i++ == 0) "START" else rule.name) to alt.tokenizeANTLRAlternative())
    }
  }
}

private data class ParserRule(val name: String, val body: String)

private fun String.parseParserRule(): ParserRule? {
  val colon = findTopLevel(':') ?: return null
  val header = substring(0, colon).trim()
  val body = substring(colon + 1).trim()

  // Only keep parser rules: lower-case identifier at start of header
  val name = Regex("""^\s*([a-z][A-Za-z0-9_]*)\b""")
    .find(header)
    ?.groupValues
    ?.get(1)
    ?: return null

  return ParserRule(name, body)
}

private fun String.tokenizeANTLRAlternative(): List<String> {
  val s = trim()
  if (s.isEmpty()) return emptyList()

  val out = mutableListOf<String>()
  var i = 0

  fun eof() = i >= s.length
  fun peek() = s[i]

  fun readQuoted(): String {
    val q = s[i++]
    val sb = StringBuilder()
    while (i < s.length) {
      val c = s[i]
      if (c == q && !s.isEscaped(i)) {
        i++
        break
      }
      sb.append(c)
      i++
    }
    return sb.toString()
  }

  fun readIdent(): String {
    val start = i
    i++
    while (i < s.length && (s[i].isLetterOrDigit() || s[i] == '_')) i++
    return s.substring(start, i)
  }

  fun readBalanced(open: Char, close: Char): String {
    val start = i
    var depth = 0
    var inSingle = false
    var inDouble = false

    while (i < s.length) {
      val c = s[i]

      if (c == '\'' && !inDouble && !s.isEscaped(i)) {
        inSingle = !inSingle
        i++
        continue
      }
      if (c == '"' && !inSingle && !s.isEscaped(i)) {
        inDouble = !inDouble
        i++
        continue
      }

      if (!inSingle && !inDouble) {
        if (c == open) depth++
        if (c == close) {
          depth--
          i++
          if (depth == 0) break
          continue
        }
      }

      i++
    }

    return s.substring(start, i)
  }

  while (!eof()) {
    when {
      peek().isWhitespace() -> i++

      peek() == '\'' || peek() == '"' ->
        out += readQuoted()

      i + 1 < s.length && s.startsWith("->", i) -> {
        out += "->"
        i += 2
      }

      i + 1 < s.length && s.startsWith("+=", i) -> {
        out += "+="
        i += 2
      }

      i + 1 < s.length && s.startsWith("..", i) -> {
        out += ".."
        i += 2
      }

      peek() == '[' ->
        out += readBalanced('[', ']')

      peek() == '{' ->
        out += readBalanced('{', '}')

      peek() == '<' ->
        out += readBalanced('<', '>')

      peek().isLetter() || peek() == '_' ->
        out += readIdent()

      peek() in listOf('(', ')', '|', '?', '*', '+', '~', '!', ',', ':', ';', '=', '#', '@', '.') -> {
        out += peek().toString()
        i++
      }

      else -> {
        out += peek().toString()
        i++
      }
    }
  }

  return out
}

private fun String.splitTopLevelStatements(): List<String> {
  val out = mutableListOf<String>()
  var start = 0
  var i = 0
  var paren = 0
  var bracket = 0
  var brace = 0
  var inSingle = false
  var inDouble = false

  while (i < length) {
    val c = this[i]

    if (c == '\'' && !inDouble && !isEscaped(i)) inSingle = !inSingle
    else if (c == '"' && !inSingle && !isEscaped(i)) inDouble = !inDouble
    else if (!inSingle && !inDouble) {
      when (c) {
        '(' -> paren++
        ')' -> paren--
        '[' -> bracket++
        ']' -> bracket--
        '{' -> brace++
        '}' -> brace--
        ';' -> if (paren == 0 && bracket == 0 && brace == 0) {
          val piece = substring(start, i).trim()
          if (piece.isNotEmpty()) out += piece
          start = i + 1
        }
      }
    }

    i++
  }

  val tail = substring(start).trim()
  if (tail.isNotEmpty()) out += tail
  return out
}

private fun String.splitTopLevel(delim: Char): List<String> {
  val out = mutableListOf<String>()
  var start = 0
  var i = 0
  var paren = 0
  var bracket = 0
  var brace = 0
  var inSingle = false
  var inDouble = false

  while (i < length) {
    val c = this[i]

    if (c == '\'' && !inDouble && !isEscaped(i)) inSingle = !inSingle
    else if (c == '"' && !inSingle && !isEscaped(i)) inDouble = !inDouble
    else if (!inSingle && !inDouble) {
      when (c) {
        '(' -> paren++
        ')' -> paren--
        '[' -> bracket++
        ']' -> bracket--
        '{' -> brace++
        '}' -> brace--
        delim -> if (paren == 0 && bracket == 0 && brace == 0) {
          out += substring(start, i).trim()
          start = i + 1
        }
      }
    }

    i++
  }

  out += substring(start).trim()
  return out
}

private fun String.findTopLevel(ch: Char): Int? {
  var i = 0
  var paren = 0
  var bracket = 0
  var brace = 0
  var inSingle = false
  var inDouble = false

  while (i < length) {
    val c = this[i]

    if (c == '\'' && !inDouble && !isEscaped(i)) inSingle = !inSingle
    else if (c == '"' && !inSingle && !isEscaped(i)) inDouble = !inDouble
    else if (!inSingle && !inDouble) {
      when (c) {
        '(' -> paren++
        ')' -> paren--
        '[' -> bracket++
        ']' -> bracket--
        '{' -> brace++
        '}' -> brace--
        ch -> if (paren == 0 && bracket == 0 && brace == 0) return i
      }
    }

    i++
  }

  return null
}

private fun String.isEscaped(i: Int): Boolean {
  var j = i - 1
  var count = 0
  while (j >= 0 && this[j] == '\\') {
    count++
    j--
  }
  return count % 2 == 1
}