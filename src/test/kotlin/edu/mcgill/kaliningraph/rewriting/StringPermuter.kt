package edu.mcgill.kaliningraph.rewriting

fun main() {
  val m = 0..3
  val s = m.map { listOf(it) }.toSet().grayCode()

//  println(s.size)
//  println(s.joinToString("\n"))
  val w = m.map { ('a'..'z').toList()[it] }.joinToString("")
  println(w.conv(s))
//  println("i: " + "abcd".conv(setOf(listOf(1, 0))))
//  println(s.joinToString { it.joinToString("") })
}

operator fun <Q, T: Iterable<Q>> Set<T>.times(s: Set<T>) =
  flatMap { l ->
    s.map { r ->
      (l + r).distinct()
    }.toSet()
  }.toSet()

infix fun <T> Set<T>.cross(s: Set<T>) =
  flatMap { l -> s.map { r -> l to r }.toSet() }.toSet()

private fun Set<List<Int>>.grayCode(int: Int = size) =
  (0..int).fold(this) { acc, _ -> acc * this }
    .let {
      val t = it.maxOf { it.size }
      it.filter { it.size > t - 1 }.toSet()
    }

// Tries to generate all permutations using convolution
// on a ring buffer containing a string. Encoding:
//
// [2, 1, 0, 3] <- index of replacement character
//  0  1  2  3  <- index of original character
//
// Must be a bijection so we do not delete anything.
// e.g. [1, 0] swaps characters until fixpoint reached.
// This should generate the following sequence:
// abcd
// bacd
// bcad
// bcda
// acdb
// ...
// dbca
// abcd <- fixpoint reached
// e.g. [2, 0, 1] permutes every abc -> cab
// e.g. [1, 0, 3, 2] permutes every abcd -> badc
//
// Qs: Can we generate all possible permutations
// using just convolution or convolution with aperiodic
// offsets? What is the maximum period cycle? What rewrite
// generates the maximum number of unique strings?
// https://mathworld.wolfram.com/ElementaryCellularAutomaton.html
// R: Set of rewrites to try

fun String.conv(R: Set<List<Int>>): Int? =
  R.map { r: List<Int> ->
    val t = toCharArray()
    var i = 0
    var q = 0
    var found = emptySet<String>()
    do {
      val rp = r.map { t[(i + it) % t.size] }
      r.forEachIndexed { m, n ->
        t[(i + m) % t.size] = rp[m]
      }

      val cc = t.joinToString("")//.also { println(it) }
      found += cc
      i++
//      if(i % t.size == 0){ q++; i+= Random.nextInt(40) }
    } while (cc != this@conv)
    found.size
  }.max()