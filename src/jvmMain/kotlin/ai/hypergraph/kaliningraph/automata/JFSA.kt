package ai.hypergraph.kaliningraph.automata

import ai.hypergraph.kaliningraph.automata.GRE.CAT
import ai.hypergraph.kaliningraph.automata.GRE.CUP
import ai.hypergraph.kaliningraph.automata.GRE.EPS
import ai.hypergraph.kaliningraph.automata.GRE.SET
import ai.hypergraph.kaliningraph.graphs.LabeledGraph
import ai.hypergraph.kaliningraph.parsing.*
import ai.hypergraph.kaliningraph.parsing.approximations.WFA
import ai.hypergraph.kaliningraph.repair.MAX_BM_WID
import ai.hypergraph.kaliningraph.sampling.FastMC
import ai.hypergraph.markovian.mcmc.MarkovChain
import dk.brics.automaton.Automaton.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.util.Collections
import java.util.LinkedHashMap
import java.util.PriorityQueue
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.stream.Stream
import kotlin.math.ln
import kotlin.random.Random
import kotlin.text.contains
import kotlin.text.get
import kotlin.text.isEmpty
import kotlin.text.iterator
import kotlin.text.orEmpty
import kotlin.time.*

typealias BState = dk.brics.automaton.State
typealias BAutomaton = dk.brics.automaton.Automaton
typealias JAutomaton<S, K> = net.jhoogland.jautomata.Automaton<S, K>

fun BState.options(dec: Map<Char, Σᐩ>) =
  transitions.flatMap { next -> (next.min..next.max).map { tok -> dec[tok] to next.dest } }.toMap()

fun JAutomaton<String, Double>.toDot(processed: MutableSet<Any> = mutableSetOf()) =
  LabeledGraph {
    val stateQueue = mutableListOf<Any>()
    initialStates().forEach { stateQueue.add(it) }
    while (true) {
      if (stateQueue.isEmpty()) break
      val state = stateQueue.removeAt(0)
      transitionsOut(state).forEach {
        val label = label(it) + "/" + transitionWeight(it).toString().take(4)
        val next = this@toDot.to(it)
        val initws = initialWeight(state)
        val finalws = finalWeight(state)
        val initwn = initialWeight(next)
        val finalwn = finalWeight(next)
        (state.hashCode().toString() + "#$initws/$finalws")[label] = next.hashCode().toString() + "#$initwn/$finalwn"
        if (next !in processed) {
          processed.add(next)
          stateQueue.add(next)
        }
      }
    }
  }.toDot()
    // States are typically unlabeled in FSA diagrams
    .replace("Mrecord\"", "Mrecord\", label=\"\"")
    // Final states are suffixed with /1.0 and drawn as double circles
    .replace("/1.0\" [\"shape\"=\"Mrecord\"", "/1.0\" [\"shape\"=\"doublecircle\"")
    .replace("Mrecord", "circle") // FSA states should be circular
    .replace("null", "ε") // null label = ε-transition

/*
 * Returns a sequence trajectories through a DFA sampled using the Markov chain.
 * The DFA is expected to be deterministic. We use the Markov chain to steer the
 * random walk through the DFA by sampling the best transitions conditioned on the
 * previous n-1 transitions, i.e., q' ~ argmax_{q'} P(q' | q_{t-1}, ..., q_{t-n+1})
 */

data class FSATrajectory(val traj: List<Σᐩ?>, val lastState: BState,
                         val score: Double, val id: Int = traj.hashCode()): Comparable<FSATrajectory> {
  val isComplete: Boolean = lastState.isAccept
  val tokens by lazy { traj.reversed().filterNotNull() }
  val lenNormedScore = score / traj.size
  fun append(tok: Σᐩ?, state: BState, score: Double) =
    FSATrajectory(listOf(tok) + traj, state, score, id * 31 + tok.hashCode())
  override fun toString() = tokens.joinToString(" ")
//  override fun equals(other: Any?): Boolean = other is FSATrajectory && lenNormedScore == other.lenNormedScore
  override fun equals(other: Any?): Boolean = other is FSATrajectory && id == other.id
  override fun compareTo(other: FSATrajectory): Int = lenNormedScore.compareTo(other.lenNormedScore)
}

fun BAutomaton.min(): BAutomaton = minimize(this)

