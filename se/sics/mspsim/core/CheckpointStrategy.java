/**
 * Copyright (c) 2024, Swedish Institute of Computer Science.
 * All rights reserved.
 *
 * Adaptive Checkpointing Strategy for Energy-Harvesting Systems
 * Implements intelligent checkpointing based on battery regions and energy balance
 *
 * Battery Regions:
 * - GOOD (>70%): No checkpointing needed
 * - MODERATE (30-70%): Checkpoint only when consuming > harvesting
 * - LOW (<30%): Checkpoint when consuming >= harvesting (balanced or worse)
 *
 * Energy Balance States:
 * - CONSUMING: Predicted consumption > harvesting (deficit)
 * - BALANCED: Consumption ≈ harvesting (±10% tolerance)
 * - CHARGING: Consumption < harvesting (surplus)
 *
 * Author: MSPSim Energy Extension
 */

package se.sics.mspsim.core;

import java.io.PrintWriter;
import java.io.FileWriter;
import java.io.IOException;

public class CheckpointStrategy {

    // ========== STRATEGY TYPES ==========

    public enum StrategyType {
        ADAPTIVE,    // Battery-region-aware (default, used by ML models)
        JIT,         // Just-In-Time: checkpoint when battery < 20%
        PERIODIC,    // Checkpoint every N blocks (fixed interval)
        TRADITIONAL_ADAPTIVE,  // Dual-threshold adaptive
        PROPOSED     // Battery-region with different thresholds
    }

    // ========== CONFIGURABLE PARAMETERS ==========

    // Battery region thresholds (percentage)
    public static double GOOD_REGION_THRESHOLD = 70.0;      // Above 70% = Good
    public static double MODERATE_REGION_THRESHOLD = 30.0;  // 30-70% = Moderate, Below 30% = Low

    // Energy balance tolerance (percentage)
    public static double BALANCE_TOLERANCE_PERCENT = 10.0;  // ±10% considered balanced

    // Minimum blocks between checkpoints (per region)
    public static int MIN_BLOCKS_BETWEEN_CP_MODERATE = 25;  // Moderate region: 25 blocks
    public static int MIN_BLOCKS_BETWEEN_CP_LOW = 10;       // Low region: 10 blocks

    // Checkpoint cost (energy required to save state)
    public static double CHECKPOINT_COST_NJ = 50.0;         // 50 nJ per checkpoint

    // Traditional strategy parameters
    public static double JIT_THRESHOLD = 20.0;              // JIT: checkpoint when < 20%
    public static int PERIODIC_INTERVAL = 10;               // Periodic: every 10 blocks
    public static double TRADITIONAL_HIGH = 50.0;           // Traditional adaptive high threshold
    public static double TRADITIONAL_MED = 40.0;            // Traditional adaptive medium
    public static double TRADITIONAL_LOW = 20.0;            // Traditional adaptive low
    public static double PROPOSED_HIGH = 50.0;              // Proposed strategy high
    public static double PROPOSED_LOW = 20.0;               // Proposed strategy low
    public static int PROPOSED_BATCH_SIZE = 250;            // Proposed: checkpoint after 250 blocks

    // ========== ENUMS ==========

    public enum BatteryRegion {
        GOOD,       // > 70%
        MODERATE,   // 30-70%
        LOW         // < 30%
    }

    public enum EnergyBalance {
        CONSUMING,  // Consumption > Harvesting (deficit)
        BALANCED,   // Consumption ≈ Harvesting (±10%)
        CHARGING    // Consumption < Harvesting (surplus)
    }

    // ========== STATE TRACKING ==========

    private StrategyType strategy;
    private int blocksSinceLastCheckpoint = 0;
    private int totalCheckpoints = 0;
    private long lastCheckpointTime = 0;
    private String lastCheckpointBlockAddr = "none";
    private double prevBatteryPercent = 100.0;

    private PrintWriter checkpointLogWriter;
    private boolean loggingEnabled;

    // Statistics
    private int checkpointsInGood = 0;
    private int checkpointsInModerate = 0;
    private int checkpointsInLow = 0;
    private int skippedDueToMinBlocks = 0;
    private int skippedDueToGoodRegion = 0;

    public CheckpointStrategy(boolean enableLogging) {
        this(enableLogging, StrategyType.ADAPTIVE);
    }

