/**
 * Copyright (c) 2024, Swedish Institute of Computer Science.
 * All rights reserved.
 *
 * Monitor for block-level energy consumption
 * Implements realistic energy deduction based on basic block execution
 * Checks predicted energy before block execution and halts if insufficient
 *
 * Author: MSPSim Energy Extension
 */

package se.sics.mspsim.core;

import java.io.PrintWriter;
import java.io.FileWriter;
import java.io.IOException;
import se.sics.mspsim.util.EnergyConfig;
import se.sics.mspsim.util.EnergyHarvester;
import se.sics.mspsim.core.CheckpointStrategy;

public class InstructionEnergyMonitor implements InstructionEnergyListener {

    private final EnergyConfig config;
    private double remainingEnergyNJ;
    private double totalConsumedEnergyNJ;
    private long instructionCount;
    private boolean energyDepleted;
    private boolean lowEnergyWarningShown;
    private boolean systemHalted;

    // Block tracking
    private EnergyConfig.BlockEnergyData currentBlock;
    private int currentBlockStartAddress;
    private boolean blockEnergyChecked;
    private int blockInstructionCount;
    private boolean blockEnergyDeducted = false; // Track if we already deducted energy for this block
    private PrintWriter energyLogWriter;
    private PrintWriter blockLogWriter;
    private boolean energyLoggingInitialized;

    // Energy harvesting
    private EnergyHarvester energyHarvester;
    private boolean harvestingEnabled;
    private long blockStartTimeMS;

    // Checkpointing strategy
    private CheckpointStrategy checkpointStrategy;
    private boolean checkpointingEnabled;

    // Block execution time tracking for prediction
    private long previousBlockExecutionTimeMS = 0;
    private double executionTimeSafetyMargin = 0.2; // 20% safety margin

    public InstructionEnergyMonitor(EnergyConfig config) {
        this.config = config;
        this.remainingEnergyNJ = config.getTotalEnergyNanoJoules();
        this.totalConsumedEnergyNJ = 0.0;
        this.instructionCount = 0;
        this.energyDepleted = false;
        this.lowEnergyWarningShown = false;
        this.systemHalted = false;
        this.energyLoggingInitialized = false;

        // Block tracking initialization
        this.currentBlock = null;
        this.currentBlockStartAddress = -1;
        this.blockEnergyChecked = false;
        this.blockInstructionCount = 0;
        this.blockEnergyDeducted = false;

        // Energy harvesting initialization
        String harvestingEnabledStr = System.getenv("ENERGY_HARVESTING_ENABLED");
        this.harvestingEnabled = harvestingEnabledStr != null && Boolean.parseBoolean(harvestingEnabledStr);

        if (harvestingEnabled) {
            this.energyHarvester = EnergyHarvester.createFromEnv();
            System.out.println("Energy harvesting enabled: " + energyHarvester.getStatusString());
        } else {
            this.energyHarvester = null;
            System.out.println("Energy harvesting disabled");
        }

        // Checkpointing initialization
        String checkpointingEnabledStr = System.getenv("CHECKPOINTING_ENABLED");
        this.checkpointingEnabled = checkpointingEnabledStr == null || Boolean.parseBoolean(checkpointingEnabledStr);

        if (checkpointingEnabled) {
            this.checkpointStrategy = new CheckpointStrategy(config.isEnergyLoggingEnabled());
        } else {
            this.checkpointStrategy = null;
            System.out.println("Checkpointing disabled");
        }

        if (config.isEnergyLoggingEnabled()) {
            initializeEnergyLogging();
        }
    }

    /**
     * Initialize energy logging to CSV file
     */
    private void initializeEnergyLogging() {
        try {
            long timestamp = System.currentTimeMillis();
            String logFileName = "./outputs/energy_log_" + timestamp + ".csv";
            String blockLogFileName = "./outputs/block_log_" + timestamp + ".csv";

            energyLogWriter = new PrintWriter(new FileWriter(logFileName));
            blockLogWriter = new PrintWriter(new FileWriter(blockLogFileName));

            energyLogWriter.println("InstructionCount,PC,Mnemonic,BlockStart,BlockEnd,InstructionEnergy,RemainingEnergy,BatteryPercent,CPUCycles");
            blockLogWriter.println("BlockStartAddr,BlockEndAddr,PredictedEnergy,ActualInstructions,EnergyConsumed,EnergyHarvested,NetEnergyChange,RemainingAfter,BatteryPercent,ExecutionTimeMS");

            energyLoggingInitialized = true;
            System.out.println("Block-level energy logging started:");
            System.out.println("  Instructions: " + logFileName);
            System.out.println("  Blocks: " + blockLogFileName);
        } catch (IOException e) {
            System.err.println("Failed to initialize energy logging: " + e.getMessage());
        }
    }