fun PTree.toDFA(
  minimize: Boolean = false,
  unitRule: (String) -> dk.brics.automaton.Automaton = {
    BAutomaton.makeChar(Random(it.hashCode()).nextInt().toChar())
  }
): BAutomaton? =
  measureTimedValue {
    BAutomaton.setMinimization(MINIMIZE_BRZOZOWSKI)
    val period = 5
    var i = 0
    var j = 0
    propagator(
      both = { a, b -> if (a == null) b else if (b == null) a
        // Only periodically minimize the automata during construction
        else if (i++ % period == 0) a.concatenate(b).min() else a.concatenate(b) },
      either = { l -> if (l.isEmpty()) null else BAutomaton.union(l).min() },
      unit = { a -> if ("ε" in a.root) null else unitRule(a.root) }
    )
  }.also { println("Took ${it.duration} to build FSA") }.value
  ?.also { println("Original automaton had ${it
    .let { "${it.numberOfStates} states and ${it.numberOfTransitions} transitions"}}")
    if (minimize) measureTimedValue { BAutomaton.minimize(it) }
      .also { println("Minimization took ${it.duration}") }.value
//            .also { it.toDot().replaceAll(stbl).alsoCopy() }
      .also {
        // Minimal automata had 92 states and 707 transitions
        println("Minimal automata had ${
          it.let { "${it.numberOfStates} states and ${it.numberOfTransitions} transitions" }
        }")
      }
  }

// Steers a random walk using the last n-1 transitions from the Markov Chain
fun BAutomaton.decodeDFA(
  mc: MarkovChain<Σᐩ>,
  // BAutomata uses a Unicode alphabet, and the Markov Chain recognizes a
  // string-based alphabet, so we need a way to translate between the two
  dec: Map<Char, Σᐩ>, // Maps unicode characters back to strings
  callback: (Σᐩ) -> Unit = {},
  timeout: Duration = Duration.INFINITE,
  beamWidth: Long = MAX_BM_WID, // Maximum number of trajectories to keep at each step
): List<Σᐩ> {
  val startTime = TimeSource.Monotonic.markNow()
  val fullTrajectories = ConcurrentLinkedQueue<FSATrajectory>() // Max-heap for full trajectories
  val beam = PriorityQueue<FSATrajectory>() // Beam for partial trajectories

  beam.add(FSATrajectory(List(mc.memory) { null }, initialState, 0.0))

  while (
    fullTrajectories.size < beamWidth &&
    beam.isNotEmpty() &&
    startTime.elapsedNow() < timeout
  ) {
    val nextBeam = beam.parallelStream().flatMap { partTraj ->
      val lastToks = partTraj.traj.take(mc.memory - 1).reversed()
      partTraj.lastState.transitions.flatMap { next ->
        (next.min..next.max).map { tok ->
          val decTok = dec[tok]
          val nextScore = partTraj.score + mc.scoreChunk(lastToks + decTok)
          partTraj.append(decTok, next.dest, nextScore)
        }
      }.flatMap { traj ->
        if (traj.isComplete) {
          fullTrajectories.add(traj)
          callback(traj.toString())
          if (traj.lastState.transitions.isNotEmpty()) listOf(traj) else emptyList()
        } else { listOf(traj) }
      }.stream()
    }.sorted().limit(beamWidth).toList()

    beam.clear()
    beam.addAll(nextBeam)
  }

  val deduped = fullTrajectories.distinct().map { it.toString() }.toList()

  println("Took ${startTime.elapsedNow()} to decode ${deduped.size} trajectories, with ${beam.size} in queue")
  return deduped
}

// Java Stream doesn't have mapNotNull in Kotlin, so add a tiny helper:
inline fun <T, R : Any> Stream<T>.mapNotNull(crossinline f: (T) -> R?): Stream<R> =
  map { f(it) }.filter { it != null }.map { it!! }

