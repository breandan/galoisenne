package ai.hypergraph.kaliningraph.vhdl

import ai.hypergraph.kaliningraph.types.*

fun genArithmeticCircuit(
    circuit: String,
    inputVars: List<String> = circuit.allVars()
) = """
          library ieee;
          use ieee.std_logic_1164.all;
          use ieee.numeric_std.all;

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
            process(${inputVars.joinToString { it + "_in" }})
               variable ${inputVars.joinToString()}: std_logic;
            begin
            """.trimIndent() +
        inputVars.joinToString("\n", "\n", "\n") { "\t$it := ${it}_in;" } +
        circuit.lines().joinToString("") { "\t$it\n" } +
        inputVars.joinToString("\n", "\n", "\n") { "\t${it}_out <= $it;" } +
        """
            end process;
          end rtl;
        """.trimIndent()

fun genTestBench(circuit: String) =
    genTestBench(circuit, circuit.allVars().associateWith { 0 } cc mapOf() )

fun genTestBench(
  circuit: String,
  vararg tests: V2<Map<String, Int>>,
  vars: List<String> = circuit.allVars()
): String = """
-- Testbench for Boolean gate
library ieee;
use ieee.std_logic_1164.all;
use ieee.numeric_std.all;

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

signal ${vars.joinToString { "${it}_in_sig, ${it}_out_sig" }}: std_logic;

begin
-- Connect DUT
DUT: gate port map(
${vars.joinToString("\n") { "\t${it}_in_sig," }}
${vars.joinToString(",\n") { "\t${it}_out_sig" }}
);

process
begin

${
    tests.joinToString("\n\n") { test ->
        test.genPreconditions(vars) + "wait for 20 ns;\n" + test.genPostconditions()
    }
}

assert false report "Test done." severity note;
wait;
end process;
end tb;
""".trimIndent()

// Test case for a boolean circuit
fun V2<Map<String, Int>>.genPreconditions(allVars: List<String>) =
    first.entries.joinToString("\n", "", "") { (k, v) -> "\t${k}_in_sig <= '$v';" } +
        allVars.filter { it !in first }.joinToString("\n", "\n", "\n") { "\t${it}_in_sig <= '0';" }

fun V2<Map<String, Int>>.genPostconditions(indent: String = "     ") =
    second.entries.joinToString("\n$indent") { (k, v) ->
        "\tassert(${k}_out_sig='$v') report \"Failed $k != $v\" severity error;"
    }

fun String.allVars(ops: List<String> = listOf("and", "or")): List<String> =
    split("\\W+".toRegex()).filter { it.all { it.isLetterOrDigit() } && it !in ops && it.isNotEmpty() }.distinct().filter { it.any { it.isLetter() } }
