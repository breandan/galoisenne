package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.nextBigInteger
import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.measureTimedValue

class BoundedAcyclicCFGTest {
  private val grammar: CFG = linkedSetOf(
    "START" to listOf("ATOM"),
    "START" to listOf("PAIR"),
    "ATOM" to listOf("X"),
    "ATOM" to listOf("Y"),
    "X" to listOf("x"),
    "Y" to listOf("y"),
    "PAIR" to listOf("ATOM", "ATOM")
  )

  @Test
  fun compactStructuralStatsMatchTheGeneralCfgSummary() {
    assertEquals(grammar.stats(), grammar.boundedAcyclic(maxLength = 2).structuralStats())
  }

  @Test
  fun suppliedChildBeforeParentOrderPreservesCountsAndSamples() {
    val order = listOf("X", "Y", "ATOM", "PAIR", "START")
    val automatic = grammar.boundedAcyclic(maxLength = 2)
    val supplied = grammar.boundedAcyclic(maxLength = 2, countingOrder = order)

    assertEquals(automatic.structuralStats(), supplied.structuralStats())
    assertEquals(automatic.derivationCount, supplied.derivationCount)
    (0..2).forEach { length ->
      assertEquals(
        automatic.derivationCount("START", length),
        supplied.derivationCount("START", length)
      )
    }
    val automaticRandom = Random(12_345)
    val suppliedRandom = Random(12_345)
    assertEquals(
      automatic.samplesByIncreasingLength(automaticRandom).toList(),
      supplied.samplesByIncreasingLength(suppliedRandom).toList()
    )
    repeat(6) { rank ->
      val exactRank = BigInteger.fromInt(rank)
      assertEquals(automatic.sample(exactRank), supplied.sample(exactRank))
    }
  }

  @Test
  fun suppliedCountingOrderRejectsMalformedSymbolSetsAndDependencies() {
    assertFailsWith<IllegalArgumentException> {
      grammar.boundedAcyclic(2, countingOrder = listOf("X", "Y", "ATOM", "PAIR"))
    }
    assertFailsWith<IllegalArgumentException> {
      grammar.boundedAcyclic(
        2,
        countingOrder = listOf("X", "Y", "ATOM", "PAIR", "PAIR")
      )
    }
    assertFailsWith<IllegalArgumentException> {
      grammar.boundedAcyclic(
        2,
        countingOrder = listOf("X", "Y", "ATOM", "PAIR", "UNKNOWN")
      )
    }
    assertFailsWith<IllegalArgumentException> {
      grammar.boundedAcyclic(
        2,
        countingOrder = listOf("START", "X", "Y", "ATOM", "PAIR")
      )
    }
    val cyclic: CFG = linkedSetOf(
      "START" to listOf("A"),
      "A" to listOf("START")
    )
    assertFailsWith<IllegalArgumentException> {
      cyclic.boundedAcyclic(2, countingOrder = listOf("A", "START"))
    }
  }

  @Test
  fun platformExactCountsRoundTripAndComputeBeyondLongRange() {
    val left = BigInteger.parseString("18446744073709551617")
    val right = BigInteger.parseString("4294967311")
    val exactLeft = left.toExactCount()
    val exactRight = right.toExactCount()
    val sameLeft = BigInteger.parseString(left.toString()).toExactCount()

    assertEquals(left, exactLeft.toPublicBigInteger())
    assertEquals(left + right, exactCountAdd(exactLeft, exactRight).toPublicBigInteger())
    assertEquals(left - right, exactCountSubtract(exactLeft, exactRight).toPublicBigInteger())
    assertEquals(left * right, exactCountMultiply(exactLeft, exactRight).toPublicBigInteger())
    assertEquals(left / right, exactCountDivide(exactLeft, exactRight).toPublicBigInteger())
    assertEquals(left % right, exactCountRemainder(exactLeft, exactRight).toPublicBigInteger())
    assertTrue(exactCountCompare(exactLeft, exactRight) > 0)
    assertTrue(exactCountEquals(exactLeft, sameLeft))
    assertEquals(exactCountHash(exactLeft), exactCountHash(sameLeft))
  }

