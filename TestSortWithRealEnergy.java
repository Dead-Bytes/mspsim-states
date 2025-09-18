/**
 * Test MSPSim energy integration with real sort.ihex analysis
 * This validates the complete integration using your actual energy analysis data
 */

import se.sics.mspsim.util.EnergyConfig;
import se.sics.mspsim.core.InstructionEnergyMonitor;

public class TestSortWithRealEnergy {

    public static void main(String[] args) {
        System.out.println("=== MSPSim Sort.ihex Real Energy Analysis Test ===");

        try {
            // Load real energy analysis
            EnergyConfig config = EnergyConfig.loadFromEnv();
            System.out.println("✓ Environment configuration loaded");
            System.out.println("  Total Energy Budget: " + config.getTotalEnergyNanoJoules() + " nJ");

            // Load the real analysis file
            config.loadFromJSON("blocks_with_real_energy.json");
            System.out.println("✓ Real energy analysis loaded from blocks_with_real_energy.json");
            System.out.println("  Energy blocks: " + config.getBlockCount());
            System.out.println("  Instruction types extracted: " + config.getKnownInstructions().size());

            // Display some key instruction energies
            System.out.println("\n=== Instruction Energy Profile ===");
            String[] commonInstructions = {"MOV.W", "MOV.B", "BIS.W", "JEQ", "JNE", "CMP.W", "ADD.W", "SUB.W", "PUSH.W", "POP.W", "CALL", "RET"};
            for (String instr : commonInstructions) {
                double energy = config.getInstructionEnergy(instr);
                System.out.printf("  %-8s: %.3f nJ%n", instr, energy);
            }

            // Create energy monitor
            InstructionEnergyMonitor monitor = new InstructionEnergyMonitor(config);
            System.out.println("\n=== Energy Monitor Initialized ===");
            System.out.printf("  Initial battery: %.1f%%%n", monitor.getBatteryPercentage());
            System.out.printf("  Initial energy: %.1f nJ%n", monitor.getRemainingEnergyNJ());

            // Simulate execution of key sort.ihex blocks
            System.out.println("\n=== Simulating Sort Algorithm Execution ===");

            // Initialization block (0x4000-0x4016)
            System.out.println("Executing initialization block (0x4000-0x4016):");
            simulateBlock(monitor, 0x4000, new String[]{"MOV.B", "BIS.W", "MOV.W", "MOV.W", "MOV.W", "TST.W", "JEQ"});

            // Sample sorting loop blocks
            System.out.println("Executing sorting loops...");
            for (int i = 0; i < 50; i++) {
                // Simulate typical sorting operations
                simulateBlock(monitor, 0x4018 + (i * 8), new String[]{"MOV.W", "CMP.W", "JL", "MOV.W"});
                simulateBlock(monitor, 0x4200 + (i * 6), new String[]{"INC.W", "CMP.W", "JNE"});
            }

            // Display energy consumption after simulation
            System.out.println("\n=== Energy Consumption Results ===");
            System.out.printf("  Instructions executed: %d%n", monitor.getInstructionCount());
            System.out.printf("  Total energy consumed: %.2f nJ%n", monitor.getTotalConsumedEnergyNJ());
            System.out.printf("  Average energy per instruction: %.3f nJ%n", monitor.getAverageEnergyPerInstruction());
            System.out.printf("  Remaining battery: %.1f%%%n", monitor.getBatteryPercentage());
            System.out.printf("  Remaining energy: %.1f nJ%n", monitor.getRemainingEnergyNJ());

            // Estimate how many full sort executions are possible
            double avgBlockEnergy = monitor.getTotalConsumedEnergyNJ() / monitor.getInstructionCount();
            double estimatedFullSortEnergy = 4000; // Based on our earlier calculation
            int possibleExecutions = (int) (config.getTotalEnergyNanoJoules() / estimatedFullSortEnergy);

            System.out.printf("\n=== Energy Budget Analysis ===");
            System.out.printf("  Estimated energy per full sort: ~4000 nJ%n");
            System.out.printf("  Possible full executions with budget: ~%d times%n", possibleExecutions);

            // Test energy depletion simulation
            System.out.println("\n=== Testing Energy Depletion Simulation ===");
            int executionCount = 0;
            long startInstructions = monitor.getInstructionCount();

            while (!monitor.isBatteryDepleted() && executionCount < 10) {
                // Simulate one sorting execution (simplified)
                for (int i = 0; i < 200; i++) {
                    monitor.deductInstructionEnergy(0x4000 + (i * 2), "MOV.W");
                    if (monitor.isBatteryDepleted()) break;
                }
                executionCount++;

                if (executionCount % 2 == 0) {
                    System.out.printf("  After %d executions: %.1f%% battery, %.0f nJ remaining%n",
                        executionCount, monitor.getBatteryPercentage(), monitor.getRemainingEnergyNJ());
                }
            }

            long endInstructions = monitor.getInstructionCount();
            System.out.printf("  Completed %d full executions before depletion%n", executionCount);
            System.out.printf("  Total instructions in depletion test: %d%n", endInstructions - startInstructions);

            // Cleanup
            monitor.shutdown();
            System.out.println("\n✓ Test completed successfully!");

            System.out.println("\n=== Ready for Real MSPSim Execution ===");
            System.out.println("Run with: java -cp mspsim.jar se.sics.mspsim.Main benchmarks/sort.ihex");
            System.out.println("Energy logs will be saved to ./outputs/energy_log_*.csv");

        } catch (Exception e) {
            System.out.println("✗ Test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void simulateBlock(InstructionEnergyMonitor monitor, int startPC, String[] instructions) {
        for (int i = 0; i < instructions.length; i++) {
            monitor.deductInstructionEnergy(startPC + (i * 2), instructions[i]);
            if (monitor.isBatteryDepleted()) {
                System.out.println("  ⚠ Energy depleted during block execution!");
                break;
            }
        }
    }
}