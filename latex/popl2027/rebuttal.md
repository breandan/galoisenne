# Overview

We thank all three reviewers for their thorough and thoughtful remarks. We are particularly encouraged that, despite some minor readability issues, they found our paper "elegant", "easy to follow," and the implementation "a significant step up from previous approaches".

# Proposed Change List

We propose to resubmit the manuscript with the following concrete revisions:

- Explicitly address the tool's targeted use case (single-line syntax repair) and postprocessing steps
- Add a dedicated evaluation assessing the impact of semantic filtering as per OrdinalFix
- Better position this work and its novelty relative to existing literature in related work
- Extend related work to include "The intersection of FSA and DCGs" ([van Noord, '95](https://aclanthology.org/P95-1022.pdf))
- Address minor readability issues regarding notation and terminology

# Detailed Response

## Reviewer A

We commend **Reviewer A** for their detailed feedback and for raising several good questions that we hope to address below.

### Data selection

First, to alleviate any doubt: all baselines were evaluated on the exact same test data -- 2,238 syntactic program repairs from Wong et al.'s ('19) dataset, fewer than 80 lexical tokens and 4 edits, disjoint from the training set. To clarify, we *do not* filter by any additional criteria and *do not* filter the 604 repair instances whose reference repair was outside the intersection.

None of the test data was ever seen during the training of any model except possibly `GPT-5.4-mini`. While we cannot be completely certain, it is likely that `GPT-5.4-mini` was exposed to Wong et al.'s raw dataset somewhere in its training corpus (cutoff [Aug. 31, 2025](https://developers.openai.com/api/docs/models/gpt-5.4-mini)), however, it significantly underperforms our approach, so we deem the risk minimal.

### Intended use case

The technique we propose should be distinguished from the tool, which is intended to target localized syntax repairs, consisting primarily of single malformed lines of code. As we emphasize in Sec. 3, the limiting factors are $|Q|$, the number of states in the parse lattice and $|A_\varnothing|$, the span of the lattice's transition relation. These parameters, we concede, are taxing for real-time whole-program analysis, but well-suited to repairs localized to a single line or a small block of code, as may be found residing in Markdown snippets, computational notebooks or structured text editors.

In common editing scenarios, we find typographic errors are typically confined to individual lines, and in most programming languages, lines are typically length-bounded by stylistic conventions to maximize visibility. For example, in Wong et al.'s Python dataset, 99.87% of lines with at least one token fall below 80 lexical tokens and 84.78% of individual lines whose repair contains at least one lexical edit also contain fewer than 4 lexical edits. For these reasons, we consider the data selection filters defensible for single-line repair. Naturally, if we consider offline usage scenarios, longer wall clock timeouts and larger chunks are possible.

### Prior work and comparison with OrdinalFix

The connection to van Noord ('95) is, indeed, well-spotted. We only recently and independently became aware of this ACL gem, and would be more than happy to cite his prior work. In fact, van Noord adopts the more expressive setting of DCGs and proves the undecidability of intersection nonemptiness there. While he does not evaluate the idea on an empirical benchmark, discuss parallelization, or draw the connection to programming language syntax as does this paper, he clearly anticipated the significance of CFG-NFA intersections for syntax repair and justly deserves credit for his early work.

OrdinalFix (Zhang et al., '23) does not impose a statistical ordering over the candidate set and only returns a small number of semantically valid repairs. Unlike Tidyparse, the authors use a dataset of synthetically corrupted clean code (mutated Middleweight Java code) and a natural error dataset (DeepFix), but only measure compiler acceptance, not blind recovery of human repairs. For this reason, it cannot be compared in the same way as our other baselines.

> Perhaps in practice we do not need a probabilistic ranking and the minimum-edit repairs are already sufficient.

Fig. 13 strongly suggests otherwise. Often (roughly half) of the time, the ground truth human repair requires extending the Levenshtein radius 2-3 edits beyond the language edit distance (minimum edit repairs). In those cases, there are between $2^8$ and $2^{26}$ admissible repairs in the Python language with the same edit distance. A deterministic repair procedure is extremely unlikely to surface the user's intended repair in language intersections with such high cardinality.

**We propose an additional evaluation in the spirit of OrdinalFix**: let us assume OrdinalFix's semantic filtering perfectly agrees with the Python reference compiler (actually implementing the OrdinalFix architecture for Python would be highly nontrivial, so let's just assume they implement this to strengthen their argument). To understand the effect of subsequent semantic filtering, we will evaluate on syntax errors from Wong et al.'s dataset with the original lexical filtering criteria, and additionally, whose human repair (1) is accepted by the Python 3.8.11 reference compiler and (2) does not require inserting a fresh identifier. For such repair instances, we will report the frequency of top-{1, 10, 100} repairs that Tidyparse generates which are rejected by the reference compiler after splicing in source-named identifiers using a deterministic postprocessing step (to be described below) and the additional latency this postprocessing plus semantic rejection introduces. Tidyparse-generated repairs containing fresh identifier insertions will be treated as a compiler failure.

### Postprocessing

Regarding identifier names, this is less of a problem than would initially appear. Once the top-k lexical repairs are produced, they can be merged into the original snippet, with source names and identifiers restored using the Levenshtein alignment. The only special case that requires special care is when a fresh named identifier is inserted or substituted into the lexical sequence. For example, suppose the original line of code is `= fizz)1)`, which would be lexicalized as `= ID ) NUM )`. We first generate a list of admissible and high probability lexical repairs, then, before displaying repairs to the user, lazily filter them through an incremental typechecker (e.g., `ty` in the case of Python) to prune any semantically unsound repairs. All displayed repairs without fresh identifiers will be filtered for semantic validity. Suppose the user's selected repair is `ID = ID ( NUM )`. This would create a repair `[PLACEHOLDER] = fizz(1)`, in which case we add a placeholder, move the cursor to the location, then open a secondary popup with contextually-scoped identifier names so the user can select one. Various IDE vendors have different names for this feature (e.g., [code templates](https://help.eclipse.org/latest/index.jsp?topic=%2Forg.eclipse.jdt.doc.user%2FgettingStarted%2Fqs-EditorTemplates.htm), [live templates](https://www.jetbrains.com/help/idea/using-live-templates.html), or [replacement fields](https://learn.microsoft.com/en-us/visualstudio/ide/code-snippets?view=visualstudio#edit-replacement-fields)).

While these postprocessing and user-facing details are mostly supplementary to this work and proper treatment deserves a separate paper, we have mostly worked them out. We would happily add a short section describing these engineering details, sketching the path for integrating single-line syntax repair into a real-time editor.

## Reviewer B

We would like to thank **Reviewer B** for their generous feedback and kind remarks.

We, too, have long sought to understand why exhaustive search appears to confer an advantage over few-shot neural repair. Anecdotally, we call the observed phenomenon the *decoder-retriever* gap: for certain constrained decoding problems where the admissible set is potentially large but finite, we observe that high-recall approximate retrieval outperforms low-throughput but precise language models at top-k decoding. On such tasks, we find it highly advantageous to exhaustively enumerate the search space with a lightweight decoder and then rerank the best candidates, rather than directly sample from a high-fidelity but slow LLM (either unconstrained or via constrained decoding). **Intuitively, it is "easier" to answer a multiple choice question than it is to synthesize an answer ex nihilo.** Naturally, this intuition is highly speculative and requires validation on other constrained decoding tasks, but at least for syntax repair we can report with high confidence that such a gap exists, even relative to state-of-the-art foundation models.

To what extent our advantage can be attributed to reranking versus retrieval is partially depicted by Fig. 17 -- we agree that it would be helpful to evaluate other methods with LaTeR reranking. While true that LaTeR can rerank other candidate pools, bolting LaTeR onto Seq2Parse/BIFI would undermine the objective of lightweight decoding: the primary benefit of a lightweight decoder is high-throughput and low-latency sampling from $\ell_\cap$. Guaranteeing that Seq2Parse and BIFI retrieve the ground-truth repair WHP would require sampling thousands of repairs and many seconds using the reference implementations of those architectures. Remember that LaTeR reranking does not alter the retrieved set, just reorders the top-512 best solutions, so if the ground-truth repair is not retrieved in the decoding phase or does not fall in the top-512, it will never be surfaced to the top of the final repair list. This can be seen in Fig. 15, which depicts BIFI's recall on 20,000 sampled outputs -- this bounds what *any* reranker could achieve on that returned pool. Since Tidyparse's top-10 result exceeds that bound, reranking alone cannot close the gap.

In practice, what occurs under higher Levenshtein margins and edit distance cutoffs is that the candidate space must be sampled and cannot be exhaustively decoded, even with an extremely high-throughput sampler. For repairs beyond the language edit distance, the size of the sample space grows rapidly in proportion to the number of distinct repairs that can be retrieved in a fixed time limit, so a more refined statistical prior becomes important. The benefit of Levenshtein intersection at greater edit distances versus ordinary constrained decoding quickly diminishes without additional constraints (e.g., affine gaps, common substitutions, type safety).

> Is 80 the length of the program?

Yes, 80 is the length of the original broken code snippet. The wording here should be clarified.

## Reviewer C

We thank **Reviewer C** for their careful consideration. We agree that some of the material presented is standard formal language theory; however, it is our hope the additional effort devoted to exposition and detailed running examples will be appreciated by the programming language community, who may be less familiar with these techniques.

To correct a small misunderstanding, the intersection we construct is exact. The Nederhof construction overapproximates the CFG and provides a lightweight statistical prior for decoding repairs the intersection language, which are finally reranked with a small transformer reranker.

We disagree that the current implementation is "too slow for real-life use". While there is always room for improvement, subsecond delay is comparable with the round-trip delay of LLM-based code completion and repair. While more granular profiling and benchmarking is needed, we believe further performance optimizations could reduce the delay by another factor of ten without a significant drop in repair precision.

> Not clear why the bit domain warrants special attention

These are slightly different from an implementation perspective, because a naïve implementation use a dynamic set whose semantics are slightly nontrivial. In the case of context-free parsing, the set cardinality is bounded by $|V|$, so this can be backed by a bitset rather than a hashset or some other data structure.

Thank you for the helpful notational comments, which we hope to address in a revised submission of the paper.