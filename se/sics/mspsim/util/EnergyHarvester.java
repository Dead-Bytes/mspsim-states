/**
 * Copyright (c) 2024, Swedish Institute of Computer Science.
 * All rights reserved.
 *
 * Energy Harvesting Module for MSPSim
 * Simulates solar energy harvesting during block execution
 * Integrates with block-level energy monitoring for realistic IoT scenarios
 *
 * Author: MSPSim Energy Extension
 */

package se.sics.mspsim.util;

import java.io.*;
import java.util.*;

public class EnergyHarvester {

    // Solar panel specifications
    private double panelAreaM2;           // Solar panel area in square meters
    private double panelEfficiency;       // Panel efficiency (0.0-1.0)
    private double converterEfficiency;   // DC-DC converter efficiency (0.0-1.0)

    // Environmental parameters
    private double baseIrradianceWM2;     // Base solar irradiance (W/m²)
    private double currentIrradianceWM2;  // Current actual irradiance (with variations)
    private double timeAcceleration;      // Time acceleration factor for simulation

    // Random variation parameters
    private Random random;
    private double irradianceVariation;   // Variation factor (0.0-1.0)
    private long lastIrradianceUpdateMS;  // Last time irradiance was updated
    private long irradianceUpdateIntervalMS; // How often to update irradiance

    // Energy harvesting tracking
    private double totalHarvestedNJ;      // Total energy harvested since start
    private double harvestingRateNJPerMS; // Current harvesting rate per millisecond
    private long lastUpdateTimeMS;        // Last time we updated harvesting

    // Block-level harvesting
    private boolean harvestingEnabled;
    private String harvestingProfile;     // "static", "dynamic", "realistic", "random"

    public EnergyHarvester() {
        // Default small IoT solar panel - sized for balanced energy scenarios
        this.panelAreaM2 = 0.001;         // 1 mm² panel
        this.panelEfficiency = 0.20;      // 20% efficiency
        this.converterEfficiency = 0.85;  // 85% converter efficiency

        // Default conditions - balanced harvest vs consumption
        this.baseIrradianceWM2 = 50.0;    // 50 W/m² (balanced indoor/outdoor conditions)
        this.currentIrradianceWM2 = baseIrradianceWM2;
        this.timeAcceleration = 1.0;       // Real-time simulation

        // Random variation settings
        this.random = new Random();
        this.irradianceVariation = 0.8;    // 80% variation (can drop to 20% or spike to 180%)
        this.irradianceUpdateIntervalMS = 100; // Update every 100ms for realistic variation
        this.lastIrradianceUpdateMS = System.currentTimeMillis();

        // Initialize tracking
        this.totalHarvestedNJ = 0.0;
        this.lastUpdateTimeMS = System.currentTimeMillis();
        this.harvestingEnabled = true;
        this.harvestingProfile = "random";  // Default to random for realistic scenarios

        updateHarvestingRate();

        System.out.println("Energy Harvester initialized:");
        System.out.println("  Panel: " + (panelAreaM2 * 10000) + " cm², " + (panelEfficiency * 100) + "% efficiency");
        System.out.println("  Current irradiance: " + currentIrradianceWM2 + " W/m²");
        System.out.println("  Harvesting rate: " + (harvestingRateNJPerMS * 1000) + " nJ/second");
    }

    /**
     * Create harvester from environment variables
     */
    public static EnergyHarvester createFromEnv() {
        EnergyHarvester harvester = new EnergyHarvester();

        String panelAreaStr = System.getenv("HARVESTER_PANEL_AREA_CM2");
        if (panelAreaStr != null) {
            try {
                double areaCM2 = Double.parseDouble(panelAreaStr);
                harvester.panelAreaM2 = areaCM2 / 10000.0; // Convert cm² to m²
            } catch (NumberFormatException e) {
                System.err.println("Invalid HARVESTER_PANEL_AREA_CM2: " + panelAreaStr);
            }
        }

        String efficiencyStr = System.getenv("HARVESTER_PANEL_EFFICIENCY");
        if (efficiencyStr != null) {
            try {
                harvester.panelEfficiency = Double.parseDouble(efficiencyStr) / 100.0;
            } catch (NumberFormatException e) {
                System.err.println("Invalid HARVESTER_PANEL_EFFICIENCY: " + efficiencyStr);
            }
        }

        String irradianceStr = System.getenv("HARVESTER_IRRADIANCE_WM2");
        if (irradianceStr != null) {
            try {
                harvester.baseIrradianceWM2 = Double.parseDouble(irradianceStr);
                harvester.currentIrradianceWM2 = harvester.baseIrradianceWM2;
            } catch (NumberFormatException e) {
                System.err.println("Invalid HARVESTER_IRRADIANCE_WM2: " + irradianceStr);
            }
        }

        String profileStr = System.getenv("HARVESTER_PROFILE");
        if (profileStr != null) {
            harvester.harvestingProfile = profileStr;
        }

        harvester.updateHarvestingRate();
        return harvester;
    }

