# Final Experiment Plan: ML-Driven Energy Prediction vs. Traditional Checkpointing
## Integration of Hyperparameter-Tuned Models with MSPSim IMC Simulation

**College Project - Meta-Analysis Report**
**Date:** December 2, 2025
**Status:** Planning Phase (VM with Linux Required for Execution)

---

## Executive Summary

This document outlines the final experiment that bridges our hyperparameter-tuned energy prediction models with the MSPSim intermittent computing (IMC) simulator. The experiment compares ML-driven energy prediction against traditional checkpoint strategies, demonstrating how accurate energy models can optimize checkpoint placement and improve forward progress in energy-harvesting embedded systems.

### Core Innovation
**Use our tuned XGBoost/Gradient Boosting models to predict basic block energy consumption in real-time during MSPSim execution, enabling intelligent checkpoint placement that outperforms traditional periodic/adaptive strategies.**

---

## 1. Background & Motivation

### 1.1 What We've Built So Far

#### A. Hyperparameter-Tuned Energy Prediction Models
From `HYPERPARAMETER_TUNING_REPORT.md`:

| Model | MAPE (%) | R² | Underestimation (%) | Training Time (s) |
|-------|----------|-----|---------------------|-------------------|
| **XGBoost** | 7.77 | 0.9585 | 44.16 | 1.03 |
| **Random Forest** | 8.09 | 0.9540 | 43.23 | 14.41 |
| **Gradient Boosting (Quantile)** | 31.68 | 0.6928 | **1.79** | 32.51 |
| **MLP (Quantile)** | 38.68 | 0.4408 | **0.47** | 413.73 |

**Key Features:**
- 31 WORTEX-based features extracted from MSP430 assembly
- Trained on 30,085 basic blocks from WORTEX dataset
- Safety-aware quantile loss functions for conservative prediction

#### B. MSPSim Energy-Aware Simulation
From `ENERGY_SYSTEM_DOCUMENTATION.md`:

**Current Capabilities:**
- Block-level energy monitoring (not instruction-level)
- Energy harvesting simulation (solar panel model)
- Battery-aware harvesting with region-based behavior (GOOD/MODERATE/LOW)
- Adaptive checkpointing based on energy balance
- Comprehensive logging (energy_log, block_log, checkpoint_log)

**Current Checkpoint Strategies:**
1. **JIT (Just-In-Time):** Checkpoint when battery < 20%
2. **Periodic:** Fixed interval checkpointing
3. **Adaptive:** Adjusts frequency based on energy drain rate
4. **Proposed (Battery-Region-Aware):**
   - GOOD (>70%): No checkpointing
   - MODERATE (30-70%): Selective checkpointing every 25 blocks
   - LOW (<30%): Aggressive checkpointing every 10 blocks

### 1.2 The Gap We're Filling

**Problem:** Existing MSPSim checkpoint strategies are reactive and heuristic-based:
- JIT waits until battery is critically low (risky)
- Periodic wastes energy with unnecessary checkpoints
- Adaptive uses simple energy drain rate (no workload awareness)
- Proposed uses battery regions but not actual workload characteristics

**Solution:** Use ML models to predict upcoming energy consumption and make proactive checkpoint decisions based on actual program execution patterns.

---

## 2. Research Questions

### RQ1: Prediction Accuracy
**Can our hyperparameter-tuned models accurately predict basic block energy consumption during live MSPSim execution?**

**Metrics:**
- Real-time MAPE between predicted and actual energy
- Prediction latency (must be < 1ms for real-time simulation)
- Correlation between predicted and consumed energy per block

### RQ2: Checkpoint Efficiency
**Does ML-driven checkpointing reduce checkpoint overhead compared to traditional strategies?**

**Metrics:**
- Total checkpoints created
- Checkpoint energy overhead (50 nJ × checkpoint count)
- Blocks executed between checkpoints (higher = better)
- Unnecessary checkpoint rate

### RQ3: Forward Progress
**Does ML-driven checkpointing improve total work completed before battery depletion?**

**Metrics:**
- Total basic blocks executed
- Total instructions executed
- Total "useful work" (excluding checkpoint overhead)
- Battery depletion time

### RQ4: Safety vs. Performance
**How do accuracy-optimized (XGBoost) vs. safety-optimized (GB Quantile) models perform in checkpoint decision-making?**

**Metrics:**
- Premature failures (battery depleted mid-block)
- Conservative checkpoint rate
- Energy efficiency (useful work / total energy)

---

## 3. Proposed Experiment Design

### 3.1 ML-Driven Checkpoint Strategy Implementation

#### Strategy Name: **"ML-Predictive" Checkpoint Strategy**

