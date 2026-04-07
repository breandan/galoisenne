package ai.hypergraph.kaliningraph.rasp

private enum class Mem { IPT, OPT, SCR }
private data class Ref(val mem: Mem, val index: Int)
private enum class Op { ADD, MUL }

private sealed interface Atom {
  data class Z(val value: Int) : Atom
  data class G(val ref: Ref) : Atom
}

private sealed interface Expr {
  data class A(val atom: Atom) : Expr
  data class Bin(val op: Op, val left: Ref, val right: Atom) : Expr
}

private sealed interface Stmt {
  data class Assign(val dst: Ref, val expr: Expr) : Stmt
  data class Ife(val cond: Ref, val thenBlk: List<Stmt>, val elseBlk: List<Stmt>) : Stmt
  data class Whl(val cond: Ref, val body: List<Stmt>) : Stmt
  data object Halt : Stmt
}

private data class Program(val inArity: Int, val outArity: Int, val body: List<Stmt>)

private sealed interface Item {
  data class Label(val name: String) : Item
  data class Ins(val op: Int, val arg: Arg) : Item
}

private sealed interface Arg {
  data class Imm(val value: Int) : Arg
  data class LabelRef(val name: String) : Arg
  data class Data(val ref: Ref) : Arg
  data object Tmp : Arg
}

private data class Layout(
  val codeWords: Int,
  val iptBase: Int,
  val optBase: Int,
  val scrBase: Int,
  val tmpAddr: Int,
  val totalWords: Int,
)

fun String.compileToRASPBytecode(memWords: Int = 255): IntArray =
  Compiler(Parser(tokenize(this)).parseProgram(), memWords).compile()

private class Compiler(
  private val p: Program,
  private val memWords: Int,
) {
  companion object {
    private const val SCR_WORDS = 9

    private const val OPCODE_BITS = 3
    private const val OPCODE_MASK = (1 shl OPCODE_BITS) - 1
  }

  private val items = mutableListOf<Item>()
  private var nextLabel = 0

  fun compile(): IntArray {
    emitBlock(p.body)
    emitReturn()

    val labels = mutableMapOf<String, Int>()
    var pc = 0
    for (it in items) when (it) {
      is Item.Label -> labels[it.name] = pc
      is Item.Ins -> pc += 1
    }

    val layout = computeLayout(codeWords = pc)

    val out = IntArray(layout.codeWords)
    var k = 0
    for (it in items) {
      if (it is Item.Ins) out[k++] = pack(it.op, resolve(it.arg, labels, layout))
    }
    return out
  }

  private fun computeLayout(codeWords: Int): Layout {
    require(p.inArity in 1..9) { "Input arity must be in 1..9, got ${p.inArity}" }
    require(p.outArity in 1..9) { "Output arity must be in 1..9, got ${p.outArity}" }

    val iptBase = codeWords
    val optBase = iptBase + p.inArity
    val scrBase = optBase + p.outArity
    val tmpAddr = scrBase + SCR_WORDS
    val totalWords = tmpAddr + 1

    require(totalWords <= memWords) {
      buildString {
        append("Program/data layout does not fit in VM memory. ")
        append("codeWords=$codeWords, inArity=${p.inArity}, outArity=${p.outArity}, ")
        append("scrWords=$SCR_WORDS, tmpWords=1, required=$totalWords, memWords=$memWords. ")
        append("Max code words for this signature is ")
        append(memWords - (p.inArity + p.outArity + SCR_WORDS + 1))
        append(".")
      }
    }

    return Layout(
      codeWords = codeWords,
      iptBase = iptBase,
      optBase = optBase,
      scrBase = scrBase,
      tmpAddr = tmpAddr,
      totalWords = totalWords,
    )
  }

  private fun pack(op: Int, arg: Int): Int {
    require(op in 0..OPCODE_MASK) { "Opcode out of range: $op" }
    require(arg >= 0) { "Negative operand: $arg" }
    return (arg shl OPCODE_BITS) or (op and OPCODE_MASK)
  }

  private fun emitBlock(xs: List<Stmt>) = xs.forEach(::emitStmt)

  private fun emitStmt(s: Stmt) {
    when (s) {
      is Stmt.Assign -> {
        emitExpr(s.expr)
        emit(4, Arg.Data(s.dst)) // STO
      }

      is Stmt.Ife -> {
        val lt = fresh()
        val le = fresh()
        emitLoad(s.cond)
        emit(5, Arg.LabelRef(lt)) // BNZ then
        emitBlock(s.elseBlk)
        emitJump(le)
        mark(lt)
        emitBlock(s.thenBlk)
        mark(le)
      }

      is Stmt.Whl -> {
        val lh = fresh()
        val lb = fresh()
        val le = fresh()
        mark(lh)
        emitLoad(s.cond)
        emit(5, Arg.LabelRef(lb)) // BNZ body
        emitJump(le)
        mark(lb)
        emitBlock(s.body)
        emitJump(lh)
        mark(le)
      }

      Stmt.Halt -> emitReturn()
    }
  }

  private fun emitExpr(e: Expr) {
    when (e) {
      is Expr.A -> emitAtom(e.atom)
      is Expr.Bin -> {
        emitLoad(e.left)
        emit(4, Arg.Tmp) // STO ν
        emitAtom(e.right)
        emit(if (e.op == Op.ADD) 2 else 3, Arg.Tmp) // ADD/MUL ν
      }
    }
  }

  private fun emitAtom(a: Atom) {
    when (a) {
      is Atom.Z -> emit(1, Arg.Imm(a.value)) // LOD z
      is Atom.G -> emitLoad(a.ref)           // LOD 0 ; ADD addr
    }
  }

  private fun emitLoad(r: Ref) {
    emit(1, Arg.Imm(0))
    emit(2, Arg.Data(r))
  }

  private fun emitJump(label: String) {
    emit(1, Arg.Imm(1))
    emit(5, Arg.LabelRef(label))
  }

  private fun emitReturn() {
    for (i in 0 until p.outArity) emit(7, Arg.Data(Ref(Mem.OPT, i))) // PRI opt[i]
    emit(0, Arg.Imm(0)) // HLT = invalid instruction
  }

  private fun emit(op: Int, arg: Arg) { items += Item.Ins(op, arg) }
  private fun mark(name: String) { items += Item.Label(name) }
  private fun fresh() = "L${nextLabel++}"

  private fun resolve(arg: Arg, labels: Map<String, Int>, layout: Layout): Int = when (arg) {
    is Arg.Imm -> arg.value
    is Arg.LabelRef -> labels[arg.name]!!
    is Arg.Tmp -> layout.tmpAddr

    is Arg.Data -> when (arg.ref.mem) {
      Mem.IPT -> layout.iptBase + mod(arg.ref.index, p.inArity)
      Mem.OPT -> layout.optBase + mod(arg.ref.index, p.outArity)
      Mem.SCR -> layout.scrBase + mod(arg.ref.index, SCR_WORDS)
    }
  }

  private fun mod(x: Int, m: Int): Int = ((x % m) + m) % m
}

