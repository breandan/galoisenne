package ai.hypergraph.kaliningraph

import ai.hypergraph.kaliningraph.types.*
import kotlin.test.Test
import kotlin.test.assertEquals

class PeanoTest {
    @Test
    fun peanoTest() {

        val five = O
            .plus2()
            .let { it + S3 }
            .plus4()
            .minus3()
            .minus3()
            .let { it + it }
            .minus3()
            .let { it * S2 }
            .minus4()
            .let { it * it }
            .let { it - S2 }
            .let { it + S3 }
            .minus4()
            .let { it + it }
            .let { it * it }
            .let { it / S2 }
            .let { it + S3 }


        assertEquals(5, five.toInt())

    }
}