fun DFSM.decodeDFA(
  mc: FastMC<Σᐩ>,
  dec: Map<Char, Σᐩ>,
  callback: (Σᐩ) -> Unit = {},
  timeout: Duration = Duration.INFINITE,
  beamWidth: Long = 40_000_000L,
): List<Σᐩ> {
  val startTime = TimeSource.Monotonic.markNow()

  val entriesInOrder: List<Map.Entry<Char, Σᐩ>> = dec.entries.toList()
  val byIndex: List<Σᐩ> = entriesInOrder.map { it.value }

  val byCode: Map<Int, Σᐩ> = entriesInOrder.associate { (ch, tok) -> ch.code to tok }
  val codesSorted: IntArray = entriesInOrder.map { it.key.code }.sorted().toIntArray()
  val contiguousBase: Int? = codesSorted.firstOrNull()

  val hasContiguousBlock: Boolean = run {
    val b = contiguousBase ?: return@run false
    if (dec.size < width) return@run false
    var i = 0
    while (i < width) {
      if (!byCode.containsKey(b + i)) return@run false
      i++
    }
    true
  }

  fun decodeSym(a: Int): Σᐩ? {
    if (hasContiguousBlock) {
      val tok = byCode[(contiguousBase!! + a)]
      if (tok != null) return tok
    }
    return byIndex.getOrNull(a)
  }

  // Precompute label -> token / encoded token once
  val tokByLabel = arrayOfNulls<String>(width)
  val tokIdByLabel = IntArray(width) { -1 }
  for (a in 0 until width) {
    val tok = decodeSym(a) ?: continue
    tokByLabel[a] = tok
    tokIdByLabel[a] = mc.encode(tok)
  }

  data class DFSMTrajectory(
    val base: FastMC.ContextBase,
    val lastState: String,
    val score: Double,
    val out: String
  ) {
    fun isComplete(dfsm: DFSM): Boolean = lastState in dfsm.F
    fun hasOutgoing(dfsm: DFSM): Boolean = dfsm.deltaMap[lastState]?.isNotEmpty() == true
  }

  data class ExpansionBatch(
    val next: MutableList<DFSMTrajectory>,
    val complete: MutableList<DFSMTrajectory>
  )

  val fullTrajectories = ArrayList<DFSMTrajectory>()
  var frontier = arrayListOf(
    DFSMTrajectory(
      base = mc.contextBaseEncoded(IntArray(0)),
      lastState = q_alpha,
      score = 0.0,
      out = ""
    )
  )

  while (
    fullTrajectories.size.toLong() < beamWidth &&
    frontier.isNotEmpty() &&
    startTime.elapsedNow() < timeout
  ) {
    val batches =
      if (frontier.size < 256) {
        // small frontiers: avoid parallel overhead
        frontier.map { partTraj ->
          val next = ArrayList<DFSMTrajectory>()
          val complete = ArrayList<DFSMTrajectory>()
          val row = deltaMap[partTraj.lastState].orEmpty()

          for ((a, nxt) in row) {
            if (a !in 0 until width) continue
            val tokId = tokIdByLabel[a]
            if (tokId < 0) continue
            val tok = tokByLabel[a] ?: continue

            val delta = mc.scoreTransitionFromBaseEncoded(partTraj.base, tokId)
            val nextBase = mc.advanceEncoded(partTraj.base, tokId)
            val nextScore = partTraj.score + delta
            val nextOut = if (partTraj.out.isEmpty()) tok else "${partTraj.out} $tok"

            val traj = DFSMTrajectory(nextBase, nxt, nextScore, nextOut)

            if (traj.isComplete(this)) {
              complete.add(traj)
              if (traj.hasOutgoing(this)) next.add(traj)
            } else {
              next.add(traj)
            }
          }

          ExpansionBatch(next, complete)
        }
      } else {
        frontier.parallelStream().unordered().map { partTraj ->
          val next = ArrayList<DFSMTrajectory>()
          val complete = ArrayList<DFSMTrajectory>()
          val row = deltaMap[partTraj.lastState].orEmpty()

          for ((a, nxt) in row) {
            if (a !in 0 until width) continue
            val tokId = tokIdByLabel[a]
            if (tokId < 0) continue
            val tok = tokByLabel[a] ?: continue

            val delta = mc.scoreTransitionFromBaseEncoded(partTraj.base, tokId)
            val nextBase = mc.advanceEncoded(partTraj.base, tokId)
            val nextScore = partTraj.score + delta
            val nextOut = if (partTraj.out.isEmpty()) tok else "${partTraj.out} $tok"

            val traj = DFSMTrajectory(nextBase, nxt, nextScore, nextOut)

            if (traj.isComplete(this)) {
              complete.add(traj)
              if (traj.hasOutgoing(this)) next.add(traj)
            } else {
              next.add(traj)
            }
          }

          ExpansionBatch(next, complete)
        }.toList()
      }

    val nextFrontier = ArrayList<DFSMTrajectory>()
    for (batch in batches) {
      if (fullTrajectories.size.toLong() >= beamWidth) break

      for (traj in batch.complete) {
        if (fullTrajectories.size.toLong() >= beamWidth) break
        fullTrajectories.add(traj)
        callback(traj.out)
      }

      if (fullTrajectories.size.toLong() < beamWidth) {
        nextFrontier.addAll(batch.next)
      }
    }

    frontier = nextFrontier
  }

  val deduped = fullTrajectories
    .asSequence()
    .map { it.out }
    .distinct()
    .toList()

  println("Took ${startTime.elapsedNow()} to decode ${deduped.size} trajectories, with ${frontier.size} in queue")
  return deduped
}

