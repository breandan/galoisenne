package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.cache.LRUCache
import ai.hypergraph.kaliningraph.graphs.LabeledGraph
import ai.hypergraph.kaliningraph.types.*

//=====================================================================================
// CFG Normalization
// http://firsov.ee/cert-norm/cfg-norm.pdf
// https://www.cs.rit.edu/~jmg/courses/cs380/20051/slides/7-1-chomsky.pdf
// https://user.phil-fak.uni-duesseldorf.de/~kallmeyer/Parsing/cyk.pdf#page=21

// Helps keep track of the history of CFG transformations for debugging purposes
val rewriteHistory = LRUCache<CFG, List<CFG>>()

/**
 * n.b. Normalization may destroy organic nonterminals!
 * If you want to preserve every organic NT, then you
 * must first generateNonterminalStubs() for all V ∈ G
 * to ensure that ∃v.(v->*) ∈ G => (v-><v>) ∈ G holds.
 */
fun CFG.normalize(): CFG =
  mutableListOf<CFG>().let { rewrites ->
    addGlobalStartSymbol()
      .expandOr()
      .unescape()
      .also { rewrites.add(it) } /** [originalForm] */
      .eliminateParametricityFromLHS()
      .also { rewrites.add(it) } /** [nonparametricForm] */
      .generateNonterminalStubs()
//      .transformIntoCNF()
      .transformIntoCNFFast()
      .freeze()
      // This should occur after CNF transform otherwise it causes issues
      // during nonterminal-constrained synthesis, e.g., _ _ _ <NT> _ _ _
      // because we do not use equivalence class during bitvector encoding
      // Must remember to run the unit test if order changes in the future
      // ./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.sat.SATValiantTest.testTLArithmetic"
      // ./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SeqValiantTest.testTLArithmetic"
      // .generateNonterminalStubs()
      .also { cnf -> rewriteHistory.put(cnf, rewrites) }
  }

fun CFG.transformIntoCNF(): CFG =
  addEpsilonProduction()
    .refactorEpsilonProds()
    .elimVarUnitProds()
//    .binarizeRHSByFrequency()
    .binarizeRHSByRightmost()
    .terminalsToUnitProds()
    .removeUselessSymbols()

val START_SYMBOL = "START"

fun Σᐩ.getParametersIn(cfg: CFG) =
  cfg.unitProductions.map { it.first }.filter { "<$it>" in this }

fun CFG.eliminateParametricityFromRHS(
  ntReplaced: Σᐩ,
  ntReplacements: Set<Σᐩ>
): CFG =
  if (ntReplacements.isEmpty()) this
  else flatMap { prod ->
    if (prod.RHS.none { ntReplaced in it }) listOf(prod)
    else ntReplacements.map { ntr ->
      (prod.LHS to prod.RHS.map { if (it == ntReplaced) ntr else it })
    }
  }.toSet()

fun CFG.eliminateParametricityFromLHS(
  parameters: Set<Σᐩ> =
    nonterminals.flatMap { it.getParametersIn(this) }.toSet()
): CFG =
  if (parameters.isEmpty()) this else {
    var i = false
    var (ntReplaced, ntReplacements) = "" to setOf<Σᐩ>()
    flatMap { prod ->
      val params = prod.LHS.getParametersIn(this)
      if (params.isEmpty() || i) return@flatMap listOf(prod)
      i = true
      ntReplaced = prod.LHS
      val map = params.associateWith { bimap[it].map { it[0] }.toSet() }
      val (s, r) = map.entries.maxByOrNull { it.value.size }!!
      r.map { rc ->
        prod.LHS.replace("<$s>", "<$rc>").also { ntReplacements += it } to
          prod.RHS.map { it.replace("<$s>", "<$rc>") }
      }
    }.toSet()
      .eliminateParametricityFromRHS(ntReplaced, ntReplacements)
      .eliminateParametricityFromLHS()
  }

fun CFG.generateNonterminalStubs(): CFG =
  this + (filter { it.LHS.isOrganicNonterminal()  }
    .map { it.LHS to listOf("<${it.LHS}>") }.toSet()).addEpsilonProduction()

