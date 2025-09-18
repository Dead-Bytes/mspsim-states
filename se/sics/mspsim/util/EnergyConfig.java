/**
 * Copyright (c) 2024, Swedish Institute of Computer Science.
 * All rights reserved.
 *
 * Configuration class for instruction-level energy modeling
 * Loads energy data from JSON files and environment variables
 *
 * Author: MSPSim Energy Extension
 */

package se.sics.mspsim.util;

import java.io.*;
import java.util.*;
import se.sics.json.JSONObject;
import se.sics.json.JSONArray;

public class EnergyConfig {

    private double totalEnergyNanoJoules;
    private Map<String, Double> instructionEnergyMap;
    private Map<String, BlockEnergyData> blockEnergyMap;
    private boolean energyLoggingEnabled;
    private double checkpointThreshold;

    public static class BlockEnergyData {
        public final String startAddress;
        public final String endAddress;
        public final double blockEnergyEst;
        public final double predictedTotalEnergy;
        public final List<InstructionEnergyData> instructions;

        public BlockEnergyData(String startAddr, String endAddr, double blockEnergy,
                              double predictedEnergy, List<InstructionEnergyData> instrs) {
            this.startAddress = startAddr;
            this.endAddress = endAddr;
            this.blockEnergyEst = blockEnergy;
            this.predictedTotalEnergy = predictedEnergy;
            this.instructions = instrs;
        }
    }

    public static class InstructionEnergyData {
        public final String address;
        public final String mnemonic;
        public final String operands;
        public final double energyEst;

        public InstructionEnergyData(String addr, String mnem, String ops, double energy) {
            this.address = addr;
            this.mnemonic = mnem;
            this.operands = ops;
            this.energyEst = energy;
        }
    }

    public EnergyConfig() {
        this.instructionEnergyMap = new HashMap<>();
        this.blockEnergyMap = new HashMap<>();
        this.totalEnergyNanoJoules = 50000.0; // Default 50µJ
        this.energyLoggingEnabled = true;
        this.checkpointThreshold = 20.0;
    }

    /**
     * Load energy configuration from environment variables
     */
    public static EnergyConfig loadFromEnv() {
        EnergyConfig config = new EnergyConfig();

        // Load from environment variables
        String totalEnergyStr = System.getenv("TOTAL_ENERGY_NJ");
        if (totalEnergyStr != null) {
            try {
                config.totalEnergyNanoJoules = Double.parseDouble(totalEnergyStr);
            } catch (NumberFormatException e) {
                System.err.println("Invalid TOTAL_ENERGY_NJ value: " + totalEnergyStr);
            }
        }

        String loggingStr = System.getenv("ENERGY_LOGGING");
        if (loggingStr != null) {
            config.energyLoggingEnabled = Boolean.parseBoolean(loggingStr);
        }

        String thresholdStr = System.getenv("ENERGY_CHECKPOINT_THRESHOLD");
        if (thresholdStr != null) {
            try {
                config.checkpointThreshold = Double.parseDouble(thresholdStr);
            } catch (NumberFormatException e) {
                System.err.println("Invalid ENERGY_CHECKPOINT_THRESHOLD value: " + thresholdStr);
            }
        }

        // Load JSON energy model if specified
        String energyModelFile = System.getenv("ENERGY_MODEL_FILE");
        if (energyModelFile != null) {
            try {
                config.loadFromJSON(energyModelFile);
            } catch (Exception e) {
                System.err.println("Failed to load energy model from " + energyModelFile + ": " + e.getMessage());
            }
        }

        return config;
    }

