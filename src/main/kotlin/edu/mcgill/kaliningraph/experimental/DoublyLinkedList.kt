package edu.mcgill.kaliningraph.experimental

class LL<T>(val head: T, val next: LL<T>? = null) {
  operator fun plus(t: T): LL<T> =
    if (next == null) LL(head, LL(t)) else LL(head, next + t)

  fun last(): T = if (next == null) head else next.last()
  fun first() = head

  override fun toString(): String = "[$head]" + "->" + next.toString()
}

// TODO: Translate to doubly-linked graph

class DLL<T>(
  val head: T,
  prev: (DLL<T>) -> DLL<T> = { it },
  next: (DLL<T>) -> DLL<T> = { it }
): Iterable<DLL<T>> {
  val prev: DLL<T> by lazy { prev(this) }
  val next: DLL<T> by lazy { next(this) }
  val size: Int by lazy { 1 + (if (!hasNext()) 0 else this.next.size) }
  val tail: T by lazy { if (!hasNext()) head else this.next.tail }

  operator fun plus(t: T): DLL<T> =
    if (t is DLL<*> && t.head!!.javaClass == head!!.javaClass)
      t.fold(this) { a, b -> a + b.head as T }
    else if (t!!.javaClass == head!!.javaClass) DLL(
      head = head,
      prev = { prev + it as T },
      next = { me ->
        if (!hasNext()) DLL(t, { me })
        else me.append(next + t)
      }
    )
    else throw Exception("Type error, t: " +
        if (t is DLL<*>) "DLL<${t.head!!.javaClass.simpleName}>"
        else t.javaClass.simpleName
    )

  private fun append(next: DLL<T>): DLL<T> =
    DLL(
      head = next.head,
      prev = { this },
      next = { me ->
        if (!next.hasNext()) me
        else me.append(next.next)
      }
    )

  fun insert(t: T): DLL<T> =
    DLL(
      head = head,
      prev = { prev + it as T },
      next = { me ->
        if (!hasNext()) this + t
        else DLL(t, { me }, { it.append(next) })
      }
    )

  operator fun get(i: Int): DLL<T> = if (i == 0) this else next[i - 1]

  fun reversed(): DLL<T> = if (!hasNext()) this else next.reversed() + head

  override fun toString(): String =
    if (!hasNext()) "[$head]" else "[$head]<->$next"

  fun hasNext() = next != this
  fun hasPrev() = prev != this

  override fun iterator() =
    generateSequence(this) { if (it.next != it) it.next else null }.iterator()
}