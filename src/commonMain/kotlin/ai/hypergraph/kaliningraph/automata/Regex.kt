package ai.hypergraph.kaliningraph.automata

import ai.hypergraph.kaliningraph.parsing.Σᐩ
import kotlin.random.Random

// TODO: rewrite in matrix form
class KRegex(val regex: Σᐩ) {
  val postfixRegex = PostFix.toPostfix(regex)
  val symbols = mutableListOf<Char>()
  val transitions = mutableListOf<Transition>()
  val finalStates = mutableListOf<State>()
  val initialStates = mutableListOf<State>()
  val states = mutableListOf<Σᐩ>()

  private var saveFinal: State? = null
  private val stackInitial = Stack<State>()
  private val stackFinal = Stack<State>()

  init {
    updateSymbols()
    compile()
    updateStates()
    updateInitialState()
  }

  fun check(input: Σᐩ): Σᐩ {
    var currentStates = initialStates
    var tmpStateList = mutableListOf<State>()
    for (element in input) {
      if (tmpStateList.size > 0) currentStates = tmpStateList
      for (j in currentStates.indices) {
        val currentChar = element.toString()
        val currentState = currentStates[j]
        var k = 0
        while (k < transitions.size) {
          if (transitions[k].from === currentState &&
            transitions[k].sym == currentChar
          ) {
            val nextState = transitions[k].to
            tmpStateList = eClosure(nextState)
            tmpStateList.add(nextState)
            k = transitions.size
          } else if (transitions[k].from === currentState &&
            transitions[k].sym == "ε"
          ) {
            val nextState = transitions[k].to
            tmpStateList = eClosure(nextState, currentStates)
            tmpStateList.add(nextState)
          }
          k++
        }
      }
    }

    val result = if (finalStates[0] in currentStates) {
      "Σᐩ was accepted (states = ${currentStates.toSet()})"
    } else {
      "Σᐩ was rejected (states = ${currentStates.toSet()})"
    }
    return result
  }

  private fun eClosure(initialState: State, closure: MutableList<State> = mutableListOf()): MutableList<State> {
    for (i in transitions.indices) {
      if (transitions[i].from === initialState) {
        if (transitions[i].sym == "ε") {
          if (transitions[i].to !in closure)
            closure.add(transitions[i].to)
          if (initialState !in closure)
            eClosure(transitions[i].to, closure)
        }
      }
    }
    return closure
  }

  private fun updateSymbols() {
    for (i in postfixRegex.indices) {
      if (postfixRegex[i] !in PostFix.precedenceMap) {
        if (postfixRegex[i] !in symbols) {
          symbols.add(postfixRegex[i])
          symbols.sort()
        }
      }
    }
  }

  fun updateStates() {
    for (i in transitions.indices) {
      if (transitions[i].from.toString() !in states)
        states.add(transitions[i].from.toString())
      if (transitions[i].to.toString() !in states)
        states.add(transitions[i].to.toString())
    }
  }

  fun updateInitialState() {
    val initial = stackInitial.pop()
    initial.initial = true
    stackInitial.push(initial)
    initialStates.add(stackInitial.pop())
  }

  private fun compile() {
    for (i in postfixRegex.indices) {
      when {
        postfixRegex[i] in symbols -> {
          val tr1 = Transition(postfixRegex[i].toString())
          transitions += tr1
          val initialState = tr1.from
          val finalState = tr1.to
          stackInitial.push(initialState)
          stackFinal.push(finalState)
        }
        postfixRegex[i] == '|' -> {
          val lowerInitial = stackInitial.pop()
          val lowerFinal = stackFinal.pop()
          val upperInitial = stackInitial.pop()
          val upperFinal = stackFinal.pop()
          union(upperInitial, upperFinal, lowerInitial, lowerFinal)
        }
        postfixRegex[i] == '*' -> {
          val initialState = stackInitial.pop()
          val finalState = stackFinal.pop()
          kleene(initialState, finalState)
        }
        postfixRegex[i] == '.' -> {
          saveFinal = stackFinal.pop()
          val finalState = stackFinal.pop()
          val initialState = stackInitial.pop()
          concat(finalState, initialState)
        }
      }
      if (i == postfixRegex.length - 1) {
        val finalState = stackFinal.pop()
        finalState.final = true
        stackFinal.push(finalState)
        finalStates += stackFinal.pop()
        if ('ε' in symbols) symbols.remove('ε')
      }
    }
  }

