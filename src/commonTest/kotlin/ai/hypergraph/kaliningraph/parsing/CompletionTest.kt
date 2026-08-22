package ai.hypergraph.kaliningraph.parsing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CompletionTest {
  private val paired = linkedSetOf(
    "START" to listOf("P", "T"),
    "T" to listOf("A", "B"),
    "T" to listOf("T", "PAIR"),
    "PAIR" to listOf("A", "B"),
    "P" to listOf("p"),
    "A" to listOf("a"),
    "B" to listOf("b")
  ).freeze()

  @Test
  fun lazilyEnumeratesRecursiveSuffixLengthsWithoutAHorizon() {
    assertEquals(3, paired.minimumSuffixLength(emptyList()))
    assertEquals(2, paired.minimumSuffixLength(listOf("p")))
    assertEquals(1, paired.minimumSuffixLength(listOf("p", "a")))
    assertEquals(0, paired.minimumSuffixLength(listOf("p", "a", "b")))
    assertEquals(2, paired.minimumNonemptySuffixLength(listOf("p", "a", "b")))
    assertNull(paired.minimumSuffixLength(listOf("a")))

    val lengths = paired.nonemptySuffixLengths(listOf("p"))
    assertEquals(listOf(2, 4, 6, 8, 10, 12), lengths.take(6).toList())
    assertEquals(listOf(2, 4, 6), lengths.take(3).toList(), "The lazy sequence must be replayable")
    assertEquals(
      listOf(2, 4, 6, 8),
      paired.nonemptySuffixLengths(listOf("p", "a", "b")).take(4).toList()
    )
    assertEquals(
      listOf(0, 2, 4, 6),
      paired.completionSuffixLengths(listOf("p", "a", "b"), includeEmpty = true)
        .take(4).toList()
    )
  }

  @Test
  fun finiteResidualJumpsGapsAndTerminates() {
    val cfg = finiteTailGrammar(1, 3, 11)

    assertEquals(listOf(1, 3, 11), cfg.nonemptySuffixLengths(listOf("p")).toList())
    assertEquals(0, cfg.minimumSuffixLength(listOf("p", "x")))
    assertEquals(listOf(2, 10), cfg.nonemptySuffixLengths(listOf("p", "x")).toList())

    val longest = listOf("p") + List(11) { "x" }
    assertEquals(0, cfg.minimumSuffixLength(longest))
    assertNull(cfg.minimumNonemptySuffixLength(longest))
    assertFalse(cfg.nonemptySuffixLengths(longest).iterator().hasNext())
  }

  @Test
  fun minimumCanExceedTheFormerCompletionBound() {
    val cfg = finiteTailGrammar(11)

    assertEquals(11, cfg.minimumSuffixLength(listOf("p")))
    assertEquals(listOf(11), cfg.nonemptySuffixLengths(listOf("p")).toList())
  }

  @Test
  fun scalarAndLazyMinimaAgreeAtLargeRepresentableLengths() {
    val grammar = linkedSetOf<Production>("N0" to listOf("x"))
    for (level in 1..28)
      grammar += "N$level" to listOf("N${level - 1}", "N${level - 1}")
    grammar += START_SYMBOL to listOf("N28", "N28")
    val cfg = grammar.freeze()

    assertEquals(1 shl 29, cfg.minimumSuffixLength(emptyList()))
    assertEquals(1 shl 29, cfg.nonemptySuffixLengths(emptyList()).first())
  }

  @Test
  fun literalEpsilonCountsAsANonemptyGrammarSlot() {
    val cfg = linkedSetOf(
      "START" to listOf("p"),
      "START" to listOf("P", "E"),
      "P" to listOf("p"),
      "E" to listOf("ε")
    ).freeze()
    val completion = cfg.completionIndex.after(listOf("p"))

    assertTrue(completion.acceptsEmpty)
    assertEquals(setOf("ε"), completion.nextTerminals)
    assertEquals(listOf(1), completion.nonemptySuffixLengths.toList())
  }

  @Test
  fun irrelevantNonproductiveRecursionDoesNotPreventTermination() {
    val cfg = (finiteTailGrammar(1) + setOf(
      "DEAD" to listOf("DEAD", "DEAD")
    )).freeze()

    assertEquals(listOf(1), cfg.nonemptySuffixLengths(listOf("p")).toList())
    assertNull(cfg.minimumSuffixLength(listOf("x")))
  }

  private fun finiteTailGrammar(vararg tailLengths: Int): CFG {
    val grammar = linkedSetOf<Production>(
      "P" to listOf("p"),
      "X" to listOf("x")
    )
    tailLengths.forEach { length ->
      if (length == 1) grammar += "START" to listOf("P", "X")
      else {
        val root = "TAIL_${length}_$length"
        grammar += "START" to listOf("P", root)
        for (remaining in length downTo 2) {
          val lhs = "TAIL_${length}_$remaining"
          val rhs = if (remaining == 2) "X" else "TAIL_${length}_${remaining - 1}"
          grammar += lhs to listOf("X", rhs)
        }
      }
    }
    return grammar.freeze()
  }
}
