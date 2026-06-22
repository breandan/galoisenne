POPL 2026 Paper #252 Reviews and Comments
===========================================================================
Paper #252 Syntax Repair as Language Intersection


Review #252A
===========================================================================

Overall merit
-------------
D. Reject

Reviewer expertise
------------------
Z. Outsider

Paper summary
-------------
The paper addresses the problem of repairing syntax errors in context-free languages (CFLs), motivated by the common scenario in which a programmer’s code is temporarily unparseable due to small typographic mistakes. The paper proposes to automatically generate a short list of candidate repairs that are guaranteed to contain the intended fix if it lies within a bounded edit distance from the original code.

The proposed method begins by constructing a finite regular language representing all possible repairs within the given edit bound. This finite repair language is then intersected with the target language's grammar using a variant of the Bar-Hillel construction, ensuring that only syntactically valid candidates remain. For enumerating the resulted set, the paper adapt Brzozowski’s derivative (previously used for regex membership testing) to generate valid witnesses.  A decision procedure is presented for determining repairability in polylogarithmic parallel time, based on the  finiteness and regularity of the repair set, and also a prototype is implemneted. An empirical evaluation on a Python syntax repair benchmark shows higher accuracy than the state-of-the-art approaches up to five times more.

\textbf{Evaluation.}
The proposed approach is conceptually interesting: intersecting a CFL with a bounded-edit regular language provides a clear formalization of the repair search space, and the use of Brzozowski derivatives for enumeration is reasonable. The combination of classical results from formal language theory (Bar-Hillel intersection, regex derivatives) with practical syntax repair tooling  is of interest. The experimental comparison to existing Python repair systems suggests that the method can be competitive while maintaining reasonable operational response times.

However, the paper suffers from a weak presentation style and a lack of rigorous formal description. The notation is underdeveloped, and several theorems are stated without proof sketches or formal statements of soundness and completeness. I found the paper difficult to read, often having to stop and think/search for possible interpretations of the used notation. These issues limit the paper’s accessibility and the verifiability of its technical claims. I would think the paper should be rewritten to fit in the POPL standard.

Comments for authors
--------------------
- Introduction, there is no single citation (there are reference point to previous works, but no citations).
  The figure in introduction, at the point, is not really helpful for the reader.



- My general impression is that the text is written in hurry and far from rigorous. For example,
  Theorem 2.4 ( Line 110) uses operators that are not already defined, so it is not clear if a theorem is stated or an algorithm.
  shorthands such as CFG, regex, etc are not defined.

- Line 65, remove $P': \cdots$, as it is not used.

- Line 97,  the footnote must be in the main body.

- Lines 85. Do I understand correctly that $L(\delta (e))= L(e) \cap \{\varepsilon\}$. This definition is supposed to define the derivative, so why not defining $\delta$ beforehand, or at least give the intuition beforehand.



- Line 120, the semantics of the probabilistic choice is not clear to me. If not mistaken, the authors might mean nondeterministic?

- The construction in definition 2.6  (Line 138) is not super clear to me. This requires some introductory text.

-Lines 156-169, refer to Fig 2, in partciular when discussing about the resulting $\ell_{\cap}$.

- Line 174, Levenshtein metric is not defined. Do I understand that $d$ edits here means operations  that outputs words that are in distance $d$ in this metric?
  Reformulate.

Line 179, does $P_{\tehta}$ induces a probability distribution or just give a weight? The formulation of Definition 2.8 does not make sense to me.

- Line 213, Proof of Theorem 3.1, Do you assume that the topological order is given such that $M$ is already strictly upper triangular? Otherwise, the complexity of a change of basis would need to be discussed in the algorithm.

- Line 239, Proof of Theorem 3.1, optimistically? Is this complexity analysis  average complexity or worse-case complexity analysis?


- Line 241, Proof of Theorem 3.1, Choose is used as defined in Theorem 2.4. There is no correctness  proof for that Theorem (or algorithm)

- Line 265, token is never formally defined.

