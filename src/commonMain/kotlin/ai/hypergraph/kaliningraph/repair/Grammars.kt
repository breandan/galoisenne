package ai.hypergraph.kaliningraph.repair

import ai.hypergraph.kaliningraph.parsing.CFG
import ai.hypergraph.kaliningraph.parsing.deserializeUVW
import ai.hypergraph.kaliningraph.parsing.freeze
import ai.hypergraph.kaliningraph.parsing.noEpsilonOrNonterminalStubs
import ai.hypergraph.kaliningraph.parsing.noNonterminalStubs
import ai.hypergraph.kaliningraph.parsing.parseCFG

val s2pCFGStr = """
START -> Stmts_Or_Newlines
Stmts_Or_Newlines -> Stmt_Or_Newline | Stmt_Or_Newline Stmts_Or_Newlines
Stmt_Or_Newline -> Stmt | Newline

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
"""

val vanillaS2PCFG by lazy { s2pCFGStr.parseCFG().noEpsilonOrNonterminalStubs.freeze() } // Without Epsilon
val vanillaS2PCFGWE by lazy { s2pCFGStr.parseCFG().noNonterminalStubs.freeze() } // With Epsilon

val toyPython: CFG by lazy {
  """
  START -> STMT | BOS STMT EOS
  STMT -> NAME = EXPR | INV
  EXPR -> NAME | NAME + EXPR | ( EXPR ) | INV
  INV -> NAME ( ARGS )
  ARGS -> EXPR | EXPR , ARGS
  """.parseCFG().noEpsilonOrNonterminalStubs
}

