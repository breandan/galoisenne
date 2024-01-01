package ai.hypergraph.kaliningraph.repair


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
  """.trimIndent()

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
  """.trimIndent()