    /**
     * Update irradiance with random variations to simulate real-world conditions
     */
    private void updateRandomIrradiance() {
        long currentTime = System.currentTimeMillis();

        if (currentTime - lastIrradianceUpdateMS >= irradianceUpdateIntervalMS) {
            if ("random".equals(harvestingProfile)) {
                // Generate random variation: base ± (variation * base)
                double variationRange = baseIrradianceWM2 * irradianceVariation;
                double minIrradiance = Math.max(0, baseIrradianceWM2 - variationRange);
                double maxIrradiance = baseIrradianceWM2 + variationRange;

                // Random value between min and max
                currentIrradianceWM2 = minIrradiance + (random.nextDouble() * (maxIrradiance - minIrradiance));

                // Occasionally simulate complete shadow/eclipse (5% chance)
                if (random.nextDouble() < 0.05) {
                    currentIrradianceWM2 = 0.0;
                }

                // Occasionally simulate bright sunlight burst (5% chance)
                if (random.nextDouble() < 0.05) {
                    currentIrradianceWM2 = baseIrradianceWM2 * 3.0;
                }

                updateHarvestingRate();
            }

            lastIrradianceUpdateMS = currentTime;
        }
    }

    /**
     * Update the current harvesting rate based on environmental conditions
     */
    private void updateHarvestingRate() {
        // Power = Irradiance × Area × Panel_Efficiency × Converter_Efficiency
        double powerWatts = currentIrradianceWM2 * panelAreaM2 * panelEfficiency * converterEfficiency;

        // Debug logging
        if (Math.random() < 0.01) { // Log 1% of the time to avoid spam
            System.out.println("DEBUG HARVEST RATE:");
            System.out.println("  Irradiance: " + currentIrradianceWM2 + " W/m²");
            System.out.println("  Area: " + panelAreaM2 + " m² (" + (panelAreaM2 * 10000) + " cm²)");
            System.out.println("  Panel eff: " + panelEfficiency);
            System.out.println("  Converter eff: " + converterEfficiency);
            System.out.println("  Power: " + powerWatts + " W");
            System.out.println("  Rate: " + (powerWatts * 1_000_000.0) + " nJ/ms");
        }

        // Convert to nanojoules per millisecond
        // 1 Watt = 1 Joule/second = 1,000,000,000 nJ/second = 1,000,000 nJ/ms
        harvestingRateNJPerMS = powerWatts * 1_000_000.0 * timeAcceleration;
    }

    /**
     * Calculate energy that will be harvested during a specific time interval
     */
    public double calculateHarvestedEnergyForDuration(long durationMS) {
        if (!harvestingEnabled) {
            return 0.0;
        }

        // Update irradiance with random variations
        updateRandomIrradiance();

        // For static profile, use current rate
        if ("static".equals(harvestingProfile)) {
            return harvestingRateNJPerMS * durationMS;
        }

        // For random profile, simulate varying conditions over time
        if ("random".equals(harvestingProfile)) {
            return calculateRandomHarvesting(durationMS);
        }

        // For dynamic profile, simulate varying conditions
        if ("dynamic".equals(harvestingProfile)) {
            return calculateDynamicHarvesting(durationMS);
        }

        // For realistic profile, use time-of-day patterns
        if ("realistic".equals(harvestingProfile)) {
            return calculateRealisticHarvesting(durationMS);
        }

        return harvestingRateNJPerMS * durationMS;
    }