    /**
     * Load energy data from JSON file
     */
    public void loadFromJSON(String jsonFilePath) throws IOException {
        File jsonFile = new File(jsonFilePath);
        if (!jsonFile.exists()) {
            System.err.println("Energy model file not found: " + jsonFilePath);
            return;
        }

        try {
            BufferedReader reader = new BufferedReader(new FileReader(jsonFile));
            StringBuilder jsonContent = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonContent.append(line);
            }
            reader.close();

            JSONObject root = JSONObject.parseJSONObject(jsonContent.toString());

            // Load total energy
            if (root.containsKey("total_energy_nj")) {
                this.totalEnergyNanoJoules = ((Number) root.get("total_energy_nj")).doubleValue();
            }

            // Load instruction energy map
            if (root.containsKey("instruction_energy_map")) {
                JSONObject instrMap = (JSONObject) root.get("instruction_energy_map");
                for (Object key : instrMap.keySet()) {
                    String mnemonic = (String) key;
                    double energy = ((Number) instrMap.get(mnemonic)).doubleValue();
                    this.instructionEnergyMap.put(mnemonic, energy);
                }
            } else {
                // Extract instruction energy mappings from blocks if no explicit map
                extractInstructionEnergyFromBlocks(root);
            }

            // Load block energy data
            if (root.containsKey("blocks")) {
                JSONArray blocks = (JSONArray) root.get("blocks");
                for (Object blockObj : blocks) {
                    JSONObject block = (JSONObject) blockObj;

                    String startAddr = (String) block.get("start_address");
                    String endAddr = (String) block.get("end_address");
                    double blockEnergy = ((Number) block.get("block_energy_est")).doubleValue();
                    double predictedEnergy = ((Number) block.get("predicted_total_energy")).doubleValue();

                    List<InstructionEnergyData> instructions = new ArrayList<>();
                    if (block.containsKey("instructions")) {
                        JSONArray instrArray = (JSONArray) block.get("instructions");
                        for (Object instrObj : instrArray) {
                            JSONObject instr = (JSONObject) instrObj;
                            String addr = (String) instr.get("addr");
                            String mnemonic = (String) instr.get("mnemonic");
                            String operands = (String) instr.get("operands");
                            double energy = ((Number) instr.get("energy_est")).doubleValue();

                            instructions.add(new InstructionEnergyData(addr, mnemonic, operands, energy));
                        }
                    }

                    BlockEnergyData blockData = new BlockEnergyData(startAddr, endAddr, blockEnergy, predictedEnergy, instructions);
                    this.blockEnergyMap.put(startAddr, blockData);
                }
            }

            System.out.println("Loaded energy model: " + instructionEnergyMap.size() + " instruction types, " +
                             blockEnergyMap.size() + " blocks, total energy: " + totalEnergyNanoJoules + " nJ");

        } catch (Exception e) {
            throw new IOException("Failed to parse JSON energy model: " + e.getMessage(), e);
        }
    }

    // Getters
    public double getTotalEnergyNanoJoules() {
        return totalEnergyNanoJoules;
    }

    public double getInstructionEnergy(String mnemonic) {
        return instructionEnergyMap.getOrDefault(mnemonic, 1.5); // Default 1.5nJ per instruction
    }

    public BlockEnergyData getBlockEnergy(String address) {
        return blockEnergyMap.get(address);
    }

    public boolean isEnergyLoggingEnabled() {
        return energyLoggingEnabled;
    }

    public double getCheckpointThreshold() {
        return checkpointThreshold;
    }

    public Set<String> getKnownInstructions() {
        return instructionEnergyMap.keySet();
    }

    public int getBlockCount() {
        return blockEnergyMap.size();
    }

    // Setters for programmatic configuration
    public void setTotalEnergyNanoJoules(double energy) {
        this.totalEnergyNanoJoules = energy;
    }

    public void addInstructionEnergy(String mnemonic, double energy) {
        this.instructionEnergyMap.put(mnemonic, energy);
    }

    /**
     * Extract instruction energy mappings from block data when no explicit map is provided
     */
    private void extractInstructionEnergyFromBlocks(JSONObject root) {
        if (!root.containsKey("blocks")) return;

        Map<String, Double> mnemonicEnergySum = new HashMap<>();
        Map<String, Integer> mnemonicCount = new HashMap<>();

        JSONArray blocks = (JSONArray) root.get("blocks");
        for (Object blockObj : blocks) {
            JSONObject block = (JSONObject) blockObj;
            if (block.containsKey("instructions")) {
                JSONArray instructions = (JSONArray) block.get("instructions");
                for (Object instrObj : instructions) {
                    JSONObject instr = (JSONObject) instrObj;
                    String mnemonic = (String) instr.get("mnemonic");
                    double energy = ((Number) instr.get("energy_est")).doubleValue();

                    mnemonicEnergySum.put(mnemonic, mnemonicEnergySum.getOrDefault(mnemonic, 0.0) + energy);
                    mnemonicCount.put(mnemonic, mnemonicCount.getOrDefault(mnemonic, 0) + 1);
                }
            }
        }

        // Calculate average energy per mnemonic
        for (String mnemonic : mnemonicEnergySum.keySet()) {
            double avgEnergy = mnemonicEnergySum.get(mnemonic) / mnemonicCount.get(mnemonic);
            this.instructionEnergyMap.put(mnemonic, avgEnergy);
        }

        System.out.println("Extracted " + instructionEnergyMap.size() + " instruction types from block analysis");
    }
}