**Core Algorithm:**
```python
class MLPredictiveCheckpointStrategy:
    def __init__(self, model_type='xgboost'):
        # Load tuned model
        self.model = load_model(f'tuned_models_wortex/{model_type}_tuned.pkl')
        self.scaler = load_scaler()

        # Prediction buffer (look-ahead window)
        self.look_ahead_blocks = 5  # Predict next 5 blocks

        # Energy thresholds
        self.critical_threshold = 0.20  # 20% battery
        self.warning_threshold = 0.50   # 50% battery

    def should_checkpoint(self, current_block, next_blocks_metadata,
                         battery_percent, remaining_energy):
        """
        Decide if checkpoint is needed based on ML predictions
        """
        # Extract features for upcoming blocks
        upcoming_features = []
        for block_meta in next_blocks_metadata[:self.look_ahead_blocks]:
            features = extract_wortex_features(block_meta)
            upcoming_features.append(features)

        # Predict energy consumption for upcoming blocks
        features_scaled = self.scaler.transform(upcoming_features)
        predicted_energies = self.model.predict(features_scaled)

        # Calculate predicted total energy needed
        total_predicted_energy = sum(predicted_energies)

        # Add safety margin (5% for XGBoost, 0% for GB Quantile)
        if self.model_type == 'xgboost':
            total_predicted_energy *= 1.05

        # Decision logic
        if total_predicted_energy > remaining_energy:
            return True, "PREDICTION: Insufficient energy for upcoming blocks"

        if battery_percent < self.critical_threshold:
            # In critical region, checkpoint if next 3 blocks exceed 10% of remaining
            if sum(predicted_energies[:3]) > 0.10 * remaining_energy:
                return True, "CRITICAL: High predicted consumption ahead"

        if battery_percent < self.warning_threshold:
            # In warning region, checkpoint if next 5 blocks exceed 20% of remaining
            if total_predicted_energy > 0.20 * remaining_energy:
                return True, "WARNING: Moderate predicted consumption ahead"

        return False, "OK: Sufficient energy predicted"
```

#### Key Features:
1. **Look-Ahead Prediction:** Predicts energy for next N blocks (configurable)
2. **Progressive Thresholds:** Different decision rules for battery regions
3. **Safety Margins:** Adjustable based on model underestimation rate
4. **Model Flexibility:** Can swap XGBoost ↔ GB Quantile ↔ MLP

### 3.2 Integration with MSPSim

#### Required Modifications (Java Side)

**New File:** `se/sics/mspsim/core/MLPredictiveCheckpointStrategy.java`

```java
public class MLPredictiveCheckpointStrategy extends CheckpointStrategy {
    private ProcessBuilder pythonProcess;
    private BufferedWriter toModel;
    private BufferedReader fromModel;

    public MLPredictiveCheckpointStrategy() {
        // Start Python subprocess with trained model
        pythonProcess = new ProcessBuilder(
            "python3",
            "ml_predictor_server.py",
            "--model", "tuned_models_wortex/XGBoost_tuned.pkl"
        );
        // Setup pipes for IPC
    }

    @Override
    public boolean shouldCheckpoint(BlockExecutionContext context) {
        // Extract block metadata
        String blockFeatures = extractBlockFeatures(context);

        // Send to Python model for prediction
        toModel.write(blockFeatures + "\n");
        toModel.flush();

        // Read prediction
        String response = fromModel.readLine();
        CheckpointDecision decision = parseDecision(response);

        return decision.shouldCheckpoint();
    }
}
```

**New File:** `ml_predictor_server.py` (Python Side)

```python
#!/usr/bin/env python3
"""
ML Model Server for MSPSim Real-Time Predictions
Listens on stdin for block features, returns energy predictions
"""
import pickle
import json
import sys
import numpy as np

class MLPredictorServer:
    def __init__(self, model_path):
        with open(model_path, 'rb') as f:
            model_data = pickle.load(f)
            self.model = model_data['model']
            self.scaler = model_data['scaler']

    def predict_energy(self, block_features):
        """Predict energy for a basic block"""
        features_array = np.array([block_features])
        features_scaled = self.scaler.transform(features_array)
        prediction = self.model.predict(features_scaled)[0]
        return prediction

    def run_server(self):
        """Main server loop"""
        for line in sys.stdin:
            try:
                data = json.loads(line.strip())

                # Extract features
                block_features = data['features']  # 31 WORTEX features
                battery_percent = data['battery_percent']
                remaining_energy = data['remaining_energy']

                # Predict energy for current + upcoming blocks
                predictions = []
                for feat in data['upcoming_blocks']:
                    pred = self.predict_energy(feat)
                    predictions.append(pred)

                # Make checkpoint decision
                decision = self.make_decision(
                    predictions, battery_percent, remaining_energy
                )

                # Return decision
                response = {
                    'checkpoint': decision['should_checkpoint'],
                    'reason': decision['reason'],
                    'predicted_energies': predictions,
                    'total_predicted': sum(predictions)
                }

                print(json.dumps(response), flush=True)

            except Exception as e:
                error_response = {'error': str(e)}
                print(json.dumps(error_response), flush=True)

if __name__ == '__main__':
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument('--model', required=True)
    args = parser.parse_args()

    server = MLPredictorServer(args.model)
    server.run_server()
```

