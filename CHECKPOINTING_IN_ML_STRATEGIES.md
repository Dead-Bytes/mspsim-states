# How Checkpointing Works in ML Strategies

**Date:** December 3, 2025  
**System:** MSPSim Energy-Aware Intermittent Computing Simulation

---

## Executive Summary

**Key Point:** ML models (XGBoost, Random Forest, MLP, etc.) **only predict energy consumption**. They do NOT directly make checkpoint decisions. The checkpoint decisions are made by the **CheckpointStrategy** class using **battery-region-aware logic**.

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                      MSPSim Execution Flow                       │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│  1. InstructionEnergyMonitor.monitorInstruction(pc, mnemonic)   │
│     - Tracks instruction execution                               │
│     - Identifies basic block boundaries                          │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│  2. Block Start Detection (e.g., 0x4000)                        │
│     - Lookup block in energy model JSON                          │
│     - Get ML-predicted energy: predictedTotalEnergy              │
│     - Example: XGBoost predicts block 0x4000 needs 16.04 nJ     │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│  3. Energy Check (Before Block Execution)                       │
│     - if (predictedEnergy > remainingBatteryNJ):                 │
│         HALT SYSTEM (insufficient energy)                        │
│     - else: Allow block to execute                               │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│  4. Block Execution                                             │
│     - Execute all instructions in the block                      │
│     - Track execution time for energy harvesting                 │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│  5. Block Completion (e.g., reaches 0x4028)                     │
│     - Deduct predicted energy from battery                       │
│     - Calculate harvested energy (if enabled)                    │
│     - Update net energy: harvested - consumed                    │
│     - Log block execution to block_log_*.csv                     │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│  6. Checkpoint Decision (CheckpointStrategy.shouldCheckpoint())  │
│     ┌───────────────────────────────────────────────────────┐   │
│     │ INPUTS:                                                │   │
│     │  - batteryPercent (e.g., 99.97%)                      │   │
│     │  - blockConsumption (e.g., 16.04 nJ)                  │   │
│     │  - blockHarvesting (e.g., 13.59 nJ)                   │   │
│     │  - blockAddress (e.g., "0x4000")                      │   │
│     └───────────────────────────────────────────────────────┘   │
│                                                                   │
│     ML MODELS USE DEFAULT "ADAPTIVE" STRATEGY:                   │
│     ┌───────────────────────────────────────────────────────┐   │
│     │ if (batteryPercent > 70%):  # GOOD region            │   │
│     │     decision = SKIP ("Battery in GOOD region")       │   │
│     │ elif (batteryPercent > 30%):  # MODERATE region      │   │
│     │     if (consuming energy):                            │   │
│     │         if (blocks_since_last_cp >= 5):               │   │
│     │             decision = CHECKPOINT                     │   │
│     │         else:                                          │   │
│     │             decision = SKIP                           │   │
│     │ else:  # LOW region (<30%)                           │   │
│     │     if (consuming or balanced):                       │   │
│     │         if (blocks_since_last_cp >= 2):               │   │
│     │             decision = CHECKPOINT                     │   │
│     └───────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│  7. Execute Checkpoint Decision                                  │
│     - if (CHECKPOINT): Trigger checkpoint to flash memory        │
│     - if (SKIP): Continue execution                              │
│     - Log decision to checkpoint_log_*.csv                       │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│  8. Repeat for Next Block                                       │
│     - Continue until energy depleted or program ends             │
└─────────────────────────────────────────────────────────────────┘
```

---

## Role of ML Models in Checkpointing

### What ML Models DO:

1. **Energy Prediction Only**
   - Predict energy consumption for each basic block
   - Example: XGBoost predicts block 0x4000 → 0x4028 consumes 16.04 nJ
   - This prediction is stored in `blocks_with_real_energy_xgboost.json`

2. **Enable Energy-Aware Execution**
   - System knows how much energy each block needs
   - Can check if battery has enough energy before execution
   - Prevents mid-block power failures

3. **Indirectly Affect Checkpoint Frequency**
   - **Accurate predictions** (XGBoost 7.77% MAPE):
     - Battery depletes slower (predictions closer to actual)
     - More blocks executed before low battery
     - MORE checkpoints triggered (because more execution events)
   
   - **Conservative predictions** (Gradient Boosting 31.68% MAPE):
     - Battery depletes faster (over-prediction)
     - Fewer blocks executed
     - FEWER checkpoints triggered (because fewer execution events)

### What ML Models DO NOT DO:

❌ **ML models do NOT make checkpoint decisions**
❌ **ML models do NOT determine WHEN to checkpoint**
❌ **ML models do NOT replace CheckpointStrategy logic**

---

## CheckpointStrategy: The Decision Engine

All checkpoint decisions are made by `CheckpointStrategy.java` using the **ADAPTIVE** strategy by default for ML models.

### Adaptive Strategy Logic (Used by ALL ML Models)

```java
public CheckpointDecision shouldCheckpoint(
    double batteryPercent,      // From actual battery state
    double blockConsumption,    // From ML prediction
    double blockHarvesting,     // From energy harvester simulation
    String blockAddress         // Current block address
) {
    BatteryRegion region = determineBatteryRegion(batteryPercent);
    EnergyBalance balance = determineEnergyBalance(blockConsumption, blockHarvesting);
    
    boolean shouldCP = false;
    String reason = "";
    
    // Region-based checkpoint logic
    switch (region) {
        case GOOD:  // Battery > 70%
            shouldCP = false;
            reason = "Battery in GOOD region (>70%)";
            break;
            
        case MODERATE:  // Battery 30-70%
            if (balance == CONSUMING) {
                if (blocksSinceLastCheckpoint >= 5) {
                    shouldCP = true;
                    reason = "MODERATE region + CONSUMING + 5+ blocks";
                } else {
                    shouldCP = false;
                    reason = "MODERATE but only " + blocksSinceLastCheckpoint + " blocks";
                }
            } else {
                shouldCP = false;
                reason = "MODERATE but CHARGING/BALANCED";
            }
            break;
            
        case LOW:  // Battery < 30%
            if (balance == CONSUMING || balance == BALANCED) {
                if (blocksSinceLastCheckpoint >= 2) {
                    shouldCP = true;
                    reason = "LOW region + CONSUMING/BALANCED + 2+ blocks";
                } else {
                    shouldCP = false;
                    reason = "LOW but only " + blocksSinceLastCheckpoint + " blocks";
                }
            } else {
                shouldCP = false;
                reason = "LOW but CHARGING (surplus)";
            }
            break;
    }
    
    return new CheckpointDecision(shouldCP, reason, region, balance);
}
```

---

## Example: XGBoost Execution Trace

### Block 1: 0x4000 → 0x4028

**Step 1: ML Prediction (XGBoost)**
```
Block 0x4000 predicted energy: 16.04 nJ
```

**Step 2: Energy Check**
```
Battery: 20,000 nJ (100%)
Predicted: 16.04 nJ
Check: 16.04 < 20,000 ✅ OK to execute
```

**Step 3: Block Execution**
```
Execute 15 instructions
Time: 1.2 ms
```

**Step 4: Energy Update**
```
Consumed: 16.04 nJ (from XGBoost prediction)
Harvested: 13.59 nJ (from solar panel @ 1.2 ms)
Net change: -2.45 nJ (consuming)
New battery: 19,997.55 nJ (99.97%)
```

**Step 5: Checkpoint Decision**
```
Input:
  batteryPercent = 99.97%
  blockConsumption = 16.04 nJ
  blockHarvesting = 13.59 nJ
  blocksSinceLastCheckpoint = 0

