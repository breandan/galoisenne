package ai.hypergraph.kaliningraph.experimental

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
      next = { me ->
        if (!hasNext()) DLL(t, prev = { me })
        else me.append(next + t)
//        else me + (next + t)
      }
    )

// TODO: can these two be merged?
  operator fun plus(other: DLL<T>): DLL<T> =
    other.fold(this) { a, b -> a + b.head }
  private fun append(next: DLL<T>): DLL<T> =
    DLL(
      // n.b. head is advanced! (wrong view for publicly-facing API)
      head = next.head,
      prev = { this },
      next = { me ->
        if (!next.hasNext()) me
        else me.append(next.next)
      }
    )

// TODO: This would appear to work, but head/prev is broken
//  operator fun plus(next: DLL<T>): DLL<T> =
//    DLL(
//      head = head,
//      prev = { this },
//      next = { me ->
//        if (!next.hasNext()) me
//        else me + next.next
//      }
//    )

  fun insert(t: T): DLL<T> =
    DLL(
      head = head,
      next = { me ->
        if (!hasNext()) this + t
        else DLL(t, { me }, { it.append(next) })
//        else DLL(t, { me }, { it + (next) })
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