### 3.3 Experimental Configurations

#### Test Scenarios

**Scenario 1: Sort Algorithm (Already Analyzed)**
- Firmware: `benchmarks/sort.ihex`
- Energy Model: `blocks_with_real_energy.json`
- Energy Budget: 20µJ (20,000 nJ)
- Expected Blocks: ~428 blocks

**Scenario 2: Dijkstra Algorithm** (Future)
- Firmware: `benchmarks/dijkstra.ihex`
- Energy Model: To be generated
- Energy Budget: 30µJ
- Characteristics: Complex control flow, variable energy per iteration

**Scenario 3: RSA Encryption** (Future)
- Firmware: `benchmarks/rsa.ihex`
- Energy Model: To be generated
- Energy Budget: 50µJ
- Characteristics: High computational intensity

**Scenario 4: Mixed Workload** (Future)
- Multiple algorithms running sequentially
- Tests adaptability of ML predictions

#### Checkpoint Strategies to Compare

| Strategy | Description | Parameters |
|----------|-------------|------------|
| **Baseline-JIT** | Save when battery < 20% | threshold=20% |
| **Baseline-Periodic** | Save every N blocks | N=25 blocks |
| **Baseline-Adaptive** | MSPSim's current adaptive | - |
| **Proposed-BatteryRegion** | Battery-aware 3-region | GOOD/MOD/LOW |
| **ML-XGBoost** | XGBoost predictions + 5% margin | look_ahead=5 |
| **ML-GBQuantile** | GB Quantile (safe) | look_ahead=5 |
| **ML-MLP** | MLP Quantile (ultra-safe) | look_ahead=3 |
| **ML-Ensemble** | Combine XGB + GB decisions | voting |

#### Energy Harvesting Profiles

**Profile 1: No Harvesting** (Baseline)
- Pure battery depletion test
- Isolates checkpoint efficiency

**Profile 2: Static Harvesting**
- Constant 5 nJ/ms
- Tests steady-state behavior

**Profile 3: Realistic Harvesting**
- Battery-aware charging probability
- Day/night cycles
- Environmental variations

### 3.4 Metrics Collection

#### Primary Metrics

**1. Energy Efficiency Metrics**
```
Total Energy Consumed        : Sum of all block energies + checkpoint costs
Useful Energy               : Total - Checkpoint overhead
Energy Efficiency Ratio     : Useful / Total
Average Energy per Block    : Total / Blocks executed
```

**2. Checkpoint Metrics**
```
Total Checkpoints           : Count
Checkpoint Rate             : Checkpoints / Blocks executed
Average Blocks Between CP   : Blocks / Checkpoints
Checkpoint Overhead         : Checkpoints × 50 nJ
Overhead Percentage         : (CP Energy / Total Energy) × 100
```

**3. Forward Progress Metrics**
```
Total Blocks Executed       : Count before depletion
Total Instructions Executed : Count
Execution Time              : CPU cycles
Battery Depletion Time      : Time to 0%
```

**4. Prediction Accuracy Metrics** (ML strategies only)
```
Prediction MAPE             : Mean |predicted - actual| / actual
Prediction Latency          : Time per prediction (must be <1ms)
False Positive Rate         : Unnecessary checkpoints / Total checkpoints
False Negative Rate         : Failures without checkpoint / Total failures
```

**5. Safety Metrics**
```
Premature Failures          : Battery depleted mid-critical-block
Recovery Success Rate       : Successful restores / Total failures
Energy Buffer Violations    : Times remaining < predicted consumption
```

#### Output Files

**For Each Run:**
- `energy_log_<strategy>_<timestamp>.csv` - Per-instruction energy
- `block_log_<strategy>_<timestamp>.csv` - Per-block energy balance
- `checkpoint_log_<strategy>_<timestamp>.csv` - Checkpoint decisions
- `ml_predictions_<timestamp>.csv` - ML model predictions vs. actuals
- `summary_<strategy>.json` - Aggregated metrics

---

## 4. Implementation Roadmap

### Phase 1: Infrastructure Setup (Week 1)
**Prerequisites:** Linux VM with Java 11+, Python 3.8+, MSPSim installed

**Tasks:**
- [ ] Set up Linux VM with Ubuntu 22.04
- [ ] Install MSPSim dependencies (Java 11, Ant)
- [ ] Compile MSPSim from source (`ant jar`)
- [ ] Verify existing energy integration works
- [ ] Test `ant runsort` with `blocks_with_real_energy.json`
- [ ] Install Python ML dependencies (scikit-learn, xgboost, pandas)