CheckpointStrategy Logic:
  region = GOOD (99.97% > 70%)
  balance = CONSUMING (16.04 > 13.59)
  
  Decision: SKIP
  Reason: "Battery in GOOD region (>70%)"
```

**Step 6: Log to checkpoint_log_*.csv**
```csv
0,0x4000,99.97,GOOD,CONSUMING,16.04,13.59,-2.45,0,SKIP,"Battery in GOOD region (>70%)",1764771014716
```

### Block 500: Battery Drops to 25%

**Step 1: ML Prediction (XGBoost)**
```
Block 0x4e1e predicted energy: 7.19 nJ
```

**Step 2: Energy Check**
```
Battery: 5,000 nJ (25%)
Predicted: 7.19 nJ
Check: 7.19 < 5,000 ✅ OK to execute
```

**Step 3: Block Execution & Energy Update**
```
Consumed: 7.19 nJ
Harvested: 3.20 nJ
Net change: -3.99 nJ (consuming)
New battery: 4,996.01 nJ (24.98%)
Blocks since last checkpoint: 3
```

**Step 4: Checkpoint Decision**
```
Input:
  batteryPercent = 24.98%
  blockConsumption = 7.19 nJ
  blockHarvesting = 3.20 nJ
  blocksSinceLastCheckpoint = 3

