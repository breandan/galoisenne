package ai.hypergraph.kaliningraph.automata

import ai.hypergraph.kaliningraph.automata.GRE.CAT
import ai.hypergraph.kaliningraph.automata.GRE.CUP
import ai.hypergraph.kaliningraph.automata.GRE.EPS
import ai.hypergraph.kaliningraph.automata.GRE.SET
import ai.hypergraph.kaliningraph.sampling.LFSR
import ai.hypergraph.kaliningraph.sampling.bigLFSRSequence
import ai.hypergraph.kaliningraph.sampling.longLFSRSequence
import ai.hypergraph.kaliningraph.types.filter
import com.ionspin.kotlin.bignum.integer.BigInteger

// Alternate to FSA; bypasses graph subtyping, basically just record types
class DFSM(
  override val Q: Set<String>,
  val deltaMap: Map<String, Map<Int, String>>,
  override val q_alpha: String,
  override val F: Set<String>,
  val width: Int
) : NFSM(
  Q,
  deltaMap.flatMap { (from, transitions) ->
    transitions.map { (symbol, to) -> Triple(from, symbol, to) }
  }.toSet(),
  q_alpha,
  F
) {
  fun countWords(): Long {
    val memo = mutableMapOf<String, Long>()

    fun countFrom(q: String): Long {
      if (memo.containsKey(q)) return memo[q]!!
      val transitions = deltaMap[q] ?: emptyMap()
      var sum = 0L
      for ((_, next) in transitions) {
        sum += countFrom(next)
      }
      val result = if (q in F) 1L + sum else sum
      memo[q] = result
      return result
    }

    return countFrom(q_alpha)
  }
}