    /**
     * Simulate random harvesting with irradiance variations during execution
     */
    private double calculateRandomHarvesting(long durationMS) {
        double totalHarvested = 0.0;
        long stepSize = Math.max(1, Math.min(durationMS / 10, irradianceUpdateIntervalMS)); // Sample every update interval or 10% of duration

        for (long t = 0; t < durationMS; t += stepSize) {
            // Update irradiance for this time step
            updateRandomIrradiance();

            // Calculate energy for this time step
            long actualStepSize = Math.min(stepSize, durationMS - t);
            totalHarvested += harvestingRateNJPerMS * actualStepSize;
        }

        return totalHarvested;
    }

    /**
     * Simulate dynamic harvesting with random variations
     */
    private double calculateDynamicHarvesting(long durationMS) {
        double totalHarvested = 0.0;
        long stepSize = Math.max(1, durationMS / 100); // Sample every 1% of duration

        for (long t = 0; t < durationMS; t += stepSize) {
            // Add ±20% random variation to irradiance
            double variation = 0.8 + (Math.random() * 0.4); // 0.8 to 1.2
            double instantRate = harvestingRateNJPerMS * variation;
            totalHarvested += instantRate * Math.min(stepSize, durationMS - t);
        }

        return totalHarvested;
    }

    /**
     * Simulate realistic harvesting based on time of day
     */
    /**
     * Simulate realistic harvesting with battery-level awareness
     * Charging probability varies by battery region:
     * - GOOD region (>70%): 5-10% charging scenarios
     * - MODERATE region (30-70%): 10-20% charging scenarios
     * - LOW region (<30%): 20-30% charging scenarios (survival mode)
     */
    private double calculateRealisticHarvesting(long durationMS) {
        return calculateRealisticHarvestingWithBattery(durationMS, -1); // Use default if battery unknown
    }

    /**
     * Calculate realistic harvesting with explicit battery level
     */
    public double calculateRealisticHarvestingWithBattery(long durationMS, double batteryPercent) {
        Calendar cal = Calendar.getInstance();
        int hourOfDay = cal.get(Calendar.HOUR_OF_DAY);
        int minute = cal.get(Calendar.MINUTE);

        // Determine battery region and corresponding charging probability
        double chargingProbability;
        double harvestingBoost;

        if (batteryPercent < 0) {
            // Battery level unknown, use default moderate behavior
            chargingProbability = 0.15; // 15% default
            harvestingBoost = 1.0;
        } else if (batteryPercent > 70.0) {
            // GOOD region: 5-10% charging scenarios (minimal effort)
            chargingProbability = 0.05 + (Math.random() * 0.05); // 5-10%
            harvestingBoost = 0.8; // Reduce harvesting by 20%
        } else if (batteryPercent > 30.0) {
            // MODERATE region: 10-20% charging scenarios
            chargingProbability = 0.10 + (Math.random() * 0.10); // 10-20%
            harvestingBoost = 1.0; // Normal harvesting
        } else {
            // LOW region: 20-30% charging scenarios (aggressive survival mode)
            chargingProbability = 0.20 + (Math.random() * 0.10); // 20-30%
            harvestingBoost = 1.5; // Increase harvesting by 50%
        }

        // Base daylight pattern
        double daylightFactor;
        if (hourOfDay < 6 || hourOfDay > 20) {
            daylightFactor = 0.0; // Night time
        } else if (hourOfDay >= 11 && hourOfDay <= 13) {
            // Peak hours with battery-aware boost
            double minuteFactor = minute / 60.0;
            daylightFactor = 0.3 + (minuteFactor * 0.4 * (1.0 + chargingProbability));
        } else {
            // Dawn/dusk
            double distanceFromNoon = Math.abs(12 - hourOfDay);
            daylightFactor = Math.max(0.05, 0.25 - (distanceFromNoon * 0.04));
        }

        // Environmental variability
        double environmentalFactor = 0.5 + (Math.random() * 0.3);

        // Random "good positioning" events based on charging probability
        // Simulates device repositioning or shadows clearing
        if (Math.random() < chargingProbability) {
            // Good harvesting event - significant boost
            environmentalFactor *= (1.5 + Math.random() * 0.5); // 1.5x to 2.0x boost
        }

        // Apply battery-aware harvesting with boost
        double effectiveFactor = daylightFactor * environmentalFactor * harvestingBoost * 0.6;

        return harvestingRateNJPerMS * effectiveFactor * durationMS;
    }

