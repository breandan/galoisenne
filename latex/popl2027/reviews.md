POPL 2027 Paper #570 Reviews and Comments
===========================================================================
Paper #570 Syntax Repair as Language Intersection


Review #570A
===========================================================================

Overall merit
-------------
C. Weak paper, though I will not fight strongly against it

Reviewer expertise
------------------
X. Expert

Paper summary
-------------
This paper presents Tidyparse, which represents bounded syntax repairs as the intersection of a context-free language and an acyclic Levenshtein automaton. It constructs a regular expression for the exact candidate language, uses WDFA-guided derivative decoding to retrieve candidates, and reranks them with LaTeR. Experiments on lexicalized Python snippets report higher repair accuracy than three baselines.

Strengths
---------
* Clean language-intersection formulation.
* Easy to follow in general

Weaknesses
----------
* Evaluation on selected and modified dataset; usage scenario unclear
* Related work insufficiently discussed

Comments for authors
--------------------
The paper is well-written and presents an elegant formal-language perspective on syntax repair. I find the language-intersection perspective cleanly integrates syntactic validity, edit locality, and statistical ranking, and could inspire future work on this topic. The empirical results reported in this paper are also promising. Overall, I think this paper is a valuable piece of work and should eventually be published.

However, I do not think this paper is ready for publication in its current form due to the following two major concerns.

First, the evaluation is conducted on a heavily selected and modified dataset, and thus does not reflect the performance of the approach in practice. The original Stack Overflow corpus contains more than 20K naturally occurring error/fix pairs, but the main evaluation only considers snippets shorter than 80 lexical tokens whose human fixes require fewer than four lexical edits.
This leaves roughly one third of the original corpus within the considered regime. The test set is further balanced across snippet-length and edit-distance bins rather than preserving the natural distribution of the original data. This leaves two issues: (1) With such a small selection, how much does the dataset represent real-world scenarios? (2) Arguably, the tasks requiring few edits are easy tasks, and the need for automated repair is unclear. The evaluation excludes the more challenging tasks that really require automated repair.

More importantly, the evaluation abstracts away identifier names and literal values and measures lexical equivalence with the ground-truth repair. In other words, the evaluation is performed on lexical tokens rather than the original code. Then **the application scenario of this approach is totally unclear to me**. (1) Can we automatically trust the result of repair without asking for human intervention? (2) If we repair a lexical token to an identifier, we still cannot further process this code because we do not know what the name of the identifier is. Then what use does this approach have? This could also partially explain why this approach outperformed GPT-5.4. GPT has not seen many lexical token sequences during its training.

Reading from the text, it is also not fully clear whether the 604 instances filtered out at the first step were given to the baselines. If not, this is also a significant bias in the evaluation.

Second, while I do believe the proposed approach has novelty, the discussion of related work does not sufficiently position its novelty against the related work. The paper presents formulating bounded syntax repair as language intersection as a main contribution. However, earlier work on parsing ill-formed input comes quite close to this view. For example, van Noord [a]describes robust parsing techniques in which an input string is composed with a finite-state transducer representing possible errors, producing an FSA that is then parsed/intersected with a context-free grammar. This is not exactly the same bounded-Levenshtein formulation used here, but the idea is close.

Similarly, the modification graph in OrdinalFix [69], if I understand correctly, is also a representation of the syntactically valid programs within a bounded Levenshtein distance, functionally the same as the intersected result in this paper. The difference is in the data structure used. The OrdinalFix also uses CFL-Reachability to calculate the repair, similar to this paper. The paper should acknowledge these prior studies, compare OrdinalFix, whose modification graph serves the same goal, and better position its novelty.

Furthermore, the current paper mainly distinguishes OrdinalFix by noting that it searches for minimum-cost repairs rather than probabilistically ranking a large repair set. While this is a valid distinction, it does not automatically imply the superiority of the proposed approach. Perhaps in practice we do not need a probabilistic ranking and the minimum-edit repairs are already sufficient. The paper should provide empirical evidence on the need for probabilistic ranking, rather than simply assuming it is better.


[a] Gertjan van Noord. The Intersection of Finite State Automata and Definite Clause Grammars. Proceedings of ACL 1995, pp. 159–165.


Minor issues:

* Section 7.1 calls the LLM baseline "GPT-5.4 mini," whereas Section 7.3 and Figure 14 call it "GPT-5.4." Please use a consistent model name.

Specific questions to be addressed in the author response
---------------------------------------------------------
1. What is the usage scenario of this approach?
2. Could you please provide a better comparison with OrdinalFix, on the performance in the target scenario rather than how the methods are different?
3. Were the 604 filtered instances given to the baselines?



Review #570B
===========================================================================

Overall merit
-------------
A. Good paper, I will champion it

Reviewer expertise
------------------
Y. Knowledgeable

Paper summary
-------------
This paper presents a scheme to suggest fixes for syntax errors.

The idea is this: given a program with an error `s` that is
*supposed* be a program in a CFG `G`, you can synthesize a
fix by searching for an inhabitant of the language

    G \intersect {s' | s' is at edit-distance <= d from s }

