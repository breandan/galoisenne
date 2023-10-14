package ai.hypergraph.kaliningraph.parsing

import ai.hypergraph.kaliningraph.*
import ai.hypergraph.kaliningraph.tensor.seekFixpoint
import ai.hypergraph.kaliningraph.types.π2
import kotlinx.datetime.Clock
import kotlin.random.Random
import kotlin.test.*
import kotlin.time.*

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest"
*/
class SetValiantTest {
/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest.testSimpleGrammar"
*/
  @Test
  fun testSimpleGrammar() {
    """
        S -> NP VP    
       VP -> eats    
       VP -> VP PP
       VP -> VP NP
       PP -> P NP
        P -> with
       NP -> she
       NP -> Det N
       NP -> NP PP
        N -> fish
        N -> fork
      Det -> a
    """.let { cfg ->
      assertTrue("she eats a fish with a fork".matches(cfg))
      assertFalse("she eats fish with".matches(cfg))
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest.testVerySimpleGrammar"
*/
  @Test
  fun testVerySimpleGrammar() {
    """
      S -> A | B
      A -> a | A A
      B -> b | B B
    """.let { cfg ->
      assertTrue("a a a a ".matches(cfg))
      assertTrue("b b b b ".matches(cfg))
      assertFalse("a b a b ".matches(cfg))
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest.testAABB"
*/
  @Test
  fun testAABB() {
//  https://www.ps.uni-saarland.de/courses/seminar-ws06/papers/07_franziska_ebert.pdf#page=3
    """
      S -> X Y
      X -> X A
      X -> A A
      Y -> Y B
      Y -> B B
      A -> a
      B -> b
    """.let { cfg ->
      assertTrue("a a a b b b ".also { println(cfg.parse(it)) }.matches(cfg))
      assertTrue("a a b b ".matches(cfg))
      assertFalse("a b a b ".matches(cfg))
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest.testArithmetic"
*/
  @Test
  fun testArithmetic() {
    """
      S -> S + S | S * S | S - S | S / S | ( S )
      S -> 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9
      S -> X | Y | Z
    """.let { cfg ->
      assertTrue("( 1 + 2 * 3 ) / 4".matches(cfg))
      assertFalse("( 1 + 2 * 3 ) - ) / 4".matches(cfg))
      assertFalse("( 1 + 2 * 3 ) - ( ) / 4".matches(cfg))
      println(cfg.parse("( 1 + ( 2 * 3 ) ) / 4")?.prettyPrint())
      println(cfg.parseCFG().prettyPrint())
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest.testCFLValidationFails"
*/
  @Test
  fun testCFLValidationFails() {
    assertFails { """( S ) -> S""".validate() }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest.testDyckLanguage"
*/
  @Test
  fun testDyckLanguage() {
    """
        START -> T
        T -> A B
        T -> A C
        T -> T T
        C -> T B
        A -> (
        B -> )
      """.parseCFG().let { cfg ->
      println(cfg.prettyPrint())
      assertTrue("( ) ( ( ) ( ) ) ( ) ".matches(cfg))
      assertFalse("( ) ( ( ) ( ) ( ) ".matches(cfg))
      assertTrue("( ) ( ( ) ) ".matches(cfg))
      assertTrue("( ) ( ) ".matches(cfg))
      assertFalse("( ) ) ".matches(cfg))
      assertFalse(")".matches(cfg))
    }

    """S -> ( ) | ( S ) | S S""".parseCFG().let { cfg ->
      assertTrue("( ) ( ( ) ( ) ) ( ) ".matches(cfg))
      assertFalse("( ) ( ( ) ( ) ( ) ".matches(cfg))
      assertTrue("( ) ( ( ) ) ".matches(cfg))
      assertTrue("( ) ( ) ".matches(cfg))
      assertFalse("( ) ) ".matches(cfg))
      assertFalse(")".matches(cfg))
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest.testDyckSolver"
*/
  @Test
  fun testDyckSolver() {
    """S -> ( ) | ( S ) | S S""".parseCFG().let { cfg ->
      val sols = "( _ _ _ _ ( ) _ _ _ _ ) ".tokenizeByWhitespace()
        .solve(cfg, fillers = cfg.terminals + "").take(5).toList()
      println("${sols.distinct().size}/${sols.size}")
      println("Solutions found: ${sols.joinToString(", ")}")

      sols.forEach { assertTrue(it.hasBalancedBrackets()) }
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest.testDyck2Solver"
*/
  @Test
  fun testDyck2Solver() {
    """S -> ( ) | [ ] | ( S ) | [ S ] | S S""".parseCFG(validate = true).let { CFG: CFG ->
      println("CFL parsed: ${CFG.prettyPrint()}")
      val sols = "_ _ _ _ _ _ _ _ ".tokenizeByWhitespace().solve(CFG).take(5).toList()
      println("${sols.distinct().size}/${sols.size}")

      println("Solutions found: ${sols.joinToString(", ")}")

      sols.forEach { assertTrue(it.hasBalancedBrackets()) }
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest.benchmarkNaiveSearch"
*/
  @Test
  fun benchmarkNaiveSearch() {
    """S -> ( ) | [ ] | ( S ) | [ S ] | S S""".parseCFG().let { cfg ->
      println("Total Holes, Instances Checked, Solutions Found")
      for (len in 2..8 step 2) {
        val template = List(len) { "_" }
        fun now() = Clock.System.now().toEpochMilliseconds()
        val startTime = now()
        var totalChecked = 0
        val sols = template
          .genCandidates(cfg, cfg.terminals)
          .onEach { totalChecked++ }
          .filter { it.matches(cfg) }.distinct()
          .takeWhile { now() - startTime < 20000 }.toList()

        println("$len".padEnd(11) + ", $totalChecked".padEnd(19) + ", ${sols.size}")
      }
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest.testDyck2Language"
*/
  @Test
  fun testDyck2Language() {
    """S -> ( ) | [ ] | ( S ) | [ S ] | S S""".let { cfg ->
      println("Grammar: $this")
      assertTrue("( ) [ ( ) ( ) ] ( ) ".matches(cfg))
      assertFalse("( [ ( ) ( ) ] ( ) ".matches(cfg))
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest.testDyck3Language"
*/
  @Test
  fun testDyck3Language() {
  """S -> ( ) | [ ] | { } | ( S ) | [ S ] | { S } | S S""".let { cfg ->
      // TODO: Fix under approximation?
      assertTrue("{ ( ) [ ( ) { } ( ) ] ( ) } ".matches(cfg))
      assertFalse("{ ( ) [ ( ) { ( ) ] ( ) } ".matches(cfg))
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest.testDyck3Solver"
*/
  @Test
  fun testDyck3Solver() {
    """S -> ( ) | [ ] | ( S ) | [ S ] | { S } | S S""".parseCFG().let { cfg ->
      val sols = "( _ _ _ _ ( ) _ _ _ _ )".tokenizeByWhitespace().solve(cfg).take(5).toList()
      println("Solution found: ${sols.joinToString(", ")}")

      sols.forEach { assertTrue(it.hasBalancedBrackets()) }
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest.testNormalization"
*/
  @Test
  fun testNormalization() {
    """
      S -> a X b X
      X -> a Y | b Y
      Y -> X | c
    """.parseCFG().let { cfg ->
      println(cfg)
      cfg.forEach { (_, b) -> assertContains(1..2, b.size) }
      cfg.nonterminalProductions.flatMap { it.π2 }.forEach { assertContains(cfg.nonterminals, it) }
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest.testEscapeChars"
*/
  @Test
  fun testEscapeChars() {
      """
        S -> a `->` b `|` c
      """.parseCFG().let { cfg ->
        println(cfg.prettyPrint())
        assertTrue("a -> b | c".matches(cfg))
      }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest.testDropUnitProds"
*/
  @Test
  fun testDropUnitProds() {
    "S -> c | d".parseCFG()
    """
      S -> A
      A -> B
      B -> C
      B -> D
      C -> c
      D -> d
    """.parseCFG().let { cfg ->
      println(cfg.prettyPrint())
      assertTrue("B" !in cfg.nonterminals)
      assertTrue("A" !in cfg.nonterminals)
    }

    """
      S -> C | D
      C -> c
      D -> d
    """.parseCFG()
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest.testNotCopy"
*/
  @Test
  fun testNotCopy() {
// https://cs.stackexchange.com/a/19155/74308
    """
      START -> A | B | A B | B A
      A -> a | a A a | a A b | b A b | b A a
      B -> b | a B a | a B b | b B b | b B a
    """.parseCFG().let { cfg ->
      assertTrue("a a a a a a b b ".matches(cfg))
      assertFalse("a a a a a a ".matches(cfg))
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest.testEpsilonProd"
*/
  @Test
  fun testEpsilonProd() {
    """
      P -> W 1 1
      W -> ε | w
    """.parseCFG().let { cfg ->
      assertTrue("w 1 1".matches(cfg))
      assertTrue("1 1".matches(cfg))
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest.testLocalCanonicity"
*/
  @Test
  fun testLocalCanonicity() {
    val cfg1 = """P -> ( P ) | P P | ε""".parseCFG().also { println(it.prettyPrint()) }
    val cfg2 = """P -> ( P ) | ( P ) | P P | ε""".parseCFG().also { println(it.prettyPrint()) }
    assertEquals(cfg1, cfg2)

    val cfg3 = """P -> ( ) | P P | ( P )""".parseCFG().also { println(it.prettyPrint()) }
    val cfg4 = """P -> P P | ( P ) | ( )""".parseCFG().also { println(it.prettyPrint()) }
    assertEquals(cfg3, cfg4)
  }


/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest.testOCaml"
*/
  @Test
  fun testOCaml() {
    val expr = "1 + 2 + 3"
    val tree = ocamlCFG.parse(expr)!!
    println(tree.prettyPrint())
    val leaves = tree.contents()
    assertEquals(expr, leaves)

    val holExpr = "_ _ _ _"

    measureTime {
      val solutions = ocamlCFG.solve(holExpr, levMetric("( false curry )"))
      println("Found: ${solutions.size} unique solutions")
      solutions.forEach { println(it); assertTrue("$it was invalid!") { ocamlCFG.isValid(it) } }
    }.also { println("Finished in ${it.inWholeMilliseconds}ms.") }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest.testSeqValiant"
*/
  @Test
  fun testSeqValiant() {
    var clock = TimeSource.Monotonic.markNow()
    val detSols = seq2parsePythonCFG.noEpsilonOrNonterminalStubs
        .enumSeq(List(20) {"_"}.joinToString(" "))
        .take(10_000).sortedBy { it.length }.toList()

    detSols.forEach { assertTrue("\"$it\" was invalid!") { it in seq2parsePythonCFG.language } }

    var elapsed = clock.elapsedNow().inWholeMilliseconds
    println("Found ${detSols.size} determinstic solutions in ${elapsed}ms or ~${detSols.size / (elapsed/1000.0)}/s, all were valid!")

    clock = TimeSource.Monotonic.markNow()
    val randSols = seq2parsePythonCFG.noEpsilonOrNonterminalStubs
      .sliceSample(20).take(10_000).toList().distinct()
      .onEach { assertTrue("\"$it\" was invalid!") { it in seq2parsePythonCFG.language } }

    // 10k in ~22094ms
    elapsed = clock.elapsedNow().inWholeMilliseconds
    println("Found ${randSols.size} random solutions in ${elapsed}ms or ~${randSols.size / (elapsed/1000.0)}/s, all were valid!")
  }

  companion object {
    val ocamlCFG = """
      S -> X
      X -> A | V | ( X , X ) | X X | ( X )
      A -> FUN | F | LI | M | L
      FUN -> fun V `->` X
      F -> if X then X else X
      M -> match V with Branch
      Branch -> `|` X `->` X | Branch Branch
      L -> let V = X
      L -> let rec V = X
      LI -> L in X

      V -> Vexp | ( Vexp ) | List | Vexp Vexp
      Vexp -> Vname | FunName | Vexp VO Vexp | B
      Vexp -> ( Vname , Vname ) | Vexp Vexp | I
      List -> [] | V :: V
      Vname -> a | b | c | d | e | f | g | h | i
      Vname -> j | k | l | m | n | o | p | q | r
      Vname -> s | t | u | v | w | x | y | z
      FunName -> foldright | map | filter
      FunName -> curry | uncurry | ( VO )
      VO ->  + | - | * | / | >
      VO -> = | < | `||` | `&&`
      I -> 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9
      B ->  true | false
    """.trimIndent().parseCFG().noNonterminalStubs

    val seq2parsePythonCFG: CFG = """
START -> Stmts_Or_Newlines Endmarker
Stmts_Or_Newlines -> Stmt_Or_Newline | Stmt_Or_Newline Stmts_Or_Newlines
Stmt_Or_Newline -> Stmt | Newline

Endmarker -> 
Newline -> NEWLINE

Async_Funcdef -> Async_Keyword Funcdef
Funcdef -> Def_Keyword Simple_Name Parameters Colon Suite | Def_Keyword Simple_Name Parameters Arrow Test Colon Suite

Parameters -> Open_Paren Close_Paren | Open_Paren Typedargslist Close_Paren
Typedargslist -> Many_Tfpdef | Many_Tfpdef Comma | Many_Tfpdef Comma Star_Double_Star_Typed | Many_Tfpdef Comma Double_Star_Tfpdef | Star_Double_Star_Typed | Double_Star_Tfpdef
Star_Double_Star_Typed -> Star_Tfpdef | Star_Tfpdef Comma | Star_Tfpdef Comma Double_Star_Tfpdef
Star_Tfpdef_Comma -> Comma Tfpdef_Default | Comma Tfpdef_Default Star_Tfpdef_Comma
Star_Tfpdef -> Star_Op | Star_Op Star_Tfpdef_Comma | Star_Op Tfpdef | Star_Op Tfpdef Star_Tfpdef_Comma
Double_Star_Tfpdef -> Double_Star_Op Tfpdef | Double_Star_Op Tfpdef Comma
Many_Tfpdef -> Tfpdef_Default | Tfpdef_Default Comma Many_Tfpdef
Tfpdef_Default -> Tfpdef | Tfpdef Assign_Op Test

Varargslist -> Many_Vfpdef | Many_Vfpdef Comma | Many_Vfpdef Comma Star_Double_Star | Many_Vfpdef Comma Double_Star_Vfpdef | Star_Double_Star | Double_Star_Vfpdef
Star_Double_Star -> Star_Vfpdef | Star_Vfpdef Comma | Star_Vfpdef Comma Double_Star_Vfpdef
Star_Vfpdef_Comma -> Comma Vfpdef_Default | Comma Vfpdef_Default Star_Vfpdef_Comma
Star_Vfpdef -> Star_Op | Star_Op Star_Vfpdef_Comma | Star_Op Vfpdef | Star_Op Vfpdef Star_Vfpdef_Comma
Double_Star_Vfpdef -> Double_Star_Op Vfpdef | Double_Star_Op Vfpdef Comma
Many_Vfpdef -> Vfpdef_Default | Vfpdef_Default Comma Many_Vfpdef
Vfpdef_Default -> Vfpdef | Vfpdef Assign_Op Test

Tfpdef -> Vfpdef | Vfpdef Colon Test
Vfpdef -> NAME
Assign_Op -> =
Star_Op -> *
Double_Star_Op -> **
Arrow -> arrow

Stmt -> Simple_Stmt | Compound_Stmt
Simple_Stmt -> Small_Stmts Newline | Small_Stmts Semicolon Newline
Small_Stmts -> Small_Stmt | Small_Stmt Semicolon Small_Stmts
Small_Stmt -> Expr_Stmt | Del_Stmt | Pass_Stmt | Flow_Stmt | Import_Stmt | Global_Stmt | Nonlocal_Stmt | Assert_Stmt
Expr_Stmt -> Testlist_Star_Expr Annotated_Assign | Testlist_Star_Expr Aug_Assign Yield_Expr | Testlist_Star_Expr Aug_Assign Testlist_Endcomma | Testlist_Star_Exprs_Assign
Annotated_Assign -> Colon Test | Colon Test Assign_Op Test
Test_Or_Star_Expr -> Test | Star_Expr
Test_Or_Star_Exprs -> Test_Or_Star_Expr | Test_Or_Star_Expr Comma Test_Or_Star_Exprs
Testlist_Star_Expr -> Test_Or_Star_Exprs | Test_Or_Star_Exprs Comma
Yield_Testlist_Star_Assign_Exprs -> Assign_Op Yield_Expr | Assign_Op Testlist_Star_Expr | Assign_Op Yield_Expr Yield_Testlist_Star_Assign_Exprs | Assign_Op Testlist_Star_Expr Yield_Testlist_Star_Assign_Exprs
Testlist_Star_Exprs_Assign -> Testlist_Star_Expr | Testlist_Star_Expr Yield_Testlist_Star_Assign_Exprs
Del_Stmt -> Del_Keyword Exprlist
Flow_Stmt -> Break_Stmt | Continue_Stmt | Return_Stmt | Raise_Stmt | Yield_Stmt
Return_Stmt -> Return_Keyword | Return_Keyword Testlist_Endcomma
Yield_Stmt -> Yield_Expr
Raise_Stmt -> Raise_Keyword | Raise_Keyword Test | Raise_Keyword Test From_Keyword Test
Import_Stmt -> Import_name | Import_From
Import_name -> Import_Keyword Dotted_As_Names
Dots_Plus -> Dot_Or_Dots | Dot_Or_Dots Dots_Plus
Start_Dotted_Name -> Dotted_Name | Dots_Plus Dotted_Name
Import_From_Froms -> From_Keyword Start_Dotted_Name | From_Keyword Dots_Plus
Import_From_Imports -> Import_Keyword Star_Op | Import_Keyword Open_Paren Import_As_Names_Endcomma Close_Paren | Import_Keyword Import_As_Names_Endcomma
Import_From -> Import_From_Froms Import_From_Imports
Import_As_Name -> Simple_Name | Simple_Name As_Keyword Simple_Name
Dotted_As_Name -> Dotted_Name | Dotted_Name As_Keyword Simple_Name
Import_As_Names -> Import_As_Name | Import_As_Name Comma Import_As_Names_Endcomma
Import_As_Names_Endcomma -> Import_As_Names | Import_As_Name Comma
Dotted_As_Names -> Dotted_As_Name | Dotted_As_Name Comma Dotted_As_Names
Dotted_Name -> Simple_Name | Simple_Name Dot Dotted_Name
Many_Names -> Simple_Name | Simple_Name Comma Many_Names
Global_Stmt -> Global_Keyword Many_Names
Nonlocal_Stmt -> Nonlocal_Keyword Many_Names
Assert_Stmt -> Assert_Keyword Test | Assert_Keyword Test Comma Test

Aug_Assign -> += | -= | *= | @= | /= | %= | &= | |= | ^= | <<= | >>= | **= | //=
Del_Keyword -> del
Pass_Stmt -> pass
Break_Stmt -> break
Continue_Stmt -> continue
Return_Keyword -> return
Yield_Keyword -> yield
Raise_Keyword -> raise
From_Keyword -> from
Import_Keyword -> import
Dot_Or_Dots -> . | ...
As_Keyword -> as
Global_Keyword -> global
Nonlocal_Keyword -> nonlocal
Assert_Keyword -> assert
Def_Keyword -> def
Class_Keyword -> class

Compound_Stmt -> If_Stmt | While_Stmt | For_Stmt | Try_Stmt | With_Stmt | Funcdef | Classdef | Async_Stmt
Async_Stmt -> Async_Keyword Funcdef | Async_Keyword With_Stmt | Async_Keyword For_Stmt
Elif_Stmt -> Elif_Keyword Test Colon Suite | Elif_Keyword Test Colon Suite Elif_Stmt
Else_Stmt -> Else_Keyword Colon Suite
If_Stmt -> If_Keyword Test Colon Suite | If_Keyword Test Colon Suite Else_Stmt | If_Keyword Test Colon Suite Elif_Stmt | If_Keyword Test Colon Suite Elif_Stmt Else_Stmt
While_Stmt -> While_Keyword Test Colon Suite | While_Keyword Test Colon Suite Else_Stmt
For_Stmt -> For_Keyword Exprlist In_Keyword Testlist_Endcomma Colon Suite | For_Keyword Exprlist In_Keyword Testlist_Endcomma Colon Suite Else_Stmt
Finally_Stmt -> Finally_Keyword Colon Suite
Except_Stmt -> Except_Clause Colon Suite | Except_Clause Colon Suite Except_Stmt
Try_Stmt -> Try_Keyword Colon Suite Finally_Stmt | Try_Keyword Colon Suite Except_Stmt | Try_Keyword Colon Suite Except_Stmt Else_Stmt | Try_Keyword Colon Suite Except_Stmt Finally_Stmt | Try_Keyword Colon Suite Except_Stmt Else_Stmt Finally_Stmt
With_Stmt -> With_Keyword With_Items Colon Suite
With_Items -> With_Item | With_Item Comma With_Items
With_Item -> Test | Test As_Keyword Expr
Except_Clause -> Except_Keyword | Except_Keyword Test | Except_Keyword Test As_Keyword Simple_Name
Suite -> Simple_Stmt | Newline Indent Stmts_Or_Newlines Dedent

Async_Keyword -> async
Await_Keyword -> await
If_Keyword -> if
Elif_Keyword -> elif
Else_Keyword -> else
While_Keyword -> while
For_Keyword -> for
In_Keyword -> in
Finally_Keyword -> finally
Except_Keyword -> except
Try_Keyword -> try
With_Keyword -> with
Lambda_Keyword -> lambda
Indent -> INDENT
Dedent -> DEDENT
Colon -> :
Semicolon -> ;
Comma -> ,
Dot -> .
Open_Paren -> (
Close_Paren -> )
Open_Sq_Bracket -> [
Close_Sq_Bracket -> ]
Open_Curl_Bracket -> {
Close_Curl_Bracket -> }

Test -> Or_Test | Or_Test If_Keyword Or_Test Else_Keyword Test | Lambdef
Test_Nocond -> Or_Test | Lambdef_Nocond
Lambdef -> Lambda_Keyword Colon Test | Lambda_Keyword Varargslist Colon Test
Lambdef_Nocond -> Lambda_Keyword Colon Test_Nocond | Lambda_Keyword Varargslist Colon Test_Nocond
Or_Test -> And_Test | Or_Test Or_Bool_Op And_Test
And_Test -> Not_Test | And_Test And_Bool_Op Not_Test
Not_Test -> Not_Bool_Op Not_Test | Comparison
Comparison -> Expr | Comparison Comp_Op Expr
Star_Expr -> Star_Op Expr
Expr -> Xor_Expr | Expr Or_Op Xor_Expr
Xor_Expr -> And_Expr | Xor_Expr Xor_Op And_Expr
And_Expr -> Shift_Expr | And_Expr And_Op Shift_Expr
Shift_Expr -> Arith_Expr | Shift_Expr Shift_Op Arith_Expr
Arith_Expr -> Term | Arith_Expr Arith_Op Term
Term -> Factor | Term MulDiv_Op Factor
Factor -> Unary_Op Factor | Power
Power -> Atom_Expr | Atom_Expr Double_Star_Op Factor
Many_Trailers -> Trailer | Trailer Many_Trailers
Atom_Expr -> Atom | Atom Many_Trailers | Await_Keyword Atom | Await_Keyword Atom Many_Trailers
Atom -> Open_Paren Close_Paren | Open_Sq_Bracket Close_Sq_Bracket | Open_Curl_Bracket Close_Curl_Bracket | Open_Paren Yield_Expr Close_Paren | Open_Paren Testlist_Comp Close_Paren | Open_Sq_Bracket Testlist_Comp Close_Sq_Bracket | Open_Curl_Bracket Dict_Or_Set_Maker Close_Curl_Bracket | Literals
Testlist_Comp -> Test_Or_Star_Expr Comp_For | Testlist_Star_Expr
Trailer -> Open_Paren Close_Paren | Open_Paren Arglist Close_Paren | Open_Sq_Bracket Subscriptlist Close_Sq_Bracket | Dot Simple_Name
Subscripts -> Subscript | Subscript Comma Subscripts
Subscriptlist -> Subscripts | Subscripts Comma
Subscript -> Test | Colon | Test Colon | Colon Test | Colon Sliceop | Test Colon Test | Colon Test Sliceop | Test Colon Sliceop | Test Colon Test Sliceop
Sliceop -> Colon | Colon Test
Generic_Expr -> Expr | Star_Expr
Generic_Exprs -> Generic_Expr | Generic_Expr Comma Generic_Exprs
Exprlist -> Generic_Exprs | Generic_Exprs Comma
Testlist -> Test | Test Comma Testlist_Endcomma
Testlist_Endcomma -> Testlist | Test Comma
KeyVal_Or_Unpack -> Test Colon Test | Double_Star_Op Expr
Many_KeyVals_Or_Unpacks -> KeyVal_Or_Unpack | KeyVal_Or_Unpack Comma Many_KeyVals_Or_Unpacks
KeyVal_Or_Unpack_Setter -> KeyVal_Or_Unpack Comp_For | Many_KeyVals_Or_Unpacks | Many_KeyVals_Or_Unpacks Comma
Test_Or_Star_Expr_Setter -> Test_Or_Star_Expr Comp_For | Testlist_Star_Expr
Dict_Or_Set_Maker -> KeyVal_Or_Unpack_Setter | Test_Or_Star_Expr_Setter

Or_Bool_Op -> or
And_Bool_Op -> and
Not_Bool_Op -> not
Comp_Op -> < | > | == | >= | <= | <> | != | in | not_in | is | is_not
Or_Op -> OR
Xor_Op -> ^
And_Op -> &
Shift_Op -> << | >>
Arith_Op -> + | -
MulDiv_Op -> * | @ | / | % | //
Unary_Op -> + | - | ~
Literals -> NAME | NUMBER | STRING | ... | None | True | False
Simple_Name -> NAME

Classdef -> Class_Keyword Simple_Name Colon Suite | Class_Keyword Simple_Name Open_Paren Close_Paren Colon Suite | Class_Keyword Simple_Name Open_Paren Arglist Close_Paren Colon Suite

Arglist -> Arguments | Arguments Comma
Arguments -> Argument | Argument Comma Arguments
Argument -> Test | Test Comp_For | Test Assign_Op Test | Double_Star_Op Test | Star_Op Test

Comp_Iter -> Comp_For | Comp_If
Comp_For -> For_Keyword Exprlist In_Keyword Or_Test | For_Keyword Exprlist In_Keyword Or_Test Comp_Iter | Async_Keyword For_Keyword Exprlist In_Keyword Or_Test | Async_Keyword For_Keyword Exprlist In_Keyword Or_Test Comp_Iter
Comp_If -> If_Keyword Test_Nocond | If_Keyword Test_Nocond Comp_Iter

Yield_Expr -> Yield_Keyword | Yield_Keyword Yield_Arg
Yield_Arg -> From_Keyword Test | Testlist_Endcomma 
""".parseCFG().noNonterminalStubs
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest.testPythonRepairs"
*/
  @Test
  fun testPythonRepairs() {
    val refStr = "NAME = ( NAME"
    val refLst = refStr.tokenizeByWhitespace()
    val template = List(refLst.size + 3) { "_" }.joinToString(" ")
    println("Solving: $template")
    measureTime {
      seq2parsePythonCFG.enumSeq(template)
        .map { it to levenshtein(it, refStr) }
        .filter { it.second < 4 }.distinct().take(100)
        .sortedWith(compareBy({ it.second }, { it.first.length }))
        .onEach { println("Δ=${it.second}: ${it.first}") }
//        .onEach { println("Δ=${levenshtein(it, refStr)}: $it") }
        .toList()
        .also { println("Found ${it.size} solutions!") }
    }.also { println("Finished in ${it.inWholeMilliseconds}ms.") }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest.testParametricLanguage"
*/
  @Test
  fun testParametricLanguage() {
    val cfg = """
      START -> E<X>
      op -> + | *
      E<X> -> E<X> op E<X>
      X -> Int | Bool | Float
      E<Int> -> 0 | 1 | 2 | 3
      E<Bool> -> T | F
      E<Float> -> E<Int> . E<Int>

      Upcasting (e.g., 1.0 + 2 ⊢ E<Float>):
      E<Float> -> E<Int> op E<Float> | E<Float> op E<Int>
    """.trimIndent().parseCFG()

    cfg.parse("<E<Float>> + <E<Int>> + <E<Float>>").also { println(it!!.prettyPrint()) }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest.testUTMRepresentationEquivalence"
*/
  @ExperimentalTime
  @Test
  fun testUTMRepresentationEquivalence() {
    with("""P -> ( P ) | P P | ε""".parseCFG()) {
      val str = "( ( ) ( ) ) ( ) ( ( ( ) ) ( ) ) ( ( ( ) ) ) ( ) ( ) ( )".tokenizeByWhitespace()
      val slowTransitionFP =  measureTimedValue {
        initialMatrix(str).seekFixpoint(succ={it + it * it})
      }.also { println("Slow transition: ${it.duration.inWholeMilliseconds}") }.value
      val fastTransitionFP = measureTimedValue {
        initialUTMatrix(str).seekFixpoint().toFullMatrix()
      }.also { println("Fast transition: ${it.duration.inWholeMilliseconds}ms") }.value

      assertEquals(slowTransitionFP, fastTransitionFP)
    }
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest.testLevenshteinAutomata"
*/
  @Test
  fun testLevenshteinAutomata() {
    // Levenshtein automata for the word "flees" with d=1 and Σ={x,f,l,e,s}
    val cfg = """
       START -> d:4:0 | d:4:1 | d:5:0 | d:5:1
       * -> x | f | l | e | s
       
       d:1:0 -> f
       d:2:0 -> d:1:0 l
       d:3:0 -> d:2:0 e
       d:4:0 -> d:3:0 e
       d:5:0 -> d:4:0 s
       
       d:0:1 -> *
       d:1:1 -> d:0:1 f | d:1:0 * | *
       d:2:1 -> d:1:1 l | d:1:0 * | d:2:0 * | l
       d:3:1 -> d:2:1 e | d:2:0 * | d:3:0 * | d:1:0 e
       d:4:1 -> d:3:1 e | d:3:0 * | d:4:0 * | d:2:0 e
       d:5:1 -> d:4:1 s | d:4:0 * | d:5:0 * | d:3:0 s
    """.trimIndent().parseCFG()

    assertNotNull(cfg.parse("f l e e s"))
    assertNotNull(cfg.parse("x l e e s"))
    assertNotNull(cfg.parse("f x l e e s"))
    assertNotNull(cfg.parse("f l e e s x"))
    assertNotNull(cfg.parse("f l e e s x"))
    assertNull(cfg.parse("f e e l s"))
  }

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest.testLevenshteinGrammar"
*/
  @Test
  fun testLevenshteinGrammar() {
    val cfg = constructLevenshteinCFG("flees".map { it.toString() }, 2, "flesx".map { it.toString() }.toSet())
      .also { println(it) }
      .parseCFG()
    assertNotNull(cfg.parse("f l e e s"))
    assertNotNull(cfg.parse("x l e e s"))
    assertNotNull(cfg.parse("f x l e e s"))
    assertNotNull(cfg.parse("f l e e s x"))
    assertNotNull(cfg.parse("f l e e s x"))
    assertNotNull(cfg.parse("f e e l s"))
    assertNull(cfg.parse("f e e l s s")?.prettyPrint())
  }

  fun randomBitVector(size: Int) =
    (0 until size).map { Random.nextBoolean() }.toBooleanArray()

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.parsing.SetValiantTest.benchmarkBitwiseJoin"
*/
  @Test
  fun benchmarkBitwiseJoin() {
    val size = ocamlCFG.nonterminals.size
    val vidx = ocamlCFG.vindex
    val pairs =
      (0..10_000_000).map { randomBitVector(size) to randomBitVector(size) }

    measureTime {
      pairs.map { (a, b) -> fastJoin(vidx, a, b) }
        .reduce { a, b -> union(a, b) }
    }.also { println("Merged a 10^6 bitvecs in ${it.inWholeMilliseconds}ms.") } // Should be ~5000ms
  }
}