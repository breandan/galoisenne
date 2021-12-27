package ai.hypergraph.kaliningraph

import ai.hypergraph.kaliningraph.types.*
import kotlin.test.Test
import kotlin.test.assertEquals

class PeanoTest {
    @Test
    fun peanoTest() {
        val one = O
            .plus1()
            .plus1()
            .plus2()
            .plus3()
            .plus3()
            .plus4()
            .minus4()
            .minus3()
            .minus2()
            .minus1()
            .minus3()
            .also { println(it.toInt()) }

        assertEquals(1, one.toInt())

        val two = one + one
        assertEquals(2, two.toInt())

        val four = two * two
        assertEquals(4, four.toInt())
    }
}