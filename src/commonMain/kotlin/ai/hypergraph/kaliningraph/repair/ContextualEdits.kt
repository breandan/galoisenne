package ai.hypergraph.kaliningraph.repair

enum class EditType { INS, DEL, SUB }
data class ContextEdit(val type: EditType, val context: Context, val newMid: String) {
  override fun toString(): String = context.run {
    "$type, (( " + when (type) {
      EditType.INS -> "$left [${newMid}] $right"
      EditType.DEL -> "$left ~${mid}~ $right"
      EditType.SUB -> "$left [${mid} -> ${newMid}] $right"
    } + " // " + when (type) {
      EditType.INS -> "$left [${newMid}] $right"
      EditType.DEL -> "$left ~${mid}~ $right"
      EditType.SUB -> "$left [${mid} -> ${newMid}] $right"
    } + " ))"
  }
}
data class CEAProb(val cea: ContextEdit, val idx: Int, val frequency: Int) {
  override fun equals(other: Any?): Boolean = when (other) {
    is CEAProb -> cea == other.cea && idx == other.idx
    else -> false
  }
  override fun hashCode(): Int = 31 * cea.hashCode() + idx
  override fun toString(): String = "[[ $cea, $idx, $frequency ]]"
}
data class Context(val left: String, val mid: String, val right: String) {
  override fun equals(other: Any?) = when (other) {
    is Context -> left == other.left && mid == other.mid && right == other.right
    else -> false
  }

  override fun hashCode(): Int {
    var result = left.hashCode()
    result = 31 * result + mid.hashCode()
    result = 31 * result + right.hashCode()
    return result
  }
}

data class CEADist(val allProbs: Map<ContextEdit, Int>) {
  val P_delSub = allProbs.filter { it.key.type != EditType.INS }
  val P_insert = allProbs.filter { it.key.type == EditType.INS }
  val P_delSubOnCtx = P_delSub.keys.groupBy { it.context }
  val P_insertOnCtx = P_insert.keys.groupBy { it.context }
}