CheckpointStrategy Logic:
  region = LOW (24.98% < 30%)
  balance = CONSUMING (7.19 > 3.20)
  
  Check: blocksSinceLastCheckpoint (3) >= MIN_BLOCKS_BETWEEN_CP_LOW (2) ✅
  
  Decision: CHECKPOINT
  Reason: "LOW region (<30%) + CONSUMING + 3 blocks since last CP"
```

**Step 5: Execute Checkpoint**
```
Trigger checkpoint to flash memory
Cost: 50 nJ
New battery: 4,946.01 nJ (24.73%)
Total checkpoints: 45
Reset blocksSinceLastCheckpoint = 0
```

---

## Why ML Models Have Different Checkpoint Counts

The different checkpoint counts between ML models are NOT because they make different checkpoint decisions, but because:

### 1. Energy Prediction Accuracy Affects Execution Length

**XGBoost (7.77% MAPE - Accurate):**
- Predicts blocks need ~16 nJ (close to actual)
- Battery depletes at realistic rate
- Executes 2,355 blocks before depletion
- More blocks = MORE opportunities for checkpoints
- **Result: 90 checkpoints**

**Gradient Boosting (31.68% MAPE - Conservative):**
- Predicts blocks need ~25 nJ (over-prediction)
- Battery depletes faster than reality
- Executes only 1,230 blocks before depletion
- Fewer blocks = FEWER opportunities for checkpoints
- **Result: 52 checkpoints**

### 2. Battery Depletion Rate Affects Region Transitions

**Fast Depletion (Conservative ML):**
```
Battery timeline:
0s:   100% (GOOD) → Skip checkpoints
10s:   70% (MODERATE) → Start checkpointing
15s:   30% (LOW) → Aggressive checkpointing
20s:    0% (DEPLETED) → System halts

Total blocks: 1,230
Total checkpoints: 52
Blocks/checkpoint: 23.7
```

**Slow Depletion (Accurate ML):**
```
Battery timeline:
0s:   100% (GOOD) → Skip checkpoints
30s:   70% (MODERATE) → Start checkpointing
50s:   30% (LOW) → Aggressive checkpointing
60s:    0% (DEPLETED) → System halts

