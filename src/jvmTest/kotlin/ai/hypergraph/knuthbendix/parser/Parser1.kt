package ai.hypergraph.knuthbendix.parser

import java.text.ParseException

/**
 * Converts a string to a list of group elements. Strings are of the form
 * <pre>
 * ab^4a
 * (ST^2)^-1SST'=1
 * ((A^2)B)^-1=((B^2)A)^-1
</pre> *
 * Parentheses are allowed, accents after a single letter denotes inversion (and is equivalent to ^-1). No parentheses
 * in exponents, zero exponent not allowed. Spaces are not allowed. The right hand side, if present is inverted and concatenated to the left hand side.
 *
 *
 *
 *
 * The lists of group elements are always 'reduced': there is never a sub-list of the form X X^-1
 */
class Parser {
  /**
   * DTO which represents a single group element.
   */
  class Element(// generator letter
    var ch: Char, // inverted if true
    var inverted: Boolean
  ) {
    fun isInverseOf(other: Element): Boolean =
      ch == other.ch && inverted == !other.inverted

    override fun toString() = ch.toString() + if (inverted) "\'" else ""

    override fun equals(o: Any?): Boolean {
      if (this === o) return true
      if (o == null || this::class != o::class) return false
      val element = o as Element
      return if (ch != element.ch) false else inverted == element.inverted
    }

    override fun hashCode(): Int = 31 * ch.code + if (inverted) 1 else 0
  }

  // PARSER
  // =======
  // current line being parsed
  private var line : String? = null
   // current position within current line
  private var pos = 0
   // length of current line
  private var length = 0

  /**
   * Parses the next element. String is known to start with a letter.
   */
  private fun element(): Element {
    val name = line!![pos]
    if (!name.isLetter()) throw ParseException("Letter expected", pos)
    pos++
    if (pos == length) return Element(name, false)
    return if (line!![pos] == '\'') {
      pos++
      Element(name, true)
    } else {
      Element(name, false)
    }
  }

  /**
   * Parses a number (used as exponent)
   */
  private fun number(): Int {
    val negative = pos < length && line!![pos] == '-'
    if (negative) pos++
    val start = pos
    while (pos < length && line!![pos] >= '0' && line!![pos] <= '9') pos++
    if (pos == start) throw ParseException("Number expected", pos)
    val result = line!!.substring(start, pos).toInt()
    return if (negative) -result else result
  }

  /**
   * Parses a factor. Returns null if end of line is reached or '='.
   */
  private fun factor(): ArrayList<Element>? {
    if (pos == length || line!![pos] == '=' || line!![pos] == ')') return null
    var result: ArrayList<Element>
    if (line!![pos] == '(') {
      // parenthesised expression
      pos++
      result = expression()
      if (pos == length || line!![pos] != ')') throw ParseException("')' expected", pos)
      pos++
    } else {
      result = ArrayList(1)
      result.add(element())
    }
    if (pos != length && line!![pos] == '^') {
      pos++
      var exponent = number()
      require(exponent != 0) { "Zero exponent not allowed" }
      if (exponent < 0) {
        result = invert(result)
        exponent = -exponent
      }
      // TODO: use repeated squaring?
      var power = result
      while (exponent > 1) {
        power = concatenate(power, result)
        exponent--
      }
      result = power
    }
    return result
  }

  /**
   * Parses an entire expression.
   */
  private fun expression(): ArrayList<Element> {
    var result = factor() ?: throw ParseException("Start of expression expected", pos)
    var factor = factor()
    while (factor != null) {
      result = concatenate(result, factor)
      factor = factor()
    }
    return result
  }

  /**
   * Carries the result of the [.parse] method. Left hand side
   * and right hand side are lists of elements, representing products of group
   * elements.
   */
  class Result(
    /** Left hand side  */
    var left: List<Element>,
    /** Right hand side. May be an empty list.  */
    var right: List<Element>
  )

  /**
   * Converts an input string to a [Result] object
   * @throws ParseException when the line does not have the correct syntax
   */
  fun parse(line: String): Result {
    this.line = line
    pos = 0
    length = line.length
    val left: List<Element> = expression()
    var right: List<Element> = emptyList()
    if (pos == length) {
      return Result(left, right)
    }
    if (line[pos] == '=') {
      pos++
      if (pos != length && line[pos] == '1') pos++ else right = expression()
    }
    return if (pos == length) Result(left, right)
    else throw ParseException("Premature end of line", pos)
  }

  companion object {
    // PROCESS LISTS OF ELEMENTS
    // =========================
    private fun invert(list: ArrayList<Element>): ArrayList<Element> {
      val size = list.size
      if (size == 0) return list
      val result = ArrayList<Element>(size)
      for (i in size - 1 downTo 0) {
        val el = list[i]
        result.add(Element(el.ch, !el.inverted))
      }
      return result
    }

    fun concatenate(left: ArrayList<Element>, right: ArrayList<Element>): ArrayList<Element> {
      var leftEnd = left.size
      if (leftEnd == 0) return right
      val rightSize = right.size
      if (rightSize == 0) return left
      var rightStart = 0
      while (leftEnd > 0 && rightStart < rightSize && left[leftEnd - 1].isInverseOf(right[rightStart])) {
        leftEnd--
        rightStart++
      }
      val result = ArrayList<Element>(leftEnd + rightSize - rightStart)
      for (i in 0 until leftEnd) result.add(left[i])
      for (i in rightStart until rightSize) result.add(right[i])
      return result
    }

    /**
     * Only used for testing
     */
    fun toString(list: List<Element>): String {
      val str = StringBuilder(list.size)
      for (element in list) {
        str.append(element.ch)
        if (element.inverted) str.append('\'')
      }
      return str.toString()
    }
  }
}