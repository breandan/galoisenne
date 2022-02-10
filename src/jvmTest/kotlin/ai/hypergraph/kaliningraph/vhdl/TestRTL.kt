package ai.hypergraph.kaliningraph.vhdl

import ai.hypergraph.kaliningraph.graphs.RTLGraph
import ai.hypergraph.kaliningraph.visualization.show
import org.junit.jupiter.api.Test

// https://bitbucket.org/cdubach/comp764ecse688_winter2022/src/8ffacfc111cf9690be87d2e72f80c5a2c66032c9/part3.md
class TestRTL {
/*
./gradlew :cleanJvmTest :jvmTest --tests "ai.hypergraph.kaliningraph.vhdl.TestRTL.vizGraph"
*/
    @Test
    fun vizGraph() {
        RTLGraph {
            RAM = malloc(4)
            RAM[3] = RAM[0] * 2
            C = A + 6
            D = C + 1
            E = C + 5
        }.show()
    }

/*
./gradlew :cleanJvmTest :jvmTest --tests "ai.hypergraph.kaliningraph.vhdl.TestRTL.simpleTest"
*/
    @Test
    fun simpleTest() {
        RTLGraph {
            RAM = malloc(4)
            RAM[3] = RAM[0] * RAM[1] + RAM[2]
        }.show()
    }

/*
./gradlew :cleanJvmTest :jvmTest --tests "ai.hypergraph.kaliningraph.vhdl.TestRTL.testReassignment"
*/
    @Test
    fun testReassignment() {
        RTLGraph {
            A = B + B
            A = 2 + A
            A = C + C
        }.show()
    }

/*
./gradlew :cleanJvmTest :jvmTest --tests "ai.hypergraph.kaliningraph.vhdl.TestRTL.testVecMul"
*/
    @Test
    fun testVecMul() {
        RTLGraph {
            A = malloc(4);
            B = malloc(4);
            C = malloc(4);
            C[0] = A[0] * B[0];
            C[1] = A[1] * B[1];
            C[2] = A[2] * B[2];
            C[3] = A[3] * B[3];
        }
        .also { it.show() }
//        .also { println(it.compile()) }
    }

/*
./gradlew :cleanJvmTest :jvmTest --tests "ai.hypergraph.kaliningraph.vhdl.TestRTL.testDotProduct"
*/
    @Test
    fun testDotProduct() {
        RTLGraph {
            A = malloc(4);
            B = malloc(4);
            C = malloc(1);
            C[0] = A[0] * B[0] +
                   A[1] * B[1] +
                   A[2] * B[2] +
                   A[3] * B[3];
        }.show()
    }

/*
./gradlew :cleanJvmTest :jvmTest --tests "ai.hypergraph.kaliningraph.vhdl.TestRTL.testConvolution"
*/
    @Test
    fun testConvolution() {
        RTLGraph {
            A = malloc(6);
            W = malloc(3);
            C = malloc(4);
            C[0] = A[0] * W[0] + A[1] * W[1] + A[2] * W[2];
            C[1] = A[1] * W[0] + A[2] * W[1] + A[3] * W[2];
            C[2] = A[2] * W[0] + A[3] * W[1] + A[4] * W[2];
            C[3] = A[3] * W[0] + A[4] * W[1] + A[5] * W[2];
        }.show()
    }

/*
./gradlew :cleanJvmTest :jvmTest --tests "ai.hypergraph.kaliningraph.vhdl.TestRTL.simpleVariableTest"
*/
    @Test
    fun simpleVariableTest() {
        RTLGraph {
            I = 1.w
            J = 1.w
            I = I+J;
        }.show()
    }

/*
./gradlew :cleanJvmTest :jvmTest --tests "ai.hypergraph.kaliningraph.vhdl.TestRTL.simpleLoopTest"
*/
    @Test
    fun simpleLoopTest() {
        RTLGraph {
            S = 0.w
            for(i in 0..3) {
                S = S + i.w
            }
        }.show()
    }

/*
./gradlew :cleanJvmTest :jvmTest --tests "ai.hypergraph.kaliningraph.vhdl.TestRTL.sumOfArrayTest"
*/

    @Test
    fun sumOfArrayTest() {
        RTLGraph {
            A = malloc(4);
            S = 0.w;
            for (i in 0..3) { // 4 iterations
                S += A[i]
            }
        }.show()
    }

/*
./gradlew :cleanJvmTest :jvmTest --tests "ai.hypergraph.kaliningraph.vhdl.TestRTL.dotProductLoopTest"
*/

    @Test
    fun dotProductLoopTest() {
        RTLGraph {
            A = malloc(4);
            B = malloc(4);
            S = 0.w;
            for (i in 0..3) { // 4 iterations
                S = S + A[i] * B[i];
            }
        }.show()
    }
}