fun DFSM.decodeDFAWithWDFA(
  wdfa: WFA,
  dec: Map<Char, Σᐩ>,
  callback: (Σᐩ) -> Unit = {},
  timeout: Duration = Duration.INFINITE,
  beamWidth: Long = MAX_BM_WID,
  penalty: Double = -20.0,
): List<Σᐩ> {
  val startTime = TimeSource.Monotonic.markNow()

  val entriesInOrder: List<Map.Entry<Char, Σᐩ>> = dec.entries.toList()
  val byIndex: List<Σᐩ> = entriesInOrder.map { it.value }

  val byCode: Map<Int, Σᐩ> = entriesInOrder.associate { (ch, tok) -> ch.code to tok }
  val codesSorted: IntArray = entriesInOrder.map { it.key.code }.sorted().toIntArray()
  val contiguousBase: Int? = codesSorted.firstOrNull()

  val hasContiguousBlock: Boolean = run {
    val b = contiguousBase ?: return@run false
    if (dec.size < width) return@run false
    var i = 0
    while (i < width) {
      if (!byCode.containsKey(b + i)) return@run false
      i++
    }
    true
  }

  fun decodeSym(a: Int): Σᐩ? {
    if (hasContiguousBlock) {
      val tok = byCode[(contiguousBase!! + a)]
      if (tok != null) return tok
    }
    return byIndex.getOrNull(a)
  }

  // Precompute label -> token once.
  val tokByLabel = arrayOfNulls<String>(width)
  for (a in 0 until width) {
    val tok = decodeSym(a) ?: continue
    tokByLabel[a] = tok
  }

  data class DFSMTrajectory(
    val wdfaState: Int,
    val lastState: String,
    val score: Double,
    val out: String
  ) {
    fun isComplete(dfsm: DFSM): Boolean = lastState in dfsm.F
    fun hasOutgoing(dfsm: DFSM): Boolean = dfsm.deltaMap[lastState]?.isNotEmpty() == true

    fun finalScore(wdfa: WFA): Double {
      val fw = wdfa.tokenFinalWeight(wdfaState)
      return if (fw == Double.NEGATIVE_INFINITY) {
        Double.NEGATIVE_INFINITY
      } else {
        score + fw
      }
    }
  }

  data class ExpansionBatch(
    val next: MutableList<DFSMTrajectory>,
    val complete: MutableList<DFSMTrajectory>
  )

  val fullTrajectories = ArrayList<DFSMTrajectory>()

  var frontier = arrayListOf(
    DFSMTrajectory(
      wdfaState = wdfa.tokenStartState(),
      lastState = q_alpha,
      score = wdfa.tokenStartWeight(),
      out = ""
    )
  )

  while (
    fullTrajectories.size.toLong() < beamWidth &&
    frontier.isNotEmpty() &&
    startTime.elapsedNow() < timeout
  ) {
    val batches =
      if (frontier.size < 256) {
        // small frontiers: avoid parallel overhead
        frontier.map { partTraj ->
          val next = ArrayList<DFSMTrajectory>()
          val complete = ArrayList<DFSMTrajectory>()
          val row = deltaMap[partTraj.lastState].orEmpty()

          for ((a, nxt) in row) {
            if (a !in 0 until width) continue
            val tok = tokByLabel[a] ?: continue

            val step = wdfa.stepTokenState(partTraj.wdfaState, tok, penalty)
            val nextScore = partTraj.score + step.delta
            val nextOut = if (partTraj.out.isEmpty()) tok else "${partTraj.out} $tok"

            val traj = DFSMTrajectory(
              wdfaState = step.target,
              lastState = nxt,
              score = nextScore,
              out = nextOut
            )

            if (traj.isComplete(this)) {
              complete.add(traj)
              if (traj.hasOutgoing(this)) next.add(traj)
            } else {
              next.add(traj)
            }
          }

          ExpansionBatch(next, complete)
        }
      } else {
        frontier.parallelStream().unordered().map { partTraj ->
          val next = ArrayList<DFSMTrajectory>()
          val complete = ArrayList<DFSMTrajectory>()
          val row = deltaMap[partTraj.lastState].orEmpty()

          for ((a, nxt) in row) {
            if (a !in 0 until width) continue
            val tok = tokByLabel[a] ?: continue

            val step = wdfa.stepTokenState(partTraj.wdfaState, tok, penalty)
            val nextScore = partTraj.score + step.delta
            val nextOut = if (partTraj.out.isEmpty()) tok else "${partTraj.out} $tok"

            val traj = DFSMTrajectory(
              wdfaState = step.target,
              lastState = nxt,
              score = nextScore,
              out = nextOut
            )

            if (traj.isComplete(this)) {
              complete.add(traj)
              if (traj.hasOutgoing(this)) next.add(traj)
            } else {
              next.add(traj)
            }
          }

          ExpansionBatch(next, complete)
        }.toList()
      }

    val nextFrontier = ArrayList<DFSMTrajectory>()
    for (batch in batches) {
      if (fullTrajectories.size.toLong() >= beamWidth) break

      for (traj in batch.complete) {
        if (fullTrajectories.size.toLong() >= beamWidth) break
        fullTrajectories.add(traj)
        callback(traj.out)
      }

      if (fullTrajectories.size.toLong() < beamWidth) {
        nextFrontier.addAll(batch.next)
      }
    }

    frontier = nextFrontier
  }

  val deduped =
    fullTrajectories
      .asSequence()
      .map { it.out to it.finalScore(wdfa) }
      // Matches your old reranker: p1.second.compareTo(p2.second)
      .sortedWith { p1, p2 -> p1.second.compareTo(p2.second) }
      .distinctBy { it.first }
      .map { it.first }
      .toList()

  println("Took ${startTime.elapsedNow()} to decode ${deduped.size} WDFA-ranked trajectories, with ${frontier.size} in queue")
  return deduped
}

