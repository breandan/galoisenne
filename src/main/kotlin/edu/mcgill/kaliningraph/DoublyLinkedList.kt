package edu.mcgill.kaliningraph

fun main() {
  val head = DLL("a") + "b" + "c" + "d"
  println(head)
  println(head.succ?.pred === head)
  println(head.succ?.succ?.pred === head.succ)
}

class LL<T>(val head: T, val succ: LL<T>? = null) {
  operator fun plus(t: T): LL<T> =
    if (succ == null) LL(head, LL(t)) else LL(head, succ + t)

  fun last(): T = if (succ == null) head else succ.last()
  fun first() = head

  override fun toString(): String = "[$head]" + "->" + succ.toString()
}

class DLL<T>(
  val head: T,
  pred: (DLL<T>) -> DLL<T>? = { null },
  succ: (DLL<T>) -> DLL<T>? = { null }
) {
  val pred: DLL<T>? by lazy { pred(this) }
  val succ: DLL<T>? by lazy { succ(this) }

  operator fun plus(t: T): DLL<T> =
    if (succ == null)
      if (pred == null) DLL(head, { null }, { me -> DLL(t, { me }, { null }) })
      else DLL(head, { pred!!.append(it) }, { me -> DLL(t, { me }, { null }) })
    else // succ != null
      if (pred == null) DLL(head, { null }, { (succ!! + t).prepend(it) })
      else DLL(head, { pred!!.append(it) }, { (succ!! + t).prepend(it) })

  fun append(succ: DLL<T>): DLL<T> =
    if (pred == null) DLL(head, { null }, { succ })
    else DLL(head, { pred!!.append(it) }, { succ })

  fun prepend(pred: DLL<T>): DLL<T> =
    if (succ == null) DLL(head, { pred }, { null })
    else DLL(head, { pred }, { succ!!.prepend(it) })

  fun last(): T = if (succ == null) head else succ!!.last()
  fun first() = head

  override fun toString(): String = "[$head]" + "<->" + succ.toString()
}