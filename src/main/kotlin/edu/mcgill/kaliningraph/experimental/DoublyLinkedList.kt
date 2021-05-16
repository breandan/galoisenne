package edu.mcgill.kaliningraph.experimental

class LL<T>(val head: T, val next: LL<T>? = null) {
  operator fun plus(t: T): LL<T> =
    if (next == null) LL(head, LL(t)) else LL(head, next + t)

  fun last(): T = if (next == null) head else next.last()
  fun first() = head

  override fun toString(): String = "[$head]" + "->" + next.toString()
}

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
    DLL(
      head = head,
      prev = { prev + it },
      next = { me ->
        if (!hasNext()) DLL(t, { me })
        else me + (next + t)
      }
    )

  operator fun plus(next: DLL<T>): DLL<T> =
    DLL(
      head = next.head,
      prev = { this },
      next = { me ->
        if (!next.hasNext()) me
        else me + next.next
      }
    )

  fun insert(t: T): DLL<T> =
    DLL(
      head = head,
      prev = { prev + it as T },
      next = { me ->
        if (!hasNext()) this + t
        else DLL(t, { me }, { it + next })
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