// Only popular prods as filtered by PCFG occurrences > 10k
val pythonStatementCNF: CFG by lazy {
  """
    Newline -> NEWLINE
    Parameters -> Open_Paren Close_Paren
    Star_Double_Star_Typed -> Star_Tfpdef Comma
    Star_Tfpdef -> Star_Op Tfpdef
    Double_Star_Tfpdef -> Double_Star_Op Tfpdef
    Varargslist -> Many_Vfpdef Comma
    Vfpdef -> NAME
    Assign_Op -> =
    Star_Op -> *
    Double_Star_Op -> **
    Arrow -> arrow
    Yield_Testlist_Star_Assign_Exprs -> Assign_Op Testlist_Star_Expr
    Dots_Plus -> Dot_Or_Dots Dots_Plus
    Start_Dotted_Name -> Dots_Plus Dotted_Name
    Import_From_Froms -> From_Keyword Start_Dotted_Name
    Import_From_Froms -> From_Keyword Dots_Plus
    Import_From_Imports -> Import_Keyword Star_Op
    Import_From_Imports -> Import_Keyword Import_As_Names_Endcomma
    Aug_Assign -> +=
    Aug_Assign -> -=
    Aug_Assign -> *=
    Aug_Assign -> @=
    Aug_Assign -> /=
    Aug_Assign -> %=
    Aug_Assign -> &=
    Aug_Assign -> |=
    Aug_Assign -> ^=
    Aug_Assign -> <<=
    Aug_Assign -> >>=
    Aug_Assign -> **=
    Aug_Assign -> //=
    Del_Keyword -> del
    Return_Keyword -> return
    Yield_Keyword -> yield
    Raise_Keyword -> raise
    From_Keyword -> from
    Import_Keyword -> import
    Dot_Or_Dots -> .
    Dot_Or_Dots -> ...
    As_Keyword -> as
    Global_Keyword -> global
    Assert_Keyword -> assert
    Def_Keyword -> def
    Class_Keyword -> class
    Except_Clause -> Except_Keyword Test
    Async_Keyword -> async
    Await_Keyword -> await
    If_Keyword -> if
    Elif_Keyword -> elif
    Else_Keyword -> else
    While_Keyword -> while
    For_Keyword -> for
    In_Keyword -> in
    Except_Keyword -> except
    Try_Keyword -> try
    With_Keyword -> with
    Lambda_Keyword -> lambda
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
    Many_Trailers -> Trailer Many_Trailers
    Atom_Expr -> Await_Keyword Atom
    Testlist_Comp -> Test_Or_Star_Expr Comp_For
    Trailer -> Open_Paren Close_Paren
    Trailer -> Dot Simple_Name
    Or_Bool_Op -> or
    And_Bool_Op -> and
    Not_Bool_Op -> not
    Comp_Op -> <
    Comp_Op -> >
    Comp_Op -> ==
    Comp_Op -> >=
    Comp_Op -> <=
    Comp_Op -> <>
    Comp_Op -> !=
    Comp_Op -> in
    Comp_Op -> not_in
    Comp_Op -> is
    Comp_Op -> is_not
    Or_Op -> OR
    Arith_Op -> +
    Arith_Op -> -
    MulDiv_Op -> *
    MulDiv_Op -> @
    MulDiv_Op -> /
    MulDiv_Op -> %
    MulDiv_Op -> //
    Unary_Op -> +
    Unary_Op -> -
    Unary_Op -> ~
    Simple_Name -> NAME
    Arglist -> Arguments Comma
    Argument -> Test Comp_For
    Argument -> Star_Op Test
    Typedargslist -> Star_Tfpdef Comma
    Varargslist -> Star_Vfpdef Comma
    Tfpdef -> NAME
    Many_Vfpdef -> NAME
    Varargslist -> NAME
    Tfpdef_Default -> NAME
    Many_Tfpdef -> NAME
    Typedargslist -> NAME
    Star_Tfpdef -> *
    Star_Vfpdef -> *
    Star_Double_Star_Typed -> *
    Typedargslist -> *
    Varargslist -> *
    Suite -> Small_Stmts Newline
    START -> Small_Stmts Newline
    Testlist_Comp -> Test_Or_Star_Exprs Comma
    Small_Stmts -> Testlist_Star_Expr Yield_Testlist_Star_Assign_Exprs
    Small_Stmts -> Del_Keyword Exprlist
    Small_Stmts -> Return_Keyword Testlist_Endcomma
    Small_Stmts -> Raise_Keyword Test
    Small_Stmts -> Import_Keyword Dotted_As_Names
    Small_Stmts -> Import_From_Froms Import_From_Imports
    Small_Stmts -> Global_Keyword Many_Names
    Small_Stmts -> Assert_Keyword Test
    Small_Stmts -> pass
    Small_Stmts -> break
    Small_Stmts -> continue
    Small_Stmts -> return
    Yield_Expr -> yield
    Small_Stmts -> raise
    Dots_Plus -> .
    Dots_Plus -> ...
    START -> Async_Keyword Funcdef
    Except_Clause -> except
    Subscript -> :
    Test -> Not_Bool_Op Not_Test
    Subscript -> Not_Bool_Op Not_Test
    With_Items -> Not_Bool_Op Not_Test
    Small_Stmts -> Star_Op Expr
    Generic_Expr -> Unary_Op Factor
    Test -> Unary_Op Factor
    Test_Nocond -> Unary_Op Factor
    With_Items -> Unary_Op Factor
    Factor -> Atom Many_Trailers
    Factor -> Await_Keyword Atom
    Term -> Atom Many_Trailers
    Term -> Await_Keyword Atom
    Arith_Expr -> Atom Many_Trailers
    Arith_Expr -> Await_Keyword Atom
    Xor_Expr -> Await_Keyword Atom
    Expr -> Atom Many_Trailers
    Expr -> Await_Keyword Atom
    Comparison -> Atom Many_Trailers
    Comparison -> Await_Keyword Atom
    Generic_Expr -> Await_Keyword Atom
    Not_Test -> Atom Many_Trailers
    Not_Test -> Await_Keyword Atom
    And_Test -> Atom Many_Trailers
    And_Test -> Await_Keyword Atom
    Or_Test -> Atom Many_Trailers
    Or_Test -> Await_Keyword Atom
    Test -> Atom Many_Trailers
    Test_Nocond -> Await_Keyword Atom
    Test_Or_Star_Expr -> Atom Many_Trailers
    Test_Or_Star_Expr -> Await_Keyword Atom
    Subscript -> Await_Keyword Atom
    Argument -> Atom Many_Trailers
    Argument -> Await_Keyword Atom
    Test_Or_Star_Exprs -> Atom Many_Trailers
    Test_Or_Star_Exprs -> Await_Keyword Atom
    Testlist_Star_Expr -> Atom Many_Trailers
    Testlist_Comp -> Atom Many_Trailers
    Testlist_Comp -> Await_Keyword Atom
    Small_Stmts -> Atom Many_Trailers
    With_Items -> Atom Many_Trailers
    With_Items -> Await_Keyword Atom
    Atom_Expr -> Open_Paren Close_Paren
    Atom_Expr -> Open_Sq_Bracket Close_Sq_Bracket
    Atom_Expr -> Open_Curl_Bracket Close_Curl_Bracket
    Factor -> Open_Curl_Bracket Close_Curl_Bracket
    Term -> Open_Curl_Bracket Close_Curl_Bracket
    Arith_Expr -> Open_Paren Close_Paren
    Arith_Expr -> Open_Curl_Bracket Close_Curl_Bracket
    Xor_Expr -> Open_Paren Close_Paren
    Xor_Expr -> Open_Sq_Bracket Close_Sq_Bracket
    Xor_Expr -> Open_Curl_Bracket Close_Curl_Bracket
    Comparison -> Open_Paren Close_Paren
    Generic_Expr -> Open_Paren Close_Paren
    Generic_Expr -> Open_Sq_Bracket Close_Sq_Bracket
    Generic_Expr -> Open_Curl_Bracket Close_Curl_Bracket
    Test -> Open_Sq_Bracket Close_Sq_Bracket
    Test_Nocond -> Open_Paren Close_Paren
    Test_Nocond -> Open_Sq_Bracket Close_Sq_Bracket
    Test_Nocond -> Open_Curl_Bracket Close_Curl_Bracket
    Subscript -> Open_Paren Close_Paren
    Subscript -> Open_Curl_Bracket Close_Curl_Bracket
    Testlist_Star_Expr -> Open_Sq_Bracket Close_Sq_Bracket
    Testlist_Star_Expr -> Open_Curl_Bracket Close_Curl_Bracket
    With_Items -> Open_Paren Close_Paren
    With_Items -> Open_Sq_Bracket Close_Sq_Bracket
    With_Items -> Open_Curl_Bracket Close_Curl_Bracket
    Many_Trailers -> Open_Paren Close_Paren
    Many_Trailers -> Dot Simple_Name
    Subscripts -> :
    Subscripts -> Not_Bool_Op Not_Test
    Subscripts -> Await_Keyword Atom
    Subscripts -> Open_Paren Close_Paren
    Subscripts -> Open_Sq_Bracket Close_Sq_Bracket
    Subscripts -> Open_Curl_Bracket Close_Curl_Bracket
    Subscriptlist -> Test Colon
    Subscriptlist -> Colon Test
    Subscriptlist -> :
    Subscriptlist -> Unary_Op Factor
    Subscriptlist -> Atom Many_Trailers
    Subscriptlist -> Await_Keyword Atom
    Subscriptlist -> Open_Sq_Bracket Close_Sq_Bracket
    Subscriptlist -> Open_Curl_Bracket Close_Curl_Bracket
    Generic_Exprs -> Unary_Op Factor
    Generic_Exprs -> Await_Keyword Atom
    Generic_Exprs -> Open_Paren Close_Paren
    Generic_Exprs -> Open_Sq_Bracket Close_Sq_Bracket
    Generic_Exprs -> Open_Curl_Bracket Close_Curl_Bracket
    Exprlist -> Star_Op Expr
    Exprlist -> Unary_Op Factor
    Exprlist -> Await_Keyword Atom
    Exprlist -> Open_Paren Close_Paren
    Exprlist -> Open_Sq_Bracket Close_Sq_Bracket
    Exprlist -> Open_Curl_Bracket Close_Curl_Bracket
    Testlist_Endcomma -> Atom Many_Trailers
    Yield_Arg -> Test Comma
    Yield_Arg -> Await_Keyword Atom
    Dict_Or_Set_Maker -> Many_KeyVals_Or_Unpacks Comma
    Dict_Or_Set_Maker -> Double_Star_Op Expr
    Dict_Or_Set_Maker -> Not_Bool_Op Not_Test
    Dict_Or_Set_Maker -> Star_Op Expr
    Dict_Or_Set_Maker -> Unary_Op Factor
    Dict_Or_Set_Maker -> Await_Keyword Atom
    Dict_Or_Set_Maker -> Open_Paren Close_Paren
    Dict_Or_Set_Maker -> Open_Sq_Bracket Close_Sq_Bracket
    Dict_Or_Set_Maker -> Open_Curl_Bracket Close_Curl_Bracket
    Atom -> NAME
    Atom -> NUMBER
    Atom -> STRING
    Atom -> ...
    Atom -> None
    Atom -> True
    Atom -> False
    Atom_Expr -> NAME
    Atom_Expr -> NUMBER
    Atom_Expr -> STRING
    Atom_Expr -> ...
    Atom_Expr -> None
    Atom_Expr -> True
    Atom_Expr -> False
    Factor -> NAME
    Factor -> NUMBER
    Factor -> STRING
    Factor -> ...
    Factor -> None
    Factor -> True
    Factor -> False
    Term -> NAME
    Term -> NUMBER
    Term -> STRING
    Term -> ...
    Term -> None
    Term -> True
    Term -> False
    Arith_Expr -> NAME
    Arith_Expr -> NUMBER
    Arith_Expr -> STRING
    Arith_Expr -> ...
    Arith_Expr -> None
    Arith_Expr -> True
    Arith_Expr -> False
    Xor_Expr -> NAME
    Xor_Expr -> NUMBER
    Xor_Expr -> STRING
    Xor_Expr -> ...
    Xor_Expr -> None
    Xor_Expr -> True
    Xor_Expr -> False
    Expr -> NAME
    Expr -> NUMBER
    Expr -> STRING
    Expr -> ...
    Expr -> None
    Expr -> True
    Expr -> False
    Comparison -> NAME
    Comparison -> NUMBER
    Comparison -> STRING
    Comparison -> ...
    Comparison -> None
    Comparison -> True
    Comparison -> False
    Generic_Expr -> NAME
    Generic_Expr -> NUMBER
    Generic_Expr -> STRING
    Generic_Expr -> ...
    Generic_Expr -> None
    Generic_Expr -> True
    Generic_Expr -> False
    Not_Test -> NAME
    Not_Test -> NUMBER
    Not_Test -> STRING
    Not_Test -> ...
    Not_Test -> None
    Not_Test -> True
    Not_Test -> False
    And_Test -> NAME
    And_Test -> NUMBER
    And_Test -> STRING
    And_Test -> ...
    And_Test -> None
    And_Test -> True
    And_Test -> False
    Or_Test -> NAME
    Or_Test -> NUMBER
    Or_Test -> STRING
    Or_Test -> ...
    Or_Test -> None
    Or_Test -> True
    Or_Test -> False
    Test -> NAME
    Test -> NUMBER
    Test -> STRING
    Test -> ...
    Test -> None
    Test -> True
    Test -> False
    Test_Nocond -> NAME
    Test_Nocond -> NUMBER
    Test_Nocond -> STRING
    Test_Nocond -> ...
    Test_Nocond -> None
    Test_Nocond -> True
    Test_Nocond -> False
    Test_Or_Star_Expr -> NAME
    Test_Or_Star_Expr -> NUMBER
    Test_Or_Star_Expr -> STRING
    Test_Or_Star_Expr -> ...
    Test_Or_Star_Expr -> None
    Test_Or_Star_Expr -> True
    Test_Or_Star_Expr -> False
    Subscript -> NAME
    Subscript -> NUMBER
    Subscript -> STRING
    Subscript -> ...
    Subscript -> None
    Subscript -> True
    Subscript -> False
    Argument -> NAME
    Argument -> NUMBER
    Argument -> STRING
    Argument -> ...
    Argument -> None
    Argument -> True
    Argument -> False
    Test_Or_Star_Exprs -> NAME
    Test_Or_Star_Exprs -> NUMBER
    Test_Or_Star_Exprs -> STRING
    Test_Or_Star_Exprs -> ...
    Test_Or_Star_Exprs -> None
    Test_Or_Star_Exprs -> True
    Test_Or_Star_Exprs -> False
    Testlist_Star_Expr -> NAME
    Testlist_Star_Expr -> NUMBER
    Testlist_Star_Expr -> STRING
    Testlist_Star_Expr -> ...
    Testlist_Star_Expr -> None
    Testlist_Star_Expr -> True
    Testlist_Star_Expr -> False
    Testlist_Comp -> NAME
    Testlist_Comp -> NUMBER
    Testlist_Comp -> STRING
    Testlist_Comp -> ...
    Testlist_Comp -> None
    Testlist_Comp -> True
    Testlist_Comp -> False
    Small_Stmts -> NAME
    Small_Stmts -> NUMBER
    Small_Stmts -> STRING
    Small_Stmts -> ...
    Small_Stmts -> None
    Small_Stmts -> True
    Small_Stmts -> False
    With_Items -> NAME
    With_Items -> NUMBER
    With_Items -> STRING
    With_Items -> ...
    With_Items -> None
    With_Items -> True
    With_Items -> False
    Subscripts -> NAME
    Subscripts -> NUMBER
    Subscripts -> STRING
    Subscripts -> ...
    Subscripts -> None
    Subscripts -> True
    Subscripts -> False
    Subscriptlist -> NAME
    Subscriptlist -> NUMBER
    Subscriptlist -> STRING
    Subscriptlist -> ...
    Subscriptlist -> None
    Subscriptlist -> True
    Subscriptlist -> False
    Generic_Exprs -> NAME
    Generic_Exprs -> NUMBER
    Generic_Exprs -> STRING
    Generic_Exprs -> ...
    Generic_Exprs -> None
    Generic_Exprs -> True
    Generic_Exprs -> False
    Exprlist -> NAME
    Exprlist -> NUMBER
    Exprlist -> STRING
    Exprlist -> ...
    Exprlist -> None
    Exprlist -> True
    Exprlist -> False
    Testlist_Endcomma -> NAME
    Testlist_Endcomma -> NUMBER
    Testlist_Endcomma -> STRING
    Testlist_Endcomma -> ...
    Testlist_Endcomma -> None
    Testlist_Endcomma -> True
    Testlist_Endcomma -> False
    Yield_Arg -> NAME
    Yield_Arg -> NUMBER
    Yield_Arg -> STRING
    Yield_Arg -> ...
    Yield_Arg -> None
    Yield_Arg -> True
    Yield_Arg -> False
    Dict_Or_Set_Maker -> NAME
    Dict_Or_Set_Maker -> NUMBER
    Dict_Or_Set_Maker -> STRING
    Dict_Or_Set_Maker -> ...
    Dict_Or_Set_Maker -> None
    Dict_Or_Set_Maker -> True
    Dict_Or_Set_Maker -> False
    Import_As_Name -> NAME
    Dotted_Name -> NAME
    Many_Names -> NAME
    Import_As_Names_Endcomma -> NAME
    Start_Dotted_Name -> NAME
    Dotted_As_Name -> NAME
    Dotted_As_Names -> NAME
    Arguments -> Test Comp_For
    Arguments -> Double_Star_Op Test
    Arguments -> Atom Many_Trailers
    Arguments -> Await_Keyword Atom
    Arguments -> NAME
    Arguments -> NUMBER
    Arguments -> STRING
    Arguments -> ...
    Arguments -> None
    Arguments -> True
    Arguments -> False
    Arglist -> Test Comp_For
    Arglist -> Double_Star_Op Test
    Arglist -> Atom Many_Trailers
    Arglist -> Await_Keyword Atom
    Arglist -> NAME
    Arglist -> NUMBER
    Arglist -> STRING
    Arglist -> ...
    Arglist -> None
    Arglist -> True
    Arglist -> False
    Comp_Iter -> If_Keyword Test_Nocond
    Small_Stmts -> Yield_Keyword Yield_Arg
    Small_Stmts -> yield
    Colon.Suite -> Colon Suite
    Parameters -> Open_Paren Typedargslist.Close_Paren
    Typedargslist.Close_Paren -> Typedargslist Close_Paren
    Typedargslist -> Many_Tfpdef Comma.Star_Double_Star_Typed
    Comma.Star_Double_Star_Typed -> Comma Star_Double_Star_Typed
    Typedargslist -> Many_Tfpdef Comma.Double_Star_Tfpdef
    Comma.Double_Star_Tfpdef -> Comma Double_Star_Tfpdef
    Star_Double_Star_Typed -> Star_Tfpdef Comma.Double_Star_Tfpdef
    Double_Star_Tfpdef -> Double_Star_Op Tfpdef.Comma
    Tfpdef.Comma -> Tfpdef Comma
    Many_Tfpdef -> Tfpdef_Default Comma.Many_Tfpdef
    Comma.Many_Tfpdef -> Comma Many_Tfpdef
    Tfpdef_Default -> Tfpdef Assign_Op.Test
    Assign_Op.Test -> Assign_Op Test
    Vfpdef.Comma -> Vfpdef Comma
    Colon.Test -> Colon Test
    Semicolon.Newline -> Semicolon Newline
    Aug_Assign.Testlist_Endcomma -> Aug_Assign Testlist_Endcomma
    Test_Or_Star_Exprs -> Test_Or_Star_Expr Comma.Test_Or_Star_Exprs
    Comma.Test_Or_Star_Exprs -> Comma Test_Or_Star_Exprs
    Yield_Testlist_Star_Assign_Exprs -> Assign_Op Yield_Expr.Yield_Testlist_Star_Assign_Exprs
    Yield_Expr.Yield_Testlist_Star_Assign_Exprs -> Yield_Expr Yield_Testlist_Star_Assign_Exprs
    Import_As_Names_Endcomma.Close_Paren -> Import_As_Names_Endcomma Close_Paren
    As_Keyword.Simple_Name -> As_Keyword Simple_Name
    Comma.Import_As_Names_Endcomma -> Comma Import_As_Names_Endcomma
    Dotted_As_Names -> Dotted_As_Name Comma.Dotted_As_Names
    Comma.Dotted_As_Names -> Comma Dotted_As_Names
    Dotted_Name -> Simple_Name Dot.Dotted_Name
    Dot.Dotted_Name -> Dot Dotted_Name
    Suite.Elif_Stmt -> Suite Elif_Stmt
    Else_Stmt -> Else_Keyword Colon.Suite
    Suite.Else_Stmt -> Suite Else_Stmt
    Elif_Stmt.Else_Stmt -> Elif_Stmt Else_Stmt
    Except_Stmt -> Except_Clause Colon.Suite
    Suite.Except_Stmt -> Suite Except_Stmt
    As_Keyword.Expr -> As_Keyword Expr
    Else_Keyword.Test -> Else_Keyword Test
    Colon.Test_Nocond -> Colon Test_Nocond
    Or_Bool_Op.And_Test -> Or_Bool_Op And_Test
    And_Bool_Op.Not_Test -> And_Bool_Op Not_Test
    Comp_Op.Expr -> Comp_Op Expr
    Expr -> Expr Or_Op.Xor_Expr
    Or_Op.Xor_Expr -> Or_Op Xor_Expr
    Arith_Expr -> Arith_Expr Arith_Op.Term
    Arith_Op.Term -> Arith_Op Term
    Term -> Term MulDiv_Op.Factor
    MulDiv_Op.Factor -> MulDiv_Op Factor
    Double_Star_Op.Factor -> Double_Star_Op Factor
    Testlist_Comp.Close_Paren -> Testlist_Comp Close_Paren
    Testlist_Comp.Close_Sq_Bracket -> Testlist_Comp Close_Sq_Bracket
    Dict_Or_Set_Maker.Close_Curl_Bracket -> Dict_Or_Set_Maker Close_Curl_Bracket
    Trailer -> Open_Paren Arglist.Close_Paren
    Arglist.Close_Paren -> Arglist Close_Paren
    Trailer -> Open_Sq_Bracket Subscriptlist.Close_Sq_Bracket
    Subscriptlist.Close_Sq_Bracket -> Subscriptlist Close_Sq_Bracket
    Comma.Subscripts -> Comma Subscripts
    Comma.Generic_Exprs -> Comma Generic_Exprs
    Comma.Testlist_Endcomma -> Comma Testlist_Endcomma
    KeyVal_Or_Unpack -> Test Colon.Test
    Many_KeyVals_Or_Unpacks -> KeyVal_Or_Unpack Comma.Many_KeyVals_Or_Unpacks
    Comma.Many_KeyVals_Or_Unpacks -> Comma Many_KeyVals_Or_Unpacks
    Arguments -> Argument Comma.Arguments
    Comma.Arguments -> Comma Arguments
    Argument -> Test Assign_Op.Test
    In_Keyword.Or_Test -> In_Keyword Or_Test
    Or_Test.Comp_Iter -> Or_Test Comp_Iter
    Typedargslist -> Double_Star_Op Tfpdef.Comma
    Typedargslist -> Tfpdef_Default Comma.Many_Tfpdef
    Many_Tfpdef -> Tfpdef Assign_Op.Test
    Varargslist -> Double_Star_Op Vfpdef.Comma
    START -> Small_Stmts Semicolon.Newline
    Small_Stmts -> Testlist_Star_Expr Aug_Assign.Testlist_Endcomma
    Testlist_Star_Expr -> Test_Or_Star_Expr Comma.Test_Or_Star_Exprs
    Testlist_Comp -> Test_Or_Star_Expr Comma.Test_Or_Star_Exprs
    Dotted_As_Names -> Dotted_Name As_Keyword.Simple_Name
    Import_As_Names_Endcomma -> Import_As_Name Comma.Import_As_Names_Endcomma
    Import_As_Names_Endcomma -> Simple_Name As_Keyword.Simple_Name
    Start_Dotted_Name -> Simple_Name Dot.Dotted_Name
    Dotted_As_Names -> Simple_Name Dot.Dotted_Name
    With_Items -> Test As_Keyword.Expr
    Subscript -> Lambda_Keyword Colon.Test
    Small_Stmts -> Lambda_Keyword Colon.Test
    With_Items -> Lambda_Keyword Colon.Test
    Test_Nocond -> Lambda_Keyword Colon.Test_Nocond
    Test -> Or_Test Or_Bool_Op.And_Test
    Subscript -> Or_Test Or_Bool_Op.And_Test
    With_Items -> Or_Test Or_Bool_Op.And_Test
    Test -> And_Test And_Bool_Op.Not_Test
    Subscript -> And_Test And_Bool_Op.Not_Test
    With_Items -> And_Test And_Bool_Op.Not_Test
    Not_Test -> Comparison Comp_Op.Expr
    And_Test -> Comparison Comp_Op.Expr
    Or_Test -> Comparison Comp_Op.Expr
    Test -> Comparison Comp_Op.Expr
    Testlist_Comp -> Comparison Comp_Op.Expr
    Comparison -> Expr Or_Op.Xor_Expr
    Generic_Expr -> Expr Or_Op.Xor_Expr
    Not_Test -> Expr Or_Op.Xor_Expr
    And_Test -> Expr Or_Op.Xor_Expr
    Or_Test -> Expr Or_Op.Xor_Expr
    Test -> Expr Or_Op.Xor_Expr
    Test_Nocond -> Expr Or_Op.Xor_Expr
    Test_Or_Star_Expr -> Expr Or_Op.Xor_Expr
    Subscript -> Expr Or_Op.Xor_Expr
    Argument -> Expr Or_Op.Xor_Expr
    Test_Or_Star_Exprs -> Expr Or_Op.Xor_Expr
    Testlist_Star_Expr -> Expr Or_Op.Xor_Expr
    Testlist_Comp -> Expr Or_Op.Xor_Expr
    Small_Stmts -> Expr Or_Op.Xor_Expr
    With_Items -> Expr Or_Op.Xor_Expr
    Generic_Expr -> Arith_Expr Arith_Op.Term
    Test -> Arith_Expr Arith_Op.Term
    Test_Nocond -> Arith_Expr Arith_Op.Term
    Argument -> Arith_Expr Arith_Op.Term
    Testlist_Star_Expr -> Arith_Expr Arith_Op.Term
    Testlist_Comp -> Arith_Expr Arith_Op.Term
    With_Items -> Arith_Expr Arith_Op.Term
    Arith_Expr -> Term MulDiv_Op.Factor
    Generic_Expr -> Term MulDiv_Op.Factor
    Test -> Term MulDiv_Op.Factor
    Argument -> Term MulDiv_Op.Factor
    Testlist_Star_Expr -> Term MulDiv_Op.Factor
    Testlist_Comp -> Term MulDiv_Op.Factor
    With_Items -> Term MulDiv_Op.Factor
    Xor_Expr -> Atom_Expr Double_Star_Op.Factor
    Generic_Expr -> Atom_Expr Double_Star_Op.Factor
    Not_Test -> Atom_Expr Double_Star_Op.Factor
    Test_Nocond -> Atom_Expr Double_Star_Op.Factor
    With_Items -> Atom_Expr Double_Star_Op.Factor
    Atom_Expr -> Open_Sq_Bracket Testlist_Comp.Close_Sq_Bracket
    Atom_Expr -> Open_Curl_Bracket Dict_Or_Set_Maker.Close_Curl_Bracket
    Factor -> Open_Paren Testlist_Comp.Close_Paren
    Term -> Open_Paren Testlist_Comp.Close_Paren
    Xor_Expr -> Open_Sq_Bracket Testlist_Comp.Close_Sq_Bracket
    Xor_Expr -> Open_Curl_Bracket Dict_Or_Set_Maker.Close_Curl_Bracket
    Generic_Expr -> Open_Sq_Bracket Testlist_Comp.Close_Sq_Bracket
    Generic_Expr -> Open_Curl_Bracket Dict_Or_Set_Maker.Close_Curl_Bracket
    Test -> Open_Paren Testlist_Comp.Close_Paren
    Test -> Open_Sq_Bracket Testlist_Comp.Close_Sq_Bracket
    Test -> Open_Curl_Bracket Dict_Or_Set_Maker.Close_Curl_Bracket
    Test_Nocond -> Open_Curl_Bracket Dict_Or_Set_Maker.Close_Curl_Bracket
    Test_Or_Star_Expr -> Open_Paren Testlist_Comp.Close_Paren
    Subscript -> Open_Curl_Bracket Dict_Or_Set_Maker.Close_Curl_Bracket
    Argument -> Open_Paren Testlist_Comp.Close_Paren
    Argument -> Open_Sq_Bracket Testlist_Comp.Close_Sq_Bracket
    Test_Or_Star_Exprs -> Open_Paren Testlist_Comp.Close_Paren
    Testlist_Star_Expr -> Open_Paren Testlist_Comp.Close_Paren
    Testlist_Star_Expr -> Open_Sq_Bracket Testlist_Comp.Close_Sq_Bracket
    Testlist_Star_Expr -> Open_Curl_Bracket Dict_Or_Set_Maker.Close_Curl_Bracket
    With_Items -> Open_Sq_Bracket Testlist_Comp.Close_Sq_Bracket
    With_Items -> Open_Curl_Bracket Dict_Or_Set_Maker.Close_Curl_Bracket
    Many_Trailers -> Open_Paren Arglist.Close_Paren
    Many_Trailers -> Open_Sq_Bracket Subscriptlist.Close_Sq_Bracket
    Subscriptlist -> Subscript Comma.Subscripts
    Subscripts -> Lambda_Keyword Colon.Test
    Subscripts -> Or_Test Or_Bool_Op.And_Test
    Subscripts -> And_Test And_Bool_Op.Not_Test
    Subscripts -> Expr Or_Op.Xor_Expr
    Subscripts -> Atom_Expr Double_Star_Op.Factor
    Subscripts -> Open_Curl_Bracket Dict_Or_Set_Maker.Close_Curl_Bracket
    Subscriptlist -> Test Colon.Test
    Subscriptlist -> Lambda_Keyword Colon.Test
    Subscriptlist -> And_Test And_Bool_Op.Not_Test
    Subscriptlist -> Expr Or_Op.Xor_Expr
    Generic_Exprs -> Expr Or_Op.Xor_Expr
    Generic_Exprs -> Arith_Expr Arith_Op.Term
    Generic_Exprs -> Term MulDiv_Op.Factor
    Generic_Exprs -> Atom_Expr Double_Star_Op.Factor
    Generic_Exprs -> Open_Sq_Bracket Testlist_Comp.Close_Sq_Bracket
    Generic_Exprs -> Open_Curl_Bracket Dict_Or_Set_Maker.Close_Curl_Bracket
    Exprlist -> Generic_Expr Comma.Generic_Exprs
    Exprlist -> Expr Or_Op.Xor_Expr
    Exprlist -> Arith_Expr Arith_Op.Term
    Exprlist -> Term MulDiv_Op.Factor
    Exprlist -> Atom_Expr Double_Star_Op.Factor
    Exprlist -> Open_Curl_Bracket Dict_Or_Set_Maker.Close_Curl_Bracket
    Testlist_Endcomma -> Test Comma.Testlist_Endcomma
    Testlist_Endcomma -> Expr Or_Op.Xor_Expr
    Testlist_Endcomma -> Arith_Expr Arith_Op.Term
    Testlist_Endcomma -> Term MulDiv_Op.Factor
    Testlist_Endcomma -> Open_Paren Testlist_Comp.Close_Paren
    Testlist_Endcomma -> Open_Sq_Bracket Testlist_Comp.Close_Sq_Bracket
    Testlist_Endcomma -> Open_Curl_Bracket Dict_Or_Set_Maker.Close_Curl_Bracket
    Yield_Arg -> And_Test And_Bool_Op.Not_Test
    Yield_Arg -> Comparison Comp_Op.Expr
    Yield_Arg -> Expr Or_Op.Xor_Expr
    Many_KeyVals_Or_Unpacks -> Test Colon.Test
    Dict_Or_Set_Maker -> KeyVal_Or_Unpack Comma.Many_KeyVals_Or_Unpacks
    Dict_Or_Set_Maker -> Test Colon.Test
    Dict_Or_Set_Maker -> Lambda_Keyword Colon.Test
    Dict_Or_Set_Maker -> Or_Test Or_Bool_Op.And_Test
    Dict_Or_Set_Maker -> And_Test And_Bool_Op.Not_Test
    Dict_Or_Set_Maker -> Comparison Comp_Op.Expr
    Dict_Or_Set_Maker -> Expr Or_Op.Xor_Expr
    Dict_Or_Set_Maker -> Arith_Expr Arith_Op.Term
    Dict_Or_Set_Maker -> Term MulDiv_Op.Factor
    Dict_Or_Set_Maker -> Atom_Expr Double_Star_Op.Factor
    Dict_Or_Set_Maker -> Open_Sq_Bracket Testlist_Comp.Close_Sq_Bracket
    Arglist -> Argument Comma.Arguments
    Arguments -> Test Assign_Op.Test
    Arguments -> Expr Or_Op.Xor_Expr
    Arguments -> Arith_Expr Arith_Op.Term
    Arguments -> Open_Paren Testlist_Comp.Close_Paren
    Arguments -> Open_Sq_Bracket Testlist_Comp.Close_Sq_Bracket
    Arguments -> Open_Curl_Bracket Dict_Or_Set_Maker.Close_Curl_Bracket
    Arglist -> Test Assign_Op.Test
    Arglist -> Comparison Comp_Op.Expr
    Arglist -> Expr Or_Op.Xor_Expr
    Arglist -> Arith_Expr Arith_Op.Term
    Arglist -> Term MulDiv_Op.Factor
    Arglist -> Open_Paren Testlist_Comp.Close_Paren
    Arglist -> Open_Sq_Bracket Testlist_Comp.Close_Sq_Bracket
    Arglist -> Open_Curl_Bracket Dict_Or_Set_Maker.Close_Curl_Bracket
    Parameters.Colon.Suite -> Parameters Colon.Suite
    Test.Colon.Suite -> Test Colon.Suite
    Import_From_Imports -> Import_Keyword Open_Paren.Import_As_Names_Endcomma.Close_Paren
    Open_Paren.Import_As_Names_Endcomma.Close_Paren -> Open_Paren Import_As_Names_Endcomma.Close_Paren
    Elif_Stmt -> Elif_Keyword Test.Colon.Suite
    Colon.Suite.Elif_Stmt -> Colon Suite.Elif_Stmt
    Colon.Suite.Else_Stmt -> Colon Suite.Else_Stmt
    Suite.Elif_Stmt.Else_Stmt -> Suite Elif_Stmt.Else_Stmt
    Testlist_Endcomma.Colon.Suite -> Testlist_Endcomma Colon.Suite
    Colon.Suite.Except_Stmt -> Colon Suite.Except_Stmt
    With_Items.Colon.Suite -> With_Items Colon.Suite
    Except_Clause -> Except_Keyword Test.As_Keyword.Simple_Name
    Test.As_Keyword.Simple_Name -> Test As_Keyword.Simple_Name
    Or_Test.Else_Keyword.Test -> Or_Test Else_Keyword.Test
    Varargslist.Colon.Test -> Varargslist Colon.Test
    Varargslist.Colon.Test_Nocond -> Varargslist Colon.Test_Nocond
    Simple_Name.Colon.Suite -> Simple_Name Colon.Suite
    Close_Paren.Colon.Suite -> Close_Paren Colon.Suite
    Comp_For -> For_Keyword Exprlist.In_Keyword.Or_Test
    Exprlist.In_Keyword.Or_Test -> Exprlist In_Keyword.Or_Test
    In_Keyword.Or_Test.Comp_Iter -> In_Keyword Or_Test.Comp_Iter
    START -> If_Keyword Test.Colon.Suite
    START -> While_Keyword Test.Colon.Suite
    START -> Try_Keyword Colon.Suite.Except_Stmt
    START -> With_Keyword With_Items.Colon.Suite
    Subscript -> Lambda_Keyword Varargslist.Colon.Test
    With_Items -> Lambda_Keyword Varargslist.Colon.Test
    Test_Nocond -> Lambda_Keyword Varargslist.Colon.Test_Nocond
    Subscripts -> Lambda_Keyword Varargslist.Colon.Test
    Subscriptlist -> Lambda_Keyword Varargslist.Colon.Test
    START -> Class_Keyword Simple_Name.Colon.Suite
    Simple_Name.Parameters.Colon.Suite -> Simple_Name Parameters.Colon.Suite
    Arrow.Test.Colon.Suite -> Arrow Test.Colon.Suite
    Elif_Stmt -> Elif_Keyword Test.Colon.Suite.Elif_Stmt
    Test.Colon.Suite.Elif_Stmt -> Test Colon.Suite.Elif_Stmt
    Test.Colon.Suite.Else_Stmt -> Test Colon.Suite.Else_Stmt
    Colon.Suite.Elif_Stmt.Else_Stmt -> Colon Suite.Elif_Stmt.Else_Stmt
    In_Keyword.Testlist_Endcomma.Colon.Suite -> In_Keyword Testlist_Endcomma.Colon.Suite
    If_Keyword.Or_Test.Else_Keyword.Test -> If_Keyword Or_Test.Else_Keyword.Test
    Arglist.Close_Paren.Colon.Suite -> Arglist Close_Paren.Colon.Suite
    Comp_For -> For_Keyword Exprlist.In_Keyword.Or_Test.Comp_Iter
    Exprlist.In_Keyword.Or_Test.Comp_Iter -> Exprlist In_Keyword.Or_Test.Comp_Iter
    Comp_For -> Async_Keyword For_Keyword.Exprlist.In_Keyword.Or_Test
    For_Keyword.Exprlist.In_Keyword.Or_Test -> For_Keyword Exprlist.In_Keyword.Or_Test
    START -> Def_Keyword Simple_Name.Parameters.Colon.Suite
    START -> If_Keyword Test.Colon.Suite.Else_Stmt
    START -> If_Keyword Test.Colon.Suite.Elif_Stmt
    START -> While_Keyword Test.Colon.Suite.Else_Stmt
    Subscript -> Or_Test If_Keyword.Or_Test.Else_Keyword.Test
    With_Items -> Or_Test If_Keyword.Or_Test.Else_Keyword.Test
    Subscripts -> Or_Test If_Keyword.Or_Test.Else_Keyword.Test
    Dict_Or_Set_Maker -> Or_Test If_Keyword.Or_Test.Else_Keyword.Test
    Comp_Iter -> Async_Keyword For_Keyword.Exprlist.In_Keyword.Or_Test
    Parameters.Arrow.Test.Colon.Suite -> Parameters Arrow.Test.Colon.Suite
    Test.Colon.Suite.Elif_Stmt.Else_Stmt -> Test Colon.Suite.Elif_Stmt.Else_Stmt
    Exprlist.In_Keyword.Testlist_Endcomma.Colon.Suite -> Exprlist In_Keyword.Testlist_Endcomma.Colon.Suite
    Open_Paren.Arglist.Close_Paren.Colon.Suite -> Open_Paren Arglist.Close_Paren.Colon.Suite
    Comp_For -> Async_Keyword For_Keyword.Exprlist.In_Keyword.Or_Test.Comp_Iter
    For_Keyword.Exprlist.In_Keyword.Or_Test.Comp_Iter -> For_Keyword Exprlist.In_Keyword.Or_Test.Comp_Iter
    START -> If_Keyword Test.Colon.Suite.Elif_Stmt.Else_Stmt
    START -> For_Keyword Exprlist.In_Keyword.Testlist_Endcomma.Colon.Suite
    Comp_Iter -> Async_Keyword For_Keyword.Exprlist.In_Keyword.Or_Test.Comp_Iter
    Funcdef -> Def_Keyword Simple_Name.Parameters.Arrow.Test.Colon.Suite
    Simple_Name.Parameters.Arrow.Test.Colon.Suite -> Simple_Name Parameters.Arrow.Test.Colon.Suite
    Simple_Name.Open_Paren.Arglist.Close_Paren.Colon.Suite -> Simple_Name Open_Paren.Arglist.Close_Paren.Colon.Suite
    START -> Def_Keyword Simple_Name.Parameters.Arrow.Test.Colon.Suite
    START -> Class_Keyword Simple_Name.Open_Paren.Arglist.Close_Paren.Colon.Suite
  """.trimIndent()
    .lines().map { it.split(" -> ").let { Pair(it[0], it[1].split(" ")) } }.toSet().freeze()
}