    @Override
    public void beforeInstruction(int pc, int instruction, String mnemonic, long cycles) {
        if (systemHalted || energyDepleted) {
            return;
        }

        String pcHex = String.format("0x%x", pc);

        // Check if we're outside the current block (entering a new one)
        if (currentBlock == null || !isWithinCurrentBlock(pc)) {

            // Look for a block starting at this PC
            EnergyConfig.BlockEnergyData blockData = config.getBlockEnergy(pcHex);

            if (blockData != null) {
                // Entering a new block - check if we have enough energy for predicted consumption
                if (!checkBlockEnergyBeforeExecution(blockData, pc)) {
                    haltSystem("Insufficient energy for block starting at " + pcHex);
                    return;
                }

                // Start tracking this new block
                currentBlock = blockData;
                currentBlockStartAddress = pc;
                blockEnergyChecked = true;
                blockInstructionCount = 0;
                blockEnergyDeducted = false; // Reset for new block
                blockStartTimeMS = System.currentTimeMillis(); // Track block execution start time

                // If harvesting is enabled, predict energy balance using previous block execution time
                if (harvestingEnabled && energyHarvester != null) {
                    // Predict execution time based on previous block with safety margin
                    long predictedExecutionTimeMS;
                    if (previousBlockExecutionTimeMS > 0) {
                        // Use 80% of previous block time (20% safety margin as you requested)
                        predictedExecutionTimeMS = (long) (previousBlockExecutionTimeMS * (1.0 - executionTimeSafetyMargin));
                        System.out.println("BLOCK START: " + pcHex + " (predicted: " + blockData.predictedTotalEnergy + " nJ)");
                        System.out.println("  Predicted execution time: " + predictedExecutionTimeMS + " ms (based on previous: " + previousBlockExecutionTimeMS + " ms)");
                    } else {
                        // First block: use simplified estimation
                        predictedExecutionTimeMS = Math.max(1, (long) blockData.predictedTotalEnergy);
                        System.out.println("BLOCK START: " + pcHex + " (predicted: " + blockData.predictedTotalEnergy + " nJ)");
                        System.out.println("  Predicted execution time: " + predictedExecutionTimeMS + " ms (first block estimation)");
                    }

                    // Predict energy balance for the block with battery-aware harvesting
                    double batteryPercent = getBatteryPercentage();
                    EnergyHarvester.EnergyBalance balance = energyHarvester.predictEnergyBalanceForBlock(
                        blockData.predictedTotalEnergy, predictedExecutionTimeMS, batteryPercent);

                    System.out.println("  Predicted harvest: " + String.format("%.2f", balance.harvestedEnergy) + " nJ");
                    System.out.println("  Net energy change: " + String.format("%.2f", balance.netEnergyChange) + " nJ");

                    // Determine energy scenario
                    double consumptionVsHarvest = Math.abs(balance.netEnergyChange) / blockData.predictedTotalEnergy;
                    String scenario;
                    if (consumptionVsHarvest <= 0.10) { // Within 10% margin
                        scenario = "BALANCED";
                    } else if (balance.netEnergyChange < 0) {
                        scenario = "CONSUMING";
                    } else {
                        scenario = "CHARGING";
                    }
                    System.out.println("  Energy scenario: " + scenario);

                    // Evaluate checkpointing decision
                    if (checkpointingEnabled && checkpointStrategy != null) {
                        CheckpointStrategy.CheckpointDecision cpDecision = checkpointStrategy.shouldCheckpoint(
                            batteryPercent,
                            blockData.predictedTotalEnergy,
                            balance.harvestedEnergy,
                            pcHex
                        );

                        System.out.println("  Checkpoint Decision: " + cpDecision);

                        if (cpDecision.shouldCheckpoint) {
                            performCheckpoint(pcHex, batteryPercent, blockData.predictedTotalEnergy, balance.harvestedEnergy);
                        }
                    }

                    // Warning if harvesting can't keep up
                    if ("CONSUMING".equals(scenario)) {
                        System.out.println("  WARNING: Block will consume more energy than harvested!");
                    }
                } else {
                    System.out.println("BLOCK START: " + pcHex + " (predicted: " + blockData.predictedTotalEnergy + " nJ)");
                }
            }
        }
    }

