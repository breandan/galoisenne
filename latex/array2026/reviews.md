ARRAY 2026 Paper #4 Reviews and Comments
===========================================================================
Paper #4 Towards a Linear-Algebraic Hypervisor


Review #4A
===========================================================================

Overall merit
-------------
3. Weak accept

Reviewer expertise
------------------
2. Some familiarity

Paper summary
-------------
The goal is to show how GPUs can be used to simulate executions of many different programs in parallel.  The programs in question are expressed in a simple, low-level computational model, RASP.  The relevance to arrays comes in because a higher-level array language is introduced, and the lowering to RASP is defined.  In an experiment, 8 million random array programs of length 100 are generated.  These are evaluated to determine the number of steps to halt (capping number of steps at 10^6).  They are evaluated using a CUDA implementation (I think executing 32 simulations in parallel?) and also a Kotlin/JVM implementation.  The experiment is conducted with different parameters and different Nvidia GPUs.  The GPU implementation is much faster.

Comments for authors
--------------------
Wow, that was a really dense 3 pages.  There is a lot of notation that is undefined, but (a) when I look closely, I can still figure out what you mean, and (b) I'm not sure how you could do better, given the space constraint.  Even if I didn't understand every detail, you got me interested to learn more.  Techniques for running these simulations in parallel in CUDA, where you have to avoid branch divergence, are probably applicable to many program analysis approaches, like symbolic execution, model checking, static analysis, etc.

The relevance to ARRAY is rather limited, but the toy high level language uses arrays, so that is something.  It might be interesting to apply this tool to programs that compute something interesting, rather than random programs.

Comparing to a JVM implementation might be a little "straw man".   What if you compared to a fast multithreaded C implementation with many cores?  I'd be curious to see if the GPU still wins.

At line 168, you write "(3)" but maybe it should be "(2)" since there is no 2.
What does "scr" stand for?



Review #4B
===========================================================================

Overall merit
-------------
4. Accept

Reviewer expertise
------------------
2. Some familiarity

Paper summary
-------------
This paper introduces a parallelized Virtual Machine for the RASP abstract machine model. The core problem the authors solve is that GPUs traditionally struggle to simulate multiple independent programs concurrently due to branch divergence. The authors bypass this by transforming the entire state-transition function of the abstract machine into a multilinear polynomial representation. By converting control-flow into deterministic branchless boolean functions, the VMs can be run on a GPU efficiently. The authors design a heapless array programming language, compile it to this multilinear VM, and concurrently evaluate 8 million programs to search for busy beavers, achieving up to a 147× speedup over JVM execution.

Comments for authors
--------------------
This topic is interesting. The multilinear-polynomial interpretation is nice, but the implementation seems closer to a branchless GPU interpreter than to an implementation that directly exploits algebraic structure. It would be better to clarify whether the linear-algebraic view leads to concrete optimizations, analysis, or future compilation strategies, or whether it is mainly a theoretical characterization.



Review #4C
===========================================================================

Overall merit
-------------
3. Weak accept

Reviewer expertise
------------------
2. Some familiarity

Paper summary
-------------
This paper implements an GPU interpreter for programs encoded as a
fixed-size words. Building on RASP, it introduces a vectorized version
of RASP whose transition functions can be encoded as linear functions
over fixed-size words. A small array programming language is
described, its semantics are provided, and an experiment over randomly
generated programs is shown. Further work aims to find efficient
matrix multiply programs and CFG parsing.

Comments for authors
--------------------
While this work fits in the scope of the workshop, in my opinion, it
could be made easier to read by expanding on (or providing examples
of) some parts of the formulation so that readers can verify their
understanding. This can be done in an appendix. The following would
benefit this reviewer:

- Showing a multilinear polynomial representation for an example
- Justifying the use of the Kronecker delta (encoding memory write as a linear function?)
- Working out some examples for the transition functions described just above 2.2

Some reading ergonomic suggestions for the syntax: ipt => inp, scr => spad, opt => out

Some context about why that particular evaluation is interesting might
be needed in the paper. While I can see matrix multiply being
interesting in the future, why is this evaluation a stepping stone towards that?

Also, fast matrix multiply algorithms and fast matrix multiply
implementations are quite different. By using the word "program" in
the conclusion, this difference is obscured.