  @Test
  fun nativeRandomRanksPreserveTheUniformReferenceStreamAndSampleOrder() {
    listOf(0, 1, 20, 21, 22, 62, 63, 64, 126, 127, 191).forEach { bits ->
      val publicRandom = Random(10_000 + bits)
      val exactRandom = Random(10_000 + bits)
      repeat(100) {
        assertEquals(
          publicRandom.nextBigInteger(bits),
          exactRandom.nextExactCount(bits).toPublicBigInteger()
        )
      }
    }

    val bounds = listOf(
      BigInteger.ONE,
      BigInteger.fromInt(3),
      BigInteger.parseString("18446744073709551617"),
      BigInteger.parseString("6277101735386680763835789423207666416102355444464034512897")
    )
    bounds.forEachIndexed { index, bound ->
      val publicRandom = Random(20_000 + index)
      val exactRandom = Random(20_000 + index)
      repeat(100) {
        assertEquals(
          publicRandom.nextBigInteger(bound),
          exactRandom.nextExactCount(bound.toExactCount()).toPublicBigInteger()
        )
      }
    }

    val bounded = grammar.boundedAcyclic(maxLength = 2)
    val publicRandom = Random(30_000)
    val exactRandom = Random(30_000)
    repeat(1_000) {
      assertEquals(
        bounded.sample(publicRandom.nextBigInteger(bounded.derivationCount)),
        bounded.sample(exactRandom)
      )
    }

    val yields = (0 until 6).map { bounded.sample(BigInteger.fromInt(it)) }
    val histogram = IntArray(yields.size)
    val uniformRandom = Random(40_000)
    repeat(6_000) {
      histogram[yields.indexOf(bounded.sample(uniformRandom))]++
    }
    assertTrue(histogram.all { it in 850..1_150 }, histogram.joinToString())
  }

  @Test
  fun countsAndIndexesMoreThanLongMaxValueExactly() {
    val longBranch: CFG = buildSet {
      add("BITS_0" to listOf("a"))
      add("BITS_0" to listOf("b"))
      for (level in 1..6)
        add("BITS_$level" to listOf("BITS_${level - 1}", "BITS_${level - 1}"))
      add("LONG" to listOf("BITS_6"))
    }
    val grammar: CFG = linkedSetOf(
      "START" to listOf("SHORT"),
      "START" to listOf("LONG"),
      "SHORT" to listOf("s")
    ).apply { addAll(longBranch) }
    val twoTo64 = BigInteger.parseString("18446744073709551616")
    val total = twoTo64 + BigInteger.ONE
    val workspace = BoundedCountWorkspace()
    val bounded = grammar.boundedAcyclic(64, "START", workspace)

    assertEquals(twoTo64, bounded.derivationCount("START", 64))
    assertEquals(total, bounded.derivationCount)
    assertEquals(listOf("s"), bounded.sample(BigInteger.ZERO))
    assertEquals(List(64) { "a" }, bounded.sample(BigInteger.ONE))
    assertEquals(List(64) { "b" }, bounded.sample(total - BigInteger.ONE))
    assertFailsWith<IllegalArgumentException> { bounded.sample(BigInteger.fromInt(-1)) }
    assertFailsWith<IllegalArgumentException> { bounded.sample(total) }

    val beyondBound = grammar.boundedAcyclic(1, "START")
    assertEquals(twoTo64, beyondBound.derivationCount("START", 64))

    val secondGrammar: CFG = linkedSetOf("SECOND" to listOf("LONG")).apply {
      addAll(longBranch)
    }
    val beforeReuse = workspace.stats()
    val reused = secondGrammar.boundedAcyclic(64, "SECOND", workspace)
    assertEquals(twoTo64, reused.derivationCount)
    assertEquals(List(64) { "b" }, reused.sample(twoTo64 - BigInteger.ONE))
    assertTrue(workspace.stats().decodingHits > beforeReuse.decodingHits)
  }

  @Test
  fun benchmarksTwentyThreeCursorRangesOfOneHundredNativeDraws() {
    val shared: CFG = buildSet {
      add("BITS_0" to listOf("a"))
      add("BITS_0" to listOf("b"))
      for (level in 1..6)
        add("BITS_$level" to listOf("BITS_${level - 1}", "BITS_${level - 1}"))
      add("LONG" to listOf("BITS_6"))
    }
    val workspace = BoundedCountWorkspace()
    val cursors = List(23) { cursor ->
      linkedSetOf("CURSOR_$cursor" to listOf("LONG")).apply { addAll(shared) }
        .boundedAcyclic(64, "CURSOR_$cursor", workspace)
        .also { assertEquals(BigInteger.parseString("18446744073709551616"), it.derivationCount) }
    }
    val random = Random(50_000)
    val measured = measureTimedValue {
      cursors.sumOf { bounded -> bounded.samples(random).take(100).sumOf(List<String>::size) }
    }

    println("BoundedAcyclicCFG native sampling 23x100: ${measured.duration}")
    assertEquals(23 * 100 * 64, measured.value)
  }

