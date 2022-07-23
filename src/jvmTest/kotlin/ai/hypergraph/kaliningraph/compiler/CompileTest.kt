package ai.hypergraph.kaliningraph.compiler

import ai.hypergraph.kaliningraph.automata.T
import com.tschuchort.compiletesting.*
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.*
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.compiler.CompileTest"
*/
class CompileTest {
  @Test
  fun testArrays() {
    testCompile("""val v3 = VT(1, 2, 3)[S4]""", COMPILATION_ERROR)
    testCompile("""val v3 = VT(1, 2, 3)[S3]""", OK)

    testCompile("""val v3 = VT(1, 2, 3).let { it.take4() }""", COMPILATION_ERROR)
    testCompile("""val v3 = VT(1, 2, 3).let { it.take3() }""", OK)

    testCompile("""val m3x2 = VT(VT(1, 2), VT(1, 2), VT(1, 2)); val q = m3x2 * m3x2""", COMPILATION_ERROR)
    testCompile("""val m3x3 = VT(VT(1, 2, 3), VT(1, 2, 3), VT(1, 2, 3)); val q = m3x3 * m3x3""", OK)

    testCompile("""val m2x2 = VT(VT(1), VT(1, 2)); val q = m2x2 * m2x2""", COMPILATION_ERROR)
    testCompile("""val m2x2 = VT(VT(1, 2), VT(1, 2)); val q = m2x2 * m2x2""", OK)

    testCompile("""val v5: VT<Int, L5> = VT(1, 2, 3) cc VT(4, 5); val v2 = v5[S2..S4]""", OK)
    testCompile("""val v5: VT<Int, L5> = VT(1, 2, 3) cc VT(4, 5); val v2 = v5[S4..S2]""", COMPILATION_ERROR)
    testCompile("""val v5: VT<Int, L5> = VT(1, 2, 3) cc VT(4, 5); val v2 = v5[S3..S5]""", COMPILATION_ERROR)
  }

  @Test
  fun testKotlinJavaInterop() {
    val kotlinSource = SourceFile.kotlin("KClass.kt", """
        import ai.hypergraph.kaliningraph.graphs.*
        
        class KClass {
          fun foo() = LabeledGraph { d - e }
        }
    """.trimIndent())

    val javaSource = SourceFile.java("JClass.java", """
        import ai.hypergraph.kaliningraph.graphs.*;

        public class JClass {
          public void bar() {
            // compiled Kotlin classes are visible to Java sources
            KClass kClass = new KClass(); 
            LabeledGraph lg = kClass.foo();
          }
        }
    """.trimIndent())

    val result = KotlinCompilation().apply {
      sources = listOf(kotlinSource, javaSource)

      inheritClassPath = true
      messageOutputStream = System.out // see diagnostics in real time
    }.compile()

    assertEquals(OK, result.exitCode)
  }

  @Test
  fun testLinearFiniteStateRegister() {
    testCompile(
      """
      val t: BVec5<T, F, T, T, F> =
        BVec(T, F, F, T, T)
          .lfsr().lfsr().lfsr().lfsr().lfsr().lfsr()
          .lfsr().lfsr().lfsr().lfsr().lfsr().lfsr()
          .lfsr().lfsr().lfsr().lfsr().lfsr().lfsr()
          .lfsr().lfsr().lfsr().lfsr().lfsr().lfsr()
          .lfsr().lfsr().lfsr().lfsr().lfsr().lfsr()
          .lfsr().lfsr().lfsr().lfsr().lfsr().lfsr()
          .lfsr().lfsr().lfsr().lfsr().lfsr().lfsr()
      """.trimIndent(), OK
    )

    testCompile(
      """
      val t: BVec5<T, T, F, T, F> =
        BVec(T, F, F, T, T)
          .lfsr().lfsr().lfsr().lfsr().lfsr().lfsr()
          .lfsr().lfsr().lfsr().lfsr().lfsr().lfsr()
          .lfsr().lfsr().lfsr().lfsr().lfsr().lfsr()
          .lfsr().lfsr().lfsr().lfsr().lfsr().lfsr()
          .lfsr().lfsr().lfsr().lfsr().lfsr().lfsr()
          .lfsr().lfsr().lfsr().lfsr().lfsr().lfsr()
          .lfsr().lfsr().lfsr().lfsr().lfsr().lfsr()
      """.trimIndent(), COMPILATION_ERROR
    )
  }

  @Test
  fun testElementaryCellularAutomaton() {
    testCompile(
      """
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
      """.trimIndent(), OK
    )

    testCompile(
      """
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
      """.trimIndent(), COMPILATION_ERROR
    )
  }

  fun testCompile(@Language("kotlin") contents: String, exitCode: ExitCode) {
    val kotlinSource = SourceFile.kotlin("KClass.kt",
      """
      import ai.hypergraph.kaliningraph.graphs.*
      import ai.hypergraph.kaliningraph.types.*
      import ai.hypergraph.kaliningraph.automata.*
      import ai.hypergraph.kaliningraph.automata.T
      import ai.hypergraph.kaliningraph.automata.F
      
      $contents
      """.trimIndent())

    val result = KotlinCompilation().apply {
      sources = listOf(kotlinSource)
      inheritClassPath = true
      messageOutputStream = System.out // see diagnostics in real time
    }.compile()

    assertEquals(exitCode, result.exitCode)
  }
}