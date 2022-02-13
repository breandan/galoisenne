package ai.hypergraph.kaliningraph.vhdl

import org.junit.jupiter.api.Test
import java.io.File
import java.lang.ProcessBuilder.Redirect.INHERIT
import java.util.concurrent.TimeUnit.MINUTES

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
./gradlew :cleanJvmTest :jvmTest --tests "ai.hypergraph.kaliningraph.vhdl.TestVHDL.testAddition"
     */

    @Test
    fun testAddition() {
        val adder = """
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
        """.trimIndent().let { File("adder.vhd").apply { writeText(it) } }

        val testadder = """
            
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
                        severity error;

                    A <= x"10000001";
                    B <= x"10000001";
                    wait for 10 ns;
                    assert (C = x"00000010")
                        report "A + B = 1"
                        severity error;

                    A <= x"00000001";
                    B <= x"11111111";
                    wait for 10 ns;
                    assert (C = x"00000000")
                        report "A + B = -1"
                        severity error;
                        
                    assert false report "Test done." severity note;
                    wait;
                end process;
            end tb;
        """.trimIndent().let { File("testbench.vhd").apply { writeText(it) } }

        runCommand("ghdl -a ${adder.absolutePath} ${testadder.absolutePath}")
        runCommand("ghdl -e testbench")
        runCommand("ghdl -r testbench --wave=wave.ghw --stop-time=100ns")
        runCommand("open wave.ghw")
    }

    /*
./gradlew :cleanJvmTest :jvmTest --tests "ai.hypergraph.kaliningraph.vhdl.TestVHDL.testMultiplication"
     */

    @Test
    fun testMultiplication() {
        val adder = """
            -- Implements signed multiplication of two integers
            library IEEE;
            use IEEE.std_logic_1164.all;

            entity signed_multiply is
                port (
                    A : in std_logic_vector(31 downto 0);
                    B : in std_logic_vector(31 downto 0);
                    C : out std_logic_vector(31 downto 0)
                );
            end signed_multiply;

            architecture sm of signed_multiply is
                signal product : std_logic_vector(31 downto 0);
                signal carry : std_logic_vector(2 downto 0);
            begin
                process (A, B)
                begin
                    if A(31) = '0' then
                        product(31) <= A(31);
                    else
                        product(31) <= '1';
                    end if;
                    carry(0) <= A(31);
                    for i in 31 downto 1 loop
                        if A(i) = '0' and B(i) = '0' then
                            product(31 - i) <= carry(0);
                            carry(0) <= '0';
                        elsif A(i) = '1' and B(i) = '1' then
                            product(31 - i) <= carry(0);
                            carry(0) <= '1';
                        elsif A(i) = '1' and B(i) = '0' then
                            product(31 - i) <= carry(1);
                            carry(0) <= '0';
                        elsif A(i) = '0' and B(i) = '1' then
                            product(31 - i) <= carry(1);
                            carry(0) <= '0';
                        end if;
                    end loop;
                end process;
                C <= product;
            end architecture;
        """.trimIndent().let { File("adder.vhd").apply { writeText(it) } }

        val testadder = """
            -- Testbench for RCA
            library IEEE;
            use IEEE.std_logic_1164.all;

            entity testbench is
            -- empty
            end testbench;

            architecture tb of testbench is
                component signed_multiply is
                    port (
                        A : in std_logic_vector(31 downto 0);
                        B : in std_logic_vector(31 downto 0);
                        C : out std_logic_vector(31 downto 0)
                    );
                end component;
                
            signal A, B, C : std_logic_vector(31 downto 0);
            begin
                -- DUT
                dut : signed_multiply
                port map (
                    A => A,
                    B => B,
                    C => C
                );
                process
                begin
                    A <= x"00000000";
                    B <= x"00000000";
                    wait for 10 ns;
                    assert (C = x"00000000")
                        report "A * B = 0"
                        severity note;

                    A <= x"00000011";
                    B <= x"00000011";
                    wait for 10 ns;
                    assert (C = x"00001001")
                        report "A * B = 9"
                        severity note;

                    assert false report "Test done." severity note;
                    wait;
                end process;
            end architecture;
        """.trimIndent().let { File("testbench.vhd").apply { writeText(it) } }

        runCommand("ghdl -a ${adder.absolutePath} ${testadder.absolutePath}")
        runCommand("ghdl -e testbench")
        runCommand("ghdl -r testbench --wave=wave.ghw --stop-time=100ns")
        runCommand("open wave.ghw")
    }

    /*
./gradlew :cleanJvmTest :jvmTest --tests "ai.hypergraph.kaliningraph.vhdl.TestVHDL.testRAM"
     */

    @Test
    fun testRAM() {
        val ram = """
            -- Dual port RAM
            library ieee;
            use ieee.std_logic_1164.all;
            use ieee.numeric_std.all;

            entity dual_port_RAM is
                generic (
                    DATA_WIDTH : integer := 8;
                    ADDR_WIDTH : integer := 8
                );
                port (
                    clk : in std_logic;
                    we : in std_logic;
                    din : in std_logic_vector(DATA_WIDTH-1 downto 0);
                    addr : in std_logic_vector(ADDR_WIDTH-1 downto 0);
                    dout : out std_logic_vector(DATA_WIDTH-1 downto 0)
                );
            end dual_port_RAM;

            architecture Behavioral of dual_port_RAM is
                type mem_type is array(0 to 2**ADDR_WIDTH-1) of std_logic_vector(DATA_WIDTH-1 downto 0);
                signal mem : mem_type;
            begin
                process (clk)
                begin
                    if (clk'event and clk = '1') then
                        if (we = '1') then
                            mem(conv_integer(addr)) := din;
                        end if;
                        dout := mem(conv_integer(addr));
                    end if;
                end process;
            end Behavioral;
        """.trimIndent().let { File("ram.vhd").apply { writeText(it) } }

        val testram = """
            -- Testbench for RAM
            library IEEE;
            use IEEE.std_logic_1164.all;

            entity testbench is
            -- empty
            end testbench;

            architecture tb of testbench is
                signal clk : std_logic;
                signal we : std_logic;
                signal din : std_logic_vector(7 downto 0);
                signal addr : std_logic_vector(7 downto 0);
                signal dout : std_logic_vector(7 downto 0);
                signal mem_addr : std_logic_vector(7 downto 0);
                signal mem_din : std_logic_vector(7 downto 0);
                signal mem_dout : std_logic_vector(7 downto 0);
                signal mem_we : std_logic;
                signal mem_clk : std_logic;
            begin
                clk <= not clk after 5 ns;
                we <= '1' after 5 ns;
                din <= "00110101" after 5 ns;
                addr <= "00000010" after 5 ns;
                process
                begin
                    wait for 10 ns;
                    mem_clk <= not clk after 5 ns;
                    mem_we <= we after 5 ns;
                    mem_addr <= addr after 5 ns;
                    mem_din <= din after 5 ns;
                end process;
                dout <= mem_dout after 5 ns;
                wait;
            end tb;
        """.trimIndent().let { File("testbench.vhd").apply { writeText(it) } }

        runCommand("ghdl -a ${ram.absolutePath} ${testram.absolutePath}")
        runCommand("ghdl -e testbench")
        runCommand("ghdl -r testbench --wave=wave.ghw --stop-time=1000ns")
        runCommand("open wave.ghw")
    }

    fun String.allVars(ops: List<String> = listOf("and", "or")) =
        split("\\W+".toRegex()).filter { it.all { it.isLetterOrDigit() } && it !in ops && it.isNotEmpty() }.distinct()

    fun genArithmeticCircuit(
        circuit: String,
        inputVars: List<String> = circuit.allVars()
    ) = """
          library IEEE;
          use IEEE.std_logic_1164.all;

          entity gate is
          port(
          """.trimIndent() +
            inputVars.joinToString("\n", "\n", "\n") { "    $it: in std_logic;" } +
            """
              q: out std_logic
          );
          end gate;

          architecture rtl of gate is
          begin
            process(a, b) is
            begin
              q <= $circuit;
            end process;
          end rtl;
        """.trimIndent()

    /*
./gradlew :cleanJvmTest :jvmTest --tests "ai.hypergraph.kaliningraph.vhdl.TestVHDL.testCircuitGen"
   */
    @Test
    fun testCircuitGen() {
        val circuit = "a and (b and c)"
        val designFile = genArithmeticCircuit(circuit).let { File("design.vhd").apply { writeText(it) } }
        val vars = circuit.allVars()

        val test1: Pair<Map<String, Int>, Int> = mapOf(
            "a" to 1,
            "b" to 0,
            "c" to 1
        ) to 0

        val test2: Pair<Map<String, Int>, Int> = mapOf(
            "a" to 1,
            "b" to 1,
            "c" to 1
        ) to 1

        val test3: Pair<Map<String, Int>, Int> = mapOf(
            "a" to 1,
            "b" to 0,
            "c" to 1
        ) to 0

        val testbench = """
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
                vars.joinToString("\n", "\n", "\n") { "    $it: in std_logic;" } +
                """
            q: out std_logic);
          end component;

          signal ${vars.joinToString(", ") { "${it}_in" }}, q_out: std_logic;

          begin
            -- Connect DUT
            DUT: gate port map(${vars.joinToString(", ") { "${it}_in" }}, q_out);

            process
            begin
              ${test1.toTest()}
              wait for 3 ns;
              assert(q_out='${test1.second}') report "Fail $test1" severity error;
              
              ${test2.toTest()}
              wait for 3 ns;
              assert(q_out='${test2.second}') report "Fail $test2" severity error;
              
              ${test3.toTest()}
              wait for 3 ns;
              assert(q_out='${test3.second}') report "Fail $test3" severity error;

              assert false report "Test done." severity note;
              wait;
            end process;
          end tb;
        """.trimIndent()

        val file = testbench.let { File("testbench.vhd").apply { writeText(it) } }

        runCommand("ghdl -a ${file.absolutePath} ${designFile.absolutePath}")
        runCommand("ghdl -e testbench")
        runCommand("ghdl -r testbench --wave=wave.ghw --stop-time=100ns")
        runCommand("open wave.ghw")
    }

    // Test case for a Boolean circuit
    fun Pair<Map<String, Int>, Int>.toTest() =
        first.entries.joinToString("; ", "", ";") { (k, v) -> "${k}_in <= '$v'" }

    private fun String.runVHDL() {
        File("hello.vhd").apply { writeText(this@runVHDL) }
        runCommand("ghdl -a hello.vhd")
        runCommand("ghdl -e hello_world")
        runCommand("ghdl -r hello_world")
    }

    fun runCommand(command: String): Boolean =
        try {
            ProcessBuilder(*command.split(" ").toTypedArray())
                .redirectOutput(INHERIT).redirectError(INHERIT).start().waitFor(60, MINUTES)
        } catch (e: Exception) {
            false
        }
}