**Deliverable:** Working MSPSim with energy monitoring

---

### Phase 2: Model Integration (Week 2)

**Task 2.1: Prepare ML Models**
- [ ] Copy tuned models to MSPSim directory
  ```bash
  cp tuned_models_wortex/*.pkl mspsim/ml_models/
  ```
- [ ] Create feature extraction module (Python)
  ```python
  # wortex_features.py
  def extract_features_from_block(block_metadata):
      # Extract 31 WORTEX features from Java-provided metadata
      pass
  ```
- [ ] Test model loading and prediction offline
- [ ] Benchmark prediction latency (target: <1ms)

**Task 2.2: Python Server Implementation**
- [ ] Implement `ml_predictor_server.py`
- [ ] Add JSON-based IPC protocol
- [ ] Test server with mock MSPSim data
- [ ] Add logging for prediction traces

**Task 2.3: Java Integration**
- [ ] Create `MLPredictiveCheckpointStrategy.java`
- [ ] Implement subprocess management (start/stop Python server)
- [ ] Implement IPC pipes (stdin/stdout)
- [ ] Add timeout handling for predictions
- [ ] Add fallback to baseline strategy on ML failure

**Deliverable:** Working ML-MSPSim integration with XGBoost model

---

### Phase 3: Feature Engineering Bridge (Week 3)

**Challenge:** MSPSim operates on runtime block execution, but our models expect static analysis features.

**Task 3.1: Runtime Feature Extraction**
```java
public class RuntimeFeatureExtractor {
    public double[] extractWORTEXFeatures(BlockExecutionContext ctx) {
        // 1. n_instructions
        int n_inst = ctx.getInstructionCount();

        // 2. estimated_total_energy (from static analysis fallback)
        double est_energy = getBlockEnergyFromJSON(ctx.getStartPC());

        // 3-7. Energy-based features (calculate from instruction mix)
        double[] energyFeatures = calculateEnergyFeatures(ctx);

        // 8-15. Instruction frequencies
        Map<String, Integer> mnemonicCounts = ctx.getMnemonicHistogram();
        double[] freqFeatures = calculateFrequencies(mnemonicCounts, n_inst);

        // 16-20. Instruction category proportions
        double[] categoryProps = calculateCategoryProportions(mnemonicCounts);

        // 21-27. Addressing modes (approximate from operands)
        double[] addrModes = estimateAddressingModes(ctx);

        // 28-31. Derived features
        double[] derived = calculateDerivedFeatures(ctx);

        return concatenate(energyFeatures, freqFeatures,
                          categoryProps, addrModes, derived);
    }
}
```

**Task 3.2: Static Analysis Integration**
- [ ] Pre-load `blocks_with_real_energy.json` into memory
- [ ] Create lookup table: PC address → block features
- [ ] Use static features when available (exact match)
- [ ] Use runtime features when block not in static analysis
- [ ] Track feature source (static vs. runtime) for analysis

**Deliverable:** Robust feature extraction that works for analyzed + unanalyzed blocks

---

### Phase 4: Baseline Experiments (Week 4)

**Goal:** Establish performance of existing strategies before adding ML.

**Experiments:**

**Exp 4.1: No Checkpointing Baseline**
```bash
ant runsort -DCHECKPOINTING_ENABLED=false -DTOTAL_ENERGY_NJ=20000
```
- Measures: Max blocks before depletion without any overhead

**Exp 4.2: JIT Strategy**
```bash
ant runsort -DSTRATEGY=jit -DENERGY_CHECKPOINT_THRESHOLD=20.0
```
- Measures: JIT checkpoint behavior

**Exp 4.3: Periodic Strategy**
```bash
for interval in 10 25 50 100; do
    ant runsort -DSTRATEGY=periodic -DCHECKPOINT_INTERVAL=$interval
done
```
- Measures: Optimal periodic interval

**Exp 4.4: Adaptive Strategy**
```bash
ant runsort -DSTRATEGY=adaptive
```
- Measures: MSPSim's current adaptive behavior

**Exp 4.5: Battery-Region Strategy**
```bash
ant runsort -DSTRATEGY=proposed
```
- Measures: Our documented 3-region strategy

**Output:** Baseline performance table for comparison

---

### Phase 5: ML Strategy Experiments (Week 5)

**Exp 5.1: XGBoost Checkpoint Strategy**
```bash
ant runsort -DSTRATEGY=ml-xgboost -DML_MODEL=XGBoost_tuned.pkl
```
**Parameters:**
- Look-ahead: 5 blocks
- Safety margin: 5% (compensate for 44% underestimation)
- Critical threshold: 20%
- Warning threshold: 50%