  @Test
  fun benchmarksTwentyThreeCursorRangesOfOneHundredShortFirstDraws() {
    val shared: CFG = buildSet {
      add("TOKEN" to listOf("a"))
      add("TOKEN" to listOf("b"))
      add("LENGTH_1" to listOf("TOKEN"))
      for (length in 2..10)
        add("LENGTH_$length" to listOf("LENGTH_${length - 1}", "TOKEN"))
      for (length in 1..10) add("CHOICE" to listOf("LENGTH_$length"))
    }
    val workspace = BoundedCountWorkspace()
    val cursors = List(23) { cursor ->
      linkedSetOf("CURSOR_$cursor" to listOf("CHOICE")).apply { addAll(shared) }
        .boundedAcyclic(10, "CURSOR_$cursor", workspace)
    }
    val random = Random(55_000)
    val measured = measureTimedValue {
      cursors.sumOf { bounded ->
        bounded.samplesByIncreasingLength(random).sumOf { it.length }
      }
    }

    println("BoundedAcyclicCFG short-first native sampling 23x100: ${measured.duration}")
    assertEquals(23 * 10 * (1..10).sum(), measured.value)
  }

  @Test
  fun benchmarksTwentyThreeLargeRecognitionFreeConstructions() {
    val width = 256
    val depth = 6
    val countingOrder = buildList {
      for (level in 0..depth) repeat(width) { index -> add("L${level}_$index") }
      add("START")
    }
    val grammar: CFG = buildSet {
      repeat(width) { index ->
        add("L0_$index" to listOf("t${index % 16}"))
        if (index % 5 == 0) add("L0_$index" to emptyList())
      }
      for (level in 1..depth) repeat(width) { index ->
        repeat((index + level) % 3 + 1) { branch ->
          val left = (index + branch * 17) % width
          val right = (index * 3 + branch * 29 + 1) % width
          add("L${level}_$index" to listOf("L${level - 1}_$left", "L${level - 1}_$right"))
        }
      }
      repeat(width) { index -> add("START" to listOf("L${depth}_$index")) }
    }
    val measured = measureTimedValue {
      List(23) { grammar.boundedAcyclic(maxLength = 64).also { it.structuralStats() } }
    }
    val supplied = grammar.boundedAcyclic(maxLength = 64, countingOrder = countingOrder)

    println(
      "BoundedAcyclicCFG ${grammar.size}-rule recognition-free construction 23x: " +
        measured.duration
    )
    assertTrue(measured.value.all { !it.isRecognitionIndexInitialized })
    assertFalse(supplied.isRecognitionIndexInitialized)
    assertEquals(measured.value.first().structuralStats(), supplied.structuralStats())
  }

  @Test
  fun recognizesVariableUnitsWithinTheLengthBound() {
    val bounded = grammar.boundedAcyclic(maxLength = 2)

    assertTrue(bounded.recognizes(listOf("x")))
    assertTrue(bounded.recognizes(listOf("x", "y")))
    assertFalse(bounded.recognizes(listOf("x", "x", "x")))
    assertFalse(bounded.recognizes(listOf("z")))
  }

  @Test
  fun countAndSamplingLeaveRecognitionIndexesLazyUntilRecognition() {
    val bounded = grammar.boundedAcyclic(maxLength = 2)

    assertFalse(bounded.isRecognitionIndexInitialized)
    assertFalse(bounded.recognizes(listOf("x", "x", "x")))
    assertFalse(bounded.isRecognitionIndexInitialized, "An over-length rejection needs no indexes")
    assertEquals(BigInteger.fromInt(6), bounded.derivationCount)
    assertEquals(listOf("y", "y"), bounded.sample(BigInteger.fromInt(5)))
    assertEquals(100, bounded.samples(Random(91)).take(100).count())
    assertFalse(bounded.isRecognitionIndexInitialized)

    assertTrue(bounded.recognizes(listOf("x", "y")))
    assertTrue(bounded.isRecognitionIndexInitialized)
    assertTrue(bounded.recognizes(listOf("y")))
    assertFalse(bounded.recognizes(listOf("z")))
  }

  @Test
  fun countsAndIndexesEveryBoundedDerivation() {
    val bounded = grammar.boundedAcyclic(maxLength = 2)

    assertEquals(BigInteger.fromInt(6), bounded.derivationCount)
    assertEquals(
      listOf(listOf("x"), listOf("y"), listOf("x", "x"), listOf("x", "y"),
        listOf("y", "x"), listOf("y", "y")),
      (0 until 6).map { bounded.sample(BigInteger.fromInt(it)) }
    )

    val shorterBound = grammar.boundedAcyclic(maxLength = 1)
    assertEquals(BigInteger.fromInt(2), shorterBound.derivationCount)
    assertEquals(
      BigInteger.fromInt(4),
      shorterBound.derivationCount("START", 2),
      "Exact count queries beyond the sampling bound retain their original semantics"
    )
  }

