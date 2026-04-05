package ai.hypergraph.kaliningraph.rasp

private enum class Mem { IPT, OPT, SCR }
private data class Ref(val mem: Mem, val index: Int)
private enum class BinOp { ADD, MUL }

private sealed interface Expr {
  data class Const(val value: Int) : Expr
  data class Load(val ref: Ref) : Expr
  data class Bin(val op: BinOp, val left: Expr, val right: Expr) : Expr
}

private sealed interface Stmt {
  data class Assign(val target: Ref, val expr: Expr) : Stmt
  data class Ife(val cond: Ref, val thenBlock: List<Stmt>, val elseBlock: List<Stmt>) : Stmt
  data class Whl(val cond: Ref, val body: List<Stmt>) : Stmt
  data object Halt : Stmt
}

private data class Program(val inputArity: Int, val outputArity: Int, val body: List<Stmt>)

private sealed interface AsmItem {
  data class Label(val name: String) : AsmItem
  data class Instr(val opcode: Int, val arg: Arg) : AsmItem
}

private sealed interface Arg {
  data class Imm(val value: Int) : Arg
  data class LabelRef(val name: String) : Arg
  data class DataRef(val ref: Ref) : Arg
  data class TempRef(val slot: Int) : Arg
}

fun String.compileToRASPBytecode(): IntArray =
  Compiler(Parser(tokenize(this)).parseProgram()).compile()

private class Compiler(private val program: Program) {
  private val items = mutableListOf<AsmItem>()
  private var nextLabelId = 0
  private var maxVisibleScrIndex = -1
  private var maxTempSlot = -1

  fun compile(): IntArray {
    emitBlock(program.body)

    val labelToWordPc = mutableMapOf<String, Int>()
    var wordPc = 0
    for (item in items) {
      when (item) {
        is AsmItem.Label -> labelToWordPc[item.name] = wordPc
        is AsmItem.Instr -> wordPc += 2
      }
    }

    val codeWords = wordPc
    val visibleScrSize = maxVisibleScrIndex + 1
    val iptBase = codeWords
    val optBase = iptBase + program.inputArity
    val scrBase = optBase + program.outputArity
    val tempBase = scrBase + visibleScrSize

    val words = ArrayList<Int>(codeWords)
    for (item in items) {
      if (item is AsmItem.Instr) {
        words += item.opcode
        words += resolveArg(
          arg = item.arg,
          labels = labelToWordPc,
          iptBase = iptBase,
          optBase = optBase,
          scrBase = scrBase,
          tempBase = tempBase,
          visibleScrSize = visibleScrSize,
        )
      }
    }

    return words.toIntArray()
  }

  private fun emitBlock(block: List<Stmt>) { for (stmt in block) emitStmt(stmt) }

  private fun emitStmt(stmt: Stmt) {
    when (stmt) {
      is Stmt.Assign -> {
        emitExpr(stmt.expr, depth = 0)
        emit(4, Arg.DataRef(stmt.target)) // STO
      }

      is Stmt.Ife -> {
        val thenLabel = freshLabel()
        val endLabel = freshLabel()

        emitGuard(stmt.cond)
        emit(5, Arg.LabelRef(thenLabel)) // BNZ then
        emitBlock(stmt.elseBlock)
        emitJump(endLabel)
        placeLabel(thenLabel)
        emitBlock(stmt.thenBlock)
        placeLabel(endLabel)
      }

      is Stmt.Whl -> {
        val headLabel = freshLabel()
        val bodyLabel = freshLabel()
        val endLabel = freshLabel()

        placeLabel(headLabel)
        emitGuard(stmt.cond)
        emit(5, Arg.LabelRef(bodyLabel)) // BNZ body
        emitJump(endLabel)
        placeLabel(bodyLabel)
        emitBlock(stmt.body)
        emitJump(headLabel)
        placeLabel(endLabel)
      }

      Stmt.Halt -> {
        for (i in 0 until program.outputArity) {
          emit(7, Arg.DataRef(Ref(Mem.OPT, i))) // PRI opt[i]
        }
        emit(0, Arg.Imm(0)) // reserved invalid pair = HLT
      }
    }
  }