fun BAutomaton.decodeDFA(
  dec: Map<Char, Σᐩ>, // Maps unicode characters back to strings because BAutomata uses Unicode
  take: Int = 10_000,
) = getFiniteStrings(take).map { it.map { dec[it]!! }.joinToString(" ") }

fun GRE.toDFA(
  terms: List<String>? = null,
  unitRule: (String) -> dk.brics.automaton.Automaton = {
    if (terms.isNullOrEmpty()) null!!
    BAutomaton.makeChar(Random(it.hashCode()).nextInt().toChar())
  }
): BAutomaton = when (this) {
  is EPS -> TODO()
  is SET -> BAutomaton.union(s.toList().map { unitRule(if (terms== null) it.toString() else terms[it]) })
  is CUP -> BAutomaton.union(args.map { it.toDFA(terms) })
  is CAT -> l.toDFA(terms).concatenate(r.toDFA(terms))
}

fun BAutomaton.toDFSM(): DFSM {
  // Ensure the automaton is deterministic
  require(this.isDeterministic) { "Automaton must be deterministic" }

  // Get all states and assign unique string names (e.g., "q0", "q1", ...)
  val states = this.states.toList()
  val stateToName = states.mapIndexed { index, state -> state to "q$index" }.toMap()

  // Set of all state names
  val Q = stateToName.values.toSet()

  // Initial state
  val initialState = this.initialState
  val q_alpha = stateToName[initialState]!!

  // Set of accepting states
  val F = states.filter { it.isAccept }.map { stateToName[it]!! }.toSet()

  // Compute the alphabet size (width) by finding the maximum symbol used
  var maxSymbol = -1
  for (state in states) {
    for (transition in state.transitions) {
      val max = transition.max.toInt()
      if (max > maxSymbol) maxSymbol = max
    }
  }
  val width = if (maxSymbol >= 0) maxSymbol + 1 else 0

  // Build the transition map
  val deltaMap = mutableMapOf<String, MutableMap<Int, String>>()
  for (state in states) {
    val stateName = stateToName[state]!!
    val symbolToNext = mutableMapOf<Int, String>()
    for (transition in state.transitions) {
      val min = transition.min.toInt()
      val max = transition.max.toInt()
      val nextStateName = stateToName[transition.dest]!!
      // Expand the character range into individual symbol transitions
      for (symbol in min..max) {
        symbolToNext[symbol] = nextStateName
      }
    }
    deltaMap[stateName] = symbolToNext
  }

  // Construct and return the DFSM
  return DFSM(Q, deltaMap, q_alpha, F, width)
}

/**
 * One HTTP request scores one conditional prefix against all valid next-token emissions
 * from the current DFA state.
 *
 * Wire protocol, POST /complete, text/plain:
 *   line 0: model-space prefix, e.g. charify(broken) + "|" + encode(partialRepair)
 *   line 1..n: model-space candidate continuations, e.g. encode(nextGrammarToken),
 *               or a full encoded repair when rescoring complete trajectories.
 *
 * Response:
 *   one floating-point log-probability per candidate, one per line, aligned with candidates.
 */
data class ExternalCompleterScores(
  val scores: DoubleArray,
  val cacheHit: Boolean
)

