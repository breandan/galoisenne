package ai.hypergraph.kaliningraph.repair

import org.intellij.lang.annotations.Language


@Language("py")
val invalidPythonStatements = """
  NAME = NAME ( NAME ( lambda ( NAME , NAME ) : [ NAME , NAME ] , NAME ) )
  NAME = NAME ( * NAME ( lambda ( NAME , NAME ) : NAME == NUMBER , NAME ( NAME . NAME ) ) ) [ NUMBER ]
  NAME = [ ( NAME , NAME ( NAME ( lambda ( NAME , NAME ) : NAME , NAME ) ) ) for ( NAME , NAME ) in NAME ]
  NAME = NAME . NAME ( NAME , NAME = { NAME : [ NAME ] , NAME : [ NAME ] } ) NAME = NAME . NAME ( NAME . NAME )
  NAME = NAME ( NAME ( lambda ( NAME , NAME ) : NAME ( NAME - NAME ) , NAME ( NAME [ NAME ] , NAME ) ) )
  NAME = NAME ( NAME . NAME ( ) , lambda ( NAME , NAME ) : [ ( NAME , NAME ) ] , NAME )
  NAME = NAME ( NAME . NAME ( ) , lambda ( NAME , NAME ) : [ ( NAME , NAME ) for NAME in NAME ] , NAME )
  NAME = NAME ( NAME ( lambda ( NAME , NAME ) : [ NAME , NAME [ NUMBER ] , NAME [ NUMBER ] ] , NAME . NAME . NAME ( ) ) )
  NAME = NAME ( [ NAME ( NAME [ NAME ] - NAME [ NAME ] ) for NAME in NUMBER , NUMBER , NUMBER ] )
  NAME = NAME ( lambda ( NAME , NAME ) : ( NAME ( NAME ) , NAME ( NAME [ NUMBER ] ) ) , NAME )
  NAME = NAME ( ( NAME NAME [ NAME ] ) for NAME in NAME if NAME > NAME )
  NAME = [ NAME ( NUMBER , NAME , NAME ( NAME ) / NAME ) for NAME , NAME in NAME ( NAME . NAME NAME ) ]
  NAME = NAME ( lambda ( NAME , NAME ) : [ NAME + [ NAME ] for NAME in NAME ] , NAME )
  NAME = NAME . NAME ( lambda ( NAME , NAME ) : ( NAME , ( NAME , NUMBER ) ) ) . NAME ( lambda NAME , NAME : ( NAME [ NUMBER ] + NAME [ NUMBER ] , NAME [ NUMBER ] + NAME [ NUMBER ] ) ) . NAME ( )
  NAME , NAME = [ ( NUMBER if ( NAME [ NUMBER ] >= NUMBER ) else NAME [ NUMBER ] , NAME [ NUMBER ] , NAME [ NUMBER ] * NUMBER ) for NAME in NAME , NAME ]
  NAME = NAME ( [ NAME ( NAME , NUMBER ) for NAME in NUMBER , NUMBER , NUMBER ] )
  NAME = [ NAME for NAME , NAME in NAME ( NAME . NAME ( ) , NAME = lambda ( NAME , NAME ) : NAME ) ]
  NAME = NAME ( lambda ( NAME , NAME ) : ( NAME [ NAME ] , NAME ( NAME ) ) , NAME )
  NAME = NAME . NAME ( * [ NAME . NAME . NAME ( NAME ) for NAME in NAME , NAME , NAME , NAME , NAME , NAME , NAME ] )
  NAME = NAME . NAME ( lambda ( NAME , NAME ) : ( NAME , NAME . NAME ( NAME . NAME ( ) , NUMBER ) [ NUMBER ] ) )
  NAME = [ NAME for NAME in NAME . NAME , ( ( NAME , ) ) ]
  NAME = NAME . NAME . NAME ( lambda ( NAME , NAME ) : ( NAME , NAME . NAME [ NAME ] . NAME ( NAME ) ) )
  NAME = [ NAME ( lambda NAME , ( NAME , NAME ) : NAME | NAME << NUMBER * NAME , NAME ( NAME ) , NUMBER ) for NAME in NAME ( NAME , NAME ) ]
  NAME = NAME [ ] + [ NAME for NAME in NAME ( NAME ( NAME ) , NAME ( NAME ) ) ]
  NAME = NAME ( NAME . NAME ( ( NAME ) NAME [ NUMBER ] / NAME ( NAME ) * NAME ) )
  NAME = NAME ( NAME . NAME ( ( NAME ) NAME [ NUMBER ] / NAME ( NAME ) * NAME ) )
  NAME = NAME ( [ NAME ( NAME ( NAME [ NAME ] , NAME [ NAME ] NAME [ NAME ] , NAME [ NAME ] ) ) for NAME , NAME in NAME ] )
  NAME = NAME . NAME ( lambda ( NAME , NAME ) : NAME . NAME ( ( NAME [ NAME ] - NAME [ NUMBER ] ) ** NUMBER ) ) . NAME ( lambda NAME , NAME : NAME + NAME )
  NAME = [ NAME for NAME in NAME ( NAME . NAME . NAME ( ) , NAME = lambda ( NAME , NAME ) : ( NAME , NAME ) ) ] [ : NAME ]
  NAME = [ ( NAME NAME [ NAME ] ) for NAME in NAME ]
  NAME [ NAME ] = NAME ( NAME ( lambda ( NAME , NAME ) : NAME , NAME ) )
  NAME = [ ( NAME [ NUMBER ] , NAME [ NUMBER ] - NAME [ NAME [ NUMBER ] ] ) for NAME in NAME . NAME ( ) if NAME [ NUMBER ] in NAME ] NAME
  NAME , NAME , NAME = [ NAME . NAME ( ( NAME . NAME ( NAME ) + NUMBER , NAME . NAME ( NAME ) + NUMBER , NAME . NAME ( NAME ) + NUMBER ) ) for NAME in NAME , NAME , NAME ]
  NAME = NAME ( [ NAME ( NAME [ NAME ] * ( NUMBER - NAME ) + NAME [ NAME ] * NAME ) for NAME in NUMBER , NUMBER , NUMBER ] )
  NAME = NAME . NAME . NAME ( lambda ( NAME , NAME ) : ( NAME [ : : - NUMBER ] , NAME ) ) . NAME ( ) . NAME ( lambda ( NAME , NAME ) : ( NAME [ : : - NUMBER ] , NAME ) )
""".trimIndent()

