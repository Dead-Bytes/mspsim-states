# Energy-Aware Simulation System for MSPSim

## Technical Documentation

### Authors
MSPSim Energy Extension Team

### Date
September 2025

---

## Table of Contents
1. [Overview](#overview)
2. [Block-Level Energy Monitoring](#block-level-energy-monitoring)
3. [Energy Harvesting System](#energy-harvesting-system)
4. [Battery-Aware Harvesting Modifications](#battery-aware-harvesting-modifications)
5. [Consumer-Harvester Synergy](#consumer-harvester-synergy)
6. [Adaptive Checkpointing Strategy](#adaptive-checkpointing-strategy)
7. [Implementation Architecture](#implementation-architecture)
8. [Configuration Parameters](#configuration-parameters)
9. [Experimental Results](#experimental-results)

---

## Overview

This document describes the implementation of a comprehensive energy-aware simulation framework for MSPSim that integrates:

1. **Block-level energy consumption monitoring** based on static analysis
2. **Solar energy harvesting simulation** with realistic environmental modeling
3. **Battery-region-aware harvesting** that adapts to current energy state
4. **Adaptive checkpointing strategy** based on energy balance and battery level

The system enables realistic simulation of energy-harvesting IoT devices executing intermittently under power constraints.

---

## Block-Level Energy Monitoring

### Architecture

The energy monitoring system operates at the basic block granularity rather than instruction-level, providing:

- **Pre-execution energy checking**: Before executing a basic block, the system verifies sufficient energy exists for the predicted worst-case consumption
- **Block-level energy deduction**: Energy is deducted once per block execution, not per instruction
- **Halt on insufficient energy**: System halts if predicted block energy exceeds remaining battery capacity

### Energy Analysis Integration

Energy consumption values are derived from static analysis of compiled firmware:

```json
{
  "blocks": [
    {
      "id": 1,
      "start_address": "0x4000",
      "end_address": "0x4016",
      "predicted_total_energy": 10.662,
      "instructions": [...]
    }
  ]
}
```

The `predicted_total_energy` field represents the worst-case energy consumption for the entire basic block in nanojoules (nJ).

### Implementation Details

**Block Execution Flow**:
1. `beforeInstruction()`: Detect block entry, check energy sufficiency
2. Block executes: Instructions execute freely without per-instruction energy checks
3. `afterInstruction()`: Detect block exit, deduct predicted energy
4. Increment block counter for checkpoint tracking

**Key Methods**:
- `checkBlockEnergyBeforeExecution()`: Pre-execution energy validation
- `completeBlock()`: Post-execution energy deduction and harvesting integration
- `isWithinCurrentBlock()`: Block boundary detection

---

## Energy Harvesting System

### Solar Panel Model

The harvesting system simulates a small-scale solar energy harvester with configurable parameters:

**Physical Parameters**:
- Panel area: 0.05 cm² (configurable)
- Panel efficiency: 12% (configurable)
- DC-DC converter efficiency: 85%
- Irradiance: 20 W/m² base (configurable)

**Power Calculation**:
```
P_harvest = Irradiance × Area × η_panel × η_converter
```

Where:
- P_harvest is harvesting power in Watts
- Irradiance is incident light intensity in W/m²
- Area is panel surface area in m²
- η_panel is photovoltaic conversion efficiency
- η_converter is power electronics efficiency

### Harvesting Profiles

Three harvesting profiles are implemented:

**1. Static Profile**
- Constant harvesting rate based on configured parameters
- No temporal or environmental variation
- Used for baseline testing

**2. Dynamic Profile**
- Random variations in irradiance (±20% per time step)
- Simulates cloud cover and shadowing effects
- 100ms sampling interval

**3. Realistic Profile**
- Time-of-day variation (day/night cycles)
- Environmental factors (clouds, shadows)
- Battery-level awareness (described below)

---

## Battery-Aware Harvesting Modifications

### Motivation

Real-world energy-harvesting systems exhibit different harvesting behavior based on battery state:

- **High battery charge**: Device may optimize for efficiency over maximum harvesting
- **Low battery charge**: Device enters "survival mode" with aggressive harvesting attempts

### Implementation

The realistic harvesting profile now incorporates battery-region-aware charging probabilities:

| Battery Region | Threshold | Charging Probability | Harvesting Multiplier | Behavior |
|---|---|---|---|---|
| **GOOD** | > 70% | 5-10% | 0.8× | Reduced harvesting effort |
| **MODERATE** | 30-70% | 10-20% | 1.0× | Normal operation |
| **LOW** | < 30% | 20-30% | 1.5× | Aggressive harvesting |

### Charging Probability Mechanism

The charging probability determines the likelihood of "good positioning events" occurring:

```java
if (Math.random() < chargingProbability) {
    environmentalFactor *= (1.5 + Math.random() * 0.5); // 1.5-2.0× boost
}
```

**Interpretation**:
- Simulates device repositioning to optimal light conditions
- Models shadow clearing or environmental changes
- Probability increases as battery depletes (survival behavior)

### Harvesting Rate Calculation

The effective harvesting rate is computed as:

```
E_harvested = R_base × F_daylight × F_environmental × M_battery × 0.6 × Δt
```

Where:
- R_base: Base harvesting rate (nJ/ms)
- F_daylight: Time-of-day factor (0.0-1.0)
- F_environmental: Environmental factor (0.5-0.8, with boost)
- M_battery: Battery-aware multiplier (0.8, 1.0, or 1.5)
- 0.6: Conservative reduction factor
- Δt: Time duration (ms)

### Time-of-Day Modeling

```
F_daylight = {
    0.0                           if hour ∈ [0,6) ∪ (20,24]  (night)
    0.3 + (minute/60 × 0.4 × f)  if hour ∈ [11,13]          (peak)
    max(0.05, 0.25 - |12-h|×0.04) otherwise                 (dawn/dusk)
}

where f = 1.0 + chargingProbability
```

---

## Consumer-Harvester Synergy

### Energy Balance States

The system classifies energy dynamics into three states:

| State | Condition | Interpretation |
|---|---|---|
| **CONSUMING** | E_consumed > E_harvested + 10% | Energy deficit |
| **BALANCED** | \|E_consumed - E_harvested\| ≤ 10% | Equilibrium |
| **CHARGING** | E_harvested > E_consumed + 10% | Energy surplus |

The 10% tolerance accounts for prediction uncertainty and measurement noise.

### Synergy Mechanisms

**1. Block-Level Integration**

Energy harvesting occurs during block execution time:

```
E_net = E_harvested(Δt_block, battery%) - E_consumed(block)
E_battery = E_battery + E_net
```

**2. Prediction vs. Reality**

The system performs two harvesting calculations:

- **Prediction** (before block): Estimates energy balance for checkpointing decision
- **Reality** (after block): Actual harvested energy based on measured execution time

Both calculations use battery-aware harvesting to ensure consistency.

**3. Feedback Loop**

```
High Battery → Reduced Harvesting → Consumption Dominates → Battery Decreases
      ↓                                                              ↓
Low Battery  ← Increased Harvesting ← Survival Mode ← Battery Critical
```

This creates a self-regulating system where:
- Device operates efficiently when battery is sufficient
- Device fights for survival when battery is critical
- Eventually depletes despite survival efforts (realistic outcome)

### Typical Execution Profile

```
Battery Region    Charging Events    Net Energy Flow    Outcome
----------------------------------------------------------------
GOOD (70-100%)    5-10%             Deficit            Slow drain
MODERATE (30-70%) 10-20%            Deficit            Moderate drain
LOW (0-30%)       20-30%            Mixed              Fast drain with occasional recovery
```

---

## Adaptive Checkpointing Strategy

### Battery Region Classification

The system divides battery capacity into three regions:

| Region | Range | Checkpoint Behavior |
|---|---|---|
| **GOOD** | > 70% | No checkpointing |
| **MODERATE** | 30-70% | Selective checkpointing |
| **LOW** | < 30% | Aggressive checkpointing |

### Checkpointing Rules

**Rule 1: GOOD Region (> 70%)**
- No checkpointing under any energy state
- Rationale: Sufficient energy buffer, checkpoint overhead not justified

**Rule 2: MODERATE Region (30-70%)**
- Checkpoint only when energy state is CONSUMING
- Skip checkpointing when BALANCED or CHARGING
- Minimum spacing: 25 blocks between checkpoints
- Rationale: Balance checkpoint overhead against energy depletion risk

**Rule 3: LOW Region (< 30%)**
- Checkpoint when energy state is CONSUMING or BALANCED
- Skip checkpointing only when CHARGING (battery recovering)
- Minimum spacing: 10 blocks between checkpoints
- Rationale: High failure risk, aggressive state preservation

### Decision Algorithm

```
function shouldCheckpoint(battery%, E_consumed, E_harvested, blocks_since_last):
    region = determineBatteryRegion(battery%)
    balance = determineEnergyBalance(E_consumed, E_harvested)

    if region == GOOD:
        return false

    if region == MODERATE:
        if balance == CONSUMING and blocks_since_last >= 25:
            return true
        else:
            return false

    if region == LOW:
        if balance in [CONSUMING, BALANCED] and blocks_since_last >= 10:
            return true
        else:
            return false
```

### Checkpoint Cost Model

Each checkpoint operation incurs an energy cost:

- **Checkpoint cost**: 50 nJ (configurable)
- Deducted from remaining battery immediately after checkpoint creation
- Represents:
  - Memory write operations
  - Flash programming energy
  - State serialization overhead

### Rationale

The adaptive strategy balances two competing objectives:

1. **Minimize checkpoint overhead**: Avoid unnecessary checkpoints when energy is abundant
2. **Maximize forward progress**: Preserve state aggressively when failure is imminent

The battery-region-based approach provides graduated response:
- GOOD: No overhead, rely on energy abundance
- MODERATE: Conservative checkpointing, 25-block spacing prevents excessive overhead
- LOW: Aggressive preservation, 10-block spacing maximizes recovery potential

---

## Implementation Architecture

### Class Structure

**Core Classes**:

1. `InstructionEnergyMonitor` (se.sics.mspsim.core)
   - Block-level energy tracking
   - Integration point for harvesting and checkpointing
   - Energy depletion detection and system halt

2. `EnergyHarvester` (se.sics.mspsim.util)
   - Solar energy simulation
   - Battery-aware harvesting calculations
   - Multiple harvesting profiles

3. `CheckpointStrategy` (se.sics.mspsim.core)
   - Region-based decision logic
   - Block spacing enforcement
   - Checkpoint statistics tracking

4. `EnergyConfig` (se.sics.mspsim.util)
   - JSON energy model loading
   - Configuration management
   - Block energy data structures

### Integration Points

**MSP430Core Integration**:
```java
// Before instruction execution
energyListener.beforeInstruction(pc, instruction, mnemonic, cycles);

// Check for system halt
if (monitor.isSystemHalted()) {
    cpuOff = true;
    writeRegister(SR, SR | CPUOFF);
    return;
}

// After instruction execution
energyListener.afterInstruction(pc, instruction, mnemonic, cycles, energyConsumed);
```

### Data Flow

```
JSON Analysis → EnergyConfig → InstructionEnergyMonitor
                                       ↓
MSP430Core → Block Execution → Energy Deduction
                ↓                      ↓
         EnergyHarvester        CheckpointStrategy
                ↓                      ↓
         Battery Update          Checkpoint Decision
```

---

## Configuration Parameters

### Energy Monitoring

```xml
<property name="TOTAL_ENERGY_NJ" value="20000.0"/>
<property name="ENERGY_MODEL_FILE" value="blocks_with_real_energy.json"/>
<property name="ENERGY_LOGGING" value="true"/>
<property name="ENERGY_CHECKPOINT_THRESHOLD" value="20.0"/>
```

### Energy Harvesting

```xml
<property name="ENERGY_HARVESTING_ENABLED" value="true"/>
<property name="HARVESTER_PANEL_AREA_CM2" value="0.05"/>
<property name="HARVESTER_PANEL_EFFICIENCY" value="12"/>
<property name="HARVESTER_IRRADIANCE_WM2" value="20.0"/>
<property name="HARVESTER_PROFILE" value="realistic"/>
```

### Checkpointing

```xml
<property name="CHECKPOINTING_ENABLED" value="true"/>
```

**Java Class Variables** (CheckpointStrategy.java):
```java
public static double GOOD_REGION_THRESHOLD = 70.0;
public static double MODERATE_REGION_THRESHOLD = 30.0;
public static double BALANCE_TOLERANCE_PERCENT = 10.0;
public static int MIN_BLOCKS_BETWEEN_CP_MODERATE = 25;
public static int MIN_BLOCKS_BETWEEN_CP_LOW = 10;
public static double CHECKPOINT_COST_NJ = 50.0;
```

---

## Experimental Results

### Output Artifacts

The system generates three CSV log files per execution:

**1. energy_log_<timestamp>.csv**
```
InstructionCount,PC,Mnemonic,BlockStart,BlockEnd,InstructionEnergy,RemainingEnergy,BatteryPercent,CPUCycles
```
Per-instruction execution trace with energy state.

**2. block_log_<timestamp>.csv**
```
BlockStartAddr,BlockEndAddr,PredictedEnergy,ActualInstructions,EnergyConsumed,EnergyHarvested,NetEnergyChange,RemainingAfter,BatteryPercent,ExecutionTimeMS
```
Per-block energy balance with harvesting data.

**3. checkpoint_log_<timestamp>.csv**
```
CheckpointID,BlockAddress,BatteryPercent,BatteryRegion,EnergyBalance,BlockConsumed,BlockHarvested,NetChange,BlocksSinceLast,Decision,Reason,Timestamp
```
Checkpoint decision trace with rationale.

### Key Metrics

**Energy Efficiency**:
- Total blocks executed before depletion
- Average energy per block
- Harvesting efficiency (harvested/consumed ratio)

**Checkpointing Overhead**:
- Total checkpoints created
- Average blocks between checkpoints
- Checkpoint energy overhead percentage

**Battery Dynamics**:
- Battery depletion rate by region
- Charging event frequency by region
- Survival time in LOW region

### Expected Behavior

**Phase 1: GOOD Region (100% → 70%)**
- No checkpointing
- Minimal harvesting (5-10% charging events)
- Steady energy depletion
- Longest execution phase

**Phase 2: MODERATE Region (70% → 30%)**
- Selective checkpointing (CONSUMING only)
- Moderate harvesting (10-20% charging events)
- Checkpoint every ~25-50 blocks
- Moderate depletion rate

**Phase 3: LOW Region (30% → 0%)**
- Aggressive checkpointing (CONSUMING + BALANCED)
- Maximum harvesting effort (20-30% charging events)
- Checkpoint every ~10-20 blocks
- Fast depletion with occasional recovery
- Eventually depletes despite survival efforts

---

## Conclusions

The implemented energy-aware simulation framework provides:

1. **Realistic energy modeling**: Block-level granularity with static analysis integration
2. **Environmental simulation**: Time-varying solar harvesting with battery-state awareness
3. **Intelligent adaptation**: Checkpointing strategy responsive to energy conditions
4. **Experimental platform**: Comprehensive logging for energy-harvesting IoT research

The battery-aware harvesting modifications ensure realistic depletion profiles while modeling survival behavior in energy-critical conditions. The adaptive checkpointing strategy balances overhead against failure prevention through graduated response across battery regions.

This framework enables research in:
- Checkpoint placement strategies
- Energy-harvesting system design
- Intermittent computing algorithms
- Power failure recovery mechanisms

---

## References

### Source Files
- `se/sics/mspsim/core/InstructionEnergyMonitor.java`: Block-level energy monitoring
- `se/sics/mspsim/util/EnergyHarvester.java`: Solar harvesting simulation
- `se/sics/mspsim/core/CheckpointStrategy.java`: Adaptive checkpointing logic
- `se/sics/mspsim/util/EnergyConfig.java`: Energy model configuration
- `se/sics/mspsim/core/MSP430Core.java`: Core integration points

### Configuration
- `build.xml`: Ant build configuration with energy parameters
- `blocks_with_real_energy.json`: Static energy analysis data
- `.env`: Runtime configuration (optional)

### Output
- `outputs/energy_log_*.csv`: Instruction-level traces
- `outputs/block_log_*.csv`: Block-level energy balance
- `outputs/checkpoint_log_*.csv`: Checkpoint decisions
