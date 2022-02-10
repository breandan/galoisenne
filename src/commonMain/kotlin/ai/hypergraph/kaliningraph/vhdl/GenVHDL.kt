package ai.hypergraph.kaliningraph.vhdl

fun genArithmeticCircuit(
    circuit: String,
    inputVars: List<String> = circuit.allVars()
) = """
          library IEEE;
          use IEEE.std_logic_1164.all;

          entity gate is
          port(
          """.trimIndent() +
        inputVars.joinToString("\n", "\n", "\n") { "    ${it}_in: in std_logic;" } +
        inputVars.joinToString(";\n", "\n", "\n") { "    ${it}_out: out std_logic" } +
        """
          );
          end gate;

          architecture rtl of gate is
          begin
            process(${inputVars.joinToString(",") { it + "_in" }})
               variable ${inputVars.joinToString(", ")}: std_logic;
            begin
            """.trimIndent() +
        inputVars.joinToString("\n", "\n", "\n") { "    $it := ${it}_in;" } +
        circuit +
        inputVars.joinToString("\n", "\n", "\n") { "    ${it}_out <= $it;" } +
        """
            end process;
          end rtl;
        """.trimIndent()

fun genTestBench(circuit: String, vararg tests: Pair<Map<String, Int>, Map<String, Int>>): String {
    val vars: List<String> = circuit.allVars()
    return """
          -- Testbench for Boolean gate
          library IEEE;
          use IEEE.std_logic_1164.all;

          entity testbench is
          -- empty
          end testbench;

          architecture tb of testbench is

          -- DUT component
          component gate is
          port(
          """.trimIndent() +
            vars.joinToString("\n", "\n", "\n") { "    ${it}_in: in std_logic;" } +
            vars.joinToString(";\n", "\n", "\n") { "    ${it}_out: out std_logic" } +
            """
          );
          end component;

          signal ${vars.joinToString(", ") { "${it}_in_sig, ${it}_out_sig" }}: std_logic;

          begin
            -- Connect DUT
            DUT: gate port map(${vars.joinToString(", ") { "${it}_in_sig" }}, ${vars.joinToString(", ") { "${it}_out_sig" }});

            process
            begin
            
            ${
                tests.joinToString("\n\n") { test ->
                """
                  ${test.genPreconditions(vars)}
                  wait for 10 ns;
                  ${test.genPostconditions()}
                """
                }
            }
            
              assert false report "Test done." severity note;
              wait;
            end process;
          end tb;
        """.trimIndent()
}

// Test case for a boolean circuit
fun Pair<Map<String, Int>, Map<String, Int>>.genPreconditions(allVars: List<String>) =
    first.entries.joinToString("; ", "", ";") { (k, v) -> "${k}_in_sig <= '$v'" } +
            allVars.filter { it !in first }.joinToString("; ", "", ";") { "${it}_in_sig <= '0'" }


fun Pair<Map<String, Int>, Map<String, Int>>.genPostconditions() =
    second.entries.joinToString("\n") { (k, v) -> """assert(${k}_out_sig='$v') report "Failed $k != $v" severity error;""" }

fun String.allVars(ops: List<String> = listOf("and", "or")) =
    split("\\W+".toRegex()).filter { it.all { it.isLetterOrDigit() } && it !in ops && it.isNotEmpty() }.distinct()