  @Test
  fun samplesExactLengthSlicesWithoutLosingDerivationMultiplicity() {
    val ambiguous: CFG = linkedSetOf(
      "START" to listOf("LEFT"),
      "START" to listOf("RIGHT"),
      "START" to listOf("OTHER"),
      "LEFT" to listOf("x"),
      "RIGHT" to listOf("x"),
      "OTHER" to listOf("y")
    )
    val bounded = ambiguous.boundedAcyclic(maxLength = 1)

    assertEquals(listOf("x"), bounded.sampleAtLength(1, BigInteger.ZERO))
    assertEquals(listOf("x"), bounded.sampleAtLength(1, BigInteger.ONE))
    assertEquals(listOf("y"), bounded.sampleAtLength(1, BigInteger.fromInt(2)))
    assertFailsWith<IllegalArgumentException> {
      bounded.sampleAtLength(1, BigInteger.fromInt(3))
    }
    assertFailsWith<IllegalStateException> { bounded.sampleAtLength(0, Random(1)) }

    val first = Random(2026)
    val second = Random(2026)
    assertEquals(
      List(100) { bounded.sampleAtLength(1, first) },
      List(100) { bounded.sampleAtLength(1, second) },
      "A seed must determine the exact fixed-length derivation stream"
    )

    val histogramRandom = Random(30_000)
    val histogram = List(6_000) { bounded.sampleAtLength(1, histogramRandom)[0] }
      .groupingBy { it }
      .eachCount()
    assertTrue(histogram.getValue("x") in 3_700..4_300, histogram.toString())
    assertTrue(histogram.getValue("y") in 1_700..2_300, histogram.toString())
  }

  @Test
  fun shortFirstSamplingVisitsNonemptyLengthsInOrderAndCapsEachSlice() {
    val lengthSlices: CFG = linkedSetOf(
      "START" to listOf("ONE"),
      "START" to listOf("TWO"),
      "START" to listOf("FOUR"),
      "ONE" to listOf("TOKEN"),
      "TWO" to listOf("ONE", "ONE"),
      "FOUR" to listOf("TWO", "TWO"),
      "TOKEN" to listOf("x")
    )
    val bounded = lengthSlices.boundedAcyclic(maxLength = 4)

    val capped = bounded.samplesByIncreasingLength(
      random = Random(99),
      sampleLimit = 8,
      samplesPerLength = 3
    ).toList()
    assertEquals(listOf(1, 1, 1, 2, 2, 2, 4, 4), capped.map { it.length })
    assertTrue(capped.all { it.terminals.size == it.length })

    val shortest = bounded.shortestSampleBatch(
      random = Random(99),
      sampleLimit = 3,
      samplesPerLength = 3
    )
    assertEquals(
      capped.take(3),
      shortest.samples,
      "A partial counting view must preserve the full view's seeded derivation ranks"
    )
    assertEquals(listOf(1, 1, 1), shortest.samples.map { it.length })
    assertEquals(BigInteger.ONE, shortest.inspectedDerivationCount)
    assertEquals(0..1, shortest.inspectedLengths)
    assertFalse(shortest.coversFullBound)

    val acrossGap = bounded.shortestSampleBatch(
      random = Random(99),
      sampleLimit = 8,
      samplesPerLength = 3
    )
    assertEquals(capped, acrossGap.samples)
    assertEquals(BigInteger.fromInt(3), acrossGap.inspectedDerivationCount)
    assertEquals(0..4, acrossGap.inspectedLengths)
    assertTrue(acrossGap.coversFullBound)

    val defaults = bounded.samplesByIncreasingLength(Random(100)).toList()
    assertEquals(30, defaults.size, "Three nonempty slices contribute ten draws apiece")
    assertEquals(listOf(1, 2, 4), defaults.map { it.length }.distinct())
    assertEquals(listOf(10, 10, 10), defaults.groupingBy { it.length }.eachCount().values.toList())
    assertEquals(
      defaults,
      bounded.samplesByIncreasingLength(Random(100)).toList(),
      "A seed must determine the complete length-stratified stream"
    )

    assertTrue(bounded.samplesByIncreasingLength(sampleLimit = 0).none())
    val emptyBatch = bounded.shortestSampleBatch(sampleLimit = 0)
    assertTrue(emptyBatch.samples.isEmpty())
    assertEquals(BigInteger.ZERO, emptyBatch.inspectedDerivationCount)
    assertEquals(IntRange.EMPTY, emptyBatch.inspectedLengths)
    assertFalse(emptyBatch.coversFullBound)
    assertFailsWith<IllegalArgumentException> {
      bounded.samplesByIncreasingLength(samplesPerLength = 0)
    }
    assertFailsWith<IllegalArgumentException> {
      bounded.samplesByIncreasingLength(samplesPerLength = 11)
    }
  }

