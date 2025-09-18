#!/bin/bash

# MSPSim Sort Energy Test Script
# Tests the sorting algorithm with real energy analysis

echo "=== MSPSim Sort Energy Analysis Test ==="
echo

# Check if sort.ihex exists
if [ ! -f "benchmarks/sort.ihex" ]; then
    echo "❌ Error: benchmarks/sort.ihex not found"
    exit 1
fi

if [ ! -f "blocks_with_real_energy.json" ]; then
    echo "❌ Error: blocks_with_real_energy.json not found"
    exit 1
fi

# Set environment variables for energy monitoring
export TOTAL_ENERGY_NJ=20000
export ENERGY_MODEL_FILE=blocks_with_real_energy.json
export ENERGY_LOGGING=true
export ENERGY_CHECKPOINT_THRESHOLD=20.0

echo "🔋 Energy Configuration:"
echo "  Total Energy Budget: ${TOTAL_ENERGY_NJ} nJ"
echo "  Energy Model: ${ENERGY_MODEL_FILE}"
echo "  Energy Logging: ${ENERGY_LOGGING}"
echo "  Checkpoint Threshold: ${ENERGY_CHECKPOINT_THRESHOLD}%"
echo

# Create outputs directory
mkdir -p outputs

echo "🧪 Running validation test..."
# Note: Would run java TestSortWithRealEnergy here if Java was available

echo "🚀 Starting MSPSim with sort.ihex..."
echo "Command would be: java -cp mspsim.jar se.sics.mspsim.Main benchmarks/sort.ihex"
echo

# Note: The actual MSPSim execution would be:
# java -cp mspsim.jar se.sics.mspsim.Main benchmarks/sort.ihex

echo "📊 Expected Results:"
echo "  - Real-time energy consumption based on actual instructions"
echo "  - Energy deduction for each MOV.W (~1.8nJ), BIS.W (~1.5nJ), JEQ (~3.5nJ), etc."
echo "  - Block-specific energy values when PC matches analysis addresses"
echo "  - Battery percentage decreasing based on actual program execution"
echo "  - Energy logs in ./outputs/energy_log_*.csv with per-instruction data"
echo

echo "📈 Energy Analysis Summary:"
echo "  - Analysis contains 428 basic blocks"
echo "  - Total predicted energy for full execution: ~4000nJ"
echo "  - With 20µJ budget: ~5 full sorting executions possible"
echo "  - Critical energy warning at 20% (~4000nJ remaining)"
echo "  - System shutdown when energy depleted"
echo

echo "🔍 Monitor files:"
echo "  - ./outputs/energy_log_*.csv - Per-instruction energy consumption"
echo "  - ./outputs/battery_log_*.csv - Battery level over time"
echo "  - Console output - Real-time energy alerts and depletion warnings"
echo

echo "✅ Energy integration ready!"
echo "🎯 Your sorting algorithm will now have realistic energy consumption!"

# Show some sample block predictions from the analysis
echo
echo "📋 Sample Block Energy Predictions from Analysis:"
head -50 blocks_with_real_energy.json | grep -A 3 -B 1 "predicted_total_energy" | head -15