fun Σᐩ.isSyntheticNonterminal() =
  split('.').size != 1 || "ε" in this || this == "START"
fun Σᐩ.isOrganicNonterminal() =
  split('.').size == 1 && "ε" !in this && this != "START"

// Add start symbol if none are present (e.g., in case the user forgets)
private fun CFG.addGlobalStartSymbol(): CFG =
  this + if (START_SYMBOL in nonterminals) emptySet()
  else nonterminals.map { START_SYMBOL to listOf(it) }

// Expands RHS `|` productions, e.g., (A -> B | C) -> (A -> B, A -> C)
fun CFG.expandOr(): CFG =
  flatMap { prod ->
    prod.RHS.fold(listOf(listOf<Σᐩ>())) { acc, s ->
      if (s == "|") (acc + listOf(listOf()))
      else (acc.dropLast(1) + listOf(acc.last() + s))
    }.map { prod.LHS to it }
  }.toSet()

fun CFG.unescape(): CFG =
  map { (l, r) -> l to r.map { it.stripEscapeChars() } }.toSet()

// Adds V -> εV | Vε to every unit production [V -> v] in CFG
// so that holes can be [optionally] elided by the SAT solver.
private fun CFG.addEpsilonProduction(): CFG =
  terminalUnitProductions.filterNot { "ε" in it.pretty() }.map { it.LHS }.toSet()
    .fold(this) { acc, it -> acc + (it to listOf(it, "ε+")) + (it to listOf("ε+", it)) } +
    ("ε+" to listOf("ε+", "ε+")) + ("ε+" to listOf("ε"))

// http://firsov.ee/cert-norm/cfg-norm.pdf#subsection.3.1
tailrec fun CFG.nullableNonterminals(
  nbls: Set<Σᐩ> = setOf("ε"),
  nnts: Set<Σᐩ> = filter { nbls.containsAll(it.RHS) }.map { it.LHS }.toSet()
): Set<Σᐩ> = if (nnts == (nbls - "ε")) nnts else nullableNonterminals(nnts + nbls)

fun List<Σᐩ>.drop(nullables: Set<Σᐩ>, keep: Set<Int>): List<Σᐩ> =
  mapIndexedNotNull { i, s ->
    if (s in nullables && i !in keep) null
    else if (s in nullables && i in keep) s
    else s
  }

// http://firsov.ee/cert-norm/cfg-norm.pdf#subsection.3.2
fun Production.allSubSeq(nullables: Set<Σᐩ>): Set<Production> =
  RHS.indices.filter { RHS[it] in nullables }.powerset().toSet()
    .map { idxs -> LHS to RHS.drop(nullables, idxs) }.toSet()

/**
 * Makes ε-productions optional. n.b. We do not use CNF, but almost-CNF!
 * ε-productions are allowed because we want to be able to synthesize them
 * as special characters, then simply omit them during printing.
 *
 *  - Determine nullable variables, i.e., those which contain ε on the RHS
 *  - For each production omit every possible subset of nullable variables,
 *      e.g., (P -> AxB, A -> ε, B -> ε) -> (P -> xB, P -> Ax, P -> x)
 *  - Remove all productions with an empty RHS
 */

fun CFG.refactorEpsilonProds(nlbls: Set<Σᐩ> = nullableNonterminals()): CFG =
  (this + setOf(START_SYMBOL to listOf(START_SYMBOL, "ε")))
    .flatMap { p -> if (p.RHS.any { it in nlbls }) p.allSubSeq(nlbls) else listOf(p) }
    .filter { it.RHS.isNotEmpty() }.toSet()

/**
 * Eliminate all non-generating and unreachable symbols.
 *
 * All terminal-producing symbols are generating.
 * If A -> [..] and all symbols in [..] are generating, then A is generating
 * No other symbols are generating.
 *
 * START is reachable.
 * If S -> [..] is reachable, then all variables in [..] are reachable.
 * No other symbols are reachable.
 *
 * A useful symbol is both generating and reachable.
 */

