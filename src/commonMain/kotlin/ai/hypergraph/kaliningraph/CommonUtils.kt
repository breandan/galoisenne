package ai.hypergraph.kaliningraph

operator fun IntRange.times(s: IntRange) =
  flatMap { l -> s.map { r -> l to r }.toSet() }.toSet()

// Returns the Cartesian product of two sets
operator fun <T, Y> Set<T>.times(s: Set<Y>): Set<Pair<T, Y>> =
  flatMap { l -> s.map { r -> l to r }.toSet() }.toSet()