  @Test
  fun oneSliceFastPathPreservesAmbiguousShortestRanksAndSeededSamplingOrder() {
    val alternatives: CFG = linkedSetOf(
      "START" to listOf("LEFT", "RIGHT"),
      "LEFT" to listOf("a"),
      "LEFT" to listOf("b"),
      "RIGHT" to listOf("c"),
      "RIGHT" to listOf("d")
    )
    val bounded = alternatives.boundedAcyclic(maxLength = 8)
    val expected = bounded.samplesByIncreasingLength(
      random = Random(818),
      sampleLimit = 10,
      samplesPerLength = 10
    ).toList()
    val shortest = bounded.shortestSampleBatch(
      random = Random(818),
      sampleLimit = 10,
      samplesPerLength = 10
    )

    assertEquals(expected, shortest.samples)
    assertEquals(BigInteger.fromInt(4), shortest.inspectedDerivationCount)
    assertEquals(0..2, shortest.inspectedLengths)
    assertFalse(shortest.coversFullBound)
    assertEquals(setOf("a c", "a d", "b c", "b d"),
      List(400) {
        bounded.shortestSampleBatch(Random(it), 1, 10).samples.single().terminals.joinToString(" ")
      }.toSet()
    )
  }

  @Test
  fun distinctShortestBatchCollapsesAmbiguityAndExpandsAcrossExactLengths() {
    val grammar: CFG = buildSet {
      repeat(30) { index ->
        add("START" to listOf("AMBIGUOUS_$index"))
        add("AMBIGUOUS_$index" to listOf("same"))
      }
      add("START" to listOf("MINIMUM"))
      add("MINIMUM" to listOf("short"))
      add("END" to listOf(";"))
      repeat(12) { index ->
        add("START" to listOf("PAIR_$index"))
        add("PAIR_$index" to listOf("HEAD_$index", "END"))
        add("HEAD_$index" to listOf("choice_$index"))
      }
    }
    val bounded = grammar.boundedAcyclic(maxLength = 2)

    val first = bounded.shortestDistinctSampleBatch(Random(1776), sampleLimit = 10)
    val repeated = bounded.shortestDistinctSampleBatch(Random(1776), sampleLimit = 10)

    assertEquals(first, repeated)
    assertEquals(10, first.samples.size)
    assertEquals(10, first.samples.map { it.terminals }.distinct().size)
    assertEquals(listOf(1, 1) + List(8) { 2 }, first.samples.map { it.length })
    assertTrue(first.samples.take(2).map { it.terminals.single() }.toSet() == setOf("same", "short"))
    assertTrue(first.coversFullBound)
  }

  @Test
  fun shortestBatchesRemainStableAcrossOverlappingWorkspaceViews() {
    val shared: CFG = linkedSetOf(
      "CHOICE" to listOf("ONE"),
      "CHOICE" to listOf("TWO"),
      "CHOICE" to listOf("FOUR"),
      "ONE" to listOf("TOKEN"),
      "TWO" to listOf("ONE", "ONE"),
      "FOUR" to listOf("TWO", "TWO"),
      "TOKEN" to listOf("x")
    )
    fun rooted(root: String): CFG = linkedSetOf(root to listOf("CHOICE")).apply {
      addAll(shared)
    }

    val workspace = BoundedCountWorkspace()
    val first = rooted("FIRST").boundedAcyclic(4, "FIRST", workspace)
      .shortestSampleBatch(Random(404), sampleLimit = 3, samplesPerLength = 3)
    val afterFirst = workspace.stats()
    val overlapping = rooted("SECOND").boundedAcyclic(4, "SECOND", workspace)
      .shortestSampleBatch(Random(404), sampleLimit = 3, samplesPerLength = 3)
    val afterOverlapping = workspace.stats()

    assertEquals(first.samples, overlapping.samples)
    assertEquals(first.inspectedDerivationCount, overlapping.inspectedDerivationCount)
    assertEquals(first.inspectedLengths, overlapping.inspectedLengths)
    assertTrue(afterFirst.minimumEntries > 0)
    assertTrue(
      afterOverlapping.minimumHits > afterFirst.minimumHits,
      "The overlapping root should reuse minimum-yield summaries for shared descendants"
    )
    assertEquals(0, afterFirst.entries, "One shortest slice should not build bounded count vectors")
    assertEquals(0, afterOverlapping.entries, "Minimum-row reuse should stay on the direct fast path")

    val expanded = rooted("SECOND").boundedAcyclic(4, "SECOND", workspace)
      .shortestSampleBatch(Random(405), sampleLimit = 8, samplesPerLength = 3)
    val isolated = rooted("SECOND").boundedAcyclic(4, "SECOND")
      .shortestSampleBatch(Random(405), sampleLimit = 8, samplesPerLength = 3)
    assertEquals(
      isolated,
      expanded,
      "Growing a shared workspace beyond its first partial range must not change counts or ranks"
    )

    workspace.clear()
    val afterClear = workspace.stats()
    assertEquals(0, afterClear.minimumEntries)
    assertEquals(0, afterClear.minimumHits)
    assertEquals(0, afterClear.minimumMisses)
  }