    public CheckpointStrategy(boolean enableLogging, StrategyType strategy) {
        this.loggingEnabled = enableLogging;
        this.strategy = strategy;
        this.lastCheckpointTime = System.currentTimeMillis();
        System.out.println("Checkpoint Strategy: " + strategy);

        if (enableLogging) {
            initializeCheckpointLogging();
        }

        System.out.println("Adaptive Checkpointing Strategy initialized:");
        System.out.println("  Battery regions: GOOD(>" + GOOD_REGION_THRESHOLD + "%), " +
                         "MODERATE(" + MODERATE_REGION_THRESHOLD + "-" + GOOD_REGION_THRESHOLD + "%), " +
                         "LOW(<" + MODERATE_REGION_THRESHOLD + "%)");
        System.out.println("  Balance tolerance: ±" + BALANCE_TOLERANCE_PERCENT + "%");
        System.out.println("  Min blocks between CP: Moderate=" + MIN_BLOCKS_BETWEEN_CP_MODERATE +
                         ", Low=" + MIN_BLOCKS_BETWEEN_CP_LOW);
    }

    /**
     * Initialize checkpoint logging
     */
    private void initializeCheckpointLogging() {
        try {
            String logFileName = "./outputs/checkpoint_log_" + System.currentTimeMillis() + ".csv";
            checkpointLogWriter = new PrintWriter(new FileWriter(logFileName));
            checkpointLogWriter.println("CheckpointID,BlockAddress,BatteryPercent,BatteryRegion,EnergyBalance," +
                                      "BlockConsumed,BlockHarvested,NetChange,BlocksSinceLast,Decision,Reason,Timestamp");
            System.out.println("  Checkpoint logging: " + logFileName);
        } catch (IOException e) {
            System.err.println("Failed to initialize checkpoint logging: " + e.getMessage());
        }
    }

    /**
     * Determine current battery region based on percentage
     */
    public BatteryRegion determineBatteryRegion(double batteryPercent) {
        if (batteryPercent > GOOD_REGION_THRESHOLD) {
            return BatteryRegion.GOOD;
        } else if (batteryPercent > MODERATE_REGION_THRESHOLD) {
            return BatteryRegion.MODERATE;
        } else {
            return BatteryRegion.LOW;
        }
    }

    /**
     * Determine energy balance state based on consumption vs harvesting
     */
    public EnergyBalance determineEnergyBalance(double blockConsumption, double blockHarvesting) {
        // Calculate percentage difference
        double difference = blockConsumption - blockHarvesting;
        double percentDiff = (Math.abs(difference) / blockConsumption) * 100.0;

        if (percentDiff <= BALANCE_TOLERANCE_PERCENT) {
            return EnergyBalance.BALANCED;  // Within ±10% tolerance
        } else if (difference > 0) {
            return EnergyBalance.CONSUMING; // Consumption > Harvesting
        } else {
            return EnergyBalance.CHARGING;  // Consumption < Harvesting
        }
    }