  private fun union(
    upperInitialState: State,
    upperFinalState: State,
    lowerInitialState: State,
    lowerFinalState: State
  ) {
    val inState = State()
    val outState = State()

    val tr1 = Transition("ε", inState, upperInitialState)
    val tr2 = Transition("ε", inState, lowerInitialState)
    val tr3 = Transition("ε", upperFinalState, outState)
    val tr4 = Transition("ε", lowerFinalState, outState)
    transitions += listOf(tr1, tr2, tr3, tr4)
    stackInitial.push(inState)
    stackFinal.push(outState)
  }

  private fun concat(initialState: State, finalState: State) {
    val tr1 = Transition("ε", initialState, finalState)
    transitions += tr1
    stackFinal.push(saveFinal!!)
    saveFinal = null
  }

  private fun kleene(initial: State, final: State) {
    val inState = State()
    val outState = State()
    val tr1 = Transition("ε", final, initial)
    val tr2 = Transition("ε", inState, outState)
    val tr3 = Transition("ε", inState, initial)
    val tr4 = Transition("ε", final, outState)
    transitions += listOf(tr1, tr2, tr3, tr4)
    stackInitial.push(inState)
    stackFinal.push(outState)
  }

  class State(var stateId: Int = Random.Default.nextInt(9999)) {
    var previousStates: MutableList<State> = mutableListOf()
    var nextStates: MutableList<State> = mutableListOf()
    var initial = false
    var final = false

    override fun toString() = stateId.toString()
  }

  class Transition(val sym: Σᐩ, val from: State, val to: State) {
    constructor(transitionSymbol: Σᐩ) : this(transitionSymbol, State(), State())

    init {
      from.nextStates.add(to)
      to.previousStates.add(from)
    }

    override fun toString() = "$from - $sym - $to"
  }

  object PostFix {
    var precedenceMap: Map<Char, Int> =
      mapOf( '(' to 1, '|' to 2, '.' to 3, '*' to 4, '^' to 5)

    private fun getPrecedence(c: Char) = precedenceMap[c] ?: 6

    private fun format(regex: Σᐩ): Σᐩ {
      var res = ""
      val allOperators = listOf('|', '*', '^')
      val binaryOperators = listOf('^', '|')
      for (i in regex.indices) {
        val c1 = regex[i]
        if (i + 1 < regex.length) {
          val c2 = regex[i + 1]
          res += c1
          if (c1 != '(' && c2 != ')' && c2 !in allOperators && c1 !in binaryOperators)
            res += '.'
        }
      }
      res += regex[regex.length - 1]
      return res
    }

    fun toPostfix(infixRegex: Σᐩ): Σᐩ {
      var postfix = ""
      val stack = Stack<Char>()
      val formattedRegEx = format(infixRegex)
      for (c in formattedRegEx.toCharArray()) {
        when (c) {
          '(' -> stack.push(c)
          ')' -> {
            while (stack.peek() != '(') postfix += stack.pop()
            stack.pop()
          }
          else -> {
            while (stack.size > 0) {
              postfix += if (getPrecedence(stack.peek()) >= getPrecedence(c)) {
                stack.pop()
              } else {
                break
              }
            }
            stack.push(c)
          }
        }
      }
      while (stack.size > 0) postfix += stack.pop()
      return postfix
    }
  }
}

typealias Stack<T> = ArrayDeque<T>

inline fun <T> Stack<T>.push(element: T) = addLast(element) // returns Unit

inline fun <T> Stack<T>.pop() = removeLastOrNull()!!          // returns T?

fun <T> Stack<T>.peek(): T = this[lastIndex]!!