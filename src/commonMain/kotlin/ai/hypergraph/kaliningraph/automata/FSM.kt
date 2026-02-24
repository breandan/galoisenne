package ai.hypergraph.kaliningraph.automata

import ai.hypergraph.kaliningraph.KBitSet
import ai.hypergraph.kaliningraph.sampling.longLFSRSequence
import ai.hypergraph.kaliningraph.types.filter
import kotlin.time.TimeSource

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

  fun recognizes(word: List<String>, tmLst: List<String>): Boolean {
    // Build encoder: terminal string -> symbol id (0..sigma-1)
    val symToId = HashMap<String, Int>(tmLst.size * 2)
    for (i in tmLst.indices) symToId[tmLst[i]] = i
    return recognizes(word, symToId)
  }

  fun recognizes(word: List<String>, symToId: Map<String, Int>): Boolean {
    var q = q_alpha
    for (tok in word) {
      val a = symToId[tok] ?: return false
      if (a !in 0 until width) return false
      q = deltaMap[q]?.get(a) ?: return false
    }
    return q in F
  }

  fun summarize() = "(states=${Q.size}, transitions=${deltaMap.values.sumOf { it.values.size }})"
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

fun GRE.toDFSM(terms: List<String>): DFSM {
  var timer = TimeSource.Monotonic.markNow()
  val nfsm = toNFSM()
  println("NFSM construction took: ${timer.elapsedNow()}")
  timer = TimeSource.Monotonic.markNow()
  val dfsm = nfsm.toDFSM(terms.size)
  println("DFSM construction took: ${timer.elapsedNow()}")
  return dfsm
}

fun GRE.toNFSM(): NFSM {
  // 1. Flatten the GRE tree to identify "leaf" (SET) positions
  // We strictly enforce order so index 'i' always refers to the same leaf
  val leaves = mutableListOf<GRE.SET>()
  fun collectLeaves(g: GRE) {
    when (g) {
      is GRE.SET -> leaves.add(g)
      is GRE.CUP -> g.args.forEach { collectLeaves(it) }
      is GRE.CAT -> { collectLeaves(g.l); collectLeaves(g.r) }
      is GRE.EPS -> {}
    }
  }
  collectLeaves(this)

  val n = leaves.size
  val follow = Array(n) { mutableSetOf<Int>() }

  // 2. Compute Nullable, First, and Last sets for every node
  data class Info(
    val nullable: Boolean,
    val first: Set<Int>, // Indices of leaves that can start this sub-expression
    val last: Set<Int>   // Indices of leaves that can end this sub-expression
  )

  var leafCounter = 0
  fun analyze(g: GRE): Info = when (g) {
    is GRE.EPS -> Info(true, emptySet(), emptySet())
    is GRE.SET -> {
      val id = leafCounter++
      val s = setOf(id)
      Info(false, s, s)
    }
    is GRE.CUP -> {
      // Union: Union of firsts, union of lasts, nullable if any is nullable
      val infos = g.args.map { analyze(it) }
      Info(
        nullable = infos.any { it.nullable },
        first = infos.flatMap { it.first }.toSet(),
        last = infos.flatMap { it.last }.toSet()
      )
    }
    is GRE.CAT -> {
      // Concatenation: Connect left.last to right.first
      val l = analyze(g.l)
      val r = analyze(g.r)

      for (i in l.last) {
        follow[i].addAll(r.first)
      }

      Info(
        nullable = l.nullable && r.nullable,
        first = l.first + if (l.nullable) r.first else emptySet(),
        last = r.last + if (r.nullable) l.last else emptySet()
      )
    }
  }

  val rootInfo = analyze(this)

  // 3. Build the NFSM directly
  val qStart = "q0"
  fun qName(i: Int) = "q${i + 1}" // State name for leaf i

  val Q = mutableSetOf(qStart)
  for (i in 0 until n) Q.add(qName(i))

  val F = mutableSetOf<String>()
  if (rootInfo.nullable) F.add(qStart)
  for (i in rootInfo.last) F.add(qName(i))

  val delta = mutableSetOf<Triple<String, Int, String>>()

  // Transitions from Start State -> First positions
  for (i in rootInfo.first) {
    val leaf = leaves[i]
    // leaf.s is KBitSet; convert to list to iterate symbols
    for (sym in leaf.s.toList()) {
      delta.add(Triple(qStart, sym, qName(i)))
    }
  }

  // Transitions between positions (Follow sets)
  for (i in 0 until n) {
    val source = qName(i)
    for (j in follow[i]) {
      val targetLeaf = leaves[j]
      val target = qName(j)
      for (sym in targetLeaf.s.toList()) {
        delta.add(Triple(source, sym, target))
      }
    }
  }

  return NFSM(Q, delta, qStart, F)
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
      for (a in row.keys.sorted()) {
        val nxt = row[a]!!
        val cnt = memo[nxt] ?: countFrom(nxt)
        if (r < cnt) { out += a; q = nxt } else { r -= cnt }
      }
    }
  }

  for (r in longLFSRSequence(total)) yield(decode(r))
}

