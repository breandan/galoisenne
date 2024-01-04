package ai.hypergraph.kaliningraph.repair

import kotlin.math.pow

val contextCSV: CEADist by lazy { pythonContext.lines().readContextCSV() }

fun List<String>.readContextCSV(diversity: Double = 1.0) =
  drop(1).map { it.split(", ") }.associate {
    ContextEdit(
      type = EditType.valueOf(it[0].trim()),
      context = Context(it[1], it[2], it[3]),
      newMid = it[4]
    ) to it[5].trim().toDouble().pow(diversity).toInt().coerceAtLeast(1)
  }.let { CEADist(it) }

val pythonContext = """
Type , Left       , Old Mid      , Right        , New Mid    , Frequency
INS  , NAME       ,              , NAME         , '('        , 1293     
INS  , NEWLINE    ,              , NAME         , 99         , 1212     
INS  , NEWLINE    ,              , NAME         , 98         , 1155     
INS  , ')'        ,              , EOS          , ')'        , 813      
DEL  , BOS        , NEWLINE      , 98           ,            , 639      
DEL  , NAME       , NAME         , NAME         ,            , 605      
INS  , NAME       ,              , STRING       , '('        , 575      
DEL  , NAME       , NAME         , NEWLINE      ,            , 539      
INS  , NAME       ,              , NEWLINE      , ')'        , 491      
INS  , ')'        ,              , NEWLINE      , ')'        , 478      
INS  , ')'        ,              , NAME         , NEWLINE    , 473      
INS  , NAME       ,              , EOS          , ')'        , 402      
INS  , NEWLINE    ,              , 'def'        , 98         , 391      
INS  , ')'        ,              , NEWLINE      , ':'        , 387      
INS  , 99         ,              , EOS          , 99         , 349      
DEL  , 99         , 99           , EOS          ,            , 344      
DEL  , NEWLINE    , 98           , NAME         ,            , 312      
DEL  , ')'        , UNKNOWN_CHAR , EOS          ,            , 307      
DEL  , NAME       , NAME         , '('          ,            , 289      
DEL  , NAME       , NAME         , '.'          ,            , 286      
DEL  , NAME       , NAME         , EOS          ,            , 268      
DEL  , BOS        , '>>'         , '>'          ,            , 268      
INS  , STRING     ,              , NEWLINE      , ')'        , 255      
DEL  , NAME       , NAME         , ':'          ,            , 243      
INS  , NEWLINE    ,              , EOS          , 99         , 232      
DEL  , STRING     , NAME         , UNKNOWN_CHAR ,            , 210      
INS  , NEWLINE    ,              , 'if'         , 98         , 207      
DEL  , BOS        , 98           , NAME         ,            , 205      
DEL  , BOS        , '>'          , NAME         ,            , 204      
INS  , STRING     ,              , NEWLINE      , STRING     , 200      
DEL  , STRING     , NAME         , NAME         ,            , 189      
INS  , NAME       ,              , NAME         , '='        , 185      
INS  , ']'        ,              , EOS          , ')'        , 184      
DEL  , BOS        , UNKNOWN_CHAR , NAME         ,            , 182      
SUB  , 98         , 'pass'       , NEWLINE      , NAME       , 167      
DEL  , NEWLINE    , 99           , NAME         ,            , 163      
INS  , NEWLINE    ,              , 'if'         , 99         , 158      
INS  , ')'        ,              , NAME         , ')'        , 157      
INS  , NUMBER     ,              , NUMBER       , ','        , 149      
DEL  , STRING     , NAME         , STRING       ,            , 148      
DEL  , NAME       , ':'          , NEWLINE      ,            , 148      
DEL  , ')'        , 99           , EOS          ,            , 147      
SUB  , '('        , UNKNOWN_CHAR , NAME         , STRING     , 143      
INS  , '('        ,              , NEWLINE      , ')'        , 143      
INS  , NEWLINE    ,              , 'for'        , 99         , 132      
DEL  , ')'        , NEWLINE      , 99           ,            , 130      
INS  , NEWLINE    ,              , EOS          , 98         , 129      
DEL  , NAME       , NAME         , ','          ,            , 125      
INS  , ']'        ,              , NEWLINE      , ')'        , 125      
INS  , NEWLINE    ,              , 'def'        , 99         , 119      
INS  , ':'        ,              , EOS          , NEWLINE    , 118      
SUB  , ','        , UNKNOWN_CHAR , NAME         , STRING     , 116      
DEL  , NAME       , NAME         , '='          ,            , 116      
DEL  , BOS        , 98           , 'class'      ,            , 115      
DEL  , NAME       , UNKNOWN_CHAR , NEWLINE      ,            , 115      
SUB  , NEWLINE    , 99           , NAME         , 98         , 114      
DEL  , ')'        , UNKNOWN_CHAR , NEWLINE      ,            , 114      
DEL  , STRING     , NAME         , ']'          ,            , 113      
DEL  , NEWLINE    , UNKNOWN_CHAR , EOS          ,            , 113      
DEL  , STRING     , NAME         , ')'          ,            , 113      
DEL  , STRING     , UNKNOWN_CHAR , ')'          ,            , 112      
INS  , ']'        ,              , NAME         , NEWLINE    , 110      
DEL  , ','        , NEWLINE      , STRING       ,            , 110      
INS  , NEWLINE    ,              , STRING       , 98         , 109      
SUB  , 'return'   , 'False'      , NEWLINE      , NAME       , 109      
INS  , NEWLINE    ,              , 99           , 98         , 108      
DEL  , NAME       , '('          , NAME         ,            , 106      
INS  , NAME       ,              , NEWLINE      , ':'        , 105      
DEL  , NAME       , '>'          , NEWLINE      ,            , 104      
INS  , NAME       ,              , EOS          , NEWLINE    , 104      
DEL  , ')'        , ')'          , EOS          ,            , 104      
DEL  , NEWLINE    , '>>'         , '>'          ,            , 102      
DEL  , NEWLINE    , '>'          , NAME         ,            , 102      
SUB  , 98         , 'break'      , NEWLINE      , NAME       , 101      
DEL  , BOS        , 98           , 'def'        ,            , 100      
INS  , '}'        ,              , EOS          , '}'        , 100      
INS  , ':'        ,              , EOS          , NAME       , 100      
SUB  , '='        , UNKNOWN_CHAR , NAME         , STRING     , 98       
DEL  , NAME       , UNKNOWN_CHAR , ')'          ,            , 98       
INS  , NEWLINE    ,              , 'for'        , 98         , 96       
DEL  , ')'        , NAME         , NAME         ,            , 96       
INS  , NAME       ,              , NAME         , ','        , 95       
DEL  , ')'        , ':'          , NEWLINE      ,            , 93       
DEL  , NAME       , NAME         , ')'          ,            , 92       
INS  , 99         ,              , 'def'        , 99         , 91       
DEL  , ')'        , ')'          , NEWLINE      ,            , 90       
INS  , STRING     ,              , EOS          , ')'        , 89       
DEL  , NEWLINE    , 98           , 'def'        ,            , 88       
INS  , ']'        ,              , EOS          , '}'        , 88       
INS  , 98         ,              , EOS          , NAME       , 87       
INS  , NEWLINE    ,              , 'return'     , 99         , 83       
DEL  , NEWLINE    , 98           , 'for'        ,            , 82       
INS  , '}'        ,              , '{'          , ','        , 82       
DEL  , STRING     , UNKNOWN_CHAR , ']'          ,            , 81       
DEL  , NEWLINE    , 99           , UNKNOWN_CHAR ,            , 80       
SUB  , NEWLINE    , 'break'      , NEWLINE      , NAME       , 79       
SUB  , '['        , UNKNOWN_CHAR , NAME         , STRING     , 78       
INS  , ')'        ,              , EOS          , NEWLINE    , 76       
DEL  , NAME       , NAME         , '['          ,            , 76       
DEL  , NAME       , '>'          , EOS          ,            , 75       
SUB  , STRING     , NAME         , UNKNOWN_CHAR , ','        , 75       
DEL  , NAME       , NAME         , UNKNOWN_CHAR ,            , 74       
INS  , NAME       ,              , NAME         , '.'        , 73       
SUB  , STRING     , NAME         , STRING       , ','        , 71       
DEL  , NAME       , UNKNOWN_CHAR , ']'          ,            , 71       
INS  , BOS        ,              , STRING       , '{'        , 71       
DEL  , ']'        , UNKNOWN_CHAR , EOS          ,            , 70       
INS  , ','        ,              , EOS          , ']'        , 69       
DEL  , BOS        , UNKNOWN_CHAR , 'import'     ,            , 69       
DEL  , NAME       , UNKNOWN_CHAR , EOS          ,            , 67       
INS  , BOS        ,              , NAME         , 'def'      , 67       
DEL  , NAME       , UNKNOWN_CHAR , NAME         ,            , 66       
DEL  , STRING     , NAME         , '.'          ,            , 65       
INS  , BOS        ,              , NAME         , 'import'   , 65       
INS  , NAME       ,              , 99           , NEWLINE    , 65       
DEL  , 99         , UNKNOWN_CHAR , EOS          ,            , 64       
INS  , '('        ,              , EOS          , ')'        , 64       
INS  , NAME       ,              , NAME         , 'in'       , 64       
INS  , '('        ,              , NAME         , '('        , 63       
INS  , 99         ,              , NAME         , 99         , 62       
INS  , ')'        ,              , NEWLINE      , '('        , 62       
DEL  , BOS        , 98           , 'import'     ,            , 61       
SUB  , 'return'   , 'True'       , NEWLINE      , NAME       , 61       
INS  , STRING     ,              , NAME         , ','        , 61       
DEL  , BOS        , 98           , 'from'       ,            , 60       
DEL  , STRING     , NAME         , ','          ,            , 59       
INS  , '}'        ,              , STRING       , ','        , 58       
DEL  , BOS        , '<'          , NAME         ,            , 57       
DEL  , STRING     , UNKNOWN_CHAR , NEWLINE      ,            , 57       
INS  , ')'        ,              , EOS          , ']'        , 57       
DEL  , NAME       , ')'          , ':'          ,            , 57       
DEL  , STRING     , NAME         , ':'          ,            , 56       
DEL  , 99         , 99           , NAME         ,            , 56       
INS  , NEWLINE    ,              , 'return'     , 98         , 56       
INS  , BOS        ,              , NAME         , 'class'    , 56       
INS  , 98         ,              , 99           , NAME       , 56       
INS  , NAME       ,              , '['          , '('        , 56       
INS  , ']'        ,              , EOS          , ']'        , 55       
INS  , NAME       ,              , STRING       , ','        , 55       
INS  , STRING     ,              , STRING       , ','        , 54       
DEL  , NAME       , NEWLINE      , 99           ,            , 53       
DEL  , ')'        , '.'          , EOS          ,            , 52       
DEL  , NUMBER     , NUMBER       , ','          ,            , 52       
DEL  , BOS        , UNKNOWN_CHAR , 'def'        ,            , 52       
DEL  , NAME       , STRING       , NEWLINE      ,            , 52       
DEL  , BOS        , UNKNOWN_CHAR , NEWLINE      ,            , 51       
DEL  , NAME       , UNKNOWN_CHAR , ','          ,            , 50       
DEL  , NAME       , ':'          , '//'         ,            , 50       
SUB  , STRING     , NAME         , UNKNOWN_CHAR , STRING     , 50       
SUB  , STRING     , '='          , STRING       , ':'        , 50       
DEL  , NAME       , ')'          , NEWLINE      ,            , 48       
SUB  , NEWLINE    , 99           , 'def'        , 98         , 47       
INS  , NAME       ,              , NAME         , NEWLINE    , 47       
DEL  , NEWLINE    , NEWLINE      , 99           ,            , 46       
DEL  , NEWLINE    , 98           , 'if'         ,            , 45       
INS  , '}'        ,              , EOS          , ')'        , 45       
DEL  , STRING     , UNKNOWN_CHAR , ','          ,            , 45       
DEL  , NAME       , NAME         , 'in'         ,            , 45       
INS  , BOS        ,              , NAME         , 'from'     , 44       
DEL  , ')'        , NAME         , EOS          ,            , 44       
INS  , ')'        ,              , 'for'        , ')'        , 44       
INS  , STRING     ,              , EOS          , '}'        , 43       
DEL  , NAME       , 99           , EOS          ,            , 43       
SUB  , 'return'   , 'None'       , NEWLINE      , NAME       , 43       
INS  , NAME       ,              , NEWLINE      , '('        , 43       
INS  , ','        ,              , EOS          , '}'        , 42       
DEL  , NEWLINE    , NEWLINE      , 98           ,            , 42       
DEL  , NAME       , STRING       , ','          ,            , 41       
INS  , 'for'      ,              , 'in'         , NAME       , 41       
INS  , 98         ,              , NAME         , 'def'      , 41       
INS  , NEWLINE    ,              , 'try'        , 98         , 41       
DEL  , ','        , UNKNOWN_CHAR , ')'          ,            , 40       
DEL  , NAME       , '.'          , EOS          ,            , 40       
INS  , NEWLINE    ,              , 'while'      , 99         , 40       
INS  , '('        ,              , ':'          , ')'        , 40       
DEL  , ')'        , ':'          , EOS          ,            , 40       
DEL  , '}'        , 99           , EOS          ,            , 39       
INS  , NUMBER     ,              , STRING       , ','        , 39       
DEL  , NEWLINE    , 99           , 'def'        ,            , 39       
INS  , 99         ,              , 'else'       , 99         , 39       
DEL  , 98         , NAME         , STRING       ,            , 39       
INS  , ']'        ,              , STRING       , ','        , 39       
DEL  , NAME       , ':'          , NAME         ,            , 39       
SUB  , '='        , 'False'      , NEWLINE      , NAME       , 39       
SUB  , NAME       , ','          , NAME         , 'as'       , 39       
INS  , ']'        ,              , ')'          , ']'        , 38       
DEL  , ')'        , '**'         , EOS          ,            , 38       
DEL  , BOS        , '**'         , NAME         ,            , 38       
DEL  , STRING     , UNKNOWN_CHAR , UNKNOWN_CHAR ,            , 38       
INS  , NAME       ,              , ':'          , '('        , 38       
SUB  , NAME       , '='          , NUMBER       , '=='       , 38       
DEL  , NAME       , '.'          , NAME         ,            , 37       
INS  , '}'        ,              , EOS          , ']'        , 37       
INS  , 99         ,              , 'return'     , 99         , 37       
SUB  , NAME       , '='          , NAME         , '=='       , 37       
INS  , NAME       ,              , STRING       , '='        , 37       
INS  , 'else'     ,              , NEWLINE      , ':'        , 37       
DEL  , NAME       , NAME         , ']'          ,            , 37       
DEL  , NEWLINE    , STRING       , NEWLINE      ,            , 36       
INS  , ','        ,              , EOS          , ')'        , 36       
SUB  , NEWLINE    , '>>'         , '>'          , NAME       , 36       
DEL  , NAME       , NAME         , 'is'         ,            , 36       
INS  , '.'        ,              , NEWLINE      , NAME       , 36       
SUB  , NAME       , '='          , STRING       , '=='       , 36       
DEL  , NAME       , STRING       , '.'          ,            , 36       
DEL  , NEWLINE    , NUMBER       , NAME         ,            , 35       
INS  , NEWLINE    ,              , NAME         , 'def'      , 35       
DEL  , 'lambda'   , '('          , NAME         ,            , 35       
DEL  , NAME       , '('          , ')'          ,            , 34       
SUB  , '='        , 'True'       , NEWLINE      , NAME       , 34       
INS  , STRING     ,              , ')'          , ']'        , 34       
INS  , ')'        ,              , ','          , ')'        , 33       
DEL  , ':'        , NEWLINE      , NAME         ,            , 33       
SUB  , ':'        , UNKNOWN_CHAR , NAME         , STRING     , 33       
INS  , 'import'   ,              , NEWLINE      , NAME       , 33       
DEL  , ']'        , '.'          , EOS          ,            , 33       
DEL  , '.'        , '.'          , NAME         ,            , 33       
INS  , ')'        ,              , ']'          , ')'        , 33       
INS  , NEWLINE    ,              , 'class'      , 98         , 32       
DEL  , NAME       , '.'          , NEWLINE      ,            , 32       
DEL  , NAME       , NAME         , 'import'     ,            , 32       
DEL  , BOS        , UNKNOWN_CHAR , 'from'       ,            , 32       
DEL  , 99         , 99           , UNKNOWN_CHAR ,            , 32       
INS  , STRING     ,              , NEWLINE      , ':'        , 32       
DEL  , ','        , UNKNOWN_CHAR , ']'          ,            , 32       
DEL  , BOS        , NAME         , NAME         ,            , 31       
INS  , ')'        ,              , '.'          , ')'        , 31       
INS  , ':'        ,              , NEWLINE      , NAME       , 31       
INS  , NAME       ,              , NAME         , 'import'   , 31       
DEL  , NAME       , NAME         , '-'          ,            , 30       
INS  , 99         ,              , 'if'         , 99         , 30       
SUB  , 98         , 'continue'   , NEWLINE      , NAME       , 30       
INS  , 99         ,              , NAME         , 'except'   , 30       
DEL  , '}'        , NEWLINE      , 99           ,            , 29       
DEL  , ']'        , NAME         , NAME         ,            , 29       
DEL  , NAME       , ':'          , EOS          ,            , 29       
INS  , ':'        ,              , EOS          , '...'      , 29       
DEL  , NAME       , NAME         , 'as'         ,            , 29       
DEL  , NUMBER     , NAME         , NAME         ,            , 28       
DEL  , NEWLINE    , NEWLINE      , NAME         ,            , 28       
INS  , ':'        ,              , EOS          , '('        , 28       
DEL  , 99         , 99           , 'else'       ,            , 28       
INS  , BOS        ,              , NEWLINE      , 'def'      , 27       
INS  , 'def'      ,              , NEWLINE      , NAME       , 27       
DEL  , STRING     , NAME         , '-'          ,            , 27       
DEL  , NUMBER     , UNKNOWN_CHAR , NEWLINE      ,            , 26       
INS  , ']'        ,              , NAME         , ')'        , 26       
DEL  , 99         , 99           , 'def'        ,            , 26       
INS  , STRING     ,              , ','          , ']'        , 26       
DEL  , NUMBER     , NUMBER       , ')'          ,            , 26       
INS  , ')'        ,              , NAME         , ','        , 26       
INS  , ':'        ,              , NAME         , NEWLINE    , 26       
DEL  , '('        , '('          , NAME         ,            , 26       
INS  , '='        ,              , NAME         , 'lambda'   , 26       
DEL  , NEWLINE    , 98           , 'while'      ,            , 25       
INS  , '['        ,              , NUMBER       , '['        , 25       
DEL  , NAME       , '-'          , '->'         ,            , 25       
INS  , NAME       ,              , NAME         , ':'        , 25       
INS  , NEWLINE    ,              , 'global'     , 98         , 25       
DEL  , NUMBER     , NUMBER       , NUMBER       ,            , 25       
INS  , NUMBER     ,              , NEWLINE      , ':'        , 25       
SUB  , BOS        , '>>'         , '>'          , NAME       , 25       
INS  , NUMBER     ,              , ')'          , ']'        , 25       
DEL  , NAME       , ','          , NAME         ,            , 25       
DEL  , ')'        , '**'         , NEWLINE      ,            , 25       
DEL  , NAME       , '->'         , NEWLINE      ,            , 25       
DEL  , NAME       , '.'          , '['          ,            , 25       
DEL  , NEWLINE    , '>>'         , NAME         ,            , 25       
INS  , NAME       ,              , 'for'        , ')'        , 25       
DEL  , '('        , UNKNOWN_CHAR , NAME         ,            , 24       
INS  , STRING     ,              , NAME         , ')'        , 24       
INS  , NAME       ,              , ']'          , ')'        , 24       
DEL  , NAME       , UNKNOWN_CHAR , UNKNOWN_CHAR ,            , 24       
SUB  , '='        , 'None'       , NEWLINE      , NAME       , 24       
DEL  , STRING     , ')'          , ','          ,            , 24       
DEL  , ')'        , '<'          , '/'          ,            , 24       
DEL  , BOS        , '<'          , UNKNOWN_CHAR ,            , 24       
DEL  , BOS        , UNKNOWN_CHAR , '-'          ,            , 24       
DEL  , ')'        , NAME         , NEWLINE      ,            , 24       
DEL  , NUMBER     , NAME         , '='          ,            , 24       
DEL  , '...'      , '.'          , NEWLINE      ,            , 24       
DEL  , NAME       , STRING       , ')'          ,            , 23       
DEL  , NAME       , '/'          , '>'          ,            , 23       
DEL  , STRING     , UNKNOWN_CHAR , NAME         ,            , 23       
DEL  , BOS        , '>'          , 'import'     ,            , 23       
INS  , NAME       ,              , NEWLINE      , 'import'   , 23       
INS  , 99         ,              , NAME         , 'return'   , 23       
DEL  , ','        , UNKNOWN_CHAR , NAME         ,            , 23       
INS  , '+'        ,              , NEWLINE      , NUMBER     , 23       
DEL  , NAME       , '='          , NAME         ,            , 23       
DEL  , ']'        , ')'          , NEWLINE      ,            , 23       
INS  , ')'        ,              , 'return'     , NEWLINE    , 23       
DEL  , STRING     , NUMBER       , NUMBER       ,            , 23       
DEL  , NUMBER     , UNKNOWN_CHAR , EOS          ,            , 22       
INS  , NEWLINE    ,              , 'else'       , 99         , 22       
DEL  , NAME       , NAME         , NUMBER       ,            , 22       
DEL  , NEWLINE    , UNKNOWN_CHAR , UNKNOWN_CHAR ,            , 22       
SUB  , STRING     , UNKNOWN_CHAR , ']'          , ','        , 22       
DEL  , STRING     , NUMBER       , STRING       ,            , 22       
SUB  , STRING     , ','          , STRING       , ':'        , 22       
DEL  , STRING     , NAME         , '/'          ,            , 22       
DEL  , 99         , 99           , 'return'     ,            , 22       
DEL  , NAME       , NAME         , '>'          ,            , 22       
DEL  , NAME       , ']'          , ')'          ,            , 22       
SUB  , NAME       , ','          , NAME         , ':'        , 22       
DEL  , ']'        , UNKNOWN_CHAR , NEWLINE      ,            , 22       
SUB  , STRING     , '='          , NUMBER       , ':'        , 22       
DEL  , BOS        , 98           , '{'          ,            , 21       
DEL  , ','        , '{'          , STRING       ,            , 21       
DEL  , NEWLINE    , 98           , 'return'     ,            , 21       
INS  , NEWLINE    ,              , 'while'      , 98         , 21       
SUB  , ','        , UNKNOWN_CHAR , NUMBER       , STRING     , 21       
DEL  , NEWLINE    , 98           , 'class'      ,            , 21       
DEL  , STRING     , NAME         , '}'          ,            , 21       
DEL  , NAME       , STRING       , EOS          ,            , 21       
DEL  , NEWLINE    , '**'         , NAME         ,            , 21       
INS  , '}'        ,              , ','          , '}'        , 21       
DEL  , ']'        , 99           , EOS          ,            , 21       
INS  , 'class'    ,              , NEWLINE      , NAME       , 21       
DEL  , ']'        , NEWLINE      , 99           ,            , 21       
DEL  , ':'        , 98           , NAME         ,            , 21       
DEL  , BOS        , 'import'     , NAME         ,            , 21       
DEL  , BOS        , NAME         , 'from'       ,            , 21       
SUB  , ')'        , NEWLINE      , NAME         , ':'        , 21       
INS  , ']'        ,              , EOS          , NEWLINE    , 21       
SUB  , STRING     , '='          , '['          , ':'        , 21       
DEL  , NUMBER     , NUMBER       , '-'          ,            , 21       
DEL  , BOS        , NAME         , '['          ,            , 21       
INS  , ')'        ,              , ':'          , ')'        , 21       
INS  , NAME       ,              , 'in'         , 'for'      , 21       
SUB  , STRING     , ':'          , STRING       , ','        , 20       
SUB  , NAME       , STRING       , NAME         , ','        , 20       
DEL  , BOS        , 'def'        , NAME         ,            , 20       
SUB  , NEWLINE    , 99           , 'if'         , 98         , 20       
INS  , '}'        ,              , NAME         , NEWLINE    , 20       
SUB  , STRING     , ':'          , NUMBER       , ','        , 20       
DEL  , ','        , NEWLINE      , 98           ,            , 20       
DEL  , STRING     , UNKNOWN_CHAR , ':'          ,            , 20       
INS  , ']'        ,              , NEWLINE      , ':'        , 20       
DEL  , ')'        , ')'          , ','          ,            , 20       
INS  , NAME       ,              , '['          , '='        , 20       
DEL  , ')'        , UNKNOWN_CHAR , NAME         ,            , 20       
INS  , 99         ,              , NAME         , 'def'      , 20       
DEL  , ')'        , NEWLINE      , '>>'         ,            , 20       
DEL  , ')'        , '>>'         , '>'          ,            , 20       
INS  , NEWLINE    ,              , 99           , NEWLINE    , 20       
DEL  , STRING     , UNKNOWN_CHAR , EOS          ,            , 20       
DEL  , NAME       , '-'          , NAME         ,            , 20       
DEL  , '}'        , UNKNOWN_CHAR , EOS          ,            , 19       
DEL  , NEWLINE    , UNKNOWN_CHAR , NEWLINE      ,            , 19       
DEL  , NEWLINE    , 99           , 'for'        ,            , 19       
DEL  , 99         , 99           , 'except'     ,            , 19       
DEL  , NEWLINE    , NAME         , STRING       ,            , 19       
DEL  , ','        , NEWLINE      , '['          ,            , 19       
SUB  , BOS        , NEWLINE      , 98           , '{'        , 19       
DEL  , '{'        , 98           , STRING       ,            , 19       
DEL  , NUMBER     , NAME         , ','          ,            , 19       
INS  , NEWLINE    ,              , 'class'      , 99         , 19       
DEL  , BOS        , '>'          , '['          ,            , 19       
INS  , STRING     ,              , NAME         , '+'        , 19       
DEL  , NEWLINE    , '//'         , NAME         ,            , 19       
INS  , BOS        ,              , NEWLINE      , 'class'    , 19       
DEL  , NAME       , NUMBER       , ']'          ,            , 19       
DEL  , NEWLINE    , '%'          , NAME         ,            , 19       
DEL  , BOS        , '>'          , 'from'       ,            , 19       
INS  , NUMBER     ,              , NEWLINE      , ')'        , 19       
INS  , ')'        ,              , EOS          , ':'        , 19       
DEL  , ']'        , ':'          , NEWLINE      ,            , 19       
DEL  , ')'        , ')'          , ':'          ,            , 19       
DEL  , NAME       , NUMBER       , NEWLINE      ,            , 18       
DEL  , NEWLINE    , 98           , 'from'       ,            , 18       
DEL  , NEWLINE    , 98           , 'import'     ,            , 18       
DEL  , NAME       , NAME         , STRING       ,            , 18       
DEL  , NAME       , '**'         , NEWLINE      ,            , 18       
DEL  , NAME       , NAME         , '**'         ,            , 18       
INS  , '}'        ,              , NAME         , ','        , 18       
DEL  , NUMBER     , NUMBER       , NEWLINE      ,            , 18       
DEL  , NAME       , NUMBER       , ','          ,            , 18       
INS  , NUMBER     ,              , EOS          , ')'        , 18       
INS  , NUMBER     ,              , EOS          , '}'        , 18       
INS  , NAME       ,              , ')'          , '('        , 18       
SUB  , 98         , NAME         , STRING       , 'return'   , 18       
SUB  , NEWLINE    , 'pass'       , NEWLINE      , NAME       , 18       
DEL  , NEWLINE    , 99           , EOS          ,            , 17       
DEL  , STRING     , NUMBER       , UNKNOWN_CHAR ,            , 17       
DEL  , ')'        , NAME         , '.'          ,            , 17       
DEL  , STRING     , UNKNOWN_CHAR , '}'          ,            , 17       
DEL  , ','        , ','          , NAME         ,            , 17       
SUB  , STRING     , '.'          , STRING       , ','        , 17       
INS  , ','        ,              , NEWLINE      , ')'        , 17       
DEL  , ')'        , NEWLINE      , 98           ,            , 17       
SUB  , NEWLINE    , 'continue'   , NEWLINE      , NAME       , 17       
SUB  , '{'        , UNKNOWN_CHAR , NAME         , STRING     , 17       
DEL  , NAME       , 'from'       , NAME         ,            , 17       
DEL  , ']'        , ')'          , EOS          ,            , 17       
INS  , 98         ,              , NAME         , 'return'   , 17       
DEL  , NEWLINE    , NAME         , NEWLINE      ,            , 17       
DEL  , 'if'       , '('          , NAME         ,            , 17       
INS  , NAME       ,              , ':'          , ')'        , 17       
DEL  , NAME       , STRING       , NAME         ,            , 16       
INS  , ':'        ,              , NEWLINE      , 'pass'     , 16       
DEL  , NEWLINE    , UNKNOWN_CHAR , NAME         ,            , 16       
SUB  , ','        , UNKNOWN_CHAR , ']'          , STRING     , 16       
DEL  , NAME       , UNKNOWN_CHAR , '}'          ,            , 16       
INS  , 99         ,              , '@'          , 99         , 16       
INS  , NEWLINE    ,              , 'with'       , 98         , 16       
INS  , '['        ,              , STRING       , '['        , 16       
DEL  , ')'        , ')'          , ']'          ,            , 16       
DEL  , NUMBER     , NUMBER       , EOS          ,            , 16       
DEL  , NAME       , UNKNOWN_CHAR , ':'          ,            , 16       
DEL  , STRING     , NAME         , NEWLINE      ,            , 16       
INS  , NAME       ,              , NAME         , ')'        , 16       
INS  , '('        ,              , NAME         , 'lambda'   , 16       
DEL  , ']'        , ']'          , EOS          ,            , 16       
DEL  , NAME       , 'for'        , NAME         ,            , 16       
INS  , ':'        ,              , STRING       , '['        , 16       
INS  , 'pass'     ,              , EOS          , NEWLINE    , 16       
INS  , ']'        ,              , NAME         , ','        , 16       
DEL  , ')'        , NAME         , UNKNOWN_CHAR ,            , 16       
DEL  , BOS        , 98           , '@'          ,            , 16       
DEL  , '='        , NEWLINE      , NAME         ,            , 16       
INS  , '+'        ,              , NEWLINE      , '+'        , 16       
DEL  , ':'        , NAME         , NAME         ,            , 16       
SUB  , ')'        , NEWLINE      , 99           , ')'        , 16       
DEL  , STRING     , ':'          , STRING       ,            , 16       
DEL  , '...'      , '...'        , '...'        ,            , 16       
DEL  , NAME       , '>'          , ')'          ,            , 16       
INS  , STRING     ,              , '{'          , ':'        , 16       
DEL  , '...'      , '.'          , '.'          ,            , 16       
DEL  , '&'        , '&'          , NAME         ,            , 16       
INS  , '.'        ,              , '('          , NAME       , 16       
DEL  , ')'        , ']'          , ')'          ,            , 16       
INS  , ']'        ,              , 'for'        , ')'        , 16       
DEL  , 99         , 99           , 'class'      ,            , 15       
DEL  , '>'        , NEWLINE      , NAME         ,            , 15       
INS  , ']'        ,              , ','          , ']'        , 15       
INS  , NUMBER     ,              , NAME         , ','        , 15       
INS  , STRING     ,              , EOS          , ']'        , 15       
INS  , ':'        ,              , NEWLINE      , '('        , 15       
INS  , '['        ,              , EOS          , ']'        , 15       
DEL  , NAME       , NEWLINE      , NAME         ,            , 15       
INS  , NEWLINE    ,              , NAME         , 'return'   , 15       
DEL  , STRING     , NUMBER       , ']'          ,            , 15       
DEL  , NUMBER     , NAME         , NEWLINE      ,            , 15       
INS  , STRING     ,              , NAME         , NEWLINE    , 15       
INS  , ')'        ,              , NAME         , '.'        , 15       
DEL  , '='        , UNKNOWN_CHAR , NAME         ,            , 15       
DEL  , NUMBER     , NEWLINE      , 99           ,            , 15       
DEL  , '('        , '['          , NAME         ,            , 15       
DEL  , BOS        , '%'          , NAME         ,            , 15       
DEL  , ')'        , NAME         , 'in'         ,            , 15       
DEL  , '('        , '('          , STRING       ,            , 15       
DEL  , ')'        , UNKNOWN_CHAR , UNKNOWN_CHAR ,            , 15       
INS  , STRING     ,              , ']'          , ')'        , 15       
DEL  , NEWLINE    , '<'          , NAME         ,            , 15       
INS  , NEWLINE    ,              , 'try'        , 99         , 14       
DEL  , BOS        , UNKNOWN_CHAR , 'class'      ,            , 14       
SUB  , '}'        , NEWLINE      , 99           , '}'        , 14       
DEL  , ','        , NAME         , STRING       ,            , 14       
DEL  , NAME       , STRING       , '%'          ,            , 14       
INS  , ','        ,              , NAME         , '('        , 14       
DEL  , NAME       , '/'          , NAME         ,            , 14       
INS  , 98         ,              , EOS          , 'pass'     , 14       
DEL  , ':'        , UNKNOWN_CHAR , NAME         ,            , 14       
DEL  , NEWLINE    , ')'          , EOS          ,            , 14       
DEL  , NUMBER     , 99           , EOS          ,            , 14       
DEL  , NAME       , '['          , NUMBER       ,            , 14       
SUB  , NEWLINE    , 'return'     , NEWLINE      , NAME       , 14       
INS  , NAME       ,              , '{'          , '='        , 14       
DEL  , NAME       , 'in'         , NAME         ,            , 14       
INS  , ']'        ,              , NEWLINE      , ']'        , 14       
INS  , NAME       ,              , ')'          , ']'        , 14       
INS  , '('        ,              , STRING       , '{'        , 14       
DEL  , ']'        , NAME         , EOS          ,            , 14       
INS  , '['        ,              , NAME         , '['        , 13       
DEL  , 99         , '<'          , '/'          ,            , 13       
DEL  , 99         , '/'          , NAME         ,            , 13       
DEL  , NUMBER     , NAME         , UNKNOWN_CHAR ,            , 13       
SUB  , STRING     , NUMBER       , STRING       , ','        , 13       
DEL  , 99         , '>>'         , '>'          ,            , 13       
DEL  , BOS        , ':'          , NEWLINE      ,            , 13       
INS  , NAME       ,              , ','          , ')'        , 13       
DEL  , NUMBER     , UNKNOWN_CHAR , ']'          ,            , 13       
SUB  , STRING     , NAME         , UNKNOWN_CHAR , ':'        , 13       
DEL  , NUMBER     , NUMBER       , ']'          ,            , 13       
DEL  , NUMBER     , NAME         , EOS          ,            , 13       
DEL  , 99         , 99           , 99           ,            , 13       
DEL  , NAME       , '.'          , '('          ,            , 13       
DEL  , NAME       , NUMBER       , ')'          ,            , 13       
SUB  , NUMBER     , ':'          , NUMBER       , ','        , 13       
INS  , ']'        ,              , ','          , ')'        , 13       
DEL  , STRING     , NAME         , '>'          ,            , 13       
DEL  , ']'        , ')'          , 'for'        ,            , 13       
DEL  , NAME       , 'is'         , ':'          ,            , 13       
DEL  , ')'        , NAME         , '>'          ,            , 13       
DEL  , 99         , NAME         , NAME         ,            , 13       
DEL  , STRING     , ':'          , '//'         ,            , 13       
DEL  , 99         , '>'          , NAME         ,            , 13       
INS  , ':'        ,              , '}'          , STRING     , 13       
DEL  , '.'        , NEWLINE      , NAME         ,            , 13       
INS  , NAME       ,              , NEWLINE      , ']'        , 13       
DEL  , BOS        , 'for'        , NAME         ,            , 13       
DEL  , BOS        , 98           , 'for'        ,            , 13       
DEL  , NAME       , '('          , STRING       ,            , 13       
INS  , '='        ,              , NAME         , '['        , 13       
DEL  , STRING     , '.'          , NAME         ,            , 13       
SUB  , '=='       , UNKNOWN_CHAR , NAME         , STRING     , 13       
INS  , '['        ,              , NAME         , '('        , 13       
DEL  , '='        , '('          , NAME         ,            , 13       
SUB  , ']'        , '='          , NAME         , '=='       , 13       
DEL  , ')'        , 'for'        , NAME         ,            , 13       
DEL  , ')'        , ')'          , 'for'        ,            , 13       
DEL  , NEWLINE    , 99           , 'class'      ,            , 12       
INS  , 98         ,              , NAME         , 'if'       , 12       
INS  , '='        ,              , NAME         , '('        , 12       
DEL  , ')'        , '.'          , NEWLINE      ,            , 12       
DEL  , ','        , '**'         , NAME         ,            , 12       
DEL  , NAME       , ')'          , NAME         ,            , 12       
SUB  , ':'        , UNKNOWN_CHAR , '}'          , STRING     , 12       
DEL  , NAME       , 'if'         , NAME         ,            , 12       
SUB  , STRING     , ']'          , EOS          , '}'        , 12       
INS  , NEWLINE    ,              , 'import'     , 98         , 12       
INS  , '}'        ,              , '}'          , ']'        , 12       
SUB  , 98         , 'raise'      , NEWLINE      , NAME       , 12       
DEL  , NAME       , '<'          , '/'          ,            , 12       
DEL  , ')'        , '*'          , EOS          ,            , 12       
INS  , ']'        ,              , NAME         , ']'        , 12       
DEL  , NEWLINE    , 99           , 'return'     ,            , 12       
DEL  , '='        , '='          , NAME         ,            , 12       
DEL  , 98         , NAME         , NAME         ,            , 12       
DEL  , ')'        , NAME         , '='          ,            , 12       
DEL  , '['        , '['          , STRING       ,            , 12       
DEL  , '{'        , '{'          , STRING       ,            , 12       
DEL  , NAME       , ']'          , ':'          ,            , 12       
INS  , STRING     ,              , NAME         , ':'        , 12       
INS  , NEWLINE    ,              , 'except'     , 99         , 12       
SUB  , '='        , '['          , STRING       , '{'        , 12       
DEL  , '|'        , '|'          , NAME         ,            , 12       
DEL  , NAME       , '%'          , NAME         ,            , 12       
INS  , 'in'       ,              , ':'          , NAME       , 12       
INS  , '.'        ,              , EOS          , NAME       , 12       
SUB  , '+'        , '+'          , NEWLINE      , NUMBER     , 12       
INS  , ')'        ,              , STRING       , ','        , 12       
DEL  , ')'        , NUMBER       , EOS          ,            , 12       
DEL  , NAME       , NAME         , '=='         ,            , 12       
SUB  , STRING     , NUMBER       , UNKNOWN_CHAR , ','        , 11       
SUB  , STRING     , UNKNOWN_CHAR , ']'          , STRING     , 11       
DEL  , NAME       , ')'          , EOS          ,            , 11       
DEL  , NAME       , NAME         , '/'          ,            , 11       
DEL  , NAME       , ']'          , NEWLINE      ,            , 11       
DEL  , '...'      , '.'          , ']'          ,            , 11       
DEL  , 99         , 99           , 'elif'       ,            , 11       
SUB  , NEWLINE    , 98           , NAME         , 99         , 11       
DEL  , NUMBER     , ':'          , NUMBER       ,            , 11       
SUB  , ')'        , '-'          , '&'          , '->'       , 11       
DEL  , '->'       , '&'          , NAME         ,            , 11       
DEL  , STRING     , NEWLINE      , 99           ,            , 11       
SUB  , NAME       , NEWLINE      , NAME         , ':'        , 11       
DEL  , NAME       , '='          , NUMBER       ,            , 11       
SUB  , NAME       , '.'          , NAME         , ','        , 11       
INS  , STRING     ,              , ','          , ')'        , 11       
INS  , NEWLINE    ,              , NAME         , 'import'   , 11       
DEL  , NEWLINE    , ':'          , NEWLINE      ,            , 11       
INS  , 'pass'     ,              , 99           , NEWLINE    , 11       
DEL  , NEWLINE    , 99           , 'from'       ,            , 11       
DEL  , 'if'       , UNKNOWN_CHAR , NAME         ,            , 11       
DEL  , '}'        , 99           , '}'          ,            , 11       
INS  , 98         ,              , 99           , NEWLINE    , 11       
DEL  , STRING     , NAME         , NUMBER       ,            , 11       
INS  , NEWLINE    ,              , 'assert'     , 99         , 11       
INS  , ','        ,              , ','          , STRING     , 11       
DEL  , BOS        , UNKNOWN_CHAR , '['          ,            , 11       
INS  , ')'        ,              , EOS          , '}'        , 11       
DEL  , '...'      , '.'          , EOS          ,            , 11       
SUB  , BOS        , 'in'         , '='          , NAME       , 11       
INS  , ']'        ,              , ']'          , ')'        , 11       
INS  , STRING     ,              , '}'          , ':'        , 11       
DEL  , '('        , NAME         , '('          ,            , 11       
DEL  , NEWLINE    , 99           , 'if'         ,            , 11       
SUB  , BOS        , NEWLINE      , 98           , NAME       , 11       
DEL  , BOS        , '['          , NUMBER       ,            , 11       
DEL  , BOS        , NUMBER       , ']'          ,            , 11       
DEL  , BOS        , ']'          , ':'          ,            , 11       
DEL  , NAME       , '**'         , EOS          ,            , 11       
SUB  , NEWLINE    , 99           , 'for'        , 98         , 11       
DEL  , ']'        , ']'          , ','          ,            , 11       
INS  , '='        ,              , '('          , NAME       , 11       
SUB  , '='        , UNKNOWN_CHAR , NUMBER       , STRING     , 11       
DEL  , 99         , NEWLINE      , 99           ,            , 11       
INS  , ')'        ,              , STRING       , '+'        , 11       
DEL  , 'in'       , 'in'         , NAME         ,            , 11       
DEL  , NAME       , '>'          , '<'          ,            , 11       
INS  , 98         ,              , 99           , '...'      , 11       
INS  , '...'      ,              , 99           , NEWLINE    , 11       
DEL  , NUMBER     , ')'          , ']'          ,            , 11       
INS  , STRING     ,              , ')'          , '}'        , 11       
INS  , ':'        ,              , EOS          , 'pass'     , 11       
DEL  , NUMBER     , UNKNOWN_CHAR , ')'          ,            , 11       
INS  , NAME       ,              , NAME         , 'if'       , 11       
INS  , '.'        ,              , NAME         , 'import'   , 11       
SUB  , NEWLINE    , '>>'         , '>'          , 98         , 10       
INS  , 'in'       ,              , NAME         , '('        , 10       
INS  , 99         ,              , 99           , NEWLINE    , 10       
SUB  , NAME       , '='          , STRING       , ':'        , 10       
DEL  , ','        , 98           , NAME         ,            , 10       
DEL  , NEWLINE    , 'or'         , NEWLINE      ,            , 10       
INS  , NEWLINE    ,              , '...'        , 98         , 10       
INS  , NUMBER     ,              , EOS          , ']'        , 10       
INS  , '='        ,              , STRING       , '{'        , 10       
DEL  , NAME       , '='          , NEWLINE      ,            , 10       
SUB  , NAME       , UNKNOWN_CHAR , ')'          , '('        , 10       
INS  , NAME       ,              , STRING       , '+'        , 10       
INS  , 98         ,              , EOS          , 'return'   , 10       
INS  , 'return'   ,              , EOS          , NEWLINE    , 10       
DEL  , NAME       , NUMBER       , EOS          ,            , 10       
INS  , 98         ,              , 99           , 'pass'     , 10       
DEL  , ','        , ','          , STRING       ,            , 10       
DEL  , STRING     , NAME         , EOS          ,            , 10       
DEL  , ':'        , UNKNOWN_CHAR , NEWLINE      ,            , 10       
DEL  , NEWLINE    , 99           , ')'          ,            , 10       
DEL  , BOS        , 98           , '['          ,            , 10       
DEL  , ']'        , ']'          , ')'          ,            , 10       
INS  , ':'        ,              , EOS          , '['        , 10       
INS  , NAME       ,              , EOS          , 'import'   , 10       
INS  , 'import'   ,              , EOS          , NAME       , 10       
DEL  , NAME       , ')'          , ']'          ,            , 10       
DEL  , NAME       , NUMBER       , NAME         ,            , 10       
DEL  , '<'        , '/'          , NAME         ,            , 10       
INS  , ']'        ,              , NUMBER       , ','        , 10       
SUB  , 99         , NAME         , ':'          , 'except'   , 10       
DEL  , '...'      , '...'        , '.'          ,            , 10       
INS  , NAME       ,              , STRING       , ':'        , 10       
INS  , 99         ,              , NEWLINE      , NAME       , 10       
DEL  , ':'        , '//'         , NAME         ,            , 10       
INS  , ':'        ,              , EOS          , NUMBER     , 10       
DEL  , NAME       , ':'          , '/'          ,            , 10       
SUB  , ')'        , NEWLINE      , 98           , ':'        , 10       
INS  , '('        ,              , STRING       , '('        , 10       
DEL  , ']'        , ']'          , NEWLINE      ,            , 10       
DEL  , NEWLINE    , NAME         , NAME         ,            , 10       
DEL  , ')'        , ')'          , ')'          ,            , 10       
INS  , '('        ,              , ','          , NAME       , 10       
INS  , STRING     ,              , NAME         , '.'        , 10       
INS  , NAME       ,              , EOS          , ']'        , 10       
INS  , ')'        ,              , 'return'     , ')'        , 10       
INS  , NAME       ,              , 'if'         , 'in'       , 10       
INS  , NAME       ,              , ','          , '('        , 10       
DEL  , BOS        , 'if'         , NAME         ,            , 10       
SUB  , BOS        , 'import'     , '.'          , 'from'     , 10       
INS  , STRING     ,              , ','          , '}'        , 9        
DEL  , ':'        , NAME         , NEWLINE      ,            , 9        
DEL  , STRING     , NUMBER       , ','          ,            , 9        
INS  , NUMBER     ,              , ']'          , ')'        , 9        
DEL  , ')'        , ')'          , '.'          ,            , 9        
DEL  , NAME       , '**'         , ')'          ,            , 9        
DEL  , NAME       , ':'          , NUMBER       ,            , 9        
DEL  , NAME       , ';'          , NAME         ,            , 9        
DEL  , STRING     , UNKNOWN_CHAR , '.'          ,            , 9        
INS  , NEWLINE    ,              , 'with'       , 99         , 9        
DEL  , 98         , '^'          , NEWLINE      ,            , 9        
DEL  , ')'        , '>'          , EOS          ,            , 9        
DEL  , NAME       , NAME         , 'for'        ,            , 9        
INS  , 98         ,              , 99           , 'return'   , 9        
INS  , NAME       ,              , '='          , '('        , 9        
DEL  , NAME       , NAME         , '*'          ,            , 9        
INS  , ':'        ,              , STRING       , '{'        , 9        
DEL  , 98         , 'for'        , NAME         ,            , 9        
DEL  , NEWLINE    , '/'          , NAME         ,            , 9        
SUB  , STRING     , '='          , NAME         , ':'        , 9        
INS  , 98         ,              , EOS          , NEWLINE    , 9        
INS  , NAME       ,              , ')'          , '}'        , 9        
DEL  , '}'        , ')'          , EOS          ,            , 9        
DEL  , ','        , '<'          , NAME         ,            , 9        
DEL  , NAME       , NAME         , '}'          ,            , 9        
INS  , 'def'      ,              , '('          , NAME       , 9        
DEL  , STRING     , ')'          , NEWLINE      ,            , 9        
INS  , NUMBER     ,              , NAME         , NEWLINE    , 9        
INS  , '}'        ,              , NEWLINE      , ')'        , 9        
DEL  , ','        , '**'         , STRING       ,            , 9        
DEL  , NAME       , ')'          , ','          ,            , 9        
DEL  , ')'        , NEWLINE      , '<'          ,            , 9        
DEL  , '}'        , '}'          , EOS          ,            , 9        
DEL  , ')'        , '>'          , NEWLINE      ,            , 9        
INS  , '}'        ,              , NAME         , ')'        , 9        
SUB  , ']'        , '='          , STRING       , '=='       , 9        
SUB  , NAME       , ':'          , NAME         , ','        , 9        
SUB  , NAME       , '('          , NUMBER       , '['        , 9        
DEL  , '('        , '<'          , NAME         ,            , 9        
SUB  , ','        , 'pass'       , ')'          , NAME       , 9        
SUB  , ')'        , ';'          , NEWLINE      , ':'        , 9        
SUB  , NEWLINE    , '...'        , NAME         , 98         , 9        
INS  , 99         ,              , NAME         , 'class'    , 9        
DEL  , NAME       , STRING       , '+'          ,            , 9        
INS  , STRING     ,              , NAME         , ']'        , 9        
INS  , '('        ,              , NAME         , '['        , 9        
DEL  , NAME       , '...'        , NEWLINE      ,            , 9        
DEL  , NAME       , NAME         , '+'          ,            , 9        
INS  , ')'        ,              , ')'          , '}'        , 9        
INS  , '['        ,              , NEWLINE      , ']'        , 9        
DEL  , NAME       , '>>'         , '>'          ,            , 9        
INS  , NAME       ,              , '='          , NEWLINE    , 9        
INS  , NEWLINE    ,              , '='          , NAME       , 9        
INS  , 'except'   ,              , NAME         , '('        , 9        
SUB  , '+'        , UNKNOWN_CHAR , UNKNOWN_CHAR , STRING     , 9        
DEL  , ']'        , '>>'         , '>'          ,            , 9        
INS  , NAME       ,              , NAME         , '-'        , 9        
DEL  , ')'        , 98           , '.'          ,            , 9        
INS  , 'in'       ,              , 'if'         , NAME       , 9        
INS  , NAME       ,              , NAME         , '['        , 9        
SUB  , ','        , '*'          , NAME         , '**'       , 9        
DEL  , NEWLINE    , 99           , 'import'     ,            , 8        
INS  , NEWLINE    ,              , '@'          , 98         , 8        
SUB  , STRING     , ','          , NUMBER       , ':'        , 8        
DEL  , STRING     , '}'          , ','          ,            , 8        
DEL  , ']'        , NAME         , '='          ,            , 8        
DEL  , BOS        , NAME         , '('          ,            , 8        
DEL  , BOS        , '('          , ')'          ,            , 8        
DEL  , ','        , '['          , NAME         ,            , 8        
DEL  , '('        , NUMBER       , NAME         ,            , 8        
DEL  , BOS        , 98           , 'try'        ,            , 8        
DEL  , NAME       , NUMBER       , '/'          ,            , 8        
INS  , STRING     ,              , ':'          , STRING     , 8        
INS  , '['        ,              , STRING       , '{'        , 8        
DEL  , STRING     , 99           , '}'          ,            , 8        
DEL  , '='        , NEWLINE      , 98           ,            , 8        
INS  , STRING     ,              , ','          , ':'        , 8        
SUB  , '='        , '['          , NAME         , '{'        , 8        
DEL  , NAME       , NAME         , 'and'        ,            , 8        
INS  , '['        ,              , 'for'        , NAME       , 8        
DEL  , NEWLINE    , '.'          , EOS          ,            , 8        
INS  , 99         ,              , 'for'        , 99         , 8        
INS  , 'return'   ,              , 99           , NEWLINE    , 8        
DEL  , BOS        , '>'          , 'def'        ,            , 8        
INS  , ')'        ,              , NEWLINE      , ']'        , 8        
DEL  , NEWLINE    , 99           , 'while'      ,            , 8        
DEL  , STRING     , UNKNOWN_CHAR , STRING       ,            , 8        
INS  , NAME       ,              , EOS          , '('        , 8        
DEL  , NEWLINE    , '...'        , NAME         ,            , 8        
INS  , '('        ,              , '='          , NAME       , 8        
DEL  , NUMBER     , UNKNOWN_CHAR , '}'          ,            , 8        
DEL  , NEWLINE    , 98           , 'with'       ,            , 8        
DEL  , NUMBER     , NAME         , ')'          ,            , 8        
SUB  , '='        , '['          , NUMBER       , '{'        , 8        
SUB  , NEWLINE    , STRING       , NEWLINE      , 98         , 8        
INS  , STRING     ,              , '['          , ':'        , 8        
DEL  , ')'        , NAME         , ','          ,            , 8        
SUB  , NAME       , UNKNOWN_CHAR , NAME         , '.'        , 8        
DEL  , '='        , ','          , NAME         ,            , 8        
INS  , 99         ,              , 'except'     , 99         , 8        
DEL  , '('        , '('          , NUMBER       ,            , 8        
SUB  , ']'        , NEWLINE      , 99           , '}'        , 8        
DEL  , NAME       , STRING       , ']'          ,            , 8        
DEL  , STRING     , NEWLINE      , 98           ,            , 8        
SUB  , 'else'     , NEWLINE      , 98           , ':'        , 8        
INS  , '='        ,              , NEWLINE      , STRING     , 8        
INS  , NEWLINE    ,              , NAME         , '('        , 8        
DEL  , NEWLINE    , 98           , 'try'        ,            , 8        
INS  , ']'        ,              , ','          , '}'        , 8        
DEL  , '...'      , '...'        , NEWLINE      ,            , 8        
INS  , BOS        ,              , NAME         , 'if'       , 8        
INS  , 98         ,              , NAME         , 'yield'    , 8        
INS  , NEWLINE    ,              , 'else'       , 98         , 8        
INS  , ')'        ,              , NAME         , ']'        , 8        
DEL  , ','        , ','          , NUMBER       ,            , 8        
INS  , NUMBER     ,              , NAME         , ')'        , 8        
DEL  , STRING     , '**'         , ','          ,            , 8        
INS  , NAME       ,              , NUMBER       , '('        , 8        
INS  , '}'        ,              , ')'          , '}'        , 8        
DEL  , NAME       , '<'          , NAME         ,            , 8        
INS  , NEWLINE    ,              , '.'          , NAME       , 8        
DEL  , 98         , '**'         , NAME         ,            , 8        
DEL  , ']'        , NEWLINE      , '>>'         ,            , 8        
DEL  , ':'        , ':'          , NEWLINE      ,            , 8        
DEL  , 'for'      , '('          , NAME         ,            , 8        
DEL  , STRING     , ','          , '%'          ,            , 8        
DEL  , NEWLINE    , '>'          , '['          ,            , 8        
INS  , ')'        ,              , NAME         , 'if'       , 8        
INS  , ')'        ,              , ')'          , '('        , 8        
DEL  , NUMBER     , ':'          , NEWLINE      ,            , 8        
DEL  , NAME       , '['          , NAME         ,            , 8        
INS  , '...'      ,              , EOS          , NEWLINE    , 8        
INS  , '('        ,              , '.'          , NAME       , 8        
DEL  , '='        , NAME         , STRING       ,            , 7        
DEL  , NAME       , NEWLINE      , 98           ,            , 7        
DEL  , STRING     , NAME         , '='          ,            , 7        
DEL  , 99         , '//'         , NAME         ,            , 7        
DEL  , ':'        , '<'          , NAME         ,            , 7        
DEL  , 'and'      , UNKNOWN_CHAR , NAME         ,            , 7        
INS  , NEWLINE    ,              , 'yield'      , 98         , 7        
DEL  , NEWLINE    , 98           , UNKNOWN_CHAR ,            , 7        
DEL  , BOS        , ')'          , ':'          ,            , 7        
DEL  , STRING     , NUMBER       , ')'          ,            , 7        
INS  , NUMBER     ,              , '('          , ','        , 7        
DEL  , NAME       , 'as'         , NAME         ,            , 7        
DEL  , 98         , STRING       , NEWLINE      ,            , 7        
DEL  , ':'        , NEWLINE      , '['          ,            , 7        
SUB  , 98         , 'return'     , NEWLINE      , NAME       , 7        
INS  , NEWLINE    ,              , 'raise'      , 98         , 7        
SUB  , BOS        , '['          , STRING       , '{'        , 7        
INS  , 99         ,              , EOS          , NAME       , 7        
INS  , '='        ,              , STRING       , '['        , 7        
DEL  , BOS        , 98           , 'if'         ,            , 7        
INS  , '}'        ,              , ','          , ']'        , 7        
DEL  , ','        , NEWLINE      , NAME         ,            , 7        
DEL  , BOS        , UNKNOWN_CHAR , '{'          ,            , 7        
INS  , '='        ,              , NEWLINE      , NUMBER     , 7        
DEL  , NAME       , UNKNOWN_CHAR , '='          ,            , 7        
DEL  , NAME       , NAME         , 'with'       ,            , 7        
DEL  , NAME       , 'with'       , NAME         ,            , 7        
DEL  , BOS        , NUMBER       , NAME         ,            , 7        
DEL  , ')'        , '/'          , NAME         ,            , 7        
DEL  , ')'        , NEWLINE      , '//'         ,            , 7        
INS  , STRING     ,              , STRING       , ':'        , 7        
SUB  , '('        , UNKNOWN_CHAR , '.'          , STRING     , 7        
DEL  , BOS        , UNKNOWN_CHAR , 'for'        ,            , 7        
INS  , NUMBER     ,              , '}'          , ']'        , 7        
DEL  , '...'      , '...'        , EOS          ,            , 7        
DEL  , NUMBER     , NAME         , ']'          ,            , 7        
INS  , NEWLINE    ,              , NAME         , 'from'     , 7        
INS  , NEWLINE    ,              , 'break'      , 99         , 7        
DEL  , 98         , NAME         , NEWLINE      ,            , 7        
INS  , NUMBER     ,              , '...'        , ','        , 7        
DEL  , STRING     , ')'          , EOS          ,            , 7        
DEL  , STRING     , NAME         , 'is'         ,            , 7        
DEL  , NEWLINE    , '<'          , '/'          ,            , 7        
SUB  , NAME       , '='          , NAME         , ':'        , 7        
INS  , NAME       ,              , '('          , '='        , 7        
DEL  , STRING     , ']'          , ','          ,            , 7        
INS  , NAME       ,              , NAME         , 'as'       , 7        
INS  , '}'        ,              , ']'          , '}'        , 7        
DEL  , BOS        , '**'         , 'from'       ,            , 7        
DEL  , ','        , '['          , '('          ,            , 7        
SUB  , NAME       , STRING       , ','          , '='        , 7        
SUB  , 99         , 'break'      , NEWLINE      , NAME       , 7        
DEL  , BOS        , ':'          , 'import'     ,            , 7        
DEL  , STRING     , '/'          , NAME         ,            , 7        
DEL  , ')'        , STRING       , NEWLINE      ,            , 7        
DEL  , ':'        , 98           , 'return'     ,            , 7        
INS  , '}'        ,              , EOS          , NEWLINE    , 7        
DEL  , NEWLINE    , '>'          , NEWLINE      ,            , 7        
DEL  , NEWLINE    , '.'          , NEWLINE      ,            , 7        
DEL  , BOS        , NAME         , STRING       ,            , 7        
DEL  , STRING     , NAME         , '*'          ,            , 7        
DEL  , NUMBER     , '}'          , ','          ,            , 7        
INS  , NEWLINE    ,              , NAME         , 'class'    , 7        
INS  , '='        ,              , '['          , NAME       , 7        
SUB  , 99         , 'return'     , NEWLINE      , NAME       , 7        
DEL  , '*'        , NEWLINE      , NAME         ,            , 7        
DEL  , ')'        , ')'          , ';'          ,            , 7        
DEL  , ')'        , NAME         , '('          ,            , 7        
DEL  , ')'        , NEWLINE      , '.'          ,            , 7        
INS  , NAME       ,              , 'def'        , NEWLINE    , 7        
DEL  , STRING     , NUMBER       , NEWLINE      ,            , 7        
DEL  , 99         , 'else'       , ':'          ,            , 7        
DEL  , 99         , ':'          , NEWLINE      ,            , 7        
DEL  , ')'        , ']'          , EOS          ,            , 7        
DEL  , ','        , NAME         , UNKNOWN_CHAR ,            , 7        
DEL  , BOS        , '>'          , 'for'        ,            , 7        
DEL  , STRING     , ':'          , NEWLINE      ,            , 7        
SUB  , NEWLINE    , 'break'      , NEWLINE      , 99         , 7        
INS  , ')'        ,              , '['          , NEWLINE    , 7        
INS  , ']'        ,              , NAME         , '='        , 7        
DEL  , 99         , '<'          , NAME         ,            , 7        
DEL  , ':'        , '**'         , NEWLINE      ,            , 7        
DEL  , NAME       , '('          , '['          ,            , 7        
DEL  , NUMBER     , ')'          , NEWLINE      ,            , 7        
DEL  , BOS        , '>>'         , NAME         ,            , 7        
DEL  , NAME       , NAME         , '...'        ,            , 7        
INS  , ':'        ,              , NAME         , '['        , 7        
INS  , NEWLINE    ,              , NAME         , 'with'     , 7        
DEL  , BOS        , NAME         , '{'          ,            , 7        
DEL  , BOS        , '>'          , NUMBER       ,            , 7        
DEL  , ')'        , NAME         , ':'          ,            , 7        
DEL  , ','        , '...'        , '}'          ,            , 7        
DEL  , ','        , '>'          , STRING       ,            , 7        
DEL  , NEWLINE    , '>'          , NUMBER       ,            , 7        
INS  , NAME       ,              , EOS          , ':'        , 7        
DEL  , NUMBER     , UNKNOWN_CHAR , ','          ,            , 7        
DEL  , 98         , '//'         , NAME         ,            , 7        
INS  , STRING     ,              , '}'          , ']'        , 7        
INS  , ']'        ,              , NAME         , '.'        , 7        
INS  , NAME       ,              , NAME         , '%'        , 7        
DEL  , ')'        , '//'         , NAME         ,            , 7        
INS  , BOS        ,              , NAME         , '('        , 7        
SUB  , BOS        , '//'         , NAME         , NEWLINE    , 7        
SUB  , NAME       , ')'          , EOS          , ']'        , 7        
DEL  , NEWLINE    , NAME         , '['          ,            , 7        
INS  , 'for'      ,              , 'if'         , NAME       , 7        
INS  , ')'        ,              , '['          , ')'        , 7        
SUB  , NAME       , ','          , STRING       , '('        , 7        
INS  , '('        ,              , 'if'         , ')'        , 7        
INS  , ')'        ,              , ')'          , ']'        , 7        
INS  , BOS        ,              , '['          , '('        , 6        
DEL  , BOS        , NAME         , NEWLINE      ,            , 6        
DEL  , ')'        , '}'          , EOS          ,            , 6        
DEL  , NAME       , '.'          , ']'          ,            , 6        
DEL  , NUMBER     , UNKNOWN_CHAR , NAME         ,            , 6        
SUB  , STRING     , NUMBER       , UNKNOWN_CHAR , STRING     , 6        
INS  , NEWLINE    ,              , NAME         , 'if'       , 6        
INS  , ']'        ,              , ')'          , '}'        , 6        
DEL  , NAME       , '-'          , '-'          ,            , 6        
INS  , '['        ,              , ']'          , NAME       , 6        
DEL  , NEWLINE    , '}'          , EOS          ,            , 6        
DEL  , 'elif'     , 'if'         , NAME         ,            , 6        
INS  , ']'        ,              , NAME         , '}'        , 6        
DEL  , BOS        , '>'          , '{'          ,            , 6        
DEL  , NAME       , NUMBER       , ':'          ,            , 6        
SUB  , NAME       , '('          , NAME         , '['        , 6        
DEL  , NUMBER     , UNKNOWN_CHAR , UNKNOWN_CHAR ,            , 6        
DEL  , NAME       , NUMBER       , '='          ,            , 6        
DEL  , BOS        , '**'         , 'import'     ,            , 6        
DEL  , BOS        , NAME         , UNKNOWN_CHAR ,            , 6        
DEL  , STRING     , NAME         , '('          ,            , 6        
DEL  , ','        , NEWLINE      , '{'          ,            , 6        
DEL  , BOS        , 98           , UNKNOWN_CHAR ,            , 6        
DEL  , ','        , 98           , STRING       ,            , 6        
DEL  , NAME       , '('          , NUMBER       ,            , 6        
SUB  , STRING     , NEWLINE      , NAME         , ':'        , 6        
INS  , ':'        ,              , ','          , STRING     , 6        
DEL  , 99         , NAME         , EOS          ,            , 6        
DEL  , '.'        , UNKNOWN_CHAR , NAME         ,            , 6        
INS  , 98         ,              , NAME         , 'raise'    , 6        
INS  , '='        ,              , EOS          , NAME       , 6        
DEL  , BOS        , NAME         , 'import'     ,            , 6        
DEL  , ']'        , '**'         , NEWLINE      ,            , 6        
INS  , ':'        ,              , NUMBER       , '['        , 6        
DEL  , STRING     , ':'          , UNKNOWN_CHAR ,            , 6        
DEL  , '}'        , '}'          , ']'          ,            , 6        
DEL  , '['        , '['          , NAME         ,            , 6        
INS  , '='        ,              , '.'          , NAME       , 6        
INS  , ']'        ,              , NAME         , ':'        , 6        
DEL  , '...'      , '.'          , ')'          ,            , 6        
DEL  , NAME       , '['          , ']'          ,            , 6        
DEL  , STRING     , NUMBER       , NAME         ,            , 6        
SUB  , NAME       , ':'          , STRING       , '='        , 6        
DEL  , NEWLINE    , 98           , STRING       ,            , 6        
INS  , ':'        ,              , NEWLINE      , STRING     , 6        
INS  , ')'        ,              , '}'          , ')'        , 6        
INS  , STRING     ,              , ']'          , '}'        , 6        
SUB  , NAME       , STRING       , ','          , '('        , 6        
DEL  , ']'        , NAME         , NEWLINE      ,            , 6        
SUB  , '+'        , UNKNOWN_CHAR , NAME         , STRING     , 6        
INS  , STRING     ,              , 'as'         , ')'        , 6        
SUB  , BOS        , NEWLINE      , 98           , '['        , 6        
SUB  , BOS        , 'def'        , NAME         , 'class'    , 6        
DEL  , NUMBER     , NUMBER       , NAME         ,            , 6        
SUB  , STRING     , UNKNOWN_CHAR , '}'          , ':'        , 6        
SUB  , ','        , UNKNOWN_CHAR , ')'          , STRING     , 6        
DEL  , BOS        , '>'          , NEWLINE      ,            , 6        
DEL  , ','        , UNKNOWN_CHAR , STRING       ,            , 6        
DEL  , NUMBER     , '-'          , NUMBER       ,            , 6        
DEL  , NEWLINE    , '...'        , NEWLINE      ,            , 6        
SUB  , NAME       , STRING       , NEWLINE      , '='        , 6        
DEL  , BOS        , '>'          , 'with'       ,            , 6        
DEL  , NAME       , '-'          , NUMBER       ,            , 6        
DEL  , '-'        , NAME         , STRING       ,            , 6        
SUB  , BOS        , 'import'     , NAME         , 'from'     , 6        
INS  , ','        ,              , STRING       , '{'        , 6        
DEL  , '+'        , UNKNOWN_CHAR , NAME         ,            , 6        
DEL  , ')'        , 'if'         , NAME         ,            , 6        
SUB  , STRING     , NEWLINE      , 99           , '}'        , 6        
DEL  , BOS        , '>'          , STRING       ,            , 6        
DEL  , '-'        , '->'         , NAME         ,            , 6        
INS  , ']'        ,              , '...'        , ','        , 6        
SUB  , BOS        , 'from'       , NAME         , 'import'   , 6        
DEL  , NEWLINE    , 'import'     , NAME         ,            , 6        
DEL  , NEWLINE    , 99           , '['          ,            , 6        
DEL  , BOS        , '.'          , NEWLINE      ,            , 6        
DEL  , ')'        , UNKNOWN_CHAR , '.'          ,            , 6        
INS  , NAME       ,              , NEWLINE      , '.'        , 6        
DEL  , ')'        , '<'          , NAME         ,            , 6        
DEL  , ','        , '.'          , '.'          ,            , 6        
SUB  , STRING     , ':'          , NAME         , ','        , 6        
INS  , ']'        ,              , '('          , ','        , 6        
SUB  , NEWLINE    , 98           , 'for'        , 99         , 6        
INS  , STRING     ,              , NUMBER       , ','        , 6        
INS  , NUMBER     ,              , NAME         , ']'        , 6        
INS  , NAME       ,              , '{'          , '('        , 6        
SUB  , ']'        , ']'          , EOS          , '}'        , 6        
INS  , 98         ,              , NEWLINE      , NAME       , 6        
SUB  , NAME       , '='          , '['          , ':'        , 6        
DEL  , NAME       , '...'        , '.'          ,            , 6        
SUB  , NAME       , NAME         , NEWLINE      , ')'        , 6        
DEL  , NAME       , '/'          , ')'          ,            , 6        
DEL  , NAME       , UNKNOWN_CHAR , '%'          ,            , 6        
DEL  , '...'      , '.'          , ','          ,            , 6        
INS  , 'True'     ,              , EOS          , ')'        , 6        
INS  , ')'        ,              , '...'        , NEWLINE    , 6        
DEL  , 99         , 'def'        , NAME         ,            , 6        
INS  , NAME       ,              , '}'          , ')'        , 6        
DEL  , ']'        , NEWLINE      , '}'          ,            , 6        
SUB  , ':'        , UNKNOWN_CHAR , NUMBER       , STRING     , 6        
DEL  , '...'      , NAME         , NAME         ,            , 6        
DEL  , STRING     , NAME         , 'in'         ,            , 6        
DEL  , NAME       , ':'          , UNKNOWN_CHAR ,            , 6        
INS  , ','        ,              , '{'          , STRING     , 6        
INS  , NEWLINE    ,              , 'yield'      , 99         , 6        
SUB  , '='        , UNKNOWN_CHAR , ')'          , STRING     , 6        
INS  , ']'        ,              , '['          , ','        , 6        
DEL  , ':'        , ':'          , NUMBER       ,            , 6        
DEL  , STRING     , NUMBER       , '}'          ,            , 6        
SUB  , ']'        , ';'          , '['          , ','        , 6        
DEL  , NAME       , '*'          , ')'          ,            , 6        
DEL  , ')'        , STRING       , EOS          ,            , 6        
SUB  , NAME       , ')'          , NEWLINE      , ']'        , 6        
DEL  , ')'        , NAME         , NUMBER       ,            , 6        
INS  , BOS        ,              , NAME         , '['        , 6        
INS  , 'lambda'   ,              , NAME         , ':'        , 6        
SUB  , NAME       , '('          , ':'          , '['        , 6        
DEL  , NUMBER     , ']'          , ':'          ,            , 6        
SUB  , 'else'     , 'None'       , NEWLINE      , NAME       , 6        
INS  , '('        ,              , '['          , '('        , 6        
SUB  , NAME       , ')'          , NEWLINE      , ':'        , 6        
SUB  , NAME       , NAME         , NEWLINE      , '('        , 6        
INS  , STRING     ,              , '=='         , ']'        , 6        
SUB  , ')'        , '='          , NAME         , '=='       , 6        
INS  , NEWLINE    ,              , 99           , NAME       , 6        
INS  , ':'        ,              , '}'          , NUMBER     , 6        
DEL  , NEWLINE    , 'if'         , NAME         ,            , 6        
SUB  , NAME       , '='          , NUMBER       , ':'        , 6        
DEL  , ','        , UNKNOWN_CHAR , EOS          ,            , 5        
SUB  , NAME       , NAME         , NEWLINE      , '='        , 5        
INS  , NAME       ,              , NUMBER       , '='        , 5        
INS  , ']'        ,              , 'for'        , ']'        , 5        
SUB  , STRING     , NAME         , UNKNOWN_CHAR , ']'        , 5        
DEL  , NUMBER     , NAME         , '['          ,            , 5        
INS  , ']'        ,              , '}'          , ']'        , 5        
DEL  , ')'        , '-'          , '-'          ,            , 5        
DEL  , NAME       , 'is'         , NAME         ,            , 5        
SUB  , 99         , 'else'       , 'if'         , 'elif'     , 5        
DEL  , NEWLINE    , 'and'        , NAME         ,            , 5        
INS  , 'in'       ,              , NEWLINE      , NAME       , 5        
DEL  , ')'        , '&'          , EOS          ,            , 5        
INS  , 'True'     ,              , NEWLINE      , ':'        , 5        
DEL  , NAME       , STRING       , UNKNOWN_CHAR ,            , 5        
SUB  , NAME       , UNKNOWN_CHAR , UNKNOWN_CHAR , '.'        , 5        
DEL  , NAME       , '['          , '('          ,            , 5        
DEL  , STRING     , '**'         , NEWLINE      ,            , 5        
INS  , STRING     ,              , '.'          , ')'        , 5        
DEL  , '}'        , '.'          , EOS          ,            , 5        
DEL  , STRING     , NAME         , '@'          ,            , 5        
INS  , ':'        ,              , EOS          , STRING     , 5        
DEL  , NUMBER     , ')'          , ','          ,            , 5        
DEL  , ']'        , ':'          , EOS          ,            , 5        
DEL  , NAME       , '='          , STRING       ,            , 5        
INS  , STRING     ,              , '}'          , ')'        , 5        
INS  , NAME       ,              , '('          , NEWLINE    , 5        
DEL  , STRING     , ']'          , ')'          ,            , 5        
DEL  , NUMBER     , NUMBER       , '/'          ,            , 5        
DEL  , '}'        , NAME         , NAME         ,            , 5        
SUB  , '('        , '<'          , NAME         , STRING     , 5        
INS  , NAME       ,              , '.'          , NEWLINE    , 5        
SUB  , NUMBER     , ']'          , EOS          , '}'        , 5        
DEL  , STRING     , '.'          , EOS          ,            , 5        
DEL  , ':'        , NAME         , STRING       ,            , 5        
INS  , NUMBER     ,              , EOS          , NEWLINE    , 5        
SUB  , NEWLINE    , 98           , 'def'        , 99         , 5        
DEL  , ','        , NAME         , NAME         ,            , 5        
INS  , NEWLINE    ,              , NAME         , 'while'    , 5        
INS  , '('        ,              , '/'          , STRING     , 5        
DEL  , ']'        , '*'          , EOS          ,            , 5        
INS  , ':'        ,              , '{'          , '['        , 5        
INS  , ','        ,              , ':'          , STRING     , 5        
DEL  , NEWLINE    , 99           , '...'        ,            , 5        
DEL  , NAME       , '...'        , EOS          ,            , 5        
INS  , 'False'    ,              , NAME         , ','        , 5        
SUB  , '('        , 'from'       , ','          , NAME       , 5        
DEL  , ']'        , '<'          , '/'          ,            , 5        
SUB  , BOS        , UNKNOWN_CHAR , NAME         , STRING     , 5        
INS  , ','        ,              , NAME         , ')'        , 5        
DEL  , ':'        , NEWLINE      , 98           ,            , 5        
DEL  , '='        , UNKNOWN_CHAR , '['          ,            , 5        
DEL  , NAME       , '*'          , NEWLINE      ,            , 5        
INS  , ':'        ,              , NAME         , '('        , 5        
DEL  , 99         , '**'         , '*'          ,            , 5        
DEL  , 99         , '*'          , EOS          ,            , 5        
DEL  , ')'        , UNKNOWN_CHAR , ')'          ,            , 5        
SUB  , ','        , UNKNOWN_CHAR , ':'          , STRING     , 5        
DEL  , BOS        , 98           , 'with'       ,            , 5        
DEL  , ','        , NEWLINE      , 99           ,            , 5        
INS  , NAME       ,              , '.'          , ')'        , 5        
DEL  , STRING     , '//'         , NAME         ,            , 5        
SUB  , NEWLINE    , 99           , 'return'     , 98         , 5        
INS  , 99         ,              , 'class'      , 99         , 5        
DEL  , ':'        , NAME         , UNKNOWN_CHAR ,            , 5        
INS  , NEWLINE    ,              , 'assert'     , 98         , 5        
DEL  , NAME       , NUMBER       , '-'          ,            , 5        
SUB  , NUMBER     , NEWLINE      , NAME         , ':'        , 5        
DEL  , ','        , UNKNOWN_CHAR , UNKNOWN_CHAR ,            , 5        
INS  , STRING     ,              , 'if'         , ')'        , 5        
DEL  , '}'        , 99           , ']'          ,            , 5        
SUB  , ']'        , ';'          , NAME         , ','        , 5        
DEL  , ')'        , NAME         , STRING       ,            , 5        
DEL  , '...'      , '...'        , ']'          ,            , 5        
DEL  , '.'        , '/'          , NAME         ,            , 5        
DEL  , ')'        , '**'         , ','          ,            , 5        
DEL  , ']'        , NAME         , UNKNOWN_CHAR ,            , 5        
DEL  , STRING     , 99           , EOS          ,            , 5        
INS  , NEWLINE    ,              , NEWLINE      , 98         , 5        
DEL  , STRING     , '.'          , '.'          ,            , 5        
DEL  , NEWLINE    , 'for'        , NAME         ,            , 5        
DEL  , NEWLINE    , NAME         , 'from'       ,            , 5        
SUB  , NAME       , NAME         , NEWLINE      , ':'        , 5        
DEL  , BOS        , '//'         , NAME         ,            , 5        
DEL  , ')'        , NEWLINE      , NAME         ,            , 5        
DEL  , ','        , NUMBER       , NAME         ,            , 5        
DEL  , '='        , NAME         , '='          ,            , 5        
DEL  , BOS        , 98           , 'while'      ,            , 5        
INS  , ']'        ,              , ']'          , '}'        , 5        
DEL  , NAME       , 99           , NAME         ,            , 5        
DEL  , ')'        , NUMBER       , NEWLINE      ,            , 5        
INS  , ']'        ,              , 'return'     , NEWLINE    , 5        
DEL  , ','        , STRING       , NAME         ,            , 5        
SUB  , ')'        , '='          , NUMBER       , '=='       , 5        
DEL  , '...'      , NAME         , '...'        ,            , 5        
DEL  , '='        , '/'          , NAME         ,            , 5        
SUB  , ']'        , ']'          , NEWLINE      , '}'        , 5        
INS  , NAME       ,              , '('          , 'in'       , 5        
SUB  , NEWLINE    , 98           , 'return'     , 99         , 5        
SUB  , ','        , UNKNOWN_CHAR , '%'          , STRING     , 5        
DEL  , '-'        , '%'          , NAME         ,            , 5        
DEL  , NAME       , '-'          , '%'          ,            , 5        
DEL  , '='        , '**'         , NAME         ,            , 5        
INS  , '+'        ,              , NAME         , '('        , 5        
DEL  , ';'        , UNKNOWN_CHAR , EOS          ,            , 5        
INS  , STRING     ,              , '...'        , ','        , 5        
DEL  , NEWLINE    , 99           , '}'          ,            , 5        
INS  , STRING     ,              , ')'          , STRING     , 5        
DEL  , 99         , ')'          , EOS          ,            , 5        
SUB  , ')'        , ')'          , EOS          , ']'        , 5        
SUB  , '+'        , UNKNOWN_CHAR , ')'          , STRING     , 5        
DEL  , 98         , 'return'     , NAME         ,            , 5        
INS  , '='        ,              , '/'          , STRING     , 5        
DEL  , NUMBER     , NAME         , NUMBER       ,            , 5        
INS  , ')'        ,              , '=='         , ')'        , 5        
DEL  , '('        , '**'         , NAME         ,            , 5        
DEL  , 99         , 99           , 'if'         ,            , 5        
DEL  , NEWLINE    , 99           , '.'          ,            , 5        
INS  , NUMBER     ,              , NEWLINE      , ']'        , 5        
INS  , ')'        ,              , NAME         , ':'        , 5        
INS  , STRING     ,              , NUMBER       , ':'        , 5        
DEL  , STRING     , NAME         , '+'          ,            , 5        
INS  , '='        ,              , NEWLINE      , NAME       , 5        
SUB  , BOS        , 'pass'       , '='          , NAME       , 5        
DEL  , ')'        , ')'          , '+'          ,            , 5        
DEL  , NUMBER     , ')'          , ':'          ,            , 5        
INS  , NEWLINE    ,              , 'elif'       , 99         , 5        
DEL  , NAME       , NUMBER       , '>'          ,            , 5        
DEL  , BOS        , NAME         , ':'          ,            , 5        
DEL  , BOS        , '*'          , NEWLINE      ,            , 5        
INS  , NUMBER     ,              , NAME         , '*'        , 5        
DEL  , STRING     , UNKNOWN_CHAR , '+'          ,            , 5        
INS  , NAME       ,              , NAME         , 'for'      , 5        
INS  , '='        ,              , '-'          , '('        , 5        
DEL  , 99         , 99           , '<'          ,            , 5        
DEL  , BOS        , '>>'         , 'from'       ,            , 5        
DEL  , NAME       , NAME         , '<'          ,            , 5        
DEL  , STRING     , NUMBER       , '-'          ,            , 5        
INS  , '['        ,              , STRING       , '('        , 5        
SUB  , '('        , UNKNOWN_CHAR , UNKNOWN_CHAR , STRING     , 5        
DEL  , ')'        , NAME         , ')'          ,            , 5        
DEL  , STRING     , NAME         , '%'          ,            , 5        
DEL  , 99         , UNKNOWN_CHAR , NEWLINE      ,            , 5        
SUB  , STRING     , ','          , NAME         , ':'        , 5        
INS  , NAME       ,              , 'if'         , 'for'      , 5        
SUB  , NAME       , 'for'        , NAME         , 'in'       , 5        
SUB  , NAME       , '.'          , NAME         , 'import'   , 5        
INS  , ')'        ,              , '+'          , ')'        , 5        
DEL  , NAME       , '.'          , 'import'     ,            , 5        
DEL  , NAME       , '>'          , ','          ,            , 5        
INS  , ')'        ,              , 'for'        , ']'        , 5        
DEL  , ']'        , NEWLINE      , 98           ,            , 5        
INS  , 'None'     ,              , NEWLINE      , ':'        , 5        
DEL  , NUMBER     , NAME         , 'in'         ,            , 5        
DEL  , '('        , ','          , ')'          ,            , 5        
SUB  , NAME       , '->'         , NAME         , '.'        , 5        
INS  , NEWLINE    ,              , NAME         , 'for'      , 5        
INS  , '('        ,              , 'for'        , NAME       , 5        
INS  , NAME       ,              , '.'          , '('        , 5        
DEL  , NEWLINE    , 99           , ':'          ,            , 5        
DEL  , NEWLINE    , ':'          , ')'          ,            , 5        
INS  , '='        ,              , NEWLINE      , '['        , 5        
SUB  , 99         , '>>'         , '>'          , NAME       , 5        
INS  , NAME       ,              , ']'          , '['        , 5        
INS  , '.'        ,              , ')'          , NAME       , 5        
DEL  , BOS        , '('          , NAME         ,            , 5        
INS  , '+'        ,              , NEWLINE      , STRING     , 4        
INS  , STRING     ,              , '{'          , ','        , 4        
DEL  , ')'        , '-'          , '->'         ,            , 4        
DEL  , NAME       , STRING       , 'is'         ,            , 4        
DEL  , '+'        , 98           , STRING       ,            , 4        
DEL  , NAME       , ':'          , '['          ,            , 4        
DEL  , NEWLINE    , '('          , NAME         ,            , 4        
INS  , BOS        ,              , STRING       , '['        , 4        
INS  , NEWLINE    ,              , STRING       , 99         , 4        
DEL  , ','        , NUMBER       , STRING       ,            , 4        
DEL  , ']'        , '}'          , ','          ,            , 4        
DEL  , ']'        , ')'          , ','          ,            , 4        
INS  , NEWLINE    ,              , 'from'       , 98         , 4        
DEL  , STRING     , '}'          , ')'          ,            , 4        
INS  , '('        ,              , NUMBER       , '['        , 4        
INS  , 'if'       ,              , NAME         , '('        , 4        
DEL  , STRING     , '{'          , '}'          ,            , 4        
INS  , BOS        ,              , NAME         , '{'        , 4        
DEL  , NAME       , NUMBER       , NUMBER       ,            , 4        
DEL  , '}'        , UNKNOWN_CHAR , NEWLINE      ,            , 4        
INS  , ']'        ,              , '['          , NEWLINE    , 4        
INS  , BOS        ,              , '{'          , '['        , 4        
SUB  , NEWLINE    , '>'          , NAME         , 98         , 4        
SUB  , NEWLINE    , 'class'      , '='          , NAME       , 4        
DEL  , STRING     , '>'          , ','          ,            , 4        
DEL  , STRING     , '.'          , STRING       ,            , 4        
DEL  , ','        , NAME         , '...'        ,            , 4        
SUB  , NEWLINE    , '...'        , 'return'     , 98         , 4        
SUB  , NAME       , STRING       , NUMBER       , ','        , 4        
DEL  , 'in'       , '**'         , NAME         ,            , 4        
INS  , 'None'     ,              , STRING       , ','        , 4        
INS  , NEWLINE    ,              , 'continue'   , 99         , 4        
SUB  , ')'        , ';'          , '('          , ','        , 4        
DEL  , ','        , ','          , '('          ,            , 4        
DEL  , NEWLINE    , '**'         , NEWLINE      ,            , 4        
INS  , NEWLINE    ,              , NAME         , '['        , 4        
DEL  , NAME       , NAME         , ';'          ,            , 4        
INS  , NAME       ,              , '['          , ')'        , 4        
SUB  , NAME       , 'in'         , NAME         , '='        , 4        
DEL  , NAME       , '.'          , '.'          ,            , 4        
DEL  , STRING     , '+'          , UNKNOWN_CHAR ,            , 4        
DEL  , STRING     , NAME         , '['          ,            , 4        
SUB  , STRING     , NEWLINE      , 98           , ':'        , 4        
DEL  , NAME       , '.'          , UNKNOWN_CHAR ,            , 4        
SUB  , NAME       , ')'          , NEWLINE      , '}'        , 4        
SUB  , NAME       , ')'          , EOS          , '}'        , 4        
DEL  , ':'        , '>'          , NEWLINE      ,            , 4        
SUB  , NEWLINE    , 'from'       , NAME         , 'import'   , 4        
INS  , NAME       ,              , NUMBER       , ','        , 4        
DEL  , 98         , NEWLINE      , NAME         ,            , 4        
SUB  , ')'        , UNKNOWN_CHAR , EOS          , NEWLINE    , 4        
INS  , 'as'       ,              , NEWLINE      , NAME       , 4        
DEL  , NAME       , 'not'        , NAME         ,            , 4        
SUB  , 98         , '>'          , EOS          , NEWLINE    , 4        
DEL  , BOS        , NAME         , '>'          ,            , 4        
DEL  , 98         , NAME         , '('          ,            , 4        
DEL  , 98         , '('          , NAME         ,            , 4        
DEL  , 98         , ')'          , NEWLINE      ,            , 4        
DEL  , BOS        , NUMBER       , 'import'     ,            , 4        
DEL  , BOS        , '*'          , 'from'       ,            , 4        
INS  , ')'        ,              , '...'        , ','        , 4        
SUB  , ','        , '.'          , '.'          , '...'      , 4        
SUB  , NEWLINE    , 'return'     , '='          , NAME       , 4        
INS  , 'break'    ,              , 99           , NEWLINE    , 4        
INS  , STRING     ,              , EOS          , STRING     , 4        
DEL  , NEWLINE    , 98           , '...'        ,            , 4        
INS  , NUMBER     ,              , ','          , ')'        , 4        
DEL  , BOS        , '>>'         , '['          ,            , 4        
DEL  , STRING     , '.'          , ')'          ,            , 4        
INS  , ']'        ,              , '.'          , ']'        , 4        
SUB  , NUMBER     , ')'          , EOS          , ']'        , 4        
DEL  , NAME       , UNKNOWN_CHAR , '*'          ,            , 4        
SUB  , NEWLINE    , 99           , 'try'        , 98         , 4        
DEL  , STRING     , '>'          , NEWLINE      ,            , 4        
DEL  , 99         , '**'         , NAME         ,            , 4        
INS  , STRING     ,              , EOS          , NEWLINE    , 4        
DEL  , ')'        , '**'         , ')'          ,            , 4        
INS  , NAME       ,              , NAME         , '+'        , 4        
SUB  , ':'        , '['          , STRING       , '{'        , 4        
SUB  , NEWLINE    , 'elif'       , NAME         , 'if'       , 4        
INS  , 99         ,              , EOS          , 'except'   , 4        
INS  , 'except'   ,              , EOS          , ':'        , 4        
DEL  , NAME       , '['          , STRING       ,            , 4        
SUB  , NAME       , 'if'         , NAME         , 'for'      , 4        
DEL  , ']'        , '...'        , EOS          ,            , 4        
SUB  , BOS        , '>>'         , '>'          , 'def'      , 4        
DEL  , 'return'   , '='          , NAME         ,            , 4        
DEL  , ')'        , '.'          , '['          ,            , 4        
DEL  , ')'        , '**'         , '**'         ,            , 4        
SUB  , NAME       , NEWLINE      , 98           , ':'        , 4        
SUB  , NAME       , UNKNOWN_CHAR , NEWLINE      , '('        , 4        
DEL  , NAME       , '/'          , NEWLINE      ,            , 4        
DEL  , 99         , 'if'         , NAME         ,            , 4        
INS  , '['        ,              , ']'          , STRING     , 4        
DEL  , BOS        , UNKNOWN_CHAR , 'if'         ,            , 4        
DEL  , NAME       , '.'          , ','          ,            , 4        
SUB  , 99         , 'continue'   , NEWLINE      , NAME       , 4        
DEL  , ']'        , '...'        , '...'        ,            , 4        
INS  , ')'        ,              , 'import'     , NEWLINE    , 4        
INS  , NUMBER     ,              , ','          , ']'        , 4        
DEL  , NAME       , '/'          , UNKNOWN_CHAR ,            , 4        
DEL  , NAME       , NAME         , 'from'       ,            , 4        
DEL  , STRING     , ':'          , ')'          ,            , 4        
DEL  , STRING     , '<'          , NAME         ,            , 4        
SUB  , NAME       , '}'          , EOS          , ')'        , 4        
DEL  , NEWLINE    , '**'         , 'for'        ,            , 4        
DEL  , NEWLINE    , '>'          , 'def'        ,            , 4        
SUB  , NEWLINE    , 99           , STRING       , 98         , 4        
DEL  , '>'        , '<'          , NAME         ,            , 4        
DEL  , ':'        , ','          , '['          ,            , 4        
INS  , 'in'       ,              , '('          , NAME       , 4        
SUB  , 'return'   , UNKNOWN_CHAR , NAME         , STRING     , 4        
INS  , 98         ,              , 'else'       , '('        , 4        
INS  , '('        ,              , 'else'       , ')'        , 4        
INS  , ')'        ,              , 'else'       , NEWLINE    , 4        
INS  , 99         ,              , 99           , 'except'   , 4        
INS  , 'except'   ,              , 99           , ':'        , 4        
INS  , '('        ,              , 99           , ')'        , 4        
INS  , ')'        ,              , 99           , NEWLINE    , 4        
INS  , '('        ,              , ')'          , '('        , 4        
DEL  , '['        , '['          , NUMBER       ,            , 4        
DEL  , NAME       , '}'          , NEWLINE      ,            , 4        
DEL  , NAME       , '...'        , ')'          ,            , 4        
INS  , 'class'    ,              , '('          , NAME       , 4        
DEL  , NAME       , ':'          , '%'          ,            , 4        
DEL  , '['        , 98           , '{'          ,            , 4        
INS  , ')'        ,              , NAME         , '('        , 4        
DEL  , '-'        , NUMBER       , NAME         ,            , 4        
SUB  , NEWLINE    , NAME         , EOS          , 99         , 4        
DEL  , '['        , '**'         , STRING       ,            , 4        
SUB  , NAME       , NAME         , '('          , '='        , 4        
DEL  , NEWLINE    , 98           , '@'          ,            , 4        
SUB  , 98         , NAME         , STRING       , 'raise'    , 4        
DEL  , '{'        , '**'         , STRING       ,            , 4        
DEL  , '='        , UNKNOWN_CHAR , STRING       ,            , 4        
DEL  , NAME       , '.'          , ')'          ,            , 4        
DEL  , 99         , '.'          , EOS          ,            , 4        
INS  , '...'      ,              , NAME         , ','        , 4        
DEL  , NEWLINE    , '>'          , STRING       ,            , 4        
INS  , NAME       ,              , EOS          , '}'        , 4        
DEL  , NAME       , '**'         , '='          ,            , 4        
DEL  , BOS        , UNKNOWN_CHAR , STRING       ,            , 4        
SUB  , '('        , UNKNOWN_CHAR , '/'          , STRING     , 4        
DEL  , NEWLINE    , 99           , 'else'       ,            , 4        
DEL  , NAME       , NEWLINE      , '>>'         ,            , 4        
DEL  , '**'       , NEWLINE      , NAME         ,            , 4        
SUB  , ','        , UNKNOWN_CHAR , UNKNOWN_CHAR , STRING     , 4        
SUB  , STRING     , ')'          , NEWLINE      , '}'        , 4        
INS  , NAME       ,              , NUMBER       , ':'        , 4        
DEL  , 99         , '>'          , EOS          ,            , 4        
DEL  , 99         , NAME         , STRING       ,            , 4        
DEL  , ']'        , NAME         , '.'          ,            , 4        
DEL  , ']'        , '.'          , NEWLINE      ,            , 4        
DEL  , '='        , NEWLINE      , '['          ,            , 4        
DEL  , 'True'     , UNKNOWN_CHAR , ')'          ,            , 4        
INS  , '='        ,              , '('          , '['        , 4        
DEL  , BOS        , '**'         , '*'          ,            , 4        
DEL  , STRING     , ')'          , ']'          ,            , 4        
INS  , '='        ,              , ')'          , '('        , 4        
SUB  , STRING     , NAME         , UNKNOWN_CHAR , '='        , 4        
DEL  , NEWLINE    , 98           , NUMBER       ,            , 4        
DEL  , '}'        , NEWLINE      , '}'          ,            , 4        
DEL  , NAME       , '*'          , ','          ,            , 4        
INS  , STRING     ,              , NEWLINE      , ']'        , 4        
SUB  , ')'        , UNKNOWN_CHAR , EOS          , ')'        , 4        
DEL  , 99         , 99           , '@'          ,            , 4        
SUB  , 99         , 'else'       , NAME         , 'elif'     , 4        
DEL  , BOS        , UNKNOWN_CHAR , 'with'       ,            , 4        
DEL  , STRING     , ']'          , NEWLINE      ,            , 4        
INS  , ','        ,              , NEWLINE      , NAME       , 4        
SUB  , STRING     , UNKNOWN_CHAR , ')'          , ','        , 4        
DEL  , ']'        , NAME         , ','          ,            , 4        
INS  , NAME       ,              , ':'          , 'in'       , 4        
DEL  , ':'        , ')'          , NEWLINE      ,            , 4        
INS  , BOS        ,              , '.'          , NAME       , 4        
SUB  , NUMBER     , ':'          , NUMBER       , '-'        , 4        
SUB  , '('        , UNKNOWN_CHAR , '%'          , STRING     , 4        
DEL  , '='        , UNKNOWN_CHAR , '{'          ,            , 4        
INS  , NEWLINE    ,              , 'pass'       , 98         , 4        
DEL  , 'return'   , '('          , NAME         ,            , 4        
INS  , ')'        ,              , 'for'        , NEWLINE    , 4        
SUB  , NAME       , '/'          , NAME         , '.'        , 4        
DEL  , NUMBER     , '='          , NUMBER       ,            , 4        
DEL  , 'in'       , '*'          , NAME         ,            , 4        
SUB  , NEWLINE    , UNKNOWN_CHAR , NEWLINE      , 98         , 4        
SUB  , NAME       , NEWLINE      , 99           , ')'        , 4        
INS  , NEWLINE    ,              , '...'        , 99         , 4        
DEL  , NAME       , STRING       , '>'          ,            , 4        
SUB  , ')'        , ']'          , EOS          , ')'        , 4        
INS  , ':'        ,              , ','          , NAME       , 4        
SUB  , NEWLINE    , 'if'         , NAME         , 98         , 4        
INS  , '['        ,              , NAME         , ']'        , 4        
DEL  , ']'        , 'for'        , NAME         ,            , 4        
DEL  , ']'        , '**'         , ']'          ,            , 4        
DEL  , ']'        , NAME         , '['          ,            , 4        
DEL  , ']'        , NAME         , 'in'         ,            , 4        
DEL  , NAME       , UNKNOWN_CHAR , '['          ,            , 4        
DEL  , NAME       , ')'          , '+'          ,            , 4        
SUB  , STRING     , UNKNOWN_CHAR , NEWLINE      , STRING     , 4        
SUB  , NAME       , NAME         , EOS          , '('        , 4        
INS  , NAME       ,              , STRING       , '['        , 4        
INS  , NEWLINE    ,              , '['          , 99         , 4        
SUB  , NEWLINE    , 'import'     , '.'          , 'from'     , 4        
SUB  , '('        , ','          , ')'          , STRING     , 4        
DEL  , NUMBER     , 'for'        , NAME         ,            , 4        
DEL  , NAME       , 99           , '>>'         ,            , 4        
INS  , ')'        ,              , ';'          , ')'        , 4        
SUB  , NAME       , '='          , '{'          , ':'        , 4        
DEL  , ':'        , 'return'     , STRING       ,            , 4        
SUB  , NAME       , '.'          , NAME         , '['        , 4        
DEL  , ')'        , 99           , '>>'         ,            , 4        
DEL  , ')'        , '='          , '>'          ,            , 4        
INS  , ')'        ,              , NAME         , 'for'      , 4        
SUB  , NAME       , UNKNOWN_CHAR , NAME         , '('        , 4        
SUB  , 99         , 'elif'       , ':'          , 'else'     , 4        
SUB  , NAME       , ','          , NAME         , '.'        , 4        
DEL  , ','        , '('          , NAME         ,            , 4        
DEL  , ')'        , '}'          , ')'          ,            , 4        
SUB  , NEWLINE    , 'else'       , ':'          , 98         , 4        
SUB  , ')'        , ')'          , EOS          , '}'        , 4        
DEL  , ')'        , NAME         , 'for'        ,            , 4        
INS  , 'import'   ,              , '.'          , NAME       , 4        
INS  , STRING     ,              , NAME         , '('        , 4        
DEL  , NAME       , '>'          , '.'          ,            , 4        
DEL  , 98         , NEWLINE      , 98           ,            , 4        
INS  , '('        ,              , '('          , NAME       , 4        
DEL  , NAME       , ','          , 'for'        ,            , 4        
INS  , '('        ,              , '['          , ')'        , 4        
INS  , ')'        ,              , '['          , ':'        , 4        
DEL  , 'from'     , '<'          , NAME         ,            , 4        
DEL  , STRING     , 'is'         , 'in'         ,            , 4        
SUB  , NUMBER     , STRING       , NUMBER       , ','        , 3        
SUB  , '='        , UNKNOWN_CHAR , NEWLINE      , STRING     , 3        
INS  , '='        ,              , NEWLINE      , '{'        , 3        
INS  , '{'        ,              , NEWLINE      , '}'        , 3        
DEL  , STRING     , ':'          , NUMBER       ,            , 3        
SUB  , 99         , '>>'         , '>'          , NUMBER     , 3        
DEL  , NEWLINE    , NEWLINE      , 'import'     ,            , 3        
SUB  , NUMBER     , STRING       , NAME         , ','        , 3        
DEL  , ']'        , NAME         , '>'          ,            , 3        
DEL  , ']'        , '>'          , NEWLINE      ,            , 3        
DEL  , NUMBER     , STRING       , NAME         ,            , 3        
DEL  , '}'        , '}'          , ','          ,            , 3        
DEL  , STRING     , ':'          , '{'          ,            , 3        
DEL  , STRING     , '{'          , STRING       ,            , 3        
DEL  , ':'        , 99           , NAME         ,            , 3        
DEL  , NAME       , 'False'      , EOS          ,            , 3        
DEL  , '}'        , '...'        , ']'          ,            , 3        
SUB  , NEWLINE    , 'import'     , NAME         , 98         , 3        
DEL  , '+'        , NEWLINE      , 98           ,            , 3        
SUB  , NAME       , '['          , NAME         , '('        , 3        
SUB  , ')'        , '('          , NAME         , NEWLINE    , 3        
SUB  , NUMBER     , ']'          , NEWLINE      , ')'        , 3        
DEL  , BOS        , 98           , '**'         ,            , 3        
DEL  , ':'        , '.'          , NEWLINE      ,            , 3        
SUB  , NEWLINE    , NAME         , 'True'       , 'while'    , 3        
DEL  , ']'        , ']'          , '='          ,            , 3        
DEL  , STRING     , NAME         , '{'          ,            , 3        
INS  , STRING     ,              , EOS          , ':'        , 3        
INS  , ','        ,              , '{'          , NEWLINE    , 3        
DEL  , NEWLINE    , '['          , NAME         ,            , 3        
SUB  , '['        , UNKNOWN_CHAR , UNKNOWN_CHAR , STRING     , 3        
INS  , NAME       ,              , '['          , ':'        , 3        
DEL  , '='        , 98           , NAME         ,            , 3        
DEL  , ']'        , '*'          , NEWLINE      ,            , 3        
DEL  , ']'        , ')'          , ':'          ,            , 3        
DEL  , ':'        , ','          , NAME         ,            , 3        
INS  , NAME       ,              , 'from'       , NEWLINE    , 3        
DEL  , 98         , NEWLINE      , 'def'        ,            , 3        
DEL  , NAME       , ':'          , '('          ,            , 3        
DEL  , ')'        , ')'          , '*'          ,            , 3        
DEL  , 'and'      , NEWLINE      , NAME         ,            , 3        
DEL  , ';'        , '//'         , NAME         ,            , 3        
DEL  , ']'        , 99           , '}'          ,            , 3        
SUB  , '='        , '/'          , NAME         , STRING     , 3        
DEL  , STRING     , '/'          , NEWLINE      ,            , 3        
SUB  , '('        , 'class'      , '='          , NAME       , 3        
DEL  , NEWLINE    , 'try'        , ':'          ,            , 3        
DEL  , '['        , '**'         , NUMBER       ,            , 3        
SUB  , NAME       , STRING       , EOS          , '='        , 3        
DEL  , STRING     , '.'          , '/'          ,            , 3        
INS  , ']'        ,              , '['          , ')'        , 3        
DEL  , ':'        , '>'          , NAME         ,            , 3        
DEL  , NAME       , '*'          , EOS          ,            , 3        
SUB  , '('        , '%'          , NAME         , STRING     , 3        
INS  , '('        ,              , ','          , STRING     , 3        
INS  , NUMBER     ,              , ','          , '}'        , 3        
DEL  , 99         , 99           , '**'         ,            , 3        
SUB  , NAME       , STRING       , NEWLINE      , '('        , 3        
SUB  , ','        , UNKNOWN_CHAR , '/'          , STRING     , 3        
INS  , ']'        ,              , ':'          , ')'        , 3        
DEL  , '/'        , NUMBER       , NAME         ,            , 3        
INS  , STRING     ,              , ':'          , ')'        , 3        
INS  , '}'        ,              , NUMBER       , ','        , 3        
DEL  , STRING     , 'from'       , NAME         ,            , 3        
DEL  , NUMBER     , ')'          , EOS          ,            , 3        
DEL  , STRING     , UNKNOWN_CHAR , 'in'         ,            , 3        
INS  , NAME       ,              , NAME         , '+='       , 3        
DEL  , '('        , ','          , NAME         ,            , 3        
SUB  , '}'        , NEWLINE      , 99           , ']'        , 3        
SUB  , NAME       , STRING       , NUMBER       , ':'        , 3        
SUB  , STRING     , NAME         , '+'          , STRING     , 3        
DEL  , 'import'   , '**'         , NAME         ,            , 3        
INS  , 'else'     ,              , 'for'        , NAME       , 3        
SUB  , '='        , UNKNOWN_CHAR , '/'          , STRING     , 3        
SUB  , NAME       , NAME         , NAME         , ')'        , 3        
DEL  , NAME       , '*'          , '='          ,            , 3        
INS  , '='        ,              , ')'          , STRING     , 3        
DEL  , 98         , 'pass'       , NEWLINE      ,            , 3        
SUB  , ']'        , '.'          , '['          , ','        , 3        
DEL  , STRING     , '...'        , ']'          ,            , 3        
INS  , STRING     ,              , EOS          , ','        , 3        
DEL  , NAME       , '*'          , 'from'       ,            , 3        
INS  , '='        ,              , STRING       , '('        , 3        
INS  , 98         ,              , NEWLINE      , '('        , 3        
INS  , BOS        ,              , NAME         , 'for'      , 3        
DEL  , ':'        , NAME         , '>'          ,            , 3        
SUB  , ')'        , NEWLINE      , 99           , '('        , 3        
SUB  , '('        , 99           , EOS          , ')'        , 3        
DEL  , 98         , NAME         , ')'          ,            , 3        
SUB  , NAME       , 'as'         , NAME         , 'for'      , 3        
DEL  , NAME       , 'False'      , NEWLINE      ,            , 3        
SUB  , 98         , 'pass'       , UNKNOWN_CHAR , NAME       , 3        
SUB  , ']'        , ')'          , EOS          , ']'        , 3        
DEL  , NAME       , '**'         , ','          ,            , 3        
INS  , '('        ,              , STRING       , ')'        , 3        
DEL  , '['        , '('          , STRING       ,            , 3        
DEL  , ':'        , '{'          , STRING       ,            , 3        
DEL  , ':'        , STRING       , ':'          ,            , 3        
SUB  , NAME       , '...'        , '.'          , '='        , 3        
SUB  , STRING     , NEWLINE      , 99           , STRING     , 3        
SUB  , NAME       , UNKNOWN_CHAR , NAME         , NEWLINE    , 3        
INS  , ')'        ,              , '&'          , ')'        , 3        
SUB  , ')'        , NEWLINE      , 'return'     , ':'        , 3        
SUB  , NEWLINE    , '>>'         , '>>'         , 98         , 3        
DEL  , 99         , 'for'        , NAME         ,            , 3        
SUB  , '('        , UNKNOWN_CHAR , '~'          , STRING     , 3        
DEL  , STRING     , '~'          , '/'          ,            , 3        
DEL  , '('        , '('          , '['          ,            , 3        
DEL  , NAME       , NAME         , 'return'     ,            , 3        
SUB  , STRING     , NAME         , STRING       , ':'        , 3        
DEL  , NEWLINE    , 98           , '['          ,            , 3        
SUB  , NUMBER     , ')'          , EOS          , '}'        , 3        
INS  , ','        ,              , NAME         , '['        , 3        
DEL  , ')'        , ']'          , '['          ,            , 3        
SUB  , ','        , 'is'         , ','          , STRING     , 3        
DEL  , NUMBER     , '<'          , '/'          ,            , 3        
DEL  , NEWLINE    , '>'          , 'from'       ,            , 3        
DEL  , ']'        , STRING       , EOS          ,            , 3        
SUB  , ']'        , '.'          , EOS          , ']'        , 3        
SUB  , NAME       , UNKNOWN_CHAR , EOS          , '('        , 3        
SUB  , ']'        , '='          , NUMBER       , '=='       , 3        
DEL  , NUMBER     , NAME         , '^'          ,            , 3        
DEL  , NUMBER     , NAME         , '-'          ,            , 3        
SUB  , NAME       , STRING       , NEWLINE      , '.'        , 3        
INS  , ','        ,              , '='          , NAME       , 3        
DEL  , NAME       , ')'          , ')'          ,            , 3        
INS  , ':'        ,              , EOS          , 'return'   , 3        
DEL  , NAME       , NAME         , '@'          ,            , 3        
DEL  , '['        , '['          , '('          ,            , 3        
DEL  , NAME       , UNKNOWN_CHAR , '<'          ,            , 3        
SUB  , '='        , UNKNOWN_CHAR , '%'          , STRING     , 3        
DEL  , NAME       , NAME         , 'class'      ,            , 3        
SUB  , 99         , NAME         , '('          , 'elif'     , 3        
INS  , 99         ,              , 'elif'       , 99         , 3        
INS  , NEWLINE    ,              , 'global'     , 99         , 3        
DEL  , ')'        , '->'         , NAME         ,            , 3        
DEL  , ','        , UNKNOWN_CHAR , NUMBER       ,            , 3        
INS  , NAME       ,              , NAME         , ']'        , 3        
SUB  , NUMBER     , '='          , NUMBER       , '=='       , 3        
DEL  , NAME       , '//'         , NAME         ,            , 3        
DEL  , NEWLINE    , '}'          , NEWLINE      ,            , 3        
INS  , ':'        ,              , NUMBER       , '{'        , 3        
INS  , ','        ,              , STRING       , '['        , 3        
INS  , '['        ,              , ','          , STRING     , 3        
INS  , NAME       ,              , ')'          , '='        , 3        
DEL  , '+'        , NEWLINE      , NAME         ,            , 3        
INS  , NAME       ,              , 'import'     , NEWLINE    , 3        
DEL  , '['        , UNKNOWN_CHAR , NAME         ,            , 3        
DEL  , NEWLINE    , NUMBER       , ']'          ,            , 3        
DEL  , NEWLINE    , ']'          , ':'          ,            , 3        
SUB  , '('        , 'in'         , ')'          , NAME       , 3        
DEL  , STRING     , NAME         , ';'          ,            , 3        
DEL  , NAME       , '...'        , NAME         ,            , 3        
INS  , ':'        ,              , ','          , NUMBER     , 3        
INS  , ','        ,              , '/'          , STRING     , 3        
DEL  , STRING     , '+'          , ')'          ,            , 3        
DEL  , NUMBER     , ']'          , ','          ,            , 3        
DEL  , 'True'     , ']'          , ')'          ,            , 3        
SUB  , NEWLINE    , 98           , 'if'         , 99         , 3        
SUB  , ']'        , NAME         , ']'          , ','        , 3        
DEL  , NEWLINE    , NEWLINE      , NUMBER       ,            , 3        
DEL  , NUMBER     , '*'          , ','          ,            , 3        
DEL  , ')'        , STRING       , '.'          ,            , 3        
SUB  , NAME       , STRING       , EOS          , '('        , 3        
INS  , '['        ,              , ','          , ':'        , 3        
SUB  , NAME       , 98           , STRING       , '='        , 3        
DEL  , ')'        , 'not'        , NAME         ,            , 3        
DEL  , '='        , 98           , '{'          ,            , 3        
DEL  , '-'        , '->'         , NUMBER       ,            , 3        
INS  , NAME       ,              , NEWLINE      , 'in'       , 3        
SUB  , NEWLINE    , 'raise'      , NEWLINE      , NAME       , 3        
DEL  , ':'        , '['          , NAME         ,            , 3        
DEL  , NEWLINE    , 'def'        , NAME         ,            , 3        
DEL  , ')'        , NEWLINE      , ')'          ,            , 3        
DEL  , ':'        , UNKNOWN_CHAR , NUMBER       ,            , 3        
DEL  , '('        , '['          , STRING       ,            , 3        
SUB  , STRING     , ':'          , '['          , ','        , 3        
DEL  , ','        , '('          , STRING       ,            , 3        
SUB  , ')'        , ']'          , EOS          , '}'        , 3        
SUB  , STRING     , NAME         , '+'          , ','        , 3        
DEL  , 99         , NAME         , '='          ,            , 3        
SUB  , NUMBER     , ':'          , STRING       , ','        , 3        
INS  , ':'        ,              , NUMBER       , '('        , 3        
INS  , STRING     ,              , 99           , NEWLINE    , 3        
INS  , ']'        ,              , NEWLINE      , '}'        , 3        
DEL  , '{'        , UNKNOWN_CHAR , NAME         ,            , 3        
INS  , ':'        ,              , NEWLINE      , 'return'   , 3        
DEL  , '='        , '.'          , NAME         ,            , 3        
DEL  , STRING     , ']'          , EOS          ,            , 3        
DEL  , NAME       , UNKNOWN_CHAR , '.'          ,            , 3        
SUB  , '='        , '('          , STRING       , '{'        , 3        
DEL  , STRING     , UNKNOWN_CHAR , NUMBER       ,            , 3        
SUB  , '='        , NEWLINE      , 98           , '('        , 3        
DEL  , '('        , 98           , '['          ,            , 3        
DEL  , BOS        , '>>'         , 'import'     ,            , 3        
DEL  , 99         , '~'          , EOS          ,            , 3        
SUB  , NAME       , ','          , NAME         , ')'        , 3        
DEL  , NEWLINE    , '**'         , '*'          ,            , 3        
DEL  , ','        , '*'          , NAME         ,            , 3        
DEL  , STRING     , NUMBER       , '/'          ,            , 3        
DEL  , BOS        , '%'          , '%'          ,            , 3        
INS  , '.'        ,              , '['          , NAME       , 3        
DEL  , BOS        , NAME         , '='          ,            , 3        
INS  , '('        ,              , NEWLINE      , NAME       , 3        
SUB  , STRING     , '='          , '{'          , ':'        , 3        
DEL  , NEWLINE    , '*'          , EOS          ,            , 3        
SUB  , BOS        , 'import'     , NEWLINE      , NAME       , 3        
DEL  , NUMBER     , NUMBER       , '}'          ,            , 3        
INS  , ']'        ,              , ')'          , '('        , 3        
DEL  , NAME       , '('          , '*'          ,            , 3        
INS  , 'True'     ,              , NAME         , ')'        , 3        
INS  , '['        ,              , NEWLINE      , NUMBER     , 3        
DEL  , STRING     , NEWLINE      , '>>'         ,            , 3        
DEL  , STRING     , '>>'         , '>'          ,            , 3        
DEL  , NAME       , '**'         , '*'          ,            , 3        
INS  , ':'        ,              , 'return'     , NEWLINE    , 3        
DEL  , 99         , UNKNOWN_CHAR , 'def'        ,            , 3        
DEL  , 98         , 'if'         , NAME         ,            , 3        
DEL  , '-'        , '*'          , NAME         ,            , 3        
SUB  , ','        , NAME         , UNKNOWN_CHAR , STRING     , 3        
DEL  , 'in'       , NUMBER       , NAME         ,            , 3        
SUB  , NAME       , '('          , NAME         , ','        , 3        
INS  , '('        ,              , NUMBER       , '('        , 3        
DEL  , NUMBER     , NUMBER       , ';'          ,            , 3        
INS  , 'else'     ,              , 'return'     , ':'        , 3        
DEL  , NAME       , '>'          , ':'          ,            , 3        
INS  , NEWLINE    ,              , 'del'        , 99         , 3        
SUB  , NAME       , NAME         , NAME         , '='        , 3        
DEL  , NAME       , 'is'         , 'not'        ,            , 3        
DEL  , ':'        , '='          , STRING       ,            , 3        
INS  , STRING     ,              , ','          , STRING     , 3        
INS  , NUMBER     ,              , NUMBER       , '|'        , 3        
DEL  , NEWLINE    , NAME         , '.'          ,            , 3        
INS  , 'for'      ,              , ':'          , NAME       , 3        
DEL  , ':'        , STRING       , NAME         ,            , 3        
INS  , '['        ,              , NEWLINE      , STRING     , 3        
DEL  , BOS        , NAME         , EOS          ,            , 3        
SUB  , BOS        , NAME         , 'True'       , 'while'    , 3        
DEL  , BOS        , NUMBER       , ')'          ,            , 3        
DEL  , 98         , NAME         , '.'          ,            , 3        
SUB  , NAME       , 'is'         , NAME         , 'if'       , 3        
DEL  , NEWLINE    , 99           , '@'          ,            , 3        
DEL  , BOS        , ':'          , NAME         ,            , 3        
DEL  , ','        , '{'          , NUMBER       ,            , 3        
INS  , 98         ,              , NAME         , 'for'      , 3        
INS  , ']'        ,              , '['          , ']'        , 3        
DEL  , 99         , NEWLINE      , EOS          ,            , 3        
DEL  , ')'        , ']'          , NEWLINE      ,            , 3        
DEL  , NEWLINE    , UNKNOWN_CHAR , 'import'     ,            , 3        
DEL  , '('        , UNKNOWN_CHAR , STRING       ,            , 3        
DEL  , ','        , '='          , STRING       ,            , 3        
DEL  , NAME       , '+'          , UNKNOWN_CHAR ,            , 3        
INS  , NEWLINE    ,              , 'raise'      , 99         , 3        
DEL  , STRING     , '**'         , ']'          ,            , 3        
SUB  , '('        , UNKNOWN_CHAR , NUMBER       , STRING     , 3        
DEL  , STRING     , '>'          , ':'          ,            , 3        
SUB  , 98         , 'elif'       , NAME         , 'if'       , 3        
DEL  , NAME       , 'is'         , 'in'         ,            , 3        
DEL  , NAME       , NAME         , '|'          ,            , 3        
INS  , 98         ,              , 99           , '('        , 3        
INS  , '*'        ,              , NAME         , '('        , 3        
DEL  , ']'        , NAME         , '('          ,            , 3        
DEL  , '['        , '**'         , '['          ,            , 3        
DEL  , '}'        , '...'        , '...'        ,            , 3        
DEL  , NEWLINE    , 99           , STRING       ,            , 3        
INS  , ','        ,              , NAME         , ']'        , 3        
INS  , '['        ,              , 'for'        , '('        , 3        
INS  , '('        ,              , 'for'        , ')'        , 3        
DEL  , NAME       , ':'          , ')'          ,            , 3        
DEL  , NEWLINE    , '...'        , ':'          ,            , 3        
SUB  , '('        , '...'        , ')'          , NAME       , 3        
SUB  , NAME       , ','          , NAME         , '('        , 3        
INS  , ':'        ,              , '.'          , NAME       , 3        
INS  , ')'        ,              , 'yield'      , NEWLINE    , 3        
SUB  , NEWLINE    , 'in'         , '='          , NAME       , 3        
INS  , '...'      ,              , NAME         , NEWLINE    , 3        
DEL  , NUMBER     , NAME         , '('          ,            , 3        
DEL  , NEWLINE    , 'return'     , NAME         ,            , 3        
INS  , BOS        ,              , NAME         , 'lambda'   , 3        
DEL  , NAME       , 'break'      , NEWLINE      ,            , 3        
DEL  , NAME       , 'import'     , EOS          ,            , 3        
DEL  , '='        , '='          , '['          ,            , 3        
SUB  , ')'        , NEWLINE      , EOS          , ')'        , 3        
DEL  , ')'        , NAME         , '=='         ,            , 3        
DEL  , ']'        , NEWLINE      , '<'          ,            , 3        
DEL  , '%'        , '('          , NAME         ,            , 3        
INS  , NAME       ,              , '+'          , ')'        , 3        
INS  , '...'      ,              , NEWLINE      , '('        , 3        
DEL  , ')'        , '->'         , NUMBER       ,            , 3        
INS  , '&'        ,              , NAME         , '('        , 3        
SUB  , STRING     , NAME         , EOS          , ')'        , 3        
INS  , ')'        ,              , ','          , ']'        , 3        
DEL  , '*'        , '.'          , NAME         ,            , 3        
DEL  , NAME       , ')'          , '['          ,            , 3        
DEL  , 98         , '.'          , NAME         ,            , 3        
SUB  , NAME       , '='          , NAME         , 'in'       , 3        
INS  , 'in'       ,              , ')'          , NAME       , 3        
INS  , ':'        ,              , NEWLINE      , '...'      , 3        
SUB  , BOS        , '>>'         , '>'          , STRING     , 3        
INS  , 'not'      ,              , NAME         , 'in'       , 3        
SUB  , NUMBER     , ')'          , NEWLINE      , ':'        , 3        
DEL  , '+'        , NAME         , STRING       ,            , 3        
INS  , 98         ,              , NAME         , '('        , 3        
DEL  , NAME       , ']'          , 'for'        ,            , 3        
INS  , ']'        ,              , 'return'     , ')'        , 3        
SUB  , 98         , '>'          , STRING       , NEWLINE    , 3        
SUB  , NEWLINE    , STRING       , EOS          , 99         , 3        
SUB  , '!='       , UNKNOWN_CHAR , NAME         , STRING     , 3        
INS  , NUMBER     ,              , ')'          , '('        , 3        
DEL  , NAME       , NAME         , 'if'         ,            , 3        
DEL  , STRING     , '.'          , '%'          ,            , 3        
INS  , 'import'   ,              , EOS          , '*'        , 3        
SUB  , BOS        , UNKNOWN_CHAR , NAME         , 'import'   , 3        
INS  , 'in'       ,              , 'if'         , '('        , 3        
DEL  , ':'        , STRING       , NEWLINE      ,            , 3        
SUB  , BOS        , '>>'         , '>'          , 'import'   , 3        
DEL  , 'import'   , '>'          , NAME         ,            , 3        
DEL  , 'while'    , UNKNOWN_CHAR , NAME         ,            , 3        
DEL  , STRING     , 98           , '+'          ,            , 3        
DEL  , NAME       , 'else'       , STRING       ,            , 3        
INS  , ':'        ,              , '...'        , NEWLINE    , 3        
INS  , ']'        ,              , NAME         , '+'        , 3        
DEL  , '('        , UNKNOWN_CHAR , UNKNOWN_CHAR ,            , 3        
SUB  , ','        , 'pass'       , '='          , NAME       , 3        
INS  , NAME       ,              , NEWLINE      , '['        , 3        
DEL  , NEWLINE    , '.'          , NAME         ,            , 3        
INS  , NAME       ,              , '.'          , 'in'       , 3        
INS  , 'in'       ,              , '.'          , NAME       , 3        
DEL  , ')'        , '//'         , '='          ,            , 3        
SUB  , NAME       , NAME         , NAME         , '('        , 3        
SUB  , ']'        , NEWLINE      , NAME         , ','        , 3        
INS  , 98         ,              , EOS          , '...'      , 3        
DEL  , 99         , NAME         , NEWLINE      ,            , 3        
DEL  , NAME       , NAME         , '>='         ,            , 3        
INS  , '['        ,              , ']'          , NUMBER     , 3        
SUB  , NAME       , '//'         , NAME         , NEWLINE    , 3        
DEL  , 99         , NAME         , '['          ,            , 3        
SUB  , 'def'      , '>'          , '['          , NAME       , 3        
DEL  , 'False'    , '**'         , ')'          ,            , 3        
DEL  , '='        , '('          , STRING       ,            , 3        
INS  , '&'        ,              , '<='         , NAME       , 3        
INS  , NAME       ,              , '.'          , '='        , 3        
SUB  , NAME       , '='          , '['          , '=='       , 3        
DEL  , ')'        , ')'          , '['          ,            , 3        
INS  , '('        ,              , NAME         , ')'        , 3        
DEL  , BOS        , 'if'         , '('          ,            , 3        
SUB  , NAME       , ']'          , NEWLINE      , ')'        , 3        
DEL  , ')'        , NAME         , '['          ,            , 3        
INS  , '('        ,              , NEWLINE      , '('        , 3        
INS  , STRING     ,              , 'for'        , ']'        , 3        
DEL  , ','        , '>>'         , '['          ,            , 3        
SUB  , NAME       , UNKNOWN_CHAR , NUMBER       , '%'        , 3        
SUB  , '('        , NAME         , ':'          , 'lambda'   , 3        
DEL  , BOS        , NAME         , 'def'        ,            , 2        
INS  , NAME       ,              , NEWLINE      , '+'        , 2        
SUB  , ')'        , UNKNOWN_CHAR , NEWLINE      , ':'        , 2        
DEL  , NUMBER     , STRING       , NUMBER       ,            , 2        
DEL  , NUMBER     , NUMBER       , '+'          ,            , 2        
INS  , NAME       ,              , NEWLINE      , ';'        , 2        
SUB  , '='        , 'class'      , '('          , NAME       , 2        
INS  , ')'        ,              , 'else'       , ')'        , 2        
DEL  , NAME       , NUMBER       , UNKNOWN_CHAR ,            , 2        
DEL  , ']'        , '<'          , NAME         ,            , 2        
DEL  , '='        , NUMBER       , NAME         ,            , 2        
SUB  , NEWLINE    , NAME         , STRING       , 99         , 2        
DEL  , NUMBER     , NUMBER       , UNKNOWN_CHAR ,            , 2        
SUB  , NAME       , UNKNOWN_CHAR , NAME         , '='        , 2        
DEL  , NAME       , '->'         , 'False'      ,            , 2        
DEL  , NAME       , NEWLINE      , 'if'         ,            , 2        
INS  , 99         ,              , NAME         , 'if'       , 2        
DEL  , 99         , UNKNOWN_CHAR , UNKNOWN_CHAR ,            , 2        
DEL  , NAME       , '/'          , EOS          ,            , 2        
SUB  , BOS        , 'from'       , '='          , NAME       , 2        
SUB  , STRING     , ']'          , EOS          , ')'        , 2        
DEL  , NUMBER     , NUMBER       , ':'          ,            , 2        
INS  , NEWLINE    ,              , 'from'       , 99         , 2        
DEL  , 99         , 99           , 'for'        ,            , 2        
INS  , '...'      ,              , EOS          , ']'        , 2        
SUB  , '='        , 'None'       , UNKNOWN_CHAR , NAME       , 2        
SUB  , ':'        , ')'          , NEWLINE      , ']'        , 2        
DEL  , '}'        , '*'          , EOS          ,            , 2        
DEL  , NEWLINE    , 98           , 'or'         ,            , 2        
DEL  , NEWLINE    , NEWLINE      , '{'          ,            , 2        
DEL  , 99         , UNKNOWN_CHAR , NAME         ,            , 2        
DEL  , NEWLINE    , '**'         , STRING       ,            , 2        
DEL  , NEWLINE    , 'or'         , NAME         ,            , 2        
INS  , BOS        ,              , NEWLINE      , 'with'     , 2        
DEL  , ','        , '['          , STRING       ,            , 2        
SUB  , STRING     , NAME         , NAME         , STRING     , 2        
INS  , '('        ,              , EOS          , '['        , 2        
DEL  , BOS        , UNKNOWN_CHAR , 'while'      ,            , 2        
INS  , ','        ,              , EOS          , STRING     , 2        
DEL  , 98         , '^'          , '^'          ,            , 2        
SUB  , '('        , '~'          , '/'          , STRING     , 2        
INS  , '.'        ,              , '='          , NAME       , 2        
DEL  , '{'        , '{'          , NAME         ,            , 2        
INS  , NUMBER     ,              , NUMBER       , NEWLINE    , 2        
DEL  , '.'        , '('          , NAME         ,            , 2        
INS  , ':'        ,              , STRING       , '('        , 2        
SUB  , ','        , '['          , STRING       , '{'        , 2        
DEL  , '}'        , ')'          , '}'          ,            , 2        
DEL  , '='        , '['          , '{'          ,            , 2        
INS  , STRING     ,              , NAME         , 'in'       , 2        
DEL  , '='        , NUMBER       , NEWLINE      ,            , 2        
DEL  , '}'        , '=='         , '>'          ,            , 2        
DEL  , '}'        , '>'          , NAME         ,            , 2        
DEL  , '}'        , NAME         , EOS          ,            , 2        
INS  , NAME       ,              , NAME         , '|'        , 2        
SUB  , ','        , NEWLINE      , 99           , ']'        , 2        
SUB  , 98         , NAME         , '('          , 'if'       , 2        
SUB  , NUMBER     , ']'          , NEWLINE      , '}'        , 2        
SUB  , NEWLINE    , 'pass'       , NEWLINE      , 98         , 2        
INS  , '}'        ,              , 'return'     , NEWLINE    , 2        
DEL  , ','        , NAME         , '}'          ,            , 2        
SUB  , STRING     , NUMBER       , UNKNOWN_CHAR , ':'        , 2        
DEL  , NEWLINE    , '>>'         , 'True'       ,            , 2        
INS  , 'import'   ,              , NEWLINE      , '*'        , 2        
INS  , 98         ,              , 'if'         , 'pass'     , 2        
INS  , 'pass'     ,              , 'if'         , NEWLINE    , 2        
DEL  , ']'        , NEWLINE      , '['          ,            , 2        
INS  , STRING     ,              , ':'          , ']'        , 2        
SUB  , STRING     , ']'          , ','          , '}'        , 2        
DEL  , ','        , NEWLINE      , ']'          ,            , 2        
INS  , ')'        ,              , 'def'        , NEWLINE    , 2        
INS  , '...'      ,              , '}'          , ':'        , 2        
DEL  , 98         , NAME         , UNKNOWN_CHAR ,            , 2        
SUB  , NAME       , '-'          , NUMBER       , ','        , 2        
SUB  , NUMBER     , STRING       , NAME         , ':'        , 2        
DEL  , NUMBER     , STRING       , '}'          ,            , 2        
DEL  , '('        , '{'          , NAME         ,            , 2        
INS  , '='        ,              , NUMBER       , '('        , 2        
SUB  , NAME       , '('          , NAME         , NEWLINE    , 2        
SUB  , NEWLINE    , NAME         , ':'          , 'try'      , 2        
SUB  , STRING     , NAME         , NAME         , ']'        , 2        
DEL  , NAME       , NEWLINE      , 'import'     ,            , 2        
DEL  , STRING     , '{'          , UNKNOWN_CHAR ,            , 2        
INS  , NUMBER     ,              , ']'          , '}'        , 2        
INS  , ','        ,              , STRING       , '('        , 2        
DEL  , NAME       , ']'          , '.'          ,            , 2        
DEL  , '='        , '{'          , NAME         ,            , 2        
SUB  , STRING     , '}'          , EOS          , ')'        , 2        
DEL  , NEWLINE    , 'and'        , NEWLINE      ,            , 2        
DEL  , '/'        , NEWLINE      , NAME         ,            , 2        
DEL  , ':'        , ':'          , STRING       ,            , 2        
DEL  , ','        , ','          , '['          ,            , 2        
DEL  , STRING     , '**'         , '*'          ,            , 2        
DEL  , STRING     , '*'          , ']'          ,            , 2        
INS  , '}'        ,              , NEWLINE      , '}'        , 2        
SUB  , STRING     , ','          , ']'          , '}'        , 2        
DEL  , '='        , NAME         , '['          ,            , 2        
DEL  , BOS        , 'def'        , 'class'      ,            , 2        
DEL  , STRING     , STRING       , '}'          ,            , 2        
INS  , 'False'    ,              , 'True'       , ','        , 2        
DEL  , NAME       , '/'          , ','          ,            , 2        
INS  , STRING     ,              , '['          , ')'        , 2        
INS  , NEWLINE    ,              , 'import'     , 99         , 2        
DEL  , NAME       , '('          , '-'          ,            , 2        
DEL  , NUMBER     , ')'          , ')'          ,            , 2        
DEL  , 98         , '**'         , 'if'         ,            , 2        
DEL  , BOS        , UNKNOWN_CHAR , UNKNOWN_CHAR ,            , 2        
SUB  , NEWLINE    , NAME         , '('          , 'with'     , 2        
DEL  , ')'        , 98           , NAME         ,            , 2        
INS  , ','        ,              , '['          , '('        , 2        
DEL  , NAME       , UNKNOWN_CHAR , '|'          ,            , 2        
DEL  , '}'        , ')'          , ','          ,            , 2        
DEL  , STRING     , '['          , '@'          ,            , 2        
SUB  , 'import'   , NUMBER       , NEWLINE      , NAME       , 2        
DEL  , '='        , '>'          , NUMBER       ,            , 2        
DEL  , '='        , '>'          , '-'          ,            , 2        
DEL  , '['        , '/'          , NAME         ,            , 2        
DEL  , NUMBER     , '**'         , EOS          ,            , 2        
DEL  , ')'        , ')'          , '/'          ,            , 2        
INS  , NAME       ,              , NEWLINE      , 'as'       , 2        
DEL  , '='        , 98           , '['          ,            , 2        
SUB  , NAME       , UNKNOWN_CHAR , NAME         , ','        , 2        
SUB  , 98         , NAME         , NUMBER       , 'return'   , 2        
SUB  , STRING     , UNKNOWN_CHAR , NAME         , STRING     , 2        
DEL  , NAME       , NAME         , 'or'         ,            , 2        
DEL  , BOS        , NAME         , 'class'      ,            , 2        
DEL  , STRING     , ','          , STRING       ,            , 2        
DEL  , '-'        , 'with'       , '-'          ,            , 2        
DEL  , BOS        , 98           , NUMBER       ,            , 2        
DEL  , NEWLINE    , NUMBER       , 'from'       ,            , 2        
DEL  , '**'       , NEWLINE      , 98           ,            , 2        
DEL  , '**'       , 98           , NAME         ,            , 2        
DEL  , STRING     , '/'          , EOS          ,            , 2        
SUB  , NAME       , UNKNOWN_CHAR , NAME         , '-'        , 2        
DEL  , ')'        , '...'        , '...'        ,            , 2        
DEL  , ')'        , '...'        , '.'          ,            , 2        
DEL  , 98         , UNKNOWN_CHAR , NAME         ,            , 2        
SUB  , NEWLINE    , NAME         , '='          , 98         , 2        
DEL  , ')'        , NAME         , 'return'     ,            , 2        
DEL  , ')'        , '**'         , '*'          ,            , 2        
INS  , 98         ,              , 99           , 'break'    , 2        
DEL  , NEWLINE    , STRING       , UNKNOWN_CHAR ,            , 2        
DEL  , '='        , NEWLINE      , STRING       ,            , 2        
SUB  , NAME       , NAME         , NUMBER       , '('        , 2        
SUB  , STRING     , ';'          , STRING       , ','        , 2        
DEL  , '->'       , NAME         , ';'          ,            , 2        
DEL  , '->'       , ';'          , 'None'       ,            , 2        
DEL  , '='        , NEWLINE      , '{'          ,            , 2        
DEL  , NEWLINE    , 99           , NUMBER       ,            , 2        
SUB  , '('        , '['          , '...'        , NAME       , 2        
SUB  , '='        , '.'          , '.'          , NUMBER     , 2        
SUB  , NUMBER     , '.'          , ']'          , ','        , 2        
SUB  , ','        , ']'          , ')'          , NAME       , 2        
DEL  , BOS        , '.'          , NAME         ,            , 2        
DEL  , STRING     , '*'          , EOS          ,            , 2        
SUB  , '('        , '.'          , '/'          , STRING     , 2        
DEL  , STRING     , '/'          , ')'          ,            , 2        
DEL  , STRING     , 98           , STRING       ,            , 2        
SUB  , NUMBER     , '='          , STRING       , ':'        , 2        
SUB  , 98         , 'pass'       , '='          , NAME       , 2        
DEL  , STRING     , '-'          , NAME         ,            , 2        
DEL  , NEWLINE    , '>>'         , NUMBER       ,            , 2        
DEL  , NUMBER     , NEWLINE      , '>>'         ,            , 2        
SUB  , '.'        , 'from'       , '='          , NAME       , 2        
SUB  , '.'        , 'from'       , NEWLINE      , NAME       , 2        
INS  , 98         ,              , NAME         , 'class'    , 2        
DEL  , ','        , '('          , NUMBER       ,            , 2        
DEL  , ')'        , '}'          , NEWLINE      ,            , 2        
INS  , 'None'     ,              , NAME         , ','        , 2        
DEL  , NEWLINE    , NEWLINE      , '>'          ,            , 2        
DEL  , 99         , 99           , 'while'      ,            , 2        
SUB  , 98         , 'while'      , NAME         , 'with'     , 2        
DEL  , NEWLINE    , NEWLINE      , '.'          ,            , 2        
DEL  , STRING     , '*'          , '.'          ,            , 2        
SUB  , ')'        , NEWLINE      , 99           , ']'        , 2        
DEL  , NUMBER     , NAME         , '+'          ,            , 2        
INS  , 'try'      ,              , 'except'     , ':'        , 2        
SUB  , NAME       , 'in'         , NAME         , ','        , 2        
DEL  , NUMBER     , '**'         , NEWLINE      ,            , 2        
DEL  , STRING     , '**'         , ')'          ,            , 2        
DEL  , BOS        , '...'        , NEWLINE      ,            , 2        
DEL  , BOS        , NEWLINE      , NAME         ,            , 2        
SUB  , NEWLINE    , 'def'        , '='          , NAME       , 2        
INS  , '=='       ,              , NEWLINE      , NAME       , 2        
SUB  , '='        , UNKNOWN_CHAR , EOS          , STRING     , 2        
SUB  , NEWLINE    , 'if'         , NAME         , 'assert'   , 2        
SUB  , NEWLINE    , 98           , 'except'     , 99         , 2        
DEL  , NUMBER     , ')'          , '*'          ,            , 2        
SUB  , ']'        , ')'          , EOS          , '}'        , 2        
DEL  , '='        , '='          , 'True'       ,            , 2        
SUB  , STRING     , NAME         , '-'          , ','        , 2        
SUB  , '.'        , UNKNOWN_CHAR , NEWLINE      , NAME       , 2        
INS  , STRING     ,              , ':'          , ','        , 2        
SUB  , '+'        , UNKNOWN_CHAR , NEWLINE      , STRING     , 2        
DEL  , ':'        , UNKNOWN_CHAR , '['          ,            , 2        
DEL  , ']'        , UNKNOWN_CHAR , '}'          ,            , 2        
SUB  , NEWLINE    , NAME         , STRING       , 98         , 2        
INS  , 98         ,              , EOS          , STRING     , 2        
DEL  , '+'        , NUMBER       , NAME         ,            , 2        
DEL  , '...'      , NAME         , ']'          ,            , 2        
DEL  , '['        , 98           , '('          ,            , 2        
DEL  , 'not'      , NAME         , 'in'         ,            , 2        
SUB  , NAME       , '='          , NAME         , '+'        , 2        
DEL  , ','        , '}'          , ']'          ,            , 2        
SUB  , ']'        , UNKNOWN_CHAR , EOS          , ')'        , 2        
SUB  , ')'        , ']'          , NEWLINE      , ')'        , 2        
DEL  , NAME       , 'return'     , '['          ,            , 2        
DEL  , NUMBER     , ')'          , '}'          ,            , 2        
INS  , '('        ,              , NEWLINE      , STRING     , 2        
DEL  , NAME       , ']'          , '='          ,            , 2        
INS  , NAME       ,              , EOS          , '['        , 2        
DEL  , NAME       , '**'         , NUMBER       ,            , 2        
DEL  , NAME       , NUMBER       , '**'         ,            , 2        
DEL  , NAME       , '**'         , '.'          ,            , 2        
SUB  , ']'        , '='          , NAME         , '!='       , 2        
DEL  , NAME       , 'not'        , 'in'         ,            , 2        
DEL  , ','        , '.'          , '}'          ,            , 2        
SUB  , 'import'   , '...'        , NEWLINE      , NAME       , 2        
SUB  , ']'        , NAME         , '...'        , ']'        , 2        
INS  , STRING     ,              , EOS          , '('        , 2        
DEL  , '=='       , '='          , NAME         ,            , 2        
INS  , 99         ,              , NEWLINE      , 99         , 2        
SUB  , '('        , STRING       , ')'          , NAME       , 2        
DEL  , '/'        , NAME         , STRING       ,            , 2        
DEL  , ')'        , '-'          , NAME         ,            , 2        
SUB  , NAME       , '='          , NEWLINE      , '=='       , 2        
DEL  , NEWLINE    , '...'        , '...'        ,            , 2        
DEL  , ']'        , '...'        , '['          ,            , 2        
DEL  , ')'        , '&'          , NEWLINE      ,            , 2        
INS  , NAME       ,              , NUMBER       , '<'        , 2        
INS  , ':'        ,              , 'for'        , NEWLINE    , 2        
SUB  , 'return'   , 'True'       , UNKNOWN_CHAR , NAME       , 2        
DEL  , NAME       , ':'          , ']'          ,            , 2        
DEL  , NEWLINE    , UNKNOWN_CHAR , 'for'        ,            , 2        
SUB  , 98         , NAME         , 'True'       , 'while'    , 2        
INS  , ')'        ,              , NEWLINE      , '}'        , 2        
SUB  , NAME       , 'as'         , NAME         , 'in'       , 2        
DEL  , NAME       , '&'          , NEWLINE      ,            , 2        
INS  , NEWLINE    ,              , 99           , 'break'    , 2        
DEL  , 'def'      , '('          , NAME         ,            , 2        
INS  , ','        ,              , '('          , '['        , 2        
INS  , '='        ,              , NAME         , STRING     , 2        
SUB  , '='        , UNKNOWN_CHAR , '...'        , STRING     , 2        
DEL  , NEWLINE    , '>'          , '{'          ,            , 2        
DEL  , ')'        , '//'         , NUMBER       ,            , 2        
DEL  , '+'        , '['          , NAME         ,            , 2        
DEL  , 'return'   , '**'         , NAME         ,            , 2        
DEL  , NAME       , '**'         , '('          ,            , 2        
SUB  , '.'        , UNKNOWN_CHAR , UNKNOWN_CHAR , NAME       , 2        
SUB  , '['        , 'def'        , ','          , STRING     , 2        
DEL  , ';'        , '&'          , NAME         ,            , 2        
INS  , '='        ,              , ')'          , NAME       , 2        
SUB  , '.'        , 'global'     , '['          , NAME       , 2        
INS  , ')'        ,              , 'import'     , ')'        , 2        
SUB  , NEWLINE    , 99           , 99           , 98         , 2        
DEL  , 98         , 99           , NAME         ,            , 2        
DEL  , '%'        , '%'          , NAME         ,            , 2        
DEL  , '%'        , NAME         , STRING       ,            , 2        
DEL  , NUMBER     , ','          , NUMBER       ,            , 2        
DEL  , STRING     , ','          , '['          ,            , 2        
INS  , ')'        ,              , ']'          , '['        , 2        
DEL  , 98         , '%'          , NAME         ,            , 2        
INS  , STRING     ,              , '['          , ']'        , 2        
DEL  , 98         , NAME         , ':'          ,            , 2        
DEL  , 98         , NUMBER       , NEWLINE      ,            , 2        
SUB  , NAME       , '}'          , NEWLINE      , ')'        , 2        
SUB  , NEWLINE    , 99           , 'class'      , 98         , 2        
DEL  , 98         , 'return'     , 'True'       ,            , 2        
DEL  , 98         , 'True'       , NEWLINE      ,            , 2        
SUB  , ','        , 'def'        , ','          , STRING     , 2        
DEL  , BOS        , '*'          , 'def'        ,            , 2        
SUB  , STRING     , UNKNOWN_CHAR , NAME         , ','        , 2        
DEL  , 99         , '>>'         , NAME         ,            , 2        
DEL  , NUMBER     , '...'        , ']'          ,            , 2        
DEL  , ':'        , NUMBER       , NAME         ,            , 2        
SUB  , '+='       , UNKNOWN_CHAR , NAME         , STRING     , 2        
INS  , ':'        ,              , 98           , NEWLINE    , 2        
DEL  , 99         , '**'         , 'if'         ,            , 2        
SUB  , NEWLINE    , '>>'         , NAME         , 98         , 2        
DEL  , ':'        , NEWLINE      , 99           ,            , 2        
DEL  , ']'        , '('          , NAME         ,            , 2        
DEL  , BOS        , '**'         , 'def'        ,            , 2        
SUB  , 'if'       , 'in'         , '=='         , NAME       , 2        
SUB  , NAME       , UNKNOWN_CHAR , ']'          , '['        , 2        
DEL  , NUMBER     , '.'          , EOS          ,            , 2        
SUB  , ','        , UNKNOWN_CHAR , EOS          , STRING     , 2        
SUB  , '+'        , '='          , '+'          , STRING     , 2        
INS  , '='        ,              , NEWLINE      , '('        , 2        
DEL  , ']'        , '>'          , EOS          ,            , 2        
INS  , STRING     ,              , ')'          , '('        , 2        
INS  , '['        ,              , ')'          , NUMBER     , 2        
DEL  , '('        , '.'          , NAME         ,            , 2        
DEL  , NEWLINE    , '>>'         , STRING       ,            , 2        
DEL  , NEWLINE    , 'class'      , NAME         ,            , 2        
DEL  , NAME       , '+'          , NEWLINE      ,            , 2        
DEL  , NEWLINE    , ']'          , NEWLINE      ,            , 2        
DEL  , ','        , '<'          , STRING       ,            , 2        
INS  , NAME       ,              , NAME         , '/'        , 2        
DEL  , NEWLINE    , NAME         , ':'          ,            , 2        
DEL  , NUMBER     , '.'          , ']'          ,            , 2        
DEL  , NUMBER     , '**'         , ','          ,            , 2        
INS  , NAME       ,              , STRING       , 'or'       , 2        
SUB  , NAME       , NAME         , NAME         , ']'        , 2        
INS  , '('        ,              , ']'          , ')'        , 2        
DEL  , BOS        , '{'          , NAME         ,            , 2        
DEL  , NAME       , '.'          , '='          ,            , 2        
SUB  , STRING     , ';'          , NUMBER       , ':'        , 2        
DEL  , ':'        , 'pass'       , NEWLINE      ,            , 2        
SUB  , ':'        , 'def'        , ','          , STRING     , 2        
INS  , 98         ,              , EOS          , 'yield'    , 2        
INS  , 'yield'    ,              , EOS          , NEWLINE    , 2        
DEL  , ']'        , '...'        , ']'          ,            , 2        
DEL  , NAME       , STRING       , ':'          ,            , 2        
SUB  , ')'        , UNKNOWN_CHAR , NAME         , NEWLINE    , 2        
INS  , '{'        ,              , ':'          , STRING     , 2        
DEL  , NAME       , ')'          , 'as'         ,            , 2        
INS  , ','        ,              , NUMBER       , '['        , 2        
INS  , '='        ,              , ','          , STRING     , 2        
DEL  , NEWLINE    , '>'          , EOS          ,            , 2        
INS  , BOS        ,              , NEWLINE      , 'for'      , 2        
INS  , 'for'      ,              , NEWLINE      , NAME       , 2        
DEL  , ','        , NEWLINE      , '}'          ,            , 2        
DEL  , BOS        , 'and'        , NAME         ,            , 2        
DEL  , BOS        , '*'          , 'import'     ,            , 2        
DEL  , ':'        , NEWLINE      , '{'          ,            , 2        
DEL  , NAME       , NAME         , '%'          ,            , 2        
DEL  , 98         , '}'          , NEWLINE      ,            , 2        
DEL  , 'None'     , ')'          , ':'          ,            , 2        
INS  , NAME       ,              , '{'          , ':'        , 2        
INS  , BOS        ,              , NUMBER       , '['        , 2        
DEL  , NAME       , NUMBER       , '.'          ,            , 2        
SUB  , 'if'       , 'not'        , 'in'         , NAME       , 2        
SUB  , ','        , 'from'       , '='          , NAME       , 2        
DEL  , ')'        , '='          , NEWLINE      ,            , 2        
DEL  , NEWLINE    , NEWLINE      , '...'        ,            , 2        
DEL  , NEWLINE    , '**'         , 'if'         ,            , 2        
SUB  , '=='       , 'True'       , NEWLINE      , NAME       , 2        
SUB  , 99         , NAME         , '('          , 'except'   , 2        
DEL  , 99         , '='          , NAME         ,            , 2        
INS  , '}'        ,              , '}'          , ')'        , 2        
SUB  , '('        , STRING       , NAME         , '{'        , 2        
INS  , '('        ,              , NUMBER       , '{'        , 2        
DEL  , NAME       , NAME         , '->'         ,            , 2        
DEL  , 'import'   , '.'          , NAME         ,            , 2        
SUB  , NAME       , '...'        , NEWLINE      , '('        , 2        
INS  , '('        ,              , NEWLINE      , '['        , 2        
DEL  , '='        , '.'          , '['          ,            , 2        
DEL  , '}'        , '<'          , '/'          ,            , 2        
DEL  , '('        , '<'          , STRING       ,            , 2        
SUB  , STRING     , '>'          , ')'          , STRING     , 2        
INS  , 99         ,              , 'with'       , 99         , 2        
INS  , BOS        ,              , NUMBER       , '{'        , 2        
INS  , '{'        ,              , NUMBER       , STRING     , 2        
DEL  , 99         , '@'          , NAME         ,            , 2        
INS  , '['        ,              , '*'          , '['        , 2        
DEL  , NEWLINE    , '>>'         , '{'          ,            , 2        
INS  , BOS        ,              , NEWLINE      , 'if'       , 2        
DEL  , NAME       , STRING       , '|'          ,            , 2        
DEL  , 'not'      , 'in'         , NAME         ,            , 2        
DEL  , ','        , NUMBER       , ','          ,            , 2        
SUB  , '=='       , 'False'      , NEWLINE      , NAME       , 2        
DEL  , NUMBER     , ']'          , NEWLINE      ,            , 2        
DEL  , '-'        , NEWLINE      , NAME         ,            , 2        
SUB  , NEWLINE    , UNKNOWN_CHAR , NAME         , STRING     , 2        
DEL  , STRING     , '*'          , 'from'       ,            , 2        
DEL  , ')'        , 'as'         , NAME         ,            , 2        
DEL  , STRING     , '>'          , ')'          ,            , 2        
DEL  , STRING     , '.'          , ']'          ,            , 2        
SUB  , 'def'      , '&'          , NEWLINE      , NAME       , 2        
DEL  , STRING     , 'in'         , UNKNOWN_CHAR ,            , 2        
INS  , '...'      ,              , EOS          , ':'        , 2        
DEL  , NEWLINE    , 'else'       , NEWLINE      ,            , 2        
DEL  , NAME       , ')'          , '/'          ,            , 2        
SUB  , STRING     , NAME         , NAME         , ')'        , 2        
DEL  , STRING     , '='          , 'is'         ,            , 2        
DEL  , ']'        , '.'          , '}'          ,            , 2        
SUB  , '='        , UNKNOWN_CHAR , UNKNOWN_CHAR , STRING     , 2        
SUB  , NAME       , STRING       , NAME         , ':'        , 2        
DEL  , '['        , UNKNOWN_CHAR , ']'          ,            , 2        
SUB  , ']'        , ','          , EOS          , ']'        , 2        
DEL  , STRING     , 'is'         , NEWLINE      ,            , 2        
INS  , '='        ,              , EOS          , STRING     , 2        
INS  , NEWLINE    ,              , '@'          , 99         , 2        
DEL  , 'and'      , NEWLINE      , 'if'         ,            , 2        
DEL  , ']'        , '...'        , '.'          ,            , 2        
SUB  , NUMBER     , '.'          , '.'          , ','        , 2        
DEL  , ','        , '.'          , EOS          ,            , 2        
DEL  , 'return'   , '//'         , NAME         ,            , 2        
DEL  , 'return'   , NAME         , NEWLINE      ,            , 2        
INS  , NAME       ,              , NAME         , '>'        , 2        
DEL  , BOS        , '='          , 'class'      ,            , 2        
DEL  , STRING     , '.'          , NEWLINE      ,            , 2        
INS  , NEWLINE    ,              , '('          , 'def'      , 2        
DEL  , NAME       , '{'          , NUMBER       ,            , 2        
DEL  , BOS        , 'class'      , NAME         ,            , 2        
SUB  , '}'        , 99           , EOS          , ']'        , 2        
SUB  , '}'        , ','          , EOS          , ']'        , 2        
INS  , ')'        ,              , '('          , ')'        , 2        
SUB  , STRING     , '}'          , EOS          , ']'        , 2        
DEL  , NEWLINE    , '>'          , '-'          ,            , 2        
DEL  , NEWLINE    , ','          , NAME         ,            , 2        
DEL  , ']'        , UNKNOWN_CHAR , NAME         ,            , 2        
DEL  , ','        , '**'         , NUMBER       ,            , 2        
DEL  , 99         , NAME         , '...'        ,            , 2        
DEL  , 99         , '('          , '...'        ,            , 2        
INS  , '('        ,              , STRING       , '['        , 2        
DEL  , '**'       , '*'          , NAME         ,            , 2        
DEL  , ')'        , '.'          , UNKNOWN_CHAR ,            , 2        
INS  , BOS        ,              , '('          , '{'        , 2        
SUB  , ','        , UNKNOWN_CHAR , '-'          , STRING     , 2        
INS  , 99         ,              , 'try'        , 99         , 2        
SUB  , STRING     , STRING       , UNKNOWN_CHAR , ','        , 2        
INS  , 98         ,              , 'def'        , 'pass'     , 2        
INS  , 'pass'     ,              , 'def'        , NEWLINE    , 2        
DEL  , '>>'       , '>>'         , NAME         ,            , 2        
SUB  , 99         , 99           , NAME         , 'except'   , 2        
DEL  , ']'        , ']'          , ':'          ,            , 2        
DEL  , ']'        , UNKNOWN_CHAR , ','          ,            , 2        
INS  , ')'        ,              , '='          , ':'        , 2        
INS  , ':'        ,              , '='          , NAME       , 2        
DEL  , ','        , 'return'     , NAME         ,            , 2        
DEL  , ':'        , NAME         , ':'          ,            , 2        
DEL  , BOS        , '>'          , EOS          ,            , 2        
SUB  , NEWLINE    , 'for'        , NAME         , 'while'    , 2        
SUB  , STRING     , NAME         , '.'          , ')'        , 2        
DEL  , 98         , NEWLINE      , 'for'        ,            , 2        
INS  , ')'        ,              , 'assert'     , NEWLINE    , 2        
DEL  , ']'        , '**'         , ','          ,            , 2        
DEL  , 'is'       , NAME         , NAME         ,            , 2        
DEL  , 'is'       , NAME         , '{'          ,            , 2        
DEL  , NEWLINE    , NEWLINE      , 'def'        ,            , 2        
DEL  , '}'        , '}'          , NEWLINE      ,            , 2        
INS  , NAME       ,              , NEWLINE      , ','        , 2        
DEL  , ':'        , NAME         , '='          ,            , 2        
INS  , NEWLINE    ,              , 'finally'    , 99         , 2        
SUB  , STRING     , ','          , EOS          , ']'        , 2        
SUB  , STRING     , ','          , '['          , ':'        , 2        
DEL  , NAME       , 'else'       , NEWLINE      ,            , 2        
DEL  , ','        , NUMBER       , ':'          ,            , 2        
DEL  , STRING     , 'pass'       , STRING       ,            , 2        
DEL  , '}'        , ')'          , NEWLINE      ,            , 2        
DEL  , STRING     , UNKNOWN_CHAR , '%'          ,            , 2        
DEL  , ']'        , NAME         , '}'          ,            , 2        
SUB  , NAME       , '.'          , NAME         , ':'        , 2        
DEL  , ':'        , NAME         , '('          ,            , 2        
DEL  , 'for'      , '['          , NAME         ,            , 2        
DEL  , '.'        , '**'         , NAME         ,            , 2        
INS  , 98         ,              , 99           , STRING     , 2        
DEL  , NAME       , ')'          , UNKNOWN_CHAR ,            , 2        
INS  , ']'        ,              , NEWLINE      , '['        , 2        
SUB  , NAME       , NAME         , NAME         , NEWLINE    , 2        
DEL  , ']'        , '}'          , NEWLINE      ,            , 2        
DEL  , '=='       , NAME         , '='          ,            , 2        
DEL  , '=='       , '='          , STRING       ,            , 2        
SUB  , NAME       , '='          , NAME         , 'is'       , 2        
INS  , ')'        ,              , '...'        , ')'        , 2        
DEL  , BOS        , ')'          , NAME         ,            , 2        
DEL  , ','        , STRING       , '='          ,            , 2        
DEL  , 98         , UNKNOWN_CHAR , NEWLINE      ,            , 2        
DEL  , NEWLINE    , NUMBER       , NEWLINE      ,            , 2        
SUB  , ','        , 'lambda'     , ')'          , NAME       , 2        
SUB  , ','        , 'lambda'     , ','          , NAME       , 2        
INS  , ':'        ,              , NEWLINE      , NUMBER     , 2        
DEL  , ')'        , '~'          , '~'          ,            , 2        
DEL  , ':'        , ':'          , '{'          ,            , 2        
DEL  , '}'        , UNKNOWN_CHAR , UNKNOWN_CHAR ,            , 2        
INS  , ':'        ,              , 'yield'      , NEWLINE    , 2        
DEL  , ')'        , '}'          , ','          ,            , 2        
SUB  , '{'        , NAME         , ':'          , STRING     , 2        
SUB  , NEWLINE    , '<'          , 'class'      , 98         , 2        
SUB  , 98         , 'class'      , STRING       , NEWLINE    , 2        
SUB  , NEWLINE    , STRING       , '>'          , 99         , 2        
SUB  , '='        , 'def'        , '['          , NAME       , 2        
DEL  , '='        , '<'          , NAME         ,            , 2        
SUB  , '.'        , 'class'      , '.'          , NAME       , 2        
DEL  , '['        , '('          , NAME         ,            , 2        
INS  , '='        ,              , '['          , '('        , 2        
DEL  , '('        , UNKNOWN_CHAR , ')'          ,            , 2        
DEL  , NUMBER     , NAME         , '.'          ,            , 2        
DEL  , NAME       , 'import'     , NAME         ,            , 2        
SUB  , ']'        , ','          , NAME         , ')'        , 2        
SUB  , '('        , '/'          , NAME         , STRING     , 2        
DEL  , '...'      , '.'          , ':'          ,            , 2        
INS  , '}'        ,              , ','          , ')'        , 2        
SUB  , 'import'   , 'class'      , EOS          , NAME       , 2        
INS  , 'if'       ,              , NEWLINE      , NAME       , 2        
DEL  , NAME       , 'class'      , '='          ,            , 2        
INS  , '}'        ,              , '('          , ','        , 2        
DEL  , '='        , NEWLINE      , 'return'     ,            , 2        
DEL  , '='        , 'return'     , NAME         ,            , 2        
DEL  , '|'        , NEWLINE      , NUMBER       ,            , 2        
DEL  , '~'        , '/'          , NAME         ,            , 2        
DEL  , ','        , NAME         , '='          ,            , 2        
INS  , NAME       ,              , ','          , ']'        , 2        
SUB  , ')'        , '='          , STRING       , '=='       , 2        
SUB  , NAME       , '='          , 'None'       , 'is'       , 2        
DEL  , '['        , ']'          , '['          ,            , 2        
DEL  , ']'        , '**'         , EOS          ,            , 2        
SUB  , ']'        , UNKNOWN_CHAR , EOS          , ']'        , 2        
DEL  , '('        , 'lambda'     , NAME         ,            , 2        
DEL  , '('        , NAME         , '*'          ,            , 2        
DEL  , '='        , NEWLINE      , 99           ,            , 2        
INS  , '*'        ,              , NAME         , NUMBER     , 2        
INS  , NUMBER     ,              , NAME         , '}'        , 2        
DEL  , BOS        , '<'          , 'class'      ,            , 2        
DEL  , BOS        , 'class'      , STRING       ,            , 2        
DEL  , NUMBER     , ']'          , '}'          ,            , 2        
SUB  , ':'        , STRING       , '}'          , NUMBER     , 2        
DEL  , ':'        , '['          , '{'          ,            , 2        
INS  , STRING     ,              , '='          , ']'        , 2        
DEL  , '-'        , '-'          , NAME         ,            , 2        
DEL  , NAME       , NUMBER       , '|'          ,            , 2        
DEL  , ','        , STRING       , '}'          ,            , 2        
SUB  , 98         , 'for'        , '='          , NAME       , 2        
DEL  , NEWLINE    , ')'          , NEWLINE      ,            , 2        
DEL  , NUMBER     , ','          , '('          ,            , 2        
DEL  , '&'        , NEWLINE      , 99           ,            , 2        
DEL  , '&'        , 99           , NAME         ,            , 2        
DEL  , '&'        , NEWLINE      , NAME         ,            , 2        
INS  , 'return'   ,              , 99           , NAME       , 2        
DEL  , STRING     , '...'        , ')'          ,            , 2        
SUB  , STRING     , NAME         , UNKNOWN_CHAR , ')'        , 2        
DEL  , NEWLINE    , 99           , 'except'     ,            , 2        
DEL  , '('        , NAME         , ')'          ,            , 2        
SUB  , NEWLINE    , 'def'        , NAME         , 'class'    , 2        
DEL  , '['        , NAME         , STRING       ,            , 2        
INS  , STRING     ,              , ']'          , STRING     , 2        
DEL  , '('        , NAME         , NAME         ,            , 2        
DEL  , ']'        , 99           , ']'          ,            , 2        
SUB  , BOS        , UNKNOWN_CHAR , '/'          , NEWLINE    , 2        
SUB  , NEWLINE    , 'import'     , NAME         , 'from'     , 2        
SUB  , '}'        , ']'          , EOS          , ')'        , 2        
DEL  , ':'        , 'import'     , NAME         ,            , 2        
DEL  , BOS        , 'try'        , ':'          ,            , 2        
DEL  , NAME       , '~'          , '/'          ,            , 2        
INS  , STRING     ,              , ';'          , ')'        , 2        
SUB  , ':'        , UNKNOWN_CHAR , ','          , STRING     , 2        
SUB  , '('        , 'lambda'     , '*'          , NAME       , 2        
SUB  , '['        , UNKNOWN_CHAR , NUMBER       , STRING     , 2        
DEL  , NEWLINE    , NAME         , '='          ,            , 2        
DEL  , NEWLINE    , '='          , STRING       ,            , 2        
SUB  , STRING     , NAME         , ':'          , ','        , 2        
DEL  , '='        , '*'          , NAME         ,            , 2        
SUB  , 'return'   , 'False'      , UNKNOWN_CHAR , NAME       , 2        
DEL  , ']'        , 'if'         , NAME         ,            , 2        
INS  , '('        ,              , '['          , NAME       , 2        
SUB  , STRING     , NAME         , ']'          , ','        , 2        
DEL  , NAME       , 'is'         , NEWLINE      ,            , 2        
SUB  , STRING     , NAME         , ')'          , ','        , 2        
INS  , '*'        ,              , '.'          , NAME       , 2        
SUB  , NUMBER     , ':'          , '-'          , ','        , 2        
DEL  , STRING     , ':'          , NAME         ,            , 2        
DEL  , NEWLINE    , ':'          , NAME         ,            , 2        
INS  , '}'        ,              , '.'          , ')'        , 2        
DEL  , '='        , '>'          , '('          ,            , 2        
INS  , '.'        ,              , '.'          , NAME       , 2        
INS  , ':'        ,              , ')'          , ']'        , 2        
DEL  , NAME       , ')'          , '='          ,            , 2        
DEL  , NAME       , '|'          , NAME         ,            , 2        
SUB  , NAME       , '{'          , NUMBER       , '('        , 2        
DEL  , NAME       , NEWLINE      , '='          ,            , 2        
DEL  , NAME       , '='          , '>'          ,            , 2        
INS  , NAME       ,              , '.'          , ':'        , 2        
INS  , ':'        ,              , 'raise'      , NEWLINE    , 2        
SUB  , NEWLINE    , 'if'         , NAME         , 'return'   , 2        
SUB  , NAME       , '{'          , NAME         , '('        , 2        
INS  , ','        ,              , ')'          , '}'        , 2        
SUB  , NEWLINE    , NAME         , STRING       , 'return'   , 2        
INS  , 98         ,              , NAME         , 'global'   , 2        
SUB  , NAME       , ']'          , NEWLINE      , '}'        , 2        
SUB  , NAME       , NEWLINE      , NAME         , ')'        , 2        
INS  , NUMBER     ,              , NAME         , 'and'      , 2        
SUB  , ','        , 'in'         , '.'          , NAME       , 2        
SUB  , NAME       , STRING       , '+'          , '='        , 2        
SUB  , '='        , '('          , NAME         , '{'        , 2        
SUB  , '('        , UNKNOWN_CHAR , '...'        , STRING     , 2        
SUB  , '.'        , '.'          , '.'          , NAME       , 2        
DEL  , NAME       , '.'          , '/'          ,            , 2        
DEL  , ','        , NAME         , '('          ,            , 2        
SUB  , ','        , 'class'      , ')'          , NAME       , 2        
INS  , NEWLINE    ,              , 'break'      , 98         , 2        
DEL  , BOS        , '@'          , NAME         ,            , 2        
INS  , '('        ,              , '-'          , '('        , 2        
DEL  , ']'        , ')'          , ']'          ,            , 2        
DEL  , ')'        , 'True'       , EOS          ,            , 2        
SUB  , 98         , 'class'      , '='          , NAME       , 2        
DEL  , NAME       , 'or'         , NAME         ,            , 2        
SUB  , 99         , NAME         , STRING       , 'return'   , 2        
SUB  , ':'        , 'raise'      , NEWLINE      , NAME       , 2        
SUB  , NAME       , NEWLINE      , '['          , ')'        , 2        
DEL  , 'None'     , UNKNOWN_CHAR , ')'          ,            , 2        
SUB  , ':'        , 98           , 'break'      , NAME       , 2        
INS  , '='        ,              , 'if'         , '('        , 2        
DEL  , 'break'    , NAME         , NAME         ,            , 2        
DEL  , ']'        , NEWLINE      , '='          ,            , 2        
DEL  , ']'        , '='          , '>'          ,            , 2        
DEL  , NAME       , 98           , NAME         ,            , 2        
DEL  , NAME       , STRING       , '*'          ,            , 2        
SUB  , '+'        , UNKNOWN_CHAR , '.'          , STRING     , 2        
INS  , NAME       ,              , '('          , ':'        , 2        
DEL  , 'while'    , '('          , NAME         ,            , 2        
DEL  , BOS        , 'while'      , NAME         ,            , 2        
INS  , '+='       ,              , NEWLINE      , NUMBER     , 2        
SUB  , NAME       , NEWLINE      , NAME         , '('        , 2        
SUB  , NAME       , ']'          , 'for'        , ')'        , 2        
DEL  , '+'        , '('          , NAME         ,            , 2        
DEL  , '|'        , NEWLINE      , 98           ,            , 2        
INS  , '['        ,              , ':'          , NAME       , 2        
INS  , NAME       ,              , ':'          , '['        , 2        
DEL  , NAME       , NAME         , '+='         ,            , 2        
DEL  , ')'        , ']'          , 'for'        ,            , 2        
DEL  , NAME       , NAME         , 'try'        ,            , 2        
DEL  , NAME       , 'try'        , NAME         ,            , 2        
DEL  , NAME       , NAME         , '!='         ,            , 2        
SUB  , '('        , UNKNOWN_CHAR , '-'          , STRING     , 2        
DEL  , NUMBER     , ']'          , ')'          ,            , 2        
DEL  , ')'        , '//'         , 'or'         ,            , 2        
DEL  , NAME       , 'None'       , EOS          ,            , 2        
DEL  , 99         , 'and'        , NAME         ,            , 2        
INS  , '...'      ,              , NUMBER       , ','        , 2        
INS  , '='        ,              , NAME         , '{'        , 2        
SUB  , 'if'       , 'continue'   , '=='         , NAME       , 2        
INS  , 'else'     ,              , NEWLINE      , NAME       , 2        
DEL  , '-'        , '('          , NAME         ,            , 2        
DEL  , NAME       , '='          , UNKNOWN_CHAR ,            , 2        
SUB  , NAME       , '{'          , STRING       , '['        , 2        
SUB  , STRING     , '}'          , '='          , ']'        , 2        
SUB  , ']'        , ':'          , NEWLINE      , ')'        , 2        
DEL  , 'if'       , 'if'         , NAME         ,            , 2        
SUB  , STRING     , ','          , NAME         , '%'        , 2        
INS  , ')'        ,              , NAME         , 'in'       , 2        
DEL  , 99         , NEWLINE      , NAME         ,            , 2        
SUB  , STRING     , '.'          , ']'          , ','        , 2        
DEL  , NAME       , NUMBER       , 'for'        ,            , 2        
SUB  , 'for'      , 'class'      , 'in'         , NAME       , 2        
INS  , NAME       ,              , NEWLINE      , 'else'     , 2        
DEL  , NAME       , '['          , ':'          ,            , 2        
INS  , '>'        ,              , NAME         , '['        , 2        
SUB  , '='        , NAME         , '('          , '['        , 2        
INS  , 98         ,              , 99           , NUMBER     , 2        
INS  , NUMBER     ,              , 99           , NEWLINE    , 2        
DEL  , NAME       , NEWLINE      , 'else'       ,            , 2        
DEL  , NUMBER     , UNKNOWN_CHAR , '%'          ,            , 2        
SUB  , 'or'       , UNKNOWN_CHAR , NAME         , STRING     , 2        
DEL  , '['        , 'True'       , 'if'         ,            , 2        
DEL  , '['        , 'if'         , NAME         ,            , 2        
INS  , ')'        ,              , NEWLINE      , '.'        , 2        
DEL  , ']'        , NAME         , ')'          ,            , 2        
SUB  , ']'        , ')'          , NEWLINE      , ']'        , 2        
DEL  , STRING     , '}'          , STRING       ,            , 2        
INS  , ')'        ,              , 'yield'      , ')'        , 2        
SUB  , NAME       , ')'          , NAME         , '('        , 2        
SUB  , STRING     , ';'          , '('          , ','        , 2        
INS  , 98         ,              , EOS          , 'break'    , 2        
INS  , 'break'    ,              , EOS          , NEWLINE    , 2        
SUB  , '('        , ','          , NAME         , '+'        , 2        
DEL  , NEWLINE    , '@'          , NAME         ,            , 2        
SUB  , STRING     , NEWLINE      , NAME         , '.'        , 2        
DEL  , ')'        , STRING       , ':'          ,            , 2        
SUB  , '*'        , '('          , NAME         , '['        , 2        
INS  , ')'        ,              , ')'          , 'in'       , 2        
SUB  , 98         , 'in'         , '='          , NAME       , 2        
SUB  , '('        , 'in'         , '.'          , NAME       , 2        
SUB  , NEWLINE    , 'in'         , '.'          , NAME       , 2        
SUB  , BOS        , '>>'         , '>'          , 'from'     , 2        
SUB  , 'from'     , '>'          , 'import'     , NAME       , 2        
DEL  , '('        , NAME         , STRING       ,            , 2        
DEL  , BOS        , '>'          , 'class'      ,            , 2        
DEL  , '='        , '{'          , STRING       ,            , 2        
DEL  , '='        , STRING       , ':'          ,            , 2        
DEL  , '='        , ':'          , NAME         ,            , 2        
INS  , ')'        ,              , 'if'         , NEWLINE    , 2        
INS  , '...'      ,              , NAME         , '+'        , 2        
INS  , '<'        ,              , 'or'         , NAME       , 2        
DEL  , 'import'   , '('          , NAME         ,            , 2        
INS  , 99         ,              , NAME         , 'yield'    , 2        
SUB  , '.'        , 'and'        , ','          , NAME       , 2        
DEL  , ','        , 98           , '['          ,            , 2        
DEL  , NAME       , STRING       , STRING       ,            , 2        
SUB  , NAME       , 'in'         , NAME         , '('        , 2        
DEL  , STRING     , '%'          , UNKNOWN_CHAR ,            , 2        
SUB  , BOS        , 'else'       , ':'          , 'for'      , 2        
INS  , 98         ,              , EOS          , 'continue' , 2        
INS  , 'continue' ,              , EOS          , NEWLINE    , 2        
DEL  , 'if'       , NAME         , 'not'        ,            , 2        
INS  , ')'        ,              , '/'          , ')'        , 2        
INS  , 'return'   ,              , NAME         , '['        , 2        
SUB  , '{'        , UNKNOWN_CHAR , 'in'         , STRING     , 2        
DEL  , STRING     , 'in'         , ':'          ,            , 2        
DEL  , ','        , '...'        , ':'          ,            , 2        
DEL  , ','        , ':'          , STRING       ,            , 2        
SUB  , NUMBER     , '...'        , NAME         , ']'        , 2        
SUB  , STRING     , ']'          , NEWLINE      , ')'        , 2        
SUB  , NEWLINE    , 'as'         , '='          , NAME       , 2        
DEL  , ','        , '**'         , EOS          ,            , 2        
INS  , '['        ,              , NAME         , NUMBER     , 2        
DEL  , '+'        , NAME         , '('          ,            , 2        
SUB  , NAME       , STRING       , NAME         , '('        , 2        
SUB  , ')'        , 'else'       , NUMBER       , 'if'       , 2        
SUB  , BOS        , 'for'        , NAME         , 'from'     , 2        
DEL  , NAME       , '**'         , 'as'         ,            , 2        
DEL  , ')'        , '('          , ')'          ,            , 2        
SUB  , NEWLINE    , 'in'         , '['          , NAME       , 2        
INS  , ']'        ,              , '='          , ']'        , 2        
DEL  , '=='       , '='          , NUMBER       ,            , 2        
INS  , 'if'       ,              , 'is'         , NAME       , 2        
INS  , 'else'     ,              , ')'          , NAME       , 2        
SUB  , ','        , '['          , '...'        , STRING     , 2        
SUB  , STRING     , '...'        , ']'          , ':'        , 2        
SUB  , ':'        , ']'          , '}'          , STRING     , 2        
INS  , 98         ,              , NAME         , 'try'      , 2        
INS  , 'try'      ,              , NAME         , ':'        , 2        
DEL  , NAME       , ':'          , '='          ,            , 2        
SUB  , '.'        , ']'          , '='          , NAME       , 2        
SUB  , ')'        , 'in'         , NAME         , ','        , 2        
DEL  , BOS        , 'import'     , '*'          ,            , 2        
SUB  , '('        , 'lambda'     , '('          , NAME       , 2        
SUB  , NAME       , 'or'         , NAME         , 'else'     , 2        
DEL  , NAME       , 'not'        , 'is'         ,            , 2        
INS  , '.'        ,              , ']'          , NAME       , 2        
DEL  , NUMBER     , '...'        , '}'          ,            , 2        
INS  , '-'        ,              , NEWLINE      , NUMBER     , 2        
DEL  , '{'        , '('          , NAME         ,            , 2        
DEL  , NUMBER     , NAME         , '>'          ,            , 2        
INS  , NAME       ,              , NEWLINE      , '='        , 2        
DEL  , NAME       , '-'          , EOS          ,            , 2        
SUB  , NAME       , ','          , '('          , ':'        , 2        
INS  , ')'        ,              , '{'          , ','        , 2        
DEL  , ')'        , NUMBER       , NAME         ,            , 2        
INS  , ','        ,              , '('          , NAME       , 2        
SUB  , 'True'     , ']'          , EOS          , ')'        , 2        
DEL  , ']'        , UNKNOWN_CHAR , '.'          ,            , 2        
DEL  , STRING     , '/'          , UNKNOWN_CHAR ,            , 2        
INS  , 98         ,              , 'else'       , NAME       , 2        
INS  , NAME       ,              , 'else'       , NEWLINE    , 2        
DEL  , NEWLINE    , '**'         , '**'         ,            , 2        
DEL  , NEWLINE    , '**'         , EOS          ,            , 2        
DEL  , ')'        , NEWLINE      , ']'          ,            , 2        
DEL  , BOS        , ':'          , 'from'       ,            , 2        
DEL  , NAME       , '>'          , ']'          ,            , 2        
DEL  , ']'        , NUMBER       , NEWLINE      ,            , 2        
INS  , BOS        ,              , NAME         , 'return'   , 2        
DEL  , NAME       , 'in'         , 'for'        ,            , 2        
INS  , NUMBER     ,              , ':'          , ')'        , 2        
DEL  , '='        , '>'          , '['          ,            , 2        
DEL  , '^'        , '^'          , NAME         ,            , 2        
DEL  , NAME       , NUMBER       , '<='         ,            , 2        
DEL  , ')'        , NEWLINE      , '+'          ,            , 2        
DEL  , STRING     , '-'          , NUMBER       ,            , 2        
INS  , BOS        ,              , 'if'         , NAME       , 2        
SUB  , ','        , UNKNOWN_CHAR , '.'          , STRING     , 2        
DEL  , ']'        , ':'          , '%'          ,            , 2        
SUB  , '('        , 'in'         , ','          , NAME       , 2        
DEL  , ')'        , 98           , 'for'        ,            , 2        
DEL  , BOS        , '>'          , '('          ,            , 2        
SUB  , NAME       , ':'          , NAME         , 'else'     , 2        
DEL  , '('        , '...'        , NAME         ,            , 2        
INS  , NAME       ,              , 'and'        , ':'        , 2        
INS  , ':'        ,              , 'and'        , NAME       , 2        
INS  , NAME       ,              , 'for'        , 'else'     , 2        
INS  , 'else'     ,              , 'for'        , NUMBER     , 2        
SUB  , NAME       , ':'          , NAME         , '('        , 2        
DEL  , NAME       , ']'          , EOS          ,            , 2        
DEL  , 99         , NEWLINE      , 98           ,            , 2        
SUB  , NAME       , '+'          , NAME         , ')'        , 2        
DEL  , '['        , '<'          , NAME         ,            , 2        
DEL  , ','        , ')'          , EOS          ,            , 2        
INS  , 'and'      ,              , NAME         , '('        , 2        
INS  , NEWLINE    ,              , NUMBER       , 99         , 2        
SUB  , NAME       , '{'          , ':'          , '['        , 2        
DEL  , NAME       , '/'          , '%'          ,            , 2        
DEL  , NEWLINE    , '.'          , '.'          ,            , 2        
SUB  , NAME       , '+='         , NUMBER       , '+'        , 2        
DEL  , BOS        , UNKNOWN_CHAR , '@'          ,            , 2        
INS  , ')'        ,              , NAME         , 'as'       , 2        
INS  , ']'        ,              , '}'          , ')'        , 2        
SUB  , ')'        , ':'          , NEWLINE      , ']'        , 2        
INS  , ':'        ,              , EOS          , 'True'     , 2        
SUB  , ')'        , '}'          , EOS          , ')'        , 2        
SUB  , NAME       , ':'          , ':'          , '.'        , 2        
DEL  , '.'        , ':'          , NAME         ,            , 2        
DEL  , NAME       , 'is'         , '='          ,            , 2        
DEL  , '['        , NAME         , '['          ,            , 2        
INS  , STRING     ,              , NAME         , '='        , 2        
SUB  , '('        , '...'        , NAME         , '['        , 2        
SUB  , NAME       , '...'        , ')'          , ']'        , 2        
INS  , ','        ,              , NAME         , 'lambda'   , 2        
DEL  , NEWLINE    , 'else'       , ':'          ,            , 2        
DEL  , ')'        , '<'          , 'class'      ,            , 2        
DEL  , ')'        , 'class'      , STRING       ,            , 2        
DEL  , ')'        , STRING       , '>'          ,            , 2        
INS  , '...'      ,              , NEWLINE      , ':'        , 2        
DEL  , NEWLINE    , 99           , NEWLINE      ,            , 2        
DEL  , ')'        , NEWLINE      , 'if'         ,            , 2        
SUB  , ')'        , 'break'      , NEWLINE      , ')'        , 2        
DEL  , ','        , ','          , '*'          ,            , 2        
INS  , NAME       ,              , NAME         , '*'        , 2        
SUB  , 'if'       , 'pass'       , '=='         , NAME       , 2        
INS  , 99         ,              , NAME         , 'elif'     , 2        
DEL  , STRING     , ';'          , ')'          ,            , 2        
DEL  , NAME       , ')'          , '}'          ,            , 2        
DEL  , ')'        , ')'          , '!='         ,            , 2        
SUB  , NAME       , '...'        , NEWLINE      , '.'        , 2        
INS  , 99         ,              , EOS          , 'return'   , 2        
INS  , 'return'   ,              , EOS          , NAME       , 2        
INS  , '.'        ,              , ','          , NAME       , 2        
SUB  , NAME       , 'as'         , NAME         , 'import'   , 2        
SUB  , NAME       , ','          , NEWLINE      , ')'        , 2        
SUB  , NAME       , NUMBER       , ')'          , '.'        , 2        
INS  , 'for'      ,              , ','          , NAME       , 2        
INS  , ']'        ,              , ')'          , ','        , 2        
DEL  , NEWLINE    , '['          , NUMBER       ,            , 2        
INS  , ']'        ,              , '.'          , ')'        , 2        
DEL  , ')'        , ')'          , '}'          ,            , 2        
SUB  , NAME       , '('          , NAME         , ']'        , 2        
SUB  , '['        , UNKNOWN_CHAR , ','          , '...'      , 2        
SUB  , '.'        , UNKNOWN_CHAR , ')'          , NAME       , 2        
DEL  , 'True'     , UNKNOWN_CHAR , '}'          ,            , 2        
INS  , '*'        ,              , ')'          , NAME       , 2        
DEL  , ','        , 99           , ')'          ,            , 2        
DEL  , 98         , NUMBER       , NAME         ,            , 2        
SUB  , ')'        , '}'          , NEWLINE      , ')'        , 2        
SUB  , NAME       , '('          , NAME         , '='        , 2        
INS  , ')'        ,              , STRING       , NEWLINE    , 2        
DEL  , '.'        , '['          , NAME         ,            , 2        
DEL  , 98         , '...'        , NEWLINE      ,            , 2        
DEL  , 99         , NEWLINE      , 'else'       ,            , 2        
DEL  , '='        , NAME         , '('          ,            , 2        
INS  , ')'        ,              , NAME         , 'and'      , 2        
SUB  , NAME       , 'in'         , NAME         , 'for'      , 2        
INS  , STRING     ,              , '.'          , ']'        , 2        
SUB  , NAME       , 'is'         , NAME         , 'in'       , 2        
INS  , NEWLINE    ,              , '['          , NAME       , 2        
DEL  , 98         , ':'          , NEWLINE      ,            , 2        
DEL  , NAME       , NAME         , 'else'       ,            , 2        
DEL  , ')'        , 98           , NEWLINE      ,            , 2        
SUB  , ')'        , ','          , NAME         , ':'        , 2        
DEL  , '='        , '('          , '['          ,            , 2        
DEL  , ']'        , ')'          , '.'          ,            , 2        
DEL  , NAME       , '{'          , NAME         ,            , 2        
SUB  , 'if'       , UNKNOWN_CHAR , NAME         , STRING     , 2        
DEL  , BOS        , ':'          , 'def'        ,            , 2        
SUB  , NAME       , NAME         , '('          , '-'        , 2        
DEL  , '&'        , '&'          , '('          ,            , 2        
DEL  , '!='       , '='          , NAME         ,            , 2        
DEL  , '='        , ','          , '['          ,            , 2        
INS  , '+'        ,              , EOS          , STRING     , 2        
SUB  , NUMBER     , ')'          , 'raise'      , ':'        , 2        
DEL  , 99         , 99           , '.'          ,            , 2        
DEL  , '&'        , NEWLINE      , 98           ,            , 2        
DEL  , '&'        , 98           , '('          ,            , 2        
INS  , ','        ,              , NEWLINE      , '}'        , 2        
DEL  , 98         , UNKNOWN_CHAR , UNKNOWN_CHAR ,            , 2        
SUB  , NAME       , ':'          , EOS          , ']'        , 2        
DEL  , ')'        , NEWLINE      , '&'          ,            , 2        
INS  , ':'        ,              , 'if'         , NEWLINE    , 2        
SUB  , ')'        , NEWLINE      , NAME         , ','        , 2        
DEL  , STRING     , ':'          , EOS          ,            , 2        
INS  , STRING     ,              , '+'          , STRING     , 2        
SUB  , NAME       , ':'          , NUMBER       , '='        , 2        
DEL  , ')'        , UNKNOWN_CHAR , ','          ,            , 2        
INS  , '('        ,              , ','          , ')'        , 2        
INS  , ')'        ,              , '<'          , ')'        , 2        
DEL  , 'or'       , NEWLINE      , 98           ,            , 2        
DEL  , 'or'       , 98           , '('          ,            , 2        
DEL  , STRING     , ']'          , '}'          ,            , 2        
INS  , ']'        ,              , NAME         , 'in'       , 2        
INS  , 'in'       ,              , '}'          , NAME       , 2        
DEL  , ')'        , '->'         , '('          ,            , 2        
DEL  , BOS        , 'with'       , NAME         ,            , 2        
SUB  , NAME       , UNKNOWN_CHAR , '='          , '!='       , 2        
SUB  , 98         , ':'          , EOS          , NAME       , 2        
DEL  , NEWLINE    , '//'         , NUMBER       ,            , 2        
DEL  , NAME       , ')'          , 'for'        ,            , 2        
SUB  , ')'        , 'from'       , NAME         , 'in'       , 2        
DEL  , 'from'     , 'class'      , '-'          ,            , 2        
DEL  , 'from'     , '-'          , NAME         ,            , 2        
INS  , '['        ,              , NEWLINE      , NAME       , 2        
DEL  , ']'        , '//'         , NAME         ,            , 2        
DEL  , ']'        , NAME         , STRING       ,            , 2        
SUB  , '('        , UNKNOWN_CHAR , '['          , STRING     , 2        
DEL  , STRING     , '['          , '%'          ,            , 2        
DEL  , NAME       , '.'          , '-'          ,            , 2        
SUB  , NAME       , '='          , 'None'       , '=='       , 2        
DEL  , 98         , 'global'     , NAME         ,            , 2        
INS  , 'if'       ,              , '=='         , NAME       , 2        
INS  , ')'        ,              , NAME         , ';'        , 2        
INS  , ')'        ,              , '!='         , NEWLINE    , 2        
INS  , NEWLINE    ,              , '!='         , 'while'    , 2        
INS  , 'while'    ,              , '!='         , NAME       , 2        
INS  , NUMBER     ,              , NAME         , '.'        , 2        
SUB  , ')'        , '->'         , NAME         , '.'        , 2        
DEL  , NAME       , ']'          , '}'          ,            , 2        
INS  , NAME       ,              , ';'          , ')'        , 2        
DEL  , ']'        , '='          , '-='         ,            , 2        
SUB  , '='        , 'for'        , '('          , NAME       , 2        
SUB  , NAME       , '.'          , STRING       , '['        , 2        
SUB  , STRING     , NEWLINE      , STRING       , '+'        , 2        
DEL  , '}'        , '}'          , ')'          ,            , 1        
INS  , STRING     ,              , '<'          , STRING     , 1        
DEL  , 98         , '>'          , NAME         ,            , 1        
DEL  , NAME       , 98           , NUMBER       ,            , 1        
DEL  , BOS        , '{'          , 'import'     ,            , 1        
SUB  , NAME       , '->'         , NAME         , '('        , 1        
SUB  , NAME       , STRING       , UNKNOWN_CHAR , ')'        , 1        
SUB  , STRING     , '.'          , NAME         , ':'        , 1        
DEL  , STRING     , '='          , NAME         ,            , 1        
SUB  , 98         , '>'          , UNKNOWN_CHAR , NEWLINE    , 1        
SUB  , NEWLINE    , UNKNOWN_CHAR , EOS          , 99         , 1        
DEL  , NUMBER     , NUMBER       , STRING       ,            , 1        
DEL  , ')'        , ')'          , '='          ,            , 1        
SUB  , ','        , NAME         , ':'          , STRING     , 1        
DEL  , ','        , 'in'         , NAME         ,            , 1        
DEL  , NAME       , '['          , '/'          ,            , 1        
SUB  , ','        , STRING       , NAME         , '{'        , 1        
SUB  , '{'        , NAME         , UNKNOWN_CHAR , '}'        , 1        
DEL  , '}'        , UNKNOWN_CHAR , ')'          ,            , 1        
DEL  , 99         , '>'          , NUMBER       ,            , 1        
DEL  , ']'        , UNKNOWN_CHAR , 'and'        ,            , 1        
INS  , '&'        ,              , NEWLINE      , NAME       , 1        
SUB  , STRING     , NAME         , '-'          , ':'        , 1        
INS  , 98         ,              , 99           , 'raise'    , 1        
INS  , 'raise'    ,              , 99           , NEWLINE    , 1        
SUB  , NAME       , '='          , '('          , '=='       , 1        
SUB  , '.'        , 'class'      , '('          , NAME       , 1        
SUB  , ']'        , UNKNOWN_CHAR , '+'          , '('        , 1        
DEL  , NEWLINE    , 'from'       , NAME         ,            , 1        
SUB  , '{'        , '{'          , NAME         , STRING     , 1        
DEL  , NUMBER     , NAME         , STRING       ,            , 1        
SUB  , 'True'     , NEWLINE      , 99           , ':'        , 1        
INS  , '='        ,              , NAME         , NUMBER     , 1        
DEL  , '-'        , '->'         , 'True'       ,            , 1        
DEL  , ')'        , '->'         , 'False'      ,            , 1        
DEL  , ')'        , 'False'      , EOS          ,            , 1        
SUB  , NUMBER     , NAME         , STRING       , ','        , 1        
SUB  , STRING     , NUMBER       , NAME         , STRING     , 1        
SUB  , ','        , UNKNOWN_CHAR , 'None'       , STRING     , 1        
DEL  , STRING     , 'None'       , UNKNOWN_CHAR ,            , 1        
SUB  , 'return'   , 'None'       , UNKNOWN_CHAR , NAME       , 1        
SUB  , ','        , 'def'        , ']'          , STRING     , 1        
SUB  , 99         , 'else'       , ':'          , 'except'   , 1        
DEL  , '{'        , '('          , STRING       ,            , 1        
SUB  , NEWLINE    , STRING       , NAME         , '['        , 1        
SUB  , NAME       , UNKNOWN_CHAR , '+'          , '.'        , 1        
INS  , '.'        ,              , '+'          , NAME       , 1        
INS  , NAME       ,              , '+'          , '('        , 1        
SUB  , ')'        , ')'          , NEWLINE      , ']'        , 1        
DEL  , '-'        , UNKNOWN_CHAR , '{'          ,            , 1        
DEL  , 'True'     , NEWLINE      , '<'          ,            , 1        
DEL  , 'True'     , '<'          , '/'          ,            , 1        
SUB  , 'return'   , '['          , NAME         , '{'        , 1        
INS  , '...'      ,              , NEWLINE      , 'in'       , 1        
DEL  , '%'        , NAME         , '['          ,            , 1        
DEL  , '%'        , '['          , STRING       ,            , 1        
DEL  , STRING     , ']'          , STRING       ,            , 1        
SUB  , ':'        , '{'          , NUMBER       , '['        , 1        
DEL  , NUMBER     , STRING       , NEWLINE      ,            , 1        
DEL  , STRING     , ','          , UNKNOWN_CHAR ,            , 1        
SUB  , '-'        , UNKNOWN_CHAR , UNKNOWN_CHAR , STRING     , 1        
SUB  , ','        , '.'          , NAME         , STRING     , 1        
DEL  , 'return'   , ':'          , NAME         ,            , 1        
SUB  , '='        , '{'          , NAME         , '['        , 1        
DEL  , ','        , '>'          , NAME         ,            , 1        
DEL  , NAME       , '**='        , STRING       ,            , 1        
SUB  , NAME       , '='          , NAME         , '.'        , 1        
SUB  , '}'        , UNKNOWN_CHAR , EOS          , ')'        , 1        
SUB  , STRING     , '...'        , STRING       , ','        , 1        
DEL  , '.'        , 'as'         , '.'          ,            , 1        
SUB  , ':'        , UNKNOWN_CHAR , UNKNOWN_CHAR , NAME       , 1        
SUB  , '.'        , UNKNOWN_CHAR , ','          , NAME       , 1        
SUB  , STRING     , NUMBER       , STRING       , '['        , 1        
DEL  , ']'        , NAME         , 'and'        ,            , 1        
SUB  , ']'        , NAME         , NEWLINE      , ')'        , 1        
SUB  , ')'        , '.'          , '.'          , ':'        , 1        
DEL  , ':'        , '.'          , '.'          ,            , 1        
SUB  , STRING     , ','          , EOS          , ')'        , 1        
SUB  , NAME       , '.'          , NAME         , 'in'       , 1        
DEL  , STRING     , '}'          , UNKNOWN_CHAR ,            , 1        
INS  , '['        ,              , NUMBER       , '{'        , 1        
DEL  , ','        , 'or'         , STRING       ,            , 1        
SUB  , STRING     , UNKNOWN_CHAR , EOS          , ']'        , 1        
DEL  , NAME       , NUMBER       , '['          ,            , 1        
SUB  , NUMBER     , '//'         , 'or'         , NEWLINE    , 1        
SUB  , 99         , UNKNOWN_CHAR , 'def'        , 99         , 1        
INS  , 'with'     ,              , NEWLINE      , '('        , 1        
DEL  , 'else'     , ':'          , STRING       ,            , 1        
SUB  , NAME       , NAME         , '*'          , 'import'   , 1        
DEL  , ','        , UNKNOWN_CHAR , NEWLINE      ,            , 1        
INS  , 'from'     ,              , NEWLINE      , NAME       , 1        
DEL  , ','        , STRING       , ']'          ,            , 1        
DEL  , ','        , ']'          , '}'          ,            , 1        
DEL  , '<='       , '=='         , NAME         ,            , 1        
SUB  , ']'        , NAME         , '['          , ','        , 1        
SUB  , NAME       , NUMBER       , '='          , '.'        , 1        
SUB  , NAME       , ')'          , '.'          , ','        , 1        
DEL  , ','        , '.'          , '('          ,            , 1        
DEL  , ':'        , ']'          , '['          ,            , 1        
DEL  , NEWLINE    , '>'          , 'for'        ,            , 1        
DEL  , 'else'     , '**'         , NAME         ,            , 1        
DEL  , NAME       , '**'         , 'for'        ,            , 1        
SUB  , '}'        , ','          , EOS          , '}'        , 1        
SUB  , ':'        , '('          , STRING       , '{'        , 1        
SUB  , BOS        , 'def'        , NAME         , NEWLINE    , 1        
SUB  , ']'        , ','          , EOS          , '}'        , 1        
SUB  , STRING     , '.'          , NAME         , ','        , 1        
DEL  , '+'        , NEWLINE      , 99           ,            , 1        
DEL  , '+'        , 99           , NAME         ,            , 1        
DEL  , '{'        , NAME         , STRING       ,            , 1        
SUB  , '='        , '<'          , NAME         , STRING     , 1        
SUB  , NEWLINE    , 99           , 'from'       , 98         , 1        
SUB  , NAME       , UNKNOWN_CHAR , NEWLINE      , '.'        , 1        
INS  , '{'        ,              , STRING       , '['        , 1        
DEL  , '}'        , NEWLINE      , ']'          ,            , 1        
INS  , ':'        ,              , '}'          , '...'      , 1        
DEL  , ']'        , ')'          , '=='         ,            , 1        
DEL  , STRING     , '...'        , '.'          ,            , 1        
DEL  , ']'        , 'and'        , NEWLINE      ,            , 1        
INS  , 'False'    ,              , ','          , ']'        , 1        
INS  , ')'        ,              , NAME         , '}'        , 1        
INS  , NEWLINE    ,              , '.'          , 99         , 1        
INS  , 99         ,              , '.'          , NAME       , 1        
SUB  , NEWLINE    , '.'          , '.'          , '...'      , 1        
DEL  , '...'      , '.'          , 'and'        ,            , 1        
DEL  , 99         , 'import'     , NAME         ,            , 1        
DEL  , 99         , NAME         , 'from'       ,            , 1        
DEL  , ')'        , NUMBER       , ':'          ,            , 1        
DEL  , ')'        , ':'          , 'if'         ,            , 1        
SUB  , STRING     , '['          , NAME         , ':'        , 1        
SUB  , ':'        , NAME         , ']'          , STRING     , 1        
DEL  , STRING     , ']'          , '@'          ,            , 1        
SUB  , NEWLINE    , NAME         , '('          , 98         , 1        
SUB  , ')'        , '=='         , 'not'        , 'is'       , 1        
SUB  , '('        , '+'          , ')'          , STRING     , 1        
DEL  , ','        , '{'          , '...'        ,            , 1        
SUB  , ')'        , '/'          , NAME         , ':'        , 1        
SUB  , '='        , NEWLINE      , STRING       , '{'        , 1        
DEL  , '*'        , STRING       , NAME         ,            , 1        
DEL  , '*'        , UNKNOWN_CHAR , NAME         ,            , 1        
DEL  , ')'        , NEWLINE      , 'and'        ,            , 1        
DEL  , '('        , '('          , 'lambda'     ,            , 1        
INS  , ','        ,              , 'return'     , '}'        , 1        
DEL  , ')'        , '<'          , '-'          ,            , 1        
DEL  , ')'        , '-'          , UNKNOWN_CHAR ,            , 1        
DEL  , NEWLINE    , NEWLINE      , 'from'       ,            , 1        
SUB  , NUMBER     , '['          , NAME         , ']'        , 1        
DEL  , STRING     , ';'          , UNKNOWN_CHAR ,            , 1        
DEL  , NUMBER     , '**'         , ']'          ,            , 1        
SUB  , NEWLINE    , '>>'         , '>'          , 99         , 1        
DEL  , '/'        , '>'          , NEWLINE      ,            , 1        
DEL  , '/'        , NEWLINE      , '['          ,            , 1        
INS  , 'or'       ,              , NAME         , '('        , 1        
SUB  , '('        , 'class'      , ')'          , NAME       , 1        
DEL  , BOS        , '*'          , 'if'         ,            , 1        
INS  , 'if'       ,              , EOS          , '('        , 1        
DEL  , STRING     , ':'          , ':'          ,            , 1        
INS  , 'as'       ,              , EOS          , NAME       , 1        
INS  , ']'        ,              , ':'          , ','        , 1        
SUB  , '['        , '['          , STRING       , '{'        , 1        
DEL  , NEWLINE    , ','          , EOS          ,            , 1        
DEL  , 98         , UNKNOWN_CHAR , 'def'        ,            , 1        
INS  , 'from'     ,              , 'import'     , '.'        , 1        
SUB  , 99         , NAME         , ')'          , 'if'       , 1        
INS  , 'if'       ,              , ')'          , '('        , 1        
SUB  , '}'        , ';'          , ']'          , ','        , 1        
SUB  , NEWLINE    , 'from'       , '='          , NAME       , 1        
SUB  , ']'        , NAME         , NAME         , '}'        , 1        
SUB  , '}'        , NAME         , EOS          , ')'        , 1        
SUB  , ']'        , NEWLINE      , NAME         , ':'        , 1        
SUB  , STRING     , '*'          , NEWLINE      , '**'       , 1        
INS  , '**'       ,              , NEWLINE      , NAME       , 1        
SUB  , ')'        , NEWLINE      , 99           , '.'        , 1        
SUB  , '.'        , 99           , EOS          , NAME       , 1        
SUB  , NEWLINE    , ']'          , ')'          , NAME       , 1        
SUB  , ','        , '-'          , ','          , STRING     , 1        
INS  , ']'        ,              , ','          , '('        , 1        
DEL  , BOS        , 'in'         , NAME         ,            , 1        
DEL  , ','        , 'if'         , NEWLINE      ,            , 1        
SUB  , NAME       , ':'          , '//'         , NEWLINE    , 1        
SUB  , NAME       , ':'          , 'False'      , '='        , 1        
DEL  , '}'        , UNKNOWN_CHAR , '}'          ,            , 1        
SUB  , ':'        , 'pass'       , NEWLINE      , NAME       , 1        
DEL  , '}'        , NAME         , ','          ,            , 1        
DEL  , 'in'       , NEWLINE      , 98           ,            , 1        
DEL  , 'in'       , 98           , '['          ,            , 1        
SUB  , ']'        , NEWLINE      , 99           , ']'        , 1        
SUB  , 'is'       , UNKNOWN_CHAR , NAME         , STRING     , 1        
DEL  , 99         , 99           , '*'          ,            , 1        
DEL  , BOS        , NUMBER       , 'while'      ,            , 1        
SUB  , NEWLINE    , NUMBER       , 'while'      , 98         , 1        
INS  , 'False'    ,              , 'False'      , ','        , 1        
INS  , 'True'     ,              , 'False'      , ','        , 1        
DEL  , '/'        , NUMBER       , NUMBER       ,            , 1        
INS  , 'if'       ,              , 'in'         , STRING     , 1        
SUB  , NAME       , '.'          , NAME         , '+='       , 1        
DEL  , '+='       , NAME         , '('          ,            , 1        
DEL  , '+='       , '('          , STRING       ,            , 1        
SUB  , NAME       , ':'          , EOS          , '('        , 1        
DEL  , 'else'     , NUMBER       , 'if'         ,            , 1        
DEL  , 'else'     , 'if'         , NAME         ,            , 1        
INS  , '{'        ,              , STRING       , '('        , 1        
SUB  , ')'        , NAME         , '('          , '+'        , 1        
DEL  , '+'        , '('          , UNKNOWN_CHAR ,            , 1        
DEL  , '+'        , UNKNOWN_CHAR , STRING       ,            , 1        
SUB  , BOS        , UNKNOWN_CHAR , STRING       , '{'        , 1        
SUB  , ','        , UNKNOWN_CHAR , UNKNOWN_CHAR , ']'        , 1        
SUB  , ']'        , UNKNOWN_CHAR , EOS          , '}'        , 1        
DEL  , STRING     , NAME         , 'from'       ,            , 1        
DEL  , NAME       , ','          , '['          ,            , 1        
DEL  , NEWLINE    , '+='         , NAME         ,            , 1        
DEL  , NUMBER     , '('          , NAME         ,            , 1        
DEL  , NAME       , '('          , '('          ,            , 1        
INS  , '}'        ,              , 'assert'     , NEWLINE    , 1        
DEL  , '+'        , '/'          , STRING       ,            , 1        
SUB  , STRING     , '.'          , NAME         , '+'        , 1        
SUB  , NAME       , UNKNOWN_CHAR , ')'          , '+'        , 1        
INS  , '+'        ,              , ')'          , STRING     , 1        
DEL  , 'assert'   , 'True'       , '='          ,            , 1        
DEL  , 'assert'   , '='          , NAME         ,            , 1        
SUB  , NEWLINE    , '@'          , 'def'        , 98         , 1        
DEL  , 'return'   , NAME         , '**'         ,            , 1        
DEL  , 'return'   , '**'         , NEWLINE      ,            , 1        
DEL  , '.'        , '.'          , 'and'        ,            , 1        
DEL  , '.'        , 'and'        , NAME         ,            , 1        
DEL  , 98         , '**'         , 'def'        ,            , 1        
SUB  , ','        , ']'          , EOS          , '}'        , 1        
SUB  , '='        , '{'          , NUMBER       , '['        , 1        
SUB  , BOS        , '['          , NUMBER       , '{'        , 1        
SUB  , ']'        , 99           , EOS          , '}'        , 1        
DEL  , '...'      , UNKNOWN_CHAR , EOS          ,            , 1        
DEL  , ','        , NAME         , NUMBER       ,            , 1        
SUB  , '['        , 98           , STRING       , '{'        , 1        
DEL  , ','        , 99           , '}'          ,            , 1        
DEL  , NAME       , NEWLINE      , 'for'        ,            , 1        
DEL  , 98         , '**'         , 'import'     ,            , 1        
DEL  , 98         , NAME         , '['          ,            , 1        
INS  , NUMBER     ,              , 'for'        , 'else'     , 1        
SUB  , STRING     , '...'        , NAME         , ','        , 1        
SUB  , ','        , UNKNOWN_CHAR , '//'         , STRING     , 1        
SUB  , '}'        , 99           , EOS          , '}'        , 1        
SUB  , NEWLINE    , 98           , 'else'       , 99         , 1        
DEL  , '.'        , STRING       , NAME         ,            , 1        
SUB  , '['        , 'is'         , ']'          , NUMBER     , 1        
DEL  , 'False'    , UNKNOWN_CHAR , ','          ,            , 1        
DEL  , ':'        , NAME         , '/'          ,            , 1        
DEL  , ':'        , '/'          , '>'          ,            , 1        
DEL  , ')'        , '>'          , NAME         ,            , 1        
SUB  , BOS        , NEWLINE      , 98           , 'def'      , 1        
DEL  , 'def'      , 98           , NAME         ,            , 1        
SUB  , STRING     , NUMBER       , NUMBER       , ','        , 1        
SUB  , ','        , NUMBER       , '.'          , STRING     , 1        
DEL  , STRING     , '.'          , UNKNOWN_CHAR ,            , 1        
SUB  , NUMBER     , 'None'       , NUMBER       , ','        , 1        
INS  , ','        ,              , NUMBER       , NAME       , 1        
SUB  , ','        , UNKNOWN_CHAR , ']'          , '['        , 1        
SUB  , '='        , 'in'         , '.'          , NAME       , 1        
INS  , '...'      ,              , ')'          , '='        , 1        
DEL  , ']'        , '...'        , '}'          ,            , 1        
SUB  , '}'        , '...'        , ']'          , ','        , 1        
INS  , 'with'     ,              , NEWLINE      , NAME       , 1        
INS  , NAME       ,              , NAME         , ';'        , 1        
INS  , BOS        ,              , STRING       , NAME       , 1        
DEL  , ','        , 99           , EOS          ,            , 1        
DEL  , NAME       , 'if'         , 'not'        ,            , 1        
SUB  , NAME       , NAME         , STRING       , '='        , 1        
SUB  , NAME       , NAME         , STRING       , '['        , 1        
SUB  , '('        , NUMBER       , ','          , NAME       , 1        
INS  , ','        ,              , STRING       , NAME       , 1        
DEL  , 'def'      , ':'          , NAME         ,            , 1        
SUB  , NEWLINE    , NAME         , '...'        , 98         , 1        
INS  , ','        ,              , ']'          , ')'        , 1        
INS  , ']'        ,              , '('          , ')'        , 1        
DEL  , NAME       , NUMBER       , '*'          ,            , 1        
DEL  , 99         , 99           , 'and'        ,            , 1        
DEL  , 99         , 'and'        , 'continue'   ,            , 1        
DEL  , 99         , 'continue'   , NAME         ,            , 1        
DEL  , BOS        , '='          , '['          ,            , 1        
SUB  , ','        , ';'          , ','          , STRING     , 1        
SUB  , ','        , ';'          , ']'          , STRING     , 1        
DEL  , ')'        , 98           , '+'          ,            , 1        
SUB  , STRING     , NAME         , '...'        , ','        , 1        
SUB  , '...'      , STRING       , NAME         , ','        , 1        
SUB  , '...'      , UNKNOWN_CHAR , ']'          , ','        , 1        
DEL  , ')'        , '<='         , '!='         ,            , 1        
DEL  , '!='       , '>'          , '('          ,            , 1        
DEL  , STRING     , STRING       , ':'          ,            , 1        
SUB  , NAME       , NAME         , '='          , 'in'       , 1        
DEL  , 'in'       , '='          , NUMBER       ,            , 1        
DEL  , ':'        , 98           , 'import'     ,            , 1        
DEL  , BOS        , UNKNOWN_CHAR , 'global'     ,            , 1        
INS  , '...'      ,              , ']'          , ':'        , 1        
INS  , ':'        ,              , ']'          , '{'        , 1        
INS  , '{'        ,              , ']'          , '}'        , 1        
INS  , 'from'     ,              , 'import'     , NAME       , 1        
SUB  , STRING     , ','          , EOS          , '}'        , 1        
DEL  , BOS        , '('          , '['          ,            , 1        
DEL  , ']'        , ')'          , ')'          ,            , 1        
INS  , '='        ,              , NEWLINE      , 'True'     , 1        
DEL  , NEWLINE    , 98           , NEWLINE      ,            , 1        
SUB  , NAME       , UNKNOWN_CHAR , ']'          , ','        , 1        
SUB  , 99         , 'try'        , '='          , NAME       , 1        
SUB  , ','        , 'try'        , ')'          , NAME       , 1        
SUB  , '('        , 'try'        , ')'          , NAME       , 1        
INS  , '+'        ,              , ']'          , NAME       , 1        
DEL  , 98         , '='          , NUMBER       ,            , 1        
SUB  , 'in'       , 'return'     , ':'          , NAME       , 1        
SUB  , NAME       , 98           , '{'          , '['        , 1        
DEL  , ')'        , 'return'     , '['          ,            , 1        
DEL  , NEWLINE    , UNKNOWN_CHAR , '@'          ,            , 1        
DEL  , ')'        , ','          , EOS          ,            , 1        
DEL  , NAME       , NUMBER       , '('          ,            , 1        
INS  , '/'        ,              , ')'          , NAME       , 1        
DEL  , 99         , '*'          , 'def'        ,            , 1        
DEL  , ')'        , '*'          , NEWLINE      ,            , 1        
INS  , 'from'     ,              , EOS          , NAME       , 1        
SUB  , NAME       , '.'          , '*'          , 'import'   , 1        
INS  , NUMBER     ,              , 'for'        , NEWLINE    , 1        
SUB  , '{'        , '...'        , NAME         , '**'       , 1        
SUB  , BOS        , '%'          , NAME         , 'import'   , 1        
DEL  , NAME       , 98           , '['          ,            , 1        
DEL  , NEWLINE    , '<'          , UNKNOWN_CHAR ,            , 1        
DEL  , NEWLINE    , UNKNOWN_CHAR , '-'          ,            , 1        
SUB  , NEWLINE    , 98           , NUMBER       , '('        , 1        
SUB  , NUMBER     , NEWLINE      , 99           , ')'        , 1        
INS  , ')'        ,              , STRING       , '('        , 1        
SUB  , '('        , NUMBER       , EOS          , ')'        , 1        
DEL  , NAME       , NEWLINE      , '<'          ,            , 1        
DEL  , NAME       , '='          , 'in'         ,            , 1        
DEL  , STRING     , ']'          , '-'          ,            , 1        
SUB  , NUMBER     , NAME         , EOS          , ']'        , 1        
SUB  , '}'        , NAME         , ':'          , ','        , 1        
DEL  , STRING     , '.'          , '@'          ,            , 1        
DEL  , STRING     , '@'          , NEWLINE      ,            , 1        
INS  , '...'      ,              , EOS          , '}'        , 1        
DEL  , BOS        , '{'          , STRING       ,            , 1        
DEL  , STRING     , ':'          , '['          ,            , 1        
DEL  , ':'        , ':'          , '...'        ,            , 1        
DEL  , STRING     , ')'          , '}'          ,            , 1        
SUB  , ']'        , 'for'        , NAME         , ';'        , 1        
DEL  , '='        , UNKNOWN_CHAR , '('          ,            , 1        
DEL  , '('        , ','          , NUMBER       ,            , 1        
SUB  , ')'        , '.'          , '('          , ','        , 1        
SUB  , NAME       , STRING       , NAME         , ']'        , 1        
DEL  , ']'        , UNKNOWN_CHAR , ')'          ,            , 1        
DEL  , NEWLINE    , 99           , 'raise'      ,            , 1        
SUB  , 'if'       , 'pass'       , '!='         , NAME       , 1        
SUB  , NUMBER     , ','          , NAME         , ':'        , 1        
DEL  , NAME       , STRING       , NUMBER       ,            , 1        
INS  , BOS        ,              , ')'          , '('        , 1        
SUB  , ','        , NEWLINE      , 99           , ')'        , 1        
SUB  , ']'        , UNKNOWN_CHAR , ':'          , ','        , 1        
DEL  , NAME       , NAME         , 'None'       ,            , 1        
DEL  , NAME       , 'None'       , 'or'         ,            , 1        
DEL  , NAME       , STRING       , '('          ,            , 1        
INS  , '['        ,              , EOS          , STRING     , 1        
DEL  , '>'        , '='          , NUMBER       ,            , 1        
DEL  , 'global'   , '('          , NAME         ,            , 1        
SUB  , NUMBER     , ','          , NUMBER       , ':'        , 1        
SUB  , NEWLINE    , UNKNOWN_CHAR , EOS          , 98         , 1        
DEL  , NAME       , NEWLINE      , '}'          ,            , 1        
SUB  , STRING     , '<'          , '/'          , STRING     , 1        
DEL  , '**'       , '**'         , NAME         ,            , 1        
DEL  , ')'        , '<='         , '=='         ,            , 1        
DEL  , ')'        , '=='         , NAME         ,            , 1        
DEL  , '{'        , '@'          , STRING       ,            , 1        
INS  , ')'        ,              , NEWLINE      , ','        , 1        
SUB  , 99         , 'else'       , ':'          , NAME       , 1        
INS  , NEWLINE    ,              , 'pass'       , 99         , 1        
DEL  , 'return'   , 'return'     , NAME         ,            , 1        
DEL  , 98         , '>>'         , 'return'     ,            , 1        
DEL  , 98         , '>>'         , NAME         ,            , 1        
SUB  , STRING     , NAME         , '-'          , STRING     , 1        
SUB  , STRING     , '-'          , NAME         , ':'        , 1        
SUB  , ':'        , NAME         , UNKNOWN_CHAR , STRING     , 1        
DEL  , NAME       , ':'          , '**'         ,            , 1        
DEL  , STRING     , STRING       , ']'          ,            , 1        
SUB  , STRING     , NUMBER       , NAME         , ','        , 1        
SUB  , STRING     , '='          , NAME         , STRING     , 1        
SUB  , NEWLINE    , 'while'      , NAME         , 'for'      , 1        
SUB  , '('        , UNKNOWN_CHAR , '*'          , STRING     , 1        
DEL  , STRING     , NAME         , 'and'        ,            , 1        
DEL  , NEWLINE    , 98           , '**'         ,            , 1        
DEL  , NUMBER     , '+'          , NUMBER       ,            , 1        
DEL  , NUMBER     , NAME         , '}'          ,            , 1        
INS  , NEWLINE    ,              , 'except'     , 'try'      , 1        
INS  , ':'        ,              , 'except'     , NAME       , 1        
INS  , NAME       ,              , 'except'     , NEWLINE    , 1        
SUB  , '...'      , '.'          , NEWLINE      , '='        , 1        
DEL  , 'class'    , '('          , NAME         ,            , 1        
DEL  , '=='       , '**'         , NUMBER       ,            , 1        
DEL  , NAME       , 'return'     , STRING       ,            , 1        
DEL  , BOS        , 98           , '...'        ,            , 1        
DEL  , ')'        , NAME         , 'is'         ,            , 1        
SUB  , '+'        , 'def'        , NEWLINE      , NAME       , 1        
DEL  , ','        , '.'          , STRING       ,            , 1        
SUB  , NEWLINE    , 99           , 'with'       , 98         , 1        
DEL  , BOS        , STRING       , 'not'        ,            , 1        
DEL  , 'in'       , '<'          , NAME         ,            , 1        
DEL  , BOS        , '**'         , NEWLINE      ,            , 1        
SUB  , NEWLINE    , 'class'      , '.'          , NAME       , 1        
SUB  , '.'        , 'class'      , 'import'     , NAME       , 1        
SUB  , NAME       , STRING       , NAME         , '='        , 1        
SUB  , '='        , NAME         , UNKNOWN_CHAR , '{'        , 1        
DEL  , '{'        , UNKNOWN_CHAR , '}'          ,            , 1        
SUB  , NUMBER     , NUMBER       , ':'          , '('        , 1        
DEL  , 99         , UNKNOWN_CHAR , '['          ,            , 1        
DEL  , NAME       , '=='         , '.'          ,            , 1        
SUB  , ']'        , NAME         , UNKNOWN_CHAR , '='        , 1        
SUB  , STRING     , NUMBER       , NAME         , ')'        , 1        
DEL  , '...'      , '/'          , ']'          ,            , 1        
DEL  , NUMBER     , NAME         , '=='         ,            , 1        
DEL  , NUMBER     , '=='         , EOS          ,            , 1        
SUB  , ']'        , '='          , NUMBER       , 'is'       , 1        
SUB  , NAME       , UNKNOWN_CHAR , NAME         , ')'        , 1        
DEL  , NAME       , '='          , ','          ,            , 1        
DEL  , NUMBER     , 99           , '<'          ,            , 1        
SUB  , NAME       , '>'          , EOS          , NEWLINE    , 1        
DEL  , NEWLINE    , '{'          , EOS          ,            , 1        
INS  , ','        ,              , '{'          , '['        , 1        
DEL  , '}'        , '['          , ']'          ,            , 1        
SUB  , BOS        , NEWLINE      , 98           , 'class'    , 1        
DEL  , 'class'    , 98           , NAME         ,            , 1        
DEL  , '='        , UNKNOWN_CHAR , ','          ,            , 1        
SUB  , 98         , UNKNOWN_CHAR , UNKNOWN_CHAR , NAME       , 1        
INS  , NUMBER     ,              , STRING       , ':'        , 1        
SUB  , '...'      , '.'          , UNKNOWN_CHAR , '+'        , 1        
INS  , '>'        ,              , ')'          , NAME       , 1        
DEL  , 'return'   , 'return'     , '['          ,            , 1        
DEL  , '...'      , '...'        , NAME         ,            , 1        
SUB  , ')'        , '{'          , '}'          , ':'        , 1        
SUB  , ':'        , '}'          , EOS          , 'pass'     , 1        
SUB  , STRING     , '}'          , NEWLINE      , ']'        , 1        
DEL  , ')'        , 99           , ']'          ,            , 1        
DEL  , 'not'      , NAME         , NAME         ,            , 1        
SUB  , '='        , '['          , NAME         , STRING     , 1        
SUB  , '='        , STRING       , NAME         , '['        , 1        
SUB  , NAME       , NAME         , ':'          , '='        , 1        
DEL  , '='        , ':'          , '['          ,            , 1        
DEL  , STRING     , NAME         , '>>'         ,            , 1        
DEL  , STRING     , '>>'         , NEWLINE      ,            , 1        
INS  , '}'        ,              , NAME         , '}'        , 1        
SUB  , '*'        , '.'          , NAME         , STRING     , 1        
INS  , BOS        ,              , 'if'         , '('        , 1        
INS  , '('        ,              , 'if'         , STRING     , 1        
DEL  , BOS        , '>>'         , '>>'         ,            , 1        
DEL  , BOS        , '>>'         , 'def'        ,            , 1        
SUB  , '}'        , '}'          , EOS          , ']'        , 1        
DEL  , NAME       , ']'          , ','          ,            , 1        
SUB  , '...'      , '...'        , NEWLINE      , '('        , 1        
DEL  , 'return'   , '='          , NUMBER       ,            , 1        
DEL  , ']'        , '.'          , NAME         ,            , 1        
DEL  , NAME       , '-'          , UNKNOWN_CHAR ,            , 1        
SUB  , ','        , NEWLINE      , EOS          , '}'        , 1        
DEL  , NEWLINE    , 99           , '>'          ,            , 1        
DEL  , NEWLINE    , '>'          , ']'          ,            , 1        
DEL  , NEWLINE    , ']'          , EOS          ,            , 1        
SUB  , ')'        , '...'        , NUMBER       , ','        , 1        
SUB  , ']'        , '...'        , NUMBER       , ','        , 1        
SUB  , ')'        , ','          , NUMBER       , '<'        , 1        
DEL  , 'elif'     , ':'          , NAME         ,            , 1        
INS  , '['        ,              , EOS          , '['        , 1        
DEL  , NUMBER     , ':'          , ']'          ,            , 1        
INS  , '['        ,              , NUMBER       , '('        , 1        
DEL  , STRING     , ']'          , NAME         ,            , 1        
DEL  , STRING     , '['          , STRING       ,            , 1        
DEL  , ']'        , NAME         , NUMBER       ,            , 1        
DEL  , ']'        , NUMBER       , EOS          ,            , 1        
DEL  , STRING     , NEWLINE      , 'if'         ,            , 1        
DEL  , STRING     , 'if'         , NAME         ,            , 1        
SUB  , ']'        , UNKNOWN_CHAR , EOS          , NEWLINE    , 1        
SUB  , 'return'   , '('          , STRING       , '{'        , 1        
SUB  , STRING     , NAME         , STRING       , '('        , 1        
INS  , 98         ,              , 99           , '['        , 1        
INS  , '['        ,              , 99           , ']'        , 1        
INS  , ']'        ,              , 99           , NEWLINE    , 1        
DEL  , 'def'      , '>'          , NAME         ,            , 1        
SUB  , ')'        , NEWLINE      , NUMBER       , ':'        , 1        
SUB  , '.'        , 'assert'     , '('          , NAME       , 1        
INS  , NUMBER     ,              , '-'          , ','        , 1        
INS  , ','        ,              , '-'          , NUMBER     , 1        
DEL  , ':'        , '='          , NUMBER       ,            , 1        
DEL  , STRING     , 98           , 'and'        ,            , 1        
INS  , '<'        ,              , 'for'        , NUMBER     , 1        
SUB  , '='        , UNKNOWN_CHAR , 'False'      , STRING     , 1        
DEL  , STRING     , 'False'      , ')'          ,            , 1        
SUB  , STRING     , 'False'      , ')'          , ','        , 1        
DEL  , STRING     , ']'          , UNKNOWN_CHAR ,            , 1        
SUB  , ','        , STRING       , ']'          , '['        , 1        
DEL  , STRING     , '%'          , NAME         ,            , 1        
DEL  , NAME       , 'class'      , 'def'        ,            , 1        
DEL  , NAME       , 'def'        , NEWLINE      ,            , 1        
DEL  , BOS        , '('          , 'import'     ,            , 1        
DEL  , '('        , 'import'     , NAME         ,            , 1        
SUB  , NAME       , 'as'         , NAME         , ','        , 1        
DEL  , ')'        , UNKNOWN_CHAR , '-'          ,            , 1        
INS  , '=='       ,              , NEWLINE      , '('        , 1        
SUB  , STRING     , NAME         , NEWLINE      , STRING     , 1        
DEL  , ')'        , '<'          , NEWLINE      ,            , 1        
DEL  , '|'        , '|'          , '|'          ,            , 1        
SUB  , NAME       , '*'          , NEWLINE      , 'as'       , 1        
DEL  , '*'        , NEWLINE      , 99           ,            , 1        
DEL  , '*'        , 99           , NAME         ,            , 1        
DEL  , '.'        , NEWLINE      , 'global'     ,            , 1        
DEL  , '.'        , 'global'     , NAME         ,            , 1        
INS  , ')'        ,              , '...'        , '('        , 1        
SUB  , ','        , UNKNOWN_CHAR , EOS          , ')'        , 1        
DEL  , NAME       , ','          , ':'          ,            , 1        
DEL  , '/'        , '{'          , NAME         ,            , 1        
DEL  , NAME       , '}'          , '/'          ,            , 1        
INS  , ']'        ,              , NUMBER       , '='        , 1        
SUB  , STRING     , UNKNOWN_CHAR , STRING       , ','        , 1        
INS  , ':'        ,              , ','          , 'None'     , 1        
SUB  , BOS        , 'in'         , NAME         , NEWLINE    , 1        
DEL  , 'from'     , UNKNOWN_CHAR , NAME         ,            , 1        
DEL  , NAME       , UNKNOWN_CHAR , 'import'     ,            , 1        
DEL  , ')'        , 'import'     , NAME         ,            , 1        
INS  , STRING     ,              , 'def'        , NEWLINE    , 1        
SUB  , NAME       , 'is'         , NAME         , 'import'   , 1        
DEL  , '='        , NAME         , NAME         ,            , 1        
DEL  , 'and'      , NAME         , '='          ,            , 1        
DEL  , 'and'      , '='          , '['          ,            , 1        
SUB  , ']'        , 99           , EOS          , ')'        , 1        
SUB  , '}'        , '}'          , NEWLINE      , ','        , 1        
SUB  , '='        , '<'          , NAME         , '{'        , 1        
INS  , '>'        ,              , NEWLINE      , NUMBER     , 1        
DEL  , NAME       , ','          , '.'          ,            , 1        
SUB  , 98         , 'return'     , '.'          , NAME       , 1        
SUB  , ')'        , ')'          , '.'          , NEWLINE    , 1        
SUB  , NEWLINE    , '.'          , ','          , NAME       , 1        
DEL  , ','        , NEWLINE      , '...'        ,            , 1        
DEL  , '...'      , NEWLINE      , ']'          ,            , 1        
INS  , '...'      ,              , EOS          , ')'        , 1        
DEL  , '>'        , ','          , NAME         ,            , 1        
DEL  , NAME       , '%'          , UNKNOWN_CHAR ,            , 1        
DEL  , NAME       , UNKNOWN_CHAR , ';'          ,            , 1        
DEL  , 'class'    , 'class'      , NAME         ,            , 1        
DEL  , NAME       , '->'         , 'True'       ,            , 1        
DEL  , NAME       , 'True'       , NEWLINE      ,            , 1        
DEL  , NEWLINE    , UNKNOWN_CHAR , 'while'      ,            , 1        
SUB  , NUMBER     , STRING       , NAME         , ')'        , 1        
SUB  , BOS        , '%'          , '%'          , 'import'   , 1        
DEL  , 'import'   , '%'          , NAME         ,            , 1        
DEL  , 'is'       , 'not'        , NEWLINE      ,            , 1        
DEL  , 'is'       , NEWLINE      , 98           ,            , 1        
DEL  , 'is'       , 98           , NAME         ,            , 1        
DEL  , STRING     , '{'          , NAME         ,            , 1        
DEL  , BOS        , 'if'         , 'not'        ,            , 1        
DEL  , BOS        , 'not'        , NAME         ,            , 1        
INS  , NAME       ,              , 'or'         , '='        , 1        
INS  , '='        ,              , 'or'         , NAME       , 1        
DEL  , STRING     , '...'        , '/'          ,            , 1        
DEL  , STRING     , ';'          , ','          ,            , 1        
DEL  , '{'        , UNKNOWN_CHAR , ':'          ,            , 1        
DEL  , '{'        , ':'          , '['          ,            , 1        
DEL  , '{'        , '['          , '{'          ,            , 1        
DEL  , ':'        , NEWLINE      , 'return'     ,            , 1        
INS  , NAME       ,              , '=='         , '('        , 1        
INS  , '('        ,              , '=='         , NAME       , 1        
DEL  , ')'        , '.'          , '//'         ,            , 1        
SUB  , STRING     , '...'        , '}'          , ','        , 1        
INS  , ']'        ,              , NAME         , '<'        , 1        
SUB  , NEWLINE    , UNKNOWN_CHAR , UNKNOWN_CHAR , NAME       , 1        
SUB  , NEWLINE    , 'return'     , NEWLINE      , 98         , 1        
INS  , NAME       ,              , ','          , '['        , 1        
INS  , '...'      ,              , '}'          , ']'        , 1        
INS  , NEWLINE    ,              , 'except'     , 98         , 1        
INS  , 98         ,              , 'except'     , NUMBER     , 1        
INS  , NUMBER     ,              , 'except'     , NEWLINE    , 1        
INS  , NAME       ,              , '*'          , 'import'   , 1        
DEL  , NAME       , '/'          , NUMBER       ,            , 1        
DEL  , 98         , '&'          , NAME         ,            , 1        
INS  , 98         ,              , '('          , STRING     , 1        
SUB  , NAME       , 'from'       , NAME         , ','        , 1        
DEL  , '+'        , STRING       , NEWLINE      ,            , 1        
DEL  , ']'        , ']'          , '...'        ,            , 1        
SUB  , NAME       , NAME         , STRING       , ','        , 1        
INS  , '='        ,              , '%'          , STRING     , 1        
DEL  , '%'        , NAME         , '%'          ,            , 1        
SUB  , 99         , NAME         , STRING       , 'elif'     , 1        
DEL  , 'class'    , UNKNOWN_CHAR , NAME         ,            , 1        
DEL  , NEWLINE    , '&'          , NEWLINE      ,            , 1        
SUB  , NUMBER     , NEWLINE      , 98           , ':'        , 1        
SUB  , NAME       , '='          , NUMBER       , '>'        , 1        
SUB  , '('        , UNKNOWN_CHAR , UNKNOWN_CHAR , NAME       , 1        
SUB  , NAME       , UNKNOWN_CHAR , UNKNOWN_CHAR , ','        , 1        
DEL  , 98         , ':'          , NUMBER       ,            , 1        
SUB  , '='        , UNKNOWN_CHAR , UNKNOWN_CHAR , NAME       , 1        
SUB  , NEWLINE    , '/'          , 'while'      , 98         , 1        
SUB  , NEWLINE    , '/'          , NAME         , 98         , 1        
SUB  , 98         , 'return'     , '='          , NAME       , 1        
SUB  , NAME       , NAME         , EOS          , NEWLINE    , 1        
SUB  , '='        , UNKNOWN_CHAR , '-'          , STRING     , 1        
INS  , '['        ,              , '('          , '{'        , 1        
DEL  , NEWLINE    , '>'          , 'import'     ,            , 1        
SUB  , 99         , '<'          , '/'          , NAME       , 1        
INS  , NAME       ,              , NAME         , 'and'      , 1        
DEL  , ')'        , NAME         , ']'          ,            , 1        
SUB  , NAME       , '['          , NUMBER       , NEWLINE    , 1        
DEL  , NEWLINE    , ':'          , 'import'     ,            , 1        
SUB  , NUMBER     , '...'        , ']'          , ','        , 1        
SUB  , ')'        , NUMBER       , '='          , '!='       , 1        
DEL  , '!='       , '='          , STRING       ,            , 1        
DEL  , ']'        , ']'          , '.'          ,            , 1        
SUB  , NUMBER     , NEWLINE      , 'return'     , ':'        , 1        
DEL  , STRING     , '('          , NAME         ,            , 1        
SUB  , NAME       , STRING       , EOS          , ')'        , 1        
INS  , NEWLINE    ,              , 98           , 'try'      , 1        
INS  , 'try'      ,              , 98           , ':'        , 1        
INS  , '/'        ,              , NAME         , '('        , 1        
INS  , ')'        ,              , 'async'      , NEWLINE    , 1        
DEL  , BOS        , '>'          , 'if'         ,            , 1        
DEL  , ']'        , NAME         , 'False'      ,            , 1        
DEL  , ']'        , 'False'      , ')'          ,            , 1        
DEL  , 'def'      , '**'         , NAME         ,            , 1        
INS  , NUMBER     ,              , '{'          , '}'        , 1        
SUB  , 'lambda'   , UNKNOWN_CHAR , UNKNOWN_CHAR , NAME       , 1        
DEL  , ','        , '**'         , '*'          ,            , 1        
DEL  , ','        , '*'          , STRING       ,            , 1        
DEL  , ']'        , '**'         , '*'          ,            , 1        
DEL  , ']'        , '*'          , '}'          ,            , 1        
DEL  , STRING     , '>'          , UNKNOWN_CHAR ,            , 1        
INS  , '{'        ,              , NUMBER       , '('        , 1        
INS  , '('        ,              , NEWLINE      , NUMBER     , 1        
INS  , ':'        ,              , 99           , '('        , 1        
DEL  , 99         , 'while'      , 'True'       ,            , 1        
DEL  , 'True'     , ':'          , NEWLINE      ,            , 1        
INS  , '}'        ,              , '{'          , NEWLINE    , 1        
SUB  , '}'        , '}'          , EOS          , NEWLINE    , 1        
INS  , BOS        ,              , NAME         , 'while'    , 1        
SUB  , ')'        , '['          , NAME         , NEWLINE    , 1        
DEL  , BOS        , 'from'       , NAME         ,            , 1        
SUB  , NUMBER     , ';'          , NEWLINE      , ':'        , 1        
DEL  , ']'        , 'and'        , NAME         ,            , 1        
SUB  , NAME       , '='          , STRING       , ','        , 1        
INS  , 'True'     ,              , '.'          , ')'        , 1        
SUB  , STRING     , NAME         , NUMBER       , STRING     , 1        
SUB  , STRING     , NUMBER       , NUMBER       , ':'        , 1        
SUB  , ':'        , NUMBER       , UNKNOWN_CHAR , STRING     , 1        
DEL  , ')'        , STRING       , '+'          ,            , 1        
INS  , STRING     ,              , ')'          , '['        , 1        
DEL  , STRING     , NUMBER       , ':'          ,            , 1        
SUB  , ')'        , '**'         , NAME         , NEWLINE    , 1        
SUB  , NEWLINE    , 'for'        , NAME         , '...'      , 1        
DEL  , '...'      , NAME         , 'in'         ,            , 1        
DEL  , ')'        , 'and'        , '('          ,            , 1        
DEL  , 99         , '{'          , NAME         ,            , 1        
DEL  , NAME       , ';'          , ')'          ,            , 1        
SUB  , NAME       , UNKNOWN_CHAR , UNKNOWN_CHAR , NEWLINE    , 1        
INS  , '}'        ,              , NEWLINE      , ']'        , 1        
DEL  , ']'        , '<'          , '-'          ,            , 1        
DEL  , ']'        , '-'          , NAME         ,            , 1        
SUB  , '['        , UNKNOWN_CHAR , '/'          , STRING     , 1        
SUB  , NAME       , '.'          , '.'          , ':'        , 1        
SUB  , ':'        , '.'          , EOS          , STRING     , 1        
SUB  , STRING     , UNKNOWN_CHAR , UNKNOWN_CHAR , STRING     , 1        
SUB  , 'if'       , 'pass'       , '>'          , NAME       , 1        
DEL  , NAME       , '+'          , '+'          ,            , 1        
DEL  , '*'        , NEWLINE      , '('          ,            , 1        
SUB  , NEWLINE    , 98           , NAME         , 'class'    , 1        
DEL  , NAME       , 'as'         , ','          ,            , 1        
DEL  , STRING     , '...'        , ':'          ,            , 1        
DEL  , STRING     , ':'          , ','          ,            , 1        
DEL  , NAME       , '@'          , '%'          ,            , 1        
DEL  , NAME       , '%'          , '('          ,            , 1        
DEL  , NEWLINE    , 98           , ']'          ,            , 1        
DEL  , '}'        , '...'        , 'and'        ,            , 1        
DEL  , '}'        , 'and'        , NAME         ,            , 1        
DEL  , '}'        , NAME         , ']'          ,            , 1        
SUB  , '='        , UNKNOWN_CHAR , '+'          , STRING     , 1        
DEL  , 98         , NEWLINE      , 'pass'       ,            , 1        
DEL  , NAME       , STRING       , '=='         ,            , 1        
SUB  , STRING     , NAME         , ':'          , ']'        , 1        
DEL  , ']'        , ':'          , NAME         ,            , 1        
INS  , NUMBER     ,              , ')'          , '}'        , 1        
DEL  , NAME       , ')'          , '('          ,            , 1        
DEL  , ')'        , 99           , NAME         ,            , 1        
DEL  , '<'        , '('          , NAME         ,            , 1        
DEL  , '<'        , UNKNOWN_CHAR , '+'          ,            , 1        
DEL  , NAME       , '/'          , 'in'         ,            , 1        
DEL  , 'import'   , 'from'       , NAME         ,            , 1        
DEL  , NUMBER     , '*'          , ']'          ,            , 1        
DEL  , NUMBER     , '...'        , '.'          ,            , 1        
DEL  , NUMBER     , '.'          , '.'          ,            , 1        
DEL  , '['        , 'def'        , ']'          ,            , 1        
DEL  , NAME       , ','          , 'not'        ,            , 1        
INS  , ','        ,              , NUMBER       , STRING     , 1        
SUB  , ')'        , NEWLINE      , NAME         , '.'        , 1        
SUB  , '...'      , '...'        , '.'          , '('        , 1        
SUB  , '('        , '.'          , NEWLINE      , NAME       , 1        
DEL  , '('        , '>>'         , '>>'         ,            , 1        
DEL  , '('        , '>>'         , NAME         ,            , 1        
SUB  , '['        , '...'        , NAME         , '['        , 1        
SUB  , NEWLINE    , 99           , '@'          , 98         , 1        
SUB  , NUMBER     , ')'          , NEWLINE      , ']'        , 1        
DEL  , '('        , NUMBER       , ')'          ,            , 1        
SUB  , ']'        , '}'          , EOS          , ')'        , 1        
SUB  , NEWLINE    , 'assert'     , '.'          , NAME       , 1        
DEL  , '-'        , ','          , NAME         ,            , 1        
SUB  , STRING     , '.'          , NAME         , ']'        , 1        
SUB  , ']'        , NAME         , UNKNOWN_CHAR , '+'        , 1        
DEL  , NUMBER     , ','          , ':'          ,            , 1        
DEL  , '-'        , UNKNOWN_CHAR , '+'          ,            , 1        
SUB  , ')'        , '...'        , ']'          , '('        , 1        
DEL  , ']'        , '}'          , EOS          ,            , 1        
INS  , '('        ,              , EOS          , NAME       , 1        
SUB  , ','        , '...'        , '.'          , '}'        , 1        
DEL  , ')'        , '-'          , 'not'        ,            , 1        
DEL  , 'return'   , '('          , NUMBER       ,            , 1        
DEL  , NUMBER     , ')'          , UNKNOWN_CHAR ,            , 1        
SUB  , ']'        , '...'        , EOS          , ']'        , 1        
DEL  , BOS        , 98           , STRING       ,            , 1        
DEL  , ')'        , '**'         , 'if'         ,            , 1        
SUB  , NAME       , '...'        , EOS          , ','        , 1        
DEL  , NAME       , '{'          , '}'          ,            , 1        
DEL  , NAME       , '}'          , '.'          ,            , 1        
SUB  , '('        , ')'          , NAME         , NUMBER     , 1        
SUB  , '['        , NAME         , ':'          , '{'        , 1        
SUB  , NUMBER     , ';'          , NAME         , ','        , 1        
SUB  , NEWLINE    , 'pass'       , NEWLINE      , 99         , 1        
DEL  , '}'        , ','          , EOS          ,            , 1        
SUB  , '}'        , NEWLINE      , EOS          , '}'        , 1        
SUB  , 98         , '>>'         , NAME         , NEWLINE    , 1        
SUB  , NAME       , UNKNOWN_CHAR , NEWLINE      , ':'        , 1        
DEL  , '-'        , '->'         , '>'          ,            , 1        
DEL  , '-'        , '>'          , NAME         ,            , 1        
DEL  , ','        , '%'          , STRING       ,            , 1        
DEL  , ','        , '//'         , STRING       ,            , 1        
DEL  , NAME       , NEWLINE      , 'del'        ,            , 1        
DEL  , NAME       , 'del'        , NAME         ,            , 1        
SUB  , BOS        , NAME         , ','          , '['        , 1        
SUB  , ','        , 'in'         , NEWLINE      , ']'        , 1        
SUB  , STRING     , NEWLINE      , 'return'     , '('        , 1        
SUB  , '('        , 'return'     , '-'          , STRING     , 1        
INS  , STRING     ,              , '-'          , ')'        , 1        
SUB  , ','        , '}'          , EOS          , ')'        , 1        
SUB  , 98         , 'for'        , NAME         , 'if'       , 1        
INS  , NAME       ,              , STRING       , '}'        , 1        
DEL  , 'None'     , NEWLINE      , 98           ,            , 1        
DEL  , 'None'     , 98           , 'else'       ,            , 1        
INS  , 98         ,              , '@'          , NUMBER     , 1        
DEL  , ','        , UNKNOWN_CHAR , '('          ,            , 1        
SUB  , NUMBER     , ']'          , ','          , ')'        , 1        
DEL  , '['        , ','          , '['          ,            , 1        
SUB  , STRING     , NAME         , '%'          , STRING     , 1        
SUB  , ','        , UNKNOWN_CHAR , NAME         , '{'        , 1        
SUB  , ']'        , ']'          , '['          , ','        , 1        
SUB  , STRING     , NAME         , STRING       , '['        , 1        
DEL  , NEWLINE    , 'is'         , NAME         ,            , 1        
DEL  , 'None'     , ':'          , NEWLINE      ,            , 1        
SUB  , BOS        , '('          , NAME         , '{'        , 1        
SUB  , STRING     , UNKNOWN_CHAR , EOS          , '+'        , 1        
INS  , '+'        ,              , EOS          , NAME       , 1        
SUB  , NUMBER     , '}'          , EOS          , ']'        , 1        
DEL  , 99         , NAME         , 'for'        ,            , 1        
SUB  , '='        , NUMBER       , NAME         , STRING     , 1        
DEL  , BOS        , '['          , NAME         ,            , 1        
INS  , '='        ,              , EOS          , '...'      , 1        
DEL  , NAME       , '.'          , NUMBER       ,            , 1        
SUB  , 'in'       , UNKNOWN_CHAR , ']'          , STRING     , 1        
DEL  , '='        , NEWLINE      , '-'          ,            , 1        
DEL  , NAME       , '='          , 'True'       ,            , 1        
DEL  , NAME       , 'True'       , ']'          ,            , 1        
SUB  , NAME       , 'from'       , NAME         , 'import'   , 1        
DEL  , NAME       , ','          , EOS          ,            , 1        
INS  , ','        ,              , STRING       , '}'        , 1        
INS  , '}'        ,              , STRING       , ']'        , 1        
INS  , 99         ,              , NAME         , 'finally'  , 1        
INS  , 'finally'  ,              , NAME         , ':'        , 1        
INS  , ':'        ,              , '}'          , 'None'     , 1        
DEL  , ','        , '>'          , '>'          ,            , 1        
SUB  , ','        , NAME         , ','          , STRING     , 1        
SUB  , ','        , 'and'        , ','          , STRING     , 1        
DEL  , ')'        , '('          , 'for'        ,            , 1        
DEL  , NEWLINE    , NEWLINE      , '('          ,            , 1        
DEL  , ']'        , NEWLINE      , NUMBER       ,            , 1        
DEL  , ']'        , NUMBER       , ','          ,            , 1        
SUB  , ','        , 'import'     , ','          , NAME       , 1        
DEL  , NEWLINE    , 99           , 'elif'       ,            , 1        
DEL  , 99         , NAME         , '.'          ,            , 1        
INS  , NAME       ,              , '...'        , ','        , 1        
DEL  , '-'        , '->'         , '('          ,            , 1        
DEL  , NAME       , '->'         , 'is'         ,            , 1        
SUB  , NAME       , STRING       , '%'          , '('        , 1        
INS  , '('        ,              , '%'          , ')'        , 1        
DEL  , NAME       , 'import'     , '*'          ,            , 1        
SUB  , ']'        , '...'        , NEWLINE      , '*'        , 1        
INS  , '*'        ,              , NEWLINE      , '('        , 1        
DEL  , NUMBER     , NAME         , ':'          ,            , 1        
SUB  , NAME       , '{'          , NAME         , ','        , 1        
INS  , '...'      ,              , NAME         , '='        , 1        
SUB  , NAME       , 'for'        , NAME         , '='        , 1        
SUB  , NAME       , NAME         , '...'        , '['        , 1        
SUB  , '['        , '...'        , NEWLINE      , NUMBER     , 1        
DEL  , BOS        , NEWLINE      , 'or'         ,            , 1        
DEL  , BOS        , 'or'         , EOS          ,            , 1        
INS  , 'True'     ,              , EOS          , ']'        , 1        
INS  , ':'        ,              , 99           , STRING     , 1        
SUB  , 99         , 99           , NAME         , 'def'      , 1        
DEL  , NAME       , 'is'         , UNKNOWN_CHAR ,            , 1        
DEL  , NAME       , UNKNOWN_CHAR , '+'          ,            , 1        
INS  , '+'        ,              , NAME         , STRING     , 1        
SUB  , ','        , 'def'        , '='          , NAME       , 1        
INS  , 99         ,              , '{'          , '('        , 1        
INS  , '&'        ,              , 'not'        , '('        , 1        
DEL  , NAME       , '.'          , 'class'      ,            , 1        
DEL  , NAME       , 'class'      , ')'          ,            , 1        
SUB  , NAME       , 'with'       , NAME         , ')'        , 1        
INS  , 'if'       ,              , NEWLINE      , NUMBER     , 1        
DEL  , NAME       , '|'          , '/'          ,            , 1        
DEL  , ']'        , '&'          , '>'          ,            , 1        
DEL  , NEWLINE    , NUMBER       , 'import'     ,            , 1        
SUB  , NUMBER     , '='          , ':'          , '!='       , 1        
INS  , '!='       ,              , ':'          , NUMBER     , 1        
SUB  , STRING     , NAME         , ':'          , STRING     , 1        
SUB  , BOS        , '['          , NAME         , STRING     , 1        
DEL  , BOS        , '>>'         , STRING       ,            , 1        
DEL  , 99         , 'if'         , STRING       ,            , 1        
DEL  , 99         , STRING       , 'not'        ,            , 1        
INS  , '...'      ,              , '...'        , '['        , 1        
SUB  , '...'      , '...'        , '.'          , ']'        , 1        
DEL  , ','        , 'and'        , ','          ,            , 1        
DEL  , ','        , ')'          , '['          ,            , 1        
SUB  , STRING     , ':'          , 'None'       , ','        , 1        
DEL  , NAME       , 99           , '}'          ,            , 1        
SUB  , ')'        , ','          , NAME         , 'and'      , 1        
SUB  , NAME       , ':'          , '//'         , ']'        , 1        
DEL  , '*'        , 'from'       , NAME         ,            , 1        
INS  , '...'      ,              , NAME         , ';'        , 1        
DEL  , '.'        , '/'          , '*'          ,            , 1        
DEL  , '.'        , '*'          , '.'          ,            , 1        
DEL  , NEWLINE    , UNKNOWN_CHAR , 'if'         ,            , 1        
SUB  , '}'        , UNKNOWN_CHAR , NAME         , ','        , 1        
SUB  , ','        , NAME         , ';'          , STRING     , 1        
DEL  , STRING     , ';'          , ':'          ,            , 1        
INS  , 99         ,              , EOS          , NEWLINE    , 1        
DEL  , NUMBER     , NEWLINE      , '}'          ,            , 1        
SUB  , STRING     , UNKNOWN_CHAR , NUMBER       , ','        , 1        
DEL  , STRING     , NUMBER       , EOS          ,            , 1        
DEL  , NEWLINE    , 98           , '{'          ,            , 1        
DEL  , ']'        , ']'          , ']'          ,            , 1        
SUB  , '('        , '('          , NAME         , 'lambda'   , 1        
SUB  , NUMBER     , ')'          , '['          , ','        , 1        
DEL  , STRING     , ']'          , '['          ,            , 1        
DEL  , NEWLINE    , 'with'       , NAME         ,            , 1        
DEL  , 99         , '='          , STRING       ,            , 1        
INS  , ')'        ,              , ':'          , 'in'       , 1        
SUB  , NAME       , 98           , '+'          , '('        , 1        
SUB  , STRING     , NAME         , '.'          , '='        , 1        
SUB  , '='        , '.'          , NAME         , STRING     , 1        
SUB  , ':'        , STRING       , NAME         , '{'        , 1        
SUB  , ','        , UNKNOWN_CHAR , EOS          , ']'        , 1        
DEL  , NAME       , UNKNOWN_CHAR , STRING       ,            , 1        
SUB  , NAME       , STRING       , NUMBER       , '<'        , 1        
SUB  , NAME       , 98           , '{'          , '='        , 1        
SUB  , NEWLINE    , '>>'         , '<'          , 98         , 1        
SUB  , 98         , '<'          , 'class'      , NEWLINE    , 1        
SUB  , NEWLINE    , 'class'      , STRING       , 99         , 1        
DEL  , 99         , STRING       , '>'          ,            , 1        
DEL  , 99         , ','          , '...'        ,            , 1        
DEL  , '}'        , ']'          , ','          ,            , 1        
DEL  , ','        , 98           , '{'          ,            , 1        
DEL  , NAME       , ','          , NEWLINE      ,            , 1        
DEL  , ']'        , 'as'         , NAME         ,            , 1        
DEL  , ']'        , NAME         , 'as'         ,            , 1        
DEL  , ']'        , 'as'         , '['          ,            , 1        
DEL  , NAME       , NUMBER       , '&'          ,            , 1        
DEL  , ']'        , NEWLINE      , ']'          ,            , 1        
SUB  , NEWLINE    , 'return'     , NAME         , 98         , 1        
SUB  , '}'        , UNKNOWN_CHAR , EOS          , ']'        , 1        
DEL  , 99         , '*'          , 'for'        ,            , 1        
DEL  , ')'        , 99           , 99           ,            , 1        
DEL  , NAME       , NEWLINE      , 'from'       ,            , 1        
DEL  , STRING     , '*'          , ','          ,            , 1        
DEL  , ']'        , '.'          , '.'          ,            , 1        
DEL  , ')'        , 'in'         , NAME         ,            , 1        
DEL  , STRING     , ','          , ':'          ,            , 1        
SUB  , ':'        , NAME         , UNKNOWN_CHAR , '['        , 1        
INS  , 98         ,              , NAME         , 'from'     , 1        
SUB  , ')'        , ';'          , EOS          , ')'        , 1        
DEL  , NUMBER     , '%'          , EOS          ,            , 1        
DEL  , '['        , UNKNOWN_CHAR , '['          ,            , 1        
DEL  , 'and'      , 'if'         , NAME         ,            , 1        
DEL  , STRING     , '/'          , NUMBER       ,            , 1        
SUB  , '('        , UNKNOWN_CHAR , ')'          , NUMBER     , 1        
SUB  , ')'        , UNKNOWN_CHAR , EOS          , '.'        , 1        
DEL  , 99         , 99           , '...'        ,            , 1        
INS  , NUMBER     ,              , NAME         , '**'       , 1        
SUB  , '}'        , '.'          , EOS          , ']'        , 1        
SUB  , STRING     , NUMBER       , STRING       , ':'        , 1        
DEL  , '('        , '@'          , STRING       ,            , 1        
DEL  , NAME       , '='          , '{'          ,            , 1        
DEL  , STRING     , STRING       , ','          ,            , 1        
DEL  , STRING     , NEWLINE      , 'in'         ,            , 1        
SUB  , 'assert'   , 'False'      , NEWLINE      , NAME       , 1        
DEL  , NEWLINE    , NEWLINE      , 'for'        ,            , 1        
SUB  , STRING     , NAME         , NEWLINE      , ')'        , 1        
DEL  , '!='       , '**'         , STRING       ,            , 1        
DEL  , STRING     , '**'         , ':'          ,            , 1        
SUB  , ','        , NUMBER       , ','          , STRING     , 1        
SUB  , NEWLINE    , '>'          , '-'          , 98         , 1        
SUB  , NEWLINE    , '>'          , '-'          , 99         , 1        
SUB  , '='        , NAME         , '('          , '{'        , 1        
SUB  , '{'        , '('          , NAME         , STRING     , 1        
DEL  , NAME       , ')'          , ';'          ,            , 1        
SUB  , ')'        , ','          , '('          , ']'        , 1        
INS  , ';'        ,              , ';'          , NUMBER     , 1        
SUB  , NUMBER     , '}'          , ','          , ']'        , 1        
INS  , ')'        ,              , NAME         , '=='       , 1        
INS  , ';'        ,              , 'while'      , NEWLINE    , 1        
INS  , '...'      ,              , NUMBER       , '['        , 1        
DEL  , ')'        , ']'          , UNKNOWN_CHAR ,            , 1        
SUB  , NAME       , NAME         , EOS          , '='        , 1        
INS  , '='        ,              , EOS          , '['        , 1        
INS  , '['        ,              , EOS          , '('        , 1        
DEL  , '('        , 'if'         , STRING       ,            , 1        
DEL  , '('        , STRING       , 'in'         ,            , 1        
DEL  , '('        , 'in'         , NAME         ,            , 1        
SUB  , '+'        , ';'          , NEWLINE      , NUMBER     , 1        
DEL  , '='        , UNKNOWN_CHAR , '+'          ,            , 1        
DEL  , '('        , '%'          , NAME         ,            , 1        
SUB  , NEWLINE    , 'continue'   , NEWLINE      , 99         , 1        
SUB  , ','        , '.'          , ','          , STRING     , 1        
SUB  , ','        , '.'          , ']'          , STRING     , 1        
DEL  , 99         , '...'        , EOS          ,            , 1        
SUB  , ')'        , '='          , '['          , '=='       , 1        
DEL  , NUMBER     , '-'          , ','          ,            , 1        
DEL  , BOS        , '['          , '{'          ,            , 1        
INS  , '['        ,              , NUMBER       , NAME       , 1        
INS  , '['        ,              , '['          , '('        , 1        
INS  , STRING     ,              , NEWLINE      , '['        , 1        
DEL  , '...'      , '...'        , ')'          ,            , 1        
DEL  , '...'      , ')'          , EOS          ,            , 1        
DEL  , ')'        , '...'        , ']'          ,            , 1        
DEL  , NEWLINE    , UNKNOWN_CHAR , '['          ,            , 1        
INS  , NAME       ,              , 'def'        , ')'        , 1        
DEL  , BOS        , '**'         , 'for'        ,            , 1        
DEL  , ')'        , UNKNOWN_CHAR , ';'          ,            , 1        
SUB  , STRING     , NAME         , '%'          , ','        , 1        
INS  , ','        ,              , '%'          , STRING     , 1        
SUB  , NAME       , '='          , '('          , '.'        , 1        
INS  , ')'        ,              , '.'          , NEWLINE    , 1        
SUB  , NEWLINE    , '%'          , NAME         , 98         , 1        
SUB  , 99         , '~'          , UNKNOWN_CHAR , 99         , 1        
SUB  , NAME       , NAME         , ']'          , 'in'       , 1        
INS  , 'in'       ,              , ']'          , '('        , 1        
DEL  , NEWLINE    , 99           , 'with'       ,            , 1        
DEL  , ']'        , UNKNOWN_CHAR , ']'          ,            , 1        
SUB  , BOS        , NEWLINE      , 98           , 'for'      , 1        
DEL  , 'for'      , 98           , 'or'         ,            , 1        
DEL  , 'for'      , 'or'         , NAME         ,            , 1        
DEL  , '...'      , '.'          , '+'          ,            , 1        
DEL  , '.'        , '.'          , NEWLINE      ,            , 1        
DEL  , ';'        , UNKNOWN_CHAR , 'None'       ,            , 1        
DEL  , 'None'     , UNKNOWN_CHAR , EOS          ,            , 1        
DEL  , NUMBER     , NEWLINE      , '<'          ,            , 1        
SUB  , NEWLINE    , 98           , 'return'     , NAME       , 1        
DEL  , NAME       , 'return'     , NEWLINE      ,            , 1        
SUB  , BOS        , STRING       , NAME         , '['        , 1        
SUB  , NUMBER     , ','          , '['          , ':'        , 1        
DEL  , STRING     , 'is'         , NAME         ,            , 1        
DEL  , STRING     , NAME         , '...'        ,            , 1        
DEL  , STRING     , '...'        , EOS          ,            , 1        
DEL  , 'return'   , '('          , NEWLINE      ,            , 1        
SUB  , 99         , 99           , 'else'       , 'elif'     , 1        
DEL  , 'elif'     , 'else'       , NAME         ,            , 1        
DEL  , NAME       , ')'          , '>'          ,            , 1        
SUB  , '='        , NEWLINE      , 98           , '['        , 1        
DEL  , '['        , 98           , NAME         ,            , 1        
SUB  , NAME       , NEWLINE      , 99           , ']'        , 1        
DEL  , BOS        , 'global'     , NAME         ,            , 1        
DEL  , NAME       , ')'          , STRING       ,            , 1        
INS  , 99         ,              , 'while'      , 'except'   , 1        
INS  , 'except'   ,              , 'while'      , ':'        , 1        
INS  , ':'        ,              , 'while'      , 'pass'     , 1        
INS  , 'pass'     ,              , 'while'      , NEWLINE    , 1        
DEL  , NAME       , '@'          , NAME         ,            , 1        
INS  , '...'      ,              , ')'          , '('        , 1        
DEL  , NAME       , ']'          , '['          ,            , 1        
SUB  , ':'        , 98           , 'await'      , '('        , 1        
INS  , '('        ,              , '='          , ')'        , 1        
DEL  , BOS        , NUMBER       , 'if'         ,            , 1        
SUB  , NEWLINE    , NUMBER       , 'if'         , 'elif'     , 1        
SUB  , NEWLINE    , NUMBER       , 'return'     , 'else'     , 1        
SUB  , STRING     , NAME         , '-'          , NEWLINE    , 1        
SUB  , BOS        , 'else'       , ':'          , 'def'      , 1        
INS  , 'def'      ,              , ':'          , NAME       , 1        
DEL  , ','        , NAME         , NEWLINE      ,            , 1        
DEL  , ')'        , ')'          , UNKNOWN_CHAR ,            , 1        
SUB  , STRING     , NEWLINE      , 98           , '('        , 1        
SUB  , '('        , 98           , '['          , ')'        , 1        
INS  , '=='       ,              , EOS          , STRING     , 1        
SUB  , STRING     , NAME         , NUMBER       , ','        , 1        
INS  , '{'        ,              , STRING       , '}'        , 1        
DEL  , ','        , 'and'        , NAME         ,            , 1        
SUB  , NEWLINE    , NAME         , '.'          , 'from'     , 1        
SUB  , '['        , '{'          , NUMBER       , '('        , 1        
DEL  , '='        , '>'          , STRING       ,            , 1        
INS  , 'while'    ,              , NAME         , 'True'     , 1        
INS  , 'True'     ,              , NAME         , ':'        , 1        
SUB  , BOS        , 'try'        , '='          , NAME       , 1        
SUB  , NEWLINE    , 'try'        , '.'          , NAME       , 1        
SUB  , NAME       , NUMBER       , NAME         , NEWLINE    , 1        
SUB  , NEWLINE    , 'and'        , EOS          , 98         , 1        
DEL  , '('        , '%'          , ')'          ,            , 1        
DEL  , 'is'       , ':'          , '['          ,            , 1        
INS  , ')'        ,              , '['          , ']'        , 1        
INS  , '}'        ,              , '{'          , '['        , 1        
INS  , '}'        ,              , '{'          , ']'        , 1        
INS  , ']'        ,              , '{'          , '['        , 1        
INS  , ')'        ,              , '.'          , '='        , 1        
DEL  , '%'        , '>'          , '%'          ,            , 1        
DEL  , '='        , '**'         , STRING       ,            , 1        
SUB  , STRING     , '**'         , NEWLINE      , STRING     , 1        
SUB  , NAME       , '%'          , '('          , NEWLINE    , 1        
DEL  , NEWLINE    , '('          , 'return'     ,            , 1        
DEL  , '('        , 'return'     , NAME         ,            , 1        
DEL  , '['        , '['          , '{'          ,            , 1        
INS  , 'True'     ,              , 'assert'     , ')'        , 1        
INS  , NEWLINE    ,              , '...'        , 'def'      , 1        
INS  , 'def'      ,              , '...'        , NAME       , 1        
INS  , NAME       ,              , '...'        , '('        , 1        
INS  , '('        ,              , '...'        , ')'        , 1        
INS  , ')'        ,              , '...'        , ':'        , 1        
SUB  , 'import'   , STRING       , NEWLINE      , NAME       , 1        
SUB  , STRING     , '}'          , NEWLINE      , ')'        , 1        
SUB  , '!='       , '+'          , 'or'         , STRING     , 1        
SUB  , '!='       , '-'          , 'or'         , STRING     , 1        
SUB  , '!='       , '*'          , NEWLINE      , STRING     , 1        
SUB  , 99         , NAME         , 'True'       , 'while'    , 1        
DEL  , ':'        , '**'         , '['          ,            , 1        
DEL  , NEWLINE    , 'import'     , NEWLINE      ,            , 1        
INS  , ')'        ,              , '}'          , ']'        , 1        
DEL  , ','        , '...'        , '.'          ,            , 1        
INS  , NAME       ,              , '!='         , ')'        , 1        
SUB  , NEWLINE    , 'pass'       , '.'          , NAME       , 1        
SUB  , NAME       , ':'          , '/'          , ','        , 1        
DEL  , ','        , '/'          , NAME         ,            , 1        
INS  , '='        ,              , STRING       , NAME       , 1        
DEL  , ')'        , '^'          , '^'          ,            , 1        
DEL  , ')'        , '^'          , EOS          ,            , 1        
DEL  , '{'        , '['          , NUMBER       ,            , 1        
DEL  , ']'        , '->'         , NAME         ,            , 1        
SUB  , '=='       , 'def'        , ')'          , NAME       , 1        
DEL  , ']'        , NAME         , 'return'     ,            , 1        
DEL  , ']'        , 'return'     , '['          ,            , 1        
DEL  , 98         , 'def'        , NAME         ,            , 1        
INS  , NAME       ,              , '='          , '.'        , 1        
SUB  , NEWLINE    , 98           , 'elif'       , 99         , 1        
SUB  , 99         , 'else'       , ':'          , 99         , 1        
INS  , 99         ,              , ':'          , 'except'   , 1        
DEL  , NEWLINE    , '>>'         , 'import'     ,            , 1        
SUB  , NUMBER     , ';'          , NUMBER       , ','        , 1        
DEL  , NAME       , '-'          , NEWLINE      ,            , 1        
DEL  , '~'        , NUMBER       , NAME         ,            , 1        
DEL  , '('        , NUMBER       , STRING       ,            , 1        
DEL  , ','        , ':'          , '{'          ,            , 1        
DEL  , NUMBER     , NUMBER       , '('          ,            , 1        
INS  , 'False'    ,              , ','          , ')'        , 1        
INS  , NAME       ,              , NAME         , '=='       , 1        
DEL  , '}'        , '<'          , NAME         ,            , 1        
DEL  , '}'        , NAME         , '>'          ,            , 1        
DEL  , '}'        , '>'          , NEWLINE      ,            , 1        
SUB  , STRING     , 'pass'       , UNKNOWN_CHAR , STRING     , 1        
DEL  , '='        , '('          , '{'          ,            , 1        
INS  , NUMBER     ,              , '-'          , ')'        , 1        
DEL  , ']'        , '**'         , '}'          ,            , 1        
INS  , NEWLINE    ,              , STRING       , '{'        , 1        
DEL  , NAME       , '['          , '='          ,            , 1        
INS  , BOS        ,              , NEWLINE      , 'import'   , 1        
SUB  , NEWLINE    , 98           , 'def'        , 'class'    , 1        
DEL  , 'class'    , 'def'        , NAME         ,            , 1        
SUB  , NAME       , ']'          , EOS          , '}'        , 1        
DEL  , ':'        , '('          , STRING       ,            , 1        
DEL  , ':'        , STRING       , ')'          ,            , 1        
INS  , ')'        ,              , ':'          , NEWLINE    , 1        
INS  , NEWLINE    ,              , ':'          , 'for'      , 1        
SUB  , NAME       , '='          , 'None'       , ':'        , 1        
DEL  , ','        , NEWLINE      , '('          ,            , 1        
SUB  , NEWLINE    , 'or'         , NAME         , 'for'      , 1        
DEL  , '='        , '>'          , 'True'       ,            , 1        
DEL  , '-'        , NAME         , '{'          ,            , 1        
DEL  , NEWLINE    , NEWLINE      , EOS          ,            , 1        
INS  , BOS        ,              , NAME         , 'with'     , 1        
DEL  , NEWLINE    , 99           , 99           ,            , 1        
SUB  , 'else'     , NEWLINE      , NAME         , ':'        , 1        
DEL  , STRING     , 99           , STRING       ,            , 1        
DEL  , STRING     , STRING       , EOS          ,            , 1        
SUB  , STRING     , STRING       , NAME         , ']'        , 1        
DEL  , ':'        , NAME         , 'not'        ,            , 1        
DEL  , ','        , '%'          , NAME         ,            , 1        
DEL  , NAME       , ','          , '%'          ,            , 1        
DEL  , NAME       , UNKNOWN_CHAR , '('          ,            , 1        
INS  , ']'        ,              , '-'          , ')'        , 1        
DEL  , ','        , NUMBER       , '...'        ,            , 1        
DEL  , NUMBER     , '|'          , EOS          ,            , 1        
SUB  , NAME       , '-'          , NAME         , NEWLINE    , 1        
SUB  , BOS        , NEWLINE      , 98           , '('        , 1        
DEL  , ']'        , 99           , ')'          ,            , 1        
DEL  , BOS        , NAME         , '-'          ,            , 1        
DEL  , BOS        , '-'          , NAME         ,            , 1        
DEL  , '='        , '['          , UNKNOWN_CHAR ,            , 1        
DEL  , NAME       , '...'        , ']'          ,            , 1        
SUB  , '}'        , UNKNOWN_CHAR , EOS          , NEWLINE    , 1        
SUB  , ','        , 'class'      , ':'          , STRING     , 1        
SUB  , NUMBER     , ','          , EOS          , ']'        , 1        
DEL  , ')'        , '='          , NAME         ,            , 1        
DEL  , STRING     , '-'          , EOS          ,            , 1        
INS  , ','        ,              , '}'          , ']'        , 1        
DEL  , ']'        , '...'        , ','          ,            , 1        
SUB  , STRING     , UNKNOWN_CHAR , '}'          , ']'        , 1        
SUB  , ':'        , 'break'      , NEWLINE      , NAME       , 1        
INS  , BOS        ,              , '='          , NAME       , 1        
INS  , '='        ,              , NUMBER       , '['        , 1        
DEL  , NEWLINE    , NUMBER       , ')'          ,            , 1        
DEL  , NEWLINE    , ')'          , NAME         ,            , 1        
DEL  , NEWLINE    , 'while'      , NUMBER       ,            , 1        
DEL  , NEWLINE    , NUMBER       , ':'          ,            , 1        
INS  , ','        ,              , EOS          , '{'        , 1        
INS  , '{'        ,              , EOS          , '}'        , 1        
SUB  , ':'        , 'continue'   , NEWLINE      , NAME       , 1        
DEL  , '['        , STRING       , '='          ,            , 1        
DEL  , '['        , '='          , NUMBER       ,            , 1        
DEL  , ','        , '='          , NUMBER       ,            , 1        
DEL  , 98         , '.'          , NEWLINE      ,            , 1        
SUB  , 'and'      , UNKNOWN_CHAR , NAME         , STRING     , 1        
SUB  , '('        , 'in'         , '('          , NAME       , 1        
DEL  , BOS        , 'class'      , NEWLINE      ,            , 1        
DEL  , NEWLINE    , 99           , '{'          ,            , 1        
DEL  , ']'        , '...'        , NAME         ,            , 1        
INS  , '%'        ,              , '=='         , NUMBER     , 1        
DEL  , ')'        , '~'          , EOS          ,            , 1        
DEL  , NEWLINE    , 99           , '*'          ,            , 1        
INS  , ','        ,              , '('          , ')'        , 1        
SUB  , '['        , 'from'       , ','          , NAME       , 1        
DEL  , ')'        , 'or'         , EOS          ,            , 1        
INS  , STRING     ,              , ':'          , '='        , 1        
INS  , '='        ,              , ':'          , '{'        , 1        
SUB  , 'from'     , 'def'        , 'import'     , NAME       , 1        
SUB  , '.'        , 'def'        , 'import'     , NAME       , 1        
DEL  , ','        , '**'         , ')'          ,            , 1        
SUB  , '='        , '.'          , NEWLINE      , NUMBER     , 1        
SUB  , NAME       , '.'          , '.'          , '='        , 1        
SUB  , '='        , '.'          , EOS          , NUMBER     , 1        
DEL  , STRING     , '&'          , NEWLINE      ,            , 1        
SUB  , '}'        , ']'          , NEWLINE      , '}'        , 1        
DEL  , BOS        , '>>'         , NUMBER       ,            , 1        
DEL  , BOS        , UNKNOWN_CHAR , 'return'     ,            , 1        
INS  , NUMBER     ,              , ':'          , ','        , 1        
INS  , ','        ,              , ':'          , NUMBER     , 1        
DEL  , '('        , '>'          , NUMBER       ,            , 1        
DEL  , '...'      , STRING       , ','          ,            , 1        
DEL  , ','        , ':'          , NUMBER       ,            , 1        
DEL  , NUMBER     , NEWLINE      , NAME         ,            , 1        
DEL  , ']'        , ']'          , '}'          ,            , 1        
DEL  , '/'        , NAME         , NUMBER       ,            , 1        
DEL  , 99         , 'elif'       , NEWLINE      ,            , 1        
INS  , NAME       ,              , 'with'       , NEWLINE    , 1        
SUB  , NAME       , UNKNOWN_CHAR , '('          , '-'        , 1        
INS  , ')'        ,              , NAME         , '*'        , 1        
SUB  , NAME       , '.'          , NAME         , NEWLINE    , 1        
SUB  , NAME       , NAME         , '['          , ')'        , 1        
SUB  , NEWLINE    , 'False'      , NEWLINE      , NAME       , 1        
INS  , 'False'    ,              , ']'          , '}'        , 1        
DEL  , 'not'      , 'None'       , 'else'       ,            , 1        
DEL  , 'not'      , 'else'       , STRING       ,            , 1        
INS  , NEWLINE    ,              , '['          , 98         , 1        
INS  , ':'        ,              , STRING       , NUMBER     , 1        
DEL  , NAME       , 'with'       , ':'          ,            , 1        
DEL  , '...'      , ':'          , '['          ,            , 1        
DEL  , 'True'     , UNKNOWN_CHAR , EOS          ,            , 1        
INS  , ','        ,              , EOS          , '['        , 1        
SUB  , NAME       , STRING       , ']'          , ','        , 1        
INS  , ')'        ,              , ','          , '}'        , 1        
DEL  , '}'        , '.'          , NEWLINE      ,            , 1        
SUB  , NEWLINE    , 98           , NAME         , 'if'       , 1        
INS  , STRING     ,              , 'in'         , STRING     , 1        
DEL  , STRING     , ':'          , '('          ,            , 1        
DEL  , STRING     , NUMBER       , ';'          ,            , 1        
SUB  , '('        , 'and'        , '=='         , NAME       , 1        
DEL  , NAME       , STRING       , '-'          ,            , 1        
SUB  , NAME       , '<'          , NAME         , NEWLINE    , 1        
DEL  , NAME       , '|'          , EOS          ,            , 1        
DEL  , ')'        , '('          , NAME         ,            , 1        
SUB  , NEWLINE    , '...'        , NEWLINE      , 98         , 1        
INS  , 98         ,              , NEWLINE      , 'return'   , 1        
DEL  , '}'        , ']'          , EOS          ,            , 1        
DEL  , ')'        , 'return'     , NUMBER       ,            , 1        
DEL  , NAME       , '*'          , 'for'        ,            , 1        
INS  , STRING     ,              , '<'          , ')'        , 1        
DEL  , BOS        , UNKNOWN_CHAR , EOS          ,            , 1        
SUB  , NUMBER     , '('          , NAME         , NEWLINE    , 1        
INS  , ','        ,              , NAME         , NEWLINE    , 1        
INS  , '{'        ,              , '}'          , STRING     , 1        
INS  , ':'        ,              , '}'          , '{'        , 1        
DEL  , 99         , '...'        , '.'          ,            , 1        
SUB  , ','        , NAME         , UNKNOWN_CHAR , '...'      , 1        
DEL  , '...'      , UNKNOWN_CHAR , ']'          ,            , 1        
INS  , '='        ,              , NAME         , 'yield'    , 1        
DEL  , ','        , UNKNOWN_CHAR , ','          ,            , 1        
SUB  , '}'        , ','          , '{'          , ']'        , 1        
SUB  , ']'        , '{'          , EOS          , '}'        , 1        
INS  , NEWLINE    ,              , '-'          , 98         , 1        
INS  , NEWLINE    ,              , '-'          , 99         , 1        
SUB  , ']'        , '...'        , '.'          , ','        , 1        
DEL  , ','        , '.'          , '['          ,            , 1        
SUB  , STRING     , UNKNOWN_CHAR , '}'          , ','        , 1        
INS  , 'or'       ,              , '>'          , NAME       , 1        
SUB  , ':'        , UNKNOWN_CHAR , '/'          , STRING     , 1        
SUB  , NAME       , '*'          , ')'          , '**'       , 1        
DEL  , '**'       , ')'          , '*'          ,            , 1        
DEL  , '+'        , '('          , NUMBER       ,            , 1        
SUB  , STRING     , NAME         , '('          , ')'        , 1        
INS  , ')'        ,              , STRING       , '['        , 1        
SUB  , STRING     , UNKNOWN_CHAR , 'for'        , NEWLINE    , 1        
DEL  , ')'        , STRING       , UNKNOWN_CHAR ,            , 1        
SUB  , BOS        , 'for'        , NAME         , NEWLINE    , 1        
INS  , ')'        ,              , ','          , NEWLINE    , 1        
INS  , NEWLINE    ,              , ','          , NAME       , 1        
DEL  , ']'        , '*'          , ']'          ,            , 1        
SUB  , '+'        , '/'          , NAME         , STRING     , 1        
DEL  , STRING     , UNKNOWN_CHAR , '*'          ,            , 1        
DEL  , ']'        , NAME         , 'is'         ,            , 1        
DEL  , ']'        , 'is'         , NAME         ,            , 1        
SUB  , STRING     , NAME         , '.'          , STRING     , 1        
SUB  , STRING     , '.'          , UNKNOWN_CHAR , STRING     , 1        
SUB  , STRING     , NAME         , '.'          , ':'        , 1        
INS  , ':'        ,              , '.'          , STRING     , 1        
SUB  , STRING     , UNKNOWN_CHAR , NAME         , ')'        , 1        
INS  , ')'        ,              , 'from'       , NEWLINE    , 1        
DEL  , BOS        , UNKNOWN_CHAR , 'del'        ,            , 1        
DEL  , STRING     , '}'          , ']'          ,            , 1        
DEL  , '//'       , '/'          , NAME         ,            , 1        
DEL  , BOS        , '**'         , '['          ,            , 1        
SUB  , 98         , '>'          , NUMBER       , NEWLINE    , 1        
SUB  , NEWLINE    , NUMBER       , EOS          , 99         , 1        
INS  , STRING     ,              , '('          , ':'        , 1        
SUB  , NUMBER     , NEWLINE      , 99           , '}'        , 1        
SUB  , STRING     , STRING       , NUMBER       , ','        , 1        
SUB  , NUMBER     , NAME         , UNKNOWN_CHAR , ':'        , 1        
DEL  , NAME       , 'return'     , NUMBER       ,            , 1        
DEL  , NAME       , NUMBER       , 'else'       ,            , 1        
INS  , 'else'     ,              , EOS          , NUMBER     , 1        
DEL  , '='        , 99           , '{'          ,            , 1        
SUB  , '{'        , ':'          , NUMBER       , STRING     , 1        
DEL  , '='        , 99           , NAME         ,            , 1        
INS  , BOS        ,              , 'except'     , 'try'      , 1        
INS  , ':'        ,              , 'except'     , '('        , 1        
INS  , '('        ,              , 'except'     , ')'        , 1        
INS  , ')'        ,              , 'except'     , NEWLINE    , 1        
SUB  , STRING     , '='          , '['          , ','        , 1        
DEL  , STRING     , '='          , '['          ,            , 1        
SUB  , ')'        , NEWLINE      , 'pass'       , ':'        , 1        
INS  , NAME       ,              , '='          , '['        , 1        
INS  , '['        ,              , '='          , STRING     , 1        
INS  , ')'        ,              , '.'          , '['        , 1        
INS  , '['        ,              , '.'          , ':'        , 1        
INS  , ':'        ,              , '.'          , ']'        , 1        
INS  , NAME       ,              , NUMBER       , NEWLINE    , 1        
SUB  , ','        , UNKNOWN_CHAR , '<'          , STRING     , 1        
DEL  , STRING     , '>'          , ']'          ,            , 1        
SUB  , 98         , 'return'     , NAME         , 'if'       , 1        
SUB  , NUMBER     , UNKNOWN_CHAR , 'True'       , ':'        , 1        
DEL  , 99         , 'else'       , 'return'     ,            , 1        
DEL  , ']'        , STRING       , NAME         ,            , 1        
DEL  , ']'        , NAME         , '-'          ,            , 1        
DEL  , STRING     , '>'          , '<'          ,            , 1        
DEL  , STRING     , '<'          , '/'          ,            , 1        
DEL  , ','        , '//'         , NAME         ,            , 1        
DEL  , ':'        , UNKNOWN_CHAR , STRING       ,            , 1        
INS  , '('        ,              , '('          , '['        , 1        
INS  , NUMBER     ,              , NAME         , '/'        , 1        
SUB  , 99         , 'break'      , NEWLINE      , 99         , 1        
DEL  , 'import'   , NEWLINE      , NAME         ,            , 1        
INS  , NAME       ,              , '-'          , NEWLINE    , 1        
DEL  , NAME       , '|'          , NEWLINE      ,            , 1        
SUB  , '('        , NUMBER       , ')'          , NAME       , 1        
DEL  , NEWLINE    , 98           , ')'          ,            , 1        
DEL  , ','        , NUMBER       , '}'          ,            , 1        
INS  , '['        ,              , ']'          , '['        , 1        
INS  , 'if'       ,              , '%'          , NAME       , 1        
INS  , 98         ,              , 'def'        , '['        , 1        
INS  , '['        ,              , 'def'        , ']'        , 1        
INS  , ']'        ,              , 'def'        , NEWLINE    , 1        
DEL  , BOS        , NUMBER       , 'def'        ,            , 1        
SUB  , NEWLINE    , NUMBER       , 'return'     , 98         , 1        
SUB  , NEWLINE    , NUMBER       , NAME         , 99         , 1        
INS  , 98         ,              , 'def'        , NAME       , 1        
DEL  , '>>'       , '>'          , '['          ,            , 1        
SUB  , ')'        , UNKNOWN_CHAR , ']'          , ')'        , 1        
SUB  , STRING     , '}'          , ','          , ']'        , 1        
SUB  , 'None'     , ']'          , ','          , '}'        , 1        
SUB  , NUMBER     , ']'          , ','          , '}'        , 1        
DEL  , NAME       , UNKNOWN_CHAR , '...'        ,            , 1        
SUB  , ','        , '{'          , NAME         , '['        , 1        
SUB  , NAME       , '}'          , ')'          , ']'        , 1        
DEL  , NEWLINE    , 'except'     , ':'          ,            , 1        
DEL  , NEWLINE    , 98           , 'pass'       ,            , 1        
INS  , 98         ,              , NAME         , '['        , 1        
SUB  , NUMBER     , '('          , NAME         , '/'        , 1        
SUB  , '='        , NAME         , NAME         , '('        , 1        
DEL  , '['        , ';'          , NAME         ,            , 1        
SUB  , NUMBER     , NUMBER       , ')'          , ','        , 1        
DEL  , ']'        , '/'          , NAME         ,            , 1        
DEL  , BOS        , 'import'     , '...'        ,            , 1        
SUB  , NUMBER     , ')'          , ']'          , ','        , 1        
INS  , '=='       ,              , EOS          , NAME       , 1        
SUB  , ','        , NEWLINE      , 98           , '['        , 1        
DEL  , STRING     , 99           , 99           ,            , 1        
DEL  , 98         , 'if'         , '('          ,            , 1        
DEL  , ']'        , ')'          , 'not'        ,            , 1        
DEL  , STRING     , '**'         , '}'          ,            , 1        
SUB  , NUMBER     , ';'          , NUMBER       , ':'        , 1        
DEL  , NEWLINE    , NAME         , 'not'        ,            , 1        
DEL  , ','        , '*'          , EOS          ,            , 1        
DEL  , ','        , 'is'         , ','          ,            , 1        
DEL  , '//'       , 'not'        , NAME         ,            , 1        
DEL  , ')'        , '//'         , 'not'        ,            , 1        
INS  , '...'      ,              , 'None'       , '['        , 1        
SUB  , ':'        , 'import'     , NAME         , NUMBER     , 1        
DEL  , NAME       , 'continue'   , NEWLINE      ,            , 1        
SUB  , ')'        , '...'        , NAME         , ','        , 1        
DEL  , BOS        , '**'         , '{'          ,            , 1        
DEL  , '}'        , '**'         , EOS          ,            , 1        
SUB  , NUMBER     , UNKNOWN_CHAR , '('          , ':'        , 1        
SUB  , BOS        , NAME         , '.'          , 'from'     , 1        
SUB  , NAME       , NAME         , ':'          , '('        , 1        
INS  , 'True'     ,              , NEWLINE      , ')'        , 1        
INS  , NUMBER     ,              , NUMBER       , '['        , 1        
INS  , NUMBER     ,              , NUMBER       , ']'        , 1        
DEL  , NAME       , ';'          , '('          ,            , 1        
DEL  , ')'        , ')'          , '~'          ,            , 1        
DEL  , ')'        , '~'          , '='          ,            , 1        
DEL  , NUMBER     , ','          , NEWLINE      ,            , 1        
INS  , NEWLINE    ,              , STRING       , 'for'      , 1        
INS  , STRING     ,              , ':'          , 'in'       , 1        
SUB  , 'True'     , NEWLINE      , 98           , ':'        , 1        
DEL  , STRING     , NAME         , '|'          ,            , 1        
DEL  , '|'        , NAME         , STRING       ,            , 1        
SUB  , ':'        , UNKNOWN_CHAR , ']'          , '('        , 1        
INS  , '('        ,              , ']'          , '['        , 1        
DEL  , ')'        , NEWLINE      , '}'          ,            , 1        
DEL  , BOS        , UNKNOWN_CHAR , NUMBER       ,            , 1        
DEL  , 99         , '**'         , EOS          ,            , 1        
DEL  , ')'        , 'or'         , 'is'         ,            , 1        
SUB  , ','        , ':'          , '//'         , STRING     , 1        
DEL  , '{'        , STRING       , '='          ,            , 1        
DEL  , '{'        , '='          , NAME         ,            , 1        
DEL  , ','        , '**'         , '['          ,            , 1        
INS  , ','        ,              , NEWLINE      , STRING     , 1        
INS  , STRING     ,              , NEWLINE      , ','        , 1        
INS  , ','        ,              , '('          , '{'        , 1        
INS  , 'and'      ,              , NEWLINE      , NAME       , 1        
INS  , NAME       ,              , '**'         , '('        , 1        
SUB  , BOS        , UNKNOWN_CHAR , '['          , NAME       , 1        
INS  , '['        ,              , '['          , '{'        , 1        
INS  , '}'        ,              , NAME         , ']'        , 1        
DEL  , NEWLINE    , 98           , 'else'       ,            , 1        
SUB  , NAME       , '->'         , NAME         , ':'        , 1        
DEL  , NAME       , ','          , 'and'        ,            , 1        
DEL  , '}'        , '...'        , '}'          ,            , 1        
SUB  , ','        , '['          , NUMBER       , '('        , 1        
SUB  , 99         , NAME         , ':'          , 'else'     , 1        
DEL  , NAME       , 'with'       , '='          ,            , 1        
DEL  , ')'        , 'for'        , '-'          ,            , 1        
DEL  , '-'        , ':'          , STRING       ,            , 1        
DEL  , '='        , '='          , 'False'      ,            , 1        
DEL  , NAME       , '['          , UNKNOWN_CHAR ,            , 1        
DEL  , NAME       , '['          , '**'         ,            , 1        
SUB  , '='        , '>'          , NAME         , '('        , 1        
SUB  , STRING     , '.'          , '/'          , ')'        , 1        
DEL  , NAME       , 98           , 'return'     ,            , 1        
DEL  , NAME       , 'return'     , '('          ,            , 1        
DEL  , ')'        , NEWLINE      , UNKNOWN_CHAR ,            , 1        
DEL  , ']'        , NEWLINE      , UNKNOWN_CHAR ,            , 1        
SUB  , ')'        , ','          , EOS          , ']'        , 1        
INS  , 'False'    ,              , ','          , '['        , 1        
INS  , '['        ,              , ','          , NUMBER     , 1        
SUB  , STRING     , '='          , STRING       , ','        , 1        
SUB  , NAME       , STRING       , STRING       , ','        , 1        
SUB  , ','        , STRING       , UNKNOWN_CHAR , NAME       , 1        
SUB  , '+'        , UNKNOWN_CHAR , '-'          , STRING     , 1        
SUB  , NUMBER     , ')'          , NEWLINE      , ','        , 1        
SUB  , ':'        , NUMBER       , ','          , STRING     , 1        
SUB  , STRING     , ')'          , ','          , ']'        , 1        
DEL  , NAME       , '('          , EOS          ,            , 1        
DEL  , STRING     , 98           , NAME         ,            , 1        
DEL  , ':'        , 'return'     , NEWLINE      ,            , 1        
DEL  , ')'        , '+'          , NEWLINE      ,            , 1        
DEL  , 'not'      , '.'          , NAME         ,            , 1        
DEL  , '.'        , '-'          , NAME         ,            , 1        
DEL  , STRING     , NAME         , 'with'       ,            , 1        
DEL  , STRING     , 'with'       , NUMBER       ,            , 1        
INS  , '...'      ,              , '...'        , '='        , 1        
SUB  , NAME       , '.'          , 'is'         , 'in'       , 1        
DEL  , 'in'       , 'is'         , NAME         ,            , 1        
SUB  , NUMBER     , '.'          , NUMBER       , ','        , 1        
INS  , 98         ,              , NAME         , 'with'     , 1        
SUB  , NAME       , ';'          , '-'          , ':'        , 1        
SUB  , '('        , 'class'      , '='          , '{'        , 1        
DEL  , '{'        , '='          , STRING       ,            , 1        
DEL  , STRING     , '='          , STRING       ,            , 1        
DEL  , STRING     , STRING       , ')'          ,            , 1        
DEL  , STRING     , ')'          , UNKNOWN_CHAR ,            , 1        
DEL  , NUMBER     , '//'         , NAME         ,            , 1        
INS  , ')'        ,              , 'return'     , '}'        , 1        
DEL  , NEWLINE    , 'class'      , '='          ,            , 1        
INS  , 'True'     ,              , '['          , ')'        , 1        
INS  , ')'        ,              , 'if'         , ']'        , 1        
INS  , ')'        ,              , EOS          , '['        , 1        
INS  , '['        ,              , EOS          , NUMBER     , 1        
DEL  , 99         , 99           , 'try'        ,            , 1        
DEL  , STRING     , '{'          , NUMBER       ,            , 1        
INS  , NEWLINE    ,              , ')'          , '('        , 1        
SUB  , NAME       , ')'          , '='          , ']'        , 1        
SUB  , '='        , '['          , '('          , '{'        , 1        
SUB  , STRING     , ','          , NAME         , '('        , 1        
SUB  , NUMBER     , UNKNOWN_CHAR , ')'          , '='        , 1        
INS  , 99         ,              , EOS          , STRING     , 1        
SUB  , 'in'       , UNKNOWN_CHAR , NAME         , STRING     , 1        
DEL  , 'in'       , UNKNOWN_CHAR , NAME         ,            , 1        
DEL  , NAME       , UNKNOWN_CHAR , 'for'        ,            , 1        
INS  , '('        ,              , '<'          , NAME       , 1        
SUB  , NUMBER     , ']'          , '['          , ')'        , 1        
SUB  , 'as'       , 'in'         , ':'          , NAME       , 1        
DEL  , NAME       , '}'          , ':'          ,            , 1        
INS  , '...'      ,              , NAME         , '('        , 1        
SUB  , NEWLINE    , 'break'      , '&'          , NAME       , 1        
INS  , ')'        ,              , NEWLINE      , '='        , 1        
DEL  , NEWLINE    , 'and'        , 'for'        ,            , 1        
DEL  , 99         , 99           , '>'          ,            , 1        
DEL  , NEWLINE    , '='          , '>'          ,            , 1        
SUB  , NAME       , '='          , NUMBER       , 'in'       , 1        
INS  , 'in'       ,              , NUMBER       , '('        , 1        
DEL  , ']'        , 99           , '>>'         ,            , 1        
DEL  , NAME       , '-'          , '*'          ,            , 1        
DEL  , NAME       , '*'          , '/'          ,            , 1        
INS  , '('        ,              , STRING       , NAME       , 1        
SUB  , NEWLINE    , '.'          , NEWLINE      , '...'      , 1        
DEL  , 99         , 99           , 'with'       ,            , 1        
INS  , 'if'       ,              , NAME         , 'not'      , 1        
DEL  , NAME       , '...'        , '}'          ,            , 1        
INS  , '+'        ,              , '.'          , STRING     , 1        
SUB  , NAME       , '.'          , NEWLINE      , '+'        , 1        
DEL  , 99         , UNKNOWN_CHAR , '>'          ,            , 1        
SUB  , ']'        , '.'          , NAME         , ')'        , 1        
DEL  , 'None'     , 'is'         , 'in'         ,            , 1        
SUB  , ','        , UNKNOWN_CHAR , '*'          , STRING     , 1        
DEL  , STRING     , '.'          , '*'          ,            , 1        
DEL  , STRING     , '*'          , UNKNOWN_CHAR ,            , 1        
SUB  , '+'        , 'class'      , '+'          , NAME       , 1        
DEL  , NAME       , 98           , '@'          ,            , 1        
DEL  , '.'        , 'import'     , NAME         ,            , 1        
SUB  , NEWLINE    , 'with'       , '='          , NAME       , 1        
SUB  , '='        , 'with'       , '('          , NAME       , 1        
SUB  , '('        , 'with'       , ')'          , NAME       , 1        
INS  , ':'        ,              , 'break'      , NEWLINE    , 1        
DEL  , 98         , '...'        , NAME         ,            , 1        
SUB  , STRING     , UNKNOWN_CHAR , ']'          , ':'        , 1        
INS  , ':'        ,              , ']'          , '['        , 1        
DEL  , 'and'      , NEWLINE      , 98           ,            , 1        
DEL  , 'and'      , 98           , NAME         ,            , 1        
SUB  , NAME       , NEWLINE      , 'def'        , ','        , 1        
DEL  , ','        , 'def'        , NAME         ,            , 1        
SUB  , NAME       , STRING       , '*'          , '('        , 1        
INS  , '&='       ,              , '('          , NAME       , 1        
INS  , ','        ,              , NEWLINE      , '('        , 1        
INS  , 'None'     ,              , 'not'        , 'or'       , 1        
SUB  , '.'        , 'continue'   , '('          , NAME       , 1        
DEL  , ')'        , 99           , '['          ,            , 1        
SUB  , '('        , '...'        , EOS          , ')'        , 1        
SUB  , 'None'     , 'and'        , EOS          , ':'        , 1        
DEL  , '&'        , '&'          , NUMBER       ,            , 1        
SUB  , ')'        , 'for'        , NAME         , 'in'       , 1        
INS  , NEWLINE    ,              , NAME         , 'del'      , 1        
SUB  , ']'        , '<='         , '>'          , ':'        , 1        
INS  , ':'        ,              , '>'          , NAME       , 1        
DEL  , ']'        , '<='         , '>'          ,            , 1        
SUB  , NAME       , NEWLINE      , 'True'       , ')'        , 1        
DEL  , NAME       , '//'         , 'or'         ,            , 1        
INS  , NUMBER     ,              , NEWLINE      , '}'        , 1        
INS  , ')'        ,              , 'for'        , '('        , 1        
INS  , '('        ,              , 'for'        , STRING     , 1        
DEL  , NUMBER     , 'if'         , NAME         ,            , 1        
DEL  , '['        , NUMBER       , ']'          ,            , 1        
DEL  , '['        , ']'          , '.'          ,            , 1        
DEL  , '['        , '.'          , NAME         ,            , 1        
DEL  , STRING     , UNKNOWN_CHAR , 'if'         ,            , 1        
DEL  , 'else'     , UNKNOWN_CHAR , STRING       ,            , 1        
INS  , STRING     ,              , NAME         , '}'        , 1        
DEL  , ')'        , '('          , 'if'         ,            , 1        
SUB  , 'is'       , 'True'       , NEWLINE      , NAME       , 1        
SUB  , '='        , UNKNOWN_CHAR , UNKNOWN_CHAR , 'None'     , 1        
INS  , 'return'   ,              , NAME         , NUMBER     , 1        
INS  , NUMBER     ,              , NAME         , 'if'       , 1        
SUB  , STRING     , UNKNOWN_CHAR , NUMBER       , 'else'     , 1        
DEL  , 'else'     , NUMBER       , ':'          ,            , 1        
DEL  , 'else'     , ':'          , NAME         ,            , 1        
INS  , ';'        ,              , NEWLINE      , NAME       , 1        
DEL  , BOS        , NUMBER       , STRING       ,            , 1        
SUB  , '='        , 'lambda'     , ','          , NAME       , 1        
SUB  , ']'        , '['          , 'for'        , ']'        , 1        
SUB  , NAME       , '='          , NUMBER       , '>='       , 1        
DEL  , 'break'    , NAME         , NEWLINE      ,            , 1        
DEL  , ')'        , ';'          , ')'          ,            , 1        
DEL  , NEWLINE    , '>'          , 'if'         ,            , 1        
INS  , 98         ,              , '@'          , NAME       , 1        
INS  , NAME       ,              , '*'          , '/'        , 1        
INS  , '/'        ,              , '*'          , NAME       , 1        
DEL  , '%'        , NUMBER       , NAME         ,            , 1        
DEL  , '%'        , NAME         , UNKNOWN_CHAR ,            , 1        
DEL  , '%'        , UNKNOWN_CHAR , '%'          ,            , 1        
DEL  , '%'        , '%'          , '('          ,            , 1        
DEL  , ']'        , ']'          , '['          ,            , 1        
INS  , '+='       ,              , STRING       , '['        , 1        
INS  , ']'        ,              , NEWLINE      , 'else'     , 1        
INS  , 'else'     ,              , NEWLINE      , 'False'    , 1        
SUB  , NAME       , 'in'         , 'not'        , 'is'       , 1        
DEL  , NUMBER     , ':'          , EOS          ,            , 1        
DEL  , NUMBER     , NAME         , '<<'         ,            , 1        
DEL  , NEWLINE    , UNKNOWN_CHAR , NUMBER       ,            , 1        
SUB  , '.'        , 'import'     , '='          , NAME       , 1        
SUB  , 'in'       , '['          , NUMBER       , NAME       , 1        
SUB  , NUMBER     , NUMBER       , ']'          , ','        , 1        
SUB  , ','        , ']'          , ':'          , ')'        , 1        
SUB  , '...'      , NAME         , '...'        , '='        , 1        
DEL  , NUMBER     , '-'          , ')'          ,            , 1        
SUB  , ')'        , '<'          , '/'          , ')'        , 1        
SUB  , '('        , STRING       , NEWLINE      , ')'        , 1        
SUB  , ')'        , 'is'         , NAME         , 'if'       , 1        
INS  , 99         ,              , NAME         , 'with'     , 1        
SUB  , NAME       , NAME         , EOS          , ':'        , 1        
SUB  , NAME       , ')'          , '['          , ']'        , 1        
DEL  , ')'        , '}'          , '}'          ,            , 1        
DEL  , ','        , ']'          , ']'          ,            , 1        
DEL  , ','        , ']'          , NEWLINE      ,            , 1        
SUB  , '('        , 'None'       , 'if'         , 'lambda'   , 1        
DEL  , 'lambda'   , 'if'         , NAME         ,            , 1        
INS  , ':'        ,              , '('          , NAME       , 1        
SUB  , NAME       , '>'          , NUMBER       , ':'        , 1        
DEL  , ':'        , NUMBER       , ')'          ,            , 1        
DEL  , 'import'   , NEWLINE      , 'import'     ,            , 1        
DEL  , 'import'   , 'import'     , NAME         ,            , 1        
SUB  , NAME       , ';'          , NAME         , '-'        , 1        
SUB  , ','        , 'pass'       , ':'          , STRING     , 1        
DEL  , NAME       , 'and'        , NAME         ,            , 1        
SUB  , ')'        , '}'          , EOS          , ']'        , 1        
INS  , ')'        ,              , 'as'         , ')'        , 1        
DEL  , '.'        , ')'          , NAME         ,            , 1        
SUB  , BOS        , 'elif'       , '('          , 'if'       , 1        
SUB  , NAME       , ']'          , '='          , ')'        , 1        
DEL  , 99         , '}'          , NEWLINE      ,            , 1        
DEL  , 99         , NEWLINE      , 'class'      ,            , 1        
DEL  , 99         , '}'          , ')'          ,            , 1        
DEL  , 'else'     , '**'         , '{'          ,            , 1        
SUB  , NAME       , STRING       , NUMBER       , '.'        , 1        
DEL  , '.'        , NUMBER       , NAME         ,            , 1        
SUB  , '='        , 'return'     , '.'          , NAME       , 1        
INS  , NEWLINE    ,              , NUMBER       , '('        , 1        
INS  , STRING     ,              , NAME         , 'if'       , 1        
INS  , ']'        ,              , STRING       , '+'        , 1        
INS  , 'False'    ,              , EOS          , NEWLINE    , 1        
DEL  , 'in'       , '('          , STRING       ,            , 1        
DEL  , ']'        , 98           , 'del'        ,            , 1        
DEL  , ']'        , 'del'        , NAME         ,            , 1        
SUB  , NAME       , 'else'       , NAME         , 'in'       , 1        
DEL  , STRING     , 'if'         , NEWLINE      ,            , 1        
SUB  , '('        , NEWLINE      , NAME         , ')'        , 1        
INS  , ':'        ,              , NEWLINE      , 'None'     , 1        
DEL  , '('        , ','          , STRING       ,            , 1        
INS  , '('        ,              , '*'          , '('        , 1        
INS  , NAME       ,              , ')'          , '['        , 1        
SUB  , NAME       , '.'          , NAME         , '('        , 1        
DEL  , NEWLINE    , 99           , '//'         ,            , 1        
DEL  , NEWLINE    , '//'         , '//'         ,            , 1        
DEL  , NEWLINE    , '//'         , EOS          ,            , 1        
SUB  , 'lambda'   , 'pass'       , ','          , NAME       , 1        
SUB  , ':'        , 'pass'       , ','          , NAME       , 1        
SUB  , NAME       , NEWLINE      , EOS          , ')'        , 1        
DEL  , ')'        , '//'         , EOS          ,            , 1        
INS  , ','        ,              , '...'        , ']'        , 1        
DEL  , '|'        , 98           , NAME         ,            , 1        
INS  , '}'        ,              , 'for'        , ')'        , 1        
INS  , '('        ,              , ','          , NUMBER     , 1        
SUB  , 'for'      , 'for'        , 'in'         , NAME       , 1        
DEL  , NUMBER     , ')'          , 'for'        ,            , 1        
INS  , ')'        ,              , 'if'         , ')'        , 1        
INS  , NEWLINE    ,              , '...'        , '['        , 1        
INS  , '['        ,              , '...'        , STRING     , 1        
SUB  , '...'      , '.'          , NEWLINE      , ']'        , 1        
INS  , '...'      ,              , NEWLINE      , '=='       , 1        
INS  , '=='       ,              , NEWLINE      , STRING     , 1        
DEL  , NUMBER     , '='          , '>'          ,            , 1        
DEL  , '['        , '...'        , NAME         ,            , 1        
INS  , '...'      ,              , NAME         , '.'        , 1        
SUB  , 98         , 'def'        , '...'        , NAME       , 1        
SUB  , NAME       , '...'        , NEWLINE      , '='        , 1        
SUB  , ')'        , ':'          , EOS          , NEWLINE    , 1        
DEL  , ')'        , 98           , '^'          ,            , 1        
DEL  , ')'        , '^'          , NEWLINE      ,            , 1        
DEL  , '('        , 'if'         , NAME         ,            , 1        
DEL  , NUMBER     , ':'          , 'for'        ,            , 1        
SUB  , NAME       , NAME         , EOS          , ']'        , 1        
SUB  , ']'        , ','          , NAME         , ':'        , 1        
DEL  , '}'        , NEWLINE      , '='          ,            , 1        
DEL  , '}'        , '='          , '>'          ,            , 1        
SUB  , NUMBER     , ']'          , ')'          , '=='       , 1        
INS  , '=='       ,              , ')'          , NUMBER     , 1        
SUB  , ']'        , '=='         , NUMBER       , '>'        , 1        
DEL  , '('        , '['          , 'class'      ,            , 1        
DEL  , '('        , 'class'      , '*='         ,            , 1        
DEL  , '('        , '*='         , STRING       ,            , 1        
SUB  , STRING     , ']'          , ')'          , STRING     , 1        
SUB  , ':'        , 'None'       , ','          , NAME       , 1        
DEL  , NAME       , ','          , 'None'       ,            , 1        
DEL  , ']'        , 98           , 'for'        ,            , 1        
SUB  , NEWLINE    , 99           , '-'          , 98         , 1        
DEL  , NAME       , '='          , ')'          ,            , 1        
DEL  , STRING     , ']'          , 'in'         ,            , 1        
DEL  , 99         , '.'          , '.'          ,            , 1        
DEL  , 99         , '.'          , 'and'        ,            , 1        
DEL  , NUMBER     , ']'          , 'for'        ,            , 1        
INS  , ')'        ,              , '='          , ']'        , 1        
INS  , NAME       ,              , 'return'     , NEWLINE    , 1        
SUB  , '.'        , '('          , STRING       , NAME       , 1        
DEL  , NEWLINE    , '>'          , '('          ,            , 1        
SUB  , ','        , '('          , EOS          , NAME       , 1        
INS  , NAME       ,              , EOS          , ','        , 1        
SUB  , ':'        , '.'          , ':'          , ','        , 1        
SUB  , ')'        , ')'          , NEWLINE      , '}'        , 1        
DEL  , ')'        , NUMBER       , NUMBER       ,            , 1        
SUB  , '('        , UNKNOWN_CHAR , '%'          , NAME       , 1        
SUB  , 99         , NAME         , STRING       , 'raise'    , 1        
INS  , STRING     ,              , 'return'     , NEWLINE    , 1        
DEL  , ','        , 98           , 'True'       ,            , 1        
DEL  , STRING     , UNKNOWN_CHAR , 'not'        ,            , 1        
SUB  , 98         , 'continue'   , '='          , NAME       , 1        
SUB  , 'or'       , 'continue'   , '=='         , NAME       , 1        
INS  , BOS        ,              , 'for'        , 'try'      , 1        
INS  , 'try'      ,              , 'for'        , ':'        , 1        
INS  , NUMBER     ,              , NEWLINE      , 'else'     , 1        
SUB  , 'while'    , 'del'        , UNKNOWN_CHAR , NAME       , 1        
SUB  , NAME       , UNKNOWN_CHAR , '+'          , '!='       , 1        
DEL  , ')'        , '->'         , EOS          ,            , 1        
SUB  , ')'        , '...'        , NEWLINE      , ')'        , 1        
INS  , 99         ,              , EOS          , '...'      , 1        
DEL  , NAME       , STRING       , '='          ,            , 1        
DEL  , NAME       , '('          , 'None'       ,            , 1        
DEL  , NAME       , 'None'       , ','          ,            , 1        
SUB  , NAME       , '>'          , 'def'        , NEWLINE    , 1        
DEL  , ':'        , NEWLINE      , NUMBER       ,            , 1        
SUB  , NUMBER     , ')'          , ';'          , '+'        , 1        
DEL  , '+'        , ';'          , NAME         ,            , 1        
DEL  , '...'      , NUMBER       , ':'          ,            , 1        
INS  , 'True'     ,              , STRING       , 'else'     , 1        
DEL  , STRING     , 'if'         , 'False'      ,            , 1        
DEL  , STRING     , 'False'      , EOS          ,            , 1        
SUB  , STRING     , ';'          , 'if'         , NEWLINE    , 1        
SUB  , ')'        , '='          , NUMBER       , '<'        , 1        
DEL  , NAME       , '...'        , '|'          ,            , 1        
SUB  , STRING     , '}'          , ','          , ')'        , 1        
SUB  , '=='       , '/'          , UNKNOWN_CHAR , STRING     , 1        
SUB  , STRING     , '='          , 'True'       , ':'        , 1        
SUB  , '('        , '<'          , NAME         , 'lambda'   , 1        
INS  , '>'        ,              , ')'          , NUMBER     , 1        
DEL  , ')'        , NEWLINE      , '('          ,            , 1        
SUB  , '['        , '.'          , '.'          , STRING     , 1        
SUB  , ')'        , 'with'       , NAME         , 'as'       , 1        
SUB  , NAME       , ';'          , NAME         , ')'        , 1        
SUB  , NAME       , ';'          , EOS          , ')'        , 1        
SUB  , NAME       , '='          , '-'          , '=='       , 1        
DEL  , NEWLINE    , NEWLINE      , '['          ,            , 1        
INS  , '{'        ,              , STRING       , '{'        , 1        
SUB  , NAME       , ','          , STRING       , ':'        , 1        
SUB  , '('        , 'class'      , '['          , NAME       , 1        
INS  , ')'        ,              , 'return'     , ':'        , 1        
SUB  , STRING     , ':'          , NUMBER       , STRING     , 1        
DEL  , STRING     , '}'          , '*'          ,            , 1        
SUB  , NUMBER     , ','          , NAME         , ')'        , 1        
DEL  , '='        , NAME         , NUMBER       ,            , 1        
SUB  , NAME       , NAME         , NUMBER       , '='        , 1        
SUB  , ':'        , 'in'         , '('          , NAME       , 1        
DEL  , '.'        , NEWLINE      , 99           ,            , 1        
DEL  , '.'        , 99           , NAME         ,            , 1        
INS  , '+'        ,              , NEWLINE      , NAME       , 1        
DEL  , 98         , '('          , ')'          ,            , 1        
SUB  , NAME       , '}'          , EOS          , ']'        , 1        
DEL  , ','        , '...'        , NAME         ,            , 1        
DEL  , '...'      , NAME         , ','          ,            , 1        
SUB  , NAME       , '{'          , '}'          , '('        , 1        
SUB  , '('        , '}'          , EOS          , ')'        , 1        
SUB  , NAME       , '('          , 'None'       , '+='       , 1        
DEL  , '+='       , 'None'       , 'for'        ,            , 1        
DEL  , '+='       , 'for'        , NAME         ,            , 1        
SUB  , BOS        , UNKNOWN_CHAR , '%'          , STRING     , 1        
DEL  , NUMBER     , '%'          , NUMBER       ,            , 1        
INS  , NAME       ,              , '.'          , ','        , 1        
INS  , ','        ,              , '.'          , NAME       , 1        
INS  , NUMBER     ,              , '('          , ']'        , 1        
SUB  , NEWLINE    , '...'        , NEWLINE      , NAME       , 1        
SUB  , '('        , 'class'      , '.'          , NAME       , 1        
DEL  , 98         , 'import'     , NAME         ,            , 1        
INS  , ','        ,              , NAME         , 'in'       , 1        
SUB  , '='        , UNKNOWN_CHAR , 'is'         , STRING     , 1        
DEL  , NAME       , '&'          , '>>'         ,            , 1        
SUB  , STRING     , ','          , NAME         , '.'        , 1        
INS  , STRING     ,              , 'for'        , 'else'     , 1        
SUB  , NEWLINE    , '@'          , NAME         , 'with'     , 1        
DEL  , STRING     , ','          , '@'          ,            , 1        
DEL  , ','        , '@'          , NAME         ,            , 1        
DEL  , 'and'      , NUMBER       , NAME         ,            , 1        
SUB  , 99         , 'for'        , NAME         , NUMBER     , 1        
SUB  , ')'        , ';'          , NUMBER       , ','        , 1        
INS  , NAME       ,              , '['          , '>>'       , 1        
DEL  , NEWLINE    , '%'          , '%'          ,            , 1        
DEL  , NUMBER     , NUMBER       , '='          ,            , 1        
SUB  , NAME       , 'from'       , NAME         , 'for'      , 1        
DEL  , ']'        , NAME         , '...'        ,            , 1        
DEL  , NUMBER     , ']'          , EOS          ,            , 1        
INS  , 'else'     ,              , NAME         , '('        , 1        
SUB  , NAME       , ':'          , NUMBER       , ','        , 1        
INS  , NUMBER     ,              , 'for'        , ')'        , 1        
DEL  , '>'        , NEWLINE      , '['          ,            , 1        
SUB  , ','        , '<'          , NAME         , STRING     , 1        
DEL  , ']'        , '='          , NAME         ,            , 1        
SUB  , NAME       , NAME         , 'in'         , 'not'      , 1        
SUB  , NAME       , '('          , STRING       , ':'        , 1        
SUB  , ':'        , STRING       , ')'          , ']'        , 1        
SUB  , NEWLINE    , 99           , 'break'      , NAME       , 1        
SUB  , NAME       , 'as'         , '*'          , 'import'   , 1        
SUB  , NAME       , '.'          , '/'          , ')'        , 1        
SUB  , '&'        , NUMBER       , NAME         , '('        , 1        
SUB  , ')'        , ':'          , EOS          , '('        , 1        
INS  , '('        ,              , EOS          , STRING     , 1        
SUB  , NAME       , NAME         , EOS          , '%'        , 1        
INS  , '%'        ,              , EOS          , '('        , 1        
INS  , 'True'     ,              , ')'          , '}'        , 1        
SUB  , NAME       , ']'          , NAME         , ')'        , 1        
INS  , BOS        ,              , 'in'         , NAME       , 1        
SUB  , BOS        , '>>'         , '>'          , 'while'    , 1        
SUB  , 'while'    , '>'          , NAME         , NUMBER     , 1        
INS  , NUMBER     ,              , NAME         , ':'        , 1        
SUB  , STRING     , ')'          , '='          , ']'        , 1        
DEL  , NUMBER     , ':'          , '('          ,            , 1        
DEL  , ')'        , ','          , STRING       ,            , 1        
INS  , ']'        ,              , EOS          , 'else'     , 1        
INS  , 'else'     ,              , EOS          , '['        , 1        
SUB  , '.'        , '.'          , NEWLINE      , NAME       , 1        
DEL  , ','        , 'class'      , '='          ,            , 1        
INS  , STRING     ,              , 'for'        , ')'        , 1        
SUB  , '+'        , '+'          , EOS          , NUMBER     , 1        
DEL  , ')'        , '...'        , ')'          ,            , 1        
SUB  , NAME       , NAME         , '='          , NEWLINE    , 1        
DEL  , NEWLINE    , '='          , '['          ,            , 1        
INS  , BOS        ,              , NEWLINE      , 'try'      , 1        
INS  , 'try'      ,              , NEWLINE      , ':'        , 1        
INS  , STRING     ,              , '%'          , STRING     , 1        
INS  , NAME       ,              , '*'          , ','        , 1        
INS  , STRING     ,              , 'not'        , STRING     , 1        
DEL  , NUMBER     , 'else'       , ':'          ,            , 1        
INS  , 99         ,              , NAME         , 'else'     , 1        
INS  , 'else'     ,              , NAME         , ':'        , 1        
INS  , ')'        ,              , 'in'         , 'for'      , 1        
SUB  , '}'        , ']'          , EOS          , '}'        , 1        
DEL  , NEWLINE    , '//'         , 'or'         ,            , 1        
DEL  , '...'      , NAME         , NEWLINE      ,            , 1        
SUB  , 'def'      , '<'          , 'lambda'     , NAME       , 1        
DEL  , NAME       , 'lambda'     , '>'          ,            , 1        
DEL  , NAME       , '>'          , '('          ,            , 1        
SUB  , NAME       , STRING       , '.'          , '='        , 1        
INS  , ','        ,              , '['          , NAME       , 1        
DEL  , NAME       , NAME         , '&'          ,            , 1        
DEL  , STRING     , '('          , STRING       ,            , 1        
DEL  , STRING     , NAME         , 'not'        ,            , 1        
DEL  , '='        , '('          , 'lambda'     ,            , 1        
DEL  , ')'        , '['          , NUMBER       ,            , 1        
DEL  , ')'        , NUMBER       , ']'          ,            , 1        
SUB  , ','        , 'False'      , NEWLINE      , NAME       , 1        
DEL  , NAME       , 98           , 'if'         ,            , 1        
SUB  , 'or'       , 'not'        , NEWLINE      , NAME       , 1        
SUB  , 'while'    , 'continue'   , ':'          , NAME       , 1        
SUB  , 99         , 'continue'   , '='          , NAME       , 1        
SUB  , NUMBER     , NEWLINE      , '='          , ')'        , 1        
DEL  , '{'        , ','          , STRING       ,            , 1        
SUB  , NUMBER     , '='          , '<'          , '<='       , 1        
DEL  , '<='       , '<'          , NAME         ,            , 1        
SUB  , NAME       , '='          , '<'          , '<='       , 1        
DEL  , '<='       , '<'          , NUMBER       ,            , 1        
SUB  , NAME       , '='          , NUMBER       , '<'        , 1        
DEL  , BOS        , '>'          , 'try'        ,            , 1        
SUB  , STRING     , STRING       , NAME         , ':'        , 1        
DEL  , ']'        , '-'          , '->'         ,            , 1        
DEL  , ']'        , '->'         , EOS          ,            , 1        
SUB  , '('        , UNKNOWN_CHAR , NAME         , '~'        , 1        
INS  , ')'        ,              , '+'          , NEWLINE    , 1        
SUB  , ']'        , ')'          , EOS          , NEWLINE    , 1        
SUB  , ']'        , NEWLINE      , 'return'     , ':'        , 1        
SUB  , NAME       , '>'          , '['          , '='        , 1        
SUB  , NAME       , '='          , '{'          , '('        , 1        
INS  , '('        ,              , '{'          , ')'        , 1        
INS  , ')'        ,              , '{'          , ':'        , 1        
DEL  , NEWLINE    , NAME         , '('          ,            , 1        
DEL  , NEWLINE    , '('          , '['          ,            , 1        
SUB  , '/'        , '<'          , NAME         , '['        , 1        
SUB  , NAME       , '>'          , '/'          , ']'        , 1        
SUB  , '('        , UNKNOWN_CHAR , '('          , 'not'      , 1        
SUB  , ':'        , 'True'       , NEWLINE      , NAME       , 1        
SUB  , NUMBER     , NAME         , EOS          , NEWLINE    , 1        
DEL  , NAME       , 'None'       , ':'          ,            , 1        
SUB  , ']'        , ';'          , NEWLINE      , ')'        , 1        
DEL  , 'return'   , 'for'        , NAME         ,            , 1        
SUB  , ')'        , ')'          , '('          , ']'        , 1        
SUB  , ')'        , NEWLINE      , NAME         , '+'        , 1        
SUB  , NAME       , 'with'       , NAME         , ','        , 1        
INS  , '+'        ,              , '['          , NAME       , 1        
INS  , BOS        ,              , NAME         , 'not'      , 1        
DEL  , ')'        , UNKNOWN_CHAR , '}'          ,            , 1        
SUB  , '='        , UNKNOWN_CHAR , ','          , STRING     , 1        
INS  , 'else'     ,              , NEWLINE      , NUMBER     , 1        
DEL  , 98         , '['          , NAME         ,            , 1        
SUB  , NAME       , NAME         , ']'          , '('        , 1        
SUB  , '('        , ']'          , NEWLINE      , ')'        , 1        
SUB  , NAME       , STRING       , ')'          , '('        , 1        
INS  , NUMBER     ,              , ']'          , ':'        , 1        
DEL  , NAME       , STRING       , 'in'         ,            , 1        
SUB  , ','        , 'as'         , ')'          , NAME       , 1        
INS  , '['        ,              , EOS          , NAME       , 1        
DEL  , STRING     , ':'          , '-'          ,            , 1        
SUB  , 99         , '>>'         , '>'          , 'for'      , 1        
DEL  , 'for'      , '>'          , NAME         ,            , 1        
DEL  , NEWLINE    , 'global'     , NAME         ,            , 1        
SUB  , NEWLINE    , 99           , '>>'         , NAME       , 1        
DEL  , BOS        , '**'         , '('          ,            , 1        
SUB  , ','        , NAME         , ':'          , 'lambda'   , 1        
SUB  , BOS        , UNKNOWN_CHAR , NAME         , '['        , 1        
DEL  , ')'        , 'and'        , '.'          ,            , 1        
DEL  , ')'        , 'return'     , NEWLINE      ,            , 1        
DEL  , '~'        , UNKNOWN_CHAR , NAME         ,            , 1        
DEL  , NAME       , ':'          , '~'          ,            , 1        
DEL  , NAME       , '~'          , UNKNOWN_CHAR ,            , 1        
SUB  , ','        , 'or'         , '='          , NAME       , 1        
SUB  , ','        , 'or'         , ')'          , NAME       , 1        
SUB  , NAME       , '->'         , NAME         , '['        , 1        
INS  , NAME       ,              , '='          , ']'        , 1        
DEL  , ':'        , 98           , 'raise'      ,            , 1        
SUB  , 'in'       , 'from'       , '.'          , NAME       , 1        
SUB  , '('        , 'from'       , ')'          , NAME       , 1        
DEL  , ']'        , ':'          , '='          ,            , 1        
SUB  , 'as'       , NEWLINE      , EOS          , NAME       , 1        
SUB  , NEWLINE    , NAME         , '.'          , '+'        , 1        
DEL  , '+'        , '.'          , NAME         ,            , 1        
DEL  , '+'        , '('          , '['          ,            , 1        
SUB  , NAME       , 'from'       , NAME         , 'as'       , 1        
INS  , ','        ,              , '...'        , NEWLINE    , 1        
SUB  , '.'        , '*'          , EOS          , NAME       , 1        
INS  , 'except'   ,              , NAME         , ':'        , 1        
SUB  , STRING     , NAME         , '@'          , ')'        , 1        
SUB  , STRING     , NUMBER       , NAME         , ']'        , 1        
DEL  , '('        , NAME         , UNKNOWN_CHAR ,            , 1        
INS  , ')'        ,              , 'assert'     , ')'        , 1        
SUB  , NAME       , '*'          , STRING       , '('        , 1        
INS  , NAME       ,              , '**'         , ','        , 1        
DEL  , '<'        , '<'          , '('          ,            , 1        
INS  , ')'        ,              , '!='         , ')'        , 1        
SUB  , NAME       , NAME         , '['          , '('        , 1        
SUB  , ']'        , ','          , NAME         , '.'        , 1        
DEL  , '...'      , NAME         , 'with'       ,            , 1        
DEL  , '...'      , 'with'       , NAME         ,            , 1        
DEL  , '...'      , NAME         , 'and'        ,            , 1        
SUB  , 'None'     , ')'          , NEWLINE      , ':'        , 1        
DEL  , ')'        , ']'          , '.'          ,            , 1        
SUB  , '='        , '%'          , NAME         , STRING     , 1        
SUB  , NAME       , ']'          , ')'          , 'else'     , 1        
INS  , 'else'     ,              , ')'          , '('        , 1        
INS  , NAME       ,              , '('          , ')'        , 1        
SUB  , '('        , '.'          , ')'          , STRING     , 1        
DEL  , '('        , NAME         , '...'        ,            , 1        
DEL  , ':'        , 'continue'   , NEWLINE      ,            , 1        
DEL  , ')'        , 'break'      , NEWLINE      ,            , 1        
INS  , 'False'    ,              , ']'          , ')'        , 1        
SUB  , '('        , NEWLINE      , NAME         , '+'        , 1        
DEL  , '*'        , ')'          , '('          ,            , 1        
SUB  , NAME       , '.'          , NAME         , '='        , 1        
INS  , NAME       ,              , '('          , ']'        , 1        
INS  , ']'        ,              , '('          , '['        , 1        
INS  , '['        ,              , '('          , NAME       , 1        
INS  , ')'        ,              , NAME         , '+'        , 1        
INS  , ')'        ,              , '*'          , ')'        , 1        
INS  , NUMBER     ,              , 'return'     , NEWLINE    , 1        
SUB  , NAME       , STRING       , NEWLINE      , 'if'       , 1        
INS  , 'else'     ,              , NEWLINE      , '('        , 1        
SUB  , 99         , UNKNOWN_CHAR , NEWLINE      , NAME       , 1        
DEL  , ')'        , '...'        , '-'          ,            , 1        
DEL  , 99         , NAME         , '('          ,            , 1        
DEL  , 'None'     , ')'          , '}'          ,            , 1        
DEL  , ':'        , 'return'     , '('          ,            , 1        
SUB  , NAME       , ','          , STRING       , '='        , 1        
INS  , ')'        ,              , ')'          , 'else'     , 1        
DEL  , '}'        , NEWLINE      , ')'          ,            , 1        
SUB  , 99         , '>>'         , '['          , NAME       , 1        
INS  , '('        ,              , 'return'     , ')'        , 1        
SUB  , ']'        , ','          , NEWLINE      , ')'        , 1        
DEL  , ','        , ','          , EOS          ,            , 1        
DEL  , 'except'   , '<'          , NAME         ,            , 1        
DEL  , NAME       , 'class'      , NAME         ,            , 1        
SUB  , NAME       , '['          , ']'          , '.'        , 1        
DEL  , '['        , NUMBER       , '->'         ,            , 1        
DEL  , '['        , '->'         , STRING       ,            , 1        
DEL  , NUMBER     , ')'          , 'or'         ,            , 1        
SUB  , NAME       , NUMBER       , NAME         , '%'        , 1        
DEL  , 'class'    , NUMBER       , NAME         ,            , 1        
DEL  , '...'      , STRING       , ']'          ,            , 1        
INS  , NEWLINE    ,              , NAME         , 'nonlocal' , 1        
SUB  , NAME       , '.'          , '.'          , '['        , 1        
SUB  , '['        , '.'          , '}'          , NUMBER     , 1        
SUB  , NEWLINE    , '...'        , NEWLINE      , 'class'    , 1        
SUB  , STRING     , NUMBER       , NUMBER       , ')'        , 1        
DEL  , ')'        , NUMBER       , '.'          ,            , 1        
SUB  , '.'        , 'or'         , ','          , NAME       , 1        
DEL  , 98         , UNKNOWN_CHAR , 'if'         ,            , 1        
SUB  , 99         , 'else'       , NAME         , 'except'   , 1        
SUB  , ')'        , ':'          , NAME         , '/'        , 1        
SUB  , ')'        , ','          , NAME         , 'as'       , 1        
INS  , NAME       ,              , STRING       , '%'        , 1        
SUB  , NEWLINE    , NAME         , NAME         , 98         , 1        
SUB  , '...'      , '...'        , ']'          , '.'        , 1        
DEL  , '('        , '@'          , 'class'      ,            , 1        
DEL  , '('        , 'class'      , ')'          ,            , 1        
DEL  , '...'      , NAME         , EOS          ,            , 1        
INS  , 'None'     ,              , NAME         , 'and'      , 1        
SUB  , ']'        , '}'          , '=='         , ')'        , 1        
SUB  , NAME       , STRING       , '+'          , ','        , 1        
DEL  , '}'        , ')'          , 'for'        ,            , 1        
SUB  , ']'        , '+='         , STRING       , '+'        , 1        
INS  , NEWLINE    ,              , 'continue'   , 98         , 1        
SUB  , NAME       , UNKNOWN_CHAR , 'for'        , '('        , 1        
DEL  , ','        , '...'        , STRING       ,            , 1        
DEL  , NUMBER     , '<'          , NAME         ,            , 1        
DEL  , NUMBER     , '>'          , NEWLINE      ,            , 1        
SUB  , NUMBER     , NUMBER       , '+'          , '-'        , 1        
SUB  , NAME       , '.'          , NUMBER       , '>'        , 1        
SUB  , '.'        , STRING       , EOS          , NAME       , 1        
SUB  , NEWLINE    , 98           , NAME         , '-'        , 1        
DEL  , NAME       , NEWLINE      , '['          ,            , 1        
DEL  , NAME       , '['          , '...'        ,            , 1        
DEL  , NAME       , '...'        , 'if'         ,            , 1        
DEL  , NEWLINE    , 98           , 'True'       ,            , 1        
INS  , NEWLINE    ,              , NAME         , 'yield'    , 1        
INS  , ']'        ,              , '+'          , ')'        , 1        
SUB  , ')'        , NEWLINE      , NAME         , 'as'       , 1        
SUB  , ']'        , '.'          , NAME         , '['        , 1        
SUB  , NAME       , '('          , NAME         , '-'        , 1        
SUB  , ')'        , ')'          , ')'          , ']'        , 1        
SUB  , STRING     , UNKNOWN_CHAR , UNKNOWN_CHAR , ':'        , 1        
SUB  , ':'        , UNKNOWN_CHAR , UNKNOWN_CHAR , STRING     , 1        
DEL  , ']'        , ','          , NAME         ,            , 1        
SUB  , NAME       , '('          , '.'          , '+'        , 1        
SUB  , '+'        , '.'          , '*'          , STRING     , 1        
DEL  , '*'        , ')'          , NAME         ,            , 1        
INS  , 'return'   ,              , NAME         , '('        , 1        
SUB  , 'not'      , 'is'         , '.'          , NAME       , 1        
DEL  , 99         , 'else'       , 'if'         ,            , 1        
INS  , ']'        ,              , NAME         , '['        , 1        
DEL  , NAME       , ':'          , '-'          ,            , 1        
DEL  , NEWLINE    , 99           , '**'         ,            , 1        
INS  , ':'        ,              , '{'          , STRING     , 1        
INS  , STRING     ,              , '{'          , '}'        , 1        
INS  , '}'        ,              , '{'          , ')'        , 1        
INS  , ')'        ,              , '{'          , '('        , 1        
SUB  , '}'        , '}'          , '{'          , ','        , 1        
DEL  , NAME       , '.'          , ':'          ,            , 1        
DEL  , STRING     , '='          , ')'          ,            , 1        
INS  , '('        ,              , ')'          , '{'        , 1        
INS  , '{'        ,              , ')'          , '}'        , 1        
SUB  , 98         , ':'          , NEWLINE      , 'True'     , 1        
INS  , NEWLINE    ,              , NEWLINE      , 99         , 1        
DEL  , STRING     , UNKNOWN_CHAR , '/'          ,            , 1        
DEL  , NAME       , '['          , '<'          ,            , 1        
DEL  , NAME       , NAME         , 'while'      ,            , 1        
DEL  , NAME       , 'while'      , NEWLINE      ,            , 1        
INS  , STRING     ,              , '.'          , '*'        , 1        
DEL  , ']'        , ','          , '%'          ,            , 1        
SUB  , NEWLINE    , 'continue'   , '='          , NAME       , 1        
SUB  , BOS        , 'while'      , NAME         , 'with'     , 1        
INS  , NEWLINE    ,              , 'finally'    , 98         , 1        
INS  , 98         ,              , 'finally'    , '('        , 1        
INS  , '('        ,              , 'finally'    , ')'        , 1        
INS  , ')'        ,              , 'finally'    , NEWLINE    , 1        
SUB  , '['        , '.'          , '.'          , '...'      , 1        
DEL  , ')'        , '!='         , STRING       ,            , 1        
INS  , 'if'       ,              , '!='         , NAME       , 1        
DEL  , 'not'      , 'is'         , NAME         ,            , 1        
SUB  , 98         , 'while'      , 'True'       , 'try'      , 1        
DEL  , 'try'      , 'True'       , ':'          ,            , 1        
SUB  , BOS        , '>>'         , '>'          , NEWLINE    , 1        
DEL  , ')'        , '...'        , 'if'         ,            , 1        
INS  , '('        ,              , '^'          , STRING     , 1        
DEL  , '^'        , '['          , '^'          ,            , 1        
DEL  , NAME       , ']'          , '+'          ,            , 1        
INS  , STRING     ,              , NEWLINE      , 'in'       , 1        
INS  , 'in'       ,              , NEWLINE      , NUMBER     , 1        
DEL  , '}'        , NUMBER       , ')'          ,            , 1        
DEL  , ','        , ':'          , NEWLINE      ,            , 1        
DEL  , '='        , '='          , 'lambda'     ,            , 1        
DEL  , 'raise'    , '.'          , NAME         ,            , 1        
INS  , NAME       ,              , NUMBER       , '>'        , 1        
DEL  , '<='       , NAME         , '<='         ,            , 1        
DEL  , '<='       , '<='         , NUMBER       ,            , 1        
INS  , '>'        ,              , '<'          , NUMBER     , 1        
SUB  , NAME       , '<'          , STRING       , '('        , 1        
DEL  , ')'        , '->'         , '['          ,            , 1        
SUB  , '='        , NAME         , 'from'       , 'yield'    , 1        
SUB  , 'else'     , ':'          , EOS          , STRING     , 1        
SUB  , NAME       , ')'          , 'if'         , ':'        , 1        
SUB  , ':'        , 'if'         , NUMBER       , NAME       , 1        
DEL  , NAME       , NUMBER       , '<'          ,            , 1        
SUB  , ')'        , 'if'         , NUMBER       , 'for'      , 1        
INS  , 'for'      ,              , NUMBER       , NAME       , 1        
INS  , NAME       ,              , NUMBER       , 'in'       , 1        
DEL  , '=='       , UNKNOWN_CHAR , NAME         ,            , 1        
DEL  , '='        , NAME         , '{'          ,            , 1        
SUB  , ')'        , 'as'         , NAME         , '='        , 1        
DEL  , NAME       , 98           , 'else'       ,            , 1        
SUB  , 'import'   , '['          , NAME         , '('        , 1        
SUB  , ','        , ']'          , NEWLINE      , ')'        , 1        
SUB  , '='        , '...'        , '.'          , '('        , 1        
SUB  , '('        , '.'          , NEWLINE      , STRING     , 1        
SUB  , NUMBER     , NAME         , '('          , ']'        , 1        
SUB  , 98         , 'global'     , NEWLINE      , NAME       , 1        
DEL  , 'elif'     , '**'         , NAME         ,            , 1        
DEL  , ')'        , '**'         , '!='         ,            , 1        
SUB  , NAME       , 'else'       , STRING       , 'in'       , 1        
DEL  , '...'      , ':'          , '('          ,            , 1        
DEL  , ')'        , '**'         , '['          ,            , 1        
SUB  , NUMBER     , '//'         , NAME         , ':'        , 1        
DEL  , ']'        , '='          , '<'          ,            , 1        
DEL  , ')'        , '='          , EOS          ,            , 1        
DEL  , '('        , 'yield'      , '('          ,            , 1        
INS  , ':'        ,              , 'if'         , '('        , 1        
DEL  , 'else'     , NAME         , '('          ,            , 1        
DEL  , '('        , '//'         , '...'        ,            , 1        
SUB  , '='        , 'False'      , NEWLINE      , NUMBER     , 1        
INS  , NAME       ,              , '|'          , ')'        , 1        
INS  , 'None'     ,              , EOS          , ']'        , 1        
SUB  , NEWLINE    , 'elif'       , '('          , 'if'       , 1        
SUB  , BOS        , 'del'        , '='          , NAME       , 1        
SUB  , 98         , 'del'        , '.'          , NAME       , 1        
SUB  , '('        , 'del'        , ')'          , NAME       , 1        
SUB  , NAME       , NAME         , '('          , '+'        , 1        
SUB  , NEWLINE    , 99           , ']'          , NAME       , 1        
DEL  , NAME       , ';'          , '='          ,            , 1        
INS  , 'return'   ,              , NUMBER       , '('        , 1        
SUB  , STRING     , '/'          , NAME         , ')'        , 1        
INS  , ')'        ,              , NAME         , '='        , 1        
SUB  , NAME       , '/'          , NUMBER       , '('        , 1        
DEL  , NUMBER     , '/'          , ')'          ,            , 1        
INS  , ')'        ,              , '{'          , NEWLINE    , 1        
SUB  , 'return'   , STRING       , NEWLINE      , NAME       , 1        
DEL  , 'return'   , 'return'     , '('          ,            , 1        
INS  , ':'        ,              , NAME         , 'raise'    , 1        
SUB  , NAME       , '('          , NAME         , ')'        , 1        
INS  , 98         ,              , NAME         , 'while'    , 1        
DEL  , 99         , 98           , 'return'     ,            , 1        
DEL  , ':'        , NAME         , '['          ,            , 1        
SUB  , NAME       , NEWLINE      , NAME         , '+'        , 1        
DEL  , ','        , '.'          , NAME         ,            , 1        
INS  , '['        ,              , NAME         , '{'        , 1        
INS  , STRING     ,              , '+'          , ']'        , 1        
DEL  , 'def'      , NUMBER       , NAME         ,            , 1        
SUB  , '['        , NAME         , '+'          , '('        , 1        
SUB  , '('        , UNKNOWN_CHAR , EOS          , STRING     , 1        
SUB  , NEWLINE    , NAME         , STRING       , 'assert'   , 1        
SUB  , 'return'   , 'return'     , '('          , NAME       , 1        
DEL  , 98         , 'yield'      , NAME         ,            , 1        
SUB  , ')'        , ':'          , NEWLINE      , ')'        , 1        
DEL  , STRING     , ','          , ']'          ,            , 1        
INS  , '{'        ,              , NAME         , '('        , 1        
SUB  , NAME       , NAME         , '('          , 'if'       , 1        
DEL  , STRING     , UNKNOWN_CHAR , '('          ,            , 1        
DEL  , STRING     , '('          , '-'          ,            , 1        
DEL  , 99         , 'else'       , NEWLINE      ,            , 1        
DEL  , 99         , 98           , NAME         ,            , 1        
SUB  , '%'        , UNKNOWN_CHAR , NAME         , STRING     , 1        
DEL  , STRING     , NEWLINE      , ')'          ,            , 1        
DEL  , NEWLINE    , NAME         , 'is'         ,            , 1        
DEL  , NEWLINE    , 'is'         , 'not'        ,            , 1        
DEL  , 'None'     , ':'          , EOS          ,            , 1        
INS  , 'True'     ,              , STRING       , ','        , 1        
DEL  , NEWLINE    , '%'          , NUMBER       ,            , 1        
DEL  , '^'        , '^'          , '('          ,            , 1        
DEL  , '&'        , '&'          , STRING       ,            , 1        
SUB  , NAME       , '('          , NAME         , ':'        , 1        
DEL  , ':'        , NAME         , ')'          ,            , 1        
DEL  , ':'        , ')'          , '!='         ,            , 1        
DEL  , ':'        , '!='         , STRING       ,            , 1        
SUB  , ':'        , 'pass'       , '}'          , NAME       , 1        
SUB  , STRING     , ']'          , NEWLINE      , '}'        , 1        
INS  , BOS        ,              , '{'          , '('        , 1        
INS  , ')'        ,              , 'for'        , '}'        , 1        
INS  , ']'        ,              , '.'          , '['        , 1        
INS  , '['        ,              , '.'          , '['        , 1        
INS  , '['        ,              , '.'          , ']'        , 1        
SUB  , 'if'       , UNKNOWN_CHAR , UNKNOWN_CHAR , NAME       , 1        
SUB  , NAME       , UNKNOWN_CHAR , UNKNOWN_CHAR , '>'        , 1        
SUB  , '>'        , UNKNOWN_CHAR , ':'          , NUMBER     , 1        
DEL  , NEWLINE    , '='          , NEWLINE      ,            , 1        
SUB  , '='        , UNKNOWN_CHAR , '**'         , STRING     , 1        
DEL  , STRING     , '**'         , NAME         ,            , 1        
DEL  , STRING     , NAME         , '**'         ,            , 1        
INS  , NUMBER     ,              , EOS          , ':'        , 1        
INS  , '-'        ,              , ','          , NAME       , 1        
SUB  , ')'        , 'return'     , NEWLINE      , ')'        , 1        
DEL  , ')'        , NEWLINE      , 'return'     ,            , 1        
DEL  , ')'        , 'return'     , STRING       ,            , 1        
DEL  , NAME       , 'and'        , ':'          ,            , 1        
INS  , NUMBER     ,              , 'return'     , ':'        , 1        
INS  , 'as'       ,              , ':'          , NAME       , 1        
SUB  , '['        , UNKNOWN_CHAR , NAME         , '~'        , 1        
SUB  , ')'        , ']'          , NEWLINE      , '}'        , 1        
INS  , 'False'    ,              , 'for'        , NEWLINE    , 1        
SUB  , BOS        , 'if'         , 'None'       , NAME       , 1        
DEL  , NAME       , 'None'       , 'in'         ,            , 1        
SUB  , ')'        , 'from'       , NAME         , 'for'      , 1        
DEL  , NAME       , STRING       , 'for'        ,            , 1        
SUB  , NAME       , '+'          , STRING       , ','        , 1        
INS  , 'import'   ,              , 'as'         , NAME       , 1        
SUB  , 'in'       , 'as'         , ':'          , NAME       , 1        
SUB  , ','        , '.'          , '.'          , STRING     , 1        
DEL  , NAME       , 'None'       , NEWLINE      ,            , 1        
DEL  , NAME       , '-'          , ')'          ,            , 1        
DEL  , '}'        , ','          , '}'          ,            , 1        
INS  , ']'        ,              , EOS          , ','        , 1        
SUB  , '='        , UNKNOWN_CHAR , '['          , NAME       , 1        
INS  , ')'        ,              , NUMBER       , '*'        , 1        
SUB  , ')'        , NAME         , NUMBER       , '<'        , 1        
DEL  , ')'        , '>'          , STRING       ,            , 1        
SUB  , 98         , '//'         , NAME         , STRING     , 1        
SUB  , ']'        , '('          , EOS          , ')'        , 1        
SUB  , ','        , 'as'         , '.'          , NAME       , 1        
DEL  , NAME       , '.'          , 'is'         ,            , 1        
SUB  , '('        , NAME         , STRING       , ')'        , 1        
INS  , NAME       ,              , '{'          , ','        , 1        
INS  , '>'        ,              , NEWLINE      , NAME       , 1        
DEL  , 99         , '//'         , 'and'        ,            , 1        
INS  , NAME       ,              , ')'          , 'if'       , 1        
INS  , 'if'       ,              , ')'          , NAME       , 1        
SUB  , ','        , STRING       , ')'          , NAME       , 1        
SUB  , 98         , '>'          , NAME         , NEWLINE    , 1        
DEL  , ':'        , '-'          , NEWLINE      ,            , 1        
SUB  , ')'        , UNKNOWN_CHAR , NAME         , ')'        , 1        
SUB  , ')'        , NEWLINE      , NAME         , '('        , 1        
INS  , '...'      ,              , 'import'     , ']'        , 1        
INS  , ']'        ,              , 'import'     , NEWLINE    , 1        
DEL  , '('        , ')'          , 'for'        ,            , 1        
DEL  , '('        , 'for'        , NAME         ,            , 1        
SUB  , '+'        , 'lambda'     , '*'          , NAME       , 1        
DEL  , '.'        , NEWLINE      , 98           ,            , 1        
DEL  , '.'        , 98           , NAME         ,            , 1        
SUB  , STRING     , UNKNOWN_CHAR , STRING       , ':'        , 1        
DEL  , ']'        , 98           , STRING       ,            , 1        
DEL  , ']'        , STRING       , '.'          ,            , 1        
DEL  , 'import'   , '<'          , NAME         ,            , 1        
DEL  , ')'        , '-'          , EOS          ,            , 1        
DEL  , ')'        , NUMBER       , '<'          ,            , 1        
DEL  , ','        , ':'          , NAME         ,            , 1        
INS  , 98         ,              , NAME         , 'del'      , 1        
DEL  , '...'      , ':'          , EOS          ,            , 1        
DEL  , 98         , 98           , 'for'        ,            , 1        
SUB  , '&'        , '&'          , NAME         , '('        , 1        
SUB  , 'return'   , 'class'      , NEWLINE      , NAME       , 1        
SUB  , STRING     , '%'          , '('          , '.'        , 1        
SUB  , 'is'       , UNKNOWN_CHAR , '+'          , NAME       , 1        
SUB  , ')'        , 'else'       , 'True'       , '+'        , 1        
SUB  , '+'        , 'True'       , ']'          , NUMBER     , 1        
SUB  , ')'        , NEWLINE      , NAME         , ')'        , 1        
SUB  , ','        , 'pass'       , ','          , NAME       , 1        
SUB  , NAME       , UNKNOWN_CHAR , '+'          , '('        , 1        
INS  , '('        ,              , '+'          , NAME       , 1        
SUB  , NEWLINE    , '//'         , NUMBER       , '('        , 1        
INS  , NUMBER     ,              , EOS          , '('        , 1        
DEL  , 'in'       , NAME         , '('          ,            , 1        
DEL  , NAME       , 'in'         , 'not'        ,            , 1        
DEL  , ']'        , '+'          , NUMBER       ,            , 1        
DEL  , ']'        , NUMBER       , ')'          ,            , 1        
SUB  , ','        , '.'          , '.'          , NAME       , 1        
SUB  , NAME       , '.'          , '}'          , ':'        , 1        
DEL  , ']'        , '}'          , '.'          ,            , 1        
DEL  , 99         , '...'        , 'continue'   ,            , 1        
DEL  , 99         , 'continue'   , 'with'       ,            , 1        
DEL  , 99         , 'with'       , NAME         ,            , 1        
DEL  , BOS        , UNKNOWN_CHAR , '('          ,            , 1        
DEL  , ')'        , '('          , EOS          ,            , 1        
DEL  , NAME       , ']'          , NAME         ,            , 1        
DEL  , NUMBER     , '='          , NAME         ,            , 1        
INS  , BOS        ,              , 'else'       , 'if'       , 1        
INS  , 'if'       ,              , 'else'       , STRING     , 1        
INS  , STRING     ,              , 'else'       , ':'        , 1        
INS  , ':'        ,              , 'else'       , '...'      , 1        
INS  , '...'      ,              , 'else'       , NEWLINE    , 1        
DEL  , NUMBER     , ')'          , '+'          ,            , 1        
DEL  , '/'        , '('          , NAME         ,            , 1        
DEL  , '{'        , ':'          , STRING       ,            , 1        
DEL  , 'if'       , '**'         , NAME         ,            , 1        
DEL  , ']'        , '**'         , '.'          ,            , 1        
DEL  , 'yield'    , '**'         , NAME         ,            , 1        
INS  , 98         ,              , 'else'       , 'pass'     , 1        
INS  , 'pass'     ,              , 'else'       , NEWLINE    , 1        
SUB  , '('        , UNKNOWN_CHAR , ';'          , STRING     , 1        
DEL  , 99         , '>'          , 'for'        ,            , 1        
SUB  , 98         , NAME         , 'True'       , 'return'   , 1        
SUB  , '}'        , '.'          , '{'          , ','        , 1        
SUB  , BOS        , '>>'         , '>'          , 'assert'   , 1        
DEL  , 'assert'   , '>'          , NAME         ,            , 1        
SUB  , NAME       , '('          , NAME         , 'for'      , 1        
SUB  , NAME       , ','          , NAME         , 'in'       , 1        
SUB  , '('        , '['          , '('          , NAME       , 1        
INS  , NEWLINE    ,              , NAME         , 'assert'   , 1        
INS  , ')'        ,              , 'for'        , 'else'     , 1        
SUB  , ')'        , 'return'     , NAME         , ')'        , 1        
SUB  , NEWLINE    , 'lambda'     , '='          , NAME       , 1        
SUB  , ']'        , '['          , NAME         , '='        , 1        
SUB  , ')'        , NEWLINE      , NAME         , '='        , 1        
SUB  , NAME       , '='          , 'False'      , '=='       , 1        
DEL  , '/'        , UNKNOWN_CHAR , '+'          ,            , 1        
SUB  , NAME       , STRING       , ','          , '.'        , 1        
DEL  , 'lambda'   , '['          , NAME         ,            , 1        
INS  , ')'        ,              , NUMBER       , ')'        , 1        
INS  , ')'        ,              , NUMBER       , NEWLINE    , 1        
SUB  , NAME       , 'not'        , NUMBER       , '!='       , 1        
SUB  , STRING     , ')'          , NEWLINE      , ':'        , 1        
DEL  , NAME       , '('          , 'lambda'     ,            , 1        
DEL  , NAME       , 'lambda'     , NAME         ,            , 1        
SUB  , STRING     , ';'          , EOS          , ')'        , 1        
SUB  , '('        , 'global'     , '('          , NAME       , 1        
INS  , NUMBER     ,              , '{'          , ','        , 1        
DEL  , 'and'      , NAME         , '('          ,            , 1        
INS  , STRING     ,              , '+'          , ')'        , 1        
SUB  , ']'        , ']'          , EOS          , ')'        , 1        
DEL  , NAME       , 98           , '.'          ,            , 1        
SUB  , '('        , '['          , '^'          , STRING     , 1        
DEL  , '-'        , NUMBER       , ']'          ,            , 1        
DEL  , '-'        , ']'          , STRING       ,            , 1        
INS  , 98         ,              , 'if'         , 'continue' , 1        
INS  , 'continue' ,              , 'if'         , NEWLINE    , 1        
INS  , '['        ,              , NAME         , STRING     , 1        
INS  , ']'        ,              , NAME         , '('        , 1        
DEL  , ')'        , '|'          , ')'          ,            , 1        
SUB  , BOS        , 'if'         , NAME         , 'from'     , 1        
SUB  , NAME       , '=='         , STRING       , 'import'   , 1        
SUB  , 'import'   , STRING       , ':'          , NAME       , 1        
DEL  , '+='       , '('          , NAME         ,            , 1        
DEL  , NEWLINE    , NAME         , '{'          ,            , 1        
DEL  , 99         , NEWLINE      , '...'        ,            , 1        
INS  , NAME       ,              , NAME         , 'or'       , 1        
DEL  , NEWLINE    , '->'         , 'return'     ,            , 1        
DEL  , '...'      , NEWLINE      , 98           ,            , 1        
DEL  , '...'      , 98           , NAME         ,            , 1        
DEL  , '...'      , NAME         , '('          ,            , 1        
SUB  , STRING     , NAME         , UNKNOWN_CHAR , '['        , 1        
SUB  , '['        , UNKNOWN_CHAR , ']'          , STRING     , 1        
DEL  , 'del'      , 'del'        , NAME         ,            , 1        
DEL  , STRING     , ';'          , NAME         ,            , 1        
INS  , NAME       ,              , ','          , 'for'      , 1        
SUB  , NAME       , '{'          , NAME         , NEWLINE    , 1        
INS  , ','        ,              , ']'          , '}'        , 1        
INS  , BOS        ,              , '('          , '['        , 1        
SUB  , '='        , '['          , STRING       , '('        , 1        
SUB  , '...'      , '.'          , ']'          , ')'        , 1        
SUB  , NAME       , NEWLINE      , STRING       , ')'        , 1        
SUB  , NUMBER     , NUMBER       , ']'          , ':'        , 1        
DEL  , NEWLINE    , ':'          , 'with'       ,            , 1        
SUB  , '('        , '...'        , ')'          , '*'        , 1        
SUB  , STRING     , '}'          , EOS          , ','        , 1        
SUB  , 98         , 'and'        , '='          , NAME       , 1        
DEL  , '-'        , NEWLINE      , 98           ,            , 1        
DEL  , '-'        , 98           , NAME         ,            , 1        
DEL  , ','        , STRING       , 'if'         ,            , 1        
DEL  , ','        , 'if'         , NAME         ,            , 1        
DEL  , '=='       , STRING       , ':'          ,            , 1        
DEL  , '=='       , ':'          , NAME         ,            , 1        
SUB  , '{'        , UNKNOWN_CHAR , 'or'         , STRING     , 1        
DEL  , STRING     , 'or'         , ':'          ,            , 1        
INS  , 98         ,              , '.'          , NAME       , 1        
SUB  , '.'        , NEWLINE      , '.'          , NAME       , 1        
SUB  , NAME       , '=='         , NUMBER       , 'and'      , 1        
DEL  , 'and'      , NUMBER       , ':'          ,            , 1        
DEL  , 'and'      , ':'          , NEWLINE      ,            , 1        
DEL  , 'and'      , 'if'         , 'not'        ,            , 1        
SUB  , STRING     , ','          , NAME         , '}'        , 1        
DEL  , NUMBER     , '*'          , EOS          ,            , 1        
SUB  , STRING     , NUMBER       , UNKNOWN_CHAR , '.'        , 1        
DEL  , ')'        , ']'          , ','          ,            , 1        
INS  , BOS        ,              , STRING       , '('        , 1        
DEL  , ':'        , '^'          , NEWLINE      ,            , 1        
SUB  , 'return'   , '['          , EOS          , NUMBER     , 1        
INS  , ':'        ,              , 99           , NAME       , 1        
SUB  , NEWLINE    , 'global'     , '='          , NAME       , 1        
INS  , ':'        ,              , 'for'        , NAME       , 1        
DEL  , '='        , '{'          , '%'          ,            , 1        
DEL  , '='        , '%'          , NAME         ,            , 1        
SUB  , '%'        , '}'          , EOS          , NAME       , 1        
SUB  , '/'        , NAME         , ':'          , STRING     , 1        
DEL  , '|'        , 98           , '('          ,            , 1        
SUB  , NAME       , UNKNOWN_CHAR , ']'          , '.'        , 1        
INS  , 'for'      ,              , '('          , NAME       , 1        
SUB  , '('        , '*'          , ','          , STRING     , 1        
DEL  , '}'        , NEWLINE      , '>>'         ,            , 1        
DEL  , '}'        , '>>'         , '>'          ,            , 1        
INS  , ':'        ,              , EOS          , 'break'    , 1        
DEL  , '}'        , '='          , '%'          ,            , 1        
SUB  , '='        , NEWLINE      , NAME         , STRING     , 1        
DEL  , ']'        , '['          , ']'          ,            , 1        
DEL  , BOS        , '{'          , '%'          ,            , 1        
DEL  , BOS        , '%'          , 'from'       ,            , 1        
SUB  , 'from'     , STRING       , 'import'     , NAME       , 1        
DEL  , NAME       , '%'          , '}'          ,            , 1        
DEL  , NAME       , UNKNOWN_CHAR , '^'          ,            , 1        
DEL  , '^'        , UNKNOWN_CHAR , NAME         ,            , 1        
DEL  , NAME       , '='          , '.'          ,            , 1        
DEL  , NAME       , '.'          , '*'          ,            , 1        
DEL  , NAME       , 98           , '('          ,            , 1        
SUB  , NEWLINE    , 'if'         , STRING       , 98         , 1        
INS  , 98         ,              , STRING       , 'while'    , 1        
INS  , '**'       ,              , NUMBER       , '('        , 1        
SUB  , STRING     , '...'        , ']'          , STRING     , 1        
DEL  , ':'        , '('          , NAME         ,            , 1        
DEL  , NAME       , '...'        , '&'          ,            , 1        
DEL  , NAME       , '&'          , ','          ,            , 1        
SUB  , '=='       , UNKNOWN_CHAR , NUMBER       , STRING     , 1        
INS  , ']'        ,              , ')'          , 'in'       , 1        
SUB  , STRING     , UNKNOWN_CHAR , NAME         , '.'        , 1        
INS  , ')'        ,              , NEWLINE      , '+'        , 1        
DEL  , NEWLINE    , '=='         , '>'          ,            , 1        
SUB  , 98         , 'is'         , 'not'        , 'if'       , 1        
SUB  , 98         , NAME         , 'not'        , 'if'       , 1        
INS  , NAME       ,              , STRING       , '>>'       , 1        
INS  , ')'        ,              , '|'          , ')'        , 1        
SUB  , 99         , 'raise'      , NEWLINE      , NAME       , 1        
SUB  , '='        , '.'          , '.'          , '...'      , 1        
INS  , NAME       ,              , 'not'        , 'or'       , 1        
INS  , BOS        ,              , 'for'        , '['        , 1        
SUB  , STRING     , ':'          , EOS          , ']'        , 1        
SUB  , '='        , STRING       , '*'          , '('        , 1        
INS  , '('        ,              , '*'          , ')'        , 1        
INS  , ')'        ,              , STRING       , ')'        , 1        
INS  , NUMBER     ,              , 'return'     , ')'        , 1        
INS  , ':'        ,              , EOS          , 'yield'    , 1        
DEL  , ')'        , ':'          , 'pass'       ,            , 1        
DEL  , ')'        , 'pass'       , NEWLINE      ,            , 1        
DEL  , NAME       , '+'          , STRING       ,            , 1        
DEL  , NEWLINE    , '/'          , '*'          ,            , 1        
DEL  , '*'        , '/'          , NEWLINE      ,            , 1        
DEL  , 98         , 98           , 'def'        ,            , 1        
SUB  , NAME       , '*='         , NAME         , '*'        , 1        
DEL  , NAME       , '...'        , '...'        ,            , 1        
INS  , '('        ,              , NAME         , '*'        , 1        
DEL  , 99         , 'continue'   , NEWLINE      ,            , 1        
INS  , '>'        ,              , NUMBER       , NAME       , 1        
INS  , NAME       ,              , NUMBER       , '['        , 1        
INS  , ':'        ,              , NEWLINE      , ']'        , 1        
DEL  , NUMBER     , NUMBER       , '%'          ,            , 1        
INS  , 98         ,              , 'return'     , '...'      , 1        
INS  , '...'      ,              , 'return'     , NEWLINE    , 1        
INS  , 98         ,              , NAME         , 'pass'     , 1        
INS  , 'pass'     ,              , NAME         , NEWLINE    , 1        
SUB  , NEWLINE    , 99           , '.'          , 98         , 1        
DEL  , '-'        , '('          , '-'          ,            , 1        
DEL  , ':'        , '('          , ')'          ,            , 1        
SUB  , NAME       , UNKNOWN_CHAR , NAME         , 'if'       , 1        
INS  , 98         ,              , 'for'        , '['        , 1        
SUB  , STRING     , ','          , NAME         , ']'        , 1        
DEL  , NAME       , 'for'        , NUMBER       ,            , 1        
DEL  , 98         , NEWLINE      , 99           ,            , 1        
DEL  , 98         , 99           , '@'          ,            , 1        
INS  , ':'        ,              , 'del'        , NEWLINE    , 1        
INS  , NEWLINE    ,              , 'del'        , 98         , 1        
DEL  , '='        , NAME         , '.'          ,            , 1        
DEL  , ')'        , NEWLINE      , '='          ,            , 1        
DEL  , '='        , '='          , STRING       ,            , 1        
SUB  , NUMBER     , 'or'         , NAME         , 'else'     , 1        
SUB  , STRING     , '/'          , '>'          , ')'        , 1        
SUB  , '='        , NUMBER       , NAME         , '-'        , 1        
SUB  , NAME       , '('          , 'lambda'     , '&'        , 1        
DEL  , '&'        , 'lambda'     , NAME         ,            , 1        
DEL  , '<='       , '='          , NUMBER       ,            , 1        
SUB  , ':'        , NAME         , '('          , '['        , 1        
SUB  , NAME       , '->'         , NAME         , ','        , 1        
SUB  , ','        , '*'          , NAME         , '['        , 1        
SUB  , NAME       , '*'          , ')'          , ']'        , 1        
DEL  , 98         , '@'          , NAME         ,            , 1        
INS  , 98         ,              , NAME         , STRING     , 1        
DEL  , 98         , 'try'        , ':'          ,            , 1        
DEL  , 98         , 98           , 'if'         ,            , 1        
SUB  , STRING     , ']'          , ','          , STRING     , 1        
INS  , NAME       ,              , 'lambda'     , '('        , 1        
SUB  , NAME       , '+'          , NAME         , ','        , 1        
SUB  , STRING     , ';'          , NEWLINE      , ':'        , 1        
DEL  , 99         , 99           , ')'          ,            , 1        
DEL  , 'is'       , UNKNOWN_CHAR , '+'          ,            , 1        
SUB  , NAME       , '('          , '('          , '='        , 1        
INS  , 'in'       ,              , '['          , '('        , 1        
SUB  , ')'        , NEWLINE      , 98           , ')'        , 1        
SUB  , 98         , NAME         , ':'          , 'try'      , 1        
SUB  , ')'        , NEWLINE      , STRING       , ')'        , 1        
DEL  , 'as'       , '('          , NAME         ,            , 1        
SUB  , BOS        , '>>'         , '>'          , 'with'     , 1        
SUB  , 'with'     , '>'          , '['          , '('        , 1        
SUB  , '{'        , '%'          , NAME         , '{'        , 1        
DEL  , ')'        , '%'          , '}'          ,            , 1        
DEL  , NEWLINE    , 'raise'      , NAME         ,            , 1        
DEL  , NAME       , STRING       , 'with'       ,            , 1        
DEL  , NAME       , 'with'       , NEWLINE      ,            , 1        
SUB  , NEWLINE    , 99           , '~'          , 98         , 1        
DEL  , 98         , '~'          , NEWLINE      ,            , 1        
SUB  , NEWLINE    , '~'          , EOS          , 99         , 1        
INS  , NAME       ,              , 'is'         , ')'        , 1        
DEL  , NAME       , ')'          , '.'          ,            , 1        
INS  , 'if'       ,              , '<'          , NAME       , 1        
SUB  , NAME       , '+='         , NAME         , '='        , 1        
SUB  , '('        , UNKNOWN_CHAR , '//'         , STRING     , 1        
DEL  , NAME       , 'else'       , '.'          ,            , 1        
SUB  , '['        , '.'          , '.'          , NUMBER     , 1        
INS  , STRING     ,              , '*'          , ')'        , 1        
DEL  , ','        , '='          , 'True'       ,            , 1        
DEL  , ','        , 'True'       , ')'          ,            , 1        
DEL  , 'or'       , '('          , NAME         ,            , 1        
DEL  , 99         , 99           , '('          ,            , 1        
DEL  , 99         , '('          , EOS          ,            , 1        
INS  , NAME       ,              , NAME         , '!='       , 1        
SUB  , NUMBER     , NEWLINE      , 98           , ')'        , 1        
DEL  , NAME       , '}'          , '}'          ,            , 1        
DEL  , NAME       , '}'          , '{'          ,            , 1        
DEL  , NAME       , '{'          , '{'          ,            , 1        
DEL  , BOS        , '>'          , 'assert'     ,            , 1        
DEL  , NEWLINE    , '>'          , 'assert'     ,            , 1        
DEL  , NEWLINE    , 'try'        , NAME         ,            , 1        
SUB  , 98         , '('          , ')'          , 'pass'     , 1        
DEL  , 'pass'     , ')'          , NEWLINE      ,            , 1        
SUB  , ':'        , 'pass'       , ')'          , 'None'     , 1        
DEL  , BOS        , '**'         , UNKNOWN_CHAR ,            , 1        
DEL  , '*'        , UNKNOWN_CHAR , '**'         ,            , 1        
DEL  , '*'        , '**'         , EOS          ,            , 1        
DEL  , NEWLINE    , '//'         , 'return'     ,            , 1        
SUB  , '('        , '...'        , ','          , NAME       , 1        
INS  , 'while'    ,              , NAME         , 'not'      , 1        
DEL  , NAME       , '&'          , ':'          ,            , 1        
DEL  , NAME       , '<'          , NEWLINE      ,            , 1        
SUB  , '('        , UNKNOWN_CHAR , 'if'         , STRING     , 1        
DEL  , 'else'     , 'or'         , STRING       ,            , 1        
DEL  , '*'        , NAME         , STRING       ,            , 1        
SUB  , STRING     , '%'          , NAME         , STRING     , 1        
DEL  , ':'        , NAME         , ','          ,            , 1        
SUB  , ','        , '**'         , NAME         , '*'        , 1        
SUB  , NAME       , '...'        , ')'          , '='        , 1        
SUB  , NEWLINE    , 'for'        , NAME         , 98         , 1        
SUB  , NAME       , NAME         , NEWLINE      , '['        , 1        
INS  , '['        ,              , '!='         , NAME       , 1        
INS  , NAME       ,              , STRING       , '.'        , 1        
INS  , '.'        ,              , STRING       , NAME       , 1        
SUB  , ':'        , 'return'     , UNKNOWN_CHAR , STRING     , 1        
DEL  , '/'        , 'from'       , '-'          ,            , 1        
INS  , BOS        ,              , '('          , NAME       , 1        
SUB  , NAME       , '.'          , NAME         , '}'        , 1        
DEL  , '}'        , NAME         , '('          ,            , 1        
DEL  , '}'        , '('          , ')'          ,            , 1        
DEL  , '}'        , ')'          , ']'          ,            , 1        
DEL  , '}'        , ']'          , NEWLINE      ,            , 1        
SUB  , NAME       , UNKNOWN_CHAR , EOS          , ','        , 1        
INS  , ','        ,              , EOS          , NAME       , 1        
SUB  , NAME       , NAME         , '+'          , '='        , 1        
SUB  , ','        , STRING       , ':'          , NAME       , 1        
DEL  , '~'        , '/'          , '.'          ,            , 1        
DEL  , '~'        , '.'          , NAME         ,            , 1        
DEL  , ')'        , '>>'         , STRING       ,            , 1        
SUB  , NAME       , NUMBER       , '='          , '!='       , 1        
DEL  , '%'        , '%'          , NUMBER       ,            , 1        
DEL  , '('        , 98           , NAME         ,            , 1        
SUB  , 99         , '...'        , NAME         , 'if'       , 1        
SUB  , NAME       , '='          , UNKNOWN_CHAR , '!='       , 1        
DEL  , '!='       , UNKNOWN_CHAR , NAME         ,            , 1        
INS  , '='        ,              , 'lambda'     , '('        , 1        
DEL  , ']'        , 98           , 'if'         ,            , 1        
SUB  , 'None'     , NEWLINE      , 99           , ')'        , 1        
DEL  , NUMBER     , 'as'         , NAME         ,            , 1        
SUB  , '*'        , 'in'         , ')'          , NAME       , 1        
DEL  , NAME       , '>'          , 'import'     ,            , 1        
INS  , ')'        ,              , EOS          , 'else'     , 1        
INS  , 'else'     ,              , EOS          , NAME       , 1        
SUB  , 'if'       , 'def'        , ':'          , NAME       , 1        
SUB  , 'or'       , 'def'        , ')'          , NAME       , 1        
INS  , ':'        ,              , EOS          , 'None'     , 1        
SUB  , '=='       , 'del'        , ':'          , NAME       , 1        
INS  , ')'        ,              , EOS          , '('        , 1        
DEL  , BOS        , NAME         , NUMBER       ,            , 1        
DEL  , ')'        , '&'          , ']'          ,            , 1        
SUB  , NAME       , NAME         , EOS          , '+'        , 1        
INS  , '>'        ,              , ')'          , STRING     , 1        
DEL  , NUMBER     , '+'          , EOS          ,            , 1        
DEL  , NAME       , ';'          , 'or'         ,            , 1        
DEL  , 'or'       , NUMBER       , NAME         ,            , 1        
DEL  , 'import'   , '/'          , NAME         ,            , 1        
DEL  , ']'        , ']'          , 'for'        ,            , 1        
INS  , NAME       ,              , 'in'         , '('        , 1        
INS  , '('        ,              , 'in'         , ')'        , 1        
SUB  , STRING     , ')'          , EOS          , '}'        , 1        
DEL  , NUMBER     , '='          , 'False'      ,            , 1        
DEL  , NUMBER     , 'False'      , ')'          ,            , 1        
DEL  , 'in'       , '<<'         , NAME         ,            , 1        
DEL  , NAME       , '>>'         , ':'          ,            , 1        
DEL  , '['        , NUMBER       , NAME         ,            , 1        
SUB  , NAME       , '/'          , '*'          , NEWLINE    , 1        
DEL  , NEWLINE    , '*'          , '('          ,            , 1        
DEL  , NEWLINE    , '('          , 'if'         ,            , 1        
SUB  , BOS        , 'elif'       , NAME         , 'for'      , 1        
DEL  , 99         , ':'          , '-'          ,            , 1        
DEL  , 99         , '-'          , ')'          ,            , 1        
SUB  , '{'        , '['          , NAME         , '{'        , 1        
DEL  , '&'        , NEWLINE      , '('          ,            , 1        
SUB  , 'else'     , UNKNOWN_CHAR , NAME         , STRING     , 1        
DEL  , '|'        , '|'          , NUMBER       ,            , 1        
DEL  , ']'        , '='          , NUMBER       ,            , 1        
SUB  , BOS        , '**'         , UNKNOWN_CHAR , NAME       , 1        
SUB  , NAME       , UNKNOWN_CHAR , STRING       , '='        , 1        
DEL  , NEWLINE    , UNKNOWN_CHAR , '**'         ,            , 1        
DEL  , ';'        , NAME         , NAME         ,            , 1        
DEL  , ';'        , NAME         , EOS          ,            , 1        
SUB  , ';'        , '//'         , NAME         , NEWLINE    , 1        
SUB  , BOS        , '...'        , NEWLINE      , 'for'      , 1        
SUB  , 'for'      , NEWLINE      , 'else'       , NAME       , 1        
SUB  , NAME       , 'else'       , ':'          , 'in'       , 1        
INS  , ']'        ,              , 'if'         , 'for'      , 1        
INS  , NEWLINE    ,              , 'elif'       , 98         , 1        
INS  , 98         ,              , 'elif'       , '('        , 1        
INS  , '('        ,              , 'elif'       , ')'        , 1        
INS  , ')'        ,              , 'elif'       , NEWLINE    , 1        
SUB  , 'lambda'   , 'from'       , ','          , NAME       , 1        
SUB  , NEWLINE    , 'while'      , 'True'       , NAME       , 1        
SUB  , NAME       , 'True'       , ':'          , '.'        , 1        
SUB  , '.'        , ':'          , EOS          , NAME       , 1        
INS  , 98         ,              , 'else'       , 'break'    , 1        
INS  , 'break'    ,              , 'else'       , NEWLINE    , 1        
SUB  , STRING     , NAME         , '>'          , ')'        , 1        
INS  , NEWLINE    ,              , 'def'        , 'async'    , 1        
DEL  , ')'        , ':'          , NAME         ,            , 1        
INS  , ','        ,              , STRING       , NEWLINE    , 1        
INS  , ','        ,              , ']'          , '['        , 1        
INS  , NAME       ,              , 'in'         , '='        , 1        
INS  , '='        ,              , 'in'         , '['        , 1        
INS  , '['        ,              , 'in'         , NAME       , 1        
SUB  , NAME       , NEWLINE      , NAME         , '=='       , 1        
SUB  , NAME       , NAME         , EOS          , ')'        , 1        
DEL  , ')'        , 98           , '&'          ,            , 1        
SUB  , ']'        , UNKNOWN_CHAR , NEWLINE      , '='        , 1        
SUB  , NAME       , '('          , STRING       , ']'        , 1        
INS  , ']'        ,              , STRING       , '='        , 1        
SUB  , '-'        , 'as'         , '.'          , NAME       , 1        
DEL  , ')'        , 'return'     , NAME         ,            , 1        
DEL  , NAME       , ']'          , '*'          ,            , 1        
INS  , NAME       ,              , 'not'        , 'is'       , 1        
INS  , '...'      ,              , NAME         , ']'        , 1        
SUB  , NAME       , ','          , UNKNOWN_CHAR , NEWLINE    , 1        
INS  , NAME       ,              , ','          , '='        , 1        
INS  , '='        ,              , ','          , NAME       , 1        
DEL  , STRING     , '...'        , UNKNOWN_CHAR ,            , 1        
DEL  , ','        , '...'        , ','          ,            , 1        
SUB  , NEWLINE    , NAME         , STRING       , 'yield'    , 1        
SUB  , ']'        , '{'          , NAME         , '['        , 1        
DEL  , ')'        , UNKNOWN_CHAR , ']'          ,            , 1        
SUB  , NAME       , ')'          , 'for'        , ']'        , 1        
SUB  , NAME       , ')'          , STRING       , '('        , 1        
DEL  , ')'        , ':'          , ']'          ,            , 1        
DEL  , NAME       , NUMBER       , '}'          ,            , 1        
DEL  , NAME       , '}'          , NAME         ,            , 1        
SUB  , '!='       , UNKNOWN_CHAR , UNKNOWN_CHAR , STRING     , 1        
DEL  , '='        , 'True'       , ')'          ,            , 1        
DEL  , '='        , ')'          , NAME         ,            , 1        
DEL  , STRING     , NAME         , '~'          ,            , 1        
DEL  , STRING     , '~'          , NAME         ,            , 1        
SUB  , 'if'       , 'def'        , '.'          , NAME       , 1        
DEL  , STRING     , NAME         , '=='         ,            , 1        
SUB  , NAME       , ']'          , EOS          , ')'        , 1        
INS  , '('        ,              , ','          , 'None'     , 1        
SUB  , NAME       , ','          , NAME         , ']'        , 1        
INS  , '('        ,              , EOS          , '('        , 1        
SUB  , '('        , NAME         , ','          , ')'        , 1        
SUB  , ','        , UNKNOWN_CHAR , NUMBER       , '('        , 1        
SUB  , NAME       , NAME         , UNKNOWN_CHAR , ')'        , 1        
SUB  , ')'        , UNKNOWN_CHAR , UNKNOWN_CHAR , NEWLINE    , 1        
SUB  , NEWLINE    , '.'          , '.'          , 98         , 1        
SUB  , 98         , '.'          , '.'          , NEWLINE    , 1        
SUB  , NEWLINE    , '.'          , EOS          , 99         , 1        
INS  , 'or'       ,              , STRING       , '('        , 1        
SUB  , STRING     , ':'          , EOS          , ')'        , 1        
DEL  , ':'        , 'return'     , NAME         ,            , 1        
SUB  , ')'        , ','          , STRING       , ')'        , 1        
INS  , NAME       ,              , ')'          , 'else'     , 1        
INS  , BOS        ,              , '.'          , '('        , 1        
INS  , NAME       ,              , 'class'      , NEWLINE    , 1        
SUB  , ':'        , ')'          , EOS          , ']'        , 1        
SUB  , NAME       , '='          , 'True'       , '=='       , 1        
INS  , ']'        ,              , 'for'        , 'else'     , 1        
INS  , 'else'     ,              , 'for'        , 'None'     , 1        
DEL  , '//'       , NAME         , ':'          ,            , 1        
DEL  , '//'       , ':'          , NUMBER       ,            , 1        
SUB  , BOS        , 'and'        , '('          , NAME       , 1        
DEL  , 99         , 99           , ':'          ,            , 1        
DEL  , 99         , ':'          , ')'          ,            , 1        
INS  , ':'        ,              , NEWLINE      , '['        , 1        
DEL  , BOS        , ';'          , NAME         ,            , 1        
DEL  , ')'        , '+'          , '.'          ,            , 1        
SUB  , '!='       , 'None'       , ')'          , NAME       , 1        
SUB  , STRING     , ','          , NEWLINE      , ')'        , 1        
INS  , NUMBER     ,              , '+'          , ')'        , 1        
DEL  , 'return'   , UNKNOWN_CHAR , EOS          ,            , 1        
INS  , 98         ,              , 'if'         , '('        , 1        
SUB  , STRING     , ')'          , NEWLINE      , ','        , 1        
SUB  , '.'        , 'in'         , '('          , NAME       , 1        
INS  , NAME       ,              , ','          , 'in'       , 1        
INS  , 'in'       ,              , ','          , '('        , 1        
INS  , ')'        ,              , ','          , ':'        , 1        
DEL  , '('        , NAME         , '.'          ,            , 1        
DEL  , '['        , '**'         , '*'          ,            , 1        
DEL  , '['        , '*'          , NAME         ,            , 1        
SUB  , NAME       , '**'         , '*'          , '('        , 1        
SUB  , '('        , '*'          , 'for'        , NAME       , 1        
DEL  , NEWLINE    , NAME         , 'False'      ,            , 1        
DEL  , NEWLINE    , 'False'      , NEWLINE      ,            , 1        
SUB  , '='        , 'continue'   , '('          , NAME       , 1        
DEL  , '/'        , '%'          , NAME         ,            , 1        
SUB  , NAME       , '['          , STRING       , '('        , 1        
DEL  , 'try'      , NAME         , '['          ,            , 1        
DEL  , 'try'      , '['          , NAME         ,            , 1        
DEL  , 'try'      , NAME         , ']'          ,            , 1        
DEL  , 'try'      , ']'          , ':'          ,            , 1        
DEL  , ','        , 'pass'       , ')'          ,            , 1        
DEL  , BOS        , '...'        , NAME         ,            , 1        
SUB  , NAME       , ']'          , ','          , '}'        , 1        
SUB  , ','        , ')'          , EOS          , ']'        , 1        
SUB  , 98         , 'return'     , '<'          , NAME       , 1        
INS  , '<'        ,              , NAME         , '['        , 1        
SUB  , NAME       , 'from'       , NAME         , 'in'       , 1        
SUB  , NAME       , '>'          , NEWLINE      , ']'        , 1        
SUB  , ':'        , '['          , NAME         , '{'        , 1        
SUB  , ']'        , ']'          , 'for'        , '}'        , 1        
SUB  , NEWLINE    , 'break'      , NEWLINE      , 98         , 1        
SUB  , BOS        , 'except'     , NAME         , 'from'     , 1        
INS  , NAME       ,              , ':'          , NEWLINE    , 1        
INS  , NEWLINE    ,              , ':'          , 'try'      , 1        
DEL  , NUMBER     , '('          , 'in'         ,            , 1        
DEL  , NUMBER     , 'in'         , NAME         ,            , 1        
DEL  , '-'        , '->'         , NEWLINE      ,            , 1        
DEL  , ']'        , STRING       , ')'          ,            , 1        
SUB  , NAME       , '}'          , ':'          , ')'        , 1        
DEL  , '['        , 'for'        , NAME         ,            , 1        
SUB  , 98         , 'raise'      , ':'          , NAME       , 1        
INS  , ')'        ,              , '+'          , ']'        , 1        
DEL  , ','        , 99           , NAME         ,            , 1        
INS  , NAME       ,              , 'as'         , ')'        , 1        
SUB  , 99         , 'pass'       , NEWLINE      , NAME       , 1        
DEL  , '='        , '/'          , NEWLINE      ,            , 1        
INS  , '}'        ,              , '...'        , ')'        , 1        
SUB  , ']'        , '['          , ']'          , '.'        , 1        
INS  , '('        ,              , 'for'        , 'True'     , 1        
INS  , 'not'      ,              , NAME         , '('        , 1        
SUB  , ')'        , '.'          , NEWLINE      , ')'        , 1        
DEL  , 99         , 99           , NEWLINE      ,            , 1        
SUB  , NAME       , '('          , 'None'       , '['        , 1        
INS  , 'async'    ,              , NAME         , 'def'      , 1        
INS  , ')'        ,              , EOS          , '+'        , 1        
DEL  , NAME       , '.'          , 'not'        ,            , 1        
DEL  , NEWLINE    , ':'          , 'from'       ,            , 1        
SUB  , 'in'       , 'as'         , 'if'         , NAME       , 1        
SUB  , 'as'       , 'as'         , EOS          , NAME       , 1        
SUB  , 'in'       , '('          , STRING       , '['        , 1        
SUB  , STRING     , ','          , ')'          , ']'        , 1        
SUB  , ']'        , ')'          , NEWLINE      , ':'        , 1        
DEL  , NAME       , 'for'        , 'in'         ,            , 1        
DEL  , '=='       , 'not'        , NAME         ,            , 1        
DEL  , STRING     , '['          , '^'          ,            , 1        
DEL  , NUMBER     , ']'          , '+'          ,            , 1        
DEL  , NAME       , '+='         , NUMBER       ,            , 1        
SUB  , '('        , NAME         , ':'          , ')'        , 1        
DEL  , ')'        , ':'          , NUMBER       ,            , 1        
DEL  , NAME       , '='          , '/'          ,            , 1        
DEL  , STRING     , 98           , '%'          ,            , 1        
SUB  , STRING     , STRING       , NAME         , ','        , 1        
SUB  , NAME       , UNKNOWN_CHAR , '%'          , '('        , 1        
INS  , '('        ,              , '%'          , STRING     , 1        
DEL  , NEWLINE    , 'pass'       , NEWLINE      ,            , 1        
SUB  , 'import'   , STRING       , EOS          , NAME       , 1        
SUB  , NAME       , '='          , '>'          , '>='       , 1        
DEL  , '>='       , '>'          , NUMBER       ,            , 1        
SUB  , ']'        , NEWLINE      , 'yield'      , ':'        , 1        
SUB  , NAME       , 'import'     , NAME         , NEWLINE    , 1        
DEL  , STRING     , 'is'         , 'not'        ,            , 1        
SUB  , STRING     , '='          , '>'          , ':'        , 1        
DEL  , ':'        , '>'          , '{'          ,            , 1        
DEL  , NAME       , '-'          , 'import'     ,            , 1        
DEL  , STRING     , '%'          , '<'          ,            , 1        
DEL  , NAME       , '('          , '}'          ,            , 1        
INS  , '>'        ,              , '.'          , NAME       , 1        
DEL  , NAME       , '('          , NEWLINE      ,            , 1        
SUB  , 'else'     , UNKNOWN_CHAR , NUMBER       , STRING     , 1        
DEL  , STRING     , NUMBER       , '+'          ,            , 1        
INS  , '}'        ,              , '['          , ')'        , 1        
DEL  , 'if'       , '>'          , NAME         ,            , 1        
DEL  , '+'        , NAME         , ')'          ,            , 1        
DEL  , '+'        , ')'          , STRING       ,            , 1        
DEL  , ':'        , NEWLINE      , 'import'     ,            , 1        
SUB  , NAME       , 98           , '['          , '('        , 1        
SUB  , ']'        , NEWLINE      , 99           , ')'        , 1        
DEL  , '>>'       , '>>'         , '['          ,            , 1        
SUB  , NEWLINE    , '='          , '>'          , 98         , 1        
SUB  , ','        , ':'          , '/'          , STRING     , 1        
DEL  , '.'        , ':'          , NEWLINE      ,            , 1        
DEL  , NUMBER     , ')'          , '>>'         ,            , 1        
SUB  , ')'        , '...'        , EOS          , '.'        , 1        
DEL  , NAME       , NAME         , 'pass'       ,            , 1        
DEL  , NAME       , 'pass'       , ':'          ,            , 1        
DEL  , 'None'     , UNKNOWN_CHAR , '}'          ,            , 1        
DEL  , 'False'    , '//'         , 'or'         ,            , 1        
DEL  , 99         , '//'         , NUMBER       ,            , 1        
SUB  , '['        , UNKNOWN_CHAR , '-'          , STRING     , 1        
DEL  , STRING     , '-'          , UNKNOWN_CHAR ,            , 1        
DEL  , STRING     , UNKNOWN_CHAR , 'for'        ,            , 1        
DEL  , NUMBER     , ')'          , '['          ,            , 1        
DEL  , NUMBER     , '['          , NUMBER       ,            , 1        
INS  , 'None'     ,              , EOS          , ')'        , 1        
SUB  , STRING     , ')'          , NEWLINE      , '.'        , 1        
INS  , '}'        ,              , '...'        , NEWLINE    , 1        
DEL  , NAME       , 'as'         , NEWLINE      ,            , 1        
DEL  , '...'      , NAME         , '['          ,            , 1        
SUB  , NAME       , 'if'         , '('          , 'in'       , 1        
DEL  , NEWLINE    , ')'          , '.'          ,            , 1        
INS  , 99         ,              , 'yield'      , 'except'   , 1        
INS  , 'except'   ,              , 'yield'      , ':'        , 1        
SUB  , NEWLINE    , 98           , '.'          , '('        , 1        
INS  , BOS        ,              , '/'          , NAME       , 1        
DEL  , NUMBER     , UNKNOWN_CHAR , 'and'        ,            , 1        
INS  , ')'        ,              , ')'          , ':'        , 1        
INS  , ':'        ,              , ')'          , '('        , 1        
DEL  , STRING     , ';'          , NEWLINE      ,            , 1        
DEL  , ')'        , ')'          , '<='         ,            , 1        
SUB  , NAME       , '.'          , NAME         , '+'        , 1        
DEL  , ')'        , ')'          , '>'          ,            , 1        
SUB  , '>='       , UNKNOWN_CHAR , NUMBER       , '-'        , 1        
SUB  , NUMBER     , UNKNOWN_CHAR , NUMBER       , '-'        , 1        
DEL  , '='        , ')'          , '{'          ,            , 1        
INS  , '~'        ,              , EOS          , '('        , 1        
INS  , NAME       ,              , '|'          , NEWLINE    , 1        
INS  , NEWLINE    ,              , '|'          , NAME       , 1        
DEL  , NEWLINE    , ')'          , UNKNOWN_CHAR ,            , 1        
DEL  , '='        , '>'          , NAME         ,            , 1        
DEL  , NAME       , '{'          , STRING       ,            , 1        
DEL  , NAME       , ','          , STRING       ,            , 1        
DEL  , NAME       , STRING       , '}'          ,            , 1        
DEL  , NAME       , '}'          , '<'          ,            , 1        
INS  , '['        ,              , '('          , '['        , 1        
SUB  , BOS        , NAME         , NAME         , '{'        , 1        
SUB  , 99         , '>>'         , '>'          , 'return'   , 1        
DEL  , 'return'   , '>'          , NAME         ,            , 1        
SUB  , '('        , 'in'         , '=='         , NAME       , 1        
DEL  , '%'        , NEWLINE      , 98           ,            , 1        
DEL  , '%'        , 98           , '('          ,            , 1        
DEL  , ':'        , '//'         , NUMBER       ,            , 1        
DEL  , ':'        , NUMBER       , NEWLINE      ,            , 1        
SUB  , NUMBER     , STRING       , NAME         , 'else'     , 1        
DEL  , '+'        , '|'          , STRING       ,            , 1        
SUB  , '('        , UNKNOWN_CHAR , NUMBER       , '-'        , 1        
DEL  , 99         , '%'          , NAME         ,            , 1        
DEL  , NAME       , ']'          , '%'          ,            , 1        
SUB  , STRING     , '['          , ')'          , ']'        , 1        
DEL  , NEWLINE    , '<<'         , '<'          ,            , 1        
DEL  , NEWLINE    , '<'          , '['          ,            , 1        
SUB  , NAME       , UNKNOWN_CHAR , NUMBER       , 'if'       , 1        
SUB  , NUMBER     , ':'          , NUMBER       , 'else'     , 1        
SUB  , NAME       , '='          , STRING       , 'or'       , 1        
SUB  , 'return'   , 'async'      , NAME         , 'await'    , 1        
DEL  , '='        , STRING       , '>'          ,            , 1        
DEL  , '='        , '>'          , NEWLINE      ,            , 1        
DEL  , BOS        , 'class'      , '='          ,            , 1        
DEL  , BOS        , '='          , STRING       ,            , 1        
DEL  , STRING     , '>'          , EOS          ,            , 1        
DEL  , BOS        , '/'          , NAME         ,            , 1        
DEL  , STRING     , ')'          , 'is'         ,            , 1        
DEL  , 99         , 'def'        , '('          ,            , 1        
INS  , 'if'       ,              , 'return'     , STRING     , 1        
INS  , STRING     ,              , 'return'     , ':'        , 1        
DEL  , BOS        , 'import'     , 'from'       ,            , 1        
INS  , 98         ,              , NEWLINE      , '...'      , 1        
INS  , '['        ,              , ']'          , ':'        , 1        
SUB  , 'True'     , 'for'        , NAME         , 'if'       , 1        
INS  , ')'        ,              , NAME         , 'else'     , 1        
DEL  , ')'        , ','          , 'for'        ,            , 1        
DEL  , '}'        , '}'          , 'for'        ,            , 1        
DEL  , 98         , '<'          , NAME         ,            , 1        
SUB  , '('        , NAME         , '>'          , ')'        , 1        
SUB  , STRING     , '/'          , UNKNOWN_CHAR , STRING     , 1        
SUB  , STRING     , UNKNOWN_CHAR , NEWLINE      , ','        , 1        
DEL  , BOS        , '='          , NAME         ,            , 1        
DEL  , BOS        , ')'          , 'class'      ,            , 1        
SUB  , NAME       , UNKNOWN_CHAR , NEWLINE      , ')'        , 1        
SUB  , NAME       , UNKNOWN_CHAR , EOS          , ')'        , 1        
INS  , 'assert'   ,              , NAME         , '{'        , 1        
INS  , '{'        ,              , NAME         , '}'        , 1        
DEL  , '.'        , 'class'      , '.'          ,            , 1        
SUB  , STRING     , UNKNOWN_CHAR , UNKNOWN_CHAR , ','        , 1        
DEL  , NAME       , '='          , '('          ,            , 1        
DEL  , ']'        , ']'          , '+='         ,            , 1        
INS  , 'else'     ,              , 'if'         , NUMBER     , 1        
SUB  , ']'        , '='          , '>'          , '<='       , 1        
DEL  , '<='       , '>'          , NAME         ,            , 1        
DEL  , NAME       , '...'        , ':'          ,            , 1        
SUB  , '='        , '...'        , NAME         , '['        , 1        
SUB  , '['        , NAME         , 'else'       , ']'        , 1        
DEL  , ']'        , 'else'       , '...'        ,            , 1        
INS  , ')'        ,              , '<='         , ')'        , 1        
DEL  , NAME       , 'and'        , NEWLINE      ,            , 1        
SUB  , STRING     , NAME         , NAME         , '}'        , 1        
SUB  , '}'        , NAME         , UNKNOWN_CHAR , ','        , 1        
INS  , NEWLINE    ,              , 98           , 'while'    , 1        
INS  , 'while'    ,              , 98           , '('        , 1        
INS  , '('        ,              , 98           , ')'        , 1        
INS  , ')'        ,              , 98           , ':'        , 1        
INS  , NAME       ,              , '<='         , ')'        , 1        
SUB  , BOS        , '>>'         , '>'          , '['        , 1        
DEL  , '['        , '>'          , NAME         ,            , 1        
INS  , STRING     ,              , 'import'     , ')'        , 1        
SUB  , STRING     , UNKNOWN_CHAR , '<'          , STRING     , 1        
DEL  , '...'      , '...'        , '['          ,            , 1        
DEL  , '>'        , '='          , NAME         ,            , 1        
SUB  , BOS        , 'if'         , '{'          , NAME       , 1        
SUB  , '}'        , ':'          , EOS          , ')'        , 1        
DEL  , NAME       , 'else'       , NAME         ,            , 1        
SUB  , NEWLINE    , 'continue'   , NEWLINE      , 98         , 1        
SUB  , NAME       , '.'          , NAME         , ')'        , 1        
DEL  , NAME       , ';'          , STRING       ,            , 1        
DEL  , ')'        , 98           , 'and'        ,            , 1        
DEL  , ')'        , '-'          , 'in'         ,            , 1        
DEL  , NUMBER     , NAME         , 'for'        ,            , 1        
INS  , ','        ,              , NUMBER       , '('        , 1        
SUB  , NUMBER     , STRING       , '-'          , ')'        , 1        
DEL  , ')'        , '->'         , 'True'       ,            , 1        
DEL  , BOS        , ')'          , '.'          ,            , 1        
DEL  , ')'        , 'return'     , UNKNOWN_CHAR ,            , 1        
SUB  , BOS        , NAME         , STRING       , 'if'       , 1        
INS  , STRING     ,              , '.'          , ':'        , 1        
DEL  , ')'        , '.'          , ')'          ,            , 1        
INS  , NAME       ,              , '//'         , ')'        , 1        
SUB  , NEWLINE    , 'pass'       , '='          , NAME       , 1        
SUB  , '/'        , NUMBER       , '.'          , NAME       , 1        
SUB  , NEWLINE    , 'is'         , '='          , NAME       , 1        
SUB  , 'return'   , 'is'         , '-'          , NAME       , 1        
INS  , '{'        ,              , 'for'        , NAME       , 1        
INS  , STRING     ,              , NAME         , '['        , 1        
DEL  , 99         , NAME         , ','          ,            , 1        
DEL  , 99         , ','          , NEWLINE      ,            , 1        
SUB  , '='        , '<'          , NAME         , NUMBER     , 1        
DEL  , NUMBER     , '>'          , ')'          ,            , 1        
INS  , STRING     ,              , 'lambda'     , ','        , 1        
INS  , STRING     ,              , '.'          , STRING     , 1        
DEL  , NAME       , '*'          , '//'         ,            , 1        
DEL  , NAME       , ']'          , '|'          ,            , 1        
DEL  , NAME       , '|'          , '%'          ,            , 1        
INS  , 98         ,              , NEWLINE      , NUMBER     , 1        
SUB  , ')'        , ':'          , EOS          , ')'        , 1        
DEL  , ':'        , ')'          , NAME         ,            , 1        
SUB  , ')'        , UNKNOWN_CHAR , NAME         , '/'        , 1        
DEL  , '('        , '<'          , 'class'      ,            , 1        
DEL  , '('        , 'class'      , STRING       ,            , 1        
DEL  , '('        , STRING       , '>'          ,            , 1        
DEL  , '('        , '>'          , ','          ,            , 1        
DEL  , '('        , ','          , '{'          ,            , 1        
DEL  , '...'      , '.'          , '-'          ,            , 1        
INS  , 'or'       ,              , '<'          , NAME       , 1        
SUB  , 98         , ':'          , EOS          , '('        , 1        
DEL  , BOS        , '@'          , STRING       ,            , 1        
DEL  , 'return'   , '('          , '('          ,            , 1        
DEL  , ','        , ')'          , ','          ,            , 1        
SUB  , ']'        , '='          , STRING       , '!='       , 1        
SUB  , 'import'   , 'import'     , NEWLINE      , NAME       , 1        
INS  , 'False'    ,              , NEWLINE      , ':'        , 1        
DEL  , 'return'   , UNKNOWN_CHAR , NAME         ,            , 1        
DEL  , NAME       , '+'          , ','          ,            , 1        
INS  , ':'        ,              , 99           , 'pass'     , 1        
SUB  , 'as'       , 'if'         , ':'          , NAME       , 1        
SUB  , '('        , 'if'         , ')'          , NAME       , 1        
INS  , 'if'       ,              , STRING       , NAME       , 1        
INS  , NAME       ,              , STRING       , ']'        , 1        
INS  , ']'        ,              , STRING       , '!='       , 1        
DEL  , 98         , 98           , NAME         ,            , 1        
DEL  , NAME       , ','          , 'if'         ,            , 1        
SUB  , '('        , UNKNOWN_CHAR , ')'          , STRING     , 1        
DEL  , ']'        , ','          , 'for'        ,            , 1        
SUB  , ':'        , NEWLINE      , '['          , '+'        , 1        
DEL  , ']'        , ')'          , 'if'         ,            , 1        
INS  , 'None'     ,              , '}'          , 'else'     , 1        
INS  , 'else'     ,              , '}'          , STRING     , 1        
INS  , NEWLINE    ,              , '('          , 'class'    , 1        
SUB  , NAME       , 'or'         , '('          , 'else'     , 1        
SUB  , NAME       , 'or'         , NUMBER       , 'else'     , 1        
INS  , '='        ,              , '['          , STRING     , 1        
SUB  , BOS        , 'def'        , NAME         , 'del'      , 1        
SUB  , '('        , 'from'       , '-'          , NAME       , 1        
SUB  , '*'        , 'from'       , ')'          , NAME       , 1        
DEL  , NEWLINE    , 99           , 'global'     ,            , 1        
DEL  , STRING     , '}'          , '.'          ,            , 1        
SUB  , ']'        , ','          , EOS          , ')'        , 1        
DEL  , NAME       , 'is'         , '<'          ,            , 1        
DEL  , '='        , STRING       , NAME         ,            , 1        
SUB  , BOS        , 'as'         , '='          , NAME       , 1        
SUB  , '('        , 'as'         , ')'          , NAME       , 1        
SUB  , '('        , 'as'         , ','          , NAME       , 1        
DEL  , 99         , '...'        , NAME         ,            , 1        
DEL  , 99         , '...'        , NEWLINE      ,            , 1        
SUB  , '='        , 'pass'       , NEWLINE      , NAME       , 1        
DEL  , NAME       , NUMBER       , 'import'     ,            , 1        
INS  , ')'        ,              , 'if'         , ':'        , 1        
INS  , STRING     ,              , '>'          , ']'        , 1        
INS  , ':'        ,              , NEWLINE      , 'break'    , 1        
DEL  , 'from'     , '**'         , NAME         ,            , 1        
DEL  , NAME       , '**'         , 'import'     ,            , 1        
SUB  , '('        , UNKNOWN_CHAR , '%'          , NUMBER     , 1        
DEL  , '*'        , '**'         , NAME         ,            , 1        
DEL  , NAME       , '('          , 'False'      ,            , 1        
DEL  , NAME       , STRING       , ';'          ,            , 1        
DEL  , NAME       , ';'          , UNKNOWN_CHAR ,            , 1        
SUB  , '+'        , UNKNOWN_CHAR , '.'          , NAME       , 1        
DEL  , ':'        , NEWLINE      , 'break'      ,            , 1        
DEL  , NEWLINE    , '%'          , 'or'         ,            , 1        
SUB  , NAME       , '*'          , NEWLINE      , '('        , 1        
DEL  , BOS        , 'try'        , NAME         ,            , 1        
INS  , ')'        ,              , NAME         , 'or'       , 1        
INS  , '%'        ,              , ')'          , NAME       , 1        
SUB  , ','        , UNKNOWN_CHAR , ')'          , NAME       , 1        
INS  , ':'        ,              , EOS          , 'continue' , 1        
SUB  , '='        , NAME         , '*'          , 'lambda'   , 1        
INS  , '['        ,              , '['          , NAME       , 1        
INS  , ')'        ,              , '.'          , ']'        , 1        
SUB  , NAME       , '+='         , NAME         , 'in'       , 1        
DEL  , '('        , '**'         , STRING       ,            , 1        
SUB  , ')'        , '{'          , NUMBER       , NEWLINE    , 1        
DEL  , NUMBER     , '}'          , NEWLINE      ,            , 1        
INS  , '%'        ,              , NAME         , '('        , 1        
SUB  , '='        , STRING       , ')'          , '('        , 1        
DEL  , ']'        , 98           , '.'          ,            , 1        
DEL  , '['        , '.'          , ']'          ,            , 1        
SUB  , ','        , '}'          , NEWLINE      , ']'        , 1        
DEL  , NAME       , 'as'         , ':'          ,            , 1        
INS  , NAME       ,              , 'for'        , ']'        , 1        
INS  , 99         ,              , 'with'       , STRING     , 1        
INS  , STRING     ,              , 'with'       , NEWLINE    , 1        
SUB  , '('        , UNKNOWN_CHAR , ','          , STRING     , 1        
INS  , NAME       ,              , EOS          , '-'        , 1        
INS  , '-'        ,              , EOS          , NAME       , 1        
SUB  , ','        , 'pass'       , ']'          , NAME       , 1        
INS  , NUMBER     ,              , NUMBER       , ':'        , 1        
DEL  , NUMBER     , UNKNOWN_CHAR , NUMBER       ,            , 1        
SUB  , NAME       , 'if'         , NAME         , 'in'       , 1        
DEL  , NAME       , ','          , ')'          ,            , 1        
DEL  , BOS        , '*'          , NAME         ,            , 1        
DEL  , 'return'   , '{'          , NAME         ,            , 1        
SUB  , ','        , 'not'        , 'in'         , NAME       , 1        
DEL  , ')'        , ';'          , NEWLINE      ,            , 1        
DEL  , ')'        , 'or'         , ':'          ,            , 1        
DEL  , 98         , '@'          , NEWLINE      ,            , 1        
SUB  , NEWLINE    , 'from'       , NAME         , 'for'      , 1        
SUB  , STRING     , '&'          , NEWLINE      , ':'        , 1        
SUB  , '+'        , '.'          , NAME         , STRING     , 1        
INS  , '...'      ,              , NEWLINE      , '['        , 1        
SUB  , BOS        , '...'        , NEWLINE      , 'class'    , 1        
DEL  , 'return'   , NEWLINE      , 99           ,            , 1        
DEL  , 'return'   , 99           , 'await'      ,            , 1        
INS  , NEWLINE    ,              , 'for'        , '('        , 1        
INS  , '('        ,              , 'for'        , '['        , 1        
DEL  , '['        , '**'         , NAME         ,            , 1        
DEL  , NAME       , '**'         , ']'          ,            , 1        
INS  , STRING     ,              , 'in'         , ')'        , 1        
SUB  , '+'        , UNKNOWN_CHAR , ','          , STRING     , 1        
SUB  , 'else'     , 'break'      , NEWLINE      , NAME       , 1        
INS  , NAME       ,              , '!='         , ':'        , 1        
INS  , ':'        ,              , '!='         , NAME       , 1        
SUB  , NAME       , ','          , STRING       , ')'        , 1        
SUB  , ')'        , STRING       , NEWLINE      , ')'        , 1        
SUB  , 'else'     , 'continue'   , EOS          , 'False'    , 1        
DEL  , STRING     , 'else'       , 'False'      ,            , 1        
DEL  , STRING     , 'False'      , ']'          ,            , 1        
DEL  , ':'        , '&'          , NEWLINE      ,            , 1        
DEL  , 99         , NAME         , NUMBER       ,            , 1        
DEL  , 99         , NUMBER       , NEWLINE      ,            , 1        
DEL  , 99         , NEWLINE      , 'return'     ,            , 1        
SUB  , ')'        , UNKNOWN_CHAR , NAME         , 'if'       , 1        
SUB  , ')'        , ':'          , STRING       , 'else'     , 1        
DEL  , NUMBER     , 'or'         , ':'          ,            , 1        
DEL  , NEWLINE    , 98           , '>>'         ,            , 1        
DEL  , ')'        , UNKNOWN_CHAR , NUMBER       ,            , 1        
DEL  , 'return'   , NAME         , '='          ,            , 1        
DEL  , 'return'   , '='          , STRING       ,            , 1        
SUB  , '='        , '.'          , '/'          , NAME       , 1        
SUB  , 'in'       , 'in'         , '.'          , NAME       , 1        
DEL  , BOS        , '**'         , EOS          ,            , 1        
SUB  , ','        , '%'          , '('          , '='        , 1        
SUB  , 'if'       , UNKNOWN_CHAR , '('          , 'not'      , 1        
SUB  , ','        , NAME         , '-'          , '('        , 1        
DEL  , STRING     , '%'          , '*'          ,            , 1        
DEL  , NAME       , UNKNOWN_CHAR , '{'          ,            , 1        
DEL  , NAME       , '}'          , UNKNOWN_CHAR ,            , 1        
SUB  , NAME       , STRING       , NAME         , '+'        , 1        
SUB  , '+'        , NAME         , UNKNOWN_CHAR , '['        , 1        
INS  , '{'        ,              , NEWLINE      , '['        , 1        
DEL  , 'None'     , ')'          , NEWLINE      ,            , 1        
DEL  , 'None'     , NEWLINE      , ']'          ,            , 1        
DEL  , '!='       , '='          , NUMBER       ,            , 1        
SUB  , NAME       , 98           , STRING       , '('        , 1        
SUB  , NAME       , NAME         , '['          , '='        , 1        
SUB  , 'not'      , 'None'       , NEWLINE      , NAME       , 1        
DEL  , '('        , STRING       , NAME         ,            , 1        
SUB  , STRING     , NEWLINE      , 98           , '+'        , 1        
DEL  , STRING     , '.'          , '('          ,            , 1        
DEL  , 'raise'    , UNKNOWN_CHAR , NAME         ,            , 1        
DEL  , NAME       , 'in'         , '<'          ,            , 1        
INS  , '...'      ,              , NEWLINE      , '.'        , 1        
DEL  , NAME       , '='          , '['          ,            , 1        
DEL  , '*'        , ')'          , NUMBER       ,            , 1        
DEL  , ','        , '%'          , '('          ,            , 1        
SUB  , STRING     , ')'          , '.'          , ']'        , 1        
SUB  , '.'        , 'and'        , '('          , NAME       , 1        
DEL  , '-'        , '->'         , '['          ,            , 1        
DEL  , ']'        , STRING       , NEWLINE      ,            , 1        
SUB  , NAME       , ':'          , NAME         , ')'        , 1        
SUB  , ')'        , NAME         , NEWLINE      , ')'        , 1        
DEL  , NAME       , ')'          , '*'          ,            , 1        
DEL  , '['        , NAME         , ']'          ,            , 1        
DEL  , ')'        , '<'          , UNKNOWN_CHAR ,            , 1        
SUB  , ')'        , ';'          , NAME         , ')'        , 1        
INS  , STRING     ,              , 'if'         , STRING     , 1        
DEL  , 98         , '='          , STRING       ,            , 1        
INS  , NEWLINE    ,              , 99           , 'return'   , 1        
DEL  , '-'        , 'as'         , '.'          ,            , 1        
DEL  , '-'        , '.'          , NAME         ,            , 1        
SUB  , 'True'     , ']'          , '.'          , ')'        , 1        
INS  , ']'        ,              , NUMBER       , '!='       , 1        
DEL  , NUMBER     , NUMBER       , 'else'       ,            , 1        
DEL  , '!='       , '('          , NAME         ,            , 1        
SUB  , NAME       , '}'          , 'for'        , ']'        , 1        
DEL  , ']'        , '=='         , '>'          ,            , 1        
DEL  , ']'        , '>'          , UNKNOWN_CHAR ,            , 1        
DEL  , '=='       , '>'          , NUMBER       ,            , 1        
DEL  , 98         , '^'          , NAME         ,            , 1        
DEL  , 98         , NAME         , '%'          ,            , 1        
DEL  , 98         , NAME         , ','          ,            , 1        
DEL  , 98         , ','          , NEWLINE      ,            , 1        
INS  , NAME       ,              , '('          , ','        , 1        
DEL  , NAME       , 'await'      , NAME         ,            , 1        
INS  , '+'        ,              , ':'          , NUMBER     , 1         
""".trimIndent()