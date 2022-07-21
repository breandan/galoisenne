package ai.hypergraph.knuthbendix

import ai.hypergraph.knuthbendix.kbs.RewriteSystem
import ai.hypergraph.knuthbendix.parser.Parser
import ai.hypergraph.knuthbendix.parser.Parser.Companion.toString
import org.junit.jupiter.api.*
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.text.ParseException
import kotlin.test.assertEquals

/*
./gradlew jvmTest --tests "ai.hypergraph.knuthbendix.RewriteTest"
*/

class RewriteTest {
  private lateinit var parser: Parser

  @BeforeEach
  fun setup() {
    parser = Parser()
  }

  @Throws(ParseException::class)
  private fun translateLHS(str: String) = toString(parser.parse(str).left)

  @Test
  @Throws(ParseException::class)
  fun testElement() {
    Assertions.assertEquals("S", translateLHS("S"))
    Assertions.assertEquals("S'", translateLHS("S'"))
  }

  @Test
  @Throws(ParseException::class)
  fun power() {
    Assertions.assertEquals("aaa", translateLHS("a^3"))
    Assertions.assertEquals("a'a'", translateLHS("a^-2"))
    Assertions.assertEquals("a", translateLHS("a'^-1"))
  }

  @Test
  @Throws(ParseException::class)
  fun reduction() {
    Assertions.assertEquals("", translateLHS("SS'"))
    Assertions.assertEquals("PT", translateLHS("PQ'RR'QT"))
  }

//  @Test(expected = IllegalArgumentException::class)
//  @Throws(ParseException::class)
//  fun invalidPower() {
//    translateLHS("b^0")
//  }

  @Test
  @Throws(ParseException::class)
  fun product() {
    Assertions.assertEquals("abc", translateLHS("abc"))
    Assertions.assertEquals("ppqqrr", translateLHS("p^2q^2r^2"))
    Assertions.assertEquals("p", translateLHS("p^3p'^2"))
  }

  @Test
  @Throws(ParseException::class)
  fun parentheses() {
    Assertions.assertEquals("a", translateLHS("(a)"))
    Assertions.assertEquals("abbbba'", translateLHS("(aba')^4"))
    Assertions.assertEquals("aba'b'", translateLHS("(ab)(ba)^-1"))
    Assertions.assertEquals("", translateLHS("(AB)(AB)^-1"))
  }

  @Test
  @Throws(ParseException::class)
  fun rhs() {
    var r: Parser.Result = parser.parse("ST=R")
    assertEquals("ST", toString(r.left))
    assertEquals("R", toString(r.right))
    r = parser.parse("((AB))=(AB)")
    assertEquals("AB", toString(r.left))
    assertEquals("AB", toString(r.right))
    r = parser.parse("ab=1")
    assertEquals("ab", toString(r.left))
    assertEquals("", toString(r.right))
    r = parser.parse("xyz")
    assertEquals("xyz", toString(r.left))
    assertEquals("", toString(r.right))
  }

  @Test
  @Throws(Exception::class)
  fun test10() {
    System.setIn(FileInputStream(File("src/jvmTest/resources/example-10.txt")))
    main(arrayOf("example-1 0.txt"))
  }

  private val SHORTLEX = object : Comparator<Collection<Char>> {
    override fun compare(o1: Collection<Char>, o2: Collection<Char>): Int {
      if (o1.size != o2.size) return o1.size - o2.size
      val iterator1 = o1.iterator()
      val iterator2 = o2.iterator()
      while (true) {
        if (!iterator1.hasNext()) return 0
        val next1 = iterator1.next()
        val next2 = iterator2.next()
        if (next1 == next2) continue
        return next1.compareTo(next2)
      }
    }
  }

  /**
   * Computes the size of the group specified by the given
   * parser result.
   */
  private fun sizeOfGroup(list: List<Parser.Result>): Int {
    //Convert to characters
    val rules: Map<List<Char>, List<Char>> =
      list.associate { it.left.map { it.ch } to it.right.map { it.ch } }

    val rewriteSystem: RewriteSystem<Char> = RewriteSystem(rules, SHORTLEX)
    val baseForms: Collection<List<Char>> = rewriteSystem.calcNormalForms()

    //baseForms.stream().forEach(System.out::println);
    //rewriteSystem.getCompleteRules().stream().forEach(System.out::println);
    return baseForms.size
  }

  fun main(args: Array<String>) {
    InputStreamReader(System.`in`).use { isr ->
      BufferedReader(isr).use { reader ->
        val currentTime = System.currentTimeMillis()
        val parser = Parser()

        // cannot use Java 8 streams because parse throws exception
        var line = reader.readLine()
        val parsed: MutableList<Parser.Result> = ArrayList<Parser.Result>()
        while (line != null) {
          if (line.isNotEmpty() && !line.startsWith("#"))
            parsed.add(parser.parse(line))
          line = reader.readLine()
        }
        val size = sizeOfGroup(parsed)
        val time = System.currentTimeMillis() - currentTime
        System.out.printf("%9d %6d.%03d s\n", size, time / 1000, time % 1000)
      }
    }
  }
//  @Test(expected = ParseException::class)
//  @Throws(ParseException::class)
//  fun parseError1() {
//    translateLHS("p^q")
//  }
//
//  @Test(expected = ParseException::class)
//  @Throws(ParseException::class)
//  fun parseError2() {
//    translateLHS("((ab)=1")
//  }
//
//  @Test(expected = ParseException::class)
//  @Throws(ParseException::class)
//  fun parseError3() {
//    translateLHS("ST = 1")
//  }
//
//  @Test
//  fun parseError4() = translateLHS("= ST")
}