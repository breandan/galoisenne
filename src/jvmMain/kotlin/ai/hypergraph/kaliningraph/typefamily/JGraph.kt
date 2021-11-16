package ai.hypergraph.kaliningraph.typefamily

import ai.hypergraph.kaliningraph.*
import ai.hypergraph.kaliningraph.theory.wl
import ai.hypergraph.kaliningraph.types.*
import com.github.benmanes.caffeine.cache.*
import java.lang.reflect.*
import kotlin.reflect.KClass

interface JGF<G, E, V>: IGF<G, E, V>
  where G : IGraph<G, E, V>, E : IEdge<G, E, V>, V : IVertex<G, E, V> {
  override val G: (vertices: Set<V>) -> G get() = { vertices ->
    if (vertices isA g.declaringClass) vertices as G // G(graph) -> graph
    else g.newInstance(vertices)
  }

  override val V: (old: V, edgeMap: (V) -> Set<E>) -> V get() = { old, edgeMap ->
    try {
      v.newInstance(old, edgeMap) /** e.g. [LGVertex] */
    } catch (e: Exception) {
      TODO("JGF subtypes must provide a copy constructor or override this method.")
    }
  }

  override val E: (s: V, t: V) -> E get() = { s, t -> e.newInstance(s, t) }

  override fun G(list: List<Any>): G = when {
    list.isEmpty() -> setOf()
    list allAre G() -> list.fold(G()) { it, acc -> it + acc as G }
    list allAre gev[2] -> list.map { it as V }.toSet()
    else -> throw Exception("Unsupported constructor: G(${list.joinToString(",") { it.javaClass.simpleName }})")
  }.let { G(it) }

  // Gafter's gadget! http://gafter.blogspot.com/2006/12/super-type-tokens.html
  val gev: Array<Class<*>> get() = memoize {
    (generateSequence(javaClass) { it.superclass as Class<JGF<G, E, V>> }
      .first { it.genericSuperclass is ParameterizedType }
      .genericSuperclass as ParameterizedType).actualTypeArguments
      .map { (if (it is ParameterizedType) it.rawType else it) as Class<*> }
      .toTypedArray()
  }

  private val g: Constructor<G> get() = gev[0].getConstructor(Set::class.java) as Constructor<G>
  private val e: Constructor<E> get() = gev.let { it[1].getConstructor(it[2], it[2]) } as Constructor<E>
  /** TODO: Generify first argument to support [TypedVertex] */
  private val v: Constructor<V> get() = gev[2].let { it.getConstructor(it, Function1::class.java) as Constructor<V> }

//  override fun <T> memoize(classRef: Any, methodRef: Int, args: Array<*>?, computation: () -> T): T =
//    computation()
//    memo.get(Triple(classRef, methodRef, args)) { computation() } as T
//    computation().also {
//      GlobalScope.async {
//        memo.get(Triple(classRef, methodRef, args)) { it as Any } as T
//      }
//    }

//  companion object {
//    val memo = Cache.Builder().build<Triple<*, *, *>, Any>()
//    /** TODO: lift into [IGF] once we find a reliable KMP cache like [Caffeine] */
//    // https://github.com/ReactiveCircus/cache4k
//    // https://github.com/ben-manes/caffeine/issues/160#issuecomment-305681211
//    val memo = Caffeine.newBuilder().buildAsync<Triple<*, *, *>, Any>().synchronous()
//  }
}

abstract class Graph<G, E, V>(override val vertices: Set<V> = setOf()) :
  IGraph<G, E, V>, Set<V> by vertices, (V) -> Set<V> by { it: V -> it.neighbors }, JGF<G, E, V>
    where G : Graph<G, E, V>, E : Edge<G, E, V>, V : Vertex<G, E, V> {
  override fun equals(other: Any?) =
    super.equals(other) || (other as? G)?.isomorphicTo(this as G) ?: false
  override fun encode() =
    if (isEmpty()) DoubleArray(10) {0.0} else wl().values.sorted().map { it.toDouble() }.toDoubleArray()
  // https://web.engr.oregonstate.edu/~erwig/papers/InductiveGraphs_JFP01.pdf#page=6
  override fun toString() = asString()
}

abstract class Edge<G, E, V>(override val source: V, override val target: V) : IEdge<G, E, V>, JGF<G, E, V>
    where G : Graph<G, E, V>, E : Edge<G, E, V>, V : Vertex<G, E, V> {
  override fun equals(other: Any?) = (other as? E)?.let { hashCode() == other.hashCode() } ?: false
  override fun hashCode(): Int = source.hashCode() + target.hashCode()
  override fun toString() = "$source→$target"
}

abstract class Vertex<G, E, V>(override val id: String) : IVertex<G, E, V>, JGF<G, E, V>
    where G : Graph<G, E, V>, E : Edge<G, E, V>, V : Vertex<G, E, V> {
  override fun equals(other: Any?) = (other as? Vertex<*, *, *>)?.let { id == it.id } ?: false
  override fun encode() = id.vectorize()
  override fun hashCode() = id.hashCode()
  override fun toString() = id
}

// Maybe we can hack reification using super type tokens?
infix fun Any.isA(that: Any) = when (that) {
  is KClass<out Any> -> that.java
  is Class<out Any> -> that
  else -> that::class.java
}.let { this::class.java.isAssignableFrom(it) }

infix fun Collection<Any>.allAre(that: Any) = all { it isA that }
infix fun Collection<Any>.anyAre(that: Any) = any { it isA that }