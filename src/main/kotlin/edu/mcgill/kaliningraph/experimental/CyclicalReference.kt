package edu.mcgill.kaliningraph.experimental

// Immutable circular reference
class Owl(lay: Owl.() -> Egg) { val egg by lazy { lay() } }
class Egg(hatch: () -> Owl) { val owl by lazy { hatch() } }