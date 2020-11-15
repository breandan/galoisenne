package edu.mcgill.kaliningraph


fun main() {
  var head = DLL("a").let { ('c'..'z').fold(it) { a, b -> a + b.toString() } }
  println(head.size)
  assertDLL(head)

  head = head.insert("b")
  assertDLL(head)
//  println(head.reversed())
}

private fun assertDLL(head: DLL<String>) =
  (1 until head.size - 1).map { head[it] }.forEach {
    val isDLLForward = it.succ.pred === it
    val isDLLBack = it.pred.succ === it
    val isDLLTwice = it.succ.succ.pred.pred === it // TODO: fixme
    println(it)
    assert(isDLLForward) { "${it.succ.pred} != $it" }
    assert(isDLLBack) { "${it.pred.succ} != $it" }
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
  pred: (DLL<T>) -> DLL<T> = { it },
  succ: (DLL<T>) -> DLL<T> = { it }
) {
  val pred: DLL<T> by lazy { pred(this) }
  val succ: DLL<T> by lazy { succ(this) }
  val size: Int
    get() = 1 + (if (hasNext()) 0 else succ.size)
  val tail: T
    get() = if (hasNext()) head else succ.tail

  operator fun plus(t: T): DLL<T> =
    if (hasNext())
      DLL(head, { pred.append(it) }, { me -> DLL(t, { me }, { it }) })
    else
      DLL(head, { pred.append(it) }, { it.append(succ + t) })

  fun append(succ: DLL<T>): DLL<T> = succ.prepend(this) // TODO: flip?

  fun prepend(pred: DLL<T>): DLL<T> =
    if (hasNext()) DLL(head, { pred }, { it })
    else DLL(head, { pred }, { succ.prepend(it) })

  fun insert(t: T): DLL<T> =
    if (hasNext()) this + t
    else DLL(t, { it.prepend(pred + head) }, { succ.prepend(it) })

  operator fun get(i: Int): DLL<T> = if (i == 0) this else succ[i - 1]

//  fun reversed(): DLL<T> =
//    if (hasNext()) this
//    else DLL(tail, { pred.append(it) }, { succ.reversed() + head })

  override fun toString(): String =
    if (hasNext()) "[$head]" else "[$head]<->$succ"

  fun hasNext() = succ == this
}