@Language("py")
val validPythonStatements = """
  NAME = NAME ( [ NAME ( NAME . NAME . NAME ( ) ) for NAME in NAME ] )
  NAME = [ ( NAME , [ ( NAME , NAME * NUMBER + NAME ) for NAME in NAME ( NUMBER ) ] ) for NAME in NAME ( NUMBER ) ]
  NAME = NAME ( NAME ( lambda NAME : NAME ( NAME [ NUMBER ] ) , NAME ) )
  NAME = NAME . NAME ( [ ( NUMBER - NUMBER * NAME ) * ( NAME . NAME ( NAME . NAME ( - NUMBER + ( NAME + NUMBER ) / NUMBER ) ) - NUMBER ) + NUMBER for NAME in NAME ] )
  NAME = NAME ( NAME ) [ : NAME ( NAME . NAME ( NAME ( NAME ) * NUMBER ) ) ]
  NAME = NAME ( ( NAME [ NUMBER : NAME [ NUMBER ] - NUMBER , : ] , NAME [ NAME [ NUMBER ] + NUMBER : NAME . NAME [ NUMBER ] - NUMBER , : ] ) )
  NAME = ( NAME [ NAME ( NAME [ : , NUMBER ] ) , NUMBER ] if NAME > NUMBER * NAME ( NAME [ : , NUMBER ] ) else NUMBER )
  NAME = NAME ( NAME ( ( NAME [ NAME : NAME - NUMBER , NUMBER ] , NAME [ NAME : NAME - NUMBER , NUMBER ] ) ) ) / NAME ( NAME . NAME [ NUMBER ] + NAME . NAME [ NUMBER ] )
  NAME = NAME ( [ NAME ( NAME ( ) . NAME ( ) [ NAME ] ) for NAME in NAME ( NAME ) ] )
  NAME = NAME . NAME ( [ ( NAME ( NAME * ( ( - NAME ) / NAME + NUMBER ) ) ) * ( - NUMBER ) * ( NAME . NAME ( NAME . NAME ( NAME * NAME ) ) - NUMBER ) ** NUMBER + NAME for NAME in NAME ] )
  NAME = NAME ( [ ( NAME [ NAME ] - NAME . NAME ( NAME ) ) ** NUMBER for NAME in NAME ( NAME ( NAME ) ) ] )
  NAME = NAME ( [ ( NAME [ NAME ] - NAME ( NAME [ NAME ] , * NAME ) ) ** NUMBER for NAME in NAME ( NAME ( NAME ) ) ] )
  NAME = NAME . NAME ( NAME ( NAME = NAME ( NAME . NAME [ NAME ( NUMBER , NAME . NAME ) ] ) ) )
  NAME = NAME ( NAME [ NUMBER : NUMBER ] ) * NUMBER + NAME ( NAME . NAME ( NAME ( NAME [ NUMBER : NUMBER ] ) / NUMBER ) )
  NAME . NAME = NAME ( NAME ( lambda NAME : ( NAME , NAME ( NAME [ NAME ] , NAME , NAME ) ) , NAME ) )
  NAME . NAME = NAME . NAME ( [ NAME [ NAME ( NAME / NUMBER ) ] ] )
  NAME = ( NAME . NAME ( [ NAME . NAME ( NAME <= NAME ) ] ) ) [ NUMBER ] [ NUMBER ] [ NUMBER ]
  NAME = NAME ( ( ( NAME ( NAME [ NUMBER ] ) - NAME ( NAME [ NAME ] ) ) / NAME ( NAME [ NAME ] ) ) * NUMBER , NUMBER )
  NAME = NAME ( [ NAME ( [ NAME ( NAME ) for NAME in NAME ( NAME ( NAME ) * NUMBER ) ] ) for NAME , NAME in NAME ( NAME ) if not NAME ( NAME ) % NUMBER ] )
  NAME = NAME . NAME ( NAME [ NAME [ NAME - NUMBER ] ] [ NAME [ NAME [ NAME - NUMBER ] ] == NAME ] )
  NAME = NAME ( ( NAME ( NAME . NAME [ NUMBER ] ) * NAME ( NAME ) ) )
  NAME = NAME ( ( NAME ( NAME [ NUMBER ] . NAME ) for NAME in NAME [ NUMBER : ] ) )
  NAME = NAME ( [ [ NAME . NAME ( ) , NAME . NAME ( ) ] for NAME in NAME ] )
  NAME = NAME ( [ [ NAME . NAME ( ) , NAME . NAME ( ) ] for NAME in NAME ] )
  NAME = NAME ( [ [ NAME . NAME ( ) , NAME . NAME ( ) ] for NAME in NAME + NAME [ NUMBER : NUMBER ] ] )
  NAME = NAME ( [ [ NAME . NAME ( ) , NAME . NAME ( ) ] for NAME in NAME + NAME [ NUMBER : NUMBER ] ] )
  NAME = NAME ( [ [ NAME . NAME ( ) , NAME . NAME ( ) ] for NAME in NAME + NAME [ NUMBER : NUMBER ] ] )
  NAME = NAME ( [ [ NAME . NAME ( ) , NAME . NAME ( ) ] for NAME in NAME + NAME [ NUMBER : NUMBER ] ] )
  NAME = NAME ( [ [ NAME . NAME ( ) , NAME . NAME ( ) ] for NAME in NAME [ NUMBER ] + NAME [ NUMBER ] [ NUMBER : NUMBER ] ] )
  NAME = NAME ( [ [ NAME . NAME ( ) , NAME . NAME ( ) ] for NAME in NAME [ NUMBER ] + NAME [ NUMBER ] [ NUMBER : NUMBER ] ] )
  NAME = NAME ( [ [ NAME . NAME ( ) , NAME . NAME ( ) ] for NAME in NAME + NAME [ NUMBER : NUMBER ] ] )
  NAME = NAME ( [ [ NAME . NAME ( ) , NAME . NAME ( ) ] for NAME in NAME + NAME [ NUMBER : NUMBER ] ] )
  NAME = NAME ( [ [ NAME . NAME ( ) , NAME . NAME ( ) ] for NAME in NAME + NAME [ NUMBER : NUMBER ] ] )
  NAME = NAME ( [ [ NAME . NAME ( ) , NAME . NAME ( ) ] for NAME in NAME + NAME [ NUMBER : NUMBER ] ] )
  NAME = NAME ( [ [ NAME . NAME ( ) , NAME . NAME ( ) ] for NAME in NAME + NAME [ NUMBER : NUMBER ] ] )
  NAME = NAME ( NAME ( NAME ( NAME . NAME [ NUMBER ] . NAME ( ) [ NUMBER ] [ NUMBER ] ) , NAME . NAME ) , NAME . NAME )
  ( NAME , NAME ) = ( NAME [ : NAME ] , NAME [ ( NAME + NAME ( NAME ) ) : ] )
  NAME = NAME . NAME ( [ NAME ( NAME [ - NUMBER ] ) for NAME in NAME ] )
  NAME = NAME . NAME ( [ ( NAME . NAME ( NAME ) , NAME . NAME ( NAME ) ) for NAME in NAME ] )
  NAME = [ ( NAME ( NUMBER , NUMBER ) , NAME . NAME ( [ [ ] ] ) ) ]
  NAME = NAME . NAME . NAME ( NAME ( NAME . NAME ( NAME [ NAME ] ) - NAME . NAME ( NAME [ NAME ] ) ) ) ;
  NAME . NAME [ NUMBER ] . NAME = NAME ( [ - NUMBER , NAME , NAME ] + NAME ( NAME ( NAME . NAME [ NUMBER ] . NAME ) ) )
  NAME = [ NAME ( [ NAME [ NUMBER ] ] + [ NAME . NAME [ NAME ] for NAME in NAME [ NUMBER : ] ] ) . NAME ( NAME , NAME ) for NAME in NAME ( NAME ) ]
  NAME = [ [ NAME * ( NAME [ NAME ] - NAME [ NAME ] ) / ( NAME + NUMBER ) , NAME * ( NAME [ NAME ] - NAME [ NAME ] ) / ( NAME + NUMBER ) ] ]
  NAME = [ [ ( NAME [ NAME ] - NAME [ NUMBER ] [ NAME ] ) , ( NAME [ NAME ] - NAME [ NUMBER ] [ NAME ] ) , NUMBER ] ]
  NAME = [ [ ( NAME [ NAME ] + NAME [ NUMBER ] [ NAME ] ) , ( NAME [ NAME ] + NAME [ NUMBER ] [ NAME ] ) , NUMBER ] ]
  NAME = [ [ ( NAME [ NAME ] - NAME [ NUMBER ] [ NAME ] ) , ( NAME [ NAME ] - NAME [ NUMBER ] [ NAME ] ) , NUMBER ] ]
  NAME = [ [ ( NAME [ NAME ] + NAME [ NUMBER ] [ NAME ] ) , ( NAME [ NAME ] + NAME [ NUMBER ] [ NAME ] ) , NUMBER ] ]
  NAME ( NAME ( [ NAME for NAME in NAME . NAME ( NAME = NAME . NAME , NAME = None ) ] ) )
  NAME = NAME . NAME ( NAME ( NAME [ NAME [ NAME ] ] [ NAME ] [ NUMBER ] [ NUMBER ] ) )
  NAME = NAME . NAME ( NAME ( NAME [ NAME [ NAME ] ] [ NAME ] [ NUMBER ] [ NUMBER ] ) )
  NAME = NAME ( [ NAME ( NAME [ NUMBER ] [ NUMBER : ] ) for NAME in NAME ] )
  NAME . NAME ( NAME ( * [ NAME ( NAME = NAME , NAME = NAME ) for NAME in NAME ] , NAME = True ) )
  NAME = not NAME . NAME [ - NUMBER ] or NAME / ( ( NAME ( NAME . NAME [ - NUMBER ] ) / NAME ) ** NUMBER ) < NAME
  if NAME == NUMBER : NAME . NAME ( NAME ( NAME ( NAME ( NAME [ - NAME - NUMBER ] . NAME ( ) , NAME = NAME ( NUMBER ) ) [ NUMBER ] ) ) )
  NAME = NAME . NAME ( [ NAME . NAME ( ( NAME [ NAME , : ] - NAME [ NAME , : ] ) * ( NAME [ NAME , : ] - NAME [ NAME , : ] ) ) for NAME in NAME [ NAME [ NAME ] ] [ NUMBER ] ] )
  NAME = NAME . NAME ( [ NAME . NAME ( ( NAME [ NAME , : ] - NAME [ NAME , : ] ) * ( NAME [ NAME , : ] - NAME [ NAME , : ] ) ) for NAME in NAME [ NAME [ NAME ] ] [ NUMBER ] ] )
  NAME = NAME . NAME ( NAME ( [ [ NUMBER / NAME , NUMBER / NAME , NUMBER / NAME ] for NAME in NAME . NAME ( NAME ) ] , [ ] ) )
  NAME = NAME ( NAME ( lambda NAME : ( NAME , [ ] ) , NAME ( NAME ) ) )
  NAME = NAME ( [ NAME . NAME ( NAME ( NAME ) / ( NAME ) ) for NAME , NAME in NAME ( NAME [ NUMBER : : NUMBER ] , NAME [ NUMBER : : NUMBER ] ) ] ) / NUMBER
  NAME = NAME . NAME ( NAME , ( ( NAME [ NAME ] - NAME ) / ( NAME - NAME ) ) )
  NAME = NAME ( NAME ( NAME [ NAME . NAME ( NAME > NAME ) ] ) )
  NAME = [ NAME for NAME , NAME in NAME [ NAME . NAME ( ( NAME , NAME ) ) : ] if ( not ( NAME in NAME or NAME in NAME ) ) ]
  NAME = NAME ( ( NAME - ( NAME * NAME . NAME [ NUMBER ] / NUMBER ) - NAME . NAME [ NUMBER ] ) / NAME . NAME [ NUMBER ] )
  NAME = NAME . NAME ( NAME . NAME ( [ NAME . NAME ( NUMBER , NUMBER ) ] * NAME ) , NAME )
  NAME . NAME [ NAME . NAME . NAME ( NAME ) ] = ( ( NAME [ NUMBER ] , NAME ( NAME [ NUMBER ] . NAME ) , NAME [ NUMBER ] , NAME [ NUMBER ] ) )
  NAME = NAME . NAME ( NAME , NAME . NAME ( [ NAME [ NUMBER ] , NAME [ NUMBER ] , NAME [ NUMBER ] , NUMBER ] ) ) [ : NUMBER ]
  NAME = NAME . NAME ( * ( NAME . NAME ( NAME , NAME [ NUMBER : ] ) ) )
  NAME = [ ( NAME ( NAME [ NAME ] ) , NAME ( NAME . NAME - NAME [ NAME ] ) ) for NAME in NAME ( NAME ( NAME ) ) ]
  NAME = NAME . NAME ( NAME , NUMBER , [ NUMBER for NAME in NAME ( NAME . NAME [ NUMBER ] ) ] , NAME = NUMBER )
  NAME = NAME . NAME ( NAME . NAME ( [ NAME [ NAME ] ] ) )
  NAME . NAME = NAME ( [ NAME ( NAME . NAME [ NAME ] ) for NAME in NAME ( NUMBER , NUMBER * NAME . NAME ) ] )
  NAME = NAME ( { NAME [ NUMBER ] : NAME . NAME [ NAME [ NUMBER ] ] / NAME , NAME [ NUMBER ] : NAME . NAME [ NAME [ NUMBER ] ] / NAME } )
  NAME = NAME + ( ( NAME [ ( NAME + NUMBER ) % NUMBER ] * NAME ) >> NAME )
  NAME = NAME ( [ ( NAME . NAME , NAME [ NAME ] ) ] )
  NAME = [ NAME ( ( NAME [ NUMBER ] * NAME . NAME + NAME ) * NAME , ( NAME [ NUMBER ] * NAME . NAME + NAME ) * NAME ) for NAME in NAME ]
  NAME = [ ( NAME . NAME ( NAME [ NAME ] ) , NAME . NAME ( NAME [ NAME ] ) ) for NAME in NAME ]
  NAME = [ ( NAME . NAME ( NAME [ NAME ] [ NAME ] ) , NAME . NAME ( NAME [ NAME ] [ NAME ] ) ) for NAME in NAME for NAME in NAME ( NUMBER ) ]
  NAME = NAME ( [ ( NAME . NAME ( ) , NAME ) for NAME , NAME in NAME . NAME ( ) ] )
  NAME . NAME ( NAME = { NAME : NAME . NAME ( [ NUMBER ] ) } )
  return NUMBER / NAME ** NAME . NAME * NAME . NAME ( NAME . NAME ( NAME . NAME , ( NAME . NAME [ NUMBER ] , NUMBER ) ) * NAME / NAME . NAME [ : , NUMBER ] ** NAME . NAME , NAME = NUMBER )
  NAME = NAME ( [ [ NUMBER ] , [ NUMBER ] , [ NAME * NAME . NAME ( [ NAME for NAME in NAME ] ) ] ] )
""".trimIndent()