class ExternalCompleterClient(
  val url: String = "http://localhost:8083/complete",
  val timeout: java.time.Duration = java.time.Duration.ofSeconds(30),
  val fallbackLogProb: Double = -20.0,
  val maxCacheEntries: Int = 50_000,
) {
  private val http: HttpClient = HttpClient.newBuilder().build()

  private val cache: MutableMap<String, DoubleArray> = Collections.synchronizedMap(
    object : LinkedHashMap<String, DoubleArray>(16, 0.75f, true) {
      override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, DoubleArray>?): Boolean =
        size > maxCacheEntries
    }
  )

  private fun key(prefix: String, candidates: List<String>): String =
    buildString {
      append(prefix)
      append('\u0000')
      candidates.forEach {
        append(it)
        append('\u0001')
      }
    }

  fun scoreNext(prefix: String, candidates: List<String>): DoubleArray =
    scoreNextWithMeta(prefix, candidates).scores

  fun scoreNextWithMeta(prefix: String, candidates: List<String>): ExternalCompleterScores {
    if (candidates.isEmpty()) return ExternalCompleterScores(DoubleArray(0), cacheHit = true)

    val k = key(prefix, candidates)
    synchronized(cache) {
      cache[k]?.let { return ExternalCompleterScores(it.copyOf(), cacheHit = true) }
    }

    val body = buildString {
      append(prefix)
      append('\n')
      candidates.forEachIndexed { i, tok ->
        append(tok)
        if (i + 1 < candidates.size) append('\n')
      }
    }

    val request = HttpRequest.newBuilder()
      .uri(URI.create(url))
      .timeout(timeout)
      .header("Content-Type", "text/plain; charset=utf-8")
      .POST(HttpRequest.BodyPublishers.ofString(body))
      .build()

    val scores = try {
      val response = http.send(request, HttpResponse.BodyHandlers.ofString())
      if (response.statusCode() !in 200..299) {
        System.err.println("External completer HTTP ${response.statusCode()}: ${response.body().take(200)}")
        uniformFallback(candidates.size)
      } else {
        parseScores(response.body(), candidates.size)
      }
    } catch (e: HttpTimeoutException) {
      System.err.println("External completer timed out: ${e.message}")
      uniformFallback(candidates.size)
    } catch (e: Exception) {
      System.err.println("External completer failed: ${e.message}")
      uniformFallback(candidates.size)
    }

    synchronized(cache) { cache[k] = scores.copyOf() }
    return ExternalCompleterScores(scores, cacheHit = false)
  }

  private fun uniformFallback(n: Int): DoubleArray {
    if (n <= 0) return DoubleArray(0)
    val lp = -ln(n.toDouble())
    return DoubleArray(n) { lp }
  }

  private fun parseScores(body: String, expected: Int): DoubleArray {
    val vals = body.lineSequence()
      .filter { it.isNotBlank() }
      .map { it.trim().toDoubleOrNull() ?: fallbackLogProb }
      .toList()

    if (vals.size != expected) {
      System.err.println("External completer returned ${vals.size} scores for $expected candidates; falling back")
      return uniformFallback(expected)
    }

    return DoubleArray(expected) { i ->
      val x = vals[i]
      if (x.isFinite()) x else fallbackLogProb
    }
  }
}

/** Encode a whitespace-delimited grammar-token string into the Makemore character stream. */
private fun encodeTokenSequenceForMakemore(
  toks: String,
  tokToChar: Map<Σᐩ, Char>,
): String {
  if (toks.isBlank()) return ""
  return toks.trim().split(Regex("\\s+")).joinToString("") { tok ->
    tokToChar[tok]?.toString()
      ?: error("No Makemore character for grammar token: '$tok'")
  }
}

private fun encodeOneTokenForMakemore(
  tok: Σᐩ,
  tokToChar: Map<Σᐩ, Char>,
): String = tokToChar[tok]?.toString()
  ?: error("No Makemore character for grammar token: '$tok'")

fun scoreNextGPUWithBrokenPrefix(
  brokePrefix: String,
  partialRepair: String,
  nextTokens: List<String>,
  tokToChar: Map<Σᐩ, Char>,
  url: String = "http://localhost:8083/complete",
): List<Double> {
  val prefix = brokePrefix + "|" + encodeTokenSequenceForMakemore(partialRepair, tokToChar)
  val cands = nextTokens.map { encodeOneTokenForMakemore(it, tokToChar) }
  return ExternalCompleterClient(url = url).scoreNext(prefix, cands).toList()
}

private data class ExternalEdge(
  val tok: Σᐩ,
  val nextState: String,
  val encoded: String,
)

/**
 * External-model DFA decoder.
 *
 * IMPORTANT: this is intentionally branch-point driven. Unary DFA chains are collapsed without
 * spending HTTP calls, then completed trajectories can be rescored exactly as full continuations.
 * This avoids the common failure mode where 3000 calls are consumed walking deterministic suffixes
 * before reaching an accepting state.
 *
 * [brokePrefix] should already be charified, e.g. "|...}".
 * [tokToChar] is MakeMore.PyTokMap.tm.
 * [charToTok] is MakeMore.PyTokMap.mt.
 */
