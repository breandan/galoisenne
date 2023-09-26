## All Reviewers

We thank all reviewers for their careful reading and thoughtful comments. Despite the generally negative reviews, we were pleasantly surprised by the overall quality of the feedback received. We will begin with some common remarks, then address the primary concerns of each reviewer in turn.

### Notational remarks

- The frequent red underlining is not an PDF artifact, but denotes a string with errors (assuming some fixed grammar). This is meant to be evocative of the "red squiggly" underlines in an IDE or word processor.
- We use precision@k to denote the fraction of repair recommendations that have the ground truth repair in the top k results. This is a standard metric in information retrieval and recommender systems.

### SAT Encoding

Some reviewers expressed confusion about the precise SAT encoding. We concede that it was not treated in sufficient detail and slightly regret the remark, "the precise encoding is immaterial". Until a more rigorous formal treatment can be provided, we will illustrate a simple encoding with a matrix `M` where the string is `_ _` and the grammar is `G:=[Q->RS, R->QS, S->QR]`.

```
    | . V Y |
M = |   . W |
    |     . |

    Q   R   S
V:=[v1, v2, v3]
W:=[w1, w2, w3]   <- Variables encoding the indicator function
Y:=[y1, y2, y3]      for the set of participating nonterminals

{xor, ʌ, ⊤} is a functionally complete set and can be viewed as GF(2) where ⊤:=1 and ʌ:=× and xor:=+.

We can define = in terms of {xor, ʌ, ⊤}:

(a = b) <=> (a xor b) xor ⊤ <=> (a + b) + ⊤

To encode M = M^2 case, we must first compute Y = V × W:

          Q->RS  R->QS  S->QR
V \dot W=[v2ʌw3, v1ʌw3, v1ʌw2]

(Y = V × W) <=> [
((y1 xor (v2 ʌ w3)) xor ⊤),  | Q
((y2 xor (v1 ʌ w3)) xor ⊤),  | R
((y3 xor (v1 ʌ w2)) xor ⊤)   | S
]

Since we only care about the S=3rd entry, we can ignore the first two:

((y3 xor (v1 ʌ w2)) xor ⊤) <=> [S ∈ Y]

Alternatively, this expression can be rewritten a polynomial over GF(2):

(v1 × w2 + y3 + 1) <=> [S ∈ Y] <=> ["Q R" ∈ L(G)]
```

Where `v1` can be interepreted as the truth value for "Is Q in hole 1?" and `w2` is interpreted as the truth value for "Is R in hole 2?" and `y3` is interpreted as the truth value for "does the entire string derive S?". This says that iff the string is recognizable by G (`y` is true), then the nonterminal Q must be hole 1 (`v1` is true) and R must be in hole 2 (`w2` is true).

## Reviewer #785A

We thank reviewer #785A for making an honest effort to understand our work and for their constructive feedback. We appreciate their detailed analysis and will address their significant technical concerns below.

#### "Trivial" standard techniques and alternatives

The reviewer may be under the mistaken impression we are searching for the shortest edit that repairs the string, which is not the case. We agree that there are standard algorithms for computing Language Edit Distance, but we are not computing for the distance itself or even the shortest repairs which share that distance, we are searching for a set of all repairs within a certain Levenshtein radius (bounded L-CFL reachability). The user provides a desired distance, and we return all repairs within that radius, ranked by naturalness. The "standard technique" proposed by the reviewer does not solve this problem, nor does it directly produce the edits for the simpler problem of synthesizing repairs for a porous string (our terminology for $(\Sigma \cup \{\_\})^*$) without significant modification.

Just to clarify, the reviewer suggests letting $V_1$ be the set of all nonterminals on the left hand side of a unit production in a CNF CFG, then substituting the superdiagonal entries corresponding to holes with $V_1$ and performing CYK. This will produce a recognizer that tells us whether a solution exists to a given porous string, but not the admissible set itself. It is possible to reconstruct the parse trees by carefully modifying  $\hat\otimes$ to tracks all parse trees for all unit nonterminal candidates in all holes, however the number of trees grows exponentially with the size of the unit nonterminals $|V_1|$, number of holes and string length.