    /**
     * Check if PC is within the current block range
     */
    private boolean isWithinCurrentBlock(int pc) {
        if (currentBlock == null) return false;

        try {
            int startAddr = Integer.parseInt(currentBlock.startAddress.substring(2), 16);
            int endAddr = Integer.parseInt(currentBlock.endAddress.substring(2), 16);
            return pc >= startAddr && pc <= endAddr;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    public void afterInstruction(int pc, int instruction, String mnemonic, long cycles, double energyConsumed) {
        if (systemHalted || energyDepleted) {
            return;
        }

        instructionCount++;
        if (currentBlock != null) {
            blockInstructionCount++;
        }

        // Check if we've reached the end of the current block
        if (currentBlock != null) {
            String pcHex = String.format("0x%x", pc);
            if (pcHex.equalsIgnoreCase(currentBlock.endAddress) || isBlockTerminalInstruction(mnemonic)) {
                // Block completed - deduct the predicted total energy
                completeBlock(pc);
            }
        }

        // Log individual instruction to file (if logging enabled)
        if (energyLogWriter != null) {
            String blockStart = currentBlock != null ? currentBlock.startAddress : "unknown";
            String blockEnd = currentBlock != null ? currentBlock.endAddress : "unknown";
            double batteryPercent = getBatteryPercentage();

            energyLogWriter.printf("%.0f,0x%04X,%s,%s,%s,%.6f,%.6f,%.2f,%d%n",
                (double) instructionCount, pc, mnemonic, blockStart, blockEnd,
                0.0, remainingEnergyNJ, batteryPercent, cycles);
            energyLogWriter.flush();
        }

        // Check for low energy warning
        double batteryPercent = getBatteryPercentage();
        if (!lowEnergyWarningShown && batteryPercent <= config.getCheckpointThreshold()) {
            lowEnergyWarningShown = true;
            onEnergyLow(batteryPercent);
        }
    }

    /**
     * Check if we have enough energy to execute a block before starting
     */
    private boolean checkBlockEnergyBeforeExecution(EnergyConfig.BlockEnergyData blockData, int pc) {
        double predictedEnergy = blockData.predictedTotalEnergy;

        if (predictedEnergy > remainingEnergyNJ) {
            System.out.println("ENERGY CHECK FAILED: Block needs " + predictedEnergy +
                             " nJ, but only " + remainingEnergyNJ + " nJ remaining");
            return false;
        }

        return true;
    }

    /**
     * Complete the execution of a basic block and deduct its total predicted energy
     * Also handles energy harvesting during block execution
     */
    private void completeBlock(int endPc) {
        if (currentBlock == null || blockEnergyDeducted) {
            return; // Already completed this block or no current block
        }

        double blockEnergy = currentBlock.predictedTotalEnergy;
        long blockExecutionTimeMS = System.currentTimeMillis() - blockStartTimeMS;
        double harvestedEnergy = 0.0;

        // Calculate actual energy harvested during real block execution time
        // Pass current battery level for battery-aware harvesting
        if (harvestingEnabled && energyHarvester != null) {
            double currentBatteryPercent = getBatteryPercentage();
            harvestedEnergy = energyHarvester.calculateRealisticHarvestingWithBattery(
                blockExecutionTimeMS, currentBatteryPercent);
        }

        // Apply net energy change to battery: harvest first, then consume
        double netEnergyChange = harvestedEnergy - blockEnergy;
        remainingEnergyNJ += netEnergyChange; // Net change (can be positive or negative)

        // Update tracking variables
        totalConsumedEnergyNJ += blockEnergy;
        blockEnergyDeducted = true; // Mark as deducted

        if (remainingEnergyNJ <= 0) {
            remainingEnergyNJ = 0;
            energyDepleted = true;
        }

        // Log block completion with harvesting details
        if (blockLogWriter != null) {
            double batteryPercent = getBatteryPercentage();
            blockLogWriter.printf("%s,%s,%.6f,%d,%.6f,%.6f,%.6f,%.6f,%.2f,%d%n",
                currentBlock.startAddress, currentBlock.endAddress,
                currentBlock.predictedTotalEnergy, blockInstructionCount,
                blockEnergy, harvestedEnergy, netEnergyChange, remainingEnergyNJ, batteryPercent,
                blockExecutionTimeMS);
            blockLogWriter.flush();
        }

        // Enhanced logging with harvesting information
        if (harvestingEnabled && energyHarvester != null) {
            // Determine actual energy scenario based on real results
            double consumptionVsHarvest = Math.abs(netEnergyChange) / blockEnergy;
            String actualScenario;
            if (consumptionVsHarvest <= 0.10) { // Within 10% margin
                actualScenario = "BALANCED";
            } else if (netEnergyChange < 0) {
                actualScenario = "CONSUMING";
            } else {
                actualScenario = "CHARGING";
            }

            System.out.println("BLOCK COMPLETE: " + currentBlock.startAddress + " -> " +
                             currentBlock.endAddress + " (actual time: " + blockExecutionTimeMS + " ms)");
            System.out.println("  Consumed: " + String.format("%.2f", blockEnergy) + " nJ");
            System.out.println("  Harvested: " + String.format("%.2f", harvestedEnergy) + " nJ (rate: " +
                             String.format("%.0f", energyHarvester.getHarvestingRateNJPerSecond()) + " nJ/s)");
            System.out.println("  Net delta: " + String.format("%.2f", netEnergyChange) + " nJ " +
                             (netEnergyChange >= 0 ? "(gained)" : "(lost)"));
            System.out.println("  Actual scenario: " + actualScenario);
            System.out.println("  Battery: " + String.format("%.2f", remainingEnergyNJ) + " nJ (" +
                             String.format("%.1f", getBatteryPercentage()) + "%)");
        } else {
            System.out.println("BLOCK COMPLETE: " + currentBlock.startAddress + " -> " +
                             currentBlock.endAddress + " (consumed: " + blockEnergy + " nJ, " +
                             "remaining: " + remainingEnergyNJ + " nJ)");
        }

        // Store actual execution time for next block prediction
        previousBlockExecutionTimeMS = blockExecutionTimeMS;

        // Check for energy depletion
        if (energyDepleted) {
            onEnergyDepleted();
        }

        // Increment checkpoint strategy block counter
        if (checkpointStrategy != null) {
            checkpointStrategy.incrementBlockCount();
        }

        // Reset block tracking for next block
        currentBlock = null;
        currentBlockStartAddress = -1;
        blockEnergyChecked = false;
        blockInstructionCount = 0;
        blockEnergyDeducted = false;
    }

    /**
     * Check if instruction marks end of a basic block
     */
    private boolean isBlockTerminalInstruction(String mnemonic) {
        // Common MSP430 instructions that end basic blocks
        return mnemonic.startsWith("jmp") || mnemonic.startsWith("br") ||
               mnemonic.startsWith("ret") || mnemonic.startsWith("reti") ||
               mnemonic.startsWith("call") || mnemonic.startsWith("jne") ||
               mnemonic.startsWith("jeq") || mnemonic.startsWith("jnz") ||
               mnemonic.startsWith("jz") || mnemonic.startsWith("jc") ||
               mnemonic.startsWith("jnc") || mnemonic.startsWith("jn") ||
               mnemonic.startsWith("jge") || mnemonic.startsWith("jl");
    }

    /**
     * Halt the system due to insufficient energy
     */
    private void haltSystem(String reason) {
        if (!systemHalted) { // Only log once
            systemHalted = true;
            energyDepleted = true;
            System.out.println("SYSTEM HALTED: " + reason);
            System.out.println("Total instructions executed: " + instructionCount);
            System.out.println("Total energy consumed: " + totalConsumedEnergyNJ + " nJ");
            System.out.println("Remaining energy: " + remainingEnergyNJ + " nJ");
        }
    }

    /**
     * Legacy method for compatibility - now returns 0 since we use block-level deduction
     */
    public double deductInstructionEnergy(int pc, String mnemonic) {
        // In block-level mode, individual instructions don't deduct energy
        // Energy is deducted when the entire block completes
        return 0.0;
    }

    /**
     * Get current battery percentage (0-100)
     */
    public double getBatteryPercentage() {
        if (config.getTotalEnergyNanoJoules() <= 0) {
            return 0.0;
        }
        return (remainingEnergyNJ / config.getTotalEnergyNanoJoules()) * 100.0;
    }

    /**
     * Check if battery is completely depleted
     */
    public boolean isBatteryDepleted() {
        return energyDepleted || remainingEnergyNJ <= 0;
    }

    /**
     * Check if system is halted due to insufficient energy
     */
    public boolean isSystemHalted() {
        return systemHalted;
    }

    @Override
    public void onEnergyLow(double remainingPercent) {
        System.out.println("ENERGY ALERT: Battery low at " + String.format("%.1f", remainingPercent) +
                         "% (" + String.format("%.2f", remainingEnergyNJ) + " nJ remaining)");
    }

    @Override
    public void onEnergyDepleted() {
        System.out.println("ENERGY DEPLETED: Battery exhausted after " + instructionCount +
                         " instructions, total consumed: " + String.format("%.2f", totalConsumedEnergyNJ) + " nJ");
    }

    // Getters for monitoring
    public double getRemainingEnergyNJ() {
        return remainingEnergyNJ;
    }

    public double getTotalConsumedEnergyNJ() {
        return totalConsumedEnergyNJ;
    }

    public long getInstructionCount() {
        return instructionCount;
    }

    public double getAverageEnergyPerInstruction() {
        return instructionCount > 0 ? totalConsumedEnergyNJ / instructionCount : 0.0;
    }

    /**
     * Shutdown and cleanup resources
     */
    public void shutdown() {
        if (energyLogWriter != null) {
            try {
                energyLogWriter.println("# Total instructions: " + instructionCount);
                energyLogWriter.println("# Total energy consumed: " + totalConsumedEnergyNJ + " nJ");
                energyLogWriter.println("# Remaining energy: " + remainingEnergyNJ + " nJ");
                energyLogWriter.close();
                System.out.println("Instruction logging completed. Total instructions: " + instructionCount +
                                 ", Total consumed: " + String.format("%.2f", totalConsumedEnergyNJ) + " nJ");
            } catch (Exception e) {
                System.err.println("Error closing instruction log: " + e.getMessage());
            }
        }

        if (blockLogWriter != null) {
            try {
                blockLogWriter.println("# Block-level energy monitoring completed");
                blockLogWriter.println("# Total instructions: " + instructionCount);
                blockLogWriter.println("# Total energy consumed: " + totalConsumedEnergyNJ + " nJ");
                blockLogWriter.println("# System halted: " + systemHalted);
                blockLogWriter.close();
                System.out.println("Block logging completed.");
            } catch (Exception e) {
                System.err.println("Error closing block log: " + e.getMessage());
            }
        }

        if (checkpointStrategy != null) {
            checkpointStrategy.shutdown();
        }
    }

    /**
     * Reset energy to full capacity (for testing or battery replacement simulation)
     */
    public void resetEnergy() {
        this.remainingEnergyNJ = config.getTotalEnergyNanoJoules();
        this.totalConsumedEnergyNJ = 0.0;
        this.instructionCount = 0;
        this.energyDepleted = false;
        this.lowEnergyWarningShown = false;
        System.out.println("Energy reset to full capacity: " + remainingEnergyNJ + " nJ");
    }

    /**
     * Perform a checkpoint operation
     */
    private void performCheckpoint(String blockAddress, double batteryPercent,
                                   double blockConsumption, double blockHarvesting) {
        // Deduct checkpoint cost from remaining energy
        double checkpointCost = CheckpointStrategy.CHECKPOINT_COST_NJ;
        remainingEnergyNJ -= checkpointCost;
        totalConsumedEnergyNJ += checkpointCost;

        if (remainingEnergyNJ < 0) {
            remainingEnergyNJ = 0;
            energyDepleted = true;
        }

        // Record checkpoint in strategy
        checkpointStrategy.executeCheckpoint(blockAddress);

        System.out.println("  *** CHECKPOINT CREATED ***");
        System.out.println("  Checkpoint cost: " + checkpointCost + " nJ");
        System.out.println("  Remaining after checkpoint: " + String.format("%.2f", remainingEnergyNJ) + " nJ");
    }
}