// Not filtered by popularity
val pythonStatementCNFAllProds: CFG by lazy {
  """
    Newline -> NEWLINE
    Parameters -> Open_Paren Close_Paren
    Typedargslist -> Many_Tfpdef Comma
    Star_Double_Star_Typed -> Star_Tfpdef Comma
    Star_Tfpdef_Comma -> Comma Tfpdef_Default
    Star_Tfpdef -> Star_Op Star_Tfpdef_Comma
    Star_Tfpdef -> Star_Op Tfpdef
    Double_Star_Tfpdef -> Double_Star_Op Tfpdef
    Varargslist -> Many_Vfpdef Comma
    Star_Double_Star -> Star_Vfpdef Comma
    Star_Vfpdef_Comma -> Comma Vfpdef_Default
    Star_Vfpdef -> Star_Op Star_Vfpdef_Comma
    Star_Vfpdef -> Star_Op Vfpdef
    Double_Star_Vfpdef -> Double_Star_Op Vfpdef
    Vfpdef -> NAME
    Assign_Op -> =
    Star_Op -> *
    Double_Star_Op -> **
    Arrow -> arrow
    Annotated_Assign -> Colon Test
    Testlist_Star_Expr -> Test_Or_Star_Exprs Comma
    Yield_Testlist_Star_Assign_Exprs -> Assign_Op Yield_Expr
    Yield_Testlist_Star_Assign_Exprs -> Assign_Op Testlist_Star_Expr
    Dots_Plus -> Dot_Or_Dots Dots_Plus
    Start_Dotted_Name -> Dots_Plus Dotted_Name
    Import_From_Froms -> From_Keyword Start_Dotted_Name
    Import_From_Froms -> From_Keyword Dots_Plus
    Import_From_Imports -> Import_Keyword Star_Op
    Import_From_Imports -> Import_Keyword Import_As_Names_Endcomma
    Import_As_Names_Endcomma -> Import_As_Name Comma
    Aug_Assign -> +=
    Aug_Assign -> -=
    Aug_Assign -> *=
    Aug_Assign -> @=
    Aug_Assign -> /=
    Aug_Assign -> %=
    Aug_Assign -> &=
    Aug_Assign -> |=
    Aug_Assign -> ^=
    Aug_Assign -> <<=
    Aug_Assign -> >>=
    Aug_Assign -> **=
    Aug_Assign -> //=
    Del_Keyword -> del
    Return_Keyword -> return
    Yield_Keyword -> yield
    Raise_Keyword -> raise
    From_Keyword -> from
    Import_Keyword -> import
    Dot_Or_Dots -> .
    Dot_Or_Dots -> ...
    As_Keyword -> as
    Global_Keyword -> global
    Nonlocal_Keyword -> nonlocal
    Assert_Keyword -> assert
    Def_Keyword -> def
    Class_Keyword -> class
    Except_Clause -> Except_Keyword Test
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
    Not_Test -> Not_Bool_Op Not_Test
    Factor -> Unary_Op Factor
    Many_Trailers -> Trailer Many_Trailers
    Atom_Expr -> Atom Many_Trailers
    Atom_Expr -> Await_Keyword Atom
    Atom -> Open_Paren Close_Paren
    Atom -> Open_Sq_Bracket Close_Sq_Bracket
    Atom -> Open_Curl_Bracket Close_Curl_Bracket
    Testlist_Comp -> Test_Or_Star_Expr Comp_For
    Trailer -> Open_Paren Close_Paren
    Trailer -> Dot Simple_Name
    Subscriptlist -> Subscripts Comma
    Subscript -> Test Colon
    Subscript -> Colon Test
    Subscript -> Colon Sliceop
    Sliceop -> Colon Test
    Exprlist -> Generic_Exprs Comma
    Testlist_Endcomma -> Test Comma
    KeyVal_Or_Unpack -> Double_Star_Op Expr
    Or_Bool_Op -> or
    And_Bool_Op -> and
    Not_Bool_Op -> not
    Comp_Op -> <
    Comp_Op -> >
    Comp_Op -> ==
    Comp_Op -> >=
    Comp_Op -> <=
    Comp_Op -> <>
    Comp_Op -> !=
    Comp_Op -> in
    Comp_Op -> not_in
    Comp_Op -> is
    Comp_Op -> is_not
    Or_Op -> OR
    Xor_Op -> ^
    And_Op -> &
    Shift_Op -> <<
    Shift_Op -> >>
    Arith_Op -> +
    Arith_Op -> -
    MulDiv_Op -> *
    MulDiv_Op -> @
    MulDiv_Op -> /
    MulDiv_Op -> %
    MulDiv_Op -> //
    Unary_Op -> +
    Unary_Op -> -
    Unary_Op -> ~
    Simple_Name -> NAME
    Arglist -> Arguments Comma
    Argument -> Test Comp_For
    Argument -> Double_Star_Op Test
    Argument -> Star_Op Test
    Yield_Expr -> Yield_Keyword Yield_Arg
    Yield_Arg -> From_Keyword Test
    Typedargslist -> Star_Tfpdef Comma
    Star_Double_Star_Typed -> Star_Op Star_Tfpdef_Comma
    Star_Double_Star_Typed -> Star_Op Tfpdef
    Typedargslist -> Star_Op Star_Tfpdef_Comma
    Typedargslist -> Star_Op Tfpdef
    Typedargslist -> Double_Star_Op Tfpdef
    Varargslist -> Star_Vfpdef Comma
    Star_Double_Star -> Star_Op Star_Vfpdef_Comma
    Star_Double_Star -> Star_Op Vfpdef
    Varargslist -> Star_Op Star_Vfpdef_Comma
    Varargslist -> Star_Op Vfpdef
    Varargslist -> Double_Star_Op Vfpdef
    Vfpdef_Default -> NAME
    Tfpdef -> NAME
    Many_Vfpdef -> NAME
    Varargslist -> NAME
    Tfpdef_Default -> NAME
    Many_Tfpdef -> NAME
    Typedargslist -> NAME
    Star_Tfpdef -> *
    Star_Vfpdef -> *
    Star_Double_Star_Typed -> *
    Typedargslist -> *
    Star_Double_Star -> *
    Varargslist -> *
    Suite -> Small_Stmts Newline
    START -> Small_Stmts Newline
    Small_Stmt -> Testlist_Star_Expr Annotated_Assign
    Small_Stmts -> Testlist_Star_Expr Annotated_Assign
    Testlist_Comp -> Test_Or_Star_Exprs Comma
    Small_Stmt -> Testlist_Star_Expr Yield_Testlist_Star_Assign_Exprs
    Small_Stmt -> Test_Or_Star_Exprs Comma
    Small_Stmts -> Testlist_Star_Expr Yield_Testlist_Star_Assign_Exprs
    Small_Stmts -> Test_Or_Star_Exprs Comma
    Small_Stmt -> Del_Keyword Exprlist
    Small_Stmts -> Del_Keyword Exprlist
    Small_Stmt -> Return_Keyword Testlist_Endcomma
    Small_Stmts -> Return_Keyword Testlist_Endcomma
    Small_Stmt -> Raise_Keyword Test
    Small_Stmts -> Raise_Keyword Test
    Small_Stmt -> Import_Keyword Dotted_As_Names
    Small_Stmts -> Import_Keyword Dotted_As_Names
    Small_Stmt -> Import_From_Froms Import_From_Imports
    Small_Stmts -> Import_From_Froms Import_From_Imports
    Small_Stmt -> Global_Keyword Many_Names
    Small_Stmts -> Global_Keyword Many_Names
    Small_Stmt -> Nonlocal_Keyword Many_Names
    Small_Stmts -> Nonlocal_Keyword Many_Names
    Small_Stmt -> Assert_Keyword Test
    Small_Stmts -> Assert_Keyword Test
    Small_Stmt -> pass
    Small_Stmts -> pass
    Small_Stmt -> break
    Small_Stmts -> break
    Small_Stmt -> continue
    Small_Stmts -> continue
    Small_Stmt -> return
    Small_Stmts -> return
    Yield_Expr -> yield
    Small_Stmt -> raise
    Small_Stmts -> raise
    Dots_Plus -> .
    Dots_Plus -> ...
    START -> Async_Keyword Funcdef
    START -> Async_Keyword With_Stmt
    START -> Async_Keyword For_Stmt
    Except_Clause -> except
    Subscript -> :
    Sliceop -> :
    And_Test -> Not_Bool_Op Not_Test
    Or_Test -> Not_Bool_Op Not_Test
    Test -> Not_Bool_Op Not_Test
    Test_Nocond -> Not_Bool_Op Not_Test
    Test_Or_Star_Expr -> Not_Bool_Op Not_Test
    With_Item -> Not_Bool_Op Not_Test
    Subscript -> Not_Bool_Op Not_Test
    Argument -> Not_Bool_Op Not_Test
    Test_Or_Star_Exprs -> Not_Bool_Op Not_Test
    Testlist_Star_Expr -> Not_Bool_Op Not_Test
    Testlist_Comp -> Not_Bool_Op Not_Test
    Small_Stmt -> Not_Bool_Op Not_Test
    Small_Stmts -> Not_Bool_Op Not_Test
    With_Items -> Not_Bool_Op Not_Test
    Test_Or_Star_Expr -> Star_Op Expr
    Generic_Expr -> Star_Op Expr
    Test_Or_Star_Exprs -> Star_Op Expr
    Testlist_Star_Expr -> Star_Op Expr
    Testlist_Comp -> Star_Op Expr
    Small_Stmt -> Star_Op Expr
    Small_Stmts -> Star_Op Expr
    Term -> Unary_Op Factor
    Arith_Expr -> Unary_Op Factor
    Shift_Expr -> Unary_Op Factor
    And_Expr -> Unary_Op Factor
    Xor_Expr -> Unary_Op Factor
    Expr -> Unary_Op Factor
    Comparison -> Unary_Op Factor
    Generic_Expr -> Unary_Op Factor
    Not_Test -> Unary_Op Factor
    And_Test -> Unary_Op Factor
    Or_Test -> Unary_Op Factor
    Test -> Unary_Op Factor
    Test_Nocond -> Unary_Op Factor
    Test_Or_Star_Expr -> Unary_Op Factor
    With_Item -> Unary_Op Factor
    Subscript -> Unary_Op Factor
    Argument -> Unary_Op Factor
    Test_Or_Star_Exprs -> Unary_Op Factor
    Testlist_Star_Expr -> Unary_Op Factor
    Testlist_Comp -> Unary_Op Factor
    Small_Stmt -> Unary_Op Factor
    Small_Stmts -> Unary_Op Factor
    With_Items -> Unary_Op Factor
    Factor -> Atom Many_Trailers
    Factor -> Await_Keyword Atom
    Term -> Atom Many_Trailers
    Term -> Await_Keyword Atom
    Arith_Expr -> Atom Many_Trailers
    Arith_Expr -> Await_Keyword Atom
    Shift_Expr -> Atom Many_Trailers
    Shift_Expr -> Await_Keyword Atom
    And_Expr -> Atom Many_Trailers
    And_Expr -> Await_Keyword Atom
    Xor_Expr -> Atom Many_Trailers
    Xor_Expr -> Await_Keyword Atom
    Expr -> Atom Many_Trailers
    Expr -> Await_Keyword Atom
    Comparison -> Atom Many_Trailers
    Comparison -> Await_Keyword Atom
    Generic_Expr -> Atom Many_Trailers
    Generic_Expr -> Await_Keyword Atom
    Not_Test -> Atom Many_Trailers
    Not_Test -> Await_Keyword Atom
    And_Test -> Atom Many_Trailers
    And_Test -> Await_Keyword Atom
    Or_Test -> Atom Many_Trailers
    Or_Test -> Await_Keyword Atom
    Test -> Atom Many_Trailers
    Test -> Await_Keyword Atom
    Test_Nocond -> Atom Many_Trailers
    Test_Nocond -> Await_Keyword Atom
    Test_Or_Star_Expr -> Atom Many_Trailers
    Test_Or_Star_Expr -> Await_Keyword Atom
    With_Item -> Atom Many_Trailers
    With_Item -> Await_Keyword Atom
    Subscript -> Atom Many_Trailers
    Subscript -> Await_Keyword Atom
    Argument -> Atom Many_Trailers
    Argument -> Await_Keyword Atom
    Test_Or_Star_Exprs -> Atom Many_Trailers
    Test_Or_Star_Exprs -> Await_Keyword Atom
    Testlist_Star_Expr -> Atom Many_Trailers
    Testlist_Star_Expr -> Await_Keyword Atom
    Testlist_Comp -> Atom Many_Trailers
    Testlist_Comp -> Await_Keyword Atom
    Small_Stmt -> Atom Many_Trailers
    Small_Stmt -> Await_Keyword Atom
    Small_Stmts -> Atom Many_Trailers
    Small_Stmts -> Await_Keyword Atom
    With_Items -> Atom Many_Trailers
    With_Items -> Await_Keyword Atom
    Atom_Expr -> Open_Paren Close_Paren
    Atom_Expr -> Open_Sq_Bracket Close_Sq_Bracket
    Atom_Expr -> Open_Curl_Bracket Close_Curl_Bracket
    Factor -> Open_Paren Close_Paren
    Factor -> Open_Sq_Bracket Close_Sq_Bracket
    Factor -> Open_Curl_Bracket Close_Curl_Bracket
    Term -> Open_Paren Close_Paren
    Term -> Open_Sq_Bracket Close_Sq_Bracket
    Term -> Open_Curl_Bracket Close_Curl_Bracket
    Arith_Expr -> Open_Paren Close_Paren
    Arith_Expr -> Open_Sq_Bracket Close_Sq_Bracket
    Arith_Expr -> Open_Curl_Bracket Close_Curl_Bracket
    Shift_Expr -> Open_Paren Close_Paren
    Shift_Expr -> Open_Sq_Bracket Close_Sq_Bracket
    Shift_Expr -> Open_Curl_Bracket Close_Curl_Bracket
    And_Expr -> Open_Paren Close_Paren
    And_Expr -> Open_Sq_Bracket Close_Sq_Bracket
    And_Expr -> Open_Curl_Bracket Close_Curl_Bracket
    Xor_Expr -> Open_Paren Close_Paren
    Xor_Expr -> Open_Sq_Bracket Close_Sq_Bracket
    Xor_Expr -> Open_Curl_Bracket Close_Curl_Bracket
    Expr -> Open_Paren Close_Paren
    Expr -> Open_Sq_Bracket Close_Sq_Bracket
    Expr -> Open_Curl_Bracket Close_Curl_Bracket
    Comparison -> Open_Paren Close_Paren
    Comparison -> Open_Sq_Bracket Close_Sq_Bracket
    Comparison -> Open_Curl_Bracket Close_Curl_Bracket
    Generic_Expr -> Open_Paren Close_Paren
    Generic_Expr -> Open_Sq_Bracket Close_Sq_Bracket
    Generic_Expr -> Open_Curl_Bracket Close_Curl_Bracket
    Not_Test -> Open_Paren Close_Paren
    Not_Test -> Open_Sq_Bracket Close_Sq_Bracket
    Not_Test -> Open_Curl_Bracket Close_Curl_Bracket
    And_Test -> Open_Paren Close_Paren
    And_Test -> Open_Sq_Bracket Close_Sq_Bracket
    And_Test -> Open_Curl_Bracket Close_Curl_Bracket
    Or_Test -> Open_Paren Close_Paren
    Or_Test -> Open_Sq_Bracket Close_Sq_Bracket
    Or_Test -> Open_Curl_Bracket Close_Curl_Bracket
    Test -> Open_Paren Close_Paren
    Test -> Open_Sq_Bracket Close_Sq_Bracket
    Test -> Open_Curl_Bracket Close_Curl_Bracket
    Test_Nocond -> Open_Paren Close_Paren
    Test_Nocond -> Open_Sq_Bracket Close_Sq_Bracket
    Test_Nocond -> Open_Curl_Bracket Close_Curl_Bracket
    Test_Or_Star_Expr -> Open_Paren Close_Paren
    Test_Or_Star_Expr -> Open_Sq_Bracket Close_Sq_Bracket
    Test_Or_Star_Expr -> Open_Curl_Bracket Close_Curl_Bracket
    With_Item -> Open_Paren Close_Paren
    With_Item -> Open_Sq_Bracket Close_Sq_Bracket
    With_Item -> Open_Curl_Bracket Close_Curl_Bracket
    Subscript -> Open_Paren Close_Paren
    Subscript -> Open_Sq_Bracket Close_Sq_Bracket
    Subscript -> Open_Curl_Bracket Close_Curl_Bracket
    Argument -> Open_Paren Close_Paren
    Argument -> Open_Sq_Bracket Close_Sq_Bracket
    Argument -> Open_Curl_Bracket Close_Curl_Bracket
    Test_Or_Star_Exprs -> Open_Paren Close_Paren
    Test_Or_Star_Exprs -> Open_Sq_Bracket Close_Sq_Bracket
    Test_Or_Star_Exprs -> Open_Curl_Bracket Close_Curl_Bracket
    Testlist_Star_Expr -> Open_Paren Close_Paren
    Testlist_Star_Expr -> Open_Sq_Bracket Close_Sq_Bracket
    Testlist_Star_Expr -> Open_Curl_Bracket Close_Curl_Bracket
    Testlist_Comp -> Open_Paren Close_Paren
    Testlist_Comp -> Open_Sq_Bracket Close_Sq_Bracket
    Testlist_Comp -> Open_Curl_Bracket Close_Curl_Bracket
    Small_Stmt -> Open_Paren Close_Paren
    Small_Stmt -> Open_Sq_Bracket Close_Sq_Bracket
    Small_Stmt -> Open_Curl_Bracket Close_Curl_Bracket
    Small_Stmts -> Open_Paren Close_Paren
    Small_Stmts -> Open_Sq_Bracket Close_Sq_Bracket
    Small_Stmts -> Open_Curl_Bracket Close_Curl_Bracket
    With_Items -> Open_Paren Close_Paren
    With_Items -> Open_Sq_Bracket Close_Sq_Bracket
    With_Items -> Open_Curl_Bracket Close_Curl_Bracket
    Many_Trailers -> Open_Paren Close_Paren
    Many_Trailers -> Dot Simple_Name
    Subscripts -> Test Colon
    Subscripts -> Colon Test
    Subscripts -> Colon Sliceop
    Subscripts -> :
    Subscripts -> Not_Bool_Op Not_Test
    Subscripts -> Unary_Op Factor
    Subscripts -> Atom Many_Trailers
    Subscripts -> Await_Keyword Atom
    Subscripts -> Open_Paren Close_Paren
    Subscripts -> Open_Sq_Bracket Close_Sq_Bracket
    Subscripts -> Open_Curl_Bracket Close_Curl_Bracket
    Subscriptlist -> Test Colon
    Subscriptlist -> Colon Test
    Subscriptlist -> Colon Sliceop
    Subscriptlist -> :
    Subscriptlist -> Not_Bool_Op Not_Test
    Subscriptlist -> Unary_Op Factor
    Subscriptlist -> Atom Many_Trailers
    Subscriptlist -> Await_Keyword Atom
    Subscriptlist -> Open_Paren Close_Paren
    Subscriptlist -> Open_Sq_Bracket Close_Sq_Bracket
    Subscriptlist -> Open_Curl_Bracket Close_Curl_Bracket
    Generic_Exprs -> Star_Op Expr
    Generic_Exprs -> Unary_Op Factor
    Generic_Exprs -> Atom Many_Trailers
    Generic_Exprs -> Await_Keyword Atom
    Generic_Exprs -> Open_Paren Close_Paren
    Generic_Exprs -> Open_Sq_Bracket Close_Sq_Bracket
    Generic_Exprs -> Open_Curl_Bracket Close_Curl_Bracket
    Exprlist -> Star_Op Expr
    Exprlist -> Unary_Op Factor
    Exprlist -> Atom Many_Trailers
    Exprlist -> Await_Keyword Atom
    Exprlist -> Open_Paren Close_Paren
    Exprlist -> Open_Sq_Bracket Close_Sq_Bracket
    Exprlist -> Open_Curl_Bracket Close_Curl_Bracket
    Testlist_Endcomma -> Not_Bool_Op Not_Test
    Testlist_Endcomma -> Unary_Op Factor
    Testlist_Endcomma -> Atom Many_Trailers
    Testlist_Endcomma -> Await_Keyword Atom
    Testlist_Endcomma -> Open_Paren Close_Paren
    Testlist_Endcomma -> Open_Sq_Bracket Close_Sq_Bracket
    Testlist_Endcomma -> Open_Curl_Bracket Close_Curl_Bracket
    Yield_Arg -> Test Comma
    Yield_Arg -> Not_Bool_Op Not_Test
    Yield_Arg -> Unary_Op Factor
    Yield_Arg -> Atom Many_Trailers
    Yield_Arg -> Await_Keyword Atom
    Yield_Arg -> Open_Paren Close_Paren
    Yield_Arg -> Open_Sq_Bracket Close_Sq_Bracket
    Yield_Arg -> Open_Curl_Bracket Close_Curl_Bracket
    Many_KeyVals_Or_Unpacks -> Double_Star_Op Expr
    Dict_Or_Set_Maker -> KeyVal_Or_Unpack Comp_For
    Dict_Or_Set_Maker -> Many_KeyVals_Or_Unpacks Comma
    Dict_Or_Set_Maker -> Double_Star_Op Expr
    Dict_Or_Set_Maker -> Test_Or_Star_Expr Comp_For
    Dict_Or_Set_Maker -> Test_Or_Star_Exprs Comma
    Dict_Or_Set_Maker -> Not_Bool_Op Not_Test
    Dict_Or_Set_Maker -> Star_Op Expr
    Dict_Or_Set_Maker -> Unary_Op Factor
    Dict_Or_Set_Maker -> Atom Many_Trailers
    Dict_Or_Set_Maker -> Await_Keyword Atom
    Dict_Or_Set_Maker -> Open_Paren Close_Paren
    Dict_Or_Set_Maker -> Open_Sq_Bracket Close_Sq_Bracket
    Dict_Or_Set_Maker -> Open_Curl_Bracket Close_Curl_Bracket
    Atom -> NAME
    Atom -> NUMBER
    Atom -> STRING
    Atom -> ...
    Atom -> None
    Atom -> True
    Atom -> False
    Atom_Expr -> NAME
    Atom_Expr -> NUMBER
    Atom_Expr -> STRING
    Atom_Expr -> ...
    Atom_Expr -> None
    Atom_Expr -> True
    Atom_Expr -> False
    Factor -> NAME
    Factor -> NUMBER
    Factor -> STRING
    Factor -> ...
    Factor -> None
    Factor -> True
    Factor -> False
    Term -> NAME
    Term -> NUMBER
    Term -> STRING
    Term -> ...
    Term -> None
    Term -> True
    Term -> False
    Arith_Expr -> NAME
    Arith_Expr -> NUMBER
    Arith_Expr -> STRING
    Arith_Expr -> ...
    Arith_Expr -> None
    Arith_Expr -> True
    Arith_Expr -> False
    Shift_Expr -> NAME
    Shift_Expr -> NUMBER
    Shift_Expr -> STRING
    Shift_Expr -> ...
    Shift_Expr -> None
    Shift_Expr -> True
    Shift_Expr -> False
    And_Expr -> NAME
    And_Expr -> NUMBER
    And_Expr -> STRING
    And_Expr -> ...
    And_Expr -> None
    And_Expr -> True
    And_Expr -> False
    Xor_Expr -> NAME
    Xor_Expr -> NUMBER
    Xor_Expr -> STRING
    Xor_Expr -> ...
    Xor_Expr -> None
    Xor_Expr -> True
    Xor_Expr -> False
    Expr -> NAME
    Expr -> NUMBER
    Expr -> STRING
    Expr -> ...
    Expr -> None
    Expr -> True
    Expr -> False
    Comparison -> NAME
    Comparison -> NUMBER
    Comparison -> STRING
    Comparison -> ...
    Comparison -> None
    Comparison -> True
    Comparison -> False
    Generic_Expr -> NAME
    Generic_Expr -> NUMBER
    Generic_Expr -> STRING
    Generic_Expr -> ...
    Generic_Expr -> None
    Generic_Expr -> True
    Generic_Expr -> False
    Not_Test -> NAME
    Not_Test -> NUMBER
    Not_Test -> STRING
    Not_Test -> ...
    Not_Test -> None
    Not_Test -> True
    Not_Test -> False
    And_Test -> NAME
    And_Test -> NUMBER
    And_Test -> STRING
    And_Test -> ...
    And_Test -> None
    And_Test -> True
    And_Test -> False
    Or_Test -> NAME
    Or_Test -> NUMBER
    Or_Test -> STRING
    Or_Test -> ...
    Or_Test -> None
    Or_Test -> True
    Or_Test -> False
    Test -> NAME
    Test -> NUMBER
    Test -> STRING
    Test -> ...
    Test -> None
    Test -> True
    Test -> False
    Test_Nocond -> NAME
    Test_Nocond -> NUMBER
    Test_Nocond -> STRING
    Test_Nocond -> ...
    Test_Nocond -> None
    Test_Nocond -> True
    Test_Nocond -> False
    Test_Or_Star_Expr -> NAME
    Test_Or_Star_Expr -> NUMBER
    Test_Or_Star_Expr -> STRING
    Test_Or_Star_Expr -> ...
    Test_Or_Star_Expr -> None
    Test_Or_Star_Expr -> True
    Test_Or_Star_Expr -> False
    With_Item -> NAME
    With_Item -> NUMBER
    With_Item -> STRING
    With_Item -> ...
    With_Item -> None
    With_Item -> True
    With_Item -> False
    Subscript -> NAME
    Subscript -> NUMBER
    Subscript -> STRING
    Subscript -> ...
    Subscript -> None
    Subscript -> True
    Subscript -> False
    Argument -> NAME
    Argument -> NUMBER
    Argument -> STRING
    Argument -> ...
    Argument -> None
    Argument -> True
    Argument -> False
    Test_Or_Star_Exprs -> NAME
    Test_Or_Star_Exprs -> NUMBER
    Test_Or_Star_Exprs -> STRING
    Test_Or_Star_Exprs -> ...
    Test_Or_Star_Exprs -> None
    Test_Or_Star_Exprs -> True
    Test_Or_Star_Exprs -> False
    Testlist_Star_Expr -> NAME
    Testlist_Star_Expr -> NUMBER
    Testlist_Star_Expr -> STRING
    Testlist_Star_Expr -> ...
    Testlist_Star_Expr -> None
    Testlist_Star_Expr -> True
    Testlist_Star_Expr -> False
    Testlist_Comp -> NAME
    Testlist_Comp -> NUMBER
    Testlist_Comp -> STRING
    Testlist_Comp -> ...
    Testlist_Comp -> None
    Testlist_Comp -> True
    Testlist_Comp -> False
    Small_Stmt -> NAME
    Small_Stmt -> NUMBER
    Small_Stmt -> STRING
    Small_Stmt -> ...
    Small_Stmt -> None
    Small_Stmt -> True
    Small_Stmt -> False
    Small_Stmts -> NAME
    Small_Stmts -> NUMBER
    Small_Stmts -> STRING
    Small_Stmts -> ...
    Small_Stmts -> None
    Small_Stmts -> True
    Small_Stmts -> False
    With_Items -> NAME
    With_Items -> NUMBER
    With_Items -> STRING
    With_Items -> ...
    With_Items -> None
    With_Items -> True
    With_Items -> False
    Subscripts -> NAME
    Subscripts -> NUMBER
    Subscripts -> STRING
    Subscripts -> ...
    Subscripts -> None
    Subscripts -> True
    Subscripts -> False
    Subscriptlist -> NAME
    Subscriptlist -> NUMBER
    Subscriptlist -> STRING
    Subscriptlist -> ...
    Subscriptlist -> None
    Subscriptlist -> True
    Subscriptlist -> False
    Generic_Exprs -> NAME
    Generic_Exprs -> NUMBER
    Generic_Exprs -> STRING
    Generic_Exprs -> ...
    Generic_Exprs -> None
    Generic_Exprs -> True
    Generic_Exprs -> False
    Exprlist -> NAME
    Exprlist -> NUMBER
    Exprlist -> STRING
    Exprlist -> ...
    Exprlist -> None
    Exprlist -> True
    Exprlist -> False
    Testlist_Endcomma -> NAME
    Testlist_Endcomma -> NUMBER
    Testlist_Endcomma -> STRING
    Testlist_Endcomma -> ...
    Testlist_Endcomma -> None
    Testlist_Endcomma -> True
    Testlist_Endcomma -> False
    Yield_Arg -> NAME
    Yield_Arg -> NUMBER
    Yield_Arg -> STRING
    Yield_Arg -> ...
    Yield_Arg -> None
    Yield_Arg -> True
    Yield_Arg -> False
    Dict_Or_Set_Maker -> NAME
    Dict_Or_Set_Maker -> NUMBER
    Dict_Or_Set_Maker -> STRING
    Dict_Or_Set_Maker -> ...
    Dict_Or_Set_Maker -> None
    Dict_Or_Set_Maker -> True
    Dict_Or_Set_Maker -> False
    Import_As_Name -> NAME
    Dotted_Name -> NAME
    Many_Names -> NAME
    Import_As_Names_Endcomma -> NAME
    Start_Dotted_Name -> NAME
    Dotted_As_Name -> NAME
    Dotted_As_Names -> NAME
    Arguments -> Test Comp_For
    Arguments -> Double_Star_Op Test
    Arguments -> Star_Op Test
    Arguments -> Not_Bool_Op Not_Test
    Arguments -> Unary_Op Factor
    Arguments -> Atom Many_Trailers
    Arguments -> Await_Keyword Atom
    Arguments -> Open_Paren Close_Paren
    Arguments -> Open_Sq_Bracket Close_Sq_Bracket
    Arguments -> Open_Curl_Bracket Close_Curl_Bracket
    Arguments -> NAME
    Arguments -> NUMBER
    Arguments -> STRING
    Arguments -> ...
    Arguments -> None
    Arguments -> True
    Arguments -> False
    Arglist -> Test Comp_For
    Arglist -> Double_Star_Op Test
    Arglist -> Star_Op Test
    Arglist -> Not_Bool_Op Not_Test
    Arglist -> Unary_Op Factor
    Arglist -> Atom Many_Trailers
    Arglist -> Await_Keyword Atom
    Arglist -> Open_Paren Close_Paren
    Arglist -> Open_Sq_Bracket Close_Sq_Bracket
    Arglist -> Open_Curl_Bracket Close_Curl_Bracket
    Arglist -> NAME
    Arglist -> NUMBER
    Arglist -> STRING
    Arglist -> ...
    Arglist -> None
    Arglist -> True
    Arglist -> False
    Comp_Iter -> If_Keyword Test_Nocond
    Small_Stmt -> Yield_Keyword Yield_Arg
    Small_Stmt -> yield
    Small_Stmts -> Yield_Keyword Yield_Arg
    Small_Stmts -> yield
    Colon.Suite -> Colon Suite
    Parameters -> Open_Paren Typedargslist.Close_Paren
    Typedargslist.Close_Paren -> Typedargslist Close_Paren
    Typedargslist -> Many_Tfpdef Comma.Star_Double_Star_Typed
    Comma.Star_Double_Star_Typed -> Comma Star_Double_Star_Typed
    Typedargslist -> Many_Tfpdef Comma.Double_Star_Tfpdef
    Comma.Double_Star_Tfpdef -> Comma Double_Star_Tfpdef
    Star_Double_Star_Typed -> Star_Tfpdef Comma.Double_Star_Tfpdef
    Star_Tfpdef_Comma -> Comma Tfpdef_Default.Star_Tfpdef_Comma
    Tfpdef_Default.Star_Tfpdef_Comma -> Tfpdef_Default Star_Tfpdef_Comma
    Star_Tfpdef -> Star_Op Tfpdef.Star_Tfpdef_Comma
    Tfpdef.Star_Tfpdef_Comma -> Tfpdef Star_Tfpdef_Comma
    Double_Star_Tfpdef -> Double_Star_Op Tfpdef.Comma
    Tfpdef.Comma -> Tfpdef Comma
    Many_Tfpdef -> Tfpdef_Default Comma.Many_Tfpdef
    Comma.Many_Tfpdef -> Comma Many_Tfpdef
    Tfpdef_Default -> Tfpdef Assign_Op.Test
    Assign_Op.Test -> Assign_Op Test
    Varargslist -> Many_Vfpdef Comma.Star_Double_Star
    Comma.Star_Double_Star -> Comma Star_Double_Star
    Varargslist -> Many_Vfpdef Comma.Double_Star_Vfpdef
    Comma.Double_Star_Vfpdef -> Comma Double_Star_Vfpdef
    Star_Double_Star -> Star_Vfpdef Comma.Double_Star_Vfpdef
    Star_Vfpdef_Comma -> Comma Vfpdef_Default.Star_Vfpdef_Comma
    Vfpdef_Default.Star_Vfpdef_Comma -> Vfpdef_Default Star_Vfpdef_Comma
    Star_Vfpdef -> Star_Op Vfpdef.Star_Vfpdef_Comma
    Vfpdef.Star_Vfpdef_Comma -> Vfpdef Star_Vfpdef_Comma
    Double_Star_Vfpdef -> Double_Star_Op Vfpdef.Comma
    Vfpdef.Comma -> Vfpdef Comma
    Many_Vfpdef -> Vfpdef_Default Comma.Many_Vfpdef
    Comma.Many_Vfpdef -> Comma Many_Vfpdef
    Vfpdef_Default -> Vfpdef Assign_Op.Test
    Tfpdef -> Vfpdef Colon.Test
    Colon.Test -> Colon Test
    Semicolon.Newline -> Semicolon Newline
    Small_Stmts -> Small_Stmt Semicolon.Small_Stmts
    Semicolon.Small_Stmts -> Semicolon Small_Stmts
    Aug_Assign.Yield_Expr -> Aug_Assign Yield_Expr
    Aug_Assign.Testlist_Endcomma -> Aug_Assign Testlist_Endcomma
    Test_Or_Star_Exprs -> Test_Or_Star_Expr Comma.Test_Or_Star_Exprs
    Comma.Test_Or_Star_Exprs -> Comma Test_Or_Star_Exprs
    Yield_Testlist_Star_Assign_Exprs -> Assign_Op Yield_Expr.Yield_Testlist_Star_Assign_Exprs
    Yield_Expr.Yield_Testlist_Star_Assign_Exprs -> Yield_Expr Yield_Testlist_Star_Assign_Exprs
    Yield_Testlist_Star_Assign_Exprs -> Assign_Op Testlist_Star_Expr.Yield_Testlist_Star_Assign_Exprs
    Testlist_Star_Expr.Yield_Testlist_Star_Assign_Exprs -> Testlist_Star_Expr Yield_Testlist_Star_Assign_Exprs
    From_Keyword.Test -> From_Keyword Test
    Import_As_Names_Endcomma.Close_Paren -> Import_As_Names_Endcomma Close_Paren
    Import_As_Name -> Simple_Name As_Keyword.Simple_Name
    As_Keyword.Simple_Name -> As_Keyword Simple_Name
    Dotted_As_Name -> Dotted_Name As_Keyword.Simple_Name
    Comma.Import_As_Names_Endcomma -> Comma Import_As_Names_Endcomma
    Dotted_As_Names -> Dotted_As_Name Comma.Dotted_As_Names
    Comma.Dotted_As_Names -> Comma Dotted_As_Names
    Dotted_Name -> Simple_Name Dot.Dotted_Name
    Dot.Dotted_Name -> Dot Dotted_Name
    Many_Names -> Simple_Name Comma.Many_Names
    Comma.Many_Names -> Comma Many_Names
    Comma.Test -> Comma Test
    Suite.Elif_Stmt -> Suite Elif_Stmt
    Else_Stmt -> Else_Keyword Colon.Suite
    Suite.Else_Stmt -> Suite Else_Stmt
    Elif_Stmt.Else_Stmt -> Elif_Stmt Else_Stmt
    Finally_Stmt -> Finally_Keyword Colon.Suite
    Except_Stmt -> Except_Clause Colon.Suite
    Suite.Except_Stmt -> Suite Except_Stmt
    Suite.Finally_Stmt -> Suite Finally_Stmt
    Except_Stmt.Else_Stmt -> Except_Stmt Else_Stmt
    Except_Stmt.Finally_Stmt -> Except_Stmt Finally_Stmt
    Else_Stmt.Finally_Stmt -> Else_Stmt Finally_Stmt
    With_Items -> With_Item Comma.With_Items
    Comma.With_Items -> Comma With_Items
    With_Item -> Test As_Keyword.Expr
    As_Keyword.Expr -> As_Keyword Expr
    Else_Keyword.Test -> Else_Keyword Test
    Colon.Test_Nocond -> Colon Test_Nocond
    Or_Test -> Or_Test Or_Bool_Op.And_Test
    Or_Bool_Op.And_Test -> Or_Bool_Op And_Test
    And_Test -> And_Test And_Bool_Op.Not_Test
    And_Bool_Op.Not_Test -> And_Bool_Op Not_Test
    Comparison -> Comparison Comp_Op.Expr
    Comp_Op.Expr -> Comp_Op Expr
    Expr -> Expr Or_Op.Xor_Expr
    Or_Op.Xor_Expr -> Or_Op Xor_Expr
    Xor_Expr -> Xor_Expr Xor_Op.And_Expr
    Xor_Op.And_Expr -> Xor_Op And_Expr
    And_Expr -> And_Expr And_Op.Shift_Expr
    And_Op.Shift_Expr -> And_Op Shift_Expr
    Shift_Expr -> Shift_Expr Shift_Op.Arith_Expr
    Shift_Op.Arith_Expr -> Shift_Op Arith_Expr
    Arith_Expr -> Arith_Expr Arith_Op.Term
    Arith_Op.Term -> Arith_Op Term
    Term -> Term MulDiv_Op.Factor
    MulDiv_Op.Factor -> MulDiv_Op Factor
    Double_Star_Op.Factor -> Double_Star_Op Factor
    Atom_Expr -> Await_Keyword Atom.Many_Trailers
    Atom.Many_Trailers -> Atom Many_Trailers
    Atom -> Open_Paren Yield_Expr.Close_Paren
    Yield_Expr.Close_Paren -> Yield_Expr Close_Paren
    Atom -> Open_Paren Testlist_Comp.Close_Paren
    Testlist_Comp.Close_Paren -> Testlist_Comp Close_Paren
    Atom -> Open_Sq_Bracket Testlist_Comp.Close_Sq_Bracket
    Testlist_Comp.Close_Sq_Bracket -> Testlist_Comp Close_Sq_Bracket
    Atom -> Open_Curl_Bracket Dict_Or_Set_Maker.Close_Curl_Bracket
    Dict_Or_Set_Maker.Close_Curl_Bracket -> Dict_Or_Set_Maker Close_Curl_Bracket
    Trailer -> Open_Paren Arglist.Close_Paren
    Arglist.Close_Paren -> Arglist Close_Paren
    Trailer -> Open_Sq_Bracket Subscriptlist.Close_Sq_Bracket
    Subscriptlist.Close_Sq_Bracket -> Subscriptlist Close_Sq_Bracket
    Subscripts -> Subscript Comma.Subscripts
    Comma.Subscripts -> Comma Subscripts
    Subscript -> Test Colon.Test
    Subscript -> Colon Test.Sliceop
    Test.Sliceop -> Test Sliceop
    Subscript -> Test Colon.Sliceop
    Colon.Sliceop -> Colon Sliceop
    Generic_Exprs -> Generic_Expr Comma.Generic_Exprs
    Comma.Generic_Exprs -> Comma Generic_Exprs
    Comma.Testlist_Endcomma -> Comma Testlist_Endcomma
    KeyVal_Or_Unpack -> Test Colon.Test
    Many_KeyVals_Or_Unpacks -> KeyVal_Or_Unpack Comma.Many_KeyVals_Or_Unpacks
    Comma.Many_KeyVals_Or_Unpacks -> Comma Many_KeyVals_Or_Unpacks
    Arguments -> Argument Comma.Arguments
    Comma.Arguments -> Comma Arguments
    Argument -> Test Assign_Op.Test
    In_Keyword.Or_Test -> In_Keyword Or_Test
    Or_Test.Comp_Iter -> Or_Test Comp_Iter
    Test_Nocond.Comp_Iter -> Test_Nocond Comp_Iter
    Typedargslist -> Star_Tfpdef Comma.Double_Star_Tfpdef
    Star_Double_Star_Typed -> Star_Op Tfpdef.Star_Tfpdef_Comma
    Typedargslist -> Star_Op Tfpdef.Star_Tfpdef_Comma
    Typedargslist -> Double_Star_Op Tfpdef.Comma
    Typedargslist -> Tfpdef_Default Comma.Many_Tfpdef
    Many_Tfpdef -> Tfpdef Assign_Op.Test
    Typedargslist -> Tfpdef Assign_Op.Test
    Varargslist -> Star_Vfpdef Comma.Double_Star_Vfpdef
    Star_Double_Star -> Star_Op Vfpdef.Star_Vfpdef_Comma
    Varargslist -> Star_Op Vfpdef.Star_Vfpdef_Comma
    Varargslist -> Double_Star_Op Vfpdef.Comma
    Varargslist -> Vfpdef_Default Comma.Many_Vfpdef
    Many_Vfpdef -> Vfpdef Assign_Op.Test
    Varargslist -> Vfpdef Assign_Op.Test
    Tfpdef_Default -> Vfpdef Colon.Test
    Many_Tfpdef -> Vfpdef Colon.Test
    Typedargslist -> Vfpdef Colon.Test
    Suite -> Small_Stmts Semicolon.Newline
    START -> Small_Stmts Semicolon.Newline
    Small_Stmt -> Testlist_Star_Expr Aug_Assign.Yield_Expr
    Small_Stmt -> Testlist_Star_Expr Aug_Assign.Testlist_Endcomma
    Small_Stmts -> Testlist_Star_Expr Aug_Assign.Yield_Expr
    Small_Stmts -> Testlist_Star_Expr Aug_Assign.Testlist_Endcomma
    Testlist_Star_Expr -> Test_Or_Star_Expr Comma.Test_Or_Star_Exprs
    Testlist_Comp -> Test_Or_Star_Expr Comma.Test_Or_Star_Exprs
    Small_Stmt -> Test_Or_Star_Expr Comma.Test_Or_Star_Exprs
    Small_Stmts -> Test_Or_Star_Expr Comma.Test_Or_Star_Exprs
    Dotted_As_Names -> Dotted_Name As_Keyword.Simple_Name
    Import_As_Names_Endcomma -> Import_As_Name Comma.Import_As_Names_Endcomma
    Import_As_Names_Endcomma -> Simple_Name As_Keyword.Simple_Name
    Start_Dotted_Name -> Simple_Name Dot.Dotted_Name
    Dotted_As_Name -> Simple_Name Dot.Dotted_Name
    Dotted_As_Names -> Simple_Name Dot.Dotted_Name
    With_Items -> Test As_Keyword.Expr
    Test -> Lambda_Keyword Colon.Test
    Test_Or_Star_Expr -> Lambda_Keyword Colon.Test
    With_Item -> Lambda_Keyword Colon.Test
    Subscript -> Lambda_Keyword Colon.Test
    Argument -> Lambda_Keyword Colon.Test
    Test_Or_Star_Exprs -> Lambda_Keyword Colon.Test
    Testlist_Star_Expr -> Lambda_Keyword Colon.Test
    Testlist_Comp -> Lambda_Keyword Colon.Test
    Small_Stmt -> Lambda_Keyword Colon.Test
    Small_Stmts -> Lambda_Keyword Colon.Test
    With_Items -> Lambda_Keyword Colon.Test
    Test_Nocond -> Lambda_Keyword Colon.Test_Nocond
    Test -> Or_Test Or_Bool_Op.And_Test
    Test_Nocond -> Or_Test Or_Bool_Op.And_Test
    Test_Or_Star_Expr -> Or_Test Or_Bool_Op.And_Test
    With_Item -> Or_Test Or_Bool_Op.And_Test
    Subscript -> Or_Test Or_Bool_Op.And_Test
    Argument -> Or_Test Or_Bool_Op.And_Test
    Test_Or_Star_Exprs -> Or_Test Or_Bool_Op.And_Test
    Testlist_Star_Expr -> Or_Test Or_Bool_Op.And_Test
    Testlist_Comp -> Or_Test Or_Bool_Op.And_Test
    Small_Stmt -> Or_Test Or_Bool_Op.And_Test
    Small_Stmts -> Or_Test Or_Bool_Op.And_Test
    With_Items -> Or_Test Or_Bool_Op.And_Test
    Or_Test -> And_Test And_Bool_Op.Not_Test
    Test -> And_Test And_Bool_Op.Not_Test
    Test_Nocond -> And_Test And_Bool_Op.Not_Test
    Test_Or_Star_Expr -> And_Test And_Bool_Op.Not_Test
    With_Item -> And_Test And_Bool_Op.Not_Test
    Subscript -> And_Test And_Bool_Op.Not_Test
    Argument -> And_Test And_Bool_Op.Not_Test
    Test_Or_Star_Exprs -> And_Test And_Bool_Op.Not_Test
    Testlist_Star_Expr -> And_Test And_Bool_Op.Not_Test
    Testlist_Comp -> And_Test And_Bool_Op.Not_Test
    Small_Stmt -> And_Test And_Bool_Op.Not_Test
    Small_Stmts -> And_Test And_Bool_Op.Not_Test
    With_Items -> And_Test And_Bool_Op.Not_Test
    Not_Test -> Comparison Comp_Op.Expr
    And_Test -> Comparison Comp_Op.Expr
    Or_Test -> Comparison Comp_Op.Expr
    Test -> Comparison Comp_Op.Expr
    Test_Nocond -> Comparison Comp_Op.Expr
    Test_Or_Star_Expr -> Comparison Comp_Op.Expr
    With_Item -> Comparison Comp_Op.Expr
    Subscript -> Comparison Comp_Op.Expr
    Argument -> Comparison Comp_Op.Expr
    Test_Or_Star_Exprs -> Comparison Comp_Op.Expr
    Testlist_Star_Expr -> Comparison Comp_Op.Expr
    Testlist_Comp -> Comparison Comp_Op.Expr
    Small_Stmt -> Comparison Comp_Op.Expr
    Small_Stmts -> Comparison Comp_Op.Expr
    With_Items -> Comparison Comp_Op.Expr
    Comparison -> Expr Or_Op.Xor_Expr
    Generic_Expr -> Expr Or_Op.Xor_Expr
    Not_Test -> Expr Or_Op.Xor_Expr
    And_Test -> Expr Or_Op.Xor_Expr
    Or_Test -> Expr Or_Op.Xor_Expr
    Test -> Expr Or_Op.Xor_Expr
    Test_Nocond -> Expr Or_Op.Xor_Expr
    Test_Or_Star_Expr -> Expr Or_Op.Xor_Expr
    With_Item -> Expr Or_Op.Xor_Expr
    Subscript -> Expr Or_Op.Xor_Expr
    Argument -> Expr Or_Op.Xor_Expr
    Test_Or_Star_Exprs -> Expr Or_Op.Xor_Expr
    Testlist_Star_Expr -> Expr Or_Op.Xor_Expr
    Testlist_Comp -> Expr Or_Op.Xor_Expr
    Small_Stmt -> Expr Or_Op.Xor_Expr
    Small_Stmts -> Expr Or_Op.Xor_Expr
    With_Items -> Expr Or_Op.Xor_Expr
    Expr -> Xor_Expr Xor_Op.And_Expr
    Comparison -> Xor_Expr Xor_Op.And_Expr
    Generic_Expr -> Xor_Expr Xor_Op.And_Expr
    Not_Test -> Xor_Expr Xor_Op.And_Expr
    And_Test -> Xor_Expr Xor_Op.And_Expr
    Or_Test -> Xor_Expr Xor_Op.And_Expr
    Test -> Xor_Expr Xor_Op.And_Expr
    Test_Nocond -> Xor_Expr Xor_Op.And_Expr
    Test_Or_Star_Expr -> Xor_Expr Xor_Op.And_Expr
    With_Item -> Xor_Expr Xor_Op.And_Expr
    Subscript -> Xor_Expr Xor_Op.And_Expr
    Argument -> Xor_Expr Xor_Op.And_Expr
    Test_Or_Star_Exprs -> Xor_Expr Xor_Op.And_Expr
    Testlist_Star_Expr -> Xor_Expr Xor_Op.And_Expr
    Testlist_Comp -> Xor_Expr Xor_Op.And_Expr
    Small_Stmt -> Xor_Expr Xor_Op.And_Expr
    Small_Stmts -> Xor_Expr Xor_Op.And_Expr
    With_Items -> Xor_Expr Xor_Op.And_Expr
    Xor_Expr -> And_Expr And_Op.Shift_Expr
    Expr -> And_Expr And_Op.Shift_Expr
    Comparison -> And_Expr And_Op.Shift_Expr
    Generic_Expr -> And_Expr And_Op.Shift_Expr
    Not_Test -> And_Expr And_Op.Shift_Expr
    And_Test -> And_Expr And_Op.Shift_Expr
    Or_Test -> And_Expr And_Op.Shift_Expr
    Test -> And_Expr And_Op.Shift_Expr
    Test_Nocond -> And_Expr And_Op.Shift_Expr
    Test_Or_Star_Expr -> And_Expr And_Op.Shift_Expr
    With_Item -> And_Expr And_Op.Shift_Expr
    Subscript -> And_Expr And_Op.Shift_Expr
    Argument -> And_Expr And_Op.Shift_Expr
    Test_Or_Star_Exprs -> And_Expr And_Op.Shift_Expr
    Testlist_Star_Expr -> And_Expr And_Op.Shift_Expr
    Testlist_Comp -> And_Expr And_Op.Shift_Expr
    Small_Stmt -> And_Expr And_Op.Shift_Expr
    Small_Stmts -> And_Expr And_Op.Shift_Expr
    With_Items -> And_Expr And_Op.Shift_Expr
    And_Expr -> Shift_Expr Shift_Op.Arith_Expr
    Xor_Expr -> Shift_Expr Shift_Op.Arith_Expr
    Expr -> Shift_Expr Shift_Op.Arith_Expr
    Comparison -> Shift_Expr Shift_Op.Arith_Expr
    Generic_Expr -> Shift_Expr Shift_Op.Arith_Expr
    Not_Test -> Shift_Expr Shift_Op.Arith_Expr
    And_Test -> Shift_Expr Shift_Op.Arith_Expr
    Or_Test -> Shift_Expr Shift_Op.Arith_Expr
    Test -> Shift_Expr Shift_Op.Arith_Expr
    Test_Nocond -> Shift_Expr Shift_Op.Arith_Expr
    Test_Or_Star_Expr -> Shift_Expr Shift_Op.Arith_Expr
    With_Item -> Shift_Expr Shift_Op.Arith_Expr
    Subscript -> Shift_Expr Shift_Op.Arith_Expr
    Argument -> Shift_Expr Shift_Op.Arith_Expr
    Test_Or_Star_Exprs -> Shift_Expr Shift_Op.Arith_Expr
    Testlist_Star_Expr -> Shift_Expr Shift_Op.Arith_Expr
    Testlist_Comp -> Shift_Expr Shift_Op.Arith_Expr
    Small_Stmt -> Shift_Expr Shift_Op.Arith_Expr
    Small_Stmts -> Shift_Expr Shift_Op.Arith_Expr
    With_Items -> Shift_Expr Shift_Op.Arith_Expr
    Shift_Expr -> Arith_Expr Arith_Op.Term
    And_Expr -> Arith_Expr Arith_Op.Term
    Xor_Expr -> Arith_Expr Arith_Op.Term
    Expr -> Arith_Expr Arith_Op.Term
    Comparison -> Arith_Expr Arith_Op.Term
    Generic_Expr -> Arith_Expr Arith_Op.Term
    Not_Test -> Arith_Expr Arith_Op.Term
    And_Test -> Arith_Expr Arith_Op.Term
    Or_Test -> Arith_Expr Arith_Op.Term
    Test -> Arith_Expr Arith_Op.Term
    Test_Nocond -> Arith_Expr Arith_Op.Term
    Test_Or_Star_Expr -> Arith_Expr Arith_Op.Term
    With_Item -> Arith_Expr Arith_Op.Term
    Subscript -> Arith_Expr Arith_Op.Term
    Argument -> Arith_Expr Arith_Op.Term
    Test_Or_Star_Exprs -> Arith_Expr Arith_Op.Term
    Testlist_Star_Expr -> Arith_Expr Arith_Op.Term
    Testlist_Comp -> Arith_Expr Arith_Op.Term
    Small_Stmt -> Arith_Expr Arith_Op.Term
    Small_Stmts -> Arith_Expr Arith_Op.Term
    With_Items -> Arith_Expr Arith_Op.Term
    Arith_Expr -> Term MulDiv_Op.Factor
    Shift_Expr -> Term MulDiv_Op.Factor
    And_Expr -> Term MulDiv_Op.Factor
    Xor_Expr -> Term MulDiv_Op.Factor
    Expr -> Term MulDiv_Op.Factor
    Comparison -> Term MulDiv_Op.Factor
    Generic_Expr -> Term MulDiv_Op.Factor
    Not_Test -> Term MulDiv_Op.Factor
    And_Test -> Term MulDiv_Op.Factor
    Or_Test -> Term MulDiv_Op.Factor
    Test -> Term MulDiv_Op.Factor
    Test_Nocond -> Term MulDiv_Op.Factor
    Test_Or_Star_Expr -> Term MulDiv_Op.Factor
    With_Item -> Term MulDiv_Op.Factor
    Subscript -> Term MulDiv_Op.Factor
    Argument -> Term MulDiv_Op.Factor
    Test_Or_Star_Exprs -> Term MulDiv_Op.Factor
    Testlist_Star_Expr -> Term MulDiv_Op.Factor
    Testlist_Comp -> Term MulDiv_Op.Factor
    Small_Stmt -> Term MulDiv_Op.Factor
    Small_Stmts -> Term MulDiv_Op.Factor
    With_Items -> Term MulDiv_Op.Factor
    Factor -> Atom_Expr Double_Star_Op.Factor
    Term -> Atom_Expr Double_Star_Op.Factor
    Arith_Expr -> Atom_Expr Double_Star_Op.Factor
    Shift_Expr -> Atom_Expr Double_Star_Op.Factor
    And_Expr -> Atom_Expr Double_Star_Op.Factor
    Xor_Expr -> Atom_Expr Double_Star_Op.Factor
    Expr -> Atom_Expr Double_Star_Op.Factor
    Comparison -> Atom_Expr Double_Star_Op.Factor
    Generic_Expr -> Atom_Expr Double_Star_Op.Factor
    Not_Test -> Atom_Expr Double_Star_Op.Factor
    And_Test -> Atom_Expr Double_Star_Op.Factor
    Or_Test -> Atom_Expr Double_Star_Op.Factor
    Test -> Atom_Expr Double_Star_Op.Factor
    Test_Nocond -> Atom_Expr Double_Star_Op.Factor
    Test_Or_Star_Expr -> Atom_Expr Double_Star_Op.Factor
    With_Item -> Atom_Expr Double_Star_Op.Factor
    Subscript -> Atom_Expr Double_Star_Op.Factor
    Argument -> Atom_Expr Double_Star_Op.Factor
    Test_Or_Star_Exprs -> Atom_Expr Double_Star_Op.Factor
    Testlist_Star_Expr -> Atom_Expr Double_Star_Op.Factor
    Testlist_Comp -> Atom_Expr Double_Star_Op.Factor
    Small_Stmt -> Atom_Expr Double_Star_Op.Factor
    Small_Stmts -> Atom_Expr Double_Star_Op.Factor
    With_Items -> Atom_Expr Double_Star_Op.Factor
    Factor -> Await_Keyword Atom.Many_Trailers
    Term -> Await_Keyword Atom.Many_Trailers
    Arith_Expr -> Await_Keyword Atom.Many_Trailers
    Shift_Expr -> Await_Keyword Atom.Many_Trailers
    And_Expr -> Await_Keyword Atom.Many_Trailers
    Xor_Expr -> Await_Keyword Atom.Many_Trailers
    Expr -> Await_Keyword Atom.Many_Trailers
    Comparison -> Await_Keyword Atom.Many_Trailers
    Generic_Expr -> Await_Keyword Atom.Many_Trailers
    Not_Test -> Await_Keyword Atom.Many_Trailers
    And_Test -> Await_Keyword Atom.Many_Trailers
    Or_Test -> Await_Keyword Atom.Many_Trailers
    Test -> Await_Keyword Atom.Many_Trailers
    Test_Nocond -> Await_Keyword Atom.Many_Trailers
    Test_Or_Star_Expr -> Await_Keyword Atom.Many_Trailers
    With_Item -> Await_Keyword Atom.Many_Trailers
    Subscript -> Await_Keyword Atom.Many_Trailers
    Argument -> Await_Keyword Atom.Many_Trailers
    Test_Or_Star_Exprs -> Await_Keyword Atom.Many_Trailers
    Testlist_Star_Expr -> Await_Keyword Atom.Many_Trailers
    Testlist_Comp -> Await_Keyword Atom.Many_Trailers
    Small_Stmt -> Await_Keyword Atom.Many_Trailers
    Small_Stmts -> Await_Keyword Atom.Many_Trailers
    With_Items -> Await_Keyword Atom.Many_Trailers
    Atom_Expr -> Open_Paren Yield_Expr.Close_Paren
    Atom_Expr -> Open_Paren Testlist_Comp.Close_Paren
    Atom_Expr -> Open_Sq_Bracket Testlist_Comp.Close_Sq_Bracket
    Atom_Expr -> Open_Curl_Bracket Dict_Or_Set_Maker.Close_Curl_Bracket
    Factor -> Open_Paren Yield_Expr.Close_Paren
    Factor -> Open_Paren Testlist_Comp.Close_Paren
    Factor -> Open_Sq_Bracket Testlist_Comp.Close_Sq_Bracket
    Factor -> Open_Curl_Bracket Dict_Or_Set_Maker.Close_Curl_Bracket
    Term -> Open_Paren Yield_Expr.Close_Paren
    Term -> Open_Paren Testlist_Comp.Close_Paren
    Term -> Open_Sq_Bracket Testlist_Comp.Close_Sq_Bracket
    Term -> Open_Curl_Bracket Dict_Or_Set_Maker.Close_Curl_Bracket
    Arith_Expr -> Open_Paren Yield_Expr.Close_Paren
    Arith_Expr -> Open_Paren Testlist_Comp.Close_Paren
    Arith_Expr -> Open_Sq_Bracket Testlist_Comp.Close_Sq_Bracket
    Arith_Expr -> Open_Curl_Bracket Dict_Or_Set_Maker.Close_Curl_Bracket
    Shift_Expr -> Open_Paren Yield_Expr.Close_Paren
    Shift_Expr -> Open_Paren Testlist_Comp.Close_Paren
    Shift_Expr -> Open_Sq_Bracket Testlist_Comp.Close_Sq_Bracket
    Shift_Expr -> Open_Curl_Bracket Dict_Or_Set_Maker.Close_Curl_Bracket
    And_Expr -> Open_Paren Yield_Expr.Close_Paren
    And_Expr -> Open_Paren Testlist_Comp.Close_Paren
    And_Expr -> Open_Sq_Bracket Testlist_Comp.Close_Sq_Bracket
    And_Expr -> Open_Curl_Bracket Dict_Or_Set_Maker.Close_Curl_Bracket
    Xor_Expr -> Open_Paren Yield_Expr.Close_Paren
    Xor_Expr -> Open_Paren Testlist_Comp.Close_Paren
    Xor_Expr -> Open_Sq_Bracket Testlist_Comp.Close_Sq_Bracket
    Xor_Expr -> Open_Curl_Bracket Dict_Or_Set_Maker.Close_Curl_Bracket
    Expr -> Open_Paren Yield_Expr.Close_Paren
    Expr -> Open_Paren Testlist_Comp.Close_Paren
    Expr -> Open_Sq_Bracket Testlist_Comp.Close_Sq_Bracket
    Expr -> Open_Curl_Bracket Dict_Or_Set_Maker.Close_Curl_Bracket
    Comparison -> Open_Paren Yield_Expr.Close_Paren
    Comparison -> Open_Paren Testlist_Comp.Close_Paren
    Comparison -> Open_Sq_Bracket Testlist_Comp.Close_Sq_Bracket
    Comparison -> Open_Curl_Bracket Dict_Or_Set_Maker.Close_Curl_Bracket
    Generic_Expr -> Open_Paren Yield_Expr.Close_Paren
    Generic_Expr -> Open_Paren Testlist_Comp.Close_Paren
    Generic_Expr -> Open_Sq_Bracket Testlist_Comp.Close_Sq_Bracket
    Generic_Expr -> Open_Curl_Bracket Dict_Or_Set_Maker.Close_Curl_Bracket
    Not_Test -> Open_Paren Yield_Expr.Close_Paren
    Not_Test -> Open_Paren Testlist_Comp.Close_Paren
    Not_Test -> Open_Sq_Bracket Testlist_Comp.Close_Sq_Bracket
    Not_Test -> Open_Curl_Bracket Dict_Or_Set_Maker.Close_Curl_Bracket
    And_Test -> Open_Paren Yield_Expr.Close_Paren
    And_Test -> Open_Paren Testlist_Comp.Close_Paren
    And_Test -> Open_Sq_Bracket Testlist_Comp.Close_Sq_Bracket
    And_Test -> Open_Curl_Bracket Dict_Or_Set_Maker.Close_Curl_Bracket
    Or_Test -> Open_Paren Yield_Expr.Close_Paren
    Or_Test -> Open_Paren Testlist_Comp.Close_Paren
    Or_Test -> Open_Sq_Bracket Testlist_Comp.Close_Sq_Bracket
    Or_Test -> Open_Curl_Bracket Dict_Or_Set_Maker.Close_Curl_Bracket
    Test -> Open_Paren Yield_Expr.Close_Paren
    Test -> Open_Paren Testlist_Comp.Close_Paren
    Test -> Open_Sq_Bracket Testlist_Comp.Close_Sq_Bracket
    Test -> Open_Curl_Bracket Dict_Or_Set_Maker.Close_Curl_Bracket
    Test_Nocond -> Open_Paren Yield_Expr.Close_Paren
    Test_Nocond -> Open_Paren Testlist_Comp.Close_Paren
    Test_Nocond -> Open_Sq_Bracket Testlist_Comp.Close_Sq_Bracket
    Test_Nocond -> Open_Curl_Bracket Dict_Or_Set_Maker.Close_Curl_Bracket
    Test_Or_Star_Expr -> Open_Paren Yield_Expr.Close_Paren
    Test_Or_Star_Expr -> Open_Paren Testlist_Comp.Close_Paren
    Test_Or_Star_Expr -> Open_Sq_Bracket Testlist_Comp.Close_Sq_Bracket
    Test_Or_Star_Expr -> Open_Curl_Bracket Dict_Or_Set_Maker.Close_Curl_Bracket
    With_Item -> Open_Paren Yield_Expr.Close_Paren
    With_Item -> Open_Paren Testlist_Comp.Close_Paren
    With_Item -> Open_Sq_Bracket Testlist_Comp.Close_Sq_Bracket
    With_Item -> Open_Curl_Bracket Dict_Or_Set_Maker.Close_Curl_Bracket
    Subscript -> Open_Paren Yield_Expr.Close_Paren
    Subscript -> Open_Paren Testlist_Comp.Close_Paren
    Subscript -> Open_Sq_Bracket Testlist_Comp.Close_Sq_Bracket
    Subscript -> Open_Curl_Bracket Dict_Or_Set_Maker.Close_Curl_Bracket
    Argument -> Open_Paren Yield_Expr.Close_Paren
    Argument -> Open_Paren Testlist_Comp.Close_Paren
    Argument -> Open_Sq_Bracket Testlist_Comp.Close_Sq_Bracket
    Argument -> Open_Curl_Bracket Dict_Or_Set_Maker.Close_Curl_Bracket
    Test_Or_Star_Exprs -> Open_Paren Yield_Expr.Close_Paren
    Test_Or_Star_Exprs -> Open_Paren Testlist_Comp.Close_Paren
    Test_Or_Star_Exprs -> Open_Sq_Bracket Testlist_Comp.Close_Sq_Bracket
    Test_Or_Star_Exprs -> Open_Curl_Bracket Dict_Or_Set_Maker.Close_Curl_Bracket
    Testlist_Star_Expr -> Open_Paren Yield_Expr.Close_Paren
    Testlist_Star_Expr -> Open_Paren Testlist_Comp.Close_Paren
    Testlist_Star_Expr -> Open_Sq_Bracket Testlist_Comp.Close_Sq_Bracket
    Testlist_Star_Expr -> Open_Curl_Bracket Dict_Or_Set_Maker.Close_Curl_Bracket
    Testlist_Comp -> Open_Paren Yield_Expr.Close_Paren
    Testlist_Comp -> Open_Paren Testlist_Comp.Close_Paren
    Testlist_Comp -> Open_Sq_Bracket Testlist_Comp.Close_Sq_Bracket
    Testlist_Comp -> Open_Curl_Bracket Dict_Or_Set_Maker.Close_Curl_Bracket
    Small_Stmt -> Open_Paren Yield_Expr.Close_Paren
    Small_Stmt -> Open_Paren Testlist_Comp.Close_Paren
    Small_Stmt -> Open_Sq_Bracket Testlist_Comp.Close_Sq_Bracket
    Small_Stmt -> Open_Curl_Bracket Dict_Or_Set_Maker.Close_Curl_Bracket
    Small_Stmts -> Open_Paren Yield_Expr.Close_Paren
    Small_Stmts -> Open_Paren Testlist_Comp.Close_Paren
    Small_Stmts -> Open_Sq_Bracket Testlist_Comp.Close_Sq_Bracket
    Small_Stmts -> Open_Curl_Bracket Dict_Or_Set_Maker.Close_Curl_Bracket
    With_Items -> Open_Paren Yield_Expr.Close_Paren
    With_Items -> Open_Paren Testlist_Comp.Close_Paren
    With_Items -> Open_Sq_Bracket Testlist_Comp.Close_Sq_Bracket
    With_Items -> Open_Curl_Bracket Dict_Or_Set_Maker.Close_Curl_Bracket
    Many_Trailers -> Open_Paren Arglist.Close_Paren
    Many_Trailers -> Open_Sq_Bracket Subscriptlist.Close_Sq_Bracket
    Subscriptlist -> Subscript Comma.Subscripts
    Subscripts -> Test Colon.Test
    Subscripts -> Colon Test.Sliceop
    Subscripts -> Test Colon.Sliceop
    Subscripts -> Lambda_Keyword Colon.Test
    Subscripts -> Or_Test Or_Bool_Op.And_Test
    Subscripts -> And_Test And_Bool_Op.Not_Test
    Subscripts -> Comparison Comp_Op.Expr
    Subscripts -> Expr Or_Op.Xor_Expr
    Subscripts -> Xor_Expr Xor_Op.And_Expr
    Subscripts -> And_Expr And_Op.Shift_Expr
    Subscripts -> Shift_Expr Shift_Op.Arith_Expr
    Subscripts -> Arith_Expr Arith_Op.Term
    Subscripts -> Term MulDiv_Op.Factor
    Subscripts -> Atom_Expr Double_Star_Op.Factor
    Subscripts -> Await_Keyword Atom.Many_Trailers
    Subscripts -> Open_Paren Yield_Expr.Close_Paren
    Subscripts -> Open_Paren Testlist_Comp.Close_Paren
    Subscripts -> Open_Sq_Bracket Testlist_Comp.Close_Sq_Bracket
    Subscripts -> Open_Curl_Bracket Dict_Or_Set_Maker.Close_Curl_Bracket
    Subscriptlist -> Test Colon.Test
    Subscriptlist -> Colon Test.Sliceop
    Subscriptlist -> Test Colon.Sliceop
    Subscriptlist -> Lambda_Keyword Colon.Test
    Subscriptlist -> Or_Test Or_Bool_Op.And_Test
    Subscriptlist -> And_Test And_Bool_Op.Not_Test
    Subscriptlist -> Comparison Comp_Op.Expr
    Subscriptlist -> Expr Or_Op.Xor_Expr
    Subscriptlist -> Xor_Expr Xor_Op.And_Expr
    Subscriptlist -> And_Expr And_Op.Shift_Expr
    Subscriptlist -> Shift_Expr Shift_Op.Arith_Expr
    Subscriptlist -> Arith_Expr Arith_Op.Term
    Subscriptlist -> Term MulDiv_Op.Factor
    Subscriptlist -> Atom_Expr Double_Star_Op.Factor
    Subscriptlist -> Await_Keyword Atom.Many_Trailers
    Subscriptlist -> Open_Paren Yield_Expr.Close_Paren
    Subscriptlist -> Open_Paren Testlist_Comp.Close_Paren
    Subscriptlist -> Open_Sq_Bracket Testlist_Comp.Close_Sq_Bracket
    Subscriptlist -> Open_Curl_Bracket Dict_Or_Set_Maker.Close_Curl_Bracket
    Generic_Exprs -> Expr Or_Op.Xor_Expr
    Generic_Exprs -> Xor_Expr Xor_Op.And_Expr
    Generic_Exprs -> And_Expr And_Op.Shift_Expr
    Generic_Exprs -> Shift_Expr Shift_Op.Arith_Expr
    Generic_Exprs -> Arith_Expr Arith_Op.Term
    Generic_Exprs -> Term MulDiv_Op.Factor
    Generic_Exprs -> Atom_Expr Double_Star_Op.Factor
    Generic_Exprs -> Await_Keyword Atom.Many_Trailers
    Generic_Exprs -> Open_Paren Yield_Expr.Close_Paren
    Generic_Exprs -> Open_Paren Testlist_Comp.Close_Paren
    Generic_Exprs -> Open_Sq_Bracket Testlist_Comp.Close_Sq_Bracket
    Generic_Exprs -> Open_Curl_Bracket Dict_Or_Set_Maker.Close_Curl_Bracket
    Exprlist -> Generic_Expr Comma.Generic_Exprs
    Exprlist -> Expr Or_Op.Xor_Expr
    Exprlist -> Xor_Expr Xor_Op.And_Expr
    Exprlist -> And_Expr And_Op.Shift_Expr
    Exprlist -> Shift_Expr Shift_Op.Arith_Expr
    Exprlist -> Arith_Expr Arith_Op.Term
    Exprlist -> Term MulDiv_Op.Factor
    Exprlist -> Atom_Expr Double_Star_Op.Factor
    Exprlist -> Await_Keyword Atom.Many_Trailers
    Exprlist -> Open_Paren Yield_Expr.Close_Paren
    Exprlist -> Open_Paren Testlist_Comp.Close_Paren
    Exprlist -> Open_Sq_Bracket Testlist_Comp.Close_Sq_Bracket
    Exprlist -> Open_Curl_Bracket Dict_Or_Set_Maker.Close_Curl_Bracket
    Testlist_Endcomma -> Test Comma.Testlist_Endcomma
    Testlist_Endcomma -> Lambda_Keyword Colon.Test
    Testlist_Endcomma -> Or_Test Or_Bool_Op.And_Test
    Testlist_Endcomma -> And_Test And_Bool_Op.Not_Test
    Testlist_Endcomma -> Comparison Comp_Op.Expr
    Testlist_Endcomma -> Expr Or_Op.Xor_Expr
    Testlist_Endcomma -> Xor_Expr Xor_Op.And_Expr
    Testlist_Endcomma -> And_Expr And_Op.Shift_Expr
    Testlist_Endcomma -> Shift_Expr Shift_Op.Arith_Expr
    Testlist_Endcomma -> Arith_Expr Arith_Op.Term
    Testlist_Endcomma -> Term MulDiv_Op.Factor
    Testlist_Endcomma -> Atom_Expr Double_Star_Op.Factor
    Testlist_Endcomma -> Await_Keyword Atom.Many_Trailers
    Testlist_Endcomma -> Open_Paren Yield_Expr.Close_Paren
    Testlist_Endcomma -> Open_Paren Testlist_Comp.Close_Paren
    Testlist_Endcomma -> Open_Sq_Bracket Testlist_Comp.Close_Sq_Bracket
    Testlist_Endcomma -> Open_Curl_Bracket Dict_Or_Set_Maker.Close_Curl_Bracket
    Yield_Arg -> Test Comma.Testlist_Endcomma
    Yield_Arg -> Lambda_Keyword Colon.Test
    Yield_Arg -> Or_Test Or_Bool_Op.And_Test
    Yield_Arg -> And_Test And_Bool_Op.Not_Test
    Yield_Arg -> Comparison Comp_Op.Expr
    Yield_Arg -> Expr Or_Op.Xor_Expr
    Yield_Arg -> Xor_Expr Xor_Op.And_Expr
    Yield_Arg -> And_Expr And_Op.Shift_Expr
    Yield_Arg -> Shift_Expr Shift_Op.Arith_Expr
    Yield_Arg -> Arith_Expr Arith_Op.Term
    Yield_Arg -> Term MulDiv_Op.Factor
    Yield_Arg -> Atom_Expr Double_Star_Op.Factor
    Yield_Arg -> Await_Keyword Atom.Many_Trailers
    Yield_Arg -> Open_Paren Yield_Expr.Close_Paren
    Yield_Arg -> Open_Paren Testlist_Comp.Close_Paren
    Yield_Arg -> Open_Sq_Bracket Testlist_Comp.Close_Sq_Bracket
    Yield_Arg -> Open_Curl_Bracket Dict_Or_Set_Maker.Close_Curl_Bracket
    Many_KeyVals_Or_Unpacks -> Test Colon.Test
    Dict_Or_Set_Maker -> KeyVal_Or_Unpack Comma.Many_KeyVals_Or_Unpacks
    Dict_Or_Set_Maker -> Test Colon.Test
    Dict_Or_Set_Maker -> Test_Or_Star_Expr Comma.Test_Or_Star_Exprs
    Dict_Or_Set_Maker -> Lambda_Keyword Colon.Test
    Dict_Or_Set_Maker -> Or_Test Or_Bool_Op.And_Test
    Dict_Or_Set_Maker -> And_Test And_Bool_Op.Not_Test
    Dict_Or_Set_Maker -> Comparison Comp_Op.Expr
    Dict_Or_Set_Maker -> Expr Or_Op.Xor_Expr
    Dict_Or_Set_Maker -> Xor_Expr Xor_Op.And_Expr
    Dict_Or_Set_Maker -> And_Expr And_Op.Shift_Expr
    Dict_Or_Set_Maker -> Shift_Expr Shift_Op.Arith_Expr
    Dict_Or_Set_Maker -> Arith_Expr Arith_Op.Term
    Dict_Or_Set_Maker -> Term MulDiv_Op.Factor
    Dict_Or_Set_Maker -> Atom_Expr Double_Star_Op.Factor
    Dict_Or_Set_Maker -> Await_Keyword Atom.Many_Trailers
    Dict_Or_Set_Maker -> Open_Paren Yield_Expr.Close_Paren
    Dict_Or_Set_Maker -> Open_Paren Testlist_Comp.Close_Paren
    Dict_Or_Set_Maker -> Open_Sq_Bracket Testlist_Comp.Close_Sq_Bracket
    Dict_Or_Set_Maker -> Open_Curl_Bracket Dict_Or_Set_Maker.Close_Curl_Bracket
    Arglist -> Argument Comma.Arguments
    Arguments -> Test Assign_Op.Test
    Arguments -> Lambda_Keyword Colon.Test
    Arguments -> Or_Test Or_Bool_Op.And_Test
    Arguments -> And_Test And_Bool_Op.Not_Test
    Arguments -> Comparison Comp_Op.Expr
    Arguments -> Expr Or_Op.Xor_Expr
    Arguments -> Xor_Expr Xor_Op.And_Expr
    Arguments -> And_Expr And_Op.Shift_Expr
    Arguments -> Shift_Expr Shift_Op.Arith_Expr
    Arguments -> Arith_Expr Arith_Op.Term
    Arguments -> Term MulDiv_Op.Factor
    Arguments -> Atom_Expr Double_Star_Op.Factor
    Arguments -> Await_Keyword Atom.Many_Trailers
    Arguments -> Open_Paren Yield_Expr.Close_Paren
    Arguments -> Open_Paren Testlist_Comp.Close_Paren
    Arguments -> Open_Sq_Bracket Testlist_Comp.Close_Sq_Bracket
    Arguments -> Open_Curl_Bracket Dict_Or_Set_Maker.Close_Curl_Bracket
    Arglist -> Test Assign_Op.Test
    Arglist -> Lambda_Keyword Colon.Test
    Arglist -> Or_Test Or_Bool_Op.And_Test
    Arglist -> And_Test And_Bool_Op.Not_Test
    Arglist -> Comparison Comp_Op.Expr
    Arglist -> Expr Or_Op.Xor_Expr
    Arglist -> Xor_Expr Xor_Op.And_Expr
    Arglist -> And_Expr And_Op.Shift_Expr
    Arglist -> Shift_Expr Shift_Op.Arith_Expr
    Arglist -> Arith_Expr Arith_Op.Term
    Arglist -> Term MulDiv_Op.Factor
    Arglist -> Atom_Expr Double_Star_Op.Factor
    Arglist -> Await_Keyword Atom.Many_Trailers
    Arglist -> Open_Paren Yield_Expr.Close_Paren
    Arglist -> Open_Paren Testlist_Comp.Close_Paren
    Arglist -> Open_Sq_Bracket Testlist_Comp.Close_Sq_Bracket
    Arglist -> Open_Curl_Bracket Dict_Or_Set_Maker.Close_Curl_Bracket
    Comp_Iter -> If_Keyword Test_Nocond.Comp_Iter
    Parameters.Colon.Suite -> Parameters Colon.Suite
    Test.Colon.Suite -> Test Colon.Suite
    Annotated_Assign -> Colon Test.Assign_Op.Test
    Test.Assign_Op.Test -> Test Assign_Op.Test
    Test.From_Keyword.Test -> Test From_Keyword.Test
    Import_From_Imports -> Import_Keyword Open_Paren.Import_As_Names_Endcomma.Close_Paren
    Open_Paren.Import_As_Names_Endcomma.Close_Paren -> Open_Paren Import_As_Names_Endcomma.Close_Paren
    Test.Comma.Test -> Test Comma.Test
    Elif_Stmt -> Elif_Keyword Test.Colon.Suite
    Colon.Suite.Elif_Stmt -> Colon Suite.Elif_Stmt
    Colon.Suite.Else_Stmt -> Colon Suite.Else_Stmt
    Suite.Elif_Stmt.Else_Stmt -> Suite Elif_Stmt.Else_Stmt
    Testlist_Endcomma.Colon.Suite -> Testlist_Endcomma Colon.Suite
    Except_Stmt -> Except_Clause Colon.Suite.Except_Stmt
    Colon.Suite.Except_Stmt -> Colon Suite.Except_Stmt
    Colon.Suite.Finally_Stmt -> Colon Suite.Finally_Stmt
    Suite.Except_Stmt.Else_Stmt -> Suite Except_Stmt.Else_Stmt
    Suite.Except_Stmt.Finally_Stmt -> Suite Except_Stmt.Finally_Stmt
    Except_Stmt.Else_Stmt.Finally_Stmt -> Except_Stmt Else_Stmt.Finally_Stmt
    With_Stmt -> With_Keyword With_Items.Colon.Suite
    With_Items.Colon.Suite -> With_Items Colon.Suite
    Except_Clause -> Except_Keyword Test.As_Keyword.Simple_Name
    Test.As_Keyword.Simple_Name -> Test As_Keyword.Simple_Name
    Or_Test.Else_Keyword.Test -> Or_Test Else_Keyword.Test
    Varargslist.Colon.Test -> Varargslist Colon.Test
    Varargslist.Colon.Test_Nocond -> Varargslist Colon.Test_Nocond
    Subscript -> Test Colon.Test.Sliceop
    Colon.Test.Sliceop -> Colon Test.Sliceop
    Simple_Name.Colon.Suite -> Simple_Name Colon.Suite
    Close_Paren.Colon.Suite -> Close_Paren Colon.Suite
    Comp_For -> For_Keyword Exprlist.In_Keyword.Or_Test
    Exprlist.In_Keyword.Or_Test -> Exprlist In_Keyword.Or_Test
    In_Keyword.Or_Test.Comp_Iter -> In_Keyword Or_Test.Comp_Iter
    Small_Stmt -> Raise_Keyword Test.From_Keyword.Test
    Small_Stmts -> Raise_Keyword Test.From_Keyword.Test
    Small_Stmt -> Assert_Keyword Test.Comma.Test
    Small_Stmts -> Assert_Keyword Test.Comma.Test
    START -> If_Keyword Test.Colon.Suite
    START -> While_Keyword Test.Colon.Suite
    START -> Try_Keyword Colon.Suite.Finally_Stmt
    START -> Try_Keyword Colon.Suite.Except_Stmt
    START -> With_Keyword With_Items.Colon.Suite
    Test -> Lambda_Keyword Varargslist.Colon.Test
    Test_Or_Star_Expr -> Lambda_Keyword Varargslist.Colon.Test
    With_Item -> Lambda_Keyword Varargslist.Colon.Test
    Subscript -> Lambda_Keyword Varargslist.Colon.Test
    Argument -> Lambda_Keyword Varargslist.Colon.Test
    Test_Or_Star_Exprs -> Lambda_Keyword Varargslist.Colon.Test
    Testlist_Star_Expr -> Lambda_Keyword Varargslist.Colon.Test
    Testlist_Comp -> Lambda_Keyword Varargslist.Colon.Test
    Small_Stmt -> Lambda_Keyword Varargslist.Colon.Test
    Small_Stmts -> Lambda_Keyword Varargslist.Colon.Test
    With_Items -> Lambda_Keyword Varargslist.Colon.Test
    Test_Nocond -> Lambda_Keyword Varargslist.Colon.Test_Nocond
    Subscripts -> Test Colon.Test.Sliceop
    Subscripts -> Lambda_Keyword Varargslist.Colon.Test
    Subscriptlist -> Test Colon.Test.Sliceop
    Subscriptlist -> Lambda_Keyword Varargslist.Colon.Test
    Testlist_Endcomma -> Lambda_Keyword Varargslist.Colon.Test
    Yield_Arg -> Lambda_Keyword Varargslist.Colon.Test
    Dict_Or_Set_Maker -> Lambda_Keyword Varargslist.Colon.Test
    START -> Class_Keyword Simple_Name.Colon.Suite
    Arguments -> Lambda_Keyword Varargslist.Colon.Test
    Arglist -> Lambda_Keyword Varargslist.Colon.Test
    Comp_Iter -> For_Keyword Exprlist.In_Keyword.Or_Test
    Funcdef -> Def_Keyword Simple_Name.Parameters.Colon.Suite
    Simple_Name.Parameters.Colon.Suite -> Simple_Name Parameters.Colon.Suite
    Arrow.Test.Colon.Suite -> Arrow Test.Colon.Suite
    Elif_Stmt -> Elif_Keyword Test.Colon.Suite.Elif_Stmt
    Test.Colon.Suite.Elif_Stmt -> Test Colon.Suite.Elif_Stmt
    Test.Colon.Suite.Else_Stmt -> Test Colon.Suite.Else_Stmt
    Colon.Suite.Elif_Stmt.Else_Stmt -> Colon Suite.Elif_Stmt.Else_Stmt
    In_Keyword.Testlist_Endcomma.Colon.Suite -> In_Keyword Testlist_Endcomma.Colon.Suite
    Testlist_Endcomma.Colon.Suite.Else_Stmt -> Testlist_Endcomma Colon.Suite.Else_Stmt
    Colon.Suite.Except_Stmt.Else_Stmt -> Colon Suite.Except_Stmt.Else_Stmt
    Colon.Suite.Except_Stmt.Finally_Stmt -> Colon Suite.Except_Stmt.Finally_Stmt
    Suite.Except_Stmt.Else_Stmt.Finally_Stmt -> Suite Except_Stmt.Else_Stmt.Finally_Stmt
    Test -> Or_Test If_Keyword.Or_Test.Else_Keyword.Test
    If_Keyword.Or_Test.Else_Keyword.Test -> If_Keyword Or_Test.Else_Keyword.Test
    Open_Paren.Close_Paren.Colon.Suite -> Open_Paren Close_Paren.Colon.Suite
    Arglist.Close_Paren.Colon.Suite -> Arglist Close_Paren.Colon.Suite
    Comp_For -> For_Keyword Exprlist.In_Keyword.Or_Test.Comp_Iter
    Exprlist.In_Keyword.Or_Test.Comp_Iter -> Exprlist In_Keyword.Or_Test.Comp_Iter
    Comp_For -> Async_Keyword For_Keyword.Exprlist.In_Keyword.Or_Test
    For_Keyword.Exprlist.In_Keyword.Or_Test -> For_Keyword Exprlist.In_Keyword.Or_Test
    START -> Def_Keyword Simple_Name.Parameters.Colon.Suite
    START -> If_Keyword Test.Colon.Suite.Else_Stmt
    START -> If_Keyword Test.Colon.Suite.Elif_Stmt
    START -> While_Keyword Test.Colon.Suite.Else_Stmt
    START -> Try_Keyword Colon.Suite.Except_Stmt.Else_Stmt
    START -> Try_Keyword Colon.Suite.Except_Stmt.Finally_Stmt
    Test_Or_Star_Expr -> Or_Test If_Keyword.Or_Test.Else_Keyword.Test
    With_Item -> Or_Test If_Keyword.Or_Test.Else_Keyword.Test
    Subscript -> Or_Test If_Keyword.Or_Test.Else_Keyword.Test
    Argument -> Or_Test If_Keyword.Or_Test.Else_Keyword.Test
    Test_Or_Star_Exprs -> Or_Test If_Keyword.Or_Test.Else_Keyword.Test
    Testlist_Star_Expr -> Or_Test If_Keyword.Or_Test.Else_Keyword.Test
    Testlist_Comp -> Or_Test If_Keyword.Or_Test.Else_Keyword.Test
    Small_Stmt -> Or_Test If_Keyword.Or_Test.Else_Keyword.Test
    Small_Stmts -> Or_Test If_Keyword.Or_Test.Else_Keyword.Test
    With_Items -> Or_Test If_Keyword.Or_Test.Else_Keyword.Test
    Subscripts -> Or_Test If_Keyword.Or_Test.Else_Keyword.Test
    Subscriptlist -> Or_Test If_Keyword.Or_Test.Else_Keyword.Test
    Testlist_Endcomma -> Or_Test If_Keyword.Or_Test.Else_Keyword.Test
    Yield_Arg -> Or_Test If_Keyword.Or_Test.Else_Keyword.Test
    Dict_Or_Set_Maker -> Or_Test If_Keyword.Or_Test.Else_Keyword.Test
    Arguments -> Or_Test If_Keyword.Or_Test.Else_Keyword.Test
    Arglist -> Or_Test If_Keyword.Or_Test.Else_Keyword.Test
    Comp_Iter -> For_Keyword Exprlist.In_Keyword.Or_Test.Comp_Iter
    Comp_Iter -> Async_Keyword For_Keyword.Exprlist.In_Keyword.Or_Test
    Parameters.Arrow.Test.Colon.Suite -> Parameters Arrow.Test.Colon.Suite
    Test.Colon.Suite.Elif_Stmt.Else_Stmt -> Test Colon.Suite.Elif_Stmt.Else_Stmt
    For_Stmt -> For_Keyword Exprlist.In_Keyword.Testlist_Endcomma.Colon.Suite
    Exprlist.In_Keyword.Testlist_Endcomma.Colon.Suite -> Exprlist In_Keyword.Testlist_Endcomma.Colon.Suite
    In_Keyword.Testlist_Endcomma.Colon.Suite.Else_Stmt -> In_Keyword Testlist_Endcomma.Colon.Suite.Else_Stmt
    Colon.Suite.Except_Stmt.Else_Stmt.Finally_Stmt -> Colon Suite.Except_Stmt.Else_Stmt.Finally_Stmt
    Simple_Name.Open_Paren.Close_Paren.Colon.Suite -> Simple_Name Open_Paren.Close_Paren.Colon.Suite
    Open_Paren.Arglist.Close_Paren.Colon.Suite -> Open_Paren Arglist.Close_Paren.Colon.Suite
    Comp_For -> Async_Keyword For_Keyword.Exprlist.In_Keyword.Or_Test.Comp_Iter
    For_Keyword.Exprlist.In_Keyword.Or_Test.Comp_Iter -> For_Keyword Exprlist.In_Keyword.Or_Test.Comp_Iter
    START -> If_Keyword Test.Colon.Suite.Elif_Stmt.Else_Stmt
    START -> For_Keyword Exprlist.In_Keyword.Testlist_Endcomma.Colon.Suite
    START -> Try_Keyword Colon.Suite.Except_Stmt.Else_Stmt.Finally_Stmt
    START -> Class_Keyword Simple_Name.Open_Paren.Close_Paren.Colon.Suite
    Comp_Iter -> Async_Keyword For_Keyword.Exprlist.In_Keyword.Or_Test.Comp_Iter
    Funcdef -> Def_Keyword Simple_Name.Parameters.Arrow.Test.Colon.Suite
    Simple_Name.Parameters.Arrow.Test.Colon.Suite -> Simple_Name Parameters.Arrow.Test.Colon.Suite
    For_Stmt -> For_Keyword Exprlist.In_Keyword.Testlist_Endcomma.Colon.Suite.Else_Stmt
    Exprlist.In_Keyword.Testlist_Endcomma.Colon.Suite.Else_Stmt -> Exprlist In_Keyword.Testlist_Endcomma.Colon.Suite.Else_Stmt
    Simple_Name.Open_Paren.Arglist.Close_Paren.Colon.Suite -> Simple_Name Open_Paren.Arglist.Close_Paren.Colon.Suite
    START -> Def_Keyword Simple_Name.Parameters.Arrow.Test.Colon.Suite
    START -> For_Keyword Exprlist.In_Keyword.Testlist_Endcomma.Colon.Suite.Else_Stmt
    START -> Class_Keyword Simple_Name.Open_Paren.Arglist.Close_Paren.Colon.Suite
  """.trimIndent().lines().map { it.split(" -> ").let { Pair(it[0], it[1].split(" ")) } }.toSet().freeze()
}

val dyck by lazy {
  """
    START -> L R
    START -> L F
    START -> START START
    F -> START R
    L -> (
    R -> )
  """.trimIndent().lines().map { it.split(" -> ").let { Pair(it[0], it[1].split(" ")) } }.toSet().freeze()
}

val simpleLang by lazy {
  """
    START -> START + START | START * START | START - START | START / START | ( START )
    START -> N | V
    N -> 0 | 1
    V -> X | Y
  """.trimIndent().parseCFG().noEpsilonOrNonterminalStubs
}