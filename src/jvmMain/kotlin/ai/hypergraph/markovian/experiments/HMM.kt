package ai.hypergraph.markovian.experiments///*******************************************************************************
// * Copyright 2012 Analog Devices, Inc.
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// * http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//import cern.colt.Arrays
//import com.analog.lyric.dimple.factorfunctions.core.FactorFunction
//import com.analog.lyric.dimple.model.core.FactorGraph
//import com.analog.lyric.dimple.model.domains.DiscreteDomain
//import com.analog.lyric.dimple.model.values.Value
//import com.analog.lyric.dimple.model.variables.Discrete
//import com.analog.lyric.dimple.options.BPOptions
//import kotlin.math.ln
//
//fun main() {
//  val domain: DiscreteDomain = DiscreteDomain.create("sunny", "rainy")
//  val MondayWeather = Discrete(domain)
//  val TuesdayWeather = Discrete(domain)
//  val WednesdayWeather = Discrete(domain)
//  val ThursdayWeather = Discrete(domain)
//  val FridayWeather = Discrete(domain)
//  val SaturdayWeather = Discrete(domain)
//  val SundayWeather = Discrete(domain)
//  val trans = TransitionFactorFunction()
//  val HMM = FactorGraph().apply {
//    addFactor(trans, MondayWeather, TuesdayWeather)
//    addFactor(trans, TuesdayWeather, WednesdayWeather)
//    addFactor(trans, WednesdayWeather, ThursdayWeather)
//    addFactor(trans, ThursdayWeather, FridayWeather)
//    addFactor(trans, FridayWeather, SaturdayWeather)
//    addFactor(trans, SaturdayWeather, SundayWeather)
//    val obs = ObservationFactorFunction()
//    addFactor(obs, MondayWeather, "walk")
//    addFactor(obs, TuesdayWeather, "walk")
//    addFactor(obs, WednesdayWeather, "cook")
//    addFactor(obs, ThursdayWeather, "walk")
//    addFactor(obs, FridayWeather, "cook")
//    addFactor(obs, SaturdayWeather, "book")
//    addFactor(obs, SundayWeather, "book")
//    MondayWeather.setPrior(0.7, 0.3)
//    setOption(BPOptions.iterations, 20)
//    solve()
//  }
//
//  val belief = TuesdayWeather.belief
//  println(Arrays.toString(belief))
//}
//
//class TransitionFactorFunction: FactorFunction() {
//  override fun evalEnergy(args: Array<Value>): Double {
//    val state1 = args[0].getObject() as String?
//    val state2 = args[1].getObject() as String?
//    val value = if (state1 == "sunny") {
//      if (state2 == "sunny") 0.8 else 0.2
//    } else {
//      0.5
//    }
//    return -ln(value)
//  }
//}
//
//class ObservationFactorFunction: FactorFunction() {
//  override fun evalEnergy(args: Array<Value>): Double {
//    val state = args[0].getObject() as String?
//    val observation = args[1].getObject() as String?
//    val value = if (state == "sunny") when (observation) {
//      "walk" -> 0.7
//      "book" -> 0.1
//      else  // cook
//      -> 0.2
//    } else when (observation) {
//      "walk" -> 0.2
//      "book" -> 0.4
//      else  // cook
//      -> 0.4
//    }
//    return -ln(value)
//  }
//}