fun DFSM.decodeDFAWithExternalModel(
  brokePrefix: String,
  tokToChar: Map<Σᐩ, Char>,
  charToTok: Map<Char, Σᐩ>,
  external: ExternalCompleterClient = ExternalCompleterClient(),
  callback: (Σᐩ) -> Unit = {},
  timeout: Duration = Duration.INFINITE,
  beamWidth: Long = MAX_BM_WID,
  frontierBeam: Int = 512,
  modelCallBudget: Int = 3000,
  fallbackScore: Double = -20.0,
  progressEveryCalls: Int = 100,
  collapseUnaryChains: Boolean = true,
  maxUnaryCollapseSteps: Int = 256,
  unaryStepScore: Double = 0.0,
  rescoreComplete: Boolean = true,
  rescoreBatchSize: Int = 4096,
  debugWire: Boolean = false,
): List<Σᐩ> {
  val startTime = TimeSource.Monotonic.markNow()

  val entriesInOrder: List<Map.Entry<Char, Σᐩ>> = charToTok.entries.toList()
  val byIndex: List<Σᐩ> = entriesInOrder.map { it.value }

  val byCode: Map<Int, Σᐩ> = entriesInOrder.associate { (ch, tok) -> ch.code to tok }
  val codesSorted: IntArray = entriesInOrder.map { it.key.code }.sorted().toIntArray()
  val contiguousBase: Int? = codesSorted.firstOrNull()

  val hasContiguousBlock: Boolean = run {
    val b = contiguousBase ?: return@run false
    if (charToTok.size < width) return@run false
    var i = 0
    while (i < width) {
      if (!byCode.containsKey(b + i)) return@run false
      i++
    }
    true
  }

  fun decodeSym(a: Int): Σᐩ? {
    if (hasContiguousBlock) {
      val tok = byCode[(contiguousBase!! + a)]
      if (tok != null) return tok
    }
    return byIndex.getOrNull(a)
  }

  val tokByLabel = arrayOfNulls<String>(width)
  for (a in 0 until width) tokByLabel[a] = decodeSym(a)

  fun edgesFrom(state: String): List<ExternalEdge> {
    val row = deltaMap[state].orEmpty()
    if (row.isEmpty()) return emptyList()

    val out = ArrayList<ExternalEdge>(row.size)
    for ((a, nxt) in row) {
      if (a !in 0 until width) continue
      val tok = tokByLabel[a] ?: continue
      val enc = encodeOneTokenForMakemore(tok, tokToChar)
      out.add(ExternalEdge(tok = tok, nextState = nxt, encoded = enc))
    }
    return out
  }

  data class DFSMTrajectory(
    val lastState: String,
    val score: Double,
    val out: String,
  ) {
    fun isComplete(dfsm: DFSM): Boolean = lastState in dfsm.F
    fun hasOutgoing(dfsm: DFSM): Boolean = dfsm.deltaMap[lastState]?.isNotEmpty() == true
  }

  fun betterFirst(): Comparator<DFSMTrajectory> =
    compareByDescending<DFSMTrajectory> { it.score }.thenBy { it.out.length }.thenBy { it.out }

  fun appendTok(traj: DFSMTrajectory, edge: ExternalEdge, delta: Double): DFSMTrajectory =
    DFSMTrajectory(
      lastState = edge.nextState,
      score = traj.score + delta,
      out = if (traj.out.isEmpty()) edge.tok else "${traj.out} ${edge.tok}",
    )

  val fullTrajectories = ArrayList<DFSMTrajectory>()
  val completeSeen = HashSet<String>()

  fun recordComplete(traj: DFSMTrajectory) {
    if (completeSeen.add(traj.out)) {
      fullTrajectories.add(traj)
      callback(traj.out)
    }
  }

  /**
   * Follow deterministic/unary stretches without model calls. This is a search optimization only;
   * final ranking can be made exact by [rescoreComplete].
   */
  fun collapseUnary(traj0: DFSMTrajectory): DFSMTrajectory? {
    if (!collapseUnaryChains) return traj0

    var traj = traj0
    var steps = 0
    while (steps < maxUnaryCollapseSteps && startTime.elapsedNow() < timeout) {
      if (traj.isComplete(this)) {
        recordComplete(traj)
        if (fullTrajectories.size.toLong() >= beamWidth) return null
        if (!traj.hasOutgoing(this)) return null
      }

      val edges = edgesFrom(traj.lastState)
      if (edges.size != 1) return traj

      traj = appendTok(traj, edges.single(), unaryStepScore)
      steps++
    }
    return traj
  }

  fun modelPrefix(out: String): String =
    brokePrefix + "|" + encodeTokenSequenceForMakemore(out, tokToChar)

  var frontier = arrayListOf(DFSMTrajectory(lastState = q_alpha, score = 0.0, out = ""))
  var modelCalls = 0
  var cacheHits = 0
  var unaryCollapsed = 0
  var firstRequestPrinted = false

  while (
    fullTrajectories.size.toLong() < beamWidth &&
    frontier.isNotEmpty() &&
    modelCalls < modelCallBudget &&
    startTime.elapsedNow() < timeout
  ) {
    val active = frontier
      .asSequence()
      .sortedWith(betterFirst())
      .take(minOf(frontierBeam, modelCallBudget - modelCalls))
      .toList()

    val nextFrontier = ArrayList<DFSMTrajectory>()

    for (rawTraj in active) {
      if (fullTrajectories.size.toLong() >= beamWidth) break
      if (modelCalls >= modelCallBudget) break
      if (startTime.elapsedNow() >= timeout) break

      val beforeOutLen = rawTraj.out.length
      val partTraj = collapseUnary(rawTraj) ?: continue
      if (partTraj.out.length != beforeOutLen) unaryCollapsed++

      if (partTraj.isComplete(this)) {
        recordComplete(partTraj)
        if (fullTrajectories.size.toLong() >= beamWidth) break
        if (!partTraj.hasOutgoing(this)) continue
      }

      val edges = edgesFrom(partTraj.lastState)
      if (edges.isEmpty()) continue

      // If collapseUnaryChains is true, this should usually be a branch point.
      if (collapseUnaryChains && edges.size == 1) {
        nextFrontier.add(appendTok(partTraj, edges.single(), unaryStepScore))
        continue
      }

      val prefix = modelPrefix(partTraj.out)
      val cands = edges.map { it.encoded }

      if (debugWire && !firstRequestPrinted) {
        firstRequestPrinted = true
        println(
          "External decoder first request: state=${partTraj.lastState}, row=${deltaMap[partTraj.lastState].orEmpty().size}, " +
              "decodedCandidates=${edges.size}, prefixChars=${prefix.length}, " +
              "outToks=${if (partTraj.out.isEmpty()) 0 else partTraj.out.trim().split(Regex("\\s+")).size}, " +
              "firstCandidates=${edges.take(8).map { it.tok }}, firstEncoded=${cands.take(8)}"
        )
        println("External decoder wire prefix sample=${prefix.take(120)}")
      }

      val scored = external.scoreNextWithMeta(prefix, cands)
      if (scored.cacheHit) cacheHits++ else modelCalls++
      val deltas = scored.scores

//      if (progressEveryCalls > 0 && modelCalls > 0 && modelCalls % progressEveryCalls == 0) {
//        println(
//          "External decoder progress: calls=$modelCalls/$modelCallBudget, cacheHits=$cacheHits, " +
//              "complete=${fullTrajectories.size}, frontier=${frontier.size}, active=${active.size}, " +
//              "nextFrontier=${nextFrontier.size}, unaryCollapsed=$unaryCollapsed, elapsed=${startTime.elapsedNow()}"
//        )
//      }

      for (i in edges.indices) {
        val delta = deltas.getOrNull(i)?.takeIf { it.isFinite() } ?: fallbackScore
        val traj = appendTok(partTraj, edges[i], delta)

        if (traj.isComplete(this)) {
          recordComplete(traj)
          if (fullTrajectories.size.toLong() >= beamWidth) break
          if (traj.hasOutgoing(this)) nextFrontier.add(traj)
        } else {
          nextFrontier.add(traj)
        }
      }
    }

    frontier = nextFrontier
      .asSequence()
      .sortedWith(betterFirst())
      .distinctBy { "${it.lastState}\u0000${it.out}" }
      .take(frontierBeam)
      .toCollection(ArrayList())
  }

  val completed = fullTrajectories
    .asSequence()
    .distinctBy { it.out }
    .toList()

  val ranked = if (rescoreComplete && completed.isNotEmpty()) {
    val rescored = ArrayList<DFSMTrajectory>(completed.size)
    val prefix = brokePrefix + "|"

    for (chunk in completed.chunked(rescoreBatchSize)) {
      val cands = chunk.map { encodeTokenSequenceForMakemore(it.out, tokToChar) }
      val scored = external.scoreNextWithMeta(prefix, cands)
      if (scored.cacheHit) cacheHits++ else modelCalls++
      for (i in chunk.indices) {
        val score = scored.scores.getOrNull(i)?.takeIf { it.isFinite() } ?: chunk[i].score
        rescored.add(chunk[i].copy(score = score))
      }
    }
    rescored.sortedWith(betterFirst())
  } else {
    completed.sortedWith(betterFirst())
  }

  val deduped = ranked.map { it.out }

  println(
    "Took ${startTime.elapsedNow()} to decode ${deduped.size} external-model-ranked trajectories, " +
        "with ${frontier.size} in queue, modelCalls=$modelCalls/$modelCallBudget, " +
        "cacheHits=$cacheHits, unaryCollapsed=$unaryCollapsed, rescoreComplete=$rescoreComplete"
  )

  return deduped
}