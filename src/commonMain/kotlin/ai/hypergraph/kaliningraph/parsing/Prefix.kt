package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.types.cache

/**
 * Terminals that can extend [prefix] and still reach a complete word, in terminal-index order.
 * The receiver must declare [START_SYMBOL] and be unit-free CNF.
 */
fun CFG.singleTokenContinuations(prefix: List<Σᐩ>): Set<Σᐩ> = completionIndex.after(prefix).nextTerminals

// https://aclanthology.org/2026.acl-short.25.pdf#page=3.31
val CFG.prefixClosure: CFG by cache {
  if (START_SYMBOL !in nonterminals) return@cache emptySet()
  val mark = generateSequence("′") { "$it′" }.first { m -> symbols.none { it.endsWith(m) } }
  val oldStart = "$START_SYMBOL$mark"
  fun Σᐩ.renameStart() = if (this == START_SYMBOL) oldStart else this
  val cfg = map { (lhs, rhs) -> lhs.renameStart() to rhs.map { it.renameStart() } }.toSet()
  val nts = cfg.nonterminals
  val productive = cfg.terminals.toMutableSet()
  while (productive.addAll(cfg.mapNotNull { (lhs, rhs) -> lhs.takeIf { productive.containsAll(rhs) } })) {}
  fun Σᐩ.prime() = if (this in nts) "$this$mark" else this

  (cfg + cfg.flatMap { (lhs, rhs) ->
    (rhs.indexOfLast { it !in productive }.coerceAtLeast(0)..rhs.lastIndex).map { i ->
      lhs.prime() to (rhs.take(i) + rhs[i].prime())
    }
  } + setOfNotNull(
    START_SYMBOL to listOf(oldStart.prime()),
    (START_SYMBOL to listOf("ε")).takeIf { oldStart in productive }
  )).transformIntoCNFFast().freeze()
}