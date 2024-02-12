package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.automata.*
import ai.hypergraph.kaliningraph.repair.MAX_TOKENS
import ai.hypergraph.kaliningraph.types.*
import ai.hypergraph.kaliningraph.types.times
import kotlin.math.absoluteValue
import kotlin.time.TimeSource

/**
 * Specialized Bar-Hillel construction for Levenshtein automata. See also
 * [FSA.intersect] for the generic Bar-Hillel version with arbitrary FSA.
 */

infix fun FSA.intersectLevFSA(cfg: CFG) = cfg.intersectLevFSA(this)

infix fun CFG.intersectLevFSA(fsa: FSA): CFG = intersectLevFSAP(fsa)
//  subgrammar(fsa.alphabet)
//    .also { it.forEach { println("${it.LHS} -> ${it.RHS.joinToString(" ")}") } }

fun CFG.makeLevGrammar(source: List<Σᐩ>, distance: Int) =
  intersectLevFSA(makeLevFSA(source, distance))

fun CFG.barHillelRepair(prompt: List<Σᐩ>, distance: Int) =
  makeLevGrammar(prompt, distance).enumSeq(List(prompt.size + distance) { "_" })

// http://www.cs.umd.edu/~gasarch/BLOGPAPERS/cfg.pdf#page=2
// https://browse.arxiv.org/pdf/2209.06809.pdf#page=5
private infix fun CFG.intersectLevFSAP(fsa: FSA): CFG {
  var clock = TimeSource.Monotonic.markNow()
  val lengthBoundsCache = lengthBounds
  val nts = mutableSetOf("START")
  fun Σᐩ.isSyntheticNT() =
    first() == '[' && last() == ']' && count { it == '~' } == 2
  fun List<Production>.filterRHSInNTS() =
    asSequence().filter { (_, rhs) -> rhs.all { !it.isSyntheticNT() || it in nts } }

  val initFinal =
    (fsa.init * fsa.final).map { (q, r) -> "START" to listOf("[$q~START~$r]") }
      .filterRHSInNTS()

  val transits =
    fsa.Q.map { (q, a, r) -> "[$q~$a~$r]".also { nts.add(it) } to listOf(a) }
      .filterRHSInNTS()

  // For every production A → σ in P, for every (p, σ, q) ∈ Q × Σ × Q
  // such that δ(p, σ) = q we have the production [p, A, q] → σ in P′.
  val unitProds = unitProdRules(fsa)
    .onEach { (a, _) -> nts.add(a) }.filterRHSInNTS()

  // For each production A → BC in P, for every p, q, r ∈ Q,
  // we have the production [p,A,r] → [p,B,q] [q,C,r] in P′.
  val validTriples =
    fsa.stateCoords.let { it * it * it }.filter { it.isValidStateTriple() }.toList()

  val binaryProds =
    nonterminalProductions.map {
//      if (i % 100 == 0) println("Finished ${i}/${nonterminalProductions.size} productions")
      val (A, B, C) = it.π1 to it.π2[0] to it.π2[1]
      validTriples
        // CFG ∩ FSA - in general we are not allowed to do this, but it works
        // because we assume a Levenshtein FSA, which is monotone and acyclic.
        .filter { it.isCompatibleWith(A to B to C, fsa, lengthBoundsCache) }
        .map { (a, b, c) ->
          val (p, q, r)  = a.π1 to b.π1 to c.π1
          "[$p~$A~$r]".also { nts.add(it) } to listOf("[$p~$B~$q]", "[$q~$C~$r]")
        }.toList()
    }.flatten().filterRHSInNTS()

  println("Constructing ∩-grammar took: ${clock.elapsedNow()}")
  clock = TimeSource.Monotonic.markNow()
  return (initFinal + transits + binaryProds + unitProds).toSet().postProcess()
    .also { println("Bar-Hillel construction took ${clock.elapsedNow()}") }
}

// For every production A → σ in P, for every (p, σ, q) ∈ Q × Σ × Q
// such that δ(p, σ) = q we have the production [p, A, q] → σ in P′.
fun CFG.unitProdRules(fsa: FSA): List<Pair<String, List<Σᐩ>>> =
  (unitProductions * fsa.nominalize().flattenedTriples)
    .filter { (_, σ, arc) -> (arc.π2)(σ) }
    .map { (A, σ, arc) -> "[${arc.π1}~$A~${arc.π3}]" to listOf(σ) }

fun CFG.postProcess() =
    this.also { println("∩-grammar has ${it.size} total productions") }
    .dropVestigialProductions()
    .normalForm
    .noEpsilonOrNonterminalStubs
    .also { println("∩-grammar has ${it.size} useful productions") }
    .freeze()
    //    .also { println(it.pretty) }
    //    .also { println(it.size) }