// TODO: https://zerobone.net/blog/cs/non-productive-cfg-rules/
fun CFG.removeUselessSymbols(
  generating: Set<Σᐩ> = genSym(),
  reachable: Set<Σᐩ> = reachSym()
): CFG =
//  toMutableSet()
//    .apply { removeAll { (s, _) -> s !in generating } }
//    .also { println("Removed ${size - it.size} nongenerating prods") }
//    .apply { removeAll { (s, _) -> s !in reachable } }
//    .also { println("Removed ${size - it.size} unreachable prods") }
//    .toSet()

//  toMutableSet().apply {
//    removeAll { (s, _) -> s !in generating || s !in reachable }
//  }
 asSequence().filter { (s, _) -> s in generating && s in reachable }.toSet()

fun CFG.reachSym(from: Σᐩ = START_SYMBOL): Set<Σᐩ> {
  val allReachable: MutableSet<Σᐩ> = mutableSetOf(from)
  val nextReachable = mutableSetOf(from)

  do {
    val t = nextReachable.first()
    nextReachable.remove(t)
    allReachable += t
    nextReachable += (bimap.NDEPS[t]?: emptyList())
      .filter { it !in allReachable && it !in nextReachable }
  } while (nextReachable.isNotEmpty())

//  println("TERM: ${allReachable.any { it in terminals }} ${allReachable.size}")

  return allReachable
}

fun CFG.genSym(from: Set<Σᐩ> = terminalUnitProductions.map { it.LHS }.toSet()): Set<Σᐩ> {
  val allGenerating: MutableSet<Σᐩ> = mutableSetOf()
  val nextGenerating = from.toMutableSet()

  do {
    val t = nextGenerating.first()
    nextGenerating.remove(t)
    allGenerating += t
    nextGenerating += (bimap.TDEPS[t] ?: emptyList())
      .filter { it !in allGenerating && it !in nextGenerating }
  } while (nextGenerating.isNotEmpty())

//  println("START: ${START_SYMBOL in allGenerating} ${allGenerating.size}")

  return allGenerating
}

//  .also {
//    println(
//      it.second.joinToString("\n") { (l, r) ->
//        "Removed ($l -> ${r.joinToString(" ")}) because it was" +
//            if (l !in generating && l !in reachable) "non-generating/unreachable."
//            else if (l !in generating) "non-generating."
//            else "unreachable."
//      }
//    )
//  }
//  .first.toSet()

//fun CFG.removeNonGenerating(generating: Set<Σᐩ> = genSym()) =
//  toMutableSet().apply { removeAll { (s, _) -> s !in generating } }
//
//fun CFG.removeUnreachable() =
//  toMutableSet().apply { removeAll { (s, _) -> !reachableNTs[bindex[s]] } }
//    .also { println("Removed ${size - it.size} unreachable productions.") }

fun CFG.equivalenceClass(from: Σᐩ): Set<Σᐩ> = unitReachability[from] ?: setOf(from)

fun LabeledGraph.transitiveClosure(from: Set<Σᐩ>) =
  transitiveClosure(filter { it.label in from }).map { it.label }.toSet()

// All symbols that are reachable from START_SYMBOL
fun CFG.reachableSymbols(from: Σᐩ = START_SYMBOL): Set<Σᐩ> =
  reachability.getOrPut(from) { depGraph.transitiveClosure(setOf(from)) }

// All symbols that are either terminals or generate terminals
fun CFG.generatingSymbols(
  from: Set<Σᐩ> = terminalUnitProductions.map { it.LHS }.toSet(),
  revGraph: LabeledGraph = revDepGraph
): Set<Σᐩ> = revGraph.transitiveClosure(from)

/* Drops variable unit productions, for example:
 * Initial grammar: (A -> B, B -> c, B -> d) ->
 * After expansion: (A -> B, A -> c, A -> d, B -> c, B -> d) ->
 * After elimination: (A -> c, A -> d, B -> c, B -> d)
 */