In our preliminary experiments, the proposed technique could only handle short strings with a very small number of holes before running out of memory and was extremely slow compared with our described method. It may be possible to improve the performance of this approach using a more efficient data structure that only tracks the strings and root, precomputes the joins and performs further careful optimizations to prune the set of trees propagated upwards. We appreciate the suggestion and will include a detailed comparison with the proposed technique in a later version of the paper.

#### Valiant's algorithm

Our recognizer is closer to Valiant's recognizer in the bit domain and the SAT domain than vanilla CYK. Valiant first realized that CFL recognition shares the same complexity as Boolean matrix multiplication (BMM), but a closer reading of Valiant [1974] will reveal he uses Strassen's result only indirectly by reducing CFL recognition to transitive closure, whose complexity he shows is upper bounded by that of Boolean matrix multiplication, then briefly cites Strassen for the complexity of matrix multiplication. We encourage the reviewer to carefully read Valiant's paper:

> "The last two theorems establish the following intermediate result: if the complexity functions grow uniformly as assumed then the problem of computing the transitive closure of a "parse" matrix is of essentially the same difficulty as that of Boolean matrix multiplication. The difference between their complexities can be bounded by a multiplicative constant...
> 
> To reach our main conclusion we use the known fact that Boolean matrix multiplication does not require time $\mathcal{O}(n^3)$. Treating the Boolean elements as integers modulo $n+1$, applying Strassen's algorithm [7], and reducing the nonzero elements to one in the result gives the Boolean product in $\mathcal{O}(n^3)$ bit operations [3]. We can therefore deduce from Theorems 1,2, and 3 that context-free languages can be recognized in time $\mathcal{O}(n^{2.81})$." 
>
>-- Valiant (1974), p. 10

Valiant's key insight was not the subcubic algorithm itself, but the reduction to BMM in order to leverage Strassen's result and later optimizations to BMM. Like Valiant, we also use BMM for recognition but elect to defer Strassen's optimization. Even in the absence of Strassen's subcubic optimization, Valiant's recognizer is still wall-clock faster than ordinary CYK, since is it consists of evaluating a fixed Boolean circuit, which is faster than naively encoding the standard CYK algorithm or Early parser, both of which use the set domain and in the case of synthesis cannot be directly encoded as a SAT instance without a similar translation. The practical speedup conferred by implementing recognition using BMM is difficult to understate, and is the main reason we use Valiant's approach rather than CYK. We will include a comparison of the BMM recognizer versus the set-based CYK algorithm in the final version.

#### On the "problematic" use of finite fields

We find this remark puzzling for an otherwise insightful review. For starters, Valiant's own work assumes the use of a finite field as noted above ("Treating the Boolean elements as integers modulo n+1,..."). Furthermore, $\mathbb{Z}_2^{|V|}$ is used with an algebra that faithfully encodes set join and union, not $\mathbb{Z}_2$ as the reviewer incorrectly states. The important point is the commutative diagram in 4.1 does indeed commute and that repairs can be decoded from the solutions to a polynomial over a finite field (or equivalently, a Boolean circuit). That is, recognition using a finite-field accepts all and only the strings accepted by the set-based recognizer.

Unlike sets, translating the problem into an algebraic domain lets us leverage the power of modern SAT solvers and automated reasoning tools. More generally, reframing computational problems in terms of circuits has yielded both theoretical and practical insights that are easily overlooked in their natural domain, as demonstrated by a large body of literature on circuit lower bounds, which shares rich connections to finite fields and algebraic complexity theory. We believe it is a mistake to dismiss the use of finite fields as "problematic" without further clarification, but perhaps the reviewer was critical of our exposition thereof rather than the approach writ large. We will include a more detailed explanation of the algebraic approach in the final version, including a proof that the commutative diagram in 4.1 in fact commutes, and a discussion of the connections to algebraic complexity theory.

