package ai.hypergraph.kaliningraph.sat

import ai.hypergraph.kaliningraph.parsing.*
import ai.hypergraph.kaliningraph.sampling.MDSamplerWithoutReplacement
import java.util.stream.Collectors
import kotlin.streams.asStream


// This experiment essentially tries every possible combination of fillers in parallel
fun List<Σᐩ>.parallelSolve(
  CJL: CJL,
  fillers: Set<Σᐩ> = CJL.cfgs.map { it.terminals }.flatten().toSet() - CJL.cfgs.map { it.blocked }.flatten().toSet()
) =
  MDSamplerWithoutReplacement(fillers, count { it == HOLE_MARKER }).asStream().parallel()
    .map {
      fold("" to it) { (a, b), c ->
        if (c == HOLE_MARKER) (a + " " + b.first()) to b.drop(1) else ("$a $c") to b
      }.first.replace("ε ", "").trim()
    }
    .filter { it.matches(CJL) }
//      .filter { measureTimedValue { it.fastMatch(CFG) }.also { println("Decided ${it.value} in ${it.duration}") }.value }

// Tries to parallelize the PRNG using leapfrog method, no demonstrable speedup observed
// https://surface.syr.edu/cgi/viewcontent.cgi?article=1012&context=npac
fun List<Σᐩ>.parallelSolve(
  CFG: CFG,
  fillers: Set<Σᐩ> = CFG.terminals - CFG.blocked,
  cores: Int = Runtime.getRuntime().availableProcessors().also { println("Cores: $it") }
) =
  (0 until cores).toSet().parallelStream().flatMap { i ->
    MDSamplerWithoutReplacement(fillers, count { it == HOLE_MARKER })
      .filterIndexed { index, _ -> index % cores == i }
      .asStream()
      .map {
        fold("" to it) { (a, b), c ->
          if (c == HOLE_MARKER) (a + " " + b.first()) to b.drop(1) else ("$a $c") to b
        }.first.replace("ε ", "").trim()
      }.filter { it.matches(CFG) }
      .map { println("Thread ($i): $it"); it }
  }.collect(Collectors.toSet())