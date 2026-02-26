package ai.hypergraph.kaliningraph.languages

import ai.hypergraph.kaliningraph.parsing.freeze
import ai.hypergraph.kaliningraph.parsing.noEpsilonOrNonterminalStubs
import ai.hypergraph.kaliningraph.parsing.parseCFG
import java.io.File

val k1 by lazy {
  File(File("").absolutePath + "/src/jvmTest/resources/fun_k1.cnf")
    .readText().trimIndent().lines().map { it.split(" -> ").let { Pair(it[0], it[1].split(" ")) } }.toSet().freeze()
}
val k2 by lazy {
  File(File("").absolutePath + "/src/jvmTest/resources/fun_k2.cnf")
    .readText().trimIndent().lines().map { it.split(" -> ").let { Pair(it[0], it[1].split(" ")) } }.toSet().freeze()
}
val k3 by lazy {
  File(File("").absolutePath + "/src/jvmTest/resources/fun_k3.cnf")
    .readText().trimIndent().lines().map { it.split(" -> ").let { Pair(it[0], it[1].split(" ")) } }.toSet().freeze()
}

val k2_orig by lazy {
  """
    P1 -> p1
    P2 -> p2
    P3 -> p3
    START -> fn f0 ( p1 : T , p2 : T ) -> T { BDY[m0|U3] }
    BDY[m0|U0] -> INVY[m0|U0]
    BDY[m0|U0] -> let mut p3 = INVX[m0|U0|V0] ; BDY[m1|U4]
    BDY[m0|U1] -> INVY[m0|U1]
    BDY[m0|U1] -> let mut p3 = INVX[m0|U1|V0] ; BDY[m1|U4]
    BDY[m0|U1] -> let mut p3 = INVX[m0|U1|V1] ; BDY[m1|U5]
    BDY[m0|U2] -> INVY[m0|U2]
    BDY[m0|U2] -> let mut p3 = INVX[m0|U2|V0] ; BDY[m1|U4]
    BDY[m0|U2] -> let mut p3 = INVX[m0|U2|V2] ; BDY[m1|U6]
    BDY[m0|U3] -> INVY[m0|U3]
    BDY[m0|U3] -> let mut p3 = INVX[m0|U3|V0] ; BDY[m1|U4]
    BDY[m0|U3] -> let mut p3 = INVX[m0|U3|V1] ; BDY[m1|U5]
    BDY[m0|U3] -> let mut p3 = INVX[m0|U3|V2] ; BDY[m1|U6]
    BDY[m0|U3] -> let mut p3 = INVX[m0|U3|V3] ; BDY[m1|U7]
    BDY[m1|U0] -> INVY[m1|U0]
    BDY[m1|U0] -> P3 = INVX[m1|U0|V0] ; BDY[m1|U4]
    BDY[m1|U1] -> INVY[m1|U1]
    BDY[m1|U1] -> P3 = INVX[m1|U1|V0] ; BDY[m1|U4]
    BDY[m1|U1] -> P3 = INVX[m1|U1|V1] ; BDY[m1|U5]
    BDY[m1|U2] -> INVY[m1|U2]
    BDY[m1|U2] -> P3 = INVX[m1|U2|V0] ; BDY[m1|U4]
    BDY[m1|U2] -> P3 = INVX[m1|U2|V2] ; BDY[m1|U6]
    BDY[m1|U3] -> INVY[m1|U3]
    BDY[m1|U3] -> P3 = INVX[m1|U3|V0] ; BDY[m1|U4]
    BDY[m1|U3] -> P3 = INVX[m1|U3|V1] ; BDY[m1|U5]
    BDY[m1|U3] -> P3 = INVX[m1|U3|V2] ; BDY[m1|U6]
    BDY[m1|U3] -> P3 = INVX[m1|U3|V3] ; BDY[m1|U7]
    BDY[m1|U4] -> INVY[m1|U4]
    BDY[m1|U5] -> INVY[m1|U5]
    BDY[m1|U6] -> INVY[m1|U6]
    BDY[m1|U7] -> INVY[m1|U7]
    ARGS[r1|m0|U0|V0] -> ARG[m0|U0|V0]
    ARGS[r1|m0|U1|V0] -> ARG[m0|U1|V0]
    ARGS[r1|m0|U1|V1] -> ARG[m0|U1|V1]
    ARGS[r1|m0|U2|V0] -> ARG[m0|U2|V0]
    ARGS[r1|m0|U2|V2] -> ARG[m0|U2|V2]
    ARGS[r1|m0|U3|V0] -> ARG[m0|U3|V0]
    ARGS[r1|m0|U3|V1] -> ARG[m0|U3|V1]
    ARGS[r1|m0|U3|V2] -> ARG[m0|U3|V2]
    ARGS[r1|m0|U3|V3] -> ARG[m0|U3|V3]
    ARGS[r2|m0|U0|V0] -> ARG[m0|U0|V0] , ARGS[r1|m0|U0|V0]
    ARGS[r2|m0|U1|V0] -> ARG[m0|U1|V0] , ARGS[r1|m0|U0|V0]
    ARGS[r2|m0|U1|V0] -> ARG[m0|U1|V1] , ARGS[r1|m0|U1|V0]
    ARGS[r2|m0|U1|V1] -> ARG[m0|U1|V1] , ARGS[r1|m0|U1|V1]
    ARGS[r2|m0|U2|V0] -> ARG[m0|U2|V0] , ARGS[r1|m0|U0|V0]
    ARGS[r2|m0|U2|V0] -> ARG[m0|U2|V2] , ARGS[r1|m0|U2|V0]
    ARGS[r2|m0|U2|V2] -> ARG[m0|U2|V2] , ARGS[r1|m0|U2|V2]
    ARGS[r2|m0|U3|V0] -> ARG[m0|U3|V0] , ARGS[r1|m0|U0|V0]
    ARGS[r2|m0|U3|V0] -> ARG[m0|U3|V1] , ARGS[r1|m0|U1|V0]
    ARGS[r2|m0|U3|V0] -> ARG[m0|U3|V2] , ARGS[r1|m0|U2|V0]
    ARGS[r2|m0|U3|V0] -> ARG[m0|U3|V3] , ARGS[r1|m0|U3|V0]
    ARGS[r2|m0|U3|V1] -> ARG[m0|U3|V1] , ARGS[r1|m0|U1|V1]
    ARGS[r2|m0|U3|V1] -> ARG[m0|U3|V3] , ARGS[r1|m0|U3|V1]
    ARGS[r2|m0|U3|V2] -> ARG[m0|U3|V2] , ARGS[r1|m0|U2|V2]
    ARGS[r2|m0|U3|V2] -> ARG[m0|U3|V3] , ARGS[r1|m0|U3|V2]
    ARGS[r2|m0|U3|V3] -> ARG[m0|U3|V3] , ARGS[r1|m0|U3|V3]
    ARG[m0|U0|V0] -> __CHOICE__
    ARG[m0|U1|V1] -> __CHOICE__
    ARG[m0|U1|V0] -> P1
    ARG[m0|U2|V2] -> __CHOICE__
    ARG[m0|U2|V0] -> P2
    ARG[m0|U3|V3] -> __CHOICE__
    ARG[m0|U3|V2] -> P1
    ARG[m0|U3|V1] -> P2
    INVY[m0|U0] -> neg ( ARGS[r1|m0|U0|V0] )
    INVX[m0|U0|V0] -> neg ( ARGS[r1|m0|U0|V0] )
    INVY[m0|U1] -> neg ( ARGS[r1|m0|U1|V0] )
    INVX[m0|U1|V0] -> neg ( ARGS[r1|m0|U1|V0] )
    INVX[m0|U1|V1] -> neg ( ARGS[r1|m0|U1|V1] )
    INVY[m0|U2] -> neg ( ARGS[r1|m0|U2|V0] )
    INVX[m0|U2|V0] -> neg ( ARGS[r1|m0|U2|V0] )
    INVX[m0|U2|V2] -> neg ( ARGS[r1|m0|U2|V2] )
    INVY[m0|U3] -> neg ( ARGS[r1|m0|U3|V0] )
    INVX[m0|U3|V0] -> neg ( ARGS[r1|m0|U3|V0] )
    INVX[m0|U3|V1] -> neg ( ARGS[r1|m0|U3|V1] )
    INVX[m0|U3|V2] -> neg ( ARGS[r1|m0|U3|V2] )
    INVX[m0|U3|V3] -> neg ( ARGS[r1|m0|U3|V3] )
    INVY[m0|U0] -> add ( ARGS[r2|m0|U0|V0] )
    INVX[m0|U0|V0] -> add ( ARGS[r2|m0|U0|V0] )
    INVY[m0|U1] -> add ( ARGS[r2|m0|U1|V0] )
    INVX[m0|U1|V0] -> add ( ARGS[r2|m0|U1|V0] )
    INVX[m0|U1|V1] -> add ( ARGS[r2|m0|U1|V1] )
    INVY[m0|U2] -> add ( ARGS[r2|m0|U2|V0] )
    INVX[m0|U2|V0] -> add ( ARGS[r2|m0|U2|V0] )
    INVX[m0|U2|V2] -> add ( ARGS[r2|m0|U2|V2] )
    INVY[m0|U3] -> add ( ARGS[r2|m0|U3|V0] )
    INVX[m0|U3|V0] -> add ( ARGS[r2|m0|U3|V0] )
    INVX[m0|U3|V1] -> add ( ARGS[r2|m0|U3|V1] )
    INVX[m0|U3|V2] -> add ( ARGS[r2|m0|U3|V2] )
    INVX[m0|U3|V3] -> add ( ARGS[r2|m0|U3|V3] )
    INVY[m0|U0] -> mul ( ARGS[r2|m0|U0|V0] )
    INVX[m0|U0|V0] -> mul ( ARGS[r2|m0|U0|V0] )
    INVY[m0|U1] -> mul ( ARGS[r2|m0|U1|V0] )
    INVX[m0|U1|V0] -> mul ( ARGS[r2|m0|U1|V0] )
    INVX[m0|U1|V1] -> mul ( ARGS[r2|m0|U1|V1] )
    INVY[m0|U2] -> mul ( ARGS[r2|m0|U2|V0] )
    INVX[m0|U2|V0] -> mul ( ARGS[r2|m0|U2|V0] )
    INVX[m0|U2|V2] -> mul ( ARGS[r2|m0|U2|V2] )
    INVY[m0|U3] -> mul ( ARGS[r2|m0|U3|V0] )
    INVX[m0|U3|V0] -> mul ( ARGS[r2|m0|U3|V0] )
    INVX[m0|U3|V1] -> mul ( ARGS[r2|m0|U3|V1] )
    INVX[m0|U3|V2] -> mul ( ARGS[r2|m0|U3|V2] )
    INVX[m0|U3|V3] -> mul ( ARGS[r2|m0|U3|V3] )
    INVX[m0|U0|V0] -> cst ( )
    INVY[m0|U0] -> cst ( )
    INVY[m0|U1] -> P1
    INVY[m0|U2] -> P2
    ARGS[r1|m1|U0|V0] -> ARG[m1|U0|V0]
    ARGS[r1|m1|U1|V0] -> ARG[m1|U1|V0]
    ARGS[r1|m1|U1|V1] -> ARG[m1|U1|V1]
    ARGS[r1|m1|U2|V0] -> ARG[m1|U2|V0]
    ARGS[r1|m1|U2|V2] -> ARG[m1|U2|V2]
    ARGS[r1|m1|U3|V0] -> ARG[m1|U3|V0]
    ARGS[r1|m1|U3|V1] -> ARG[m1|U3|V1]
    ARGS[r1|m1|U3|V2] -> ARG[m1|U3|V2]
    ARGS[r1|m1|U3|V3] -> ARG[m1|U3|V3]
    ARGS[r1|m1|U4|V0] -> ARG[m1|U4|V0]
    ARGS[r1|m1|U4|V4] -> ARG[m1|U4|V4]
    ARGS[r1|m1|U5|V0] -> ARG[m1|U5|V0]
    ARGS[r1|m1|U5|V1] -> ARG[m1|U5|V1]
    ARGS[r1|m1|U5|V4] -> ARG[m1|U5|V4]
    ARGS[r1|m1|U5|V5] -> ARG[m1|U5|V5]
    ARGS[r1|m1|U6|V0] -> ARG[m1|U6|V0]
    ARGS[r1|m1|U6|V2] -> ARG[m1|U6|V2]
    ARGS[r1|m1|U6|V4] -> ARG[m1|U6|V4]
    ARGS[r1|m1|U6|V6] -> ARG[m1|U6|V6]
    ARGS[r1|m1|U7|V0] -> ARG[m1|U7|V0]
    ARGS[r1|m1|U7|V1] -> ARG[m1|U7|V1]
    ARGS[r1|m1|U7|V2] -> ARG[m1|U7|V2]
    ARGS[r1|m1|U7|V3] -> ARG[m1|U7|V3]
    ARGS[r1|m1|U7|V4] -> ARG[m1|U7|V4]
    ARGS[r1|m1|U7|V5] -> ARG[m1|U7|V5]
    ARGS[r1|m1|U7|V6] -> ARG[m1|U7|V6]
    ARGS[r1|m1|U7|V7] -> ARG[m1|U7|V7]
    ARGS[r2|m1|U0|V0] -> ARG[m1|U0|V0] , ARGS[r1|m1|U0|V0]
    ARGS[r2|m1|U1|V0] -> ARG[m1|U1|V0] , ARGS[r1|m1|U0|V0]
    ARGS[r2|m1|U1|V0] -> ARG[m1|U1|V1] , ARGS[r1|m1|U1|V0]
    ARGS[r2|m1|U1|V1] -> ARG[m1|U1|V1] , ARGS[r1|m1|U1|V1]
    ARGS[r2|m1|U2|V0] -> ARG[m1|U2|V0] , ARGS[r1|m1|U0|V0]
    ARGS[r2|m1|U2|V0] -> ARG[m1|U2|V2] , ARGS[r1|m1|U2|V0]
    ARGS[r2|m1|U2|V2] -> ARG[m1|U2|V2] , ARGS[r1|m1|U2|V2]
    ARGS[r2|m1|U3|V0] -> ARG[m1|U3|V0] , ARGS[r1|m1|U0|V0]
    ARGS[r2|m1|U3|V0] -> ARG[m1|U3|V1] , ARGS[r1|m1|U1|V0]
    ARGS[r2|m1|U3|V0] -> ARG[m1|U3|V2] , ARGS[r1|m1|U2|V0]
    ARGS[r2|m1|U3|V0] -> ARG[m1|U3|V3] , ARGS[r1|m1|U3|V0]
    ARGS[r2|m1|U3|V1] -> ARG[m1|U3|V1] , ARGS[r1|m1|U1|V1]
    ARGS[r2|m1|U3|V1] -> ARG[m1|U3|V3] , ARGS[r1|m1|U3|V1]
    ARGS[r2|m1|U3|V2] -> ARG[m1|U3|V2] , ARGS[r1|m1|U2|V2]
    ARGS[r2|m1|U3|V2] -> ARG[m1|U3|V3] , ARGS[r1|m1|U3|V2]
    ARGS[r2|m1|U3|V3] -> ARG[m1|U3|V3] , ARGS[r1|m1|U3|V3]
    ARGS[r2|m1|U4|V0] -> ARG[m1|U4|V0] , ARGS[r1|m1|U0|V0]
    ARGS[r2|m1|U4|V0] -> ARG[m1|U4|V4] , ARGS[r1|m1|U4|V0]
    ARGS[r2|m1|U4|V4] -> ARG[m1|U4|V4] , ARGS[r1|m1|U4|V4]
    ARGS[r2|m1|U5|V0] -> ARG[m1|U5|V0] , ARGS[r1|m1|U0|V0]
    ARGS[r2|m1|U5|V0] -> ARG[m1|U5|V1] , ARGS[r1|m1|U1|V0]
    ARGS[r2|m1|U5|V0] -> ARG[m1|U5|V4] , ARGS[r1|m1|U4|V0]
    ARGS[r2|m1|U5|V0] -> ARG[m1|U5|V5] , ARGS[r1|m1|U5|V0]
    ARGS[r2|m1|U5|V1] -> ARG[m1|U5|V1] , ARGS[r1|m1|U1|V1]
    ARGS[r2|m1|U5|V1] -> ARG[m1|U5|V5] , ARGS[r1|m1|U5|V1]
    ARGS[r2|m1|U5|V4] -> ARG[m1|U5|V4] , ARGS[r1|m1|U4|V4]
    ARGS[r2|m1|U5|V4] -> ARG[m1|U5|V5] , ARGS[r1|m1|U5|V4]
    ARGS[r2|m1|U5|V5] -> ARG[m1|U5|V5] , ARGS[r1|m1|U5|V5]
    ARGS[r2|m1|U6|V0] -> ARG[m1|U6|V0] , ARGS[r1|m1|U0|V0]
    ARGS[r2|m1|U6|V0] -> ARG[m1|U6|V2] , ARGS[r1|m1|U2|V0]
    ARGS[r2|m1|U6|V0] -> ARG[m1|U6|V4] , ARGS[r1|m1|U4|V0]
    ARGS[r2|m1|U6|V0] -> ARG[m1|U6|V6] , ARGS[r1|m1|U6|V0]
    ARGS[r2|m1|U6|V2] -> ARG[m1|U6|V2] , ARGS[r1|m1|U2|V2]
    ARGS[r2|m1|U6|V2] -> ARG[m1|U6|V6] , ARGS[r1|m1|U6|V2]
    ARGS[r2|m1|U6|V4] -> ARG[m1|U6|V4] , ARGS[r1|m1|U4|V4]
    ARGS[r2|m1|U6|V4] -> ARG[m1|U6|V6] , ARGS[r1|m1|U6|V4]
    ARGS[r2|m1|U6|V6] -> ARG[m1|U6|V6] , ARGS[r1|m1|U6|V6]
    ARGS[r2|m1|U7|V0] -> ARG[m1|U7|V0] , ARGS[r1|m1|U0|V0]
    ARGS[r2|m1|U7|V0] -> ARG[m1|U7|V1] , ARGS[r1|m1|U1|V0]
    ARGS[r2|m1|U7|V0] -> ARG[m1|U7|V2] , ARGS[r1|m1|U2|V0]
    ARGS[r2|m1|U7|V0] -> ARG[m1|U7|V3] , ARGS[r1|m1|U3|V0]
    ARGS[r2|m1|U7|V0] -> ARG[m1|U7|V4] , ARGS[r1|m1|U4|V0]
    ARGS[r2|m1|U7|V0] -> ARG[m1|U7|V5] , ARGS[r1|m1|U5|V0]
    ARGS[r2|m1|U7|V0] -> ARG[m1|U7|V6] , ARGS[r1|m1|U6|V0]
    ARGS[r2|m1|U7|V0] -> ARG[m1|U7|V7] , ARGS[r1|m1|U7|V0]
    ARGS[r2|m1|U7|V1] -> ARG[m1|U7|V1] , ARGS[r1|m1|U1|V1]
    ARGS[r2|m1|U7|V1] -> ARG[m1|U7|V3] , ARGS[r1|m1|U3|V1]
    ARGS[r2|m1|U7|V1] -> ARG[m1|U7|V5] , ARGS[r1|m1|U5|V1]
    ARGS[r2|m1|U7|V1] -> ARG[m1|U7|V7] , ARGS[r1|m1|U7|V1]
    ARGS[r2|m1|U7|V2] -> ARG[m1|U7|V2] , ARGS[r1|m1|U2|V2]
    ARGS[r2|m1|U7|V2] -> ARG[m1|U7|V3] , ARGS[r1|m1|U3|V2]
    ARGS[r2|m1|U7|V2] -> ARG[m1|U7|V6] , ARGS[r1|m1|U6|V2]
    ARGS[r2|m1|U7|V2] -> ARG[m1|U7|V7] , ARGS[r1|m1|U7|V2]
    ARGS[r2|m1|U7|V3] -> ARG[m1|U7|V3] , ARGS[r1|m1|U3|V3]
    ARGS[r2|m1|U7|V3] -> ARG[m1|U7|V7] , ARGS[r1|m1|U7|V3]
    ARGS[r2|m1|U7|V4] -> ARG[m1|U7|V4] , ARGS[r1|m1|U4|V4]
    ARGS[r2|m1|U7|V4] -> ARG[m1|U7|V5] , ARGS[r1|m1|U5|V4]
    ARGS[r2|m1|U7|V4] -> ARG[m1|U7|V6] , ARGS[r1|m1|U6|V4]
    ARGS[r2|m1|U7|V4] -> ARG[m1|U7|V7] , ARGS[r1|m1|U7|V4]
    ARGS[r2|m1|U7|V5] -> ARG[m1|U7|V5] , ARGS[r1|m1|U5|V5]
    ARGS[r2|m1|U7|V5] -> ARG[m1|U7|V7] , ARGS[r1|m1|U7|V5]
    ARGS[r2|m1|U7|V6] -> ARG[m1|U7|V6] , ARGS[r1|m1|U6|V6]
    ARGS[r2|m1|U7|V6] -> ARG[m1|U7|V7] , ARGS[r1|m1|U7|V6]
    ARGS[r2|m1|U7|V7] -> ARG[m1|U7|V7] , ARGS[r1|m1|U7|V7]
    ARG[m1|U0|V0] -> __CHOICE__
    ARG[m1|U1|V1] -> __CHOICE__
    ARG[m1|U1|V0] -> P1
    ARG[m1|U2|V2] -> __CHOICE__
    ARG[m1|U2|V0] -> P2
    ARG[m1|U3|V3] -> __CHOICE__
    ARG[m1|U3|V2] -> P1
    ARG[m1|U3|V1] -> P2
    ARG[m1|U4|V4] -> __CHOICE__
    ARG[m1|U4|V0] -> P3
    ARG[m1|U5|V5] -> __CHOICE__
    ARG[m1|U5|V4] -> P1
    ARG[m1|U5|V1] -> P3
    ARG[m1|U6|V6] -> __CHOICE__
    ARG[m1|U6|V4] -> P2
    ARG[m1|U6|V2] -> P3
    ARG[m1|U7|V7] -> __CHOICE__
    ARG[m1|U7|V6] -> P1
    ARG[m1|U7|V5] -> P2
    ARG[m1|U7|V3] -> P3
    INVY[m1|U0] -> neg ( ARGS[r1|m1|U0|V0] )
    INVX[m1|U0|V0] -> neg ( ARGS[r1|m1|U0|V0] )
    INVY[m1|U1] -> neg ( ARGS[r1|m1|U1|V0] )
    INVX[m1|U1|V0] -> neg ( ARGS[r1|m1|U1|V0] )
    INVX[m1|U1|V1] -> neg ( ARGS[r1|m1|U1|V1] )
    INVY[m1|U2] -> neg ( ARGS[r1|m1|U2|V0] )
    INVX[m1|U2|V0] -> neg ( ARGS[r1|m1|U2|V0] )
    INVX[m1|U2|V2] -> neg ( ARGS[r1|m1|U2|V2] )
    INVY[m1|U3] -> neg ( ARGS[r1|m1|U3|V0] )
    INVX[m1|U3|V0] -> neg ( ARGS[r1|m1|U3|V0] )
    INVX[m1|U3|V1] -> neg ( ARGS[r1|m1|U3|V1] )
    INVX[m1|U3|V2] -> neg ( ARGS[r1|m1|U3|V2] )
    INVX[m1|U3|V3] -> neg ( ARGS[r1|m1|U3|V3] )
    INVY[m1|U4] -> neg ( ARGS[r1|m1|U4|V0] )
    INVX[m1|U4|V0] -> neg ( ARGS[r1|m1|U4|V0] )
    INVX[m1|U4|V4] -> neg ( ARGS[r1|m1|U4|V4] )
    INVY[m1|U5] -> neg ( ARGS[r1|m1|U5|V0] )
    INVX[m1|U5|V0] -> neg ( ARGS[r1|m1|U5|V0] )
    INVX[m1|U5|V1] -> neg ( ARGS[r1|m1|U5|V1] )
    INVX[m1|U5|V4] -> neg ( ARGS[r1|m1|U5|V4] )
    INVX[m1|U5|V5] -> neg ( ARGS[r1|m1|U5|V5] )
    INVY[m1|U6] -> neg ( ARGS[r1|m1|U6|V0] )
    INVX[m1|U6|V0] -> neg ( ARGS[r1|m1|U6|V0] )
    INVX[m1|U6|V2] -> neg ( ARGS[r1|m1|U6|V2] )
    INVX[m1|U6|V4] -> neg ( ARGS[r1|m1|U6|V4] )
    INVX[m1|U6|V6] -> neg ( ARGS[r1|m1|U6|V6] )
    INVY[m1|U7] -> neg ( ARGS[r1|m1|U7|V0] )
    INVX[m1|U7|V0] -> neg ( ARGS[r1|m1|U7|V0] )
    INVX[m1|U7|V1] -> neg ( ARGS[r1|m1|U7|V1] )
    INVX[m1|U7|V2] -> neg ( ARGS[r1|m1|U7|V2] )
    INVX[m1|U7|V3] -> neg ( ARGS[r1|m1|U7|V3] )
    INVX[m1|U7|V4] -> neg ( ARGS[r1|m1|U7|V4] )
    INVX[m1|U7|V5] -> neg ( ARGS[r1|m1|U7|V5] )
    INVX[m1|U7|V6] -> neg ( ARGS[r1|m1|U7|V6] )
    INVX[m1|U7|V7] -> neg ( ARGS[r1|m1|U7|V7] )
    INVY[m1|U0] -> add ( ARGS[r2|m1|U0|V0] )
    INVX[m1|U0|V0] -> add ( ARGS[r2|m1|U0|V0] )
    INVY[m1|U1] -> add ( ARGS[r2|m1|U1|V0] )
    INVX[m1|U1|V0] -> add ( ARGS[r2|m1|U1|V0] )
    INVX[m1|U1|V1] -> add ( ARGS[r2|m1|U1|V1] )
    INVY[m1|U2] -> add ( ARGS[r2|m1|U2|V0] )
    INVX[m1|U2|V0] -> add ( ARGS[r2|m1|U2|V0] )
    INVX[m1|U2|V2] -> add ( ARGS[r2|m1|U2|V2] )
    INVY[m1|U3] -> add ( ARGS[r2|m1|U3|V0] )
    INVX[m1|U3|V0] -> add ( ARGS[r2|m1|U3|V0] )
    INVX[m1|U3|V1] -> add ( ARGS[r2|m1|U3|V1] )
    INVX[m1|U3|V2] -> add ( ARGS[r2|m1|U3|V2] )
    INVX[m1|U3|V3] -> add ( ARGS[r2|m1|U3|V3] )
    INVY[m1|U4] -> add ( ARGS[r2|m1|U4|V0] )
    INVX[m1|U4|V0] -> add ( ARGS[r2|m1|U4|V0] )
    INVX[m1|U4|V4] -> add ( ARGS[r2|m1|U4|V4] )
    INVY[m1|U5] -> add ( ARGS[r2|m1|U5|V0] )
    INVX[m1|U5|V0] -> add ( ARGS[r2|m1|U5|V0] )
    INVX[m1|U5|V1] -> add ( ARGS[r2|m1|U5|V1] )
    INVX[m1|U5|V4] -> add ( ARGS[r2|m1|U5|V4] )
    INVX[m1|U5|V5] -> add ( ARGS[r2|m1|U5|V5] )
    INVY[m1|U6] -> add ( ARGS[r2|m1|U6|V0] )
    INVX[m1|U6|V0] -> add ( ARGS[r2|m1|U6|V0] )
    INVX[m1|U6|V2] -> add ( ARGS[r2|m1|U6|V2] )
    INVX[m1|U6|V4] -> add ( ARGS[r2|m1|U6|V4] )
    INVX[m1|U6|V6] -> add ( ARGS[r2|m1|U6|V6] )
    INVY[m1|U7] -> add ( ARGS[r2|m1|U7|V0] )
    INVX[m1|U7|V0] -> add ( ARGS[r2|m1|U7|V0] )
    INVX[m1|U7|V1] -> add ( ARGS[r2|m1|U7|V1] )
    INVX[m1|U7|V2] -> add ( ARGS[r2|m1|U7|V2] )
    INVX[m1|U7|V3] -> add ( ARGS[r2|m1|U7|V3] )
    INVX[m1|U7|V4] -> add ( ARGS[r2|m1|U7|V4] )
    INVX[m1|U7|V5] -> add ( ARGS[r2|m1|U7|V5] )
    INVX[m1|U7|V6] -> add ( ARGS[r2|m1|U7|V6] )
    INVX[m1|U7|V7] -> add ( ARGS[r2|m1|U7|V7] )
    INVY[m1|U0] -> mul ( ARGS[r2|m1|U0|V0] )
    INVX[m1|U0|V0] -> mul ( ARGS[r2|m1|U0|V0] )
    INVY[m1|U1] -> mul ( ARGS[r2|m1|U1|V0] )
    INVX[m1|U1|V0] -> mul ( ARGS[r2|m1|U1|V0] )
    INVX[m1|U1|V1] -> mul ( ARGS[r2|m1|U1|V1] )
    INVY[m1|U2] -> mul ( ARGS[r2|m1|U2|V0] )
    INVX[m1|U2|V0] -> mul ( ARGS[r2|m1|U2|V0] )
    INVX[m1|U2|V2] -> mul ( ARGS[r2|m1|U2|V2] )
    INVY[m1|U3] -> mul ( ARGS[r2|m1|U3|V0] )
    INVX[m1|U3|V0] -> mul ( ARGS[r2|m1|U3|V0] )
    INVX[m1|U3|V1] -> mul ( ARGS[r2|m1|U3|V1] )
    INVX[m1|U3|V2] -> mul ( ARGS[r2|m1|U3|V2] )
    INVX[m1|U3|V3] -> mul ( ARGS[r2|m1|U3|V3] )
    INVY[m1|U4] -> mul ( ARGS[r2|m1|U4|V0] )
    INVX[m1|U4|V0] -> mul ( ARGS[r2|m1|U4|V0] )
    INVX[m1|U4|V4] -> mul ( ARGS[r2|m1|U4|V4] )
    INVY[m1|U5] -> mul ( ARGS[r2|m1|U5|V0] )
    INVX[m1|U5|V0] -> mul ( ARGS[r2|m1|U5|V0] )
    INVX[m1|U5|V1] -> mul ( ARGS[r2|m1|U5|V1] )
    INVX[m1|U5|V4] -> mul ( ARGS[r2|m1|U5|V4] )
    INVX[m1|U5|V5] -> mul ( ARGS[r2|m1|U5|V5] )
    INVY[m1|U6] -> mul ( ARGS[r2|m1|U6|V0] )
    INVX[m1|U6|V0] -> mul ( ARGS[r2|m1|U6|V0] )
    INVX[m1|U6|V2] -> mul ( ARGS[r2|m1|U6|V2] )
    INVX[m1|U6|V4] -> mul ( ARGS[r2|m1|U6|V4] )
    INVX[m1|U6|V6] -> mul ( ARGS[r2|m1|U6|V6] )
    INVY[m1|U7] -> mul ( ARGS[r2|m1|U7|V0] )
    INVX[m1|U7|V0] -> mul ( ARGS[r2|m1|U7|V0] )
    INVX[m1|U7|V1] -> mul ( ARGS[r2|m1|U7|V1] )
    INVX[m1|U7|V2] -> mul ( ARGS[r2|m1|U7|V2] )
    INVX[m1|U7|V3] -> mul ( ARGS[r2|m1|U7|V3] )
    INVX[m1|U7|V4] -> mul ( ARGS[r2|m1|U7|V4] )
    INVX[m1|U7|V5] -> mul ( ARGS[r2|m1|U7|V5] )
    INVX[m1|U7|V6] -> mul ( ARGS[r2|m1|U7|V6] )
    INVX[m1|U7|V7] -> mul ( ARGS[r2|m1|U7|V7] )
    INVX[m1|U0|V0] -> cst ( )
    INVY[m1|U0] -> cst ( )
    INVY[m1|U1] -> P1
    INVY[m1|U2] -> P2
    INVY[m1|U4] -> P3
  """.trimIndent().parseCFG().noEpsilonOrNonterminalStubs.freeze()
}