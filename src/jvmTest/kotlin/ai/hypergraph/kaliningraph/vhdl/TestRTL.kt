package ai.hypergraph.kaliningraph.vhdl

import ai.hypergraph.kaliningraph.graphs.RTLGraph
import ai.hypergraph.kaliningraph.visualization.show
import org.junit.jupiter.api.Test
import java.io.File

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
    }
    .also { it.compileAndRun() }
    .also { it.show() }
  }

  /*
  ./gradlew :cleanJvmTest :jvmTest --tests "ai.hypergraph.kaliningraph.vhdl.TestRTL.simpleTest"
  */
  @Test
  fun simpleTest() {
    RTLGraph {
      RAM = malloc(4)
      RAM[3] = RAM[0] * RAM[1] + RAM[2]
    }
    .also { it.compileAndRun() }
    .also { it.show() }
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
    }
    .also { it.compileAndRun() }
    .also { it.show() }
  }

  /*
  ./gradlew :cleanJvmTest :jvmTest --tests "ai.hypergraph.kaliningraph.vhdl.TestRTL.testVecMul"
  */
  @Test
  fun testVecMul() {
    RTLGraph {
      A = malloc(4)
      B = malloc(4)
      C = malloc(4)
      C[0] = A[0] * B[0]
      C[1] = A[1] * B[1]
      C[2] = A[2] * B[2]
      C[3] = A[3] * B[3]
    }
    .also { it.compileAndRun() }
    .also { it.show() }
  }

  /*
  ./gradlew :cleanJvmTest :jvmTest --tests "ai.hypergraph.kaliningraph.vhdl.TestRTL.testDotProduct"
  */
  @Test
  fun testDotProduct() {
    RTLGraph {
      A = malloc(4)
      B = malloc(4)
      C = malloc(1)
      C[0] = A[0] * B[0] +
        A[1] * B[1] +
        A[2] * B[2] +
        A[3] * B[3]
    }
    .also { it.compileAndRun() }
    .also { it.show() }
  }

  /*
  ./gradlew :cleanJvmTest :jvmTest --tests "ai.hypergraph.kaliningraph.vhdl.TestRTL.testConvolution"
  */
  @Test
  fun testConvolution() {
    RTLGraph {
      A = malloc(6)
      W = malloc(3)
      C = malloc(4)
      C[0] = A[0] * W[0] + A[1] * W[1] + A[2] * W[2]
      C[1] = A[1] * W[0] + A[2] * W[1] + A[3] * W[2]
      C[2] = A[2] * W[0] + A[3] * W[1] + A[4] * W[2]
      C[3] = A[3] * W[0] + A[4] * W[1] + A[5] * W[2]
    }
    .also { it.compileAndRun() }
    .also { it.show() }
  }

  /*
  ./gradlew :cleanJvmTest :jvmTest --tests "ai.hypergraph.kaliningraph.vhdl.TestRTL.simpleVariableTest"
  */
  @Test
  fun simpleVariableTest() {
    RTLGraph {
      I = 1.w
      J = 1.w
      I += J
    }
    .also { it.compileAndRun() }
    .also { it.show() }
  }

  /*
  ./gradlew :cleanJvmTest :jvmTest --tests "ai.hypergraph.kaliningraph.vhdl.TestRTL.simpleLoopTest"
  */
  @Test
  fun simpleLoopTest() {
    RTLGraph {
      S = 0.w
      for (i in 0..3) {
        S += i.w
      }
    }
    .also { it.compileAndRun() }
    .also { it.show() }
  }

  /*
  ./gradlew :cleanJvmTest :jvmTest --tests "ai.hypergraph.kaliningraph.vhdl.TestRTL.sumOfArrayTest"
  */

  @Test
  fun sumOfArrayTest() {
    RTLGraph {
      A = malloc(4)
      S = 0.w
      for (i in 0..3) { // 4 iterations
        S += A[i]
      }
    }
    .also { it.compileAndRun() }
    .also { it.show() }
  }

  /*
  ./gradlew :cleanJvmTest :jvmTest --tests "ai.hypergraph.kaliningraph.vhdl.TestRTL.dotProductLoopTest"
  */

  @Test
  fun dotProductLoopTest() {
    RTLGraph {
      A = malloc(4)
      B = malloc(4)
      S = 0.w
      for (i in 0..3) { // 4 iterations
        S += A[i] * B[i]
      }
    }
    .also { it.compileAndRun() }
    .also { it.show() }
  }
}

fun RTLGraph.compileAndRun() {
  val circuit = vhdl()
  println(circuit)
  val designFile = genArithmeticCircuit(circuit).let { File("design.vhd").apply { writeText(it) } }
  val testBench = genTestBench(circuit).let { File("testbench.vhd").apply { writeText(it) } }
  return

  runCommand("ghdl -a ${testBench.absolutePath} ${designFile.absolutePath}")
  runCommand("ghdl -e testbench")
  runCommand("ghdl -r testbench --wave=wave.ghw --stop-time=100ns")
  runCommand("open wave.ghw")
}