package ai.hypergraph.knuthbendix.kbs

import java.util.*

/**
 * Rule of the Rewrite system.
 * String finding algorithm of Knuth-Moris-Pratt is used on linked list to ease working with changing lengths.
 * @author  Robbert Gurdeep Singh
 */
class Rule<T>(from: List<T>, to: List<T>) {
  val from: List<T> = ArrayList(from)
  val to: List<T> = ArrayList(to)
  lateinit var lut: IntArray

  /**
   * Make a Rule for the rule system
   *
   * *Note*: Rules must not make thing bigger (in the strict sense)
   * And calculate the Knuth-Moris-Pratt Shift table.
   *
   * @param from An array of elements that should be replaced
   * @param to The array by witch the from array should be replaced
   */
  init { makeKMP() }

  constructor(other: Rule<T>) : this(other.from, other.to)

  /**
   * Make the Knuth-Moris-Pratt Shift table. (helper function)
   */
  private fun makeKMP() {
    val size = from.size
    lut = IntArray(size)
    lut[0] = 1
    lut[1] = 1
    var start = 1
    var same = 0
    while (start < size - 1) {
      var miss = size
      for (i in 0 until size) {
        if (i + start >= size || from[i] !== from[i + start]) {
          miss = i
          break
        }
      }
      if (miss == 0) {
        lut[start + 1] = start + 1
      } else {
        var i = same + 1
        while (i <= miss && i + start < lut.size) {
          lut[start + i] = start
          i++
        }
      }
      start += lut[miss]
      same = if (miss == 0) 0 else miss - lut[miss]
    }
  }

  /**
   * Apply the rule to the given LinkedList. (Will be applied to the given reference)
   * @param input to be rewitten
   * @return true if something has been replaced
   */
  fun apply(input: LinkedList<T>): Boolean = applyOnIter(input.listIterator())

  /**
   * Execute the replacement
   *
   * Works by iterating over the list until a match is found and then using the iterator to
   * replace the match.
   *
   * @param iter active iterator on the linked list
   * @return a boolean indicating if a replacement was made
   */
  private fun applyOnIter(iter: MutableListIterator<T>): Boolean {
    //Look for an occurrence
    var curPos = 0
    val length = from.size
    while (iter.hasNext()) {
      val cur = iter.next()
      if (cur == from[curPos]) {
        curPos++
        if (curPos == length) break
      } else {
        if (curPos != 0) {
          //Knuth-Moris-Pratt
          curPos -= lut[curPos]
          iter.previous() //next .next() is same
        }
      }
    }
    if (curPos != length) {
      //No match was found
      return false
    }

    //Replace the occurrence backwards and remove excess characters
    for (i in 1..length) {
      //move cursor backwards, if first itteration -> same as last next()
      iter.previous()
      if (to.size - i >= 0) iter.set(to[to.size - i]) else iter.remove()
    }
    for (i in 0 until to.size - length) iter.add(to[i])
    return true
  }

  fun getCritical(other: Rule<T>): Set<CriticalPair<T>> {
    val f1 = from
    val f2 = other.from
    val t1 = to
    val t2 = other.to
    val result: MutableSet<CriticalPair<T>> = HashSet()
    for (overlap in f1.size.coerceAtMost(f2.size) downTo 1) {
      var ok = true
      var i = 0
      while (i < overlap && ok) {
        if (f2[i] != f1[f1.size - overlap + i]) ok = false
        i++
      }
      if (ok) {
        //Construct result by applying "this" first
        val critTo1 = LinkedList<T>()
        for (i in t1.indices) critTo1.add(t1[i])
        for (i in overlap until f2.size) critTo1.add(f2[i])

        //Construct result by applying "other" first
        val critTo2 = LinkedList<T>()
        for (i in 0 until f1.size - overlap) critTo2.add(f1[i])
        for (i in t2.indices) critTo2.add(t2[i])
        if (critTo1 != critTo2) result.add(CriticalPair(critTo1, critTo2))
      }
    }
    return result
  }

  /**
   * find out if this Rule can optimize the given rule
   * @param rule the rule that might be optimised
   * @return
   */
  fun canOptimize(rule: Rule<T>): Boolean {
    if (this == rule) return false
    val iter = rule.from.listIterator()
    //Look for an occurrence
    var curPos = 0
    val length = from.size
    while (iter.hasNext()) {
      val cur = iter.next()
      if (cur == from[curPos]) {
        curPos++
        if (curPos == length) return true
      } else {
        if (curPos != 0) {
          //Knuth-Moris-Pratt
          curPos = curPos - lut[curPos]
          iter.previous() //next .next() is same
        }
      }
    }
    return false
  }

  override fun equals(o: Any?): Boolean {
    if (this === o) return true
    if (o == null || javaClass != o.javaClass) return false
    val rule = o as Rule<*>
    return from == rule.from && to == rule.to
  }

  override fun hashCode(): Int {
    var result = from.hashCode()
    result = 31 * result + to.hashCode()
    return result
  }

  override fun toString(): String {
    return "Rule{" +
        from.toString().replace("[ ,\\[\\]]".toRegex(), "") +
        " -> " + to.toString().replace("[ ,\\[\\]]".toRegex(), "") +
        '}'
  }

  fun createCriticalPair(to1: LinkedList<T>, to2: LinkedList<T>): CriticalPair<T> {
    return CriticalPair(to1, to2)
  }

  fun compareTo(o: Rule<T>, comp: Comparator<Collection<T>>): Int {
    var diff = comp.compare(from, o.from)
    if (diff == 0) {
      diff = comp.compare(to, o.to)
    }
    return diff
  }
}

class CriticalPair<T>(val to1: LinkedList<T>, val to2: LinkedList<T>) {
  override fun hashCode(): Int = to1.hashCode() + to2.hashCode()

  override fun toString(): String = "CriticalPair{to1=$to1, to2=$to2}"
}