Re: Associativity of $\otimes$. The issue with associativity only arises when computing the fixpoints iteratively, but may be dispensed when computing the fixpoints directly with a SAT solver. This was a surprising result to us as well, but we have verified it holds empirically and will include a proof in the final version.

#### Questions to be addressed by author response

> Do you actually use Valiants speedups and are they in practice faster? Or do you just use the normal cubic CKY variant?

As discussed above, Valiant does not directly propose a novel speedup, but a reduction to BMM and even in the absence of subcubic speedups (e.g., Strassen et al.), Valiant's parser is practically faster for recognition by evaluating a pure Boolean circuit. As our work shows, this circuit can be directly translated to a fast synthesizer by lifting it into the domain of polynomials over finite fields, then fed directly into a SAT solver. Our work is based on Valiant's technique for recognition, adapts it for synthesis, and uses CYK for parsing, but parsing is only an extension for cosmetic purposes that can be implemented with a few additional lines of code.

> What does matrix completion mean in this paper?

Matrix completion is a well-defined problem in statistical learning. Without going into much detail, given a matrix M with some missing entries, completion is the problem of how best to fill the missing entries in order to minimize some objective function, subject to a set of constraints. In our case, we want to minimize the perplexity of the superdiagonal entries representing the synthesized repair, subject to the Levenshtein bound and idempotency constraint.

> Why not use an Earley parser which seems more efficient in these cases?

We could use Earley for parsing, but as we disclaim in Sec. 5, the main result of this paper is not a more efficient parser, but a more efficient technique for synthesizing repairs. In our case, generating the concrete syntax trees is only a cosmetic matter once the set of repairs have been computed, and in practice parsing takes a negligible amount of time compared with synthesis. We will clarify this in the text.

> At several points you talk about "solving". Does this always mean using a SAT solver? What are the advantages of using a SAT solver here rather than a standard polynomial time algorithm?

A common misconception with SAT solvers is they are only practically useful for solving NP-hard problems, but this is not the case. SAT solvers offer a general, albeit low-level assembly-like framework for logic programming, and can still be used to solve many problems that have PTIME solutions. SAT is a general intermediate language which can be used to solve many combinatorial search problems, is convenient to implement via staging, flexible to new constraints and typically fast in practice. There are specialized algorithms for solving systems of linear equations over a finite field, including Gaussian elimination, Gröbner basis algorithms and model counting.

By default, we use SAT but as with many algorithms, there are tradeoffs. SAT solving offers a compact representation and is simple to implement once a Boolean recognizer is constructed, but the order in which models are decoded is generally less controllable than a custom solver. We are not opposed to alternative solvers, and have implemented several custom solvers ourselves for the bounded L-CFL problem, but we are not aware of any general algorithms that are faster in practice and have tried a variety of alternatives. 

It may be possible to realize still faster wall clock optimizations by adapting the "trivial" CYK parsing technique with holes proposed by the reviewer, but we remain skeptical and intend to find out. If the solution were indeed so trivial, then we would expect it to be more widely known and used in source code editors, but automatic syntax error correction is still an open research problem and we are not aware of any existing implementations that use this technique in practice, so there must be some reason why it is not used. We will include a detailed comparison of the proposed technique with our approach in the final version.

> Line 322: why use a CFG rather than a FA here?

We could also specialize the recognizer to a finite automaton, but this would require a different encoding and would be less general. It may indeed be possible to improve the empirical performance for bounded L-CFL reachability by using Bar-Hillel's construction as we allude to in Sec. 9 and later suggested by the reviewer, however this would only allow computing intersections between FA and CFGs and would require modifying the CFG. The simpler and more expressive solution is to treat the FA as a CFG and support the intersection between CFGs, without merging the CFGs. This allows us to encode arbitrary CFL intersections and lines up naturally with the conjunctive grammar formalism, which we later use to encode other semantic properties like the CFL pumping lemma.

## Reviewer #785B

