package edu.mcgill.kaliningraph


fun main() {
  var head = DLL("a").let { ('c'..'z').fold(it) { a, b -> a + b.toString() } }
  assertDLL(head)

  head = head.insert("b")
  assertDLL(head)
//  println(head.reversed())
}

private fun assertDLL(head: DLL<String>) =
  (1 until head.size - 1).map { head[it] }.forEach {
    val isDLLForward = it.succ?.pred === it
    val isDLLBack = it.pred?.succ === it
    println(it)
    assert(isDLLForward) { "${it.succ?.pred} != $it" }
    assert(isDLLBack) { "${it.pred?.succ} != $it" }
  }

class LL<T>(val head: T, val succ: LL<T>? = null) {
  operator fun plus(t: T): LL<T> =
    if (succ == null) LL(head, LL(t)) else LL(head, succ + t)

  fun last(): T = if (succ == null) head else succ.last()
  fun first() = head

  override fun toString(): String = "[$head]" + "->" + succ.toString()
}

// TODO: Translate to doubly-linked graph

class DLL<T>(
  val head: T,
  pred: (DLL<T>) -> DLL<T>? = { null },
  succ: (DLL<T>) -> DLL<T>? = { null }
) {
  val pred: DLL<T>? by lazy { pred(this) }
  val succ: DLL<T>? by lazy { succ(this) }
  val size: Int
    get() = 1 + (succ?.size ?: 0)
  val tail: T
    get() = if(succ == null) head else succ!!.tail

  operator fun plus(t: T): DLL<T> =
    if (succ == null)
      DLL(head, { it.prepend(pred) }, { me -> DLL(t, { me }, { null }) })
    else
      DLL(head, { it.prepend(pred) }, { succ?.plus(t)?.prepend(it) })

  fun append(succ: DLL<T>?): DLL<T> =
    DLL(head, { it.prepend(pred) }, { succ })

  fun prepend(pred: DLL<T>?): DLL<T> =
    DLL(head, { pred }, { succ?.prepend(it) })

  fun insert(t: T): DLL<T> =
    if(succ == null) this + t
    else DLL(t, { it.prepend(pred?.plus(head)) }, { succ?.prepend(it) })

  operator fun get(i: Int): DLL<T> = if(i == 0) this else succ!![i - 1]

  fun reversed(): DLL<T> =
    if(succ == null) this
    else DLL(tail, { it.prepend(pred) }, { TODO() })

  fun last(): T = if (succ == null) head else succ!!.last()
  fun first() = head

  override fun toString(): String = "[$head]<->$succ"
}