  @Test
  fun countsAmbiguousDerivationsSeparately() {
    val ambiguous: CFG = linkedSetOf(
      "START" to listOf("LEFT"),
      "START" to listOf("RIGHT"),
      "LEFT" to listOf("x"),
      "RIGHT" to listOf("x")
    )
    val bounded = ambiguous.boundedAcyclic(maxLength = 1)

    assertEquals(BigInteger.fromInt(2), bounded.derivationCount)
    assertEquals(listOf("x"), bounded.sample(BigInteger.ZERO))
    assertEquals(listOf("x"), bounded.sample(BigInteger.ONE))
  }

  @Test
  fun uniformlySampledDerivationsRemainRecognized() {
    val bounded = grammar.boundedAcyclic(maxLength = 2)
    val samples = bounded.samples(Random(7)).take(100).toList()

    assertTrue(samples.all(bounded::recognizes))
    assertEquals(BigInteger.fromInt(6), bounded.forest?.totalTrees)
  }

  @Test
  fun rejectsRecursiveAndNonBinaryGrammars() {
    assertFailsWith<IllegalArgumentException> {
      setOf("START" to listOf("START")).boundedAcyclic(maxLength = 2)
    }
    assertFailsWith<IllegalArgumentException> {
      setOf("START" to listOf("a", "b", "c")).boundedAcyclic(maxLength = 3)
    }
  }

  @Test
  fun aZeroLengthBoundExcludesAGrammarWithoutEpsilon() {
    val bounded = grammar.boundedAcyclic(maxLength = 0)

    assertTrue(bounded.isEmpty)
    assertFalse(bounded.recognizes(emptyList()))
    assertFailsWith<IllegalStateException> { bounded.sample(Random(0)) }
  }

  @Test
  fun recognizesCountsAndSamplesEpsilonAtAZeroLengthBound() {
    val bounded = setOf("START" to emptyList<String>()).boundedAcyclic(maxLength = 0)

    assertTrue(bounded.recognizes(emptyList()))
    assertEquals(BigInteger.ONE, bounded.derivationCount("START", 0))
    assertEquals(BigInteger.ONE, bounded.derivationCount)
    assertEquals(emptyList(), bounded.sample(BigInteger.ZERO))
    assertEquals(emptyList(), bounded.sample(Random(0)))
    assertEquals(BigInteger.ONE, bounded.forest?.totalTrees)
  }

  @Test
  fun nullableBinaryChildrenParticipateInRecognitionCountingAndSampling() {
    val nullable: CFG = linkedSetOf(
      "START" to listOf("PAIR"),
      "PAIR" to listOf("LEFT", "RIGHT"),
      "LEFT" to emptyList(),
      "LEFT" to listOf("l"),
      "RIGHT" to emptyList(),
      "RIGHT" to listOf("r")
    )
    val bounded = nullable.boundedAcyclic(maxLength = 2)

    assertEquals(BigInteger.ONE, bounded.derivationCount("START", 0))
    assertEquals(BigInteger.fromInt(2), bounded.derivationCount("START", 1))
    assertEquals(BigInteger.ONE, bounded.derivationCount("START", 2))
    assertEquals(BigInteger.fromInt(4), bounded.derivationCount)
    assertTrue(bounded.recognizes(emptyList()))
    assertTrue(bounded.recognizes(listOf("l")))
    assertTrue(bounded.recognizes(listOf("r")))
    assertTrue(bounded.recognizes(listOf("l", "r")))
    assertFalse(bounded.recognizes(listOf("r", "l")))
    assertEquals(
      listOf(emptyList(), listOf("r"), listOf("l"), listOf("l", "r")),
      (0 until 4).map { bounded.sample(BigInteger.fromInt(it)) }
    )
    assertTrue(bounded.samples(Random(7)).take(100).all(bounded::recognizes))
    assertEquals(BigInteger.fromInt(4), bounded.forest?.totalTrees)
  }

  @Test
  fun sharesCountVectorsAcrossStructurallyEquivalentRenamedGrammars() {
    fun equivalent(prefix: String, left: String, right: List<String>): CFG = linkedSetOf(
      "${prefix}START" to listOf("${prefix}CHOICE"),
      "${prefix}CHOICE" to listOf("${prefix}PAIR"),
      "${prefix}CHOICE" to listOf("${prefix}LEFT"),
      "${prefix}PAIR" to listOf("${prefix}LEFT", "${prefix}RIGHT"),
      "${prefix}LEFT" to emptyList(),
      "${prefix}LEFT" to listOf(left),
      "${prefix}RIGHT" to listOf(right[0]),
      "${prefix}RIGHT" to listOf(right[1])
    )

    BoundedAcyclicCFG.clearSharedCountCache()
    val first = equivalent("A_", "x", listOf("y", "z")).boundedAcyclic(2, "A_START")
    val firstCount = first.derivationCount
    val firstSamples = (0 until 6).map { first.sample(BigInteger.fromInt(it)) }
    val afterFirst = BoundedAcyclicCFG.sharedCountCacheStats()

    val renamed = equivalent("B_", "u", listOf("w", "v")).boundedAcyclic(2, "B_START")
    assertEquals(firstCount, renamed.derivationCount)
    assertEquals(
      listOf(emptyList(), listOf("y"), listOf("z"), listOf("x"),
        listOf("x", "y"), listOf("x", "z")),
      firstSamples
    )
    assertEquals(
      listOf(emptyList(), listOf("w"), listOf("v"), listOf("u"),
        listOf("u", "w"), listOf("u", "v")),
      (0 until 6).map { renamed.sample(BigInteger.fromInt(it)) }
    )
    val afterRenamed = BoundedAcyclicCFG.sharedCountCacheStats()

    assertEquals(afterFirst.misses, afterRenamed.misses)
    assertTrue(afterRenamed.hits > afterFirst.hits)
    assertTrue(afterRenamed.weight <= afterRenamed.maxWeight)
  }