We thank reviewer #785B for their careful reading and thoughtful comments. We are grateful for their analysis, which makes a good faith effort to understand our work and provides a variety of actionable feedback. First, some high level remarks:

#### On the necessity of Conjunctive Grammars

We agree that conjunctive grammars are not strictly necessary for our approach, but they are a natural fit for and are more expressive than CFGs. Computing intersections between CFGs allows us to encode future semantic properties and conjunction can be easily expressed with a SAT solver by appending additional constraints in conjunctive normal form. Futhermore, computing the intersection between a regular language (REG) and CFL as the intersection between two CFLs (CFL ∩ CFL) is simpler than using the Bar-Hillel construction (REG ∩ CFL) and avoids specialization or refactoring.

#### Context sensitivity

> "However, the paper suggests in a few places that all context-sensitive languages can be parsed with these techniques."

To be clear, our paper makes no such claim and we are unsure how the reviewer arrived at this conclusion.

We do claim that our approach can be applied to conjunctive languages, which are a proper subset of context-sensitive languages. We also briefly allude to the fact that indentation checking, name resolution, scope checking and some form of type checking are possible to simulate with a large enough grammar, but this requires generating the grammar dynamically by first analyzing the program's source code or knowing some properties ahead-of-time. This can be done for an arbitrarily large string with a fixed number of names, maximum indentation depth, and scope nesting, or a fixed-length string with an unspecified number of identifiers. As the reviewer astutely notes, in order to generate the grammar, we must know ahead-of-time either (1) the length of the string or (2) the maximum number of unique identifiers, primitive types or indents.

Our argument is based on a simple observation: if the string length is known ahead-of-time, these problems are all decidable and as long as the property being checked is decidable, intersection with a bounded-length string is decidable by an FA and thus a CFG. Otherwise, if the length is unknown but the number of identifiers are bounded, we can exhaust every possible valid ordering in an arbitrary length-string. In fact, we will go one step further and claim that context-free grammars can be forced to check bounded integer arithmetic within a fixed range, but admittedly, the grammar grows exponentially with the size of the range. We concede that this is mostly an ancillary property, but have implemented several prototypes for each of these properties (types, name resolution, scoping, indentation, arithmetic) and will stand by those claims. Further details will be provided in a future manuscript.

#### Line-by-line and low level remarks

We regret being unable to write a detailed response to all of these remarks in the time allotted, but agree that most of them make sense. Some brief replies.

- The upper bound in Lemma 3.3. is indeed off by a small factor. We appreciate the reviewer's careful reading and will correct this in the final version.

- $\Gamma$ represents the set of active grammars and strings in the metalangauge. Intuitively, some examples are given in Sec. 6. It is not meant to be a rigorous description, but a convenient notation for describing the set of grammars in a metalanguage. We will provide a more precise definition in a later version.

- $\equiv_{\sigma_i}$ denotes an equivalence relation between of nonterminals representing a given terminal in a string. For example if we have two grammars `G1:=[A->a, B->a]` and `G2:=[S->a]`, then $\equiv_{\sigma_i}$ denotes that the expressions representing the presence of nonterminals `A, B` must be equivalent to the truth value for the presence of the nonterminal `S` at every position of `a` in the string. This establishes an equivalence relation between sets of nonterminals representing terminals on the superdiagonal. Essentially, for each terminal in the string, we must compute the nonterminal sets each one is generated by in each CFG to bind their truth values together.

- Morally, $H(\sigma)$ is a function which accepts a porous string, and returns the set of all non-porous strings which are identical with the porous string at all non-hole locations (or equivalently, differ only at the hole locations). We will amend line 242 to be consistent with its usage in line 247, which should instead read as follows: "We denote $\sqsubseteq: \Sigma^n \times \underline\Sigma^n$ as the relation $\{\langle\sigma', \sigma\rangle \mid \sigma_i \in \Sigma \implies \sigma_i' = \sigma_i\}$ and the set of all $\{\sigma' \mid \sigma' \sqsubseteq \sigma\}$ as $\text{H}(\sigma)$."

