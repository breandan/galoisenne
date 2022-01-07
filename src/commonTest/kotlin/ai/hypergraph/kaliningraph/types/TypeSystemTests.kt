package ai.hypergraph.kaliningraph.types

import kotlin.test.Test
import kotlin.time.*

class TypeSystemTests {
  val max = 8

  @Test
  @ExperimentalTime
  fun benchmark() =
    listOf(
      BaseType(
        max = max,
        one = 1,
        nil = 0,
        plus = { a, b -> a + b },
        minus = { a, b -> a - b },
        times = { a, b -> a * b },
        div = { a, b -> a / b },
      ),
      BaseType(
        max = max.toDouble(),
        one = 1.0,
        nil = 0.0,
        plus = { a, b -> a + b },
        minus = { a, b -> a - b },
        times = { a, b -> a * b },
        div = { a, b -> a / b },
      ),
      BaseType(
        max = max.toLong(), one = 1L, nil = 0L,
        plus = { a, b -> a + b }, minus = { a, b -> a - b },
        times = { a, b -> a * b }, div = { a, b -> a / b },
      ),
      BaseType(
        max = max.toFloat(), one = 1f, nil = 0f,
        plus = { a, b -> a + b }, minus = { a, b -> a - b },
        times = { a, b -> a * b }, div = { a, b -> a / b },
      ),
      BaseType(
        max = Rational(max.toInt()),
        nil = Rational.ZERO, one = Rational.ONE,
        plus = { a, b -> a + b }, times = { a, b -> a * b },
        div = { a, b -> a / b }, minus = { a, b -> a - b }
      )
    ) // Benchmark all (types x algebras)
      .forEach { baseType ->
        baseType.algebras().forEach {
          it.benchmark(baseType.max)
        }
      }

  @Test
  fun vectorFieldTest() =
    VectorField.of(f = Field.of(
      nil = 0L,
      one = 1L,
      plus = { a, b -> a + b },
      times = { a, b -> a * b },
      div = { a, b -> a / b },
      minus = { a, b -> a - b }
    )).run {
      println(1L dot Vector.of(0L, 1L))
      println(Vector.of(0L, 1L) + Vector.of(1L, 1L))
    }

  fun <T> BaseType<T>.algebras(): List<Nat<T>> = listOf(
    Nat.of(
      nil = nil,
      vnext = { this + one }
    ),
    Group.of(
      nil = nil, one = one,
      plus = { a, b -> a + b }
    ),
    Ring.of(
      nil = nil, one = one,
      plus = { a, b -> a + b },
      times = { a, b -> a * b }
    ),
    Field.of(
      nil = nil, one = one,
      plus = { a, b -> a + b },
      times = { a, b -> a * b },
      div = { a, b -> a / b },
      minus = { a, b -> a - b }
    )
  )

  data class BaseType<T>(
    val max: T, val one: T, val nil: T,
    val plus: (T, T) -> T,
    val times: (T, T) -> T,
    val minus: (T, T) -> T,
    val div: (T, T) -> T,
  ) {
    operator fun T.plus(that: T) = plus(this, that)
    operator fun T.times(that: T) = times(this, that)
    operator fun T.minus(that: T) = minus(this, that)
    operator fun T.div(that: T) = div(this, that)
  }

  @ExperimentalTime
  @Suppress("UNCHECKED_CAST")
  fun <T> Nat<T>.benchmark(max: Any) =
    measureTimedValue {
      println(
        this::class.simpleName + "<${nil!!::class.simpleName}>" + " results\n" +
          "\tFibonacci: " + fibonacci(max as T) + "\n" +
          "\tPrimes:    " + primes(max as T) + "\n" +
          "\tPower:     " + (one + one).pow(max as T) + "\n" +
          "\tFactorial: " + factorial(max as T)
      )
    }.also { ms -> println("Total: ${ms}ms\n") }
}