private tailrec fun CFG.elimVarUnitProds(
  toVisit: Set<Σᐩ> = nonterminals,
  vars: Set<Σᐩ> = nonterminals,
  toElim: Σᐩ? = toVisit.firstOrNull()
): CFG {
  fun Production.isVariableUnitProd() = RHS.size == 1 && RHS[0] in vars
  if (toElim == null) return filter { !it.isVariableUnitProd() }
  val varsThatMapToMe =
    filter { it.RHS.size == 1 && it.RHS[0] == toElim }.map { it.LHS }.toSet()
  val thingsIMapTo = filter { it.LHS == toElim }.map { it.RHS }.toSet()
  return (varsThatMapToMe * thingsIMapTo).fold(this) { g, p -> g + p }
    .elimVarUnitProds(toVisit.drop(1).toSet(), vars)
}

// Counts the number of times a pair of adjacent symbols appears in the RHS of a production
private fun CFG.countPairFreqs() =
  flatMap { it.RHS.windowed(2, 1) }.groupingBy { it }.eachCount()

// TODO: try different heuristics from https://pages.cs.wisc.edu/~sding/paper/EMNLP2008.pdf
// Refactors long productions, e.g., (A -> BCD) -> (A -> BE, E -> CD)
private tailrec fun CFG.binarizeRHSByFrequency(): CFG {
  val histogram: Map<List<Σᐩ>, Int> = countPairFreqs()
  // Greedily chooses the production with the RHS pair that appears most frequently
  val eligibleProds = filter { it.RHS.size > 2 }.maxByOrNull { longProd ->
      longProd.RHS.windowed(2, 1).maxOfOrNull { histogram[it]!! } ?: 0
    } ?: return this.elimVarUnitProds()
  val mostFreqPair = eligibleProds.RHS.windowed(2, 1).mapIndexed { i, it -> i to it }.toSet()
    .maxByOrNull { histogram[it.second]!! }!!
  val freshName = mostFreqPair.second.joinToString(".")
  val newProd = freshName to mostFreqPair.second
  // Replace frequent pair of adjacent symbols in RHS with freshName
  val allProdsWithPair = filter { mostFreqPair.second in it.RHS.windowed(2) }
  val spProds = allProdsWithPair.map {
    val idx = it.RHS.windowed(2).indexOfFirst { it == mostFreqPair.second }
    it.LHS to (it.RHS.subList(0, idx) + freshName + it.RHS.subList(idx + 2, it.RHS.size))
  }
  val newGrammar = (this - allProdsWithPair) + spProds + newProd
  return if (this == newGrammar) this.elimVarUnitProds() else newGrammar.binarizeRHSByFrequency()
}

private tailrec fun CFG.binarizeRHSByRightmost(): CFG {
  val longProd = firstOrNull { it.RHS.size > 2 } ?: return this
  val freshName = longProd.RHS.takeLast(2).joinToString(".")
  val newProd = freshName to longProd.RHS.takeLast(2)
  val shortProd = longProd.LHS to (longProd.RHS.dropLast(2) + freshName)
  val newGrammar = this - longProd + shortProd + newProd
  return if (this == newGrammar) this else newGrammar.binarizeRHSByRightmost()
}

// Replaces terminals in non-unit productions, e.g., (A -> bC) -> (A -> BC, B -> b)
private tailrec fun CFG.terminalsToUnitProds(): CFG {
  val mixProd = nonterminalProductions.firstOrNull { it.RHS.any { it !in nonterminals } } ?: return this
  val termIdx = mixProd.RHS.indexOfFirst { it !in nonterminals }
  val freshName = "F." + mixProd.RHS[termIdx]
  val freshRHS = mixProd.RHS.toMutableList().also { it[termIdx] = freshName }
  val newProd = freshName to listOf(mixProd.RHS[termIdx])
  val newGrammar = this - mixProd + (mixProd.LHS to freshRHS) + newProd
  return if (this == newGrammar) this else newGrammar.freeze().terminalsToUnitProds()
}