**Exp 5.2: GB Quantile Strategy**
```bash
ant runsort -DSTRATEGY=ml-gb-quantile -DML_MODEL=Gradient_Boosting_tuned.pkl
```
**Parameters:**
- Look-ahead: 5 blocks
- Safety margin: 0% (quantile loss already conservative)
- Critical threshold: 20%
- Warning threshold: 50%

**Exp 5.3: MLP Strategy**
```bash
ant runsort -DSTRATEGY=ml-mlp -DML_MODEL=Multi-Layer_Perceptron_tuned.pkl
```
**Parameters:**
- Look-ahead: 3 blocks (slower predictions)
- Safety margin: 0% (0.47% underestimation)
- Ultra-conservative thresholds

**Exp 5.4: Ensemble Strategy**
```bash
ant runsort -DSTRATEGY=ml-ensemble
```
**Decision Logic:**
```
Checkpoint if ANY of:
  - XGBoost predicts insufficient energy
  - GB Quantile predicts insufficient energy
  - Battery < 30% AND either model suggests checkpoint
```

**Exp 5.5: Sensitivity Analysis**
```bash
# Vary look-ahead window
for lookahead in 3 5 7 10; do
    ant runsort -DSTRATEGY=ml-xgboost -DML_LOOKAHEAD=$lookahead
done

# Vary safety margins
for margin in 0.0 0.05 0.10 0.15; do
    ant runsort -DSTRATEGY=ml-xgboost -DML_SAFETY_MARGIN=$margin
done
```

---

### Phase 6: Energy Harvesting Experiments (Week 6)

**Goal:** Test ML strategies with realistic energy harvesting.

**Exp 6.1: No Harvesting** (Control)
```bash
ant runsort -DSTRATEGY=ml-xgboost -DENERGY_HARVESTING_ENABLED=false
```

**Exp 6.2: Static Harvesting**
```bash
ant runsort -DSTRATEGY=ml-xgboost -DHARVESTER_PROFILE=static
```

**Exp 6.3: Dynamic Harvesting**
```bash
ant runsort -DSTRATEGY=ml-xgboost -DHARVESTER_PROFILE=dynamic
```

**Exp 6.4: Realistic Harvesting**
```bash
ant runsort -DSTRATEGY=ml-xgboost -DHARVESTER_PROFILE=realistic
```

**Repeat for All Strategies:** JIT, Periodic, Adaptive, BatteryRegion, XGBoost, GB, MLP, Ensemble

**Cross-Analysis:**
- Which strategy benefits most from harvesting?
- Does ML prediction adapt better to energy influx?
- Checkpoint frequency changes with harvesting?

---

### Phase 7: Analysis & Visualization (Week 7)

**Task 7.1: Data Aggregation**
```python
# aggregate_results.py
import pandas as pd
import glob

def aggregate_experiment_results():
    results = []

    for log_file in glob.glob('outputs/checkpoint_log_*.csv'):
        strategy = extract_strategy_from_filename(log_file)
        df = pd.read_csv(log_file)

        metrics = {
            'strategy': strategy,
            'total_checkpoints': len(df),
            'avg_blocks_between': df['BlocksSinceLast'].mean(),
            'checkpoint_overhead': len(df) * 50,  # nJ
            # ... more metrics
        }
        results.append(metrics)

    return pd.DataFrame(results)
```

**Task 7.2: Comparative Visualizations**

**Plot 1: Forward Progress Comparison**
```python
# Bar chart: Total blocks executed per strategy
strategies = ['No-CP', 'JIT', 'Periodic', 'Adaptive',
              'BatteryRegion', 'XGBoost', 'GB-Quantile', 'Ensemble']
blocks_executed = [...]  # From experiments

plt.bar(strategies, blocks_executed)
plt.ylabel('Total Blocks Executed')
plt.title('Forward Progress: ML vs. Traditional Strategies')
```

**Plot 2: Checkpoint Overhead**
```python
# Stacked bar: Useful energy vs. checkpoint overhead
```

**Plot 3: Energy Prediction Accuracy** (ML only)
```python
# Scatter plot: Predicted vs. Actual block energy
# Separate plots for XGBoost, GB, MLP
```

**Plot 4: Battery Depletion Curves**
```python
# Line plot: Battery % over CPU cycles for each strategy
# Annotate checkpoint points
```

**Plot 5: Checkpoint Decision Heatmap**
```python
# Heatmap: Battery % vs. Predicted Energy → Checkpoint decision
# Shows decision boundaries
```

**Plot 6: Safety Analysis**
```python
# Box plot: Energy buffer (remaining - predicted) over time
# Negative values = failure risk
```

**Task 7.3: Statistical Analysis**
```python
# Paired t-tests: ML vs. baselines
# ANOVA: Across all strategies
# Effect sizes: Practical significance
```

---

## 5. Expected Results

### 5.1 Hypothesis 1: ML Improves Forward Progress

