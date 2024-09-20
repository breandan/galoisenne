POPL 2025 Paper #257 Reviews and Comments
===========================================================================
Paper #257 Syntax Repair as Language Intersection


Review #257A
===========================================================================

Overall merit
-------------
1. Reject

Reviewer expertise
------------------
3. Knowledgeable

Paper summary
-------------
This paper presents a method for generating and ranking repairs of invalid strings relative a CFG. Generated repairs are guaranteed to be sound (repaired string is valid wrt grammar) and complete (all valid repairs are generated for a given edit distance, given enough time and resources). The possible repairs are ranked in order of naturalness using a simple statistical technique. The system is implemented in a tool called Tidyparse and evaluated using a dataset of 20k Python syntax errors paired with human repairs.

Comments for authors
--------------------
Strengths:
+ The problem being tackled here is interesting -- syntax errors are not handled well in existing language tooling, and new approaches are needed.

+ The proposed approach is rooted in established techniques and appears to make sensible and theoretically justified choices, notably completeness, while also incorporating statistical techniques in the ranking phase to compete with AI-based techniques.

+ The paper considers a wide variety of related work.

Weaknesses:

- This paper was painfully difficult to read -- from Section 1 onward, the paper uses notations and diagrams without clearly defining or motivating them and makes assumptions about the readers background that are unjustified even at a conference like POPL. The various sections do not clearly flow into one another and the overall system design does not cohere as described. This paper needs a substantial clarity pass with the view of explaining the technique to someone who has not previously encountered these ideas or the corresponding notation. I expand in the detailed comments below.

- The compute needed and the latency of this technique seems prohibitive for normal use in developer tooling.

Detailed comments:

- 38: "not only does this approach guarantee both soundness and completeness, we find it also improves precision when ranking by naturalness" what does it mean to improve precision? precision seems to be defined in abstract as being soundness + completeness
- "Levenshtein ball" is used on Line 39 before it is defined
- Figure 1: I don't understand what the arrow between "Code edits" and "Edit automaton" refers to.
- "known in the literature as the Bar-Hillel construction" could use a citation (even though it was cited earlier)
- The example at the top of Section 2 is appreciated but the details in the rest of this section are inscrutable at this point in the paper. How does the automaton in Figure 2 correspond to 1-edit patches? What are the states and transitions? What is the notation [= (] on the right hand side? How are all of these BH construction rules motivated?
- Definition 3.1 is similarly very difficult to understand without defining a number of other concepts from prior work. What is the notation $\sigma : \bar\ell$ in Definition 3.1? What is the edit language? How is the Levenshtein metric defined?
- Fig. 3 could be substantially clarified with more labeling. What is the distinction between Syntax and CFG? Where is the Chomsky normal form step? What is the relationship between "Code" and "L-NFA"? Why does the arrow from "Increase radius, d" point to L-NFA when there is no d in that box? What is $T_2$? What is $G'_\cap$? What does PCFG stand for?
    - 220: acronym PCFG, presumably probabilistic context free grammar, not defined earlier
    - 221: "(3) translate G_intersection to an equivalent DFA..." what does it mean for a DFA (not PDA) to be equivalent to a CFG?
        - ah Lemma 4.8 shows G_intersection is acyclic which implies finite which implies DFA-modelable
- A citation for Chomsky normal forms is needed when they are introduced.
- Section 4.2 seems to assume the reader already understands how a Levenshtein automaton is constructed and what the notation in FIgure 4 means. The inference rules on page 6 are also quite difficult to understand. What are $I$ and $F$ and $\delta$? What does the premise $q_{i, j}$ appear by itself in the DONE rule? Where did $n$ come from in that rule? It is never stated that the rules are parameterized by $d_\text{max}$. What is $\sigma_i$ in the "right arrow" rule (which is not mentioned in the paragraph below like the other three diagrammatically named rules)?
- the evaluation really should include a comparison with Diekmann and Tratt's Don't Panic! error recovery method and/or the methods cited there
- 301: the given Bar-Hillel construction generates production rules with three symbols on the LHS eg qAr -> a. should qAr be read as a single non-terminal where the states q and r are subscripts of the original non-terminal A? these read at first glance like context-sensitive rules
- 312: is <q, v, q'> meant to be an alternative syntax for nonterminals as described above? be consistent
- 317-351: this part describes how Parikh mappings are used to minimize the size of the desired intersection grammar. found it hard to read at first, Parikh images seemed assumed knowledge at first, then were explained. earlier signposting about how they'd be used would have been appreciated.
- Section 4.4 introduces the (matrix-based) technique for computing grammar-sanctioned hole fillings, and is described in earlier section (Line 355) as being the way to generate a sample-able data structure of repairs, but the paper doesn't seem to describe how these holes are generated in the first place
- 361: "...we will introduce the porous completion problem..." not sure at this point how this relates or why I should care about it
- 382: later learn that its the problem of generating completions for language strings explicitly marked with holes, but still don't know how this connects to earlier sections
- 726: "a variation on a standard metric used in information retrieval" citation?
- 732: why Seq2Parse and BIFI? what do they do (explain at high level here for purposes of interpreting benchmark comparisons even if explained in related work later)? were other alternatives considered? what about Diekmann and Tratt's Don't Panic paper?
- 737-740: says they filter an existing dataset of 20k broken-fixed program pairs based on size and edit distance... how big is the remaining data set??
    - oh... it says later in 771
- 741-752: different machines used for tidyparse compared to related work? no explanation?? 150GB RAM for tidyparse???
- 775-781: describes "stability" of dataset repairs. why should we care about this?
- 787: "we rebalanced the...dataset...across each length interval and edit distance..." what does this mean? is data pruned? is synthetic data generated?
- Fig 8 looks good, but Fig 9 shows it comes at a high latency cost, Fig 11 shows many samples need to be drawn for 2-or-more-edit-distance repairs
- Fig 12: presumably being described in 853-866, but missing explicit reference. caption says 30s while text says 10 seconds. if the goal is to measure throughput, the plot suggests throughput increases as edit distance goes up, but surely that's just a function of there being fewer possible repairs at all for fewer edits?



Review #257B
===========================================================================

Overall merit
-------------
3. Weak accept

Reviewer expertise
------------------
2. Some familiarity

Paper summary
-------------
This paper presents an approach to automatically fixing syntax errors by ranking repairs from the language described by the intersection of the target context-free grammar and a Levenstein automaton derived from the faulty input. The ranking is accomplished by use of a probabilistic model of usual strings in the language. Three methods are proposed for extracting and ranking strings from the intersected grammar, with preference given to an approach that converts to a DFA and samples from it. The technique is evaluated on a dataset with given human repairs and compared to existing tools based on neural network techniques.

Comments for authors
--------------------
My understanding of the hypothesis of this paper is that a combined symbolic and statistical approach to langauge repair should out perform a purely statistical approach in replicating human repairs to broken programs. Such a technique is presented, and the hypothesis is supported by empirical evidence comparing against two existing implementations that are more statistical based.

It took me some time to read this paper, because I got bogged down in the description of the matrix approach to (porous) parsing, and in the details of the algorithm (in particular sections 4.4-4.6). I'm also no expert in empirical evaluations, so I'm trusting the authors that the evlauation is sound.

I think that the approach described in this paper is really interesting, and the evidence that the combined approach described by the authors works is compelling. On the other hand, I thought that some of the technical details could be explained better, in particular the techniques for analysing the intersected grammar. Maybe this is just my background not being aligned with the authors' though.

I have put my thoughts on the sections of the paper below and marked some questions I have for the author response in **bold**.

1. Introduction, explains the problem at a high level, and the plan.

2. Example, using Python. Explains the use of the Levenstein automaton, intersection with the original grammar, conversion to an NFA, then DFA, then weighted sampling using a language model. An optimisation of the original intersection construction is discussed, which will be followed up later.

3. Problem statement: overall plan is, given a grammar and a string not in that grammar, to find a string in the grammar that is (a) within some edit distance of the invalid string, and (b) maximises the probability in a model of "likely" strings in the original language. The hypothesis is that the use of both language intersection and a probabilistic model yields better results than the techniques would do individually. Two techniques are sketched for sampling the intersection language: one is to encode parse trees as integers, sample some integers and then rank them; the other is to convert the intersected grammar to a DFA and then sample that.

4. This section explains the method in detail.

     1. Defines and sets notation for CFGs in Chomsky Normal Form (CNF), and FSMs.
     2. Describes how to construct the Levenstein automaton for a given string and edit distance, which is an NFA. An optimisation is presented to ensure that the size of the automaton does not get too large to make the rest of the construction infeasible.
     3. Describes the construction of the CFG describing the intersection of an automaton and a CFG. Again, the straightforward construction generates CFGs that are too large, so an optimisation based on removing impossible states is presented.
     4. Parsing against grammars in CNF can be expressed as matrix multiplication (does this essentially simulate the CYK chart parsing algorithm?). The porous completion problem is described, where we want to find all strings in a grammar that match a string with holes in it. To be honest I'm not sure I understood this construction.

	    Difference between $2^V$ and $\\mathbb{Z}_2^{|V|}$ is that the former has the subset ordering and the latter has the lexicographic ordering?

	    This section seemed difficult to understand to me because it wasn't described in the introductory sections, so it was difficult for me to understand how it fitted into the overall plan. **Question** Why is the porous completion problem important for what follows?
	 5. This sub-section describes how to represent parse trees for grammars in CNF as an algebraic datatype, and how to construct these for the porous parsing problem using the matrix completion process.

	    **Question**: Is the datatype this, in OCaml notation (where `sigma` is the type of letters in the alphabet, and `v` is the type of non-terminals):

        ```
        type p = Leaf of sigma | Node of v * (v * v * p * p) list
        ```

        **Question** Line 461: What is the 'a' parameter in 'P' for? It doesn't seem to be used?

        **Question** I didn't fully understand the relationships between $\mathbb{T}_2$ and $P$? The former seems to allow an infinite number of children, but the latter only a finite number?

		**Question** Line 486: The text talks about parse forests, but a naive interpretation of the algebraic datatype wouldn't necessarily preserve sharing?
     6. This section shows how to use the representation of parse trees to compute the Parikh map of a CFG for finite strings by first parsing the porous string in the algebraic datatype domain, and then counting the non-terminal occurences in the string.

	    **Question** Isn't this possible without going through the tree representation?
     7. This section describes a method for sampling parse trees from a CFG by using the parse tree representation described in the previous sections. A "direct" solution is given first, but this doesn't work because it doesn't sample uniformly. An alternative solution is to encode parse trees as integers and then uniformly generate integers. As long as the grammar is unambiguous, this is guaranteed to uniformly sample strings.
     8. Given a collection of sampled strings, and a collection of "natural" parsed strings, it is now possible to rank them. Putting everything together, this gives a complete solution to the original problem. Two methods for scoring the generated strings are presented.
     9. If the intersection grammar is ambiguous, then the method of sampling strings described above may generate the same string in multiple ways, leading to non-uniform sampling. Instead, a procedure for generating a DFA is presented, using the fact that the intersection grammar must be acyclic and hence representation as a DFA. DFAs can be easily probabilistically sampled using a Markov chain model of the language.

5. This section presents an experimental evaluation based on a corpus of Python programs. The technique is compared to two existing tools, Seq2Parse and BIFI, both which use neural networks as language models. Ground truth of the "right" repair is taken from human repairs given to StackOverflow.

   The evaluation appears to show that TidyParse (the system described here) has better precision than either of the other tools.

6. This section discusses the results, and points out that TidyParse improves on the precision of neural based techniques, while also requiring less training data and computational power for training. The cost of this is that applying the technique is more compute intensive. Some future work is discussed, including finding intersections of weighed CFGs and automata, which may allow more efficient construction of natural and samplable intersected languages.

7. Discusses related work. This section is (for me) very comprehensive.

8. Concludes.

**Question** The Levenstein automaton construction and the intersection construction are both phrased as inference rules in this work, which indicates that there might be a way of expressing the problem using Logic Programming. Would it be possible to phrase the problem described here as a instance of probabilistic Datalog or Answer Set Programming? Would existing tools for this be useful?



Review #257C
===========================================================================

Overall merit
-------------
1. Reject

Reviewer expertise
------------------
4. Expert

Paper summary
-------------
When a programmer enters a syntactically invalid expression, we might help fix the typo by looking for a small edit that renders the expression syntactically valid and semantically natural. This paper formulates this “syntax repair” problem in an elegant way: we need to intersect the context-free language of syntactically valid expressions with the finite language of strings within a small edit distance of the original. Both languages can be represented compactly—the latter by a polynomial-size automaton whose transitions are labeled by equality and inequality predicates on tokens. The intersection is a finite language, represented by a not-so-compact CFG. To rank its strings for naturalness, we can use the probabilities of a PCFG or Markov model, trained from a corpus of programs (without requiring a corpus of repairs, which is harder to amass). We want to enumerate or sample high-ranking strings in the intersection, without taking a lot of time or memory.

This paper presents this problem formulation and describes some optimizations that may make the computation more efficient, such as pruning productions in the intersection grammar by how many (and how few) terminals each nonterminal can possibly yield, and making it easier to rank the strings in the intersection by representing it as a DFA. The paper also examines an existing corpus of human repairs of Python errors, and evaluates the performance of the new method against BIFI and Seq2Parse, two existing repair systems that require a pairwise repair dataset.

Comments for authors
--------------------
This elegant formulation of the syntax repair problem, and its effective implementation, deserve continuing study and evaluation, which this paper promotes. However, this paper cannot be published in its current state:

# The application is unspecified

How useful a syntax repair system is depends on how it is put to use. Does it run while the programmer is typing, or overnight at StackOverflow? Does it present its suggestions in a drop-down menu, or does it feed them through CI testing? The closest, and earliest, this paper comes to specifying an application use-case is on line 1025: “We envision a few primary uses cases for Tidyparse”. Even that paragraph is too broad to judge which measurements in Section 5 are relevant, and to decide whether Tidyparse at any parameter setting or algorithm combination is useful at all.
- What kind of user has the patience to wait 30 seconds to edit a few tokens? Yet waiting several hours does not “tax the patience of most users” of StackOverflow (line 878).
- Why is higher throughput a good thing, if each suggestion generated requires the programmer’s attention to accept or reject? It seems better to suggest fewer repairs if one of them is correct.
- Why does it matter that other approaches are “trained on a much larger dataset” (line 809)? The claim of “little to no training data” (line 957) seems overblown.

# Related work is rehashed

The POPL audience is familiar with edit-distance FA, CFG-FA intersection, CYK parsing, and overloading the operations of a semiring. So instead of pages 3 and 6 and 8–10 and 14, just say that you take a CFG-FA intersection then compute its CYK parse forest, where each CYK chart arc is labeled not only by a nonterminal but by a DFA or by back-pointers to constituents.

Cube-pruning and counting parse trees are also intuitive for the POPL audience, so it is important to show whether existing techniques suffice for your practical problem of finding highly ranked repairs. What “adapt”ations did you have to perform (line 1135)? In what sense is your approach “complementary” (line 1136, 1146) to existing work, rather than duplicating it?

# The writing is poor

The paper fails to describe why (motivation) then what (specification) before how (implementation). It is a good idea to start the paper with a concrete end-to-end example, and to state the overall problem to be solved. But Section 2 did not talk about ranking or user interaction or application context, and Section 3 did not specify what “much simpler, syntax-oblivious language model” to use for ranking, so the discussion of convergence in the second half of Section 3, and the discussion of sampling until Section 4.7, remain unmotivated. The beginnings of Section 2, Section 3, and Section 4 end up repeating the same broad strokes painting a picture shrouded in the same mystery as to what all this optimization and sampling is for. The paper should first show us what is computed, why it’s worth computing, and how inefficient it is to compute it naively, before bogging us down in details about how to compute it more efficiently.
- Line 102 “We use a variant”: Why?
- Line 124 “our variation”: Why vary?
- Line 146: What is the entirety of “this technique” and why is it worth “scaling up”?
- Line 204: Why “retrieve as many repairs as possible”?
- Line 260: Why “more amenable to our purposes”?
- Line 361: Why should I care about “the porous completion problem”?
- Line 536: What use of this approach determines whether it is “viable” or not? What does “converges extremely poorly” mean, and why is that bad if the language contains both natural and unnatural repairs?
- Line 634, 930: How do “large finite CFLs and language intersections involving highly ambiguous CFGs” arise in “practice” (line 641) with Python?
- Line 972: What is a concrete example of “small or contextually unlikely repairs, which may be overlooked by neural models”, that motivates your approach?
- Line 973: Without specifying the use case, how do you know that “latency and precision are ultimately the deciding usability factors”, or that “repair throughput is a crucial intermediate factor”?

Details are given before motivating setup.
- Line 120: Before talking about what rules a construction has, first say that it produces a context-free grammar, and explain what nonterminals are in the grammar.
- Line 729 finally tells us which algorithm is used, after pages describing three algorithms to choose from. It takes a few more paragraphs before we find out what corpus is used to train this algorithm.
- Why “filter” (line 737)? Why “balance” (line 739)? What number is the “equal number” on line 740?

Unfamiliar terms are used before explanation.
- Line 104, 111, 144: What does “nominal(ize)” mean?
- Line 150, 756: What is “lexical”?
- Line 169, 186: What is $\ell_\cap$?
- Line 183: What is a “generating function”? A function that returns a set (“$2^\ell$”) doesn’t look like the coefficients of a power series.
- Line 204: What are the trajectories being sampled?
- Line 317: “Parikh image” is not explained.
- Line 749: What are “BIFI tokens”?
- Line 772: What counts as “easily”?
- Line 779: What is “caret freedom”?
- Line 859: What counts as “valid”?
- Line 928: What are “higher-order nonterminal dependencies”?
- Line 955–956: What cost is considered “expensive” vs “cheap”?
- Line 958: What constitutes “flexibility and controllability”?
- Line 1074: What are “language equations”?

Notation is obscure and hard to read. It’s hard to tell whether a red tilde is present. A bold sigma in a subscript looks a lot like a non-bold sigma.
- Line 463: Don’t assume that your readers or their printers can distinguish red from blue.

Figures are too small to read. When the text says that something can be observed or seen in a figure (line 771, 801), it is unclear where to look.

In Figures 2 and 4, the states should be arranged in a rectangular array rather than a slanted parallelogram. This way, on line 271 insertions would be handled by arrows pointing up, and the weird gaps in the dots in the second and last cases on line 276 would disappear.

# Minor comments

- Line 65: Isn’t possibility (4) just the original?
- Line 139: What is “the nonterminal I”?
- Line 755–758: Repetitive.
- Line 805, 968: What “fraction”?
- Line 999 “would significantly improve”: why?