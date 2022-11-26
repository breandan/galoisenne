package ai.hypergraph.kaliningraph.compiler

import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import javax.script.*

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.compiler.CompileTest"
*/
class CompileTest {
  @Test
  fun testArrays() {
    testCompile("""val v3 = VT(1, 2, 3)[S4]""", false)
    testCompile("""val v3 = VT(1, 2, 3)[S3]""", true)

    testCompile("""val v3 = VT(1, 2, 3).let { it.take4() }""", false)
    testCompile("""val v3 = VT(1, 2, 3).let { it.take3() }""", true)

    testCompile("""val m3x2 = VT(VT(1, 2), VT(1, 2), VT(1, 2)); val q = m3x2 * m3x2""", false)
    testCompile("""val m3x3 = VT(VT(1, 2, 3), VT(1, 2, 3), VT(1, 2, 3)); val q = m3x3 * m3x3""", true)

    testCompile("""val m2x2 = VT(VT(1), VT(1, 2)); val q = m2x2 * m2x2""", false)
    testCompile("""val m2x2 = VT(VT(1, 2), VT(1, 2)); val q = m2x2 * m2x2""", true)

    testCompile("""val v5: VT<Int, L5> = VT(1, 2, 3) cc VT(4, 5); val v2 = v5[S2..S4]""", true)
    testCompile("""val v5: VT<Int, L5> = VT(1, 2, 3) cc VT(4, 5); val v2 = v5[S4..S2]""", false)
    testCompile("""val v5: VT<Int, L5> = VT(1, 2, 3) cc VT(4, 5); val v2 = v5[S3..S5]""", false)
  }

//  @Test
//  fun testKotlinJavaInterop() {
//    val kotlinSource = SourceFile.kotlin("KClass.kt", """
//        import ai.hypergraph.kaliningraph.graphs.*
//
//        class KClass {
//          fun foo() = LabeledGraph { d - e }
//        }
//    """.trimIndent())
//
//    val javaSource = SourceFile.java("JClass.java", """
//        import ai.hypergraph.kaliningraph.graphs.*;
//
//        public class JClass {
//          public void bar() {
//            // compiled Kotlin classes are visible to Java sources
//            KClass kClass = new KClass();
//            LabeledGraph lg = kClass.foo();
//          }
//        }
//    """.trimIndent())
//
//    val result = KotlinCompilation().apply {
//      sources = listOf(kotlinSource, javaSource)
//
//      inheritClassPath = true
//      messageOutputStream = System.out // see diagnostics in real time
//      verbose = false
//    }.compile()
//
//    assertEquals(true, result.exitCode)
//  }

  @Test
  fun testLinearFiniteStateRegister() {
    testCompile(
      """
      import ai.hypergraph.kaliningraph.automata.*
      import ai.hypergraph.kaliningraph.automata.T
      import ai.hypergraph.kaliningraph.automata.F

      val t: BVec5<T, F, T, T, F> =
        BVec(T, F, F, T, T)
          .lfsr().lfsr().lfsr().lfsr().lfsr().lfsr()
          .lfsr().lfsr().lfsr().lfsr().lfsr().lfsr()
          .lfsr().lfsr().lfsr().lfsr().lfsr().lfsr()
          .lfsr().lfsr().lfsr().lfsr().lfsr().lfsr()
          .lfsr().lfsr().lfsr().lfsr().lfsr().lfsr()
          .lfsr().lfsr().lfsr().lfsr().lfsr().lfsr()
          .lfsr().lfsr().lfsr().lfsr().lfsr().lfsr()
      """.trimIndent(), true
    )

    testCompile(
      """
      import ai.hypergraph.kaliningraph.automata.*
      import ai.hypergraph.kaliningraph.automata.T
      import ai.hypergraph.kaliningraph.automata.F

      val t: BVec5<T, T, F, T, F> =
        BVec(T, F, F, T, T)
          .lfsr().lfsr().lfsr().lfsr().lfsr().lfsr()
          .lfsr().lfsr().lfsr().lfsr().lfsr().lfsr()
          .lfsr().lfsr().lfsr().lfsr().lfsr().lfsr()
          .lfsr().lfsr().lfsr().lfsr().lfsr().lfsr()
          .lfsr().lfsr().lfsr().lfsr().lfsr().lfsr()
          .lfsr().lfsr().lfsr().lfsr().lfsr().lfsr()
          .lfsr().lfsr().lfsr().lfsr().lfsr().lfsr()
      """.trimIndent(), false
    )
  }

  @Test
  fun testElementaryCellularAutomaton() {
    testCompile(
      """
      import ai.hypergraph.kaliningraph.automata.*
      import ai.hypergraph.kaliningraph.automata.T
      import ai.hypergraph.kaliningraph.automata.F

      val t: BVec10<T, T, F, F, F, T, F, F, F, F> = 
        BVec(F, F, F, F, F, F, F, F, F, T)
          .eca(::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r)
          .eca(::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r)
          .eca(::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r)
          .eca(::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r)
          .eca(::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r)
          .eca(::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r)
          .eca(::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r)
          .eca(::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r)
          .eca(::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r)
          .eca(::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r)
          .eca(::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r)
          .eca(::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r)
          .eca(::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r)
          .eca(::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r)
          .eca(::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r)
          .eca(::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r)
          .eca(::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r)
          .eca(::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r)
          .eca(::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r)
          .eca(::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r)
      """.trimIndent(), true
    )

    testCompile(
      """
      import ai.hypergraph.kaliningraph.automata.*
      import ai.hypergraph.kaliningraph.automata.T
      import ai.hypergraph.kaliningraph.automata.F

      val t: BVec10<T, T, T, F, F, T, F, F, F, F> = 
        BVec(F, F, F, F, F, F, F, F, F, T)
          .eca(::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r)
          .eca(::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r)
          .eca(::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r)
          .eca(::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r)
          .eca(::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r)
          .eca(::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r)
          .eca(::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r)
          .eca(::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r)
          .eca(::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r)
          .eca(::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r)
          .eca(::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r)
          .eca(::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r)
          .eca(::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r)
          .eca(::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r)
          .eca(::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r)
          .eca(::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r)
          .eca(::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r)
          .eca(::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r)
          .eca(::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r)
          .eca(::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r, ::r)
      """.trimIndent(), false
    )
  }

  val engine = ScriptEngineManager().getEngineByExtension("kts")!!
  fun testCompile(@Language("kotlin") contents: String, shouldCompile: Boolean) {
    engine.run {
      try {
        eval(
          """
            import ai.hypergraph.kaliningraph.graphs.*
            import ai.hypergraph.kaliningraph.types.*
            $contents
          """.trimIndent()
        )
      } catch (e: Exception) {
        if(shouldCompile) throw e else return
      }
    }
  }
}