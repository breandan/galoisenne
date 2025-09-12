We thank all four reviewers for their time, particularly `A`, `C`, and `D` who made an effort to read our POPL submission and provide their earnest feedback. In particular, Reviewer `D` should be commended for their counterexample, which is exemplary of a good review and far more valuable than lukewarm praise or vague stylistic critique. The manuscript will be withdrawn.

Review #252A
===========================================================================

We thank Reviewer A for carefully reading the manuscript, and for providing several cogent questions and notational remarks which demonstrate their willingness to engage with the ideas beyond a cosmetic level.

> Do I understand correctly that $\mathcal{L}(\delta(e))=\mathcal{L}(e)\cap\{ε\}$.

Yes. $\mathcal{L}\big(\delta(e)\big)$ is effectively an indicator function for whether the empty string is matched by $e$. If $\delta(e) = \varnothing$, then the language is empty and the empty string is not matched. If $\delta(e) = \varepsilon$, the empty string is matched and the langauge is nonempty.

> Line 120, the semantics of the probabilistic choice is not clear to me. If not mistaken, the authors might mean nondeterministic?

Yes, although the semantics differ from the use of nondeterminism in an NFA, so we prefer the term "probabilistic" to avoid confusion. Semantically, $\overset{\$}{\leftarrow}$ can be interpreted as selecting an element uniformly at random from some finite set (this notation is standard in the cryptographic literature). So in the context of Line 120, any of the $\texttt{follow}$ set will suffice to generate a witness, as will choosing, e.g., the lexicographically least element or some arbitrary deterministic (i.e., non-random) strategy. Later in Algorithm 4, we replace the uniform distribution with a weighted distribution over the $\texttt{follow}$ set based on the Markov transition tensor.

> Line 179, does $P_{\theta}$ induces a probability distribution or just give a weight?

We can think of $P_{\theta}$ as a black box which takes a string as input and returns a scalar value that approximates some actual distribution of interest. This value can be normalized or not - it is not our intent to debate the semantics of probabilistic programming, empirical risk or the minutiae of statistical learning theory. We will call $P_{\theta}$ a "probability distribution" by fiat and conduct a search for inputs that maximize it subject to some syntactic constraints.

> Line 213, Proof of Theorem 3.1, Do you assume that the topological order is given such that M is already strictly upper triangular?

No, the cost of topological sorting is accounted for in the complexity analysis. This term disappears in the case of Levenshtein automata as we mention in L403-L406, but otherwise for arbitrary ANFAs the cost is $\mathcal{O}(\log^2|Q|)$ in the states, $Q$.

> Line 239, Proof of Theorem 3.1, optimistically? Is this complexity analysis average complexity or worse-case complexity analysis?

"Optimistically" here refers to the best-case time complexity, hence $\Omega(\ldots)$. We make this remark without proof, but the best-case relates to our discussion with Reviewer C about tree growth rates, so it may be worth reviewing that discussion for a better understanding.

> Line 241, Proof of Theorem 3.1, $\texttt{choose}$ is used as defined in Theorem 2.4. There is no correctness proof for that Theorem (or algorithm)

In the context of Theorem 3.1, $\texttt{choose}$ is simply mentioned as an expository note to preface the subsequent decoding step and is not required for the proof of Theorem 3.1, which claims nonemptiness and does not require synthesizing a witness. We confess that $\texttt{choose}$ is slightly nonstandard and assert Theorem 2.4 without proof in the paper. We will now restate the theorem and proceed to sketch the proof.

#### Theorem 2.4

For any nonempty $(\varepsilon, \land)$-free regex, $e$, to witness $\sigma \in \mathcal{L}(e)$:

$$
\begin{aligned}
\texttt{follow}(e): E \rightarrow 2^\Sigma &= \begin{cases}
\{e\} &\text{ if } e \in \Sigma \\
\texttt{follow}(x) &\text{ if } e = x \cdot z\\
\texttt{follow}(x)\cup\texttt{follow}(z) &\text{ if } e = x \lor z
\end{cases} \\\\
\texttt{choose}(e): E \rightarrow \Sigma^+ &= \begin{cases}
e &\text{ if } e \in \Sigma \\
\big(s \overset{\$}{\leftarrow} \texttt{follow}(e)\big)\cdot \texttt{choose}(\partial_s e) &\text{ if } e = x \cdot z\\
\texttt{choose}\big(e' \overset{\$}{\leftarrow} \{x, z\}\big) &\text{ if } e = x \lor z
\end{cases}
\end{aligned}
$$

#### Proof Sketch

By struructural induction on $e$. WTS: $\texttt{choose}(e) \in \mathcal{L}(e)$.

* Base case ($e \in \Sigma$): Then $\texttt{choose}(e) = e$ with $e \in \mathcal{L}(e) = \{e\}$ trivially.
* Induction hypothesis (IH): Assume the claim holds for all subexpressions of $e$.
* Disjunctive case ($e=x\lor z$): Since $\mathcal{L}(e) \neq \varnothing$ by assumption, then either one or both of $\mathcal{L}(x)$, $\mathcal{L}(z)$ are nonempty. Then $\texttt{follow}(e) = \texttt{follow}(x) \cup \texttt{follow}(z)$ collects all possible initial symbols from either branch. Choose nonempty $e' \in \{x, z\}$ (possible by assumption), then recurse as $\texttt{choose}(e') \in \mathcal{L}(e')$ by IH, hence in $\mathcal{L}(x) \cup \mathcal{L}(z) = \mathcal{L}(e)$.
* Concatenative case ($e=x \cdot z$): By assumption $e$ is $\varepsilon$-free, so the left subexpression must contain terminals and $\texttt{follow}(e) = \texttt{follow}(x)$. Choose $s \in \texttt{follow}(x)$, then $\partial_s e = (\partial_s x)\cdot z$ (as $\delta(x)=\varnothing$ since $x$ is $\varepsilon$-free) and recurse as $\texttt{choose}(\partial_s e) = \tau \in \mathcal{L}(\partial_s e) = \mathcal{L}(\partial_s x) \circ \mathcal{L}(z)$ by the IH (n.b. $\partial_s e$ may introduce subterms containing $\varepsilon, \varnothing$ which can be handled via simplification: $\varepsilon \cdot w = w, \varnothing \cdot w = \varnothing$). By the semantic property of quotients, $b \in \mathcal{L}(\partial_a L) \Longleftrightarrow a\cdot b \in \mathcal{L}(L)$ and therefore $\tau \in \mathcal{L}(\partial_s e)$ implies $s \cdot \tau \in \mathcal{L}(e)$.
  
□

Review #252B
===========================================================================

> The tool does not read like a POPL paper.

The review does not read like a POPL review, but a layperson's hastily-construed remarks written ten minutes before the deadline.

> Most syntax errors, however, require only a few typographic modifications to repair, of which there are only a finite number of possibilities to consider. -- What is the evidence for this statement?

"Most syntax errors": See Figure 12. 60% of all repairs in Wong et al.'s (2019) Syntax and StackOverflow dataset contain four or fewer Levenshtein edits.

Finite possibilities: Obvious, if we assume a finite number of typographic modifications.

> the quotient ... ${b \mid ab \in L}$ --> the quotient ... ${x \mid ax \in L}$

Incorrect. The full statement for the left quotient is ${b \mid ab \in L}$ where $a: \Sigma$, $b: \Sigma^*$. We only use $x, z: E$ to denote the left and right subexpressions, respectively, or the regular expression $e: E$.

> witnesses -- you use the word witness without introducing its meaning

In model theory, a "witness" refers to an explicit instance of an existentially quantified statement. This is consistent with the standard English usage, i.e., a fact that attests to the truth of some statement, e.g., "The careless feedback was witness to the reviewer's lack of time or expertise."

> to witness \sigma -- there is no \sigma in the text that follows

$\texttt{choose}: E \rightarrow \Sigma^+$ has a string return type. $\sigma$ is used to denote a string, should be understood to mean the witness that $\texttt{choose}$ returns.

> follow 117 choose -- you use the terms without introducing their meaning

The meaning is explicitly defined on lines 111-117. $\texttt{follow}$ returns the subset of terminals that immediately "follow" in a regular expression (i.e., the valid single-terminal prefixes of some matching string), and $\texttt{choose}$ chooses one of the strings from the langauge of the regular expression.

Review #252C
===========================================================================

These concise remarks of this reviewer reflect a deep understanding of the parsing literature. We have reproduced and acknowledge the flaw in the proof of Theorem 3.1. Furthermore, we appreciate their advice on how to salvage the upper bound via Brent and Goldschlager (1983) which previously escaped our notice. This paper is indeed a hidden gem which should be more widely known. We will rebase the proof on Brent and Goldschlager's time bound, fallback to a fixpoint-detection based test for the implementation, and discuss the discrepancy more carefully.

Anecdotally, when the iteration bounds on Line 4 of $\texttt{reg_build}$ are relaxed to $[0, \infty)$, we did not observe any measurable downstream improvement due to the escape condition on Line 10 of the same algorithm, which detects the fixpoint and terminates in well under $\log(|Q||V|)$ iterations on the Python syntax repair benchmark.

> technical ideas needed to implement it are mostly straightforward ... the difficulty lies not really in finding algorithms to do these things, but to engineer a sequence of constructions

We agree the engineering details are mostly straightforward once the right algorithm has been developed. Where we disagree, is that the problem is simply a matter of translating existing theory into developer tools. The pure engineering approach has failed, as demonstrated by the lack of viable tools for syntax repair. The syntax repair problem is primarily algorithmic in nature and requires fresh theoretical contributions as well as novel engineering, which our work uniquely provides.

> In fact, it is easy to see that performing $M \mapsto M+M^2$ a number of $\ell$ times will collect exactly the strings that have a derivation tree of height at most $\ell$.

While true for the specific grammar presented, this does not generally hold as the entries of $M_i$ can parse strings of length up to $i^2$, where an entry in $M[r, r+n, v]$ represents parsability of the substring from index $r$ to $r+n$, i.e., $\sigma_{r, r+n}\vdash v$ (i.e., the substring $\sigma_{r, r+n}$ deriving nonterminal $v$ according to $P$).

We depict the best-case reachability scenario under $M \mapsto M+M^2$ iteration. Here, `■` in location $M[r, r+n]$ will be used to represent the existence of any $\sigma: \Sigma^+$ and any $v: V$ in any CFG $G:=\langle V, \Sigma, P, S\rangle$ such that $\sigma_{r, r+n} \vdash v$.

```
    M_0           M_1           M_2           M_3
□ ■ □ □ □ □   □ ■ ■ □ □ □   □ ■ ■ ■ ■ □   □ ■ ■ ■ ■ ■ 
□ □ ■ □ □ □   □ □ ■ ■ □ □   □ □ ■ ■ ■ ■   □ □ ■ ■ ■ ■ 
□ □ □ ■ □ □   □ □ □ ■ ■ □   □ □ □ ■ ■ ■   □ □ □ ■ ■ ■ 
□ □ □ □ ■ □   □ □ □ □ ■ ■   □ □ □ □ ■ ■   □ □ □ □ ■ ■ 
□ □ □ □ □ ■   □ □ □ □ □ ■   □ □ □ □ □ ■   □ □ □ □ □ ■ 
□ □ □ □ □ □   □ □ □ □ □ □   □ □ □ □ □ □   □ □ □ □ □ □ 
```

So an $M[0, n, S]$ entry can be reached in $\lceil \log_2 n \rceil$ steps, and in general, any $(r, c)$ entry can be attained after $\lceil \log_2 (c-r) \rceil$ iterations under *some* CFG.

Let us visualize the fixpoint computation under the specific CFG given, where $G = \{A \rightarrow AB, A \rightarrow a, B \rightarrow b\}$.

```
    M_0           M_1           M_2           M_3           M_4
□ ■ □ □ □ □   □ ■ ■ □ □ □   □ ■ ■ ■ □ □   □ ■ ■ ■ ■ □   □ ■ ■ ■ ■ ■
□ □ ■ □ □ □   □ □ ■ □ □ □   □ □ ■ □ □ □   □ □ ■ □ □ □   □ □ ■ □ □ □
□ □ □ ■ □ □   □ □ □ ■ □ □   □ □ □ ■ □ □   □ □ □ ■ □ □   □ □ □ ■ □ □
□ □ □ □ ■ □   □ □ □ □ ■ □   □ □ □ □ ■ □   □ □ □ □ ■ □   □ □ □ □ ■ □
□ □ □ □ □ ■   □ □ □ □ □ ■   □ □ □ □ □ ■   □ □ □ □ □ ■   □ □ □ □ □ ■
□ □ □ □ □ □   □ □ □ □ □ □   □ □ □ □ □ □   □ □ □ □ □ □   □ □ □ □ □ □
```

Indeed, we can reproduce the fixpoint convergence being linear in the length of the string. A more careful analysis will be needed to bound the slowest tree growth rate (i.e., the maximum number of iterations needed to ensure fixpoint convergence), but our cursory analysis would seem to rule out grammars with a superlinear convergence.

> An obvious alternative (but with worse run-time bounds) would be to give up the poly-logarithmic time bound and just iterate the squaring polynomially many times.

We concede the existence of grammars requiring $\mathcal{O}(n)$ fixpoint iterations of $M \mapsto M+M^2$ although we believe this to be the worst case scenario and despite our best efforts are unable to produce a CNF grammar where a polynomial upper bound is necessary. Perhaps a closer reading of Brent and Goldschlager's paper will prove instructive or the reviewer would be willing to elaborate on this point.

> The authors' mistake is to assume that the multplication operation they introduce is associative

Let us briefly address the question of non-associativity (see also [Bernardy and Jansson, 2016](https://arxiv.org/pdf/1601.07724#page=3) for a more detailed discussion). It would be problematic under cubing $(MM)M \neq M(MM)$, however direct cubing never actually occurs under the fixpoint $M \mapsto M+M^2$. So while this defect affects the equivalence step of the proof, it should *not* affect the infinite fixpoint, $M_\infty$. Short of formal verification, we have extensively tested the completeness property on a suite of grammars (including the test case provided by the reviewer), and have not detected any incompleteness bugs therein.

Review #252D
===========================================================================

We appreciate Reviewer D's balanced feedback and helpful remarks, which demonstrate a reasonable familiarity with the technique and its implementation. We acknowledge the slightly underdeveloped reranking discussion and the limited discussion of repair ambiguity in practice. Cross-language evaluation also seems prudent, however most academic datasets and prior work in the machine learning literature use the Python language, so we align with these for benchmarking purposes.

> Compare to LLM *with guidance*

We did run a number of unpublished experiments with constrained decoding and from our experience, latency, precision and recall underperform n-gram constrained decoding with neural reranking. The main bottleneck is the tokens per second (TPS) throughput of LLM decoders, which is severely bandwidth-limited relative to n-gram decoding. For comparison, the fastest small langauge models running on Groq custom silicon (server-grade hardware) can expect to see [~10^3 TPS](https://groq.com/pricing) as of Sept. 2025, whereas a 4-gram decoder can achieve ~10^7 TPS on Apple M4 chips (commodity laptop hardware), or roughly a 10000x speedup. While LLM decoders are generally more sample-efficient, the TPS bottleneck is difficult to overcome.

Most popular constrained LLM decoding frameworks do not support CFG-NFA intersections so this must currently be implemented externally. The landscape of such libraries is also rapidly evolving (see also [Outlines](https://github.com/dottxt-ai/outlines), [SynCode](https://github.com/structuredllm/syncode), [GenLM Control](https://github.com/genlm/genlm-control), [Constrained Diffusion](https://constrained-diffusion.ai) et al.) and it would be impractical to compare each one individually on the task of syntax repair. Nevertheless, we will consider adding these experiments to the paper.

> The role of symbolic predicates in automata is underspecified... Please be formal also wrt line 352 in the paper 

Noted. The symbolic predicate is mostly syntactic sugar for presentation clarity, but does not affect the regular expression, which always uses terminal literals. As this symbolic predicate only appears in a very limited scope (namely storing and decoding of unit nonterminals in the parse chart), introducing the EBA and associative derivative machinery seems like an unnecessary notational burden on the reader, who is already taxed as the reviewer rightly points out.