- Line 285, the relation $\sqsubseteq$ is used pairs of the form $(\sigma,\sigma')$ where $\sigma \in \Sigma^*$ and $\sigma'$ is a obtained from $\sigma$ by replacing some letters with holes. But then in definition of inhabitants, the role is reversed.

- Line 274, $M_{\infty}$ is used to talk about fixpoint of equation in line 265. In Line 317 however it is used in another setting, and not really clear if it is the fixpoint of which  equations.



Review #252B
===========================================================================

Overall merit
-------------
C. Weak paper, though I will not fight strongly against it

Reviewer expertise
------------------
Z. Outsider

Paper summary
-------------
The paper addresses the problem of syntax repair.  The approach is to model the problem using language intersection, i.e., the intersection between the language of all words in increasing distance from the original, syntactically incorrect word (that is to be "repaired") and the set of all valid (syntactically correct) words. The approach is embodied in a tool for autocorrection ("Tidyparse") which can generate repairs for invalid source code in a practical language such as Python.  The paper presents a an experimental evaluation of the effectiveness of the tool.

Comments for authors
--------------------
The tool does not read like a POPL paper.  It presents a tool which perhaps advances the state of the art but it does not present a technical contribution, nor does it present an investigation of the principles underlying a new approach.  The paper gives a sketchy description of the formal ingredients used in the approach, without saying whether there are new ingredients or whether putting together the ingredients is the actual novelty which is based on a new insight.

Plus:

+ a new tool which appareantly advances the state of the art in syntax repair/autocorrection

Minus:

- technical contribution remains unclear
- sketchy presentation of the formal details


Most syntax errors, however, require only a few typographic modifications to repair, of which there are only a finite number of possibilities to consider.
-- What is the evidence for this statement?

The formal presentation is sloppy.  Here are a few examples:

the quotient ... {b|ab \in L}
-->
the quotient ... {x|ax \in L}

107 witnesses
-- you use the word witness without introducing its meaning

110 to witness \sigma
-- there is no \sigma in the text that follows

113 follow
117 choose
-- you use the terms without introducing their meaning



Review #252C
===========================================================================

Overall merit
-------------
D. Reject

Reviewer expertise
------------------
X. Expert

Paper summary
-------------
# Summary

The submission proposes an approach to "syntax repair", where we are given a
(context-free) grammar and a string that is not generated by the grammar (i.e.
is not grammatical). We are then asked to produce strings similar to the input
string that are generated by the grammar.

The central idea is to consider the set of grammatical strings with bounded
(Levenshtein) distance from the input string. This set is finite and one can
construct a relatively small regular expression for it. Then within this set of
candidate corrections, the approach ranks the strings using ML methods.  This
has the advantage of producing all bounded-distance candidates, while still
exploiting ML techniques to find the most likely correction quickly.

# Evaluation

The proposed approach seems like a very promising idea. In my opinion, it
strikes the right balance between methods with theoretical guarantees (find all
corrections of a given Levenshtein distance) while still exploiting ML
capabilities to find the most likely correction fast.

Given my background, my focus was mostly on the theoretical contributions of
the paper. Here, I have to say that I found those a bit on the light side. Once
the approach above is formulated (which I think is a very nice idea!), the
technical ideas needed to implement it are mostly straightforward.

(For several (perhaps most) algorithmic tasks of the approach, one could easily
find alternatives; for example, they use regular expressions to describe the
set of corrections. A more direct phrasing would be just to observe that the
intersection with Levenshtein balls leads to an acyclic grammar, which
essentially already is a regular expression with just disjunction and
concatenation. Then, the acyclic grammar could be turned into an NFA using the
standard grammar to PDA construction, which yields a bounded stack-height PDA.
Thus, the difficulty lies not really in finding algorithms to do these things,
but to engineer a sequence of constructions that works well in practice.)

Therefore, the paper should be judged by its practical impact.  Unfortunately,
I believe the central algorithm of the paper---for checking intersection of a
CFL and an acyclic NFA in Theorem 3.1 and Algorithm 2---is fundamentally flawed. I
also believe that Theorem 3.1 can be corrected (with a similar statement, but
substantially different algorithm). But since implementation and experimental
evaluation rely on the flawed algorithm, it seems difficult to gauge the
practical impact.

# Flawed algorithm (Theorem 3.1 and Algorithm 2)

The problem in Theorem 3.1 and Algorithm 2 is that with the given definitions,
it is not sufficient to perform the "squaring" operation logarithmically many
times. The authors' mistake is to assume that the multplication operation they
introduce is associative; but it is not. Intuitively, this is because a
multiplication term $t$ over $M_0$ collects only those strings that can be
generated using derivation trees shaped like $t$. For example,
consider the Chomsky normal-form grammar

$$ A \to AB, A \to a, B \to b $$

and an NFA for the language $\{abb\}$. Then $(M_0M_0)M_0$ will contain the word
$abb$ (since it has a derivation where $ab$ is grouped into a subtree), but
$M_0(M_0M_0)$ does not.

In fact, it is easy to see that performing $M\mapsto M+M^2$ a number of $\ell$
times will collect exactly the strings that have a derivation tree of height at
most $\ell$. However, there are derivable words whose derivation trees
require linear (rather than logarithmic) height. For example, the set
$\{ab^n\}$ has an NFA with $n$ states, but with the grammar above, the word
$ab^n$ needs a derivation tree of height $n$.

# Alternative algorithms

An obvious alternative (but with worse run-time bounds) would be to give up the
poly-logarithmic time bound and just iterate the squaring polynomially many times.

But one can also salvage (at least roughly) the claimed bounds in Theorem 3.1
by using a different algorithm: One can rely on the known approaches to
context-free parsing in NC^2 (which can be adapted to CFL-ANFA intersection in
NC^2).  They circumvent the non-associativity of this particular matrix
multiplication operation by simultaneously saturating (i) derivable strings
(resp. state pairs) but also (ii) derivations "with holes". By doing this, one
can show that logarithmically many compositions will suffice. This was shown
independently by Ruzzo and Brent&Goldschlager, the latter of which is quite
accessible:

https://maths-people.anu.edu.au/brent/pub/pub085.html



Review #252D
===========================================================================

Overall merit
-------------
B. OK paper, but I will not champion it

Reviewer expertise
------------------
X. Expert

Paper summary
-------------
**Summary**

This paper introduces a new approach to repairing syntax errors in context-free languages by modeling the task as a language intersection between context free program grammars and Levenshtein automata. The authors prove complexity bounds, develop an enumeration algorithm based on Brzozowski derivatives, implement a parallelized version for CPU/GPU, and evaluate on Python syntax repair benchmarks. Results show significant improvements (up to 5× accuracy) over state-of-the-art methods.

**Evaluation**

*Novelty: 5/5*

•	The framing of syntax repair as CFL–regular intersection is original and elegant.
•	Establishes new theoretical connections (Bar-Hillel construction, CFL reachability, Brzozowski derivatives) and grounds them in a practical setting.

*Technical Quality: 3/5*

•  Strong theoretical results, with complexity analysis and formal correctness arguments.
•  Rigorous mathematical definitions.
•	Implementation demonstrates feasibility, but scalability analysis for large grammars and higher edit distances could be expanded.
•	Ranking model (n-grams) feels underspecified compared to the rigor elsewhere.
• The role of symbolic predicates in automata is underspecified, it seems that you working with automata modulo and alphabet as an Effective Boolean Algebra, i.e., with *symbolic* Levenshtein automata, that allows you to work with infinite alphabets. This aspect is important and should be presented more clearly.

*Clarity: 3/5*

•	The paper is dense and notation-heavy, making it challenging for readers not already steeped in formal language theory as well as some aspects of ML.
•	More  intuitive explanations would improve accessibility.

*Significance: 4/5*

•	Potentially high impact, bridging theory and practice in syntax repair.
•	Promising results on Python benchmarks, but broader evaluation (other languages, user studies) is needed to strengthen claims of generality.

*Reproducibility: 4/5*

•	Algorithms and pseudocode are described in detail, and an implementation of Tidyparse is also available and is promised for evaluation.
•	Empirical setup is sound, though additional experimental details (dataset splits, hyperparameters, evaluation metrics) would aid replication.
________________________________________

*Strengths*

•	Strong theoretical foundation with new insights into syntax repair.
•	Algorithmic rigor: formal definitions, constructions, and complexity proofs.
•	Practical implementation with parallelization on modern hardware.
•	Empirical results show clear improvements over prior work.

*Main Weakness*

• Missing comparison with or **at least a discussion about** as to how state of the art LLM frameworks (that have essentially all recently been enhanced with CFG based **guidance**, see e.g., https://github.com/guidance-ai/guidance) would deal with repair.

*Other Weaknesses*
•	Nonexpert readers will have a hard time reading the paper due to heavy formalism.
•	Evaluation is limited to Python; broader cross-language results would add weight.
•	Reranking strategy underdeveloped relative to the rest of the paper.
•	Limited discussion of IDE/user integration and repair ambiguity in practice.


________________________________________
**Overall**

The paper offers a novel and rigorous theoretical contribution with promising practical results. Its main limitations are in clarity and evaluation, which I think could be addressed in revision. Overall, it is a good candidate for acceptance and has the potential for significant impact.

Comments for authors
--------------------
**Too Dense Examples**

Some sections are very difficult to follow, in particular sec 4.2 on example of completion, where the matrix representations on lines 300-314 need more explanation. Also, I could not follow the details from lines 324 – 342 … too much technical detail. I would recommend leaving this section out and using the space for improvements elsewhere.

**Presentation(layout) issues**

• Figure on lines 40-44 is way too small (only readable in pdf), same applies to many other figures in the paper. I understand that the issue is limited space, but as it stands the paper is way too dense/packed right now.
•	Avoid colors, … or at least make sure that the paper is readable in BW when printed.

Specific questions to be addressed in the author response
---------------------------------------------------------
**Mainly questions** (see summary above)

1) Compare to *LLM with guidance*

and

2) Please be formal also wrt line 352 in the paper

*"we adopt a symbolic form that supports infinite alphabets and
simplifies the description to follow"*

This is not just "a symbolic form" but implies also many new questions of algorithms as well as derivatives (symbolic derivatives as in ref [49]?). It seems that the role of the alphabet is an EBA, if so, please make that formal.