// Recursively removes all productions from a synthetic CFG containing a
// dangling nonterminal, i.e., a nonterminal that does not produce any terminals
//
// This works but is the most inefficient part of the current implementation...
//
// TODO: Maybe instead of creating an enormous CFG and then removing productions
//       we can just create a CFG that only contains the productions we need, by
//       starting from the terminals and working our way up to START?
//  Consider:
//    ∩-grammar has 96634 total productions
//    Removed 81177 vestigial productions.
//    Removed 15035 vestigial productions.
//    Removed 331 vestigial productions.
//    Removed 57 vestigial productions.
//    Removed 7 vestigial productions.
//    Removed 0 vestigial productions.
//    Disabling nonterminal stubs!
//    ∩-grammar has 56 useful productions <- Why can't we just create this CFG?!
fun CFG.dropVestigialProductions(
  criteria: (Σᐩ) -> Boolean = { it.first() == '[' && it.last() == ']' && it.count { it == '~' } == 2 }
): CFG {
  val nts: Set<Σᐩ> = map { it.LHS }.toSet()
//  val reachable = reachableSymbols()
//  val rw = toMutableSet()
//    .apply { removeAll { prod -> prod.RHS.any { criteria(it) && it !in nts } } }
//    .also { println("Removed ${size - it.size} invalid productions") }
//    .freeze().removeUselessSymbols()
  val rw = asSequence().filter { prod -> prod.RHS.all { !criteria(it) || it in nts } }.toSet()
    .also { println("Removed ${size - it.size} invalid productions") }
    .freeze().removeUselessSymbols()

  println("Removed ${size - rw.size} vestigial productions, resulting in ${rw.size} productions.")

  return if (rw.size == size) rw else rw.dropVestigialProductions(criteria)
}

// Generic Bar-Hillel construction for arbitrary CFL ∩ REG language
infix fun FSA.intersect(cfg: CFG) = cfg.freeze().intersect(this)

infix fun CFG.intersect(fsa: FSA): CFG {
  val clock = TimeSource.Monotonic.markNow()
  val initFinal =
    (fsa.init * fsa.final).map { (q, r) -> "START" to listOf("[$q~START~$r]") }

  val transits =
    fsa.Q.map { (q, a, r) -> "[$q~$a~$r]" to listOf(a) }

  // For every production A → σ in P, for every (p, σ, q) ∈ Q × Σ × Q
  // such that δ(p, σ) = q we have the production [p, A, q] → σ in P′.
  val unitProds = unitProdRules(fsa)

  // For each production A → BC in P, for every p, q, r ∈ Q,
  // we have the production [p,A,r] → [p,B,q] [q,C,r] in P′.
  val binaryProds =
    nonterminalProductions.mapIndexed { i, it ->
      val triples = fsa.states * fsa.states * fsa.states
      val (A, B, C) = it.π1 to it.π2[0] to it.π2[1]
      triples.map { (p, q, r) ->
        "[$p~$A~$r]" to listOf("[$p~$B~$q]", "[$q~$C~$r]")
      }
    }.flatten()

  return (initFinal + transits + binaryProds + unitProds).toSet().postProcess()
    .also { println("Postprocessing took ${clock.elapsedNow()}") }
}

// Tracks the number of tokens a given nonterminal can represent
// e.g., a NT with a bound of 1..3 can parse { s: Σ^[1, 3] }
val CFG.lengthBounds: Map<Σᐩ, IntRange> by cache {
  val clock = TimeSource.Monotonic.markNow()
  val epsFree = noEpsilonOrNonterminalStubs.freeze()
  val tpl = List(MAX_TOKENS + 5) { "_" }
  val map =
    epsFree.nonterminals.associateWith { -1..-1 }.toMutableMap()
    epsFree.initPForestMat(tpl).seekFixpoint().diagonals.mapIndexed { idx, sets ->
    sets.flatMap { it.map { it.key } }.forEach { nt ->
      map[nt]?.let {
        (if (it.first < 0) (idx + 1) else it.first)..(idx + 1)
      }?.let { map[nt] = it }
    }
  }

  println("Computed NT length bounds in ${clock.elapsedNow()}")
  map
}

fun Π3A<STC>.isValidStateTriple(): Boolean {
  fun Pair<Int, Int>.dominates(other: Pair<Int, Int>) =
    first <= other.first && second <= other.second

  return first.coords().dominates(second.coords())
      && second.coords().dominates(third.coords())
}

fun Π3A<STC>.isCompatibleWith(nts: Triple<Σᐩ, Σᐩ, Σᐩ>, fsa: FSA, lengthBounds: Map<Σᐩ, IntRange>): Boolean {
  fun lengthBounds(nt: Σᐩ, fudge: Int = 1): IntRange =
    (lengthBounds[nt] ?: -9999..-9990)
      // Okay if we overapproximate the length bounds a bit
//      .let { (it.first - fudge)..(it.last + fudge) }

  fun manhattanDistance(first: Pair<Int, Int>, second: Pair<Int, Int>): Int =
    (second.second - first.second).absoluteValue + (second.first - first.first).absoluteValue

  // Range of the shortest path to the longest path, i.e., Manhattan distance
  fun SPLP(a: STC, b: STC) =
    (fsa.APSP[a.π1 to b.π1] ?: Int.MAX_VALUE)..
        manhattanDistance(a.coords(), b.coords())

  fun IntRange.overlaps(other: IntRange) =
    (other.first in first..last) || (other.last in first..last)

  // "[$p,$A,$r] -> [$p,$B,$q] [$q,$C,$r]"
  fun isCompatible() =
    lengthBounds(nts.first).overlaps(SPLP(first, third))
      && lengthBounds(nts.second).overlaps(SPLP(first, second))
      && lengthBounds(nts.third).overlaps(SPLP(second, third))

  return isCompatible()
}