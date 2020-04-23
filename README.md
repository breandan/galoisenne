# Kaliningraph

![](https://upload.wikimedia.org/wikipedia/commons/1/15/Image-Koenigsberg%2C_Map_by_Merian-Erben_1652.jpg)

## Graphs, Inductively

What are graphs? A graph is a (possibly empty) set of nodes.

What are nodes? A node is a unique integer with neighbors (possibly containing itself).

What are neighbors? Neighbors are a graph.

## Requirements

Kaliningraph uses [KraphViz](https://github.com/nidi3/graphviz-java#kotlin-dsl) for graph visualization.

## Getting Started

Run [the demo](src/main/kotlin/edu/mcgill/kaliningraph/HelloKaliningraph.kt) via `./gradlew HelloKaliningraph` to get started.

## References

* [Graph Representation Learning](https://cs.mcgill.ca/~wlh/comp766/notes.html), William Hamilton
* [Account of the Icosian Calculus](http://www.kurims.kyoto-u.ac.jp/EMIS/classics/Hamilton/PRIAIcos.pdf), William (Rowan) Hamilton
* [Functional programming with structured graphs](http://www.cs.utexas.edu/~wcook/Drafts/2012/graphs.pdf), Bruno Oliveira and William Cook
* [Think Like a Vertex, Behave Like a Function! A Functional DSL for Vertex-Centric Big Graph Processing](http://research.nii.ac.jp/~hu/pub/icfp16.pdf), Kento Emoto et al.
* [Inductive Graphs and Functional Graph Algorithms](http://citeseerx.ist.psu.edu/viewdoc/download?doi=10.1.1.28.9377&rep=rep1&type=pdf), Martin Erwig
* [Fully Persistent Graphs – Which One To Choose?](https://s3.amazonaws.com/academia.edu.documents/15079317/implementation_of_functional_languages__9_conf.__iflsharp97%28lncs1467__springer__1998%29%28isbn_3540648496%29%28382s%29.pdf?response-content-disposition=inline%3B%20filename%3DNaira_A_parallel_2_Haskell_compiler.pdf&X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=ASIATUSBJ6BABXJXRH5M%2F20200423%2Fus-east-1%2Fs3%2Faws4_request&X-Amz-Date=20200423T075605Z&X-Amz-Expires=3600&X-Amz-Security-Token=IQoJb3JpZ2luX2VjEC8aCXVzLWVhc3QtMSJGMEQCIG5m9WlYOLS9BNCScy6FMKQNrv%2FjYmZggcA2VFTT1RoQAiB0OpOpjD%2F%2BEuJCTbVfdZ11V2VxnYc3bFfGZjcgx2MuKSq0AwhYEAAaDDI1MDMxODgxMTIwMCIMQ1SSGBeof5SCebxdKpEDo562ivybD5xpON5zgly8GVqLlfJKaMs0bTgr1AnJWM5ak9iqPSs0GX4tl%2BwnY0tpf%2Fhs5w5OaaNVHaRvO0yMB3DY7NLZ7ud%2F4hcPIuH0aWUIhHgFv6%2Fpt8qaF9n3%2B4ThoFOJSONp4SCXWWXHDl%2F3crPbWGjUU%2Fx2w%2B4%2FJDlfVTT4bwGFO7k7%2FIh6eCWVuvhPrPLW5gdUKudr11A9%2B4kRabZ5k0X%2Fj6lxNW%2BjIuk%2BmTvJdQQ7HhkFCe962uJzJhoDhJ1F1X8Qn6yXQJN0Oitm%2BqH8F5lewC35kS4nu57huUoIORDsXRXfNlIdNhcHwZ2TaVmiKdvwZ5lviJfGV5DitpfiXCM9QwrFCfKIEizIWlw6Tp%2FyilCcyLB%2Fr13fqBIBYT6X3%2Bf3TyiCCUQukqxKWHQG1%2FIJPXcvDAxApEemInXmUZbeo%2FOsUB0RfRqzzQ8Sh4%2Fht4tsfVcItJLvYbYaVUMm33ChLHi8a4SSVeSAORSvQ1ylJoMZpwMtG6tXONR1MymFDqbAEpS039dMb4%2F6Jmkwh%2FmE9QU67AFiGv8U4v%2FRjhs2FRGjPjORPw17yvIhK8cYQ5gQaoA41VXc%2BHtoNuXhQbKKRLtJKeF9GF7z8%2BvAbT4z5jNdHBNoCSs9QuV0srf23hNhH0%2FfbKbtfry2lzDhkdZzqjKkI8vQ3VTAv4T8tyGPkMBvvAG4REL0H4YvFAa5cI3M%2Fp7soPGFAVgdemSwUKQANP0WnqC%2BGab1oy%2BnFAEfgD4eQ2BqYCIzhlifRm0h67KukIxOzbQsDL87uVb8YhTimbiI7oAw%2FbzBxn8vs0tt213Snu6jaG%2BBIFr0PsfPnDyy2cbNUiDBJkI2h5xEYHI2vg%3D%3D&X-Amz-SignedHeaders=host&X-Amz-Signature=93ba97905d8dd38781f329926382290ba6537e827e3dc1fb82d8db11f753a494#page=131)
* [Solutio problematis ad geometriam situs pertinentis](http://eulerarchive.maa.org/docs/originals/E053.pdf), Leonhard Euler