  private fun emitGuard(cond: Ref) = emitLoad(cond)

  private fun emitExpr(expr: Expr, depth: Int) {
    when (expr) {
      is Expr.Const -> emit(1, Arg.Imm(expr.value)) // LOD immediate
      is Expr.Load -> emitLoad(expr.ref)

      is Expr.Bin -> {
        emitExpr(expr.left, depth)
        emit(4, temp(depth)) // STO temp[depth]
        emitExpr(expr.right, depth + 1)
        emit(if (expr.op == BinOp.ADD) 2 else 3, temp(depth)) // ADD/MUL temp[depth]
      }
    }
  }

  private fun emitLoad(ref: Ref) {
    emit(1, Arg.Imm(0))      // LOD 0
    emit(2, Arg.DataRef(ref)) // ADD addr
  }

  private fun emitJump(label: String) {
    emit(1, Arg.Imm(1))        // LOD 1
    emit(5, Arg.LabelRef(label)) // BNZ label
  }

  private fun emit(opcode: Int, arg: Arg) {
    when (arg) {
      is Arg.DataRef -> {
        if (arg.ref.mem == Mem.SCR) {
          maxVisibleScrIndex = maxOf(maxVisibleScrIndex, arg.ref.index)
        }
      }
      is Arg.TempRef -> { maxTempSlot = maxOf(maxTempSlot, arg.slot) }
      else -> Unit
    }
    items += AsmItem.Instr(opcode, arg)
  }

  private fun placeLabel(name: String) { items += AsmItem.Label(name) }

  private fun freshLabel(): String = "L${nextLabelId++}"

  private fun temp(slot: Int): Arg.TempRef = Arg.TempRef(slot)

  private fun resolveArg(
    arg: Arg,
    labels: Map<String, Int>,
    iptBase: Int,
    optBase: Int,
    scrBase: Int,
    tempBase: Int,
    visibleScrSize: Int,
  ): Int = when (arg) {
    is Arg.Imm -> arg.value
    is Arg.LabelRef -> labels[arg.name] ?: error("Undefined label ${arg.name}")
    is Arg.TempRef -> tempBase + arg.slot
    is Arg.DataRef -> when (arg.ref.mem) {
      Mem.IPT -> iptBase + floorMod(arg.ref.index, program.inputArity)
      Mem.OPT -> optBase + floorMod(arg.ref.index, program.outputArity)
      Mem.SCR -> {
        require(visibleScrSize > 0) { "scr[] used but no scratch cells were inferred" }
        scrBase + floorMod(arg.ref.index, visibleScrSize)
      }
    }
  }

  private fun floorMod(x: Int, m: Int): Int {
    require(m > 0) { "Modulo base must be positive" }
    val r = x % m
    return if (r >= 0) r else r + m
  }
}

private class Parser(private val tokens: List<String>) {
  private var pos = 0

  fun parseProgram(): Program {
    expect("fun")
    expect("f0")
    expect("(")
    expect("ipt")
    expect(":")
    expect("W")
    expect("^")
    val inputArity = expectInt()
    expect(")")
    expect("->")
    expect("W")
    expect("^")
    val outputArity = expectInt()
    expect("{")
    val body = parseBlockUntil("}")
    expect("}")
    check(atEnd()) { "Unexpected trailing tokens: ${tokens.drop(pos)}" }
    return Program(inputArity, outputArity, body)
  }

  private fun parseBlockUntil(endToken: String): List<Stmt> {
    val out = mutableListOf<Stmt>()
    while (peek() != endToken) {
      out += parseStmt()
      if (peek() == ";") take() else break
    }
    return out
  }