// One-shot CNF transform: ε-refactor + unit-closure + terminal lifting + binarization + prune
fun CFG.transformIntoCNFFast(): CFG {
  // ---------- helpers ----------
  fun isNt(sym: Σᐩ, nts: Set<Σᐩ>) = sym in nts
  fun isTerminal(sym: Σᐩ, nts: Set<Σᐩ>) = sym !in nts

  // Build initial NT set from LHSs
  val nts0 = LinkedHashSet<Σᐩ>(this.size * 2).apply { for ((l, _) in this@transformIntoCNFFast) add(l) }

  // If user forgot START, mimic addGlobalStartSymbol() behavior
  val baseProds = ArrayList<Production>(this.size + nts0.size + 8)
  baseProds.addAll(this)
  if (START_SYMBOL !in nts0) {
    nts0.add(START_SYMBOL)
    for (nt in nts0) if (nt != START_SYMBOL) baseProds.add(START_SYMBOL to listOf(nt))
  }

  // ---------- 1) add ε+ wrapper prods (same idea as addEpsilonProduction) ----------
  // "holes" = LHS of terminal unit productions in the *input* grammar, excluding ε
  val holeNTs = LinkedHashSet<Σᐩ>()
  run {
    val ntsSnap = nts0 // snapshot for membership tests here
    for ((lhs, rhs) in baseProds) {
      if (rhs.size == 1) {
        val s = rhs[0]
        if (s != "ε" && isTerminal(s, ntsSnap)) holeNTs.add(lhs)
      }
    }
  }

  // Add: V -> ε+ V | V ε+   and ε+ -> ε+ ε+ | ε
  val withEpsPlus = ArrayList<Production>(baseProds.size + holeNTs.size * 2 + 4)
  withEpsPlus.addAll(baseProds)
  nts0.add("ε+")
  for (v in holeNTs) {
    withEpsPlus.add(v to listOf(v, "ε+"))
    withEpsPlus.add(v to listOf("ε+", v))
  }
  withEpsPlus.add("ε+" to listOf("ε+", "ε+"))
  withEpsPlus.add("ε+" to listOf("ε"))

  // Also match refactorEpsilonProds() pre-step: add START -> START ε
  withEpsPlus.add(START_SYMBOL to listOf(START_SYMBOL, "ε"))

  // ---------- 2) nullable + ε-refactor in one pass ----------
  val nts1 = LinkedHashSet<Σᐩ>().apply { for ((l, _) in withEpsPlus) add(l) }
  val nullable = LinkedHashSet<Σᐩ>().apply { add("ε") }

  // Fixpoint nullable: A nullable if A -> (all nullable symbols)
  run {
    var changed: Boolean
    do {
      changed = false
      for ((lhs, rhs) in withEpsPlus) {
        if (lhs in nullable) continue
        if (rhs.isNotEmpty() && rhs.all { it in nullable }) {
          nullable.add(lhs); changed = true
        }
      }
    } while (changed)
  }

  // Expand productions by dropping any subset of nullable occurrences; keep non-empty RHS
  val epsExpanded = LinkedHashSet<Production>(withEpsPlus.size * 2)
  fun emitNullableVariants(lhs: Σᐩ, rhs: List<Σᐩ>) {
    // indices of nullable symbols in rhs
    val idxs = IntArray(rhs.size)
    var k = 0
    for (i in rhs.indices) if (rhs[i] in nullable) idxs[k++] = i
    if (k == 0) {
      epsExpanded.add(lhs to rhs)
      return
    }

    // Enumerate keep/drop choices for nullable positions without allocating powersets.
    // For k up to 62 this is fine; if k is huge, grammar is already toast.
    val total = 1L shl k
    for (mask in 0L until total) {
      val out = ArrayList<Σᐩ>(rhs.size)
      for (i in rhs.indices) {
        val s = rhs[i]
        if (s in nullable) {
          // keep nullable at position i iff corresponding bit is 1
          val j = run {
            // find j such that idxs[j] == i (k is small in practice; linear scan is ok)
            var jj = 0
            while (jj < k && idxs[jj] != i) jj++
            jj
          }
          if (j < k && ((mask ushr j) and 1L) == 0L) continue
        }
        out.add(s)
      }
      if (out.isNotEmpty()) epsExpanded.add(lhs to out)
    }
  }

  for ((lhs, rhs) in withEpsPlus) {
    if (rhs.any { it in nullable }) emitNullableVariants(lhs, rhs) else if (rhs.isNotEmpty()) epsExpanded.add(lhs to rhs)
  }

  // Recompute NTs after expansion (still just LHS)
  val nts2 = LinkedHashSet<Σᐩ>().apply { for ((l, _) in epsExpanded) add(l) }

  // ---------- 3) lift terminals to preterminals (linear) ----------
  val termToPre = HashMap<Σᐩ, Σᐩ>(64)
  val preTermUnits = LinkedHashSet<Production>(64)

  fun ensurePreterminal(t: Σᐩ): Σᐩ {
    return termToPre.getOrPut(t) {
      val base = "F.$t"
      var name = base
      if (name in nts2) {
        var i = 1
        while (("$base#$i").also { name = it } in nts2) i++
      }
      nts2.add(name)
      preTermUnits.add(name to listOf(t))
      name
    }
  }

  val lifted = ArrayList<Production>(epsExpanded.size + 64)
  for ((lhs, rhs) in epsExpanded) {
    if (rhs.size <= 1) {
      lifted.add(lhs to rhs)
    } else {
      val out = ArrayList<Σᐩ>(rhs.size)
      var changed = false
      for (s in rhs) {
        if (isNt(s, nts2)) out.add(s)
        else { changed = true; out.add(ensurePreterminal(s)) }
      }
      lifted.add(lhs to if (changed) out else rhs)
    }
  }
  // include generated F.t -> t
  lifted.addAll(preTermUnits)

  val nts3 = LinkedHashSet<Σᐩ>().apply { for ((l, _) in lifted) add(l) }

  // ---------- 4) eliminate variable unit prods via unit-closure ----------
  // Partition into: unit edges (A->B), terminal units (A->t), and proper rules (len>=2)
  val declared = nts3.toList()
  val idxOf = HashMap<Σᐩ, Int>(declared.size * 2).apply {
    for (i in declared.indices) put(declared[i], i)
  }
  val n = declared.size
  val words = (n + 63) ushr 6

  val unitAdj = Array(n) { IntArray(0) }
  val unitTmp = Array(n) { ArrayList<Int>(2) }
  val termUnits = Array(n) { LinkedHashSet<Σᐩ>() }
  val properByLhs = Array(n) { ArrayList<List<Σᐩ>>(4) }

  for ((lhs, rhs) in lifted) {
    val a = idxOf[lhs] ?: continue
    if (rhs.size == 1) {
      val s = rhs[0]
      val b = idxOf[s]
      if (b != null) unitTmp[a].add(b)
      else termUnits[a].add(s)
    } else if (rhs.size >= 2) {
      properByLhs[a].add(rhs)
    }
  }
  for (i in 0 until n) unitAdj[i] = unitTmp[i].toIntArray()

  // Transitive closure over unit graph using bitsets with iterative propagation
  val reach = Array(n) { LongArray(words) }
  fun setBit(bs: LongArray, j: Int) { bs[j ushr 6] = bs[j ushr 6] or (1L shl (j and 63)) }
  fun orInto(dst: LongArray, src: LongArray): Boolean {
    var changed = false
    for (w in dst.indices) {
      val x = dst[w]
      val y = x or src[w]
      if (y != x) { dst[w] = y; changed = true }
    }
    return changed
  }

  for (a in 0 until n) for (b in unitAdj[a]) setBit(reach[a], b)

  run {
    var changed: Boolean
    do {
      changed = false
      for (a in 0 until n) {
        // reach[a] |= reach[b] for all b in unitAdj[a]
        for (b in unitAdj[a]) {
          if (orInto(reach[a], reach[b])) changed = true
        }
      }
    } while (changed)
  }

  // Closed terminal units + closed proper rules
  val closedTermUnits = Array(n) { LinkedHashSet<Σᐩ>() }
  val closedProper = Array(n) { ArrayList<List<Σᐩ>>(4) }

  for (a in 0 until n) {
    closedTermUnits[a].addAll(termUnits[a])
    closedProper[a].addAll(properByLhs[a])

    val bs = reach[a]
    for (w in 0 until words) {
      var bits = bs[w]
      while (bits != 0L) {
        val lsb = bits and -bits
        val bitIdx = lsb.countTrailingZeroBits()
        val b = (w shl 6) + bitIdx
        if (b < n) {
          closedTermUnits[a].addAll(termUnits[b])
          closedProper[a].addAll(properByLhs[b])
        }
        bits -= lsb
      }
    }
  }

  // ---------- 5) binarize (rightmost) with pair reuse ----------
  val pairToName = HashMap<Pair<Σᐩ, Σᐩ>, Σᐩ>(256)
  val binaryRules = LinkedHashSet<Production>(1024)
  val nts4 = LinkedHashSet<Σᐩ>(nts3.size + 256).apply { addAll(nts3) }

  fun ensureBin(a: Σᐩ, b: Σᐩ): Σᐩ {
    val key = a to b
    return pairToName.getOrPut(key) {
      // mimic old naming ($a.$b), but collision-safe
      val base = "$a.$b"
      var name = base
      if (name in nts4) {
        var i = 1
        while (("$base#$i").also { name = it } in nts4) i++
      }
      nts4.add(name)
      binaryRules.add(name to listOf(a, b))
      name
    }
  }

  val cnfish = LinkedHashSet<Production>(2048)
  // terminal units
  for (a in 0 until n) {
    val lhs = declared[a]
    for (t in closedTermUnits[a]) cnfish.add(lhs to listOf(t))
  }
  // proper rules -> binary
  for (a in 0 until n) {
    val lhs = declared[a]
    for (rhs0 in closedProper[a]) {
      var rhs = rhs0
      if (rhs.size < 2) continue
      // after lifting, rhs symbols should be NTs; if not, lift again defensively
      if (rhs.any { it !in nts4 } && rhs.size >= 2) {
        val tmp = ArrayList<Σᐩ>(rhs.size)
        for (s in rhs) tmp.add(if (s in nts4) s else ensurePreterminal(s))
        rhs = tmp
      }

      var work = rhs
      while (work.size > 2) {
        val x = work[work.size - 2]
        val y = work[work.size - 1]
        val bin = ensureBin(x, y)
        work = work.dropLast(2) + bin
      }
      cnfish.add(lhs to work)
    }
  }
  cnfish.addAll(binaryRules)

  // ---------- 6) prune to productive + reachable ----------
  val allNts = LinkedHashSet<Σᐩ>().apply { for ((l, _) in cnfish) add(l) }
  val byLhs = HashMap<Σᐩ, MutableList<List<Σᐩ>>>(allNts.size * 2).apply {
    for ((l, r) in cnfish) getOrPut(l) { ArrayList() }.add(r)
  }

  // productive
  val productive = HashSet<Σᐩ>(allNts.size * 2)
  run {
    // seed: any A -> terminal
    for ((lhs, rhss) in byLhs) {
      for (r in rhss) if (r.size == 1 && r[0] !in allNts) productive.add(lhs)
    }
    var changed: Boolean
    do {
      changed = false
      for ((lhs, rhss) in byLhs) {
        if (lhs in productive) continue
        for (r in rhss) {
          if (r.size == 2 && r[0] in productive && r[1] in productive) {
            productive.add(lhs); changed = true; break
          }
          // allow unary terminal already handled
        }
      }
    } while (changed)
  }

  // reachable
  val reachable = HashSet<Σᐩ>(allNts.size * 2)
  run {
    val q = ArrayDeque<Σᐩ>()
    if (START_SYMBOL in byLhs) { reachable.add(START_SYMBOL); q.add(START_SYMBOL) }
    while (q.isNotEmpty()) {
      val a = q.removeFirst()
      for (r in byLhs[a].orEmpty()) {
        if (r.size == 2) {
          val x = r[0]; val y = r[1]
          if (x in byLhs && x !in reachable) { reachable.add(x); q.add(x) }
          if (y in byLhs && y !in reachable) { reachable.add(y); q.add(y) }
        }
      }
    }
  }

  // filter
  val pruned = LinkedHashSet<Production>(cnfish.size)
  for ((lhs, rhs) in cnfish) {
    if (lhs !in productive || lhs !in reachable) continue
    if (rhs.size == 2 && (rhs[0] !in productive || rhs[1] !in productive)) continue
    pruned.add(lhs to rhs)
  }

  return pruned
}