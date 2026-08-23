package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.repair.dyck
import ai.hypergraph.kaliningraph.repair.pythonStatementCNFAllProds
import kotlin.test.*

class PrefixTest {
  @Test
  fun findsSingleTokenContinuations() {
    val cfg = linkedSetOf(
      START_SYMBOL to listOf("P", "A"),
      START_SYMBOL to listOf("P", "B"),
      START_SYMBOL to listOf("p"),
      START_SYMBOL to listOf("Q", "C"),
      "P" to listOf("p"),
      "Q" to listOf("q"),
      "A" to listOf("a"),
      "B" to listOf("b"),
      "C" to listOf("c")
    ).freeze()

    assertEquals(listOf("p", "q"), cfg.singleTokenContinuations(emptyList()).toList())
    assertEquals(listOf("a", "b"), cfg.singleTokenContinuations(listOf("p")).toList())
    assertEquals(listOf("c"), cfg.singleTokenContinuations(listOf("q")).toList())
    assertTrue(cfg.singleTokenContinuations(listOf("p", "a")).isEmpty())
    assertTrue(cfg.singleTokenContinuations(listOf("bogus")).isEmpty())
  }

  @Test
  fun constructsPrefixGrammar() {
    val cfg: CFG = setOf(
      START_SYMBOL to listOf("A", "B"),
      "A" to listOf("a"),
      "B" to listOf("b")
    )

    assertEquals(
      setOf(
        "START′" to listOf("A", "B"),
        "A" to listOf("a"),
        "B" to listOf("b"),
        "START′′" to listOf("A′"),
        "START′′" to listOf("A", "B′"),
        "A′" to listOf("a"),
        "B′" to listOf("b"),
        START_SYMBOL to listOf("START′′"),
        START_SYMBOL to listOf("ε")
      ).transformIntoCNFFast(),
      cfg.prefixClosure
    )
  }

  @Test
  fun omitsDeadSuffixesAndEmptyPrefix() {
    val prefix = setOf(
      START_SYMBOL to listOf("a", "LOOP"),
      "LOOP" to listOf("LOOP")
    ).prefixClosure

    assertFalse((START_SYMBOL to listOf("ε")) in prefix)
    assertFalse(("START′′" to listOf("a")) in prefix)
  }

  @Test
  fun keepsGeneratedNamesFresh() {
    val prefix = setOf(
      START_SYMBOL to listOf("A"),
      "A" to listOf("A′")
    ).prefixClosure

    assertTrue("A′" in prefix.terminals)
  }

  @Test
  fun dyckPrefixTest() {
    val cfg = dyck
    val prefixCFG = cfg.prefixClosure
    assertTrue("( ( ) )" in cfg.language)
    assertTrue("( (" in prefixCFG.language)
  }

  @Test
  fun pythonPrefixTest() {
    val cfg = pythonStatementCNFAllProds
    val prefixCFG = cfg.prefixClosure
    val code = "NAME = NAME ( ) NEWLINE"
    assertTrue(code in cfg.language)
    val codePrefix = "NAME ="
    assertTrue(codePrefix in prefixCFG.language)
  }
}