fun GRE.toDFSMDirect(tmLst: List<String>): DFSM {
  val timer = TimeSource.Monotonic.markNow()
  val sigma = tmLst.size                // real alphabet size
  val END = sigma                       // internal endmarker symbol index

  // Build GRE' = (this) · #
  val endSet = GRE.SET(KBitSet(sigma + 1).apply { set(END) })
  val root = GRE.CAT(this, endSet)

  // Pass 1: give every SET occurrence a unique position id
  val posId = mutableMapOf<GRE, Int>()
  val posSyms = mutableListOf<KBitSet>()   // per-position symbol set
  fun index(g: GRE) {
    when (g) {
      is GRE.SET -> {
        if (g !in posId) {
          posId.put(g, posSyms.size)
          posSyms += g.s // (KBitSet) symbol-set for this position
        }
      }
      is GRE.CUP -> g.args.forEach(::index)
      is GRE.CAT -> { index(g.l); index(g.r) }
      is GRE.EPS -> {}
    }
  }
  index(root)

  val P = posSyms.size
  val endPos = posId[endSet] ?: error("Internal endmarker not indexed")

  // follow[pos] is a set of positions
  val follow = Array(P) { KBitSet(P) }

  // Small helpers over position-sets
  fun emptyPosSet() = KBitSet(P)
  fun union(a: KBitSet, b: KBitSet): KBitSet = KBitSet(P).apply { or(a); or(b) }

  data class Info(val first: KBitSet, val last: KBitSet, val nullable: Boolean)

  // Pass 2: compute first/last/nullable and fill follow by recursion
  fun info(g: GRE): Info = when (g) {
    is GRE.EPS -> Info(emptyPosSet(), emptyPosSet(), true)

    is GRE.SET -> {
      val id = posId[g]!!
      val one = emptyPosSet().apply { set(id) }
      Info(one, one, false)
    }

    is GRE.CUP -> {
      // fold union across children
      var first = emptyPosSet()
      var last  = emptyPosSet()
      var nullb = false
      for (c in g.args) {
        val I = info(c)
        first.or(I.first)
        last.or(I.last)
        nullb = nullb || I.nullable
      }
      Info(first, last, nullb)
    }

    is GRE.CAT -> {
      val L = info(g.l)
      val R = info(g.r)
      // For every i in last(L) and j in first(R), add i -> j to follow
      for (i in L.last.iterator()) follow[i].or(R.first)

      val first = union(L.first, if (L.nullable) R.first else emptyPosSet())
      val last  = union(R.last, if (R.nullable) L.last else emptyPosSet())
      Info(first, last, L.nullable && R.nullable)
    }
  }

  val rootInfo = info(root)

  // Precompute: positions that carry each real symbol a ∈ [0, sigma)
  val posBySym: Array<IntArray> = Array(sigma) { a ->
    val acc = ArrayList<Int>()
    for (p in 0 until P) if (p != endPos) {
      // copy real-alphabet part only
      // (endPos has END bit set; others may have multiple real bits)
      if (posSyms[p][a]) acc += p
    }
    acc.toIntArray()
  }

  // BFS subset construction over position-sets (as KBitSet)
  val start = rootInfo.first // contains endPos iff GRE was nullable
  data class IntKey(val a: IntArray) { override fun hashCode() = a.contentHashCode()
    override fun equals(o: Any?) = o is IntKey && a.contentEquals(o.a) }
  fun keyOf(bits: KBitSet): IntKey {
    val xs = ArrayList<Int>()
    for (i in bits.iterator()) xs += i
    return IntKey(xs.toIntArray())
  }

  val subset2name = LinkedHashMap<IntKey, String>()
  val queue = ArrayDeque<KBitSet>()
  val deltaMap = mutableMapOf<String, MutableMap<Int, String>>()
  val finals = mutableSetOf<String>()

  run {
    val k = keyOf(start)
    subset2name[k] = "q0"
    queue.add(start)
  }

  while (queue.isNotEmpty()) {
    val S = queue.removeFirst()
    val sName = subset2name[keyOf(S)]!!
    if (S[endPos]) finals.add(sName)

    val row = deltaMap.getOrPut(sName) { mutableMapOf() }

    // For each real symbol a, T = ⋃_{p∈S∩posBySym[a]} follow[p]
    for (a in 0 until sigma) {
      var any = false
      val T = KBitSet(P)
      val candidates = posBySym[a]
      var i = 0
      while (i < candidates.size) {
        val p = candidates[i]
        if (S[p]) { T.or(follow[p]); any = true }
        i++
      }
      if (!any) continue // no outgoing edge on a

      val k = keyOf(T)
      val tName = subset2name.getOrPut(k) {
        val n = "q${subset2name.size}"
        queue.add(T)
        n
      }
      row[a] = tName
    }
  }

  val Qd = subset2name.values.toSet()
  return DFSM(Qd, deltaMap, "q0", finals, sigma)
    .also { println("Direct DFSM construction took: ${timer.elapsedNow()}") }
}