  @Test
  fun sharedCountVectorsAreSeparatedByLengthBound() {
    fun nullableLeaf(prefix: String): CFG = linkedSetOf(
      "${prefix}START" to emptyList(),
      "${prefix}START" to listOf("token")
    )

    BoundedAcyclicCFG.clearSharedCountCache()
    assertEquals(
      BigInteger.ONE,
      nullableLeaf("A_").boundedAcyclic(0, "A_START").derivationCount
    )
    val afterZero = BoundedAcyclicCFG.sharedCountCacheStats()
    assertEquals(
      BigInteger.fromInt(2),
      nullableLeaf("B_").boundedAcyclic(1, "B_START").derivationCount
    )
    val afterOne = BoundedAcyclicCFG.sharedCountCacheStats()

    assertTrue(afterOne.misses > afterZero.misses)
    assertEquals(afterZero.entries, afterOne.entries)
    assertEquals(
      BigInteger.ONE,
      nullableLeaf("C_").boundedAcyclic(0, "C_START").derivationCount
    )
    val afterDominatedLookup = BoundedAcyclicCFG.sharedCountCacheStats()
    assertEquals(afterOne.misses, afterDominatedLookup.misses)
    assertTrue(afterDominatedLookup.hits > afterOne.hits)
  }

  @Test
  fun workspaceReusesStableOverlappingRowsIndependentlyOfTheGlobalCache() {
    val shared: CFG = linkedSetOf(
      "PAIR" to listOf("LEFT", "RIGHT"),
      "LEFT" to emptyList(),
      "LEFT" to listOf("x"),
      "RIGHT" to listOf("y"),
      "RIGHT" to listOf("z")
    )
    val firstGrammar: CFG = linkedSetOf("FIRST" to listOf("PAIR")).apply { addAll(shared) }
    val secondGrammar: CFG = linkedSetOf(
      "SECOND" to listOf("CHOICE"),
      "CHOICE" to listOf("PAIR"),
      "CHOICE" to listOf("EXTRA"),
      "EXTRA" to listOf("LEFT", "LEFT")
    ).apply { addAll(shared) }
    val workspace = BoundedCountWorkspace()

    val first = firstGrammar.boundedAcyclic(3, "FIRST", workspace)
    assertEquals(BigInteger.fromInt(4), first.derivationCount)
    val afterFirst = workspace.stats()
    assertEquals(4, afterFirst.entries)
    assertEquals(0, afterFirst.hits)
    assertEquals(0, afterFirst.misses, "A cold workspace skips pointless lookup probes")

    // Reusing process-global IDs after a clear must not collide with retained workspace IDs.
    BoundedAcyclicCFG.clearSharedCountCache()
    val unrelated = linkedSetOf(
      "GLOBAL" to listOf("GLOBAL_LEAF"),
      "GLOBAL_LEAF" to listOf("global")
    ).boundedAcyclic(2, "GLOBAL")
    assertEquals(BigInteger.ONE, unrelated.derivationCount)

    val expected = secondGrammar.boundedAcyclic(2, "SECOND")
    BoundedAcyclicCFG.clearSharedCountCache()
    val reused = secondGrammar.boundedAcyclic(2, "SECOND", workspace)
    assertEquals(expected.derivationCount, reused.derivationCount)
    (0..2).forEach { length ->
      assertEquals(
        expected.derivationCount("SECOND", length),
        reused.derivationCount("SECOND", length)
      )
    }
    assertEquals(
      (0 until 8).map { expected.sample(BigInteger.fromInt(it)) },
      (0 until 8).map { reused.sample(BigInteger.fromInt(it)) }
    )

    val afterSecond = workspace.stats()
    assertEquals(7, afterSecond.entries)
    assertEquals(3, afterSecond.hits - afterFirst.hits)
    assertEquals(3, afterSecond.misses - afterFirst.misses)
  }

