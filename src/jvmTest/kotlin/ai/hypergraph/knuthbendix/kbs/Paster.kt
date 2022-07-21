package ai.hypergraph.knuthbendix.kbs

/**
 * Class that creates every possible outcome of adding a letter of the baseset to the given set of lists
 * Created by Robbert Gurdeep Singh on 24/02/16.
 */
class Paster<T>(base: Collection<T>) {
  private val base = HashSet<T>().apply { addAll(base) }

  fun paste(current: Set<List<T>>): MutableSet<List<T>> {
    val newSet = HashSet<List<T>>()
    for (cur in current) {
      for (end in base) {
        val tmp = ArrayList<T>(cur.size + 1)
        tmp.addAll(cur)
        tmp.add(end)
        newSet.add(tmp)
      }
    }
    return newSet
  }
}