    /**
     * Decide whether to checkpoint based on strategy type, battery level, and energy balance
     *
     * @param batteryPercent Current battery percentage (0-100)
     * @param blockConsumption Predicted energy consumption for this block (nJ)
     * @param blockHarvesting Predicted energy harvesting during block execution (nJ)
     * @param blockAddress Address of the current block
     * @return CheckpointDecision object with decision and reasoning
     */
    public CheckpointDecision shouldCheckpoint(double batteryPercent, double blockConsumption,
                                               double blockHarvesting, String blockAddress) {

        BatteryRegion region = determineBatteryRegion(batteryPercent);
        EnergyBalance balance = determineEnergyBalance(blockConsumption, blockHarvesting);

        boolean shouldCP = false;
        String reason = "";

        // Route to appropriate strategy implementation
        switch (strategy) {
            case JIT:
                // JIT: Checkpoint when battery < 20%
                if (batteryPercent < JIT_THRESHOLD) {
                    shouldCP = true;
                    reason = "JIT: Battery critical (" + String.format("%.2f", batteryPercent) + "% < " + JIT_THRESHOLD + "%)";
                } else {
                    shouldCP = false;
                    reason = "JIT: Battery sufficient (" + String.format("%.2f", batteryPercent) + "%)";
                }
                break;

            case PERIODIC:
                // Periodic: Checkpoint every N blocks
                if (blocksSinceLastCheckpoint >= PERIODIC_INTERVAL) {
                    shouldCP = true;
                    reason = "Periodic: " + blocksSinceLastCheckpoint + " blocks since last CP (interval=" + PERIODIC_INTERVAL + ")";
                } else {
                    shouldCP = false;
                    reason = "Periodic: Only " + blocksSinceLastCheckpoint + " blocks (need " + PERIODIC_INTERVAL + ")";
                }
                break;

            case TRADITIONAL_ADAPTIVE:
                // Traditional Adaptive: Dual-threshold with skipping
                double deltaBattery = batteryPercent - prevBatteryPercent;
                if (batteryPercent > TRADITIONAL_HIGH) {
                    // Above 50%: Skip if battery stable/increasing
                    if (deltaBattery >= 0) {
                        shouldCP = false;
                        reason = "TraditionalAdaptive: Battery stable/increasing (" + String.format("%.2f", batteryPercent) + "%)";
                    } else {
                        shouldCP = true;
                        reason = "TraditionalAdaptive: Battery dropping (" + String.format("%.2f", batteryPercent) + "%)";
                    }
                } else if (batteryPercent > TRADITIONAL_MED) {
                    // 40-50%: Always checkpoint
                    shouldCP = true;
                    reason = "TraditionalAdaptive: Medium battery (" + String.format("%.2f", batteryPercent) + "%)";
                } else {
                    // Below 40%: Always checkpoint (JIT mode)
                    shouldCP = true;
                    reason = "TraditionalAdaptive: Low battery (" + String.format("%.2f", batteryPercent) + "%), JIT mode";
                }
                prevBatteryPercent = batteryPercent;
                break;

            case PROPOSED:
                // Proposed: Battery-region with batch checkpointing
                if (batteryPercent > PROPOSED_HIGH) {
                    shouldCP = false;
                    reason = "Proposed: Battery sufficient (" + String.format("%.2f", batteryPercent) + "% > " + PROPOSED_HIGH + "%)";
                } else if (batteryPercent > PROPOSED_LOW) {
                    // 20-50%: Checkpoint after batch
                    if (blocksSinceLastCheckpoint >= PROPOSED_BATCH_SIZE) {
                        shouldCP = true;
                        reason = "Proposed: Mid battery (" + String.format("%.2f", batteryPercent) + "%) + " + blocksSinceLastCheckpoint + " blocks (batch=" + PROPOSED_BATCH_SIZE + ")";
                    } else {
                        shouldCP = false;
                        reason = "Proposed: Mid battery but only " + blocksSinceLastCheckpoint + " blocks (need " + PROPOSED_BATCH_SIZE + ")";
                    }
                } else {
                    // Below 20%: Always checkpoint
                    shouldCP = true;
                    reason = "Proposed: Critical battery (" + String.format("%.2f", batteryPercent) + "% < " + PROPOSED_LOW + "%)";
                }
                break;

            case ADAPTIVE:
            default:
                // Default ADAPTIVE strategy (battery-region-aware, used by ML models)
                // Rule 1: GOOD region - never checkpoint
                if (region == BatteryRegion.GOOD) {
                    shouldCP = false;
                    reason = "Battery in GOOD region (>" + GOOD_REGION_THRESHOLD + "%)";
                    skippedDueToGoodRegion++;
                }
                // Rule 2: MODERATE region - checkpoint only when consuming
                else if (region == BatteryRegion.MODERATE) {
                    if (balance == EnergyBalance.CONSUMING) {
                        // Check minimum block distance
                        if (blocksSinceLastCheckpoint >= MIN_BLOCKS_BETWEEN_CP_MODERATE) {
                            shouldCP = true;
                            reason = "MODERATE region + CONSUMING (deficit) + " + blocksSinceLastCheckpoint + " blocks since last CP";
                        } else {
                            shouldCP = false;
                            reason = "MODERATE + CONSUMING but only " + blocksSinceLastCheckpoint +
                                   " blocks (need " + MIN_BLOCKS_BETWEEN_CP_MODERATE + ")";
                            skippedDueToMinBlocks++;
                        }
                    } else {
                        shouldCP = false;
                        reason = "MODERATE region but " + balance + " (only checkpoint when CONSUMING)";
                    }
                }
                // Rule 3: LOW region - checkpoint when balanced or consuming
                else if (region == BatteryRegion.LOW) {
                    if (balance == EnergyBalance.CONSUMING || balance == EnergyBalance.BALANCED) {
                        // Check minimum block distance (lower threshold in LOW region)
                        if (blocksSinceLastCheckpoint >= MIN_BLOCKS_BETWEEN_CP_LOW) {
                            shouldCP = true;
                            reason = "LOW region (<" + MODERATE_REGION_THRESHOLD + "%) + " + balance +
                                   " + " + blocksSinceLastCheckpoint + " blocks since last CP";
                        } else {
                            shouldCP = false;
                            reason = "LOW + " + balance + " but only " + blocksSinceLastCheckpoint +
                                   " blocks (need " + MIN_BLOCKS_BETWEEN_CP_LOW + ")";
                            skippedDueToMinBlocks++;
                        }
                    } else {
                        shouldCP = false;
                        reason = "LOW region but CHARGING (surplus, no need to checkpoint)";
                    }
                }
                break;
        }

        // Create decision object
        CheckpointDecision decision = new CheckpointDecision(
            shouldCP, reason, region, balance,
            blockConsumption, blockHarvesting,
            blockConsumption - blockHarvesting
        );

        // Log decision
        if (checkpointLogWriter != null) {
            checkpointLogWriter.printf("%d,%s,%.2f,%s,%s,%.2f,%.2f,%.2f,%d,%s,\"%s\",%d%n",
                totalCheckpoints + (shouldCP ? 1 : 0),
                blockAddress,
                batteryPercent,
                region,
                balance,
                blockConsumption,
                blockHarvesting,
                blockConsumption - blockHarvesting,
                blocksSinceLastCheckpoint,
                shouldCP ? "CHECKPOINT" : "SKIP",
                reason,
                System.currentTimeMillis()
            );
            checkpointLogWriter.flush();
        }

        return decision;
    }