open class NFSM(
  open val Q: Set<String>,                  // Set of state names
  val delta: Set<Triple<String, Int, String>>, // Transitions: (from, symbol_index, to)
  open val q_alpha: String,                 // Initial state
  open val F: Set<String>                   // Final states
) {
  open fun toDOT(terminals: List<String>? = null): String {
    fun toSubscriptString(i: Int): String {
      val subscriptMap = mapOf(
        '0' to '\u2080',
        '1' to '\u2081',
        '2' to '\u2082',
        '3' to '\u2083',
        '4' to '\u2084',
        '5' to '\u2085',
        '6' to '\u2086',
        '7' to '\u2087',
        '8' to '\u2088',
        '9' to '\u2089'
      )
      return i.toString().map { subscriptMap[it] ?: it }.joinToString("")
    }

    val symbolsByPair = delta.groupBy { it.first to it.third }
      .mapValues { entry -> entry.value.map { it.second }.toSet() }

    val sb = StringBuilder()
    sb.append("digraph NFA {\n")
    sb.append("  rankdir=LR;\n")
    sb.append("  node [shape=circle];\n")
    for (finalState in F) {
      sb.append("  $finalState [shape=doublecircle];\n")
    }
    sb.append("  start [label=\"\", shape=none];\n")
    sb.append("  start -> $q_alpha;\n")
    for ((pair, symbols) in symbolsByPair) {
      val (from, to) = pair
      val label = symbols.sorted().joinToString(", ") { sym ->
        if (terminals != null && sym < terminals.size) terminals[sym] else "σ" + toSubscriptString(sym)
      }
      sb.append("  $from -> $to [label=\"$label\"];\n")
    }
    sb.append("}\n")
    return sb.toString()
  }

  fun pruneDeadStates(): NFSM {
    // Build forward and backward adjacency lists for reachability
    val forwardAdj = mutableMapOf<String, MutableSet<String>>()
    val backwardAdj = mutableMapOf<String, MutableSet<String>>()
    for (transition in delta) {
      val (from, _, to) = transition
      forwardAdj.getOrPut(from) { mutableSetOf() }.add(to)
      backwardAdj.getOrPut(to) { mutableSetOf() }.add(from)
    }

    // BFS to find all reachable states from given starting states
    fun bfs(adj: Map<String, Set<String>>, start: Set<String>): Set<String> {
      val visited = mutableSetOf<String>()
      val queue = ArrayDeque<String>()
      queue.addAll(start)
      visited.addAll(start)
      while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        val neighbors = adj[current] ?: emptySet()
        for (neighbor in neighbors) {
          if (neighbor !in visited) {
            visited.add(neighbor)
            queue.add(neighbor)
          }
        }
      }
      return visited
    }

    // Compute states reachable from start and states that can reach a final state
    val reachableFromStart = bfs(forwardAdj, setOf(q_alpha))
    val canReachFinal = bfs(backwardAdj, F)
    val liveStates = reachableFromStart.intersect(canReachFinal)

    // If no live states exist, return an NFA that accepts nothing
    return if (liveStates.isEmpty()) {
      val q0 = "q0"
      NFSM(
        Q = setOf(q0),
        delta = emptySet(),
        q_alpha = q0,
        F = emptySet()
      )
    } else {
      // Filter transitions to include only those between live states
      val newDelta = delta.filter { (from, _, to) -> from in liveStates && to in liveStates }.toSet()
      NFSM(
        Q = liveStates,
        delta = newDelta,
        q_alpha = q_alpha,
        F = F.intersect(liveStates)
      )
    }
  }

  fun getAllSymbols(): Set<Int> = delta.map { it.second }.toSet()

  fun simplify(): NFSM {
    // Initial partition: separate final and non-final states
    val finalStates = F.toMutableSet()
    val nonFinalStates = (Q - F).toMutableSet()
    var partition = mutableListOf<MutableSet<String>>()
    if (finalStates.isNotEmpty()) partition.add(finalStates)
    if (nonFinalStates.isNotEmpty()) partition.add(nonFinalStates)

    // Map from state to its current block index
    val stateToBlock = mutableMapOf<String, Int>()
    for (i in partition.indices) {
      for (state in partition[i]) {
        stateToBlock[state] = i
      }
    }

    // Refine partition until no changes occur
    var changed = true
    while (changed) {
      changed = false
      val newPartition = mutableListOf<MutableSet<String>>()
      for (block in partition) {
        val signatureMap = mutableMapOf<String, MutableSet<String>>()
        for (state in block) {
          // Compute signature: map of symbol to reachable block indices
          val signature = getAllSymbols().sorted().joinToString(";") { a ->
            val reachableBlocks = delta.filter { it.first == state && it.second == a }
              .map { stateToBlock[it.third]!! }
              .toSet()
              .sorted()
              .joinToString(",")
            "a$a:$reachableBlocks"
          }
          signatureMap.getOrPut(signature) { mutableSetOf() }.add(state)
        }
        if (signatureMap.size > 1) {
          // Split block if states have different signatures
          changed = true
          for (group in signatureMap.values) {
            newPartition.add(group.toMutableSet())
          }
        } else {
          newPartition.add(block)
        }
      }
      if (changed) {
        partition = newPartition
        // Update state-to-block mapping
        stateToBlock.clear()
        for (i in partition.indices) {
          for (state in partition[i]) {
            stateToBlock[state] = i
          }
        }
      }
    }

    // Assign unique IDs to each block (e.g., "b0", "b1", ...)
    val blockIds = partition.indices.map { "b$it" }
    val blockToId = partition.indices.associateWith { blockIds[it] }
    val stateToBlockId = stateToBlock.mapValues { blockToId[it.value]!! }

    // Construct new delta with block IDs
    val newDelta = mutableSetOf<Triple<String, Int, String>>()
    for (trans in delta) {
      val fromBlock = stateToBlockId[trans.first]!!
      val toBlock = stateToBlockId[trans.third]!!
      val symbol = trans.second
      newDelta.add(Triple(fromBlock, symbol, toBlock))
    }

    // Define new NFA components
    val newQ = blockIds.toSet()
    val newQAlpha = stateToBlockId[q_alpha]!!
    val newF = F.mapNotNull { stateToBlockId[it] }.toSet()

    return NFSM(newQ, newDelta, newQAlpha, newF)
  }
}