**Expected:** ML strategies execute 15-30% more blocks than JIT/Periodic.

**Why:**
- Avoids premature checkpoints (wastes energy)
- Avoids late checkpoints (risks failure)
- Workload-aware: checkpoints before expensive blocks

**Validation:**
- Compare total blocks executed
- Statistical significance test (p < 0.05)

### 5.2 Hypothesis 2: GB Quantile Reduces Failures

**Expected:** GB Quantile has 0 premature failures vs. 5-10% for XGBoost.

**Why:**
- 1.79% underestimation vs. 44.16% for XGBoost
- Conservative predictions trigger earlier checkpoints
- Trade-off: slightly more checkpoints, but safer

**Validation:**
- Count failures (battery depleted mid-block)
- Energy buffer violations

### 5.3 Hypothesis 3: XGBoost Maximizes Efficiency

**Expected:** XGBoost achieves highest useful work / total energy ratio.

**Why:**
- Accurate predictions minimize unnecessary checkpoints
- 7.77% MAPE allows aggressive checkpoint deferral
- With 5% safety margin, balances accuracy + safety

**Validation:**
- Calculate energy efficiency ratio
- Compare checkpoint overhead percentages

### 5.4 Hypothesis 4: Ensemble Balances All Objectives

**Expected:** Ensemble achieves:
- Forward progress: 90-95% of XGBoost
- Safety: 95-100% of GB Quantile
- Overhead: Between XGBoost and GB

**Why:**
- Voting mechanism: conservative when models disagree
- Aggressive when both models agree no checkpoint needed

**Validation:**
- Multi-objective Pareto analysis
- Radar chart: efficiency, safety, progress

---

## 6. Limitations & Challenges

### 6.1 Technical Challenges

**Challenge 1: Real-Time Prediction Latency**
- **Problem:** Python model inference must complete in <1ms
- **Mitigation:**
  - Use XGBoost (fastest model, 1.03s training suggests fast inference)
  - Batch predictions (predict 5 blocks at once)
  - Pre-compute for analyzed blocks (lookup table)

**Challenge 2: Feature Extraction Accuracy**
- **Problem:** Runtime features may not match training features exactly
- **Mitigation:**
  - Use static analysis features when available (exact PC match)
  - Validate runtime feature extraction against static analysis
  - Track feature source and analyze impact on predictions

**Challenge 3: Java-Python IPC Overhead**
- **Problem:** Subprocess communication adds latency
- **Mitigation:**
  - Use buffered pipes (not files)
  - JSON for minimal parsing overhead
  - Consider JNI for direct model embedding (future work)

**Challenge 4: Model Generalization**
- **Problem:** Models trained on WORTEX, tested on different firmware
- **Mitigation:**
  - Start with `sort.ihex` (analyzed)
  - Gradually test on unanalyzed firmware
  - Report generalization performance separately

### 6.2 Experimental Limitations

**Limitation 1: Single Architecture**
- Only MSP430 (no ARM, RISC-V comparison)
- Accept as scope limitation for college project

**Limitation 2: Simulation vs. Hardware**
- MSPSim is emulator, not real hardware
- Energy values from WORTEX measurements (real MSP430)
- Accept as reasonable approximation

**Limitation 3: Limited Benchmarks**
- Only sort algorithm fully analyzed
- Future work: Dijkstra, RSA, SHA analysis
- Report results primarily on sort, with discussion of generalization

**Limitation 4: VM Requirement**
- Cannot execute on macOS (Java version issues, MSPSim Linux-optimized)
- Need Linux VM with proper setup
- Document setup process for reproducibility

### 6.3 Risk Mitigation

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| Python IPC fails | Medium | High | Implement fallback to baseline strategy |
| Prediction latency >1ms | Medium | Medium | Use lookup table for analyzed blocks |
| Model doesn't generalize | Low | Medium | Report separate results for analyzed/unanalyzed |
| VM setup issues | High | High | Provide detailed VM setup guide + Docker container |
| Experiments take too long | Medium | Low | Prioritize core experiments, mark others as future work |

---

## 7. Deliverables

### 7.1 Code & Implementation