    /**
     * Execute a checkpoint (call this when checkpoint is performed)
     */
    public void executeCheckpoint(String blockAddress) {
        totalCheckpoints++;
        blocksSinceLastCheckpoint = 0;
        lastCheckpointTime = System.currentTimeMillis();
        lastCheckpointBlockAddr = blockAddress;

        // Track statistics by region (would need to pass region here or track separately)
        System.out.println("CHECKPOINT #" + totalCheckpoints + " at block " + blockAddress);
    }

    /**
     * Increment block counter (call after each block execution)
     */
    public void incrementBlockCount() {
        blocksSinceLastCheckpoint++;
    }

    /**
     * Get checkpoint statistics
     */
    public String getStatistics() {
        return String.format(
            "Checkpoint Statistics:\n" +
            "  Total checkpoints: %d\n" +
            "  Last checkpoint: %s (%d blocks ago)\n" +
            "  Skipped (good region): %d\n" +
            "  Skipped (min blocks): %d\n",
            totalCheckpoints,
            lastCheckpointBlockAddr,
            blocksSinceLastCheckpoint,
            skippedDueToGoodRegion,
            skippedDueToMinBlocks
        );
    }

    /**
     * Shutdown and cleanup
     */
    public void shutdown() {
        if (checkpointLogWriter != null) {
            checkpointLogWriter.println("# " + getStatistics().replace("\n", "\n# "));
            checkpointLogWriter.close();
            System.out.println("Checkpoint logging completed.");
        }
        System.out.println(getStatistics());
    }

    // Getters
    public int getTotalCheckpoints() { return totalCheckpoints; }
    public int getBlocksSinceLastCheckpoint() { return blocksSinceLastCheckpoint; }

    /**
     * Decision result from checkpoint evaluation
     */
    public static class CheckpointDecision {
        public final boolean shouldCheckpoint;
        public final String reason;
        public final BatteryRegion region;
        public final EnergyBalance balance;
        public final double blockConsumption;
        public final double blockHarvesting;
        public final double netEnergyChange;

        public CheckpointDecision(boolean shouldCP, String reason, BatteryRegion region,
                                 EnergyBalance balance, double consumption, double harvesting,
                                 double netChange) {
            this.shouldCheckpoint = shouldCP;
            this.reason = reason;
            this.region = region;
            this.balance = balance;
            this.blockConsumption = consumption;
            this.blockHarvesting = harvesting;
            this.netEnergyChange = netChange;
        }

        @Override
        public String toString() {
            return String.format("CheckpointDecision{checkpoint=%s, region=%s, balance=%s, reason='%s'}",
                               shouldCheckpoint, region, balance, reason);
        }
    }
}
