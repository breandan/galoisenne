# Kaliningraph

[![](https://jitpack.io/v/breandan/kaliningraph.svg)](https://jitpack.io/#breandan/kaliningraph)
[![CI](https://github.com/breandan/kaliningraph/workflows/CI/badge.svg)](https://github.com/breandan/kaliningraph/actions)

Kaliningraph is a purely functional graph library with a DSL for constructing graphs and visualizing the behavior of graph algorithms.

![](http://breandan.net/images/konigsberg_bridges.png)

## Installation

Kaliningraph is hosted on [JitPack](https://jitpack.io/#breandan/kaliningraph/).

### Gradle

```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.breandan:kaliningraph:-SNAPSHOT")
}
```

### Maven

```xml
<project>
  <repositories>
    <repository>
      <id>jitpack.io</id>
      <url>https://jitpack.io</url>
    </repository>
  </repositories>
  
  <dependency>
    <groupId>com.github.breandan</groupId>
    <artifactId>kaliningraph</artifactId>
    <version>0.0.1</version>
  </dependency>
</project>
```

### Jupyter Notebook

[First install](https://github.com/kotlin/kotlin-jupyter#installation) the Kotlin Jupyter kernel, then run the `jupyterInstall` task to install the library descriptor:

```
./gradlew jupyterInstall [-Path=~/.jupyter_kotlin/libraries]
```

To access Kotlin∇'s notebook support, use the following line magic:

```
%use kotlingrad
```

For more information, explore the [tutorial](notebooks/Hello%20Kaliningraph.ipynb).

## Graphs, Inductively

What are graphs? A [graph](src/main/kotlin/edu/mcgill/kaliningraph/Graph.kt) is a (possibly empty) set of vertices.

What are vertices? A vertex is a unique label with neighbors (possibly containing itself).

What are neighbors? Neighbors are a graph.

## Getting Started

Run [the demo](src/main/kotlin/edu/mcgill/kaliningraph/HelloKaliningraph.kt) via `./gradlew HelloKaliningraph` to get started.

## Usage

To construct a graph, the [graph builder DSL](src/main/kotlin/edu/mcgill/kaliningraph/LabeledGraph.kt) provides an small alphabet:

```kotlin
val graph = Graph { a - b - c - d - e; a - c - e }
```

This is the same as:

```kotlin
val abcde = Graph { a - b - c - d - e }
val ace = Graph { a - c - e }
val graph = abcde + ace
```

Equality is supported using the [Weisfeiler-Lehman](http://www.jmlr.org/papers/volume12/shervashidze11a/shervashidze11a.pdf#page=6) test:

```kotlin
val x = Graph { a - b - c - d - e; a - c - e }
val y = Graph { b - c - d - e - f; b - d - f }
assertEquals(x == y) // true
```

## Visualization

Kaliningraph supports a number of graph visualizations.

### Graphviz

Graph visualization is made possible thanks to [KraphViz](https://github.com/nidi3/graphviz-java#kotlin-dsl).

```kotlin
val de = Graph { d - e }
val dacbe = Graph { d - a - c - b - e }
val dce = Graph { d - c - e }

val abcd = Graph { a - b - c - d }
val cfde = Graph { c - "a" - f - d - e }

val dg = Graph(dacbe, dce, de) + Graph(abcd, cfde)
dg.show()
```

Running the above snippet will cause the following figure to be rendered in the browser:

![](latex/figures/visualization.svg)

### Matrix form

Graph visualization in both DOT and adjacency matrix format is supported.

|DOT Graph|Matrix|
|-----|------|
|![image](latex/figures/random_dot_graph.png)|![image_1](latex/figures/random_matrix.png)|

It is also possible to visualize the state and transition matrices and step through the graph (`./gradlew PrefAttach`).

![transition_diagram](latex/figures/transition_diagram.png)

### Computation graph

Computational notebooks prototyping is also supported.

```
Notebook {
  a = b + c
  f = b - h
}.show()
```

The above snippet should display something like the following:

![](pdg_demo.svg)

## Translation

Bidirectional translation to various graph formats, including [Graphviz](https://github.com/nidi3/graphviz-java), [JGraphT](https://jgrapht.org/guide/UserOverview), [Tinkerpop](https://tinkerpop.apache.org/docs/current/reference/) and [RedisGraph](https://oss.redislabs.com/redisgraph/) is supported:

```kotlin
val g = Graph { a - b - c - a }
        .toJGraphT().toKaliningraph()
        .toTinkerpop().toKaliningraph()
        .toGraphviz().toKaliningraph()
```

## Code2Vec

Code2Vec generation and visualization is supported. The following demo was generated using message passing on the adjacency matrix, for graphs of varying height. The technique to create the embeddings is described [here](https://www.cs.mcgill.ca/~wlh/grl_book/files/GRL_Book-Chapter_5-GNNs.pdf#page=6). We use TSNE to visualize the resulting vectors in 2D, and can clearly distinguish the clusters.

![](src/main/resources/clusters.svg)

## Automata-Based Regex

A regex to NFA compiler is provided. To run the demo, run `./gradlew RegexDemo`. You should see something like this:

![](regex_demo.png)

## Research Questions

* How could we implement graph rewriting?
   - Is there an algebraic definition for graph grammars?
   - Maybe graph convolution. How to encode rewrites as a kernel?
   - Rectangular matrix multiplication or square with upper bound?
   - Maybe possible to represent using tensor contraction
   - Need to look into hyperedge replacement grammars
   - How do we identify confluent rewrite systems?
* What are the advantages and disadvantages of graph rewriting? 
   - Graphs as vertices and rewrites as edges in a nested graph?
   - Reduction/canonicalization versus expansion graph grammar
* What happens if we represent the graph as a symbolic matrix?
   - Could we propogate functions instead of just values?
   - What if matrix elements were symbolic expressions?
   - Should we represent the whole matrix as a big bold symbol?
* Is there an efficient way to parallelize arithmetic circuits?
   - Translate formula graph to matrix using Miller's evaluator
   - How to distribute the work evenly across sparse matrices   
* What are some good way to [visualize](https://setosa.io/ev/markov-chains/) random walks?
   - Display states, transitions and graph occupancy side-by-side
* What is the connection between belief and error propagation?
   - Look into Turing's [unorganized machines](https://weightagnostic.github.io/papers/turing1948.pdf#page=8)
* Is there a connection between linear algebra and λ-calculus?
   - λ expressions can be represented as a graph/matrix
   - Maybe [Arrighi and Dowek](https://lmcs.episciences.org/3203/pdf) (2017) have the answer?
   - Look into [optimal beta reduction](https://www.youtube.com/channel/UCKQa6Ls95RhShE0kQsiXzVw) and Lamping's [optimal reduction algorithm](https://doi.org/10.1145%2F96709.96711)

## References

### Graph theory

* [Solutio problematis ad geometriam situs pertinentis](http://eulerarchive.maa.org/docs/originals/E053.pdf), Leonhard Euler
* [Account of the Icosian Calculus](http://www.kurims.kyoto-u.ac.jp/EMIS/classics/Hamilton/PRIAIcos.pdf), William (Rowan) Hamilton
* [Mathematical Foundations of the GraphBLAS](https://arxiv.org/pdf/1606.05790.pdf)
* [Graph Algorithms in the Language of Linear Algebra](https://epubs.siam.org/doi/book/10.1137/1.9780898719918)

### Graph learning

* [Graph Representation Learning](https://www.cs.mcgill.ca/~wlh/grl_book/), William Hamilton (2020)
* [Spectral Graph Theory with Applications to ML](http://www.cs.cmu.edu/afs/cs/academic/class/15859n-s20/recordings.html), Gary Miller (2020)
* [Neural Execution of Graph Algorithms](https://arxiv.org/abs/1910.10593), Veličković et al. (2020)

### Functional graphs

* [Functional programming with structured graphs](http://www.cs.utexas.edu/~wcook/Drafts/2012/graphs.pdf), Bruno Oliveira and William Cook
* [Think Like a Vertex, Behave Like a Function! A Functional DSL for Vertex-Centric Big Graph Processing](http://research.nii.ac.jp/~hu/pub/icfp16.pdf), Kento Emoto et al.
* [Inductive Graphs and Functional Graph Algorithms](http://web.engr.oregonstate.edu/~erwig/papers/InductiveGraphs_JFP01.pdf), Martin Erwig
* [Fully Persistent Graphs – Which One To Choose?](http://web.engr.oregonstate.edu/~erwig/papers/PersistentGraphs_IFL97.pdf), Erwig
* [The Program Dependence Graph and its Use for Optimization](https://www.cs.utexas.edu/~pingali/CS395T/2009fa/papers/ferrante87.pdf), Ferrante

### Graph Rewriting

- [Equational term graph rewriting](https://www.researchgate.net/profile/Jan_willem_Klop/publication/2688773_Equational_Term_Graph_Rewriting/links/53ce68260cf2b8e35d148342.pdf), Ariola
- [Bisimilarity in Term Graph Rewriting](https://doi.org/10.1006/inco.1999.2824), Ariola
- [LEAN: An intermediate language based on graph rewriting](https://doi.org/10.1016/0167-8191(89)90126-9), Barendregt
- [An Algorithm for Optimal Lambda Calculus Reduction](https://dl.acm.org/doi/pdf/10.1145/96709.96711), Lamping
- [A New Implementation Technique for Applicative Languages](https://doi.org/10.1002/spe.4380090105), Turner
- [A Reformulation of Matrix Graph Grammars with Boolean Complexes](https://www.emis.de/journals/EJC/ojs/index.php/eljc/article/view/v16i1r73/pdf) Velasco, Juan de Lara
- [Towards a GPU-based implementation of interaction nets](https://arxiv.org/pdf/1404.0076.pdf), Jiresch

#### Unification

- [Graph Unification and Matching](https://www.cs.york.ac.uk/plasma/publications/pdf/PlumpHabel.96.pdf), Plump
- [Unification with Drags](https://hal.inria.fr/hal-02562152/document)
- [The identity problem for elementary functions and constants](https://dl.acm.org/doi/pdf/10.1145/190347.190429)

#### Termination checking

- [Proving Termination of Graph Transformation Systems using Weighted Type Graphs over Semirings](https://arxiv.org/pdf/1505.01695.pdf), Bruggink
- [Termination of string rewriting with matrix interpretations](https://www.imn.htwk-leipzig.de/~waldmann/talk/06/rta/rta06.pdf), Hofbauer
- [Matrix Interpretations for Proving Termination of Term Rewriting](https://link.springer.com/content/pdf/10.1007/s10817-007-9087-9.pdf), Endrullis et al.
- [Graph Path Orderings](https://hal.inria.fr/hal-01903086/document#page=2), Dershowitz and Jouannaud

### Algebra

- [Algebraic Graphs with Class (Functional Pearl)](https://github.com/snowleopard/alga-paper/releases/download/final/algebraic-graphs.pdf), Mokhov (2017)
- [Fun with Semirings](http://stedolan.net/research/semirings.pdf), Dolan (2013)
- [Introduction to Algebraic Theory of Graph Grammars](https://doi.org/10.1007/BFb0025714), Erhig
- [Drags: A Simple Algebraic Framework For Graph Rewriting](https://hal.inria.fr/hal-01853836/document), Dershowitz and Jouannaud
- [An Algebraic Theory of Graph Reduction](https://dl.acm.org/doi/pdf/10.1145/174147.169807#page=19), Arnborg
- [Lineal: A linear-algebraic λ-calculus](https://lmcs.episciences.org/3203/pdf), Arrighi and Dowek
- [Graph products](https://en.wikipedia.org/wiki/Graph_product), Wikipedia
- [Graphs and Geometry](http://web.cs.elte.hu/~lovasz/bookxx/geomgraphbook/geombook2019.01.11.pdf), Lovász

### Circuits

- [Efficient parallel evaluation of straight-line code and arithmetic circuits](http://www.cs.cmu.edu/~glmiller/Publications/MRK86b.pdf), Miller (1986)
- [Arithmetic Circuit Verification Based on Word-Level Decision Diagrams](https://apps.dtic.mil/dtic/tr/fulltext/u2/a350486.pdf), Chen (1998)
- [An Efficient Graph Representation for Arithmetic Circuit Verification](https://doi.org/10.1109/43.969437), Chen and Bryant (2001)
- [A Top-Down Compiler for Sentential Decision Diagrams](https://www.ijcai.org/Proceedings/15/Papers/443.pdf), Oztok and Darwiche (2015)
- [Complexities of Graph-Based Representations for Elementary Functions](https://doi.org/10.1109/TC.2008.134), Nagayama and Sasao (2008)
- [Numerical Function Generators Using LUT Cascades](https://doi.org/10.1109/TC.2007.1033), Sasao and Nagayama (2007)

### Random Walks

- [Random Walks on Graphs: A Survey](https://web.cs.elte.hu/~lovasz/erdos.pdf), Lovász (1993)
- [String Edit Distance, Random Walks and Graph Matching](https://link.springer.com/content/pdf/10.1007/3-540-70659-3_10.pdf), Kelly and Hancock (2002)
- [Exact and Approximate Graph Matching Using Random Walks](https://ieeexplore.ieee.org/stamp/stamp.jsp?arnumber=1432743), Gori and Maggini (2005)
- [Reweighted random walks for graph matching](https://www.researchgate.net/profile/Minsu_Cho/publication/221304918_Reweighted_Random_Walks_for_Graph_Matching/links/54c50ae80cf256ed5a98633c.pdf), Cho and Lee (2010)
- [Small Subgraphs in the trace of a random walk](https://arxiv.org/pdf/1605.04585.pdf), Krivelevich and Michaeli (2018)
- [Biased random walk on the trace of a biased random walk on the trace of...](https://arxiv.org/pdf/1901.04673.pdf), Crydon and Holmes (2019)
- [KnightKing: A Fast Distributed Graph Random Walk Engine](https://dl.acm.org/doi/pdf/10.1145/3341301.3359634) (2019)

### Software Engineering

* [Getting F-Bounded Polymorphism into Shape](https://www.cs.cornell.edu/~ross/publications/shapes/shapes-pldi14-tr.pdf), Tate
* [Frequent Subgraph Analysis and its Software Engineering Applications](https://etd.ohiolink.edu/!etd.send_file?accession=case1496835753068605), Henderson
* [Semantic Enrichment of Data Science Code](https://arxiv.org/pdf/2006.08945.pdf#chapter.6), Patterson
* [Finally, a Polymorphic Linear Algebra Language](https://drops.dagstuhl.de/opus/volltexte/2019/10817/pdf/LIPIcs-ECOOP-2019-25.pdf)
* [Towards an API for the Real Numbers](https://dl.acm.org/doi/pdf/10.1145/3385412.3386037)

### Proof Search

- [Generative Language Modeling for Automated Theorem Proving](https://arxiv.org/pdf/2009.03393.pdf) Polu et al., 2020
- [Towards Proof Synthesis Guided by Neural Machine Translation for Intuitionistic Propositional Logic](https://arxiv.org/pdf/1706.06462.pdf) Sekiyama, 2020
- [Can Neural Networks Learn Symbolic Rewriting?](https://arxiv.org/pdf/1911.04873.pdf) Piotrowski et al., 2020
- [Tree Neural Networks in HOL4](https://arxiv.org/pdf/2009.01827.pdf) Gauthier, 2020
- [Modelling High-Level Mathematical Reasoning in Mechanised Declarative Proofs](https://arxiv.org/pdf/2006.09265.pdf) Li et al., 2020

### Software

#### Graphs

* [Alga](https://github.com/snowleopard/alga) - a library for algebraic construction and manipulation of graphs in Haskell
* [Bifurcan](https://github.com/lacuna/bifurcan) - high-quality JVM implementations of immutable data structures
* [Kraphviz](https://github.com/nidi3/graphviz-java#kotlin-dsl) - Graphviz with pure Java
* [JGraLab](https://github.com/jgralab/jgralab) - a Java graph library implementing [TGraphs](https://github.com/jgralab/jgralab/wiki/TGraphs): typed, attributed, ordered, and directed graphs ([paper](https://www.researchgate.net/profile/Juergen_Ebert2/publication/228566960_Using_the_TGraph_approach_for_model_fact_repositories/links/09e41509259d33a161000000/Using-the-TGraph-approach-for-model-fact-repositories.pdf))
* [GraphBLAS](http://graphblas.org) - open effort to define standard building blocks for graph algorithms in the language of linear algebra
* [GraphBLAST](https://github.com/gunrock/graphblast) - High-Performance Linear Algebra-based Graph Primitives on GPUs

#### Rewriting

* [Grez](http://www.ti.inf.uni-due.de/research/tools/grez/) - graph transformation termination checker ([manual](http://www.ti.inf.uni-due.de/fileadmin/public/tools/grez/grez-manual.pdf))
* [GP2](https://github.com/UoYCS-plasma/GP2) - Rule-based graph programming language
* [AGG](https://www.user.tu-berlin.de/o.runge/agg/) - development environment for attributed graph transformation systems supporting an algebraic approach to graph transformation ([manual](http://www.informatik.uni-bremen.de/agbkb/lehre/rbs/seminar/AGG-ShortManual.pdf))
* [Henshin](https://github.com/de-tu-berlin-tfs/Henshin-Editor) - an IDE for developing and simulating triple graph grammars (TGGs) ([manual](https://wiki.eclipse.org/Henshin))
* [JavaSMT](https://github.com/sosy-lab/java-smt) - Unified Java API for SMT solvers

#### Automata

* [roll-library](https://github.com/ISCAS-PMC/roll-library)
* [dk.brics.automata](https://github.com/cs-au-dk/dk.brics.automaton)
* [LearnLib](https://github.com/Learnlib/learnlib)
