package ai.hypergraph.kaliningraph.vhdl

import org.junit.jupiter.api.*
import java.io.File
import java.util.concurrent.TimeUnit

/*
./gradlew jvmTest --tests "ai.hypergraph.kaliningraph.vhdl.TestVHDL"
*/
class TestVHDL {
  /*
./gradlew :cleanJvmTest :jvmTest --tests "ai.hypergraph.kaliningraph.vhdl.TestVHDL.testOutput"
   */
  @Test
  fun testOutput() {
    """
     --  Hello world program.
     use std.textio.all; --  Imports the standard textio package.
     
     --  Defines a design entity, without any ports.
     entity hello_world is
     end hello_world;
     
     architecture behaviour of hello_world is
     begin
        process
           variable l : line;
        begin
           write (l, String'("Hello world!"));
           writeline (output, l);
           wait;
        end process;
     end behaviour;
    """.trimIndent().runVHDL()
  }

  /*
./gradlew :cleanJvmTest :jvmTest --tests "ai.hypergraph.kaliningraph.vhdl.TestVHDL.testVHDLCodeGen"
 */
  @Test
  fun testVHDLCodeGen() {
    val designFile = File("design.vhd").apply {
      writeText(
        """
        -- Simple OR gate design
        library IEEE;
        use IEEE.std_logic_1164.all;

        entity or_gate is
        port(
          a: in std_logic;
          b: in std_logic;
          q: out std_logic);
        end or_gate;

        architecture rtl of or_gate is
        begin
          process(a, b) is
          begin
            q <= a or b;
          end process;
        end rtl;
      """.trimIndent()
      )
    }
    val testbench = File("testbench.vhd").apply {
      writeText(
        """
      -- Testbench for OR gate
      library IEEE;
      use IEEE.std_logic_1164.all;

      entity testbench is
      -- empty
      end testbench;

      architecture tb of testbench is

      -- DUT component
      component or_gate is
      port(
        a: in std_logic;
        b: in std_logic;
        q: out std_logic);
      end component;

      signal a_in, b_in, q_out: std_logic;

      begin

        -- Connect DUT
        DUT: or_gate port map(a_in, b_in, q_out);

        process
        begin
          a_in <= '0';
          b_in <= '0';
          wait for 1 ns;
          assert(q_out='0') report "Fail 0/0" severity error;

          a_in <= '0';
          b_in <= '0';
          wait for 1 ns;
          assert(q_out='0') report "Fail 0/1" severity error;

          a_in <= '1';
          b_in <= 'X';
          wait for 1 ns;
          assert(q_out='1') report "Fail 1/X" severity error;

          a_in <= '1';
          b_in <= '1';
          wait for 1 ns;
          assert(q_out='1') report "Fail 1/1" severity error;

          -- Clear inputs
          a_in <= '0';
          b_in <= '0';

          assert false report "Test done." severity note;
          wait;
        end process;
      end tb;
        """.trimIndent()
      )
    }

    runCommand("ghdl -a ${testbench.absolutePath} ${designFile.absolutePath}")
    runCommand("ghdl -e testbench")
    runCommand("ghdl -r testbench --wave=wave.ghw")
    runCommand("open wave.ghw")
  }
}

private fun String.runVHDL() {
  File("hello.vhd").apply { writeText(this@runVHDL) }
  runCommand("ghdl -a hello.vhd")
  runCommand("ghdl -e hello_world")
  runCommand("ghdl -r hello_world")
}

fun runCommand(command: String): Boolean =
  ProcessBuilder(*command.split(" ").toTypedArray())
    .redirectOutput(ProcessBuilder.Redirect.INHERIT)
    .redirectError(ProcessBuilder.Redirect.INHERIT)
    .start()
    .waitFor(60, TimeUnit.MINUTES)