**Repository Structure:**
```
imc-metaanalysis/
├── mspsim/
│   ├── se/sics/mspsim/core/
│   │   └── MLPredictiveCheckpointStrategy.java  [NEW]
│   ├── ml_models/
│   │   ├── XGBoost_tuned.pkl
│   │   ├── Gradient_Boosting_tuned.pkl
│   │   └── Multi-Layer_Perceptron_tuned/
│   ├── ml_predictor_server.py  [NEW]
│   ├── wortex_features.py      [NEW]
│   └── run_experiments.sh      [NEW]
├── experiments/
│   ├── configs/
│   │   ├── exp1_baselines.json
│   │   ├── exp2_ml_strategies.json
│   │   └── exp3_harvesting.json
│   ├── results/
│   │   ├── raw_logs/
│   │   ├── aggregated_metrics.csv
│   │   └── statistical_tests.txt
│   └── visualizations/
│       ├── forward_progress.png
│       ├── checkpoint_overhead.png
│       ├── prediction_accuracy.png
│       └── battery_curves.png
├── analysis/
│   ├── aggregate_results.py
│   ├── visualize_experiments.py
│   └── statistical_analysis.R
└── docs/
    ├── FINAL_EXPERIMENT_PLAN.md  [THIS FILE]
    ├── VM_SETUP_GUIDE.md
    └── EXPERIMENT_RESULTS_REPORT.md  [AFTER EXPERIMENTS]
```

### 7.2 Documentation

**Document 1: VM Setup Guide**
- Ubuntu 22.04 installation
- Java 11, Python 3.8, dependencies
- MSPSim compilation
- Model deployment
- Verification tests

**Document 2: Experiment Execution Guide**
- Step-by-step commands
- Expected outputs
- Troubleshooting common issues

**Document 3: Results Report**
- Experiment results
- Statistical analysis
- Visualizations
- Discussion of findings
- Conclusions and future work

### 7.3 Academic Deliverables

**For College Project Submission:**

1. **Executive Summary** (2 pages)
   - Problem statement
   - Approach overview
   - Key results
   - Conclusions

2. **Technical Report** (15-20 pages)
   - Background on IMC and checkpointing
   - Hyperparameter tuning results (from existing report)
   - ML-MSPSim integration methodology
   - Experimental design
   - Results and analysis
   - Discussion and future work

3. **Presentation Slides** (20-30 slides)
   - Problem motivation
   - System architecture
   - Experimental methodology
   - Results highlights
   - Demo video (if experiments completed)
   - Q&A preparation

4. **Code Repository**
   - GitHub repo with all code
   - README with setup instructions
   - Reproducibility documentation
   - License (MIT or Apache 2.0)

---

## 8. Timeline & Resource Requirements

### 8.1 Estimated Timeline

**Assuming Part-Time Work (10-15 hrs/week):**

| Phase | Duration | Tasks | Dependencies |
|-------|----------|-------|--------------|
| Phase 1: Infrastructure | 1 week | VM setup, MSPSim compilation | Linux VM access |
| Phase 2: Model Integration | 1 week | Python server, Java bridge | Phase 1 complete |
| Phase 3: Feature Engineering | 1 week | Runtime feature extraction | Phase 2 complete |
| Phase 4: Baseline Experiments | 1 week | Run traditional strategies | Phase 3 complete |
| Phase 5: ML Experiments | 1 week | Run ML strategies | Phase 4 complete |
| Phase 6: Harvesting Experiments | 1 week | Energy harvesting tests | Phase 5 complete |
| Phase 7: Analysis | 1 week | Data analysis, visualization | Phase 6 complete |
| **Total** | **7 weeks** | | |

**Critical Path:** Sequential phases, cannot parallelize without Linux VM.

### 8.2 Resource Requirements

**Compute Resources:**
- Linux VM (Ubuntu 22.04)
- 4 CPU cores minimum
- 8 GB RAM minimum
- 20 GB disk space
- Recommended: VirtualBox on M4 MBA with 8 GB allocated

**Software:**
- Java 11 or newer
- Apache Ant
- Python 3.8+
- scikit-learn, xgboost, tensorflow, pandas, matplotlib
- R (for statistical analysis, optional)

**Time Investment:**
- Total: 70-105 hours
- Per week: 10-15 hours
- Peak: Weeks 5-6 (experiments running)

---

## 9. Success Criteria

### 9.1 Minimum Viable Success

**For college project to be considered successful:**

✅ **Criterion 1:** MSPSim integration works
- ML predictor server runs without crashes
- At least XGBoost strategy functional
- Produces valid checkpoint logs

✅ **Criterion 2:** Baseline comparison exists
- At least 2 baseline strategies tested (JIT + Periodic)
- Metrics collected and aggregated
- Results documented

✅ **Criterion 3:** ML shows improvement
- XGBoost strategy executes more blocks than at least 1 baseline
- Statistical significance demonstrated
- Results visualized

### 9.2 Desired Success

**Exceeds expectations:**

⭐ **All 5 strategies compared** (JIT, Periodic, Adaptive, BatteryRegion, ML)
⭐ **Multiple ML models tested** (XGBoost, GB, MLP)
⭐ **Energy harvesting experiments** completed
⭐ **Comprehensive statistical analysis**
⭐ **Publication-quality visualizations**

### 9.3 Stretch Goals

**If time permits:**

🚀 **Generalization testing** on Dijkstra/RSA
🚀 **Ensemble strategy** implementation
🚀 **Real-time prediction benchmarking** (<1ms verified)
🚀 **Docker container** for reproducibility
🚀 **GitHub Actions CI/CD** for automated testing