fun NFSM.toDFSM(width: Int): DFSM {
  // Pre-index NFA transitions: from -> (symbol -> {to,...})
  val tmap: Map<String, Map<Int, Set<String>>> = run {
    val tmp = mutableMapOf<String, MutableMap<Int, MutableSet<String>>>()
    for ((from, a, to) in delta) {
      val row = tmp.getOrPut(from) { mutableMapOf() }
      row.getOrPut(a) { mutableSetOf() }.add(to)
    }
    tmp.mapValues { (_, row) -> row.mapValues { it.value.toSet() } }
  }

  fun succ(states: Set<String>, a: Int): Set<String> {
    if (states.isEmpty()) return emptySet()
    val out = mutableSetOf<String>()
    for (s in states) {
      val row = tmap[s] ?: continue
      val tgt = row[a] ?: continue
      out.addAll(tgt)
    }
    return out
  }

  // Canonical name for a subset of NFA states
  fun nameOf(S: Set<String>) = S.sorted().joinToString("|").ifEmpty { "∅" }

  val alphabet = 0 until width
  val q0set = setOf(q_alpha)

  val subset2name = LinkedHashMap<Set<String>, String>()
  val queue = ArrayDeque<Set<String>>()
  val deltaMap = mutableMapOf<String, MutableMap<Int, String>>()
  val finals = mutableSetOf<String>()

  subset2name[q0set] = "q0"
  queue.add(q0set)

  while (queue.isNotEmpty()) {
    val S = queue.removeFirst()
    val sName = subset2name[S]!!
    if (S.any { it in F }) finals.add(sName)

    val row = deltaMap.getOrPut(sName) { mutableMapOf() }
    for (a in alphabet) {
      val T = succ(S, a)
      if (T.isEmpty()) continue // no sink
      val tName = subset2name.getOrPut(T) {
        val n = "q${subset2name.size}"
        queue.add(T)
        n
      }
      row[a] = tName
    }
  }

  val Qd = subset2name.values.toSet()
  return DFSM(Qd, deltaMap, "q0", finals, width)
}

fun GRE.toDFSM(terms: List<String>): DFSM = toNFSM().toDFSM(terms.size)

fun GRE.toNFSM(): NFSM {
  var stateCounter = 0
  fun freshState(): String = "q${stateCounter++}"

  fun buildNFSM(g: GRE): NFSM = when (g) {
    is EPS -> {
      // NFA accepting only the empty string
      val q0 = freshState()
      NFSM(
        Q = setOf(q0),
        delta = emptySet(),
        q_alpha = q0,
        F = setOf(q0)
      )
    }
    is SET -> {
      // NFA accepting single symbols whose indices are in s
      val q_alpha = freshState()
      val q_omega = freshState()
      val transitions = g.s.toList().map { Triple(q_alpha, it, q_omega) }.toSet()
      NFSM(
        Q = setOf(q_alpha, q_omega),
        delta = transitions,
        q_alpha = q_alpha,
        F = setOf(q_omega)
      )
    }
    is CUP -> {
      // Union of sub-expressions
      val subNFAs = g.args.map { buildNFSM(it) }
      val q_new = freshState()
      val Q = subNFAs.flatMap { it.Q }.toSet() + q_new
      val delta = subNFAs.flatMap { it.delta }.toSet()
      val F = subNFAs.flatMap { it.F }.toSet()
      val newTransitions = mutableSetOf<Triple<String, Int, String>>()
      val allSigma = subNFAs.flatMap { it.delta.map { t -> t.second } }.toSet()
      for (σ in allSigma) {
        val targets = subNFAs.flatMap { nfa ->
          nfa.delta.filter { it.first == nfa.q_alpha && it.second == σ }.map { it.third }
        }
        for (target in targets) newTransitions.add(Triple(q_new, σ, target))
      }
      NFSM(
        Q = Q,
        delta = delta + newTransitions,
        q_alpha = q_new,
        F = F
      )
    }
    is CAT -> {
      // Concatenation of l and r
      val nfaL = buildNFSM(g.l)
      val nfaR = buildNFSM(g.r)
      val q_new = freshState()
      val Q = nfaL.Q + nfaR.Q + q_new
      val delta = nfaL.delta + nfaR.delta
      val newTransitions = mutableSetOf<Triple<String, Int, String>>()
      val allSigma = (nfaL.delta + nfaR.delta).map { it.second }.toSet()
      // Initial transitions from q_new
      for (σ in allSigma) {
        val S = mutableSetOf<String>()
        S.addAll(nfaL.delta.filter { it.first == nfaL.q_alpha && it.second == σ }.map { it.third })
        if (g.l.nullable) S.addAll(nfaR.delta.filter { it.first == nfaR.q_alpha && it.second == σ }.map { it.third })
        for (target in S) newTransitions.add(Triple(q_new, σ, target))
      }
      // Transitions from final states of l to states reachable from r's initial state
      for (f in nfaL.F)
        for (trans in nfaR.delta.filter { it.first == nfaR.q_alpha })
          newTransitions.add(Triple(f, trans.second, trans.third))
      // Final states
      val F = if (g.r.nullable) nfaL.F + nfaR.F else nfaR.F
      val finalF = if (g.l.nullable && g.r.nullable) F + q_new else F
      NFSM(
        Q = Q,
        delta = delta + newTransitions,
        q_alpha = q_new,
        F = finalF
      )
    }
  }
  return buildNFSM(this)
}