  @Test
  fun workspaceReusesMaterializedChoicesWithoutChangingIndexedSamples() {
    val shared: CFG = linkedSetOf(
      "PAIR" to listOf("LEFT", "RIGHT"),
      "LEFT" to emptyList(),
      "LEFT" to listOf("x"),
      "RIGHT" to listOf("y"),
      "RIGHT" to listOf("z")
    )
    val firstGrammar: CFG = linkedSetOf("FIRST" to listOf("PAIR")).apply { addAll(shared) }
    val secondGrammar: CFG = linkedSetOf(
      "SECOND" to listOf("CHOICE"),
      "CHOICE" to listOf("PAIR"),
      "CHOICE" to listOf("EXTRA"),
      "EXTRA" to listOf("LEFT", "LEFT")
    ).apply { addAll(shared) }
    val workspace = BoundedCountWorkspace()

    BoundedAcyclicCFG.clearSharedCountCache()
    val expectedFirst = firstGrammar.boundedAcyclic(2, "FIRST")
    val reusedFirst = firstGrammar.boundedAcyclic(2, "FIRST", workspace)
    val expectedFirstSamples = (0 until 4).map { expectedFirst.sample(BigInteger.fromInt(it)) }
    assertEquals(
      expectedFirstSamples,
      (0 until 4).map { reusedFirst.sample(BigInteger.fromInt(it)) }
    )
    val afterFirst = workspace.stats()
    assertTrue(afterFirst.decodingEntries > 0)
    assertEquals(0, afterFirst.decodingHits)
    assertTrue(afterFirst.decodingMisses > 0)

    val expectedSecond = secondGrammar.boundedAcyclic(2, "SECOND")
    val reusedSecond = secondGrammar.boundedAcyclic(2, "SECOND", workspace)
    val expectedSecondSamples = (0 until 8).map { expectedSecond.sample(BigInteger.fromInt(it)) }
    assertEquals(
      expectedSecondSamples,
      (0 until 8).map { reusedSecond.sample(BigInteger.fromInt(it)) }
    )
    val afterSecond = workspace.stats()
    assertTrue(afterSecond.decodingHits > afterFirst.decodingHits)
    assertTrue(afterSecond.decodingEntries > afterFirst.decodingEntries)

    workspace.clear()
    val afterClear = workspace.stats()
    assertEquals(0, afterClear.entries)
    assertEquals(0, afterClear.decodingEntries)
    assertEquals(0, afterClear.decodingHits)
    assertEquals(0, afterClear.decodingMisses)
  }

  @Test
  fun countsAndIndexesARepresentativeLargeLayeredGrammar() {
    val width = 256
    val depth = 6
    fun layered(prefix: String): CFG = buildSet {
      repeat(width) { index ->
        if (index % 5 == 0) add("${prefix}L0_$index" to emptyList())
        repeat(index % 4 + 1) { terminal ->
          add("${prefix}L0_$index" to listOf("t${index % 16}_$terminal"))
        }
      }
      for (level in 1..depth) repeat(width) { index ->
        repeat((index + level) % 3 + 1) { branch ->
          val left = (index + branch * 17) % width
          val right = (index * 3 + branch * 29 + 1) % width
          add("${prefix}L${level}_$index" to
            listOf("${prefix}L${level - 1}_$left", "${prefix}L${level - 1}_$right"))
        }
      }
      repeat(width) { index -> add("${prefix}START" to listOf("${prefix}L${depth}_$index")) }
    }
    BoundedAcyclicCFG.clearSharedCountCache()
    val firstGrammar = layered("A_")
    val first = firstGrammar.boundedAcyclic(maxLength = 64, startSymbol = "A_START")
    val firstMeasured = measureTimedValue { first.derivationCount }
    val afterFirst = BoundedAcyclicCFG.sharedCountCacheStats()
    val renamedGrammar = layered("B_")
    val renamed = renamedGrammar.boundedAcyclic(maxLength = 64, startSymbol = "B_START")
    val renamedMeasured = measureTimedValue { renamed.derivationCount }
    val afterRenamed = BoundedAcyclicCFG.sharedCountCacheStats()

    println(
      "BoundedAcyclicCFG ${firstGrammar.size}-rule count: first=${firstMeasured.duration}, " +
        "renamed=${renamedMeasured.duration}, secondHits=${afterRenamed.hits - afterFirst.hits}, " +
        "entries=${afterRenamed.entries}, weight=${afterRenamed.weight}/${afterRenamed.maxWeight}"
    )
    assertTrue(firstMeasured.value > BigInteger.ZERO)
    assertEquals(firstMeasured.value, renamedMeasured.value)
    assertEquals(afterFirst.misses, afterRenamed.misses)
    assertTrue(afterRenamed.hits > afterFirst.hits)
    assertTrue(afterRenamed.weight <= afterRenamed.maxWeight)
    assertEquals(64, first.sample(firstMeasured.value - BigInteger.ONE).size)
    assertEquals(
      first.sample(firstMeasured.value - BigInteger.ONE),
      renamed.sample(renamedMeasured.value - BigInteger.ONE)
    )
  }
}
