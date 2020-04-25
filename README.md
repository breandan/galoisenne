# Kaliningraph

Kaliningraph is a purely functional graph library with a DSL for constructing graphs and visualizing the behavior of graph algorithms.

![](https://upload.wikimedia.org/wikipedia/commons/1/15/Image-Koenigsberg%2C_Map_by_Merian-Erben_1652.jpg)

## Graphs, Inductively

What are graphs? A [graph](src/main/kotlin/edu/mcgill/kaliningraph/Graph.kt) is a (possibly empty) set of vertices.

What are vertices? A [vertex](src/main/kotlin/edu/mcgill/kaliningraph/Vertex.kt) is a unique label with neighbors (possibly containing itself).

What are neighbors? Neighbors are a graph.

## Requirements

Kaliningraph uses [KraphViz](https://github.com/nidi3/graphviz-java#kotlin-dsl) for graph visualization.

## Getting Started

Run [the demo](src/main/kotlin/edu/mcgill/kaliningraph/HelloKaliningraph.kt) via `./gradlew HelloKaliningraph` to get started.

## Usage

To construct a graph, the [graph builder DSL](src/main/kotlin/edu/mcgill/kaliningraph/GraphBuilder.kt) provides an small alphabet:

```kotlin
val graph = Graph { a - b - c - d - e; a - c - e }
```

This is the same as:

```kotlin
val abcde = Graph { a - b - c - d - e }
val ace = Graph { a - c - e }
val graph = abcde + ace
```

Equality is supported using the Weisfeiler-Lehman test:

```kotlin
val x = Graph { a - b - c - d - e; a - c - e }
val y = Graph { b - c - d - e - f; b - d - f }
assertEquals(x == y) // true
```

## References

* [Graph Representation Learning](https://cs.mcgill.ca/~wlh/comp766/notes.html), William Hamilton
* [Account of the Icosian Calculus](http://www.kurims.kyoto-u.ac.jp/EMIS/classics/Hamilton/PRIAIcos.pdf), William (Rowan) Hamilton
* [Functional programming with structured graphs](http://www.cs.utexas.edu/~wcook/Drafts/2012/graphs.pdf), Bruno Oliveira and William Cook
* [Think Like a Vertex, Behave Like a Function! A Functional DSL for Vertex-Centric Big Graph Processing](http://research.nii.ac.jp/~hu/pub/icfp16.pdf), Kento Emoto et al.
* [Inductive Graphs and Functional Graph Algorithms](http://citeseerx.ist.psu.edu/viewdoc/download?doi=10.1.1.28.9377&rep=rep1&type=pdf), Martin Erwig
* [Fully Persistent Graphs – Which One To Choose?](http://www.academia.edu/download/15079317/implementation_of_functional_languages__9_conf.__iflsharp97(lncs1467__springer__1998)(isbn_3540648496)(382s).pdf#page=131)
* [Solutio problematis ad geometriam situs pertinentis](http://eulerarchive.maa.org/docs/originals/E053.pdf), Leonhard Euler