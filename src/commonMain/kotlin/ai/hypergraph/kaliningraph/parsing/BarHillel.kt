package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.automata.*
import ai.hypergraph.kaliningraph.repair.MAX_TOKENS
import ai.hypergraph.kaliningraph.types.*
import ai.hypergraph.kaliningraph.types.times
import kotlin.math.*
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
  val prods: Set<Pair<Int, List<Int>>> = nonterminalProductions
    .map { (a, bc) -> ntMap[a]!! to bc.map { ntMap[it]!! } }.toSet()
//  val lengthBoundsCache = lengthBounds.let { lb -> ntLst.map { lb[it] ?: 0..0 } }
  val validTriples = fsa.validTriples.map { arrayOf(it.π1.π1, it.π2.π1, it.π3.π1) }.toTypedArray()

  val ct = (fsa.validPairs * nonterminals.indices.toSet()).toList()
//  val ct1 = Array(fsa.states.size) { Array(nonterminals.size) { Array(fsa.states.size) { false } } }
//  ct.filter { lengthBoundsCache[it.π3].overlaps(fsa.SPLP(it.π1, it.π2)) }
//    .forEach { ct1[it.π1.π1][it.π3][it.π2.π1] = true }
  val ct2 = Array(fsa.states.size) { Array(nonterminals.size) { Array(fsa.states.size) { false } } }
  ct.filter { fsa.obeys(it.π1, it.π2, it.π3, parikhMap) }
    .forEach { ct2[it.π1.π1][it.π3][it.π2.π1] = true }

  val states = fsa.stateLst
  val allsym = ntLst
  val binaryProds =
    prods.map {
//      if (i % 100 == 0) println("Finished ${i}/${nonterminalProductions.size} productions")
      val (A, B, C) = it.π1 to it.π2[0] to it.π2[1]
      val trip = arrayOf(A, B, C)
      validTriples
        // CFG ∩ FSA - in general we are not allowed to do this, but it works
        // because we assume a Levenshtein FSA, which is monotone and acyclic.
//        .filter { it.checkCT(trip, ct1) }
        .filter { it.checkCompatibility(trip, ct2) }
//        .filter { it.obeysLevenshteinParikhBounds(A to B to C, fsa, parikhMap) }
        .map { (a, b, c) ->
          val (p, q, r)  = states[a] to states[b] to states[c]
          "[$p~${allsym[A]}~$r]".also { nts.add(it) } to listOf("[$p~${allsym[B]}~$q]", "[$q~${allsym[C]}~$r]")
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

val CFG.parikhMap: ParikhMap by cache {
  val clock = TimeSource.Monotonic.markNow()
  val parikhMap = ParikhMap(this, MAX_TOKENS + 5)
  println("Computed Parikh map in ${clock.elapsedNow()}")
  parikhMap
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
    sets
      .first().keys
//      .flatMap { it.map { it.key } }.toSet()
      .forEach { nt ->
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

/**
 * Before Levenshtein-Parikh contraints:
 * Levenshtein-40x3 automaton has 641 arcs!
 * Constructed ∩-grammar with 140110438 productions in 18.130682713s
 * Reduced ∩-grammar from 126119038 to 40670 useful productions in 6m 55.547571414s
 * Constructed LEV(3, 40, 641) ∩ CFG grammar with 40670 productions in 7m 13.682658465s
 * Human repair is recognized by LEV ∩ CFG grammar
 * Ground truth repair: NAME = NAME ( NAME , STRING ) NEWLINE NAME = NAME . NAME ( ) NEWLINE NAME = NAME ( [ ] [ NAME [ NUMBER ] . NAME ( STRING ) + NUMBER : ] . NAME ( ) ) NEWLINE
 * Drew 3921 samples in 30s, 40670 prods, length-3 human repair not found
 *
 * After Levenshtein-Parikh contraints:
 * Levenshtein-40x3 automaton has 641 arcs!
 * Constructed ∩-grammar with 69055302 productions in 17.683343522s
 * Reduced ∩-grammar from 30075865 to 40670 useful productions in 2m 25.985649805s
 * Constructed LEV(3, 40, 641) ∩ CFG grammar with 40670 productions in 2m 43.673171671s
 * Human repair is recognized by LEV ∩ CFG grammar
 * Ground truth repair: NAME = NAME ( NAME , STRING ) NEWLINE NAME = NAME . NAME ( ) NEWLINE NAME = NAME ( [ ] [ NAME [ NUMBER ] . NAME ( STRING ) + NUMBER : ] . NAME ( ) ) NEWLINE
 * Drew 3389 samples in 30s, 40670 prods, length-3 human repair not found
 */

//fun Π3A<STC>.obeysLevenshteinParikhBounds(nts: Triple<Σᐩ, Σᐩ, Σᐩ>, fsa: FSA, parikhMap: ParikhMap): Boolean {
//  fun sameLevel(a: STC, b: STC) = a.third == b.third
//  fun fetchPath(a: STC, b: STC) = fsa.levString
////    .also { println("Levstr(${a.second}, ${b.second}): $it :: ${it.subList(a.second, b.second)}") }
//    .subList(a.second, b.second)
//  fun lpImage(a: STC, b: STC) = fetchPath(a, b).parikhVector()
////    .also { println("${fetchPath(a, b)} => $it") }
//  fun obeys(a: STC, b: STC, nt: Σᐩ): Bln {
//    val sl = !sameLevel(a, b) ||
//      fsa.levString.size <= a.second ||
//      fsa.levString.size <= b.second
//
//    if (sl) return true
////    println("Filtering by Parikh bounds: $a, $b, $nt")
//
//    val length = (b.second - a.second)
//    val pb = parikhMap.parikhBounds(nt, length)
////    println("PB ($nt,$length) : $pb / ${fsa.levString.subList(a.second, b.second)}")
//
//    val pv = lpImage(a, b)
////    println("PV: $pv")
//    return pb?.subsumes(pv) ?: false
//  }
//
//  return obeys(first, third, nts.first)
//      && obeys(first, second, nts.second)
//      && obeys(second, third, nts.third)
//}

fun FSA.obeys(a: STC, b: STC, nt: Int, parikhMap: ParikhMap): Bln {
  val sl = levString.size <= max(a.second, b.second) // Part of the LA that handles extra

  if (sl) return true
  val margin = (b.third - a.third).absoluteValue
  val length = (b.second - a.second)
  val range = (length - margin).coerceAtLeast(1)..(length + margin)
  val pb = parikhMap.parikhBounds(nt, range)
  val pv = parikhVector(a.second, b.second)
  return pb.admits(pv, margin)
}

fun Π3A<STC>.obeysLevenshteinParikhBounds(nts: Π3A<Int>, fsa: FSA, parikhMap: ParikhMap): Boolean =
  fsa.obeys(first, third, nts.first, parikhMap)
    && fsa.obeys(first, second, nts.second, parikhMap)
    && fsa.obeys(second, third, nts.third, parikhMap)

private fun manhattanDistance(first: Pair<Int, Int>, second: Pair<Int, Int>): Int =
  (second.second - first.second).absoluteValue + (second.first - first.first).absoluteValue

// Range of the shortest path to the longest path, i.e., Manhattan distance
fun FSA.SPLP(a: STC, b: STC) =
  (APSP[a.π1 to b.π1] ?: Int.MAX_VALUE)..//.also { /*if (Random.nextInt(100000) == 3) if(it == Int.MAX_VALUE) println("Miss! ${hash(a.π1, b.π1)} / ${a.first} / ${b.first}") else */
//    if (it != Int.MAX_VALUE) println("Hit: ${hash(a.π1, b.π1)} / ${a.first} / ${b.first}") }..
      manhattanDistance(a.coords(), b.coords())

fun IntRange.overlaps(other: IntRange) =
  (other.first in first..last) || (other.last in first..last)

fun Π3A<STC>.isCompatibleWith(nts: Π3A<Int>, fsa: FSA, lengthBounds: List<IntRange>): Boolean =
    lengthBounds[nts.first].overlaps(fsa.SPLP(first, third))
      && lengthBounds[nts.second].overlaps(fsa.SPLP(first, second))
      && lengthBounds[nts.third].overlaps(fsa.SPLP(second, third))

fun Array<Int>.checkCompatibility(nts: Array<Int>, ct: Array<Array<Array<Boolean>>>): Boolean =
  ct[this[0]][nts[0]][this[2]] &&
  ct[this[0]][nts[1]][this[1]] &&
  ct[this[1]][nts[2]][this[2]]