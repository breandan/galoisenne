package ai.hypergraph.markovian.concurrency

import java.util.*
import kotlin.reflect.KProperty

//https://stackoverflow.com/a/35757638
class ResettableLazyManager {
  // we synchronize to make sure the timing of a reset() call and new inits do not collide
  val managedDelegates = LinkedList<Resettable>()

  fun register(managed: Resettable) =
    synchronized(managedDelegates) {
      managedDelegates.add(managed)
    }

  fun reset() = synchronized(managedDelegates) {
    managedDelegates.forEach { it.reset() }
    managedDelegates.clear()
  }
}

interface Resettable {
  fun reset()
}

class ResettableLazy<P>(
  val manager: ResettableLazyManager,
  val init: () -> P
): Resettable {
  @Volatile
  var lazyHolder = makeInitBlock()

  operator fun getValue(thisRef: Any?, property: KProperty<*>): P =
    lazyHolder.value

  override fun reset() {
    lazyHolder = makeInitBlock()
  }

  fun makeInitBlock(): Lazy<P> = lazy {
    manager.register(this)
    init()
  }
}

fun <P> resettableLazy(
  manager: ResettableLazyManager,
  init: () -> P
): ResettableLazy<P> = ResettableLazy(manager, init)