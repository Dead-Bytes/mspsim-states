/**
 * Simple test to validate energy integration
 * Run this without compilation to check for basic syntax and logic
 */

import se.sics.mspsim.util.EnergyConfig;
import se.sics.mspsim.core.InstructionEnergyMonitor;

public class TestEnergyIntegration {

    public static void main(String[] args) {
        System.out.println("=== MSPSim Energy Integration Test ===");

        // Test 1: EnergyConfig loading
        try {
            // Test environment loading (with default values)
            EnergyConfig config = EnergyConfig.loadFromEnv();
            System.out.println("✓ EnergyConfig creation successful");
            System.out.println("  Total Energy: " + config.getTotalEnergyNanoJoules() + " nJ");
            System.out.println("  Energy logging: " + config.isEnergyLoggingEnabled());
            System.out.println("  Checkpoint threshold: " + config.getCheckpointThreshold() + "%");

            // Test JSON loading if file exists
            try {
                config.loadFromJSON("energy_analysis.json");
                System.out.println("✓ JSON loading successful");
                System.out.println("  Known instructions: " + config.getKnownInstructions().size());
                System.out.println("  Energy blocks: " + config.getBlockCount());

                // Test instruction energy lookup
                double movEnergy = config.getInstructionEnergy("MOV.W");
                double bisEnergy = config.getInstructionEnergy("BIS.W");
                double unknownEnergy = config.getInstructionEnergy("UNKNOWN_INSTR");

                System.out.println("  MOV.W energy: " + movEnergy + " nJ");
                System.out.println("  BIS.W energy: " + bisEnergy + " nJ");
                System.out.println("  Unknown instruction energy: " + unknownEnergy + " nJ");

            } catch (Exception e) {
                System.out.println("⚠ JSON loading failed: " + e.getMessage());
            }

            // Test 2: InstructionEnergyMonitor
            try {
                InstructionEnergyMonitor monitor = new InstructionEnergyMonitor(config);
                System.out.println("✓ InstructionEnergyMonitor creation successful");
                System.out.println("  Initial battery: " + String.format("%.1f", monitor.getBatteryPercentage()) + "%");
                System.out.println("  Initial energy: " + String.format("%.1f", monitor.getRemainingEnergyNJ()) + " nJ");

                // Test energy deduction
                double consumed1 = monitor.deductInstructionEnergy(0x4000, "MOV.W");
                double consumed2 = monitor.deductInstructionEnergy(0x4004, "BIS.W");
                double consumed3 = monitor.deductInstructionEnergy(0x4008, "JEQ");

                System.out.println("  Energy after 3 instructions:");
                System.out.println("    Consumed: " + String.format("%.3f", consumed1 + consumed2 + consumed3) + " nJ");
                System.out.println("    Battery: " + String.format("%.1f", monitor.getBatteryPercentage()) + "%");
                System.out.println("    Remaining: " + String.format("%.1f", monitor.getRemainingEnergyNJ()) + " nJ");

                // Test many instructions to see battery drain
                System.out.println("  Simulating 1000 instructions...");
                for (int i = 0; i < 1000; i++) {
                    monitor.deductInstructionEnergy(0x4000 + (i * 2), "MOV.W");
                }

                System.out.println("  After 1000 instructions:");
                System.out.println("    Battery: " + String.format("%.1f", monitor.getBatteryPercentage()) + "%");
                System.out.println("    Total consumed: " + String.format("%.1f", monitor.getTotalConsumedEnergyNJ()) + " nJ");
                System.out.println("    Avg per instruction: " + String.format("%.3f", monitor.getAverageEnergyPerInstruction()) + " nJ");
                System.out.println("    Instruction count: " + monitor.getInstructionCount());

                monitor.shutdown();
                System.out.println("✓ InstructionEnergyMonitor shutdown successful");

            } catch (Exception e) {
                System.out.println("✗ InstructionEnergyMonitor test failed: " + e.getMessage());
                e.printStackTrace();
            }

        } catch (Exception e) {
            System.out.println("✗ EnergyConfig test failed: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("\n=== Integration Test Complete ===");
        System.out.println("If all tests passed, the energy integration is ready!");
        System.out.println("\nNext steps:");
        System.out.println("1. Set environment variables: export TOTAL_ENERGY_NJ=50000 ENERGY_MODEL_FILE=energy_analysis.json");
        System.out.println("2. Run MSPSim with: java -cp mspsim.jar se.sics.mspsim.Main your_firmware.ihex");
        System.out.println("3. Monitor energy consumption in ./outputs/energy_log_*.csv");
    }
}