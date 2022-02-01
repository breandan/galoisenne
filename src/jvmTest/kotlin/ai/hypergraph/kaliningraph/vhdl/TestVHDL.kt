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
./gradlew :cleanJvmTest :jvmTest --tests "ai.hypergraph.kaliningraph.vhdl.TestVHDL.testSignedInteger"
     */

    @Test
    fun testSignedInteger() {
        val adder = File("adder.vhd").apply {
            """
          -- Implements signed addition of two integers
          library IEEE;
          use IEEE.std_logic_1164.all;
          
          entity ripple_carry_adder is
              port (
                  A : in std_logic_vector(31 downto 0);
                  B : in std_logic_vector(31 downto 0);
                  C : out std_logic_vector(31 downto 0)
              );
          end ripple_carry_adder;
          
          architecture rca of ripple_carry_adder is
              signal sum : std_logic_vector(31 downto 0);
              signal carry : std_logic_vector(1 downto 0);
          begin
              process (A, B)
              begin
                  if A(31) = '0' then
                      sum(31) <= A(31);
                  else
                      sum(31) <= '1';
                  end if;
                  carry(0) <= A(31);
                  for i in 31 downto 1 loop
                      if A(i) = '0' and B(i) = '0' then
                          sum(i) <= carry(0);
                          carry(0) <= '0';
                      elsif A(i) = '1' and B(i) = '1' then
                          sum(i) <= carry(0);
                          carry(0) <= '1';
                      elsif A(i) = '1' and B(i) = '0' then
                          sum(i) <= carry(1);
                          carry(0) <= '0';
                      elsif A(i) = '0' and B(i) = '1' then
                          sum(i) <= carry(1);
                          carry(0) <= '0';
                      end if;
                  end loop;
              end process;
              C <= sum;
          end architecture;
          """.trimIndent().also { writeText(it) }
        }

        val testadder = File("testbench.vhd").apply {
          """
            -- Testbench for RCA
            library IEEE;
            use IEEE.std_logic_1164.all;

            entity testbench is
            -- empty
            end testbench;

            architecture tb of testbench is

            -- DUT component
            component ripple_carry_adder
                port (
                    A : in std_logic_vector(31 downto 0);
                    B : in std_logic_vector(31 downto 0);
                    C : out std_logic_vector(31 downto 0)
                );
            end component;

            -- Testbench signals
            signal A, B, C : std_logic_vector(31 downto 0);

            begin
                -- DUT
                dut : ripple_carry_adder
                port map (
                    A => A,
                    B => B,
                    C => C
                );

                -- Test vectors
                process
                begin
                    A <= x"00000000";
                    B <= x"00000000";
                    wait for 10 ns;
                    assert (C = x"00000000")
                        report "A + B = 0"
                        severity note;

                    A <= x"00000001";
                    B <= x"00000001";
                    wait for 10 ns;
                    assert (C = x"00000010")
                        report "A + B = 1"
                        severity note;

                    A <= x"00000001";
                    B <= x"11111111";
                    wait for 10 ns;
                    assert (C = x"00000000")
                        report "A + B = -1"
                        severity note;
                        
                    assert false report "Test done." severity note;
                end process;
              end tb;
            """.trimIndent().also { writeText(it) }
            }

            runCommand("ghdl -a ${adder.absolutePath} ${testadder.absolutePath}")
            runCommand("ghdl -e testbench")
            runCommand("ghdl -r testbench --wave=wave.ghw")
            runCommand("open wave.ghw")
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
    try {
        ProcessBuilder(*command.split(" ").toTypedArray())
            .redirectOutput(ProcessBuilder.Redirect.INHERIT)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()
            .waitFor(60, TimeUnit.MINUTES)
    } catch (e: Exception) {
        false
    }