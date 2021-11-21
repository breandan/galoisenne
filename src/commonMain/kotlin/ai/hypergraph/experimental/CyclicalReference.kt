package ai.hypergraph.experimental

// Immutable circular reference
class Owl(lay: (Owl) -> Egg) { val egg by lazy { lay(this) } }
class Egg(hatch: () -> Owl) { val owl by lazy { hatch() } }