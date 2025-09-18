#!/bin/bash

# MSPSim Energy Integration Usage Examples
# Demonstrates various ways to run MSPSim with energy profiles

echo "🔋 MSPSim Energy Integration Usage Examples"
echo "============================================"
echo

echo "📋 Available Commands:"
echo

echo "1. 🎯 Basic Usage - Run sort with default energy profile:"
echo "   ant runsort"
echo

echo "2. 🔧 Custom Energy Profile - Use different JSON analysis:"
echo "   ant runsort -DENERGY_MODEL_FILE=my_custom_analysis.json"
echo

echo "3. ⚡ Different Energy Budget - Change total energy available:"
echo "   ant runsort -DTOTAL_ENERGY_NJ=50000"
echo

echo "4. 📊 Different Checkpoint Strategy:"
echo "   ant runsort -DSTRATEGY=jit"
echo "   ant runsort -DSTRATEGY=periodic"
echo "   ant runsort -DSTRATEGY=adaptive"
echo

echo "5. 🎛️ Combined Parameters:"
echo "   ant runsort -DENERGY_MODEL_FILE=blocks_with_real_energy.json -DTOTAL_ENERGY_NJ=30000 -DSTRATEGY=adaptive"
echo

echo "6. 🧪 Generic Target - Run any firmware with energy:"
echo "   ant run-with-energy -DFIRMWARE=benchmarks/sort.ihex -DENERGY_MODEL_FILE=blocks_with_real_energy.json"
echo "   ant run-with-energy -DFIRMWARE=benchmarks/rsa.ihex -DENERGY_MODEL_FILE=rsa_energy_analysis.json"
echo

echo "7. 🔬 Energy Testing Target:"
echo "   ant test-energy-sort -DTEST_ENERGY_FILE=blocks_with_real_energy.json"
echo

echo "8. 📚 Other Benchmarks with Energy:"
echo "   ant rundjikstra -DENERGY_MODEL_FILE=djikstra_analysis.json"
echo "   ant runcuckoo -DENERGY_MODEL_FILE=cuckoo_analysis.json"
echo "   ant runrsa -DENERGY_MODEL_FILE=rsa_analysis.json"
echo "   ant runsha -DENERGY_MODEL_FILE=sha_analysis.json"
echo

echo "9. ❓ Help and Configuration Info:"
echo "   ant help-energy"
echo

echo "📁 File Structure:"
echo "   blocks_with_real_energy.json    # Your sort.ihex energy analysis"
echo "   .env                            # Default configuration file"
echo "   ./outputs/energy_log_*.csv      # Generated energy consumption logs"
echo "   ./outputs/battery_log_*.csv     # Generated battery level logs"
echo

echo "🔬 Real Usage Examples:"
echo

echo "# Use your actual sort energy analysis"
echo "ant runsort"
echo

echo "# Test with lower energy budget to see depletion faster"
echo "ant runsort -DTOTAL_ENERGY_NJ=5000"
echo

echo "# Use JIT checkpointing with energy monitoring"
echo "ant runsort -DSTRATEGY=jit -DENERGY_CHECKPOINT_THRESHOLD=15.0"
echo

echo "# Run with different energy profile (when you create more analyses)"
echo "ant run-with-energy -DFIRMWARE=benchmarks/sort.ihex -DENERGY_MODEL_FILE=optimized_sort_analysis.json"
echo

echo "⚙️  Parameter Explanations:"
echo "   TOTAL_ENERGY_NJ              : Total energy budget in nanojoules"
echo "   ENERGY_MODEL_FILE            : JSON file with instruction/block energy data"
echo "   ENERGY_LOGGING               : true/false - log per-instruction energy"
echo "   ENERGY_CHECKPOINT_THRESHOLD  : Battery % when checkpoints trigger"
echo "   STRATEGY                     : jit/periodic/adaptive/proposed"
echo "   FIRMWARE                     : Path to .ihex or .elf firmware file"
echo

echo "🎯 What Each Strategy Does:"
echo "   jit        : Checkpoint only when battery critical (<20%)"
echo "   periodic   : Regular checkpoints every N cycles"
echo "   adaptive   : Adjust checkpoint frequency based on energy drain"
echo "   proposed   : Custom strategy with batch processing"
echo

echo "📊 Expected Output:"
echo "   - Real-time energy consumption display"
echo "   - Battery percentage based on actual instructions"
echo "   - Energy alerts when battery gets low"
echo "   - Automatic system shutdown when energy depleted"
echo "   - Detailed CSV logs of every instruction's energy cost"
echo

echo "✅ Your system is ready! Pick a command above and run it."
echo "🔋 Energy monitoring will show realistic consumption based on your sort.ihex analysis!"