fun DFSM.minimize(): DFSM {
//  println("Size before minimization: ${Q.size}")
  val timer = TimeSource.Monotonic.markNow()
  // 1. Prune unreachable states using BFS
  val reachable = mutableSetOf<String>()
  val queue = ArrayDeque<String>()
  if (q_alpha in Q) {
    queue.add(q_alpha)
    reachable.add(q_alpha)
  }

  while (queue.isNotEmpty()) {
    val u = queue.removeFirst()
    // deltaMap might be partial; ensure we only follow transitions to existing states
    deltaMap[u]?.forEach { (_, v) ->
      if (v !in reachable && v in Q) {
        reachable.add(v)
        queue.add(v)
      }
    }
  }

  // Filter Q, F, and Delta to only reachable states
  val pQ = Q.intersect(reachable)
  val pF = F.intersect(reachable)
  val pDelta = deltaMap.filterKeys { it in pQ }
    .mapValues { (_, trans) -> trans.filterValues { it in pQ } }

  // 2. Initial Partition: Separate Final states from Non-Final states
  // We use a List<Set<String>> to represent the partition blocks.
  val initialPartitions = pQ.groupBy { it in pF }.values.map { it.toSet() }
  var partitions = initialPartitions

  // 3. Refine Partitions (Moore's Algorithm)
  var changed = true
  while (changed) {
    changed = false
    val newPartitions = mutableListOf<Set<String>>()

    // Map every state to its current block index for O(1) lookups
    val stateToBlockIndex = mutableMapOf<String, Int>()
    partitions.forEachIndexed { idx, block ->
      block.forEach { stateToBlockIndex[it] = idx }
    }

    for (block in partitions) {
      // If a block has only 1 state, it cannot be split further
      if (block.size <= 1) {
        newPartitions.add(block)
        continue
      }

      // Group states by their "transition signature"
      // Signature key: Map<Symbol, TargetBlockIndex>
      // Two states are equivalent iff for every symbol, they transition to the same block index.
      // (Partial transitions are handled naturally: missing keys in the map are part of the signature)
      val subGroups = block.groupBy { state ->
        val transitions = pDelta[state] ?: emptyMap()
        transitions.mapValues { (_, target) -> stateToBlockIndex[target]!! }
      }

      // If we found more than one group within this block, a split occurred
      if (subGroups.size > 1) changed = true
      newPartitions.addAll(subGroups.values.map { it.toSet() })
    }
    partitions = newPartitions
  }

  // 4. Construct the minimized DFSM
  // Assign new names "q0", "q1"... to the partition blocks
  val blockIndexToName = partitions.indices.associateWith { "q$it" }
  val stateToNewName = mutableMapOf<String, String>()
  partitions.forEachIndexed { idx, block ->
    block.forEach { stateToNewName[it] = blockIndexToName[idx]!! }
  }

  val newStart = stateToNewName[q_alpha] ?: "q0" // Fallback if Q was empty
  val newQ = blockIndexToName.values.toSet()

  // A block is final if it contains any final states (all should be final due to initial split)
  val newF = partitions.withIndex()
    .filter { (_, block) -> block.any { it in pF } }
    .map { blockIndexToName[it.index]!! }
    .toSet()

  val newDelta = mutableMapOf<String, Map<Int, String>>()

  partitions.forEachIndexed { idx, block ->
    val representative = block.first()
    val sourceName = blockIndexToName[idx]!!
    val originalTrans = pDelta[representative] ?: emptyMap()

    val newTrans = originalTrans.mapValues { (_, target) ->
      stateToNewName[target]!!
    }

    if (newTrans.isNotEmpty()) {
      newDelta[sourceName] = newTrans
    }
  }

//  println("DFSM minimization took: ${timer.elapsedNow()}")
  return DFSM(newQ, newDelta, newStart, newF, width)
//    .also { println("Size after minimization ${it.Q.size}") }
}