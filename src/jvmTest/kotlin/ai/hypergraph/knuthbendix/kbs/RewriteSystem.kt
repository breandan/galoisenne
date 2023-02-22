package ai.hypergraph.knuthbendix.kbs

import java.util.*
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Predicate
import java.util.stream.Collectors

/**
 * A rewrite systemn that can complete itself
 * @param <T> The type of the "characters" in the text
</T> */

  /**
   * Make a rewrite system with the given comparator and ruleset
   * @param rules      A map of completeRules
   * @param comparator A comparator indicating the sort order
   */

class RewriteSystem<T>(rules: Map<List<T>, List<T>>, val comparator: Comparator<Collection<T>>) {
  //Convert Rules to actual Rule's, big to small
  //Note that ordering does not belong in Rule, because a rule does not need to
  //know the ordering.
  private val rules: Set<Rule<T>> = rules.keys.map { e: List<T> ->
      val t2 = rules[e]!!
      if (comparator.compare(e, t2) > 0) Rule(e, t2) else Rule(t2, e)
    }.toSet()
  private var completeRules: MutableSet<Rule<T>>? = null

  /**
   * Apply completeRules until a normal form is reached using the rules that set up the system
   *
   * @param pInput list to rewrite
   * @return a rewritten copy of the list
   */
  fun rewrite(pInput: List<T>): List<T> {
    return rewriteWith(pInput, rules)
  }

  /**
   * Returns a rewriten version of the input list the input remains unchanged.
   * @param pInput the list to rewrite
   * @return a rewritten copy of the list
   */
  fun getUniqueNF(pInput: List<T>): List<T> =
    complete().run { rewriteWith(pInput, completeRules!!) }

  /**
   * Helper function for replacements withs {@see RewriteSystem::rewrite} and {@see getUniqueNF()}
   * @param pInput   the input to rewrite
   * @param ruleSet  the set of rules to use
   * @return a rewritten copy of the input list
   */
  private fun rewriteWith(pInput: List<T>, ruleSet: Set<Rule<T>>): List<T> {
    val input = LinkedList(pInput)
    var doneSomething: Boolean
    do {
      doneSomething = false
      for (rule in ruleSet) { doneSomething = rule.apply(input) || doneSomething }
    } while (doneSomething)
    return input
  }

  /**
   * Changes the list to its rewriten version (input is changed).
   * Only considers completeRules that are already computed!
   * A call to {@see RewriteSystem::complete()}
   *
   * @param list the list to rewrite
   */
  private fun changeToUniqueNF(list: LinkedList<T>) {
    var doneSomething: Boolean
    do {
      doneSomething = false
      for (rule in completeRules!!) {
        doneSomething = rule.apply(list) || doneSomething
        //Heuristic if a rule was applied, start from te beginning
        //Simple rules are in the beginning of the list due to the order of
        //the TreeSet complete rules
        if (doneSomething) break
      }
    } while (doneSomething)
  }

  /**
   * Complete the rule system
   *
   * This is done by looking for overlap in the rules and extracting critical pairs from them. From each critical
   * pair, a new rule is made. Rules whose "from" part can be rewritten are removed, they are no longer
   * useful because we choose to always apply the new rule first.
   *
   * This is the "Knuth–Bendix completion algorithm"
   */
  fun complete() {
    if (completeRules != null) return
    completeRules = TreeSet { o1: Rule<T>, o2: Rule<T> -> o1.compareTo( o2, comparator ) }
    completeRules!!.addAll(rules)

    //Using treesets with a special order does not speedup
    val criticalPairs: MutableCollection<CriticalPair<T>> = HashSet<CriticalPair<T>>()
    val toProcess: MutableCollection<Rule<T>> = HashSet(completeRules)
    while (true) {

      //Collect the critical pairs
      for (rule1 in completeRules!!) {
        //We only need to look at combinations with new completeRules
        for (rule2 in toProcess) {
          criticalPairs.addAll(rule1.getCritical(rule2))
          criticalPairs.addAll(rule2.getCritical(rule1))
        }
      }
      toProcess.clear() //done with these

      //palatalise the first optimisation of the rules
      criticalPairs.parallelStream().forEach { c ->
        changeToUniqueNF(c.to1)
        changeToUniqueNF(c.to2)
      }

      //No critical pairs left, we are done
      if (criticalPairs.isEmpty()) break
      for (criticalPair in criticalPairs) {
        // check if anny new rule applies
        changeToUniqueNF(criticalPair.to1)
        changeToUniqueNF(criticalPair.to2)
        val to1: LinkedList<T> = criticalPair.to1
        val to2: LinkedList<T> = criticalPair.to2
        val compare = comparator.compare(to1, to2)
        if (compare == 0) {
          //new rule is 0 transformation after further simplification
          continue
        }
        val big: List<T> = if (compare > 0) to1 else to2
        val small: List<T> = if (compare < 0) to1 else to2
        val tRule = Rule(big, small)
        if (completeRules!!.add(tRule)) {
          //Rule was new
          toProcess.add(tRule)
        }
      }

      //Rules of which the "from" part can be rewritten can be removed, if the new rule
      //the rule can never be applied because the new rule will rewrite it first (we choose this)
      for (toProces in toProcess) completeRules!!.removeIf { rule: Rule<T> -> toProces.canOptimize(rule) }

      //Reuse the critical pairs set
      criticalPairs.clear()
    }
  }

  /**
   * Calculate the normal forms of the system.
   *
   * This is done by completing the system and applying the completed rules to the empty sting and
   * every letter occurring in the rules that were supplied at creation time. Then we add every letter
   * occurring in the rules that were supplied at creation time to every newly found normal form.
   * this process is repeated untlil there are no new normal forms found
   *
   * @return a set of unique normal forms to which every input containing only letters that occur in the rules
   * will be reduced to using {@see getUniqueNF()}
   */
  fun calcNormalForms(): Set<List<T>> {
    complete()
    val elements: MutableSet<T> = HashSet()
    rules.forEach(Consumer { e: Rule<T> ->
      e.from.forEach(Consumer { e: T -> elements.add(e) })
      e.to.forEach(Consumer { e: T -> elements.add(e) })
    })
    val baseForms: MutableSet<List<T>> = HashSet()
    val pasteSet = Paster(elements)
    var suggestion: MutableSet<List<T>> = HashSet()
    suggestion.add(ArrayList())
    val newSuggestions: MutableSet<List<T>> = HashSet()
    while (suggestion.size > 0) {
      newSuggestions.clear()
      for (sugestion in suggestion) {
        val clean = getUniqueNF(sugestion)
        if (baseForms.add(clean)) newSuggestions.add(clean)
      }
      suggestion = pasteSet.paste(newSuggestions)
    }
    return baseForms
  }

  fun getCompleteRules(): Set<Rule<T>> = complete().run { completeRules!!.map { Rule(it) }.toSet()  }

  fun getRules(): Set<Rule<T>> = rules.stream().map { other: Rule<T> -> Rule(other) }.collect(Collectors.toSet())
}