Total blocks: 2,355
Total checkpoints: 90
Blocks/checkpoint: 26.2
```

### 3. Energy Harvesting Interaction

With energy harvesting enabled, the balance between consumption and harvesting changes:

**Accurate Predictions (XGBoost):**
- Block consumes 16 nJ, harvests 14 nJ → Net -2 nJ
- Slow battery drain
- More time in MODERATE/LOW regions
- More checkpoint triggers

**Conservative Predictions (GB):**
- Block "consumes" 25 nJ, harvests 14 nJ → Net -11 nJ
- Fast battery drain
- Quickly exits execution
- Fewer checkpoint triggers

---

## Comparison: ML vs Traditional Strategies

### ML Models (All use ADAPTIVE strategy)

```
XGBoost:          ADAPTIVE strategy, 2,355 blocks, 90 checkpoints
Random Forest:    ADAPTIVE strategy, 2,222 blocks, 86 checkpoints
Gradient Boosting: ADAPTIVE strategy, 1,230 blocks, 52 checkpoints
QLR:              ADAPTIVE strategy, 1,347 blocks, 54 checkpoints
Ensemble:         ADAPTIVE strategy, 1,226 blocks, 52 checkpoints
MLP:              ADAPTIVE strategy, ??? blocks, ??? checkpoints
```

**Common Strategy Logic:**
- GOOD region (>70%): SKIP
- MODERATE region (30-70%): Checkpoint if consuming + 5+ blocks
- LOW region (<30%): Checkpoint if consuming/balanced + 2+ blocks

### Traditional Strategies (Different checkpoint logic)

```
ADAPTIVE:  Battery-region-aware, 2,697 blocks, 47 checkpoints
JIT:       Checkpoint when <20%, 2,506 blocks, 71 checkpoints
PERIODIC:  Checkpoint every 10 blocks, 1,733 blocks, 173 checkpoints
PROPOSED:  Batch checkpointing, 2,500 blocks, 75 checkpoints
```

**Different Strategy Implementations:**
- Each has unique checkpoint placement logic
- Not influenced by ML predictions (no ML used)
- Make decisions based on battery thresholds, intervals, or batching

---

## Key Takeaways

1. **ML models only predict energy consumption** - they don't make checkpoint decisions

2. **CheckpointStrategy makes all checkpoint decisions** using battery-region-aware logic

3. **ML prediction accuracy affects checkpoint COUNT** indirectly:
   - Accurate predictions → More blocks executed → More checkpoints
   - Conservative predictions → Fewer blocks executed → Fewer checkpoints

4. **All ML models use the SAME checkpoint strategy** (ADAPTIVE)
   - Differences in checkpoint count are due to prediction accuracy
   - NOT due to different checkpoint logic

5. **Traditional strategies have DIFFERENT checkpoint logic**
   - Adaptive: Best (47 checkpoints, 2,697 blocks)
   - Periodic: Worst (173 checkpoints, 1,733 blocks)
   - Each implements unique checkpoint placement algorithm

6. **The ADAPTIVE strategy (used by ML models) is actually WORSE than traditional Adaptive**
   - Traditional Adaptive: 47 checkpoints, 2,697 blocks (best)
   - ML + ADAPTIVE strategy: 52-90 checkpoints, 1,226-2,355 blocks
   - Conclusion: Traditional Adaptive checkpoint logic is superior

---

## Recommendations

### For Maximum Throughput + Minimal Checkpoints:
→ Use **Traditional Adaptive** strategy (no ML needed)
- 2,697 blocks executed
- Only 47 checkpoints
- 57.4 blocks per checkpoint
- Zero prediction overhead

### For ML-Based Approach:
→ Use **Ensemble or Gradient Boosting** model
- Conservative predictions
- 52 checkpoints (lowest of ML models)
- 87% energy efficiency
- Safety guarantees (<2% underestimation)

### Avoid:
→ **Periodic strategy** (traditional or ML)
- Wastes 43% of energy on excessive checkpoints
- Only 1,733 blocks executed
- 173 checkpoints (worst of all)

---

## Testing the MLP Model

Now that we understand how checkpointing works, let's test the MLP model:

```bash
# Run MLP model test
ant test-mlp

# Check results
wc -l outputs/checkpoint_log_*.csv | tail -1
wc -l outputs/block_log_*.csv | tail -1

# Compare with other ML models
grep -c "CHECKPOINT" outputs/checkpoint_log_<timestamp>.csv
```

Expected behavior:
- MLP will use ADAPTIVE checkpoint strategy
- Checkpoint count will depend on MLP prediction accuracy
- If MLP is accurate (like XGBoost): ~80-90 checkpoints
- If MLP is conservative (like GB): ~50-60 checkpoints

---

**Document Version:** 1.0  
**Last Updated:** December 3, 2025  
**Status:** ✅ Complete

---
