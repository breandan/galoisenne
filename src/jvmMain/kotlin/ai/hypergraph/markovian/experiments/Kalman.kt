package ai.hypergraph.markovian.experiments

//import com.analog.lyric.dimple.model.core.FactorGraph
//import com.analog.lyric.dimple.model.variables.RealJoint
//
//fun main() {
//  val dt = 1.0
//  val gamma = 1.0
//  val m = 1.0
//  val F = arrayOf(
//    doubleArrayOf(1.0, 0.0, dt, 0.0, dt * dt / 2, 0.0, 0.0, 0.0),
//    doubleArrayOf(0.0, 1.0, 0.0, dt, 0.0, dt * dt / 2, 0.0, 0.0),
//    doubleArrayOf(0.0, 0.0, 1.0, 0.0, dt / 2, 0.0, 0.0, 0.0),
//    doubleArrayOf(0.0, 0.0, 0.0, 1.0, 0.0, dt / 2, 0.0, 0.0),
//    doubleArrayOf(0.0, 0.0, -gamma / m, 0.0, 0.0, 0.0, 0.0, 0.0),
//    doubleArrayOf(0.0, 0.0, 0.0, -gamma / m, 0.0, 0.0, 0.0, 0.0),
//    doubleArrayOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0),
//    doubleArrayOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0)
//  )
//
//  //H is the matrix that projects down to the observation.
//  val H = arrayOf(
//    doubleArrayOf(1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
//    doubleArrayOf(0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
//  )
//  val fz = RealJoint(2)
//  val fv = RealJoint(2)
//  val fznonoise = RealJoint(2)
//  val fx = RealJoint(8)
//  val fxnext = RealJoint(8)
//  val fg = FactorGraph().apply {
//    addFactor("constmult", fznonoise, H, fx)
//    addFactor("add", fz, fv, fznonoise)
//    addFactor("constmult", fxnext, F, fx)
//  }
//}