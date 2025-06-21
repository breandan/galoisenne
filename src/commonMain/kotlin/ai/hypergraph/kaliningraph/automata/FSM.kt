package ai.hypergraph.kaliningraph.automata

import ai.hypergraph.kaliningraph.automata.GRE.CAT
import ai.hypergraph.kaliningraph.automata.GRE.CUP
import ai.hypergraph.kaliningraph.automata.GRE.EPS
import ai.hypergraph.kaliningraph.automata.GRE.SET
import ai.hypergraph.kaliningraph.types.filter

// Alternate to FSA; bypasses graph subtyping, basically just record types
class DFSM(
  Q: Set<String>,
  deltaMap: Map<String, Map<Int, String>>,
  q_alpha: String,
  F: Set<String>,
  val width: Int
) : NFSM(
  Q,
  deltaMap.flatMap { (from, transitions) ->
    transitions.map { (symbol, to) -> Triple(from, symbol, to) }
  }.toSet(),
  q_alpha,
  F
)

open class NFSM(
  val Q: Set<String>,                  // Set of state names
  val delta: Set<Triple<String, Int, String>>, // Transitions: (from, symbol_index, to)
  val q_alpha: String,                 // Initial state
  val F: Set<String>                   // Final states
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

fun GRE.toNFA(): NFSM {
  var stateCounter = 0
  fun newState(): String = "q${stateCounter++}"

  fun buildNFA(g: GRE): NFSM = when (g) {
    is EPS -> {
      // NFA accepting only the empty string
      val q0 = newState()
      NFSM(
        Q = setOf(q0),
        delta = emptySet(),
        q_alpha = q0,
        F = setOf(q0)
      )
    }
    is SET -> {
      // NFA accepting single symbols whose indices are in s
      val q_alpha = newState()
      val q_omega = newState()
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
      val subNFAs = g.args.map { buildNFA(it) }
      val q_new = newState()
      val Q = subNFAs.flatMap { it.Q }.toSet() + q_new
      val delta = subNFAs.flatMap { it.delta }.toSet()
      val F = subNFAs.flatMap { it.F }.toSet()
      val newTransitions = mutableSetOf<Triple<String, Int, String>>()
      val allSigma = subNFAs.flatMap { it.delta.map { t -> t.second } }.toSet()
      for (σ in allSigma) {
        val targets = subNFAs.flatMap { nfa ->
          nfa.delta.filter { it.first == nfa.q_alpha && it.second == σ }.map { it.third }
        }
        for (target in targets) {
          newTransitions.add(Triple(q_new, σ, target))
        }
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
      val nfaL = buildNFA(g.l)
      val nfaR = buildNFA(g.r)
      val q_new = newState()
      val Q = nfaL.Q + nfaR.Q + q_new
      val delta = nfaL.delta + nfaR.delta
      val newTransitions = mutableSetOf<Triple<String, Int, String>>()
      val allSigma = (nfaL.delta + nfaR.delta).map { it.second }.toSet()
      // Initial transitions from q_new
      for (σ in allSigma) {
        val S = mutableSetOf<String>()
        S.addAll(nfaL.delta.filter { it.first == nfaL.q_alpha && it.second == σ }.map { it.third })
        if (g.l.nullable) {
          S.addAll(nfaR.delta.filter { it.first == nfaR.q_alpha && it.second == σ }.map { it.third })
        }
        for (target in S) {
          newTransitions.add(Triple(q_new, σ, target))
        }
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
  return buildNFA(this)
}

fun GRE.toDFSM(): DFSM {
  val width = this.width
  val alphabet = 0 until width

  // Handle empty language
  if (enumerate().none()) {
    val sink = "sink"
    return DFSM(
      Q = setOf(sink),
      deltaMap = mapOf(sink to alphabet.associateWith { sink }),
      q_alpha = sink,
      F = emptySet(),
      width = width
    )
  }

  val languageToState = mutableMapOf<String, String>()
  val queue = ArrayDeque<GRE>()
  val deltaMap = mutableMapOf<String, MutableMap<Int, String>>()
  val F = mutableSetOf<String>()
  val sink = "sink"

  // Initial state
  val q0 = "q0"
  languageToState[hash()] = q0
  queue.add(this)

  while (queue.isNotEmpty()) {
    val currentGRE = queue.removeFirst()
    val currentLang = currentGRE.hash()
    val currentState = languageToState[currentLang]!!

    // Set accepting state
    if (currentGRE.nullable) F.add(currentState)

    // Compute transitions
    for (σ in alphabet) {
      val derivative = currentGRE.dv(σ)
      val targetState = if (derivative != null) {
        val derivLang = derivative.hash()
        languageToState.getOrPut(derivLang) {
          val newState = "q${languageToState.size}"
          queue.add(derivative)
          newState
        }
      } else {
        sink
      }
      deltaMap.getOrPut(currentState) { mutableMapOf() }[σ] = targetState
    }
  }

  // Configure sink state
  deltaMap[sink] = alphabet.associateWith { sink }.toMutableMap()
  val Q = languageToState.values.toSet() + sink

  return DFSM(Q, deltaMap, q0, F, width)
}