- Isolation implicitly assumes that subtrees are associated with a unique range in the string. As we mention, this optimization is not currently enabled as we have not solved it in full generality, but is important to unlocking the full potential of the approach and we intuitively feel the general problem is tractable.

- *"At least once and at most approximately once"* - [Partial surjection](https://en.wikipedia.org/wiki/Subcountability) from $\mathbb{Z}_2^m$ to $\Delta_q(\sigma)$. All members of the Levenshtein ball will be sampled at least once and sometimes more than once, since it is rare that the volume of the Levenshtein ball will be exactly a power of 2.

#### Questions to be addressed by author response

> Please explain the diagram on page 6, including defining all notation, and explain the relevance of the Recognition and Synthesis columns of the figure.

- $\mathcal{V}$ denotes a vector of polynomials over GF(2) encoding an element of the powerset of nonterminals.
- $\varphi/\varphi^{-1}$ is used to denote the encoding of a fully-determined expression containing all literals into equational form, which may contain variables corresponding to holes in the string and participating nonterminals in the upper triangular parse forest. For a more detailed description of the equational theory thereof, please refer to our SAT encoding under "All Reviewers".
- $\mathbf{1}$ is standard mathematical notation for the [indicator function](https://en.wikipedia.org/wiki/Indicator_function).

Recognition is the process of deciding whether a given string is contained in the language, which we do by compiling into a circuit, or equivalently a polynomial over GF(2) whose free variables are all fully determined. This uses no sets or strings, but simply evaluates a pure Boolean circuit. Synthesis is the process of generating the set of all repairs for a porous string, which is simulated using a SAT solver to produce the set of all models that satisfy the circuit and some porous strings can be unsatisfiable.

> Can you give a high-level overview of the sequence of steps in your approach...?

The approach we offer is less a sequence of steps than a general framework for solving a class of language intersection and membership problems. Many paths through the commutative diagram are possible to obtain the same result. The fastest path we have found in practice is to compile the problem into a circuit with free variables, then solve for the inputs that satisfy the circuit. The high-level steps for are as follows:

1. Rewrite the grammar into Chomsky Normal Form (CNF).
2. Given an invalid string, sample hole locations using the probabilistic sampler.
3. Feed the porous string to the SAT solver, by compiling in into a circuit.
4. Solver returns a set of models satisfying the circuit, which are all valid repairs.
5. Hole configurations that yield admissible repairs are resampled adaptively.
6. Repeat 2-5 until the timeout expires or all holes configurations are exhausted.

For languages with statistical priors, better controllability can be achieved by using the sampler to generate both the hole locations and the candidate repairs models, which are filtered through the recognizer to remove inadmissible candidates. (These are the steps we performed to synthesize the repairs in Sec. 8.1-8.4.) This does not require a SAT solver, but does require a pretrained variable-order Markov chain on a large corpus of lexical tokens. This may not be available for custom domain-specific languages or minority languages.

Alternately, we can omit the sampler entirely and encode the entire problem as a single SAT instance. This is the least controllable approach, but is still useful for validating completeness and correctness of the approach.

> How is the grammar mentioned in Section 4.3 constructed?

We first constructed some simple examples by hand, then abstracted the pattern using a macro system. The following example is derived from [this blog post](https://fulmicoton.com/posts/levenshtein/#observations-lets-count-states), and recognizes distance-1 Levenshtein edits from the string "flees":

```
START -> d:4:0 | d:4:1 | d:5:0 | d:5:1
* -> a | ... | z

d:1:0 -> f
d:2:0 -> d:1:0 l
d:3:0 -> d:2:0 e
d:4:0 -> d:3:0 e
d:5:0 -> d:4:0 s

d:0:1 -> *
d:1:1 -> d:0:1 f | d:1:0 * | *
d:2:1 -> d:1:1 l | d:1:0 * | d:2:0 * | l
d:3:1 -> d:2:1 e | d:2:0 * | d:3:0 * | d:1:0 e
d:4:1 -> d:3:1 e | d:3:0 * | d:4:0 * | d:2:0 e
d:5:1 -> d:4:1 s | d:4:0 * | d:5:0 * | d:3:0 s
```

After gazing at the example for some time, we then built a macro system to dynamically generate the grammar for more general strings and distances:

```
// Only accept states that are within radius dist of (strLen, 0)
fun acceptStates(strLen: Int, dist: Int) =
  ((strLen - dist..strLen) * (0..dist))
    .filter { (i, j) -> ((strLen - i) + j).absoluteValue <= dist }
    .map { (i, j) -> "d:$i:$j" }

fun backtrace(x: Int, y: Int, sym: String) =
    if (x == 0 && y == 0) sym else if (x < 0) "" else "d:$x:$y $sym"

private fun levenshteinTransitions(symbols: List<String>, i: Int) =
  "d:0:$i -> ${if(i == 1) "" else "d:0:${i - 1} "}*\n" +
    symbols.mapIndexed { j, s ->
      "d:${j + 1}:$i -> " +
          // Inbound transitions
          backtrace(j, i, s) + " | " +
          backtrace(j, i - 1, "*") + " | " +
          backtrace(j + 1, i - 1, "*") +
          if (0 < j) " | " + backtrace(j - 1, i - 1, symbols.getOrElse(j) { "" }) else ""
    }.joinToString("\n")

fun constructLevenshteinCFG(symbols: List<String>, dist: Int, alphabet: Set<String> = symbols.toSet()): String =
  """
     START -> ${acceptStates(symbols.size, dist).joinToString(" | ")}
     * -> ${(alphabet + symbols).joinToString(" | ") { "%$it" }}
  """.trimIndent() +
      (alphabet + symbols).joinToString("\n", "\n", "\n") { "%$it -> $it" } + "d:1:0 -> ${symbols[0]}\n" +
      symbols.drop(1).mapIndexed { i, symbol -> "d:${i+2}:0 -> d:${i+1}:0 $symbol" }.joinToString("\n", "\n") +
      (1..dist).joinToString("\n\n", "\n") { levenshteinTransitions(symbols, it) }
```

The macro system is not particularly sophisticated, but suffices to generating the grammar for the Levenshtein distance example, which is then compiled into a CFG, intersected with a PL syntax, then fed to the synthesizer to generate the repairs.

> What is the relationship between the use of the SAT solver to find related strings in Section 4.4 and the probabilistic sampling of 4.7? In particular, it's unclear to me how to use a SAT solver (which gives no distributional guarantees about what solutions it might return) as part of the controlled sampling approach.

There are essentially three approaches, which we will number (1-3) and offer increasing controllability, and range from language-oblivious to language-aware:

 1. For minority languages with no statistical priors, we can simply use the SAT solver with all blanks, intersecting the CFG and Levenshtein grammar, decoding the models directly, then reranking repairs by Levenshtein distance after a fixed timeout. This technique is not very controllable as the models are generated in whatever order the SAT solver happens to find first.

 2. We can use the sampler to generate the hole locations and feed them to the SAT solver to extract the admissible set. This lets us control the hole locations, and when a given hole configuration has no models the SAT solver will return UNSAT. When a repair is found, the generated repairs are used to bias the sampler towards nearby hole locations.

 3. Finally, we can pretrain a variable-order Markov chain (VOMC), then use the uniform sampler to generate the hole locations and the VOMC to sample candidate solutions, which are filtered through the recognizer to ensure they are admissible, then the resulting repairs are ranked by perplexity of the VOMC. This the approach we use for the experiments in Sec. 8.1-8.4.

Typically, if the number of holes in the string is small, it is faster to simply enumerate the solutions by brute forcing a recognizer with all Levenshtein edits. As the number of holes and string length increases, it becomes more efficient to use a SAT solver.

> How reasonable / representative are 40-50 character/token code snippets?

40 lexical tokens are more than enough for ~95% of the samples in the StackOverflow dataset and 99% of the samples in the Seq2Parse dataset. We provide the CDFs below:

Percent of snippets with lexical token length <= n

| n   | StackOverflow | Seq2Parse |
|:---:|:-------------:|:---------:|
| 10  |      60%      |    90%    |
| 20  |      84%      |    98%    |
| 30  |      92%      |    99%    |
| 40  |      95%      |    99%    |
| 50  |      97%      |    99%    |

We believe these datasets are representative of the kinds of strings that programmers write in practice and certainly more than enough to repair a single line of code in most programming languages, but will leave this question to the reader to decide. 

> Why are the experiments in Section 8.4 informative?

Pairwise error-fix datasets containing broken code and human repairs are difficult to obtain, and even more so for minority languages. Error correction could be easily evaluated on a single popular language like Python or Java, but our goal is to synthesize repairs for errors of arbitrary provenance in a variety of context-free languages, including those never previously seen before by the repair model. Natural datasets may contain noise distributions biases concentrated nearby certain locations, but the experiments in Sec. 8.4 demonstrate that our approach can be used to synthesize repairs for synthetic errors generated uniformly at random in natural languages, and synthetic errors in synthetic languages, a more challenging setting where a prior noise distribution is unavailable and relies on the solver to locate and fix the error. Admittedly, such applications may be rare, but we believe they are still informative in evaluating the generalizability of our approach to rare errors in minority languages.

### Review #785C

We thank the reviewer for their terse and incisive comments.

#### SAT Encoding

We refer Reviewer #785C to the remarks under All Reviewers.

#### Obvious way to compute the intersection

In practice, we only need two grammars to compute the intersection $\mathcal{L}(\mathcal{G}) \cap \Delta_d(\sigma)$ (CFL ∩ LEV), but in general there may be more than two grammars in the conjunctive case. It may be possible to sample paths through the Levenshtein automaton, but in practice when using the Levenshtein automaton, we encode the bounded L-CFL reachability problem as a single SAT instance and let the solver find all solutions directly. We must confess is a little unclear here precisely what baseline the reviewer is referring to, but perhaps they are referring to sampling $\Delta_d(\sigma)$ and rejecting the strings not in $\mathcal{L}(\mathcal{G})$, which is indeed the procedure we use in Sec. 8.1 of the experiments, before reranking.

#### Section 4.2

The conjunctive grammar is used to encode containment of a single porous string in multiple CFGs in the SAT solver. Equation 2 uses an equivalence relation between sets of nonterminals for each terminal in the string, and equates their truth values in the encoded SAT formula.

#### Section 4.5

The "tensor" here refers to the multidimensional array consisting of polynomials identifying participating nonterminals in the parse forest. This can be viewed as either a matrix whose elements are each a vector of finite field polynomials, or a rank-3 tensor (Rubix, like a Rubik's cube) whose fibers consist of those same polynomials.

#### Levenshtein Automaton

We use a variant of Schulz and Mihov's Levenshtein automaton that avoids ε transitions, which are problematic for the Bar-Hillel construction as originally presented. Even though we do not currrently employ Bar-Hillel's construction, ε-arcs significantly complicate matters (see [Pasti et al. (2022)](https://arxiv.org/abs/2209.06809)), and so we avoid them altogether and use the "knights move" to simulate deletion. Indeed, Kleene star should be $Σ$ as the reviewer notes, or one arc per each $s ∈ Σ$. Additional arcs may be introduced from $q_{i, j} \rightarrow q_{i+h+1, j+h}$ to simulate h contiguous multitoken deletions.

#### Liner-feedback shift register

> I think the idea is to enumerate all bitvectors using a linear-feedback shift register...

Correct. At a high level, we use an LFSR to build a uniform no-replacement sampler from the Levenshtein ball. We use the bitvectors to identify Levenshtein edits. This is a polynomial over GF(2) and the elliptic curve to which we refer on pg. 2, line 78.