---

## 10. Future Work Beyond College Project

### 10.1 Short-Term Extensions

**A. Multi-Algorithm Analysis**
- Generate energy analysis for all benchmarks (Dijkstra, RSA, SHA, Cuckoo)
- Test ML generalization across workloads
- Create workload-specific models

**B. Hardware Validation**
- Deploy on real MSP430 hardware
- Compare simulated vs. measured energy
- Validate checkpoint overhead on real flash

**C. Advanced ML Techniques**
- Online learning: Update models during execution
- Transfer learning: Adapt WORTEX model to new architectures
- Reinforcement learning: Learn optimal checkpoint policy

### 10.2 Long-Term Research Directions

**A. Cross-Architecture Energy Prediction**
- Extend to ARM Cortex-M, RISC-V
- Multi-architecture ensemble models
- Architecture-agnostic features

**B. Real-Time Embedded Systems**
- Hard real-time guarantee compatible checkpointing
- WCET-aware energy prediction
- Timing + energy co-optimization

**C. Energy-Harvesting Co-Design**
- Adapt ML predictions to harvesting availability
- Joint optimization: checkpoint + energy harvesting
- Adaptive workload scheduling

**D. Publication Targets**
- **ACM TECS** (Transactions on Embedded Computing Systems)
- **IEEE TCAD** (Transactions on CAD)
- **LCTES** (Languages, Compilers, Tools for Embedded Systems)
- **DATE** (Design, Automation & Test in Europe)

---

## 11. Conclusion

This experiment plan bridges two major contributions:

1. **Hyperparameter-tuned ML models** for MSP430 energy prediction (WORTEX-based, 31 features, 7.77% MAPE)
2. **MSPSim energy-aware simulation** with checkpointing strategies

By integrating these systems, we enable **ML-driven checkpoint placement** that leverages accurate energy predictions to optimize forward progress in intermittent computing systems.

### Key Innovations

✨ **First ML-driven checkpointing** for embedded systems (to our knowledge)
✨ **Real-time energy prediction** during program execution
✨ **Quantile-loss safety** integrated into checkpoint decisions
✨ **Comprehensive comparison** of traditional vs. ML strategies

### Expected Impact

For college project:
- Demonstrates end-to-end ML systems thinking
- Combines static analysis, ML, and systems simulation
- Shows practical application of hyperparameter tuning

For research community:
- Opens new direction in energy-aware checkpointing
- Validates ML applicability to intermittent computing
- Provides reproducible experimental framework

---

## Appendices

### Appendix A: Quick Start Commands

**After VM Setup:**

```bash
# 1. Compile MSPSim
cd mspsim/mspsim-states
ant jar

# 2. Copy ML models
cp ../../tuned_models_wortex/*.pkl ml_models/

# 3. Test energy integration (baseline)
ant runsort

# 4. Run ML strategy (after integration)
ant runsort -DSTRATEGY=ml-xgboost

# 5. Aggregate results
cd ../../experiments
python analysis/aggregate_results.py
python analysis/visualize_experiments.py
```

### Appendix B: Key Configuration Files

**MSPSim Build Properties:**
```xml
<!-- build.xml excerpt -->
<property name="TOTAL_ENERGY_NJ" value="20000.0"/>
<property name="ENERGY_MODEL_FILE" value="blocks_with_real_energy.json"/>
<property name="STRATEGY" value="ml-xgboost"/>
<property name="ML_MODEL_PATH" value="ml_models/XGBoost_tuned.pkl"/>
<property name="ML_LOOKAHEAD_BLOCKS" value="5"/>
<property name="ML_SAFETY_MARGIN" value="0.05"/>
```

**Python Model Config:**
```json
{
  "model_type": "xgboost",
  "model_path": "ml_models/XGBoost_tuned.pkl",
  "scaler_path": "ml_models/XGBoost_scaler.pkl",
  "features": 31,
  "look_ahead": 5,
  "safety_margin": 0.05,
  "critical_threshold": 0.20,
  "warning_threshold": 0.50
}
```

### Appendix C: Contact & Support

**For Questions:**
- GitHub Issues: [Repository URL]
- Email: [Your Email]

**Related Work:**
- Hyperparameter Tuning Report: `HYPERPARAMETER_TUNING_REPORT.md`
- WORTEX Feature Extractor: `wortex_feature_extractor.py`
- MSPSim Energy Docs: `mspsim/mspsim-states/ENERGY_SYSTEM_DOCUMENTATION.md`

---

**Document Version:** 1.0
**Last Updated:** December 2, 2025
**Status:** Planning Phase - Awaiting Linux VM Setup
**Next Step:** Phase 1 - Infrastructure Setup

---

*End of Final Experiment Plan*