The second language, is in fact regular, and in fact, finite
(recognized by an acyclic DFA) hence the entire intersection
can be cleverly encoded as an automaton.

Now, further one can use a carefully crafted representation
of that automaton to do constrained decoding, sampling and re-ranking
of the (possibly many) candidates in that intersection.

The paper fleshes out this elegant high-level idea, working out
all the details, and conducting an empirical evaluation showing
that because the above intersection is "complete" i.e. preserves
*all* possible fixes upto size `d`, that the resulting re-ranking
allows finding the correct fix far more accurately than prior work.

Strengths
---------
+ Tackles a classic and important problem

+ The actual methods are a combination of classic PL/symbolic ideas with
  new approaches (language intersection + constrained decoding)

+ Especially appreciate the comparison with ChatGPT 5.4 (?) and that the proposed method
  is a significant improvement

+ Impressive results compared to prior approaches (on certain benchmarks)

Weaknesses
----------
- Fixing parse errors might be construed as a somewhat quaint problem in this day and age ...

Comments for authors
--------------------
NGL I was not expecting to like this paper quite so much, but here we are.
Its a very elegant idea but with lots of tricky details that this paper nicely
works out to give some substantial improvements over prior approaches.

My main two questions are these.

First, the main claim/hypothesis is that the "completeness" of this approach, namely
that in principle, it can represent *all* repairs upto some Levenshtein distance, is
what sets it apart. I couldn't help thinking that perhaps instead it is the re-ranking
(How) does the method scale to beyond 3 edits. In general how much of the payoff is
actually due to the re-ranking, and could that same re-ranking be applied to the other
methods (e.g. Seq2Parse, BIFI). I *think* the whole LaTeR approach crucially depends
on representing the whole space of edits, but I'd like to have seen some crisp intuition
of *why* that is the case...

Second, what happens beyond 3 edits? IIRC prior work [55] shows that these are not uncommon?
Does the method blow up? Is there some way to get a graceful degradation? Can you clarify
what is meant by

    "filter for syntax errors shorter than 80 tokens
     and fewer than four lexical edits apart from the
     corresponding repair"

Is 80 the length of the program (or what does "syntax errors" mean here?)

- I thought the red squiggle was a PDF bug until I got to line 702; you might add a footnote
  to clarify it earlier :-)



Review #570C
===========================================================================

Overall merit
-------------
B. OK paper, but I will not champion it

Reviewer expertise
------------------
Y. Knowledgeable

Paper summary
-------------
The paper is concerned with syntactic repair, that is, given a language L and a word w not in L, determining plausible modifications of w (described by a regular language) that lie in L.

The papers main contributions are threefold:
1. An efficiently parallelizable algorithm for effective intersection of CFGs with epsilon-free acyclic NFAs
2. A regular overapproximation of said intersection supporting efficient sampling and ranking .
3. Experimental evaluation of a reference implementation against a StackOverflow-based test set.

Strengths
---------
The paper connects a number of concepts in order to develop a tool that, while the reference implementation still appears to be too slow for real-life use, still presents a significant step up from previous approaches.

The main insight of the paper is that a combined two-step approach of fast probabilistic sampling combined with a small transformer-based ranking of the generated sample can outperform a fully LLM-based repairing strategy while at the same time being significantly faster.

Weaknesses
----------
The theoretical section of the paper is at times underexplained, with notation and variables left undefined and implicit. At the same time, a lot of space is spent recapping fairly standard constructions such as using matrix multiplication to model reachability. This section could benefit from being cleaned up and simplified.

Comments for authors
--------------------
L160: Turnstile notation not introduced, but I believe it should be the other way around ("from S one can derive (the label of) the run p -> q")

L160: Using lowercase w for nonterminals instead of words is mildly confusing

L169: Explicitly mention that the matrix multiplication algorithm used is parallel

L265: Notation M_0[r+1 = c](G', 𝜎) is unclear. What is the meaning of "= c"? Why the function call with G' and 𝜎? Judging by the figure below I'd expect M_0[r,r+1] = 𝜎_r

L316: Not clear why the bit domain warrants special attention since the correspondence to the powerset domain is immediate

L424: Matrix A not introduced (presumably the adjacency matrix?)

L427: LED not introduced

L447: PCFG not introduced

L447: treebank not introduced

L553: Ambiguous, could plausibly mean nondeterministic choice or mutually exclusive branches but as far as I can tell, { EOS, a } ⊆ L(e) is plausible

L666: Figures so small as to be almost impossible to read. Limiting the examples to h <= 3 would be equally illustrative without compromising readability.


Comment @A1 by Reviewer A
---------------------------------------------------------------------------
# Meta-Review

Thank you for submitting to POPL. The reviewers generally find that the paper addresses an important problem with a clean approach connecting different concepts and techniques. However, the evaluation was conducted on lexical token sequences, and the reviewers were not convinced by the author response that the mapping between code and lexical token sequences can be studied and evaluated separately. As a result, there is no champion for this paper, and the decision is rejection. We hope the reviews help you improve your paper for a future submission.