  private fun parseStmt(): Stmt = when (peek()) {
    "ife" -> parseIfe()
    "whl" -> parseWhl()
    "hlt" -> {
      expect("hlt")
      Stmt.Halt
    }
    else -> parseAssign()
  }

  private fun parseIfe(): Stmt.Ife {
    expect("ife")
    val cond = parseRef()
    expect("{")
    val thenBlock = parseBlockUntil("}")
    expect("}")
    expect("{")
    val elseBlock = parseBlockUntil("}")
    expect("}")
    return Stmt.Ife(cond, thenBlock, elseBlock)
  }

  private fun parseWhl(): Stmt.Whl {
    expect("whl")
    val cond = parseRef()
    expect("{")
    val body = parseBlockUntil("}")
    expect("}")
    return Stmt.Whl(cond, body)
  }

  private fun parseAssign(): Stmt.Assign {
    val target = parseRef().also {
      require(it.mem != Mem.IPT) { "Cannot assign to ipt[]" }
    }
    expect("=")
    val expr = parseExpr()
    return Stmt.Assign(target, expr)
  }

  private fun parseExpr(): Expr = parseAdditive()

  private fun parseAdditive(): Expr {
    var expr = parseMultiplicative()
    while (peek() == "+") {
      expect("+")
      expr = Expr.Bin(BinOp.ADD, expr, parseMultiplicative())
    }
    return expr
  }

  private fun parseMultiplicative(): Expr {
    var expr = parsePrimary()
    while (peek() == "*") {
      expect("*")
      expr = Expr.Bin(BinOp.MUL, expr, parsePrimary())
    }
    return expr
  }

  private fun parsePrimary(): Expr {
    val t = peek()
    return if (t.firstOrNull()?.isDigit() == true) {
      Expr.Const(expectInt())
    } else {
      Expr.Load(parseRef())
    }
  }

  private fun parseRef(): Ref {
    val mem = when (val t = take()) {
      "ipt" -> Mem.IPT
      "opt" -> Mem.OPT
      "scr" -> Mem.SCR
      else -> error("Expected ipt/opt/scr, got $t at token index ${pos - 1}")
    }
    expect("[")
    val index = expectInt()
    expect("]")
    return Ref(mem, index)
  }

  private fun expectInt(): Int =
    take().toIntOrNull() ?: error("Expected int at token index ${pos - 1}, got '${tokens.getOrNull(pos - 1)}'")

  private fun expect(expected: String) {
    val actual = take()
    check(actual == expected) { "Expected '$expected', got '$actual' at token index ${pos - 1}" }
  }

  private fun peek(): String = tokens.getOrNull(pos) ?: error("Unexpected end of input")
  private fun take(): String = tokens.getOrNull(pos++) ?: error("Unexpected end of input")
  private fun atEnd(): Boolean = pos == tokens.size
}

private fun tokenize(src: String): List<String> {
  val tokens = mutableListOf<String>()
  var i = 0

  while (i < src.length) {
    when (val c = src[i]) {
      ' ', '\t', '\n', '\r' -> i++

      '-' -> {
        if (i + 1 < src.length && src[i + 1] == '>') {
          tokens += "->"
          i += 2
        } else {
          error("Unexpected '-' at character $i")
        }
      }

      '(', ')', '{', '}', '[', ']', ':', '^', ';', '=', '+', '*' -> {
        tokens += c.toString()
        i++
      }

      else -> when {
        c.isDigit() -> {
          val start = i
          while (i < src.length && src[i].isDigit()) i++
          tokens += src.substring(start, i)
        }

        c.isLetter() || c == '_' -> {
          val start = i
          while (i < src.length && (src[i].isLetterOrDigit() || src[i] == '_')) i++
          tokens += src.substring(start, i)
        }

        else -> error("Unexpected character '$c' at position $i")
      }
    }
  }

  return tokens
}