    /**
     * Update harvester and return energy harvested since last update
     */
    public double updateAndGetHarvestedEnergy() {
        if (!harvestingEnabled) {
            return 0.0;
        }

        long currentTime = System.currentTimeMillis();
        long elapsedMS = currentTime - lastUpdateTimeMS;

        double harvested = calculateHarvestedEnergyForDuration(elapsedMS);
        totalHarvestedNJ += harvested;
        lastUpdateTimeMS = currentTime;

        return harvested;
    }

    /**
     * Predict energy balance for a block execution (without battery level)
     */
    public EnergyBalance predictEnergyBalanceForBlock(double blockEnergyConsumption, long blockExecutionTimeMS) {
        return predictEnergyBalanceForBlock(blockEnergyConsumption, blockExecutionTimeMS, -1);
    }

    /**
     * Predict energy balance for a block execution with battery-aware harvesting
     * Returns: { harvestedEnergy, netEnergyChange, isEnergyPositive }
     */
    public EnergyBalance predictEnergyBalanceForBlock(double blockEnergyConsumption, long blockExecutionTimeMS, double batteryPercent) {
        double harvestedEnergy = calculateRealisticHarvestingWithBattery(blockExecutionTimeMS, batteryPercent);
        double netEnergyChange = harvestedEnergy - blockEnergyConsumption;
        boolean isEnergyPositive = netEnergyChange > 0;

        return new EnergyBalance(harvestedEnergy, netEnergyChange, isEnergyPositive, blockExecutionTimeMS);
    }

    /**
     * Energy balance result for a block
     */
    public static class EnergyBalance {
        public final double harvestedEnergy;
        public final double netEnergyChange;
        public final boolean isEnergyPositive;
        public final long executionTimeMS;

        public EnergyBalance(double harvested, double netChange, boolean positive, long timeMS) {
            this.harvestedEnergy = harvested;
            this.netEnergyChange = netChange;
            this.isEnergyPositive = positive;
            this.executionTimeMS = timeMS;
        }

        @Override
        public String toString() {
            return String.format("EnergyBalance{harvested=%.2f nJ, net=%.2f nJ, positive=%s, time=%d ms}",
                               harvestedEnergy, netEnergyChange, isEnergyPositive, executionTimeMS);
        }
    }

    // Getters and setters
    public double getCurrentIrradiance() { return currentIrradianceWM2; }
    public void setCurrentIrradiance(double irradiance) {
        this.currentIrradianceWM2 = irradiance;
        updateHarvestingRate();
    }

    public double getPanelAreaCM2() { return panelAreaM2 * 10000; }
    public void setPanelAreaCM2(double areaCM2) {
        this.panelAreaM2 = areaCM2 / 10000.0;
        updateHarvestingRate();
    }

    public double getPanelEfficiency() { return panelEfficiency * 100; }
    public void setPanelEfficiency(double efficiency) {
        this.panelEfficiency = efficiency / 100.0;
        updateHarvestingRate();
    }

    public double getHarvestingRateNJPerSecond() { return harvestingRateNJPerMS * 1000; }
    public double getTotalHarvestedNJ() { return totalHarvestedNJ; }

    public boolean isHarvestingEnabled() { return harvestingEnabled; }
    public void setHarvestingEnabled(boolean enabled) { this.harvestingEnabled = enabled; }

    public String getHarvestingProfile() { return harvestingProfile; }
    public void setHarvestingProfile(String profile) { this.harvestingProfile = profile; }

    /**
     * Get harvester status string
     */
    public String getStatusString() {
        if ("random".equals(harvestingProfile)) {
            return String.format("Harvester: %.3f cm² panel, %.3f W/m² base (current: %.3f), %.2f nJ/s rate, %.2f nJ total",
                               getPanelAreaCM2(), baseIrradianceWM2, currentIrradianceWM2, getHarvestingRateNJPerSecond(), totalHarvestedNJ);
        } else {
            return String.format("Harvester: %.3f cm² panel, %.3f W/m² irradiance, %.2f nJ/s rate, %.2f nJ total",
                               getPanelAreaCM2(), currentIrradianceWM2, getHarvestingRateNJPerSecond(), totalHarvestedNJ);
        }
    }
}