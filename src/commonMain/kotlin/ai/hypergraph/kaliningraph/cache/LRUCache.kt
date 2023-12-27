package ai.hypergraph.kaliningraph.cache

// TODO: LFU Cache
class LRUCache<K, V>(
  private val maxSize: Int = 10_000,
  private val sizeOf: (key: K, value: V) -> Int = { _, _ -> 1 }
) {
  val map: LinkedHashMap<K, V> = LinkedHashMap(0, .75f)
  private var size: Int = 0

  fun getOrPut(key: K, value: () -> V): V =
    map[key] ?: value().also { put(key, it) }

  operator fun get(key: K) = map[key]

  fun put(key: K, value: V): V? {
    size += sizeOf(key, value)
    val prev: V? = map.put(key, value)
    prev?.let { size -= sizeOf(key, it) }

    trimToSize()
    return prev
  }

  fun remove(key: K): V? {
    val prev = map.remove(key)
    prev?.let { size -= sizeOf(key, it) }
    return prev
  }

  private fun trimToSize() {
    if (size <= maxSize || map.isEmpty()) return
    try {
      val toEvict = map.entries.iterator().next()
      val key = toEvict.key
      val value = toEvict.value
      map.remove(key)
      size -= sizeOf(key, value)
      trimToSize()
    } catch (_: Exception) {}
  }

  override fun toString() = "$size/$maxSize cached=$map"
  operator fun contains(key: K) = key in map
}