fun DFSM.printAdjMatrixPowers() {
  // Build adjacency list from deltaMap
  val adj = Q.associateWith { q -> deltaMap[q]?.values?.toSet() ?: emptySet() }

  // Compute in-degrees for topological sort
  val inDegree: MutableMap<String, Int> = mutableMapOf<String, Int>()
  for (q in Q) {
    inDegree[q] = 0
  }
  for (q in Q) {
    for (r in adj[q]!!) {
      inDegree[r] = inDegree.getOrElse(r) { 0 } + 1
    }
  }

  // Perform topological sort using Kahn's algorithm
  val order = mutableListOf<String>()
  val queue = ArrayDeque<String>()
  for (q in Q) {
    if (inDegree[q] == 0) {
      queue.add(q)
    }
  }
  while (queue.isNotEmpty()) {
    val q = queue.removeFirst()
    order.add(q)
    for (r in adj[q]!!) {
      inDegree[r] = inDegree[r]!! - 1
      if (inDegree[r] == 0) {
        queue.add(r)
      }
    }
  }

  // Map states to indices based on topological order
  val stateToIndex = order.mapIndexed { index, state -> state to index }.toMap()
  val n = Q.size

  // Construct adjacency matrix
  val AA = List(n) { MutableList(n) { 0 } }
  for (q in Q) {
    val i = stateToIndex[q]!!
    for (r in adj[q]!!) {
      val j = stateToIndex[r]!!
      AA[i][j] = 1
    }
  }

  val A: List<List<Int>> = AA

  // Helper function to multiply two matrices
  fun multiply(M1: List<List<Int>>, M2: List<List<Int>>): List<List<Int>> {
    val C = List(n) { MutableList(n) { 0 } }
    for (i in 0 until n) {
      for (j in 0 until n) {
        for (k in 0 until n) {
          C[i][j] += M1[i][k] * M2[k][j]
        }
      }
    }
    return C
  }

  // Helper function to check if a matrix is all zeros
  fun isZeroMatrix(M: List<List<Int>>): Boolean {
    return M.all { row -> row.all { it == 0 } }
  }

  // Helper function to convert matrix to LaTeX bmatrix
  fun matrixToLatex(M: List<List<Int>>): String {
    val sb = StringBuilder()
    sb.append("\\begin{bmatrix}\n")
    for (row in M) {
      sb.append(row.joinToString(" & "))
      sb.append(" \\\\\n")
    }
    sb.append("\\end{bmatrix}")
    return sb.toString()
  }

  // Print the state ordering for reference
  println("States ordered as: " + order.joinToString(", "))

  // Compute and print matrix powers until zero
  var current = A
  var k = 1
  while (!isZeroMatrix(current)) {
    println("A^{$k} = " + matrixToLatex(current))
    current = multiply(current, A)
    k++
  }
}

fun DFSM.sampleUniformly(tmLst: List<String>): Sequence<String> = sequence {
  // Precompute (and memoize) the number of accepted words from each state.
  val memo = HashMap<String, Long>()
  fun countFrom(q: String): Long {
    memo[q]?.let { return it }
    val row = deltaMap[q].orEmpty()
    var sum = 0L
    for ((_, next) in row) sum += countFrom(next)
    val res = if (q in F) 1L + sum else sum // +1 for epsilon at finals
    memo[q] = res
    return res
  }

  val total = countFrom(q_alpha)
  require(total > 0L) { "Language is empty; no words to sample." }

  // Decode a rank r ∈ [0, total) into a word (as symbol indices joined by spaces).
  fun decode(r0: Long): String {
    var r = r0
    var q = q_alpha
    val out = mutableListOf<Int>()

    while (true) {
      // If current state is final, epsilon contributes the first block of mass.
      if (q in F) {
        if (r == 0L) return out.joinToString(" ") { tmLst[it] }
        r -= 1L
      }

      // Walk one symbol along the unique branch containing r.
      val row = deltaMap[q].orEmpty()
      var stepped = false
      for (a in row.keys.sorted()) {
        val nxt = row[a]!!
        val cnt = memo[nxt] ?: countFrom(nxt)
        if (r < cnt) {
          out += a
          q = nxt
          stepped = true
          break
        }
        r -= cnt
      }
      check(stepped) { "Rank out of range while decoding (graph must be acyclic and counts finite)." }
    }
  }

  for (r in longLFSRSequence(total)) yield(decode(r))
}