private class Parser(private val t: List<String>) {
  private var i = 0

  fun parseProgram(): Program {
    take("fun"); take("f0"); take("("); take("ipt"); take(":"); take("W"); take("^")
    val inArity = int()
    take(")"); take("->"); take("W"); take("^")
    val outArity = int()
    take("{")
    val body = block("}")
    take("}")
    return Program(inArity, outArity, body)
  }

  private fun block(end: String): List<Stmt> {
    val out = mutableListOf<Stmt>()
    while (peek() != end) {
      out += stmt()
      if (peek() == ";") i++
    }
    return out
  }

  private fun stmt(): Stmt = when (peek()) {
    "ife" -> {
      take("ife")
      val c = ref()
      take("{"); val th = block("}"); take("}")
      take("{"); val el = block("}"); take("}")
      Stmt.Ife(c, th, el)
    }

    "whl" -> {
      take("whl")
      val c = ref()
      take("{"); val b = block("}"); take("}")
      Stmt.Whl(c, b)
    }

    "hlt" -> {
      take("hlt")
      Stmt.Halt
    }

    else -> {
      val dst = ref()
      take("=")
      Stmt.Assign(dst, expr())
    }
  }

  private fun expr(): Expr {
    val a = atom()
    val op = peek()
    if (op != "+" && op != "*") return Expr.A(a)
    i++
    val rhs = atom()
    val lhs = (a as Atom.G).ref
    return Expr.Bin(if (op == "+") Op.ADD else Op.MUL, lhs, rhs)
  }

  private fun atom(): Atom =
    if (peek()[0].isDigit()) Atom.Z(int())
    else Atom.G(ref())

  private fun ref(): Ref {
    val mem = when (next()) {
      "ipt" -> Mem.IPT
      "opt" -> Mem.OPT
      "scr" -> Mem.SCR
      else -> error("Expected ipt/opt/scr at token ${i - 1}")
    }
    take("[")
    val idx = int()
    take("]")
    return Ref(mem, idx)
  }

  private fun int() = next().toInt()
  private fun peek() = t[i]
  private fun next() = t[i++]

  private fun take(s: String) {
    val got = next()
    require(got == s) { "Expected '$s', got '$got' at token ${i - 1}" }
  }
}

private fun tokenize(src: String): List<String> =
  src.replace("->", " -> ")
    .replace("(", " ( ")
    .replace(")", " ) ")
    .replace("{", " { ")
    .replace("}", " } ")
    .replace("[", " [ ")
    .replace("]", " ] ")
    .replace(":", " : ")
    .replace("^", " ^ ")
    .replace(";", " ; ")
    .replace("=", " = ")
    .replace("+", " + ")
    .replace("*", " * ")
    .split(Regex("\\s+"))
    .filter { it.isNotEmpty() }