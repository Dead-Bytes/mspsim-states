# MSPSim Instruction-Level Energy Integration

## Overview

This integration adds realistic, instruction-level energy consumption to MSPSim, replacing the previous random battery simulation with actual program-dependent energy deduction based on executed instructions.

## Architecture

### Core Components

1. **EnergyConfig** (`se/sics/mspsim/util/EnergyConfig.java`)
   - Loads energy configuration from environment variables and JSON files
   - Maps instruction mnemonics to energy consumption values
   - Manages block-level energy data from static analysis

2. **InstructionEnergyListener** (`se/sics/mspsim/core/InstructionEnergyListener.java`)
   - Interface for instruction-level energy monitoring
   - Called before/after each instruction execution
   - Supports proxy pattern for multiple listeners

3. **InstructionEnergyMonitor** (`se/sics/mspsim/core/InstructionEnergyMonitor.java`)
   - Main energy tracking implementation
   - Deducts energy per instruction based on mnemonic
   - Tracks battery percentage and energy depletion
   - Logs energy consumption to CSV files

4. **MSP430Core Integration**
   - Modified `emulateOP()` method to call energy listeners
   - Added mnemonic extraction for instruction identification
   - Energy deduction occurs after each instruction execution

5. **MSP430 Battery Integration**
   - Updated `printCPUSpeed()` to use real energy data
   - Falls back to old random simulation if energy monitor unavailable
   - Maintains existing checkpoint strategy compatibility

## Configuration

### Environment Variables (.env)

```bash
# Total energy budget in nanojoules (50µJ = 50,000 nJ)
TOTAL_ENERGY_NJ=50000.0

# Path to JSON file containing instruction-level energy data
ENERGY_MODEL_FILE=energy_analysis.json

# Enable/disable energy logging to CSV
ENERGY_LOGGING=true

# Battery percentage threshold for low energy warnings
ENERGY_CHECKPOINT_THRESHOLD=20.0

# Existing MSPSim configuration
STRATEGY=adaptive
BATTERY_BASELINE=100.0
```

### JSON Energy Model Format

```json
{
  "total_energy_nj": 50000.0,
  "instruction_energy_map": {
    "MOV.W": 1.853,
    "BIS.W": 1.534,
    "JEQ": 3.505,
    "ADD.W": 1.650
  },
  "blocks": [
    {
      "id": 1,
      "start_address": "0x4000",
      "end_address": "0x4016",
      "instructions": [
        {
          "addr": "0x4000",
          "mnemonic": "MOV.B",
          "operands": "&WDTCTL,R5",
          "energy_est": 2.779
        }
      ],
      "predicted_total_energy": 16.036
    }
  ]
}
```

## Usage

### 1. Setup Configuration

```bash
# Set environment variables
export TOTAL_ENERGY_NJ=50000
export ENERGY_MODEL_FILE=energy_analysis.json
export ENERGY_LOGGING=true

# Or use .env file (automatically loaded)
```

### 2. Run MSPSim

```bash
# Compile MSPSim (if needed)
ant jar

# Run with iHex firmware
java -cp mspsim.jar se.sics.mspsim.Main firmware.ihex

# Run with ELF firmware
java -cp mspsim.jar se.sics.mspsim.Main firmware.elf
```

### 3. Monitor Energy Consumption

- **Real-time**: Watch console output for energy alerts and depletion
- **Detailed logs**: Check `./outputs/energy_log_*.csv` for per-instruction data
- **Battery logs**: Existing `./outputs/battery_log_*.csv` now uses real energy data

## Energy Logging Format

The energy log CSV contains:
- `InstructionCount`: Sequential instruction number
- `PC`: Program counter (instruction address)
- `Mnemonic`: Instruction mnemonic (MOV.W, BIS.W, etc.)
- `EnergyConsumed`: Energy consumed by this instruction (nJ)
- `RemainingEnergy`: Total remaining energy (nJ)
- `BatteryPercent`: Battery percentage (0-100%)
- `CPUCycles`: Total CPU cycles at execution

## Integration Benefits

1. **Realistic Energy Consumption**: Based on actual instruction execution patterns
2. **Program-Dependent**: Different programs have different energy profiles
3. **Fine-Grained Tracking**: Per-instruction energy deduction
4. **Configurable Models**: Easy to update energy values via JSON
5. **Backward Compatible**: Existing checkpoint strategies still work
6. **Extensible**: Can add peripheral energy consumption later

## Checkpoint Integration

The energy system integrates with existing MSPSim checkpoint strategies:

- **JIT Strategy**: Saves state when battery < 20%
- **Periodic Strategy**: Regular saves with energy-aware intervals
- **Adaptive Strategy**: Adjusts checkpoint frequency based on energy drain rate
- **Proposed Strategy**: Custom strategy with energy-aware batch processing

When energy is critically low or depleted:
- System triggers checkpoint save
- Console shows energy alerts
- Simulation can be configured to stop on depletion

## Testing

Run the test class to validate integration:

```bash
java TestEnergyIntegration
```

This will verify:
- Configuration loading
- JSON parsing
- Energy calculation
- Battery percentage tracking
- Instruction counting

## Extending the System

### Adding New Instruction Types

1. Update JSON energy model with new mnemonics
2. Modify `getInstructionMnemonic()` fallback mapping if needed

### Adding Peripheral Energy

1. Create peripheral-specific energy listeners
2. Register with appropriate IOUnit components
3. Add peripheral energy to total consumption

### Custom Energy Models

1. Implement `InstructionEnergyListener` interface
2. Register with CPU core via `setEnergyListener()`
3. Override energy calculation logic as needed

## Files Modified/Created

### New Files
- `se/sics/mspsim/util/EnergyConfig.java`
- `se/sics/mspsim/core/InstructionEnergyListener.java`
- `se/sics/mspsim/core/InstructionEnergyMonitor.java`
- `.env` - Configuration file
- `energy_analysis.json` - Sample energy model
- `TestEnergyIntegration.java` - Test validation

### Modified Files
- `se/sics/mspsim/core/MSP430Core.java`
  - Added energy listener support
  - Added mnemonic extraction method
  - Modified `emulateOP()` for energy deduction

- `se/sics/mspsim/core/MSP430.java`
  - Added energy monitor initialization
  - Modified `printCPUSpeed()` for real energy data
  - Updated `getCPUPercent()` to use energy monitor
  - Added energy shutdown in cleanup

This integration provides MSPSim with realistic, instruction-level energy modeling that reflects actual program execution patterns, enabling more accurate energy-aware simulation and checkpointing strategies.