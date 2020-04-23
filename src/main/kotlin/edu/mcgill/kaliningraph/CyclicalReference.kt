package edu.mcgill.kaliningraph

fun main() {
  val owl = Owl { Egg { this } }
  println(owl)
  println(owl.egg)
  println(owl.egg.owl)
  println(owl.egg.owl.egg)
  println(owl === owl.egg.owl)
  println(owl.egg === owl.egg.owl.egg)
}

// Immutable circular reference
class Owl(lay: Owl.() -> Egg) { val egg by lazy { lay() } }
class Egg(hatch: () -> Owl) { val owl by lazy { hatch() } }